# 07 · La escena 3D de la molécula: bolas y varillas, colores, cámara y toque (código línea a línea)

> Estado: **PARTE PURA ESCRITA Y PROBADA EN LA JVM; PARTE DE ANDROID SIN COMPILAR.** Los colores, los radios, la geometría de los enlaces y el encuadre de la cámara (`scene/`, 3 ficheros) tienen **27 pruebas** y **mutación**
> (todas las averías detectadas salvo una equivalente, sección 14). La escena en sí (`MoleculeScene`, `MoleculeScreen`) usa **las mismas llamadas de SceneView que el spike** (comprobadas en el A33), pero **no se ha compilado ni probado
> en el móvil**; se ha comprobado de tipos contra piezas de relleno escritas de memoria (como en el documento 06c).
> Redactado el 20/11/2026. Viene después de [`06c`](06c-pegamento-android-lista-y-carga.md): **sustituye** a la pantalla provisional con la tabla de átomos. Reutiliza el trabajo del spike
> ([`02-spike-3d.md`](02-spike-3d.md), [`02b-spike-seleccion-atomos.md`](02b-spike-seleccion-atomos.md)) y el modelo `Molecule` ([`03a`](03a-codigo-parser.md)).

## Índice

1. [Qué pide el enunciado y qué cubre esta etapa](#1-qué-pide-el-enunciado-y-qué-cubre-esta-etapa)
2. [Mapa: qué fichero hace qué](#2-mapa-qué-fichero-hace-qué)
3. [Teoría: de una lista de átomos a una imagen](#3-teoría-de-una-lista-de-átomos-a-una-imagen)
4. [Lo que dicen los datos reales (1242 ligandos)](#4-lo-que-dicen-los-datos-reales-1242-ligandos)
5. [`ElementStyle.kt`: color, tamaño y nombre de cada elemento](#5-elementstylekt-color-tamaño-y-nombre-de-cada-elemento)
6. [`BondGeometry.kt`: el cilindro que une dos átomos](#6-bondgeometrykt-el-cilindro-que-une-dos-átomos)
7. [`SceneFraming.kt`: a qué distancia empieza la cámara](#7-sceneframingkt-a-qué-distancia-empieza-la-cámara)
8. [`MoleculeScene.kt`: la escena de SceneView](#8-moleculescenekt-la-escena-de-sceneview)
9. [`MoleculeScreen.kt`: la pantalla](#9-moleculescreenkt-la-pantalla)
10. [Cómo probarlo en el móvil y qué ajustar](#10-cómo-probarlo-en-el-móvil-y-qué-ajustar)
11. [Simulaciones paso a paso](#11-simulaciones-paso-a-paso)
12. [Trampas y límites conocidos](#12-trampas-y-límites-conocidos)
13. [Qué está verificado y qué no](#13-qué-está-verificado-y-qué-no)
14. [Pruebas y mutación](#14-pruebas-y-mutación)
15. [Alternativas](#15-alternativas)
16. [Decisiones](#16-decisiones)
17. [Qué recordar](#17-qué-recordar)

---

## 1. Qué pide el enunciado y qué cubre esta etapa

Del documento 01, para la vista 3D: modelo de **bolas y varillas** con **colores CPK**, **giro, zoom y desplazamiento**, **tocar un átomo** para saber qué es, y **compartir una captura**; más bonus.

| Requisito | Estado tras esta etapa |
|---|---|
| Bolas y varillas con colores CPK | **Escrito** (secciones 5, 6, 8) |
| Giro y zoom | **Escrito** con la cámara orbital del spike (comprobada en H3) y **límites de zoom en ångström** (sección 7.1). **No se ha comprobado el desplazamiento** (*pan*) con dos dedos: ver sección 12 |
| Tocar un átomo | **Escrito**: se resalta en amarillo y una tarjeta dice nombre, elemento y coordenadas |
| Compartir captura | **Pendiente** (hipótesis H4 del spike, sin probar). Es la siguiente etapa |
| Bonus | Se añade un interruptor «H» para ocultar los hidrógenos. Enlaces dobles/triples dibujados: **pendiente** |

**Qué NO hace esta etapa y por qué:** no se ha medido el rendimiento (H6) ni el ciclo de vida (H5) con moléculas reales; las dos cosas se miran **con esta escena en el móvil**, no antes.

---

## 2. Mapa: qué fichero hace qué

| Fichero | Ruta (bajo `app/src/main/java/dev/knowledge82/swiftyproteins/`) | Líneas | Para qué |
|---------|------|-------:|----------|
| `scene/ElementStyle.kt` | `scene/` | 70 | Color, radio y nombre de cada elemento. **Puro, con pruebas.** |
| `scene/BondGeometry.kt` | `scene/` | 60 | Dónde y cómo girar un cilindro para unir dos átomos. **Puro, con pruebas.** |
| `scene/SceneFraming.kt` | `scene/` | 57 | Radio de la molécula y distancia inicial de la cámara. **Puro, con pruebas.** |
| `ui/MoleculeScene.kt` | `ui/` | 148 | Convierte un `Molecule` en nodos de SceneView y atiende los toques. **Sin compilar.** |
| `ui/MoleculeScreen.kt` | `ui/` | 105 | Barra superior, interruptor «H», tarjeta del átomo. **Sin compilar.** |
| `ui/SwiftyApp.kt` | `ui/` | — | **Una línea cambia:** la rama `Loaded` dibuja `MoleculeScreen` en vez de la pantalla provisional. |
| `ElementStyleTest.kt`, `BondGeometryTest.kt`, `SceneFramingTest.kt` | `app/src/test/.../scene/` | 10 + 8 + 13 pruebas | Ver sección 14. |

**Se borran del repositorio:** `MoleculeScene.kt` de la **raíz del paquete** (el código demostrativo del spike, con átomos fijos; decisión D11) y `ui/MoleculeSummaryScreen.kt` (la pantalla provisional del documento 06c, sección 10).

```
 LoadState.Loaded(molecule) ─▶ SwiftyApp ─▶ MoleculeScreen ──┬─▶ MoleculeScene ──▶ SceneView (SphereNode, CylinderNode, cámara orbital)
                                                              │        ▲ usa
                                                              │   ElementStyle · BondGeometry · SceneFraming   (puros, probados en la JVM)
                                                              └─▶ barra superior · «H» · tarjeta del átomo tocado
```

---

## 3. Teoría: de una lista de átomos a una imagen

**3.1 Qué es «bolas y varillas».** Cada átomo es una **esfera** (bola) y cada enlace un **cilindro** (varilla) que une los centros de dos esferas. Las esferas son **más pequeñas** que la distancia entre átomos enlazados, para que se vean las varillas
(en el modo «rellenar espacio» las esferas son enormes y se tocan: no se ven los enlaces). Un enlace típico mide **1 a 1,5 Å** (1 Å = 0,1 nm); por eso los radios de bola de esta etapa son **0,25 a 0,55 Å**.

**3.2 Colores CPK.** El nombre viene de Corey, Pauling y Koltun, que hicieron modelos de plástico con un color por elemento. La convención que se usa casi siempre es: **hidrógeno blanco, carbono gris (o negro), nitrógeno azul, oxígeno rojo,
azufre amarillo, fósforo naranja, halógenos (flúor, cloro) verdes, bromo rojo oscuro, yodo violeta, hierro naranja oxidado**. **No es un estándar oficial**: cada programa elige tonos algo distintos. Aquí se usa la tabla del visor Jmol
(sección 5). Los elementos que no están en la tabla salen en **rosa fuerte**, para que **se noten** y no pasen por un elemento «normal».

**3.3 Unidades y ejes.** Las coordenadas del fichero son **ångström** y la escena usa **la misma unidad**: una unidad de la escena = 1 Å. El parser ya **centró** la molécula en el origen (documento 03, D10). Los ejes son los de OpenGL/Filament:
**X a la derecha, Y arriba, Z hacia el observador** (sistema de mano derecha).

**3.4 Un cilindro «nace» de pie.** Un nodo cilindro se crea con su eje a lo largo de **Y** y centrado en su propio origen. Para que una el átomo A con el B hay que: (1) **alargarlo** a la longitud del enlace, (2) **inclinarlo** hasta que apunte
de A a B y (3) **llevarlo** al punto medio. El spike lo resolvió con **dos nodos anidados** y dos ángulos:
- el nodo **exterior** está en el punto medio y gira **`spin`** grados alrededor de Y (la vertical);
- el nodo **interior** contiene el cilindro y lo inclina **`tilt`** grados alrededor de Z.

Al estar anidados, la rotación del interior se aplica **primero** y la del exterior **después**. Es el orden que se necesita: primero se inclina el cilindro en el plano XY (ya sabes cuánto se aparta de la vertical) y luego se le hace girar
alrededor de la vertical hasta que apunte hacia donde toca. La sección 6 lo demuestra con un ejemplo numérico.

**3.5 La cámara orbital.** La cámara mira siempre al centro y se mueve por la superficie de una esfera de radio **`orbitRadius`**: un dedo arrastra (gira la molécula), el pellizco cambia el radio (zoom). Como la molécula está centrada en el origen,
**todo lo que hay que decidir es a qué distancia empieza**: si es poca, la molécula se sale de la pantalla; si es mucha, se ve diminuta. La sección 7 lo calcula a partir del átomo más lejano.

**3.6 Tocar un átomo (selección por rayo).** Al tocar la pantalla, SceneView lanza un **rayo** desde la cámara hacia el punto tocado y busca qué nodo corta primero. Cada nodo tiene una **forma de colisión** (*collision shape*); por defecto es la caja
que contiene al nodo. En el spike se cambió a **esfera** (decisión D8) para que el toque sea exacto; aquí se amplía a un **mínimo de 0,35 Å** para los hidrógenos, que son más pequeños que un dedo.

**3.7 Por qué cada átomo es un nodo propio.** Es lo más simple y la selección sale gratis (un nodo por átomo = un objeto que se puede tocar). Su coste es que, con 256 átomos y 256 enlaces, hay **cientos de nodos y de mallas**. La alternativa (fundir todas las
esferas en **una** malla y averiguar el átomo tocado calculando el rayo a mano) es mucho más rápida de dibujar y mucho más trabajo: se deja como plan B si el rendimiento (H6) no basta (sección 15).

---

## 4. Lo que dicen los datos reales (1242 ligandos)

Medido sobre el corpus analizado en el documento 03 (los 1242 ligandos del diccionario, parseados con el parser real):

| Dato | Valor | Qué implica |
|------|------:|-------------|
| Átomos en total | 53 241 | |
| De ellos, **hidrógenos** | **25 613 (48,1 %)** | Casi la mitad de lo que se dibuja son hidrógenos: **por eso hay un interruptor «H»** y bolas de hidrógeno más sencillas |
| Átomos por ligando: mediana / p90 / p99 / máximo | 35 / 81 / 152 / **256** (`CDL`) | La carga típica es pequeña; el peor caso es 256 bolas y 256 varillas |
| Ligandos con más de 100 / 150 / 200 átomos | 68 / 14 / 3 | Casos raros, pero **la evaluación puede elegirlos** |
| Átomos sin contar hidrógenos: mediana / máximo | 20 / 126 | Ocultar la «H» reduce a la mitad el trabajo en el caso medio |
| Ligandos **sin ningún** hidrógeno | 73 | Para ellos el interruptor «H» **no se muestra** |
| Enlaces: simples / dobles / triples / aromáticos | 48 437 / 6 061 / 29 / **0** | El 11 % de los enlaces son dobles: dibujarlos como simples **pierde información** (sección 12). Ningún enlace viene como aromático |
| Radio (átomo más lejano del centro): mediana / p90 / máximo | 6,5 / 11,5 / **41,4 Å** (`15P`) | La cámara debe alejarse **seis veces más** en el peor caso que en la mediana |
| Elementos distintos | **50 reales + `X`** (el comodín de `UNX`) | Una tabla de 50 colores cubre todo el corpus (una prueba lo comprueba) |
| Los más frecuentes | H 25 613, C 18 461, O 5 708, N 2 374, P 294, S 285, F 194, Cl 138, Br 45, B 23 | Con cinco colores (H, C, O, N y «otros») se entiende casi toda la imagen |

Casos de ejemplo (distancia de cámara inicial con la fórmula de la sección 7):

| Ligando | Átomos | Radio | Distancia inicial |
|---------|-------:|------:|------------------:|
| `001` | 87 | 8,6 Å | ≈ 50 |
| `HEM` | 75 | 8,4 Å | ≈ 49 |
| `ATP` | 47 | 10,1 Å | ≈ 58 |
| `CDL` | **256** | 30,9 Å | ≈ 173 |
| `15P` | 244 | **41,4 Å** | ≈ 230 |

---

## 5. `ElementStyle.kt`: color, tamaño y nombre de cada elemento

```kotlin
// ElementStyle.kt · líneas 3–22
 3  /**
 4   * How each chemical element is drawn: its COLOR, the RADIUS of its ball and its English NAME.
 5   *
 6   * Pure Kotlin, no Android: the 3D scene asks this object and does not decide anything itself, so the rules can be
 7   * tested on the JVM.
 8   *
 9   * The keys are the symbols AS THE PARSER NORMALIZES THEM: first letter upper case, the rest lower case ("Cl", "Fe",
10   * never "CL"). The lookup is case sensitive on purpose.
11   *
12   * **Colors** follow the table that Jmol (a well-known molecule viewer) calls "CPK": hydrogen white, carbon grey,
13   * nitrogen blue, oxygen red, sulfur yellow, phosphorus orange, halogens green, and so on. Written from memory: it is
14   * a widely used convention and not an official standard, and the exact shade of each metal was NOT checked against
15   * the original table.
16   */
17  object ElementStyle {
18
19      /** Used for any symbol that is not in the table (and for "X", the placeholder of unknown atoms): bright pink, so it stands out. */
20      const val UNKNOWN_COLOR = 0xFF1493
21
22      private val colors: Map<String, Int> = mapOf(
```

- **Es un `object`**: un único ejemplar, sin estado que cambie; sus datos (tres tablas) se construyen una vez.
- **Líneas 9–10, las claves son los símbolos TAL COMO los deja el parser**: primera letra mayúscula, el resto minúscula (`Cl`, `Fe`). El fichero escribe `CL` y `FE`; el parser los normaliza (documento 03a). **La búsqueda distingue mayúsculas a propósito**: si
  algún día llegara `CL`, saldría rosa (desconocido) y **se notaría**, en vez de ocultar un fallo del parser. Una prueba lo fija (`symbolsAreCaseSensitive...`).
- **Línea 20, `UNKNOWN_COLOR = 0xFF1493`**: rosa fuerte (el que usa Jmol para lo desconocido). Se usa con cualquier símbolo fuera de la tabla **y con `X`**, el comodín de los ligandos `UNX` (átomo de tipo desconocido).
- **Los colores son enteros `0xRRGGBB`** (tres bytes: rojo, verde, azul), **sin transparencia**. `ElementStyle` no depende de Compose: la pantalla los convierte a su tipo de color (sección 8).
- **La tabla de colores** (líneas 22–34 del fichero) tiene **un color por elemento del corpus** más algunos más (gases nobles y otros). Los valores salen de la tabla de Jmol, **escrita de memoria**: los de los elementos comunes
  (H, C, N, O, S, P, Cl, Br, I, Fe) son los que se conocen; **los tonos exactos de los metales raros no se han comprobado** contra la tabla original. Para esta app, que distingan «es un metal» de «es carbono» basta.
- **Colores repetidos:** nada obliga a que dos elementos tengan colores distintos (el carbono y el platino, por ejemplo, son ambos grisáceos). Lo que se comprueba es que los **cinco más frecuentes** (H, C, O, N, S) se **distinguen**.

```kotlin
// ElementStyle.kt · líneas 53–70
53      /** Is the element in the color table? ("X" and anything unlisted are not.) */
54      fun isKnown(element: String): Boolean = element in colors
55
56      /**
57       * Radius of the ball, in angstrom (the same unit as the coordinates). It is NOT the real size of the atom: it is
58       * chosen so that the picture looks like "ball and stick" (balls clearly smaller than the distance between bonded
59       * atoms, about 1 to 1.5 A), with hydrogen smaller and heavy atoms bigger.
60       */
61      fun radiusOf(element: String): Float = when (element) {
62          "H" -> 0.25f
63          "C", "N", "O", "F" -> 0.40f
64          "P", "S", "Cl", "Br", "I", "Se", "B", "Si" -> 0.50f
65          else -> 0.55f
66      }
67
68      /** English name for the screen; the symbol itself when the element is not in the table. */
69      fun nameOf(element: String): String = names[element] ?: element
70  }
```

- **`radiusOf`:** tres tamaños según el elemento. **No es el tamaño real del átomo** (el radio de van der Waals del carbono es 1,7 Å, **cuatro veces más**); es un tamaño de dibujo para que se vean las varillas:
  - **H: 0,25** (el más pequeño; es casi la mitad de las bolas y no debe tapar nada);
  - **C, N, O, F: 0,40** (el spike usaba 0,4 y se veía bien);
  - **P, S, Cl, Br, I, Se, B, Si: 0,50** (más grandes en la realidad);
  - **todo lo demás: 0,55** (metales y raros).
  - Una prueba comprueba que **ninguno sale de 0,2–0,6** y que la suma de los radios de H y O (0,65) no se come el enlace O–H más corto del corpus (0,86 Å), de modo que **la varilla se ve**.
- **`nameOf`:** el nombre en inglés (`Carbon`, `Iron`); si el elemento no está, el propio símbolo. Los 50 elementos del corpus tienen nombre; `X` se llama `Unknown element`.
- **`isKnown`:** ¿está en la tabla de colores? Sirve a las pruebas (y a quien quiera avisar de un elemento sin color).

---

## 6. `BondGeometry.kt`: el cilindro que une dos átomos

```kotlin
// BondGeometry.kt · líneas 8–60
 8  /**
 9   * Where and how to place a CYLINDER so that it joins two atoms.
10   *
11   * A cylinder is born standing up (its axis is the Y axis), centered on the origin. Two nested nodes bring it to
12   * place (this is what the spike validated on the device, document 02b):
13   *
14   *  1. the OUTER node sits at the midpoint ([x], [y], [z]) and turns [spinDegrees] around the vertical axis Y;
15   *  2. the INNER node holds the cylinder, of height [length], and leans it [tiltDegrees] around Z.
16   *
17   * The inner rotation is applied first (it is closer to the cylinder), then the outer one. [BondGeometryTest]
18   * checks with matrices that the result points exactly from one atom to the other.
19   */
20  data class BondPose(
21      val x: Float,
22      val y: Float,
23      val z: Float,
24      val length: Float,
25      val tiltDegrees: Float,
26      val spinDegrees: Float,
27  )
28
29  object BondGeometry {
30
31      /** Two atoms closer than this (angstrom) have no direction to draw: the bond is skipped. */
32      private const val MIN_LENGTH = 1e-4
33
34      private const val RAD_TO_DEG = 180.0 / Math.PI
35
36      /** The pose of the cylinder between [a] and [b], or `null` if they are at the same place. */
37      fun poseOf(a: Atom, b: Atom): BondPose? {
38          val dx = (b.x - a.x).toDouble()
39          val dy = (b.y - a.y).toDouble()
40          val dz = (b.z - a.z).toDouble()
41          val length = sqrt(dx * dx + dy * dy + dz * dz)
42          if (length < MIN_LENGTH) return null
43
44          // How far the axis must lean from the vertical. coerceIn is a SAFETY NET: acos of a number above 1 is NaN and the cylinder
45          // would vanish. With Float arithmetic (the spike) the quotient could round to 1.0000001; in Double it cannot exceed 1
46          // (the root of a sum of squares is never below one of the terms), so removing the guard changes nothing today.
47          val tilt = acos((dy / length).coerceIn(-1.0, 1.0)) * RAD_TO_DEG
48          // Which way it leans (around the vertical axis).
49          val spin = atan2(dz, -dx) * RAD_TO_DEG
50
51          return BondPose(
52              x = (a.x + b.x) / 2f,
53              y = (a.y + b.y) / 2f,
54              z = (a.z + b.z) / 2f,
55              length = length.toFloat(),
56              tiltDegrees = tilt.toFloat(),
57              spinDegrees = spin.toFloat(),
58          )
59      }
60  }
```

- **`BondPose` (líneas 20–27):** el resultado: **dónde** (`x`, `y`, `z` del punto medio), **cuánto mide** (`length`) y **los dos ángulos** (`tiltDegrees`, `spinDegrees`). Es un dato, sin comportamiento.
- **Línea 32, `MIN_LENGTH`:** si los dos átomos están (casi) en el mismo sitio **no hay dirección** que dibujar; la función devuelve **`null`** y la escena **se salta** esa varilla (un fichero dañado no debe producir un cilindro con ángulos sin sentido ni un
  cierre de la app). El umbral es `1e-4` Å: nada real se acerca.
- **Líneas 38–41:** el vector de A a B y su longitud, **en `Double`**. El spike lo hacía en `Float`; con `Double` los errores de redondeo no llegan a notarse.
- **Línea 47, `tilt = acos(dy / length)`:** el ángulo entre el eje del cilindro (la vertical, Y) y la dirección del enlace es el **arco coseno** de `dy / length` (el coseno del ángulo entre dos vectores unitarios es su producto escalar, y el de la vertical solo
  recoge `dy`). `coerceIn(-1, 1)` es una **red de seguridad**: `acos` de un número mayor que 1 es `NaN` y el cilindro **desaparecería**. Con `Double` el cociente nunca supera 1 (la raíz de una suma de cuadrados no baja de ninguno de sus términos), así que hoy quitar
  la red **no cambia nada** (es el mutante equivalente de la sección 14); se deja por si el cálculo cambia.
- **Línea 49, `spin = atan2(dz, -dx)`:** el ángulo alrededor de la vertical. **El signo menos de `-dx` no es un capricho**: sale de cómo gira SceneView (mano derecha). `atan2(y, x)` da el ángulo del punto `(x, y)`; con `(−dx, dz)` se obtiene el giro que, aplicado después
  de la inclinación, deja el eje exactamente sobre `(dx, dy, dz)`. Es la fórmula que el spike validó **con los ojos** en el A33; ahora además se **demuestra con matrices** (abajo).
- **Líneas 52–54, el punto medio:** la media de las coordenadas de A y de B.

**Ejemplo numérico** (enlace de A = (0, 0, 0) a B = (1, 1, 0)):
1. Vector (1, 1, 0); longitud √2 ≈ 1,414.
2. `tilt = acos(1 / 1,414) = acos(0,7071) = 45°`.
3. `spin = atan2(0, −1) = 180°`.
4. El cilindro nace con eje (0, 1, 0). **Primero** el nodo interior lo inclina 45° alrededor de Z: el eje pasa a (−sin 45°, cos 45°, 0) = (−0,707; 0,707; 0).
5. **Después** el exterior gira 180° alrededor de Y: (x, y, z) → (−x, y, −z), y el eje queda en **(0,707; 0,707; 0)**, que es la dirección (1, 1, 0) normalizada. ✔

**Otro** (de A = (0, 0, 0) a B = (0, 0, 1), hacia el observador): `tilt = acos(0) = 90°`, `spin = atan2(1, −0) = 90°`. Inclinar 90° deja el eje en (−1, 0, 0); girar 90° alrededor de Y lleva (−1, 0, 0) a (0, 0, 1). ✔

**La demostración de verdad** está en `BondGeometryTest`: implementa las dos rotaciones como fórmulas y comprueba, para **2000 pares de átomos al azar**, para los seis ejes y para los siete enlaces de la escena del spike, que el eje resultante **coincide con la
dirección de A a B** (error menor que 0,0001). Lo que **no** demuestra es que SceneView aplique las rotaciones **en ese orden y con ese sentido**; eso lo dice el spike, que se vio correcto en el A33, y lo confirmará **ver la molécula** (sección 10).

---

## 7. `SceneFraming.kt`: a qué distancia empieza la cámara

```kotlin
// SceneFraming.kt · líneas 7–37
 7  /**
 8   * How far to put the camera so that the WHOLE molecule is in view when the scene opens.
 9   *
10   * The parser centers every molecule on the origin, so the camera orbits around (0, 0, 0) and it is enough to know
11   * how far the farthest atom is.
12   */
13  object SceneFraming {
14
15      /** Never closer than this (angstrom), even for a single atom. */
16      const val MIN_DISTANCE = 6f
17
18      /**
19       * Distance = [DISTANCE_PER_RADIUS] times the radius of the molecule. ESTIMATED, not measured: a phone is TALL, so
20       * the narrow side (the width) limits what fits; with the usual lens that asks for about five radii. To be adjusted
21       * on the device (document 07, section 10).
22       */
23      const val DISTANCE_PER_RADIUS = 5.5f
24
25      /** Distance from the origin to the outer edge of the farthest ball. It counts ALL atoms, hidden hydrogens included, so the view does not jump when they are shown or hidden. */
26      fun radiusOf(molecule: Molecule): Float {
27          var farthest = 0f
28          for (atom in molecule.atoms) {
29              val reach = sqrt(atom.x * atom.x + atom.y * atom.y + atom.z * atom.z) + ElementStyle.radiusOf(atom.element)
30              farthest = max(farthest, reach)
31          }
32          return farthest
33      }
34
35      /** Where the camera starts, as a distance from the center. */
36      fun cameraDistance(radius: Float): Float = max(MIN_DISTANCE, DISTANCE_PER_RADIUS * radius)
37
```

- **`radiusOf` (líneas 26–33):** el átomo más lejano del origen **más el radio de su bola**: la distancia del centro al borde exterior de la molécula. **Cuenta todos los átomos, también los hidrógenos ocultos**: así, al pulsar «H» **la vista no salta**.
  (Una prueba lo fija: `hydrogensCountEvenWhenTheyAreHidden`. Otra, `theFarthestAtomCountsEvenWhenItIsNotTheLastOne`, mata una avería real que la primera versión de las pruebas no veía: usar el **último** átomo en vez del más lejano.)
- **Línea 23, `DISTANCE_PER_RADIUS = 5.5`:** la distancia es **5,5 veces el radio**. **Es una estimación, no una medida.** Razón: un móvil es **alto y estrecho**; lo que limita es el ancho de la pantalla, y con un objetivo normal (campo de visión vertical de unos 45°) cabe
  una esfera de radio `R` a una distancia de unos `2,4·R` en vertical y de unos `5·R` en el ancho de un móvil en vertical. El spike usó 9,5 para una molécula de radio ≈ 1,9 (cociente ≈ 5), y se veía bien. **El campo de visión que usa SceneView no se ha comprobado**.
  Si en el móvil la molécula sale **muy pequeña** o **cortada**, **este es el número que hay que tocar** (sección 10).
- **Línea 16, `MIN_DISTANCE = 6`:** una molécula de un solo átomo o de dos no debe pegar la cámara a la bola.
- **Línea 36, `cameraDistance`:** `max(6, 5,5 · radio)`.
- **Plano lejano (*far plane*):** para `15P` la distancia sale ≈ 228. **Leído en el código de SceneView 4.69.4** (`CameraNode.kt`): plano cercano **0,01** y lejano **1000**. Con la distancia inicial (228) y la molécula (radio 41) todo cabe; con el límite de alejamiento de 7.1 (3 × 228 + 41 = 725) **también**. Sin ese límite (SceneView permite 8 × la distancia inicial: 1822) **se recortaría**. *Leído, no probado en el móvil.*

### 7.1 Los límites de zoom: por qué `15P` no se dejaba acercar

**El síntoma** (lo vio Valera en el móvil): con `001` se puede acercar la cámara hasta ver cómo se unen bolas y varillas; con `15P`, no: hay un tope de acercamiento que parece depender del tamaño del ligando.

**La causa, leída en el código fuente de SceneView 4.69.4** (`gesture/CameraGestureDetector.kt`, `DefaultCameraManipulator`; no es una suposición):

1. El gestor **mide la distancia inicial** de la cámara al centro («*home*») la primera vez que se usa el zoom.
2. En cada pellizco calcula la nueva distancia como `distancia · exp(Δ)` y la **recorta** entre `home × minZoomDistanceFactor` y `home × maxZoomDistanceFactor`. **Los valores por defecto** (`DEFAULT_MIN_ZOOM_DISTANCE_FACTOR` y `DEFAULT_MAX_ZOOM_DISTANCE_FACTOR`) son **0,15 y 8**.
3. Nuestra distancia inicial es **5,5 × radio** (sección 7). Luego el acercamiento máximo era `0,15 × 5,5 × radio ≈ 0,83 × radio`:

| Ligando | Radio (Å) | Distancia inicial | Acercamiento máximo **antes** (0,15 ×) | **Ahora** |
|---------|----------:|------------------:|--------------------------------------:|----------:|
| `001` (mediana, radio ≈ 6,5) | 6,5 | ≈ 36 | ≈ **5,4 Å** | 4 Å |
| `CDL` | 30,9 | ≈ 170 | ≈ **25 Å** | 4 Å |
| `15P` (el mayor) | 41,4 | ≈ 228 | ≈ **34 Å** | 4 Å |

Es decir: el tope era **proporcional al tamaño de la molécula**, cuando lo que importa para ver el detalle es **cuánto mide una bola** (≈ 1 Å), que es lo mismo en todos los ligandos. **Con `15P` el tope quedaba a 34 Å del centro: las bolas del centro nunca se veían de cerca.**

**La corrección** (`SceneFraming.kt`, más `MoleculeScene.kt`, líneas 88–99):

```kotlin
// SceneFraming.kt · líneas 38–56
38  /**
39   * The closest the camera may come to the center when zooming in (angstrom), THE SAME for every ligand.
40   *
41   * Why absolute: SceneView limits the zoom as a FRACTION of the starting distance (default 0.15). The starting
42   * distance grows with the molecule (5.5 x radius), so a big ligand such as 15P (radius 41 -> start 228) could not
43   * get nearer than 34 angstrom, while the small 001 (start ~35) could reach 5. The limit must be about what we want
44   * to SEE (a ball is ~1 angstrom wide; at 4 it fills a good part of the screen), not about how big the molecule is.
45   * ESTIMATED value: to be tuned on the device (document 07).
46   */
47  const val MIN_CLOSE_DISTANCE = 4f
48
49  /** How far the camera may go back, as a multiple of the starting distance. Keeps everything inside the far plane (1000 in SceneView) even for 15P: 3 x 228 + 41 = 725. */
50  const val MAX_ZOOM_OUT_FACTOR = 3f
51
52  /** The fraction of the starting distance that equals [MIN_CLOSE_DISTANCE]; never above 0.5, so zooming in is always possible, even for a tiny molecule whose start is already close. */
53  fun minZoomFactor(startDistance: Float): Float =
54      (MIN_CLOSE_DISTANCE / startDistance).coerceAtMost(MAX_MIN_ZOOM_FACTOR)
55
56  private const val MAX_MIN_ZOOM_FACTOR = 0.5f
```

- **`MIN_CLOSE_DISTANCE = 4`:** el acercamiento máximo es **el mismo en ångström para todos**. **Es una estimación** (una bola mide ≈ 1 Å de diámetro; a 4 Å del centro llena buena parte de la pantalla): **se ajusta en el móvil**.
- **`minZoomFactor(start)`:** convierte «4 Å» en la **fracción** que SceneView espera (`4 / distancia inicial`), con un tope de 0,5 para que **siempre** se pueda acercar, incluso con una molécula diminuta cuya distancia inicial (6) ya es corta.
- **`MAX_ZOOM_OUT_FACTOR = 3`:** se baja de 8 a 3 para que la molécula más grande **no salga del plano lejano** (arriba) y porque alejarse 8 veces no sirve de nada.
- **Todo es una función pura** y se prueba en el ordenador (cuatro pruebas nuevas, sección 14; cada constante tiene su mutante).

**Lo que esto NO resuelve (honesto):** la cámara **gira siempre alrededor del centro de la molécula**. En `15P` (≈ 80 Å de largo) puedes ver de cerca **lo que está cerca del centro**; para ver de cerca **un extremo** hace falta **desplazar** la escena (*pan*, dos dedos), y eso **no está comprobado en el móvil** (sección 12, punto 2). Si no funciona, la solución de verdad es **cambiar el centro de giro**: p. ej. «doble toque en un átomo → ese átomo pasa a ser el centro» (sección 15).

---

## 8. `MoleculeScene.kt`: la escena de SceneView

```kotlin
// MoleculeScene.kt · líneas 27–44
27  // Node names are how a TAP is traced back to what was drawn: "atom 12" is atoms[12]; "bond 3" is a stick.
28  private const val ATOM_PREFIX = "atom "
29  private const val BOND_PREFIX = "bond "
30
31  private const val BOND_RADIUS = 0.12f
32
33  // Mesh detail of a ball (how many bands it is cut into). The spike used 32; with up to 256 atoms (and more than half of
34  // them hydrogens) a lighter ball is used, and the smaller hydrogens need even less. ESTIMATED, not measured on the A33.
35  private const val BALL_DETAIL = 20
36  private const val HYDROGEN_BALL_DETAIL = 12
37
38  // A finger is much wider than a hydrogen (radius 0.25): the area that answers to a tap is never smaller than this.
39  private const val MIN_TOUCH_RADIUS = 0.35f
40
41  private fun colorOf(element: String): Color {
42      val rgb = ElementStyle.colorOf(element)
43      return Color((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF)
44  }
```

- **Líneas 27–29, los nombres de nodo** son el hilo que une **lo dibujado con los datos**: el nodo `"atom 12"` es `molecule.atoms[12]`. Al tocar, SceneView entrega el nodo; de su nombre sale el índice.
- **Líneas 35–36, el detalle de la malla:** una esfera se corta en `stacks` franjas horizontales y `slices` verticales; con 20×20 salen unos 800 triángulos por bola. El spike usó 32 (unos 2000). Con **256 átomos** la diferencia importa; los hidrógenos (la mitad,
  y pequeños) usan 12. **Los valores son una estimación; no se ha medido nada en el A33** (H6).
- **Línea 39, `MIN_TOUCH_RADIUS = 0,35`:** la zona que responde al toque nunca es menor que esto, aunque la bola dibujada sea más pequeña.
- **Líneas 41–44, `colorOf`:** convierte el entero `0xRRGGBB` de `ElementStyle` al `Color` de Compose separando los tres bytes con desplazamientos (`shr 16` = rojo, `shr 8` = verde).

```kotlin
// MoleculeScene.kt · líneas 46–83
46  /**
47   * The 3D view of a molecule: balls (atoms) and sticks (bonds), colored by element, with a camera that orbits.
48   *
49   * It decides NOTHING by itself: colors, radii, where each stick goes and how far the camera starts come from the
50   * pure classes in `scene/` (tested on the JVM). This function only turns those numbers into SceneView nodes.
51   *
52   * @param showHydrogens when `false`, hydrogens and the sticks that touch them are not drawn (the numbering of the rest does not change)
53   * @param selected      index in `molecule.atoms` of the highlighted atom, or `null`
54   * @param onSelect      called with the tapped atom, or `null` when the tap hit a stick or empty space
55   */
56  @Composable
57  fun MoleculeScene(
58      molecule: Molecule,
59      showHydrogens: Boolean,
60      selected: Int?,
61      onSelect: (Int?) -> Unit,
62      modifier: Modifier = Modifier,
63  ) {
64      val engine = rememberEngine()
65      val materialLoader = rememberMaterialLoader(engine)
66
67      // One material per ELEMENT, created the first time it is needed (a ligand has a handful of elements, not 256 colors).
68      val materials = remember(materialLoader) { HashMap<String, MaterialInstance>() }
69      fun materialOf(element: String): MaterialInstance =
70          materials.getOrPut(element) { materialLoader.createColorInstance(colorOf(element)) }
71
72      val stick = remember(materialLoader) { materialLoader.createColorInstance(Color(0.7f, 0.7f, 0.7f, 1f)) }
73      // Selection highlight: yellow stands out against grey, red, blue and white alike.
74      val highlight = remember(materialLoader) { materialLoader.createColorInstance(Color(1f, 0.9f, 0.1f, 1f)) }
75
76      // Both depend only on the molecule: computed once, not at every recomposition (and every tap recomposes).
77      val distance = remember(molecule) { SceneFraming.cameraDistance(SceneFraming.radiusOf(molecule)) }
78      val poses = remember(molecule) {
79          molecule.bonds.map { BondGeometry.poseOf(molecule.atoms[it.from], molecule.atoms[it.to]) }
80      }
81
82      // The gesture listener is created once and remembers the FIRST lambda it receives; this always calls the latest.
83      val currentOnSelect by rememberUpdatedState(onSelect)
```

- **Línea 57:** la función recibe **datos** (`Molecule`, qué mostrar, qué está seleccionado) y **avisa** con `onSelect`. No guarda estado: lo guarda `MoleculeScreen` (elevación de estado, documento 06c, sección 3.3).
- **Líneas 64–65:** el motor gráfico y el cargador de materiales, como en el spike. `rememberEngine()` **destruye el motor** cuando la función sale de la pantalla: **abrir otro ligando crea uno nuevo**. Es el punto delicado de H5 (ciclo de vida), aún sin probar.
- **Líneas 68–70, un material por ELEMENTO:** un **material** es el «color» de un objeto en Filament. En vez de crear uno por átomo (256), se crea **uno por elemento** y se **reutiliza** (`getOrPut`: lo busca; si no existe, lo crea y lo guarda).
  Una molécula típica usa 3–6 colores.
- **Líneas 72–74:** la varilla (gris, el valor del spike) y el **resaltado** (amarillo, que se distingue del gris, el rojo, el azul y el blanco).
- **Líneas 77–80, `remember(molecule)`:** la distancia de la cámara y **la pose de cada enlace** se calculan **una vez por molécula**, no en cada recomposición. Esto importa porque **cada toque recompone la escena** (cambia `selected`), y repetir 256 cálculos con
  senos y cosenos en cada toque sería gastar a lo tonto.
- **Línea 83, `rememberUpdatedState(onSelect)`:** `rememberOnGestureListener` **crea el oyente una vez** y se queda con **la primera lambda** que recibe; si `onSelect` cambiara después, el oyente llamaría a la vieja. Con `rememberUpdatedState` se llama
  **siempre a la última**. (Hoy `onSelect` es estable y daría igual; es una protección barata.)
- **Líneas 88–99:** la cámara orbital, con `distance` de `SceneFraming` y el centro en el origen. **Desde la sección 7.1** el gestor de cámara se crea a mano (`creator = { ... }`) para **poner los límites de zoom en ångström**: `createDefaultCameraManipulator(...)` devuelve el gestor por defecto de SceneView y `.apply { ... }` cambia sus dos factores (`minZoomDistanceFactor`, `maxZoomDistanceFactor`), que **se leen en cada gesto**, así que basta con fijarlos tras crearlo.
- **Líneas 101–104, el toque:** `onSingleTapConfirmed` (un toque que **no es** el primero de un doble toque) recibe el nodo tocado (o `null`). El nombre `"atom 3"` pierde el prefijo y queda `"3"` → `3`. Un enlace (`"bond 2"`), el vacío o un nodo sin
  nombre dan `null` (`toIntOrNull` no lanza si no es un número): **se deselecciona**.

```kotlin
// MoleculeScene.kt · líneas 106–147
106  ) {
107      for ((i, atom) in molecule.atoms.withIndex()) {
108          if (showHydrogens || atom.element != "H") {
109              // key(i): each atom keeps its own identity when the list of drawn nodes changes (hydrogens hidden or shown).
110              key(i) {
111                  val radius = ElementStyle.radiusOf(atom.element)
112                  val detail = if (atom.element == "H") HYDROGEN_BALL_DETAIL else BALL_DETAIL
113                  SphereNode(
114                      radius = radius, stacks = detail, slices = detail,
115                      materialInstance = if (selected == i) highlight else materialOf(atom.element),
116                      position = Position(atom.x, atom.y, atom.z),
117                      apply = {
118                          this.name = "$ATOM_PREFIX$i"
119                          this.collisionShape = Sphere(max(radius, MIN_TOUCH_RADIUS))
120                      },
121                  )
122              }
123          }
124      }
125
126      for ((k, bond) in molecule.bonds.withIndex()) {
127          val pose = poses[k]
128          val touchesHydrogen = molecule.atoms[bond.from].element == "H" || molecule.atoms[bond.to].element == "H"
129          if (pose != null && (showHydrogens || !touchesHydrogen)) {
130              key(-k - 1) {   // negative keys: they can never collide with the atoms' keys
131                  // OUTER node: at the midpoint, turned around the vertical axis. INNER: the cylinder, leaned.
132                  Node(
133                      position = Position(pose.x, pose.y, pose.z),
134                      rotation = Rotation(y = pose.spinDegrees),
135                  ) {
136                      CylinderNode(
137                          radius = BOND_RADIUS,
138                          height = pose.length,
139                          rotation = Rotation(z = pose.tiltDegrees),
140                          materialInstance = stick,
141                          apply = { this.name = "$BOND_PREFIX$k" },
142                      )
143                  }
144              }
145          }
146      }
147  }
```

- **Líneas 107–124, las bolas:** un `SphereNode` por átomo. Si los hidrógenos están ocultos, **no se crean** los nodos de hidrógeno (`if (showHydrogens || ...)`): ocultar es **quitar nodos**, que además alivia el trabajo de la GPU.
  - **Línea 110, `key(i)`:** da a cada átomo una **identidad estable** igual a su índice. **Es importante.** Al pulsar «H», la lista de nodos dibujados cambia de longitud y de contenido; **sin `key`**, Compose empareja los nodos **por posición** y
    reutilizaría «el tercer nodo» para un átomo distinto, cambiando su posición pero **no necesariamente su malla** (el radio y el detalle se fijan al crear la esfera). Con `key(i)` cada átomo conserva **su** nodo, o se crea y se destruye **el suyo**.
  - **Línea 115:** el material es **el del elemento**, salvo si está seleccionado (amarillo). Cambiar el material de un nodo existente es lo que hizo el spike.
  - **Líneas 117–120:** el nombre y la **forma de colisión esférica** con el radio mínimo de toque.
- **Líneas 126–146, las varillas:** una por enlace.
  - **Línea 127:** la pose ya calculada (puede ser `null` si los dos átomos coinciden: **se salta**).
  - **Líneas 128–129:** una varilla que toca un hidrógeno **se oculta con él** (dejar varillas sueltas sin bola sería absurdo).
  - **Línea 130, `key(-k - 1)`:** claves **negativas** para las varillas; no pueden coincidir con las de los átomos (que son 0, 1, 2…).
  - **Líneas 132–143:** el par de nodos anidados de la sección 3.4: el exterior (`Node`) en el punto medio y girado `spin` alrededor de Y; el interior (`CylinderNode`) de altura `length`, inclinado `tilt` alrededor de Z. **Idéntico al spike.**
- **No se dibuja una varilla de dos colores** (mitad de cada átomo, como en muchos visores): sería una mejora de aspecto con el doble de cilindros. Pendiente (sección 15).

---

## 9. `MoleculeScreen.kt`: la pantalla

```kotlin
// MoleculeScreen.kt · líneas 29–60
29  /**
30   * The screen of one molecule: the 3D scene full size, a bar on top (back, name, hydrogens on/off) and, when an atom is
31   * tapped, a card at the bottom that says what it is.
32   */
33  @Composable
34  fun MoleculeScreen(
35      molecule: Molecule,
36      onBack: () -> Unit,
37      modifier: Modifier = Modifier,
38  ) {
39      // The system Back button returns to the list, exactly like the button on screen.
40      BackHandler(onBack = onBack)
41
42      // rememberSaveable: the selection and the switch survive a rotation of the phone.
43      var selected by rememberSaveable { mutableStateOf<Int?>(null) }
44      var showHydrogens by rememberSaveable { mutableStateOf(true) }
45      val hasHydrogens = remember(molecule) { molecule.atoms.any { it.element == "H" } }
46
47      // What is REALLY highlighted: a valid index, and not a hydrogen that is currently hidden.
48      val atom = selected
49          ?.takeIf { it in molecule.atoms.indices }
50          ?.let { molecule.atoms[it] }
51          ?.takeIf { showHydrogens || it.element != "H" }
52
53      Box(modifier = modifier.fillMaxSize()) {
54          MoleculeScene(
55              molecule = molecule,
56              showHydrogens = showHydrogens,
57              selected = if (atom == null) null else selected,
58              onSelect = { selected = it },
59              modifier = Modifier.fillMaxSize(),
60          )
```

- **Línea 40, `BackHandler`:** el botón «Atrás» del sistema vuelve a la lista (si no, **cerraría la app**).
- **Líneas 43–44, `rememberSaveable`:** el átomo seleccionado y el interruptor «H» **sobreviven al giro del móvil**. `mutableStateOf<Int?>(null)` es un estado que puede ser `null` (nada seleccionado).
- **Línea 45:** ¿tiene hidrógenos? Los 73 ligandos sin ninguno **no muestran** el interruptor.
- **Líneas 48–51, el átomo REALMENTE resaltado:** se encadenan tres comprobaciones con `?.takeIf` / `?.let`: (1) hay selección, (2) **el índice es válido** (protección contra un estado guardado de otra molécula) y (3) **no es un hidrógeno oculto**
  (si el usuario oculta los H con uno seleccionado, la tarjeta no debe seguir hablando de un átomo que ya no se ve). Si cualquiera falla, `atom` es `null`.
- **Línea 57:** a la escena solo se le pasa la selección si `atom` no es `null`.

```kotlin
// MoleculeScreen.kt · líneas 62–105
 62          // Semi-transparent so the text is readable on any background and the molecule is still seen behind it.
 63          Surface(
 64              modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
 65              color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
 66          ) {
 67              Row(
 68                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
 69                  verticalAlignment = Alignment.CenterVertically,
 70              ) {
 71                  TextButton(onClick = onBack) { Text("Back") }
 72                  Text(
 73                      text = "${molecule.id} · ${molecule.atoms.size} atoms · ${molecule.bonds.size} bonds",
 74                      style = MaterialTheme.typography.titleSmall,
 75                      modifier = Modifier.weight(1f),
 76                  )
 77                  if (hasHydrogens) {
 78                      FilterChip(
 79                          selected = showHydrogens,
 80                          onClick = { showHydrogens = !showHydrogens },
 81                          label = { Text("H") },
 82                      )
 83                  }
 84              }
 85          }
 86
 87          Card(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp)) {
 88              Column(modifier = Modifier.padding(16.dp)) {
 89                  if (atom == null) {
 90                      Text("Tap an atom to see what it is. Drag to rotate, pinch to zoom.", style = MaterialTheme.typography.bodyMedium)
 91                  } else {
 92                      Text(
 93                          text = "${atom.name} · ${ElementStyle.nameOf(atom.element)} (${atom.element})",
 94                          style = MaterialTheme.typography.titleMedium,
 95                      )
 96                      Text(
 97                          // Locale.US: always "1.234", never "1,234".
 98                          text = String.format(Locale.US, "x %.3f   y %.3f   z %.3f  (angstrom)", atom.x, atom.y, atom.z),
 99                          style = MaterialTheme.typography.bodySmall,
100                      )
101                  }
102              }
103          }
104      }
105  }
```

- **Líneas 63–66, la barra superior:** un `Surface` **semitransparente** (85 %) sobre la escena; así el texto se lee sobre cualquier fondo y la molécula **se sigue viendo** por detrás.
- **Línea 71:** botón «Back». **Línea 72–76:** «001 · 87 atoms · 90 bonds»; `weight(1f)` le da **todo el espacio sobrante**, lo que empuja el interruptor a la derecha.
- **Líneas 77–83:** `FilterChip` (una «pastilla» que se enciende y se apaga) con la letra H. Cambia `showHydrogens`.
- **Líneas 87–103, la tarjeta de abajo:** sin selección enseña una **ayuda** («Tap an atom…»); con selección, **el nombre del átomo, el elemento en inglés y su símbolo** («C01 · Carbon (C)») y sus tres coordenadas con tres decimales.
  `Locale.US` hace que el punto decimal sea siempre punto, con el móvil en el idioma que sea.
- **La ayuda dice «Drag to rotate, pinch to zoom»** y **no** habla de desplazamiento: no se ha comprobado que exista (sección 12).

---

## 10. Cómo probarlo en el móvil y qué ajustar

**Lista de comprobación** (con red normal):

| # | Qué hacer | Qué debe verse | Si no |
|--:|-----------|----------------|-------|
| 1 | Abrir `001` | Una molécula con **bolas de varios colores unidas por varillas grises**, entera en pantalla | Ver tabla de ajustes abajo |
| 2 | Contar | 87 átomos; con «H» apagado quedan **45** (87 − 42) | Si «H» no quita 42, hay un fallo en el filtro |
| 3 | Arrastrar con un dedo | La molécula gira **de forma fluida** y **no se sale** | |
| 4 | Pellizcar con `001` y luego con **`15P`** | Acerca y aleja; **el acercamiento máximo es parecido en ambos** (bolas grandes en pantalla), y se **para** antes de entrar en la molécula. Alejarse tiene tope | Si en `15P` aún no se acerca lo bastante: bajar `MIN_CLOSE_DISTANCE` (3, 2,5…); si se mete dentro de una bola o atraviesa: subirla (sección 7.1) |
| 5 | Tocar una bola | Se pone **amarilla** y la tarjeta dice p. ej. «C01 · Carbon (C)» con sus coordenadas | Si no responde: el radio táctil (`MIN_TOUCH_RADIUS`) |
| 6 | Tocar una varilla o el vacío | Se **deselecciona** | |
| 7 | Pulsar «H» con un hidrógeno seleccionado | Desaparecen hidrógenos y varillas; la tarjeta vuelve a la ayuda; **la vista no salta** | |
| 8 | Girar el móvil | Misma molécula, misma selección, mismo «H» | Si se cierra o sale negro: es H5 |
| 9 | «Atrás», abrir otro ligando, repetir 5 veces | Sin cierres ni pantalla negra | Es H5: **apuntar el mensaje del Logcat** |
| 10 | Abrir **`CDL`** (256 átomos) y **`15P`** (el más grande) | Se ve entera; el giro sigue siendo usable | Ver abajo |

**Los mandos** (cada uno es **una constante** y cada una es una **estimación**):

| Síntoma | Constante | Fichero | Qué hacer |
|---------|-----------|---------|-----------|
| La molécula sale **diminuta** | `DISTANCE_PER_RADIUS` (5,5) | `SceneFraming.kt` | Bajarla (4, 3,5…) |
| Sale **cortada por los lados** | `DISTANCE_PER_RADIUS` | `SceneFraming.kt` | Subirla (6, 7…) |
| Con `15P` **desaparece parte** de la molécula al alejarse | `MAX_ZOOM_OUT_FACTOR` (3) | `SceneFraming.kt` | Bajarlo (2…). El plano lejano de SceneView es 1000 (leído en el código) |
| No se **acerca lo bastante** / se acerca **demasiado** | `MIN_CLOSE_DISTANCE` (4 Å) | `SceneFraming.kt` | Bajarla / subirla |
| El giro **va a tirones** con 256 átomos | `BALL_DETAIL`, `HYDROGEN_BALL_DETAIL` | `MoleculeScene.kt` | Bajarlos (12 / 8); y probar con «H» apagado |
| Las bolas **se tocan y tapan** las varillas | `radiusOf` | `ElementStyle.kt` | Bajar los radios |
| Cuesta **acertar** un hidrógeno | `MIN_TOUCH_RADIUS` | `MoleculeScene.kt` | Subirlo (0,45…) |
| Las varillas **son demasiado finas o gruesas** | `BOND_RADIUS` (0,12) | `MoleculeScene.kt` | Cambiar |

---

## 11. Simulaciones paso a paso

**A. Abrir `001` y tocar un átomo.**

| Paso | Qué ocurre | Pantalla |
|-----:|---|---|
| 1 | `LoadState.Loaded(001)` llega a `SwiftyApp`, que dibuja `MoleculeScreen` | Cambia la lista por la escena |
| 2 | `MoleculeScene`: `radiusOf` ≈ 8,6 → `distance` ≈ 50; se calculan las 90 poses | La cámara arranca a 50 Å del centro |
| 3 | Se crean 87 `SphereNode` (nombres `atom 0`…`atom 86`) y 90 pares de nodos de varilla | La molécula aparece |
| 4 | El usuario toca una bola roja | SceneView lanza el rayo y entrega el nodo `atom 41` |
| 5 | `onSingleTapConfirmed`: `"atom 41"` → `"41"` → `41`; `currentOnSelect(41)` → `selected = 41` | |
| 6 | Recompone: `atom` = `atoms[41]`; la escena recibe `selected = 41`; el nodo 41 cambia a **amarillo** | Bola amarilla; tarjeta «O3 · Oxygen (O)» (el nombre real depende del ligando) y sus coordenadas |
| 7 | Toca el vacío | `node` es `null` → `onSelect(null)` | Se apaga el amarillo; vuelve la ayuda |

**B. Pulsar «H» con un hidrógeno seleccionado.**

| Paso | Qué ocurre |
|-----:|---|
| 1 | `selected = 63` (un H); `showHydrogens = true` |
| 2 | El usuario pulsa «H»: `showHydrogens = false` |
| 3 | `atom` pasa por el tercer `takeIf` (`showHydrogens || it.element != "H"`) → **`null`**; `selected` **sigue siendo 63** en el estado guardado, pero la escena recibe `null` y la tarjeta vuelve a la ayuda |
| 4 | La escena: los 42 nodos de H **no se crean** (y sus `key` desaparecen); las varillas que los tocaban, tampoco |
| 5 | El usuario vuelve a pulsar «H»: los nodos vuelven; **`selected` aún es 63**, así que el hidrógeno **reaparece resaltado** |

*(El paso 5 es un detalle de diseño: la selección «se acuerda» de un átomo oculto. Si resultara raro en el móvil, se limpia `selected` al ocultar.)*

**C. Girar el móvil.** La actividad se destruye; el `ViewModel` conserva `Loaded(molecule)`; `SwiftyApp` dibuja `MoleculeScreen` otra vez; `rememberSaveable` devuelve `selected` y `showHydrogens`. **La cámara vuelve a su posición inicial**
(el estado de la cámara no se guarda): se pierde el giro que el usuario había dado. *(Límite conocido, sección 12.)*

---

## 12. Trampas y límites conocidos

1. **Dobles y triples se dibujan como simples.** El 11 % de los enlaces del corpus son dobles (6061) y hay 29 triples. Es la mejora de aspecto más evidente que queda (varillas paralelas); `BondOrder` ya llega hasta la escena, pero no se usa.
2. **Desplazamiento (*pan*) sin comprobar.** El spike probó giro y zoom (H3); **no consta** que dos dedos desplacen la escena. Si el enunciado lo exige y no existe, hay que añadirlo (el gestor de cámara de SceneView/Filament admite varios modos;
   **no se ha leído su código en esta etapa**).
3. **Límites de zoom: puestos (sección 7.1), valores sin ajustar.** Mínimo 4 Å y máximo 3 × la distancia inicial; son estimaciones. **El centro de giro sigue siendo el centro de la molécula**: en un ligando grande no se pueden ver de cerca los extremos sin desplazar la escena.
4. **La cámara vuelve al inicio al girar el móvil.** Su estado no se guarda.
5. **El plano lejano** (1000, leído en SceneView) deja dentro a `15P` con el límite de alejamiento de la sección 7.1. No probado en el móvil.
6. **Rendimiento sin medir (H6).** 256 bolas + 256 varillas = cientos de nodos. La hipótesis H6 del spike hablaba de ~200 átomos y ~200 enlaces a ~60 FPS; **no se midió**. Si no basta: menos detalle, «H» apagado por defecto en moléculas grandes, o la alternativa de una sola malla (sección 15).
7. **Ciclo de vida sin probar (H5).** `rememberEngine()` crea un motor por escena y lo destruye al salir. Abrir y cerrar ligandos muchas veces, girar y pasar a segundo plano **no se ha probado**: es lo primero que probar (sección 10, filas 8–9).
8. **Selección de átomos tapados.** El rayo devuelve **el primer nodo** que corta, que puede ser **una varilla** delante de una bola: tocar justo en una varilla **deselecciona**, aunque detrás haya un átomo.
9. **Un átomo oculto detrás de otro** no se puede tocar sin girar (es lo normal en 3D).
10. **Iluminación:** la luz es la que SceneView pone por defecto. El spike habló de «dos luces»; **no se ha comprobado qué hay**. Las bolas se ven con volumen si hay sombreado; si salen planas, es la luz.
11. **Transparencia y fondo:** no se ha fijado el color de fondo. La barra superior es semitransparente para leerse sobre cualquiera.
12. **`X` (átomo desconocido) y símbolos raros:** salen en rosa fuerte con radio 0,55; la tarjeta dice «Unknown element» o el símbolo.
13. **Bola sin varillas:** un ion (`ZN`, `NA`) es **una sola bola** sin enlaces; se dibuja bien (hay una prueba del radio de un átomo solo). El encuadre da 6 Å como mínimo.
14. **Coordenadas guardadas:** la escena no cambia el `Molecule`; solo lo lee.

---

## 13. Qué está verificado y qué no

**Verificado (hecho):**
- **189 pruebas** pasan en la JVM desde una compilación limpia: las 162 anteriores + **27 nuevas** de `scene/` (10 de elementos, 8 de geometría, 9 de encuadre).
- **La fórmula de los ángulos de los cilindros es correcta como geometría**: con matrices, para 2000 pares al azar, los seis ejes y los siete enlaces del spike, el eje obtenido coincide con la dirección del enlace (error < 0,0001).
- **Los 50 elementos del corpus** tienen color y nombre.
- **Mutación:** 18 averías en las tres piezas puras; 17 detectadas, **1 equivalente** (sección 14).
- Los tipos del código de Compose son coherentes contra **mis** piezas de relleno.

**NO verificado (honesto):**
- **Nada de la escena se ha compilado con Compose ni SceneView reales.** Las llamadas son **las del spike** (`SphereNode`, `CylinderNode`, `Node`, `rememberCameraManipulator`, `rememberOnGestureListener`, `collisionShape`, `createColorInstance`),
  más tres cosas nuevas que **no** estaban en el spike: `key(...)` dentro del contenido de `SceneView`, un `HashMap<String, MaterialInstance>` (con `import com.google.android.filament.MaterialInstance`) y `FilterChip`/`Card`/`Surface` de Material 3.
  Si el compilador protesta, pega el error.
- **El aspecto:** colores, tamaños, encuadre, brillo. Todo son estimaciones hasta verlo.
- **El rendimiento (H6), el ciclo de vida (H5), la captura (H4)**, el desplazamiento con dos dedos y que los nuevos límites de zoom se **sientan** bien (los valores 4 Å y × 3 son estimaciones).
- **Verificado leyendo el código de SceneView 4.69.4 (no en el móvil):** que el tope de zoom es relativo a la distancia inicial (0,15 × y 8 ×), que los factores se leen en cada gesto, y que el plano lejano es 1000.
- **Los tonos exactos de la tabla de colores** de los metales raros.
- Que `key(i)` dentro del contenido de `SceneView` **funcione como espero** con los nodos de SceneView (es el comportamiento estándar de Compose; sobre SceneView no está comprobado).

---

## 14. Pruebas y mutación

| Fichero | Pruebas | Qué demuestra |
|---------|--------:|---------------|
| `ElementStyleTest.kt` | 10 | Los 50 elementos del corpus tienen color y no el desconocido; H/C/N/O/S/P tienen sus colores CPK; los cinco más frecuentes se distinguen; `X` y los desconocidos salen rosa; las mayúsculas importan; colores en tres bytes; radios entre 0,2 y 0,6 con H el menor; la bola no se come el enlace más corto; nombres legibles |
| `BondGeometryTest.kt` | 8 | Seis ejes, siete enlaces del spike y 2000 pares al azar apuntan bien (matrices); cambiar A y B solo invierte el eje; punto medio y longitud; átomos coincidentes → sin pose; enlace corto real sí se dibuja; sin `NaN` |
| `SceneFramingTest.kt` | 13 | Radio = más lejano + su bola (aunque no sea el último); un átomo solo; hidrógenos ocultos cuentan; distancia mínima; proporcional; nunca decrece; la cámara siempre fuera; el `001` real da un radio y una distancia razonables; **límites de zoom**: el acercamiento es 4 Å para ligandos de cualquier tamaño, `15P` se acerca mucho más que con el valor de SceneView, un ligando diminuto puede acercarse (factor ≤ 0,5), y el alejamiento máximo deja `15P` dentro del plano lejano |

**Mutación** (se estropea el código a propósito y se comprueba que alguna prueba **falla**):

| # | Avería | Resultado | La detecta |
|---|--------|-----------|------------|
| G1 | `spin = atan2(dz, dx)` (sin el menos) | Muere | las tres de direcciones |
| G2 | Quitar `coerceIn` del `acos` | **Sobrevive (equivalente)** | Nadie: con `Double` el cociente no puede superar 1 (sección 6) |
| G3 | `spin = atan2(-dz, -dx)` | Muere | las tres de direcciones |
| G4 | El punto medio usa solo B | Muere | `theCylinderIsCentered...`, `theOrderOfTheTwoAtoms...` |
| G5 | No descartar átomos coincidentes | Muere | `twoAtomsAtTheSamePlace...` |
| G6 | La longitud ignora `dz` | Muere | las tres de direcciones |
| G7 | `MIN_LENGTH = 1` (descarta enlaces reales) | Muere | `aVeryShortButRealBond...` |
| F1 | El radio no suma la bola | Muere | 3 pruebas de radio |
| F2 | El radio toma el **último** átomo, no el más lejano | **Sobrevivía; se escribió una prueba** y ahora muere | `theFarthestAtomCounts...` |
| F3 | `DISTANCE_PER_RADIUS = 1,5` | Muere | `aBigMolecule...`, `theReal001...` |
| F4 | Sin distancia mínima | Muere | `aSmallMoleculeStillGetsTheMinimumDistance` |
| E1 | El oxígeno azul | Muere | `theCommonElements...`, `theFiveMostFrequent...` |
| E2 | Falta el zinc | Muere | `theCorpusHas50Elements...` |
| E3 | Radio del H = 0,45 | Muere | 2 pruebas de radio |
| E4 | `isKnown` siempre verdadero | Muere | 2 pruebas |
| E5 | Lo desconocido sale blanco | Muere | `anUnknownElement...` |
| E6 | El nombre del hierro es «Fe» | Muere | `everyElementHasAReadableName` |
| E7 | El carbono blanco | Muere | 2 pruebas |

**Resultado: 17 de 18 mueren; 1 equivalente (G2).** **No hay pruebas ni mutación** de `MoleculeScene` ni `MoleculeScreen`: necesitan Android y SceneView.

---

## 15. Alternativas

| Tema | Lo que hacemos | Alternativa | Por qué no (todavía) |
|------|---------------|-------------|----------------------|
| Un nodo por átomo | `SphereNode` por átomo y dos nodos por enlace | **Una malla fundida** con todas las esferas y cilindros (más rápida) | La selección habría que calcularla a mano (rayo contra esferas). Plan B si H6 falla |
| Instancias | Material por elemento | **Instanciado** (`instancing`): una esfera dibujada 256 veces | Depende de que SceneView lo exponga; no se ha mirado |
| Dobles y triples | Una varilla | Varillas paralelas (con un desplazamiento perpendicular al enlace) | Pendiente; el desplazamiento se puede calcular como función pura probable en la JVM |
| Color de varilla | Gris | Mitad y mitad con el color de cada átomo | Doble de cilindros; mejora de aspecto |
| Modo de relleno | Bolas y varillas | Esferas de van der Waals (rellenar espacio) | No pedido; oculta los enlaces |
| Hidrógenos | Visibles con interruptor | Ocultos por defecto en moléculas grandes | Esperar a medir |
| Colores | Tabla Jmol | Tabla «CPK» clásica (carbono negro); colores por cadena | Gusto; es cambiar una tabla |
| Encuadre | Radio × 5,5 | Calcular con el campo de visión real y el formato de la pantalla | Hay que leer la API de la cámara; con el número a mano se ajusta en un minuto |
| Radios | Tamaño de dibujo (0,25–0,55 Å) | Radios covalentes reales escalados | Más cercano a la química, más trabajo; los valores de dibujo se ven bien en ball-and-stick |
| Nombre del elemento | Inglés en el código | Recurso traducible | D20 |
| Centro de giro | Siempre el centro de la molécula | **Doble toque en un átomo → ese átomo es el nuevo centro** (pivote móvil); o un botón «recentrar» | Lo que haría falta si el desplazamiento no funciona; hay que crear el gestor de cámara a mano. Pendiente de medir |
| Límite de zoom | Absoluto (4 Å) | Proporcional al radio (el de SceneView, 0,15 ×) | **Es la causa del fallo de `15P`** (sección 7.1) |
| Motor propio | SceneView/Filament | OpenGL ES a mano; o Canvas 2D con proyección | Más trabajo; el spike ya validó SceneView (D7) |

---

## 16. Decisiones

| # | Decisión | Estado | Motivo |
|---|----------|--------|--------|
| D21 | La paleta de colores es la tabla **«CPK» de Jmol** (escrita de memoria), con rosa fuerte para lo desconocido; los radios son de **dibujo** (0,25–0,55 Å), no físicos | Provisional | Convención reconocible; cambiar una tabla es trivial. Los tonos de metales raros sin comprobar |
| D22 | Se dibujan **todos los átomos, hidrógenos incluidos**, con un interruptor «H» para ocultarlos; el encuadre cuenta siempre todos | Decidida | El 48 % de los átomos del corpus son hidrógenos: ocultarlos sin avisar cambiaría la molécula, y dejarlos sin interruptor cargaría la escena |
| D23 | La escena es **un nodo por átomo y dos por enlace**, con la distancia inicial de la cámara en **5,5 × radio** | Provisional | Lo más simple y con selección gratis; el rendimiento (H6) y el encuadre se miden en el A33 y se ajustan; plan B: una malla |
| D24 | Los límites de zoom son **absolutos en ångström** (acercamiento mínimo 4 Å; alejamiento máximo 3 × la distancia inicial), no relativos al tamaño del ligando | Provisional | La causa del tope de `15P` se leyó en el código de SceneView; el valor se ajusta en el móvil |

---

## 17. Qué recordar

- **El 3D se decide en funciones puras** (`ElementStyle`, `BondGeometry`, `SceneFraming`) que se prueban en el ordenador; la escena solo las traduce a nodos.
- **Un cilindro nace de pie**; para unir dos átomos se le da **un ángulo de inclinación y otro de giro**, con dos nodos anidados. Está **demostrado con matrices**.
- **`key(i)`** da identidad estable a cada átomo; sin él, ocultar los hidrógenos mezclaría nodos.
- **Casi la mitad de los átomos son hidrógenos**, de ahí el interruptor «H».
- **Todo lo numérico de aspecto es una estimación** (`DISTANCE_PER_RADIUS`, `BALL_DETAIL`, `MIN_TOUCH_RADIUS`, `radiusOf`): son constantes con nombre, listas para ajustar viendo el móvil.
- **El zoom de SceneView es relativo** a la distancia inicial (0,15 × .. 8 ×): con ligandos grandes el tope de acercamiento crece con ellos. Lo arreglan dos constantes en ångström (`MIN_CLOSE_DISTANCE`, `MAX_ZOOM_OUT_FACTOR`).
- **No se ha comprobado en el móvil**: desplazamiento con dos dedos, el *tacto* de los límites nuevos, rendimiento, ciclo de vida, captura.
- **El código de Compose y SceneView no está compilado:** el primer compilador real puede traer errores de firma.
