# 22 · Vídeo: la molécula da una vuelta completa

> **Estado:** **escrito**. Reglas puras **probadas en la JVM** (603 pruebas en total; 7 nuevas; 5 mutantes, todos muertos). La grabación, la superposición y la entrega **compilan contra *stubs*** hechos con el código fuente de SceneView 4.69.4 y pasan las reglas de Compose; **sin probar en el móvil** (§8).

## Índice

1. [Qué pide el enunciado](#1-qué-pide-el-enunciado)
2. [Cómo se graba: el `SceneRecorder` de SceneView](#2-cómo-se-graba-el-scenerecorder-de-sceneview)
3. [El giro: gira la molécula, no la cámara](#3-el-giro-gira-la-molécula-no-la-cámara)
4. [La pantalla mientras graba](#4-la-pantalla-mientras-graba)
5. [El rótulo dentro del vídeo](#5-el-rótulo-dentro-del-vídeo)
6. [Compartir o guardar en la galería](#6-compartir-o-guardar-en-la-galería)
7. [Errores y cancelación](#7-errores-y-cancelación)
8. [Cómo probarlo en el móvil](#8-cómo-probarlo-en-el-móvil)
9. [Pruebas y mutación](#9-pruebas-y-mutación)
10. [Trampas](#10-trampas)
11. [Alternativas](#11-alternativas)
12. [Decisiones](#12-decisiones)
13. [Qué recordar](#13-qué-recordar)

---

## 1. Qué pide el enunciado

VII.5 *Extended Sharing and Export* nombra un **vídeo de la rotación de la molécula**. Desde el menú SHR hay ahora dos entradas nuevas:

| Entrada | Qué hace |
|---------|----------|
| **Share video** · MP4 · one full turn · 6 s | Graba y abre el menú de compartir de Android |
| **Save video to gallery** · Movies/Swifty Proteins | Graba y lo deja en la galería, álbum «Swifty Proteins» (el mismo nombre que las imágenes, bajo Movies) |

## 2. Cómo se graba: el `SceneRecorder` de SceneView

SceneView 4.69.4 trae `SceneRecorder` (`capture/SceneRecorder.kt`, leído entero). Usa un `SurfaceMirrorer`: Filament dibuja la escena **una segunda vez**, directamente en la superficie de entrada del codificador de vídeo de Android (`MediaRecorder`, H.264 en MP4).

**Qué ganamos así:**

- **Sin `MediaProjection`**: no aparece el diálogo «esta app va a grabar la pantalla», no hace falta servicio en primer plano y no se graban las barras del sistema.
- **Solo la escena**: los paneles de Compose no salen en el vídeo.
- **Su tamaño**: el de la vista, encajado en 1920 × 1080 (en vertical, unos 864 × 1920), alineado a lo que el codificador del móvil admite (`chooseVideoSize`).
- **Se para solo** si la app pasa a segundo plano (`ON_STOP`): un codificador sin fotogramas daría un vídeo congelado.

**Ajustes:** 30 fotogramas por segundo, 12 Mbit/s (los de SceneView), **sin micrófono** (no hace falta el permiso `RECORD_AUDIO`) y un tope de 7 s por si el giro no terminara.

Mientras no se graba, el `SurfaceMirrorer` no tiene destino y no cuesta nada: SceneView solo lo cuenta como «hay que dibujar» cuando tiene superficies espejo (`isMirroring`). El IDLE del documento 21 se mantiene.

## 3. El giro: gira la molécula, no la cámara

Todo lo de la molécula (átomos, enlaces, halos, etiquetas, la regla) cuelga ahora de **un nodo**. En reposo su rotación es cero y no cambia nada. Al grabar, un bucle de fotogramas propio (`withFrameNanos`) le escribe la rotación en cada fotograma:

```
ángulo = 360° × tiempo / 6 s       (Turntable.angle, puro: nunca menos de 0 ni más de 360)
```

- **Velocidad constante**: el último fotograma toca al primero, y el vídeo se puede repetir en bucle sin salto.
- **Cada escritura de la rotación** es un cambio de transformación: SceneView pide su fotograma él solo. No hace falta forzar el bucle.
- **¿Por qué no girar la cámara?** La cámara es del manipulador de SceneView: escribirle encima es pelearse con él. Girar un nodo es una línea.
- **La luz no gira**: la contraluz y las luces quedan fuera del nodo, así que la molécula se ve **iluminada por todos los lados** al girar, como en un torno de escultor.
- **Las etiquetas** se colocan hacia la cámara (documento 19). Mientras el nodo gira, la cámara se expresa en las coordenadas **de la molécula** con `node.getLocalPosition(cámara)`: es el propio nodo quien hace la cuenta, así que ningún convenio de signos puede salir al revés.

## 4. La pantalla mientras graba

1. **Se quitan la selección y la órbita de un átomo**: el vídeo, limpio y centrado.
2. **Los paneles desaparecen.** En su lugar sale una línea **VID-CAPTURE** («● REC 2.4 / 6.0 s», en rojo y en palabras) y **Cancel**. Un velo transparente se queda **todos los toques**: un dedo girando la molécula estropearía la vuelta.
3. **La cámara apunta a toda la pantalla** (`contentPadding` a cero). El vídeo es la superficie entera, así que la molécula debe estar en **su** centro y no en el de la ventana entre paneles (documento 21, §18). Como la forma de la ventana cambia, la cámara vuelve a su distancia inicial deslizándose ~0,6 s.
4. **0,8 s de espera** (`SETTLE_MILLIS`) antes del primer fotograma: el vídeo no empieza con ese movimiento.
5. **6 s de giro**, después se para el codificador.
6. Vuelven los paneles. **Precio:** la cámara vuelve a su vista inicial, porque la ventana vuelve a cambiar de forma. Lo que el usuario hubiera girado antes de grabar se pierde.

## 5. El rótulo dentro del vídeo

La misma banda que las imágenes («Swifty Proteins · ATP · C10H16N5O13P3 · 47 atoms · 49 bonds»), pero **dentro** del fotograma, abajo. El vídeo tiene el tamaño de la escena y no hay sitio para añadir la banda debajo. La dibuja el propio grabador en cada fotograma (`CaptureOverlay`, Android 10 o superior). El código de la banda es ahora **uno** para imágenes y vídeo (`drawCaption`).

## 6. Compartir o guardar en la galería

- **Compartir:** el MP4 del grabador (en la carpeta de la app) se **copia** a la carpeta compartida (`cache/shared`, la única que abre el `FileProvider`, documento 09) y se ofrece como `video/mp4`. A diferencia de los ficheros químicos (documento 18), muchas apps reclaman ese tipo: WhatsApp, Telegram, Gmail, Drive…
- **Galería:** `GallerySaver.saveVideo`. Igual que las imágenes: en Android 10+, **MediaStore** (tabla de **vídeo**, `Movies/Swifty Proteins`, `IS_PENDING` hasta acabar, sin permisos); en Android 7-9, fichero en Movies, el escáner de medios y el permiso `WRITE_EXTERNAL_STORAGE` pedido antes. El permiso ahora recuerda **qué** exportación lo pidió (imagen o vídeo).
- En los dos casos **se borra** la copia del grabador.

## 7. Errores y cancelación

| Caso | Qué ve el usuario |
|------|-------------------|
| El codificador no arranca (tamaño no admitido, lo que sea) | «The video could not be recorded on this phone.» |
| Cancel o Atrás durante la grabación | «Recording cancelled.»; el fichero a medias se **borra** |
| La app pasa a segundo plano | El grabador se para solo; al volver, el giro termina y no hay fichero: el mismo mensaje de error |
| No se puede escribir en la galería | «The file could not be saved on the phone.» |

Atrás durante la grabación **cancela** y no sale de la pantalla: su `BackHandler` se registra después del de la pantalla, y gana.

## 8. Cómo probarlo en el móvil

1. ATP → SHR → **Share video**. Desaparecen los paneles, «Preparing the turntable…», después «● REC 0.0 / 6.0 s» que cuenta hasta 6.0; la molécula da **una** vuelta y se abre el menú de compartir.
2. Mandarlo (a uno mismo) y verlo: 6 s, la molécula centrada, una vuelta suave, el rótulo abajo; **sin paneles ni barras del sistema**.
3. SHR → **Save video to gallery**: en la galería, álbum «Swifty Proteins», en vídeos.
4. Grabar y pulsar **Cancel** a los 2 s, y otra vez con **Atrás**: «Recording cancelled.» y nada en la galería.
5. Con **LABELS** activas: las letras siguen mirando a la cámara mientras gira.
6. 15P en espacio lleno: ¿la vuelta va fluida a 30 fps en el vídeo?
7. Después de grabar, el contador de FPS vuelve a **IDLE**.

## 9. Pruebas y mutación

| Fichero | Nuevas | Qué fija |
|---------|--------|----------|
| `TurntableTest` | 5 | Una vuelta a velocidad constante; nunca más de 360 ni hacia atrás; termina justo en su duración; la grabación dura más que el giro y la espera más que el deslizamiento de la cámara; la línea REC |
| `ExportFormatTest` | 2 (+1 cambiada) | Ocho entradas en orden; los vídeos son MP4 y van como `video/mp4`; dos van a la galería; el nombre del vídeo acaba en `.mp4` |
| `ShareTextTest` | +4 líneas | Los textos de estado del vídeo y de la cancelación |

**Mutantes (5, muertos):** ángulo sin límite, fin del giro estricto, espera corta, vídeo como datos genéricos y galería solo para imágenes.

**Sin prueba automática:** el codificador, el espejo, el nodo que gira, la superposición y la entrega. Ver §8.

## 10. Trampas

1. **Grabar la pantalla** con `MediaProjection`: diálogo de consentimiento, servicio en primer plano, y salen los paneles y las barras.
2. **Girar la cámara a mano** con un manipulador activo: se pelean.
3. **Etiquetas mirando a la cámara mientras gira su padre**: la cámara hay que expresarla en las coordenadas del padre.
4. **Grabar con la cámara apuntando a la ventana**: en el vídeo, que es la superficie entera, la molécula sale descentrada.
5. **Empezar a grabar en el mismo instante** en que cambia la pantalla: el vídeo arranca con un salto.
6. **Una función local usada antes de declararla**: no compila (ocurrió aquí: `select`).
7. **Ocho entradas en un diálogo**: en horizontal no caben; ahora se desplazan.

## 11. Alternativas

| Alternativa | Por qué no |
|-------------|-----------|
| `MediaProjection` (grabar la pantalla) | Consentimiento, servicio y paneles en el vídeo |
| `MediaCodec` propio con `PixelCopy` por fotograma | Copias a la CPU 30 veces por segundo: lento y con más código; SceneView ya resuelve el espejo en la GPU |
| **GIF** animado | Mucho más pesado a igual calidad y sin aceleración; el MP4 lo reproduce cualquier móvil |
| Vuelta con aceleración y frenada (*easing*) | Más «cinematográfico», pero el bucle tendría un frenazo en cada vuelta |
| Mantener la pose del usuario y grabar con los paneles en su sitio | La molécula saldría descentrada en el vídeo (§4.3) |
| Duración elegible (3 / 6 / 12 s) | Una opción más en un menú ya largo; 6 s enseñan todos los lados |

## 12. Decisiones

| # | Decisión | Estado | Motivo |
|---|----------|--------|--------|
| D83 | **Vídeo** con el `SceneRecorder` de SceneView: **una vuelta de 6 s a 30 fps**, la molécula gira en un nodo (no la cámara), rótulo dentro del fotograma, paneles fuera y cámara a pantalla completa; compartir como `video/mp4` o a **Movies/Swifty Proteins** | Decidida (sin probar en el móvil) | VII.5 «vídeo» |

## 13. Qué recordar

- **Leer lo que la biblioteca ya trae** antes de escribirlo: SceneView graba vídeo sin consentimientos ni copias a la CPU.
- **Girar el objeto, no la cámara**, cuando la cámara pertenece a otro.
- Un nodo que gira **pide sus propios fotogramas**: la escena «a demanda» sigue siendo a demanda.
- Una pantalla que cambia **necesita un momento** antes de grabarla.
- **Cancelar** también es un camino: hay que borrar lo que quede a medias.
