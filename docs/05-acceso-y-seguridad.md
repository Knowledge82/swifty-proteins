# 05 · Acceso y seguridad: cuentas, contraseñas, biometría y bloqueo

> **Estado:** **diseño** (teoría, decisiones, arquitectura, plan de pruebas). Aún sin código. Las firmas de API de `androidx.biometric` y `lifecycle-process` se comprobaron en los ficheros de API publicados del repositorio de AndroidX (`api/1.4.0-rc01.txt`, `api/current.txt`); los parámetros de Argon2 salen de la guía de OWASP; **los tiempos en el A33 son estimaciones** hasta medirlos.

## Índice

1. [Qué pide el enunciado](#1-qué-pide-el-enunciado)
2. [Teoría: hash no es cifrado](#2-teoría-hash-no-es-cifrado)
3. [Teoría: el Android Keystore](#3-teoría-el-android-keystore)
4. [Teoría: la biometría en Android](#4-teoría-la-biometría-en-android)
5. [Teoría: «la app pasó a segundo plano»](#5-teoría-la-app-pasó-a-segundo-plano)
6. [Decisión 1: el algoritmo de hash](#6-decisión-1-el-algoritmo-de-hash)
7. [Decisión 2: dónde viven las cuentas](#7-decisión-2-dónde-viven-las-cuentas)
8. [Decisión 3: qué significa «entrar con la huella»](#8-decisión-3-qué-significa-entrar-con-la-huella)
9. [Decisión 4: cuándo se bloquea](#9-decisión-4-cuándo-se-bloquea)
10. [Decisión 5: reglas de usuario y contraseña, y ataques de fuerza bruta](#10-decisión-5-reglas-de-usuario-y-contraseña-y-ataques-de-fuerza-bruta)
11. [Los mensajes: errores de biometría](#11-los-mensajes-errores-de-biometría)
12. [Pantallas y flujo](#12-pantallas-y-flujo)
13. [Arquitectura: qué es lógica pura y qué es Android](#13-arquitectura-qué-es-lógica-pura-y-qué-es-android)
14. [Dependencias nuevas](#14-dependencias-nuevas)
15. [Trampas](#15-trampas)
16. [Plan de pruebas](#16-plan-de-pruebas)
17. [Alternativas descartadas](#17-alternativas-descartadas)
18. [Decisiones](#18-decisiones)
19. [Qué recordar](#19-qué-recordar)

---

## 1. Qué pide el enunciado

**VI.2** (todo obligatorio):

1. Un sistema de cuentas: Firebase, **almacenamiento seguro de la plataforma** (Keystore) o un servidor propio con hash seguro.
2. Crear cuenta con **nombre de usuario único** y **contraseña con requisitos mínimos**.
3. Entrar con **biometría** (`BiometricPrompt`: huella, cara, iris).
4. Si la biometría falla: **diálogo claro** con el motivo («Authentication failed. Please try again.», «Too many failed attempts.»).
5. Sin biometría en el dispositivo: usuario y contraseña, con la opción biométrica **oculta o desactivada**.
6. **El login se muestra SIEMPRE:** al abrir, al volver de segundo plano, al reabrir tras el botón Inicio, aunque ya se hubiera entrado.
7. Contraseñas **nunca en texto plano**: «bcrypt, Argon2 o almacenamiento seguro de la plataforma».
8. Interfaz clara: etiquetas, **teclado adecuado**, mensajes de error útiles.

**Instrucciones generales (seguridad):** nada sensible en texto plano; **usar el Keystore**; validar lo que llega de la red; **tratar con seguridad los fallos de biometría**. **VIII.2** cita como error común «guardar contraseñas en texto plano **o con algoritmos de hash débiles**» y «no implementar el requisito de seguridad del login».

---

## 2. Teoría: hash no es cifrado

- **Cifrar** es reversible: con la clave se recupera el original. **Hacer hash** no: de la huella no se vuelve a la contraseña. Para comprobar una contraseña no hace falta guardarla: se hace el hash de lo que escribe el usuario y se **compara** con el guardado.
- **El ataque real** no es «invertir» el hash, sino **adivinar**: alguien con el fichero de hashes prueba millones de contraseñas probables («123456», «qwerty», diccionarios, filtraciones) y compara. Contra eso, tres defensas:
  1. **Sal** (*salt*): bytes aleatorios distintos por cuenta que entran en el hash. Dos usuarios con la misma contraseña tienen hashes distintos, y las tablas precalculadas no sirven: hay que atacar **cada cuenta por separado**.
  2. **Lentitud a propósito**: un hash de propósito general (SHA-256) se calcula **miles de millones de veces por segundo** en una tarjeta gráfica. Los algoritmos para contraseñas (bcrypt, scrypt, Argon2, PBKDF2) se hacen **lentos** a propósito: unos cientos de milisegundos no molestan al usuario, pero dividen por millones la velocidad del atacante.
  3. **Memoria a propósito** (*memory-hard*): scrypt y Argon2 además **exigen mucha memoria** por intento. Las tarjetas gráficas y los chips de minado tienen mucho cálculo pero poca memoria por núcleo: así se les quita la ventaja. bcrypt y PBKDF2 no lo hacen.
- **Comparación en tiempo constante:** comparar byte a byte y parar en el primer distinto filtra, por el tiempo, cuántos bytes acertaste. Se compara siempre todo (`MessageDigest.isEqual`).

---

## 3. Teoría: el Android Keystore

- Un **almacén de claves del sistema**. La app pide «crea una clave AES con el alias X» y recibe un **manejador**, nunca los bytes de la clave. Las operaciones (cifrar, descifrar) las hace el sistema; en móviles como el A33 la clave vive en **hardware seguro** (TEE; en algunos, un chip aparte, StrongBox) y **no se puede extraer** ni con el móvil rooteado.
- Una clave puede exigir **autenticación del usuario** para usarse: «esta clave solo descifra si el usuario acaba de pasar la huella». Lo comprueba el hardware seguro, no nuestro código.
- Una clave así puede quedar **invalidada** si se registra una huella nueva (`setInvalidatedByBiometricEnrollment(true)`): quien añade su dedo al móvil no hereda el acceso.
- Las claves del Keystore **no viajan en las copias de seguridad** ni sobreviven a desinstalar la app: lo cifrado con ellas solo se puede leer en **este** móvil y con **esta** instalación.

---

## 4. Teoría: la biometría en Android

- **`BiometricPrompt`** es el diálogo del sistema (huella, cara, iris). La app **nunca ve** la huella: solo recibe «éxito», «fallo» o «error con código».
- **Clases de biometría:** Android clasifica los sensores. **Clase 3 (`BIOMETRIC_STRONG`)**: fiable para operaciones con claves (la huella del A33). **Clase 2 (`BIOMETRIC_WEAK`)**: menos fiable, muchas caras por cámara frontal (el desbloqueo facial del A33 es de este tipo, **según lo que se sabe de Samsung; no comprobado**). Las claves del Keystore que exigen biometría **solo** se desbloquean con Clase 3.
- **Un «éxito» a secas es débil:** es una llamada a nuestro código que dice `onSuccess()`. Con la app manipulada (o en un móvil rooteado con herramientas de *hooking*), se puede forzar esa llamada sin dedo. La forma robusta es el **`CryptoObject`**: le das al diálogo un `Cipher` que usa una clave que **exige** huella; si el sistema no la ha visto, el `Cipher` **no funciona**. El éxito ya no es un booleano: es poder **descifrar algo**.
- **Disponibilidad** (`BiometricManager.canAuthenticate(BIOMETRIC_STRONG)`): `BIOMETRIC_SUCCESS`; `BIOMETRIC_ERROR_NO_HARDWARE` (no hay sensor); `BIOMETRIC_ERROR_HW_UNAVAILABLE` (ocupado o roto); `BIOMETRIC_ERROR_NONE_ENROLLED` (hay sensor, no hay huellas); `BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED`; `BIOMETRIC_ERROR_UNSUPPORTED`; `BIOMETRIC_STATUS_UNKNOWN`.
- **Fallo frente a error:** un dedo no reconocido es un **intento fallido**: el diálogo del sistema dice «No reconocido» y **sigue abierto**. Un **error** cierra el diálogo con un código: `ERROR_LOCKOUT` (7: demasiados intentos, ~30 s), `ERROR_LOCKOUT_PERMANENT` (9: hay que desbloquear el móvil con PIN), `ERROR_USER_CANCELED` (10), `ERROR_NEGATIVE_BUTTON` (13), `ERROR_TIMEOUT` (3), `ERROR_NO_BIOMETRICS` (11), `ERROR_HW_UNAVAILABLE` (1), `ERROR_HW_NOT_PRESENT` (12), `ERROR_CANCELED` (5), `ERROR_UNABLE_TO_PROCESS` (2), `ERROR_NO_SPACE` (4), `ERROR_VENDOR` (8), `ERROR_SECURITY_UPDATE_REQUIRED` (15)… (valores del fichero de API de `androidx.biometric` 1.4.0-rc01).

---

## 5. Teoría: «la app pasó a segundo plano»

- Una **actividad** pasa por `onStart`/`onStop` cuando se ve o deja de verse. Pero girar el móvil también la para y la recrea: «actividad parada» **no** es «app en segundo plano».
- **`ProcessLifecycleOwner`** (`androidx.lifecycle:lifecycle-process`) da el ciclo de vida **de la app entera**: `ON_START` cuando se ve la primera actividad, `ON_STOP` cuando ya no se ve ninguna (con un pequeño retraso para que un giro no cuente). Es exactamente «la app fue a segundo plano / volvió».
- Lo que **no** lo dispara: diálogos dentro de la app y el diálogo de la huella (es una ventana sobre la nuestra; la actividad se **pausa**, no se **para**).
- Lo que **sí** lo dispara: Inicio, cambiar de app, apagar la pantalla **y la hoja de compartir** (es otra actividad, del sistema, a pantalla completa).

---

## 6. Decisión 1: el algoritmo de hash

| Algoritmo | Memoria a propósito | En el enunciado | En Android | Veredicto |
|-----------|---------------------|-----------------|------------|-----------|
| **Argon2id** | **Sí** | **Sí** («Argon2») | Biblioteca: Bouncy Castle (`Argon2BytesGenerator`, Java puro) | **Elegido** |
| bcrypt | No | Sí | Bouncy Castle (`OpenBSDBCrypt`) | Bueno; límite de 72 bytes por contraseña |
| scrypt | Sí | No | Bouncy Castle | Bueno, pero no nombrado |
| PBKDF2-HMAC-SHA256 | No | No | **Viene con Android** | Aceptado por OWASP con 600 000 iteraciones, pero un evaluador podría verlo como «débil» (VIII.2) |
| SHA-256 con sal | No, y rápido | No | Viene | **Insuficiente**: es justo el «hash débil» de VIII.2 |

**Argon2id** ganó la *Password Hashing Competition* (2015), es el primero de la lista de OWASP y está **nombrado** en el enunciado. La variante **id** mezcla la resistencia a canales laterales de Argon2i con la resistencia a tarjetas gráficas de Argon2d.

**Parámetros:** la configuración mínima de OWASP: **memoria 19 MiB (19 456 KiB), 2 pasadas, paralelismo 1**; sal de **16 bytes** aleatorios (`SecureRandom`), resultado de **32 bytes**. Los parámetros **se guardan con cada cuenta**: si mañana se suben, las cuentas antiguas se siguen comprobando con los suyos y se pueden **re-hashear** al entrar.

**Coste en el A33: sin medir.** Bouncy Castle es Java puro (más lento que C nativo). La estimación es **del orden de cientos de milisegundos**; por eso el hash corre en `Dispatchers.Default` (nunca en el hilo de la interfaz) y la pantalla muestra «Verifying…». Se **medirá** en el móvil; si pasa de ~1 s, se baja a la otra configuración de OWASP con menos memoria y más pasadas.

---

## 7. Decisión 2: dónde viven las cuentas

**Un fichero propio**, `accounts.bin`, en `noBackupFilesDir` (carpeta privada de la app **excluida** de las copias de seguridad), **cifrado** con AES-256-GCM con una clave del **Keystore** (alias `swifty_accounts`, sin exigir huella).

Por qué cifrar si las contraseñas ya van en hash:

- **Defensa en profundidad:** sin cifrar, quien copie el fichero (móvil rooteado, copia de seguridad) tiene los hashes y la lista de usuarios y puede atacarlos **fuera del móvil**, con todas las tarjetas gráficas que quiera. Cifrado con una clave que **no sale del hardware**, el fichero robado es ruido.
- **El enunciado pide el Keystore** («use platform-specific secure storage»): así se usa de verdad.
- **GCM** cifra **y autentica**: un fichero manipulado no se descifra «mal», se **rechaza**.

Por qué no lo demás:

| Opción | Por qué no |
|--------|-----------|
| `EncryptedSharedPreferences` (Jetpack Security) | La biblioteca `security-crypto` está **obsoleta** (Google dejó de recomendarla) |
| Room (SQLite) | Una base de datos para unas pocas cuentas es demasiado; y sin cifrar es un fichero legible |
| DataStore | Bien, pero no cifra: habría que cifrar igual |
| Firebase Authentication | Necesita red y un proyecto en Google; el enunciado lo permite, pero entrar sin conexión dejaría de funcionar |

**Formato:** texto propio, una línea por cuenta, campos en hexadecimal (sin depender de `org.json` ni de `java.util.Base64`, que no existe en API 24–25): así **todo el formato se prueba en la JVM**. Escritura **atómica** (fichero temporal + renombrar), como la de la captura (documento 09).

**Si el fichero no se puede descifrar** (se restauró de otro móvil, el Keystore se borró): se trata como **sin cuentas**, con un aviso claro («Stored accounts could not be read on this device. Create a new account.»). Nunca un cierre.

---

## 8. Decisión 3: qué significa «entrar con la huella»

**El problema de fondo:** la huella demuestra «es el dueño del móvil», **no** «es el usuario *adept42*». Con varias cuentas en el móvil, hay que decidir **qué cuenta** abre la huella.

**La decisión:** la biometría es **por cuenta y voluntaria**.

1. Tras crear la cuenta (o tras entrar con contraseña, si aún no se ofreció), la app pregunta: **«Enable bio-auspex for *adept42*?»**. Activarla **pide la huella en ese momento** (confirma que el dueño del dedo está de acuerdo).
2. Al activarla se crea en el Keystore una clave AES **de esa cuenta** que **exige huella en cada uso** y **se invalida** si se registra una huella nueva. Con ella se cifra un **testigo** aleatorio de 32 bytes; en la cuenta se guardan el testigo cifrado y su IV.
3. **Para entrar:** el campo de usuario viene **relleno con el último que entró**; si esa cuenta tiene la huella activada, aparece el botón **«BIO-AUSPEX SCAN»**. El diálogo del sistema recibe un `Cipher` de **descifrado** con esa clave (`CryptoObject`); si el sistema acepta el dedo, el `Cipher` descifra el testigo, y **coincide** con el de la cuenta → dentro.
4. El botón de la huella **se oculta** si el dispositivo no tiene biometría de Clase 3 o no hay huellas registradas (con una línea que explica por qué y cómo activarla), y **se desactiva** si la cuenta escrita no la tiene activada.

**API:** `androidx.biometric` 1.4 trae una interfaz nueva, **`registerForAuthenticationResult(activity, callback)`** con `AuthenticationRequest.Biometric` y `Strength.Class3(cryptoObject)`, al estilo de *Activity Result*, que funciona con `ComponentActivity` (nuestra `MainActivity` no tiene que cambiar a `FragmentActivity`). Según las notas de versión, **sustituye** a la clásica `BiometricPrompt` (que sigue existiendo y no está marcada obsoleta). Se usa la **nueva**; la clásica queda como alternativa. El botón negativo del diálogo es una opción propia, **«Use password»** (`Fallback.CustomOption`), que vuelve al campo de contraseña.

---

## 9. Decisión 4: cuándo se bloquea

**Lectura estricta:** la app se bloquea en **cada** `ON_STOP` del proceso (`ProcessLifecycleOwner`): Inicio, cambio de app, pantalla apagada **y también al volver de la hoja de compartir**.

El documento 01 (ambigüedad 2) planteaba distinguir «el usuario se fue» de «la app abrió un diálogo del sistema». Se descarta, por tres motivos:

1. El enunciado dice «**ALWAYS**», en mayúsculas, y «**even if the user was previously authenticated**». Un evaluador nunca penalizará pedir el login de más; sí podría penalizar no pedirlo.
2. La excepción abre un agujero: desde la hoja de compartir se puede pulsar Inicio, y la app volvería **sin login**. Cerrarlo exige temporizadores y casos raros.
3. El coste es pequeño: tras compartir, **un toque y la huella**.

El diálogo de la huella **no** bloquea (no dispara `ON_STOP`), y **girar el móvil tampoco**.

**Qué se ve al volver:** el login **encima** de todo; detrás, la app **conserva** dónde estaba (la lista con la búsqueda, o la molécula abierta), pero **no se dibuja** mientras está bloqueada. Tras entrar, se vuelve allí. Si Android mató el proceso en segundo plano, se empieza de cero: arranque y login.

**La miniatura de «Recientes»:** Android guarda una captura de la app para la pantalla de apps recientes; sin hacer nada, enseñaría la molécula **sin login**. En **Android 13+** se desactiva con `setRecentsScreenshotEnabled(false)`. No se usa `FLAG_SECURE`: además de la miniatura bloquearía **las capturas**, y nuestra función de compartir **es** una captura (de la vista 3D, con `PixelCopy`; con `FLAG_SECURE` podría salir negra).

---

## 10. Decisión 5: reglas de usuario y contraseña, y ataques de fuerza bruta

**Usuario:** se recortan espacios y se pasa a minúsculas; **3–20 caracteres**, solo `a–z`, `0–9`, `_`, `.`, `-`. **Único sin distinguir mayúsculas** («Adept» y «adept» son el mismo). Teclado: texto ASCII, sin autocorrección ni mayúscula automática.

**Contraseña:** **8–64 caracteres**, **al menos una letra y un dígito**, **no puede contener el usuario**. Se repite en un segundo campo al crear la cuenta. La pantalla enseña la **lista de reglas** y marca cada una al cumplirse, mientras se escribe.

> **Nota honesta:** la guía del NIST (SP 800-63B) desaconseja las reglas de composición («una letra y un dígito») y prefiere longitud + listas de contraseñas comprometidas. Aquí se añade la regla de letra y dígito porque el enunciado pide «requisitos mínimos de seguridad» **visibles**, y porque una lista de contraseñas comprometidas necesitaría un fichero grande o la red. Lo decimos así en la defensa.

Teclado de contraseña (`KeyboardType.Password`: sin sugerencias ni aprendizaje), botón para **ver/ocultar**, y pistas de autorrelleno (`ContentType.Username`/`NewPassword`/`Password`) para que un gestor de contraseñas pueda ayudar.

**Fuerza bruta en el propio móvil:**

- Mensaje **único** para usuario inexistente y contraseña errónea: **«Wrong username or password.»** (no revela qué usuarios existen).
- Con un usuario inexistente se calcula **igual** un hash (con una sal fija): el tiempo de respuesta no lo delata.
- **Freno por cuenta**, guardado en el fichero (sobrevive a cerrar la app): tras **5** fallos seguidos, esperar **30 s**; cada nuevo fallo **duplica** la espera, hasta **5 min**. Entrar bien lo pone a cero. La pantalla dice cuánto falta.

---

## 11. Los mensajes: errores de biometría

| Código | Diálogo (título · texto) |
|--------|--------------------------|
| `ERROR_LOCKOUT` | «Too Many Attempts» · «Too many failed attempts. Fingerprint is disabled for 30 seconds. Use your password, or try again later.» |
| `ERROR_LOCKOUT_PERMANENT` | «Biometrics Locked» · «Too many failed attempts. Unlock the phone with its PIN, pattern or password to use the fingerprint again. You can sign in with your password now.» |
| `ERROR_TIMEOUT` | «Time Out» · «Authentication timed out. Please try again.» |
| `ERROR_NO_BIOMETRICS` | «No Fingerprint» · «No fingerprint is enrolled on this phone. Add one in the phone settings, or use your password.» |
| `ERROR_HW_UNAVAILABLE`, `ERROR_HW_NOT_PRESENT` | «Sensor Unavailable» · «The biometric sensor is not available right now. Use your password.» |
| `ERROR_USER_CANCELED`, `ERROR_NEGATIVE_BUTTON`, `ERROR_CANCELED` | **Sin diálogo**: lo decidió el usuario (o el sistema, al irse la app); se queda en el login |
| Clave invalidada (huella nueva registrada) | «Fingerprints Changed» · «The fingerprints on this phone changed, so the fingerprint sign-in was turned off for safety. Sign in with your password to turn it on again.» |
| Cualquier otro código | «Authentication Failed» · «Authentication failed. Please try again.» + el texto del sistema |

Un dedo **no reconocido** no abre diálogo propio: el del sistema ya dice «No reconocido» y deja reintentar; al quinto, llega `ERROR_LOCKOUT`. El título lleva el estilo de la app (Pirata One); el texto, inglés llano, como los avisos de carga.

---

## 12. Pantallas y flujo

```
Arranque (4,2 s, una vez)
   │
   ▼
¿Hay cuentas? ──no──► CREATE ACCOUNT ─────────────┐
   │ sí                                          │
   ▼                                             ▼
LOGIN  «NOOSPHERE ACCESS — Identify, Adept»   «Enable bio-auspex?» (si el móvil puede)
   │  usuario (relleno con el último)              │
   │  contraseña · SIGN IN                         │
   │  BIO-AUSPEX SCAN (si la cuenta la tiene)      │
   │  Create account                               │
   ▼                                               ▼
Lista ◄──────────────────────────────────────────────┘
   │
   └── ON_STOP (Inicio, otra app, pantalla apagada, compartir) ──► LOGIN encima; al entrar, vuelta a donde estaba
```

- Ilustración: el **servidor** encorvado sobre la consola (`art_servitor`) en el login («el servidor espera tu huella»); el **tecnosacerdote** (`art_tech_priest`, hoy sin usar) al crear cuenta.
- **Confirmación de éxito** («Success confirmations», instrucciones generales): «Account created. Welcome, adept42.» y «Bio-auspex enabled.».
- Atrás en el login: **sale de la app** (no hay nada «detrás» a lo que volver sin entrar).

---

## 13. Arquitectura: qué es lógica pura y qué es Android

**Lógica pura** (paquete `auth`, sin Android, **probada en la JVM**):

| Pieza | Qué hace |
|-------|----------|
| `UsernamePolicy` | Normaliza y valida el usuario; devuelve qué regla falla |
| `PasswordPolicy` | Lista de reglas y cuáles se cumplen (para la lista en pantalla) |
| `PasswordHasher` / `Argon2Hasher` | Hash y verificación (Bouncy Castle), parámetros en el registro, comparación en tiempo constante |
| `Account`, `AccountCodec` | El registro de una cuenta y su formato de texto (ida y vuelta probada) |
| `AccountBook` | Crear, buscar sin mayúsculas, verificar contraseña con freno y reloj inyectable, activar/desactivar biometría |
| `LoginThrottle` | 5 fallos → 30 s, duplicando hasta 5 min |
| `LockState` | Bloqueado/desbloqueado; `ON_STOP` → bloqueado |
| `BiometricText` | Código de error → título y texto (o «sin diálogo»); disponibilidad → qué enseña el botón |

**Pegamento Android:**

| Pieza | Qué hace |
|-------|----------|
| `KeystoreAead` | Clave AES-GCM del Keystore; cifra/descifra el fichero |
| `AccountFile` | Lee y escribe `accounts.bin` (atómico) |
| `BiometricKeys` | Clave por cuenta con huella obligatoria; `Cipher` para cifrar/descifrar; detecta la invalidación |
| `AuthViewModel` | Estado de las pantallas; hash en `Dispatchers.Default`; fichero en `Dispatchers.IO` |
| `AppLock` | Observa `ProcessLifecycleOwner` y bloquea |
| `LoginScreen`, `CreateAccountScreen`, diálogos | Compose, con el estilo del cogitador |
| `MainActivity` | Registra el lanzador de biometría en `onCreate`; desactiva la miniatura de Recientes |

---

## 14. Dependencias nuevas

| Biblioteca | Versión | Para qué |
|------------|---------|----------|
| `androidx.biometric:biometric` | **1.4.0-rc01** | Diálogo de biometría con la API nueva. La última estable es la 1.1.0 (2021); la 1.4 es candidata a versión final (18-11-2026) y el enunciado pide versiones recientes |
| `androidx.lifecycle:lifecycle-process` | **2.11.1** | `ProcessLifecycleOwner` |
| `org.bouncycastle:bcprov-jdk18on` | **1.87** (última etiqueta del repositorio, `r1rv87`) | Argon2id |

De paso: el catálogo trae versiones **viejas** de la plantilla (`core-ktx` 1.10.1, `lifecycle` 2.6.1, `activity-compose` 1.8.0). Las últimas estables son `core` 1.19.1, `lifecycle` 2.11.1 y `activity` 1.13.0 (página de versiones de AndroidX, 18-11-2026). **Todas las de `lifecycle` deben ir a la par**, así que se suben juntas al añadir `lifecycle-process`.

**Riesgo:** desde aquí no se puede descargar de Maven (la red lo bloquea). Las firmas de API se comprobaron en los ficheros de API del código fuente de AndroidX; la compilación real la hace Gradle en el ordenador del equipo.

---

## 15. Trampas

1. **El hash en el hilo de la interfaz** congela la pantalla cientos de milisegundos (y en el límite, «la app no responde»). Siempre en `Dispatchers.Default`.
2. **`KeyPermanentlyInvalidatedException`**: al registrar una huella nueva, la clave de la cuenta muere. Hay que capturarla **al crear el `Cipher`** (antes de abrir el diálogo), desactivar la huella de esa cuenta y explicarlo.
3. **Clase 2 no sirve con `CryptoObject`:** si el móvil solo tiene cara «débil», el botón se oculta (no hay Clase 3). Un evaluador con un móvil así verá solo la contraseña: es lo correcto y se explica.
4. **El lanzador de biometría se registra en `onCreate`**, antes de `STARTED`, como cualquier *Activity Result*; registrarlo tarde lanza una excepción.
5. **Copias de seguridad:** el fichero va en `noBackupFilesDir`; si aun así llegara uno de otro móvil, no se descifra (otra clave) y se trata como «sin cuentas», con aviso.
6. **Reinstalar borra las cuentas** (el Keystore se borra con la app). Y la desinstalación obligatoria al cambiar de firma (documento 11) también.
7. **Tests que «prueban» Argon2 con parámetros de producción** tardarían segundos: en las pruebas se usan parámetros mínimos y **vectores oficiales** (RFC 9106) para comprobar que el algoritmo es el correcto.
8. **Ocultar la contraseña también en los registros:** nunca `Log` con la contraseña; la cadena se suelta en cuanto se ha hecho el hash (en Kotlin no se puede «borrar» un `String`; se documenta el límite).
9. **El evaluador pulsa Inicio en mitad de la carga de un ligando:** al volver, login encima; la carga sigue detrás y su resultado espera. No debe perderse ni duplicarse.

---

## 16. Plan de pruebas

**En la JVM (automáticas):** reglas de usuario y contraseña (límites exactos: 2/3/20/21 caracteres, sin letra, sin dígito, contiene el usuario, mayúsculas); Argon2id contra el **vector de RFC 9106**; verificar bien/mal, sal distinta → hash distinto; ida y vuelta del formato del fichero, fichero truncado o manipulado → error controlado; usuarios duplicados con mayúsculas; freno (4 fallos sin espera, 5.º → 30 s, luego 60, 120…, tope 5 min, éxito → cero) con reloj falso; textos de biometría para cada código; estado de bloqueo. Mutación de las piezas críticas, como en las demás etapas.

**En el móvil (lista):** crear cuenta (y duplicada, y contraseña débil); entrar con contraseña bien/mal; 5 fallos → espera; activar la huella; entrar con huella; dedo equivocado 5 veces → diálogo «Too Many Attempts»; cancelar → sin diálogo; Inicio y volver → login; compartir y volver → login; girar → **sin** login; añadir una huella nueva en Ajustes → «Fingerprints Changed»; quitar todas las huellas → botón oculto con explicación; miniatura de Recientes sin contenido; medir el tiempo del hash.

---

## 17. Alternativas descartadas

| Alternativa | Por qué no |
|-------------|-----------|
| Biometría sin `CryptoObject` (solo el «éxito») | Más simple, pero el éxito es un booleano que se puede falsear; el enunciado pide tratar la seguridad en serio |
| Huella que entra «a la última cuenta» sin mostrarla | Ambiguo con varias cuentas; mostrar el usuario relleno deja claro quién entra |
| Huella como **único** factor sin contraseña | El enunciado exige poder entrar con contraseña |
| Excepción de bloqueo para la hoja de compartir | Sección 9 |
| `FLAG_SECURE` | Rompería la captura de compartir; `setRecentsScreenshotEnabled(false)` cubre la miniatura |
| *Passkeys* / Credential Manager | Modernas, pero pensadas para cuentas en un servidor; aquí no hay servidor |
| Argon2 nativo (biblioteca en C) | Más rápido, pero no se prueba en la JVM y añade código nativo; si el tiempo medido no es aceptable, es el plan B |

---

## 18. Decisiones

| # | Decisión | Estado | Motivo |
|---|----------|--------|--------|
| D40 | Contraseñas con **Argon2id** (Bouncy Castle), 19 MiB, 2 pasadas, paralelismo 1, sal de 16 bytes; parámetros guardados por cuenta | Decidida (tiempo sin medir) | Nombrado en el enunciado; resistente a tarjetas gráficas; recomendado por OWASP |
| D41 | Cuentas en un fichero propio en `noBackupFilesDir`, **cifrado con AES-GCM** con clave del **Keystore** | Decidida | Defensa en profundidad; usa el Keystore como pide el enunciado; sin bibliotecas obsoletas |
| D42 | Biometría **por cuenta y voluntaria**, Clase 3, con **`CryptoObject`** (testigo cifrado con clave que exige huella y se invalida con huellas nuevas); API nueva de `androidx.biometric` 1.4 | Decidida | La huella prueba al dueño, no la cuenta; el `CryptoObject` hace el éxito verificable |
| D43 | Bloqueo en **cada** `ON_STOP` del proceso, **incluida la hoja de compartir**; la app conserva dónde estaba; miniatura de Recientes desactivada (Android 13+) | Decidida. **Sustituye la lectura de la ambigüedad 2 del documento 01** | «ALWAYS» del enunciado; sin agujeros; coste pequeño |
| D44 | Usuario 3–20 `[a-z0-9_.-]`, único sin mayúsculas; contraseña 8–64 con letra y dígito, sin el usuario; mensaje único de error; freno 5 fallos → 30 s duplicando hasta 5 min | Decidida | Requisitos visibles; sin revelar usuarios; frena la fuerza bruta local |

---

## 19. Qué recordar

- **Hash ≠ cifrado.** Contraseñas: hash **lento, con sal y con memoria** (Argon2id). Datos que hay que recuperar: cifrado con clave del **Keystore**.
- **La huella prueba al dueño del móvil, no la cuenta.** Por eso va ligada a una cuenta, y por eso el `CryptoObject`: el éxito es **descifrar**, no un booleano.
- **Clase 3** para claves; la cara «débil» no vale.
- **`ProcessLifecycleOwner.ON_STOP`** = «la app fue a segundo plano» (girar no cuenta, el diálogo de huella tampoco; compartir **sí**).
- **Un mensaje para «usuario o contraseña incorrectos»**, mismo tiempo de respuesta, freno que sobrevive a cerrar la app.
- **Nada de trabajo pesado en el hilo de la interfaz.**
