# 24 · Comparar dos moléculas

> **Estado:** **escrito**. Reglas puras **probadas en la JVM** (618 pruebas en total; 10 nuevas, 1 ampliada; 16 mutantes, todos muertos al final). La masa se comparó con la del **Chemical Component Dictionary** en los **1241 ligandos con átomos: todos dentro de 0,06 g/mol**. La pantalla, el selector y las dos escenas **compilan contra *stubs*** y pasan las reglas de Compose; **sin probar en el móvil** (§9).

## Índice

1. [Qué pide el enunciado](#1-qué-pide-el-enunciado)
2. [El recorrido: CMP, elegir, cargar, comparar](#2-el-recorrido-cmp-elegir-cargar-comparar)
3. [Los números: qué se compara y cómo se cuenta](#3-los-números-qué-se-compara-y-cómo-se-cuenta)
4. [Las masas: una tabla comprobada contra el CCD](#4-las-masas-una-tabla-comprobada-contra-el-ccd)
5. [La pantalla CONCORDANCE](#5-la-pantalla-concordance)
6. [Dos escenas 3D a la vez](#6-dos-escenas-3d-a-la-vez)
7. [SPIN: las dos giran juntas](#7-spin-las-dos-giran-juntas)
8. [Pruebas y mutación](#8-pruebas-y-mutación)
9. [Cómo probarlo en el móvil](#9-cómo-probarlo-en-el-móvil)
10. [Trampas](#10-trampas)
11. [Alternativas](#11-alternativas)
12. [Decisiones](#12-decisiones)
13. [Qué recordar](#13-qué-recordar)

---

## 1. Qué pide el enunciado

VII.5 *Extended Sharing and Export* termina con una **«comparison view of two molecules»**. Era lo último que faltaba de los bonus. El enunciado no dice qué comparar, así que la vista ofrece las dos cosas que tiene sentido mirar:

- **la forma**: las dos moléculas en 3D, lado a lado, que se pueden girar y que pueden **girar juntas**;
- **los números**: fórmula, masa, átomos, átomos pesados, enlaces, anillos, enlaces aromáticos, carga, tamaño, y la cuenta de cada elemento.

## 2. El recorrido: CMP, elegir, cargar, comparar

```
molécula ATP ── CMP ──> selector «Compare ATP with…» ── CFF ──> carga de CFF ──> CONCORDANCE ATP :: CFF
                                                                (los mismos diálogos)         │
                                         molécula ATP <───────────── Atrás ───────────────────┘
```

1. **CMP**, en el panel AUSPEX READOUT, junto a la estrella. Está desactivado mientras la escena se ensambla: comparar media molécula no compara nada.
2. **El selector** (`ComparePicker`): el **mismo buscador** que el índice (por ID, con el cursor de consola, documento 23) y celdas cortas: «> CFF  C₈H₁₀N₄O₂  ARCHIVED». La molécula abierta no aparece.
3. **La carga** de la segunda va por un **segundo `LigandLoader`** en el ViewModel: el mismo repositorio, así que la **misma caché** (sin conexión, si ya estaba en el móvil), el mismo progreso por etapas y los mismos errores. Se ven los **mismos diálogos** de carga y error que en el índice, sobre la molécula.
4. **La pantalla** cambia con el tubo CRT (documento 23).
5. **Atrás** (tecla o sistema) cierra la comparación y vuelve a la primera molécula. Cerrar la primera cierra también la comparación.

### 2.1 Una pantalla más en la función pura

`Navigation.screenOf` recibe ahora también el estado de la segunda molécula:

```
5. la comparación, si la molécula Y la segunda están cargadas;
6. la molécula, si está cargada;
...
```

- `Screen.Compare(first, second)` **lleva sus dos moléculas**, por la misma razón que `Screen.Molecule` lleva la suya: mientras el tubo se apaga, la pantalla vieja se sigue dibujando con **sus** datos.
- Mientras la segunda carga o falla, se queda la **molécula** (con los diálogos encima).
- Una segunda molécula **sola** (la primera ya cerrada) no es una comparación: índice.
- Bloqueado, **nunca** (D43): el acceso va primero, como siempre.

**Precio:** al volver de la comparación, la pantalla de la molécula se **construye de nuevo**: la cámara vuelve a su vista inicial y se pierden la selección y el modelo elegido allí. Se guarda en la historia de decisiones; mantenerlas exigiría guardar el estado de la pantalla fuera de ella (`SaveableStateHolder`), un trabajo que no aporta a la defensa.

## 3. Los números: qué se compara y cómo se cuenta

`Comparison` es puro y **lo cuenta todo de los átomos y enlaces** que se dibujan, nunca de otra parte. La tabla no puede contradecir a la vista 3D (la misma regla que la fórmula, documento 15).

| Línea | Cómo se cuenta | ATP (001 en la prueba) |
|-------|----------------|------------------------|
| **Formula** | `MolecularFormula` (Hill), con subíndices | C₁₀H₁₆N₅O₁₃P₃ |
| **Mass** | Suma de los pesos atómicos (§4), **una** cifra decimal | 507,2 g/mol |
| **Atoms** | Todos, hidrógenos incluidos | 47 |
| **Heavy atoms** | Los que no son H: el «tamaño» que citan los químicos | 31 |
| **Bonds** | Uno por línea del fichero (un doble es **un** enlace) | 49 |
| **Rings** | **Número ciclomático**: enlaces − átomos + piezas | 3 |
| **Aromatic bonds** | `value_order` = AROM | según el fichero |
| **Charge** | Suma de las cargas formales, siempre con signo («+1», «−2», «0») | |
| **Span** | La **mayor distancia** entre dos átomos, en Å | |

### 3.1 Los anillos sin buscar anillos

Un grafo **conexo** con V vértices necesita **V − 1** aristas para estar unido (un árbol). Cada arista de más **cierra un ciclo**. Así que el número de anillos independientes es **E − V + 1**, y con varias piezas, **E − V + C**:

```
ATP:       49 enlaces − 47 átomos + 1 pieza = 3   (dos en la adenina, uno en la ribosa)
cafeína:   25 − 24 + 1 = 2
```

Las piezas se cuentan con **union-find** (unir conjuntos): cada enlace une los conjuntos de sus dos átomos, y cada unión real resta una pieza. Lleva **compresión de caminos** (cada búsqueda deja a los nodos apuntando directamente a la raíz), así que es prácticamente lineal.

**Lo que no es:** el número ciclomático cuenta anillos **independientes** (la «base» de ciclos). El naftaleno da **2**, no 3, aunque el contorno exterior también sea un ciclo. Es la convención química habitual (SSSR, *smallest set of smallest rings*, tiene el mismo tamaño).

En el corpus: **ninguno** de los 1242 ligandos tiene más de una pieza, y el que más anillos tiene es **10R, con 19**.

### 3.2 Quién es «mayor»

Cada línea dice cuál de las dos tiene más (−1 la primera, 1 la segunda, 0 iguales). La pantalla pinta de **oro** el valor mayor, de hueso el menor y de **ceniza** los dos si son iguales. El color **nunca** va solo: los números están escritos. La masa y el tamaño se comparan **tal como se muestran** (con un decimal): dos masas que se escriben igual son «iguales».

### 3.3 El parecido de composición

```
match = Σ min(nA, nB) / Σ max(nA, nB)      (por elemento)
ATP C10H16N5O13P3 vs cafeína C8H10N4O2:  (8+10+4+2+0) / (10+16+5+13+3) = 24/47 = 51 %
```

Es el índice de **Tanimoto (Jaccard) de dos multiconjuntos**: 100 % con la misma fórmula, 0 % sin ningún elemento en común. **Solo habla de la composición, no de la forma**: dos isómeros (los mismos átomos, otros enlaces) dan 100 %. Lo dice el código y lo dice este documento; un parecido **estructural** de verdad usaría huellas moleculares (§11).

## 4. Las masas: una tabla comprobada contra el CCD

`AtomicMass`: los **pesos atómicos estándar de la IUPAC** (abreviados a 5 cifras; el valor convencional para los que la IUPAC da como intervalo: H 1,008, C 12,011…) de los **50 elementos** del catálogo.

- **Elemento desconocido** (UNX trae un «X»): la masa es **nula** y se muestra «?». Una masa que se saltara un átomo sería un número falso con aspecto de verdadero.
- **La comprobación (22-11-2026):** cada ligando del catálogo, con nuestra tabla, contra su propio `_chem_comp.formula_weight` del CCD (la copia del CCD que trae biotite 2.0.0):

```
comparados 1241 · peor diferencia 0,060 g/mol (MO7) · sin masa: UNX · sin leer: 0
```

- Las diferencias vienen de **valores antiguos** de la IUPAC en el CCD (Zn 65,409 en vez de 65,38; Mo 95,94 en vez de 95,95; S 32,065). Por eso se muestra **un** decimal: con uno, la app y el CCD coinciden en todo el catálogo.
- La comprobación es **también una prueba**: `ccd_formula_weight.tsv` (16 KB, en los recursos de prueba) guarda el peso del CCD de cada ligando, y `everyLigandWeighsWhatTheDictionarySays` lo compara con la masa de la fórmula del índice. Así queda fijado **cada** elemento de la tabla: un peso mal escrito mueve la masa de los ligandos que lo contienen. Al principio un mutante (Zn 66,38) **sobrevivía**, porque solo se probaban ATP y la cafeína; esta prueba lo mata.

## 5. La pantalla CONCORDANCE

```
┌ CONCORDANCE // TWO PATTERNS ───────────────────┐
│ <  ATP :: CFF                       [H] [SPIN] │
│    BALL AND STICK                              │
└────────────────────────────────────────────────┘
┌ ATP ──────────────┐  ┌ CFF ──────────────┐
│                   │  │                   │
│       3D          │  │       3D          │
│                   │  │                   │
│ C₁₀H₁₆N₅O₁₃P₃ 60 FPS│  │ C₈H₁₀N₄O₂     IDLE │
└───────────────────┘  └───────────────────┘
┌ CONCORDANCE // NUMBERS ─────────────────────────┐
│ Composition match 51 %                          │
│              ATP              CFF               │
│ Mass         507.2 g/mol      194.2 g/mol       │
│ ...                                             │
│ ● C          10               8                 │
│ ● H          16               10                │
└─────────────────────────────────────────────────┘
```

- **Las escenas, lado a lado también en vertical**: izquierda y derecha coinciden con las columnas de la tabla. Apiladas habría que pensar «la de arriba es la de la izquierda».
- **Horizontal**: las escenas a la izquierda y la tabla en una columna a la derecha que se desplaza entera. La tabla **no** se desplaza por su cuenta: un desplazable dentro de otro recibe una altura infinita y Compose lo rechaza.
- **La orientación** sale de la **configuración** (`LocalConfiguration`), no de un `BoxWithConstraints`: las escenas deben quedar **fuera** de uno (documento 21, §20).
- **H** muestra u oculta los hidrógenos **de las dos**; **SPIN** las hace girar juntas (§7). El modelo es el de los ajustes.
- **Cada elemento de la tabla es una tecla** de 48 dp: lo ilumina con el halo dorado **en las dos escenas** a la vez. Con H apagado, la tecla de H se desactiva (no se puede iluminar lo que no se dibuja).
- **Tocar un átomo no hace nada** aquí: es una vista para comparar, no para inspeccionar. Arrastrar y pellizcar siguen girando y acercando cada escena por separado.
- TalkBack: cada línea se lee junta («Mass, 507.2 g/mol, 194.2 g/mol») y cada elemento con su nombre («Carbon: 10 and 8»).

## 6. Dos escenas 3D a la vez

Cada escena es un `MoleculeScene` completo, con **su propio motor** Filament (`rememberEngine`) y su propio SurfaceView:

- **En reposo, las dos están en IDLE**: cada una dibuja solo cuando algo cambia (documento 12). Dos escenas quietas no cuestan nada.
- **Dos contadores de FPS**, uno por escena: se ve lo que cuestan las dos mientras se **ensamblan** o **giran** (bonus VII.4).
- **Las lambdas que se pasan a las escenas se crean una vez** (`remember`). Una lambda nueva en cada recomposición sería un parámetro «cambiado», la escena se volvería a ejecutar y pediría fotogramas: es la misma trampa del documento 21, §20.
- **Cada ventana mide su tamaño** (`onSizeChanged`) antes de crear su escena: la cámara necesita su forma para que la molécula quepa entera (documento 21, §18).

**Riesgo sin probar:** dos motores Filament y dos SurfaceView a la vez. Filament y Android lo admiten; SceneView 4.69.4 dice que el motor se puede compartir (§11), pero lo habitual en sus ejemplos es uno por vista. Si en el móvil faltara memoria o fallara algo, compartir un motor es el primer cambio.

## 7. SPIN: las dos giran juntas

Reutiliza la **plataforma giratoria** del vídeo (documento 22): todo lo de la molécula cuelga de un nodo que gira sobre el eje vertical. Ahora `MoleculeScene` acepta `spinSince`, el **instante** en que empezó el giro:

```
ángulo = 360° × ((tiempo del fotograma − spinSince) mod 12 s) / 12 s       (Turntable.loopAngle)
```

- **Las dos escenas reciben el mismo instante**, y el tiempo del fotograma es **el mismo** para las dos en cada fotograma (el reloj de Compose, `withFrameNanos`, sobre `System.nanoTime`). Así que tienen **exactamente el mismo ángulo** en cada fotograma, sin comunicarse.
- `Math.floorMod` y no `%`: un fotograma con fecha **un poco anterior** al inicio daría un resto negativo y un ángulo negativo. Con `floorMod` da casi 360°, y el giro sigue sin salto (un mutante con `%` muere).
- **12 s por vuelta**, el doble que el vídeo: se están comparando dos formas.
- Se guarda al girar el móvil (`rememberSaveable`): el mismo instante, así que el giro **sigue** donde iba.
- **Lo que no sincroniza:** la **cámara**. Si el usuario gira una escena a mano, las dos dejan de mirar desde el mismo lado. Copiar la pose de una cámara a la otra en cada fotograma es posible (§11), pero pelearse con dos manipuladores sin probar en el móvil era demasiado riesgo para el último bonus.

## 8. Pruebas y mutación

| Fichero | Nuevas | Qué fija |
|---------|--------|----------|
| `ComparisonTest` | 8 | Anillos = enlaces − átomos + piezas (agua 0, triángulo 1, sal 2 piezas); la tabla con sus 9 líneas en orden y quién es mayor (también al revés); cargas con signo, el tamaño mide **todos** los pares; elementos en orden Hill con 0 donde faltan; el parecido de composición (100, 0, 67 %, simétrico); el 001 real es una pieza con la masa del CCD que trae su propio fichero (624,715); masas conocidas (ATP 507,18, cafeína 194,19), «X» sin masa; **los 1241 ligandos pesan lo que dice el CCD** |
| `NavigationTest` | 1 (+1 ampliada) | La comparación solo con las dos cargadas; mientras carga, la molécula; sola, el índice; bloqueado, el acceso; claves únicas |
| `TurntableTest` | 1 | El giro sin fin: 90° a un cuarto, vuelve a 0 tras la vuelta, nunca negativo, más lento que el vídeo |
| `OnboardingTest` | (ampliada) | El tutorial nombra también **CMP** |
| `ListTextsTest` | (ampliada) | El selector dice «Compare» a TalkBack |

**Mutantes (16):** anillos sin piezas, union-find que no une, átomos pesados = todos, «mayor» al revés, parecido con el máximo, sin orden Hill, carga sin signo, masa que se salta lo desconocido, giro con `%`, comparación con la pantalla bloqueada, dos pesos mal escritos (Zn, Se): **muertos**. Tres **sobrevivieron** al principio y obligaron a mejorar las pruebas: el tamaño que solo miraba pares vecinos (nueva prueba con el par más lejano separado), el peso del Zn (nueva prueba contra el CCD entero) y un mutante de navegación mal escrito (no compilaba: se rehízo).

**Sin prueba automática:** las dos escenas a la vez, el giro sincronizado en pantalla, el selector y los diálogos de la segunda carga. Ver §9.

## 9. Cómo probarlo en el móvil

1. ATP → **CMP** (desactivado mientras se ensambla) → buscar «CFF» → tocar. Diálogo de carga (o nada, si estaba en el móvil) y el tubo cambia a **CONCORDANCE ATP :: CFF**.
2. Las dos escenas se ensamblan; sus FPS suben y luego bajan a **IDLE** las dos.
3. **SPIN**: las dos giran **al mismo ritmo y con el mismo ángulo**. Los FPS de las dos suben. SPIN otra vez: se paran y vuelven a IDLE.
4. Girar el móvil con SPIN encendido: siguen girando, en horizontal la tabla a la derecha.
5. **H**: desaparecen los hidrógenos de las dos; la tecla de H de la tabla se apaga.
6. Tocar **C** en la tabla: los carbonos de las dos escenas, con halo dorado.
7. Comprobar la tabla: ATP 507,2 g/mol, 3 anillos; CFF 194,2 g/mol, 2 anillos; «Composition match 51 %».
8. **Atrás**: vuelve a ATP (con la vista inicial). Atrás otra vez: el índice.
9. Sin conexión, CMP con un ligando **no** guardado: el error de siempre, sobre ATP.
10. Dos ligandos **grandes** (15P y otro): ¿cuánto bajan los FPS con dos escenas girando?

## 10. Trampas

1. **Un desplazable dentro de otro desplazable**: altura infinita, Compose lo rechaza. En horizontal la tabla no se desplaza por sí misma.
2. **`BoxWithConstraints` alrededor de escenas 3D**: las vuelve a ejecutar en cada medida (documento 21, §20).
3. **Lambdas nuevas en cada recomposición** pasadas a una escena: parámetro «cambiado», fotogramas.
4. **`%` con tiempos negativos**: ángulo negativo. `floorMod`.
5. **Una masa que ignora un elemento desconocido**: un número falso. Mejor «?».
6. **Probar una tabla con dos ejemplos**: un peso mal escrito de un elemento raro pasa. Una referencia **independiente** y completa (el CCD) lo atrapa.
7. **Contar anillos buscando ciclos**: complicado y fácil de equivocar. El número ciclomático sale de tres cuentas.
8. **Llamar «parecido» a la composición**: dos isómeros son 100 %. Hay que decir qué mide.

## 11. Alternativas

| Alternativa | Por qué no (ahora) |
|-------------|--------------------|
| **Superponer** las dos moléculas en una escena (alineadas por RMSD, algoritmo de Kabsch) | Lo que haría un químico para formas parecidas; exige emparejar átomos de dos ligandos distintos, que en general no tienen correspondencia |
| **Parecido estructural** con huellas moleculares (Morgan/ECFP + Tanimoto) | La medida estándar; necesita percepción de anillos y aromaticidad tipo RDKit, una biblioteca entera |
| **Cámaras sincronizadas** (copiar la pose de una a otra en cada fotograma) | Pelearse con dos manipuladores de SceneView sin probar en el móvil; SPIN da la comparación de formas sin ese riesgo |
| **Un solo motor Filament** compartido por las dos escenas | Menos memoria y un solo hilo de Filament; SceneView lo admite (`engine` es un parámetro). Primer cambio si el móvil sufre |
| Escenas **apiladas** en vertical | Más ancho para cada una, pero izquierda/derecha ya no coincide con las columnas de la tabla |
| Comparar **más de dos** | El enunciado dice dos; la tabla de dos columnas es legible en un móvil |
| Guardar el estado de la molécula al volver (`SaveableStateHolder`) | Trabajo sin valor para la defensa (§2.1) |
| Elegir la segunda molécula **desde el índice** (selección múltiple) | Dos gestos nuevos en la lista; CMP desde la molécula es un recorrido natural: «esta, ¿comparada con cuál?» |

## 12. Decisiones

| # | Decisión | Estado | Motivo |
|---|----------|--------|--------|
| D87 | **Comparación de dos moléculas** (`CompareScreen`): CMP en la molécula, selector con el buscador del índice, segunda carga con su propio `LigandLoader`, dos escenas lado a lado con su FPS, H y SPIN para las dos, tabla de números contada de los átomos (fórmula, masa, átomos, pesados, enlaces, anillos, aromáticos, carga, tamaño, elementos) y parecido de composición | Decidida (sin probar en el móvil) | VII.5 «comparison view» |
| D88 | **Masa** con los pesos estándar de la IUPAC de los 50 elementos, comprobada contra el `formula_weight` del CCD en los 1241 ligandos (≤ 0,06 g/mol), mostrada con **un** decimal; «?» con un elemento desconocido | Decidida | La comparación necesita la masa; la referencia es independiente |
| D89 | **SPIN sincronizado** por un instante común (`spinSince`) y el tiempo del fotograma, no copiando cámaras | Decidida (sin probar en el móvil) | El mismo ángulo en cada fotograma sin comunicación entre escenas |

## 13. Qué recordar

- Una pantalla nueva es **una línea más** en una función pura (`screenOf`), y una prueba.
- **Reutilizar** el cargador, el buscador, los diálogos, la escena y la plataforma giratoria: la comparación es sobre todo **pegamento**.
- **Contar** en vez de buscar: anillos = E − V + C.
- Una tabla de datos se prueba contra **otra fuente independiente y completa**, no contra dos ejemplos.
- Dos cosas animadas que deben ir **a la par**: el mismo instante de inicio y el mismo reloj, nada más.
