# 03 · Formato CIF y parser

> Estado: **BORRADOR, contrastado con un fichero real y con todo el corpus** (sección 3.4 y 3.5). Un fichero de texto real (`001`,
> descargado de RCSB el 18/11/2026) confirma la estructura; las estadísticas de los 1243 ligandos salen de una copia **binaria** del
> diccionario de componentes (sección 3.5), que **no conserva el formato de texto**: lo que depende de cómo se escribe el texto
> (p. ej. un ion en forma de pares) sigue sin verificar y está marcado.
> Documento redactado el 16/11/2026 y actualizado el 18/11/2026.

## Índice

1. [Qué necesitamos del fichero](#1-qué-necesitamos-del-fichero)
2. [Teoría: sintaxis de CIF](#2-teoría-sintaxis-de-cif)
3. [Estructura de un fichero de ligando](#3-estructura-de-un-fichero-de-ligando)
4. [Modelo de datos](#4-modelo-de-datos)
5. [Diseño del parser en tres capas](#5-diseño-del-parser-en-tres-capas)
6. [Simulación paso a paso](#6-simulación-paso-a-paso)
7. [Decisiones de política (abiertas)](#7-decisiones-de-política-abiertas)
8. [Gestión de errores](#8-gestión-de-errores)
9. [Trampas y casos límite](#9-trampas-y-casos-límite)
10. [Pruebas y plan para conseguir datos reales](#10-pruebas-y-plan-para-conseguir-datos-reales)
11. [Alternativas descartadas](#11-alternativas-descartadas)
12. [Qué está verificado y qué no](#12-qué-está-verificado-y-qué-no)
13. [Qué recordar](#13-qué-recordar)

---

## 1. Qué necesitamos del fichero

El fichero `.cif` de un ligando contiene mucha información (nombre químico, fórmula, descriptores, identificadores en otras bases de
datos...). Para dibujar la molécula solo necesitamos **tres cosas**:

| Dato | Para qué sirve | Dónde está |
|------|----------------|------------|
| Lista de **átomos** con su elemento | Una esfera por átomo, color CPK según el elemento | categoría `_chem_comp_atom` |
| **Coordenadas** (x, y, z) de cada átomo | Posición de la esfera | misma categoría |
| Lista de **enlaces** (pares de átomos) | Un cilindro entre dos esferas | categoría `_chem_comp_bond` |

Todo lo demás se **ignora**. Un parser que intenta entenderlo todo es un parser con más sitios donde fallar, y aquí un fallo
durante la evaluación equivale a suspender (documento 01).

El producto del parser es un objeto de dominio, `Molecule`, independiente de Android, de la red y de SceneView. La vista 3D
(documento 07) solo conoce `Molecule`, nunca el texto `.cif`.

---

## 2. Teoría: sintaxis de CIF

CIF (*Crystallographic Information File*) es un formato de texto de la IUCr. Los ficheros de RCSB usan su extensión **mmCIF/PDBx**,
pero la sintaxis es la misma: lo que cambia son los nombres de las categorías. Reglas relevantes (CIF 1.1):

### 2.1 Elementos léxicos

| Elemento | Regla |
|----------|-------|
| Espacio en blanco | Espacios, tabuladores y saltos de línea separan *tokens*. Un `\r` de final de línea de Windows es otro espacio en blanco. |
| Comentario | `#` al principio de un token hasta el final de la línea. No es comentario dentro de comillas ni dentro de un campo de texto. |
| Bloque de datos | `data_NOMBRE`. Un fichero de ligando trae uno. |
| Nombre de dato (*tag*) | Empieza por `_`, por ejemplo `_chem_comp_atom.atom_id`. **No distingue mayúsculas de minúsculas** (`_chem_comp_atom.Atom_ID` es el mismo dato). |
| Palabras reservadas | `loop_`, `data_*`, `save_*`, `global_`, `stop_`. Tampoco distinguen mayúsculas. |
| Valor sin comillas | Cualquier secuencia sin espacios que no empiece por `_ # $ ' " [ ]` y no sea una palabra reservada. **Los valores sí distinguen mayúsculas.** |
| Valor entre comillas | `'...'` o `"..."`, **en una sola línea**. La comilla de cierre solo cierra si va **seguida de un espacio en blanco** (o fin de fichero). No hay escapes con barra invertida. |
| Campo de texto | Empieza con `;` **en la columna uno** y termina en la siguiente línea que empiece con `;`. Puede ocupar varias líneas. |
| `?` y `.` | Valores especiales: `?` = desconocido, `.` = no aplicable. *(Convención general de CIF; no hemos leído la página de la IUCr sobre ellos, ver sección 12.)* |

### 2.2 Dos formas de escribir una categoría

**Forma de pares clave-valor** (una sola fila):

```
_chem_comp.id      XXA
_chem_comp.name    "EJEMPLO INVENTADO"
```

**Forma de bucle** (`loop_`, varias filas): primero las columnas, luego los valores, **fila tras fila, sin saltos de línea
significativos**.

```
loop_
_chem_comp_atom.atom_id
_chem_comp_atom.type_symbol
C1  C
O2  O
```

El número de filas es `nº de valores / nº de columnas`. Las filas **no** se delimitan por el salto de línea: el lector solo sabe
dónde acaba una fila porque cuenta columnas. Esto tiene una consecuencia importante: **un solo valor mal tokenizado desplaza todas
las filas siguientes** (sección 9).

> **Importante:** una categoría con una sola fila (por ejemplo un ion como el cloruro, con un único átomo) puede venir escrita
> en forma de pares y no de bucle. El parser tiene que aceptar **las dos formas** para la misma categoría. *(Comportamiento
> esperado según el formato; pendiente de ver en un fichero real, sección 12.)*

### 2.3 Por qué no basta con `split(" ")`

Un nombre de átomo puede llevar comilla simple: en los nucleótidos hay átomos como `O5'`, que en el fichero se escriben
**entre comillas dobles**: `"O5'"`. Si el parser corta por espacios y por comillas a ciegas, esa fila tiene un valor de más o de
menos y todas las filas siguientes quedan desplazadas. Es **el caso límite que ya anticipaba el documento 01** y el motivo de que el
tokenizador siga las reglas de la sección 2.1.

---

## 3. Estructura de un fichero de ligando

Ejemplo **inventado** para ilustrar (el identificador `XXA` y los números no corresponden a ningún ligando real; la estructura sí
sigue lo descrito en las referencias de la sección 12):

```
data_XXA
#
_chem_comp.id               XXA
_chem_comp.name             "EJEMPLO INVENTADO"
_chem_comp.type             NON-POLYMER
#
loop_
_chem_comp_atom.comp_id
_chem_comp_atom.atom_id
_chem_comp_atom.type_symbol
_chem_comp_atom.charge
_chem_comp_atom.model_Cartn_x
_chem_comp_atom.model_Cartn_y
_chem_comp_atom.model_Cartn_z
_chem_comp_atom.pdbx_model_Cartn_x_ideal
_chem_comp_atom.pdbx_model_Cartn_y_ideal
_chem_comp_atom.pdbx_model_Cartn_z_ideal
XXA C1    C  0 0.000 0.000 0.000  0.001 0.000 0.000
XXA "O5'" O  0 1.230 0.000 0.000  1.229 0.000 0.000
XXA CL1   CL 0 -1.700 0.100 0.000 -1.699 0.101 0.000
#
loop_
_chem_comp_bond.comp_id
_chem_comp_bond.atom_id_1
_chem_comp_bond.atom_id_2
_chem_comp_bond.value_order
XXA C1 "O5'" DOUB
XXA C1 CL1   SING
```

### 3.1 Categorías que usamos

**`_chem_comp_atom`** (la tabla oficial de wwPDB tiene 16 elementos; los que nos importan):

| Columna | Significado | Uso |
|---------|-------------|-----|
| `atom_id` | Nombre del átomo dentro del ligando (`C1`, `"O5'"`). **Es la clave que usan los enlaces.** | Sí |
| `type_symbol` | Símbolo del elemento (`C`, `CL`, `FE`) | Sí (color CPK) |
| `model_Cartn_x/y/z` | Coordenadas del átomo en la estructura experimental de referencia, en ångström | Sí (ver 7.1) |
| `pdbx_model_Cartn_x/y/z_ideal` | Coordenadas «ideales» calculadas | Sí (ver 7.1) |
| `charge` | Carga formal | No (ignorada) |
| `pdbx_aromatic_flag`, `pdbx_stereo_config`, `pdbx_leaving_atom_flag`, `pdbx_ordinal`, `alt_atom_id`, `pdbx_align`, `comp_id` | Metadatos | No |

**`_chem_comp_bond`**:

| Columna | Significado | Uso |
|---------|-------------|-----|
| `atom_id_1`, `atom_id_2` | Nombres (de `atom_id`) de los dos átomos unidos | Sí |
| `value_order` | Orden del enlace. Valores permitidos: `arom`, `delo`, `doub`, `pi`, `poly`, `quad`, `sing`, `trip` (sin distinguir mayúsculas; por defecto `sing`; no es obligatoria) | Sí (modelo); dibujo en 3D, ver 7.4 |
| `comp_id`, `pdbx_aromatic_flag`, `pdbx_stereo_config`, `pdbx_ordinal` | Metadatos | No |

### 3.2 Regla de oro: las columnas se buscan por NOMBRE, nunca por posición

Aunque el orden de las columnas parezca fijo, **no lo asumimos**. Un fichero de PDBe que hemos podido consultar (ATP) trae columnas y
categorías adicionales que el de RCSB quizá no tenga. La búsqueda siempre es «dame la posición de `atom_id` en *este* bucle» y, si
no existe, se trata como error controlado.

### 3.3 Cosas que el fichero no garantiza

- Un ligando puede no tener **ninguna** fila de `_chem_comp_bond` (por ejemplo, un ion de un solo átomo): sin categoría de enlaces
  no es un error, es una molécula con cero enlaces. *(Inferencia; `ligands.txt` trae unos 40 ids de 1–2 caracteres que lo ejercitarán.)*
- Los símbolos de elemento llegan **en mayúsculas** (`CL`, `FE`), mientras que la tabla CPK se escribe habitualmente `Cl`, `Fe`.
  Hay que normalizar. *(Pendiente de confirmar en datos reales.)*
- Puede haber `?` en lugar de coordenadas en alguna de las dos familias. *(Pendiente de confirmar.)*

### 3.4 Verificado con un fichero real: ligando `001`

El 18/11/2026 se obtuvo el fichero real del ligando `001` (el primero de `ligands.txt`). Lo medido con el analizador de referencia
(`cif_stats.py`) y con un cálculo geométrico sobre el fichero:

| Hecho | Valor | Consecuencia para el parser |
|-------|-------|------------------------------|
| Átomos / enlaces | 87 átomos (45 pesados + 42 H), 90 enlaces | Coincide con la fórmula del propio fichero (`C35 H42 F2 N2 O6`) y con la cuenta de anillos (90 − 87 + 1 = 4) |
| Columnas de `_chem_comp_atom` | **18**: las 16 de la tabla de wwPDB **más** `pdbx_component_atom_id` y `pdbx_component_comp_id` | Confirma «buscar por nombre»: la tabla oficial **no** coincide con el fichero real. (Un test del tokenizador lo comprueba: 87 filas × 18 valores; en una primera redacción de este documento se escribió 17 por un error de cuenta, que el propio test detectó.) |
| Coordenadas | Existen las dos familias, **sin `?`** | Una muestra; no prueba nada sobre otros ligandos |
| Nombre del ligando | Campo de texto `;` **multilínea** que empieza en la línea siguiente a la etiqueta (`_chem_comp.name`) | El tokenizador debe soportar campos `;` (caso 2 de la sección 9): **confirmado como real**, no teórico |
| Categorías de una fila | `_chem_comp` y `_pdbx_chem_comp_synonyms` vienen **en forma de pares** | Confirma que la forma de pares existe; para `_chem_comp_atom` en un ion **sigue sin verificar** |
| Otras categorías | `_pdbx_chem_comp_descriptor`, `_pdbx_chem_comp_identifier`, `_pdbx_chem_comp_audit` con valores entre comillas dobles que contienen espacios, paréntesis, corchetes y `@` | Se ignoran, pero **el tokenizador tiene que atravesarlas sin romperse** |
| Líneas `#   #` | Comentarios con texto tras el `#` | Comentario hasta fin de línea: correcto |
| Identificador | `001`: solo dígitos | **Nunca convertir un id a número** (42 de los 1243 ids son solo dígitos) |
| Símbolos de elemento | `C`, `H`, `O`, `N`, `F` (una letra, mayúscula) | No contradice la normalización; los de dos letras (`CL`) **siguen sin verificar** |
| `value_order` | Solo `SING` y `DOUB`, en mayúsculas | El aromático va aparte, en `pdbx_aromatic_flag` (`Y`), con forma de Kekulé: **no apareció `arom`** en `value_order` |
| Nombres de átomo | Únicos, sin comillas (`C01`, `H021`) | Con una muestra no se puede afirmar que no se repitan en otros ligandos |
| Fin de línea | `\n` (el fichero adjunto no tiene `\r`) | No prueba nada sobre otros orígenes |

**Hallazgo clave para la política 7.1: los dos juegos de coordenadas no están en el mismo sistema de referencia.**

| Juego | Centroide (Å) | Radio máx. desde el centroide | Longitud de enlace mín.–máx. | Enlaces C–H | Dobles |
|-------|---------------|-------------------------------|------------------------------|-------------|--------|
| `model_Cartn_*` | (28,99; 13,81; 24,98) | 9,16 | 1,102–1,561 | 1,102–1,116 | 1,216–1,426 |
| `pdbx_model_Cartn_*_ideal` | (−0,08; −0,04; 0,26) | 8,65 | 1,080–1,532 | 1,080–1,091 | 1,209–1,390 |

- En `001` las **ideales** están centradas casi en el origen y las de **modelo** a unos 30 Å (vienen del sistema de coordenadas de la
  estructura cristalina de origen). **Esto no se cumple en general:** en el corpus completo hay ligandos con las coordenadas
  ideales a más de 200 Å del origen (`OMO`: 226 Å; sección 3.5). Por tanto **siempre se centra en el centroide**, sea cual sea el juego.
- Las longitudes de enlace de ambas son químicamente razonables (C–H ≈ 1,1 Å, C=O ≈ 1,2 Å).
- Este análisis es de **un solo ligando**; la política se confirma con el corpus.

#### Sobre `ligands.txt` y `ligands_temp.txt`

| Hecho | Valor |
|-------|-------|
| Ligandos en `ligands.txt` | **1243**, sin repetidos, ordenados alfabéticamente (orden de bytes), un id por línea, solo `[A-Z0-9]`, sin `\r` |
| Longitud de los ids | 3 ids de 1 carácter (`F`, `K`, `O`), 36 de 2 (`CL`, `NA`, `ZN`, `FE`, `OH`, `NO`...) y 1204 de 3 |
| Ids solo con dígitos | 42 (`001`, `011`, `140`, `200`...) |
| `ligands_temp.txt` | Son **exactamente las primeras 235 líneas** de `ligands.txt` (comprobado con `diff`). Parece una lista recortada; **no sabemos para qué se creó**. No hace falta en el proyecto. |

Consecuencias: (1) la lista contiene unas 40 entradas de uno o dos caracteres, en su mayoría iones o moléculas de muy pocos átomos,
así que **los casos «ion» y «sin enlaces» se van a ejercitar de verdad** (no es una hipótesis de laboratorio); (2) hay ids como
`UNK`, `UNL` y `UNX` cuyo contenido **no hemos visto** y hay que mirar con cuidado (sección 10); (3) el id es siempre **texto**.

### 3.5 Corpus completo: los 1243 ligandos de `ligands.txt`

**De dónde salen los datos.** Desde el entorno donde se redacta este documento no se puede llegar a RCSB, wwPDB ni PDBe. Sí se
puede descargar de PyPI el paquete `biotite` 2.0.0, que incluye una copia **binaria** (BinaryCIF) de todo el Diccionario de
Componentes Químicos (51 436 componentes, 2,46 millones de átomos). Se **validó contra el fichero real**: para `001` da 87 átomos,
90 enlaces y **las mismas coordenadas** que el `.cif` de RCSB.

**Límite importante:** es una copia binaria, no el texto. Sirve para saber **qué contienen** los ligandos; no sirve para saber **cómo
se escriben** en el fichero de texto (por ejemplo, si un ion usa pares o bucle). Esas preguntas siguen abiertas y están marcadas.

Resultados sobre los 1243 ids de `ligands.txt`:

| Medida | Resultado |
|--------|-----------|
| Ids presentes | **1242 de 1243. Falta `UNL`** (en esta copia no existe; qué responde el servidor de RCSB para `UNL.cif` no se ha podido comprobar; lo esperable es un 404) |
| Átomos por ligando | mín. 1, mediana 35, p95 104, **máx. 256** (`CDL`; después `BV4` 249, `15P` 244) |
| Enlaces por ligando | mín. 0, mediana 36, p95 105, máx. 256 |
| Distribución por nº de átomos | 1: 39 · 2: 8 · 3–5: 29 · 6–20: 194 · 21–60: 715 · 61–150: 243 · >150: 14 |
| Ligandos de **un solo átomo y sin enlaces** | **39** (`AU`, `BA`, `CL`, `ZN`, `NA`...); coinciden exactamente con los de «sin enlaces» |
| Símbolos de elemento | **Siempre en mayúsculas**: `CL` 138, `FE` 18, `BR` 45, `MO`, `CU`... Ninguno en minúsculas. Hay un elemento `X` (el ligando `UNX`) |
| Orden de enlace (`value_order`) | Solo `SING` (48 437), `DOUB` (6 061) y `TRIP` (29). **Ninguno `AROM`** |
| Nombres de átomo duplicados dentro de un ligando | **0** |
| Enlaces que apuntan a un átomo inexistente | **0** |
| Átomos sin ningún juego de coordenadas | **0** |
| Dos átomos a menos de 0,4 Å (geometría degenerada) | **0** |
| Nombres de átomo que necesitan comillas | **155 ligandos, 2324 átomos, todos por una comilla simple** (`C1'`, `O5'`...). Ninguno con espacio, comilla doble ni carácter especial. Alfabeto de los nombres: letras mayúsculas, dígitos y `'` |
| Longitud de los nombres de átomo | 1 a 4 caracteres |
| Radio de la molécula (ideales) | hasta **41 Å** (`15P`), `PEU` 37 Å, `CDL` 31 Å |
| Distancia del centroide ideal al origen | hasta **226 Å** (`OMO`), `MO7` 102 Å, `ALF` 102 Å |

**Completitud de coordenadas, por ligando:**

| Situación | Ligandos | Ejemplos |
|-----------|----------|----------|
| Ideales completas **y** modelo completas | 1177 | `001`, `031`... |
| Ideales completas, modelo **incompleta** (`?` en algún átomo) | 48 | `011`, `15P`, `418`... (en `15P` falta **todo** el modelo) |
| Ideales **incompletas**, modelo completa | 17 | `2HP`, `A8N`, `BCR`, `CBE`, `DHI`, `EAA`, `F19`, `FCO`...: en 10 de ellos faltan **todas** las ideales; en 7, solo algunas |
| Ninguna de las dos completa | **0** | — |

Consecuencias, que se recogen en las secciones 7 y 9:
- **Siempre existe un juego completo**, pero **no siempre es el mismo**: hay que elegir **por ligando**, no por átomo (7.1).
- **Los iones y las moléculas diminutas no son un caso raro de laboratorio**: son el 3 % de la lista.
- **La molécula más grande tiene 256 átomos y 256 enlaces**. Con un nodo de pivote y un cilindro por enlace (diseño del spike) serían
  unos **768 nodos** en escena, no los ~200 que se supusieron para la hipótesis H6.

---

## 4. Modelo de datos

Diseño propuesto (esbozo, el código lo escribe el equipo):

```
Molecule
 ├─ id:      String           // identificador del ligando, "ATP"
 ├─ atoms:   List<Atom>
 └─ bonds:   List<Bond>

Atom
 ├─ name:    String           // "O5'" — lo que se muestra al tocar
 ├─ element: Element          // enum o String normalizado ("Cl")
 └─ x, y, z: Float            // ångström

Bond
 ├─ from:    Int              // ÍNDICE en atoms (no el nombre)
 ├─ to:      Int
 └─ order:   BondOrder        // SINGLE, DOUBLE, TRIPLE, AROMATIC, OTHER
```

Decisiones de diseño:

1. **Los enlaces guardan índices, no nombres.** El nombre sirve solo para unir las dos tablas durante el análisis. Una vez
   resuelto, el índice permite acceder al átomo en O(1) y no depende de que los nombres sean únicos.
2. **Clases inmutables** (`data class` con `val`): se pueden pasar entre hilos y entre pantallas sin copias defensivas.
3. **Sin `android.*` en el parser.** Así se prueba con tests de JVM (rápidos, sin dispositivo). Prohibido `Log.d` dentro del parser.
4. **`Float`, no `Double`**: las coordenadas ya vienen con tres decimales, y SceneView trabaja con `Float`.
5. **El orden de `atoms` es el orden del fichero.** Es lo que usaremos como `id` del nodo (`"atom 3"`), igual que en el spike.

---

## 5. Diseño del parser en tres capas

```
texto .cif ──▶ [1 Tokenizador] ──▶ tokens ──▶ [2 Lector de tablas] ──▶ CifBlock ──▶ [3 Extractor] ──▶ Molecule
                 sintaxis CIF                  loop_ / pares            «tablas      semántica de
                 (comillas, ;, #)              (columnas × filas)       con nombre»  ligandos + validación
```

Separar en capas permite probar cada una por separado y, sobre todo, que **solo la capa 3 sepa algo de química**.

### 5.1 Capa 1: tokenizador

Entrada: `String`. Salida: secuencia de tokens `(tipo, valor)`, con tipo ∈ {`LOOP`, `DATA`, `TAG`, `VALUE`}.

Algoritmo (máquina de estados sobre un índice `i` y una marca «estoy al principio de línea»):

```
mientras i < n:
    c = texto[i]
    si c es espacio, tab o \r:  i++
    si c es \n:                  principio_de_linea = true; i++
    si c == '#':                 saltar hasta \n                          (comentario)
    si c == ';' y principio_de_linea:
                                 leer líneas hasta la que empieza por ';'  -> VALUE (campo de texto)
    si c es ' o ":               buscar la comilla igual QUE VA SEGUIDA DE
                                 espacio/fin;  -> VALUE (sin comillas)
                                 si llegamos a \n sin cerrar: entrada MALFORMADA
    en otro caso:                leer hasta espacio -> palabra
                                 si palabra.lower() == "loop_"     -> LOOP
                                 si empieza por "data_" (sin caso)  -> DATA
                                 si empieza por "_"                 -> TAG
                                 en otro caso                       -> VALUE
```

Detalles que importan:

- «Principio de línea» se pone a `false` en cuanto se consume cualquier carácter que no sea `\n` (si no, un `;` en mitad de línea se
  confundiría con el inicio de un campo de texto).
- Un `#` solo es comentario **al principio de un token**: en `C1#` el `#` forma parte del valor.
- Un campo de texto sin cerrar (fin de fichero antes del `;` final) es entrada malformada.
- Un BOM UTF-8 (`﻿`) al inicio se descarta.

### 5.2 Capa 2: lector de tablas

Convierte la secuencia de tokens en **tablas**: lista de nombres de columna (en minúsculas) y lista de filas.

```
estado = FUERA
por cada token:
    LOOP:   estado = LEYENDO_COLUMNAS; columnas = []
    TAG:    si estado == LEYENDO_COLUMNAS:   columnas += tag
            si no (forma de pares):          recordar tag como «clave pendiente»
    VALUE:  si estado == LEYENDO_COLUMNAS y hay columnas:  estado = LEYENDO_FILAS
            si estado == LEYENDO_FILAS:      acumular valor
            si hay clave pendiente:          asignar par clave → valor
    LOOP/TAG/DATA mientras LEYENDO_FILAS:    cerrar el bucle
```

Al cerrar un bucle: si `nº de valores % nº de columnas != 0`, hay una **fila truncada** → entrada malformada (no se inventan
valores). Los pares clave-valor de la misma categoría (prefijo antes del punto) se agrupan en **una tabla de una fila**, de modo que
el extractor ve una única abstracción: `Tabla { columnas, filas }`, vengan de un `loop_` o de pares.

Solo se procesa el **primer** bloque `data_`; si aparece otro, se ignora el resto.

### 5.3 Capa 3: extractor de molécula

```
1. tabla_atomos  = buscar la tabla que contiene la columna "_chem_comp_atom.atom_id"   (si no hay → ERROR)
2. índices de columnas: atom_id, type_symbol, coordenadas (según política 7.1)         (si falta lo imprescindible → ERROR)
3. por cada fila: nombre, elemento normalizado, x, y, z (Float finito)                 (valor inválido → ver 7.2)
4. mapa nombre → índice  (si un nombre se repite → ver 7.3)
5. tabla_enlaces = buscar "_chem_comp_bond.atom_id_1"   (si no existe → lista vacía, NO es error)
6. por cada fila: resolver los dos nombres a índices                                   (nombre desconocido → ver 7.2)
7. exigir al menos 1 átomo
8. devolver Molecule
```

---

## 6. Simulación paso a paso

Entrada (dos líneas del ejemplo de la sección 3):

```
XXA "O5'" O  0 1.230 0.000 0.000  1.229 0.000 0.000
XXA CL1   CL 0 -1.700 0.100 0.000 -1.699 0.101 0.000
```

**Tokenizador**, primera línea (`i` avanza por el texto):

| Posición | Carácter | Acción | Token emitido |
|----------|----------|--------|---------------|
| inicio de línea | `X` | palabra hasta espacio → `XXA`; no es `loop_`, ni `data_`, ni `_` | `VALUE XXA` |
| tras el espacio | `"` | modo comillas dobles; leo `O`, `5`, `'` (la comilla simple **no** cierra: estamos en modo `"`), luego `"` seguida de espacio → cierra | `VALUE O5'` |
| siguiente | `O` | palabra | `VALUE O` |
| siguiente | `0` | palabra | `VALUE 0` |
| siguientes seis | `1.230 0.000 ...` | palabras | `VALUE 1.230`, ..., `VALUE 0.000` |

Resultado: **10 valores** para la fila, igual al número de columnas declaradas. Aquí un `split` por espacios habría dado el mismo
número (el nombre no tiene espacios), pero con un nombre entre comillas que contenga un espacio (`"C1' A"`) saldría un valor de
más y desplazaría todo lo que viene después. Ese es el motivo de respetar las comillas.

**Lector de tablas**, bucle de átomos: el ejemplo declara **10 columnas** (`comp_id`, `atom_id`, `type_symbol`, `charge`, tres
de modelo y tres ideales). Con 3 filas se acumulan 30 valores y `30 % 10 = 0`: tres filas completas. Si por un fallo de
tokenización llegaran 29 valores, `29 % 10 = 9 ≠ 0` y el lector declara **fila truncada** en vez de inventar un valor.

**Extractor**: `atom_id` está en la posición 1 (índice base 0), `type_symbol` en la 2. Fila 2: nombre `O5'`, elemento `O`, x = 1.229
(si la política es «ideales primero»). Fila 3: elemento `CL` → normalizado a `Cl`. Mapa: `C1→0`, `O5'→1`, `CL1→2`. Enlace
`C1 "O5'" DOUB` → `Bond(0, 1, DOUBLE)`; `C1 CL1 SING` → `Bond(0, 2, SINGLE)`.

---

## 7. Decisiones de política (abiertas)

Son elecciones con varias respuestas razonables. Se cierran **con los datos reales**, no antes.

### 7.1 ¿Qué coordenadas usar? (decidida con datos: D10)

Hay dos juegos por átomo: **ideales** (`pdbx_model_Cartn_*_ideal`) y de **modelo** (`model_Cartn_*`). El corpus completo (3.5) dice:

| Situación | Ligandos |
|-----------|----------|
| Ideales completas | 1225 |
| Ideales incompletas, modelo completa | 17 |
| Ninguna completa | **0** |

**Regla:** se elige **un juego entero por ligando**: ideales si **todos** los átomos las tienen; si no, modelo si **todos** las
tienen; si ninguno está completo, `Fallo`. Con los datos actuales ese fallo no ocurre nunca; se deja como red de seguridad.

**Por qué por ligando y no por átomo** (la opción «ideales primero y, átomo a átomo, modelo como reserva» queda **descartada**): los
dos juegos están en **sistemas de referencia distintos** (en `001`, a unos 30 Å uno de otro). Mezclarlos dentro de una molécula
colocaría unos átomos en un sitio y otros a decenas de ångström, y las longitudes de enlace serían absurdas. Los 7 ligandos con ideales
incompletas son justo el caso que rompería esa mezcla.

**Regla común:** siempre se **centra en el centroide**, y se calcula el radio para ajustar la cámara (la distancia del centroide al
origen llega a 226 Å y el radio a 41 Å).

### 7.2 ¿Qué hacemos con datos defectuosos?

*Con los datos actuales (3.5) no hay ni enlaces rotos, ni nombres duplicados, ni átomos sin coordenadas. La política sigue siendo necesaria como defensa ante un fichero corrupto o un cambio futuro del formato, pero **no se espera que se dispare** con el corpus.*

| Caso | Opción estricta | Opción tolerante |
|------|-----------------|------------------|
| Átomo sin coordenadas utilizables | Error de formato | Descartar el átomo y los enlaces que lo usan |
| Enlace con un átomo que no existe | Error de formato | Descartar solo ese enlace |
| Número ilegible (`1.2.3`) | Error de formato | Tratar como «sin coordenadas» |

Propuesta provisional: **tolerante** para enlaces sueltos (la molécula sigue siendo útil), **estricta** si queda **menos de un
átomo**. Hay un coste de la opción tolerante: puede dibujar una molécula incompleta sin avisar; por eso el resultado debería poder
llevar una **lista de avisos** (`warnings`) que se registra y se puede mostrar.

### 7.3 Nombres de átomo repetidos

No esperamos duplicados dentro de un ligando, pero no lo hemos comprobado. Si pasa, un enlace sería ambiguo. Opción provisional:
**gana el primero y se emite un aviso**.

### 7.4 Hidrógenos y orden de enlace

- **Hidrógenos**: el fichero los incluye explícitamente (en ATP, 16 de 47 átomos son hidrógeno, según la página de PDBe). El enunciado
  pide H blanco, así que se muestran; un interruptor «ocultar H» queda como mejora de legibilidad y rendimiento (documento 07).
- **Orden de enlace**: el modelo lo guarda, pero la primera versión dibuja **un cilindro por enlace**, sea simple o doble. Dibujar
  dobles y triples como cilindros paralelos es un bonus posible (documento 09).

---

## 8. Gestión de errores

El enunciado define **cuatro errores de carga**: sin red, 404, fichero corrupto y tiempo agotado. Al parser le toca solo uno: **corrupto**.

```
Resultado del parser = Éxito(Molecule, avisos) | Fallo(motivo)
```

Reglas:

1. **El parser no lanza excepciones por culpa de la entrada hacia fuera.** Su punto de entrada público devuelve `Fallo(motivo)`. Una
   entrada hostil o aleatoria jamás debe cerrar la app. *Internamente* las capas pueden lanzar `CifFormatException` (lleva un
   `CifFailure` tipado) y la puerta de entrada la captura y la convierte en valor. Alternativa descartada: devolver `Result` en
   cada capa; es más puro, pero obliga a comprobar el resultado tras cada token y llena el código de ramas.
2. Aun así, la **frontera** (el sitio que llama al parser) envuelve la llamada y convierte cualquier excepción inesperada en `Fallo`.
   *Cuidado*: en corrutinas no se debe tragar `CancellationException`, hay que relanzarla (documento 06).
3. Los **motivos** son valores de un tipo cerrado (`sealed`), no cadenas: `CuerpoVacío`, `NoEsCif` (por ejemplo, una página HTML con
   estado 200), `SinÁtomos`, `ColumnaObligatoriaAusente(nombre)`, `FilaTruncada`, `CampoSinCerrar`, `CoordenadaInválida`, `DemasiadoGrande`.
   Todos se muestran al usuario como «fichero corrupto» con un texto útil; el motivo concreto va al registro.
4. **Tope de tamaño** antes de tokenizar (por ejemplo, descartar cuerpos de varios MB): protege la memoria frente a respuestas
   inesperadas.
5. El indicador de carga se oculta **siempre** (`finally`): es un requisito del enunciado y se verifica en la capa de red/UI.
6. El parser **se ejecuta fuera del hilo principal** (`Dispatchers.Default`): es CPU, no red.

Los otros tres errores (sin red, 404, tiempo agotado) son de la capa de red y se tratan en el documento 06. Tabla de reparto:

| Situación | Dónde se detecta | Mensaje |
|-----------|------------------|---------|
| Sin conexión | Red (excepción de E/S) | «Sin conexión» |
| 404 | Red (código HTTP) | «Ligando no encontrado» |
| Tiempo agotado | Red (timeout) | «Tiempo de espera agotado» |
| 200 con HTML, vacío, sin átomos, truncado | **Parser** | «Fichero corrupto» |

---

## 9. Trampas y casos límite

| # | Caso | Qué pasa si no se trata | Tratamiento |
|---|------|--------------------------|-------------|
| 1 | Nombre con comilla (`"O5'"`) | Fila desplazada, todos los enlaces siguientes mal | Tokenizador con regla de comillas (2.1). **Afecta a 155 de 1243 ligandos (12 %)** |
| 2 | Campo de texto multilínea (`;`) | Una línea suelta se interpreta como valores | Reconocer `;` solo en columna uno |
| 3 | Un solo átomo (ion) en forma de pares *(la forma exacta de escribirlo: sin verificar)* | «No hay bucle de átomos» → falso error | Tabla de una fila (5.2). **39 ligandos son de un solo átomo** |
| 4 | Sin categoría de enlaces *(si el fichero omite la categoría o la deja vacía: sin verificar)* | Excepción por tabla ausente | Lista vacía de enlaces. **39 ligandos sin enlaces** |
| 5 | Orden de columnas distinto | Coordenadas leídas de otra columna | Buscar por nombre |
| 6 | `CL`, `FE` en mayúsculas | Elemento no encontrado en la tabla CPK | Normalizar: primera mayúscula, resto minúsculas. **Confirmado: todos los símbolos de dos letras vienen en mayúsculas** |
| 7 | Elemento desconocido o ausente | Excepción en `map[element]!!` | Color por defecto, sin `!!` |
| 8 | `?` o `.` en coordenadas | `NumberFormatException` | `toFloatOrNull()` y elección de juego por ligando (7.1). **65 ligandos tienen `?` en alguno de los dos juegos** |
| 9 | `NaN`/`Infinity` en un `Float` válido | Geometría rota en Filament | Exigir `isFinite()` |
| 10 | Enlace a un átomo inexistente | `IndexOutOfBounds` o `null` | Resolver con mapa; política 7.2 |
| 11 | `\r\n` y BOM | Valores con `\r` pegado, no coinciden con nada | `\r` es espacio; descartar BOM |
| 12 | Cuerpo HTML con estado 200 | Parser «tiene éxito» con cero átomos | Detectar y `Fallo(NoEsCif)`; exigir ≥ 1 átomo |
| 13 | Molécula descentrada o muy grande | Cámara apuntando al vacío | Centrar en el centroide y escalar según el radio (documento 07) |
| 14 | `Float` vs texto con coma decimal | Entrada con `,` en lugar de `.` | `toFloatOrNull()` no usa la configuración regional; si falla → política 7.2 |
| 15 | Muchos átomos | Lentitud en 3D | Máximo real: **256 átomos y 256 enlaces** (`CDL`). Medir con él (H6); el parser no es el cuello de botella |
| 16 | `UNL` está en la lista pero no en el diccionario | Fallo al tocarlo en la evaluación | Es el caso «404»; debe mostrar su aviso y quitar el indicador de carga |

---

## 10. Pruebas y plan para conseguir datos reales

### 10.1 Qué se prueba y dónde

- **Tests de JVM** (`app/src/test/java/...`), sin Android: tokenizador, lector de tablas y extractor por separado.
- **Fixtures reales**: ficheros `.cif` en `app/src/test/resources/cif/`, leídos con
  `javaClass.classLoader.getResourceAsStream(...)`.
- **Tests de robustez**: entradas vacías, truncadas en cada posición posible, bytes aleatorios. Criterio: **ninguna excepción**, el
  resultado siempre es `Éxito` o `Fallo`.
- **Corpus completo**: ejecutar el parser contra **todos** los ligandos de `ligands.txt` (descargados una vez). Criterio de
  aceptación: cero excepciones, y cada fallo explicado y revisado.
- **Verificación cruzada con una fuente independiente**: con la copia binaria del diccionario (3.5) se generan ficheros de texto de
  los 1243 ligandos y se comprueba que el parser de Kotlin devuelve **los mismos átomos, enlaces y coordenadas** que la copia binaria.
  Ojo: esos ficheros los serializa nuestro propio script, así que **comprueban la extracción de datos, no la fidelidad de la grafía
  de RCSB**; para esto último se necesitan ficheros descargados de RCSB (`001` ya lo es).

Casos mínimos del test del extractor:

| Entrada | Resultado esperado |
|---------|--------------------|
| Ejemplo de la sección 3 | 3 átomos, 2 enlaces, `Cl` normalizado |
| Un ion como par clave-valor | 1 átomo, 0 enlaces |
| Sin `_chem_comp_bond` | N átomos, 0 enlaces |
| Columnas en distinto orden | Mismo resultado |
| Fila truncada | `Fallo(FilaTruncada)` |
| Enlace a átomo inexistente | Enlace descartado + aviso (política 7.2) |
| Cuerpo HTML | `Fallo(NoEsCif)` |
| Cadena vacía | `Fallo(CuerpoVacío)` |
| Ideales con `?` en **algún** átomo y modelo completa | Se usa el modelo **entero**; nunca se mezclan juegos (política 7.1) |
| Modelo con `?` y ideales completas (caso `15P`) | Se usan las ideales |
| Ninguno de los dos juegos completo | `Fallo` (no ocurre en el corpus actual) |
| Ion de un átomo (`CL`) | 1 átomo, 0 enlaces, elemento `Cl` |
| Nombre `C1'` con comilla simple | El átomo se llama `C1'` y los enlaces lo encuentran |

### 10.2 Cómo conseguir los datos reales

Hay dos caminos. El **lote** de la página de descargas de ligandos de RCSB (`rcsb.org/downloads/ligands`, «Generate File Batches
for Download»), que es el que usó Valera para obtener `001`; **no hemos verificado** cómo nombra los ficheros ni cuántos devuelve por
lote. Y el **script** por ficheros individuales. Las URL `https://files.rcsb.org/ligands/view/{ID}.cif` y
`https://files.rcsb.org/ligands/download/{ID}.cif` figuran en la página oficial de descargas HTTP de RCSB (comprobado el 18/11/2026).
Desde la máquina de desarrollo (el entorno donde se escribe este documento no puede llegar a RCSB):

```bash
mkdir -p ~/ligands-cache && cd ~/ligands-cache
tr -d '\r' < /ruta/a/ligands.txt | while read -r id; do
  [ -z "$id" ] && continue
  if ! curl -sS -f --max-time 20 -o "$id.cif" "https://files.rcsb.org/ligands/view/$id.cif"; then
    echo "$id" >> failures.txt
    rm -f "$id.cif"
  fi
  sleep 0.2          # no martillear el servidor
done
python3 -I cif_stats.py ~/ligands-cache
```

`cif_stats.py` es una **herramienta desechable**, no código del proyecto: un tokenizador de referencia en Python que imprime
estadísticas (átomos y enlaces por ligando, qué columnas aparecen, cuántos ficheros tienen `?`, átomos en forma de pares, ficheros
sin categoría de enlaces, enlaces a átomos inexistentes, nombres con comillas...). Con esas cifras se cierran las políticas de la
sección 7 con datos y se eligen los ficheros de prueba. Además sirve de **segunda implementación** para comparar con la de Kotlin.

Ligandos propuestos como *fixtures* (por diversidad; ver también la salida del script): `ATP` (nucleótido con `O5'` y muchos H), un
ion (`NA`, `CL` o `ZN`), `HEM`, `FAD`/`NAD` (grandes), `SF4` (clúster hierro-azufre), un identificador que empieza por dígito, y el
ligando con más átomos del corpus. **Licencia:** la página de políticas de RCSB indica que los ficheros de datos del archivo PDB y los datos de sus API se publican bajo
**CC0 1.0**; no hemos visto una mención explícita al Diccionario de Componentes Químicos (de donde salen los `.cif` de ligandos), y
la página del CCD de wwPDB no declara licencia. Es muy probable que valga lo mismo, pero **es una inferencia**; con fixtures
sueltos de tamaño pequeño el riesgo es mínimo, y se añade a `fixtures/README` el origen y la fecha de descarga.

---

## 11. Alternativas descartadas

| Alternativa | Por qué no (por ahora) |
|-------------|------------------------|
| **`split` por espacios o expresiones regulares** sobre las líneas | Rompe con comillas y campos multilínea; es el origen típico de los fallos del documento 01 |
| **Librería de terceros** para CIF/mmCIF (existen implementaciones en Java, p. ej. de RCSB o BioJava; *no verificado desde aquí*) | Dependencias pesadas en Android, tamaño de la app, y el enunciado centra el aprendizaje en escribir el parser (D2) |
| **Otro formato** de RCSB (p. ej. `{ID}_ideal.sdf`; la ruta figura en la página de descargas de RCSB) | El enunciado fija `.cif`; además cambiar de formato mueve el problema, no lo elimina |
| **Parser por streaming** (línea a línea, sin cargar todo) | Los ficheros de ligandos son pequeños (decenas de KB, *a confirmar con el corpus*) y las comillas/campos multilínea complican el estado; con tope de tamaño la versión en memoria es más simple |
| **Parser de «solo lo que necesito»** (buscar las líneas que empiezan por el id del ligando) | Frágil: depende del orden y del formato exacto; falla con la forma de pares y los nombres entre comillas |

---

## 12. Qué está verificado y qué no

| Afirmación | Estado | Fuente |
|------------|--------|--------|
| Reglas léxicas de CIF 1.1 (comentarios, comillas, campos `;`, palabras reservadas, mayúsculas) | Verificado | Especificación de la IUCr |
| Lista de columnas de `_chem_comp_atom` (16 en la tabla de wwPDB) | Verificado; **el fichero real trae 18** | Tabla de wwPDB + ligando `001` |
| Valores permitidos de `value_order` | Verificado (en `001` solo `SING`/`DOUB`) | wwPDB + ligando `001` |
| ATP de PDBe: 47 átomos (31 pesados + 16 H), 49 enlaces, con columnas y categorías extra | Verificado (fichero de PDBe, no de RCSB) | Página de PDBe |
| Estructura real de un `.cif` de RCSB: `_chem_comp`, `_chem_comp_atom`, `_chem_comp_bond`, descriptores, identificadores, auditoría | **Verificado en 1 ligando** (`001`: 87 átomos, 90 enlaces, ambas familias de coordenadas sin `?`) | Fichero real |
| Campo de texto `;` multilínea (nombre del ligando) | **Verificado, aparece en el 1.er ligando** | Fichero real |
| Categorías de una fila en forma de pares (`_chem_comp`, `_pdbx_chem_comp_synonyms`) | Verificado | Fichero real |
| En `001` las ideales están en el origen y las de modelo a ~30 Å; **en general no** (ideales hasta 226 Å del origen) | Verificado | Fichero real + corpus |
| Estadísticas de los 1243 ligandos (3.5) | Verificado **sobre una copia binaria** del diccionario, validada contra `001` | `biotite` 2.0.0 (PyPI), cálculo propio |
| `ligands.txt`: 1243 ids únicos, ordenados, solo `[A-Z0-9]`; 42 solo con dígitos; 39 de 1–2 caracteres | Verificado | Fichero adjunto |
| `ligands_temp.txt` = primeras 235 líneas de `ligands.txt` | Verificado (`diff`); su propósito, **no** | Fichero adjunto |
| URL `files.rcsb.org/ligands/view/{ID}.cif` y `/download/{ID}.cif` | Verificado en la página de descargas de RCSB | RCSB |
| Datos de archivo PDB y API de RCSB bajo CC0 1.0 | Verificado; **que cubra los `.cif` del CCD, no** | Políticas de RCSB |
| `?` = desconocido y `.` = no aplicable | Conocimiento general, **sin leer la página de la IUCr** | — |
| `_chem_comp_atom` de un ion de un átomo en forma de pares clave-valor | **No verificado** | Inferencia del formato |
| Ligandos sin categoría `_chem_comp_bond` | **No verificado** | Inferencia |
| Nombres con comilla simple en 155 ligandos; en texto van entre comillas dobles | Contenido verificado en el corpus; **la grafía en texto, no** (se asume `"O5'"`) | Corpus binario |
| Tamaño máximo de un ligando: 256 átomos; tamaño máximo del **fichero** en bytes | Átomos verificado; **bytes no** | Corpus binario |
| `UNK` 16 átomos; `UNX` 1 átomo de elemento `X`; `UNL` **no está** en el diccionario | Verificado en el corpus; respuesta real del servidor para `UNL`: **no** | Corpus binario |
| Cómo nombra y empaqueta los ficheros el «lote» de RCSB | **No verificado** | — |
| Existencia de librerías Java para CIF | **No verificado** | Memoria |

Lo que sigue «No verificado» depende del **texto** de los ficheros (formato de iones, categorías ausentes, respuesta del servidor) y se cierra con una descarga real de la lista (sección 10.2).

---

## 13. Qué recordar

1. **CIF no es «líneas separadas por espacios»**: tiene comillas, campos multilínea y comentarios. El tokenizador sigue las reglas
   de la sección 2.1.
2. Las filas de un `loop_` se delimitan **contando columnas**, no por saltos de línea: un valor mal leído desplaza todo lo demás.
3. Las columnas se buscan **por nombre**, nunca por posición.
4. Una categoría puede venir en forma de **bucle** o de **pares**. El lector de tablas las unifica.
5. Sin enlaces no es un error; sin átomos sí.
6. Los enlaces del modelo guardan **índices**; los nombres solo sirven para resolverlos.
7. El parser **nunca lanza excepciones por la entrada**: devuelve éxito o fallo con motivo.
8. Las políticas de la sección 7 se cierran **con datos reales**, no con suposiciones.
9. El parser vive sin `android.*` para poder probarlo en JVM.
