# 08d · Icono v3: el cráneo del arranque, el 42 y el ligando

> **Estado:** *prompt* listo; **pendiente de generar** e integrar. El icono actual (v2, vectorial y plano) sigue en el proyecto hasta que llegue el nuevo. Las medidas del icono adaptativo salen de la documentación de Android; las del A33 son **cálculo**, no medición.

## Índice

1. [Qué se pide](#1-qué-se-pide)
2. [El punto de partida: el arte del arranque como icono](#2-el-punto-de-partida-el-arte-del-arranque-como-icono)
3. [Teoría: cómo es un icono de Android](#3-teoría-cómo-es-un-icono-de-android)
4. [Qué cabe en 48 píxeles: jerarquía](#4-qué-cabe-en-48-píxeles-jerarquía)
5. [El «42»: por qué números propios y no el logotipo](#5-el-42-por-qué-números-propios-y-no-el-logotipo)
6. [Tres composiciones](#6-tres-composiciones)
7. [El *prompt*](#7-el-prompt)
8. [Qué hacer con lo que devuelva el generador](#8-qué-hacer-con-lo-que-devuelva-el-generador)
9. [Trampas](#9-trampas)
10. [Alternativas](#10-alternativas)
11. [Decisión](#11-decisión)
12. [Qué recordar](#12-qué-recordar)

---

## 1. Qué se pide

- **El enunciado** (documento 01, VI.1): *«Icono acorde con el tema (molecular, científico o médico), con todas las resoluciones necesarias»*.
- **Lo que queremos:** que el icono sea el **cráneo en la rueda dentada** de la pantalla de arranque (`art_skull_cog_art`), mucho más vistoso que el icono plano v2, y que además lleve **el 42** (la escuela) y **una referencia al ligando** (el tema del proyecto: así el icono cumple el «acorde con el tema» por sí mismo, no solo por el estilo).

Una precisión de nombres: en el arranque sale el **cráneo con ojo biónico verde dentro de una rueda dentada**; el **servidor** (*servitor*) es la figura encorvada sobre la consola (`art_servitor`). Para un icono, el cráneo es la elección correcta: una figura entera con brazos, cables y velas a 48 píxeles es papilla. Lo que sí tomamos del servidor son los **rasgos**: implantes, cables, rejilla vocal.

---

## 2. El punto de partida: el arte del arranque como icono

Prueba hecha: el arte actual, sin tocar, metido en el círculo seguro (sección 3) sobre fondo de consola y recortado con máscaras redonda y cuadrada, a 48, 96 y 192 píxeles.

![El arte actual como icono: máscara redonda y cuadrada a 48, 96 y 192 px](img_08d_arte_actual_como_icono.png)

**Resultado:** se lee bien incluso a 48 px: rueda de bronce, cráneo claro y **el ojo verde**, que es el punto de mayor contraste. Problemas:

- **Queda pequeño:** la cinta de pergamino de abajo alarga la figura; al encajarla entera en el círculo, la rueda ocupa menos de lo que podría.
- **No hay ni 42 ni molécula.** Es Warhammer, pero no dice «proteínas».
- **Es un único PNG** con el fondo transparente: no hay capa de fondo propia ni versión monocroma.

Conclusión: la **dirección** es buena y ya está probada; falta una **composición pensada para icono**.

---

## 3. Teoría: cómo es un icono de Android

**Icono adaptativo** (Android 8+, API 26): dos capas de **108 × 108 dp**, *fondo* y *primer plano*. El lanzador las apila y las recorta con **su** máscara (círculo, cuadrado redondeado, «squircle»… depende del fabricante; Samsung usa el cuadrado redondeado). Además puede moverlas un poco por separado (paralaje) al tocarlas.

| Zona | Tamaño | En un lienzo de 1024 px | Qué va ahí |
|------|--------|-------------------------|------------|
| Lienzo completo | 108 dp | 1024 px | Solo margen: se recorta |
| Zona visible | 72 dp (cuadrado central) | 683 px | Lo que la máscara puede dejar ver |
| **Círculo seguro** | **66 dp** de diámetro | **626 px** | **Todo lo importante**: ninguna máscara lo recorta |

- **Monocromo** (Android 13+, «iconos temáticos»): una tercera capa de una sola tinta; el sistema la colorea con la paleta del fondo de pantalla. Si falta, el icono sale con sus colores aunque el usuario active los iconos temáticos.
- **Icono antiguo** (API 24–25, que soportamos: `minSdk 24`): un PNG de **48 × 48 dp** por densidad (`mipmap-mdpi` 48 px … `mipmap-xxxhdpi` 192 px), cuadrado y redondo.
- **Splash del sistema** (Android 12+): usa `windowSplashScreenAnimatedIcon`, que hoy apunta al primer plano del icono (`@drawable/ic_launcher_foreground`, `themes.xml`). El sistema lo recorta a un círculo de **2/3** del lienzo. Nuestro contenido va en el 61 % (66/108), así que cabe.

**Tamaño real:** el A33 tiene una densidad de unos 2,6× (≈ 411 ppp, cálculo), así que el icono de 48 dp mide **≈ 125 px físicos**. El caso peor (48 px) es el de pantallas `mdpi`, raras hoy, pero es la prueba de que la silueta funciona.

---

## 4. Qué cabe en 48 píxeles: jerarquía

No todo cabe igual de legible; hay que decidir **qué se ve a qué tamaño**:

| Nivel | Elemento | Debe leerse a… |
|-------|----------|----------------|
| 1 | Silueta: **cráneo dentro de la rueda dentada** | 48 px |
| 2 | **Ojo verde** (punto de máximo contraste) | 48 px |
| 3 | **Molécula** de bolas y varillas, colores CPK | 96 px (a 48, una mancha de puntos de colores: suficiente) |
| 4 | **«42»** grabado | 125–192 px (el tamaño real en el móvil) |
| 5 | Detalle: remaches, cables, `101010` en la cinta | 192 px o más; a menos, textura |

La regla para el generador: **pocos elementos grandes**, contraste alto entre el hueso claro, el bronce medio y el fondo oscuro; nada de letras pequeñas que tengan que leerse.

---

## 5. El «42»: por qué números propios y no el logotipo

El logotipo oficial de 42 es un «42» geométrico, de palo seco, plano. Meterlo tal cual en un icono pictórico y gótico **chocaría** (dos estilos peleándose) y además es la marca de la escuela. La alternativa, mejor para el tema:

- **«42» con números propios**, grabados en latón o estampados en el **sello de lacre** rojo, al estilo Mechanicus.
- **`101010`** en la cinta de pergamino: es **42 en binario** (32 + 8 + 2), y el binario es la «lengua sagrada» (*binharic*) del Mechanicus. Una referencia doble que un evaluador de 42 pilla al momento.

---

## 6. Tres composiciones

Se piden las tres; se elige mirando las vistas previas a 48/96/192 px.

| | Composición | Ligando | 42 | A favor | En contra |
|---|-------------|---------|----|---------|-----------|
| **A** | Cráneo en la rueda; **del ojo sale un haz verde** que proyecta un holograma de una molécula pequeña sobre la frente o al lado | Holograma (bolas CPK + contorno verde) | Sello de lacre | Cuenta una historia: el cogitador «escanea» el ligando, que es lo que hace la app | El holograma puede emborronarse a 48 px |
| **B** | **La rueda dentada es una molécula:** los dientes son átomos (bolas CPK) unidos por varillas de latón formando un anillo, con el cráneo dentro | Anillo de bolas y varillas | Grabado en la frente o en el sello | Silueta única y muy legible; la molécula es la estructura, no un añadido | Se pierde la rueda clásica del Mechanicus |
| **C** | Cráneo en la rueda; **mecadendritas** (brazos mecánicos) que **sostienen** una molécula delante de la mandíbula | Molécula sujeta | Sello | Muy «servidor» | Más elementos: riesgo de saturar |

---

## 7. El *prompt*

En inglés, como el de la v2 (documento 08a): los generadores entienden mejor el vocabulario del género en inglés. **Adjuntar** al generador dos imágenes:

1. [`img_08d_referencia_estilo.png`](img_08d_referencia_estilo.png): `art_skull_cog_art` sobre fondo oscuro (referencia de **estilo**: mismo pincel, misma paleta).
2. [`img_08d_zonas_seguras.png`](img_08d_zonas_seguras.png): la plantilla de zonas (lienzo 1024 px con el cuadrado visible y el círculo seguro marcados).

```text
# App icon brief — "Swifty Proteins" (Android adaptive icon), v3

## What it is
An Android app that shows 3D molecules ("ligands") as balls and sticks. Its whole UI is styled as a
Warhammer 40,000 Adeptus Mechanicus cogitator terminal (grimdark, gothic-industrial, bronze, bone,
phosphor-green CRT). It is a student project of the coding school "42". The icon must say three things
at once: MECHANICUS (skull + cog + green bionic optic), MOLECULE (a small ball-and-stick ligand),
and 42 (the school).

## Style reference (attached image 1)
Match the attached skull-in-cog artwork EXACTLY in style: painterly, detailed but with bold readable
shapes, worn and scratched bronze cog with rivets, aged bone-coloured skull, a brass bionic eyepiece with
a glowing PHOSPHOR-GREEN lens (#43E07A), cables behind the skull, a red wax purity seal (#8E1B14) with a
parchment strip. Keep this look. Do NOT redesign it into flat, cartoon, vector, or clean sci-fi.

## Composition — make THREE variants
A. HOLOGRAM: skull in the cog; the green optic projects a thin green scanning beam that forms a small
   floating hologram of a ball-and-stick molecule (5–7 atoms) beside or above the skull. The atom balls
   keep their CPK colours (carbon dark grey, oxygen red, nitrogen blue, hydrogen white) with a faint green
   holographic outline.
B. MOLECULAR COG: the cog itself is built as a ring molecule: its teeth are atom balls (CPK colours)
   joined by brass rods (bonds); the skull sits inside the ring with its green optic.
C. MECHADENDRITES: skull in the cog; two thin mechanical tendrils hold a small ball-and-stick molecule in
   front of the jaw.

## The "42"
- Put the number "42" as ENGRAVED or STAMPED numerals, Mechanicus style (gothic / blackletter-inspired
  numerals in bone or brass), on the red wax seal OR engraved on the brass of the cog. Original lettering:
  do NOT copy any existing logo.
- On the parchment strip, write the binary "101010" (which is 42 in binary). Plain, short, centred.
- These are the ONLY texts in the icon. No app name, no other words.

## Legibility rules (most important)
- The icon is seen at 48 px (worst case) and about 125 px on a real phone. At 48 px the silhouette
  "skull inside a cog with a green eye" must still read clearly.
- Few big shapes, strong value contrast: light bone skull, mid-tone bronze, dark background. The green
  optic is the brightest point of the whole icon.
- Fill the safe circle: the cog should be as big as the safe circle allows (see canvas rules), not a
  small medallion in a big empty field. Make the parchment strip short so it does not stretch the
  figure vertically.

## Canvas rules (adaptive icon; attached image 2 shows the zones)
- Canvas 1024 × 1024 px = 108 dp.
- ALL key content (cog, skull, eye, molecule, 42) inside the CENTRAL CIRCLE of 626 px diameter
  (66 dp). Launchers crop with circles, squircles or rounded squares; anything outside that circle
  may be cut off.
- Nothing important outside the central 683 × 683 px square (72 dp). Outside it: empty.

## Deliverables (per variant)
1. ic_launcher_foreground.png — 1024 × 1024, RGBA with REAL transparency. Only the emblem. Clean, hard,
   dark-friendly edges: NO white or grey halo, NO glow fringe onto transparent pixels, NO painted
   checkerboard pretending to be transparency.
2. ic_launcher_background.png — 1024 × 1024, fully opaque. Very dark and calm: abyss #050806 to
   console #08120B, at most a soft green vignette or faint scanlines in the centre. No details that
   matter (it gets cropped and shifted).
3. ic_launcher_monochrome.png — 1024 × 1024, a single-colour (white on transparent) SIMPLIFIED
   silhouette of the same emblem (cog + skull + eye socket + molecule), used by Android 13 themed icons.
   Same size and position as the foreground.
4. preview sheet — the foreground over the background, shown in a circle mask, a squircle mask and a
   rounded-square mask, each at 48, 96 and 192 px, on light and on dark wallpaper.
5. composite_on_dark.png — the foreground alone over #050806, 1024 px, so edge halos are visible.

## Avoid
Flat or vector look, cartoon, glossy 3D plastic, clean futuristic sci-fi, steampunk (goggles, top hats,
sepia leather), any official logo, any text other than "42" and "101010", tiny details that only exist
at full size, white edge halos.
```

---

## 8. Qué hacer con lo que devuelva el generador

El plan, para cuando lleguen las imágenes:

1. **Revisión** con las vistas previas: elegir A, B o C (o mezclar).
2. **Limpieza del borde** con el mismo procedimiento que el resto del arte (documento 08c, 3): medir el halo sobre fondo oscuro, quitarlo y difuminar 2 px. Esta vez se pide sin halo, pero **se comprueba igual**.
3. **Medir la zona segura con un guion**: el radio máximo de los píxeles opacos del primer plano respecto al centro debe ser ≤ 313 px (626 / 2) en el lienzo de 1024. Es una comprobación numérica, no a ojo.
4. **Exportar por densidad** (WebP sin pérdida o PNG):

   | Carpeta | Capas adaptativas (108 dp) | Icono antiguo (48 dp) |
   |---------|----------------------------|-----------------------|
   | `mipmap-mdpi` | 108 px | 48 px |
   | `mipmap-hdpi` | 162 px | 72 px |
   | `mipmap-xhdpi` | 216 px | 96 px |
   | `mipmap-xxhdpi` | 324 px | 144 px |
   | `mipmap-xxxhdpi` | 432 px | 192 px |

5. **`mipmap-anydpi-v26/ic_launcher.xml`** (y `_round`): las tres capas pasan de `@drawable/…` (vectores) a `@mipmap/…` (mapas de bits).
6. **Monocromo:** el PNG del generador, o mejor, **vectorizado** (una silueta simple se convierte bien a vector y escala sin pérdida).
7. **Splash del sistema:** `windowSplashScreenAnimatedIcon` debe seguir apuntando al primer plano nuevo; comprobar en el móvil que el recorte circular no se come nada.
8. **Iconos antiguos** (`ic_launcher.png`, `ic_launcher_round.png`): se generan **con un guion** a partir de fondo + primer plano, recortados con su máscara; no hace falta pedirlos al generador.

---

## 9. Trampas

1. **El halo otra vez:** los generadores «inventan» transparencia con bordes claros. Se pide explícitamente y se comprueba sobre fondo oscuro (entrega 5).
2. **Tablero de ajedrez pintado:** a veces la «transparencia» es un dibujo gris y blanco dentro de un PNG opaco. Se comprueba que el canal alfa existe y tiene valores 0.
3. **Texto ilegible:** cualquier letra que deba leerse a 48 px fracasa. Por eso solo «42» y `101010`, y ninguno es imprescindible a ese tamaño (jerarquía, sección 4).
4. **El generador ignora el círculo seguro:** lo más habitual. La medición numérica del paso 3 lo detecta; si se sale, se reescala el primer plano (con cuidado de no dejarlo pequeño).
5. **Mapas de bits en `drawable/` sin densidad:** el sistema los escalaría desde `mdpi` y saldrían borrosos. Van en `mipmap-<densidad>`.
6. **Caché del lanzador:** al cambiar el icono, algunos lanzadores (Samsung incluido) siguen mostrando el viejo hasta reiniciar el lanzador o desinstalar. No es un fallo nuestro.

---

## 10. Alternativas

| Opción | Cómo | A favor | En contra |
|--------|------|---------|-----------|
| **Arte actual tal cual** | `art_skull_cog_art` como primer plano + fondo de consola | Funciona **ya**, probado a 48/96/192 px | Sin 42 ni ligando: el «acorde con el tema» queda flojo |
| **Composición a mano** | Arte actual + molécula y «42» dibujados por código encima | Sin generador; control total | Mezcla pintura y vector: suele verse pegado |
| **Generador** (elegida) | El *prompt* de la sección 7 | Una pieza coherente con todo dentro | Hay que limpiar y medir el resultado |
| **Vector redibujado** | Trazar el cráneo a mano en vector | Nítido a cualquier tamaño | Mucho trabajo y pierde el aspecto pictórico, que es lo que gusta |

La primera sirve de **plan B**: si el generador no da algo mejor, se integra el arte actual en una hora.

---

## 11. Decisión

| # | Decisión | Estado | Motivo |
|---|----------|--------|--------|
| D38 | El icono pasa a ser **pictórico**: el cráneo en la rueda del arranque, con **ligando** de bolas y varillas y **«42»** (números propios + `101010`), como icono adaptativo de mapas de bits | Propuesta (*prompt* listo) | Mucho más reconocible que el icono plano; cumple «acorde con el tema» con la molécula; el 42 en binario conecta con el *binharic* del Mechanicus |

---

## 12. Qué recordar

- **Icono adaptativo:** lienzo de 108 dp, visible 72 dp, **lo importante en el círculo de 66 dp** (626 px de 1024).
- **Tres capas:** fondo, primer plano y **monocromo** (iconos temáticos de Android 13+); más el icono antiguo de 48 dp para API 24–25.
- **Jerarquía por tamaño:** a 48 px solo se lee la silueta y el ojo; el «42» es para el tamaño real (~125 px).
- **42 = `101010`** en binario.
- Lo que devuelva el generador **se mide** (radio del contenido, canal alfa, halo), no se integra a ojo.
