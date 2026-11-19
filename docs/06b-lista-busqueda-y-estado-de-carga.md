# 06b · Lista, búsqueda y estado de carga (diseño y código línea a línea)

> Estado: **LÓGICA ESCRITA Y PROBADA EN LA JVM** (el catálogo de ligandos con su búsqueda, el estado de carga y el cargador que lo gobierna).
> **Pendiente**: todo lo que toca Android (leer `ligands.txt` de los *assets*, el `ViewModel`, las pantallas, la navegación y las dependencias nuevas);
> **ese código no se ha compilado**, porque necesita el SDK de Android (sección 12).
> Redactado el 19/11/2026. Viene después de [`06-red-y-carga-de-ligandos.md`](06-red-y-carga-de-ligandos.md) y [`06a-codigo-red.md`](06a-codigo-red.md): usa `LigandRepository` y `LigandLoadResult`.
> Este documento reúne **teoría, motivos y código línea a línea** porque las tres piezas son pequeñas; las citas de código se generan **desde los ficheros reales**.

## Índice

1. [Qué pide el enunciado](#1-qué-pide-el-enunciado)
2. [Mapa: qué fichero hace qué y cómo encaja](#2-mapa-qué-fichero-hace-qué-y-cómo-encaja)
3. [Conceptos nuevos: corrutinas, `StateFlow` y compañía](#3-conceptos-nuevos-corrutinas-stateflow-y-compañía)
4. [`LigandCatalog.kt`: la lista y la búsqueda](#4-ligandcatalogkt-la-lista-y-la-búsqueda)
5. [La búsqueda en tiempo real: qué significa y cuánto cuesta](#5-la-búsqueda-en-tiempo-real-qué-significa-y-cuánto-cuesta)
6. [`LoadState.kt`: una pantalla, un estado](#6-loadstatekt-una-pantalla-un-estado)
7. [`LigandLoader.kt`: el cargador](#7-ligandloaderkt-el-cargador)
8. [Simulaciones del cargador](#8-simulaciones-del-cargador)
9. [Por qué el estado no vive dentro del `ViewModel`](#9-por-qué-el-estado-no-vive-dentro-del-viewmodel)
10. [Las pruebas, una a una](#10-las-pruebas-una-a-una)
11. [Qué demuestran las pruebas: mutación](#11-qué-demuestran-las-pruebas-mutación)
12. [Qué falta y cómo se enchufará a Android](#12-qué-falta-y-cómo-se-enchufará-a-android)
13. [Trampas y límites conocidos](#13-trampas-y-límites-conocidos)
14. [Qué está verificado y qué no](#14-qué-está-verificado-y-qué-no)
15. [Decisiones](#15-decisiones)
16. [Qué recordar](#16-qué-recordar)

---

## 1. Qué pide el enunciado

De la tarea (documento 01):

1. Mostrar una **lista de ligandos** leída de `ligands.txt` (1243 identificadores, uno por línea).
2. **Búsqueda en tiempo real**, **sin distinguir mayúsculas de minúsculas**: la lista se filtra mientras se escribe.
3. Al elegir un ligando se descarga (documento 06), con un **indicador de carga que siempre desaparece**: con éxito, con cualquiera de los errores, al cancelar.
4. Un cierre inesperado en la evaluación entre compañeros es un suspenso.

Esta etapa cubre las **reglas** (qué lista sale para un texto escrito; en qué estado está la carga en cada momento) y deja para la siguiente
el **pegamento con Android**. Se hace así porque las reglas se pueden **demostrar con pruebas** en el ordenador, y el pegamento no.

---

## 2. Mapa: qué fichero hace qué y cómo encaja

| Fichero | Ruta | Líneas | Para qué |
|---------|------|-------:|----------|
| `LigandCatalog.kt` | `app/src/main/java/dev/knowledge82/swiftyproteins/catalog/` | 47 | Texto de `ligands.txt` → lista de ids; `search(texto)` filtra. |
| `LoadState.kt` | `.../loading/` | 23 | Los cuatro estados posibles de la carga: `Idle`, `Loading`, `Loaded`, `Failed`. |
| `LigandLoader.kt` | `.../loading/` | 71 | Lanza la carga en segundo plano, la cancela y publica el estado. |
| `ligands.txt` | `app/src/main/assets/` | 1243 | La lista que da la tarea, **sin modificar**. |
| `LigandCatalogTest.kt` | `app/src/test/java/dev/knowledge82/swiftyproteins/catalog/` | 148 | 15 pruebas del catálogo, incluidas las del fichero real. |
| `LigandLoaderTest.kt` | `.../loading/` (en `src/test`) | 220 | 10 pruebas del cargador con una fuente falsa. |

Cómo encaja con lo anterior (cada flecha es «llama a» o «publica en»):

```
 ligands.txt (assets)
        │  (lo lee la capa de Android: pendiente)
        ▼
 LigandCatalog.fromText(texto) ──► catalog.search("at") ──► ["AT1","ATP","CAT"]   ◄── lo que se escribe en el buscador
                                                                      │
                                              el usuario toca "ATP"   ▼
                                                      LigandLoader.load("ATP")
                                                              │   (hilo de fondo)
                                                              ▼
                                      LigandRepository.load ──► red (06) ──► parser (03) ──► Molecule
                                                              │
                                                              ▼
                                  state: StateFlow<LoadState>   Idle → Loading("ATP") → Loaded(molécula) | Failed(...)
                                                              │
                                                              ▼
                                                    la pantalla observa y dibuja
```

Dependencias: `LigandLoader` → `LigandLoadResult`, `LoadError` (paquete `net`) y `Molecule`; **no** conoce `LigandRepository` (recibe una función, sección 7.1);
`LigandCatalog` no depende de nada del proyecto. **Nada** de estas tres piezas usa `android.*`.

---

## 3. Conceptos nuevos: corrutinas, `StateFlow` y compañía

**Hilo (*thread*).** Una línea de ejecución del programa. Android tiene un hilo **principal** (el que dibuja y recibe los toques): si se bloquea 5 segundos,
la pantalla se queda congelada. El trabajo lento (red, parseo) tiene que ir **en otro hilo**.

**Corrutina.** Una forma de escribir trabajo asíncrono **como si fuera código normal, de arriba abajo**, sin *callbacks* anidados. Una corrutina puede **suspenderse**
(esperar sin bloquear el hilo) y **reanudarse** después, incluso en otro hilo. Una corrutina es mucho más barata que un hilo: puede haber miles. No son magia del lenguaje
sino una **librería** (`kotlinx.coroutines`) más ayuda del compilador.

**`CoroutineScope` (ámbito).** Quien **posee** a las corrutinas: si el ámbito se cancela, todas sus corrutinas se cancelan. En Android, cada `ViewModel` trae uno (`viewModelScope`).
Esto se llama *concurrencia estructurada*: no hay corrutinas «sueltas» que sigan vivas sin dueño.

**`launch { ... }`.** Arranca una corrutina nueva en un ámbito y devuelve un **`Job`**: una *asa* para preguntarle si sigue viva o para cancelarla (`job.cancel()`).

**`withContext(dispatcher) { ... }`.** «Ejecuta este bloque **en otro hilo** (el que indique el *dispatcher*), espera su resultado y sigue». Es una función de suspensión: mientras el
bloque corre, el hilo de origen queda libre.

**Dispatcher (despachador).** El que decide **en qué hilo(s)** corre una corrutina. Los habituales: `Dispatchers.Main` (hilo principal; solo en Android), `Dispatchers.IO` (un grupo de hilos
pensado para trabajo que **espera**, como red y disco) y `Dispatchers.Default` (para cálculo). *(Recordado de la documentación; no se ha vuelto a leer en esta sesión.)*

**Cancelación cooperativa.** `job.cancel()` **no mata** un hilo: marca el trabajo como cancelado y las funciones de suspensión **lo notan al ejecutarse y lanzan `CancellationException`**.
Un código **bloqueante** (como nuestra descarga, que espera en `read`) **no se suspende**, así que nadie le avisa: por eso el descargador recibe la función `isCancelled` y la
consulta él mismo en cada vuelta (documento 06, sección 7). El cargador conecta las dos ideas: `isCancelled = { !isActive }`.

**`CancellationException` hay que dejarla pasar.** Es la forma en que una corrutina **sabe** que fue cancelada. Si un `catch (e: Exception)` la captura y la trata como un error normal,
la corrutina cancelada **sigue ejecutándose como si nada** (en el cargador, publicaría un fallo falso). Por eso hay una rama `catch (e: CancellationException) { throw e }` **antes** de la general.

**`StateFlow<T>`.** Un «contenedor observable» que **siempre tiene un valor actual** (`.value`) y avisa a quien lo observe cuando cambia. Dos detalles: **solo avisa si el valor nuevo es
distinto** (por `equals`), y quien empieza a observar recibe **inmediatamente** el valor actual. Es lo que hace que una pantalla de Compose «se vuelva a dibujar» sola. `MutableStateFlow` es la versión
en que se puede escribir; se publica al exterior como `StateFlow` (de solo lectura) con `asStateFlow()`.

**`typealias`.** Un **apodo** para un tipo. `typealias LigandSource = (id: String, isCancelled: () -> Boolean) -> LigandLoadResult` significa: «una `LigandSource` es **cualquier función** que reciba
un id y una función de cancelación y devuelva un `LigandLoadResult`». No crea un tipo nuevo.

**Constructor privado + función de fábrica.** `class LigandCatalog private constructor(...)` impide `LigandCatalog(lista)` desde fuera; la única puerta es `LigandCatalog.fromText(texto)` (en el
`companion object`). Así la lista **siempre** pasa por la limpieza (espacios, vacíos, repetidos).

**Desestructuración.** `val (a, b) = par` saca los dos elementos de un `Pair`. **`partition { condición }`** divide una lista en un `Pair` de listas: las que cumplen la condición y las que no.

**`lineSequence()`.** Recorre un texto línea a línea **sin construir antes una lista con todas** (es una *secuencia*: se calcula elemento a elemento). Separa por `\n` y también por `\r\n`.

---

## 4. `LigandCatalog.kt`: la lista y la búsqueda

```kotlin
// LigandCatalog.kt · líneas 3–12
 3  /**
 4   * The list of ligands the app offers (the content of `ligands.txt`) and the search over it.
 5   *
 6   * Pure Kotlin: it receives the TEXT of the file, so it knows nothing about Android, assets or screens.
 7   * Reading the file from the assets is somebody else's job; this class only turns text into a list and filters it.
 8   */
 9  class LigandCatalog private constructor(
10      /** Every ligand id, in the order of the file, without blanks or repetitions. */
11      val ids: List<String>,
12  ) {
```

- **Línea 9, `private constructor`**: nadie de fuera puede crear un catálogo con una lista cualquiera. Línea 11: `val ids` es **pública y de solo lectura** (lista en el orden del fichero).
  El comentario de las líneas 3–8 deja escrita la decisión importante: el catálogo recibe **texto**, no sabe de *assets* ni de pantallas. Por eso se prueba con una cadena escrita en la prueba.

```kotlin
// LigandCatalog.kt · líneas 35–47
35      companion object {
36
37          /** Builds the catalog from the text of `ligands.txt`: one id per line. */
38          fun fromText(text: String): LigandCatalog {
39              val ids = text.lineSequence()      // splits on \n and also on \r\n
40                  .map { it.trim() }
41                  .filter { it.isNotEmpty() }
42                  .distinct()                    // keeps the first appearance of each id
43                  .toList()
44              return LigandCatalog(ids)
45          }
46      }
47  }
```

- **Línea 38, `fromText(text)`**: la única puerta. Cuatro pasos encadenados sobre una secuencia:
  - **39, `lineSequence()`**: corta por líneas (también las de estilo Windows, `\r\n`).
  - **40, `trim()`**: quita espacios y tabuladores de los extremos. Un `\r` suelto al final de una línea también cae aquí.
  - **41, `isNotEmpty()`**: tira las líneas vacías (hay una final si el fichero termina en salto de línea).
  - **42, `distinct()`**: elimina repetidos **conservando la primera aparición**. El fichero real no tiene ninguno (se comprobó), pero la lista **no debe mostrar un ligando dos veces** si algún día los hubiera.
  - **43, `toList()`**: convierte la secuencia en lista (hasta aquí no se había calculado nada).
- **No se valida el formato** de cada id: el catálogo no sabe qué ids son «legales». La validación es del repositorio (`[A-Z0-9]{1,5}`). La prueba `everyIdOfTheRealFileIsAcceptedByTheRepository` comprueba que **ambos
  están de acuerdo** con el fichero real (los 1243).

```kotlin
// LigandCatalog.kt · líneas 14–33
14  /**
15   * The ids that match what the user typed, for showing the list while he types.
16   *
17   * - Case does not matter: "atp", "ATP" and "aTp" are the same search.
18   * - Spaces at the ends are ignored.
19   * - An id matches if it CONTAINS the text; the ones that START with it come first, then the rest.
20   *   Inside each group the order of the file is kept.
21   * - Empty text (or only spaces) matches everything.
22   */
23  fun search(query: String): List<String> {
24      val wanted = query.trim()
25      if (wanted.isEmpty()) return ids
26
27      // ignoreCase compares letter by letter without depending on the language of the phone
28      // (with "i".uppercase() in Turkish you would get "İ", and the search would break).
29      val (startingWith, containing) = ids
30          .filter { it.contains(wanted, ignoreCase = true) }
31          .partition { it.startsWith(wanted, ignoreCase = true) }
32      return startingWith + containing
33  }
```

- **Línea 24, `query.trim()`**: se ignoran los espacios de los extremos (`"  atp "` busca `atp`); los de **dentro** cuentan (`"A TP"` no encuentra nada).
- **Línea 25**: texto vacío = **todo** (la lista completa al abrir la pantalla). Devuelve `ids` tal cual, sin copiarla.
- **Línea 29–31**: **filtrar y repartir**.
  - `filter { it.contains(wanted, ignoreCase = true) }` se queda con los ids que **contienen** el texto, sin distinguir mayúsculas. `ignoreCase = true` compara letra a letra y **no depende del idioma** del móvil.
  - `partition { it.startsWith(wanted, ignoreCase = true) }` reparte esos en dos listas: los que **empiezan** por el texto y los que solo lo **contienen**.
- **Línea 32, `startingWith + containing`**: primero los que empiezan por el texto, después el resto; dentro de cada grupo se conserva el **orden del fichero** (`filter` y `partition` no reordenan).
- **Por qué `ignoreCase` y no `uppercase()`:** `"i".uppercase()` en un móvil configurado en **turco** da `"İ"` (con punto), no `"I"`; una búsqueda escrita con `uppercase()` fallaría para `zip`. Hay una prueba
  que cambia el idioma por defecto a turco y comprueba que se sigue encontrando (`theSearchDoesNotDependOnTheLanguageOfThePhone`). *(No se ha comprobado que el Kotlin/Android actual reproduzca exactamente el
  caso turco de `uppercase()`; la prueba demuestra que **nuestra** búsqueda no depende del idioma.)*

**Simulación con el fichero real** (salida real del código):

| Texto escrito | Resultados | Primeros |
|---------------|-----------:|----------|
| `` (vacío) | 1243 | `001, 011, 031, 041, 04G, 083, 0AF, 0DS` |
| `a` | 196 | `A1E, A23, A2E, A2G, A2M, A37, A3P, A48` |
| `at` | 3 | `AT1, ATP, CAT` |
| `atp` | 1 | `ATP` |
| `ZN` | 1 | `ZN` |
| ` hem ` | 1 | `HEM` |
| `0` | 64 | `001, 011, 031, 041, 04G, 083, 0AF, 0DS` |
| `01` | 3 | `011, 001, P01` |
| `qqq` | 0 | (lista vacía) |

Dos ejemplos para entender el orden: con `at` salen `AT1`, `ATP` (empiezan por `AT`) y `CAT` (solo lo contiene), que en este fichero ya estaban en ese orden por casualidad (está ordenado
alfabéticamente). Con `01` **sí** se nota: en el fichero `001` va antes que `011`, pero `011` **empieza** por `01` y `001` solo lo **contiene**, así que sale primero `011`; después `001`, y por último `P01`
(que también solo lo contiene y va después de `001` en el fichero).

---

## 5. La búsqueda en tiempo real: qué significa y cuánto cuesta

«En tiempo real» significa que **cada pulsación** de tecla recalcula la lista. La pantalla guarda el texto escrito; la lista mostrada es `catalog.search(texto)`. No hay botón «buscar».

**Coste.** `search` recorre los 1243 ids en cada pulsación. Medido en la JVM del entorno de análisis: **unos 57 microsegundos** de media por búsqueda de dos letras (1000 repeticiones tras calentar). Un móvil
será más lento, pero la distancia hasta algo que se note (16 000 microsegundos por fotograma a 60 Hz) es de **más de dos órdenes de magnitud**. **Sin medir en el A33.** El peor caso de tamaño del resultado es una sola
letra (212 resultados como máximo con este fichero): la lista tiene que ser **perezosa** (`LazyColumn` de Compose, que solo dibuja lo visible), no un `Column` con 1243 elementos.

**Alternativas** que se descartaron: un **índice** (mapa por prefijos) o una **estructura de árbol** (*trie*) aceleran la búsqueda por prefijo pero no la de «contiene», y para 1243 elementos son complicación sin beneficio;
la **búsqueda difusa** (tolerar erratas) no la pide la tarea. **Debouncing** (esperar unos milisegundos tras la última tecla antes de recalcular) sirve cuando cada búsqueda es cara o va a red; aquí **no se usa**.

---

## 6. `LoadState.kt`: una pantalla, un estado

```kotlin
// LoadState.kt · líneas 6–23
 6  /**
 7   * What the screen shows while a ligand is being loaded. Exactly ONE of these at any moment, so the screen can
 8   * never show a spinner and an error at the same time: the compiler does not even allow that state to exist.
 9   */
10  sealed interface LoadState {
11
12      /** Nothing is being loaded and nothing is ready (the start, or the user left). */
13      data object Idle : LoadState
14
15      /** The download is in progress: show the loading indicator. It is the ONLY state that shows it. */
16      data class Loading(val id: String) : LoadState
17
18      /** The molecule is ready to be drawn. */
19      data class Loaded(val molecule: Molecule) : LoadState
20
21      /** It failed; [error] says why (the screen turns it into one of the four messages). */
22      data class Failed(val id: String, val error: LoadError) : LoadState
23  }
```

Cuatro estados, **uno solo a la vez**:

| Estado | Qué dibuja la pantalla | Cómo se sale de él |
|--------|------------------------|--------------------|
| `Idle` | La lista (no se está cargando nada) | `load(id)` |
| `Loading(id)` | El **indicador de carga**; **es el único estado que lo muestra** | El trabajo termina (a `Loaded`, `Failed` o `Idle`) o `load`/`cancel` |
| `Loaded(molecule)` | La escena 3D con la molécula | `load(otro)` o `cancel()` |
| `Failed(id, error)` | El mensaje de error (uno de los cuatro) y un botón de reintentar | `load(id)` (reintentar) o `cancel()` |

Diagrama de transiciones (cada flecha es una llamada o un fin de trabajo):

```
              load(id)                       el trabajo termina bien
   Idle  ───────────────►  Loading(id)  ───────────────────────────►  Loaded(molécula)
    ▲  ▲                      │  │  ▲                                        │
    │  │   cancel()           │  │  │   load(otro)  (cancela el anterior)    │ load(otro) / cancel()
    │  └──────────────────────┘  │  └────────────────────────────────────────┘
    │                            │ el trabajo falla (cualquier error, incluso una excepción)
    │      cancel() / fin        ▼
    └─────────────────────  Failed(id, error)   ── load(id) = reintentar ──► Loading(id)
```

**Por qué una sola `sealed interface` en vez de varias variables** (`cargando: Boolean`, `error: String?`, `molécula: Molecule?`): con tres variables caben **combinaciones imposibles** (`cargando = true` **y** `error != null`)
y alguien, algún día, dibujará las dos a la vez. Con la `sealed interface` esa combinación **no se puede escribir** y el `when` de la pantalla obliga a tratar los cuatro casos. Es la misma idea que `FetchResult` y `LoadError` (D16).

`Loading` lleva el `id` para que la pantalla pueda decir «Cargando ATP…» y para que `Failed` sepa **qué** reintentar.

---

## 7. `LigandLoader.kt`: el cargador

### 7.1 La fuente y la clase

```kotlin
// LigandLoader.kt · líneas 16–39
16  /** Whatever can load a ligand by id. The real one is [dev.knowledge82.swiftyproteins.net.LigandRepository]. */
17  typealias LigandSource = (id: String, isCancelled: () -> Boolean) -> LigandLoadResult
18
19  /**
20   * Runs a load in the background and publishes its progress as a [LoadState].
21   *
22   * The guarantee this class exists for: **after [load] the state is [LoadState.Loading] until the work ends, and
23   * then it is ALWAYS something else** (loaded, failed or idle), whatever happened: success, any of the errors, a
24   * bug that throws, or a cancellation. The loading indicator can therefore never stay on the screen forever.
25   *
26   * Threads: [load] and [cancel] are meant to be called from ONE thread (the main thread). The scope must run its
27   * continuations on that same thread (`viewModelScope` does). The slow work runs on [workDispatcher].
28   */
29  class LigandLoader(
30      private val source: LigandSource,
31      private val scope: CoroutineScope,
32      private val workDispatcher: CoroutineDispatcher,
33  ) {
34      private val mutableState = MutableStateFlow<LoadState>(LoadState.Idle)
35
36      /** What the screen observes. */
37      val state: StateFlow<LoadState> = mutableState.asStateFlow()
38
39      private var job: Job? = null
```

- **Línea 17, `typealias LigandSource`**: el cargador **no recibe un `LigandRepository`**, recibe **una función** con su misma forma. En la aplicación real será `{ id, cancelado -> repositorio.load(id, cancelado) }`; en las
  pruebas, una función que devuelve lo que se le diga o que se queda esperando. **No hizo falta tocar `LigandRepository`** ni crear una interfaz nueva.
- **Líneas 29–33, constructor con tres piezas**:
  - `source`: la función que **hace** la carga (y bloquea el hilo que la llama).
  - `scope`: el ámbito de las corrutinas. En la app será `viewModelScope`; en las pruebas, un ámbito con un **único hilo** que hace de «hilo principal».
  - `workDispatcher`: dónde corre el trabajo lento (`Dispatchers.IO` en la app).
  Las **tres se inyectan** (se reciben de fuera) para poder sustituirlas en las pruebas; nada está «cableado» dentro.
- **Líneas 34–37, `mutableState` y `state`**: dentro se puede escribir; hacia fuera solo se puede leer (`asStateFlow()`). Es el patrón estándar para que **solo el cargador** cambie el estado.
- **Línea 39, `job`**: la asa de la carga en curso (o `null`). Sirve para cancelarla.
- El comentario de las líneas 19–28 deja escrita la **garantía** y la regla de hilos (sección 13).

### 7.2 `load`

```kotlin
// LigandLoader.kt · líneas 41–63
41  /** Starts loading [id]. If another load is running it is cancelled first and its result is thrown away. */
42  fun load(id: String) {
43      job?.cancel()
44      mutableState.value = LoadState.Loading(id)
45
46      job = scope.launch {
47          val result = try {
48              // The lambda { !isActive } is what the network code asks while it reads: false once this job
49              // has been cancelled. Inside withContext, `isActive` is the one of THIS block.
50              withContext(workDispatcher) { source(id) { !isActive } }
51          } catch (e: CancellationException) {
52              throw e   // cancelled: do not publish anything, somebody else already decided the state
53          } catch (e: Exception) {
54              LigandLoadResult.Failure(LoadError.Unexpected(e.toString()))
55          }
56
57          mutableState.value = when (result) {
58              is LigandLoadResult.Success -> LoadState.Loaded(result.molecule)
59              is LigandLoadResult.Failure -> LoadState.Failed(id, result.error)
60              is LigandLoadResult.Cancelled -> LoadState.Idle
61          }
62      }
63  }
```

Paso a paso:

1. **Línea 43, `job?.cancel()`**: si hay una carga en curso, se **cancela**. El `?.` evita fallar si `job` es `null`. Sin esta línea, dos cargas **convivirían** y la lenta podría **pisar** el resultado de la última (sección 8.2).
2. **Línea 44**: el estado pasa a `Loading(id)` **de inmediato, de forma síncrona**: la pantalla muestra el indicador en el mismo instante en que el usuario toca (no «cuando arranque el trabajo»).
3. **Línea 46, `scope.launch { ... }`**: arranca la corrutina y guarda su asa en `job`.
4. **Líneas 47–55, el `try`**:
   - **50**: `withContext(workDispatcher) { source(id) { !isActive } }`. El bloque se ejecuta **en un hilo de trabajo**; la corrutina de origen se suspende y el hilo principal queda libre. Dentro, se llama a `source` y se le pasa la
     **función de cancelación** `{ !isActive }`: «¿esta corrutina fue cancelada?». `isActive` es una propiedad del `CoroutineScope` del bloque; es `true` mientras no se cancele. **Esta línea es el puente** entre la
     cancelación de corrutinas y el `isCancelled` del descargador.
   - **51–52, `catch (e: CancellationException) { throw e }`**: si nos cancelaron, **no se publica nada** (otro, `load` o `cancel`, ya decidió el estado) y se vuelve a lanzar para que la corrutina termine bien.
     **Debe ir antes** que la rama general, porque `CancellationException` es una `Exception`.
   - **53–54, `catch (e: Exception)`**: cualquier excepción que escape de `source` (un fallo nuestro: la red ya devuelve valores) se convierte en `Failure(Unexpected(...))`. **El indicador no se queda girando.**
     No se captura `Error` (`OutOfMemoryError` y similares): ahí no se puede garantizar nada; para eso están los límites de tamaño (documento 06).
5. **Líneas 57–61, `when` sobre el resultado** (como **expresión**, así que debe cubrir todos los casos): `Success` → `Loaded`, `Failure` → `Failed(id, error)`, `Cancelled` → `Idle`.
   El resultado `Cancelled` (el descargador paró porque se lo pidieron) acaba en `Idle`, **sin mensaje de error**.

**Por qué un resultado tardío no pisa a otro.** `withContext` tiene una propiedad que importa: cuando el trabajo termina **pero la corrutina ya fue cancelada**, **descarta el resultado y lanza `CancellationException`**
en lugar de devolverlo (garantía de cancelación inmediata de las funciones de suspensión; *recordado de la documentación, y comprobado en las pruebas 8.2 y 8.3*). Por eso la línea 57 **no se ejecuta** para una carga que fue
sustituida o cancelada.

### 7.3 `cancel`

```kotlin
// LigandLoader.kt · líneas 65–71
65      /** The user left: stops the work and returns to [LoadState.Idle]. Whatever arrives later is ignored. */
66      fun cancel() {
67          job?.cancel()
68          job = null
69          mutableState.value = LoadState.Idle
70      }
71  }
```

- **Línea 67**: cancela la carga en curso; el hilo de trabajo verá `isCancelled() == true` en su siguiente vuelta del bucle de lectura y soltará la conexión.
- **Línea 68, `job = null`**: ya no hay nada en curso.
- **Línea 69**: el estado vuelve a `Idle` **al instante**, sin esperar a que el trabajo acabe de parar. Lo que llegue después se ignora (sección 8.3).
- Es lo que debe llamar la pantalla (o el `ViewModel` al destruirse) cuando el usuario **sale**: se deja de gastar batería y datos y **no hay indicador huérfano**.

---

## 8. Simulaciones del cargador

Notación: **Main** es el hilo principal; **Work-1**, **Work-2** son hilos del grupo de trabajo. Los tiempos son relativos.

### 8.1 Todo bien

| Instante | Hilo | Qué ocurre | `state` |
|---------:|------|------------|---------|
| 0 | Main | El usuario toca ATP → `load("ATP")`: `job?.cancel()` (no hay), estado `Loading("ATP")`, `launch` | `Loading(ATP)` |
| 0+ | Main | La corrutina llama a `withContext(trabajo)`: se **suspende**; Main queda libre | `Loading(ATP)` |
| 1 | Work-1 | Corre `source("ATP", ...)`: descarga y parsea (cientos de ms) | `Loading(ATP)` |
| 2 | Work-1 | `source` devuelve `Success(molécula)`; `withContext` devuelve el resultado | `Loading(ATP)` |
| 2+ | Main | La corrutina se **reanuda en Main**, línea 57: `Loaded(molécula)` | `Loaded(ATP)` |

### 8.2 El usuario toca otro ligando antes de que acabe el primero (A, luego B)

| Instante | Hilo | Qué ocurre | `state` |
|---------:|------|------------|---------|
| 0 | Main | `load("A")`: estado `Loading(A)`; Work-1 arranca la descarga de A (lenta) | `Loading(A)` |
| 1 | Main | `load("B")`: **`job?.cancel()` cancela A**; estado `Loading(B)`; Work-2 arranca B | `Loading(B)` |
| 2 | Work-1 | `isCancelled()` de A ya vale `true`; A **sigue** hasta terminar su vuelta (el hilo no se mata) | `Loading(B)` |
| 3 | Work-2 | B termina bien → Main: `Loaded(B)` | `Loaded(B)` |
| 4 | Work-1 | A termina por fin con un resultado. `withContext` **lo descarta y lanza `CancellationException`**; la línea 57 no se ejecuta | `Loaded(B)` (**no cambia**) |

Sin la línea 43 (`job?.cancel()`), en el instante 4 se publicaría `Loaded(A)` y la pantalla enseñaría el ligando **equivocado**. Hay una prueba para esto (`aNewLoadReplacesTheRunningOneAndTheOldResultNeverShowsUp`).

### 8.3 El usuario sale de la pantalla

| Instante | Hilo | Qué ocurre | `state` |
|---------:|------|------------|---------|
| 0 | Main | `load("A")`; Work-1 descargando | `Loading(A)` |
| 1 | Main | `cancel()`: cancela el trabajo, `job = null`, estado `Idle` | `Idle` |
| 2 | Work-1 | En su siguiente vuelta, `isCancelled()` es `true`: el descargador devuelve `Cancelled` y suelta la conexión | `Idle` |
| 3 | Main | El resultado se descarta (la corrutina está cancelada) | `Idle` |

### 8.4 Una excepción inesperada

`source` lanza `IllegalStateException("boom")` (un fallo nuestro). `withContext` **relanza** la excepción en la corrutina; la rama `catch (e: Exception)` (línea 53) la convierte en
`Failure(Unexpected("java.lang.IllegalStateException: boom"))` y la línea 59 publica `Failed(id, Unexpected(...))`. **El indicador desaparece** y se ve un error en lugar de un cierre.
Sin esa rama, la excepción subiría a la corrutina, que moriría **sin tocar el estado**: el `Loading` se quedaría **para siempre**. Es la garantía de la clase, y la prueba `aSourceThatThrows...` la protege.

---

## 9. Por qué el estado no vive dentro del `ViewModel`

En Android lo habitual es poner esta lógica directamente en un `ViewModel` (una clase que **sobrevive a los giros de pantalla**: la actividad se destruye y se recrea, el `ViewModel` no). Aquí el `ViewModel` será
una **envoltura finísima** (unas diez líneas) alrededor de `LigandLoader`. Motivos:

- **Probar sin Android.** `ViewModel` y `viewModelScope` pertenecen a la librería `androidx.lifecycle`, que **no existe** en el entorno donde se escriben las pruebas rápidas. `LigandLoader` es Kotlin con corrutinas y se
  prueba con JUnit sin emulador (10 pruebas de hilos reales: cancelación, sustitución, excepción).
- **Una sola responsabilidad.** El `ViewModel` se ocupa del **ciclo de vida** (sobrevivir a giros, cancelar al destruirse); el cargador, de las **reglas de la carga**.
- **Reutilizable.** Si la pantalla cambiara de arquitectura (otro mecanismo de navegación), la regla «el indicador siempre desaparece» no tendría que reescribirse.

**Alternativas:**

| Opción | A favor | En contra |
|--------|---------|-----------|
| Todo dentro del `ViewModel` | Menos ficheros | No se puede probar en la JVM sin dependencias de Android; las pruebas de cancelación, difíciles |
| *Callbacks* (`onSuccess`, `onError`) | Sin corrutinas | Es fácil olvidar una rama; la cancelación y la «carrera» A/B se vuelven manuales |
| `LiveData` en vez de `StateFlow` | Muy usado en Android clásico | Atado a Android; menos natural con corrutinas; Compose prefiere `StateFlow` |
| Flujo «frío» (`flow { emit(...) }`) | Cancelación automática al dejar de observar | El estado no se conserva si la pantalla se recrea; más difícil de razonar al principio |
| RxJava | Potente | Dependencia grande, otro modelo mental; no se justifica aquí |

---

## 10. Las pruebas, una a una

Ejecutar desde Android Studio: botón derecho sobre `app/src/test/java` → *Run Tests*; o `./gradlew :app:testDebugUnitTest`. Las pruebas del catálogo que leen el **fichero real**
buscan `src/main/assets/ligands.txt` **relativo a la carpeta `app/`** (el directorio de trabajo que Gradle y Android Studio usan para las pruebas). Si se ejecutaran desde otra carpeta, esas pruebas fallarían con el mensaje
`ligands.txt not found at ...` y la ruta que probaron.

### 10.1 `LigandCatalogTest` (15 pruebas)

**Leer el texto**

```kotlin
// LigandCatalogTest.kt · líneas 25–29
25  @Test
26  fun windowsLineEndingsBlankLinesAndSpacesAreIgnored() {
27      val catalog = LigandCatalog.fromText("ZN\r\n\r\n  ATP  \r\n\n001")
28      assertEquals(listOf("ZN", "ATP", "001"), catalog.ids)
29  }
```

```kotlin
// LigandCatalogTest.kt · líneas 31–34
31  @Test
32  fun repeatedIdsAppearOnlyOnce() {
33      assertEquals(listOf("ATP", "ZN"), catalog("ATP", "ZN", "ATP", "ZN").ids)
34  }
```

```kotlin
// LigandCatalogTest.kt · líneas 36–41
36  @Test
37  fun emptyTextIsAnEmptyCatalogNotAnError() {
38      assertEquals(emptyList<String>(), LigandCatalog.fromText("").ids)
39      assertEquals(emptyList<String>(), LigandCatalog.fromText("\n \n").ids)
40      assertEquals(emptyList<String>(), LigandCatalog.fromText("").search("A"))
41  }
```

Un fichero vacío o corrupto produce un catálogo **vacío**, no una excepción: la pantalla enseñará una lista vacía, no se cerrará.

**Buscar**

```kotlin
// LigandCatalogTest.kt · líneas 66–70
66  @Test
67  fun idsStartingWithTheTextComeFirstThenTheRestKeepingTheFileOrder() {
68      val catalog = catalog("AAT", "ATP", "BAT", "CAT", "AT1", "ZN")
69      assertEquals(listOf("ATP", "AT1", "AAT", "BAT", "CAT"), catalog.search("at"))
70  }
```

La prueba que fija la **regla de orden**: `at` da `ATP` y `AT1` (empiezan por `AT`, en el orden del fichero) antes de `AAT`, `BAT`, `CAT` (solo lo contienen).

```kotlin
// LigandCatalogTest.kt · líneas 72–77
72  @Test
73  fun spacesAroundTheQueryAreIgnoredButNotInsideIt() {
74      val catalog = catalog("ATP", "ZN")
75      assertEquals(listOf("ATP"), catalog.search("  atp "))
76      assertEquals(emptyList<String>(), catalog.search("A TP"))
77  }
```

```kotlin
// LigandCatalogTest.kt · líneas 84–90
84  @Test
85  fun digitsWorkLikeLetters() {
86      val catalog = catalog("001", "011", "0A1", "ATP")
87      assertEquals(listOf("001", "011", "0A1"), catalog.search("0"))
88      // "011" starts with "01"; "001" only contains it, so it goes second although it comes first in the file
89      assertEquals(listOf("011", "001"), catalog.search("01"))
90  }
```

Aquí **la propia prueba tenía un error** al escribirse: esperaba `[001, 011]` para `01`, y el código dio `[011, 001]`. Tenía razón el código (`011` empieza por `01`; `001` solo lo contiene): se corrigió la prueba
y se dejó el comentario que explica el porqué.

```kotlin
// LigandCatalogTest.kt · líneas 92–103
 92  @Test
 93  fun theSearchDoesNotDependOnTheLanguageOfThePhone() {
 94      // In Turkish "i".uppercase() is "İ" (with a dot): a search written with uppercase() would not find "ZIP".
 95      val saved = Locale.getDefault()
 96      try {
 97          Locale.setDefault(Locale.forLanguageTag("tr-TR"))
 98          assertEquals(listOf("ZIP"), catalog("ZIP", "ATP").search("zip"))
 99          assertEquals(listOf("ZIP"), catalog("ZIP", "ATP").search("ZIP"))
100      } finally {
101          Locale.setDefault(saved)
102      }
103  }
```

El `try/finally` **restaura** el idioma por defecto: una prueba no debe dejar el mundo cambiado para las demás.

**Con el fichero real**

```kotlin
// LigandCatalogTest.kt · líneas 114–120
114  @Test
115  fun theRealFileHas1243DifferentIds() {
116      val catalog = LigandCatalog.fromText(realText)
117      assertEquals(1243, catalog.ids.size)
118      assertEquals("001", catalog.ids.first())
119      assertEquals("ZZS", catalog.ids.last())
120  }
```

```kotlin
// LigandCatalogTest.kt · líneas 122–133
122  @Test
123  fun everyIdOfTheRealFileIsAcceptedByTheRepository() {
124      // If one id were not valid, tapping it would fail with InvalidId. So the list and the repository must agree.
125      val fetcher = object : HttpFetcher {
126          override fun fetch(url: String, isCancelled: () -> Boolean) = FetchResult.HttpError(404)
127      }
128      val repository = LigandRepository(fetcher)
129      for (id in LigandCatalog.fromText(realText).ids) {
130          val result = repository.load(id)
131          assertTrue("$id rejected: $result", !(result is LigandLoadResult.Failure && result.error is LoadError.InvalidId))
132      }
133  }
```

Conecta **dos módulos**: pasa los 1243 ids por el `LigandRepository` real (con un descargador falso que siempre responde `404`) y comprueba que **ninguno** se rechaza como `InvalidId`.
Si el catálogo y la validación del repositorio discreparan, **tocar un ligando de la lista daría un error absurdo**; esta prueba lo impediría.

```kotlin
// LigandCatalogTest.kt · líneas 135–147
135  @Test
136  fun searchesOnTheRealFile() {
137      val catalog = LigandCatalog.fromText(realText)
138      assertEquals(listOf("ATP"), catalog.search("atp").filter { it == "ATP" })
139      assertEquals("ATP", catalog.search("ATP").first())
140      assertEquals(catalog.ids.size, catalog.search("").size)
141      assertTrue(catalog.search("ZN").contains("ZN"))
142      assertEquals("ZN", catalog.search("zn").first())
143      // every result really contains the text, and nothing that contains it is lost
144      val expected = catalog.ids.filter { it.contains("AT", ignoreCase = true) }.toSet()
145      assertEquals(expected, catalog.search("at").toSet())
146      assertEquals(expected.size, catalog.search("at").size)
147  }
```

La última parte comprueba una **propiedad**, no un valor concreto: lo que `search("at")` devuelve es **exactamente** el conjunto de ids que contienen `at` (nada de más, nada de menos) y sin repetir.

### 10.2 `LigandLoaderTest` (10 pruebas)

**Montaje.** Cada prueba crea dos «mundos» como la app real: un **hilo único** (`fake-main`) donde se llama a `load`/`cancel` y se publica el estado, y un **grupo de hilos** (`fake-work`) donde corre la fuente lenta.
La fuente se controla con **cerrojos** (`CountDownLatch`: un contador que bloquea a quien espera hasta que otro lo pone a cero) para que **la prueba decida cuándo termina el trabajo**; así se reproducen
las carreras sin depender de esperas por azar. `awaitState` espera (máximo 5 s) a que el estado cumpla una condición.

```kotlin
// LigandLoaderTest.kt · líneas 66–79
66  @Test
67  fun showsLoadingWhileTheWorkRunsAndThenTheMolecule() {
68      val release = CountDownLatch(1)
69      val loader = loader { id, _ ->
70          release.await(5, TimeUnit.SECONDS)
71          LigandLoadResult.Success(molecule(id))
72      }
73
74      onMain { loader.load("ATP") }
75      assertEquals(LoadState.Loading("ATP"), loader.state.value)   // synchronously, before any work finishes
76
77      release.countDown()
78      assertEquals(LoadState.Loaded(molecule("ATP")), awaitState(loader) { it !is LoadState.Loading })
79  }
```

Comprueba que **justo después de `load`**, el estado ya es `Loading` (síncrono); y que tras soltar el cerrojo, llega `Loaded`.

```kotlin
// LigandLoaderTest.kt · líneas 92–114
 92  @Test
 93  fun everyKindOfResultLeavesTheLoadingState() {
 94      val cases = listOf(
 95          LigandLoadResult.Success(molecule("X")) to LoadState.Loaded(molecule("X")),
 96          LigandLoadResult.Failure(LoadError.NoConnection) to LoadState.Failed("X", LoadError.NoConnection),
 97          LigandLoadResult.Failure(LoadError.Timeout) to LoadState.Failed("X", LoadError.Timeout),
 98          LigandLoadResult.Failure(LoadError.NotFound) to LoadState.Failed("X", LoadError.NotFound),
 99          LigandLoadResult.Failure(LoadError.ServerError(500)) to LoadState.Failed("X", LoadError.ServerError(500)),
100          LigandLoadResult.Failure(LoadError.Incomplete) to LoadState.Failed("X", LoadError.Incomplete),
101          LigandLoadResult.Failure(LoadError.TooLarge(10)) to LoadState.Failed("X", LoadError.TooLarge(10)),
102          LigandLoadResult.Failure(LoadError.Corrupt(CifFailure.NoDataBlock)) to
103              LoadState.Failed("X", LoadError.Corrupt(CifFailure.NoDataBlock)),
104          LigandLoadResult.Failure(LoadError.InvalidId("X")) to LoadState.Failed("X", LoadError.InvalidId("X")),
105          LigandLoadResult.Failure(LoadError.NetworkError("tls")) to LoadState.Failed("X", LoadError.NetworkError("tls")),
106          LigandLoadResult.Failure(LoadError.Unexpected("bug")) to LoadState.Failed("X", LoadError.Unexpected("bug")),
107          LigandLoadResult.Cancelled to LoadState.Idle,
108      )
109      for ((result, expected) in cases) {
110          val loader = loader { _, _ -> result }
111          onMain { loader.load("X") }
112          assertEquals("for $result", expected, awaitState(loader) { it !is LoadState.Loading })
113      }
114  }
```

**La prueba de la garantía.** Recorre **12 resultados** (el éxito, los 10 `LoadError` y `Cancelled`) y para cada uno comprueba que el estado final es el esperado **y no es `Loading`**.

```kotlin
// LigandLoaderTest.kt · líneas 116–123
116  @Test
117  fun aSourceThatThrowsEndsAsAnUnexpectedFailureNotAsAnEternalSpinner() {
118      val loader = loader { _, _ -> throw IllegalStateException("boom") }
119      onMain { loader.load("X") }
120      val state = awaitState(loader) { it !is LoadState.Loading }
121      assertTrue("got $state", state is LoadState.Failed && state.error is LoadError.Unexpected)
122      assertTrue((((state as LoadState.Failed).error) as LoadError.Unexpected).description.contains("boom"))
123  }
```

```kotlin
// LigandLoaderTest.kt · líneas 140–167
140  @Test
141  fun aNewLoadReplacesTheRunningOneAndTheOldResultNeverShowsUp() {
142      val startedA = CountDownLatch(1)
143      val releaseA = CountDownLatch(1)
144      val finishedA = CountDownLatch(1)
145      val sawCancelled = AtomicBoolean(false)
146      val loader = loader { id, isCancelled ->
147          if (id == "A") {
148              startedA.countDown()
149              releaseA.await(5, TimeUnit.SECONDS)
150              sawCancelled.set(isCancelled())
151              finishedA.countDown()
152          }
153          LigandLoadResult.Success(molecule(id))
154      }
155
156      onMain { loader.load("A") }
157      assertTrue(startedA.await(5, TimeUnit.SECONDS))   // A must really be running, not just queued
158      onMain { loader.load("B") }
159      assertEquals(LoadState.Loaded(molecule("B")), awaitState(loader) { it is LoadState.Loaded })
160
161      releaseA.countDown()                       // A finishes now, late
162      assertTrue(finishedA.await(5, TimeUnit.SECONDS))
163      letLateResultsArrive()
164
165      assertTrue("A was never told to stop", sawCancelled.get())
166      assertEquals(LoadState.Loaded(molecule("B")), loader.state.value)
167  }
```

La **carrera A/B** de la sección 8.2. `startedA` asegura que A **de verdad está corriendo** antes de cancelarla (sin esto, la cancelación podía llegar antes de que A empezara y la prueba no probaría nada: **fallo real que
se encontró al escribirla**, ver sección 13). Luego se suelta A y se espera a que termine; `letLateResultsArrive` da 150 ms y vacía la cola del hilo principal para que un resultado tardío, si lo hubiera, tuviera
tiempo de publicarse; y se comprueba que el estado sigue siendo `Loaded(B)`. También se comprueba que A **vio** `isCancelled() == true`.

```kotlin
// LigandLoaderTest.kt · líneas 171–193
171  @Test
172  fun cancelReturnsToIdleAndTheLateResultIsIgnored() {
173      val started = CountDownLatch(1)
174      val release = CountDownLatch(1)
175      val finished = CountDownLatch(1)
176      val loader = loader { id, _ ->
177          started.countDown()
178          release.await(5, TimeUnit.SECONDS)
179          finished.countDown()
180          LigandLoadResult.Success(molecule(id))
181      }
182      onMain { loader.load("A") }
183      assertEquals(LoadState.Loading("A"), loader.state.value)
184      assertTrue(started.await(5, TimeUnit.SECONDS))    // the work is really running when we cancel
185
186      onMain { loader.cancel() }
187      assertEquals(LoadState.Idle, loader.state.value)
188
189      release.countDown()
190      assertTrue(finished.await(5, TimeUnit.SECONDS))
191      letLateResultsArrive()
192      assertEquals(LoadState.Idle, loader.state.value)
193  }
```

```kotlin
// LigandLoaderTest.kt · líneas 195–212
195  @Test
196  fun cancelTellsTheSourceToStopSoTheNetworkWorkEnds() {
197      val started = CountDownLatch(1)
198      val stopped = CountDownLatch(1)
199      val loader = loader { _, isCancelled ->
200          started.countDown()
201          while (!isCancelled()) Thread.sleep(5)    // like the read loop of the downloader
202          stopped.countDown()
203          LigandLoadResult.Cancelled
204      }
205      onMain { loader.load("A") }
206      assertTrue(started.await(5, TimeUnit.SECONDS))
207
208      onMain { loader.cancel() }
209
210      assertTrue("the source never saw isCancelled() == true", stopped.await(5, TimeUnit.SECONDS))
211      assertEquals(LoadState.Idle, loader.state.value)
212  }
```

La fuente falsa hace lo mismo que el descargador real: **repite hasta que `isCancelled()` sea `true`** y entonces devuelve `Cancelled`. La prueba demuestra que `cancel()` **llega** hasta la función de cancelación de la fuente
(la línea 50 del cargador). Con `{ false }` en su lugar, la fuente no pararía nunca y la prueba daría un fallo de tiempo.

```kotlin
// LigandLoaderTest.kt · líneas 125–136
125  @Test
126  fun afterAFailureTheSameLoaderCanTryAgain() {
127      var attempt = 0
128      val loader = loader { id, _ ->
129          if (++attempt == 1) LigandLoadResult.Failure(LoadError.NoConnection) else LigandLoadResult.Success(molecule(id))
130      }
131      onMain { loader.load("X") }
132      assertEquals(LoadState.Failed("X", LoadError.NoConnection), awaitState(loader) { it !is LoadState.Loading })
133
134      onMain { loader.load("X") }
135      assertEquals(LoadState.Loaded(molecule("X")), awaitState(loader) { it !is LoadState.Loading })
136  }
```

El **botón «reintentar»** del futuro: tras `Failed`, otro `load` del mismo id funciona.

---

## 11. Qué demuestran las pruebas: mutación

Se estropeó el código a propósito (una avería cada vez) y se ejecutaron las 25 pruebas (15 del catálogo y 10 del cargador).

| N.º | Avería introducida | Resultado | Prueba(s) que la detectan |
|----:|--------------------|-----------|---------------------------|
| C1 | **Control**: «avería» que no cambia nada (el mismo código) | Pasa, **como debe** | — (comprueba que el montaje de averías no da falsos fallos) |
| C2 | La búsqueda distingue mayúsculas en `contains` | Muere | `caseDoesNotMatter`, `theSearchDoesNotDependOnTheLanguageOfThePhone`, `idsStartingWithTheText...`, `searchesOnTheRealFile` |
| C3 | La búsqueda distingue mayúsculas en `startsWith` | Muere | `idsStartingWithTheTextComeFirstThenTheRestKeepingTheFileOrder` |
| C4 | No recortar los espacios de la consulta | Muere | `spacesAroundTheQueryAreIgnoredButNotInsideIt`, `emptyOrBlankQueryMatchesEverything` |
| C5 | Quitar `distinct()` | Muere | `repeatedIdsAppearOnlyOnce` |
| C6 | No recortar los espacios de cada línea | Muere | `windowsLineEndingsBlankLinesAndSpacesAreIgnored`, `emptyTextIsAnEmptyCatalogNotAnError` |
| C7 | No descartar las líneas vacías | Muere | 4 pruebas, entre ellas `theRealFileHas1243DifferentIds` |
| C8 | No poner primero los que empiezan por el texto | Muere | `idsStartingWithTheText...`, `digitsWorkLikeLetters` |
| C9 | Consulta vacía → lista vacía (en vez de todo) | Muere | `emptyOrBlankQueryMatchesEverything`, `searchesOnTheRealFile` |
| C10 | «Contiene» → «empieza por» | Muere | `theTextCanBeAnywhereInsideTheId`, `idsStartingWithTheText...`, `digitsWorkLikeLetters`, `searchesOnTheRealFile` |
| L1 | Quitar `job?.cancel()` en `load` (la carga vieja sigue viva) | Muere | `aNewLoadReplacesTheRunningOneAndTheOldResultNeverShowsUp` |
| L2 | Quitar `job?.cancel()` en `cancel` | Muere | `cancelReturnsToIdleAndTheLateResultIsIgnored`, `cancelTellsTheSourceToStopSoTheNetworkWorkEnds` |
| L3 | Quitar la rama `catch (CancellationException) { throw e }` (la cancelación se trata como un error normal) | Muere | `aNewLoadReplacesTheRunningOneAndTheOldResultNeverShowsUp`, `cancelReturnsToIdleAndTheLateResultIsIgnored` |
| L4 | Capturar solo `IOException` en lugar de `Exception` (una excepción inesperada escapa) | Muere | `aSourceThatThrowsEndsAsAnUnexpectedFailureNotAsAnEternalSpinner` |
| L5 | Pasar `{ false }` en lugar de `{ !isActive }` a la fuente | Muere | `aNewLoadReplacesTheRunning...`, `cancelTellsTheSourceToStop...` |
| L6 | El resultado `Cancelled` publica `Loading` (spinner eterno) | Muere | `everyKindOfResultLeavesTheLoadingState` |
| L7 | No publicar `Loading` al empezar | Muere | 4 pruebas, entre ellas `showsLoadingWhileTheWorkRunsAndThenTheMolecule` |
| L8 | `cancel()` no vuelve a `Idle` | Muere | `cancelReturnsToIdleAndTheLateResultIsIgnored`, `cancelTellsTheSourceToStop...` |
| L9 | Un `Failure` publica `Idle` (el error desaparece sin mostrarse) | Muere | `afterAFailureTheSameLoaderCanTryAgain`, `everyKindOfResultLeavesTheLoadingState`, `aSourceThatThrows...` |
| L10 | Ejecutar el trabajo en `Dispatchers.Unconfined` en lugar del despachador de trabajo | Muere | `aNewLoadReplacesTheRunning...`, `cancelReturnsToIdle...`, `cancelTellsTheSourceToStop...` |

**Resultado:** de 19 averías reales, **las 19 mueren** (más un control que pasa, como debe). Una nota de honestidad: la primera versión de la avería L3 estaba mal escogida
(sustituía la rama por otra excepción, y lo que detectaba era eso y no la cancelación); se rehízo quitando exactamente la rama `catch (CancellationException)`, y esa es la que figura en la tabla.
**No se probaron** averías en la lectura de los *assets* ni en el `ViewModel` porque aún no existen.

---

## 12. Qué falta y cómo se enchufará a Android

Lo que sigue **no está escrito ni compilado**: necesita el SDK de Android, que el entorno de análisis no tiene. Es un **esquema**, no código verificado.

1. **Dependencias nuevas** en `gradle/libs.versions.toml` y `app/build.gradle.kts`:
   - `androidx.lifecycle:lifecycle-viewmodel-compose` (para `viewModel()` en Compose) y `androidx.lifecycle:lifecycle-runtime-compose` (para `collectAsStateWithLifecycle()`, que observa un `StateFlow` y **deja de observar
     cuando la pantalla no está visible**). La versión actual de `lifecycle-runtime-ktx` del proyecto es **2.6.1**; según la página de versiones estables de AndroidX, la última del grupo *Lifecycle* es **2.11.1** (18/11/2026).
     **No se ha comprobado que 2.11.1 compile con AGP 9.4.1 / Kotlin 2.4.21**: es lo primero que hay que verificar al añadirla.
   - `kotlinx-coroutines`: el cargador usa `launch`, `withContext`, `StateFlow`. Llega **de forma transitiva** con la librería de *lifecycle* (lo recordamos, no verificado); si el IDE marca `Unresolved reference`, se declara
     `org.jetbrains.kotlinx:kotlinx-coroutines-android` explícitamente. **No se ha verificado cuál es la última versión estable** (no se pudo consultar).
2. **Leer `ligands.txt` de los *assets*:** `context.assets.open("ligands.txt").bufferedReader().use { it.readText() }`, **fuera del hilo principal** o al arrancar, y pasar el texto a `LigandCatalog.fromText`.
3. **`ViewModel`** (envoltura de unas diez líneas): crea el `LigandLoader` con `viewModelScope`, `Dispatchers.IO` y `{ id, cancelado -> repository.load(id, cancelado) }`; expone `loader.state`; en `onCleared()` llama a `loader.cancel()`.
4. **Pantallas:** buscador (`TextField` con el texto en `rememberSaveable`) + `LazyColumn` con `catalog.search(texto)`; al tocar un ligando, `load(id)`; un `when` sobre `LoadState` que dibuja la lista, el indicador, los cuatro errores con botón de reintentar,
   o la escena 3D.
5. **Navegación** entre inicio de sesión, lista y vista 3D (documento 05 y 07).
6. **Permiso `INTERNET`** (documento 06, sección 8).

---

## 13. Trampas y límites conocidos

| # | Trampa o límite | Consecuencia | Estado |
|--:|-----------------|--------------|--------|
| 1 | `load` y `cancel` **no son seguros entre hilos**: `job` es una variable normal | Llamarlos desde dos hilos a la vez puede perder una cancelación | Regla escrita en la documentación: **solo desde el hilo principal** |
| 2 | El ámbito debe reanudar **en el mismo hilo** desde el que se llama (el principal) | Si no, la publicación del estado podría cruzarse con un `load` | `viewModelScope` lo cumple (lo recordamos: usa `Dispatchers.Main.immediate`; no verificado) |
| 3 | **Una prueba de carrera que no arranca la carrera no prueba nada** | `cancel()` antes de que el trabajo empiece lo cancela sin que la fuente se llegue a ejecutar | **Encontrado al escribir las pruebas** (falló `cancelReturnsToIdle...` una vez): se añadieron cerrojos `started` |
| 4 | `letLateResultsArrive` espera 150 ms | Una prueba de «no pasa nada» no puede ser perfectamente determinista | Aceptado: la avería que busca (publicar un resultado tardío) se detecta; ver mutación L1 |
| 5 | `Error` (p. ej. `OutOfMemoryError`) **no** se captura | El estado puede quedarse en `Loading` y la app caer | Aceptado y documentado; los límites de tamaño (documento 06) lo previenen |
| 6 | `StateFlow` solo emite si el valor **cambia** (`equals`) | `load("A")` seguido de otro `load("A")` mientras carga no emite de nuevo | Inofensivo: la pantalla ya muestra `Loading(A)` |
| 7 | `Loaded(molecule)` compara la molécula **entera** (`data class`) | `StateFlow` hace una comparación profunda al publicar | Aceptado: 87 átomos, microsegundos |
| 8 | Girar la pantalla destruye la actividad | Un cargador **dentro** de la actividad perdería la carga | Por eso va en el `ViewModel`, que sobrevive (sección 9) |
| 9 | Si el proceso muere (Android lo mata en segundo plano), el estado se pierde | Al volver, `Idle` | Aceptado: se vuelve a la lista |
| 10 | La búsqueda corre en el hilo que la llama | 57 µs en la JVM de análisis; sin medir en el A33 | Aceptado hasta medir |
| 11 | El orden «prefijo primero» no está en la tarea | Es una elección de usabilidad | D17: fácil de quitar (es una línea) |

---

## 14. Qué está verificado y qué no

| Afirmación | Estado |
|------------|--------|
| 25 pruebas nuevas (15 + 10) pasan en la JVM (kotlinc 2.4.21 + JUnit, OpenJDK 21.0.12); las del cargador, 5 ejecuciones seguidas sin fallos | **Verificado** |
| Las averías de la sección 11 | **Verificado** (resultados reales) |
| Los 1243 ids de `ligands.txt` son distintos, están ordenados, y cumplen `[A-Z0-9]{1,5}` | **Verificado** |
| La tabla de la sección 4 (resultados de búsqueda) | **Verificado** (salida real del código) |
| Tiempo de búsqueda: ~57 µs en la JVM de análisis | **Verificado en esa JVM**; **sin medir** en el A33 |
| `withContext` descarta el resultado de una corrutina cancelada | Recordado de la documentación y **comprobado indirectamente** por las pruebas 8.2 y 8.3 |
| Las pruebas pasan **bajo el Gradle del equipo** | **Sin verificar** (las anteriores sí pasaron tras dos arreglos; documento 06) |
| Que `kotlinx-coroutines` llegue transitivamente con la dependencia de *lifecycle* | **Sin verificar** |
| Versión 2.11.1 de *lifecycle* compatible con AGP 9.4.1 y Kotlin 2.4.21 | **Sin verificar** |
| Todo el pegamento con Android (sección 12) | **No escrito** |

---

## 15. Decisiones

| N.º | Decisión | Motivo | Alternativas |
|-----|----------|--------|--------------|
| D17 | Búsqueda: texto **contenido** en el id, sin distinguir mayúsculas, con los que **empiezan** por el texto primero | Con 3 letras por id, «contiene» es más útil que solo «empieza por»; el prefijo primero pone arriba lo que el usuario probablemente quiere (`ATP` antes de `CAT` al escribir `AT`). Coste: 57 µs | Solo prefijo; solo «contiene» sin reordenar; búsqueda difusa |
| D18 | Las reglas de la carga viven en `LigandLoader` (Kotlin + corrutinas), no en el `ViewModel`; el estado es una `sealed interface` (`LoadState`) | Se prueba en la JVM sin Android; el indicador no puede coexistir con un error; el `ViewModel` queda como envoltura mínima | Todo en el `ViewModel`; tres variables sueltas; `LiveData`; RxJava |

---

## 16. Qué recordar

1. **El catálogo recibe texto, no ficheros**: no sabe de Android. Limpia (`trim`, vacíos, repetidos) y se prueba con cadenas.
2. **`ignoreCase = true` y no `uppercase()`**: comparar sin depender del idioma del móvil (el caso turco).
3. **Una búsqueda en tiempo real es recalcular la lista en cada pulsación**: con 1243 ids cuesta microsegundos; la lista visible debe ser perezosa (`LazyColumn`).
4. **`partition` + `+`** da un orden estable «los que empiezan primero, luego el resto».
5. **Un solo estado a la vez** (`sealed interface`): el indicador de carga **solo** existe en `Loading`.
6. **La garantía**: tras `load`, el estado es `Loading` hasta que el trabajo acaba, y entonces es **siempre** otra cosa: éxito, cualquier error, excepción o cancelación.
7. **`CancellationException` se deja pasar** (`throw e` antes del `catch (e: Exception)`), o una carga cancelada publicaría un fallo falso.
8. **`job?.cancel()` en `load`**: sin él, el resultado lento de A pisa al de B.
9. **La cancelación cooperativa necesita un puente**: `{ !isActive }` conecta la cancelación de corrutinas con el `isCancelled` del descargador, que es código bloqueante.
10. **`load` y `cancel` solo desde el hilo principal**; el trabajo lento, en `workDispatcher`.
11. **El `ViewModel` será una envoltura**: las reglas se prueban sin Android.
12. **Una prueba de carrera debe asegurarse de que la carrera ocurre** (`started`); si no, pasa sin probar nada.
13. **Lo que falta y no se ha compilado**: dependencias nuevas, lectura de *assets*, `ViewModel`, pantallas, navegación, `INTERNET`.
