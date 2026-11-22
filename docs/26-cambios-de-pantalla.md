# 26 · Cambios de pantalla: encender, entrelazar, cortar

> **Estado:** **escrito**. Sustituye la mitad «apagar» del tubo del documento 23. Reglas puras **probadas en la JVM** (632 pruebas en total; 15 nuevas; 48 mutantes, todos muertos al final). El cambio entre pantallas es un **entrelazado**: la pantalla vieja sale en bandas finas, primero las impares y luego las pares, y la nueva vuelve igual, 200 ms (§3). Las cinco versiones anteriores se describen en §3.3. El dibujo del entrelazado, el arranque que espera al tubo y el diálogo que espera en una carga silenciosa **compilan contra *stubs*** y pasan las reglas de Compose; **sin probar en el móvil** (§8).

## Índice

1. [Qué se pidió tras verlo en el móvil](#1-qué-se-pidió-tras-verlo-en-el-móvil)
2. [Tres maneras de cambiar de pantalla](#2-tres-maneras-de-cambiar-de-pantalla)
3. [El entrelazado](#3-el-entrelazado)
4. [El encendido, solo al arrancar, y el arranque que lo espera](#4-el-encendido-solo-al-arrancar-y-el-arranque-que-lo-espera)
5. [Abrir un ligando: descargado o archivado](#5-abrir-un-ligando-descargado-o-archivado)
6. [Cómo lo hace `CrtSwitch`](#6-cómo-lo-hace-crtswitch)
7. [Pruebas y mutación](#7-pruebas-y-mutación)
8. [Cómo probarlo en el móvil](#8-cómo-probarlo-en-el-móvil)
9. [Trampas](#9-trampas)
10. [Alternativas](#10-alternativas)
11. [Decisiones](#11-decisiones)
12. [Qué recordar](#12-qué-recordar)

---

## 1. Qué se pidió tras verlo en el móvil

El tubo del documento 23 era **un solo efecto en dos mitades**: la pantalla vieja **se apagaba** (se aplastaba en una línea y en un punto) y la nueva **se encendía** (el punto se estiraba en una línea y la línea se abría). Gustó, pero no siempre encajaba:

| Situación | Antes | Lo que se pidió |
|-----------|-------|-----------------|
| Arranque en frío | Encendido sobre la pantalla de arranque | **Igual**, pero que no se coma el principio del arranque (sus líneas y sus sonidos) |
| Abrir un ligando que hay que **descargar** | Diálogo de carga **y además** el tubo | **Solo** el diálogo con el servo-cráneo y su sonido, y que dure **al menos 2 s** |
| Abrir un ligando **archivado** | Un parpadeo del diálogo y el tubo | Un efecto corto de cambio de pantalla (hoy, el **entrelazado**) |
| Atrás, ajustes, tutorial, acceso… | Apagar + encender | Un efecto **corto**, sin abrir ni cerrar desde el centro. Primero se pidió «como un televisor que cambia de canal»; después, un pulso de luz; al final, el **entrelazado** (§3) |

## 2. Tres maneras de cambiar de pantalla

| Cambio | Cuándo | Efecto | Pieza pura |
|--------|--------|--------|------------|
| **Encendido** | Solo en el **arranque en frío**, sobre la pantalla de arranque | Un punto → una línea → la imagen (420 ms) | `CrtTransition` |
| **Corte** (`CUT`) | Un ligando (o el segundo de una comparación) **recién descargado** | Ninguno: el diálogo de carga ya fue la transición | `Navigation.change` |
| **Entrelazado** (`CHANNEL`) | **Todo lo demás**: un ligando archivado, Atrás, ajustes, tutorial, acceso, bloqueo y desbloqueo | La pantalla sale en bandas (impares, luego pares) y la nueva vuelve igual (200 ms) | `ChannelChange` |

La regla es **una función pura**, probada:

```kotlin
fun change(from: Screen, to: Screen): Change = when {
    from is Screen.Index && to is Screen.Molecule && to.origin == Origin.NETWORK -> Change.CUT
    from is Screen.Molecule && to is Screen.Compare && to.secondOrigin == Origin.NETWORK -> Change.CUT
    else -> Change.CHANNEL
}
```

- **El origen decide**, y ya venía en la pantalla: `Screen.Molecule` lleva `origin` (NETWORK, ARCHIVE, ARCHIVE_OFFLINE, documento 13), y `Screen.Compare` lleva ahora el de su segunda molécula (`secondOrigin`).
- **Solo desde el índice** (o desde la molécula, para la comparación). Volver del acceso a una molécula descargada hace una hora es un **entrelazado**: esta vez no hubo diálogo antes.

## 3. El entrelazado

```
progreso 0 ─────────────── 0,45 ─────────────────────── 1          (200 ms)
          salen las bandas impares, │ vuelven las impares,
          luego las pares            │ luego las pares
```

1. La pantalla se corta en **bandas horizontales de 4 dp** (unas 240 en un móvil).
2. **La imagen vieja se apaga** banda a banda, en **dos pasadas** de arriba abajo: primero las **impares**, luego las **pares**. Es como un tubo escribe un cuadro **entrelazado**: dos semicuadros de líneas alternas.
3. **Cuando todas están en negro** (punto 0,45), se intercambian las pantallas.
4. **La imagen nueva vuelve** con las mismas dos pasadas.
5. Cada banda **parpadea en verde fósforo** en el instante en que cambia: un hilo de luz que recorre la pantalla dos veces a la ida y dos a la vuelta.

En cada momento solo **una franja fina** de la pantalla brilla (una prueba lo limita a un 10 % de las bandas): es una **línea**, no un fogonazo. Lo demás es la imagen o el negro.

**La regla de cada banda** (`ChannelChange.band`, pura):

```kotlin
fun bandTime(k: Int, count: Int): Float {          // cuándo cambia la banda k, de 0 a 1 en cada mitad
    val perPass = (count + 1) / 2
    val pass = if (k % 2 == 1) 0 else 1              // las impares en la primera pasada
    return (pass + (k / 2).toFloat() / perPass) / 2f // y dentro de su pasada, de arriba abajo
}
```

**El dibujo:** un rectángulo por banda (negro si está fuera, verde translúcido si parpadea). Son unos cientos de rectángulos por fotograma, durante 200 ms.

![El entrelazado sobre dos capturas del A33, ralentizado ×4](img_26_entrelazado.gif)

*El entrelazado sobre dos capturas reales del A33, ralentizado ×4 (el mismo guion de Python que sirvió para elegirlo).*

### 3.1 Dos errores de frontera que encontraron las pruebas

La primera versión decía «la banda está fuera si `l >= t`». Pero la primera banda impar tiene `t = 0`, así que con `p = 0` **ya estaba en negro**: el efecto empezaba con una línea negra antes de empezar. Y en la vuelta, «en negro si `l < t`» dejaba esa misma banda **visible en el instante del intercambio**: se habría visto una franja de la pantalla nueva antes de tiempo.

La regla correcta cambia **estrictamente después** de su momento:
- a la ida, fuera si `l > t`;
- a la vuelta, en negro mientras `l <= t`.

Tres pruebas cayeron con el error, y dos mutantes (volver a `>=` o a `<`) lo vigilan desde entonces.

### 3.2 Un cambio en mitad de otro

`ChannelChange.restartAt`:
- **Si las bandas aún están saliendo**, siguen donde estaban: ya van tapando la pantalla que se va.
- **Si ya están volviendo**, empieza una salida nueva desde el principio.

### 3.3 Cómo se llegó: seis versiones vistas en el A33

| Versión | Qué era | Por qué no |
|---------|---------|-----------|
| 1. **Estática de televisor** (320 ms) | Nieve y una barra del sincronismo vertical | Evocaba un **televisor viejo de la Tierra**, no el cogitador; y era larga |
| 2. **Pulso de luz** (160 ms) | Todo el cristal en verde fósforo pálido | En una app oscura, un fogonazo: **demasiado brillante** |
| 3. **Parpadeo con resplandor** (180 ms) | A negro, y la imagen nueva bajo un resplandor verde | En el GIF convencía; **en el móvil no se notaba** |
| 4. **Pulso oscuro** (160 ms) | La versión 2 con el verde apagado y los bordes negros | **En el móvil no gustó** |
| 5. **Barrido del haz** (150 y 220 ms) | Una línea borraba hacia abajo y volvía a bajar dibujando | A 150 ms no se seguía; a 220 ms **no gustó** |
| 6. **Entrelazado** (200 ms, la que se queda) | Bandas que salen y vuelven en dos pasadas, impares y pares | — |

**Cómo se eligió:** con **dos rondas de propuestas** como GIF sobre capturas reales del A33, ralentizados ×4, hechos con un guion de Python que reproduce las mismas reglas que el código:
- **primera ronda:** pulso oscuro, parpadeo y barrido;
- **segunda ronda,** «en el estilo del barrido» (líneas y bloques, nada de luz en toda la pantalla):
  - **D**, el entrelazado;
  - **E**, un «fallo del espíritu máquina» (barras horizontales negras y verdes que cambian en cada fotograma);
  - **G**, bloques de datos (una rejilla que se apaga y se reescribe bloque a bloque, con un borde verde).

Se eligió **D**.

**Lección:** un GIF ralentizado ×4 enseña la **forma** de un efecto, pero no cómo se **siente** a velocidad real: la última palabra la tiene el móvil. Por eso cada versión es **un fichero puro con sus pruebas** (`ChannelChange`) y **una función de dibujo**: cambiar de efecto no toca nada más.

## 4. El encendido, solo al arrancar, y el arranque que lo espera

**El problema.** La pantalla de arranque empezaba su reloj **en cuanto se componía**, a la vez que el tubo se encendía encima. Los 420 ms del encendido **se comían** su principio: la primera línea y su tic sonaban con la pantalla aún tapada.

**El arreglo.**
- `CrtSwitch` dice a cada pantalla **si el tubo ya está encendido** (`on`, un `derivedStateOf`: cambia una sola vez, no recompone en cada fotograma).
- `BootScreen(running = on)`: su reloj **no arranca** hasta entonces (`LaunchedEffect(running)`). Hasta ese momento se queda en el instante 0, sin ninguna línea escrita.
- El **zumbido del tubo** (`BOOT_HUM`) sigue sonando **al componerse**, es decir, **durante el encendido**. Suena justo cuando el punto se abre en la línea: el sonido y la imagen cuentan lo mismo.

Resultado: **4,2 s de arranque completos** después de los 0,42 s del encendido, con todas sus líneas y sus sonidos. El mínimo de 1 s antes de poder saltarlo cuenta también desde que el tubo está encendido.

## 5. Abrir un ligando: descargado o archivado

### 5.1 La política (`LoadingPolicy`, pura)

| | Descargado | Archivado («ARCHIVED») |
|---|-----------|-----------------------|
| ¿Diálogo de carga? | Sí, **al momento** | **No**… salvo que tarde más de **600 ms** |
| ¿Sonido del módem? | Sí | No |
| Duración mínima | **2 s** | Ninguna |
| Cambio de pantalla | **Corte** | **Entrelazado** |

- **¿Por qué 2 s?** En una red rápida el fichero llega en unos cientos de milisegundos, y el diálogo solo **parpadeaba**. El servo-cráneo que flota, las etapas que se marcan y el módem son parte de la experiencia: ahora tienen su tiempo.
- **¿Por qué un archivado va en silencio?** La copia está en el móvil y se abre en milisegundos: un diálogo solo parpadearía. Basta el entrelazado.
- **¿Y si un archivado tarda?** Una copia de más de 30 días se **refresca** en la red (documento 13), y con una red lenta eso tarda. Si la carga silenciosa pasa de **600 ms**, el diálogo aparece igualmente: nadie se queda esperando sin saber qué pasa.

### 5.2 Dónde se decide

- **El ViewModel**, al tocar: `open(id)` → `loader.load(id, quiet = id in archived.value)`, usando el mismo conjunto que pone ARCHIVED en las celdas. Igual para la segunda molécula de la comparación (`openCompare`).
- **`LoadState.Loading`** lleva ahora `quiet`. El diálogo lo lee: silencioso → espera la gracia; si no, aparece ya. Los sonidos lo leen: silencioso → sin módem. Su etiqueta de sonido es otra (`quiet:ATP`), para que una carga ruidosa posterior del mismo id sí suene.
- **`LigandLoader`** aplica el mínimo: con el resultado ya listo, **espera lo que falte** (`delay`) **dentro de la misma tarea** y en el hilo principal, y solo entonces publica. Así sigue cumpliendo su garantía (documento 06b):
  - un **Cancel** durante la espera la detiene y **no se publica nada**;
  - el progreso de las etapas sigue llegando;
  - el mínimo vale también para un **fallo**: el diálogo no parpadea antes del error.

## 6. Cómo lo hace `CrtSwitch`

```kotlin
CrtSwitch(target = screen, startOff = !booted, reduceMotion = reduce) { shown, tubeOn -> … }
```

- **Dos valores**, leídos solo en el dibujo:
  - `cover`, 1 → 0 durante el encendido;
  - `channel`, el progreso del entrelazado, o −1 si no hay ninguno.

  En reposo los dos están en «nada», la cortina no dibuja y no se pide ningún fotograma.
- **Al llegar una pantalla nueva** (otra `key`):
  - `CUT`: se cambia al momento;
  - `CHANNEL`: se avanza fotograma a fotograma, y la pantalla mostrada se cambia **el fotograma en que la pantalla es toda negra**.
- **Mientras dura**, una capa transparente absorbe los toques.
- **Animaciones del sistema desactivadas**: todo es inmediato, y el arranque empieza al momento.

## 7. Pruebas y mutación

| Fichero | Nuevas | Qué fija |
|---------|--------|----------|
| `ChannelChangeTest` | 6 | Todo negro en el intercambio y limpio en los dos extremos (sin parpadeo al final); a mitad de la salida, todas las impares fuera y ninguna par; dentro de cada pasada, de arriba abajo; la salida solo añade negro y la vuelta solo lo quita; cada banda parpadea solo al cambiar, más suave a la vuelta, y nunca más del 10 % a la vez; corto (150-250 ms) y las bandas cubren la pantalla (la última se corta, nunca falta); un cambio en mitad de otro sigue saliendo o empieza de nuevo |
| `LoadingPolicyTest` | 3 | 2 s para una descarga, nada para un archivado; lo que falta nunca es negativo; la gracia entre 0,3 y 1 s |
| `LigandLoaderTest` | 5 | Una descarga instantánea sigue cargando hasta el mínimo; una silenciosa no espera; sigue silenciosa con las etapas; un Cancel durante la espera no publica nada; un fallo también espera |
| `NavigationTest` | 1 | Corte solo para lo recién descargado y solo desde el índice (o la molécula, en la comparación); entrelazado en todo lo demás, incluido volver del acceso; la comparación lleva su origen |
| `SoundTagsTest` | (ampliada) | La etiqueta de una carga silenciosa es otra |
| `CrtTransitionTest` | 1 (−2) | Solo queda el encendido: tiempo, desde la mitad, reloj hacia atrás, por debajo de 600 ms |
| `GlintTest` | (cambiada) | La primera pasada del **destello** de la cabecera espera al encendido y al entrelazado |

**Mutantes (48, muertos):**
- (estática) estática incompleta en el intercambio, intercambio tardío, reinicio desde cero, barra antes de tiempo, cambio largo;
- (pulso de luz) luz incompleta en el intercambio, caída en línea recta, 320 ms, reinicio desde cero, líneas de barrido a tope todo el tiempo;
- (parpadeo) resplandor sobre la pantalla vieja, pico sin escalar, negro que se levanta en línea recta, reinicio desde cero, 320 ms, y un resplandor **apagado** (0,3) que al principio **sobrevivió**: se añadió el mínimo de 0,5;
- (pulso oscuro) pulso incompleto en el intercambio, caída en línea recta, 320 ms, reinicio desde cero, líneas a tope todo el tiempo;
- (entrelazado) sin entrelazar (todas en una pasada), las pares primero, de abajo arriba, parpadeo al final, parpadeo ancho, vuelta más brillante que la ida, bandas que no llegan al final de la pantalla, reinicio que no distingue ida de vuelta, `>=` a la ida y `<` a la vuelta (los dos errores de frontera, §3.1);
- (barrido) con aceleración, una franja sin tapar abajo, el negro al otro lado de la línea, reinicio que no distingue borrar de dibujar, demasiado rápido (150 ms), demasiado lento (400 ms), pasadas desiguales, y 220 ms cuando el tope era 160 (al volver a 220 se cambió la prueba, no el código);
- corte para lo archivado, corte desde cualquier pantalla, comparación sin origen;
- mínimo también para lo silencioso, resto negativo, sin espera, etapas que pierden el silencio, silencioso que espera;
- la misma etiqueta de sonido.

**Sin prueba automática:** el dibujo de las bandas y su parpadeo; que el arranque espere de verdad al tubo; el diálogo que aparece tras la gracia. Ver §8.

## 8. Cómo probarlo en el móvil

1. **Arranque en frío** (cerrar desde Recientes): punto → línea → arranque, y **después** las líneas una a una con sus tics. El zumbido suena con el encendido.
2. **Arranque → acceso**, **acceso → índice**: la pantalla sale en bandas finas, con un hilo verde que la recorre, y la nueva vuelve igual.
3. Un ligando **nunca abierto**: el diálogo con el cráneo y el módem **2 s** como mínimo, y la molécula **sin** estática.
4. Un ligando **ARCHIVED**: sin diálogo, sin módem; un entrelazado y la molécula.
5. **Atrás** desde la molécula, **ajustes**, **tutorial**: entrelazado.
6. Dos toques rápidos (abrir los ajustes y volver): la pantalla vieja no vuelve a asomar.
7. **El 3D también queda tapado** por el negro en el intercambio.
8. Modo avión y un ligando nuevo: el diálogo 2 s y luego el error.
9. **IDLE** en la molécula tras el cambio.

## 9. Trampas

1. **Animar una pantalla mientras otra animación la tapa**: el arranque perdía su principio bajo el encendido.
2. **Un diálogo de carga que parpadea**: rápido no siempre es mejor; un mínimo lo hace legible.
3. **Un mínimo que rompe la cancelación**: esperar **fuera** de la tarea publicaría un resultado ya cancelado.
4. **Dos transiciones seguidas** (diálogo + tubo): demasiado.
5. **Un efecto que evoca otra cosa**: la estática decía «televisor de la Tierra», no «cogitador». La estética manda tanto como la técnica.
6. **Reiniciar un efecto desde cero** cuando se interrumpe: la pantalla equivocada asoma un instante.
7. **Ocultar un diálogo para siempre** en una carga «rápida» que resulta lenta: hace falta la gracia.

## 10. Alternativas

| Alternativa | Por qué no |
|-------------|-----------|
| Un *shader* AGSL de ruido de verdad | Solo Android 13+; la app es minSdk 24 |
| Desplazamiento horizontal de la imagen (*tearing*) | El SurfaceView del 3D no se transforma (documento 23, §2.3) |
| Entrelazado también tras una descarga | Dos transiciones seguidas |
| Diálogo también para lo archivado, con su mínimo | 2 s de espera para algo que ya está en el móvil |
| El mínimo en el diálogo (que se quede abierto aunque el estado ya sea Loaded) | La molécula aparecería **debajo** del diálogo; el estado único (`LoadState`) dejaría de decir la verdad |
| Estática de televisor (nieve y barra de sincronismo) | La primera versión (§3.3): evocaba un televisor viejo de la Tierra, no el cogitador, y era el doble de larga |
| Pulso de luz (todo verde pálido) | La segunda versión (§3.3): demasiado brillante en una app oscura |
| Parpadeo a negro con resplandor | La tercera versión (§3.3): en el móvil no se notaba |
| Pulso oscuro | La cuarta versión (§3.3): no gustó en el móvil |
| Barrido del haz | La quinta versión (§3.3): no gustó en el móvil |
| «Fallo del espíritu máquina» (barras aleatorias) | Propuesta E de la segunda ronda: muy del tema, pero nervioso en un «Atrás» frecuente |
| Bloques de datos | Propuesta G de la segunda ronda |
| Un sonido para el entrelazado | Ninguno de los diez sonidos encaja; uno nuevo con `make_sounds.py` es posible si se quiere |

## 11. Decisiones

| # | Decisión | Estado | Motivo |
|---|----------|--------|--------|
| D91 | **Tres cambios de pantalla**: encendido solo en el arranque en frío, **corte** tras una descarga, **entrelazado** de 200 ms en todo lo demás (bandas de 4 dp que salen y vuelven en dos pasadas, impares y pares; la estática, el pulso de luz, el parpadeo, el pulso oscuro y el barrido se descartaron); la mitad «apagar» del tubo desaparece | Decidida (sin probar en el móvil) | Probado en el móvil por el equipo; sustituye parte de D84 |
| D92 | La pantalla de arranque **espera al tubo** para empezar su reloj | Decidida (sin probar en el móvil) | El encendido se comía sus primeras líneas |
| D93 | **Carga silenciosa** de un ligando archivado (sin diálogo ni sonido, salvo que pase de 600 ms) y **mínimo de 2 s** del diálogo de una descarga, aplicado en el `LigandLoader` | Decidida | El diálogo parpadeaba; lo archivado no lo necesita |

## 12. Qué recordar

- **Cada transición tiene un sitio:** encender es empezar, entrelazar es moverse, y un diálogo de carga ya es una transición.
- **El efecto tiene que hablar el idioma de la app:** la nieve era técnica correcta con la estética equivocada, y el pulso, demasiada luz para una app oscura.
- **Para elegir un efecto, enseñarlo:** tres GIF sobre capturas reales deciden más rápido que tres descripciones; pero la última palabra la tiene el móvil, a velocidad real.
- **Una animación no debe tapar a otra:** lo de debajo espera.
- **El mínimo se aplica donde está la verdad** (el cargador), no en la pantalla.
- **Interrumpir un efecto** es continuarlo desde lo que se ve, no empezarlo de nuevo.
