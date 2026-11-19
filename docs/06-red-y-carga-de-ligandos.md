# 06 · Red y carga de ligandos: diseño

> Estado: **ESCRITA Y PROBADA EN LA JVM** (la capa de red completa: descarga, errores, tiempos, cancelación y conversión a molécula).
> **Sin probar** en el dispositivo ni contra el servidor real de RCSB. **Pendientes** de este bloque: la lista de ligandos con búsqueda,
> el `ViewModel` que llama a la red desde un hilo de fondo y las pantallas de error (sección 14).
> Redactado el 19/11/2026. El **código línea a línea** está en [`06a-codigo-red.md`](06a-codigo-red.md); aquí están la **teoría, los
> riesgos y los motivos**.

## Índice

1. [Qué pide el enunciado](#1-qué-pide-el-enunciado)
2. [HTTP en cinco minutos (y una simulación)](#2-http-en-cinco-minutos-y-una-simulación)
3. [Todo lo que puede salir mal](#3-todo-lo-que-puede-salir-mal)
4. [Tres relojes, no uno](#4-tres-relojes-no-uno)
5. [¿Llegó el fichero entero?](#5-llegó-el-fichero-entero)
6. [Los errores son valores, no excepciones](#6-los-errores-son-valores-no-excepciones)
7. [Hilos y cancelación](#7-hilos-y-cancelación)
8. [Permiso de Internet y tráfico seguro](#8-permiso-de-internet-y-tráfico-seguro)
9. [El identificador del ligando va dentro de una URL](#9-el-identificador-del-ligando-va-dentro-de-una-url)
10. [Decisiones](#10-decisiones)
11. [Alternativas y por qué no se eligieron](#11-alternativas-y-por-qué-no-se-eligieron)
12. [Cómo se prueba una capa de red sin Internet](#12-cómo-se-prueba-una-capa-de-red-sin-internet)
13. [Qué está verificado y qué no](#13-qué-está-verificado-y-qué-no)
14. [Qué falta](#14-qué-falta)
15. [Qué recordar](#15-qué-recordar)

---

## 1. Qué pide el enunciado

De la tarea (documento 01) se desprenden cuatro obligaciones para esta capa:

1. **Descargar** `https://files.rcsb.org/ligands/view/{id}.cif` para el ligando elegido y **convertirlo** en molécula con el parser.
2. Tratar **cuatro errores de carga**: sin red, `404` (ligando inexistente), fichero corrupto y tiempo agotado.
3. Mostrar un **indicador de carga** que **siempre desaparece**: con éxito, con cualquier error y al cancelar.
4. **Ningún cierre inesperado**: en la evaluación entre compañeros, un cierre es un suspenso. De ahí sale la regla de oro de esta capa:
   **ninguna situación de red o de fichero puede acabar en una excepción que llegue a la interfaz.** Todas acaban en un valor.

Esta capa **no** dibuja nada y **no** sabe qué es una pantalla. Entrega un resultado (`LigandLoadResult`) y la interfaz decide qué enseñar.

---

## 2. HTTP en cinco minutos (y una simulación)

**HTTP** es el protocolo con el que un cliente (nuestra app) pide algo a un servidor (RCSB). Es texto sobre una conexión TCP. Una
petición tiene tres partes: una **línea de petición** (método, ruta, versión), **cabeceras** (pares `Nombre: valor`) y una línea vacía.
La respuesta tiene una **línea de estado** (versión, **código de estado**, frase), cabeceras, una línea vacía y el **cuerpo**.

**Códigos de estado** que nos importan (la primera cifra es la familia):

| Familia | Significa | Ejemplos | Qué hacemos |
|---------|-----------|----------|-------------|
| 2xx | Éxito | `200 OK` | Leer el cuerpo |
| 3xx | Redirección: «está en otra dirección» (cabecera `Location`) | `301`, `302` | Seguirla (lo hace la librería) |
| 4xx | Fallo **del cliente**: la petición no tiene sentido o no existe | `404 Not Found`, `410 Gone`, `403`, `429` | `404` y `410` → «ligando no encontrado»; el resto, «error del servidor» |
| 5xx | Fallo **del servidor** | `500`, `503` | «Error del servidor» |

**Cabeceras** relevantes:

- `Content-Length: N`: el cuerpo mide exactamente N bytes. Es la única forma de saber si **llegó entero** (sección 5).
- `Transfer-Encoding: chunked`: el servidor **no** anuncia el tamaño; manda el cuerpo en trozos, cada uno precedido de su tamaño en
  hexadecimal, y termina con un trozo de tamaño 0. La librería lo reensambla; si la conexión se corta antes del trozo final, la lectura falla.
- `Content-Encoding: gzip`: el cuerpo viene comprimido. Entonces `Content-Length` es el tamaño **comprimido** y no coincide con lo que leemos.
  Por eso pedimos `Accept-Encoding: identity` («sin compresión»).
- `Location`: dónde está de verdad el recurso (redirecciones).

**Simulación.** Esto es lo que el servidor de pruebas **recibió de verdad** cuando el código descargó `/ligands/view/ATP.cif`
(captura real en la JVM; en Android la librería interna es otra y las cabeceras automáticas pueden diferir, ver sección 13):

```
GET /ligands/view/ATP.cif HTTP/1.1
Accept-Encoding: identity
User-Agent: SwiftyProteins/1.0 (42 school project)
Cache-Control: no-cache
Pragma: no-cache
Host: 127.0.0.1:34301
Accept: */*
Connection: keep-alive

```

Las dos primeras cabeceras las pone nuestro código; `Cache-Control` y `Pragma` salen de `useCaches = false`; el resto las añade la librería.
Una respuesta normal sería:

```
HTTP/1.1 200 OK
Content-Length: 5731

data_ATP
#
... (5731 bytes en total)
```

**TLS (HTTPS).** La URL empieza por `https`: antes de enviar la petición, cliente y servidor negocian una conexión **cifrada y autenticada**
(el servidor demuestra con un certificado que es `files.rcsb.org`). Si el certificado no es válido o el reloj del móvil está muy desviado,
la negociación **falla** con una excepción (`SSLHandshakeException`). No hacemos nada especial para desactivar esa comprobación, y **no hay que hacerlo nunca**.

---

## 3. Todo lo que puede salir mal

La tabla ordena los fallos **en el orden en que ocurren** en una descarga. Las dos columnas de la derecha son lo que ve el usuario:
la primera usa los **cuatro errores obligatorios**, la segunda los casos extra que no se pueden dejar sin explicar.

| # | Qué pasa | Cómo se nota en el código | `FetchResult` | `LoadError` | Mensaje (propuesto) |
|--:|----------|---------------------------|---------------|-------------|---------------------|
| 0 | El identificador no es plausible (`../x`, vacío, 6 letras) | Ni se intenta | — | `InvalidId` | «Identificador no válido» |
| 1 | El usuario ya se fue de la pantalla | `isCancelled()` es `true` antes de empezar | `Cancelled` | `Cancelled` (no es error) | Nada |
| 2 | Modo avión, sin datos, sin Wi-Fi | `UnknownHostException` o `SocketException` | `NoConnection` | `NoConnection` | **«Sin conexión»** *(obligatorio 1)* |
| 3 | El servidor existe pero no contesta a tiempo | `SocketTimeoutException` al conectar o esperar | `Timeout` | `Timeout` | **«Tiempo de espera agotado»** *(obligatorio 4)* |
| 4 | El certificado TLS no vale | `SSLHandshakeException` (es una `IOException`) | `Failed` | `NetworkError` | «Error de red» |
| 5 | El servidor responde `404` o `410` | Código de estado | `HttpError(404)` | `NotFound` | **«Ligando no encontrado»** *(obligatorio 2)* |
| 6 | El servidor responde `500`, `503`, `403`, `429`… | Código de estado | `HttpError(n)` | `ServerError(n)` | «Error del servidor» |
| 7 | Dice que mide más de 2 MB | `Content-Length` grande | `TooLarge` | `TooLarge` | «Fichero corrupto» |
| 8 | Llega más de 2 MB sin haberlo anunciado | El contador de bytes pasa el límite | `TooLarge` | `TooLarge` | «Fichero corrupto» |
| 9 | Va tan lento que cada lectura sale a tiempo pero no acaba nunca | El reloj total vence | `Timeout` | `Timeout` | «Tiempo de espera agotado» |
| 10 | La conexión se corta con el cuerpo a medias | Menos bytes que `Content-Length`, o `IOException` al leer | `Incomplete` | `Incomplete` | **«Fichero corrupto»** *(obligatorio 3)* |
| 11 | El cuerpo llega entero pero no es un CIF (una página HTML de un *proxy* o de un portal de Wi-Fi con código 200) | El parser lo rechaza | `Body` | `Corrupt(failure)` | **«Fichero corrupto»** *(obligatorio 3)* |
| 12 | El cuerpo es un CIF defectuoso | El parser lo rechaza | `Body` | `Corrupt(failure)` | «Fichero corrupto» |
| 13 | Un fallo nuestro | Excepción inesperada | — | `Unexpected` | «Error inesperado» |

Observaciones:

- **El caso 11 es el que más engaña.** Un portal cautivo (el típico «inicie sesión en el Wi-Fi del hotel») devuelve `200 OK` con una página HTML.
  Para la red todo es correcto; solo el parser nota que aquello no es un CIF (`NoDataBlock`). Por eso la capa de red **no** juzga el contenido y el
  parser **no** juzga el transporte: cada capa detecta lo suyo.
- **Cuatro errores obligatorios, diez variantes de `LoadError`.** Varios motivos internos comparten mensaje (7, 8, 10, 11 y 12 dicen «Fichero corrupto»),
  pero se distinguen en el registro (`Log`), que es lo que sirve para depurar.
- El **caso 10 no lo puede detectar el parser** si el corte cae justo entre dos filas de átomos (documento 03, tabla de «verificado» y sección 5 de este documento): el texto sigue
  siendo un CIF bien formado, solo que con menos átomos. La única defensa es comparar con `Content-Length` (sección 5).

---

## 4. Tres relojes, no uno

Un solo «tiempo máximo» no basta, porque hay **tres** esperas distintas y cada una se pega de forma diferente:

| Reloj | Qué mide | Valor | Qué atrapa |
|-------|----------|------:|------------|
| **Conexión** (`connectTimeout`) | Establecer la conexión TCP con el servidor | 10 s | Servidor caído o red que no deja pasar los paquetes |
| **Lectura** (`readTimeout`) | Silencio entre dos llegadas de datos (cada llamada a `read`) | 10 s | Servidor que se bloquea a mitad de respuesta |
| **Total** (`totalTimeoutMs`) | Todo, desde que empezamos hasta tener el cuerpo | 30 s | El servidor que **gotea**: manda un byte cada pocos segundos |

El tercero es el que se olvida. **Simulación del goteo** (servidor de pruebas, límite de lectura 2 s, límite total 0,5 s):

```
t = 0,0 s   conexión establecida; llega la cabecera
t = 0,1 s   llega 1 byte        (lectura OK: han pasado 0,1 s < 2 s)
t = 0,2 s   llega 1 byte        (lectura OK)
t = 0,3 s   llega 1 byte        (lectura OK)
...
t = 0,5 s   el bucle comprueba el reloj total: vencido → Timeout
```

Sin el reloj total, esta descarga duraría `100 bytes × 0,1 s` = 10 s **y nunca saltaría** el de lectura. Con un servidor real que mande un byte
cada 9 s, tardaría horas y el indicador de carga **no desaparecería**, que es justo lo que el enunciado prohíbe.

**Dos límites honestos del diseño**, porque el reloj total solo se mira **entre** lecturas:

1. Una lectura que se quede esperando puede durar hasta `readTimeoutMs` más después de que venza el total. El peor caso del cuerpo es,
   por tanto, **30 s + 10 s = 40 s**, no 30.
2. La fase de **conexión y cabeceras** no está acotada por el reloj total, sino solo por `connectTimeout` y `readTimeout`. Además, la
   **resolución del nombre** (DNS) puede no estar cubierta por `connectTimeout` en algunas implementaciones (lo recordamos, **no verificado**):
   un DNS roto podría bloquear más de 10 s. Si en las pruebas con un dispositivo real se observa, la solución es un límite externo
   (`Future.get(timeout)` o `withTimeout` en la corrutina que llame, sección 7).

Los valores (10 / 10 / 30 s) son una elección razonable para un fichero de 5–30 KB, **no un dato medido**; se ajustarán con la prueba real.

---

## 5. ¿Llegó el fichero entero?

Un cuerpo cortado se presenta de dos formas:

- **Con error**: la conexión se rompe (`IOException` al leer). Es el caso fácil: devolvemos `Incomplete`.
- **Sin error**: el servidor cierra la conexión «limpiamente» y la lectura devuelve `-1` (fin) **aunque falten bytes**. Si no comparamos con
  `Content-Length`, lo tomaríamos por un fichero completo.

**Regla:** si el servidor anunció `Content-Length: N` y recibimos menos de `N` bytes, es `Incomplete`.

**Cuándo no se puede aplicar.**

- Si no hay `Content-Length` (modo `chunked`): no hay nada con que comparar. La protección es que la lectura de un `chunked` incompleto **sí** lanza
  `IOException` (lo comprueba una prueba), y que el parser detecta casi el 90 % de los cortes (medición de abajo).
- Si hay `Content-Encoding` (compresión): `Content-Length` cuenta bytes comprimidos y no son los que leemos. Se salta la comprobación. Por eso pedimos
  `identity`; si el servidor comprime igual, no se rompe nada.

**Medición: qué hace el parser con un fichero cortado en cualquier punto.** Se tomó el `001` real (16 067 caracteres) y se pasó al
parser **cortado en cada una de las 16 068 posiciones posibles** (de 0 caracteres al fichero entero):

| Resultado del parser | Posiciones de corte | Porcentaje |
|----------------------|--------------------:|-----------:|
| `Failure` (la mayoría, `TruncatedRow`: 11 176) | 14 400 | 89,6 % |
| `Success` con **menos átomos** (corte entre dos filas de átomos; sin tabla de enlaces, un ion válido) | 421 | 2,6 % |
| `Success` con los 87 átomos pero **menos de 90 enlaces** (corte entre dos filas de enlaces) | 624 | 3,9 % |
| `Success` con los 87 átomos y los 90 enlaces (el corte cae después de las tablas, en los descriptores finales) | 623 | 3,9 % |

Conclusión: **el parser por sí solo deja pasar el 10,4 % de los cortes**, y en el **6,5 %** (filas 2 y 3 de la tabla) el resultado es una molécula incompleta dada por buena; en el 3,9 % de la fila 4 no se pierde nada que se dibuje.
Esto no es un fallo del parser (el texto cortado entre filas **es** un CIF bien formado) sino el motivo por el que la comprobación de
`Content-Length` es obligatoria. Reparto de la protección:

| Corte | `Content-Length` | ¿Quién lo detecta? | Resultado |
|-------|------------------|--------------------|-----------|
| Cualquiera, y el servidor anunció el tamaño del fichero entero | Mayor que lo recibido | **Red** | `Incomplete` |
| A mitad de una fila, y la longitud anunciada cuadra con lo enviado | Coherente | **Parser** casi siempre (`TruncatedRow`, etc.); no si el corte cae dentro del último número de la fila (el valor queda mal escrito y no se nota) | `Corrupt` o, con mala suerte, coordenada errónea |
| Entre dos filas, y la longitud anunciada cuadra | Coherente | **Nadie** | `Success` con menos átomos o enlaces |
| Sin `Content-Length` (`chunked`) y la conexión se rompe | — | **Red** si la librería lanza la excepción (lo comprueba una prueba) | `Incomplete` |

Las dos últimas filas son el **límite conocido**. Para llegar a ellas hace falta que un intermediario reescriba la longitud o que el servidor
genere un fichero corto: no hay motivo para esperarlo de RCSB, pero tampoco se ha comprobado. Hay una prueba que **documenta** (no defiende) el
corte entre filas de átomos. **Una defensa más que se podría añadir, y no se ha evaluado:** contrastar la fórmula química del fichero
(`_chem_comp.formula`, por ejemplo `C10 H16 N5 O13 P3`) con los átomos leídos; detectaría los cortes dentro de la tabla de átomos, pero no los de la de enlaces.

**Límite de tamaño.** Un ligando real ocupa menos de 100 KB. Aceptamos hasta **2 000 000 bytes**; por encima, `TooLarge`.
Se comprueba dos veces: **al leer la cabecera** (para no empezar a descargar algo que ya sabemos enorme) y **mientras se lee** (por si el servidor
no anunció el tamaño o mintió). Sin este límite, un servidor hostil podría llenar la memoria del móvil: ese sí sería un cierre inesperado
(`OutOfMemoryError`).

---

## 6. Los errores son valores, no excepciones

Hay dos maneras de contar «esto salió mal»:

| | Excepciones | Valores (resultado cerrado) |
|---|-------------|------------------------------|
| Cómo se ve | `try { ... } catch (e: ...)` en quien llama | `when (result) { is Success -> ... is Failure -> ... }` |
| Si te olvidas de un caso | **Compila y revienta en ejecución** | **No compila** (el `when` sobre una `sealed interface` es exhaustivo) |
| Dónde se pierde el error | Se puede escapar hacia arriba sin que nadie lo vea | Hay que tratarlo para que compile |
| Pruebas | Hay que provocar la excepción y capturarla | Se compara con `assertEquals` |

Para un proyecto donde **un cierre inesperado es un suspenso**, la segunda es la adecuada (decisión D16). Se aplica en **dos niveles**:

```
HttpFetcher.fetch(url) ──► FetchResult        TRANSPORTE: ¿llegaron los bytes?  (Body, HttpError, NoConnection, Timeout,
                                                                                 Incomplete, TooLarge, Cancelled, Failed)
        │
        ▼
LigandRepository.load(id) ──► LigandLoadResult   DOMINIO: ¿tengo una molécula?  (Success, Failure(LoadError), Cancelled)
```

¿Por qué dos y no uno? Porque **mezclan niveles de conocimiento** distintos: el nivel de transporte no sabe qué es un CIF (por eso devuelve un
`Body(text)` neutro) y el de dominio no sabe qué es un `SocketTimeoutException` (por eso recibe un `Timeout`). Cada traducción vive en un solo sitio:
las excepciones de la red se traducen en `HttpUrlConnectionFetcher`; los códigos de estado y los fallos del parser se traducen en `LigandRepository`.
Además, el repositorio se prueba con un `HttpFetcher` **falso** (sin red) y el `HttpFetcher` real con un servidor local.

**Las excepciones sí existen dentro** de las funciones (la librería las lanza), pero se capturan **en la frontera**. Hay además una **red de
seguridad** en `LigandRepository.load` (`catch (Exception)`) por si algo que no previmos se escapa: acaba en `LoadError.Unexpected`. No captura
`Error` (como `OutOfMemoryError`) a propósito: no se puede seguir con garantías. Por eso existe el límite de tamaño.

`Cancelled` no es un error: el usuario salió de la pantalla y **no hay nadie a quien enseñar nada**. Está en un tipo aparte para que nadie lo muestre
como fallo.

---

## 7. Hilos y cancelación

**La red bloquea.** `fetch` y `load` esperan a que llegue la respuesta y **paran el hilo que las llama**. En Android, hacer red en el **hilo principal**
(el que dibuja la pantalla y recibe los toques) está prohibido: el sistema lanza `NetworkOnMainThreadException`, y aunque no lo hiciera,
la pantalla se congelaría. Por eso ambas funciones **no son seguras para el hilo principal** y lo dicen en su documentación.

**Quién las llamará** (se escribirá en el siguiente bloque): un `ViewModel` lanza una corrutina con `Dispatchers.IO` (el grupo de hilos pensado
para trabajo bloqueante) y publica el resultado en un estado que la interfaz observa. La red se escribe **sin** depender de corrutinas
(Kotlin puro, probable en la JVM sin Android ni librerías), y la corrutina se pone **por encima**.

**Cancelación.** Si el usuario sale de la pantalla mientras se descarga, hay que **parar** (no gastar batería ni datos) y **no mostrar** nada.
El código no usa ninguna librería de cancelación: recibe una función `isCancelled: () -> Boolean` y la consulta **antes de empezar y en cada vuelta del bucle de lectura**.
En el `ViewModel` será algo como `{ !coroutineContext.isActive }`.

Límite honesto de este diseño: la consulta ocurre **entre** lecturas. Si el servidor está en silencio, la cancelación se nota cuando la lectura
actual termina (hasta `readTimeoutMs` = 10 s). La prueba `cancellingWhileTheBodyArrivesStopsTheDownload` confirma que, con datos fluyendo, la
descarga se detiene en cuanto se pide.

Alternativa más inmediata: llamar a `connection.disconnect()` **desde otro hilo**, lo que aborta la lectura bloqueada con una `IOException`.
Es más rápida pero exige compartir la conexión entre hilos y razonar sobre carreras (¿el `disconnect` llega antes o después del `read`?). Se descartó por
complejidad frente a un beneficio pequeño: si se ve en la práctica que 10 s molestan, se puede añadir.

---

## 8. Permiso de Internet y tráfico seguro

**Sin el permiso `INTERNET` la app no puede usar la red**, y el fallo es engañoso: no hay un mensaje claro, simplemente las conexiones fallan. Con una excepción de red (`SocketException` o `UnknownHostException`;
**el tipo exacto no se ha verificado**) la mostraríamos como «Sin conexión», y se culparía a la red del usuario; con una `SecurityException` acabaría en `Unexpected`.
Hay que declararlo en `AndroidManifest.xml`, **fuera** de `<application>` y antes de él:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />

    <application ...>
```

Es un permiso **normal**: se concede al instalar, **sin cuadro de diálogo** al usuario. (Esto lo recordamos de la documentación de Android;
no se ha vuelto a leer en esta sesión.)

**Tráfico en claro.** Las versiones modernas de Android bloquean por defecto el tráfico `http://` sin cifrar. Nuestra URL es `https://`, así que
no hace falta ninguna excepción y **no se debe añadir**. (Las pruebas usan `http://127.0.0.1` pero corren en la JVM, no en Android.)

---

## 9. El identificador del ligando va dentro de una URL

`LigandRepository` construye la dirección como `base + id + ".cif"`. Si el `id` viniera de un campo de texto sin comprobar, un valor como
`../../x` o `A?b=1` cambiaría **qué** se pide. Por eso el identificador se **normaliza** (recortar espacios, pasar a mayúsculas, para que `" atp "`
valga como `ATP`) y se **valida** con la expresión `[A-Z0-9]{1,5}`; si no encaja, se devuelve `InvalidId` **sin hacer ninguna petición**.

Hoy todos los ids vienen de `ligands.txt` (1243 entradas) y por tanto son válidos, así que la validación es un cinturón de seguridad por si algún día
hay un campo de entrada o un enlace profundo. Los ligandos reales tienen 1–3 caracteres; la regla acepta hasta 5 porque el diccionario lo permite.

---

## 10. Decisiones

| N.º | Decisión | Motivo | Alternativas |
|-----|----------|--------|--------------|
| D14 | Cliente HTTP: **`java.net.HttpURLConnection`**, sin dependencias nuevas, detrás de la interfaz `HttpFetcher` | Hace exactamente lo que necesitamos (un `GET` de un fichero pequeño); viene con Android y con la JVM, así que se prueba sin emulador; cero riesgo de versiones con AGP 9 / Kotlin 2.4 | OkHttp, Ktor, Retrofit (sección 11) |
| D15 | Tres relojes (10 s conexión, 10 s lectura, 30 s total), tamaño máximo 2 000 000 bytes, comprobación de `Content-Length` | Un servidor lento o mentiroso no puede dejar el indicador girando ni llenar la memoria; los valores son estimaciones | Un solo tiempo; sin límite de tamaño |
| D16 | Errores **como valores** (`FetchResult`, `LigandLoadResult`, `LoadError`), en dos niveles; red de seguridad `catch (Exception)` en el repositorio | Un cierre inesperado suspende; con tipos cerrados el compilador obliga a tratar todos los casos | Excepciones propias; `kotlin.Result` (no distingue motivos) |

---

## 11. Alternativas y por qué no se eligieron

| Opción | A favor | En contra para este proyecto |
|--------|---------|------------------------------|
| **`HttpURLConnection`** (elegida) | Sin dependencias, sin versiones, probable en la JVM | API antigua y de bajo nivel; los tiempos y la cancelación los gestionamos a mano (por eso este documento) |
| **OkHttp** | Estándar de facto; tiempos de **llamada completa** (`callTimeout`) incorporados; cancelación inmediata (`call.cancel()`); `HttpURLConnection` en Android ya se apoya en él (lo recordamos, no verificado) | Una dependencia más que alinear con AGP 9.4.1 y Kotlin 2.4.21; para un único `GET` es mucho para tan poco; habría que verificar que compila y las pruebas necesitarían `MockWebServer` (otra dependencia) |
| **Ktor client** | Pensado para corrutinas; multiplataforma | Dependencia grande, más superficie de fallo; ninguna ventaja para un solo `GET` |
| **Retrofit** | Describe la API como interfaz | Pensado para APIs REST con JSON y muchos *endpoints*; aquí hay uno solo y el cuerpo es texto |
| **`DownloadManager` de Android** | Descarga en segundo plano con notificación | Descarga **a un fichero** y notifica fuera de la app; no encaja con «mostrar un indicador y fallar con un mensaje en pantalla» |
| **`java.net.http.HttpClient`** | API moderna del JDK | Según recordamos, **no está en Android**; no verificado, y aunque estuviera no serviría en el dispositivo |

Si en la prueba real aparece un problema que OkHttp resuelve (DNS colgado, cancelación lenta), se cambia **solo** la clase `HttpUrlConnectionFetcher`:
el resto del código depende de la interfaz `HttpFetcher` y no se entera.

---

## 12. Cómo se prueba una capa de red sin Internet

Probar contra `files.rcsb.org` sería **lento, inestable y no reproducible** (no se puede pedir al servidor real que «se corte a mitad» o que
«gotee»). Se hacen tres cosas:

1. **Un servidor HTTP casero** (`TestHttpServer`, unas 100 líneas sobre `ServerSocket`): escucha solo en `127.0.0.1` y en un puerto libre, y cada
   prueba decide **byte a byte** qué responde, con qué lentitud y cuándo cuelga. Así se pueden provocar los fallos de la sección 3 a voluntad.
2. **Conexiones falsas** para lo que un servidor local no puede provocar (un nombre que no se resuelve, un certificado inválido): la clase real
   admite que se le dé una función para abrir la conexión, y la prueba le da una que lanza la excepción deseada.
3. **Un `HttpFetcher` falso** para probar el repositorio sin red: devuelve lo que se le diga y recuerda qué URL se le pidió.

Además, el repositorio tiene pruebas **de extremo a extremo**: el fichero real `001.cif` viaja por HTTP desde el servidor casero, a través del
`HttpUrlConnectionFetcher` real y del parser real, hasta ser una `Molecule` de 87 átomos.

Las pruebas de tiempo usan límites cortos (0,3 s) para ser rápidas; **eso las hace algo sensibles a un ordenador muy cargado**: un fallo
esporádico de una prueba de tiempo no es necesariamente un error del código. Todas las pruebas de red tardan unos 2 s en conjunto.

---

## 13. Qué está verificado y qué no

| Afirmación | Estado |
|------------|--------|
| 41 pruebas nuevas de red pasan en la JVM (kotlinc 2.4.21 + JUnit), junto a las 88 del parser: **129** | **Verificado** |
| Las pruebas detectan averías introducidas a propósito en el código de red | Ver la tabla de la sección 13.1 |
| Petición real vista por el servidor de pruebas (cabeceras de la sección 2) | **Verificado en la JVM**; en Android la librería es otra |
| Se sigue una redirección `302` con `Location` relativo (http a http) | **Verificado en la JVM** |
| Una respuesta `chunked` completa se reensambla; una incompleta se detecta | **Verificado en la JVM** |
| El reloj total detiene un servidor que gotea | **Verificado en la JVM** |
| Las pruebas de red **compilan y pasan bajo el Gradle del equipo** (AGP 9.4.1) | **Sin verificar** (se pasan con `kotlinc` y JUnit sueltos) |
| La descarga real desde `files.rcsb.org` (certificado, cabeceras, tipo de contenido, redirecciones) | **Sin verificar**: el entorno de análisis no llega a ese servidor |
| Cómo responde RCSB para `UNL` (el único id de `ligands.txt` que no está en el diccionario) | **Sin verificar**: se espera `404` → «Ligando no encontrado», pero podría devolver otra cosa |
| Cómo escribe el texto de RCSB un ion (¿pares sueltos o `loop_`?) | **Sin verificar** (el parser cubre las dos formas) |
| `HttpURLConnection` en Android se comporta como en la JVM (tiempos, `Content-Length`, redirecciones, `chunked`) | **Sin verificar**: se cree que sí; hay que comprobarlo en el dispositivo |
| `connectTimeout` cubre la resolución de nombres (DNS) | **Sin verificar**; ver sección 4 |
| Rendimiento (tiempo de descarga + parseo en un A33 real) | **Sin medir** |
| El permiso `INTERNET` y el manifiesto | Pendiente de añadir en el proyecto |

### 13.1 Pruebas de mutación de la capa de red

Se estropeó el código a propósito (una avería cada vez) y se ejecutaron las 41 pruebas de red; una avería «sobrevive» si ninguna se pone en rojo.

| N.º | Avería introducida | Resultado | Prueba(s) que la detectan |
|----:|--------------------|-----------|---------------------------|
| N1 | Quitar la cabecera `Accept-Encoding: identity` | Muere | `theRequestAsksForNoCompressionAndIdentifiesItself` |
| N2 | `declared > maxBytes` → `>=` (el límite exacto se rechaza) | Muere | `aBodyExactlyAtTheLimitIsAccepted` |
| N3 | `body.size() + count > maxBytes` → `>=` | Muere | `aBodyExactlyAtTheLimitIsAccepted` |
| N4 | Quitar el reloj total | Muere | `aServerThatDripsOneByteAtATimeHitsTheTotalLimit` |
| N5 | Quitar la consulta de cancelación del bucle de lectura | Muere | `cancellingWhileTheBodyArrivesStopsTheDownload` |
| N6 | Quitar la consulta de cancelación inicial | Muere | `cancellingBeforeStartingNeverTouchesTheServer` |
| N7 | `checkLength = false` (nunca comparar el tamaño) | Muere | `aBodyShorterThanItsContentLengthIsIncomplete`, `endToEndAFileCutInTheMiddleIsIncompleteNotCorrupt` |
| N8 | Quitar la rama `SocketTimeoutException` de la fase 1 | Muere | `aServerThatNeverAnswersIsATimeout`, `networkExceptionsAreTranslated`, `endToEndAServerThatNeverAnswersIsATimeout` |
| N9 | `UnknownHostException` → `Failed` en lugar de `NoConnection` | Muere | `networkExceptionsAreTranslated` |
| N10 | `SocketException` → `Failed` en lugar de `NoConnection` | Muere | `aClosedPortIsNoConnection`, `networkExceptionsAreTranslated`, `endToEndNothingListeningIsNoConnection` |
| N11 | `status !in 200..299` → `status >= 500` (un 404 se lee como éxito) | Muere | `status404IsAnHttpError`, `endToEndAnUnknownLigandIs404` |
| N12 | Decodificar con `ISO_8859_1` en vez de UTF-8 | Muere | `aSuccessfulResponseGivesItsBodyDecodedAsUtf8` |
| N13 | La `IOException` al leer el cuerpo → `Failed` en vez de `Incomplete` | Muere | `aChunkedBodyThatIsNeverFinishedIsIncomplete` |
| N14 | Quitar la comparación con `Content-Length` tras el bucle | Muere | `aBodyShorterThanItsContentLengthIsIncomplete`, `endToEndAFileCutInTheMiddleIsIncompleteNotCorrupt` |
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

## 14. Qué falta

1. **`ViewModel`** que llame a `LigandRepository.load` desde `Dispatchers.IO`, con `isCancelled = { !isActive }` y un estado
   (`Cargando` / `Éxito` / `Error`) que la interfaz observe. **Garantía a probar:** el indicador de carga desaparece en **todas** las ramas.
2. **Pantallas de error** con los cuatro mensajes obligatorios y una forma de **reintentar**.
3. **Permiso `INTERNET`** en el manifiesto.
4. **Lista de ligandos** desde `ligands.txt` (assets o recurso) con **búsqueda insensible a mayúsculas en tiempo real**.
5. **Prueba con el servidor real** y en el dispositivo: tiempos, DNS, `UNL`, cómo escribe RCSB un ion, rendimiento.
6. **Registro** (`Log`) con el motivo exacto de cada fallo (el mensaje al usuario es genérico).

---

## 15. Qué recordar

1. **Un error de red no es una excepción que llega a la pantalla: es un valor** (`FetchResult` → `LoadError`). Cierre inesperado = suspenso.
2. **Dos niveles**: el transporte (¿llegaron bytes?) y el dominio (¿hay una molécula?). Cada traducción vive en un solo sitio.
3. **Cuatro errores obligatorios**: sin red (`NoConnection`), `404` (`NotFound`), corrupto (`Corrupt`, `Incomplete`, `TooLarge`) y tiempo agotado (`Timeout`).
4. **Tres relojes**: conexión, lectura y total. Solo el total para al servidor que gotea; y solo se mira **entre** lecturas.
5. **Un `200` no garantiza un CIF**: un portal cautivo devuelve HTML con éxito. El parser es el que lo nota.
6. **`Content-Length` es la única forma de saber si llegó entero**; solo se aplica sin compresión. El corte justo entre dos filas con longitud cuadrada es el
   límite **conocido y documentado**.
7. **El tamaño se limita** antes y durante la lectura: si no, un servidor hostil acaba en `OutOfMemoryError`.
8. **La red bloquea**: nunca en el hilo principal. La cancelación es una función que se consulta; no hay librería.
9. **Sin permiso `INTERNET` la red falla de forma engañosa** (parece «sin conexión»).
10. **El id va dentro de la URL**: se normaliza y valida antes de pedir nada.
11. **Probar la red sin red**: servidor local con sockets, conexiones falsas y un `HttpFetcher` falso; y estropear el código a propósito para ver si las pruebas lo notan.
12. **Lo no verificado**: servidor real, Android real, DNS, `UNL`, rendimiento.
