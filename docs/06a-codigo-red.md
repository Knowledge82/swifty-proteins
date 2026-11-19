# 06a · Código de la capa de red (línea a línea)

> Estado: **ESCRITO Y PROBADO EN LA JVM**. Describe los cuatro ficheros de la capa de red (`FetchResult.kt`, `HttpUrlConnectionFetcher.kt`,
> `LigandLoadResult.kt`, `LigandRepository.kt`) y los tres ficheros de pruebas (`TestHttpServer.kt`, `HttpUrlConnectionFetcherTest.kt`,
> `LigandRepositoryTest.kt`). **No** incluye el `ViewModel`, la interfaz ni la lista de ligandos (sección 14).
> Las citas de código se generan **desde los ficheros reales**; si se edita un fichero hay que regenerar el documento.
> Redactado el 19/11/2026. La teoría, los riesgos y los motivos están en [`06-red-y-carga-de-ligandos.md`](06-red-y-carga-de-ligandos.md); aquí está el **código**.
> El parser (que esta capa usa) está en [`03a-codigo-parser.md`](03a-codigo-parser.md).

## Índice

1. [Mapa: qué fichero hace qué y dónde vive](#1-mapa-qué-fichero-hace-qué-y-dónde-vive)
2. [Conceptos de Kotlin y de Java que aparecen](#2-conceptos-de-kotlin-y-de-java-que-aparecen)
3. [`FetchResult.kt`: qué pasó en la red](#3-fetchresultkt-qué-pasó-en-la-red)
4. [`HttpUrlConnectionFetcher.kt`: la descarga real](#4-httpurlconnectionfetcherkt-la-descarga-real)
5. [`LigandLoadResult.kt`: qué ve el resto de la app](#5-ligandloadresultkt-qué-ve-el-resto-de-la-app)
6. [`LigandRepository.kt`: red + parser](#6-ligandrepositorykt-red--parser)
7. [Simulaciones de extremo a extremo](#7-simulaciones-de-extremo-a-extremo)
8. [Las pruebas: qué hay y cómo se ejecutan](#8-las-pruebas-qué-hay-y-cómo-se-ejecutan)
9. [`TestHttpServer.kt`: el servidor casero](#9-testhttpserverkt-el-servidor-casero)
10. [Las pruebas del descargador, una a una](#10-las-pruebas-del-descargador-una-a-una)
11. [Las pruebas del repositorio, una a una](#11-las-pruebas-del-repositorio-una-a-una)
12. [Qué demuestran las pruebas: mutación](#12-qué-demuestran-las-pruebas-mutación)
13. [Trampas y límites conocidos](#13-trampas-y-límites-conocidos)
14. [Qué falta](#14-qué-falta)
15. [Qué está verificado y qué no](#15-qué-está-verificado-y-qué-no)
16. [Qué recordar](#16-qué-recordar)

---

## 1. Mapa: qué fichero hace qué y dónde vive

El paquete es `dev.knowledge82.swiftyproteins.net`, en la carpeta `dev/knowledge82/swiftyproteins/net/` (la carpeta tiene que coincidir con la línea `package`).

| Fichero | Ruta | Líneas | Para qué |
|---------|------|-------:|----------|
| `FetchResult.kt` | `app/src/main/java/dev/knowledge82/swiftyproteins/net/` | 50 | `FetchResult` (qué pasó en la red) y la interfaz `HttpFetcher` (algo que descarga una URL). |
| `HttpUrlConnectionFetcher.kt` | `.../net/` | 104 | La implementación real de `HttpFetcher` con `java.net.HttpURLConnection`: tres relojes, límite de tamaño, `Content-Length`, cancelación. |
| `LigandLoadResult.kt` | `.../net/` | 53 | `LigandLoadResult` (éxito, fallo o cancelado) y `LoadError` (por qué falló). Lo único que verá la interfaz. |
| `LigandRepository.kt` | `.../net/` | 56 | Valida el id, pide la URL, pasa el cuerpo al parser y traduce todo a `LigandLoadResult`. |
| `TestHttpServer.kt` | `app/src/test/java/dev/knowledge82/swiftyproteins/net/` | 99 | Servidor HTTP casero sobre `ServerSocket` para las pruebas. |
| `HttpUrlConnectionFetcherTest.kt` | `.../net/` (en `src/test`) | 274 | 22 pruebas del descargador real contra el servidor casero. |
| `LigandRepositoryTest.kt` | `.../net/` (en `src/test`) | 237 | 20 pruebas del repositorio: con un descargador falso y de extremo a extremo. |

Flujo de datos (cada flecha es una llamada; lo que baja es el resultado):

```
 Interfaz / ViewModel (más adelante)
        │  load("ATP", isCancelled)                    (en un hilo de fondo, NUNCA en el principal)
        ▼
 LigandRepository.load ── id inválido ───────────────────────────► Failure(InvalidId)       (sin red)
        │  fetch("https://files.rcsb.org/ligands/view/ATP.cif", isCancelled)
        ▼
 HttpFetcher.fetch  (la real: HttpUrlConnectionFetcher)
        │  fase 1: conectar + estado + cabeceras ──► NoConnection / Timeout / Failed / HttpError(código)
        │  fase 2: leer el cuerpo ────────────────► Cancelled / Timeout / TooLarge / Incomplete / Body(texto)
        ▼
 FetchResult
        │  Body ──► CifParser.parse(texto) ──► Success(molécula) / Failure(Corrupt(motivo))
        │  el resto se traduce a LoadError
        ▼
 LigandLoadResult  =  Success(molecule) | Failure(error) | Cancelled
```

Dependencias (una flecha significa «usa»): `LigandRepository` → `HttpFetcher`, `FetchResult`, `LigandLoadResult`, `LoadError`, `CifParser`;
`LigandLoadResult` → `Molecule`, `CifFailure`; `HttpUrlConnectionFetcher` → `HttpFetcher`, `FetchResult`. **Nada** de esta capa usa `android.*`.

---

## 2. Conceptos de Kotlin y de Java que aparecen

Los conceptos del parser (clase `sealed`, `data class`, `Int?`, `when`, etc.) están en el documento 03a, sección 2. Aquí solo lo nuevo.

**Un parámetro que es una función.** `isCancelled: () -> Boolean` significa «una función que no recibe nada y devuelve `true` o `false`». Se
escribe una *lambda* (una función sin nombre) entre llaves: `{ false }` es «siempre falso» y `{ !coroutineContext.isActive }` será «¿me han cancelado?».
Se puede **dar un valor por defecto**: `isCancelled: () -> Boolean = { false }`, y entonces quien llama puede no pasarla. Si el último parámetro es una función,
al llamar se puede escribir **fuera de los paréntesis**: `repository.load("XYZ") { flag }` es `load("XYZ", { flag })`.

**`data object`.** Como `object` (existe **una única instancia**), pero con `toString` legible (`Timeout`) y `equals` correcto. Se usa para los casos sin datos:
`FetchResult.Timeout`, `LoadError.NotFound`. Los casos **con** datos son `data class` (`HttpError(val code: Int)`).

**`try` es una expresión.** `val status = try { connection.responseCode } catch (...) { return ... }` asigna el resultado del `try`; en cada `catch` se
**sale de la función** con `return`, así que nunca se llega a asignar. (Un `return` tiene tipo `Nothing`: «nunca produce valor», por eso encaja en cualquier sitio.)

**`return` desde dentro de `use { ... }`.** `use` ejecuta un bloque y **cierra el recurso al final aunque haya excepción o `return`** (es el `try/finally` de Java hecho función).
El bloque es una lambda *inline*, y por eso un `return` dentro de ella **sale de la función que la contiene** (se llama *retorno no local*), no solo del bloque.

**`try { ... } finally { ... }`.** El `finally` se ejecuta **siempre**: tras el éxito, tras un `return` y tras una excepción. Se usa para llamar a `disconnect()`.

**`when` exhaustivo sobre una `sealed interface`.** Si el `when` se usa **como expresión** (su valor se devuelve o se asigna), el compilador exige cubrir todos los casos.
Si mañana se añade un caso a `FetchResult` y se olvida tratarlo en el repositorio, **no compila**.

**`System.nanoTime()`.** Reloj **monótono**: solo sirve para medir intervalos y no salta si el usuario cambia la hora del móvil (a diferencia de `System.currentTimeMillis()`).
Su valor absoluto no significa nada y puede ser negativo, así que se compara **restando**: `ahora - límite > 0`, no `ahora > límite` (la resta es correcta aunque el contador dé la vuelta).

**`ByteArrayOutputStream`.** Un «saco» de bytes que crece al escribir en él; `size()` dice cuántos hay y `toByteArray()` los entrega. **`InputStream.read(buffer)`** llena
**como mucho** el búfer y devuelve **cuántos bytes** puso, o **-1** si ya no hay más; **puede devolver menos de los que caben** aunque falten datos.

**`declared in 0..maxBytes`.** Pregunta de pertenencia a un rango (`0 <= declared && declared <= maxBytes`).

**`companion object`.** Lugar para lo que pertenece a la **clase**, no a cada objeto (constantes y la expresión regular). `const val` solo vale para constantes de compilación.

**Un «punto de inyección».** El descargador recibe `open: (URL) -> HttpURLConnection`, una función que **crea la conexión**. Normalmente es la de verdad;
una prueba le da otra que fabrica una conexión falsa. Es la forma más barata de poder probar lo que un servidor local no puede provocar (sección 10).

**Java: el ciclo de vida de `HttpURLConnection`.** (1) Se **crea** (`openConnection`; todavía no hay red). (2) Se **configura** (tiempos, método, cabeceras): después de conectar ya no se puede.
(3) La primera vez que se pide algo de la respuesta (`responseCode`, `inputStream`...) **se conecta de verdad**, se envía la petición y se leen la línea de estado y las cabeceras.
(4) Se **lee** el cuerpo con `inputStream`. (5) Se **desconecta** con `disconnect()`. En una respuesta de error (4xx/5xx) pedir `inputStream` **lanza** `FileNotFoundException`/`IOException`
(el cuerpo del error está en `errorStream`); por eso nuestro código **no** lo pide en ese caso.

---

## 3. `FetchResult.kt`: qué pasó en la red

Este fichero describe **solo el transporte**: ¿llegaron los bytes? No sabe qué es un CIF.

```kotlin
// FetchResult.kt · líneas 3–7
3  /**
4   * What happened when we asked a server for a file. It describes the TRANSPORT only (did the bytes arrive?);
5   * it knows nothing about CIF. Nothing in here is an exception: every outcome is a value.
6   */
7  sealed interface FetchResult {
```

Línea 7: `sealed interface FetchResult`. Todas las variantes están **en este fichero** y el compilador las conoce todas. Las líneas 3–6 son la documentación (`/** ... */`): no ejecutan nada.

```kotlin
// FetchResult.kt · líneas 9–19
 9  /** Status 2xx and the whole body arrived, decoded as UTF-8. */
10  data class Body(val text: String) : FetchResult
11
12  /** The server answered, but not with success (404, 500...). */
13  data class HttpError(val code: Int) : FetchResult
14
15  /** We could not reach the server: no network, unknown host, connection refused or unreachable. */
16  data class NoConnection(val reason: String) : FetchResult
17
18  /** The server or the network was too slow: connecting, waiting for data, or the total time limit. */
19  data object Timeout : FetchResult
```

- **Línea 10, `Body(text)`**: la única variante de éxito. Lleva el texto del cuerpo **ya decodificado como UTF-8**. «Éxito» aquí significa «estado 2xx **y** cuerpo entero»; no significa «es un CIF válido».
- **Línea 13, `HttpError(code)`**: el servidor **sí** respondió, pero no con éxito. No decide si es «no encontrado» o «error del servidor»: esa interpretación es del repositorio.
- **Línea 16, `NoConnection(reason)`**: no pudimos llegar al servidor. `reason` es texto para el registro, **no** para el usuario.
- **Línea 19, `Timeout`**: `data object`: no hay nada más que decir.

```kotlin
// FetchResult.kt · líneas 21–35
21      /**
22       * The connection ended before the whole body arrived.
23       * @param expectedBytes what the server announced in `Content-Length`, or -1 if it did not.
24       */
25      data class Incomplete(val expectedBytes: Long, val receivedBytes: Long) : FetchResult
26
27      /** The body is bigger than we accept; we stopped reading. */
28      data class TooLarge(val maxBytes: Int) : FetchResult
29
30      /** The caller asked to stop (the user left the screen). Not an error. */
31      data object Cancelled : FetchResult
32
33      /** Any other network problem (a TLS failure, a malformed URL...). */
34      data class Failed(val reason: String) : FetchResult
35  }
```

- **Línea 25, `Incomplete(expectedBytes, receivedBytes)`**: la conexión terminó antes de tiempo. `expectedBytes` vale **-1** si el servidor no anunció el tamaño. Los dos números son para el registro.
- **Línea 28, `TooLarge(maxBytes)`**: más grande de lo que aceptamos.
- **Línea 31, `Cancelled`**: el llamador pidió parar. **No es un error.**
- **Línea 34, `Failed(reason)`**: el resto (un certificado inválido, una URL mal formada). Existe para que **nada** quede sin explicar.

Ninguna variante es una excepción: son **valores** que se devuelven. Un `when` sobre ellas **no compila** si se olvida alguna.

```kotlin
// FetchResult.kt · líneas 37–50
37  /**
38   * Something that can download a URL. It is an interface so the code that USES it (the repository) can be
39   * tested with a fake, and the real implementation can be tested against a local server.
40   */
41  interface HttpFetcher {
42
43      /**
44       * Downloads [url] with a GET. BLOCKS the calling thread, so it must not run on the main thread.
45       * It never throws because of the network: every outcome is a [FetchResult].
46       *
47       * @param isCancelled asked repeatedly while the body is being read; when it answers true the download stops.
48       */
49      fun fetch(url: String, isCancelled: () -> Boolean = { false }): FetchResult
50  }
```

La interfaz `HttpFetcher` es **el contrato**: «dame una URL y una función para cancelar, te devuelvo un `FetchResult`». Hay dos implementaciones: la real (`HttpUrlConnectionFetcher`)
y los falsos de las pruebas. **El repositorio depende de la interfaz, no de la clase real**, y por eso se puede probar sin red y, si algún día hace falta, cambiar de librería tocando un solo fichero.
La línea 49 contiene el valor por defecto `{ false }`: quien no quiera cancelar no tiene que pasar nada. El comentario de las líneas 43–48 deja escritas las dos promesas importantes: **bloquea** y **no lanza**.

---

## 4. `HttpUrlConnectionFetcher.kt`: la descarga real

### 4.1 La clase y su constructor

```kotlin
// HttpUrlConnectionFetcher.kt · líneas 12–28
12  /**
13   * The real [HttpFetcher], built on `java.net.HttpURLConnection`: it comes with Android and the JVM, so it
14   * adds no dependency. Pure Kotlin/Java: no `android.*`, so it can be tested on the JVM against a local server.
15   *
16   * Three different clocks protect us from a slow server: [connectTimeoutMs] (establishing the connection),
17   * [readTimeoutMs] (silence between two chunks of data) and [totalTimeoutMs] (the whole body). The third one
18   * is needed because a server that sends one byte every few seconds never trips the second.
19   *
20   * @param open how a connection is created. The default is the normal one; the tests pass a fake.
21   */
22  class HttpUrlConnectionFetcher(
23      private val connectTimeoutMs: Int = 10_000,
24      private val readTimeoutMs: Int = 10_000,
25      private val totalTimeoutMs: Long = 30_000,
26      private val maxBytes: Int = 2_000_000,
27      private val open: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
28  ) : HttpFetcher {
```

- **Líneas 22–28**: el constructor tiene **cinco parámetros, todos con valor por defecto**. Con `HttpUrlConnectionFetcher()` se usan los valores reales; las pruebas pasan valores pequeños (`readTimeoutMs = 300`) para ir rápidas.
- `connectTimeoutMs` 10 000 ms y `readTimeoutMs` 10 000 ms: lo que la librería espera para conectar y entre dos llegadas de datos. `totalTimeoutMs` 30 000: lo que **nosotros** vigilamos (sección 4.3). `maxBytes` 2 000 000: el límite de tamaño.
- **`private val`**: cada parámetro es a la vez una propiedad privada de la clase (así se usa dentro de `fetch` y `readBody` sin escribir más).
- **Línea 27, `open`**: el punto de inyección de la sección 2. Su valor por defecto es `{ it.openConnection() as HttpURLConnection }`: «dada la `URL`, ábrela y trátala como `HttpURLConnection`». `openConnection()` devuelve un tipo general; el `as` es la conversión. Para `https://` la librería devuelve una subclase (`HttpsURLConnection`) que sigue siendo una `HttpURLConnection`.

### 4.2 `fetch`: preparar la conexión

```kotlin
// HttpUrlConnectionFetcher.kt · líneas 30–42
30  override fun fetch(url: String, isCancelled: () -> Boolean): FetchResult {
31      if (isCancelled()) return FetchResult.Cancelled
32      val deadline = System.nanoTime() + totalTimeoutMs * 1_000_000
33
34      val connection = try {
35          open(URI(url).toURL())
36      } catch (e: IOException) {                 // MalformedURLException is an IOException
37          return FetchResult.Failed("cannot open $url: $e")
38      } catch (e: IllegalArgumentException) {    // URI(...) complains about a text that is not a URL
39          return FetchResult.Failed("not a valid URL: $url")
40      } catch (e: java.net.URISyntaxException) {
41          return FetchResult.Failed("not a valid URL: $url")
42      }
```

- **Línea 31**: si ya nos cancelaron, ni se empieza. Es lo primero: no se toca la red.
- **Línea 32**: se calcula **el instante límite**: ahora + 30 s. `totalTimeoutMs * 1_000_000` convierte milisegundos en nanosegundos (1 ms = 1 000 000 ns), porque `nanoTime` está en nanosegundos. El reloj empieza **antes** de conectar: el tiempo de la conexión también cuenta.
- **Líneas 34–42**: se convierte el texto en una `URL`. Se pasa por `URI(url)` primero porque el constructor `URL(String)` está **obsoleto** en las versiones recientes de Java; `URI(url).toURL()` es la ruta recomendada. Tres cosas pueden fallar y las tres se convierten en `Failed` en lugar de en una excepción:
  - `IOException` (línea 36): incluye `MalformedURLException` (un protocolo desconocido) o lo que lance `open`.
  - `IllegalArgumentException` (línea 38): `URI("")` es válido pero **no es absoluta**, y `toURL()` protesta con esta excepción.
  - `URISyntaxException` (línea 40): el texto tiene caracteres que no caben en una URL (`"not a url at all"`, por los espacios). No es una `IOException` sino una `Exception` normal, por eso hay un `catch` aparte.
- El orden de los `catch` no importa aquí porque ninguna de las tres excepciones es subclase de otra.

### 4.3 `fetch`: configurar y conectar

```kotlin
// HttpUrlConnectionFetcher.kt · líneas 44–51
44  try {
45      connection.connectTimeout = connectTimeoutMs
46      connection.readTimeout = readTimeoutMs
47      connection.requestMethod = "GET"
48      connection.useCaches = false
49      // Without this, the server may compress the body and the size we read stops matching Content-Length.
50      connection.setRequestProperty("Accept-Encoding", "identity")
51      connection.setRequestProperty("User-Agent", "SwiftyProteins/1.0 (42 school project)")
```

- **Línea 44**: empieza el `try` cuyo `finally` (línea 69) garantiza la desconexión.
- **Líneas 45–46**: los dos primeros relojes. **Hay que fijarlos antes de conectar.** Si no se fijan, el valor por defecto de la plataforma puede ser **esperar sin límite** (en Java el valor 0 significa «infinito»; el valor por defecto en Android no se ha verificado): el indicador de carga giraría para siempre.
- **Línea 47**: `GET` es el método por defecto, pero se escribe explícito para que se lea.
- **Línea 48, `useCaches = false`**: no usar la caché de HTTP. Es la causa de las cabeceras `Cache-Control: no-cache` y `Pragma: no-cache` que se vieron en la captura de la sección 2 del documento 06. Evita que un fallo se **quede guardado**.
- **Línea 49–50, `Accept-Encoding: identity`**: pedir el cuerpo **sin comprimir**. Si el servidor comprimiera, `Content-Length` contaría los bytes comprimidos y la comparación de la sección 4.5 dejaría de valer.
- **Línea 51, `User-Agent`**: identifica la app. Opcional, pero muchos servidores agradecen saber quién les pide.

```kotlin
// HttpUrlConnectionFetcher.kt · líneas 53–65
53  // ---- phase 1: connect, send the request, read the status line and the headers -------------
54  val status = try {
55      connection.responseCode   // this call is what actually connects
56  } catch (e: SocketTimeoutException) {
57      return FetchResult.Timeout
58  } catch (e: UnknownHostException) {
59      return FetchResult.NoConnection("unknown host: ${e.message}")
60  } catch (e: SocketException) {      // ConnectException and NoRouteToHostException are SocketExceptions
61      return FetchResult.NoConnection(e.toString())
62  } catch (e: IOException) {
63      return FetchResult.Failed(e.toString())
64  }
65  if (status !in 200..299) return FetchResult.HttpError(status)
```

- **Línea 55, `connection.responseCode`**: esta lectura aparentemente inocente es **la que conecta de verdad**: resuelve el nombre del servidor, abre el TCP, negocia TLS, envía la petición y lee la línea de estado. Aquí se producen casi todos los fallos de red.
- **Cuatro `catch`**. Todas las excepciones capturadas son `IOException` o subclases; por eso la **general va la última** (si fuera la primera se tragaría a las demás). Entre las tres específicas el orden no cambia el resultado, porque no son subclases unas de otras:
  - **Línea 56, `SocketTimeoutException`** → `Timeout`. Es una subclase de `InterruptedIOException`, **no** de `SocketException`: por eso no la recoge el `catch` de la línea 60 y necesita su propia rama. Sin ella, el tiempo agotado caería en el `catch` de la línea 62 y se mostraría como un fallo genérico (al quitar esa rama a propósito fallan 3 pruebas: sección 12).
  - **Línea 58, `UnknownHostException`** → `NoConnection`. «No sé qué dirección tiene `files.rcsb.org`»: lo que pasa en modo avión.
  - **Línea 60, `SocketException`** → `NoConnection`. Cubre `ConnectException` («conexión rechazada») y `NoRouteToHostException` («no hay ruta»), que son subclases suyas.
  - **Línea 62, `IOException`** → `Failed`. Todo lo demás de la red, incluido `SSLHandshakeException` (certificado inválido). Es el último y más general.
- **Línea 65**: si el estado **no** es 2xx se devuelve `HttpError(código)` **sin tocar el cuerpo** (ver el final de la sección 2: pedir `inputStream` aquí lanzaría una excepción). La redirección (3xx) no llega aquí en la práctica: la librería la sigue sola y nos entrega el resultado final.

### 4.4 `fetch`: el `finally` y la fase 2

```kotlin
// HttpUrlConnectionFetcher.kt · líneas 67–72
67          // ---- phase 2: the body -----------------------------------------------------------------------------
68          return readBody(connection, deadline, isCancelled)
69      } finally {
70          connection.disconnect()
71      }
72  }
```

- **Línea 68**: se delega la lectura del cuerpo y se devuelve su resultado.
- **Línea 70, `disconnect()` en el `finally`**: se ejecuta tras el éxito, tras cualquier `return` anterior (incluidos los de los `catch`) y tras una excepción. Libera el socket aunque la lectura se haya abandonado a medias. Esto **no tiene prueba que falle si se borra**: un `disconnect` olvidado no cambia ningún resultado visible (sección 12).

### 4.5 `readBody`: el cuerpo, con tres vigilantes

```kotlin
// HttpUrlConnectionFetcher.kt · líneas 74–81
74  private fun readBody(connection: HttpURLConnection, deadline: Long, isCancelled: () -> Boolean): FetchResult {
75      val declared = connection.contentLengthLong          // -1 when the server does not say
76      if (declared > maxBytes) return FetchResult.TooLarge(maxBytes)
77      // Only trust the announced length when nothing was compressed on the way.
78      val checkLength = declared >= 0 && connection.contentEncoding == null
79
80      val body = ByteArrayOutputStream(if (declared in 0..maxBytes) declared.toInt() else 8_192)
81      val buffer = ByteArray(8_192)
```

- **Línea 75, `contentLengthLong`**: el `Content-Length` de la respuesta, o **-1** si no vino (`chunked`). Se usa la versión `Long` porque la `Int` se desborda con más de 2 GB.
- **Línea 76**: si **ya sabemos** por la cabecera que es demasiado grande, se rechaza **sin leer ni un byte**. Es `>` y no `>=`: un cuerpo de exactamente `maxBytes` es válido (lo comprueba `aBodyExactlyAtTheLimitIsAccepted`).
- **Línea 78**: `checkLength` decide si se podrá comparar el tamaño recibido con el anunciado: hay que **haber recibido** `Content-Length` (`declared >= 0`) **y** que el cuerpo **no** venga con una codificación (`contentEncoding == null`). Con compresión, `Content-Length` cuenta otros bytes (la prueba `whenTheServerCompressesAnyway...` fija esta regla).
- **Línea 80**: se crea el saco con **capacidad inicial**: lo anunciado si cabe, 8 KB si no. Evita copiar el contenido al crecer. Un servidor que anuncie 1 999 999 bytes y no los mande solo nos cuesta esa reserva, nunca más del límite.
- **Línea 81**: el búfer de lectura de 8 192 bytes: cada vuelta del bucle trae **como mucho** eso.

```kotlin
// HttpUrlConnectionFetcher.kt · líneas 82–97
82  try {
83      connection.inputStream.use { input ->
84          while (true) {
85              if (isCancelled()) return FetchResult.Cancelled
86              if (System.nanoTime() - deadline > 0) return FetchResult.Timeout
87              val count = input.read(buffer)
88              if (count == -1) break
89              if (body.size() + count > maxBytes) return FetchResult.TooLarge(maxBytes)
90              body.write(buffer, 0, count)
91          }
92      }
93  } catch (e: SocketTimeoutException) {
94      return FetchResult.Timeout
95  } catch (e: IOException) {          // the connection broke while the body was arriving
96      return FetchResult.Incomplete(declared, body.size().toLong())
97  }
```

El bucle `while (true)` es el corazón. En **cada vuelta**, **antes de leer**:

1. **Línea 85, ¿cancelado?** → `Cancelled`.
2. **Línea 86, ¿venció el reloj total?** → `Timeout`. `System.nanoTime() - deadline > 0` significa «ahora está **después** del límite». Por esta línea, un servidor que gotea un byte cada 100 ms no puede alargar la descarga más allá de 30 s (más lo que tarde la lectura en curso: sección 4 del documento 06).
3. **Línea 87, `input.read(buffer)`**: **aquí se queda esperando** hasta que llegan datos (hasta `readTimeoutMs`, el segundo reloj). Devuelve cuántos bytes llegaron.
4. **Línea 88**: `-1` es «fin del cuerpo»: se sale del bucle con `break`.
5. **Línea 89**: ¿al añadir esto superaríamos el límite? → `TooLarge`. Se comprueba **antes** de escribir, para no acumular más de `maxBytes`. Cubre al servidor que **no anunció** el tamaño o que mintió.
6. **Línea 90**: se guardan los `count` primeros bytes del búfer.

Importante: **el `use` de la línea 83 cierra el flujo siempre**, y los `return` de las líneas 85, 86 y 89 son retornos no locales (sección 2): salen de `readBody`, no solo del bloque.

**Los `catch` de las líneas 93–97:**

- **93, `SocketTimeoutException`** → `Timeout`: el segundo reloj saltó durante un `read`. Va **antes** que el siguiente porque también es una `IOException`.
- **95, `IOException`** → `Incomplete(declared, body.size())`: la conexión se rompió **mientras llegaba el cuerpo**. Los bytes que habían llegado se cuentan, para el registro.

```kotlin
// HttpUrlConnectionFetcher.kt · líneas 99–102
 99  if (checkLength && body.size() < declared) {
100      return FetchResult.Incomplete(declared, body.size().toLong())
101  }
102  return FetchResult.Body(String(body.toByteArray(), Charsets.UTF_8))
```

- **Líneas 99–101**: el caso silencioso. La lectura terminó **sin error** (`-1`) pero llegaron **menos bytes** de los anunciados: el servidor cerró «limpiamente» antes de tiempo. Es el motivo de toda la comprobación de `Content-Length` (documento 06, sección 5). Sin estas tres líneas, un fichero cortado pasaría por completo.
- **Línea 102**: el texto se decodifica **como UTF-8** (`Charsets.UTF_8`) y se devuelve `Body`. Si se omitiera el juego de caracteres, se usaría el de la plataforma, que puede variar.

---

## 5. `LigandLoadResult.kt`: qué ve el resto de la app

```kotlin
// LigandLoadResult.kt · líneas 6–16
 6  /**
 7   * The outcome of loading one ligand. The screen turns each case into something the user sees; none of
 8   * them is an exception, so none of them can crash the app.
 9   */
10  sealed interface LigandLoadResult {
11      data class Success(val molecule: Molecule) : LigandLoadResult
12      data class Failure(val error: LoadError) : LigandLoadResult
13
14      /** The caller asked to stop. The screen shows nothing for it. */
15      data object Cancelled : LigandLoadResult
16  }
```

Tres posibilidades y solo tres:

- **`Success(molecule)`**: hay una molécula lista para dibujar.
- **`Failure(error)`**: no la hay, y `error` dice por qué.
- **`Cancelled`**: no la hay **y nadie la espera**. Está **separado de `Failure`** a propósito: la pantalla no debe enseñar nada para un `Cancelled`; si fuera un `Failure`, algún día alguien enseñaría «Error» al usuario que acaba de pulsar «atrás».

```kotlin
// LigandLoadResult.kt · líneas 18–53
18  /**
19   * Why a ligand could not be loaded. The task asks for four user-visible errors; here is where each lives:
20   * no network = [NoConnection], 404 = [NotFound], corrupt file = [Corrupt] / [Incomplete] / [TooLarge],
21   * timeout = [Timeout]. The rest exist so that NOTHING is left unexplained.
22   */
23  sealed interface LoadError {
24      /** No network, unknown host, connection refused. */
25      data object NoConnection : LoadError
26
27      /** Too slow (connecting, waiting for data, or the whole download). */
28      data object Timeout : LoadError
29
30      /** The server says the ligand does not exist (404, 410). */
31      data object NotFound : LoadError
32
33      /** The server failed or refused (500, 503, 429...). */
34      data class ServerError(val code: Int) : LoadError
35
36      /** The download was cut before it ended. */
37      data object Incomplete : LoadError
38
39      /** The body is bigger than any ligand file can be. */
40      data class TooLarge(val maxBytes: Int) : LoadError
41
42      /** The body arrived whole but it is not a usable CIF file. The [failure] says exactly why. */
43      data class Corrupt(val failure: CifFailure) : LoadError
44
45      /** The id is not a plausible ligand id; no request was made. */
46      data class InvalidId(val id: String) : LoadError
47
48      /** Another network problem (TLS, malformed URL...). */
49      data class NetworkError(val reason: String) : LoadError
50
51      /** A bug of ours. Safety net; it should never appear. */
52      data class Unexpected(val description: String) : LoadError
53  }
```

`LoadError` es el catálogo de motivos. El comentario de las líneas 18–22 lo resume: los **cuatro errores obligatorios** del enunciado viven aquí así:

| Obligatorio | Variante(s) | Mensaje propuesto (más adelante, en la interfaz) |
|-------------|-------------|--------------------------------------------------|
| Sin red | `NoConnection` | «Sin conexión» |
| 404 | `NotFound` | «Ligando no encontrado» |
| Fichero corrupto | `Corrupt(failure)`, `Incomplete`, `TooLarge` | «Fichero corrupto» |
| Tiempo agotado | `Timeout` | «Tiempo de espera agotado» |

Las otras (`ServerError`, `InvalidId`, `NetworkError`, `Unexpected`) existen para que **nada quede sin explicar**: un `500` no es un «no encontrado», y un certificado inválido no es una «falta de conexión».
`Corrupt` lleva dentro el `CifFailure` del parser: la interfaz solo dirá «Fichero corrupto», pero el registro sabrá si fue `TruncatedRow` o `NoDataBlock`.
Las variantes con **datos** son `data class` (`ServerError(code)`, `TooLarge(maxBytes)`, `Corrupt(failure)`, `InvalidId(id)`, `NetworkError(reason)`, `Unexpected(description)`); las **sin datos**, `data object`.

---

## 6. `LigandRepository.kt`: red + parser

### 6.1 La clase

```kotlin
// LigandRepository.kt · líneas 6–18
 6  /**
 7   * Downloads a ligand and turns it into a Molecule. It glues two things: the network ([HttpFetcher]) and the
 8   * parser ([CifParser]), and translates every outcome into a [LigandLoadResult].
 9   *
10   * It BLOCKS the calling thread (network + parsing): call it from a background thread, never from the main one.
11   * It never throws because of the network or the file.
12   */
13  class LigandRepository(
14      private val fetcher: HttpFetcher,
15      private val baseUrl: String = DEFAULT_BASE_URL,
16  ) {
17
18      fun load(rawId: String, isCancelled: () -> Boolean = { false }): LigandLoadResult {
```

- **Líneas 13–16**: el constructor recibe **un `HttpFetcher`** (no la clase concreta: se le puede pasar uno falso) y la **dirección base**, con la de RCSB por defecto. Las pruebas de extremo a extremo pasan la dirección del servidor casero.
- **Línea 18**: `load(rawId, isCancelled)`. `rawId` se llama «raw» (en crudo) porque **aún no está validado**. Devuelve siempre un `LigandLoadResult`. Los comentarios de las líneas 10–11 repiten las dos promesas: **bloquea** y **no lanza**.

### 6.2 Normalizar y validar el identificador

```kotlin
// LigandRepository.kt · líneas 19–21
19  val id = rawId.trim().uppercase()
20  // The id goes INSIDE a URL: only letters and digits are allowed, so "../x" or "a?b" never reach the network.
21  if (!ID_PATTERN.matches(id)) return failure(LoadError.InvalidId(rawId))
```

- **Línea 19**: `trim()` quita espacios por los dos lados y `uppercase()` pasa a mayúsculas: `" atp "` se convierte en `"ATP"`. (Los ids de `ligands.txt` están todos en mayúsculas y la URL de RCSB se construye con ellos; la **búsqueda** de la lista será insensible a mayúsculas por su cuenta.)
- **Línea 21**: `ID_PATTERN.matches(id)` exige que **todo** el texto encaje con `[A-Z0-9]{1,5}`: de 1 a 5 caracteres, cada uno una letra mayúscula o un dígito. Si no encaja, se devuelve `InvalidId(rawId)` **sin hacer ninguna petición**. Se guarda el valor **original** (`rawId`) para poder mostrarlo o registrarlo tal cual lo escribieron.
- Con esto `../x`, `a?b=1`, `A/B`, un salto de línea o la cadena vacía **no llegan nunca a formar una URL** (la prueba `invalidIdsAreRejectedWithoutAnyRequest` comprueba además que no se hizo ninguna petición).

### 6.3 Pedir y traducir

```kotlin
// LigandRepository.kt · líneas 23–41
23      return try {
24          when (val fetched = fetcher.fetch("$baseUrl$id.cif", isCancelled)) {
25              is FetchResult.Body -> parse(fetched.text)
26              is FetchResult.HttpError -> failure(
27                  if (fetched.code == 404 || fetched.code == 410) LoadError.NotFound
28                  else LoadError.ServerError(fetched.code),
29              )
30              is FetchResult.NoConnection -> failure(LoadError.NoConnection)
31              is FetchResult.Timeout -> failure(LoadError.Timeout)
32              is FetchResult.Incomplete -> failure(LoadError.Incomplete)
33              is FetchResult.TooLarge -> failure(LoadError.TooLarge(fetched.maxBytes))
34              is FetchResult.Failed -> failure(LoadError.NetworkError(fetched.reason))
35              is FetchResult.Cancelled -> LigandLoadResult.Cancelled
36          }
37      } catch (e: Exception) {
38          // A bug of ours, never the network (the fetcher does not throw). It becomes a value instead of a crash.
39          failure(LoadError.Unexpected(e.toString()))
40      }
41  }
```

- **Línea 23**: `return try { ... } catch ...`: **toda** la parte que puede fallar está dentro de un `try` que devuelve un valor.
- **Línea 24**: se construye la URL (`"$baseUrl$id.cif"`: base + id + `.cif`) y se pide. `when (val fetched = ...)` guarda el resultado en `fetched` y lo examina en el mismo paso.
- **Línea 25, `Body`** → se llama a `parse` (sección 6.4).
- **Líneas 26–29, `HttpError`**: el único sitio donde se interpretan los **códigos**: `404` y `410` («ya no existe») son `NotFound`; cualquier otro es `ServerError(código)`.
- **Líneas 30–34**: traducciones directas. Cada `FetchResult` tiene su `LoadError`: `NoConnection`, `Timeout`, `Incomplete`, `TooLarge(maxBytes)`, `Failed` → `NetworkError`. Nótese que `NoConnection(reason)` pierde el `reason` al convertirse en `LoadError.NoConnection` (es un `data object`): el motivo detallado ya no se necesita para el usuario. **Si se quisiera registrar, habría que hacerlo en el descargador**.
- **Línea 35, `Cancelled`** → `LigandLoadResult.Cancelled` (no es un `Failure`).
- Como el `when` es una **expresión** y `FetchResult` es `sealed`, **faltar un caso no compila**.
- **Líneas 37–40: la red de seguridad.** Si algo que no previmos lanza una excepción (un fallo nuestro; el descargador **no** lanza), se convierte en `Unexpected` en lugar de cerrar la app. **No se captura `Error`** (como `OutOfMemoryError`): ahí no se puede seguir con garantías; para eso están los límites de tamaño.

### 6.4 Del cuerpo a la molécula

```kotlin
// LigandRepository.kt · líneas 43–48
43  private fun parse(text: String): LigandLoadResult = when (val parsed = CifParser.parse(text)) {
44      is ParseResult.Success -> LigandLoadResult.Success(parsed.molecule)
45      is ParseResult.Failure -> failure(LoadError.Corrupt(parsed.failure))
46  }
47
48  private fun failure(error: LoadError) = LigandLoadResult.Failure(error)
```

- **Línea 43**: `CifParser.parse` ya no lanza (documento 03a, sección 10), así que aquí solo se mira cuál de sus dos resultados llegó. `Success` → `LigandLoadResult.Success(molécula)`; `Failure(motivo)` → `Failure(Corrupt(motivo))`.
- **Línea 48**: una función de una línea que evita repetir `LigandLoadResult.Failure(...)` en cada rama.

### 6.5 Constantes

```kotlin
// LigandRepository.kt · líneas 50–56
50      companion object {
51          const val DEFAULT_BASE_URL = "https://files.rcsb.org/ligands/view/"
52
53          // Real ids are 1 to 3 letters/digits ("001", "ATP", "HEM"); the dictionary allows up to 5.
54          private val ID_PATTERN = Regex("[A-Z0-9]{1,5}")
55      }
56  }
```

La dirección base **termina en `/`** y el id y `.cif` se le pegan detrás (por eso las pruebas usan una base que también termina en `/`). `Regex` está creada **una vez** (en el `companion object`), no en cada llamada.

---

## 7. Simulaciones de extremo a extremo

Cada simulación sigue una llamada `repository.load(...)`. Los tiempos son los de una prueba (relojes reducidos) y salen de las pruebas reales.

### 7.1 Todo bien: ligando `001`

| Paso | Dónde | Qué ocurre |
|-----:|-------|------------|
| 1 | `load("001")` línea 19 | `id = "001"`; encaja con `[A-Z0-9]{1,5}` |
| 2 | línea 24 | `fetch("http://127.0.0.1:PUERTO/ligands/view/001.cif")` |
| 3 | `fetch` línea 31 | `isCancelled()` es `false`; se calcula el límite (ahora + 30 s) |
| 4 | líneas 34–51 | se abre la `URL`, se fijan relojes y cabeceras (nada de red todavía) |
| 5 | línea 55 | `responseCode`: se conecta, se envía `GET`, llega `200` y `Content-Length: 16067` |
| 6 | línea 65 | 200 está en 200..299: sigue |
| 7 | `readBody` 75–81 | `declared = 16067` (≤ 2 000 000); `checkLength = true`; saco de 16 067 bytes |
| 8 | bucle 84–91 | vuelta 1: lee 8 192 B; vuelta 2: lee 7 875 B; vuelta 3: `read` devuelve -1 → `break` |
| 9 | línea 99 | `16067 < 16067`: falso, no es `Incomplete` |
| 10 | línea 102 | `Body(texto)`; vuelve el `finally`, `disconnect()` |
| 11 | repositorio 25 → 43 | `CifParser.parse(texto)` → `Success(molécula de 87 átomos, 90 enlaces)` |
| 12 | resultado | `LigandLoadResult.Success(molécula)` |

(El tamaño `16 067` es el del fichero real; el reparto en bloques de 8 192 es el máximo posible, en la práctica la red entrega trozos de otro tamaño.)

### 7.2 El ligando no existe

El servidor responde `404 Not Found`. Pasos 1–5 como antes; en el 6, `404` no está en 200..299, y la línea 65 devuelve `HttpError(404)` **sin leer el cuerpo**. El `finally` desconecta.
El repositorio (líneas 26–29) lo traduce a `Failure(NotFound)`. La interfaz mostrará «Ligando no encontrado».

### 7.3 El servidor gotea

El servidor envía la cabecera con `Content-Length: 100` y después **1 byte cada 100 ms**; el límite total es de 0,5 s y el de lectura de 2 s.

| Instante | Qué hace el bucle |
|---------:|--------------------|
| 0,00 s | cabecera recibida. Vuelta 1: ¿cancelado? no. ¿Pasó el límite? no. `read` espera… |
| 0,10 s | `read` devuelve 1 byte. Se guarda. Vuelta 2 |
| 0,20 s | 1 byte. Vuelta 3 |
| 0,30 s | 1 byte. Vuelta 4 |
| 0,40 s | 1 byte. Vuelta 5 |
| 0,50 s | 1 byte. Vuelta 6: **`nanoTime() - deadline > 0` es verdadero** → `Timeout` |

Resultado: `FetchResult.Timeout` → `LoadError.Timeout`. Sin la línea 86, esta descarga tardaría 10 s en un test y **horas** con un servidor real que mandara un byte cada 9 s.

### 7.4 La conexión se corta

El servidor anuncia `Content-Length: 1000`, envía 100 bytes y **cierra**. La lectura recibe los 100 y después `-1`: **sin error**. Se sale del bucle; la línea 99 compara
`checkLength` (verdadero) y `100 < 1000` → `Incomplete(1000, 100)` → `LoadError.Incomplete` → «Fichero corrupto».
Si el servidor no hubiera anunciado el tamaño (`chunked`) y hubiera cortado sin cerrar el último trozo, la **librería** habría lanzado `IOException` dentro del `read` y el `catch` de la línea 95 habría devuelto el mismo `Incomplete` (con `expectedBytes = -1`).

### 7.5 Un portal cautivo

La red responde `200 OK` con `<html>Proxy login required</html>`. Los pasos 1–10 salen bien: `Body("<html>…")`. El **parser** busca el bloque `data_` y no lo encuentra: `Failure(NoDataBlock)` →
`Failure(Corrupt(NoDataBlock))` → «Fichero corrupto». La capa de red no tuvo que saber que aquello no era un CIF.

---

## 8. Las pruebas: qué hay y cómo se ejecutan

| Fichero | Pruebas | Qué verifica |
|---------|--------:|--------------|
| `HttpUrlConnectionFetcherTest.kt` | 22 | El descargador **real** contra un servidor casero: éxito, estados, fallos de conexión, relojes, cuerpos incompletos o demasiado grandes, cancelación |
| `LigandRepositoryTest.kt` | 20 | El repositorio con un descargador **falso**, y de extremo a extremo con el real |

Junto a las 88 del parser son **130** en total (Android Studio cuenta además `ExampleUnitTest`, la de plantilla: 131).

**Ejecutar desde Android Studio.** Abrir el fichero de pruebas y pulsar el triángulo verde junto al nombre de la clase, o con el botón derecho sobre la carpeta `app/src/test/java` → *Run Tests*. Los resultados salen en la ventana *Run*.

**Ejecutar desde la terminal** (en la raíz del proyecto):

```
./gradlew testDebugUnitTest
```

Las pruebas se ejecutan **en la JVM del ordenador**, no en el móvil ni en el emulador: por eso la capa de red **no usa nada de `android.*`**. Hay que dar un mensaje honesto: estas pruebas se han ejecutado con `kotlinc` y JUnit sueltos, **no** bajo el Gradle del equipo (AGP 9.4.1); y la primera ejecución en el Gradle del equipo **sí** mostró una diferencia: una prueba dependía de cómo reacciona el JDK a un cuerpo que termina antes de lo anunciado (ver sección 10).

Dos cosas que pueden confundir:

- Las pruebas de **tiempo** (`aServerThatNeverAnswersIsATimeout`, `aServerThatDripsOneByteAtATimeHitsTheTotalLimit`...) usan límites de 0,3–0,5 s y comprueban que el tiempo real está **dentro de un intervalo**. En un ordenador muy cargado podrían fallar de forma esporádica; si pasa, volver a ejecutar antes de sospechar del código.
- Las pruebas abren **puertos reales** en `127.0.0.1` (solo accesible desde el propio ordenador). Un cortafuegos muy estricto podría molestar; no se ha visto.

---

## 9. `TestHttpServer.kt`: el servidor casero

Probar contra Internet sería lento y no reproducible; este servidor deja **provocar a voluntad** los fallos de la red.

```kotlin
// TestHttpServer.kt · líneas 9–40
 9  /**
10   * A tiny HTTP server for the tests, written with plain sockets so it works in any JVM and gives TOTAL control:
11   * a test decides byte by byte what to send, how slowly, and when to hang up. Each connection serves ONE request.
12   *
13   * It listens only on the loopback address (127.0.0.1) and on a port chosen by the system.
14   */
15  class TestHttpServer(private val handler: (request: String, socket: Socket) -> Unit) : AutoCloseable {
16
17      private val server = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
18      val port: Int get() = server.localPort
19
20      /** The text of every request received (request line and headers), in order. */
21      val requests = CopyOnWriteArrayList<String>()
22
23      @Volatile
24      private var running = true
25
26      init {
27          Thread {
28              while (running) {
29                  val socket = try {
30                      server.accept()
31                  } catch (e: IOException) {
32                      break   // the server was closed
33                  }
34                  Thread { serve(socket) }.apply { isDaemon = true }.start()
35              }
36          }.apply { isDaemon = true }.start()
37      }
38
39      /** `http://127.0.0.1:PORT/ligands/view/`, like the real base URL but local. */
40      val baseUrl: String get() = "http://127.0.0.1:$port/ligands/view/"
```

- **Línea 15**: el constructor recibe el **manejador**: una función `(petición, socket) -> Unit` que cada prueba escribe para decidir qué responder. `AutoCloseable` permite usar `.use { }` y que se cierre solo.
- **Línea 17 (`ServerSocket(0, 50, InetAddress.getLoopbackAddress())`)**: `0` = «elige tú un puerto libre»; `50` = longitud de la cola de espera; y la dirección **de bucle local** (`127.0.0.1`) hace que **solo el propio ordenador** pueda conectarse. El puerto elegido se lee de `server.localPort` (línea 18).
- **Línea 21, `requests`**: una `CopyOnWriteArrayList` (lista segura cuando varios hilos la usan a la vez) con el texto de cada petición recibida: las pruebas la leen para ver **qué salió por el cable**.
- **Líneas 26–37**: un hilo que **espera conexiones** (`server.accept()` bloquea hasta que llega una) y, por cada una, lanza otro hilo que la atiende (línea 34). Ambos son `isDaemon = true`: **no impiden que la JVM termine** si una prueba se olvida de cerrar el servidor.
- **Líneas 31–32**: cuando se cierra el servidor, el `accept()` pendiente lanza `IOException`; el `break` sale del bucle y el hilo termina.

```kotlin
// TestHttpServer.kt · líneas 42–64
42      private fun serve(socket: Socket) {
43          try {
44              socket.use {
45                  val input = it.getInputStream()
46                  val head = StringBuilder()
47                  while (!head.endsWith("\r\n\r\n")) {
48                      val b = input.read()
49                      if (b == -1) return
50                      head.append(b.toChar())
51                  }
52                  requests += head.toString()
53                  handler(head.toString(), it)
54              }
55          } catch (e: IOException) {
56              // the client hung up while we were writing: normal in the tests that cut or time out
57          }
58      }
59
60      override fun close() {
61          running = false
62          server.close()
63      }
64  }
```

- **`serve` (líneas 42–58)**: lee **byte a byte** hasta la línea vacía final (`\r\n\r\n`), que marca el fin de la cabecera de la petición; la guarda en `requests` (línea 52) y llama al manejador (línea 53). El `socket.use { }` cierra la conexión al terminar.
- **El `catch (IOException)` (línea 55)**: cuando el cliente cuelga a mitad de una respuesta (pruebas de tiempo y de cancelación), la escritura del servidor falla; no es un error de la prueba, es lo esperado.
- **`close()` (líneas 60–63)**: marca `running = false` y cierra el socket del servidor.

```kotlin
// TestHttpServer.kt · líneas 66–99
66  /** The path of a request: from `GET /ligands/view/001.cif HTTP/1.1` it returns `/ligands/view/001.cif`. */
67  fun requestPath(request: String): String = request.lineSequence().first().split(" ")[1]
68
69  /** Writes a whole response and flushes it. [lengthHeader] lets a test lie about the length. */
70  fun Socket.respond(
71      status: String = "200 OK",
72      body: ByteArray = ByteArray(0),
73      lengthHeader: Long? = body.size.toLong(),
74      extraHeaders: List<String> = emptyList(),
75  ) {
76      val out = getOutputStream()
77      val head = buildString {
78          append("HTTP/1.1 $status\r\n")
79          if (lengthHeader != null) append("Content-Length: $lengthHeader\r\n")
80          extraHeaders.forEach { append("$it\r\n") }
81          append("Connection: close\r\n\r\n")
82      }
83      out.write(head.toByteArray())
84      out.write(body)
85      out.flush()
86  }
87
88  /** A chunked response (`Transfer-Encoding: chunked`): each piece is announced with its size in hexadecimal. */
89  fun Socket.respondChunked(pieces: List<ByteArray>, finish: Boolean = true) {
90      val out = getOutputStream()
91      out.write("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\nConnection: close\r\n\r\n".toByteArray())
92      for (piece in pieces) {
93          out.write("${piece.size.toString(16)}\r\n".toByteArray())
94          out.write(piece)
95          out.write("\r\n".toByteArray())
96      }
97      if (finish) out.write("0\r\n\r\n".toByteArray())
98      out.flush()
99  }
```

- **`requestPath`**: de `GET /ligands/view/001.cif HTTP/1.1` extrae `/ligands/view/001.cif` (la segunda palabra de la primera línea). Sirve para que un manejador responda distinto según la ruta.
- **`Socket.respond`**: una **función de extensión** (añade un método a `Socket` desde fuera). Escribe la línea de estado, `Content-Length` (que **se puede falsear** con `lengthHeader`, clave para las pruebas de «dice una cosa y manda otra»), las cabeceras extra, `Connection: close` y el cuerpo.
- **`Socket.respondChunked`**: envía una respuesta con `Transfer-Encoding: chunked`: cada trozo va precedido de su tamaño **en hexadecimal** (`5\r\n`) y seguido de `\r\n`; el final es un trozo de tamaño 0. Con `finish = false` **no se manda el final**: simula una respuesta que se queda a medias.

---

## 10. Las pruebas del descargador, una a una

Cada prueba crea su servidor con `TestHttpServer { petición, socket -> ... }.use { servidor -> ... }`. El `use` **cierra el servidor al terminar**, pase lo que pase.
El auxiliar `fetcher(...)` crea un descargador con relojes cortos (2 s) y `fetch(servidor, ...)` pide `/x.cif` al servidor.

**Éxito**

```kotlin
// HttpUrlConnectionFetcherTest.kt · líneas 33–38
33  @Test
34  fun aSuccessfulResponseGivesItsBodyDecodedAsUtf8() {
35      TestHttpServer { _, s -> s.respond(body = "Ångström ✓".toByteArray(Charsets.UTF_8)) }.use { server ->
36          assertEquals(FetchResult.Body("Ångström ✓"), fetch(server))
37      }
38  }
```

Envía `"Ångström ✓"` en UTF-8 y espera el mismo texto. Con otra codificación, los caracteres `Å`, `ö` y `✓` saldrían mal (lo prueba la avería con `ISO_8859_1`).

```kotlin
// HttpUrlConnectionFetcherTest.kt · líneas 54–65
54  @Test
55  fun aRedirectIsFollowed() {
56      TestHttpServer { request, s ->
57          if (requestPath(request) == "/x.cif") {
58              s.respond("302 Found", lengthHeader = 0, extraHeaders = listOf("Location: /moved.cif"))
59          } else {
60              s.respond(body = "arrived".toByteArray())
61          }
62      }.use { server ->
63          assertEquals(FetchResult.Body("arrived"), fetch(server))
64      }
65  }
```

Pide `/x.cif`, el servidor responde `302` con `Location: /moved.cif`, y el descargador **recibe el cuerpo de la segunda ruta**. Verifica que la redirección se sigue (aquí http a http; la de https a https **no** se ha probado con servidor real).

```kotlin
// HttpUrlConnectionFetcherTest.kt · líneas 67–76
67  @Test
68  fun theRequestAsksForNoCompressionAndIdentifiesItself() {
69      TestHttpServer { _, s -> s.respond(body = "x".toByteArray()) }.use { server ->
70          fetch(server)
71          val request = server.requests.single()
72          assertTrue(request, request.startsWith("GET /x.cif HTTP/1.1"))
73          assertTrue(request, request.contains("Accept-Encoding: identity"))
74          assertTrue(request, request.contains("User-Agent: SwiftyProteins"))
75      }
76  }
```

Comprueba **lo que sale por el cable**: la línea de petición y las dos cabeceras propias. Si se borra la línea 50 del descargador, esta prueba falla.

**El servidor dice que no**

```kotlin
// HttpUrlConnectionFetcherTest.kt · líneas 80–85
80  @Test
81  fun status404IsAnHttpError() {
82      TestHttpServer { _, s -> s.respond("404 Not Found", body = "nope".toByteArray()) }.use { server ->
83          assertEquals(FetchResult.HttpError(404), fetch(server))
84      }
85  }
```

**No se puede alcanzar el servidor**

```kotlin
// HttpUrlConnectionFetcherTest.kt · líneas 96–101
 96  @Test
 97  fun aClosedPortIsNoConnection() {
 98      val port = TestHttpServer { _, _ -> }.use { it.port }   // the server is closed when use ends
 99      val result = fetcher().fetch("http://127.0.0.1:$port/x.cif")
100      assertTrue("got $result", result is FetchResult.NoConnection)
101  }
```

Se arranca un servidor, se le pregunta su puerto y se **cierra**. Después se pide algo a ese puerto muerto: el sistema operativo responde «conexión rechazada» (`ConnectException`), que el descargador traduce a `NoConnection`.

```kotlin
// HttpUrlConnectionFetcherTest.kt · líneas 103–120
103  @Test
104  fun networkExceptionsAreTranslated() {
105      fun withFailure(e: IOException) = HttpUrlConnectionFetcher(open = { url ->
106          object : HttpURLConnection(url) {
107              override fun disconnect() {}
108              override fun usingProxy() = false
109              override fun connect() {}
110              override fun getResponseCode(): Int = throw e
111          }
112      }).fetch("http://example.test/x.cif")
113
114      assertTrue(withFailure(UnknownHostException("example.test")) is FetchResult.NoConnection)
115      assertTrue(withFailure(java.net.ConnectException("refused")) is FetchResult.NoConnection)
116      assertTrue(withFailure(java.net.NoRouteToHostException("no route")) is FetchResult.NoConnection)
117      assertEquals(FetchResult.Timeout, withFailure(SocketTimeoutException("connect timed out")))
118      assertTrue(withFailure(SSLHandshakeException("bad certificate")) is FetchResult.Failed)
119      assertTrue(withFailure(IOException("something else")) is FetchResult.Failed)
120  }
```

Lo que un servidor local **no puede** provocar (un nombre que no se resuelve, un certificado inválido) se simula con una **conexión falsa**: una subclase anónima de `HttpURLConnection` cuyo `getResponseCode()` **lanza la excepción** deseada.
Se le pasa al descargador por el punto de inyección `open`. La función local `withFailure(e)` devuelve el `FetchResult` para cada excepción. Se comprueba cada rama de los `catch` de las líneas 56–63 del descargador: `UnknownHost`, `Connect`, `NoRouteToHost` y `SocketException` en general → `NoConnection`; `SocketTimeout` → `Timeout`; `SSLHandshake` y la `IOException` genérica → `Failed`.
Lo que **no** prueba: que Android lance realmente esas excepciones en esas situaciones.

```kotlin
// HttpUrlConnectionFetcherTest.kt · líneas 122–126
122  @Test
123  fun aTextThatIsNotAUrlFailsWithoutThrowing() {
124      assertTrue(fetcher().fetch("not a url at all") is FetchResult.Failed)
125      assertTrue(fetcher().fetch("") is FetchResult.Failed)
126  }
```

**Servidores lentos**

```kotlin
// HttpUrlConnectionFetcherTest.kt · líneas 130–139
130  @Test
131  fun aServerThatNeverAnswersIsATimeout() {
132      TestHttpServer { _, _ -> Thread.sleep(3_000) }.use { server ->
133          val started = System.nanoTime()
134          val result = fetch(server, fetcher(readTimeoutMs = 300))
135          val millis = (System.nanoTime() - started) / 1_000_000
136          assertEquals(FetchResult.Timeout, result)
137          assertTrue("took $millis ms", millis in 250..2_500)
138      }
139  }
```

El servidor acepta la conexión y **se duerme 3 s** sin responder; el límite de lectura es de 0,3 s. Se mide el tiempo real: tiene que estar **entre 0,25 y 2,5 s**. La cota inferior demuestra que de verdad se esperó; la superior, que **no** se esperó los 3 s del servidor.

```kotlin
// HttpUrlConnectionFetcherTest.kt · líneas 153–171
153  @Test
154  fun aServerThatDripsOneByteAtATimeHitsTheTotalLimit() {
155      // Every single read succeeds well within the read timeout, so ONLY the total clock can stop this.
156      TestHttpServer { _, s ->
157          val out = s.getOutputStream()
158          out.write("HTTP/1.1 200 OK\r\nContent-Length: 100\r\nConnection: close\r\n\r\n".toByteArray())
159          repeat(100) {
160              out.write('a'.code)
161              out.flush()
162              Thread.sleep(100)
163          }
164      }.use { server ->
165          val started = System.nanoTime()
166          val result = fetch(server, fetcher(readTimeoutMs = 2_000, totalTimeoutMs = 500))
167          val millis = (System.nanoTime() - started) / 1_000_000
168          assertEquals(FetchResult.Timeout, result)
169          assertTrue("took $millis ms", millis in 450..2_500)
170      }
171  }
```

La prueba que justifica el tercer reloj: **cada lectura sale a tiempo** (100 ms frente a un límite de 2 s), así que solo el reloj total puede pararla (0,5 s). Si se borra la línea 86 del descargador, falla.

**Cuerpos incompletos y demasiado grandes**

```kotlin
// HttpUrlConnectionFetcherTest.kt · líneas 175–182
175  @Test
176  fun aBodyShorterThanItsContentLengthIsIncomplete() {
177      TestHttpServer { _, s ->
178          s.respond(body = ByteArray(100) { 'a'.code.toByte() }, lengthHeader = 1_000)   // promises 1000, sends 100
179      }.use { server ->
180          assertEquals(FetchResult.Incomplete(expectedBytes = 1_000, receivedBytes = 100), fetch(server))
181      }
182  }
```

```kotlin
// HttpUrlConnectionFetcherTest.kt · líneas 184–189
184  @Test
185  fun aChunkedBodyThatIsNeverFinishedIsIncomplete() {
186      TestHttpServer { _, s -> s.respondChunked(listOf("hello".toByteArray()), finish = false) }.use { server ->
187          assertEquals(FetchResult.Incomplete(expectedBytes = -1, receivedBytes = 5), fetch(server))
188      }
189  }
```

Sin `Content-Length` y sin el trozo final: la **librería** lanza `IOException` al leer, y el `catch` de la línea 95 lo convierte en `Incomplete(-1, 5)`.

**Dos pruebas con una conexión falsa (independientes del JDK).** Un cuerpo que termina antes de lo anunciado puede verse de **dos maneras** según el JDK: como un fin limpio (`read` devuelve `-1`) o como una `IOException`. La primera versión de la prueba de compresión suponía la primera y **falló en el Gradle del equipo** (no en el entorno donde se escribió). Por eso estas dos pruebas no usan red: una `HttpURLConnection` falsa responde exactamente lo que se le diga.

```kotlin
// HttpUrlConnectionFetcherTest.kt · líneas 205–221
205  /**
206   * A connection that answers with exactly what a test says, with no network and no JDK in between.
207   * The JDK decides by itself how a body that ends too early looks (a clean end, or an IOException), so the tests
208   * about WHAT OUR CODE DOES with the announced length use this one and do not depend on that.
209   */
210  private fun fakeConnection(announcedLength: Long, encoding: String?, body: ByteArray) =
211      HttpUrlConnectionFetcher(open = { url ->
212          object : HttpURLConnection(url) {
213              override fun disconnect() {}
214              override fun usingProxy() = false
215              override fun connect() {}
216              override fun getResponseCode() = 200
217              override fun getContentLengthLong() = announcedLength
218              override fun getContentEncoding() = encoding
219              override fun getInputStream() = java.io.ByteArrayInputStream(body)
220          }
221      }).fetch("http://example.test/x.cif")
```

```kotlin
// HttpUrlConnectionFetcherTest.kt · líneas 223–227
223  @Test
224  fun aBodyShorterThanAnnouncedIsIncompleteEvenWhenTheStreamEndsCleanly() {
225      val result = fakeConnection(announcedLength = 1_000, encoding = null, body = ByteArray(100) { 'a'.code.toByte() })
226      assertEquals(FetchResult.Incomplete(expectedBytes = 1_000, receivedBytes = 100), result)
227  }
```

Se anuncian 1 000 bytes, llegan 100 y el flujo termina **sin error**: la línea 99 del descargador debe devolver `Incomplete(1000, 100)`.

```kotlin
// HttpUrlConnectionFetcherTest.kt · líneas 229–235
229  @Test
230  fun whenTheServerCompressesAnyway_theLengthIsNotComparedBecauseItCountsCompressedBytes() {
231      // Content-Length would describe the COMPRESSED bytes, which are not the ones we read (on Android the
232      // library may decompress by itself). So with Content-Encoding present the comparison is skipped.
233      val result = fakeConnection(announcedLength = 1_000, encoding = "gzip", body = ByteArray(100) { 'a'.code.toByte() })
234      assertTrue("got $result", result is FetchResult.Body)
235  }
```

Fija la regla de la línea 78: con `Content-Encoding` **no se compara** el tamaño. Mismo escenario, pero con `gzip`: el resultado es `Body`, no `Incomplete`. **Documenta una decisión** (aceptar lo que llegue cuando hay compresión), no un deseo.

```kotlin
// HttpUrlConnectionFetcherTest.kt · líneas 191–196
191  @Test
192  fun anAnnouncedHugeBodyIsRefusedWithoutReadingIt() {
193      TestHttpServer { _, s -> s.respond(body = ByteArray(0), lengthHeader = 10_000_000) }.use { server ->
194          assertEquals(FetchResult.TooLarge(1_000), fetch(server, fetcher(maxBytes = 1_000)))
195      }
196  }
```

```kotlin
// HttpUrlConnectionFetcherTest.kt · líneas 198–203
198  @Test
199  fun anUnannouncedHugeBodyIsCutWhenItPassesTheLimit() {
200      TestHttpServer { _, s -> s.respondChunked(listOf(ByteArray(2_000) { 'a'.code.toByte() })) }.use { server ->
201          assertEquals(FetchResult.TooLarge(1_000), fetch(server, fetcher(maxBytes = 1_000)))
202      }
203  }
```

```kotlin
// HttpUrlConnectionFetcherTest.kt · líneas 237–243
237  @Test
238  fun aBodyExactlyAtTheLimitIsAccepted() {
239      TestHttpServer { _, s -> s.respond(body = ByteArray(1_000) { 'a'.code.toByte() }) }.use { server ->
240          val result = fetch(server, fetcher(maxBytes = 1_000))
241          assertTrue("got $result", result is FetchResult.Body && result.text.length == 1_000)
242      }
243  }
```

Las tres juntas fijan el límite: lo que **anuncia** que es enorme se rechaza antes de leer; lo que **no anuncia** se corta al pasar el límite; y lo que mide **exactamente** el límite se acepta.

**Cancelación**

```kotlin
// HttpUrlConnectionFetcherTest.kt · líneas 247–253
247  @Test
248  fun cancellingBeforeStartingNeverTouchesTheServer() {
249      TestHttpServer { _, s -> s.respond(body = "x".toByteArray()) }.use { server ->
250          assertEquals(FetchResult.Cancelled, fetch(server, isCancelled = { true }))
251          assertTrue(server.requests.isEmpty())
252      }
253  }
```

`server.requests.isEmpty()` demuestra que **no se hizo ninguna conexión**.

```kotlin
// HttpUrlConnectionFetcherTest.kt · líneas 255–273
255  @Test
256  fun cancellingWhileTheBodyArrivesStopsTheDownload() {
257      TestHttpServer { _, s ->
258          val out = s.getOutputStream()
259          out.write("HTTP/1.1 200 OK\r\nContent-Length: 100\r\nConnection: close\r\n\r\n".toByteArray())
260          repeat(100) {
261              out.write('a'.code)
262              out.flush()
263              Thread.sleep(50)
264          }
265      }.use { server ->
266          var asked = 0
267          val started = System.nanoTime()
268          val result = fetch(server, isCancelled = { ++asked > 3 })
269          val millis = (System.nanoTime() - started) / 1_000_000
270          assertEquals(FetchResult.Cancelled, result)
271          assertTrue("took $millis ms, should be far below the 5 s the full body needs", millis < 2_000)
272      }
273  }
```

El servidor manda 100 bytes a 50 ms cada uno (5 s en total). `isCancelled` responde `true` a partir de la cuarta consulta. La prueba comprueba que se devuelve `Cancelled` y que tardó **menos de 2 s**, es decir, que **no esperó** a que terminara la descarga.

---

## 11. Las pruebas del repositorio, una a una

El `FakeFetcher` (una clase anidada) devuelve lo que se le configure y **anota las URL** que le piden. El auxiliar `repo { respuesta }` crea el par `(falso, repositorio)` con la base `http://base/`.

```kotlin
// LigandRepositoryTest.kt · líneas 36–45
36  @Test
37  fun aGoodBodyBecomesAMolecule() {
38      val (fetcher, repository) = repo { FetchResult.Body(real001) }
39      val result = repository.load("001")
40      assertTrue("got $result", result is LigandLoadResult.Success)
41      val molecule = (result as LigandLoadResult.Success).molecule
42      assertEquals("001", molecule.id)
43      assertEquals(87, molecule.atoms.size)
44      assertEquals(listOf("http://base/001.cif"), fetcher.urls)
45  }
```

Con el fichero real `001.cif` como cuerpo, se obtiene una molécula de 87 átomos, y la URL pedida es exactamente `http://base/001.cif`.

```kotlin
// LigandRepositoryTest.kt · líneas 63–70
63  @Test
64  fun invalidIdsAreRejectedWithoutAnyRequest() {
65      for (bad in listOf("", "   ", "../x", "ABCDEF", "A B", "A/B", "a?b=1", "AT\nP", "ÁTP", "AT.P")) {
66          val (fetcher, repository) = repo { FetchResult.Body(real001) }
67          assertEquals("for '$bad'", LoadError.InvalidId(bad), errorOf(repository.load(bad)))
68          assertTrue("a request was made for '$bad'", fetcher.urls.isEmpty())
69      }
70  }
```

Diez ids malos (vacío, espacios, `../x`, 6 letras, un salto de línea, una `Á`...). Para cada uno se comprueba **que se devuelve `InvalidId` y que el descargador falso no recibió ninguna URL**.

```kotlin
// LigandRepositoryTest.kt · líneas 83–87
83  @Test
84  fun status404And410AreNotFound() {
85      assertEquals(LoadError.NotFound, errorOf(repo { FetchResult.HttpError(404) }.second.load("XYZ")))
86      assertEquals(LoadError.NotFound, errorOf(repo { FetchResult.HttpError(410) }.second.load("XYZ")))
87  }
```

```kotlin
// LigandRepositoryTest.kt · líneas 89–94
89  @Test
90  fun otherStatusesAreServerErrors() {
91      assertEquals(LoadError.ServerError(500), errorOf(repo { FetchResult.HttpError(500) }.second.load("XYZ")))
92      assertEquals(LoadError.ServerError(403), errorOf(repo { FetchResult.HttpError(403) }.second.load("XYZ")))
93      assertEquals(LoadError.ServerError(503), errorOf(repo { FetchResult.HttpError(503) }.second.load("XYZ")))
94  }
```

```kotlin
// LigandRepositoryTest.kt · líneas 96–103
 96  @Test
 97  fun transportProblemsAreMapped() {
 98      assertEquals(LoadError.NoConnection, errorOf(repo { FetchResult.NoConnection("x") }.second.load("XYZ")))
 99      assertEquals(LoadError.Timeout, errorOf(repo { FetchResult.Timeout }.second.load("XYZ")))
100      assertEquals(LoadError.Incomplete, errorOf(repo { FetchResult.Incomplete(10, 5) }.second.load("XYZ")))
101      assertEquals(LoadError.TooLarge(77), errorOf(repo { FetchResult.TooLarge(77) }.second.load("XYZ")))
102      assertEquals(LoadError.NetworkError("tls"), errorOf(repo { FetchResult.Failed("tls") }.second.load("XYZ")))
103  }
```

```kotlin
// LigandRepositoryTest.kt · líneas 111–122
111  @Test
112  fun aBodyThatIsNotCifIsCorrupt() {
113      val html = "<html><body>Service unavailable</body></html>"
114      assertEquals(
115          LoadError.Corrupt(CifFailure.NoDataBlock),
116          errorOf(repo { FetchResult.Body(html) }.second.load("XYZ")),
117      )
118      assertEquals(
119          LoadError.Corrupt(CifFailure.NoDataBlock),
120          errorOf(repo { FetchResult.Body("") }.second.load("XYZ")),
121      )
122  }
```

HTML y cadena vacía acaban en `Corrupt(NoDataBlock)`: el parser los rechaza y el repositorio lo envuelve.

```kotlin
// LigandRepositoryTest.kt · líneas 126–130
126  @Test
127  fun aFetcherThatThrowsBecomesUnexpectedInsteadOfCrashing() {
128      val error = errorOf(repo { throw IllegalStateException("boom") }.second.load("XYZ"))
129      assertTrue("got $error", error is LoadError.Unexpected && "boom" in error.description)
130  }
```

La **red de seguridad**: un descargador defectuoso que lanza `IllegalStateException("boom")` **no** hace caer el repositorio: el resultado es `Unexpected` y su descripción contiene `boom`.

```kotlin
// LigandRepositoryTest.kt · líneas 132–146
132  @Test
133  fun theCancellationFlagIsPassedToTheFetcher() {
134      val received = mutableListOf<() -> Boolean>()
135      val fetcher = object : HttpFetcher {
136          override fun fetch(url: String, isCancelled: () -> Boolean): FetchResult {
137              received += isCancelled
138              return FetchResult.Cancelled
139          }
140      }
141      var flag = false
142      LigandRepository(fetcher).load("XYZ") { flag }
143      assertEquals(false, received.single()())
144      flag = true
145      assertEquals(true, received.single()())
146  }
```

Comprueba que el repositorio **pasa la función de cancelación al descargador sin cambiarla**: lo que se cambia en la variable `flag` después se refleja en lo que ve el descargador. Se guarda la función recibida y se llama dos veces con la bandera distinta.

**De extremo a extremo** (el descargador **real** y el servidor casero)

```kotlin
// LigandRepositoryTest.kt · líneas 153–164
153  @Test
154  fun endToEndTheRealFileTravelsThroughHttpAndBecomesAMolecule() {
155      TestHttpServer { request, s ->
156          if (requestPath(request) == "/ligands/view/001.cif") s.respond(body = real001.toByteArray())
157          else s.respond("404 Not Found")
158      }.use { server ->
159          val result = endToEnd(server).load("001")
160          assertTrue("got $result", result is LigandLoadResult.Success)
161          assertEquals(87, (result as LigandLoadResult.Success).molecule.atoms.size)
162          assertEquals("GET /ligands/view/001.cif HTTP/1.1", server.requests.single().lineSequence().first())
163      }
164  }
```

El `001.cif` completo sale del servidor, cruza HTTP, el descargador, el parser y llega como molécula. Se verifica también la **línea de petición**.

```kotlin
// LigandRepositoryTest.kt · líneas 173–180
173  @Test
174  fun endToEndAFileCutInTheMiddleIsIncompleteNotCorrupt() {
175      // The server announces the full length but the connection dies half way: the typical bad mobile network.
176      val half = real001.toByteArray().copyOf(real001.length / 2)
177      TestHttpServer { _, s -> s.respond(body = half, lengthHeader = real001.length.toLong()) }.use { server ->
178          assertEquals(LoadError.Incomplete, errorOf(endToEnd(server).load("001")))
179      }
180  }
```

El servidor anuncia el tamaño del fichero entero y manda **la mitad**: resultado `Incomplete`, no `Corrupt`. Sin la comprobación de `Content-Length`, el parser vería un fichero cortado (y casi seguro lo rechazaría, pero **con un motivo equivocado** y con probabilidad de equivocarse; ver la medición de la sección 5 del documento 06).

```kotlin
// LigandRepositoryTest.kt · líneas 182–187
182  @Test
183  fun endToEndAnHtmlErrorPageWithStatus200IsCorrupt() {
184      TestHttpServer { _, s -> s.respond(body = "<html>Proxy login required</html>".toByteArray()) }.use { server ->
185          assertEquals(LoadError.Corrupt(CifFailure.NoDataBlock), errorOf(endToEnd(server).load("001")))
186      }
187  }
```

**Límites documentados** (pruebas que **describen** una debilidad, no que la defienden)

```kotlin
// LigandRepositoryTest.kt · líneas 206–221
206  @Test
207  fun aDownloadCutExactlyAtARowBoundaryWithConsistentLengthPassesAsAMoleculeWithFewerAtoms() {
208      // If the cut falls between two atom rows AND Content-Length matches what was sent (a proxy that rewrites
209      // it, or a server that generates a short file), nothing can detect it: the file is well formed.
210      // This test does not defend the behaviour: it DOCUMENTS it, so nobody believes the check is perfect.
211      val lines = real001.lines()
212      // The atom rows start right after the last "_chem_comp_atom." header line; keep only the first 10.
213      val firstAtomRow = lines.indexOfLast { it.startsWith("_chem_comp_atom.") } + 1
214      val cut = lines.take(firstAtomRow + 10).joinToString("\n") + "\n"
215      val body = cut.toByteArray()
216      TestHttpServer { _, s -> s.respond(body = body) }.use { server ->
217          val result = endToEnd(server).load("001")
218          assertTrue("got $result", result is LigandLoadResult.Success)
219          assertEquals(10, (result as LigandLoadResult.Success).molecule.atoms.size)
220      }
221  }
```

```kotlin
// LigandRepositoryTest.kt · líneas 223–236
223  @Test
224  fun aDownloadCutBetweenTwoBondRowsPassesAsAMoleculeWithFewerBonds() {
225      // Same limitation, in the bond table: all 87 atoms arrive, but only some of the 90 bonds.
226      val lines = real001.lines()
227      val firstBondRow = lines.indexOfLast { it.startsWith("_chem_comp_bond.") } + 1
228      val body = (lines.take(firstBondRow + 40).joinToString("\n") + "\n").toByteArray()   // keep 40 bond rows
229      TestHttpServer { _, s -> s.respond(body = body) }.use { server ->
230          val result = endToEnd(server).load("001")
231          assertTrue("got $result", result is LigandLoadResult.Success)
232          val molecule = (result as LigandLoadResult.Success).molecule
233          assertEquals(87, molecule.atoms.size)
234          assertEquals(40, molecule.bonds.size)
235      }
236  }
```

Estas dos pruebas fijan el comportamiento conocido: si la **longitud anunciada cuadra** y el corte cae **entre dos filas**, no hay nada que lo note. Si algún día se añade una defensa (por ejemplo, contrastar la fórmula del fichero con los átomos),
estas pruebas **se pondrán en rojo a propósito** y habrá que actualizarlas: es la señal de que la defensa funciona.

---

## 12. Qué demuestran las pruebas: mutación

Estropear el código a propósito (una avería cada vez) y ejecutar las pruebas de red. Una avería **muere** si alguna prueba se pone en rojo; **sobrevive** si todas siguen en verde (esa parte del código no estaba protegida).

| N.º | Avería introducida | Resultado | Prueba(s) que la detectan |
|----:|--------------------|-----------|---------------------------|
| N1 | Quitar la cabecera `Accept-Encoding: identity` | Muere | `theRequestAsksForNoCompressionAndIdentifiesItself` |
| N2 | `declared > maxBytes` → `>=` (el límite exacto se rechaza) | Muere | `aBodyExactlyAtTheLimitIsAccepted` |
| N3 | `body.size() + count > maxBytes` → `>=` | Muere | `aBodyExactlyAtTheLimitIsAccepted` |
| N4 | Quitar el reloj total | Muere | `aServerThatDripsOneByteAtATimeHitsTheTotalLimit` |
| N5 | Quitar la consulta de cancelación del bucle de lectura | Muere | `cancellingWhileTheBodyArrivesStopsTheDownload` |
| N6 | Quitar la consulta de cancelación inicial | Muere | `cancellingBeforeStartingNeverTouchesTheServer` |
| N7 | `checkLength = false` (nunca comparar el tamaño) | Muere | `aBodyShorterThanAnnouncedIsIncompleteEvenWhenTheStreamEndsCleanly`, `aBodyShorterThanItsContentLengthIsIncomplete`, `endToEndAFileCutInTheMiddleIsIncompleteNotCorrupt` |
| N8 | Quitar la rama `SocketTimeoutException` de la fase 1 | Muere | `aServerThatNeverAnswersIsATimeout`, `networkExceptionsAreTranslated`, `endToEndAServerThatNeverAnswersIsATimeout` |
| N9 | `UnknownHostException` → `Failed` en lugar de `NoConnection` | Muere | `networkExceptionsAreTranslated` |
| N10 | `SocketException` → `Failed` en lugar de `NoConnection` | Muere | `aClosedPortIsNoConnection`, `networkExceptionsAreTranslated`, `endToEndNothingListeningIsNoConnection` |
| N11 | `status !in 200..299` → `status >= 500` (un 404 se lee como éxito) | Muere | `status404IsAnHttpError`, `endToEndAnUnknownLigandIs404` |
| N12 | Decodificar con `ISO_8859_1` en vez de UTF-8 | Muere | `aSuccessfulResponseGivesItsBodyDecodedAsUtf8` |
| N13 | La `IOException` al leer el cuerpo → `Failed` en vez de `Incomplete` | Muere | `aChunkedBodyThatIsNeverFinishedIsIncomplete` |
| N14 | Quitar la comparación con `Content-Length` tras el bucle | Muere | `aBodyShorterThanAnnouncedIsIncompleteEvenWhenTheStreamEndsCleanly`, `aBodyShorterThanItsContentLengthIsIncomplete`, `endToEndAFileCutInTheMiddleIsIncompleteNotCorrupt` |
| N15 | Quitar `readTimeout` | Muere | `aServerThatStopsHalfWayIsATimeout`, `aServerThatNeverAnswersIsATimeout`, `endToEndAServerThatNeverAnswersIsATimeout` |
| N16 | `contentEncoding == null` → `true` (comparar también con compresión) | **Sobrevivió la primera vez**; se añadió la prueba y ahora muere | `whenTheServerCompressesAnyway_theLengthIsNotComparedBecauseItCountsCompressedBytes` |
| N20 | Quitar la rama `IllegalArgumentException` de la conversión a URL | Muere | `aTextThatIsNotAUrlFailsWithoutThrowing` |
| N21 | Quitar la rama `URISyntaxException` | Muere | `aTextThatIsNotAUrlFailsWithoutThrowing` |
| R1 | `404 \|\| 410` → solo `404` | Muere | `status404And410AreNotFound` |
| R2 | Patrón de id `{1,5}` → `{1,6}` | Muere | `invalidIdsAreRejectedWithoutAnyRequest` |
| R3 | Quitar `trim()` | Muere | `theIdIsTrimmedAndUppercasedBeforeBuildingTheUrl` |
| R4 | Quitar `uppercase()` | Muere | `theIdIsTrimmedAndUppercasedBeforeBuildingTheUrl` |
| R5 | `Cancelled` → un fallo `Timeout` | Muere | `cancellationIsNotAnError` |
| R6 | La red de seguridad solo captura `FileNotFoundException` | Muere | `aFetcherThatThrowsBecomesUnexpectedInsteadOfCrashing` |
| R7 | `Incomplete` → `LoadError.Timeout` | Muere | `transportProblemsAreMapped`, `endToEndAFileCutInTheMiddleIsIncompleteNotCorrupt` |
| R8 | `Failed` → `LoadError.NoConnection` | Muere | `transportProblemsAreMapped` |
| R9 | No pasar `isCancelled` al descargador | Muere | `theCancellationFlagIsPassedToTheFetcher` |
| N17 | Quitar `useCaches = false` | **Sobrevive** | Ninguna. Solo cambia dos cabeceras (`Cache-Control`, `Pragma`) que dependen de la implementación de la JVM; no se ha considerado útil fijarlas con una prueba |
| N18 | Quitar `requestMethod = "GET"` | **Sobrevive** (mutante **equivalente**) | `GET` ya es el método por defecto: el comportamiento no cambia, no hay nada que detectar |
| N19 | Quitar `connection.disconnect()` | **Sobrevive** | Ninguna. Un `disconnect` olvidado filtraría conexiones abiertas pero no cambia ningún resultado visible en una prueba corta |

**Resultado:** de 30 averías, **27 mueren** y **3 sobreviven** (N17, N18 y N19: una sin consecuencias comprobables, un mutante equivalente y una fuga de recursos que
una prueba corta no puede observar). N16 sobrevivió en la primera pasada y reveló que la regla «con compresión no se compara el tamaño» no estaba protegida; se
añadió la prueba y se repitió la avería: ahora muere. **Una avería que no se ha probado no es una garantía:** las 30 son las que se pensaron, no todas las posibles.

---

## 13. Trampas y límites conocidos

| # | Trampa o límite | Consecuencia | Estado |
|--:|-----------------|--------------|--------|
| 1 | El reloj total solo se mira **entre** lecturas | El peor caso del cuerpo es 30 s + 10 s | Documentado (06, sección 4) |
| 2 | Conexión y cabeceras no están acotadas por el reloj total | Solo `connectTimeout` y `readTimeout` | Documentado |
| 3 | El DNS puede no estar cubierto por `connectTimeout` | Un DNS colgado podría pasar de 10 s | **Sin verificar** |
| 4 | La cancelación se nota **entre lecturas** | Con el servidor en silencio, hasta `readTimeoutMs` | Documentado; alternativa: `disconnect()` desde otro hilo |
| 5 | Un corte **entre dos filas** con longitud coherente pasa como molécula incompleta | El 6,5 % de los cortes posibles del `001` | **Límite conocido**, con dos pruebas |
| 6 | Con `Content-Encoding` no se compara el tamaño | Un cuerpo comprimido cortado solo lo detecta la librería o el parser | Fijado por una prueba |
| 7 | Sin `INTERNET` en el manifiesto, la red falla de forma engañosa | Parece «sin conexión» | **Pendiente de añadir** |
| 8 | `NoConnection` pierde su `reason` al pasar a `LoadError` | El registro no sabrá si fue DNS o conexión rechazada | Mejora pendiente: registrarlo en el descargador |
| 9 | `disconnect()` no tiene prueba que falle | Un olvido filtraría sockets sin cambiar ningún resultado | Aceptado |
| 10 | Un `SecurityException` (sin permiso) **no** lo captura el descargador | Llega al repositorio y acaba en `Unexpected` | Razonado, no probado |
| 11 | Las pruebas de tiempo dependen de la carga de la máquina | Un fallo aislado puede ser falso | Documentado |
| 12 | `HttpURLConnection` de Android ≠ la de la JVM | Las pruebas no demuestran el comportamiento en el móvil | **Sin verificar** |

---

## 14. Qué falta

1. `ViewModel` con corrutina en `Dispatchers.IO`, `isCancelled = { !isActive }` y un estado `Cargando / Éxito / Error`. **Garantía a probar:** el indicador desaparece en **todas** las ramas.
2. Pantallas de error con los cuatro mensajes y botón de reintentar; registro (`Log`) del motivo exacto.
3. Permiso `INTERNET` en `AndroidManifest.xml`.
4. Lista de ligandos con búsqueda (resto del documento 06).
5. Prueba real: servidor de RCSB, `UNL`, forma de los iones, DNS, rendimiento en el A33.
6. Decidir si se añade la defensa de la fórmula química (documento 06, sección 5).

---

## 15. Qué está verificado y qué no

| Afirmación | Estado |
|------------|--------|
| Las 130 pruebas (88 + 42) pasan con `kotlinc` y JUnit (OpenJDK 21.0.12) | **Verificado** |
| Las averías de la sección 12 | **Verificado** (resultados reales) |
| La captura de la petición (documento 06, sección 2) | **Verificado en la JVM** |
| La medición de cortes del `001` (documento 06, sección 5) | **Verificado** (salida real del código) |
| Las pruebas pasan bajo el Gradle del equipo | Primera ejecución con 13 fallos (12 por falta de `001.cif`, 1 dependiente del JDK, reescrita); segunda ejecución **pendiente** |
| Descarga real desde `files.rcsb.org` | **Sin verificar** |
| Comportamiento en el dispositivo (cabeceras, DNS, tiempos) | **Sin verificar** |
| Respuesta para `UNL` | **Sin verificar** |
| Forma del texto de un ion en RCSB | **Sin verificar** |
| Rendimiento | **Sin medir** |

---

## 16. Qué recordar

1. **La red devuelve valores, no lanza excepciones**: `FetchResult` (transporte) y `LigandLoadResult` (dominio). Cierre inesperado = suspenso.
2. **La interfaz `HttpFetcher` es la costura**: el repositorio no sabe qué librería hay debajo, y se prueba con un falso.
3. **`responseCode` es lo que conecta**; casi todos los fallos de red salen de ahí. Los `catch` van **del más específico al más general**.
4. **`4xx`/`5xx` no se leen**: se devuelve el código sin pedir el cuerpo.
5. **Tres relojes**: conexión, lectura y total. Solo el total para al que gotea, y solo se mira entre lecturas.
6. **`Content-Length` es la única señal de «llegó entero»**, y solo sin compresión. Sin ella, un corte entre filas pasa como molécula incompleta (medido: 6,5 % de los cortes del `001`).
7. **El tamaño se limita antes y durante la lectura**; la comparación del límite es `>` (el límite exacto es válido).
8. **`Cancelled` no es un error**; se consulta antes de empezar y en cada vuelta.
9. **`HttpError(404 o 410)` → `NotFound`; otro código → `ServerError`**: la interpretación vive en el repositorio.
10. **El id se normaliza y se valida antes de formar la URL**: `../x` nunca sale por la red.
11. **Dos redes de seguridad**: el repositorio captura `Exception` (→ `Unexpected`); no captura `Error`.
12. **Probar sin red**: servidor casero con sockets, conexión falsa, descargador falso; y **estropear el código a propósito** para ver quién lo nota.
13. **Falta**: `INTERNET` en el manifiesto, `ViewModel`, pantallas, lista, prueba real y en el dispositivo.
