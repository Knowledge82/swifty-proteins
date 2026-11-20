# 08b · El diseño aplicado: tema, fuentes, iconos y pantallas (paso A)

> **Estado:** escrito, con la lógica nueva probada en el ordenador (19 pruebas más, 217 en total; 12 mutantes muertos) y el código de interfaz comprobado **contra declaraciones escritas a mano** de la API de Compose. **No compilado con las bibliotecas reales ni visto en el móvil**: es lo primero que hay que hacer (sección 11).

## Índice

1. [Qué es el «paso A» y qué no incluye](#1-qué-es-el-paso-a-y-qué-no-incluye)
2. [Revisión del kit del generador](#2-revisión-del-kit-del-generador)
3. [Mapa: qué fichero hace qué](#3-mapa-qué-fichero-hace-qué)
4. [Las fuentes: licencias, fuente variable y glifos](#4-las-fuentes-licencias-fuente-variable-y-glifos)
5. [Los iconos: de SVG a VectorDrawable](#5-los-iconos-de-svg-a-vectordrawable)
6. [El tema: colores, tipografía y formas](#6-el-tema-colores-tipografía-y-formas)
7. [Los componentes](#7-los-componentes)
8. [Las pantallas: qué cambió y qué no](#8-las-pantallas-qué-cambió-y-qué-no)
9. [La lógica nueva (pura y probada)](#9-la-lógica-nueva-pura-y-probada)
10. [Desvíos conscientes respecto al diseño](#10-desvíos-conscientes-respecto-al-diseño)
11. [Cómo probarlo en el móvil](#11-cómo-probarlo-en-el-móvil)
12. [Qué está verificado y qué no](#12-qué-está-verificado-y-qué-no)
13. [Pruebas y mutación](#13-pruebas-y-mutación)
14. [Alternativas](#14-alternativas)
15. [Decisiones](#15-decisiones)
16. [Qué recordar](#16-qué-recordar)

---

## 1. Qué es el «paso A» y qué no incluye

El orden acordado tras recibir el kit del generador: **A** = aplicar el diseño a **lo que ya existe** (tema, fuentes, icono, *splash*, lista, diálogos, pantalla de la molécula); **B** = captura y «compartir» (H4); **C** = inicio de sesión. Razón: el kit ya dibuja las pantallas de **B** y **C**, así que se construirán **directamente con el aspecto final**, sin hacerlas dos veces; y el diseño se puede juzgar en el móvil **antes** de construir más pantallas sobre él.

**Regla del paso A: se cambia la piel, no el motor.** Ninguna clase de lógica (parser, red, catálogo, cargador, `ViewModel`, escena 3D) se ha tocado. Las firmas públicas de las pantallas (`LigandListScreen`, `LoadStateDialogs`, `MoleculeScreen`) son **las mismas**; `SwiftyApp` no cambió.

**No incluye:** el botón «Share» (llega con B; un botón que no hace nada parecería un fallo), las pantallas de acceso (C), y lo que la sección 10 explica.

---

## 2. Revisión del kit del generador

El kit (105 ficheros) trae: especificación (`DESIGN_SPEC.md`), *tokens* en JSON, informe de contraste, **23 pantallas** en SVG y PNG, **39 recursos SVG** originales, un borrador de icono adaptable para Android y **dos ficheros Kotlin de ejemplo**. Calidad: **buena**; respeta el brief del documento 08a (rojo solo como relleno, sin desenfoque sobre la escena, emblema original, objetivos táctiles de 48 dp).

Lo que **no** se tomó tal cual, y por qué:

| Hallazgo | Detalle | Qué se hizo |
|----------|---------|-------------|
| **Las previsualizaciones no usan las fuentes reales** | El propio kit lo dice: se dibujaron con fuentes de reserva del sistema | El aspecto en el móvil **será distinto** de las imágenes (Cinzel y Rajdhani son más estrechas y «técnicas») |
| **Un cuadrado en lugar del signo menos** (`Y □5.006`) | La previsualización usó el signo tipográfico U+2212, que su fuente de reserva no tenía | Se comprobó que **nuestras seis fuentes sí lo tienen** (sección 4), y nuestras coordenadas usan el guion ASCII de todos modos (`Locale.US`) |
| **El código Kotlin es un ejemplo, no un fichero listo** | Paquete `com.example`, su propio tipo de error (`LigandError`, 5 casos frente a nuestros 10 `LoadError`) y un *composable* `LigandCatalog` **con el mismo nombre que nuestra clase** de catálogo | Se tomaron **los valores y las ideas**; los componentes se escribieron de nuevo sobre nuestros tipos |
| **El icono para Android que trae está incompleto** | Su `ic_launcher_foreground.xml` **pierde** el hexágono y los extremos redondeados de las varillas respecto a su propio SVG | Se generó de nuevo desde el SVG con un conversor propio y se comparó píxel a píxel (sección 5) |
| **Decoración sin función** | Un menú «…» en la barra y el rótulo «VIEWPORT / FILAMENT» sobre la escena | **No se pusieron**: un botón que no hace nada parece un fallo en la defensa |
| **Su especificación desaconseja retener la *splash* 1,5 s** | «*never artificially stall a fast start*» | **Manda la hoja de evaluación** (≥ 1 s, documento 07b): se mantiene |
| **Colores CPK ligeramente distintos** | Proponen otros tonos para H, C, N, O, S, P | Se mantiene la tabla de Jmol (D21): es la convención y ya está probada |

---

## 3. Mapa: qué fichero hace qué

| Fichero | Qué es | Estado |
|---------|--------|--------|
| `ui/theme/Color.kt` | La paleta (`SwiftyColors`), con los nombres que avisan (rojo = solo relleno) | Nuevo (sustituye al de la plantilla) |
| `ui/theme/Type.kt` | Las tres familias de fuentes y los estilos de texto (`SwiftyTypography`, `SwiftyDataText`) | Nuevo |
| `ui/theme/Shape.kt` | Esquinas biseladas (`CutCornerShape`) | Nuevo |
| `ui/theme/Theme.kt` | `SwiftyProteinsTheme`: siempre oscuro, sin «color dinámico» | Reescrito |
| `ui/SwiftyComponents.kt` | Placa, botones, botón-icono, interruptor «H», engranaje que gira, barra de marca, ornamento, etiqueta, pozo de icono, panel de diálogo | Nuevo |
| `ui/LigandListScreen.kt`, `ui/LoadDialogs.kt`, `ui/MoleculeScreen.kt` | Las tres pantallas con el aspecto nuevo | Reescritos (misma lógica) |
| `ui/ListTexts.kt`, `ui/AtomText.kt`, `scene/ElementLegend.kt` | Textos y leyenda, **puros y probados** | Nuevos |
| `ui/LoadErrorText.kt` | + título y dibujo de cada error | Ampliado |
| `MainActivity.kt` | Barras del sistema siempre oscuras | Una línea cambiada |
| `res/font/*.ttf` (6), `assets/licenses/fonts/*.txt` (3) | Fuentes y sus licencias | Nuevos |
| `res/drawable/*.xml` (18) | Emblema, capas del icono, engranaje, 14 iconos | Nuevos |
| `res/mipmap-anydpi-v26/*.xml`, `res/mipmap-*/ic_launcher*.png` | Icono adaptable y, para Android 7, iconos de píxeles | Rehechos |
| `res/values/themes.xml`, `colors.xml` | Ventana oscura, *splash* con el emblema sobre hierro | Reescritos |

---

## 4. Las fuentes: licencias, fuente variable y glifos

**Licencia.** Las tres son **SIL Open Font License 1.1**: se pueden **incluir en una app** (también de pago) con dos condiciones que nos afectan: (1) **acompañar la licencia** (está en `assets/licenses/fonts/`, viaja dentro del APK), y (2) si la fuente declara un **«Reserved Font Name»** (RFN), una versión **modificada** no puede llevar ese nombre. **Share Tech Mono declara RFN «Share»** → se incluye **sin tocar**. Cinzel y Rajdhani **no declaran RFN**.

**El problema de Cinzel.** En el repositorio de Google Fonts, Cinzel solo existe como **fuente variable** (`Cinzel[wght].ttf`: un único fichero con un **eje** de grosor de 400 a 900). Elegir un punto del eje desde Compose necesita **API 26**; la app empieza en **API 24**: en Android 7 se vería siempre con el grosor por defecto. **Solución:** se generaron **dos cortes estáticos** (400 y 700) con `fontTools` (`instancer`), que fija el eje en un valor y produce un TTF normal (≈ 77 KB cada uno). Como Cinzel no tiene RFN, la licencia lo permite.

**Rajdhani** pesa ≈ 390 KB por grosor (lleva también **devanagari**, la escritura del hindi). Se podría recortar a latín (≈ 1 MB menos), pero **no se hizo**: menos cambios, cero riesgo de quitar un glifo que luego haga falta.

**Glifos comprobados** (con `fontTools`, mirando la tabla de caracteres de cada fichero): los seis contienen **todo el ASCII** y `° · Å … −`, los caracteres no ASCII que usa nuestra interfaz o que podríamos usar. Es exactamente el fallo que se vio en las previsualizaciones del kit (sección 2): **comprobado, no supuesto**.

---

## 5. Los iconos: de SVG a VectorDrawable

**Por qué hay que convertir.** Android **no dibuja SVG**: usa su propio formato vectorial, **VectorDrawable** (un XML con `<path android:pathData="...">`). Lo habitual es convertirlos con Android Studio (*Vector Asset*), que no está disponible en este entorno; y el icono que traía el kit ya convertido **estaba mal** (sección 2).

**El conversor** (`svg2vd.py`, fuera del repositorio): entiende **exactamente** el subconjunto de SVG que usa el kit (`path`, `circle`, `rect`, `polygon`, `line`, grupos `g` con atributos heredados; relleno, trazo, grosor, extremos, uniones, regla par-impar) y **falla con error** ante cualquier otra cosa, para no producir nunca un dibujo silenciosamente distinto. La idea clave: `pathData` de Android **usa la misma sintaxis** que el atributo `d` de SVG; un círculo se escribe como dos arcos, un rectángulo como cuatro líneas.

**La verificación.** Además del VectorDrawable, el conversor escribe un **SVG «aplanado»** con exactamente los mismos caminos y atributos. Se dibujaron el original y el aplanado con `rsvg-convert` a 216 px y se compararon **píxel a píxel**: **0 píxeles distintos en los 18**. Eso prueba que la conversión de formas y la herencia de atributos son correctas. *(Lo que no prueba: que Android dibuje `pathData` idéntico a `rsvg`; es el mismo lenguaje, pero no se ha visto.)*

**El icono de la app**, en tres capas vectoriales:

- **Fondo** (`ic_launcher_background`): hierro `#17171A`.
- **Primer plano** (`ic_launcher_foreground`): el emblema. **Medido:** el píxel más lejano del centro está a **30,7 dp**, dentro del círculo seguro de **33 dp** (66 dp de diámetro).
- **Monocromo** (`ic_launcher_monochrome`): la misma silueta; Android 13+ lo tiñe con los colores del sistema («iconos temáticos»). Las versiones anteriores ignoran la etiqueta `<monochrome>`.

**Android 7 (API 24–25) no tiene iconos adaptables**: usa imágenes `mipmap-*/ic_launcher.png`. Se generaron a partir de las mismas capas: se dibuja el lienzo de 108 dp, se recorta la **zona visible** (los 72 dp centrales) y se aplica una máscara de **cuadrado redondeado** (`ic_launcher`) o **círculo** (`ic_launcher_round`) en cinco densidades (48 a 192 px). Se borraron los `.webp` antiguos (dos ficheros con el mismo nombre y distinta extensión **chocan** en Android).

---

## 6. El tema: colores, tipografía y formas

- **`SwiftyColors`** (`Color.kt`): los 13 colores medidos del documento 08a + `IronOverlay` (hierro al 96 % para los paneles **encima** de la escena). Los comentarios dejan escrito qué **no** se puede hacer con cada uno.
- **Roles de Material 3** (`Theme.kt`): Material colorea sus componentes por **roles** (`primary`, `surface`, `onSurface`, `outline`…). Se asigna cada rol a un color de la paleta (tabla de la especificación del kit). Así, cualquier componente de Material que se use sin colores explícitos **sale ya en la estética**.
- **Siempre oscuro, sin color dinámico:** la plantilla traía `dynamicColor = true`, que en Android 12+ **repinta la app con los colores del fondo de pantalla** del usuario. Se quitó: la app tiene **un** aspecto (D26).
- **Tipografía** (`Type.kt`): Cinzel (títulos ceremoniales, poco), Rajdhani (toda la interfaz), Share Tech Mono (identificadores y coordenadas). Los estilos monoespaciados viven en `SwiftyDataText`, porque Material 3 no tiene casilla para ellos. `headlineMedium` pide grosor 600 en el kit; solo hay cortes 400 y 700 de Cinzel → se usa **700**.
- **Formas** (`Shape.kt`): `CutCornerShape` de 2 a 16 dp; los componentes de Material eligen la suya por tamaño.
- **Fuera de Compose** (`themes.xml`): el tema de la **ventana** pasa a ser **oscuro** con fondo `void`. Entre la *splash* y el primer fotograma de Compose se ve un instante **la ventana**: con el tema claro de la plantilla, eso era **un destello blanco**.
- **Barras del sistema** (`MainActivity`): `enableEdgeToEdge(SystemBarStyle.dark(...))`. Por defecto, Android elige el color de los iconos de la barra según el **modo del sistema**; con el móvil en modo **claro** dibujaría **iconos oscuros sobre nuestro fondo oscuro**: reloj y batería invisibles.

---

## 7. Los componentes

Todos son **envoltorios finos** sobre componentes de Material 3: el **comportamiento** (pulsación, *ripple*, accesibilidad, foco) es de Material; solo cambian colores, formas y bordes. Todos los objetivos táctiles miden **≥ 48 dp**.

```kotlin
// SwiftyComponents.kt · líneas 158–189
158  @Composable
159  fun HydrogenChip(on: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
160      val ink = if (on) SwiftyColors.Gold else SwiftyColors.Ash
161      Surface(
162          onClick = onToggle,
163          modifier = modifier
164              .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
165              .semantics {
166                  contentDescription = "Hydrogen atoms"
167                  stateDescription = if (on) "shown" else "hidden"
168              },
169          shape = CutCornerShape(5.dp),
170          color = SwiftyColors.Plate,
171          contentColor = ink,
172          border = BorderStroke(1.dp, if (on) SwiftyColors.Gold else SwiftyColors.Rivet),
173      ) {
174          Row(
175              modifier = Modifier.padding(horizontal = 10.dp),
176              verticalAlignment = Alignment.CenterVertically,
177              horizontalArrangement = Arrangement.Center,
178          ) {
179              Text("H", style = SwiftyDataText.large, color = ink)
180              Spacer(Modifier.width(4.dp))
181              Icon(
182                  painterResource(if (on) R.drawable.ic_check else R.drawable.ic_close),
183                  contentDescription = null,
184                  tint = ink,
185                  modifier = Modifier.size(14.dp),
186              )
187          }
188      }
189  }
```

- **`semantics { contentDescription; stateDescription }`:** TalkBack (el lector de pantalla) dice «Hydrogen atoms, shown». El estado se comunica **escrito («✓/×»), dibujado (borde dorado) y hablado**, nunca solo por color.
- **`Surface(onClick = …)`:** la variante **pulsable** de `Surface`; da el *ripple* y el rol de botón.

```kotlin
// SwiftyComponents.kt · líneas 195–213
195  @Composable
196  fun RotatingCog(modifier: Modifier = Modifier) {
197      val context = LocalContext.current
198      val animationsOff = remember(context) {
199          Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
200      }
201      val turn = rememberInfiniteTransition(label = "cog")
202      val angle by turn.animateFloat(
203          initialValue = 0f,
204          targetValue = 360f,
205          animationSpec = infiniteRepeatable(tween(durationMillis = 1300, easing = LinearEasing)),
206          label = "cog angle",
207      )
208      Image(
209          painter = painterResource(R.drawable.loading_cog),
210          contentDescription = null,
211          modifier = modifier.size(48.dp).rotate(if (animationsOff) 0f else angle),
212      )
213  }
```

- **`rememberInfiniteTransition` + `animateFloat`:** un valor que va de 0 a 360 cada 1,3 s y vuelve a empezar; `rotate()` gira el dibujo. Es **solo composición** (no vuelve a dibujar el vector), barato.
- **Movimiento reducido:** si el usuario puso la escala de animaciones a 0 (opciones de desarrollador o accesibilidad), el engranaje **se queda quieto**. *(Se lee una vez al aparecer el diálogo.)*

El resto, en una línea cada uno: **`SwiftyPlate`** (placa con borde, sin sombra: el diseño sustituye las sombras por bordes), **`PrimaryButton`** (relleno rojo, texto hueso, 6,9 : 1), **`SecondaryButton`** (hierro, borde latón, texto oro), **`IconPlateButton`** (cuadrado de 48 dp, botón «atrás»), **`BrandBar`** (emblema + nombre + línea de latón), **`SeamDivider`** (el ornamento, dibujado con `Canvas`: línea, soporte de latón y punto de oro), **`SectionLabel`** (versalitas pequeñas), **`IconWell`** (círculo oscuro con icono, cabeza de los diálogos), **`DialogPanel`** (el panel de los diálogos).

---

## 8. Las pantallas: qué cambió y qué no

**Lista** (`LigandListScreen`, diseño 05–08): barra de marca; título «Ligand catalog» en Cinzel; debajo, en verde, el contador (`1,243 ENTRIES` / `2 RESULTS`); buscador con icono de lupa y **botón de borrar de 48 dp**; cabecera «ID … ACTION»; cada fila es una placa de **56 dp** con una marca roja (relleno), el identificador en oro monoespaciado e «INSPECT ›»; estados vacíos con icono, ornamento, título, explicación y, si la búsqueda no encuentra nada, botón **«Clear search»**; línea de estado al pie. **Igual que antes:** la búsqueda (`remember(catalog, query)`), las claves de `LazyColumn`, ocultar el teclado al tocar, `imePadding()`.

**Diálogos** (`LoadDialogs`, diseño 09–14): se pasó de `AlertDialog` a **`Dialog` + panel propio**. `AlertDialog` tiene una disposición fija (botones a la derecha, sin sitio para el icono en su pozo ni para dos botones de igual anchura). **Igual que antes:** el `when` sin `else`, el diálogo de carga **modal** que no se cierra tocando fuera, «Atrás» = cancelar, el mensaje técnico al Logcat, **qué errores ofrecen «Retry»** (`canRetry`). Nuevo: cada error tiene **título** y **dibujo** (sección 9).

**Molécula** (`MoleculeScreen`, diseño 15, 16, 18): barra superior de hierro al 96 % con botón atrás de 48 dp, «LIGAND 001» y «001 · 87 atoms · 90 bonds», interruptor «H ✓/×»; **leyenda de colores** de los elementos dibujados; tarjeta inferior con tres estados (ayuda; «Hydrogens hidden»; átomo seleccionado con coordenadas en verde monoespaciado). **Igual que antes:** la selección y el interruptor sobreviven al giro (`rememberSaveable`), el átomo oculto no se resalta, «Atrás». **La escena 3D (`MoleculeScene`) no se tocó.**

---

## 9. La lógica nueva (pura y probada)

Todo lo que **decide** algo (no solo dibuja) se sacó a funciones puras, como en las etapas anteriores:

```kotlin
// ListTexts.kt · líneas 15–19
15  fun status(available: Boolean, total: Int, query: String, shown: Int): String = when {
16      !available -> "CATALOG UNAVAILABLE"
17      query.isBlank() -> plural(total, "ENTRY", "ENTRIES")
18      else -> plural(shown, "RESULT", "RESULTS")
19  }
```

- **`Locale.US`** para el separador de miles: con el móvil en alemán o español, `%,d` daría `1.243`; el diseño quiere `1,243` siempre. **Hay una prueba que pone el idioma en alemán** y su mutante.
- **`isBlank()`** (no `isEmpty()`): una búsqueda de solo espacios cuenta como vacía, igual que en el catálogo (documento 06b).

```kotlin
// ElementLegend.kt · líneas 14–28
14  /**
15   * @param showHydrogens when `false`, H is not drawn, so it is not in the key either
16   * @return element symbols, by number of atoms (descending) and then alphabetically, at most [MAX_KEYS]
17   */
18  fun of(molecule: Molecule, showHydrogens: Boolean): List<String> =
19      molecule.atoms
20          .asSequence()
21          .map { it.element }
22          .filter { showHydrogens || it != "H" }
23          .groupingBy { it }
24          .eachCount()
25          .entries
26          .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
27          .take(MAX_KEYS)
28          .map { it.key }
```

- **La leyenda muestra lo que está dibujado:** con «H» apagado, el hidrógeno sale de la leyenda. Orden: **más átomos primero**, empates por orden alfabético (para que no «baile»); máximo **6** (lo que cabe en una fila).

`AtomText.coordinate` escribe `X 12.184 Å` con **tres decimales**, **punto** y **guion ASCII** en cualquier idioma del móvil. `LoadError.title()` y `LoadError.glyph()` amplían `LoadErrorText.kt` con la misma regla de `when` **sin `else`**: un error nuevo no compila hasta tener título y dibujo. El diseño trae **cinco** dibujos; nuestros otros cinco errores usan el triángulo de aviso.

---

## 10. Desvíos conscientes respecto al diseño

| Diseño | Lo hecho | Por qué |
|--------|----------|---------|
| Fondo de la escena `#100F10` (carbón cálido) | **Negro** (el de SceneView) | El fondo de SceneView es el **cielo** (*skybox*) del entorno. La vía segura (`environmentLoader.createEnvironment(skybox = …)`, la que usa el propio SceneView) **quita la luz indirecta** → bolas planas. Cambiar solo el cielo obliga a gestionar a mano la **destrucción** de objetos de Filament: riesgo de cierre. La diferencia de color es casi invisible. **Leído en el código de SceneView 4.69.4** |
| Botón «Share» en la barra | No está | Llega con la captura (paso B) |
| Menú «…», rótulo «VIEWPORT / FILAMENT» | No están | Sin función: parecerían fallos |
| Etiqueta «C01» junto al átomo, dentro de la escena | No está | Necesita proyectar 3D → pantalla en cada fotograma; el nombre ya está en la tarjeta |
| «CATALOG UNAVAILABLE» en verde, con «Retry» | En **rojo de error**, sin «Retry» | El verde es «dato de máquina», no fallo; y nuestro catálogo es un fichero **de la propia app**: reintentar leerlo no cambia nada (el diseño supuso que venía de la red) |
| Tonos CPK propios | Tabla de Jmol | D21 |
| *Splash* sin retención | 1500 ms | Hoja de evaluación (D25) |
| Escena a pantalla completa bajo la barra de estado | Como antes (debajo de las barras) | No cambiar el encuadre ya comprobado; se puede probar después |

---

## 11. Cómo probarlo en el móvil

| # | Qué hacer | Qué debe verse |
|--:|-----------|----------------|
| 1 | **Compilar.** | Si falla, pegar el error: los nombres de parámetros de Compose se escribieron de memoria y se comprobaron contra declaraciones propias, no contra la biblioteca |
| 2 | Mirar el **icono** en el lanzador | Engranaje dorado con la molécula, sobre fondo oscuro. Con iconos temáticos (Android 13+, si están activados): la misma silueta en los colores del sistema |
| 3 | Abrir la app (cerrada antes) | *Splash*: el emblema sobre hierro, ~1,5 s; **sin destello blanco** después |
| 4 | Lista | Fuentes **nuevas** (títulos en Cinzel, ids monoespaciados), filas de placas, contador verde, reloj y batería **visibles** en la barra (probar también con el móvil en **modo claro**) |
| 5 | Buscar «at», luego «xyz», luego la **X** del buscador y «Clear search» | Contador `N RESULTS`; estado vacío con lupa; el buscador se vacía |
| 6 | Abrir `001` con el modo avión puesto | Diálogo de carga con el **engranaje girando**; luego «No network connection» con dibujo, **Close / Retry** de igual anchura |
| 7 | Abrir `001` con red | Barra con «LIGAND 001», leyenda `H C O N…`, tarjeta de ayuda |
| 8 | Tocar un átomo; pulsar «H» | Tarjeta «SELECTED ATOM» con coordenadas en verde; con «H ×»: la leyenda pierde la H y la tarjeta dice «Hydrogens hidden» |
| 9 | Girar el móvil en cada pantalla | Nada se rompe ni se pierde (como antes) |
| 10 | Opciones de desarrollador → escala de animación **0** | El engranaje del diálogo de carga **no gira** |

---

## 12. Qué está verificado y qué no

**Verificado (en el ordenador):**
- Lógica nueva: **19 pruebas**, **12 mutantes muertos**; total **217** pruebas verdes.
- Conversión de los 18 iconos: **0 píxeles distintos** frente al SVG original.
- Icono dentro de la zona segura (30,7 dp de 33).
- Glifos de las seis fuentes (`° · Å … −` + ASCII).
- Todos los XML de recursos bien formados.
- El código de interfaz pasa el **comprobador de tipos** contra declaraciones de la API **escritas a mano** con las firmas que recuerdo.

**NO verificado:**
- **Compilación real** (biblioteca Compose/Material 3 de la versión del proyecto). Riesgo concreto: algún nombre de parámetro o de color de `OutlinedTextFieldDefaults.colors(...)`, `darkColorScheme(...)` o `Surface(onClick = …)`.
- **Cómo se ve**: tamaños, peso de las fuentes reales, legibilidad al sol, la *splash* nueva.
- Que el icono de píxeles de Android 7 se vea bien (no tenemos un Android 7).
- Que `Dialog` con `usePlatformDefaultWidth` deje el ancho que se espera en el A33.
- Que la lectura de la escala de animaciones funcione en One UI (Samsung).

---

## 13. Pruebas y mutación

| Fichero | Pruebas | Qué demuestra |
|---------|--------:|---------------|
| `ListTextsTest.kt` | 6 | Contador con coma de miles **en cualquier idioma**; plural (`1 RESULT` / `2 RESULTS`); espacios = búsqueda vacía; pie según la búsqueda; catálogo ilegible o vacío |
| `AtomTextTest.kt` | 4 | Título del átomo; tres decimales y unidad; **guion ASCII** en negativos; punto decimal con el móvil en francés |
| `ElementLegendTest.kt` | 5 | Orden por frecuencia y alfabético; la H oculta sale de la leyenda; máximo 6; molécula solo de H oculta → leyenda vacía; el `001` real |
| `LoadErrorTextTest.kt` (4 nuevas) | 11 | + título corto de una línea para **cada** error; los cuatro errores del enunciado tienen títulos **y** dibujos distintos; cada dibujo del diseño va con su error; el 404 lo dice |

| # | Avería | Resultado |
|--:|--------|-----------|
| 1 | Plural con `n <= 1` | Muerto |
| 2 | Contador sin `Locale.US` | Muerto (la prueba en alemán) |
| 3 | `isBlank` → `isEmpty` | Muerto |
| 4 | Pie: `shown == 0` → `shown < 0` | Muerto |
| 5 | Coordenada sin `Locale.US` | Muerto (la prueba en francés) |
| 6 | Dos decimales en vez de tres | Muerto |
| 7 | Leyenda sin desempate alfabético | Muerto |
| 8 | Leyenda sin filtrar la H oculta | Muerto |
| 9 | Leyenda sin límite de 6 | Muerto |
| 10 | Leyenda en orden ascendente | Muerto |
| 11 | Timeout con el dibujo de «sin red» | Muerto |
| 12 | Título del 404 sin «404» | Muerto |

---

## 14. Alternativas

| Tema | Lo hecho | Alternativa | Por qué no |
|------|----------|-------------|------------|
| Componentes | Envoltorios propios sobre Material 3 | Usar los `Button`, `OutlinedButton`, `AlertDialog` de Material con colores | Menos control (diálogo con disposición fija); el kit pide formas y bordes concretos |
| Iconos | VectorDrawable de los SVG del kit | Biblioteca `material-icons` de Compose | Otra dependencia, otro estilo de trazo; los del kit son coherentes con el emblema |
| Conversión SVG | Conversor propio verificado píxel a píxel | Android Studio *Vector Asset* | No disponible aquí; el resultado sería equivalente |
| Fuentes | Ficheros en `res/font` | **Fuentes descargables** de Google (`GoogleFont` provider) | Necesita Google Play Services y red en el primer arranque; la app debe verse igual sin red |
| Cinzel | Dos cortes estáticos | Fuente variable con `FontVariation` | Solo API 26+; la app empieza en 24 |
| Fondo de la escena | Negro | Cielo propio de color (sección 10) | Riesgo de cierre / pérdida de la luz; para más adelante si se ve necesario |

---

## 15. Decisiones

| # | Decisión | Estado | Motivo |
|---|----------|--------|--------|
| D27 | El diseño se aplica **como piel**: tema de Material 3 + componentes propios finos; **la lógica no se toca** y lo que decide algo se saca a funciones puras con pruebas | Decidida | Romper lo que funciona es el riesgo real; la piel se juzga mirando |
| D28 | Fuentes **incluidas** en la app (OFL, licencias en `assets/licenses/fonts/`), Cinzel en **dos cortes estáticos**; iconos como **VectorDrawable** generados con un conversor verificado | Decidida | Sin red ni servicios para verse bien; compatible con API 24; conversión comprobada |
| D29 | El fondo de la escena **se queda negro** (el diseño pedía `#100F10`) | Provisional | Cambiarlo sin perder la luz ni arriesgar un cierre requiere más trabajo; la diferencia apenas se ve |

---

## 16. Qué recordar

- **Paso A = piel nueva, mismo motor.** Las firmas de las pantallas y toda la lógica siguen igual; 217 pruebas verdes.
- **Material 3 colorea por roles**: asignar bien `primary`, `surface`, `onSurface`… viste de golpe todo lo que no lleva color explícito.
- **Siempre oscuro de verdad** son tres sitios: el tema de Compose (sin color dinámico), el tema de la **ventana** (sin destello blanco) y las **barras del sistema** (iconos claros).
- **Fuentes OFL:** licencia dentro de la app; **no modificar** una fuente con RFN (Share Tech Mono); las variables necesitan API 26 → cortes estáticos.
- **SVG ≠ Android:** se convierte a VectorDrawable; **el conversor se verificó píxel a píxel** (y así se descubrió que el icono del kit estaba incompleto).
- **Android 7 no tiene iconos adaptables:** necesita imágenes PNG aparte.
- **No compilado con las bibliotecas reales:** si falla, pegar el error.
