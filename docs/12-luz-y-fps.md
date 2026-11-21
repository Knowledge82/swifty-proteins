# 12 · Luz de la escena y contador de FPS

> **Estado:** **escrito**. **Comprobado en el A33: 60 FPS, sin bajadas.** El contador (`FpsMeter`) está probado en la JVM (10 pruebas, 6 mutantes muertos). La luz y el contador en pantalla **compilan contra *stubs*** hechos con el código fuente de SceneView 4.69.4 (etiqueta `v4.69.4` de GitHub); en el móvil se ha comprobado el contador (60 FPS). Los valores de la luz son un **primer ajuste**: se juzgan a ojo en el A33.

## Índice

1. [Qué pide el enunciado](#1-qué-pide-el-enunciado)
2. [Lo que había: la luz por defecto de SceneView](#2-lo-que-había-la-luz-por-defecto-de-sceneview)
3. [Teoría: luz física en Filament](#3-teoría-luz-física-en-filament)
4. [Lo hecho: tres luces y el ambiente](#4-lo-hecho-tres-luces-y-el-ambiente)
5. [Teoría: qué es «FPS» si la escena dibuja a demanda](#5-teoría-qué-es-fps-si-la-escena-dibuja-a-demanda)
6. [El contador: `FpsMeter`](#6-el-contador-fpsmeter)
7. [En pantalla](#7-en-pantalla)
8. [Pruebas y mutación](#8-pruebas-y-mutación)
9. [Cómo probarlo y ajustarlo en el móvil](#9-cómo-probarlo-y-ajustarlo-en-el-móvil)
10. [Trampas](#10-trampas)
11. [Alternativas](#11-alternativas)
12. [Decisiones](#12-decisiones)
13. [Qué recordar](#13-qué-recordar)

---

## 1. Qué pide el enunciado

- **VI.4:** «la iluminación debe resaltar la estructura 3D (evitar un aspecto plano)»; «considerar **varias fuentes de luz** para una mejor percepción de la profundidad».
- **VI.4:** gestos «fluidos, objetivo de **60 FPS**»; «sin *lag*, tirones ni pérdida de fotogramas».
- **VIII.2 (errores comunes):** «rendimiento 3D pobre (**< 30 FPS**)».
- Objetivos del proyecto: «entender grafos de escena, **cámaras, iluminación y materiales**».

Hasta ahora la luz era la que SceneView pone por defecto (sin comprobar qué incluía) y los FPS **nunca se habían medido**.

---

## 2. Lo que había: la luz por defecto de SceneView

Leído en el código fuente de SceneView 4.69.4 (`SceneView.kt`, `SceneFactories.kt`):

| Luz | Tipo | Dirección (hacia dónde viaja) | Intensidad |
|-----|------|-------------------------------|------------|
| Principal («sol») | Direccional, con sombras | `(-0.25, -1, -0.25)`: casi **vertical, hacia abajo** | 10 000 lux |
| Relleno | Direccional, sin sombras | `(0.5, -0.5, 0.5)`: desde **atrás**-arriba-izquierda | 3 000 lux |
| Ambiente (IBL) | Imagen `neutral_ibl.ktx` | Todas | 10 000 |

O sea: **ya había varias luces**. Pero la cámara empieza **delante** (eje +z, un poco por encima) y ninguna de las dos direccionales ilumina de frente: la principal da en la **parte de arriba** de cada bola y el relleno en la cara **que no se ve**. Lo que el usuario ve primero lo ilumina sobre todo el ambiente, que es uniforme: el riesgo de «aspecto plano» que el enunciado pide evitar.

---

## 3. Teoría: luz física en Filament

- Filament usa **unidades físicas**: una luz direccional se mide en **lux** (iluminancia), como el sol (≈ 100 000 lux a mediodía). La cámara tiene una **exposición** (apertura, obturador, ISO) que decide cuánto de eso se ve claro.
- **Luz direccional:** rayos paralelos desde el infinito (como el sol). Solo tiene **dirección**: no importa dónde «esté». Una bola recibe más luz en la cara que mira **contra** la dirección del rayo.
- **IBL (*image-based lighting*):** una imagen de 360° del entorno que ilumina desde todas partes. Da el **ambiente** y los reflejos suaves; por sí sola no marca volumen.
- **Iluminación de tres puntos** (fotografía y cine):
  - **Principal (*key*):** la fuerte, de lado y un poco desde arriba: crea el sombreado que dice «esto es una esfera».
  - **Relleno (*fill*):** más débil, del otro lado: aclara la sombra sin borrarla.
  - **Contraluz (*rim*):** desde detrás: dibuja un borde claro que **separa** el objeto del fondo. Con fondo negro es lo que más «saca» las bolas.
- **Luces fijas en el mundo:** la cámara orbita; las luces no. Al girar la molécula cambia qué cara está iluminada, como al girar un objeto real bajo una lámpara. Es lo esperable y además **ayuda** a percibir la forma.

---

## 4. Lo hecho: tres luces y el ambiente

En `MoleculeScene.kt`:

| Luz | Dirección | Lux | Cómo |
|-----|-----------|-----|------|
| **Principal** | `(0.45, -0.65, -0.6)`: desde **arriba-delante-izquierda** | 10 000 | `mainLightNode = rememberMainLightNode(engine) { … }` |
| **Relleno** | `(-0.6, 0.15, -0.8)`: desde **delante-derecha**, un poco por debajo | 3 000 | `fillLightNode = rememberFillLightNode(engine) { … }` |
| **Contraluz** | `(0, -0.4, 0.9)`: desde **detrás**-arriba, hacia la cámara | 5 000 | `LightNode(type = DIRECTIONAL, …)` dentro de la escena (SceneView solo tiene parámetros para dos) |
| Ambiente | (el de SceneView) | 10 000 | Sin cambios |

- Las intensidades siguen el orden de magnitud de los valores por defecto (10 000 / 3 000): se cambia **de dónde** viene la luz, no **cuánta** hay. Así el riesgo de quemar los colores es bajo.
- Las tres están **escritas en nuestro código** con un comentario cada una: en la defensa se señala la línea, no «lo que hace la biblioteca».
- Los bloques `apply` de las luces son constantes: SceneView solo vuelve a pedir un fotograma si el bloque **cambia** (lo dice su código), así que declarar las luces no impide que la escena quieta se pare (sección 5).

---

## 5. Teoría: qué es «FPS» si la escena dibuja a demanda

- **FPS** = fotogramas **presentados** por segundo. A 60 Hz, un fotograma cada 16,7 ms; si uno tarda más, se **repite** el anterior y se nota un tirón.
- **SceneView 4 dibuja A DEMANDA por defecto** (`FrameRatePolicy.OnDemand`): con la escena quieta **no dibuja nada** (ni GPU ni CPU); se despierta con un toque, un gesto, una animación, un cambio de nodo, y sigue unos fotogramas tras el último cambio. Es una **optimización** de batería.
- Consecuencia: un contador ingenuo («fotogramas ÷ tiempo») marcaría **0 FPS** con la escena quieta, que es justo cuando todo va bien. Por eso el contador distingue **IDLE**: «no se dibuja porque no hace falta».
- **`onFrame`** de SceneView se llama **una vez por fotograma que llegó a la pantalla** (su documentación: un intento que `Renderer.beginFrame` rechazó no cuenta). Es exactamente lo que hay que contar: si la GPU no da abasto, hay **menos** llamadas, y el contador baja.

---

## 6. El contador: `FpsMeter`

`scene/FpsMeter.kt`, Kotlin puro:

- **`onFrame(t)`** guarda la marca de tiempo (nanosegundos, el reloj de `Choreographer`, que es el de `System.nanoTime`).
- **`reading(now)`** mira el **último segundo**: `fps = (n − 1) / (último − primero)`, redondeado; y el **hueco más largo** entre dos fotogramas (el peor fotograma, redondeado **hacia arriba**: nunca adorna). Si el último fotograma tiene más de **0,5 s**, o hay menos de dos, la lectura es **IDLE**.
- **Anillo de `LongArray`** (256 posiciones) y no una lista: `onFrame` corre 60–120 veces por segundo, y una `List<Long>` crearía un objeto por fotograma (*boxing*) para el recolector de basura, en el camino más caliente de la app.
- Una marca **anterior** a la última se ignora (no debería pasar; si pasara, rompería los huecos).
- **Nivel** (`FpsText.level`): **GOOD** desde 55 (60 Hz menos el baile normal), **FAIR** de 30 a 54, **POOR** por debajo de 30 (el «< 30 FPS» del enunciado), **IDLE**.

---

## 7. En pantalla

- En el panel superior de la molécula: **«87 ATOMS :: 90 BONDS :: 60 FPS»**, o **«… :: IDLE»** con la escena quieta.
- **Color por nivel:** fósforo (bien), ámbar (regular), rojo óptico (mal), ceniza (parada). El **número** dice lo mismo que el color: nunca solo el color.
- **En todas las compilaciones**, también *release* (decisión del equipo: es un proyecto de aprendizaje y el número es parte de lo que se enseña). En un producto iría tras un ajuste.
- El texto se refresca **4 veces por segundo** (no en cada fotograma: recomponer la pantalla 60 veces por segundo solo para un número costaría más que lo que mide).

---

## 8. Pruebas y mutación

`FpsMeterTest` (10 pruebas): nada dibujado → IDLE; 60 Hz → «60», peor fotograma 17 ms; 120 Hz → «120»; un tirón de 100 ms aparece en el peor fotograma; solo cuenta el último segundo (120 → 30); parada → IDLE a partir de 0,5 s (a 499 ms aún mide); un solo fotograma no es una tasa; el anillo sigue bien tras darse la vuelta muchas veces; una marca del pasado se ignora; texto y niveles en sus límites (55/54, 30/29).

**Mutantes, todos muertos:** quitar la regla de IDLE, quitar la ventana de un segundo, redondear el peor fotograma hacia abajo, aceptar un solo fotograma como tasa, cambiar `>= 55` por `> 55`, aceptar marcas del pasado.

---

## 9. Cómo probarlo y ajustarlo en el móvil

1. Abrir una molécula y no tocar nada: **IDLE** (gris).
2. Girarla sin parar: **~60 FPS** en fósforo (el A33 tiene pantalla de 90 Hz: puede marcar **hasta 90**; es correcto).
3. Una molécula grande (p. ej. una de ~250 átomos con hidrógenos): ¿baja de 55? ¿de 30? Anotar cuál y cuánto.
4. **La luz:** ¿se ven las bolas con volumen (lado claro, lado oscuro, borde claro contra el negro)? ¿Algún color se «quema» (el blanco del hidrógeno y el gris del carbono deben seguir distinguiéndose)? Si hace falta, se ajustan las constantes `*_LIGHT_LUX` y `*_LIGHT_DIRECTION` al principio de `MoleculeScene.kt`: cada una tiene su comentario.

---

## 10. Trampas

1. **«0 FPS» con la escena quieta** no es un fallo: es el dibujo a demanda. Por eso IDLE.
2. **No forzar `FrameRatePolicy.Continuous` para «enseñar 60»:** gastaría batería dibujando lo mismo; el número sería verdad pero el diseño, peor.
3. **`adb shell dumpsys gfxinfo`** mide la interfaz de Android, no el `SurfaceView` de Filament: para la escena 3D el dato bueno es el nuestro.
4. **Pantallas de 90/120 Hz:** el techo no es 60. «90 FPS» no es un error.
5. **Medir en *debug* frente a *release*:** la compilación *debug* es más lenta (sin optimizar). Si en *debug* va bien, en *release* irá igual o mejor.
6. **Demasiada luz** satura los colores CPK claros (H blanco, S amarillo). Ajustar bajando intensidades, no subiendo la exposición.

---

## 11. Alternativas

| Alternativa | Por qué no (por ahora) |
|-------------|------------------------|
| Dejar la luz por defecto | Ya eran varias luces, pero ninguna de frente: riesgo de aspecto plano; y no se podía señalar en el código |
| Luz pegada a la cámara (*headlight*) | Siempre iluminado de frente, pero **más plano** (sin sombreado que cambie al girar) |
| Mapa de entorno HDR propio | Más bonito, pero un fichero grande y otro elemento que justificar |
| Contador con `Choreographer` propio | Contaría los **vsync**, no los fotogramas **presentados** por Filament |
| Superposición de depuración de Filament | Más datos, pero no la puede leer un evaluador de un vistazo |

---

## 12. Decisiones

| # | Decisión | Estado | Motivo |
|---|----------|--------|--------|
| D45 | Iluminación de **tres puntos** escrita en nuestro código (principal arriba-delante-izquierda, relleno delante-derecha, contraluz desde detrás) + el ambiente IBL de SceneView | Decidida (valores a ajustar en el móvil) | VI.4 pide volumen y varias luces; la de serie no iluminaba lo que se ve primero |
| D46 | **Contador de FPS** de los fotogramas **presentados** (`onFrame`), con estado **IDLE**, en **todas** las compilaciones | Decidida | VI.4 / VIII.2 piden 60 y castigan < 30: hace falta un número; el dibujo a demanda obliga a distinguir «parado» de «lento» |

---

## 13. Qué recordar

- **Varias luces = varias direcciones con papeles distintos:** principal (forma), relleno (sombra suave), contraluz (silueta).
- Una luz **direccional** solo tiene dirección; la intensidad en Filament va en **lux**.
- **Dibujo a demanda:** una escena quieta no dibuja. FPS se mide **mientras se mueve**; quieta es IDLE, no «0».
- **Contar lo presentado**, no lo intentado (`onFrame`).
- En el camino caliente, **nada de objetos por fotograma** (anillo de `LongArray`).
