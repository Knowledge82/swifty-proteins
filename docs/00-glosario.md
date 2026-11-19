# 00 · Glosario

> Estado: **vivo**. Se amplía cuando aparece un término nuevo en los documentos. Cada entrada indica dónde se desarrolla.
> Lo que no está verificado en esta sesión se marca como tal. Redactado el 18/11/2026.

## Índice

1. [Datos y organizaciones](#1-datos-y-organizaciones)
2. [Formato del fichero](#2-formato-del-fichero)
3. [Química y geometría](#3-química-y-geometría)
4. [Términos del 3D y del spike](#4-términos-del-3d-y-del-spike)
5. [Tecnologías del proyecto y términos de arquitectura](#5-tecnologías-del-proyecto-y-términos-de-arquitectura)
6. [Herramientas usadas solo para analizar](#6-herramientas-usadas-solo-para-analizar)

---

## 1. Datos y organizaciones

| Término | Qué es |
|---------|--------|
| **PDB** (*Protein Data Bank*) | Archivo mundial de estructuras 3D de proteínas, ácidos nucleicos y complejos. Nuestros ligandos son pequeñas moléculas que aparecen dentro de esas estructuras. |
| **wwPDB** (*worldwide PDB*) | Consorcio que **gestiona** el archivo PDB y define sus formatos. Lo forman, que sepamos, RCSB PDB (EE. UU.), PDBe (Europa), PDBj (Japón) y BMRB (RMN). *(Composición recordada de memoria, no releída aquí.)* |
| **RCSB PDB** | Miembro estadounidense del wwPDB y **el sitio del que descarga nuestra app**: `files.rcsb.org/ligands/view/{ID}.cif`. Es el host de ficheros; la web con buscador es `rcsb.org`. |
| **PDBe** (*PDB in Europe*) | Miembro europeo (EMBL-EBI). Publica sus propias versiones enriquecidas de los ficheros de ligandos (con columnas extra). Lo usamos solo como referencia en el documento 03; la app no lo usa. |
| **CCD** (*Chemical Component Dictionary*) | Diccionario con la definición de **cada** componente químico (ligando) del PDB: átomos, enlaces, coordenadas. Cada `.cif` de ligando es una entrada del CCD. |
| **Ligando** | Molécula pequeña (ion, azúcar, fármaco, cofactor...) asociada a una proteína. Se identifica con un código de 1 a 3 caracteres (`ATP`, `HEM`, `CL`, `001`). |
| **`ligands.txt`** | Lista de 1243 ids que acompaña al enunciado. Es lo que ve el usuario en la pantalla de lista. |

Se desarrolla en: documento 03, sección 3.4 y 3.5.

## 2. Formato del fichero

| Término | Qué es |
|---------|--------|
| **CIF** / **mmCIF** | Formato de texto de datos científicos (CIF) y su variante para macromoléculas (mmCIF/PDBx). Es el formato de nuestros ficheros. Detalle en el documento 03, sección 2. |
| **BinaryCIF** | Versión binaria y comprimida del mismo modelo de datos. **La app no la usa**; se menciona porque el análisis del corpus completo (documento 03, 3.5) se hizo sobre una copia binaria del CCD. |
| **Categoría** | Grupo de datos con un prefijo, p. ej. `_chem_comp_atom` (los átomos) o `_chem_comp_bond` (los enlaces). |
| **Tag** (etiqueta) | Nombre de una columna, p. ej. `_chem_comp_atom.atom_id`. |
| **`loop_`** | Palabra que abre una **tabla** de varias filas. Las filas no se separan por saltos de línea: se reconocen **contando columnas**. |
| **Token / tokenizador** | Token = pieza del texto (palabra, valor entre comillas, etiqueta...). Tokenizador = el código que corta el texto en tokens. Código en `CifTokenizer.kt`. |

## 3. Química y geometría

### 3.1 Coordenadas «de modelo» e «ideales»

Cada átomo trae **dos juegos** de posiciones x, y, z, en ångström (1 Å = 0,1 nm):

| Juego | Columnas | Qué es |
|-------|----------|--------|
| **Modelo** (*model*) | `model_Cartn_x/y/z` | Posiciones del ligando **en una estructura experimental concreta** (medida, por ejemplo, por cristalografía) que sirvió de referencia. En el fichero real `001` el campo `pdbx_model_coordinates_db_code` apunta a la entrada `1J4R`. Están expresadas en el sistema de coordenadas de **esa** estructura, por eso en `001` el centro de la molécula queda a unos 30 Å del origen. |
| **Ideales** (*ideal*) | `pdbx_model_Cartn_x/y/z_ideal` | Posiciones **calculadas** por software para una geometría «ideal» de la molécula, no medidas. Según la descripción general de wwPDB; *no hemos releído la fuente en esta sesión*. Suelen estar cerca del origen, pero **no siempre** (en el corpus hay ligandos a más de 200 Å). |

Ambos juegos son válidos y dan longitudes de enlace razonables, pero **están en sistemas de referencia distintos y no se pueden mezclar** dentro de una misma molécula. Algunos ligandos tienen `?` (dato ausente) en uno de los dos. La regla del proyecto (D10) es elegir **un juego completo por ligando** y centrarlo. Se desarrolla en el documento 03, secciones 3.4, 3.5 y 7.1.

### 3.2 Orden de enlace (`value_order`)

El **orden** de un enlace es cuántos pares de electrones comparten los dos átomos:

| Valor en el fichero | Significado | Ejemplo | Cuántos hay en `ligands.txt` |
|---------------------|-------------|---------|------------------------------|
| `SING` | enlace **sencillo** (1 par) | C–C, C–H | 48 437 |
| `DOUB` | enlace **doble** (2 pares) | C=O | 6 061 |
| `TRIP` | enlace **triple** (3 pares) | C≡N | 29 |
| `AROM` | enlace **aromático** (electrones repartidos por un anillo, como el benceno) | — | **0**: no aparece en ninguno de los 1243 |

Los anillos aromáticos **sí** aparecen, pero escritos de la forma clásica de Kekulé (enlaces sencillos y dobles alternados) y con una columna aparte, `pdbx_aromatic_flag = Y`. Por eso `AROM` no sale en `value_order`. El modelo del proyecto guarda el orden (`BondOrder`), pero la primera versión dibuja **un cilindro por enlace**, sea del orden que sea; dibujar dobles y triples como cilindros paralelos es un bonus posible.

### 3.3 Otros

| Término | Qué es |
|---------|--------|
| **CPK** | Esquema de colores por elemento (Corey-Pauling-Koltun): C gris o negro, H blanco, O rojo, N azul, S amarillo, P naranja... |
| **Ball-and-stick** (bolas y varillas) | Representación: una **esfera** por átomo y un **cilindro** por enlace. |
| **Centroide** | Punto medio de las posiciones de todos los átomos. Se resta para que la molécula quede centrada en el origen y la cámara gire alrededor de ella. |
| **Elemento** | Símbolo químico del átomo. El fichero lo escribe en mayúsculas (`CL`, `FE`); la app lo normaliza a `Cl`, `Fe`. |
| **Ion** | Átomo suelto con carga (`CL`, `ZN`, `NA`). En `ligands.txt` hay **39 de un solo átomo y sin enlaces**. |

## 4. Términos del 3D y del spike

| Término | Qué es | Dónde se desarrolla |
|---------|--------|---------------------|
| **Nodo** (*node*) | Objeto de la escena 3D de SceneView (una esfera, un cilindro o un contenedor). Puede tener nodos hijos. | Documento 02a |
| **Nodo pivote** (*pivot node*) | Nodo **vacío** colocado en el **punto medio de un enlace**, que gira alrededor del eje Y (`spin`). Dentro lleva el cilindro del enlace, inclinado alrededor de Z (`tilt`). Dos giros encadenados orientan el cilindro en **cualquier** dirección. Es como una bisagra: el pivote gira y el cilindro hijo se inclina dentro de él. Hizo falta porque colocar el giro directamente en el cilindro no funcionaba (SceneView lo sobrescribía). | Documento 02a, 9.10 y 9.11 |
| **Picking** | Averiguar qué objeto 3D hay bajo el dedo: se lanza un rayo desde el toque y se mira qué toca. | Documento 02b |
| **Collider / forma de colisión** | Forma invisible que se usa para ese rayo. Por defecto es una caja; la sustituimos por una esfera (más precisa) en D8. | Documento 02b |

## 5. Tecnologías del proyecto y términos de arquitectura

| Término | Qué es | En el proyecto |
|---------|--------|----------------|
| **Jetpack Compose** | Herramienta de Google para construir interfaces de Android **de forma declarativa**, en Kotlin. En vez de crear vistas y modificarlas a mano, se escriben funciones `@Composable` que **describen** la pantalla a partir de un *estado*; cuando el estado cambia, Compose vuelve a dibujar solo lo afectado (*recomposición*). Alternativa clásica: vistas XML («Views»). | D3. Compose BOM 2026.09.00 (Compose 1.12.1). El login, la lista, los avisos de error y la integración con la escena 3D se escriben con él. |
| **SceneView** | Librería de 3D (y realidad aumentada) para Android, construida sobre Filament, con una API pensada para Compose: la escena se declara como elementos (`SceneView { SphereNode(...) }`). Ahorra el código repetitivo de usar Filament «a pelo». | D4. Versión 4.69.4. Se leyó su código fuente en el spike. Documentos 02, 02a y 02b. |
| **Filament** | Motor de renderizado 3D de Google para Android y otras plataformas (el que realmente dibuja). SceneView es una capa por encima. *Que por debajo use OpenGL ES o Vulkan es lo que recordamos de su documentación; no se ha releído en esta sesión.* | Aparece de forma explícita entre las opciones del enunciado. Documento 02, sección 1. |
| **OpenGL ES** | API gráfica estándar para móviles (versión reducida de OpenGL). Se programa la GPU con *shaders* y se dibuja con listas de vértices. Muy potente y de **bajo nivel**: hay que escribir las mallas, las luces y la cámara. | Plan B del spike (no usado, porque SceneView pasó). Documento 02, sección 6. |
| **`GLSurfaceView`** | Vista clásica de Android que gestiona el contexto de OpenGL ES y un hilo de dibujo. Es la base de dibujar con OpenGL ES. Riesgo documentado: al pasar la app a segundo plano **el contexto de OpenGL puede perderse** y hay que recrear los recursos. | Plan B del spike. Documento 02, sección 6. |
| **ADR** (*Architecture Decision Record*) | Nota breve que registra **una decisión técnica**: qué se decidió, qué alternativas había, por qué y qué consecuencias tiene. Sirve para no olvidar el motivo y poder revisarlo con datos. | ADR-001 (capa 3D) en el documento 02a, sección 7. El documento 04 reunirá todos. |
| **Spike** | Experimento corto, con tiempo limitado, para **reducir una incertidumbre** (aquí: ¿SceneView sirve?). Su código es **provisional**: se aprovecha solo lo que quedó comprobado y se anota qué es temporal. | Documento 02, secciones 1 y 10. |

## 6. Herramientas usadas solo para analizar

| Término | Qué es |
|---------|--------|
| **PyPI** (*Python Package Index*) | Repositorio público de paquetes de Python (el que usa `pip install`). **No forma parte del proyecto.** Se mencionó porque el entorno de análisis no llega a RCSB, wwPDB ni PDBe, pero sí a PyPI; de ahí se descargó el paquete `biotite`, que incluye una copia binaria del CCD, para calcular las estadísticas del documento 03, 3.5. |
| **`cif_stats.py`** | Script desechable de Python que analiza ficheros `.cif`. No es código del proyecto. |
| **Prueba de mutación (manual)** | Estropear el código a propósito (p. ej. cambiar un `<=` por `true`) y comprobar que alguna prueba se pone en rojo. Si ninguna falla, esa parte del código no está protegida. |
| **Red de seguridad (`catch (Exception)`)** | Captura final en `CifParser.parse` que convierte cualquier excepción inesperada en un `Failure(Unexpected)` para que la aplicación no se cierre. Marca un fallo del parser, no del fichero. |
| **Enlace creíble / juego de coordenadas creíble** | Juego de coordenadas cuyo enlace más largo no pasa de 5 Å (D13). Un juego puede estar completo y aun así ser absurdo. |
| **Política estricta** | Ante un dato dudoso, fallar con un motivo en lugar de adivinar o descartar en silencio (D12). |
