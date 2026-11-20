# 06c · Pegamento con Android: lista, búsqueda, carga y errores en pantalla (código línea a línea)

> **Nota posterior (documento [`08b-diseno-aplicado.md`](08b-diseno-aplicado.md)):** el **aspecto** de la lista y de los diálogos se rehízo con el diseño «Culto de la Máquina» (los diálogos pasan de `AlertDialog` a `Dialog` con panel propio). **La lógica explicada aquí no cambió** (búsqueda, estado elevado, diálogo modal, qué errores ofrecen «Retry»); el código citado abajo es el de **esta etapa**: para el actual, ver el 08b.

> Estado: **ESCRITO Y COMPROBADO EN EL MÓVIL** (20/11/2026: en el dispositivo funcionaron la lista, la selección, la descarga y la pantalla con el fichero; compiló sin cambios en el código). **Los diálogos de error no se han probado todavía en el móvil** (sección 13). Se escribió sin SDK de Android a mano. La parte que no toca Android (`LoadErrorText.kt`) tiene **7 pruebas** y pasan en la JVM (junto con las 155 anteriores: **162**).
> El resto (el `ViewModel`, las pantallas, la actividad) se comprobó de tipos contra unas piezas de relleno (*stubs*) escritas de memoria y **después** se compiló y ejecutó en el móvil del equipo (sección 15 tiene el detalle de lo que sigue sin probar).
> Redactado el 19/11/2026. Viene después de [`06b`](06b-lista-busqueda-y-estado-de-carga.md): usa `LigandCatalog`, `LoadState` y `LigandLoader`.
> Las citas de código se generan **desde los ficheros reales**.
> **Actualización (20/11/2026):** la pantalla provisional de la sección 10 (`MoleculeSummaryScreen`, la tabla de átomos) **ya fue sustituida** por la escena 3D del [documento 07](07-escena-3d-de-la-molecula.md); el fichero se ha borrado del repositorio. El resto de este documento sigue vigente.

## Índice

