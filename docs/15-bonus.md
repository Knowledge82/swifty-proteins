# 15 · Bonus: fórmula, nombre, modelos de visualización, enlaces, resaltado y doble toque

> **Estado:** **escrito**. Toda la lógica es Kotlin puro y está **probada en la JVM**: 45 pruebas nuevas (410 en total) y **19 mutantes, todos muertos**. La fórmula se comparó con la oficial de RCSB en los **1242 ligandos del corpus: 1242 iguales**. La escena 3D y la pantalla **compilan contra *stubs*** hechos con el código fuente de SceneView 4.69.4; **sin probar en el móvil** (sección 10).

## Índice

1. [Qué piden los bonus y qué se hizo](#1-qué-piden-los-bonus-y-qué-se-hizo)
2. [La fórmula molecular (VII.5)](#2-la-fórmula-molecular-vii5)
3. [El nombre del ligando (VII.5)](#3-el-nombre-del-ligando-vii5)
4. [El mensaje de compartir (VII.5)](#4-el-mensaje-de-compartir-vii5)
5. [Cuatro modelos de visualización (VII.1)](#5-cuatro-modelos-de-visualización-vii1)
6. [Tocar un enlace: tipo y longitud (VII.3)](#6-tocar-un-enlace-tipo-y-longitud-vii3)
7. [Resaltar los átomos de un elemento (VII.3)](#7-resaltar-los-átomos-de-un-elemento-vii3)
8. [Doble toque: orbitar alrededor de un átomo (VII.3)](#8-doble-toque-orbitar-alrededor-de-un-átomo-vii3)
9. [Pruebas, mutación y comprobación con el corpus](#9-pruebas-mutación-y-comprobación-con-el-corpus)
10. [Cómo probarlo en el móvil](#10-cómo-probarlo-en-el-móvil)
11. [Trampas](#11-trampas)
12. [Alternativas y lo que no se hizo](#12-alternativas-y-lo-que-no-se-hizo)
13. [Decisiones](#13-decisiones)
14. [Qué recordar](#14-qué-recordar)

---

## 1. Qué piden los bonus y qué se hizo

El enunciado lista cinco bloques (documento 01, §15.3) sin decir cómo puntúan. Criterio: primero lo que **completa un punto** con poco código y mucha lógica comprobable; después, lo que se ve en la defensa.

| Bloque | Lo que pide | Hecho aquí | Antes |
|--------|-------------|------------|-------|
| VII.1 Modelos | Espacio lleno (van der Waals), alambre, *stick*, cambio en tiempo real sin recargar | **Bolas y varillas, espacio lleno, varillas, alambre**; una tecla los recorre; no se descarga ni se interpreta nada de nuevo | — |
| VII.2 Interfaz | Celdas propias, animaciones, modo oscuro, *onboarding*, ajustes | — (nada nuevo) | Celdas de consola, animaciones, tema oscuro también en 3D |
| VII.3 Interacción | Resaltar mismo elemento, información de enlaces (tipo y longitud), medir distancias y ángulos, etiquetas, centrar con doble toque | **Resaltar mismo elemento**, **información de enlaces**, **doble toque para orbitar un átomo** | — |
| VII.4 Rendimiento | Caché, carga perezosa, *parsing* en segundo plano, memoria, 60 FPS | — (nada nuevo en esta ronda; **después**, documento 20: etapas de carga, nivel de detalle, montaje por lotes) | Caché (13), FPS (12), lista perezosa |
| VII.5 Compartir | Mensaje personalizado (**nombre, nº de átomos, fórmula**), formatos, vídeo, favoritos, comparar | **Nombre** (del fichero) y **fórmula** en el mensaje y en pantalla | Imagen con nombre, átomos y enlaces (09) |

---

## 2. La fórmula molecular (VII.5)

### 2.1 Teoría: el sistema de Hill

Una fórmula molecular dice **cuántos átomos de cada elemento** hay (no cómo están unidos). El orden no es libre: las bases de datos químicas y los catálogos usan el **sistema de Hill** (Edwin Hill, 1900):

1. **Con carbono:** primero **C**, después **H**, después **todos los demás por orden alfabético**.
2. **Sin carbono:** **todos** por orden alfabético, **H incluido**. El cloruro de hidrógeno se escribe `ClH`, no `HCl`; el agua, `H2O`; el amoníaco, `H3N`.
3. Un recuento de **1 no se escribe**: `CH4O`, no `C1H4O1`.

> Ejemplo: ATP tiene 10 C, 16 H, 5 N, 13 O, 3 P. Con carbono: C, H y luego N, O, P alfabéticamente → **C10H16N5O13P3**. Si una molécula tuviera Br, B y Cl además de C y H, la H iría **antes** que B aunque alfabéticamente B < H: con carbono, H es siempre la segunda.

### 2.2 El código: `MolecularFormula`

```kotlin
fun of(atoms: List<Atom>): List<Part> {
    val counts = atoms.groupingBy { it.element }.eachCount()        // {"C"=10, "H"=16, ...}
    val withCarbon = "C" in counts
    return counts.entries
        .sortedWith(compareBy({ if (withCarbon) hillRank(it.key) else 0 }, { it.key }))
        .map { Part(it.key, it.value) }
}
```

- `groupingBy { }.eachCount()` cuenta en una pasada, sin listas intermedias.
- `compareBy(a, b)`: ordena por `a` y, a igualdad, por `b`. `hillRank` da C = 0, H = 1, resto = 2; **sin carbono** el primer criterio vale 0 para todos y solo cuenta el alfabético.
- Los símbolos ya vienen normalizados por el *parser* (`Cl`, no `CL`, documento 03): el orden alfabético de cadenas es el químico (`B` < `Br` < `C` < `Ca` < `Cl`).
- Tres salidas: `plain` (`C10H16N5O13P3`, mensajes), `spaced` (`C10 H16 N5 O13 P3`, el estilo del CCD, para compararlo) y `of` (las partes, para dibujar subíndices).

**Contada, no leída del fichero.** El CCD trae su propia fórmula (`_chem_comp.formula`), pero contarla de los átomos garantiza que lo que dice la fórmula es **exactamente lo que se dibuja**. La sección 9 compara las dos en todo el corpus.

### 2.3 Los subíndices en pantalla: tipografía, no Unicode

Unicode tiene dígitos subíndice (₀ … ₉, U+2080–U+2089). La tentación es escribir «C₁₀H₁₆…» y listo. Se comprobó con `fontTools` y **ninguna de nuestras dos fuentes** (Pirata One, Share Tech Mono) tiene esos glifos. Android no deja un hueco: busca el carácter en **otra** fuente del sistema, de modo que los números saldrían con otra letra, otro grosor y otra altura en mitad de la fórmula.

Solución: los subíndices se **dibujan** con nuestra fuente. Un `AnnotatedString` de Compose aplica a cada recuento un estilo de tramo (`SpanStyle`) con `baselineShift = BaselineShift.Subscript` (bajado) y `fontSize = 0.75.em` (al 75 % del tamaño del texto que lo rodea):

```kotlin
buildAnnotatedString {
    for (part in MolecularFormula.of(molecule.atoms)) {
        append(part.element)
        if (part.count > 1) withStyle(SpanStyle(fontSize = 0.75.em, baselineShift = BaselineShift.Subscript)) {
            append(part.count.toString())
        }
    }
}
```

`em` es una unidad **relativa** al tamaño de la fuente actual: si mañana cambia el estilo, los subíndices siguen en proporción.

### 2.4 Dónde se ve

- En el panel **AUSPEX READOUT** cuando no hay nada seleccionado: nombre, fórmula, instrucciones.
- En la **banda** de la imagen compartida y en el **mensaje** (sección 4), en texto plano.

---

## 3. El nombre del ligando (VII.5)

### 3.1 Dónde está

Cada fichero del CCD trae `_chem_comp.name`. En `001`, el real que tenemos en las pruebas, es un **campo de texto de varias líneas** (entre `;` al principio de línea, documento 03):

```
_chem_comp.name
;1-[2,2-DIFLUORO-2-(3,4,5-TRIMETHOXY-PHENYL)-ACETYL]-PIPERIDINE-2-CARBOXYLIC ACID
4-PHENYL-1-(3-PYRIDIN-3-YL-PROPYL)-BUTYL ESTER
;
```

### 3.2 Opcional y limpio

El nombre se lee en `CifExtractor.nameOf` y va a `Molecule.name` (`String?`, con `null` por defecto: ninguna prueba ni llamada anterior cambió). Reglas:

| Regla | Por qué |
|-------|---------|
| **Opcional:** sin nombre, o con uno raro, la molécula sigue siendo buena (`name = null`), **nunca** un `CifFailure` | El nombre no hace falta para dibujar; rechazar una molécula por él sería absurdo |
| `?` (desconocido) y `.` (no aplicable) en CIF → sin nombre | Son los «nulos» del formato |
| Los **caracteres de control** pasan a espacio | Vienen de la red y van a la pantalla y a un mensaje: un `\u0007` o un `\u0000` no tienen nada que hacer ahí |
| Cada racha de espacios y saltos de línea → **un** espacio | El salto de línea del campo de texto de 001 no es parte del nombre |
| **Máximo 400 caracteres**; más se corta con «…» | En los 1242 ligandos el más largo tiene **384** (1SZ), la mediana **39**, el percentil 99 **193** (medido en el CCD): 400 no recorta ninguno real y pone techo a un fichero malicioso |

En pantalla se muestran **dos líneas como mucho**, con «…» (`maxLines = 2`, `TextOverflow.Ellipsis`).

---

## 4. El mensaje de compartir (VII.5)

`ShareText.message` (el `EXTRA_TEXT` del *intent*, documento 09):

```
Ligand ATP · ADENOSINE-5'-TRIPHOSPHATE
C10H16N5O13P3 · 47 atoms · 49 bonds
Viewed in Swifty Proteins
```

Sin nombre, la primera línea es «Ligand ATP». La **banda** impresa bajo la imagen (`ShareText.caption`) añade la fórmula: «Swifty Proteins · ATP · C10H16N5O13P3 · 47 atoms · 49 bonds». Ambas con **dígitos normales**: la banda se dibuja en un `Canvas` con una fuente que tampoco tiene subíndices, y el mensaje lo lee otra app con fuentes que no controlamos.

---

## 5. Cuatro modelos de visualización (VII.1)

### 5.1 Teoría: qué enseña cada modelo

| Modelo | Átomos | Enlaces | Enseña bien | Esconde |
|--------|--------|---------|-------------|---------|
| **Bolas y varillas** (el de siempre) | Bolas pequeñas | Varillas finas grises | La **conectividad** y los ángulos | El tamaño real de los átomos |
| **Espacio lleno** (*space-filling*, «CPK») | Esferas del **radio de van der Waals** | Ninguno: las esferas se **solapan** | La **forma** y el volumen que ocupa la molécula (lo que «ve» una proteína) | Los enlaces y el interior |
| **Varillas** (*sticks*, *licorice*) | Del grueso del enlace (solo rótulas) | Gruesas, **cada mitad del color de su átomo** | El esqueleto, limpio; el color dice qué une cada varilla | Qué átomo es cuál, si no se miran las uniones |
| **Alambre** (*wireframe*) | Del grueso del enlace | Muy finas, cada mitad de su color | Moléculas grandes sin taparse | Volumen; es difícil tocar |

El modelo de espacio lleno se llama «CPK» por **Corey, Pauling y Koltun**, que fabricaron en los años 50–60 maquetas de plástico a escala de los radios de van der Waals. Los mismos tres dan nombre a la **convención de colores** que ya usamos (documento 07): de ahí la doble acepción de «CPK».

### 5.2 Los radios de van der Waals

El **radio de van der Waals** es la mitad de la distancia más corta a la que se acercan dos átomos **no enlazados**: el «tamaño» de un átomo cuando no comparte electrones. Es mucho mayor que la distancia de enlace (un C–C mide ~1,54 Å; un C tiene radio de van der Waals de 1,70 Å). Por eso en espacio lleno los vecinos **se solapan** más de un radio entero: ese solape **es** el enlace (lo comprueba `inSpaceFillingNeighboursOverlapThatIsTheBond`).

- **Fuente:** Mantina, Chamberlin, Valero, Cramer y Truhlar, *Consistent van der Waals Radii for the Whole Main Group*, J. Phys. Chem. A 113 (2009), tabla 12, DOI 10.1021/jp8111556. Valores transcritos de la copia en QCElemental (MolSSI). Extienden los clásicos de **Bondi (1964)** a todo el **grupo principal**; el H es **1,10 Å** (Rowland y Taylor) en lugar del 1,20 de Bondi.
- **Hueco:** la tabla **no** tiene metales de transición, lantánidos ni actínidos (no hay un conjunto coherente para ellos). Se les da **`FALLBACK = 2,0 Å`**, elección **nuestra** del orden de sus vecinos. Afecta a **55 de los 1242** ligandos (contado en el CCD): Fe en 11, Cu y Ru en 5, Pt en 4, Mo, V y Co en 3…, y al elemento desconocido `X` de uno.
- `VanDerWaals.isTabulated(e)` dice si un radio es de la tabla o el de reserva (para no presentarlo como dato cuando es una estimación).

### 5.3 El código: `Representation`

Un `enum` **puro** responde todo lo que la escena necesita, sin Android:

```kotlin
fun atomRadius(element: String, bonded: Boolean): Float = when (this) {
    BALL_AND_STICK -> ElementStyle.radiusOf(element)                    // los de siempre (0,25–0,55 Å)
    SPACE_FILLING  -> VanDerWaals.radiusOf(element)                     // 1,1–2,0 Å
    STICKS         -> if (bonded) STICK_RADIUS else LONE_ATOM_RADIUS    // 0,2 / 0,35
    WIREFRAME      -> if (bonded) WIRE_RADIUS  else LONE_ATOM_RADIUS    // 0,05 / 0,35
}
val bondRadius: Float?            // 0,12 · null (sin enlaces) · 0,2 · 0,05
val bondsInAtomColours: Boolean   // varillas y alambre: dos mitades
fun haloRadius(element, bonded)   // 1,5 × la bola, nunca menos de 0,45
fun next()                        // para la tecla: B&S → espacio lleno → varillas → alambre → B&S
```

Tres detalles con su porqué:

1. **El átomo solitario.** En varillas y alambre un átomo es una rótula del grueso del enlace. Un **ion** (ZN, un átomo sin enlaces) o un átomo cuyos únicos vecinos son hidrógenos **ocultos** se quedaría en un punto de 0,05 Å: invisible e imposible de tocar. `bonded` = «tiene al menos un enlace **dibujado**» (se recalcula al ocultar hidrógenos) y el solitario recibe 0,35 Å.
2. **El halo mínimo.** El halo de selección es 1,5 veces la bola; alrededor de una rótula de alambre serían 0,075 Å, invisible. Nunca menos de 0,45 Å.
3. **Mitades de color** (`BondGeometry.halvesOf`): un enlace se parte en dos cilindros, del átomo A al punto medio y del punto medio a B, cada uno con el material del elemento de su extremo. Reutiliza `poseOf` (la orientación ya probada con matrices, documento 07) con un «átomo» ficticio en el punto medio.

### 5.4 Cambiar en vivo: por qué `key(…, representation)`

La pantalla guarda `representation` con `rememberSaveable` (sobrevive al giro) y se la pasa a la escena: cambiarla es **recomponer** con otro valor. Nada se descarga ni se interpreta de nuevo.

En el código fuente de SceneView, `SphereNode` **sí** actualiza su geometría si cambia `radius` (un `SideEffect` llama a `updateGeometry`). Pero la **forma de toque** (`collisionShape`) se pone en `apply`, que **solo se ejecuta al crear el nodo**. Un nodo reutilizado con un radio nuevo seguiría respondiendo a los toques con el tamaño viejo: en espacio lleno, tocar el borde de una esfera de 1,7 Å no seleccionaría nada (la forma de toque seguiría en 0,4). Por eso los nodos llevan como clave el modelo (y `bonded`): `key(i, representation, bonded[i])`. Al cambiar de modelo, Compose **destruye y crea** los nodos con su forma de toque correcta.

**Coste:** cambiar de modelo recrea todos los nodos (hasta 256 átomos y ~540 mitades de enlace). Es un instante puntual, no por fotograma; **sin medir en el A33**.

**Detalle de malla:** las esferas de espacio lleno ocupan unas 4 veces más pantalla y se les verían las facetas: 24 bandas en vez de 20 (≈ 295 000 triángulos con 256 átomos frente a ≈ 200 000). Si el contador de FPS (documento 12) baja de 55 en espacio lleno con un ligando grande, este es el primer número a tocar.

**Encuadre:** la cámara sigue encuadrando con los radios de bolas y varillas. En espacio lleno la molécula «crece» hasta 1,5 Å por cada lado, de sobra dentro del margen de 5,5 radios (documento 07). Así **la cámara no salta** al cambiar de modelo.

### 5.5 En pantalla

> **Nota posterior (documento 19):** la tecla pasó a ser **MODEL…**, una **lista desplegable** con los cuatro modelos (VII.1 pide «control segmentado, desplegable o botones»).

La tecla del modelo está en la cabecera del panel **Elements / CPK key** y muestra el modelo actual («BALL & STICK», «SPACE-FILLING», «STICKS», «WIREFRAME»). No cabía en el panel superior: con «Pattern 001» a 32 sp, la tecla atrás y las teclas H y SHR, una tercera tecla dejaba ~95 dp para el título. Su lugar natural es junto a la leyenda: los dos dicen **cómo** se dibuja la molécula. TalkBack la anuncia como «Model: Ball & stick. Change the model».

---

## 6. Tocar un enlace: tipo y longitud (VII.3)

### 6.1 Qué se toca: `ScenePick`

La escena nombra sus nodos «atom 12» y «bond 3» (las mitades de un enlace se llaman igual que el enlace). SceneView devuelve el **nodo** tocado; `ScenePick.of(nombre)` lo convierte en `Atom(12)`, `Bond(3)` o `null` (vacío, halos, luces, cualquier otro nombre, índices negativos). Antes, un toque en una varilla se trataba como «nada»; ahora es una selección.

### 6.2 Qué se muestra: `BondText`

```
C1 – O2 · Double bond
Atoms      Carbon – Oxygen
Length     1.230 Å
```

- **Orden:** lo que dice `value_order` del CCD: `SING`, `DOUB`, `TRIP`, `AROM`. Cualquier otro (`QUAD`, `DELO`, `PI`, `POLY` o la columna ausente) se leyó como `OTHER` (documento 03) y se muestra como «Other bond»: **no se adivina**.
- **Longitud:** distancia euclídea entre los dos átomos, √(Δx² + Δy² + Δz²), con las coordenadas que eligió el *parser* (las ideales del CCD, o las del modelo si las ideales no son completas o creíbles; documento 03). Tres decimales, como las coordenadas. Ejemplos reales de referencia: C–C sencillo ≈ 1,54 Å, C=O ≈ 1,23 Å, C–H ≈ 1,09 Å.
- **Halo:** un tubo translúcido verde alrededor de la varilla, 2,2 veces su grosor (y nunca más fino que 0,3 Å: en alambre la varilla mide 0,05).

### 6.3 Lo que se ve y lo que no: `Drawn`

La pantalla **recuerda** el índice del átomo **o** del enlace seleccionado (como mucho uno). `Drawn.selection` decide qué se **enseña**:

- un átomo, si existe y no es un hidrógeno oculto;
- un enlace, si existe, el modelo dibuja enlaces (en espacio lleno **no**) y **sus dos** átomos están dibujados.

El índice **se conserva** aunque no se enseñe: ocultar los hidrógenos y volver a mostrarlos, o pasar por espacio lleno y volver, **recupera** la selección. Es el mismo principio de antes (documento 07), ahora en una función pura con su prueba.

---

## 7. Resaltar los átomos de un elemento (VII.3)

- **Cómo:** tocar un elemento en la **leyenda CPK** pone un halo **dorado** a **todos** los átomos dibujados de ese elemento. Tocarlo otra vez lo quita; tocar otro cambia el resaltado.
- **Por qué dorado:** el verde es la **selección** (un átomo, un enlace); el dorado (la tinta de los datos del diseño) es «todos los de esta clase». Un átomo seleccionado que además es del elemento resaltado lleva solo el verde (dos halos encima se verían turbios).
- **Accesibilidad:** cada elemento de la leyenda es un botón de **48 dp** de alto (la medida mínima de toque de Android), con descripción («Carbon»), estado («highlighted» / «not highlighted») y acción («Highlight every atom of this element»). El elemento resaltado lleva **borde** dorado y letra dorada: el estado nunca depende solo del color.
- **Hidrógenos:** si se ocultan con H resaltado, el resaltado se quita (el elemento desaparece de la leyenda; un resaltado de algo que no se puede ver ni desmarcar sería un estado fantasma).

---

## 8. Doble toque: orbitar alrededor de un átomo (VII.3)

### 8.1 Lo que había: SceneView ya usa el doble toque

Leyendo `CameraGestureDetector` de SceneView 4.69.4: el manipulador por defecto trae **`isDoubleTapZoomEnabled = true`**: doble toque = acercar ×2; toque con dos dedos = alejar. Es decir, **hasta hoy el doble toque ya acercaba** la cámara (sin que lo hubiéramos decidido). Para darle otro significado hay que apagarlo; si no, los dos efectos ocurrirían a la vez.

### 8.2 Lo que hace ahora

| Doble toque en… | Efecto |
|-----------------|--------|
| Un **átomo** | La cámara pasa a **orbitar alrededor de ese átomo**, a 12 Å, y el átomo queda **seleccionado** |
| El **vacío** | Vuelve a orbitar el **centro** de la molécula, a la distancia inicial |
| Un **enlace** | Nada (un enlace no tiene un punto que sea «él») |

Resuelve además lo que el documento 07 dejó apuntado: ver de cerca los **extremos** de una molécula grande, que con el centro fijo quedaban siempre lejos.

### 8.3 Cómo: `SceneFraming.orbit`

```kotlin
fun orbit(focus: Atom?, wholeDistance: Float): Orbit {
    if (focus == null) return Orbit(0f, 0f, 0f, wholeDistance, minZoomFactor(wholeDistance), MAX_ZOOM_OUT_FACTOR)
    val distance = minOf(FOCUS_DISTANCE, wholeDistance)
    return Orbit(focus.x, focus.y, focus.z, distance, minZoomFactor(distance), MAX_ZOOM_OUT_FACTOR * wholeDistance / distance)
}
```

- **Centro y distancia:** el átomo y 12 Å (estimado: el átomo y sus vecinos ocupan el ancho del móvil). Si la molécula entera ya está más cerca (una pequeña), no se aleja.
- **Límites de zoom en ångström** (como en el documento 07, donde SceneView los da como **fracciones** de la distancia inicial): nunca más cerca de 4 Å; y, desde un átomo, se puede **alejar con los dedos hasta ver la molécula entera** (3 veces la vista completa, no 3 veces 12 Å), sin necesidad de un segundo doble toque.

En la escena, `rememberCameraManipulator(orbitRadius, targetPosition, creator)` **se reconstruye cuando cambian el radio o el objetivo** (está así documentado en SceneView). Al reconstruirse, SceneView **desliza** la cámara a la nueva órbita en ~0,6 s en lugar de cortar.

**Precio** (documentado por SceneView): una órbita nueva empieza en la vista de tres cuartos por defecto, no en el ángulo en que el usuario la tenía. Es lo que se espera de «centrar»: un salto de ángulo animado, no un corte.

### 8.4 Un toque y dos toques no se pisan

El toque simple llega por `onSingleTapConfirmed`: Android lo entrega **solo cuando está seguro** de que no era la primera mitad de un doble toque (espera el tiempo del doble toque, ~300 ms). Por eso un doble toque **no** selecciona dos veces ni deselecciona entre medias. El precio es esa pequeña espera en el toque simple, que ya existía.

---

## 9. Pruebas, mutación y comprobación con el corpus

### 9.1 La fórmula contra RCSB, en los 1242 ligandos

Un programa de comprobación (fuera de la app) interpreta los 1242 `.cif` del corpus con **nuestro** *parser* y calcula `MolecularFormula.spaced`. Un script de Python lee `chem_comp.formula` del CCD binario (`components.bcif`, el mismo que generó el corpus, documento 03a) y compara: **1242 de 1242 idénticas**, carácter a carácter. Valida a la vez el recuento, el orden de Hill, la normalización de símbolos del *parser* y el «1 no se escribe».

### 9.2 Pruebas nuevas (45)

| Clase | Pruebas | Qué fija |
|-------|--------:|----------|
| `MolecularFormulaTest` | 7 | Hill con y sin carbono (`ClH`, `H2O`, `H3N`), H segunda con carbono aunque B y Br vayan antes, símbolos de dos letras, recuento 1, estilo CCD, partes |
| `CifExtractorTest` (nombre) | 6 | Sin nombre → `null`, `?` y `.`, espacios y saltos, caracteres de control, corte con «…», exactamente 400 se conserva |
| `CifParserTest` (nombre) | 1 | El nombre real de 001, de dos líneas, sale en una |
| `ShareTextTest` | 3 nuevas / 2 cambiadas | Banda con fórmula, mensaje con y sin nombre, dígitos planos en la banda |
| `RepresentationTest` | 7 | Radios de cada modelo, sin enlaces en espacio lleno, el solape, rótulas, átomo solitario, halo mínimo, el ciclo de la tecla |
| `VanDerWaalsTest` | 3 | Valores de Mantina, metales a la reserva, plausibilidad física (atrapa un 17,0 o un 0,17) |
| `ScenePickTest` | 7 | Nombres de nodo → selección, todo lo demás → vacío; `Drawn`: hidrógenos, espacio lleno, índices fuera, qué selección se enseña |
| `BondTextTest` | 4 | Título, elementos, longitud 3D (triángulos 3-4-5 en dos planos), un nombre por orden y «Other» sin adivinar |
| `BondGeometryTest` | 3 | Las mitades se encuentran en el medio y apuntan igual, átomos coincidentes, longitud 3-4-12-13 |
| `SceneFramingTest` | 4 | Sin foco igual que antes, el átomo como centro, límites de zoom en Å desde el foco, una molécula diminuta no se aleja |

**Toda la batería: 410 pruebas verdes.**

### 9.3 Mutantes (19, todos muertos)

| Pieza | Mutantes |
|-------|----------|
| Fórmula | Hill siempre (también sin carbono) · sin Hill · escribir el 1 · ordenar por recuento |
| Nombre | Aceptar `?` · no limpiar caracteres de control · techo `<` en vez de `<=` · no juntar espacios |
| Modelos | Ignorar el átomo solitario · sin halo mínimo · la tecla no vuelve al principio · enlaces en espacio lleno |
| Selección | Índices negativos válidos · comprobar solo un extremo del enlace · no mirar el modelo |
| Textos y geometría | Orden de enlace cambiado · zoom máximo relativo al foco · foco sin `minOf` · mitades al revés |

**Sin prueba automática:** la escena (`MoleculeScene`) y la pantalla: nodos de SceneView y Compose. Se prueban en el móvil (sección 10).

---

## 10. Cómo probarlo en el móvil

1. **Fórmula y nombre:** abrir `ATP`; sin nada seleccionado, el panel de abajo dice «ADENOSINE-5'-TRIPHOSPHATE», la fórmula con **subíndices de la misma letra** (no de otra fuente) e instrucciones. Abrir `001`: el nombre largo en **dos líneas con «…»**.
2. **Compartir:** el mensaje en la app de destino tiene las tres líneas (nombre, fórmula · átomos · enlaces, la firma); la banda de la imagen, la fórmula.
3. **Modelos:** la tecla de la leyenda recorre los cuatro **sin recargar** (no aparece el módem ni el diálogo de carga). En espacio lleno, **tocar el borde** de una esfera grande la selecciona (prueba de la `collisionShape`, §5.4). En varillas, cada varilla es de **dos colores**. En alambre, un ion (`ZN`) sigue siendo una bola visible.
4. **FPS:** en espacio lleno, con un ligando grande (`15P`, 256 átomos) y girando: ¿se mantiene ≥ 55? (§5.4). ¿Cuánto tarda el cambio de modelo?
5. **Enlace:** tocar una varilla: tubo verde, «C1 – O2 · Double bond», elementos y longitud. Cambiar a espacio lleno: la selección del enlace desaparece; volver: reaparece.
6. **Resaltar:** tocar «C» en la leyenda: todos los carbonos con halo dorado y la «C» con borde. Otra vez: se quita. Con H resaltado, ocultar hidrógenos: el resaltado se va.
7. **Doble toque:** sobre un átomo de un extremo de `15P`: la cámara **se desliza** hasta orbitarlo de cerca y lo selecciona; girar: gira alrededor de él; pellizcar hacia fuera: se puede ver la molécula entera. Doble toque en el vacío: vuelve al centro. Y que el doble toque **ya no acerca** como antes.
8. **Girar el móvil** con todo activo (modelo, resaltado, foco, selección): todo se conserva.
9. **TalkBack:** la tecla de modelo y los elementos de la leyenda se anuncian con su estado.

---

## 11. Trampas

1. **Unicode subíndice sin glifos en la fuente:** Android lo toma de otra fuente, a media fórmula. Dibujar con `BaselineShift` y `em`.
2. **Hill con hidrógeno siempre segundo:** solo es así **con carbono**. Sin carbono, alfabético.
3. **Leer la fórmula del fichero en vez de contarla:** podría no coincidir con lo dibujado (y es otra cadena que validar). Contar, y comprobar contra el fichero en el corpus.
4. **Un nombre de la red directo a la pantalla:** caracteres de control, longitud sin límite. Limpiar y acotar; y que su ausencia nunca sea un error.
5. **Cambiar el radio de un nodo sin cambiar su forma de toque:** SceneView actualiza la malla pero no la `collisionShape` (que se pone en `apply`). Recrear el nodo con `key`.
6. **Átomos invisibles en modelos finos:** iones y átomos cuyos vecinos se ocultaron. Radio mínimo para los solitarios.
7. **Halos invisibles alrededor de cosas finas:** halo mínimo (átomo) y grosor mínimo (enlace).
8. **El doble toque de SceneView:** ya acercaba por defecto; si no se apaga, se suma al nuestro.
9. **Límites de zoom como fracción:** al cambiar la distancia de la órbita, las fracciones cambian de significado; se recalculan para que sigan siendo ångström.
10. **Una selección de algo que no se dibuja:** un enlace en espacio lleno, un hidrógeno oculto. Se recuerda, pero no se enseña.
11. **Radios de van der Waals de metales de transición presentados como dato:** no hay tabla coherente; el 2,0 es una **reserva** nuestra, y el código lo distingue (`isTabulated`).

---

## 12. Alternativas y lo que no se hizo

| Alternativa | Por qué no (o no todavía) |
|-------------|---------------------------|
| Usar `_chem_comp.formula` del fichero | Contada coincide en 1242/1242 y garantiza la coherencia con lo dibujado |
| Radios de **Bondi (1964)** | Solo cubren parte del grupo principal (y el H a 1,20); Mantina es su extensión coherente a todo él |
| Radios de van der Waals de **Alvarez (2013)** para los metales | Se obtienen con otro criterio (estadística de estructuras cristalinas): mezclar dos tablas en una misma escena no sería coherente. Opción si los metales importan |
| **Instancias** de malla (*instancing*) para miles de átomos | Con ≤ 256 átomos no hace falta; sería lo primero si el FPS en espacio lleno no aguanta |
| Superficies (*molecular surface*, SES/SAS) | Requieren calcular una malla (*marching cubes*): mucho más trabajo; espacio lleno da la idea de forma |
| Doble toque que **conserva el ángulo** de la cámara | Exigiría un manipulador propio (la clase `Manipulator` de Filament es final); el deslizamiento de SceneView basta |
| Pulsación larga para resaltar el elemento del átomo tocado | La leyenda ya lista los elementos y es accesible; la pulsación larga no se descubre sola |
| **Medir distancias y ángulos** entre átomos (VII.3) | **Hecho después**: la regla del documento 16 (también la torsión) |
| **Etiquetas** de átomos en 3D (VII.3) | Texto en 3D con SceneView (`ViewNode` o texturas): coste alto en rendimiento con 256 etiquetas |
| **Ajustes** (VII.2): modelo por defecto, hidrógenos | **Hecho después** (documento 16), con los sonidos |
| **Favoritos** (VII.5) | **Hecho después** (documento 16): por cuenta |
| Vídeo, comparar dos moléculas, otros formatos (VII.5) | Más trabajo que valor para la defensa |

---

## 13. Decisiones

| # | Decisión | Estado | Motivo |
|---|----------|--------|--------|
| D49 | **Fórmula** contada de los átomos, en orden de **Hill**; en pantalla con subíndices **tipográficos** (`BaselineShift`), en texto con dígitos planos | Decidida | Coincide con RCSB en 1242/1242; nuestras fuentes no tienen subíndices Unicode |
| D50 | **Nombre** del ligando (`_chem_comp.name`) **opcional**, limpio (control → espacio, espacios juntos, `?`/`.` = ninguno) y con techo de 400 caracteres | Decidida | VII.5 pide el nombre; viene de la red |
| D51 | **Cuatro modelos** (bolas y varillas, espacio lleno con radios de **Mantina 2009** y reserva de 2,0 Å, varillas y alambre con mitades de color), una tecla en la leyenda, cambio en vivo recreando los nodos | Decidida | VII.1 entero; la forma de toque obliga a recrear |
| D52 | **Tocar un enlace** = orden (sin adivinar) y longitud 3D; **tocar un elemento de la leyenda** = halo dorado a todos sus átomos | Decidida | VII.3; accesible (48 dp, estado en palabras) |
| D53 | **Doble toque** en un átomo = orbitarlo a 12 Å (y seleccionarlo); en el vacío = volver al centro; doble toque de zoom de SceneView **apagado** | Decidida | VII.3 «centrar con doble toque»; ver de cerca los extremos |

---

## 14. Qué recordar

- **Hill:** C, H, el resto alfabético; sin carbono, todo alfabético. El 1 no se escribe.
- **Comprobar contra la fuente de verdad en todo el corpus** vale más que diez ejemplos a mano.
- **Subíndices = tipografía**, no caracteres: una fuente sin el glifo hace que Android lo busque en otra.
- **Lo que viene de la red se limpia y se acota** aunque sea «solo un nombre»; y si falta, no es un error.
- **Van der Waals > distancia de enlace:** en espacio lleno los vecinos se solapan, y eso es el enlace.
- **Una lógica pura por pregunta** (`Representation`, `ScenePick`, `Drawn`, `BondText`, `SceneFraming.orbit`): la escena solo traduce respuestas a nodos.
- **Lo que una biblioteca pone en `apply` solo vive al crear:** si cambia, `key(…)` para recrear.
- **Antes de dar un significado a un gesto, mirar si la biblioteca ya le da otro** (el doble toque acercaba).
