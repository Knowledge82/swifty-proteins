# 08a · Dirección visual «Culto de la Máquina» y el prompt para el generador

> **Estado:** **versión 2 del prompt** (sección 12). La versión 1 se usó: el kit se aplicó en [`08b-diseno-aplicado.md`](08b-diseno-aplicado.md), pero **visto en el móvil no transmite el Adeptus Mechanicus**; la v2 corrige el brief. Historia de la v1: el kit que devolvió el generador se revisó y se aplicó en el documento [`08b-diseno-aplicado.md`](08b-diseno-aplicado.md) (qué se tomó, qué no y por qué). Este documento explica **qué se pide y por qué**, deja el **prompt completo** (para copiar tal cual) y describe **cómo se traducirá el resultado a Compose**. Nada de esto toca todavía el código de la app.

## Índice

1. [Por qué el diseño importa aunque no lo pida el enunciado](#1-por-qué-el-diseño-importa-aunque-no-lo-pida-el-enunciado)
2. [La dirección: qué es y por qué encaja](#2-la-dirección-qué-es-y-por-qué-encaja)
3. [Reglas medidas: la paleta y el contraste](#3-reglas-medidas-la-paleta-y-el-contraste)
4. [Restricciones técnicas que el generador debe conocer](#4-restricciones-técnicas-que-el-generador-debe-conocer)
5. [Propiedad intelectual: inspirado en, no copiado de](#5-propiedad-intelectual-inspirado-en-no-copiado-de)
6. [El prompt (copiar y pegar)](#6-el-prompt-copiar-y-pegar)
7. [Qué hacer con lo que devuelva el generador](#7-qué-hacer-con-lo-que-devuelva-el-generador)
8. [Alternativas](#8-alternativas)
9. [Qué está verificado y qué no](#9-qué-está-verificado-y-qué-no)
10. [Decisiones](#10-decisiones)
11. [Qué recordar](#11-qué-recordar)
12. [Versión 2 del prompt: por qué la primera no funcionó](#12-versión-2-del-prompt-por-qué-la-primera-no-funcionó)

---

## 1. Por qué el diseño importa aunque no lo pida el enunciado

El enunciado pide **funciones**; la hoja de evaluación comprueba **funciones**. Pero una defensa la hace una **persona** mirando una **pantalla**: una app correcta y con aspecto de «plantilla gris» se recuerda peor que una correcta y cuidada. Además hay un efecto práctico: **un diseño coherente esconde los fallos pequeños** (un diálogo de error bien hecho se percibe como «ha funcionado como debe»). No es un requisito, pero **no lo ignoramos**.

**Coste de hacerlo bien:** el riesgo no está en el diseño, sino en **romper lo que ya funciona** (lista, carga, 3D). Por eso el plan separa dos pasos: (1) **diseñar** fuera del código (generador), (2) **trasladar** a Compose **solo la «piel»** (tema, formas, tipografías, iconos), **sin tocar la lógica**. La lógica ya tiene 198 pruebas automáticas; la piel se comprueba mirando.

---

## 2. La dirección: qué es y por qué encaja

**«Culto de la Máquina»**: una estética **original** inspirada en el ánimo del *Adeptus Mechanicus* (Warhammer 40.000): **catedral tecnológica oscura**, hierro negro remachado, **latón y oro** desgastados, **rojo marciano** (paño, sellos de cera), **verde fósforo** de terminal, tiras de «letanía» en binario/hexadecimal, engranajes y circuitos.

**Por qué encaja con esta app (y no es solo un capricho):**
- La app **inspecciona moléculas**, objetos que en el mundo real se ven como **máquinas diminutas**. «El espíritu de la máquina de la materia» es un relato natural para un visor de ligandos.
- Una paleta **oscura** es **mejor para el visor 3D**: los colores CPK (blanco, gris, azul, rojo, amarillo) **resaltan** sobre fondo casi negro. Un fondo claro o muy saturado los apagaría.
- El **verde fósforo** es perfecto para **datos** (coordenadas, contadores): refuerza la sensación de «instrumento».

**Lo que NO es:** vapor, cuero, gafas protectoras y tonos sepia (**steampunk**, descartado), ni **neón** *cyberpunk*, ni «corporativo plano».

---

## 3. Reglas medidas: la paleta y el contraste

Una paleta oscura con rojo, latón y verde se estropea muy fácil por **contraste insuficiente**. Por eso la paleta del brief **se calculó** (fórmula WCAG de luminancia relativa; mínimo exigido **4,5 : 1** para texto normal, **3 : 1** para texto grande e iconos). Medido sobre el fondo `iron #17171A`:

| Color | Hex | Contraste | ¿Texto normal? | Uso permitido |
|-------|-----|----------:|:--:|---------------|
| bone (hueso) | `#E9E0C9` | 13,60 | sí | texto principal |
| ash (ceniza) | `#A39D8E` | 6,62 | sí | texto secundario |
| gold (oro) | `#E0B65C` | 9,38 | sí | títulos, selección |
| brass (latón) | `#B8893A` | 5,69 | sí | bordes, iconos, texto secundario |
| phosphor (fósforo) | `#43E07A` | 10,38 | sí | datos, éxito, progreso |
| amber (ámbar) | `#F2A12B` | 8,44 | sí | avisos |
| err | `#FF5A4A` | 5,80 | sí | texto e icono de error |
| **blood (sangre)** | `#C0291D` | **3,05** | **no** | **solo relleno** |
| **martian_red (rojo marciano)** | `#8E1B14` | **1,97** | **no** | **solo relleno** |
| rivet (remache) | `#3A3A42` | 1,59 | no | solo decoración |

**La regla que sale de la tabla (y que el prompt impone):** *el rojo no se usa como color de **texto ni de línea** sobre fondo oscuro; solo como **relleno** con texto «hueso» encima* (hueso sobre `martian_red`: 6,89 ✔; hueso sobre `blood`: 4,47, **por debajo de 4,5**: solo texto grande). Un diseñador humano o una IA suele querer «títulos en rojo sangre» en esta estética; **con estos números quedaría ilegible**. Sobre `plate #232328` los valores bajan algo (hueso 11,9; latón 4,97): **también cumplen**.

---

## 4. Restricciones técnicas que el generador debe conocer

Un generador de diseño **no sabe** cómo se construirá. Si no se le dice, entrega cosas **bonitas e imposibles**. Las restricciones del brief y su razón:

| Restricción | Razón |
|-------------|-------|
| **Solo tema oscuro** | Una variante clara duplica el trabajo y empeora el visor 3D |
| **Sin desenfoque (*blur*) ni filtros sobre el visor** | El visor 3D (SceneView/Filament) pinta en una **superficie de GPU** debajo de la interfaz; el desenfoque de Compose actúa sobre **sus propias capas**, no sobre lo que hay debajo (*creído; no comprobado aquí*). Solo **paneles sólidos semitransparentes** |
| **Colores de átomo fijos (CPK)** | Son una convención científica (documento 07); el fondo se adapta a ellos, no al revés |
| **Formas con esquinas biseladas** | Se programan con `CutCornerShape` (ya existe en Compose); las formas orgánicas complicadas, no |
| **Ornamentos en SVG (vector), sin texturas pesadas** | Un vector se convierte en recurso de Android (`VectorDrawable`) y escala sin pérdida; una textura grande cuesta memoria |
| **Texto funcional literal en inglés** | Decisión D20 (textos en inglés); y un error de red debe **entenderse** a la primera: la «letanía» solo como subtítulo |
| **Objetivos táctiles ≥ 48 dp, contraste ≥ 4,5 : 1** | Accesibilidad básica de Android/WCAG |
| **Fuentes libres (Google Fonts)** | Hay que **empaquetarlas** en la app (y el repositorio es público): no valen fuentes de pago |
| **Icono adaptable de 108 dp con zona segura de 66 dp + capa monocroma** | Es lo que exige Android para iconos modernos y temáticos (documento 07b, sección 2) |

---

## 5. Propiedad intelectual: inspirado en, no copiado de

*Adeptus Mechanicus* y su simbología (la calavera-engranaje, el nombre, el arte) son **propiedad de Games Workshop**. Este es un proyecto **escolar que no se publica**, pero la regla de trabajo es **tomar solo el ánimo estético**:

- **Sin** logotipos, emblemas, marcas denominativas, personajes ni ilustraciones oficiales; **sin** la palabra «Warhammer» ni «Adeptus Mechanicus» como marca dentro de la app. **El nombre en pantalla es «Swifty Proteins».**
- **Emblema original:** un anillo dentado (engranaje) o cabeza de tornillo hexagonal con una **pequeña molécula** (3–5 átomos unidos por varillas) en el centro. Dice «máquina + molécula» y **no** se parece a ningún símbolo existente.
- **Beneficio práctico:** el icono y el emblema son **nuestros**: se pueden enseñar, subir al repositorio y defender sin dudas sobre su origen.

---

## 6. El prompt (copiar y pegar)

Está en **inglés** (los generadores lo siguen mejor y la app también está en inglés). **Se puede pegar entero.** Va delimitado por `~~~~` para que los bloques de código internos no lo rompan.

~~~~markdown
# Brief for a visual-design generator — "Swifty Proteins" (Android, Jetpack Compose)

## 0. What I need from you
Design the complete visual identity and the screens of an Android app that shows 3D molecules ("ligands"). The app already works; it has **no visual design at all** (default grey Material). I need a **striking, coherent, high-quality** look, in the direction below. Output must be **implementable in Jetpack Compose / Material 3** by a developer, so give me **design tokens and vector assets**, not only pictures.

## 1. Art direction: "Cult of the Machine" (original, inspired by the Adeptus Mechanicus mood of Warhammer 40,000)
Mood: a **dark techno-cathedral**. The app is a **cogitator** (a sacred scientific machine) and molecules are **machine-spirits of matter** to be inspected. Think: oiled black iron, riveted plates, worn **brass and gold** filigree, **martian red** cloth and wax seals, **phosphor-green** terminal readouts, binary/hex "litany" strips, cog and circuit ornaments, candle-like warm glows. Reverent, industrial, severe, a bit gothic. **Not** steampunk (no Victorian leather/goggles/steam pipes/sepia), **not** cyberpunk neon, **not** flat corporate.

**Legal / originality rules (important):**
- Take the *aesthetic* only. **Do NOT reproduce** any official Games Workshop logo, emblem, wordmark, icon (no copy of the official skull-and-cog symbol), character, artwork, or text such as "Warhammer", "Adeptus Mechanicus", "Omnissiah" as branding. The app name on screen is **"Swifty Proteins"**.
- Create an **ORIGINAL emblem**: suggestion — a hexagonal bolt-head / toothed gear ring with a **small molecule (3–5 atoms joined by sticks)** in its centre. It must read clearly at 48 px.

## 2. Hard technical constraints (please respect them)
1. **Platform:** Android phone, portrait first (reference 1080×2400 px, 412×915 dp). Landscape must not break (keep layouts flexible).
2. **Dark theme only.** (No light theme needed.)
3. **Text language: English.** Functional texts must stay **literal and clear** (errors must be understandable). Flavour ("litany") text may appear only as a **secondary, small subtitle**, never replacing the functional message.
4. **Fonts must be free and bundleable (OFL / Google Fonts).** Suggestions (you may replace with similarly free ones): display = **Cinzel**; UI = **Rajdhani** (or Oxanium); data/monospace = **Share Tech Mono**. Give sizes in sp.
5. **The 3D viewport is a GPU surface** (SceneView/Filament) behind the interface. Therefore: **no blur, no backdrop filters, no shaders over it**; overlays are only **semi-transparent solid panels**. Keep any animation over it light (the 3D must stay ≥ 30 fps).
6. **Atom colours in the 3D scene are fixed** (CPK: H white, C grey, N blue, O red, S yellow, P orange, etc.). **The scene background must keep them readable**: use a very dark, slightly warm, low-detail background (a faint vignette or faint grid is OK; nothing busy, nothing red-saturated). A selected atom is highlighted **yellow** in the scene.
7. **Shapes must be expressible in code:** prefer **chamfered (cut) corners** (Compose `CutCornerShape`), 1–2 dp borders, simple gradients, and **vector** ornaments (SVG paths) over bitmap textures. Textures only if tiny and tileable.
8. **Accessibility:** body text contrast ≥ **4.5:1**, large text and icons ≥ **3:1**, touch targets ≥ **48 dp**, state never conveyed by colour alone. A contrast-checked starting palette is given below — **keep these rules**: *martian red and blood red must never be used as text/line colour on dark backgrounds (contrast 2–3:1); use them only as fills with bone-coloured text on top.*

## 3. Starting palette (measured contrast on `iron #17171A`)
| Token | Hex | Use | Contrast |
|---|---|---|---|
| void | #0A0A0B | scene / app background | — |
| iron | #17171A | surfaces | — |
| plate | #232328 | raised panels, cards | — |
| rivet | #3A3A42 | decorative lines only | 1.6 (decor) |
| martian_red | #8E1B14 | **fills only** (primary button, seals) | bone on it 6.9 |
| blood | #C0291D | **fills only** (accents); bone text on it is 4.47 → large text only | bone on it 4.47 |
| brass | #B8893A | borders, icons, secondary text | 5.7 |
| gold | #E0B65C | headings, active/selected, key icons | 9.4 |
| bone | #E9E0C9 | primary text | 13.6 |
| ash | #A39D8E | secondary text | 6.6 |
| phosphor | #43E07A | data readouts, success, progress | 10.4 |
| amber | #F2A12B | warnings | 8.4 |
| err | #FF5A4A | error text/icons | 5.8 |
You may refine the hex values, but **report the contrast ratio of every text/background pair you use**.

## 4. Screens and components to design (all states)
For each: a mock at 1080×2400 and a short note on layout/spacing/behaviour.

1. **App icon** (Android *adaptive icon*): 108×108 dp canvas, important content inside the central **66 dp** circle, a solid background colour + a foreground vector + a **monochrome** version (for themed icons). Plus a 512×512 preview.
2. **Launch screen (splash):** solid background + the emblem, centred; shown ~1.5 s; very simple (the system only allows background colour + icon).
3. **Sign-in screen** ("access to the cogitator"): the user chooses **fingerprint/face** or a **password**; password field; "Create account" first-run variant (name + password + confirm); error state ("Wrong password"). *(Functionality is built later; design it now.)*
4. **Ligand list screen:** title bar with the emblem; a **search field** (type to filter in real time; clear button); a list of ~1243 short ids (e.g. `001`, `ATP`, `15P`, `CDL`) — each row ≥ 48 dp, tappable, with a subtle press/ripple state and a divider that looks like a plate seam; scrollbar/fast-scroll indicator welcome; **empty result state** ("No ligand matches 'xyz'"); **catalog-unavailable state**.
5. **Loading dialog** (modal, appears while a ligand downloads): spinner (design a **rotating cog** that can be a vector animation) + text "Loading ligand 001…" + a **Cancel** button.
6. **Error dialogs** (modal), one visual family, five variants with an icon, a **title**, a **plain-language message** and buttons **Retry** / **Close** (or **OK**): (a) *No network connection*, (b) *Ligand not found (404)*, (c) *Server timeout*, (d) *Received data is corrupt*, (e) *Invalid ligand id*.
7. **Molecule screen** (the main one): full-screen 3D viewport; a **top bar** (Back button, title "001 · 87 atoms · 90 bonds", an **"H" toggle chip** to show/hide hydrogens, a **Share** button); a **bottom info card** with two states: *empty* (hint "Drag to rotate, pinch to zoom, tap an atom") and *atom selected* (e.g. "C01 · Carbon (C)" + x/y/z coordinates in monospace); optionally a small **element legend** (CPK colour chips). Bars are **semi-transparent solid panels**.
8. **Share flow:** the Android share sheet is system UI; design only the **in-app button states** and a tiny **toast/snackbar** ("Image ready").
9. **Common components sheet:** buttons (primary filled / secondary outlined / text), text field (normal, focused, error), list row, chip (off/on), top bar, card, dialog, progress (determinate + cog), divider/ornament strips, icon set (back, share, search, clear, fingerprint, close, retry, warning).

## 5. Motion (describe, keep cheap)
Splash → list: fade. Dialog in/out: short scale+fade (150–200 ms). Loading cog: continuous rotation. List row press: brass flash. Molecule screen enter: scene fades in over 250 ms. Respect "reduce motion".

## 6. Deliverables (in this order)
1. **Design principles** (5–8 lines) and **moodboard description** (no copyrighted imagery).
2. **Design tokens**: colours (Material 3 roles: primary, onPrimary, secondary, background, surface, surfaceVariant, outline, error, … **mapped to the palette**), typography scale (name, font, size sp, weight, line height, letter spacing), shapes (corner cut sizes), spacing scale (dp), border widths, elevation substitutes (since flat chamfered panels), opacity of the semi-transparent panels. As a **table and as JSON**.
3. **All screens** from section 4, with states.
4. **Vector assets as SVG** (clean paths, no raster): emblem, app-icon foreground + monochrome, rotating cog, divider ornaments (2–3 variants), corner brackets, the 5 error icons, the icon set.
5. **A contrast report** for every text/background pair.
6. **Implementation notes for Compose**: which parts are `CutCornerShape`, which are `Canvas`/vector drawables, which are plain `Surface`/`Card` with a border; anything that is hard to do in Compose, flagged with a simpler fallback.

## 7. Do / Don't
**Do:** consistency (one ornament language everywhere), generous spacing, strong hierarchy, restrained ornament (decoration frames content, never competes with it), readable first.
**Don't:** steampunk; neon; busy backgrounds behind text; red text on black; tiny ornaments under 12 dp that vanish on a phone; skull imagery as a literal copy of any existing franchise symbol; fake "Warhammer" branding; long flavour text; light theme.
~~~~

**Cómo usarlo:** pegarlo entero en el generador. Si el generador tiene un límite de longitud, pedirle **en este orden** (cada paso es una petición nueva y se le recuerdan las secciones 1–3 del brief): (1) tokens + paleta + tipografía; (2) pantalla de moléculas y lista; (3) diálogos; (4) icono y emblema; (5) resto.

---

## 7. Qué hacer con lo que devuelva el generador

Qué se espera recibir y **dónde aterriza** en el código (**nada de esto está hecho aún**):

| Entregable | Aterriza en | Notas |
|------------|-------------|-------|
| Colores (roles M3) | `ui/theme/Color.kt` y `Theme.kt` (`darkColorScheme(...)`) | La tabla del brief ya usa roles de Material 3 |
| Tipografías | `res/font/*.ttf` + `ui/theme/Type.kt` | Hay que **descargar** las fuentes (OFL) y comprobar su licencia |
| Formas | `ui/theme/Shape.kt` (`CutCornerShape(...)`) | |
| Emblema, ornamentos, iconos | `res/drawable/*.xml` (*vector drawable*, convertidos del SVG) | Android Studio convierte SVG → VectorDrawable (*Vector Asset*); **pueden perder degradados complejos** |
| Icono de la app (adaptable) | `res/mipmap-anydpi-v26/ic_launcher.xml` + capas | Sustituye al provisional (07b) |
| Pantalla de inicio | `res/values/themes.xml` (fondo + icono) | Si se quiere más (nombre, animación): alternativa 11.2 del 07b |
| Mocks de pantallas | Referencia visual al reescribir los *composables* | Se cambian **modificadores, colores y formas**, **no** la lógica |

**Proceso:** (1) tokens y tema → (2) se ve la lista con la piel nueva → (3) diálogos → (4) pantalla 3D → (5) icono/splash. **Después de cada paso, las pruebas automáticas siguen verdes** (no tocan la UI) **y se mira en el móvil**. **Criterios de aceptación:** (a) todos los pares texto/fondo ≥ 4,5 : 1 (se mide con la misma fórmula); (b) los colores CPK se distinguen sobre el fondo del visor; (c) el 3D mantiene la fluidez de antes; (d) los cinco errores se entienden sin leer la letanía.

---

## 8. Alternativas

| # | Alternativa | Ventajas | Inconvenientes |
|--:|-------------|----------|----------------|
| 8.1 | **Elegida:** brief a un generador + traslado a Compose | Resultado vistoso con poco trabajo manual | Hay que **traducir** lo imposible; el generador puede ignorar restricciones |
| 8.2 | Diseñar **a mano** en Compose, directamente | Control total; todo es implementable | Más lento y el resultado depende del ojo de quien lo haga |
| 8.3 | **Material You** (colores dinámicos del fondo de pantalla) | Casi gratis, se integra con el móvil | **Sin identidad propia**; en la defensa todas las apps parecen iguales |
| 8.4 | Tema claro + oscuro | Más completo | El doble de trabajo; el visor 3D se ve peor en claro |
| 8.5 | Otra estética (p. ej. «laboratorio holográfico») | Más limpia y fácil de leer | Menos memorable; **la elegida ya está decidida por el equipo** |

---

## 9. Qué está verificado y qué no

**Verificado:** los **contrastes** de la paleta (calculados con la fórmula WCAG, ver sección 3); que `CutCornerShape` es la clase de Compose usada para esquinas biseladas **(según mi conocimiento de la API; no compilado aquí)**.

**NO verificado:**
- Que el generador **respete** las restricciones (hay que revisar su salida contra la lista de aceptación, sección 7).
- Que las fuentes sugeridas (Cinzel, Rajdhani, Share Tech Mono) estén disponibles con licencia libre **en la versión que se descargue**: **comprobarlo al bajarlas**.
- Que el desenfoque no funcione sobre el visor (sección 4: creído).
- Que el aspecto **funcione en el móvil**: tamaños, brillo, legibilidad al sol.

---

## 10. Decisiones

| # | Decisión | Estado | Motivo |
|---|----------|--------|--------|
| D26 | La dirección visual es **«Culto de la Máquina»** (estética *inspirada en* el Adeptus Mechanicus, **sin elementos de marca**), **solo tema oscuro**, con un **emblema original** (engranaje + molécula) | Decidida (dirección); **valores provisionales** | Encaja con un visor 3D de moléculas; el fondo oscuro realza los colores CPK; la paleta se **midió** para el contraste |

---

## 11. Qué recordar

- **Diseño ≠ lógica:** se cambia **la piel**, no el motor. Las pruebas automáticas siguen verdes.
- **El rojo es solo relleno** (contraste 2–3 : 1 como texto). Para texto: hueso, oro, latón, fósforo.
- **El visor 3D manda:** fondo oscuro y simple, **sin desenfoque encima**, colores CPK intactos.
- **Inspirado en, no copiado de:** emblema propio, sin marcas ajenas.
- **Se pide al generador** tokens + SVG + informe de contraste, no solo imágenes: es lo que se puede **implementar**.

---

## 12. Versión 2 del prompt: por qué la primera no funcionó

**El veredicto en el móvil:** el resultado del paso A (documento 08b) **no parece Warhammer 40.000 ni Adeptus Mechanicus**: parece una app oscura moderna con un engranaje. Las fuentes no transmiten nada. Faltan **servidores (*servitors*), calaveras y consolas verdes**.

**La causa está en el brief, no en el generador.** El generador hizo **exactamente** lo que pedía la v1:

| Lo que pedía la v1 | Efecto |
|--------------------|--------|
| «Inspirado en, **no copiado**», sin calaveras «literales», sin nombres de la franquicia | Quitó **los símbolos que hacen reconocible** la estética |
| «Ornamento **periférico**, contenido soberano, sin fondos cargados» | Interfaz **sobria**: lo contrario del barroco gótico-industrial |
| «**Solo vectores**, sin texturas de mapa de bits» | Imposible pintar servidores, calaveras con detalle, metal gastado |
| Fuentes Cinzel / Rajdhani | Romana clásica + sans técnica: **ni gótica ni de terminal** |
| Verde solo para «datos» | La consola verde, que es **el** rasgo del cogitador, quedó en detalle |

**Qué cambia en la v2:**

- **Pide reconocibilidad explícita** («quien vea una captura debe pensar en Warhammer 40k») y lista los motivos: consolas CRT verdes con líneas de barrido, servo-cráneos, servidores conectados a consolas, túnicas rojas, sellos de pureza, arcos góticos, binario en los márgenes.
- **Arte de mapa de bits permitido** (ilustraciones detalladas en PNG/WebP con transparencia, marcos en **9-slice** para que estiren sin deformar las esquinas, texturas pequeñas repetibles), con un **presupuesto de 4 MB**.
- **Fuentes nuevas** (comprobadas en el repositorio de Google Fonts; muestra en la sección siguiente): **gótica** para títulos (**Grenze Gotisch**, **UnifrakturMaguntia** o **Pirata One**) y **de terminal CRT** para casi todo lo demás (**VT323**).
- **El verde fósforo pasa a ser el color principal del texto** de la interfaz.
- **Textos:** título con sabor Mechanicus **más una línea literal** en inglés (el evaluador debe reconocer cada uno de los cuatro errores del enunciado).
- **Una pantalla de arranque propia** («secuencia de arranque del cogitador») además de la *splash* del sistema: es la alternativa 11.2 del documento 07b.
- **Se mantienen** las restricciones técnicas que **no** son de gusto: el visor 3D es una superficie de GPU (sin desenfoque encima, centro despejado, fondo oscuro liso por los colores CPK), objetivos táctiles de 48 dp, legibilidad, texto real (no dentro de imágenes), apaisado.
- **Lo único que se conserva de la prudencia de la v1:** pedir un **emblema propio** (calavera en engranaje) en vez de **calcar el logotipo oficial**. Todo lo demás de la franquicia se usa como referencia de estilo sin reparos (el proyecto no se publica).

**Fuentes candidatas, comprobadas** (todas OFL en el repositorio de Google Fonts): `VT323-Regular.ttf`, `GrenzeGotisch[wght].ttf` (variable: habría que sacar cortes estáticos como con Cinzel), `UnifrakturMaguntia-Book.ttf`, `PirataOne-Regular.ttf` (declara nombre reservado: se usaría **sin modificar**), `Silkscreen`. **Ojo con la legibilidad:** la gótica solo en títulos grandes; VT323 es de mapa de bits dibujado a 7×9, se lee bien en tamaños medios y grandes, peor por debajo de ~14 sp.

**Qué pasa con el código ya hecho (08b):** **se reaprovecha casi todo**. La estructura (tema de Material 3, componentes propios, funciones puras con pruebas, conversor de iconos, icono adaptable) es la misma; cambian **valores** (colores, fuentes), **recursos** (imágenes, marcos) y el **dibujo** de los componentes. Las 217 pruebas no dependen del aspecto.

### 12.1 El prompt v2 (copiar y pegar)

~~~~markdown
# Brief v2 for a visual-design generator — "Swifty Proteins" as an Adeptus Mechanicus cogitator (Android, Jetpack Compose)

## 0. What went wrong last time, and what I want now
The previous round came back as a **clean, modern, restrained dark UI with a gear logo**. That is NOT what I want. I want the app to look like it was **built by the Adeptus Mechanicus of Warhammer 40,000**: an ancient, grimdark, baroque, gothic-industrial **cogitator terminal** of the Cult Mechanicus. When someone sees a screenshot they should immediately think "Warhammer 40k, Mechanicus". **Be bold, dense and ornate.** Detailed, painterly raster artwork is welcome (see section 6). Do not play it safe.

The app: a viewer of 3D molecules ("ligands"). The user picks a ligand from a list of ~1243 ids, the app downloads it and shows it in 3D (balls and sticks). In the lore of the UI: the user is an **Adept** consulting the **data-vaults** of a **Forge World**; the molecules are **sacred patterns of matter**; the cogitator **invokes the machine spirit** to render them.

## 1. Mood and motifs (use them generously)
- **Cogitator consoles:** black glass CRT screens with **phosphor-green** monochrome text, **scanlines**, slight glow, blinking block cursor, ASCII/box-drawing frames, readouts, hex and **binharic** (binary) "cant" streams in the margins, flickering status lights.
- **Skulls everywhere:** **servo-skulls** (floating skull drones with lenses, antennae, cables, a lamp or scanner beam), skulls set into **cogs**, skulls on brackets and frame corners, skull-and-wing ornaments.
- **Servitors:** grim half-machine figures **wired into consoles**, hunched over keyboards, mechadendrites, tubes and cables plugged into the screen frames. Use them in illustrations: sign-in screen, empty states, loading.
- **Tech-priest iconography:** **red robes** (Martian red cloth), hoods with glowing green or red optics, **brass and bronze** machinery, **gothic arches**, pipes, rivets, gears, pistons, cables, **candles and incense**, **purity seals** (red wax seals with parchment strips bearing small gothic text), **cog halos**.
- **Gothic cathedral meets factory:** pointed arches as frames, heavy bronze bezels around screens, engraved panels, chains, grilles, worn and stained metal.
- **Tone:** grimdark, sacred, ancient, oppressive, reverent, mechanical. **Not** steampunk-Victorian (no top hats, no goggles, no sepia leather), **not** clean sci-fi, **not** corporate flat design.

## 2. Copywriting (Mechanicus flavour + clarity)
UI texts are English. Use Mechanicus flavour **in headings and decorative lines** ("Data-Vault Index", "Invoking the Machine Spirit", "Auspex Readout", "The Machine Spirit is displeased", "Litany of Ignition", "Praise the Omnissiah"). **But** each functional message keeps **one plain-English line** under the flavour, because the app is tested by people who must recognise what happened:
- Loading: flavour "Invoking the machine spirit…" + plain "Downloading ligand 001".
- Errors (5 kinds, each its own illustration/icon): **No network connection**, **Ligand not found (404)**, **Server timeout**, **Received data is corrupt**, **Invalid ligand ID**. Flavour title + plain line, e.g. "THE NOOSPHERE IS SILENT" / "No network connection. Check Wi-Fi or mobile data."
- Buttons stay literal: RETRY, CLOSE, OK, CANCEL, SIGN IN, CREATE ACCOUNT, SHARE.

## 3. Typography (all must be free Google Fonts, they get bundled in the app)
- **Headings / titles:** a **blackletter** gothic font: **Grenze Gotisch** (bold) or **UnifrakturMaguntia** or **Pirata One** — pick the one that reads best at 20–32 sp and say why.
- **Console text, data, ids, coordinates, most UI labels:** a **CRT terminal** font: **VT323** (preferred). Optionally **Silkscreen** for tiny all-caps tags.
- No other fonts. Give a type scale (name, font, size in sp, line height, use).

## 4. Palette (start here, refine if needed)
| Token | Hex | Use |
|---|---|---|
| abyss | #050806 | deepest background, behind the 3D scene |
| console | #08120B | CRT screen glass |
| iron | #16161A | metal panels |
| bronze | #7A5A2E | frames, bezels |
| brass | #B8893A | engraved lines, icons |
| gold | #E0B65C | blackletter headings |
| bone | #E6DCC3 | skulls, parchment, main light text |
| phosphor | #43E07A | console text and data (main UI text colour!) |
| phosphor_dim | #1F7A42 | scanlines, secondary console text, grids |
| mars_red | #8E1B14 | robes, wax seals, primary button FILLS |
| optic_red | #FF3B2F | warning optics, error accents |
| amber | #F2A12B | candle glow, warnings |
Readability rule (keep it): **red is never used as small text on black** (too dark to read); use it as a fill/seal/optic glow. Text on dark = phosphor, bone or gold. Body text ≥ 4.5:1 contrast.

## 5. Screens to design (portrait, 1080×2400 px reference; show every state)
1. **App icon** (adaptive: 108 dp canvas, key content inside the central 66 dp circle; background layer + foreground layer + monochrome version + 512 px preview): an **original skull-in-cog insignia** (do not trace any existing official logo), bone skull, brass/bronze cog, a small green optic glow.
2. **Launch / boot screen** (in-app, ~1.5 s): a cogitator **boot sequence**: the insignia, a green console printing lines ("LITANY OF IGNITION… OK", "MACHINE SPIRIT… AWAKE", "DATA-VAULT… 1,243 PATTERNS"), a progress bar of cogs. Plus a minimal **system splash** version (background colour + insignia only).
3. **Sign-in** ("Noosphere Access — Identify, Adept"): a servitor or tech-priest illustration framing a console; **biometric button** ("Bio-auspex scan" + fingerprint icon, literal sub-label "Fingerprint / face"); password field ("Passphrase"); SIGN IN; link CREATE ACCOUNT. Variants: **create account** (name + password + confirm), **wrong password** error.
4. **Ligand list** ("Data-Vault Index"): the whole screen is a **cogitator console** inside a bronze gothic bezel; a search field styled as a command prompt (`> search by ligand id_`); a counter ("1,243 PATTERNS"); rows as console lines (`> 001 ........ [ INSPECT ]`), each row ≥ 56 dp tall and tappable; skull/purity-seal ornaments on the frame, binharic streams in the margins; states: **searching**, **no match** (servo-skull illustration with "No pattern matches 'xyz'" + CLEAR SEARCH), **catalog unreadable**.
5. **Loading dialog** (modal): a **servo-skull** scanning with a green beam (design it so the skull image can bob up and down and the beam can pulse, done in code), flavour + plain line, CANCEL.
6. **Five error dialogs** (modal), one family, each with its **own illustration**: e.g. broken noosphere antenna (offline), empty archive shelf / skull with question mark (404), hourglass with cog (timeout), corrupted glitching data-slate (corrupt), crossed-out rune (invalid id). Flavour title + plain line + RETRY/CLOSE or OK.
7. **Molecule screen ("Auspex Readout")**: the 3D molecule fills the screen (it is rendered live by a 3D engine, you only design the frame around it). Top bar: back button, gothic title "Pattern 001", console line "87 ATOMS :: 90 BONDS", an **H toggle** (hydrogens shown/hidden, both states), a **SHARE** button (states: normal, working "Preparing pict-capture…", done "Pict-capture ready", disabled). Corner **targeting brackets** around the viewport. Bottom **auspex readout card** in console style: empty state ("Tap an atom to inspect it"), selected atom ("C01 · Carbon (C)" + `X 12.184 Å  Y -5.006 Å  Z 0.472 Å` in green), "Hydrogens hidden" state. An element colour key (CPK dots + symbols).
8. **Component sheet:** buttons (primary = red wax/robe fill with bone text; secondary = bronze frame with green text; text button), input (normal/focused/error), list row (normal/pressed), H toggle (on/off), dialog frame, console panel, dividers (skull, cog, binharic), corner brackets, icon set (back, share, search, clear, fingerprint, close, retry, warning, check).

## 6. Art and assets (raster is welcome now)
- **Illustrations** (servitors, servo-skulls, tech-priest, error pictures, boot screen art): detailed, painterly, grimdark. Deliver as **transparent PNG/WebP**, sized for 1080 px wide screens, **total art budget ≤ 4 MB**.
- **Frames and bezels:** deliver as **9-slice** PNG (stretchable centre, fixed ornate corners), so they fit any screen size. Say where the stretch lines are.
- **Textures** (metal, parchment, stained glass): small **tileable** squares.
- **Icons and the insignia:** **SVG**, single-colour versions too (they get tinted in code).
- **Scanlines, glow, blinking cursor, cog rotation, skull bobbing:** describe them; they will be done in code (no video, no Lottie).

## 7. Technical constraints (keep them, everything else is free)
1. Android, Jetpack Compose + Material 3, dark only. Text is real text (not baked into images), so it can change and scale.
2. **The 3D viewport is a live GPU surface**: nothing can blur or filter it; panels over it are solid or semi-transparent, with frames/brackets **around** it. Keep the centre of the viewport clear. Atom colours are fixed (CPK: H white, C grey, N blue, O red, S yellow, P orange…), so the viewport background must stay very dark and plain (no busy texture behind the molecule).
3. Touch targets ≥ 48 dp. Body text readable on a phone in daylight (≥ 4.5:1).
4. Landscape must not break (frames stretch; that is why 9-slice).

## 8. Deliverables
1. One-paragraph art direction statement.
2. **Tokens**: colours, type scale, spacing, frame/corner sizes, as a table **and** JSON.
3. **All screens and states** from section 5 as PNG 1080×2400, plus a contact sheet.
4. **Assets** from section 6, named for Android (`snake_case`), each with its intended size in dp.
5. **Implementation notes for Compose**: what is an image, what is 9-slice, what is drawn in code (scanlines, glow, brackets), what animates and how.
~~~~