1. [Qué pide el enunciado y qué cubre esta etapa](#1-qué-pide-el-enunciado-y-qué-cubre-esta-etapa)
2. [Mapa: qué fichero hace qué](#2-mapa-qué-fichero-hace-qué)
3. [Conceptos de Android y Compose que hacen falta](#3-conceptos-de-android-y-compose-que-hacen-falta)
4. [Dependencias nuevas (Gradle)](#4-dependencias-nuevas-gradle)
5. [`LoadErrorText.kt`: qué lee el usuario cuando falla](#5-loaderrortextkt-qué-lee-el-usuario-cuando-falla)
6. [`LigandsViewModel.kt`: la envoltura que sobrevive al giro](#6-ligandsviewmodelkt-la-envoltura-que-sobrevive-al-giro)
7. [`SwiftyApp.kt`: la raíz, o «navegar» sin librería](#7-swiftyappkt-la-raíz-o-navegar-sin-librería)
8. [`LigandListScreen.kt`: buscador y lista](#8-ligandlistscreenkt-buscador-y-lista)
9. [`LoadDialogs.kt`: el indicador de carga y los errores](#9-loaddialogskt-el-indicador-de-carga-y-los-errores)
10. [`MoleculeSummaryScreen.kt`: la pantalla provisional](#10-moleculesummaryscreenkt-la-pantalla-provisional)
11. [`MainActivity.kt`: el punto de entrada](#11-mainactivitykt-el-punto-de-entrada)
12. [Simulaciones paso a paso](#12-simulaciones-paso-a-paso)
13. [Cómo probarlo en el móvil (incluidos los cuatro errores)](#13-cómo-probarlo-en-el-móvil-incluidos-los-cuatro-errores)
14. [Trampas y límites conocidos](#14-trampas-y-límites-conocidos)
15. [Qué está verificado y qué no](#15-qué-está-verificado-y-qué-no)
16. [Alternativas](#16-alternativas)
17. [Pruebas y mutación de `LoadErrorText`](#17-pruebas-y-mutación-de-loaderrortext)
18. [Decisiones](#18-decisiones)
19. [Qué recordar](#19-qué-recordar)

---

## 1. Qué pide el enunciado y qué cubre esta etapa

Del documento 01: lista de ligandos con búsqueda en tiempo real; al elegir uno, descarga con **indicador de carga que siempre desaparece**; **cuatro errores** visibles (sin red, 404, fichero corrupto, tiempo agotado); **ningún cierre inesperado**.

Hasta ahora teníamos las **reglas** probadas en el ordenador (parser, red, catálogo, cargador). Esta etapa las **enchufa a la pantalla**:

| Antes (documentos 03, 06, 06a, 06b) | Esta etapa (06c) |
|---|---|
| `LigandCatalog`: lista y búsqueda, en Kotlin puro | Leer `ligands.txt` de los *assets* y dibujar la lista con su buscador |
| `LigandLoader` + `LoadState`: la carga y sus cuatro estados | Un `ViewModel` que lo guarda; un indicador y un diálogo de error por cada estado |
| `LoadError`: **por qué** falló, como valor | **El texto** que lee el usuario, y si ofrecemos «Reintentar» |
| `Molecule`: el resultado | Una pantalla **provisional** que lo muestra como texto (la escena 3D viene después) |

**Por qué una pantalla provisional y no ya el 3D:** el 3D con una molécula real necesita decisiones propias (colores CPK, tamaños, encuadre de la cámara, rendimiento); mezclarlas con el pegamento haría imposible saber,
si algo falla, **de quién** es la culpa. Con la pantalla de texto se comprueba **en el móvil** que la cadena entera (lista → descarga → parser) funciona con datos reales, y se puede ver cada átomo con sus coordenadas.

---

## 2. Mapa: qué fichero hace qué

| Fichero | Ruta (bajo `app/src/main/java/dev/knowledge82/swiftyproteins/`) | Líneas | Para qué |
|---------|------|-------:|----------|
| `ui/LoadErrorText.kt` | `ui/` | 49 | `LoadError` → frase para el usuario; ¿ofrecer «Reintentar»? **Kotlin puro, con pruebas.** |
| `ui/LigandsViewModel.kt` | `ui/` | 82 | El `ViewModel` (envoltura de `LigandCatalog` y `LigandLoader`) y su fábrica. |
| `ui/SwiftyApp.kt` | `ui/` | 67 | Raíz: según el `LoadState` dibuja la lista o la molécula. Guarda el texto buscado y la posición de la lista. |
| `ui/LigandListScreen.kt` | `ui/` | 95 | El buscador y la `LazyColumn`. |
| `ui/LoadDialogs.kt` | `ui/` | 90 | El diálogo «cargando» y el diálogo de error. |
| `ui/MoleculeSummaryScreen.kt` | `ui/` | 62 | **Provisional:** números y átomos de la molécula, como texto. |
| `MainActivity.kt` | raíz del paquete | 28 | Sustituye a la anterior (que mostraba la escena de prueba): crea el `ViewModel` y pone `SwiftyApp`. |
| `LoadErrorTextTest.kt` | `app/src/test/.../ui/` | 74 | 7 pruebas de los textos y de «Reintentar». |
| `build.gradle.kts` y `libs.versions.toml` | `app/` y `gradle/` | — | Tres dependencias nuevas (sección 4). |

`MoleculeScene.kt` (el código demostrativo del spike, decisión D11) **sigue en el proyecto pero ya nadie lo llama**. Compila igual; se reescribirá para dibujar un `Molecule` en la etapa del 3D.

Cómo encaja (cada flecha es «llama a» o «publica en»):

```
 ligands.txt (assets) ─leer─▶ LigandsViewModel.catalog ──▶ LigandListScreen (buscar, tocar un id)
                                                                   │ onOpen(id)
                                                                   ▼
 LigandRepository ◀── source ── LigandLoader ◀── LigandsViewModel.open(id)
 (red + parser)                      │ state: StateFlow<LoadState>
                                     ▼
                      SwiftyApp  ──  when (LoadState)
                       ├─ Idle / Loading / Failed ─▶ lista  + LoadStateDialogs (cargando / error)
                       └─ Loaded ─────────────────▶ MoleculeSummaryScreen  (pronto: escena 3D)
```

---

## 3. Conceptos de Android y Compose que hacen falta

Esta sección es **teoría**; si ya la dominas, salta a la 4. Cada concepto está en el glosario (`00-glosario.md`, sección 8).

**3.1 La actividad se destruye cuando giras el móvil.** Al girar, Android **destruye la `Activity` y crea otra** (para volver a leer recursos, tamaños...). Todo lo que viva en la actividad o en sus variables **se pierde**.
Si la descarga de un ligando estaba a medias y el estado vivía ahí, el giro la tiraría. Hay **tres sitios** para guardar cosas, con distinta vida:

| Dónde | Sobrevive al giro | Sobrevive a que Android mate el proceso | Para qué lo usamos |
|-------|:-:|:-:|---|
| Variable normal / `remember` | no | no | Cálculos temporales (el filtrado de la lista) |
| `rememberSaveable` (se guarda en un `Bundle`) | **sí** | **sí** (pequeño: texto, números) | El **texto del buscador** |
| `ViewModel` | **sí** | no | El catálogo, la carga en curso y su estado |

**3.2 `ViewModel`.** Objeto que Android **mantiene vivo mientras dure la pantalla lógica**, aunque la actividad se recree. Trae un ámbito de corrutinas, `viewModelScope`, que **se cancela solo** cuando el `ViewModel` muere definitivamente
(el usuario sale de la app con «Atrás»): cualquier descarga en curso se cancela sin que escribamos nada. En esa cancelación se apoya la garantía del indicador (doc 06b, sección 7).

**3.3 Compose en cinco ideas** (la teoría completa está en el glosario):
1. Una pantalla es una **función `@Composable`** que **describe** la interfaz a partir de datos. No se «modifica» la pantalla: se cambian los datos y la función se **vuelve a ejecutar**.
2. Esa re-ejecución se llama **recomposición**. Compose solo recompone lo que **leyó** el dato que cambió.
3. El dato que, al cambiar, provoca recomposición es un **`State`**. `mutableStateOf(x)` crea uno; `by` permite leerlo y escribirlo como una variable normal.
4. `remember { ... }` **guarda un valor entre recomposiciones** (si no, se recalcularía cada vez). `remember(a, b) { ... }` lo recalcula **solo si `a` o `b` cambian**.
5. **Elevar el estado** (*state hoisting*): una pantalla no guarda su estado; lo **recibe** (`query`) y recibe **cómo avisar** de un cambio (`onQueryChange`). Así quien está encima decide dónde vive. Aquí lo usamos para que
   el texto buscado **no se pierda** cuando la lista sale de la pantalla (sección 7).

**3.4 Observar un `StateFlow` desde Compose:** `flow.collectAsStateWithLifecycle()` lo convierte en `State` **y deja de escuchar cuando la app no está visible** (ahorra batería; reanuda al volver). Hay una versión más simple,
`collectAsState()`, que escucha siempre; la elegimos por ser la práctica recomendada.

**3.5 Hilos: nadie bloquea el principal.** La interfaz se dibuja en **un solo hilo** (el principal). Si ese hilo se queda esperando a la red, la pantalla se congela y Android muestra «la aplicación no responde» (ANR). Por eso
`LigandLoader` ejecuta la descarga en `Dispatchers.IO` y **solo** publica el resultado en el hilo principal. Todas las llamadas de esta etapa (`open`, `cancel`) **vienen de toques**, es decir, del hilo principal.

**3.6 *Assets*.** Carpeta `app/src/main/assets/` que se **empaqueta tal cual** dentro de la app. Se lee con `context.assets.open("ligands.txt")`. No es un fichero del disco que se pueda borrar: si falta, la instalación está rota.

**3.7 Márgenes del sistema (*insets*).** Con `enableEdgeToEdge()` la app dibuja **debajo** de la barra de estado y de navegación; hay que dejar margen para no tapar contenido. `Scaffold` da ese margen (`innerPadding`).
Con el teclado abierto hay que dejar otro margen más (`imePadding`).

---

## 4. Dependencias nuevas (Gradle)

**`gradle/libs.versions.toml`**, tres líneas en `[libraries]` y una en `[versions]`:

```toml
[versions]
kotlinxCoroutines = "1.9.0"

[libraries]
androidx-lifecycle-viewmodel-ktx = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-ktx", version.ref = "lifecycleRuntimeKtx" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycleRuntimeKtx" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "kotlinxCoroutines" }
```

**`app/build.gradle.kts`**, dentro de `dependencies { }`:

```kotlin
implementation(libs.androidx.lifecycle.viewmodel.ktx)
implementation(libs.androidx.lifecycle.runtime.compose)
implementation(libs.kotlinx.coroutines.android)
```

| Dependencia | Para qué | Por qué esta versión |
|---|---|---|
| `lifecycle-viewmodel-ktx` | `ViewModel` y `viewModelScope` | **2.6.1**, la misma que ya usa el proyecto en `lifecycle-runtime-ktx` (`version.ref` compartido) para no mezclar |
| `lifecycle-runtime-compose` | `collectAsStateWithLifecycle()` | ídem |
| `kotlinx-coroutines-android` | corrutinas y `Dispatchers.Main` de Android | **1.9.0**: la recuerdo como existente; **no se ha comprobado** |

Qué puede pasar y qué significa:
- **Si otra librería trae una versión mayor** de alguna, Gradle **elige la mayor** (regla de resolución de conflictos). Declarar una versión baja no estropea nada; lo que **sí** puede fallar es que la versión que escribí **no exista**
  (error `Could not find ...`): se arregla con otra del mismo grupo.
- Las corrutinas **ya llegaban de forma transitiva** (las 25 pruebas del cargador compilaron en tu Gradle sin declararlas); declararlas **explícitamente** es honestidad: el código las usa directamente.
- **Lo que no se verificó:** que 2.6.1 convenga con AGP 9.4.1 y Kotlin 2.4.21. En 2.8 en adelante `viewModelScope` pasó del artefacto `-ktx` al principal, **con el mismo nombre de paquete**: el código fuente no cambia si Gradle sube la versión.

---

## 5. `LoadErrorText.kt`: qué lee el usuario cuando falla

Hasta ahora `LoadError` decía **por qué** falló (para el programa). Aquí se convierte en **la frase** (para la persona). Es Kotlin puro: sin `android.*`, con pruebas.

```kotlin
// LoadErrorText.kt · líneas 5–35
 5  /**
 6   * What the user reads when a load fails. Pure Kotlin (no Android): it can be tested on the JVM.
 7   *
 8   * `when` over a sealed interface WITHOUT an `else` branch: if tomorrow someone adds a new [LoadError],
 9   * this file stops compiling until it gets a message. A new error can never reach the screen mute.
10   *
11   * The technical detail (the reason text, the parser failure) is NOT shown: the user cannot act on it.
12   * It goes to Logcat instead (see `LoadStateDialogs`).
13   */
14  fun LoadError.userMessage(id: String): String = when (this) {
15      is LoadError.NoConnection ->
16          "There is no network connection. Check your Wi-Fi or mobile data and try again."
17      is LoadError.Timeout ->
18          "The server took too long to answer. Try again in a moment."
19      is LoadError.NotFound ->
20          "The ligand \"$id\" does not exist on the server (error 404)."
21      is LoadError.ServerError ->
22          "The server had a problem (error $code). Try again later."
23      is LoadError.Incomplete ->
24          "The download was cut off before it finished. Try again."
25      is LoadError.TooLarge ->
26          "The file for \"$id\" is far bigger than any ligand file can be, so it was discarded."
27      is LoadError.Corrupt ->
28          "The file for \"$id\" arrived, but it is damaged or has an unexpected format."
29      is LoadError.InvalidId ->
30          "\"$id\" is not a valid ligand identifier."
31      is LoadError.NetworkError ->
32          "A network error happened. Try again."
33      is LoadError.Unexpected ->
34          "Something unexpected went wrong. Try again."
35  }
```

- **Línea 14**, `fun LoadError.userMessage(id: String)`: una **función de extensión** sobre `LoadError`. Se escribe `error.userMessage("ATP")` como si fuera un método de `LoadError`, pero vive aquí: `LoadError` (capa de red) no sabe nada de
  textos ni de pantallas. Recibe el `id` porque algunas frases lo nombran.
- **`when (this)` sin `else`**: `LoadError` es una `sealed interface`, así que el compilador **conoce todos los casos** y **exige** que `when` los trate. Si mañana alguien añade un caso nuevo, **este fichero deja de compilar** hasta que tenga frase:
  ningún error puede llegar a la pantalla sin explicación. (Con un `else` se perdería esa garantía: por eso no lo hay.)
- **Los cuatro errores del enunciado** salen así: sin red → línea 15; 404 → línea 19; fichero corrupto → línea 27 (también `Incomplete`, línea 23); tiempo agotado → línea 17.
- **Línea 22, `$code`**: dentro de `ServerError` el código (`503`) es una propiedad; en el `when` con `is` el compilador sabe que `this` **es** un `ServerError`, así que `code` está disponible (*smart cast*).
- **Lo que NO se muestra**: el texto técnico (`NetworkError.reason`, el `CifFailure` de `Corrupt`, la descripción de `Unexpected`). El usuario no puede hacer nada con «`SSLHandshakeException`»; a nosotros nos sirve en el **Logcat**
  (sección 9). Una prueba lo garantiza: `technicalDetailsNeverReachTheUser`.
- Las frases están **en inglés y escritas en el código** (decisión D20, sección 18).

```kotlin
// LoadErrorText.kt · líneas 37–49
37  /**
38   * Does it make sense to offer a "Retry" button? Only if repeating the same request can give another answer.
39   *
40   * - NotFound, InvalidId, TooLarge: the same request gets the same answer. No button.
41   * - Corrupt: YES, on purpose. A hotel Wi-Fi login page answers "200 OK" with HTML instead of the CIF file, which
42   *   arrives as "corrupt"; once the user logs in, retrying works.
43   */
44  fun LoadError.canRetry(): Boolean = when (this) {
45      is LoadError.NotFound, is LoadError.InvalidId, is LoadError.TooLarge -> false
46      is LoadError.NoConnection, is LoadError.Timeout, is LoadError.ServerError,
47      is LoadError.Incomplete, is LoadError.Corrupt, is LoadError.NetworkError,
48      is LoadError.Unexpected -> true
49  }
```

- **¿Cuándo tiene sentido «Reintentar»?** Solo si repetir **la misma petición** puede dar **otra respuesta**.
  - `NotFound`, `InvalidId`, `TooLarge` → **no**: el servidor volvería a decir lo mismo. Solo botón «OK».
  - `NoConnection`, `Timeout`, `ServerError`, `Incomplete`, `NetworkError`, `Unexpected` → **sí**.
  - **`Corrupt` → sí, a propósito.** Es el caso sutil: la página de acceso de un Wi-Fi de hotel o de aeropuerto responde «200 OK» con **HTML**, no con el fichero CIF. A nosotros nos llega como «fichero dañado», pero el usuario, al
    identificarse en el Wi-Fi, lo arregla y **reintenta**. Sin botón tendría que cerrar y volver a tocar el ligando. *(Es un razonamiento; no se ha reproducido con un portal real.)*
- Otra vez `when` sin `else`: un error nuevo obliga a decidir también esto.

---

## 6. `LigandsViewModel.kt`: la envoltura que sobrevive al giro

**Idea (decisión D18):** todas las reglas ya están en `LigandCatalog` y `LigandLoader` (con pruebas). El `ViewModel` solo **guarda** esos objetos mientras dure la pantalla y **reenvía** las llamadas.

```kotlin
// LigandsViewModel.kt · líneas 19–52
19  /**
20   * Thin wrapper (decision D18): it holds the objects that must SURVIVE a screen rotation and forwards the calls.
21   * All the rules live in [LigandLoader] and [LigandCatalog], which are tested on the JVM.
22   *
23   * It receives plain lambdas instead of a `Context`, so nothing in here depends on Android classes except
24   * `ViewModel` and `Log`.
25   *
26   * @param readCatalogText returns the whole text of `ligands.txt` (may throw if the file cannot be read)
27   * @param source          loads one ligand (blocking; it is run on `Dispatchers.IO` by the loader)
28   */
29  class LigandsViewModel(
30      readCatalogText: () -> String,
31      source: LigandSource,
32  ) : ViewModel() {
33
34      /** The ligand list, or `null` if `ligands.txt` could not be read (a broken installation: the screen says so). */
35      val catalog: LigandCatalog? = try {
36          LigandCatalog.fromText(readCatalogText())
37      } catch (e: Exception) {
38          Log.e(TAG, "ligands.txt could not be read", e)
39          null
40      }
41
42      private val loader = LigandLoader(source, viewModelScope, Dispatchers.IO)
43
44      /** What the screens observe. */
45      val loadState: StateFlow<LoadState> = loader.state
46
47      /** Called from the main thread (a tap). */
48      fun open(id: String) = loader.load(id)
49
50      /** Called from the main thread (Cancel, Back, closing an error). Returns the state to Idle. */
51      fun cancel() = loader.cancel()
52  }
```

- **Líneas 29–32:** el constructor **no recibe un `Context`** sino **dos funciones**: cómo leer el texto de `ligands.txt` y cómo cargar un ligando (`LigandSource`, el mismo tipo del cargador). Así este fichero casi no depende de Android
  (solo de `ViewModel` y `Log`) y quien lo crea decide qué piezas reales o falsas le da. Ver la fábrica abajo.
- **Líneas 35–40, `val catalog`:** se lee el texto y se construye el catálogo **una sola vez**, al crear el `ViewModel`. Es **nullable** (`LigandCatalog?`) a propósito: si `ligands.txt` no se pudo leer, `catalog` es `null`,
  se anota en el Logcat y **la pantalla lo dice** («no se pudo leer la lista»). La alternativa (dejar que la excepción suba) **cerraría la app**, y un cierre inesperado es un suspenso. El `try` solo envuelve la lectura, no todo el constructor.
- **Lee en el hilo principal** (sin corrutina): son unos 5 KB empaquetados en la app; leerlos tarda una fracción de milisegundo. Con un fichero grande habría que moverlo a `Dispatchers.IO` (sección 14).
- **Línea 42:** el cargador, con **`viewModelScope`** (el ámbito que muere con el `ViewModel`) y **`Dispatchers.IO`** para el trabajo lento. Cumple lo exigido por el cargador: sus continuaciones se ejecutan en el hilo principal.
- **Línea 45:** `loadState` es el `StateFlow` del cargador, **sin envolver**: la pantalla lo observa.
- **Líneas 48 y 51:** `open` y `cancel` son **una línea cada una**: reenvían. Es todo el comportamiento.
- **No hay `onCleared()`**: cuando el `ViewModel` muere, `viewModelScope` se cancela solo, y eso cancela la corrutina de descarga, y esa cancelación llega a la red a través de `isCancelled` (doc 06, sección 7).

```kotlin
// LigandsViewModel.kt · líneas 54–82
54  /**
55   * Builds the [LigandsViewModel] with the real pieces: the file in `assets/` and the real network.
56   *
57   * A plain `ViewModelProvider.Factory` (and not the `viewModelFactory { initializer { } }` DSL) because overriding
58   * `create(Class)` compiles with every version of the library, and we have not verified which one Gradle will pick.
59   */
60  class LigandsViewModelFactory(context: Context) : ViewModelProvider.Factory {
61
62      // The APPLICATION context, never an Activity: a ViewModel outlives the activity and would keep it in memory.
63      private val appContext = context.applicationContext
64
65      @Suppress("UNCHECKED_CAST")
66      override fun <T : ViewModel> create(modelClass: Class<T>): T {
67          require(modelClass.isAssignableFrom(LigandsViewModel::class.java)) {
68              "This factory only builds LigandsViewModel, not ${modelClass.name}"
69          }
70          val repository = LigandRepository(HttpUrlConnectionFetcher())
71          return LigandsViewModel(
72              readCatalogText = {
73                  appContext.assets.open(CATALOG_FILE).bufferedReader().use { it.readText() }
74              },
75              source = { id, isCancelled -> repository.load(id, isCancelled) },
76          ) as T
77      }
78
79      private companion object {
80          const val CATALOG_FILE = "ligands.txt"
81      }
82  }
```

- **¿Por qué hace falta una fábrica?** Android crea los `ViewModel` **él mismo**, y por defecto solo sabe llamar a un constructor **sin argumentos**. El nuestro necesita dos funciones; la fábrica le enseña a construirlo.
- **Línea 60:** recibe un `Context` pero solo guarda **`context.applicationContext`** (línea 63). Regla de oro: **un `ViewModel` nunca debe retener una `Activity`** (vive más que ella y la mantendría en memoria tras destruirse:
  una fuga). El contexto de aplicación vive tanto como el proceso.
- **Líneas 66–69, `create`:** Android pregunta «dame un `T`». `require(...)` comprueba que piden **nuestro** `ViewModel` (si piden otro, falla con un mensaje claro en vez de un `ClassCastException` raro).
- **Línea 70:** el repositorio **real**: `HttpUrlConnectionFetcher` con los tres relojes y el límite de tamaño por defecto (D15).
- **Línea 73:** `assets.open(...).bufferedReader().use { it.readText() }`: abre el fichero, lo lee **entero como texto UTF-8** y **lo cierra** (`use` cierra aunque haya error).
- **Línea 75:** `{ id, isCancelled -> repository.load(id, isCancelled) }` es el `LigandSource`: la función que el cargador llamará **en un hilo de fondo**.
- **Línea 76, `as T`:** conversión sin comprobar (por eso el `@Suppress("UNCHECKED_CAST")` de la 65). Es seguro porque `require` ya comprobó la clase.
- **Por qué una fábrica «a mano» y no `viewModelFactory { initializer { } }`:** esa sintaxis existe desde la versión 2.5 de la librería, pero en qué artefacto vive ha cambiado entre versiones; sobrescribir `create(Class)` **compila con
  todas**, y no hemos verificado cuál elegirá Gradle.

---

## 7. `SwiftyApp.kt`: la raíz, o «navegar» sin librería

```kotlin
// SwiftyApp.kt · líneas 19–67
19  /**
20   * The root of the screen: decides WHAT to show from the [LoadState] (no navigation library, see D19).
21   *
22   *   Loaded                 -> the 3D screen of the molecule
23   *   Idle / Loading / Failed -> the list, with a dialog on top while loading or when it failed
24   */
25  @Composable
26  fun SwiftyApp(viewModel: LigandsViewModel) {
27      val state by viewModel.loadState.collectAsStateWithLifecycle()
28
29      // Kept HERE (and not inside the list screen) because the list screen leaves the composition while the molecule
30      // is shown, and anything it remembered would be lost: the user would come back to an empty search box at the top.
31      var query by rememberSaveable { mutableStateOf("") }
32      val listState = rememberLazyListState()
33      val scope = rememberCoroutineScope()
34
35      Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
36          // consumeWindowInsets: tells the children "the system bars are already taken into account", so that
37          // imePadding() inside the list does not add them a second time when the keyboard opens.
38          val content = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding)
39
40          val current = state   // `state` is a delegated property: copy it to be able to use `is` checks on it
41          if (current is LoadState.Loaded) {
42              MoleculeScreen(
43                  molecule = current.molecule,
44                  onBack = viewModel::cancel,
45                  modifier = content,
46              )
47          } else {
48              LigandListScreen(
49                  catalog = viewModel.catalog,
50                  query = query,
51                  onQueryChange = { newText ->
52                      query = newText
53                      // A new search starts at the top; without this the list would keep an old position.
54                      scope.launch { listState.scrollToItem(0) }
55                  },
56                  listState = listState,
57                  onOpen = viewModel::open,
58                  modifier = content,
59              )
60              LoadStateDialogs(
61                  state = current,
62                  onOpen = viewModel::open,
63                  onClose = viewModel::cancel,
64              )
65          }
66      }
67  }
```

- **Línea 27:** `val state by viewModel.loadState.collectAsStateWithLifecycle()`. Convierte el `StateFlow` en un `State` de Compose: cada vez que el cargador publica un estado nuevo, **esta función se vuelve a ejecutar**.
- **Líneas 31–33, lo que se guarda aquí y por qué:**
  - `query` con `rememberSaveable`: el **texto del buscador**; sobrevive al giro y a que Android mate el proceso.
  - `listState`: la **posición de desplazamiento** de la lista (`rememberLazyListState` ya es «saveable» por dentro).
  - `scope`: un ámbito de corrutinas atado a esta función (para `scrollToItem`, que es una función de suspensión).
  - **Están aquí y no en `LigandListScreen` porque esa pantalla sale de la composición mientras se ve la molécula**; lo que hubiera recordado se perdería y, al volver, el usuario encontraría el buscador vacío y la lista arriba.
    Con el estado aquí, **volver deja todo como estaba** (elevación de estado, sección 3.3).
- **Línea 35, `Scaffold`:** estructura estándar de Material 3; entrega `innerPadding`, el margen que deja libre las barras del sistema.
- **Línea 38:** `Modifier.padding(innerPadding).consumeWindowInsets(innerPadding)`. El `padding` aplica el margen; `consumeWindowInsets` avisa a los hijos de que **ese margen ya está contado**. Sin él, `imePadding()` (en la lista)
  volvería a sumar la barra de navegación al abrir el teclado y quedaría un hueco de más. *(Es mi comprensión de cómo se reparten los márgenes; **no comprobado en el A33**.)*
- **Línea 40:** `val current = state`. `state` es una propiedad **delegada** (`by`); el compilador no permite deducir su tipo tras un `is` porque podría cambiar entre la comprobación y el uso. Copiarla a un `val` local lo permite
  (*smart cast* en la 41–43).
- **Líneas 41–65: la «navegación».** Es un simple `if`: si el estado es `Loaded`, se dibuja la molécula; en cualquier otro, la lista (y encima, el diálogo que corresponda). **El estado de carga ES la navegación.** Ventajas: cero
  librerías, y **es imposible** estar en la pantalla de la molécula sin molécula. Desventaja: cuando haya inicio de sesión, ajustes, etc., esto se queda corto (sección 16).
- **Líneas 51–55, `onQueryChange`:** guarda el texto nuevo y **lleva la lista al principio** (`scrollToItem(0)`). Sin esto, tras escribir una letra la lista podría quedarse en una posición vieja y enseñar el final de los resultados.
- **Líneas 44 y 57–63: `viewModel::cancel` y `viewModel::open`** son *referencias a función*: pasan la propia función como valor. `onBack = viewModel::cancel` equivale a `{ viewModel.cancel() }`.
- **El botón «Atrás» y la molécula:** `MoleculeSummaryScreen` llama a `cancel()`; el estado vuelve a `Idle`; la raíz recompone y dibuja la lista. Ver sección 10.

---

## 8. `LigandListScreen.kt`: buscador y lista

```kotlin
// LigandListScreen.kt · líneas 25–88
25  /**
26   * The list of ligands with the search box on top.
27   *
28   * It owns NO state: the text, the scroll position and what to do on a tap come from outside ("state hoisting").
29   * That is what lets [SwiftyApp] keep them while the user looks at a molecule and comes back.
30   *
31   * @param catalog the ligands, or `null` if the file could not be read
32   */
33  @Composable
34  fun LigandListScreen(
35      catalog: LigandCatalog?,
36      query: String,
37      onQueryChange: (String) -> Unit,
38      listState: LazyListState,
39      onOpen: (String) -> Unit,
40      modifier: Modifier = Modifier,
41  ) {
42      // Recomputed ONLY when the catalog or the text change (not on every recomposition). It is the "real time"
43      // search: each keystroke changes `query`, this line runs again and the list below redraws.
44      val results = remember(catalog, query) { catalog?.search(query) ?: emptyList() }
45      val focusManager = LocalFocusManager.current
46
47      Column(modifier = modifier.fillMaxSize().imePadding()) {
48          OutlinedTextField(
49              value = query,
50              onValueChange = onQueryChange,
51              label = { Text("Search ligand (for example ATP, hem, 01)") },
52              singleLine = true,
53              modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
54          )
55
56          when {
57              catalog == null ->
58                  CenteredMessage("The ligand list could not be read. Reinstall the app.")
59              results.isEmpty() && query.isBlank() ->
60                  CenteredMessage("The ligand list is empty.")
61              results.isEmpty() ->
62                  CenteredMessage("No ligand matches \"${query.trim()}\".")
63              else -> {
64                  Text(
65                      text = if (query.isBlank()) "${results.size} ligands" else "${results.size} matches",
66                      style = MaterialTheme.typography.labelMedium,
67                      modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
68                  )
69                  LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
70                      // `key`: identifies each row by its id, so Compose can reuse rows when the list changes.
71                      items(results, key = { it }) { id ->
72                          Text(
73                              text = id,
74                              style = MaterialTheme.typography.bodyLarge,
75                              modifier = Modifier
76                                  .fillMaxWidth()
77                                  .clickable {
78                                      focusManager.clearFocus()   // hide the keyboard
79                                      onOpen(id)
80                                  }
81                                  .padding(horizontal = 16.dp, vertical = 14.dp),
82                          )
83                      }
84                  }
85              }
86          }
87      }
88  }
```

- **Líneas 34–41:** la pantalla **no guarda estado**: recibe `query`, `onQueryChange`, `listState` y `onOpen`. Eso la hace fácil de entender y de probar (y es lo que permite el truco de la sección 7).
- **Línea 44, la búsqueda en tiempo real:** `remember(catalog, query) { catalog?.search(query) ?: emptyList() }`.
  1. Cada pulsación de tecla llama a `onQueryChange` → cambia `query` en `SwiftyApp` → Compose vuelve a ejecutar esta función con el `query` nuevo.
  2. `remember(catalog, query)` ve que `query` cambió y **recalcula** `search`; si **no** cambió (p. ej. una recomposición por otra causa), reutiliza el resultado.
  3. `catalog?.search(query)`: si el catálogo es `null`, el resultado es `null`, y `?: emptyList()` lo cambia por una lista vacía.
  - **Coste:** la búsqueda mide 57 µs en la JVM de análisis (doc 06b, sección 5; **no medido en el A33**). Con el máximo posible (212 resultados) el coste que importa es **dibujar**, y eso lo limita la `LazyColumn`.
- **Línea 45:** `LocalFocusManager.current`: para quitar el foco (y con él ocultar el teclado) al tocar un ligando.
- **Línea 47:** `Column` con `fillMaxSize()` y **`imePadding()`**: al abrir el teclado la lista se encoge en vez de quedar tapada.
- **Líneas 48–54, `OutlinedTextField`:** el cuadro de texto. `value` y `onValueChange` son el patrón de estado elevado: el campo **no guarda el texto**, lo recibe y avisa. `singleLine = true`: sin saltos de línea.
  **No hay mayúsculas forzadas**: la búsqueda no distingue (D17), así que escribir `hem` o `HEM` da lo mismo.
- **Líneas 56–86, el `when` de cuatro casos:**
  - `catalog == null` → mensaje de instalación rota (la lectura falló).
  - Sin resultados y texto vacío → «la lista está vacía» (fichero vacío).
  - Sin resultados con texto → «ningún ligando coincide con "xyz"». **No se queda en blanco**: una pantalla vacía sin explicación parece un fallo.
  - Si hay resultados → contador («1243 ligands» / «3 matches») y la lista.
- **Línea 71, `items(results, key = { it })`:** una fila por id. **`key`** identifica cada fila por su id: al cambiar el filtro, Compose **reutiliza** las filas que siguen existiendo en vez de recrearlas. Exige claves **únicas**, y lo son porque
  `LigandCatalog.fromText` elimina repetidos (`distinct()`, doc 06b). **Si hubiera dos filas con la misma clave, la app se cerraría**; esa es una razón concreta de que `distinct()` importe.
- **Líneas 75–81:** toda la fila es pulsable (`clickable`), con relleno para un blanco táctil cómodo. Al tocar: quita el foco y llama a `onOpen(id)`.

```kotlin
// LigandListScreen.kt · líneas 90–95
90  @Composable
91  private fun CenteredMessage(text: String) {
92      Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
93          Text(text = text, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
94      }
95  }
```

- `CenteredMessage` es `private`: solo la usa este fichero. Un `Box` que llena el espacio y centra el texto.

---

## 9. `LoadDialogs.kt`: el indicador de carga y los errores

```kotlin
// LoadDialogs.kt · líneas 23–56
23  /**
24   * Draws the dialog that goes with the current [LoadState], or nothing.
25   *
26   * - Loading: a modal dialog with a spinner and a Cancel button. A MODAL dialog is the simplest way to block the
27   *   screen below while loading (no taps reach the list), and the system Back button closes it as Cancel.
28   * - Failed: the message for the error, with "Retry" when repeating makes sense.
29   * - Idle / Loaded: nothing. The `when` has no `else`, so a fifth state would not compile until handled here.
30   *
31   * @param onOpen  starts loading an id (used by "Retry")
32   * @param onClose goes back to Idle (Cancel, Back, closing an error)
33   */
34  @Composable
35  fun LoadStateDialogs(
36      state: LoadState,
37      onOpen: (String) -> Unit,
38      onClose: () -> Unit,
39  ) {
40      when (state) {
41          is LoadState.Loading -> LoadingDialog(id = state.id, onCancel = onClose)
42          is LoadState.Failed -> {
43              // The user sees a friendly sentence; the technical cause (parser failure, reason text) goes to Logcat.
44              LaunchedEffect(state) {
45                  Log.w(TAG, "loading ${state.id} failed: ${state.error}")
46              }
47              ErrorDialog(
48                  id = state.id,
49                  error = state.error,
50                  onRetry = { onOpen(state.id) },
51                  onClose = onClose,
52              )
53          }
54          is LoadState.Idle, is LoadState.Loaded -> Unit
55      }
56  }
```

- **Es un `when` sobre `LoadState` sin `else`**: los cuatro estados están tratados. Un quinto estado no compilaría hasta tener su caso aquí. Esto, junto con la garantía del cargador (doc 06b), es lo que sostiene el requisito
  «el indicador siempre desaparece»: **el indicador es el diálogo de `Loading`; en cuanto el estado deja de serlo, el diálogo deja de dibujarse.** No hay un `isLoading = true` que alguien olvide poner a `false`.
- **Línea 41, `Loading`:** dibuja el diálogo de carga. `onCancel = onClose`: cancelar y cerrar son lo mismo (volver a `Idle`).
- **Líneas 42–53, `Failed`:**
  - `LaunchedEffect(state) { Log.w(...) }` ejecuta el bloque **una vez cada vez que aparece un `Failed` nuevo** (la clave es el estado). Escribe en el **Logcat** el id y el `LoadError` completo, con el **motivo técnico** que al
    usuario no se le enseña (el `CifFailure` exacto, la excepción de red). Es lo que se lee en Android Studio cuando alguien dice «me sale "fichero dañado"».
    Un `LaunchedEffect` y no un `Log` suelto porque el cuerpo de una función `@Composable` **se ejecuta muchas veces** (en cada recomposición); sin él, el mismo fallo se anotaría decenas de veces.
  - `onRetry = { onOpen(state.id) }`: reintentar es **volver a pedir el mismo id**. El estado pasa por `Loading` (otro valor) y de ahí al resultado.
- **Línea 54:** `Idle` y `Loaded` no dibujan nada aquí (`Unit`).

```kotlin
// LoadDialogs.kt · líneas 58–73
58  @Composable
59  private fun LoadingDialog(id: String, onCancel: () -> Unit) {
60      AlertDialog(
61          onDismissRequest = onCancel,                                    // the system Back button
62          properties = DialogProperties(dismissOnClickOutside = false),   // an accidental tap must not cancel
63          title = { Text("Loading $id") },
64          text = {
65              Row(verticalAlignment = Alignment.CenterVertically) {
66                  CircularProgressIndicator(modifier = Modifier.size(32.dp))
67                  Spacer(modifier = Modifier.width(16.dp))
68                  Text("Downloading the ligand...")
69              }
70          },
71          confirmButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
72      )
73  }
```

- **Por qué un diálogo y no una capa encima de la lista:** un `AlertDialog` es **modal** (los toques de fuera **no llegan** a la lista de debajo) y el botón «Atrás» del sistema lo cierra. Con una capa hecha a mano habría que
  bloquear los toques y atender el botón «Atrás» a mano; son dos fuentes de errores.
- **Línea 61, `onDismissRequest = onCancel`:** Android llama a esto cuando el usuario pulsa «Atrás» (o toca fuera). Aquí significa **cancelar**: el cargador cancela el trabajo y vuelve a `Idle`.
- **Línea 62, `dismissOnClickOutside = false`:** un toque accidental fuera **no** cancela la descarga. Solo «Cancelar» o «Atrás».
- **Líneas 64–70:** el indicador (`CircularProgressIndicator`, giratorio, sin porcentaje: no sabemos cuánto falta) y el texto.
- **Línea 71:** botón «Cancel». Va en `confirmButton` porque ese es el único hueco de botón que usamos (el diálogo solo tiene una acción).

```kotlin
// LoadDialogs.kt · líneas 75–90
75  @Composable
76  private fun ErrorDialog(id: String, error: LoadError, onRetry: () -> Unit, onClose: () -> Unit) {
77      val retry = error.canRetry()
78      AlertDialog(
79          onDismissRequest = onClose,
80          title = { Text("Could not load $id") },
81          text = { Text(error.userMessage(id)) },
82          confirmButton = {
83              if (retry) TextButton(onClick = onRetry) { Text("Retry") }
84              else TextButton(onClick = onClose) { Text("OK") }
85          },
86          dismissButton = {
87              if (retry) TextButton(onClick = onClose) { Text("Close") }
88          },
89      )
90  }
```

- **Línea 77:** `retry` decide el diseño: si **se puede reintentar** → botones «Retry» (principal) y «Close»; si no → solo «OK».
- **Línea 81:** `error.userMessage(id)`, el texto de la sección 5.
- **Líneas 82–88:** `confirmButton` y `dismissButton` contienen un `if`; cuando no hay «Close», el `dismissButton` queda **vacío** (no dibuja nada).
- **Línea 79, `onDismissRequest = onClose`:** «Atrás» o tocar fuera cierra el error y vuelve a la lista (`Idle`). **Así el error nunca atrapa al usuario.**

---

## 10. `MoleculeSummaryScreen.kt`: la pantalla provisional

```kotlin
// MoleculeSummaryScreen.kt · líneas 19–62
19  /**
20   * TEMPORARY screen: it shows the numbers of the molecule that the parser produced, as text.
21   * It proves that the whole chain (list -> download -> parse) works on the device with real data.
22   * The next step replaces it with the 3D scene that draws the same [Molecule].
23   */
24  @Composable
25  fun MoleculeSummaryScreen(
26      molecule: Molecule,
27      onBack: () -> Unit,
28      modifier: Modifier = Modifier,
29  ) {
30      // The system Back button returns to the list, exactly like the button on screen.
31      BackHandler(onBack = onBack)
32
33      Column(modifier = modifier.fillMaxSize()) {
34          Text(
35              text = "Ligand ${molecule.id}",
36              style = MaterialTheme.typography.headlineSmall,
37              modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
38          )
39          Text(
40              text = "${molecule.atoms.size} atoms, ${molecule.bonds.size} bonds",
41              style = MaterialTheme.typography.bodyLarge,
42              modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
43          )
44          Button(onClick = onBack, modifier = Modifier.padding(16.dp)) {
45              Text("Back to the list")
46          }
47          LazyColumn(modifier = Modifier.fillMaxSize()) {
48              itemsIndexed(molecule.atoms) { index, atom ->
49                  Text(
50                      // Locale.US: always "1.234", never "1,234" (the device language must not change the text).
51                      text = String.format(
52                          Locale.US, "%3d  %-5s %-2s %8.3f %8.3f %8.3f",
53                          index, atom.name, atom.element, atom.x, atom.y, atom.z,
54                      ),
55                      fontFamily = FontFamily.Monospace,
56                      style = MaterialTheme.typography.bodySmall,
57                      modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
58                  )
59              }
60          }
61      }
62  }
```

- **Línea 31, `BackHandler(onBack = onBack)`:** captura el botón «Atrás» **del sistema** mientras esta pantalla está dibujada. Sin él, «Atrás» **cerraría la app** (saldría de la actividad). Con él, hace lo mismo que el botón
  de la pantalla: `cancel()`, estado `Idle`, vuelve la lista.
- **Líneas 34–43:** título y recuento (`molecule.atoms.size`, `molecule.bonds.size`). Para `001` deben salir **87 átomos y 90 enlaces** (documento 03): es la primera comprobación **en el móvil** de que el parser entrega lo mismo que en el ordenador.
- **Líneas 47–60, `LazyColumn` + `itemsIndexed`:** una fila por átomo: índice, nombre, elemento y coordenadas.
  - `String.format(Locale.US, "%3d  %-5s %-2s %8.3f %8.3f %8.3f", ...)`: columnas alineadas (`%3d` entero a 3 anchos, `%-5s` texto alineado a la izquierda, `%8.3f` decimal con tres cifras).
  - **`Locale.US` es esencial:** sin él, en un móvil en español el formato escribiría `1,234` (coma) y no `1.234`. Los datos deben verse **idénticos** en cualquier móvil.
  - `FontFamily.Monospace`: letra de ancho fijo, para que las columnas se alineen.
- **Es temporal**: esta pantalla se sustituirá por la escena 3D de `Molecule` (documento 07). Sirve para **ver los datos reales** sin 3D de por medio.

---

## 11. `MainActivity.kt`: el punto de entrada

```kotlin
// MainActivity.kt · líneas 13–28
13  class MainActivity : ComponentActivity() {
14
15      // Created the first time it is used and KEPT across rotations: the same object comes back after the
16      // activity is destroyed and recreated. The lambda builds it only if it does not exist yet.
17      private val viewModel: LigandsViewModel by viewModels { LigandsViewModelFactory(applicationContext) }
18
19      override fun onCreate(savedInstanceState: Bundle?) {
20          super.onCreate(savedInstanceState)
21          enableEdgeToEdge()
22          setContent {
23              SwiftyProteinsTheme {
24                  SwiftyApp(viewModel)
25              }
26          }
27      }
28  }
```

- **Línea 17, `by viewModels { ... }`:** pide a Android el `LigandsViewModel` **de esta actividad**. La primera vez lo **crea** con la fábrica (`LigandsViewModelFactory`); tras un giro, **devuelve el mismo objeto** y la lambda **no se ejecuta**.
  Con `by` se evalúa de forma **perezosa**: no se crea hasta que se usa por primera vez (en `setContent`, después de `super.onCreate`, cuando `applicationContext` ya existe).
- **Línea 21, `enableEdgeToEdge()`:** ya estaba: dibuja bajo las barras del sistema (de ahí los `innerPadding` de la sección 7).
- **Líneas 22–26:** `setContent` da el contenido de la actividad; `SwiftyProteinsTheme` (el tema de Material 3, que ya existía) envuelve todo; `SwiftyApp(viewModel)` es la raíz.
- Desaparecen `Greeting` y su vista previa (restos de la plantilla de Android Studio).

---

## 12. Simulaciones paso a paso

**A. Caso feliz: tocar `ATP`.**

| Paso | Qué ocurre | `loadState` | Pantalla |
|-----:|---|---|---|
| 0 | La app arranca. La fábrica lee `ligands.txt` y crea el catálogo | `Idle` | Lista de 1243 ligandos, buscador vacío |
| 1 | El usuario escribe `at` | `Idle` | `remember` recalcula; la lista pasa a 3 filas: `AT1, ATP, CAT`; la lista vuelve arriba |
| 2 | Toca `ATP`: `clearFocus()`; `open("ATP")` | `Loading("ATP")` | **Aparece el diálogo «Loading ATP»** con el indicador |
| 3 | En un hilo `IO`: descarga, comprueba tamaño, parsea | `Loading("ATP")` | El indicador gira; el hilo principal está libre |
| 4 | Llega el resultado: `Success(molecule)` | `Loaded(molecule)` | **`SwiftyApp` pasa a la rama `Loaded`: el diálogo desaparece** y se ve la pantalla de la molécula |
| 5 | El usuario pulsa «Atrás» | `Idle` | Vuelve la lista **con `at` escrito y la posición intacta** |

**B. Falla la red (modo avión).**

| Paso | Qué ocurre | `loadState` | Pantalla |
|-----:|---|---|---|
| 1 | Toca `ATP` | `Loading("ATP")` | Diálogo «cargando» |
| 2 | El descargador devuelve `NoConnection` (rápido: no hay conexión) | `Failed("ATP", NoConnection)` | **El diálogo de carga desaparece**; aparece «Could not load ATP / There is no network connection...» con «Retry» y «Close». En el Logcat: `loading ATP failed: NoConnection` |
| 3a | Quita el modo avión y pulsa «Retry» | `Loading("ATP")` → `Loaded` | Vuelve a cargar y se ve la molécula |
| 3b | O pulsa «Close» / «Atrás» | `Idle` | Lista |

**C. Cancelar durante la carga.** Toca un ligando; con el diálogo en pantalla pulsa «Cancel» (o «Atrás»). `onClose` → `cancel()` → el cargador cancela el trabajo y el estado vuelve a `Idle`: el diálogo desaparece.
Si el resultado llegara **después**, se ignora (garantía probada del cargador, doc 06b, sección 8).

**D. Girar el móvil durante la carga.** La actividad se destruye y se recrea. El `ViewModel` **sigue vivo**, con su cargador y su `Job`. La composición nueva lee `loadState`: si sigue en `Loading`, **dibuja el diálogo de nuevo**
(el estado manda, no un recuerdo de la pantalla vieja). Cuando acabe, la pantalla se actualiza igual. El texto del buscador vuelve desde el `Bundle`.

**E. Mandar la app a segundo plano durante la carga.** `collectAsStateWithLifecycle` **deja de escuchar** mientras la app no es visible, pero la descarga **continúa** en el `ViewModel`. Al volver, la pantalla lee el estado
actual (puede que ya sea `Loaded` o `Failed`). *(Comportamiento por documentación de la librería; no comprobado en el A33.)*

**F. Dos toques seguidos en ligandos distintos.** `open(A)` y enseguida `open(B)`: el cargador cancela A y carga B; **nunca se ve el resultado de A** (probado en la JVM). En la práctica el diálogo modal aparece casi enseguida y bloquea el segundo toque.

---

## 13. Cómo probarlo en el móvil (incluidos los cuatro errores)

**Primero lo básico**, con red normal:
1. Abre la app. Debe verse **«1243 ligands»** y la lista. Escribe `hem` → las filas con `HEM`. Escribe `HEM` → lo mismo.
2. Toca `001`. Debe verse el diálogo y, después, **«Ligand 001» con 87 atoms, 90 bonds**.
3. Pulsa «Atrás»: la lista vuelve **con tu búsqueda**. Gira el móvil: nada se pierde.

**Los cuatro errores del enunciado.** Dos se prueban de verdad; dos hay que **provocar**:

| Error | Cómo provocarlo | Qué debe verse | Honestidad |
|-------|-----------------|----------------|------------|
| **Sin red** | Modo avión, o Wi-Fi y datos apagados; tocar un ligando | «There is no network connection...» con Retry | Es la prueba real. Cómo reacciona `HttpURLConnection` de Android a «sin red» **no se ha comprobado** (puede salir como `NoConnection` o como `NetworkError`; ambos tienen mensaje y no cierran la app) |
| **404** | Añade **temporalmente** un id inventado (p. ej. `ZZZZ`) a `ligands.txt` y tócalo | «The ligand "ZZZZ" does not exist... (error 404)», **sin** Retry | **No he podido comprobar** qué contesta RCSB a un id que no existe (puede ser 404 u otra cosa). Si sale otro código, saldrá `ServerError` y el mensaje será el de servidor: ahí vemos el dato real |
| **Fichero corrupto** | Con el arnés de abajo | «...arrived, but it is damaged...» con Retry | Provocado por código; el camino real (parser) está probado con 88 pruebas |
| **Tiempo agotado** | Con el arnés de abajo, o `https://10.255.255.1/` como `baseUrl` (dirección que no contesta) | «The server took too long...» con Retry (a los ~10 s con la dirección que no contesta) | Con una dirección que no contesta, en algunas redes llega un «inalcanzable» inmediato y saldrá «sin red» en vez de «tiempo»: no es un fallo |

**Arnés temporal para ver cada diálogo (NO se sube al repositorio).** En `LigandsViewModelFactory.create`, cambia la línea `source = ...` por:

```kotlin
source = { id, isCancelled ->
    when (id) {
        "ATP" -> { Thread.sleep(4000); LigandLoadResult.Failure(LoadError.Timeout) }
        "ADP" -> LigandLoadResult.Failure(LoadError.NotFound)
        "GTP" -> LigandLoadResult.Failure(LoadError.Corrupt(CifFailure.NoAtoms))
        "NAD" -> { Thread.sleep(4000); LigandLoadResult.Failure(LoadError.NoConnection) }
        else -> repository.load(id, isCancelled)
    }
},
```
(con los `import` de `LigandLoadResult`, `LoadError` y `CifFailure`). Con los 4 segundos de `sleep` **se ve el indicador**, y se pueden probar **Cancelar**, **Atrás** y **girar** durante la carga. Al terminar, **deshaz el cambio**.

**Qué mirar en el Logcat** (filtro `SwiftyProteins`): una línea `loading XXX failed: ...` por cada error, con el motivo técnico.

---

## 14. Trampas y límites conocidos

1. **Cancelar no interrumpe la conexión en curso.** Si el usuario cancela mientras el sistema aún está **conectando** (hasta 10 s), el hilo `IO` sigue esperando; **el resultado se descarta** y la pantalla vuelve al instante, pero el hilo no se libera hasta que termine. No hay riesgo para el usuario; es un límite de `HttpURLConnection` (doc 06, sección 7).
2. **Leer los *assets* en el hilo principal.** Vale con 5 KB. Con un fichero grande causaría un tirón; habría que leerlo en `Dispatchers.IO`.
3. **El proceso se mata en segundo plano.** Android puede matar la app mientras no se ve. Al volver, el `ViewModel` **ya no está**: la app arranca en la lista, con el **texto del buscador restaurado** (`rememberSaveable`) pero sin molécula. Es aceptable.
4. **La molécula vive en memoria mientras el estado es `Loaded`.** Al volver a la lista pasa a `Idle` y se libera.
5. **Una `AlertDialog` en Compose es una ventana aparte.** Sale por encima de todo, también del teclado.
6. **Teclado y márgenes (*insets*).** Según mi comprensión (no comprobada en el A33), con `enableEdgeToEdge` y una versión reciente de Android el `windowSoftInputMode="adjustResize"` del manifiesto **no basta** para que el contenido se encoja; por eso el `imePadding()` explícito.
   Comprobar en el móvil que la lista **no queda tapada** por el teclado y que no hay un hueco de más.
7. **Rotación con el diálogo de error abierto.** El error vive en el `ViewModel`, no en el diálogo: tras girar, **el diálogo reaparece**. Es lo deseado.
8. **`Log.w` en una función de composición.** Solo está dentro de un `LaunchedEffect`, que se ejecuta **una vez por estado nuevo**. Fuera, se anotaría en cada recomposición.
9. **Tema oscuro.** Los colores salen del tema de Material 3 (ya existente); no se han fijado colores a mano, así que debe funcionar en claro y oscuro. No se ha mirado.
10. **Idioma.** Los textos están en inglés fijo; en un móvil en español **no se traducen** (D20).
11. **`MoleculeScene` sin uso.** Sigue compilando; no se llama. La limpieza llega con la escena 3D.

---

## 15. Qué está verificado y qué no

**Verificado (hecho):**
- **162 pruebas** pasan en la JVM desde una compilación limpia: 88 (parser) + 42 (red) + 25 (catálogo y cargador) + **7 nuevas** (textos y «Reintentar»).
- **7 averías** metidas a propósito en `LoadErrorText.kt`: **las 7 las detectan las pruebas** (sección 17).
- Los **tipos** de todo el código de esta etapa son coherentes entre sí: se compiló con `kotlinc` contra unas piezas de relleno de Android/Compose escritas de memoria. Eso encontró un fallo de **mi relleno**, no del código, y demuestra
  que `when` exhaustivos, nulabilidad, referencias a función y parámetros **de nuestras clases** cuadran.

**NO verificado (honesto):**
- **Actualización:** el código **se compiló y se ejecutó en el móvil** (lista, búsqueda, selección y carga funcionaron). Lo que sigue sin probarse: los **cuatro diálogos de error** (sección 13), el giro con el diálogo abierto, el modo oscuro, los márgenes con teclado. Lo que sigue **escrito antes de esa prueba**, sin cambios:
- **Nada de esto se había compilado con el SDK de Android ni con Compose real al redactar.** Las firmas (`AlertDialog`, `OutlinedTextField`, `LazyColumn`, `Scaffold`, `consumeWindowInsets`, `imePadding`, `collectAsStateWithLifecycle`, `viewModels`,
  `DialogProperties`) están **escritas de memoria**. Si alguna ha cambiado en la versión de Compose del proyecto, el compilador lo dirá: **pégame el error y se corrige** (es lo esperado, no un fallo del enfoque).
- Que las **versiones** de la sección 4 existan y encajen con AGP 9.4.1 / Kotlin 2.4.21.
- El comportamiento en el móvil: márgenes con teclado, tema oscuro, rotación, segundo plano, rendimiento en el A33.
- Qué contesta **RCSB** a un id inexistente y cómo reacciona `HttpURLConnection` de Android a «sin red»; el cambio de lista corta a larga.
- El `ViewModel` y la fábrica **no tienen pruebas** (necesitan Android o un entorno de corrutinas de prueba, que aquí no hay). Por eso son lo más delgado posible (D18).

---

## 16. Alternativas

| Tema | Lo que hacemos | Alternativa | Por qué no (todavía) |
|------|---------------|-------------|----------------------|
| Navegación | El `LoadState` decide la pantalla (`if`) | **Navigation Compose** (librería oficial, con pila de pantallas) | Hoy hay **dos** pantallas. Cuando lleguen inicio de sesión, ajustes y la escena, **habrá que reconsiderarlo** (D19) |
| Indicador de carga | Diálogo modal | Capa sobre la lista; pantalla completa de carga | Capa: hay que bloquear toques y «Atrás» a mano. Pantalla completa: más código sin ganar nada aquí |
| Mostrar errores | `AlertDialog` | `Snackbar` (aviso breve abajo) | Un `Snackbar` desaparece solo: el usuario puede no verlo, y los errores deben **verse** |
| Fábrica del `ViewModel` | Clase `Factory` a mano | `viewModelFactory { initializer { } }`; inyección de dependencias (**Hilt**) | Hilt añade una librería y generación de código para dos objetos |
| Contexto | `applicationContext` en la fábrica | `AndroidViewModel(application)` | Funciona, pero ata el `ViewModel` a Android; con lambdas queda casi puro |
| Texto buscado | `rememberSaveable` en la raíz | Guardarlo en el `ViewModel` o en `SavedStateHandle` | `rememberSaveable` hace lo mismo con menos código; el `ViewModel` no lo guarda tras matar el proceso |
| Observar el estado | `collectAsStateWithLifecycle()` | `collectAsState()` | Sigue escuchando con la app en segundo plano |
| Cuadro de búsqueda | `OutlinedTextField` | `SearchBar` de Material 3 | Más complejo (sugerencias, expansión) y se ha movido entre versiones |
| Textos | Inglés en el código (D20) | `strings.xml` (traducible) | Más ceremonia; se puede migrar luego |
| Filtrado | En la pantalla, con `remember` | Filtro en el `ViewModel` con `Flow` (`debounce`) | 57 µs no necesita `debounce`; cuando haya cientos de milisegundos, sí |

---

## 17. Pruebas y mutación de `LoadErrorText`

7 pruebas, sobre un ejemplo de **cada** tipo de `LoadError`:

| Prueba | Qué demuestra |
|--------|---------------|
| `everyErrorHasANonBlankMessage` | Ninguno de los diez errores produce un texto vacío |
| `theFourErrorsOfTheTaskReadDifferently` | Sin red, 404, corrupto y tiempo agotado dan **cuatro textos distintos** (si dos coincidieran, el usuario no sabría qué pasa) |
| `notFoundNamesTheLigandAndTheCode` | El 404 nombra el ligando y el código |
| `serverErrorShowsTheStatusCode` | `ServerError(503)` muestra el 503 |
| `technicalDetailsNeverReachTheUser` | Ni `SSL...` ni la descripción de `Unexpected` salen en pantalla |
| `retryIsOfferedOnlyWhenRepeatingCanHelp` | `NotFound`, `InvalidId`, `TooLarge` sin reintento; el resto, con él |
| `aCorruptFileCanBeRetriedBecauseALoginPageOfAWifiLooksLikeOne` | `Corrupt` sí se puede reintentar (sección 5) |

**Mutación** (se estropea el código a propósito y se comprueba que alguna prueba **falla**; si ninguna falla, la prueba no vale):

| # | Avería | Resultado | La detecta |
|---|--------|-----------|------------|
| U1 | `NotFound` se puede reintentar | Muere | `retryIsOfferedOnlyWhenRepeatingCanHelp` |
| U2 | El mensaje de `Timeout` copia el de «sin red» | Muere | `theFourErrorsOfTheTaskReadDifferently` |
| U3 | El mensaje de 404 no nombra el ligando | Muere | `notFoundNamesTheLigandAndTheCode` |
| U4 | `Corrupt` no se puede reintentar | Muere | `aCorruptFileCanBeRetriedBecauseALoginPageOfAWifiLooksLikeOne` |
| U5 | El mensaje de `NetworkError` añade el motivo técnico | Muere | `technicalDetailsNeverReachTheUser` |
| U6 | El mensaje de `ServerError` pierde el código | Muere | `serverErrorShowsTheStatusCode` |
| U7 | El mensaje de `Unexpected` añade la descripción | Muere | `technicalDetailsNeverReachTheUser` |

**Resultado: las 7 mueren.** **No hay mutación ni pruebas** del `ViewModel`, la fábrica ni las pantallas: necesitan Android.

*(Una primera versión de la prueba de cobertura usaba `sealedSubclasses`, que en la JVM exige la librería `kotlin-reflect`; se quitó porque **el compilador ya garantiza** que cada caso tiene frase, al no haber `else`.)*

---

## 18. Decisiones

| # | Decisión | Estado | Motivo |
|---|----------|--------|--------|
| D19 | La pantalla se decide con un `if` sobre el `LoadState` (**sin librería de navegación**); el texto buscado y la posición de la lista viven en `SwiftyApp` | Provisional | Hay dos pantallas y el estado de carga ya es la verdad; una librería de navegación no aporta aún. Se revisará al llegar inicio de sesión y escena 3D |
| D20 | Los textos de la interfaz van **en inglés, escritos en el código** (no en `strings.xml`) | Provisional | Menos ceremonia ahora; la tarea está en inglés. Cambiarlo después es mecánico. **Supuesto no confirmado:** que la app deba estar en inglés |

---

## 19. Qué recordar

- **Tres sitios para el estado:** `remember` (se pierde al girar), `rememberSaveable` (sobrevive; para pequeños), `ViewModel` (sobrevive al giro; no a que maten el proceso).
- **El `ViewModel` es una envoltura**: las reglas viven en `LigandLoader` y `LigandCatalog`, que **sí** tienen pruebas.
- **El indicador es el diálogo del estado `Loading`**: no hay un booleano que olvidar apagar.
- **`when` sin `else` sobre una `sealed interface`**: el compilador obliga a tratar cada caso; un error nuevo **no compila** hasta tener frase.
- **Elevar el estado**: la lista no guarda el texto; lo guarda quien siempre está en pantalla.
- **Los errores técnicos van al Logcat; el usuario lee una frase.** Y siempre hay una salida (Close / OK / Atrás).
- **Corrupto sí se reintenta** (portales de Wi-Fi); no encontrado, no.
- **Esto no está compilado contra Android**: la primera compilación real puede traer errores de firma. Se corrigen con el mensaje del compilador.
