# 21 · Interfaz avanzada: tema claro y oscuro, colores de átomos, transiciones, tutorial y celdas ricas

> **Estado:** **escrito**, con **correcciones tras la primera prueba en el A33** (§17). Reglas puras **probadas en la JVM** (582 pruebas en total; 58 nuevas; 32 mutantes: 31 muertos y 1 equivalente, eliminado del código). La interfaz **compila contra *stubs*** y, nuevo en este paso, **pasa las comprobaciones del compilador de Compose** (§9). **Sin probar en el móvil** (§11).

## Índice

1. [Qué pide el enunciado](#1-qué-pide-el-enunciado)
2. [Tema claro y oscuro](#2-tema-claro-y-oscuro)
3. [El 3D en cada tema](#3-el-3d-en-cada-tema)
4. [Esquemas de color de los átomos](#4-esquemas-de-color-de-los-átomos)
5. [Transiciones entre pantallas](#5-transiciones-entre-pantallas)
6. [Tutorial del primer arranque](#6-tutorial-del-primer-arranque)
7. [Celdas ricas en la lista](#7-celdas-ricas-en-la-lista)
8. [Ajustes](#8-ajustes)
9. [Una herramienta nueva: las reglas de Compose sin Android](#9-una-herramienta-nueva-las-reglas-de-compose-sin-android)
10. [Pruebas y mutación](#10-pruebas-y-mutación)
11. [Cómo probarlo en el móvil](#11-cómo-probarlo-en-el-móvil)
12. [Trampas](#12-trampas)
13. [Alternativas](#13-alternativas)
14. [Decisiones](#14-decisiones)
15. [Fuentes](#15-fuentes)
16. [Qué recordar](#16-qué-recordar)
17. [Tras la primera prueba en el móvil](#17-tras-la-primera-prueba-en-el-móvil)
18. [Segunda ronda: la ventana del 3D, la rotación y el FPS que no paraba](#18-segunda-ronda-la-ventana-del-3d-la-rotación-y-el-fps-que-no-paraba)
19. [Tercera ronda: la lista en horizontal y el FPS, otra vez](#19-tercera-ronda-la-lista-en-horizontal-y-el-fps-otra-vez)
20. [Lo que dijo el diagnóstico](#20-lo-que-dijo-el-diagnóstico)

---

## 1. Qué pide el enunciado

VII.2 *Advanced User Interface*: celdas personalizadas, animaciones, modo oscuro (también en la vista 3D), *onboarding* y pantalla de ajustes. Cada punto debe ser «completamente funcional y aportar valor real».

| Punto | Antes | Ahora |
|-------|-------|-------|
| **Celdas** | Línea de consola: «> 001 ······· [INSPECT] ☆» | **Nombre, fórmula, nº de átomos, ARCHIVED, barra de composición**; búsqueda **también por nombre** |
| **Animaciones** | Arranque, cráneo flotante, cursor | Además, **transiciones entre pantallas** y entre páginas del tutorial |
| **Modo oscuro** | Solo oscuro (D26) | **Seguir al móvil / Oscuro / Claro**, también en el **3D** |
| ***Onboarding*** | — | **Cuatro páginas** en el primer arranque, repetibles desde ajustes |
| **Ajustes** | Modelo, hidrógenos, sonido | Además **tema**, **colores de átomos** y **tutorial** |

## 2. Tema claro y oscuro

### 2.1 La idea: los mismos papeles, otra tinta

El tema oscuro es la consola de cogitador (documento 08c). El claro es un **pergamino** al estilo de un manuscrito iluminado: vitela en los paneles, tinta ferrogálica para el texto, **rojo rúbrica** para los títulos y **verde verdete** para los datos. Los marcos de bronce son los mismos.

Los **papeles** (*roles*) no cambian: `Phosphor` sigue siendo «texto de datos» y `Gold` sigue siendo «títulos». Cambia solo el valor de cada papel. Así, ninguna pantalla sabe que hay dos temas: pide `SwiftyColors.Phosphor` y recibe el del tema activo.

### 2.2 `PaletteSpec`: números puros, contraste probado

Las dos paletas están en `PaletteSpec` como enteros `0xRRGGBB`, sin Compose, para que el **contraste** se pruebe en la JVM con la fórmula WCAG 2:

```
luminancia = 0,2126·R + 0,7152·G + 0,0722·B      (canales sRGB pasados a luz lineal)
contraste  = (L_clara + 0,05) / (L_oscura + 0,05)
```

La prueba recorre **cada papel de texto** (brass, gold, bone, phosphor, opticRed, amber, ash) de **ambos** temas sobre el cristal del panel y sobre el fondo, y exige **4,5 : 1** (WCAG AA). También comprueba el texto del botón principal y los títulos sobre sus placas.

| Claro, sobre el panel | Contraste |
|-----------------------|-----------|
| bone (texto) | 13,4 |
| gold (títulos) | 8,7 |
| brass | 6,8 |
| phosphor (datos) | 6,5 |
| ash | 6,2 |
| opticRed, amber | 6,1 |
| texto del botón rojo | 7,6 |

**Un papel nuevo:** `OnMarsRed`, el texto sobre el botón rojo. Antes era `Bone`, pero en el tema claro `Bone` es tinta **oscura**, y tinta oscura sobre rojo oscuro no se lee. El papel decía «texto principal», no «texto sobre rojo».

### 2.3 Lo que no tiene sentido en pergamino

- **Brillo CRT** (`glowing`): una sombra difuminada detrás del texto. Detrás de tinta oscura solo emborrona. En claro, `glow = 0`, y `glowing` devuelve el estilo sin sombra.
- **Texturas CRT y de hierro:** son imágenes **oscuras**. Encima del pergamino solo lo agrisan. En claro, la del CRT tiene opacidad 0 y la del hierro 0,06 (un leve grano).

### 2.4 `CompositionLocal` y no una variable global

La paleta llega a toda la app por `LocalSwiftyPalette` (`staticCompositionLocalOf`). `SwiftyColors.X` son **getters `@Composable @ReadOnlyComposable`** que leen la paleta **del lugar** donde se usan.

¿Por qué no una variable global? Porque una **parte** del árbol puede llevar **otra** paleta: la pantalla de arranque es un CRT que se enciende y es **siempre oscura** (`AlwaysDark { BootScreen(…) }`). Con una global habría que cambiarla al entrar y al salir, y la transición, que dibuja las dos pantallas a la vez, mezclaría los colores.

**Estática** (`static…`): leerla no cuesta nada, y cambiarla recompone todo lo que hay debajo. Es lo correcto para un tema, que cambia rara vez y, cuando cambia, cambia todo.

**Precio:** un getter `@Composable` no se puede leer dentro de `drawBehind { }` ni de `Canvas { }`, porque esas lambdas de dibujo no son *composable*. Hay que leer los colores **antes**, en la parte *composable*, y pasarlos (`CogitatorPanel`, `CogitatorBackground`, `CornerBrackets`). El compilador de Compose lo detecta: el §9 explica cómo lo comprobamos sin Android.

### 2.5 Quién decide el tema

```
Ajustes.theme (SYSTEM | DARK | LIGHT)  +  isSystemInDarkTheme()
        → ThemeMode.isDark(teléfonoOscuro)          (puro, probado)
        → SwiftyProteinsTheme(SwiftyPalette.of(oscuro))
            → LocalSwiftyPalette + el esquema de Material 3 (claro u oscuro, con los MISMOS papeles)
```

**Por defecto: oscuro** (la identidad de la app, D26), decidido tras verla en el móvil (§17). «Follow the phone» sigue disponible: con él, basta con tocar «Tema oscuro» en los ajustes rápidos del móvil. Android **recrea la actividad** al cambiar el modo del sistema, así que todo se redibuja solo.

### 2.6 Las barras del sistema

Con *edge to edge*, la hora y la batería se dibujan **sobre** la app. Antes eran siempre claras (app oscura). Ahora la raíz avisa a la actividad (`onSystemBarsDark`) y ella repinta los iconos: claros sobre oscuro, oscuros sobre pergamino. Durante el arranque son claros, aunque el tema sea claro, porque el arranque es oscuro. `SystemBarStyle.auto` **no** sirve: sigue al **móvil**, no al tema elegido, y un usuario con el móvil en claro y la app en oscuro vería iconos oscuros sobre negro.

## 3. El 3D en cada tema

### 3.1 `SceneLook`

| | Oscuro | Claro |
|-|--------|-------|
| Fondo | El negro de SceneView | Pergamino `#D8CCAE`, algo más oscuro que la interfaz, para enmarcar la molécula |
| Halo de selección | Verde fósforo, 35 % | Verde más profundo, 42 % |
| Halo del elemento | Oro pálido, 28 % | Ámbar oscuro, 38 % (un oro pálido desaparece en pergamino) |
| Luz principal / de relleno | 10 000 / 3 000 lux | 10 000 / 3 500 lux |
| Contraluz | 5 000 lux | 2 000 lux: existe para separar las bolas del **negro** |

### 3.2 Pintar el fondo: el *tone mapper* se mete en medio

El fondo de SceneView es el **skybox** de Filament: por defecto, un color constante negro (`SceneFactories.kt`). Para el pergamino se cambia ese color. Pero todo lo que se dibuja pasa por el ***tone mapper*** que usa SceneView, **PBR Neutral** de Khronos (`ColorGrading … ToneMapper.PBRNeutral()`, leído en su código). Ese *tone mapper*:

1. resta un pequeño **desplazamiento** (0,04 en luz lineal) a todo píxel cuyo canal más oscuro sea al menos 0,08;
2. **comprime** las luces por encima de 0,76.

Un pergamino elegido **por debajo** de ese codo solo necesita que se le devuelva el desplazamiento:

```
skybox = lineal(rgb) + 0,04   →  tone mapper  →  lineal(rgb)  →  pantalla: rgb
```

`SceneLook.skyboxColor` hace esa cuenta, y `SceneLook.pbrNeutral` es el *tone mapper* escrito según su referencia. La prueba encadena los dos y comprueba que sale el color pedido (error < 10⁻⁶) y que el pergamino está por debajo del codo.

> **Sin comprobar en el móvil:** si Filament multiplica el color constante del skybox también por la **exposición** de la cámara, el fondo saldrá casi negro. Si pasa: o se compensa esa exposición, o en el tema claro el visor 3D se deja **oscuro**, como hacen muchos visores profesionales (PyMOL, Blender). Esto último es poner `backdrop = null` en `SceneLook.LIGHT`.

### 3.3 Por qué no hay que reaccionar a un cambio de tema con el 3D abierto

El tema cambia en **Ajustes** (la molécula no está en pantalla) o en el **sistema** (Android recrea la actividad). En ningún caso cambia con el 3D vivo: el fondo, los halos y las luces se fijan al crear la escena. Aun así, el entorno y los materiales de los halos dependen de `look` en sus `remember`: si algún día cambiara en vivo, se crearían de nuevo.

## 4. Esquemas de color de los átomos

### 4.1 Dos convenciones CPK

| Esquema | Origen | Carácter |
|---------|--------|----------|
| **CPK · Jmol** (por defecto) | La tabla de Jmol | Un color propio por elemento hasta el uranio |
| **CPK · RasMol** | La tabla clásica de RasMol | 15 colores con nombre para grupos (Ni, Cu, Zn y Br, todos marrones); lo que no está, **rosa intenso**, como en RasMol |

### 4.2 Verificación (21-11-2026)

La tabla Jmol del código se había **escrito de memoria** (documento 07). Ahora se comparó con la copia de 3Dmol.js (`elementColors.Jmol`): **los 54 valores coinciden**. RasMol se comparó con la página de UMass «RasMol/Chime Color RGB Values». En esa página el morado del yodo aparece como «[160,32.240]»: es un punto por una coma; el valor bueno es `A0 20 F0`, el de su tabla de nombres.

### 4.3 Dónde se aplica

En el 3D (un material por elemento **y por esquema**), en la leyenda (con un aro de bronce: un punto blanco de hidrógeno tiene que verse sobre pergamino), en la barra de composición de la lista y en el **.glb** exportado (se exporta lo que se ve).

## 5. Transiciones entre pantallas

### 5.1 Una decisión pura y una animación

`Navigation.screenOf(...)` decide **qué** pantalla se ve (puro, probado). En este orden:

1. **Arranque**, hasta que termine **y** se hayan leído los ajustes guardados;
2. **tutorial** del primer arranque;
3. **acceso**: siempre antes de cualquier dato (D43);
4. tutorial repetido desde ajustes;
5. **molécula** cargada;
6. **ajustes**;
7. **índice**.

`AnimatedContent` anima el cambio. Hacia una pantalla **más profunda**, la nueva entra por la derecha (¼ del ancho) y se funde; al **volver**, al revés; al mismo nivel, solo fundido. Duran **300 ms**, o **0** si el usuario quitó las animaciones del sistema (`rememberReduceMotion`). El tutorial siempre va «hacia delante»: es un desvío, no un nivel.

### 5.2 La pantalla lleva lo que necesita

`Screen.Molecule` **lleva su molécula**. Durante la transición de vuelta, la pantalla de la molécula **sigue dibujándose** 300 ms mientras sale, y para entonces el estado de carga ya es `Idle`. Si leyera la molécula del estado vivo, no tendría nada que dibujar. Es la regla de oro de `AnimatedContent`: el contenido se dibuja con **su** `targetState`, no con el estado actual.

`contentKey = { it.key }`: misma clave = misma pantalla. Así, un cambio de **datos** (otro origen de la molécula) no se anima como un cambio de pantalla.

### 5.3 ¿Por qué esperar a los ajustes en el arranque?

Antes de leerlos, `tutorialSeen` vale `false`: el valor por defecto. Sin la espera, quien terminó el tutorial hace un mes lo vería **parpadear** al abrir la app. El arranque dura más de un segundo y los ajustes se leen en milisegundos: en la práctica no se espera nunca, pero la regla no depende de la suerte.

## 6. Tutorial del primer arranque

### 6.1 Contenido

| Página | Título | Qué enseña |
|--------|--------|------------|
| RITE I // THE INDEX | The Data-Vault Index | Buscar por id o nombre, cargar, copia para usar sin conexión, favoritos |
| RITE II // THE AUSPEX | Look around the molecule | Girar, zoom, tocar átomo o enlace, doble toque para orbitar |
| RITE III // THE KEYS | The cogitator keys | MODEL…, H, LABELS, RULER |
| RITE IV // THE SHARING | Share the pattern | SHR (imagen, galería, .cif, .mol, .glb), ajustes |

Una prueba comprueba que el tutorial **nombra cada tecla** de la pantalla de la molécula: si mañana se añade una tecla y no se explica, la prueba falla.

### 6.2 Manejo

- **NEXT / BACK / SKIP**; la última página dice **BEGIN**.
- **Deslizar**: a la izquierda, la siguiente página; a la derecha, la anterior. Hace falta recorrer **64 dp**, porque un dedo dudoso no es una decisión. La regla (`Onboarding.afterSwipe`) es pura y probada.
- **Atrás** del sistema: la página anterior; en la primera, salta el tutorial.
- La página cambia con un deslizamiento animado en la dirección del movimiento.
- «2 / 4» en la cabecera; los títulos son *headings* para TalkBack.

### 6.3 Cuándo

Una vez, **antes** del acceso: no enseña nada privado, y quien instala la app ve primero qué hace. SKIP o BEGIN guardan `tutorialSeen = true`. Desde **Ajustes → NOVITIATE → SHOW** se repite (ya con la sesión iniciada).

## 7. Celdas ricas en la lista

### 7.1 Qué muestra una celda

```
>  ATP   ADENOSINE-5'-TRIPHOSPHATE                          ☆
         C₁₀H₁₆N₅O₁₃P₃ · 47 ATOMS                  ARCHIVED
         ▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇
```

- **Nombre** (CCD), cortado con «…» si no cabe.
- **Fórmula** con subíndices tipográficos (los mismos que en la molécula, ahora compartidos en `FormulaText.kt`) y **número de átomos**.
- **ARCHIVED**: hay copia en el móvil (documento 13), así que se abre sin conexión. Se actualiza al cargar un ligando.
- **Barra de composición**: la proporción de cada elemento, en su color. Antes de descargar nada ya se ve si es casi todo carbono e hidrógeno o un ion metálico. Es decoración: TalkBack recibe lo mismo en palabras.
- TalkBack lee la celda entera: «Ligand ATP, ADENOSINE-5'-TRIPHOSPHATE, formula C 10 H 16 N 5 O 13 P 3, 47 atoms, saved on the phone. Inspect». La fórmula va **separada**: dicha de corrido sería una palabra rara.

### 7.2 De dónde salen: un índice incluido en la app

`assets/ligand_index.tsv` (86 KB; `ID ⇥ nombre ⇥ fórmula ⇥ átomos`). Lo genera `tools/index/BuildLigandIndex.kt` con el **parser y la fórmula de la propia app**: una celda nunca promete otra fórmula que la que mostrará la pantalla tras cargar. Una prueba lo comprueba con el `001.cif` real.

- **1242 de 1243**: falta **UNL** («ligando desconocido»), que no tiene átomos en el diccionario. Su celda es la de antes, solo el id. Una prueba fija exactamente esa ausencia.
- Los **nombres** vienen del `chem_comp.name` del CCD: el corpus con el que se construyó tenía átomos y enlaces del CCD pero «?» como nombre. La herramienta acepta un fichero de nombres para ese caso.
- La lectura es **permisiva línea a línea**: una línea rota se salta. Sin fichero, las celdas vuelven a ser las de antes.

### 7.3 Búsqueda por nombre

`LigandCatalog.search(texto, nombreDe)`: primero los ids que **empiezan** por el texto, luego los que lo **contienen**, y después los ligandos cuyo **nombre** lo contiene. Ninguno aparece **dos veces**. «caffeine» encuentra CFF; «heme» encuentra HEM y sus parientes. El cuadro dice «Search by ID or name».

## 8. Ajustes

| Panel | Ajuste | Valores |
|-------|--------|---------|
| AUSPEX DEFAULTS | Modelo al abrir, hidrógenos al abrir | (documento 16) |
| **PICT-DISPLAY** | **Tema** | Dark (cogitator, por defecto) · Light (parchment) · Follow the phone |
| | **Colores de átomos** | CPK · Jmol · CPK · RasMol |
| VOX-CASTER | Sonidos | (documento 14) |
| **NOVITIATE** | **Tutorial** | SHOW |

- La pantalla ahora **se desplaza**: con más paneles, un móvil pequeño o el texto grande cortarían los últimos.
- **Compatibilidad:** un fichero de ajustes **anterior** (solo tres claves) da los valores por defecto de las nuevas; un valor raro («SEPIA») solo reinicia **su** ajuste (probado).
- **La lista de claves** que lee el almacén (`SettingsCodec.KEYS`) vive ahora junto a `encode`, y una prueba exige que coincidan. Antes estaba en el almacén Android: un ajuste nuevo se habría **guardado y no leído nunca**, sin que fallara nada.

## 9. Una herramienta nueva: las reglas de Compose sin Android

Hasta ahora, el código de interfaz se compilaba contra *stubs*. Eso comprueba tipos, pero **no** las reglas propias de Compose, porque `@Composable` es para `kotlinc` una anotación sin significado. Este paso las rompía en varios sitios (leer `SwiftyColors.X` dentro de `drawBehind`), y Gradle lo habría dicho en tu máquina, no aquí.

**Descubrimiento:** la distribución de `kotlinc` 2.4.21 trae `compose-compiler-plugin.jar`. Con `-Xplugin=…`, sus **comprobaciones de fase de análisis** se ejecutan sobre nuestros *stubs*. Después falla al generar código, porque busca el *runtime* real, pero los **errores de reglas** ya se han emitido. Se verificó con un fichero de prueba con tres violaciones: las tres aparecen. El código de antes de este paso: **cero errores**.

Con ella se encontraron y corrigieron **19 errores en cuatro sitios**: 12 lecturas de color en el dibujo de los marcos (`cogitatorFrame`, `CogitatorBackground`, `CornerBrackets`), 5 en una función de color que no era *composable* (`fpsColor`) y 2 `@ReadOnlyComposable` que llamaban a algo que no lo era (uno de ellos, un *stub* mal anotado: en Compose real, `CompositionLocal.current` es `@ReadOnlyComposable`).

**Límite:** solo comprueba lo que los *stubs* declaran bien. No sustituye a la compilación real.

## 10. Pruebas y mutación

| Fichero | Nuevas | Qué fija |
|---------|--------|----------|
| `PaletteSpecTest` | 7 | Fórmula WCAG con valores conocidos; **todos** los textos ≥ 4,5 en ambos temas; botón y placas; el claro es claro; pergamino sin brillo ni textura CRT; la paleta oscura no se movió |
| `SceneLookTest` | 5 | El oscuro conserva el negro; skybox → *tone mapper* → color pedido; por debajo del codo; *tone mapper* contra su referencia; halos y contraluz |
| `AtomColorsTest` | 4 | Jmol por defecto; valores RasMol; grupos con un color; lo no listado, rosa |
| `NavigationTest` | 9 | Prioridades de pantalla; la molécula viaja con su pantalla; direcciones; claves únicas; arranque oscuro; sin animación si se pidió |
| `OnboardingTest` | 5 | Cuatro páginas; límites; deslizamiento con umbral; contador; **cada tecla explicada** |
| `LigandIndexTest` | 8 | Líneas rotas; nombre vacío; búsqueda por nombre, orden y sin duplicados; el índice real cubre el catálogo (menos UNL); cada fórmula se relee y suma sus átomos; **coincide con el parser** |
| `SettingsAndFavoritesTest` | 7 | Ida y vuelta; ficheros antiguos; valores raros; **claves leídas = escritas**; tema según el móvil; ciclos; `loaded` |
| `MolecularFormulaTest` | 2 | `parse` lee lo que escribe `plain` y rechaza todo lo demás |
| `ListTextsTest` | 3 | Átomos en singular y plural; lo que oye TalkBack; barra de composición |
| `FileLigandCacheTest` | 2 | `ids` solo de copias (no `.tmp`); carpeta inexistente |
| `MeshAndGlbTest` | 1 | El .glb lleva el esquema elegido |

**Mutantes: 28.** 27 muertos. El **equivalente**: «el tutorial va hacia delante también al **salir**». Como solo el arranque está por debajo del tutorial, salir de él ya es «hacia delante» por profundidad, y esa mitad de la condición sobraba. Se quitó; el comentario explica por qué basta con la otra.

**Sin prueba automática:** el dibujo de las pantallas, `AnimatedContent`, el deslizamiento real, el skybox en Filament y las barras del sistema. Ver §11.

## 11. Cómo probarlo en el móvil

1. **Instalación limpia** (o borrar datos): arranque → **tutorial** (4 páginas, deslizar y botones, Atrás) → acceso.
2. **Volver a abrir:** **no** aparece el tutorial, ni un instante.
3. **Ajustes → Theme** hasta *Light*: toda la app en pergamino; textos legibles; **iconos del sistema oscuros**.
4. Abrir **ATP** en claro: fondo pergamino en el 3D (**§3.2**: si sale negro o muy oscuro, decírmelo); halos visibles; leyenda con puntos rodeados de bronce.
5. **Theme → Follow the phone** y alternar el modo oscuro en los ajustes rápidos: la app sigue al móvil.
6. **Atom colours → RasMol:** carbono más claro, nitrógeno azul lavanda; la leyenda dice «CPK · RasMol»; un ligando con cobalto o platino lo muestra rosa (como RasMol). Exportar .glb y verlo: con esos colores.
7. **Transiciones:** índice → molécula (entra por la derecha) → atrás (sale por la izquierda); ajustes igual. Con «Quitar animaciones» en accesibilidad: cambio instantáneo.
8. **Durante la transición de la molécula**: el 3D se mueve con la pantalla (es un `SurfaceView`: **mirar si se ve bien**).
9. **Celdas:** nombres, fórmulas, ATOMS, ARCHIVED en los ya abiertos, barra de colores. Buscar «caffeine» → CFF.
10. **TalkBack** sobre una celda: la frase completa.
11. **Ajustes → NOVITIATE → SHOW:** el tutorial; al terminar, vuelve a ajustes.

## 12. Trampas

1. **Un getter `@Composable` dentro de `drawBehind`:** no compila con Compose (y con *stubs* sin el plugin, sí). §9.
2. **«Texto principal» sobre un botón de color:** en el otro tema deja de leerse. Hace falta un papel propio (`OnMarsRed`).
3. **`SystemBarStyle.auto`:** sigue al móvil, no a la app.
4. **`AnimatedContent` leyendo el estado vivo:** la pantalla que sale se queda sin datos.
5. **Mostrar el tutorial con los ajustes por defecto:** parpadeo para quien ya lo vio.
6. **Un color de fondo 3D ignorando el *tone mapper*:** sale otro color.
7. **Argumentos posicionales en `darkColorScheme(...)`:** treinta y tantos parámetros del mismo tipo; un orden distinto entre versiones confunde papeles **sin error de compilación**. Se usan por **nombre**.
8. **La lista de claves lejos del códec:** un ajuste guardado que nunca se lee.
9. **Una tabla de colores de memoria:** se verificó por fin; en RasMol, una errata de la fuente (punto por coma).
10. **`BackHandler` de la pantalla que sale:** sigue registrado los 300 ms de la animación. Un Atrás justo en ese instante lo atiende la pantalla que se va. Es inofensivo aquí (las dos acciones llevan al mismo sitio), pero conviene saberlo.

## 13. Alternativas

| Alternativa | Por qué no |
|-------------|-----------|
| **Navigation Compose** (`NavHost`) con sus transiciones | Una dependencia y una pila de rutas para seis pantallas cuya elección ya es una función pura; D19 sigue en pie, ahora animada |
| **Paleta global** (`mutableStateOf`) leída sin `@Composable` | Funciona en `drawBehind` sin cambios, pero no permite una parte del árbol con otra paleta (el arranque oscuro), y mezcla colores en las transiciones |
| **Color dinámico** de Android 12+ | La app tiene una identidad visual; los colores del fondo de pantalla del usuario la borrarían |
| Visor 3D **siempre oscuro** en el tema claro | Es lo que hacen PyMOL y Blender, y es el plan B del §3.2; pero el enunciado pide el modo oscuro «también en el 3D», y un 3D que sigue al tema lo demuestra mejor |
| `HorizontalPager` para el tutorial | Una dependencia más (`foundation.pager`) para cuatro páginas; `AnimatedContent` y un gesto de 15 líneas bastan |
| Nombres y fórmulas **descargados** de RCSB para cada celda | 1243 peticiones o una API de búsqueda; sin conexión, nada. El índice incluido pesa 86 KB y funciona siempre |
| **Miniatura 2D** de la molécula en cada celda | Haría falta un dibujo 2D (RDKit, CDK) o guardar coordenadas en el índice; la barra de composición da una idea a coste cero |
| Un tercer esquema **apto para daltónicos** | Buena idea pendiente; los dos CPK son lo que un químico espera ver |

## 14. Decisiones

| # | Decisión | Estado | Motivo |
|---|----------|--------|--------|
| D68 | **Tema claro «pergamino» y oscuro «cogitador»**, por defecto **oscuro** (§17); mismos papeles, otros valores; contraste AA **probado** para ambos. **Sustituye** a «solo tema oscuro» de D26 | Decidida | VII.2 «modo oscuro» de verdad (con algo que oscurecer) |
| D69 | La paleta por **`CompositionLocal` estático**, con getters `@Composable`; el arranque, siempre oscuro | Decidida | Una parte del árbol puede tener otra paleta |
| D70 | El **3D sigue al tema**: skybox compensado para el *tone mapper* PBR Neutral, halos y contraluz por tema | Decidida (sin probar en el móvil) | VII.2 «también en el 3D» |
| D71 | **Dos esquemas CPK** (Jmol, RasMol), verificados contra fuentes; en 3D, leyenda, lista y .glb | Decidida | VII.2 «ajustes: colores» |
| D72 | **Pantalla elegida por función pura** y animada con `AnimatedContent`; la pantalla lleva sus datos | Decidida | VII.2 «transiciones»; amplía D19 |
| D73 | **Tutorial** de 4 páginas antes del acceso, una vez, repetible | Decidida | VII.2 «onboarding» |
| D74 | **Celdas ricas** desde un **índice incluido** generado con el parser de la app; búsqueda por nombre; marca ARCHIVED | Decidida | VII.2 «celdas personalizadas» |
| D75 | **Comprobación de reglas de Compose** con el plugin de `kotlinc` sobre los *stubs* | Decidida | Encontró 19 errores que solo habría visto Gradle |

## 15. Fuentes

- Jmol: tabla `elementColors.Jmol` de 3Dmol.js — <https://3dmol.csb.pitt.edu/doc/colors.ts.html>
- RasMol: «RasMol/Chime Color RGB Values» (UMass) — <https://www.umass.edu/microbio/rasmol/rascolor.htm>
- WCAG 2: definición de contraste y de luminancia relativa.
- Khronos PBR Neutral: el *tone mapper* que usa SceneView 4.69.4 (`SceneFactories.kt`).
- Código fuente de SceneView 4.69.4: `createEnvironment`, `rememberEnvironment`, `Sphere.kt`.

## 16. Qué recordar

- **Papeles, no colores:** una pantalla pide «texto de datos» y el tema decide qué tinta es.
- **El contraste se prueba**, no se mira a ojo: una prueba que recorre todos los textos de los dos temas.
- Un `CompositionLocal` permite que **una parte** del árbol sea distinta; una global, no.
- En `AnimatedContent`, el contenido se dibuja con **su** estado, no con el actual: la pantalla lleva sus datos.
- Los valores por defecto **antes de cargar** no son la verdad: hay que esperar a lo guardado.
- Entre el color que pides y el que se ve hay un ***tone mapper***.
- Si una herramienta del entorno puede comprobar algo que solo vería Gradle, **úsala**: aquí encontró 19 errores.

## 17. Tras la primera prueba en el móvil

Lo que se vio en el A33 el 21-11-2026 y lo que se cambió.

### 17.1 ¿Pide el enunciado dos temas?

**No con esas palabras.** VII.2 pide «modo oscuro, también en la vista 3D» (documento 01). Una app que **siempre** es oscura ya lo cumple en sentido literal, y hasta este paso esa era nuestra respuesta (documento 15). El tema claro es una **interpretación**: un «modo» implica algo que activar y desactivar, y la plantilla Android de la que partimos ya tenía tema claro y oscuro. Se mantiene porque no cuesta nada al evaluador (la app abre oscura) y, si pregunta, se le enseña el cambio en vivo, también en el 3D. En la defensa hay que decirlo así: «el enunciado pide modo oscuro; la app es oscura, y además se puede cambiar».

### 17.2 La molécula no salía centrada

**Causa:** SceneView 4.69.4 tiene `autoCenterContent = true` **por defecto** (`SceneView.kt`, `SceneAutoCenter.kt`). En el primer fotograma en que hay contenido, **traslada todo el contenido una vez** para llevar el centro de su caja al origen. Con el **montaje por lotes** del documento 20, ese primer fotograma solo tenía los primeros 16 átomos: SceneView centró **esos**, y el resto de la molécula quedó desplazado para siempre.

**Arreglo:** `autoCenterContent = false`. Nuestro parser **ya** centra cada molécula en su centroide (`CifExtractor`), y la cámara, las etiquetas, la regla y el doble toque trabajan con esas coordenadas. Antes del montaje por lotes el desplazamiento también existía, pero era pequeño (la diferencia entre el centro de la caja y el centroide), y el doble toque orbitaba **un poco fuera** del átomo.

**Lección:** dos piezas correctas por separado (el montaje por lotes y un valor por defecto de la biblioteca) pueden dar un fallo juntas. Al leer el código de SceneView para el documento 20 se vio el parámetro, pero no se leyó qué hacía.

### 17.3 El sirviente se veía alrededor del primer campo

**Causa:** el dibujo va **detrás** del formulario, y un degradado lo funde con el panel. Ese degradado solo llegaba al 92 % y al final del dibujo. Sobre el cristal oscuro no se notaba. Sobre el pergamino, la parte baja del dibujo (oscura) se veía **entre los campos** y en el hueco del contorno donde flota la etiqueta «User name».

**Arreglo:** el degradado es **opaco desde 10 dp por encima del formulario** (`SOLID_FROM`). La cabeza y la consola se siguen viendo enteras.

### 17.4 El cuadro de búsqueda (y el panel VOX-CASTER) no estaban centrados

**Causa:** el relleno era `contentPadding` por todos los lados **más 6 dp arriba** si el panel tenía etiqueta: el contenido quedaba **más abajo** que el centro. Además, con un relleno pequeño (6 dp en el cuadro de búsqueda) el campo **tapaba el remache** de abajo a la izquierda.

**Arreglo:** `PanelGeometry` (puro, probado), con una sola regla para todos los paneles. Arriba y abajo, **el mismo** relleno, nunca menor que lo que despeja los remaches y la etiqueta. Los lados conservan el relleno pedido, porque los remaches viven en las franjas de arriba y de abajo. El dibujo de los remaches usa **los mismos** números que la regla.

| Panel | Antes (arriba / abajo) | Ahora |
|-------|------------------------|-------|
| Cuadro de búsqueda (6 dp, con etiqueta) | 12 / 6, remache tapado | 14,7 / 14,7 |
| Ajustes y diálogos (16 dp, con etiqueta) | 22 / 16 | 16 / 16 |
| Leyenda de la molécula (10 dp, sin etiqueta) | 10 / 10, remaches justos | 14,7 / 14,7 |

### 17.5 Tema oscuro por defecto

Pedido tras verlo: la app abre como siempre se diseñó. Los ajustes guardados antes de este cambio con «Follow the phone» **no** se tocan, y los que no tengan valor de tema reciben «Dark».

### 17.6 Pruebas

`PanelGeometryTest` (5): el contenido nunca tapa un remache ni pasa por debajo de la etiqueta (para todos los cortes y rellenos usados), un relleno generoso se respeta, el cuadro de búsqueda recibe 14,7, y la regla de la etiqueta vale **por sí sola**. Este último caso nació de un mutante superviviente: con los cortes de hoy, los remaches ya exigen más que la etiqueta. Mutantes: sin remaches, sin etiqueta, sin holgura y tema por defecto: los cuatro muertos.

## 18. Segunda ronda: la ventana del 3D, la rotación y el FPS que no paraba

Tres cosas vistas en el A33 tras §17, con capturas.

### 18.1 La molécula, centrada en la pantalla y no en la ventana

**Síntoma:** recién cargada, 001 aparecía **abajo**, medio tapada por el panel de lectura. §17.2 la había centrado... **en la pantalla entera**. La escena 3D ocupa toda la pantalla, por debajo de los paneles, pero lo que se ve es la **ventana** entre el panel de arriba y los de abajo, y su centro queda **más arriba** que el de la pantalla (los paneles de abajo son más altos).

**Arreglo, sin mover la cámara ni redimensionar nada:** SceneView 4.69.4 tiene `contentPadding` («la parte de la vista que tapan un panel o una barra», leído en `SceneView.kt` y `CameraNode.kt`). La escena **sigue dibujándose debajo de los paneles**, pero la **proyección** se desplaza para que el centro óptico y el campo de visión sean los de la ventana. Lo hace con `Camera.setScaling` / `setShift` de Filament, y los toques siguen cayendo donde se ve (`viewToRay` aplica lo mismo).

- La ventana se **mide** (`onGloballyPositioned`, en coordenadas de la ventana de la app) y `Viewport.cover` (puro, probado) calcula la banda tapada de cada lado.
- Hasta conocer la ventana, la escena espera **un fotograma**: si no, nacería con la forma equivocada.
- Si un panel cambia de alto, la banda se **anima** (`animateDpAsState`) y la molécula **se desliza** al nuevo centro. Para que no ocurra a cada toque, el panel de lectura tiene la **misma altura mínima** (150 dp) para la molécula, un átomo, un enlace y la regla. Con letra grande crece, y entonces la molécula se desliza.
- Las líneas efímeras (ASSEMBLY, captura) ya no **empujan** la ventana: van **encima** de su borde inferior.

### 18.2 La distancia de la cámara, según la forma de la ventana

La regla «5,5 radios» estaba pensada para la pantalla entera de un móvil alto (aspecto 0,45, limitado por el ancho). Con la ventana, el objetivo cubre **su** altura, y la forma cambia con el móvil y la orientación. Ahora:

```
distancia = 1,06 · radio / (tan(fov/2) · min(aspecto, 1))      tan(fov/2) = 12/28  (lente de 28 mm, sensor de 24 mm)
```

Con aspecto 0,45 da **5,50**: la regla de antes es un caso de esta. Una ventana estrecha la limita el ancho, y una ancha (horizontal), la altura. La prueba comprueba, para cinco formas, que la molécula **cabe** y que **llena** el lado estrecho dejando el 6 % de margen.

El aspecto entra **redondeado a 0,05** (`aspectKey`): la cámara se reconstruye (y vuelve a su posición inicial) solo si la ventana cambia **de verdad** de forma, nunca por un píxel de maquetación.

### 18.3 En horizontal no se veía la molécula

**Síntoma:** al girar el móvil, los tres paneles ocupaban **toda** la altura: ni un píxel de 3D, y sin desplazamiento. La columna vertical no cabe en 1080 px de alto.

**Arreglo:** en horizontal, la **ventana a la izquierda** y los paneles en una **columna a la derecha** (hasta el 46 % del ancho, 440 dp como máximo) **con desplazamiento**. `contentPadding` tapa el lado derecho, y la molécula se centra en la izquierda.

### 18.4 El FPS no bajaba nunca a IDLE

**Síntoma:** con la imagen quieta, el contador saltaba entre 70 y 90 FPS sin llegar a IDLE (el A33 tiene pantalla de **90 Hz**: por eso pasa de 60).

**Causa: un bucle de tres eslabones.**

1. El contador cambia su valor **4 veces por segundo**. Ese valor se leía en el cuerpo de `MoleculeScreen`, así que cada cambio **recomponía toda la pantalla**, y con ella la escena 3D.
2. Al recomponerse, las luces de SceneView vuelven a escribir su intensidad y su dirección (`rememberMainLightNode`, `LightNode`: lo hacen «en cada recomposición», por diseño). Cada uno de esos *setters* llama a `onComponentChanged()`, que en una luz es `requestRender()`: **piden un fotograma aunque el valor no haya cambiado** (`LightComponent.kt`, `LightNode.kt`).
3. Esos fotogramas los cuenta el contador, que cambia su valor... y vuelta al paso 1.

**Arreglo:** el contador se lee **a través de una lambda**, dentro de su propio texto (`FpsLabel`). Cuando cambia el número, **solo ese texto** se redibuja. La pantalla no se recompone, la escena no pide nada y, con la cámara quieta, el bucle se para: **IDLE**.

**Honestidad:** este bucle existía desde el documento 12, y el «IDLE» que allí se describe **no se había visto nunca en el móvil**: el documento lo daba por hecho. Las luces del tema (§3) no lo crearon, pero sí son uno de sus eslabones.

**Lección:** en una escena «a demanda», cualquier estado que cambie **periódicamente** y se lea **por encima** de la escena es sospechoso. Hay que leerlo lo más abajo posible en el árbol.

### 18.5 Pruebas

| Fichero | Nuevas | Qué fija |
|---------|--------|----------|
| `SceneFramingTest` | 4 (+1 cambiada) | La molécula cabe y llena la ventana para cinco formas; más ancha, más cerca hasta el cuadrado; `aspectKey` ignora píxeles y no giros; sin división por cero; 0,45 da 5,5 |
| `ViewportTest` | 5 | Bandas en vertical y en horizontal; pantalla que empieza bajo la barra; nada medido = esperar; nunca bandas negativas |

**Mutantes: 7, muertos:** sin `min(aspecto, 1)`, sin aspecto, sin suelo de 0,1, clave sin redondear, banda negativa, banda absoluta y ventana vacía aceptada.

**Sin prueba automática:** la medida real, la animación de la banda, la composición horizontal y el IDLE. Para el móvil:

1. 001 y 15P recién cargados: **centrados en la ventana** entre paneles.
2. Tocar un átomo y luego el vacío: la molécula **no se mueve**.
3. Girar el móvil: 3D a la izquierda, paneles a la derecha, que se desplazan si no caben.
4. Soltar la molécula: en uno o dos segundos el contador dice **IDLE**.
5. Tocar un átomo con la imagen quieta: sigue la selección y vuelve a IDLE.

### 18.6 Un error que solo vio Gradle: `maxWidth` desde un ámbito exterior

`./gradlew installDebug` falló: *DSL_SCOPE_VIOLATION*, «`val maxWidth: Dp` cannot be called in this context with an implicit receiver».

**Causa:** `maxWidth` pertenece a `BoxWithConstraintsScope`, y se leía **dentro** de un `Row` y un `Column`. Los ámbitos de maquetación de Compose llevan `@LayoutScopeMarker`, un `@DslMarker` de Kotlin: dentro de un ámbito marcado, el compilador **prohíbe** usar sin nombrarlo el receptor de **otro** ámbito marcado de fuera. Es lo que impide escribir por error, dentro de una `Row`, un `Modifier.weight` que pertenece a la `Column` de fuera.

**Arreglo:** leer el valor en el propio ámbito de `BoxWithConstraints` (`val sideWidth = …`) y usar la variable dentro.

**Por qué no lo vimos aquí:** los *stubs* declaraban `RowScope`, `ColumnScope`, `BoxScope` y `BoxWithConstraintsScope` **sin** el marcador. Ahora lo llevan, igual que en Compose, y también `SceneScope` y `NodeScope` llevan `@SceneDsl`, como en SceneView. Se comprobó que con los *stubs* corregidos el código anterior da **el mismo error en la misma línea y columna** (353:53) que Gradle, y que el corregido compila.

**Lección:** un *stub* tiene que copiar también las **anotaciones** que cambian las reglas del compilador, no solo las firmas.

## 19. Tercera ronda: la lista en horizontal y el FPS, otra vez

### 19.1 En horizontal, la lista no se veía

**Síntoma:** al girar el móvil en el índice, solo quedaba el cuadro de búsqueda: ni una fila y sin desplazamiento. En horizontal el móvil mide unos 400 dp de alto, y la cabecera, el título, la búsqueda y el pie, apilados, no dejaban **nada** a la lista. Es el mismo fallo que §18.3 en la pantalla de la molécula, y debió revisarse entonces en **todas** las pantallas.

**Arreglo:** dos columnas. A la izquierda (40 % del ancho, 400 dp como máximo) van la cabecera, el título, la búsqueda y el pie, con desplazamiento. A la derecha va la lista, en un panel PATTERNS de toda la altura. En vertical, todo sigue igual.

**Revisión del resto:** el acceso y los ajustes ya se desplazaban. El **tutorial** apretaba la página igual: en horizontal ahora va dibujo a la izquierda y texto a la derecha, con desplazamiento. En vertical sigue centrado: una columna con desplazamiento da a su contenido una altura **ilimitada**, y `Arrangement.Center` no centraría nada, así que la columna interior mide **al menos** lo que la página. El arranque es una animación de un segundo: queda igual.

### 19.2 El FPS seguía sin llegar a IDLE

El arreglo de §18.4 era **necesario**, pero no bastó. Releer SceneView no encontró otro culpable seguro: todas las fuentes de fotogramas que se ven en su código están apagadas en nuestra escena o tienen motivo. Así que no se adivina más:

**1. Diagnóstico en el móvil** (`FrameDiagnosis`, puro, probado). Mientras la escena dibuja, una línea por segundo en Logcat, con la etiqueta `SwiftyFrames`:

```
frames=88 sceneRecompositions=0 contentRecompositions=0 cameraMoves=0
```

| Lo que dice | Lo que significa |
|-------------|------------------|
| fotogramas **con** recomposiciones | algo por encima de la escena cambia sin parar (§18.4 era de este tipo) |
| fotogramas **con** movimientos de cámara | la cámara deriva (una inercia que no acaba, una pose que nunca es igual) |
| fotogramas **sin** nada de eso | la petición viene de fuera de nuestro código (SceneView, la superficie) |

Para leerlo: `adb logcat -s SwiftyFrames`, con la molécula **quieta** unos diez segundos.

**2. Las luces ya no amplifican.** Aunque algo recomponga la escena, ya no se piden fotogramas por ello:

- **luz principal y de relleno:** se crean **vacías** y se ajustan **una vez** por tema en un `LaunchedEffect`. Antes, su bloque `apply` (que SceneView vuelve a ejecutar en **cada** recomposición) llamaba a setters que piden fotograma siempre;
- **contraluz:** va en su propia función `RimLight(lux)`. Con el mismo valor, Compose **se la salta** al recomponer, y sus setters no se vuelven a ejecutar.

**Pruebas:** `FrameDiagnosisTest` (3): una línea por segundo y nada en medio; recomposiciones y movimientos contados aparte, con una migaja numérica que no cuenta como movimiento; cada ventana empieza de cero. Mutantes: sin umbral, sin reinicio y ventana corta: los tres muertos.

**Lección:** cuando la lectura del código ya no da más, **se mide en el sitio**. Y se miden **varias** hipótesis a la vez, para que una sola prueba en el móvil las separe.

## 20. Lo que dijo el diagnóstico

### 20.1 Los datos (A33, 21-11-2026)

```
001, quieta:  frames=65 sceneRecompositions=10 contentRecompositions=10 cameraMoves=0   (cada ~1,5 s)
15P:          frames≈recompositions mientras se monta (§5); después, nada: IDLE
```

- **La cámara no se mueve** (`cameraMoves=0`): no es una inercia.
- **Cada fotograma va con recomposiciones**: unas 6-7 por segundo, y tras cada una SceneView dibuja unos 6 fotogramas de «cola».
- **Lo que se vio a simple vista:** el marco **da un pequeño tirón** con una cadencia fija, y el contador baja al rojo y sube al verde.

### 20.2 La causa: el texto del contador movía la maquetación, y la escena estaba dentro de un `BoxWithConstraints`

1. **El número del contador cambiaba de ancho.** «9 FPS», «75 FPS» e «IDLE» no miden lo mismo. Cada cambio de ancho volvía a **maquetar** la fila del panel superior: ese es el tirón del marco.
2. **`BoxWithConstraints` compone su contenido durante la maquetación**, dentro de un `SubcomposeLayout`. En cada medida crea un ámbito nuevo y una lambda nueva (`subcompose(Unit) { scope.content() }`), y su contenido **vuelve a ejecutarse**. La escena 3D estaba **dentro**.
3. Cada ejecución de la escena pedía fotogramas, los fotogramas cambiaban el contador, y el contador cambiaba de ancho otra vez: el bucle.

15P se libraba por los números: sus ráfagas dejaban de cambiar el texto del contador y el bucle se apagaba en IDLE. Con 001, no.

### 20.3 El arreglo, en tres capas

1. **El contador tiene un ancho fijo** (64 dp, alineado a la derecha): un número nuevo ya no mueve nada.
2. **La escena está fuera del `BoxWithConstraints`**: en un `Box` normal, al lado. Los paneles siguen dentro, que es lo que necesita saber la orientación. Una maquetación de los paneles ya no ejecuta la escena.
3. **El diagnóstico nombra al culpable** si queda alguno: cada línea dice qué **parámetros** de la escena eran distintos cuando se volvió a ejecutar, `contentPadding:9` (otro valor) u `onTap~:3` (el mismo valor en un objeto **nuevo**, lo que hace una lambda descuidada). Si el bucle siguiera, la próxima línea de Logcat diría su nombre.

**Pruebas:** `FrameDiagnosisTest` +2: los parámetros cambiados se nombran y se cuentan; `changes` distingue un valor nuevo de un objeto nuevo con el mismo valor.

**Lección:** un contenedor que compone **durante la maquetación** (`BoxWithConstraints`, `SubcomposeLayout`) vuelve a ejecutar su contenido con cada cambio de medida. Lo caro, y sobre todo lo que tiene efectos (una escena 3D que pide fotogramas), va **fuera**, y dentro solo lo que de verdad depende de las medidas.

