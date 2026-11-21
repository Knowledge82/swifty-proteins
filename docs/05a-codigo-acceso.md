# 05a · El acceso, línea a línea

> **Estado:** **escrito** y **probado en la JVM** (22 + 15 + 9 + 5 + 8 + 10 + 4 + 6 pruebas, todas en verde; mutación hecha). La parte Android (Keystore, diálogo de huella, pantallas) **compila contra *stubs*** escritos a partir de los ficheros de API publicados, pero **no se ha compilado con Gradle ni probado en el móvil**: la primera compilación real y la lista de la sección 12 son el siguiente paso. El diseño y sus porqués están en el documento 05; aquí, **el código**.

## Índice

1. [Mapa de ficheros](#1-mapa-de-ficheros)
2. [Reglas: `Policies.kt`](#2-reglas-policieskt)
3. [El hash: `PasswordHasher.kt`](#3-el-hash-passwordhasherkt)
4. [Cuentas y formato: `Account.kt`](#4-cuentas-y-formato-accountkt)
5. [El libro de cuentas: `AccountBook.kt`](#5-el-libro-de-cuentas-accountbookkt)
6. [El fichero cifrado: `AccountStore.kt`](#6-el-fichero-cifrado-accountstorekt)
7. [El flujo entero: `AuthController.kt`](#7-el-flujo-entero-authcontrollerkt)
8. [La parte Android](#8-la-parte-android)
9. [Simulación: de la primera cuenta a la huella](#9-simulación-de-la-primera-cuenta-a-la-huella)
10. [Pruebas y mutación](#10-pruebas-y-mutación)
11. [Dependencias y versiones](#11-dependencias-y-versiones)
12. [Cómo probarlo en el móvil](#12-cómo-probarlo-en-el-móvil)
13. [Qué está verificado y qué no](#13-qué-está-verificado-y-qué-no)
14. [Qué recordar](#14-qué-recordar)

---

## 1. Mapa de ficheros

| Fichero | Líneas | Android | Qué hace |
|---------|--------|---------|----------|
| `auth/Policies.kt` | 116 | No | Reglas de usuario y contraseña; freno de intentos |
| `auth/PasswordHasher.kt` | 94 | No | Argon2id (Bouncy Castle) |
| `auth/Account.kt` | 130 | No | La cuenta, el formato de texto, hexadecimal |
| `auth/AccountBook.kt` | 121 | No | Crear, entrar, freno, huella ligada |
| `auth/AccountStore.kt` | 97 | No | AES-GCM y el fichero (escritura atómica) |
| `auth/BiometricText.kt` | 95 | No | Textos de los errores de biometría; bloqueado/desbloqueado |
| `auth/AuthController.kt` | 489 | No | **Todo el flujo**: pantallas, hilos, huella, bloqueo |
| `auth/AndroidSecurity.kt` | 145 | **Sí** | Claves del Keystore; traducción de los códigos de `androidx.biometric` |
| `ui/AuthViewModel.kt` | 61 | **Sí** | Sobrevive a los giros; bloquea en `ON_STOP` |
| `ui/AuthScreens.kt` | 389 | **Sí** | Pantallas de acceso y diálogos (Compose) |
| `MainActivity.kt`, `ui/SwiftyApp.kt` | — | **Sí** | Lanzador de la huella, miniatura de Recientes, «bloqueado → acceso» |

La regla de siempre (D18): **la lógica es Kotlin puro y se prueba en la JVM**; Android solo conecta. Aquí la parte pura es mayor que nunca: incluso el flujo de pantallas (`AuthController`) es puro. Hasta el `Cipher` es de `javax.crypto`, que existe en la JVM: las pruebas cifran **de verdad** con claves de software en lugar de las del Keystore.

---

## 2. Reglas: `Policies.kt`

```kotlin
// Policies.kt · líneas 72–82
72  fun unmet(password: String, username: String): Set<Rule> {
73      val result = mutableSetOf<Rule>()
74      val length = password.codePointCount(0, password.length)
75      if (length < MIN_LENGTH || length > MAX_LENGTH) result += Rule.LENGTH
76      if (password.none { it.isLetter() }) result += Rule.LETTER
77      if (password.none { it.isDigit() }) result += Rule.DIGIT
78      val name = UsernamePolicy.normalize(username)
79      // Without a name there is nothing to compare: the rule is not failed because of an empty field.
80      if (name.isNotEmpty() && password.lowercase(Locale.ROOT).contains(name)) result += Rule.NOT_USERNAME
81      return result
82  }
```

- **`codePointCount`** y no `length`: `length` cuenta unidades UTF-16; un emoji son **dos**. Para el usuario es **un** carácter, y la regla «8 caracteres» habla de lo que él ve.
- **`isLetter()` / `isDigit()`**: cualquier alfabeto cuenta («ñandú123» tiene letras).
- **«No contiene el usuario»** compara en minúsculas y solo si hay usuario: con el campo vacío la regla no «falla».
- Devuelve un **conjunto** de reglas incumplidas: la pantalla pinta la lista entera con **[OK]** / **[--]**.

```kotlin
// Policies.kt · líneas 100–105
100  fun waitAfter(failures: Int): Long {
101      if (failures < FREE_ATTEMPTS) return 0
102      // The exponent is capped BEFORE the shift: "30 000 shl 60" overflows into nonsense (even negative numbers).
103      val doublings = (failures - FREE_ATTEMPTS).coerceAtMost(MAX_DOUBLINGS)
104      return (FIRST_WAIT_MILLIS shl doublings).coerceAtMost(MAX_WAIT_MILLIS)
105  }
```

- Hasta 4 fallos, 0. Al 5.º, 30 s; luego se **duplica** con `shl` (desplazar bits a la izquierda = multiplicar por 2).
- **El exponente se limita antes de desplazar.** `30_000L shl 60` no da «un número enorme»: los bits se salen por la izquierda y queda **basura** (incluso negativa). Una mutación que quitaba el límite la cazó la prueba con `waitAfter(70)`.

`UsernamePolicy.normalize` usa `lowercase(Locale.ROOT)`. Con el idioma del móvil, en turco «I» pasa a «ı» (sin punto) y el mismo nombre sería **otra cuenta**. Curiosidad descubierta en la mutación: en Kotlin, `lowercase()` sin argumentos **ya** usa `Locale.ROOT` (el mutante que lo quitaba era equivalente); se deja escrito a propósito, y la prueba turca mata el mutante que usa el idioma del móvil.

---

## 3. El hash: `PasswordHasher.kt`

```kotlin
// PasswordHasher.kt · líneas 69–92
69  fun derive(
70      password: ByteArray,
71      salt: ByteArray,
72      params: HashParams,
73      length: Int,
74      secret: ByteArray? = null,
75      associatedData: ByteArray? = null,
76  ): ByteArray {
77      val builder = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
78          .withVersion(Argon2Parameters.ARGON2_VERSION_13)
79          .withMemoryAsKB(params.memoryKiB)
80          .withIterations(params.iterations)
81          .withParallelism(params.parallelism)
82          .withSalt(salt)
83      if (secret != null) builder.withSecret(secret)
84      if (associatedData != null) builder.withAdditional(associatedData)
85      val out = ByteArray(length)
86      try {
87          Argon2BytesGenerator().apply { init(builder.build()) }.generateBytes(password, out)
88      } finally {
89          password.fill(0)
90      }
91      return out
92  }
```

- **`ARGON2_id`, versión 1.3**: la variante y versión de la RFC 9106.
- **Memoria en KiB**, pasadas, carriles: los tres parámetros de `HashParams`.
- **`secret` y `associatedData`** no los usa la app: existen para comprobar el **vector oficial** de la RFC, que sí los usa. Si la prueba con ese vector pasa, Bouncy Castle está llamado **exactamente** como Argon2id, y no como Argon2i o Argon2d.
- **`password.fill(0)`** en `finally`: los bytes de la contraseña se borran de memoria al acabar, pase lo que pase. (El `String` original no se puede borrar en Kotlin: es inmutable; límite documentado en 05, trampa 8.)

```kotlin
// PasswordHasher.kt · líneas 43–52
43  override fun hash(password: String): PasswordHash {
44      val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
45      return PasswordHash(params, salt, derive(passwordBytes(password), salt, params, HASH_BYTES))
46  }
47
48  override fun verify(password: String, stored: PasswordHash): Boolean {
49      val candidate = derive(passwordBytes(password), stored.salt, stored.params, stored.hash.size)
50      // Constant time: compares ALL the bytes, whatever the first difference (document 05, section 2).
51      return MessageDigest.isEqual(candidate, stored.hash)
52  }
```

- **Sal nueva** en cada `hash`: dos cuentas con la misma contraseña tienen hashes distintos (prueba `twoAccountsWithTheSamePassword...`).
- **`verify` usa los parámetros GUARDADOS** con la cuenta, no los actuales: si mañana subimos el coste, las cuentas viejas siguen entrando. Una mutación que usaba los actuales la cazó la prueba correspondiente.
- **`MessageDigest.isEqual`**: comparación en tiempo constante.
- **NFC** (`passwordBytes`): «é» escrito como un carácter o como «e» + tilde combinante da **la misma** contraseña.

**Tiempo medido aquí** (JVM de servidor, 2 núcleos): un hash + una verificación con los parámetros de OWASP, **75–160 ms**. En el A33 se espera bastante más (ART, núcleos móviles); **sin medir**.

---

## 4. Cuentas y formato: `Account.kt`

- `Account` es **inmutable** (`data class` con `copy`): cada cambio crea otra cuenta; nada cambia «por detrás».
- `PasswordHash` y `BiometricLink` **no** son `data class`: en una `data class`, `equals` compara los `ByteArray` **por referencia** y mentiría. Las pruebas comparan campo a campo con `assertArrayEquals`.

El fichero, antes de cifrar:

```
SWIFTY-ACCOUNTS 1
last adept42
account adept42 19456 2 1 01027f ff0010 0 0 0 - - -
```

```kotlin
// Account.kt · líneas 84–104
 84  private fun decodeAccount(line: String): Account {
 85      val f = line.split(' ')
 86      if (f.size != 13 || f[0] != "account") throw AccountFormatException("bad account line")
 87      if (UsernamePolicy.check(f[1]) != null || UsernamePolicy.normalize(f[1]) != f[1]) throw AccountFormatException("bad user name")
 88      val params = HashParams(int(f[2]), int(f[3]), int(f[4]))
 89      if (params.memoryKiB <= 0 || params.iterations <= 0 || params.parallelism <= 0) throw AccountFormatException("bad parameters")
 90      val bioParts = listOf(f[10], f[11], f[12])
 91      val biometric = when {
 92          bioParts.all { it == NONE } -> null
 93          bioParts.none { it == NONE } -> BiometricLink(Hex.decode(f[10]), Hex.decode(f[11]), Hex.decode(f[12]))
 94          else -> throw AccountFormatException("half a fingerprint link")
 95      }
 96      return Account(
 97          username = f[1],
 98          password = PasswordHash(params, Hex.decode(f[5]), Hex.decode(f[6])),
 99          failedAttempts = int(f[7]).also { if (it < 0) throw AccountFormatException("negative count") },
100          blockedUntilMillis = f[8].toLongOrNull() ?: throw AccountFormatException("bad time"),
101          biometricOffered = when (f[9]) { "1" -> true; "0" -> false; else -> throw AccountFormatException("bad flag") },
102          biometric = biometric,
103      )
104  }
```

- **Todo o nada:** cualquier campo raro lanza `AccountFormatException`; un fichero dañado **nunca** se lee a medias. Las pruebas recorren 13 formas de romperlo (cabecera, longitud hexadecimal impar, dígito no hexadecimal, coste 0, nombre sin normalizar, media huella, bandera rara, contador negativo, cuenta repetida, línea cortada…).
- **«Media huella»**: los tres campos de la huella están **los tres** o **ninguno**.

---

## 5. El libro de cuentas: `AccountBook.kt`

```kotlin
// AccountBook.kt · líneas 60–82
60  fun signIn(rawUsername: String, password: String): SignInResult {
61      val account = find(rawUsername)
62      if (account == null) {
63          // Same work as a real check, so the time does not reveal that the name does not exist.
64          hasher.verify(password, decoy)
65          return SignInResult.Wrong
66      }
67      val remaining = account.blockedUntilMillis - now()
68      if (remaining > 0) return SignInResult.Blocked(remaining)
69
70      if (!hasher.verify(password, account.password)) {
71          val failures = account.failedAttempts + 1
72          val wait = LoginThrottle.waitAfter(failures)
73          replace(account.copy(failedAttempts = failures, blockedUntilMillis = if (wait > 0) now() + wait else 0))
74          return if (wait > 0) SignInResult.Blocked(wait) else SignInResult.Wrong
75      }
76      // Right password: brake reset, and an old hash is upgraded to today's parameters (D40).
77      val upgraded = if (account.password.params != hasher.params) hasher.hash(password) else account.password
78      val updated = account.copy(password = upgraded, failedAttempts = 0, blockedUntilMillis = 0)
79      replace(updated)
80      snapshot = snapshot.copy(lastUsername = updated.username)
81      return SignInResult.Success(updated)
82  }
```

1. **Usuario inexistente:** se verifica contra un **señuelo** (`decoy`, un hash inventado con los parámetros actuales) y se responde `Wrong`. Mismo trabajo, mismo tiempo, misma respuesta que una contraseña errónea. La prueba cuenta las verificaciones del hasher falso para demostrarlo.
2. **Cuenta bloqueada:** `Blocked` **sin** calcular hash (no gasta tiempo ni da pistas por tiempo). Límite reconocido: que esté bloqueada sí delata que existe (05, sección 10).
3. **Contraseña errónea:** sube el contador; si toca espera, `Blocked(espera)`.
4. **Correcta:** contador a cero y, si los parámetros guardados no son los actuales, **se rehace el hash** con los nuevos (la contraseña está en la mano justo ahora: es el único momento posible).

`signInWithToken` compara el **SHA-256** del testigo descifrado con el guardado (también en tiempo constante): el testigo correcto demuestra que el descifrado lo hizo **la clave de esta cuenta** tras una huella.

---

## 6. El fichero cifrado: `AccountStore.kt`

```kotlin
// AccountStore.kt · líneas 37–55
37  override fun encrypt(plain: ByteArray): ByteArray {
38      val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
39      val iv = cipher.iv
40      check(iv.size == IV_BYTES) { "unexpected IV size ${iv.size}" }
41      return byteArrayOf(VERSION) + iv + cipher.doFinal(plain)
42  }
43
44  override fun decrypt(blob: ByteArray): ByteArray {
45      if (blob.size < 1 + IV_BYTES + TAG_BITS / 8 || blob[0] != VERSION) throw AccountFormatException("not an accounts file")
46      return try {
47          Cipher.getInstance(TRANSFORMATION).run {
48              init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, blob, 1, IV_BYTES))
49              doFinal(blob, 1 + IV_BYTES, blob.size - 1 - IV_BYTES)
50          }
51      } catch (e: GeneralSecurityException) {
52          // A wrong tag (changed or foreign file) or a key that does not open it: unreadable, never half-read.
53          throw AccountFormatException("cannot decrypt: ${e.javaClass.simpleName}")
54      }
55  }
```

- **El IV lo elige el `Cipher`**, no nosotros: el Keystore de Android **rechaza** un IV dado por la app (podría repetirse, y en GCM repetir IV con la misma clave es catastrófico). Con una clave de software, el `Cipher` también lo genera solo: **el mismo código** sirve en el móvil y en las pruebas.
- **Formato:** 1 byte de versión + 12 de IV + texto cifrado + etiqueta de 16.
- **`GeneralSecurityException` → `AccountFormatException`**: etiqueta errónea (fichero cambiado), clave distinta (fichero de otro móvil): «ilegible», nunca un cierre ni medio fichero.

```kotlin
// AccountStore.kt · líneas 86–96
86  override fun save(snapshot: AccountsSnapshot) {
87      val blob = aead.encrypt(AccountCodec.encode(snapshot).toByteArray(Charsets.UTF_8))
88      // Write a new file, then RENAME it over the old one: a crash in the middle leaves the old file or the new one,
89      // never half of each (the same pattern as the share image, document 09).
90      val tmp = File(file.parentFile, file.name + ".tmp")
91      tmp.writeBytes(blob)
92      if (!tmp.renameTo(file)) {
93          tmp.delete()
94          throw IOException("cannot replace ${file.name}")
95      }
96  }
```

Escritura **atómica**: fichero temporal y `renameTo`. En Linux (y Android) renombrar sobre un fichero existente lo **sustituye de golpe**: tras un corte de luz queda el viejo o el nuevo, nunca una mezcla.

---

## 7. El flujo entero: `AuthController.kt`

El estado (`AuthUiState`) es una `data class` con **propiedades calculadas** (`unmetRules`, `canCreate`…): la pantalla no decide nada, solo lee.

```kotlin
// AuthController.kt · líneas 187–206
187  fun signIn() {
188      val s = state.value
189      if (!s.canSignIn) return
190      mutableState.update { it.copy(busy = true, formError = null) }
191      val generation = lockGeneration
192      runWork {
193          val book = book ?: return@runWork
194          val result = withContext(work) {
195              book.signIn(s.username, s.password).also { save(book) }
196          }
197          if (generation != lockGeneration) return@runWork   // locked meanwhile: lock() already reset the form
198          when (result) {
199              is SignInResult.Success -> unlocked(result.account, offer = true, welcome = null)
200              SignInResult.Wrong -> mutableState.update { it.copy(busy = false, password = "", formError = WRONG) }
201              is SignInResult.Blocked -> mutableState.update {
202                  it.copy(busy = false, password = "", formError = LoginThrottle.message(result.remainingMillis))
203              }
204          }
205      }
206  }
```

- **`canSignIn`** primero: un toque con el formulario vacío no hace nada.
- **`busy = true`** antes de empezar: botones apagados y «> VERIFYING_».
- **`generation`**: el número de bloqueos al empezar. Argon2id tarda; si mientras tanto el usuario pulsa Inicio (`lock()` suma 1), el resultado **se tira**: una comprobación empezada **antes** de irse no puede desbloquear la app **después**. Prueba: `aCheckThatEndsAfterTheAppWentAwayDoesNotUnlockIt` (con un despachador que solo avanza cuando la prueba lo manda).
- La contraseña **se borra del estado** tras cada intento, salga bien o mal.

```kotlin
// AuthController.kt · líneas 460–476
460  private fun runWork(block: suspend CoroutineScope.() -> Unit) {
461      scope.launch {
462          try {
463              mutex.withLock {
464                  saveFailed = false
465                  block()
466              }
467          } catch (e: CancellationException) {
468              throw e
469          } catch (e: Exception) {
470              // Never a crash on the sign-in screen: an unexpected failure is a message and the form comes back.
471              mutableState.update {
472                  it.copy(busy = false, dialog = AuthDialog.Message(BiometricText.dialogFor(BiometricError.OTHER, e.javaClass.simpleName)!!))
473              }
474          }
475      }
476  }
```

- **`Mutex`**: una operación a la vez sobre el libro de cuentas (dos toques seguidos no lanzan dos comprobaciones en paralelo).
- **Nunca un cierre:** cualquier excepción inesperada acaba en un diálogo y el formulario vuelve. `CancellationException` se **relanza**: es la forma normal de parar una corrutina y no es un error.

```kotlin
// AuthController.kt · líneas 237–260
237  fun startBiometricSignIn() {
238      val s = state.value
239      if (s.busy || s.prompt != null) return
240      val account = book?.find(s.username)
241      val link = account?.biometric
242      if (account == null || link == null) {
243          // The SAME words for "no such user" and "no fingerprint for this user".
244          mutableState.update { it.copy(formError = NO_FINGERPRINT) }
245          return
246      }
247      // Keystore calls go to the worker: they talk to the secure hardware and can take a moment.
248      runWork {
249          val cipher = try {
250              withContext(work) { biometrics.signInCipher(account.username, link.iv) }
251          } catch (e: KeyInvalidatedException) {
252              keyInvalidated(account.username)
253              return@runWork
254          } catch (e: GeneralSecurityException) {
255              showMessage(BiometricText.dialogFor(BiometricError.OTHER, e.javaClass.simpleName)!!)
256              return@runWork
257          }
258          prompt(PromptRequest.Purpose.SIGN_IN, account.username, cipher)
259      }
260  }
```

- **El botón no depende del nombre escrito** (`BiometricButton.SHOWN`/`HIDDEN` según el móvil): si se iluminara solo para algunas cuentas, diría cuáles existen. Para «no existe» y «existe sin huella», **el mismo texto** (`NO_FINGERPRINT`). Prueba: `theFingerprintButtonSaysTheSame...`.
- El `Cipher` se prepara **en el hilo de trabajo**: hablar con el hardware seguro no es instantáneo.
- **`KeyInvalidatedException`** (huellas nuevas en el móvil) se detecta **aquí**, al preparar el `Cipher`, **antes** de abrir el diálogo.

```kotlin
// AuthController.kt · líneas 296–312
296  fun onBiometricOutcome(outcome: BiometricOutcome) {
297      val request = inFlight ?: return
298      inFlight = null
299      when (outcome) {
300          is BiometricOutcome.Failure -> {
301              if (outcome.error == BiometricError.KEY_INVALIDATED) return runWork { keyInvalidated(request.username) }
302              BiometricText.dialogFor(outcome.error, outcome.systemText)?.let(::showMessage)
303          }
304          is BiometricOutcome.Success -> {
305              val cipher = outcome.cipher ?: request.cipher
306              when (request.purpose) {
307                  PromptRequest.Purpose.ENROL -> finishEnrol(request.username, cipher)
308                  PromptRequest.Purpose.SIGN_IN -> finishSignIn(request.username, cipher)
309              }
310          }
311      }
312  }
```

- **`inFlight`**: la petición que está en pantalla vive en el controlador (en el `ViewModel`), no en la actividad. Si se gira el móvil con el diálogo abierto, la actividad se recrea y el resultado llega a la **nueva**; el controlador sigue sabiendo a qué petición corresponde.
- **Sin petición en vuelo, se ignora:** si se bloqueó mientras tanto, un «éxito» tardío no abre nada. Prueba: `aResultWithNoDialogOnScreenIsIgnored`.
- **`outcome.cipher ?: request.cipher`**: el resultado puede no traer el `CryptoObject`; el nuestro es el mismo objeto, ya autorizado por el hardware.

```kotlin
// AuthController.kt · líneas 343–360
343  private suspend fun completeSignIn(username: String, cipher: Cipher, generation: Int) {
344      val book = book ?: return
345      val account = withContext(work) {
346          val link = book.find(username)?.biometric ?: return@withContext null
347          val token = try {
348              cipher.doFinal(link.encryptedToken)
349          } catch (e: GeneralSecurityException) {
350              return@withContext null
351          }
352          book.signInWithToken(username, token).also {
353              token.fill(0)
354              if (it != null) save(book)
355          }
356      }
357      if (generation != lockGeneration) return
358      if (account != null) unlocked(account, offer = false, welcome = null)
359      else showMessage(BiometricText.dialogFor(BiometricError.OTHER)!!)
360  }
```

El éxito **no** es el aviso de la biblioteca: es que `doFinal` **descifre** el testigo (sin huella, el hardware no lo permite) y que su SHA-256 coincida. Prueba: `aWrongTokenDoesNotUnlock` cambia la clave y GCM rechaza el descifrado.

```kotlin
// AuthController.kt · líneas 380–400
380  fun lock() {
381      lockGeneration++
382      pendingOffer = null
383      inFlight = null
384      session.lock()
385      val name = session.username ?: book?.snapshot?.lastUsername.orEmpty()
386      mutableState.update {
387          it.copy(
388              unlocked = false,
389              screen = if (book?.isEmpty == true) AuthScreen.CREATE else AuthScreen.SIGN_IN,
390              username = name,
391              password = "",
392              confirmation = "",
393              formError = null,
394              // An offer or a notice that was open belongs to the old session; a prompt not yet shown is dropped.
395              dialog = null,
396              prompt = null,
397          )
398      }
399      refreshBiometricButton()
400  }
```

Bloquear borra contraseñas, diálogos y peticiones pendientes, pero **conserva el nombre** (el formulario vuelve relleno) y, fuera del controlador, **todo lo demás** (la lista, la molécula abierta).

---

## 8. La parte Android

### 8.1 Claves del Keystore (`AndroidSecurity.kt`)

```kotlin
// AndroidSecurity.kt · líneas 69–91
69  override fun enrolCipher(username: String): Cipher {
70      forget(username)   // a fresh key every time the fingerprint is turned on
71      val builder = KeyGenParameterSpec.Builder(alias(username), KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
72          .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
73          .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
74          .setKeySize(256)
75          .setUserAuthenticationRequired(true)
76          // Somebody who adds their finger to the phone does not inherit the access (document 05, section 3).
77          .setInvalidatedByBiometricEnrollment(true)
78      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
79          // 0 seconds = EVERY use needs a fresh biometric; only Class 3 (strong) counts.
80          builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
81      } else {
82          @Suppress("DEPRECATION")
83          builder.setUserAuthenticationValidityDurationSeconds(-1)   // the same meaning before Android 11
84      }
85      val key = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
86          init(builder.build())
87          generateKey()
88      }
89      // init() works without a finger; it is doFinal() that the hardware allows only after the biometric dialog.
90      return Cipher.getInstance(AES_GCM).apply { init(Cipher.ENCRYPT_MODE, key) }
91  }
```

- **`forget` primero:** activar la huella crea siempre una clave **nueva**.
- **`setUserAuthenticationRequired(true)`** + **`setInvalidatedByBiometricEnrollment(true)`**: solo tras huella, y muere si se añade un dedo.
- **Android 11+:** `setUserAuthenticationParameters(0, AUTH_BIOMETRIC_STRONG)` = **cada uso** necesita una huella nueva de Clase 3. Antes de Android 11 se dice lo mismo con `setUserAuthenticationValidityDurationSeconds(-1)` (obsoleto desde la 11, por eso el `@Suppress`).
- **`init` funciona sin dedo**; es `doFinal` lo que el hardware solo permite tras el diálogo. Por eso se puede preparar el `Cipher` **antes** de abrirlo.

```kotlin
// AndroidSecurity.kt · líneas 116–125
116  fun PromptRequest.toAuthenticationRequest(): AuthenticationRequest.Biometric {
117      val enrol = purpose == PromptRequest.Purpose.ENROL
118      return AuthenticationRequest.Biometric.Builder(
119          if (enrol) "Enable bio-auspex" else "Bio-auspex scan",
120          AuthenticationRequest.Biometric.Fallback.CustomOption(USE_PASSWORD),
121      )
122          .setSubtitle(if (enrol) "Confirm with your fingerprint" else "Sign in as $username")
123          .setMinStrength(AuthenticationRequest.Biometric.Strength.Class3(BiometricPrompt.CryptoObject(cipher)))
124          .build()
125  }
```

La API nueva de `androidx.biometric` 1.4: `Strength.Class3(CryptoObject(cipher))` pide biometría fuerte **ligada a la clave**; la única opción alternativa (`CustomOption`) aparece como botón negativo, «Use password».

### 8.2 El `ViewModel` y la actividad

```kotlin
// AuthViewModel.kt · líneas 29–46
29  // Dispatchers.Default: Argon2id is pure computation (the accounts file is tiny, so it goes there too).
30  val controller = AuthController(store, hasher, biometrics, viewModelScope, Dispatchers.Default)
31
32  // ON_STOP of the PROCESS: no activity of the app is visible any more (Home, another app, screen off, share sheet).
33  // Rotating does NOT send it (ProcessLifecycleOwner waits a moment before deciding), nor does the biometric dialog.
34  private val lockOnBackground = LifecycleEventObserver { _, event ->
35      if (event == Lifecycle.Event.ON_STOP) controller.lock()
36  }
37
38  init {
39      ProcessLifecycleOwner.get().lifecycle.addObserver(lockOnBackground)
40  }
41
42  // The process lifecycle lives as long as the app: without this, the observer (and this ViewModel) would stay
43  // registered after the activity is finished for good.
44  override fun onCleared() {
45      ProcessLifecycleOwner.get().lifecycle.removeObserver(lockOnBackground)
46  }
```

- **`ProcessLifecycleOwner`**: `ON_STOP` cuando **ninguna** actividad de la app se ve. Se registra en el `ViewModel` (que vive lo que la app) y se quita en `onCleared`: si lo registrara la actividad, cada giro añadiría un observador más.

```kotlin
// MainActivity.kt · líneas 28–36
28  private val viewModel: LigandsViewModel by viewModels { LigandsViewModelFactory(applicationContext) }
29  private val authViewModel: AuthViewModel by viewModels { AuthViewModelFactory(applicationContext) }
30
31  // The system's biometric dialog (androidx.biometric 1.4, document 05). It MUST be registered while the activity
32  // is being created (like any "activity result"): a field initializer is the place the library recommends.
33  // The result goes to the controller, which knows which request was on screen (even after a rotation).
34  private val biometricLauncher = registerForAuthenticationResult { result ->
35      authViewModel.controller.onBiometricOutcome(result.toOutcome())
36  }
```

- **El lanzador es un campo**: la biblioteca exige registrarlo durante la creación de la actividad (como cualquier *Activity Result*); registrarlo más tarde lanza una excepción.

```kotlin
// MainActivity.kt · líneas 51–69
51  // The "Recent apps" screen keeps a picture of the app; without this it would show the molecule WITHOUT signing
52  // in (D43). Android 13+; not FLAG_SECURE, which would also blank our own share capture (document 05, 9).
53  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setRecentsScreenshotEnabled(false)
54
55  setContent {
56      SwiftyProteinsTheme {
57          val auth = authViewModel.controller
58          val authState by auth.state.collectAsStateWithLifecycle()
59          // A biometric dialog requested by the controller is shown ONCE (keyed by its id), then handed back.
60          val prompt = authState.prompt
61          LaunchedEffect(prompt?.id) {
62              if (prompt != null) {
63                  biometricLauncher.launch(prompt.toAuthenticationRequest())
64                  auth.onPromptShown(prompt.id)
65              }
66          }
67          SwiftyApp(viewModel, auth)
68      }
69  }
```

- **`LaunchedEffect(prompt?.id)`**: se ejecuta cuando cambia el **id** de la petición: cada petición se lanza **una vez**, aunque la pantalla se recomponga cien veces. Después, `onPromptShown` la pasa a «en vuelo».

### 8.3 Pantallas (`AuthScreens.kt`, `SwiftyApp.kt`)

- **Orden en `SwiftyApp`:** arranque → **si está bloqueado, acceso** → molécula o lista. Mientras está bloqueado **no se dibujan** ni la lista ni la escena 3D, pero su estado (búsqueda, desplazamiento, molécula cargada) sigue guardado: al entrar, todo vuelve.
- **`AuthDialogs` se dibuja encima de todo** (tras el arranque): «Account created», la oferta de huella o «Bio-auspex enabled» aparecen justo **después** de desbloquear, sobre la lista.
- **Campos:** usuario con teclado ASCII sin corrección ni mayúscula automática; contraseñas con `KeyboardType.Password` (sin sugerencias ni aprendizaje), ocultas con `PasswordVisualTransformation` y tecla **SHOW/HIDE**; `ContentType.Username`/`Password`/`NewPassword` para gestores de contraseñas.
- **El formulario se desplaza** (`verticalScroll` + `imePadding`): cabe en horizontal y con el teclado abierto.
- **Atrás:** en «crear cuenta» con cuentas existentes vuelve a «entrar» (`BackHandler`); en «entrar», sale de la app.
- **Arte:** el servidor encorvado en «entrar» y el tecnosacerdote en «crear cuenta» (por fin se usan los dos). **Tras verlo en el móvil:** las figuras van **grandes (330 dp) y detrás del formulario** (`FigureBehindForm`): los campos se dibujan encima de su parte baja (tienen fondo opaco), y un degradado oscurece esa parte para que la lista de reglas y los mensajes escritos encima conserven el contraste. Límite: el arte mide ~424 px; a 330 dp en una pantalla de ~2,6× se amplía unas dos veces y queda algo blando. Un arte más grande del generador lo resolvería. Las manchas claras de ambas figuras se quitaron con una segunda pasada (documento 08c, 3, paso 6).
- **La lista de reglas** dice **[OK]** / **[--]** en el texto, no solo con el color (accesibilidad).

---

## 9. Simulación: de la primera cuenta a la huella

1. **Primer arranque.** No hay `accounts.bin` → `load()` devuelve `EMPTY` → pantalla **CREATE**, `hasAccounts = false` (sin «Back to sign in»).
2. Escribe «Adept42», «omnissiah7», «omnissiah7». Las cuatro reglas en **[OK]**, `canCreate = true`.
3. **Create account** → `busy` → en el hilo de trabajo: `UsernamePolicy` OK, no existe, Argon2id (sal aleatoria de 16 bytes, 32 de hash), `snapshot` con la cuenta y `last = adept42` → **cifrado** con la clave `swifty_accounts` → `accounts.bin.tmp` → renombrado.
4. **Desbloqueado**; diálogo «Account Created — Welcome, adept42.». Al cerrarlo, «Enable Bio-Auspex?» (el móvil puede, no estaba ofrecido).
5. **Enable** → en el hilo de trabajo: nueva clave `swifty_bio_adept42` con huella obligatoria → `Cipher` de cifrado → petición `ENROL` → la actividad abre el diálogo del sistema → dedo → `Success` → `doFinal(testigo de 32 bytes)` → se guardan cifrado, IV y SHA-256 del testigo → «Bio-Auspex Enabled».
6. **Inicio.** `ON_STOP` del proceso → `lock()`: `generation` 0 → 1, contraseña y diálogos fuera, pantalla **SIGN_IN** con «adept42».
7. **Vuelve.** Login encima. **Bio-auspex scan** → `signInCipher(adept42, iv)` → petición `SIGN_IN` → dedo → `doFinal(cifrado)` → testigo → SHA-256 coincide → contador a cero, `last = adept42`, guardado → **desbloqueado**, de vuelta donde estaba.
8. **Variante: 5 contraseñas malas.** Intentos 1–4: «Wrong username or password.»; 5.º: `failedAttempts = 5`, `blockedUntil = ahora + 30 000` → «Too many failed attempts. Try again in 30 s.». Se **guarda**: cerrar la app no lo salta.
9. **Variante: huella nueva en Ajustes.** El siguiente «Bio-auspex scan» → `KeyPermanentlyInvalidatedException` al preparar el `Cipher` → `forget`, diálogo «Fingerprints Changed», la cuenta queda sin huella y **se volverá a ofrecer** tras la próxima entrada con contraseña.

---

## 10. Pruebas y mutación

| Clase | Pruebas | Qué demuestra |
|-------|---------|---------------|
| `PoliciesTest` | 10 | Límites exactos (2/3/20/21 y 7/8/64/65), turco, cirílico, emoji, mayúsculas |
| `LoginThrottleTest` | 4 | 0×4, 30/60/120/240 s, tope de 5 min sin desbordar, redondeo hacia arriba |
| `PasswordHasherTest` | 8 | **Vector de RFC 9106**, bien/mal, sal distinta, parámetros guardados, NFC, borrado de bytes |
| `AccountCodecTest` | 5 | Ida y vuelta exacta, formato legible, 13 ficheros rotos rechazados |
| `AccountStoreTest` | 9 | Cifrado real: nada legible en disco, IV nuevo, byte cambiado, otra clave, fichero cortado, sin temporales |
| `AccountBookTest` | 15 | Crear, duplicados, señuelo, freno con reloj falso, mejora del hash, testigo, ofrecer/no ofrecer |
| `BiometricTextTest` | 6 | Cancelar sin diálogo, textos del enunciado, nota cuando se oculta el botón, sesión |
| `AuthControllerTest` | 22 | El flujo entero con claves de software: crear, ofrecer, activar, bloquear, huella, errores, clave muerta, bloqueo a mitad de hash, guardado fallido, doble toque |

**Mutación** (cambios pequeños a propósito; una prueba debe fallar): 37 mutantes (35 compilaron) sobre las piezas críticas (comparación en tiempo constante, Argon2id frente a Argon2i, NFC, borrado de bytes, parámetros guardados, señuelo, freno, mayúsculas, duplicados, `generation`, petición en vuelo, guardado fallido, doble toque, descifrado…). **Supervivientes encontrados y corregidos:**

| Mutante superviviente | Por qué sobrevivía | Arreglo |
|-----------------------|--------------------|---------|
| `lowercase(Locale.ROOT)` → `lowercase()` | **Equivalente**: en Kotlin, sin argumentos ya es `Locale.ROOT` | Comentario; el mutante con el idioma del móvil sí muere |
| Quitar «el último que entró» tras entrar | La prueba creaba la cuenta justo antes (ya era la última) | Prueba nueva con dos cuentas |
| Quitar `prompt != null` (doble toque) | Ninguna prueba tocaba dos veces | `aDoubleTapOnTheFingerprintButtonAsksOnlyOnce` |
| Devolver bytes vacíos al fallar el descifrado | El formato vacío también fallaba más arriba | Prueba directa del `AesGcmAead` |
| Guardado fallido tapado por la bienvenida | **Fallo real** encontrado al escribir la prueba: el aviso se perdía | `saveFailed` gana a la bienvenida |

---

## 11. Dependencias y versiones

| Biblioteca | Antes | Ahora | Nota |
|------------|-------|-------|------|
| `androidx.biometric:biometric` | — | **1.4.0-rc01** | API comprobada en `api/1.4.0-rc01.txt` del código de AndroidX |
| `androidx.lifecycle:lifecycle-process` | — | **2.11.1** | `ProcessLifecycleOwner` (`api/current.txt`) |
| `org.bouncycastle:bcprov-jdk18on` | — | **1.87** | El código de Argon2 de la etiqueta `r1rv87` se compiló aquí para las pruebas |
| `androidx.core:core-ktx` | 1.10.1 | **1.19.1** | Versiones de la plantilla, viejas («versiones recientes», instrucciones generales) |
| `androidx.lifecycle:*` | 2.6.1 | **2.11.1** | Todas a la par con `lifecycle-process` |
| `androidx.activity:activity-compose` | 1.8.0 | **1.13.0** | |

`packaging { resources { excludes += "/META-INF/versions/9/OSGI-INF/MANIFEST.MF" } }`: Bouncy Castle es un *jar* multiversión; ese fichero es solo metadato y, si otro *jar* de Bouncy Castle llegara por una dependencia, el empaquetado fallaría por duplicado. Permiso `USE_BIOMETRIC` declarado (normal: se concede al instalar).

**Riesgo honesto:** subir tres bibliotecas de golpe puede dar avisos de obsolescencia o algún cambio de firma que los *stubs* no reflejan. Si la compilación falla, el mensaje dirá dónde.

---

## 12. Cómo probarlo en el móvil

```bash
git pull
./gradlew :app:testDebugUnitTest     # deben pasar todas, también las de auth
./gradlew :app:installDebug
```

| # | Qué hacer | Qué debe pasar |
|---|-----------|----------------|
| 1 | Abrir la app (primera vez) | Arranque → **Register, Adept** con el tecnosacerdote |
| 2 | Usuario «ab» | «[--]» y «The user name needs at least 3 characters.» |
| 3 | Contraseña «omnissiah» | Las reglas se encienden al escribir; falta el dígito |
| 4 | Crear «adept42» / «omnissiah7» | «Account Created», luego «Enable Bio-Auspex?» |
| 5 | Enable + dedo | Diálogo del sistema «Enable bio-auspex»; «Bio-Auspex Enabled» |
| 6 | Inicio y volver | **Identify, Adept**, usuario relleno |
| 7 | Bio-auspex scan + dedo | Dentro, en la lista |
| 8 | Abrir una molécula, Inicio, volver, huella | De vuelta **en la molécula** |
| 9 | Compartir y volver | Login (D43) |
| 10 | Girar el móvil en la lista | **Sin** login |
| 11 | Dedo equivocado ×5 en el diálogo | «Too Many Attempts» |
| 12 | Cancelar el diálogo / «Use password» | Sin mensaje; sigue en el login |
| 13 | 5 contraseñas malas | La 5.ª: «Try again in 30 s.»; cerrar y abrir la app: sigue bloqueado |
| 14 | «Create account» con «ADEPT42» | «This user name is already taken.» |
| 15 | Ajustes → añadir una huella → volver → Bio-auspex scan | «Fingerprints Changed» |
| 16 | Ajustes → borrar todas las huellas → volver | Botón oculto y la nota «Add a fingerprint…» |
| 17 | Pantalla de apps recientes | La miniatura **no** enseña la app (Android 13+) |
| 18 | Medir: tocar «Sign in» con la contraseña correcta | Cuánto dura «> VERIFYING» (decide D40) |

---

## 13. Qué está verificado y qué no

| | Estado |
|---|--------|
| Reglas, hash (vector oficial), formato, fichero cifrado, libro de cuentas, flujo completo con huella simulada por cifrado real | **Verificado** en la JVM (pruebas + mutación) |
| Que el código Android compila | **Contra *stubs*** hechos con los ficheros de API publicados, con control negativo (romper cada fichero nuevo da errores) |
| Compilación con Gradle y las bibliotecas reales | **Sin hacer** (aquí no hay SDK ni acceso a Maven) |
| Keystore real, diálogo de huella, bloqueo con `ProcessLifecycleOwner`, miniatura de Recientes | **Sin probar en el móvil** (sección 12) |
| Tiempo de Argon2id en el A33 | **Sin medir** (75–160 ms en esta JVM) |

---

## 14. Qué recordar

- **La lógica, pura; Android, solo pegamento.** Hasta el flujo de pantallas se prueba en la JVM, y con **criptografía real** (`javax.crypto` existe en ambos lados).
- **Un vector oficial** (RFC 9106) demuestra que llamas al algoritmo correcto; las pruebas «funcional» no lo demuestran.
- **`generation`:** una operación lenta que empezó antes de bloquear no puede desbloquear después.
- **El estado de un diálogo del sistema vive en el `ViewModel`**, no en la actividad: los giros la recrean.
- **El éxito de la huella es descifrar**, no un `onSuccess`.
- **Mismo mensaje, mismo tiempo, mismo botón** para cuentas que existen y que no: no regalar información.
- **La mutación encuentra fallos reales**, no solo pruebas flojas (el aviso de guardado que la bienvenida tapaba).
