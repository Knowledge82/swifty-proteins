# 19 · Etiquetas de átomos en 3D, resaltado al seleccionar y lista de modelos

> **Estado:** **escrito**. Reglas puras **probadas en la JVM** (488 pruebas en total; 5 mutantes nuevos, todos muertos). Las etiquetas y el menú **compilan contra *stubs*** hechos con el código fuente de SceneView 4.69.4; **sin probar en el móvil** (§6).

## Índice

1. [Qué pide el enunciado](#1-qué-pide-el-enunciado)
2. [Etiquetas: el problema](#2-etiquetas-el-problema)
3. [Etiquetas: la solución](#3-etiquetas-la-solución)
4. [Resaltar el elemento al seleccionar](#4-resaltar-el-elemento-al-seleccionar)
5. [La lista de modelos](#5-la-lista-de-modelos)
6. [Cómo probarlo en el móvil](#6-cómo-probarlo-en-el-móvil)
7. [Pruebas y mutación](#7-pruebas-y-mutación)
8. [Trampas](#8-trampas)
9. [Alternativas](#9-alternativas)
10. [Decisiones](#10-decisiones)
11. [Qué recordar](#11-qué-recordar)

---

## 1. Qué pide el enunciado

| Punto | Texto | Antes | Ahora |
|-------|-------|-------|-------|
| VII.3 *Atom Labels* | «mostrar los símbolos de los elementos directamente sobre los átomos en la vista 3D (**activable**)» | — | Tecla **LABELS** |
| VII.3 *Atom Highlighting* | «al pasar por encima o **seleccionar un átomo**, resaltar **todos los átomos del mismo elemento**» | Solo tocando la leyenda | Seleccionar un átomo **ya** resalta su elemento |
| VII.1 *UI control* | «control segmentado, **desplegable** o botones» | Una tecla que **recorría** los modelos | **Lista desplegable** con los cuatro |

## 2. Etiquetas: el problema

SceneView 4.69.4 trae `TextNode`: un texto en 3D que **mira siempre a la cámara** (un *billboard*). Leyendo su código salieron tres problemas:

1. **Memoria.** Cada `TextNode` dibuja **su propia** imagen de **512 × 128** píxeles (el tamaño por defecto; la función de Compose no deja cambiarlo). 256 átomos × 512 × 128 × 4 bytes = **64 MB** de imágenes. Pero las etiquetas son **símbolos de elementos**: en un ligando hay unos pocos distintos.
2. **Tapadas.** Una etiqueta en el **centro** del átomo queda **dentro** de la bola: la superficie la tapa.
3. **El bucle de dibujo.** La forma «natural» de mover algo cada fotograma, la propiedad `onFrame` de un nodo, significa en SceneView «**sigue dibujando siempre**» (lo dice su documentación: `isFrameActive`). La escena no descansaría nunca: el contador de FPS no diría IDLE y la batería se gastaría para nada (documento 12).

## 3. Etiquetas: la solución

### 3.1 Una imagen por elemento (`LabelPicture`)

Una imagen de **96 × 64** píxeles **por elemento** («C», «O», «Cl»), compartida por todos sus átomos: **24 KB** por elemento. Letra de la app (Share Tech Mono), color hueso con **contorno oscuro**: se lee sobre un hidrógeno blanco y sobre el fondo negro. Se muestra con `BillboardNode` (la base de `TextNode`, que acepta una imagen ya hecha).

> Se comprobó con `fontTools`: el triángulo «▾» **no está** en nuestra fuente (como antes las estrellas y los subíndices). La tecla del modelo dice «MODEL…»: los puntos suspensivos **sí** están, y en las interfaces significan «abre una elección».

### 3.2 Delante de su átomo (`AtomLabels`, puro)

La etiqueta se coloca **fuera de la bola, hacia la cámara**, justo pasada la superficie:

```
posición = centro + (cámara − centro) / |cámara − centro| × (radio + 0,03)
```

Desde cualquier ángulo queda sobre la **cara visible** de su átomo. Los átomos de delante siguen tapando las etiquetas de los de detrás, como debe ser. Los 0,03 Å de holgura evitan que bola y etiqueta peleen por la misma profundidad (parpadeo, *z-fighting*). Si la cámara está **dentro** del átomo (zoom extremo), no hay dirección: el centro.

**Tamaño:** 1,4 veces la bola, entre 0,35 Å (legible sobre una rótula de alambre) y 1,2 Å (no gigante en espacio lleno); proporción 3:2, la de la imagen.

### 3.3 Moverlas sin impedir el descanso de la escena

- Se mueven desde **el observador** `SceneView(onFrame = …)`, que **no** mantiene vivo el bucle: SceneView lo llama **después** de cada fotograma presentado.
- Una etiqueta solo se mueve si está a **más de 0,001 Å** de su sitio (`AtomLabels.needsMove`). Mover un nodo pide otro fotograma; con la cámara **quieta**, todas están ya en su sitio, nada se mueve y la escena se para.
- Precio: al girar, las etiquetas van **un fotograma** por detrás de la cámara (16 ms a 60 FPS): imperceptible.
- La **orientación** hacia la cámara la hace SceneView (`cameraPositionProvider`), con su propio mecanismo interno, que tampoco mantiene el bucle.

### 3.4 Una carrera al cambiar de modelo

Los nodos de etiqueta se guardan en un mapa (átomo → nodo) para moverlos. Al cambiar de modelo, Compose **primero crea** el grupo nuevo (que se apunta en el mapa) y **después destruye** el viejo. Si el viejo borrara «su» entrada sin mirar, borraría **la nueva**: esa etiqueta se quedaría en el centro, tapada. Cada grupo recuerda **qué nodo creó** y solo lo borra si el mapa aún apunta a **ese**. Detectado al revisar el código, antes de verlo en el móvil.

### 3.5 Detalles

- Las etiquetas **no se pueden tocar** (`isTouchable = false`): un toque sobre una etiqueta llega a su átomo.
- Solo de los átomos **dibujados** (un hidrógeno oculto no tiene etiqueta), con el tamaño del **modelo actual**.
- Tecla **LABELS** con estado («on» / «off» para TalkBack); se conserva al girar el móvil.

## 4. Resaltar el elemento al seleccionar

`Drawn.highlight(molécula, elegidoEnLaLeyenda, selección)`:

1. Si en la **leyenda** se eligió un elemento → **ese** (una elección explícita manda).
2. Si no, y hay un **átomo** seleccionado → **su** elemento.
3. Un enlace seleccionado, o nada → ninguno.

Al tocar un oxígeno: halo **verde** en él (la selección) y halos **dorados** en todos los demás oxígenos. Con la regla (documento 16) activa no hay selección, así que tampoco resaltado implícito.

## 5. La lista de modelos

**MODEL…** abre un menú desplegable (`DropdownMenu` de Material 3) con los cuatro modelos; el actual va marcado con «>» en verde y con el estado «current» para TalkBack. La leyenda dice el modelo actual: «Elements / CPK key · Space-filling». Las tres teclas **MODEL… · RULER · LABELS** se reparten el ancho.

## 6. Cómo probarlo en el móvil

1. ATP → **LABELS**: una letra sobre cada átomo, mirando a la cámara. Girar: las letras siguen en la cara visible de su átomo; las de los átomos de detrás, tapadas.
2. Dejar la cámara quieta: el contador de FPS pasa a **IDLE** también con las etiquetas (§3.3).
3. **MODEL…** → Space-filling, Sticks, Wireframe: las etiquetas cambian de tamaño y siguen visibles (también las rótulas finas del alambre).
4. 15P (256 átomos) con etiquetas, girando: ¿sigue en 60 FPS?
5. Tocar un oxígeno: halo verde en él y dorado en todos los oxígenos. Tocar «C» en la leyenda: mandan los carbonos.
6. Tocar una etiqueta: se selecciona su átomo.

## 7. Pruebas y mutación

- **`AtomLabelsTest`** (5): sobre la bola mirando a la cámara; desde cualquier ángulo, a radio + holgura y en la dirección de la cámara; cámara dentro del átomo; tamaño con sus límites y 3:2; una etiqueta en su sitio no se mueve.
- **`ScenePickTest`** (+1): el resaltado por selección, la leyenda manda, enlace y nada no resaltan, un índice imposible no rompe nada.
- **Mutantes (5, muertos):** sin holgura; etiqueta hacia el lado contrario de la cámara; sin tamaño mínimo; mover siempre (la escena no descansaría); la selección manda sobre la leyenda.
- **Sin prueba automática:** los nodos de SceneView, el mapa y su carrera (§3.4), el menú. Sección 6.

## 8. Trampas

1. **`TextNode` por átomo:** 64 MB de imágenes iguales.
2. **Etiqueta en el centro:** tapada por su propia bola.
3. **`node.onFrame` para moverlas:** la escena no descansa nunca.
4. **Mover sin umbral:** cada pequeño error de coma flotante pediría otro fotograma.
5. **Borrar del mapa en `onDispose` sin comprobar de quién es la entrada:** el grupo viejo borra al nuevo.
6. **Glifos que la fuente no tiene** («▾»).

## 9. Alternativas

| Alternativa | Por qué no |
|-------------|-----------|
| Etiquetas como **texto de Compose** encima de la escena, proyectando cada átomo a la pantalla | Recomponer 256 textos 60 veces por segundo; y no se ocultan detrás de otros átomos |
| **Desactivar la prueba de profundidad** de la etiqueta | Se verían todas, también las de detrás: ilegible en un ligando grande |
| Etiquetas con el **nombre** del átomo («C01») | El enunciado pide el **símbolo**; el nombre está en la ficha al tocar |
| Resaltar el elemento **solo** desde la leyenda | El enunciado dice «al seleccionar un átomo» |
| Control **segmentado** de 4 opciones | Cuatro textos largos («Space-filling») no caben en el ancho de un móvil en vertical |

## 10. Decisiones

| # | Decisión | Estado | Motivo |
|---|----------|--------|--------|
| D62 | **Etiquetas** con `BillboardNode` y **una imagen por elemento**, colocadas sobre la cara visible hacia la cámara, movidas desde el **observador** de fotogramas con umbral | Decidida | VII.3; 64 MB → 24 KB por elemento; la escena sigue descansando |
| D63 | **Seleccionar un átomo resalta su elemento**; la leyenda manda | Decidida | VII.3 al pie de la letra |
| D64 | **MODEL…** abre una **lista desplegable** con los cuatro modelos | Decidida | VII.1 «dropdown» |

## 11. Qué recordar

- **Leer el código de la biblioteca** antes de usarla a escala: el tamaño por defecto de una textura multiplicado por 256 es un problema que la documentación no grita.
- **Compartir** lo que se repite (una imagen por elemento, no por átomo).
- En una escena **a demanda**, distinguir lo que **mantiene vivo** el bucle (un `onFrame` de nodo) de lo que solo **observa** (el `onFrame` de la escena).
- **Umbral** antes de mover: sin él, el ruido numérico es trabajo infinito.
- En Compose, **el grupo nuevo nace antes de que muera el viejo**: un `onDispose` no debe pisar lo del nuevo.
