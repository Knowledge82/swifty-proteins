# 23 · Pulido: el tubo que se enciende, el destello del nombre y el cursor de consola

> **Actualizado (documento 26):** el tubo ya no se apaga antes de cada cambio. Se **enciende** solo en el arranque en frío; tras una descarga hay un **corte**, y en todo lo demás un **pulso de luz** verde pálido de 160 ms. La §2 describe la primera versión.

> **Estado:** **escrito**. Reglas puras **probadas en la JVM** (608 pruebas en total; 9 nuevas; 9 mutantes, todos muertos). El dibujo (la cortina del tubo, el destello, el cursor) **compila contra *stubs*** y pasa las reglas de Compose; **sin probar en el móvil** (§8).

## Índice

1. [Qué cambia y por qué](#1-qué-cambia-y-por-qué)
2. [El cambio de pantalla como un tubo de rayos catódicos](#2-el-cambio-de-pantalla-como-un-tubo-de-rayos-catódicos)
3. [El destello sobre «Swifty Proteins»](#3-el-destello-sobre-swifty-proteins)
4. [El cursor de consola del buscador](#4-el-cursor-de-consola-del-buscador)
5. [La celda sin nombre y la búsqueda solo por ID](#5-la-celda-sin-nombre-y-la-búsqueda-solo-por-id)
6. [Fotogramas: nada de esto rompe el IDLE](#6-fotogramas-nada-de-esto-rompe-el-idle)
7. [Pruebas y mutación](#7-pruebas-y-mutación)
8. [Cómo probarlo en el móvil](#8-cómo-probarlo-en-el-móvil)
9. [Trampas](#9-trampas)
10. [Alternativas](#10-alternativas)
11. [Decisiones](#11-decisiones)
12. [Qué recordar](#12-qué-recordar)
13. [Después: SHR pasa a SHARE y H baja al panel de teclas](#13-después-shr-pasa-a-share-y-h-baja-al-panel-de-teclas)

---

## 1. Qué cambia y por qué

| Cambio | Antes | Ahora | Motivo |
|--------|-------|-------|--------|
| **Cambio de pantalla** (VII.2 «transiciones») | Deslizamiento de ¼ del ancho y fundido, 300 ms | La pantalla vieja **se apaga** como un tubo y la nueva **se enciende** (660 ms) | El de antes apenas se notaba en una pantalla de 90 Hz; en la defensa, un bonus que no se ve no cuenta |
| **Nombre de la app** en la cabecera | Letras doradas quietas | Un **destello** recorre las letras cada 6 s | El metal pulido «vive»; el mismo efecto que el logotipo de Chaos Mode |
| **Buscador** | «> Search by ID or name» | «> **_** Search by ID», con el «_» parpadeando cada 0,75 s | Prompt de consola; el nombre ya no se busca (§5) |
| **Celda de la lista** | ID, nombre químico en gris, fórmula, átomos, barra | ID, fórmula, átomos, barra | El nombre era largo, gris y casi siempre cortado con «…» |

## 2. El cambio de pantalla como un tubo de rayos catódicos

### 2.1 Qué se ve

Un televisor o un monitor antiguo, al **apagarse**, aplasta la imagen en una **línea horizontal** brillante, y la línea se encoge hasta un **punto**. Al **encenderse**, al revés. Es lo que hace ahora cada cambio de pantalla:

```
apagar (240 ms)                       encender (420 ms)
imagen -> línea -> punto  [cambio]  punto -> línea -> imagen
```

El intercambio de pantallas ocurre **a oscuras**, cuando solo queda el punto. Al arrancar la app en frío, el tubo **se enciende** desde el punto sobre la pantalla de arranque, que ya era «un CRT encendiéndose».

Detalles que lo hacen creíble:

- **La imagen se ilumina al aplastarse**: la misma energía del haz en menos líneas. La ventana se lava de blanco verdoso hasta ser una línea que brilla.
- **Líneas de barrido** (*scanlines*) sobre la ventana, cada 3 dp, más fuertes cuanto más cerrado está el tubo.
- Un **halo** de fósforo verde por encima y por debajo de la línea.
- Fuera de la ventana, **cristal negro**, sea cual sea el tema: un tubo apagado es negro también en el tema claro.

### 2.2 Un solo número: la cobertura

Todo se calcula a partir de un número entre **0** (pantalla encendida) y **1** (apagada, un punto). Es `CrtTransition`, puro y probado:

```
cobertura 0 ............ 0,7 ............ 1
          imagen -> línea (se aplasta)   línea -> punto (se encoge)
```

- `frame(cobertura, ancho, alto, línea)` da la **ventana** que aún muestra la imagen (media anchura y media altura), el **brillo** del lavado, la fuerza de las **líneas de barrido** y el brillo del **punto**.
- `closing(desde, ms)` y `opening(desde, ms)` dan la cobertura en cada instante. Son **rectas desde donde estaba**: si llega otra pantalla en mitad de un cambio, el tubo sigue desde ahí, sin saltar a una imagen completa.
- La forma (aceleración) está **dentro** de `frame`: el tubo se cierra cada vez más deprisa (*ease-in*, `t²`), como un campo que muere; el brillo usa *smoothstep* (`t²·(3−2t)`), sin saltos en los extremos.

### 2.3 Por qué una cortina y no una transformación

Lo obvio sería aplastar la pantalla misma con `graphicsLayer { scaleY = … }`. **No sirve** en la pantalla de la molécula: Filament dibuja en un **SurfaceView**, una superficie propia de la ventana que Compose coloca en un hueco. Las transformaciones y la transparencia de Compose no llegan a esa superficie de forma fiable: el 3D se quedaría a tamaño completo mientras sus paneles se aplastan.

Por eso **no se transforma nada**: se **dibuja encima** una cortina opaca que deja una ventana cada vez más pequeña (o más grande). Que Compose dibuja **sobre** el SurfaceView ya lo demuestran nuestros paneles sobre la escena; la cortina tapa el 3D igual que el resto.

### 2.4 `CrtSwitch`: la pantalla que se ve va un paso por detrás

```kotlin
CrtSwitch(target = screen, startOff = !booted, reduceMotion = reduce) { shown -> /* dibuja shown */ }
```

- Guarda la pantalla **que está en el tubo** (`shown`). Cuando la app pide otra (`target`, por su `key`), primero **apaga** con la vieja todavía dibujada, luego la cambia y **enciende**.
- La pantalla vieja **lleva sus datos** (documento 21, §5.2): la molécula se sigue dibujando mientras se apaga, aunque el estado de carga ya sea `Idle`.
- `key(shown.key) { … }`: cada pantalla es una rama distinta del árbol, con su propio estado recordado.
- **Mismo `key`, datos nuevos** (la misma pantalla con un estado más fresco): se dibuja la nueva **sin** cambio de pantalla.
- **Mientras el tubo cambia, los toques no llegan a nada**: una capa transparente los absorbe, para que un toque sobre la pantalla que se apaga no abra otra cosa.
- **Animaciones del sistema desactivadas**: el cambio es inmediato.

### 2.5 Lo que se perdió: la dirección

El deslizamiento decía «hacia dentro» (desde la derecha) o «hacia atrás» (desde la izquierda). El tubo no tiene dirección, así que `Navigation.direction` y la profundidad de cada pantalla **se quitaron** con sus dos pruebas: no se queda código muerto. Dentro del **tutorial**, las páginas siguen deslizándose en la dirección del gesto, porque allí la dirección sí informa (página siguiente o anterior).

## 3. El destello sobre «Swifty Proteins»

En la cabecera de **arranque, tutorial, acceso e índice** (todas las que tienen el nombre), cada **6 s** una banda inclinada de luz blanca cruza las letras doradas de izquierda a derecha en **0,9 s**.

### 3.1 Cómo: una capa propia y `SrcAtop`

```kotlin
Text(...).graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()                                   // las letras
            drawRect(bandaInclinada, blendMode = BlendMode.SrcAtop)   // la luz, solo sobre ellas
        }
```

- `SrcAtop` significa «dibuja **solo donde ya hay algo dibujado**». La luz cae **sobre las letras** (y su resplandor), nunca sobre el fondo de alrededor. Es lo mismo que `ShaderMask` con `BlendMode.srcATop` en Flutter (Chaos Mode).
- **La capa propia (*offscreen*) es imprescindible.** Sin ella, `SrcAtop` mezclaría con **todo lo que hay debajo** del texto (el panel), y se iluminaría el rectángulo entero.
- La capa **recorta** a sus bordes, y el resplandor de las letras se sale unos píxeles por los lados. Por eso lleva 6 dp de relleno dentro y un desplazamiento de −6 dp fuera: el resplandor cabe entero y las letras no se mueven.

### 3.2 Dónde está la banda

`Glint` es puro: `progress(ms)` va de 0 a 1 durante la pasada, y `center(progreso)` coloca el centro de la banda entre **−0,2** y **1,2** del ancho, con *smoothstep*: lenta en los bordes y rápida por el medio, como el reflejo de una lámpara sobre una pieza de metal que gira. La banda mide 2 × 0,17 del ancho. En los extremos está **entera fuera** del texto, así que la luz nunca «aparece de golpe».

La primera pasada espera **1,2 s**: el tubo tarda 0,66 s en encenderse, y un destello sobre una pantalla aún oscura no se vería.

## 4. El cursor de consola del buscador

```
> _ Search by ID        (vacío: el «_» parpadea, 0,75 s encendido y 0,75 s apagado)
> ATP|                  (con texto: el «_» se va y aparece el cursor del campo)
```

- El «_» **es** el cursor mientras el cuadro está vacío; el cursor propio del campo se pinta **transparente** entonces. Nunca hay dos cursores parpadeando a la vez.
- Con la primera letra, el «_» desaparece y el cursor del campo vuelve, **detrás del texto**, donde irá la siguiente letra.
- `BlinkingCursor` se **reescribió** para toda la app (arranque, acceso, buscador), con el mismo ritmo de 0,75 s:
  - **antes**: una animación infinita (`rememberInfiniteTransition`) pedía un fotograma a la frecuencia de la pantalla **todo el tiempo**, aunque el cursor solo cambiara dos veces por segundo;
  - **ahora**: un bucle con `delay(750)` invierte un booleano, y ese booleano se lee en la **capa** (`graphicsLayer { alpha = … }`), no en la composición. Cada parpadeo solo vuelve a pintar la capa del cursor.

## 5. La celda sin nombre y la búsqueda solo por ID

```
> ATP                                               ☆
  C₁₀H₁₆N₅O₁₃P₃ · 47 ATOMS                  ARCHIVED
  ▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇
```

- Se quitó el **nombre químico** gris de la celda. Todo lo demás sigue igual.
- **TalkBack lo sigue leyendo** («Ligand ATP, ADENOSINE-5'-TRIPHOSPHATE, formula…»): a quien no ve la pantalla no le quita sitio y sí le ayuda.
- **La búsqueda por nombre se quitó** con él. Con el nombre invisible, escribir «at» habría devuelto filas sin ninguna «at» a la vista, encontradas por su nombre químico. Un evaluador lo tomaría por un **fallo** del buscador. Se vuelve a buscar **solo por ID**; el texto del tutorial también dice «by their ID».
- `LigandIndex.nameOf` y el parámetro de nombre de `LigandCatalog.search` se quitaron (no queda código muerto). Sus tres pruebas se sustituyeron por una que fija lo contrario: «caffeine» ya **no** encuentra CFF.

## 6. Fotogramas: nada de esto rompe el IDLE

Después de dos rondas persiguiendo un FPS que nunca bajaba a IDLE (documento 21, §18-20), cada efecto nuevo se hizo **para no pedir fotogramas en reposo**:

| Efecto | En reposo | Mientras actúa |
|--------|-----------|----------------|
| Tubo | La cortina con cobertura 0 **no dibuja nada** y no pide nada | Solo se repinta la cortina: la cobertura se lee en la **fase de dibujo**, no se recompone nada |
| Destello | Entre pasadas, **ningún** fotograma (un `delay`, no una animación infinita) | 0,9 s de repintado de la capa del nombre |
| Cursor | — | Dos repintados de su capa por parpadeo |

La pantalla de la molécula **no tiene cabecera** con el nombre ni cursor: su IDLE no cambia.

## 7. Pruebas y mutación

| Fichero | Nuevas | Qué fija |
|---------|--------|----------|
| `CrtTransitionTest` | 6 | En reposo, la imagen entera y sin efectos; se aplasta en una línea y la línea en un punto; la ventana solo encoge al crecer la cobertura, nunca por debajo de la línea, y los efectos en 0..1; fuera de rango = el extremo; tiempos de apagar y encender (encender más largo, todo por debajo de 1 s); un cambio a medias sigue desde donde estaba, y un reloj hacia atrás no mueve nada |
| `GlintTest` | 3 | La banda está fuera del texto en los extremos y no vuelve con un fotograma tardío; solo avanza y se suaviza en los bordes; una pasada dura lo suyo, descansa la mayor parte del ciclo y espera al tubo |
| `LigandIndexTest` | 1 (−3) | La búsqueda ya no mira el nombre |
| `NavigationTest` | (−2) | Las pruebas de la dirección se fueron con ella |

**Mutantes (9, muertos):** sin suelo de línea, cierre que no parte de donde estaba, punto que aparece al aplastar, encender más corto que apagar, reloj hacia atrás sin límite, banda lineal, banda que empieza dentro del texto, banda sin límite de progreso (este **sobrevivió** al principio: se añadió la comprobación de los extremos), y los ids «que contienen» antes que los que «empiezan por».

**Sin prueba automática:** el dibujo de la cortina, la capa del destello y su `SrcAtop`, el parpadeo, el bloqueo de toques. Ver §8.

## 8. Cómo probarlo en el móvil

1. **Arranque en frío**: un punto en el centro se estira en una línea y la línea se abre en la pantalla de arranque.
2. **Índice → ATP**: el índice se aplasta en una línea, la línea en un punto, y el punto se abre en la molécula. **El 3D también queda tapado** por la cortina (si no, es que el SurfaceView se dibuja por encima: avisar).
3. **Atrás**: lo mismo hacia el índice. Tocar la pantalla durante el cambio **no hace nada**.
4. **Ajustes ↔ índice**, **tutorial**: el mismo tubo. Las páginas del tutorial siguen deslizándose.
5. **Destello**: en el índice, cada 6 s la luz cruza «Swifty Proteins»; sobre las letras, **no** sobre el panel. En el tema claro también.
6. **Buscador vacío**: «> _ Search by ID», con el «_» parpadeando; al tocar el cuadro no aparece un segundo cursor; al escribir, el «_» se va.
7. **Celdas**: sin nombre gris; «caffeine» no encuentra nada, «CFF» sí.
8. **Animaciones del sistema desactivadas** (Ajustes → Accesibilidad → Quitar animaciones): cambios inmediatos, sin destello, cursor fijo.
9. **IDLE** en la molécula: el contador de FPS baja a IDLE como antes.

## 9. Trampas

1. **Transformar una pantalla con un SurfaceView dentro**: la superficie de Filament no sigue a `graphicsLayer`. Hay que **tapar**, no transformar.
2. **`SrcAtop` sin capa propia**: se mezcla con lo que hay detrás y se ilumina todo el rectángulo.
3. **La capa propia recorta**: el resplandor de las letras necesita sitio dentro de ella.
4. **Una animación infinita para algo que cambia dos veces por segundo**: pide 90 fotogramas por segundo para nada.
5. **Leer la animación en la composición**: cada fotograma recompondría la pantalla. Leerla **dentro del dibujo** solo repinta.
6. **Dos cursores**: el del campo y el de consola a la vez.
7. **Buscar por algo que no se ve**: el resultado parece un fallo.
8. **Cambiar de pantalla con la vieja leyendo el estado vivo**: se queda sin datos mientras se apaga.

## 10. Alternativas

| Alternativa | Por qué no |
|-------------|-----------|
| `graphicsLayer { scaleY }` sobre la pantalla | El SurfaceView del 3D no lo sigue (§2.3) |
| `AnimatedContent` con transiciones propias (`transition.animateFloat`) | Funciona, pero las dos pantallas estarían **a la vez** en el árbol; con la cortina el intercambio ocurre a oscuras y solo vive una |
| Deslizamiento completo (100 % del ancho, 400 ms) | Claro y estándar, pero sin carácter; el tubo es del mundo de la app |
| *Shared element* (la celda «crece» hasta la molécula) | Muy vistoso, pero el SurfaceView no participa en esas transiciones |
| Un *shader* AGSL para el tubo (curvatura, ruido) | Solo Android 13+; la app es minSdk 24 |
| Destello con `rememberInfiniteTransition` | Fotogramas continuos aunque la banda esté fuera (§6) |
| Destello en todas las letras doradas (títulos de panel) | Demasiado brillo; el nombre de la app es la «placa» de metal |
| Mantener la búsqueda por nombre «oculta» | Resultados que parecen errores (§5) |

## 11. Decisiones

| # | Decisión | Estado | Motivo |
|---|----------|--------|--------|
| D84 | **Cambio de pantalla como un tubo CRT** (`CrtSwitch`): apagar 240 ms, cambiar a oscuras, encender 420 ms; **cortina dibujada encima**, no transformación; toques bloqueados durante el cambio; se quita la dirección | Decidida (sin probar en el móvil) | VII.2; sustituye la animación de D72 |
| D85 | **Destello** sobre «Swifty Proteins» cada 6 s (capa *offscreen* y `SrcAtop`) y **cursor de consola** de 0,75 s en el buscador; los dos sin fotogramas en reposo | Decidida (sin probar en el móvil) | Pulido pedido tras las pruebas |
| D86 | Celda **sin nombre químico** (TalkBack lo lee) y búsqueda **solo por ID** | Decidida | Pulido pedido; un resultado por nombre invisible parece un fallo |

## 12. Qué recordar

- Un bonus de animación **tiene que verse**: 300 ms y ¼ del ancho, a 90 Hz, no se ven.
- Con un **SurfaceView**, lo que va «por encima» se **dibuja**, no se transforma.
- `SrcAtop` + capa propia = luz **solo sobre lo dibujado**.
- Lo que se anima se lee **en el dibujo**; lo que solo cambia de vez en cuando, con un `delay`, no con una animación infinita.
- Si se quita algo de la vista, hay que quitar también **lo que dependía de verlo** (la búsqueda por nombre).

## 13. Después: SHR pasa a SHARE y H baja al panel de teclas

**El problema.** «SHR» era la abreviatura del diseño, pero **no se entendía**: quien no sabe qué es no la toca, y en la defensa el compartir es **obligatorio** (VI.4).

**El sitio.** La fila de arriba de la vista 3D tenía «<», «Pattern ATP», **H** y **SHR**. Con «SHARE» entera (~75 dp en lugar de 52), las cuentas con las fuentes reales (Share Tech Mono 19 sp en las teclas, Pirata One 32 sp en el título):

| Ancho del móvil | Sitio para el título con H y SHARE arriba | «Pattern ATP» necesita |
|-----------------|-------------------------------------------|------------------------|
| A33, 411 dp | ~188 dp | ~150 dp: cabe justo |
| Móvil pequeño, 360 dp | ~137 dp | **no cabe** |

**La solución.** **H** baja al panel de teclas, al final de la fila MODEL… · RULER · LABELS, y solo aparece si la molécula tiene hidrógenos. Además de dar sitio, **ordena** la pantalla:
- **arriba**, quién es la molécula y qué hacer con ella: volver, el nombre, SHARE;
- **abajo**, cómo se **dibuja**: el modelo, la regla, las etiquetas y los hidrógenos.

Con H fuera, el título tiene ~248 dp en el A33 y ~197 dp en un móvil de 360 dp.

- El tutorial dice ahora «SHARE sends a picture…», y su prueba busca «SHARE».
- La comparación (documento 24) conserva su H arriba: allí no hay tecla de compartir.
- En los documentos que siguen vivos (17, 18, 22, 25), «SHR» pasó a «SHARE»; los de diseño y los anteriores lo dejan como estaba en su momento.
- **Alternativas:**
  - un icono de compartir (el estándar de Android) en vez de la palabra: menos sitio, pero se sale del estilo de consola;
  - dos filas en la cabecera: más alta, y quitaría ventana al 3D;
  - «Pattern ATP» → «ATP»: ganaría sitio, pero perdería la placa del diseño.

