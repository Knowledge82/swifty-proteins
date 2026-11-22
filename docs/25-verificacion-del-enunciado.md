# 25 · Verificación punto por punto contra el enunciado

> **Para qué sirve.** Recorre el enunciado **frase a frase**: primero la parte obligatoria (VI.1–VI.4), luego las instrucciones generales y por último los bonus (VII.1–VII.5). Para cada exigencia dice **si está hecha**, **cómo**, **en qué ficheros**, **por qué así** y **qué enseñar** en la defensa. Es la base de la presentación: cada apartado puede convertirse en una o dos diapositivas.
>
> **Estado de la app a 22-11-2026:** 618 pruebas automáticas en 61 clases, todas en verde; pruebas de mutación en cada regla nueva. Las rutas de código son relativas a `app/src/main/java/dev/knowledge82/swiftyproteins/`; las de recursos, a `app/src/main/`.
>
> **Leyenda:** ✅ hecho y comprobado en el Samsung Galaxy A33 · 🟢 hecho y probado en la JVM, **falta verlo en el móvil** · ⚠️ riesgo o matiz que hay que explicar.

## Índice

- [Resumen en una tabla](#resumen-en-una-tabla)
- **Parte obligatoria**
  - [1. Icono y pantalla de arranque (VI.1)](#1-icono-y-pantalla-de-arranque-vi1)
  - [2. Pantalla de acceso (VI.2)](#2-pantalla-de-acceso-vi2)
  - [3. Lista de ligandos (VI.3)](#3-lista-de-ligandos-vi3)
  - [4. Vista 3D de la molécula (VI.4)](#4-vista-3d-de-la-molécula-vi4)
- [5. Instrucciones generales](#5-instrucciones-generales)
- **Parte de bonus**
  - [6. VII.1 Modelos de visualización](#6-vii1-modelos-de-visualización)
  - [7. VII.2 Interfaz avanzada](#7-vii2-interfaz-avanzada)
  - [8. VII.3 Interacción molecular](#8-vii3-interacción-molecular)
  - [9. VII.4 Rendimiento y caché](#9-vii4-rendimiento-y-caché)
  - [10. VII.5 Compartir y exportar ampliado](#10-vii5-compartir-y-exportar-ampliado)
- [11. Lo que falta comprobar en el móvil](#11-lo-que-falta-comprobar-en-el-móvil)
- [12. De este documento a la presentación](#12-de-este-documento-a-la-presentación)

---

## Resumen en una tabla

| Apartado | Exigencias | Estado | Documentos |
|----------|-----------|--------|------------|
| VI.1 Icono y arranque | Icono temático en todas las densidades; arranque ≥ 1-2 s, de marca, que no parezca «carga eterna» | ✅ | 07b, 08c, 08d |
| VI.2 Acceso | Cuentas (usuario único, contraseña con requisitos), biometría, diálogos de fallo, alternativa sin biometría, **login siempre**, hash, interfaz clara | ✅ | 05, 05a |
| VI.3 Lista | Todos los ligandos, lista desplazable, búsqueda en tiempo real, indicador de carga, descarga + *parsing* + navegación, cuatro errores con sus textos | ✅ | 03, 06, 06b, 06c |
| VI.4 Vista 3D | Filament, CPK, bolas y varillas, ficha del átomo, gestos, compartir, cámara y luces, fluidez | ✅ (compartir y desplazamiento con dos dedos: 🟢) | 02, 07, 09, 12, 18 |
| VII.1 Modelos | Espacio lleno, varillas, alambre; cambio sin recargar | ✅ | 15, 19 |
| VII.2 Interfaz | Celdas propias, animaciones, modo oscuro (también en 3D), tutorial, ajustes | ✅ / 🟢 (transición CRT, destello) | 08c, 14, 16, 21, 23 |
| VII.3 Interacción | Resaltar un elemento, información de enlaces, distancias y ángulos, etiquetas, doble toque | ✅ / 🟢 (etiquetas, regla) | 15, 16, 19 |
| VII.4 Rendimiento | Caché, carga perezosa, *parsing* en segundo plano con progreso, memoria, 60 FPS | ✅ | 12, 13, 20, 21 §18-20 |
| VII.5 Compartir | Mensaje con nombre/átomos/fórmula, varios formatos, vídeo, favoritos, comparar dos moléculas | ✅ / 🟢 (formatos, vídeo, comparación) | 15, 16, 18, 22, 24 |

---

# Parte obligatoria

## 1. Icono y pantalla de arranque (VI.1)

### 1.1 Icono temático en todas las resoluciones

> *Choose an icon for your application that is in accordance with the theme (molecular/scientific/medical). The icon must be properly sized for all required resolutions on your platform.*

**✅ Cómo lo cumplimos.** El icono es un **cráneo cibernético con ocular verde que escanea con su haz el holograma de una molécula**: ciencia molecular (el ligando) en el estilo de toda la app (el «Culto de la Máquina», documento 08c). Se eligió la composición en **diagonal** (variante A) entre dos propuestas, porque a 48 px el cráneo sigue siendo reconocible y el haz cuenta lo que hace la app: **mirar un ligando** (documento 08d, §12.3).

**Dónde y en qué tamaños** (medidos en los ficheros):

| Fichero | Para qué | mdpi | hdpi | xhdpi | xxhdpi | xxxhdpi |
|---------|----------|------|------|-------|--------|---------|
| `res/mipmap-*/ic_launcher_foreground.webp` | Capa delantera del **icono adaptativo** (Android 8+) | 108 | 162 | 216 | 324 | 432 px |
| `res/mipmap-*/ic_launcher_background.webp` | Capa de fondo | 108 | 162 | 216 | 324 | 432 px |
| `res/mipmap-*/ic_launcher.png`, `ic_launcher_round.png` | Icono clásico (Android 7) | 48 | 72 | 96 | 144 | 192 px |
| `res/mipmap-anydpi-v26/ic_launcher.xml` (y `_round`) | Une las dos capas y el monocromo | — | — | — | — | — |
| `res/drawable/ic_launcher_monochrome.xml` | Silueta vectorial para los **iconos temáticos** de Android 13 | vector | | | | |

**Por qué así.**
- Un **icono adaptativo** tiene dos capas de 108 dp. El lanzador recorta la máscara que quiere (círculo, *squircle*, gota) y las mueve un poco en paralaje. Las capas miden exactamente **108 dp × densidad**: 1×, 1,5×, 2×, 3× y 4×.
- La silueta **monocroma** es un vector: el sistema la tiñe con los colores del fondo de pantalla (Material You).
- La composición se **comprobó con un guion**: nada fuera del círculo seguro (radio máximo 303,6 px ≤ 313), un 88 % de píxeles realmente transparentes y sin halo claro alrededor del cráneo.
- Declarado en `AndroidManifest.xml`: `android:icon="@mipmap/ic_launcher"` y `android:roundIcon="@mipmap/ic_launcher_round"`.

🎯 **Qué enseñar:** el icono en la pantalla de inicio del A33 y, si da tiempo, con los iconos temáticos activados.

### 1.2 Pantalla de arranque visible al menos 1-2 s

> *When the application is launched, it must display a Launch Screen that remains visible for at least 1-2 seconds. The launch screen should be professional and thematic.*

**✅ Cómo lo cumplimos.** Hay **dos capas**, y es importante explicar por qué:

1. **La *splash* del sistema**, que existe pero es **invisible**.
   - Desde Android 12 el sistema **siempre** enseña una *splash* propia al abrir una app; no se puede quitar.
   - La configuramos con el mismo color del fondo de la app (`swifty_abyss`) y un icono **transparente** (`res/drawable/splash_icon_blank.xml`).
   - Tema `Theme.SwiftyProteins.Starting` en `res/values/themes.xml`; `installSplashScreen()` en `MainActivity.kt` la cambia por el tema normal en cuanto la app dibuja. La biblioteca `core-splashscreen` hace que funcione igual desde Android 7.
   - **Por qué invisible:** si se viera, el usuario vería **dos logotipos seguidos**, el del sistema y el nuestro. La *splash* del sistema dura solo lo que tarda la app en dibujar su primer fotograma; retenerla a propósito va contra las guías de Android, y solo puede enseñar un icono.
2. **Nuestra pantalla de arranque**, `BootScreen` (`ui/BootScreen.kt`), que es la *launch screen* de verdad: **la ignición del cogitador**.
   - En el centro, el **emblema completo** (`art_emblem_hologram`): el cráneo, el holograma del ligando, el sello «42» y la tira «101010».
   - Debajo, cinco líneas de consola que se escriben una a una:
     ```
     > LITANY OF IGNITION ... OK
     > MACHINE SPIRIT ... AWAKE
     > DATA-VAULT ... 1,243          ← el número REAL de ligandos leídos de ligands.txt
     > AUSPEX ENGINE ... READY
     > SEEKING ADEPT ...
     ```
   - Una barra `[##][##][  ]…` y al final «> DONE», que parpadea.
   - Suena: el zumbido del tubo, un tic por línea y dos campanas al terminar (documento 14).
   - **Duración: 4,2 s** (`boot/BootSequence.kt`, `DURATION_MILLIS`). Se puede **saltar con un toque**, pero **nunca antes de 1 s** (`MIN_VISIBLE_MILLIS = 1_000`): el mínimo del enunciado se cumple siempre, también para quien toca enseguida.
   - Además **espera de verdad** a algo: no sale hasta que se han leído los **ajustes guardados** (`Navigation.screenOf`: «arranque, hasta que termine **y** se hayan leído los ajustes»). Si no esperara, el tutorial aparecería un instante a quien ya lo vio.
   - Es **siempre oscura** (un CRT que se enciende), sea cual sea el tema elegido.
   - Al abrir la app en frío, el tubo **se enciende** sobre ella: un punto que se estira en una línea y la línea que se abre (documento 23).

🎯 **Qué enseñar:** cerrar la app desde Recientes y abrirla: punto → línea → arranque completo con sonido → acceso.

### 1.3 Que no parezca una carga eterna

> *The launch screen must not be a static image that looks like the app is loading forever. It should be a clean, branded splash screen.*

**✅ Cómo lo cumplimos.**
- **No es estática:** las líneas aparecen una cada ~0,55 s, la barra se llena y «DONE» parpadea.
- **Tiene un final visible:** la barra llega al final y sale «> DONE». El usuario **ve** que ha terminado y no se queda esperando.
- **Es de marca:** el emblema, la tipografía gótica del nombre, la consola y los colores de la app.
- Con las **animaciones del sistema desactivadas** (accesibilidad), las líneas salen todas a la vez, pero la pantalla dura lo mismo: sigue sin parecer colgada.
- Toda la temporización es **pura y probada** (`BootSequenceTest`): cuándo sale cada línea, cuándo se llena la barra, cuándo se puede saltar.

---

## 2. Pantalla de acceso (VI.2)

### 2.1 Sistema de autenticación

> *Choose an authentication system that allows you to store and manage user accounts. Options include: Firebase Authentication · Platform-specific secure storage (Keychain on iOS, KeyStore on Android) · Custom backend with secure password hashing.*

**✅ Cómo lo cumplimos:** cuentas **locales en el móvil**, con **dos** de las opciones del enunciado a la vez: **hash seguro** de la contraseña (Argon2id) **y** el **Android Keystore**.

| Pieza | Fichero | Qué hace |
|-------|---------|----------|
| Reglas de las cuentas | `auth/AuthController.kt` | Crear, entrar, huella, bloqueo, frenos; **puro**, probado en la JVM |
| Hash | `auth/PasswordHasher.kt` | Argon2id (Bouncy Castle) |
| Fichero de cuentas | `auth/AccountStore.kt`, `auth/AccountBook.kt` | Cifrado AES-256-GCM, en `noBackupFilesDir` |
| Llaves del Keystore y biometría | `auth/AndroidSecurity.kt` | La llave del fichero y una llave **por cuenta** para la huella |
| Normas | `auth/Policies.kt` | Usuario, contraseña, freno de intentos |
| Pantallas | `ui/AuthScreens.kt`, `ui/AuthViewModel.kt` | Formularios y diálogos |

**Por qué local y no Firebase:** no se necesita servidor, funciona sin conexión, y **ninguna** contraseña ni huella sale nunca del móvil. Firebase añadiría una cuenta de Google, red obligatoria para entrar y datos de terceros (documento 05).

### 2.2 Crear una cuenta: usuario único y contraseña con requisitos

> *A user must be able to create an account with at least: Username (unique identifier) · Password (must meet minimum security requirements).*

**✅ Cómo lo cumplimos.**
- **Usuario** (`UsernamePolicy`):
  - de 3 a 20 caracteres: letras minúsculas, cifras y `_ . -`;
  - se **normaliza** (sin espacios en los extremos, en minúsculas): «Valera» y «valera» son la misma cuenta;
  - **único**: si ya existe, la app lo dice («This user name is already taken.»).
- **Contraseña** (`PasswordPolicy`):
  - de 8 a 64 caracteres (contados como los ve el usuario: un emoji es **uno**);
  - al menos **una letra** y **una cifra**;
  - que **no contenga** el nombre de usuario.
- Los requisitos se ven como una **lista de comprobación en vivo** mientras se escribe, cada línea marcada al cumplirse; hay que **repetir** la contraseña.
- Al crearla, la app **ofrece activar la huella** para esa cuenta.

### 2.3 Entrar con biometría

> *A user must be able to log in with biometric authentication: iOS: Face ID or Touch ID · Android: BiometricPrompt API (fingerprint, face, iris).*

**✅ Cómo lo cumplimos.** El botón de huella abre el **`BiometricPrompt`** del sistema (`androidx.biometric`).

**Lo que lo hace seguro de verdad:**
- Se exige biometría de **Clase 3** (fuerte) **con `CryptoObject`**. El éxito no es un simple `true` que cualquiera podría falsificar: la huella **desbloquea una llave del Keystore** de esa cuenta (`setUserAuthenticationRequired(true)`, solo con `AUTH_BIOMETRIC_STRONG`), y esa llave descifra un *token* de la cuenta. Sin huella válida, la llave no se puede usar.
- Si cambian las huellas del móvil, la llave **se invalida sola** (`setInvalidatedByBiometricEnrollment(true)`) y hay que volver a activarla con la contraseña.
- La huella **nunca** llega a la app: se queda en el sensor y en el hardware seguro; la app solo recibe el uso de la llave.

🎯 Crear una cuenta → aceptar la huella → Home → volver → entrar con la huella → lista.

### 2.4 Si la biometría falla, un diálogo que explica por qué

> *If biometric authentication fails, you must display a clear error message in a popup or alert dialog explaining why authentication failed (e.g., "Authentication failed. Please try again." or "Too many failed attempts.").*

**✅ Cómo lo cumplimos.** `auth/BiometricText.kt` traduce cada error del sistema a **un diálogo con título y explicación**:

| Situación | Título | Texto (resumido) |
|-----------|--------|------------------|
| 5 fallos: bloqueo de 30 s | **Too Many Attempts** | «Too many failed attempts. The fingerprint is disabled for 30 seconds…» |
| Bloqueo permanente | **Biometrics Locked** | «…Unlock the phone with its PIN, pattern or password…» |
| Tiempo agotado | **Time Out** | «Authentication timed out. Please try again.» |
| Sin huellas registradas | **No Fingerprint** | «…Add one in the phone settings, or use your password.» |
| Sensor no disponible | **Sensor Unavailable** | |
| Huellas cambiadas | **Fingerprints Changed** | «…turned off for safety…» |
| Cualquier otro error | **Authentication Failed** | «**Authentication failed. Please try again.**» (el texto del enunciado) |

- **Cancelar a propósito** (Atrás, «Use password») **no** es un fallo: no sale diálogo.
- ⚠️ **Matiz que explicar:** un dedo que **no coincide** no cierra el `BiometricPrompt`; el propio diálogo del sistema dice «No reconocido» y deja reintentar. Así funciona Android. Nuestro diálogo sale cuando el intento **termina** en error; el más fácil de enseñar son los **5 fallos** («Too Many Attempts»). Hacerlo **al final** de la parte de biometría: luego la huella queda bloqueada 30 s.

### 2.5 Sin biometría: usuario y contraseña, y la opción oculta

> *If the device does not support biometric authentication, the user must be able to log in with their username and password. The biometric option should be hidden or disabled in this case.*

**✅ Cómo lo cumplimos.**
- La contraseña funciona **siempre**; la huella es un añadido **por cuenta**.
- Si el móvil no tiene sensor fuerte, no tiene huellas o el sensor no está disponible, el botón de huella **no aparece**, y una línea explica por qué (`BiometricText.availabilityNote`), por ejemplo «This phone has no strong biometric sensor: sign in with your password.»

🎯 En el **emulador** (sin huella) o con una cuenta sin huella: entrar con la contraseña.

### 2.6 El acceso, SIEMPRE

> *The Login View must ALWAYS be displayed when launching the app: first launch, brought back from the background, reopened after pressing Home, even if the user was previously authenticated.*

**✅ Cómo lo cumplimos.**
- **Al arrancar:** después del arranque (y del tutorial la primera vez), `Navigation.screenOf` pone **siempre** el acceso antes de cualquier pantalla con datos (decisión D43; hay pruebas de que ni la molécula ni la comparación aparecen bloqueadas).
- **Al pasar a segundo plano:** `ui/AuthViewModel.kt` escucha el ciclo de vida **del proceso entero** (`ProcessLifecycleOwner`). Su `ON_STOP` llega cuando **ninguna** pantalla de la app está visible: Home, otra app, pantalla apagada, incluso la hoja de compartir. Entonces llama a `controller.lock()`.
  ```kotlin
  private val lockOnBackground = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_STOP) controller.lock()
  }
  ```
- **Girar el móvil no bloquea:** `ProcessLifecycleOwner` espera un momento antes de dar el proceso por parado. El diálogo de la huella tampoco bloquea.
- **Tras volver a entrar**, la app sigue **donde estaba** (la molécula, la búsqueda): bloquear no es cerrar.
- **En Recientes** (Android 13+) la miniatura **no enseña** el contenido: `setRecentsScreenshotEnabled(false)` en `MainActivity.kt`. No usamos `FLAG_SECURE`, porque dejaría en negro nuestras propias capturas para compartir.

🎯 Abrir → acceso → lista → **Home** → abrir → **acceso** otra vez.

### 2.7 Contraseñas nunca en claro

> *Passwords must never be stored in plain text. Use appropriate hashing algorithms (bcrypt, Argon2, or platform-specific secure storage).*

**✅ Cómo lo cumplimos.**
1. **Argon2id**, ganador de la *Password Hashing Competition* y el primero que recomienda OWASP. Parámetros de OWASP:
   - **19 MiB** de memoria, **2** pasadas, paralelismo 1;
   - **sal aleatoria de 16 bytes por cuenta**, hash de 32 bytes.

   Se guardan el hash, la sal y los parámetros; **nunca** la contraseña. La implementación se prueba con el **vector oficial del RFC 9106**.
2. **El fichero de cuentas, además, está cifrado** con **AES-256-GCM** y una llave que vive en el **Keystore** (no se puede sacar del móvil). Está en `noBackupFilesDir`, fuera de las copias en la nube.
3. **Detalles:**
   - la comparación del hash es de **tiempo constante** (`MessageDigest.isEqual`);
   - la contraseña se borra del estado nada más usarla;
   - tras **5 fallos**, un freno de 30 s que se **duplica** hasta 5 min (`LoginThrottle`);
   - el mensaje es **el mismo** para «no existe» y «contraseña mala», así no se revela qué cuentas hay.

### 2.8 Interfaz clara

> *The login interface should be user-friendly with clear labels, appropriate keyboard types, and helpful error messages.*

**✅ Cómo lo cumplimos** (`ui/AuthScreens.kt`):
- **Etiquetas** en cada campo: «User name», «Password», «Repeat the password».
- **Teclados:**
  - el usuario, con `KeyboardType.Ascii`;
  - las contraseñas, ocultas (`PasswordVisualTransformation`, con un botón para verlas);
  - **«Siguiente» / «Hecho»** en el teclado (`ImeAction.Next`, `ImeAction.Done`): de un campo al siguiente y enviar sin buscar el botón.
- **Autorrelleno:** `ContentType.Username`, `Password` y `NewPassword`, para que el gestor de contraseñas del móvil los reconozca.
- **Errores útiles:** en el propio campo (un nombre con caracteres no válidos, contraseñas que no coinciden), bajo el formulario («This user name is already taken.»), la lista de requisitos en vivo y, si hay freno, cuánto falta.
- Ilustraciones (el servidor, el tecnosacerdote) detrás del formulario, sin restar contraste.

---

## 3. Lista de ligandos (VI.3)

### 3.1 Todos los ligandos de `ligands.txt`

> *You must display all ligands listed in the ligands.txt file. This file contains a list of ligand identifiers, one per line.*

**✅** `ligands.txt` va dentro de la app (`assets/ligands.txt`, **1243** ids) y se lee una vez al arrancar (`catalog/LigandCatalog.kt`). La lectura quita espacios, líneas vacías y repetidos. El contador dice «PATTERNS FOUND: 1,243», y el arranque enseña el mismo número.

### 3.2 Lista desplazable

> *The list must be displayed in a scrollable view (UITableView on iOS, RecyclerView on Android, or equivalent).*

**✅** Un **`LazyColumn`** (`ui/LigandListScreen.kt`), el equivalente en Compose del `RecyclerView`:
- solo **crea las filas que se ven** (unas diez), no las 1243;
- cada fila tiene una **clave** (`key = { it }`, su id) para reutilizarse bien cuando la lista cambia;
- en **horizontal**, la lista ocupa su propia columna a la derecha, a toda la altura (documento 21, §19).

### 3.3 Búsqueda en tiempo real

> *A search bar that filters in real-time: as the user types, case-insensitive, through ligand identifiers, updating immediately.*

**✅** `LigandCatalog.search` (puro, probado):
- **cada tecla** vuelve a filtrar, sin botón de buscar;
- **sin distinguir mayúsculas** (`ignoreCase = true`, que no depende del idioma del móvil: en turco «i».uppercase() daría «İ»);
- busca **en el identificador**: primero los que **empiezan** por el texto, luego los que lo **contienen**;
- al cambiar la búsqueda, la lista **vuelve arriba**.

El cuadro es un *prompt* de consola: «> _ Search by ID», con el «_» parpadeando (documento 23). Al lado, una estrella filtra **solo los favoritos** (bonus).

### 3.4 Al elegir un ligando: indicador, descarga, *parsing*, navegación

> *Display a loading indicator · Fetch the .cif file from https://files.rcsb.org/ligands/view/{ligand}.cif · Parse it to extract atomic coordinates and bond information · Navigate to the Protein View.*

**✅**
- **Indicador:** un **diálogo modal** (`ui/LoadDialogs.kt`) con:
  - el cráneo servidor que flota;
  - «Downloading ligand ATP»;
  - las **etapas** Archive → Connect → Download → Parse con su marca `[OK]`, una **barra de progreso** con el porcentaje y los KB («62 % · 4.6 / 7.4 KB»);
  - el botón **Cancel**.

  Suena el módem (documento 14). Es modal: mientras carga, ningún toque llega a la lista.
- **Descarga:** `net/LigandRepository.kt` usa **exactamente** la URL del enunciado (`DEFAULT_BASE_URL = "https://files.rcsb.org/ligands/view/"` + id + `.cif`), con `HttpUrlConnectionFetcher` y tres relojes de seguridad: conexión 10 s, silencio 10 s y total 30 s. Todo en segundo plano (`Dispatchers.IO`).
- ***Parsing*:** un **parser CIF propio** (`cif/CifTokenizer.kt` → `CifTableReader.kt` → `CifExtractor.kt`):
  - lee las tablas `_chem_comp_atom` (átomos, elemento, carga, coordenadas) y `_chem_comp_bond` (enlaces y su orden);
  - toma las coordenadas **ideales** del diccionario y, si faltan, las del modelo: el primer juego que esté **completo**;
  - **centra** la molécula en el origen.

  Se probó con los **1242** ligandos del catálogo que tienen átomos.
- **Navegación:** al terminar, `Navigation.screenOf` pasa a la molécula, con la transición de tubo CRT.

### 3.5 Si no se puede cargar: un aviso claro

> *A warning popup with a clear error message: "No internet connection. Please check your network." · "Ligand not found (404). This ligand may not exist in the database." · "Failed to parse ligand data. The file may be corrupted." · "Request timeout. Please try again."*

**✅** `ui/LoadErrorText.kt` usa **las palabras exactas** del enunciado (lo fija la prueba `theFourCasesOfTheSubjectUseItsWordsExactly`). Cada error tiene su **diálogo** con un dibujo propio, un título del mundo de la app, el id del ligando, el motivo y **Retry** cuando repetir puede dar otro resultado:

| Error | Título | Texto |
|-------|--------|-------|
| Sin red | *The Noosphere is Silent* | **No internet connection. Please check your network.** |
| 404 | *The Archive is Empty* | **Ligand not found (404). This ligand may not exist in the database.** |
| Fichero corrupto | *The Data-Slate is Damaged* | **Failed to parse ligand data. The file may be corrupted.** |
| Tiempo agotado | *Time Has Forsaken Us* | **Request timeout. Please try again.** |

**Y seis más** que el enunciado no pide: error del servidor (5xx), descarga cortada, fichero demasiado grande (> 2 MB), id no válido, otro error de red e inesperado. Todo lo que llega de la red **se valida**. Una página HTML de un wifi de hotel que responde «200 OK» se toma por fichero **corrupto**, no por un ligando, y sale el diálogo con Retry. `LoadError` es una **interfaz sellada**: un `when` que olvide un caso **no compila**.

🎯 **Modo avión** + un ligando **nunca abierto** → «No internet connection…» con Retry. ⚠️ Uno **ya abierto** se abre sin red, desde la caché (bonus VII.4): enseñarlo también.

### 3.6 El indicador desaparece siempre

> *The loading indicator must be visible during the entire loading process and must disappear once loading is complete (success or failure).*

**✅** Hay **un único** estado de carga (`loading/LoadState.kt`: `Idle`, `Loading`, `Loaded`, `Failed`): es **imposible** tener a la vez el indicador y un error. `LigandLoader` **garantiza**, con pruebas, que tras `Loading` el estado **siempre** pasa a otro, pase lo que pase: éxito, cualquier error, una excepción inesperada o una cancelación. Un progreso que llega **tarde** de una carga cancelada no puede devolver el indicador.

### 3.7 Listas grandes sin tirones

> *The list should handle large datasets efficiently without lag or stuttering.*

**✅** `LazyColumn` compone solo lo visible. La búsqueda se recalcula **solo** cuando cambia el texto (`remember(catalog, query, …)`). La información de cada celda (fórmula, átomos) viene de un **índice precalculado** (`assets/ligand_index.tsv`, 86 KB): **no se descarga nada** para pintar la lista.

---

## 4. Vista 3D de la molécula (VI.4)

### 4.1 Motor 3D permitido

> *Android: ViroCore, filament, or OpenGL ES. Full game engines (Unity, Unreal) are NOT allowed.*

**✅** **SceneView 4.69.4** (una biblioteca para Jetpack Compose) sobre **Filament**, el motor de render físico de Google, **nombrado** en el enunciado. No hay motor de juego: es una vista más dentro de una app Compose normal (`ui/MoleculeScene.kt`). Cada átomo es un `SphereNode` y cada enlace un `CylinderNode`.

### 4.2 Colores CPK

> *Carbon: Black or Gray · Hydrogen: White · Oxygen: Red · Nitrogen: Blue · Sulfur: Yellow · Phosphorus: Orange · Other elements: standard CPK colors.*

**✅** `scene/ElementStyle.kt`, la tabla **Jmol** (la más usada hoy; comprobada el 21-11-2026 contra `elementColors.Jmol` de 3Dmol.js):

| Elemento | Color | RGB |
|----------|-------|-----|
| C | gris | `#909090` |
| H | blanco | `#FFFFFF` |
| O | rojo | `#FF0D0D` |
| N | azul | `#3050F8` |
| S | amarillo | `#FFFF30` |
| P | naranja | `#FF8000` |
| Cl / F / Br | verde / verde claro / rojo oscuro | `#1FF01F` / `#90E050` / `#A62929` |

- Los **50 elementos** del catálogo tienen color (lo comprueba una prueba). Un elemento desconocido sale en **rosa intenso**, para que se note.
- En **Ajustes** se puede elegir también la tabla **RasMol** (bonus VII.2).
- Abajo, la **leyenda** con el color **y la letra** de cada elemento dibujado: el color nunca va solo.
- ⚠️ La selección **no** pinta el átomo de amarillo, porque el amarillo es el azufre: se dibuja un **halo** translúcido alrededor.

### 4.3 Bolas y varillas

> *Atoms as spheres · Bonds as cylinders connecting atoms · Sphere sizes proportional to atomic radii (or uniform) · Bond cylinders thinner than atom spheres.*

**✅** `scene/Representation.kt` y `ElementStyle.radiusOf`:
- **Radios por tamaño de átomo**: H 0,25 Å; C, N, O, F 0,40 Å; P, S, Cl, Br, I… 0,50 Å; metales 0,55 Å.
- **Varillas de 0,12 Å**: más finas que la bola más pequeña (0,25), como pide el enunciado.
- **Cada cilindro** va de un átomo al otro: un nodo en el punto medio, girado y después inclinado, con la trigonometría de `scene/BondGeometry.kt`, probada con matrices.

### 4.4 Información del átomo al tocarlo

> *Show a tooltip, popup, or overlay · At minimum the element symbol · Optionally atom name, residue, coordinates · It should disappear when the user taps anywhere else on the screen or taps another atom.*

**✅**
- **Al tocar un átomo:** un **halo** verde lo rodea y el panel **AUSPEX READOUT** dice «**C01 · Carbon (C)**», es decir, nombre, elemento y símbolo, con sus coordenadas **X, Y, Z**.
- **Desaparece** al tocar el vacío, al tocar **otro átomo** (pasa a ese) o al tocar **cualquier panel** de la pantalla (`deselect` en `ui/MoleculeScreen.kt`). Es literalmente «anywhere else».
- La zona que responde al toque nunca es menor de **0,35 Å**: un dedo es más ancho que un hidrógeno.
- **Además** (bonus VII.3): tocar una **varilla** enseña el enlace (sus dos átomos, el orden y la longitud en Å).

🎯 Tocar un átomo → ficha → tocar el vacío → desaparece; tocar un átomo y luego otro.

### 4.5 Gestos

> *Rotation: drag to rotate around its center · Zoom: pinch · Pan: two-finger drag (optional but recommended) · Smooth and responsive (60 FPS target).*

**✅** El manipulador de cámara de SceneView (`rememberCameraManipulator`), con nuestros límites (`scene/SceneFraming.kt`):
- **Girar** con un dedo, alrededor del **centro** de la molécula (el parser ya la centra).
- **Zoom** con el pellizco, con límites en **ångström**: nunca más cerca de 4 Å, para no meterse dentro de un átomo.
- **Desplazar** con dos dedos 🟢: está activo por defecto en el manipulador de SceneView (`isPanEnabled = true`, leído en su código fuente).
- El doble toque **no** hace zoom: lo usamos para **orbitar un átomo** (bonus VII.3).
- Con inercia, a la frecuencia de la pantalla (90 Hz en el A33).

### 4.6 Compartir

> *A "Share" button: capture a screenshot of the current 3D view · share via the native share sheet · to social media, messaging apps, email, or save to photos.*

**✅ / 🟢** La tecla **SHR** abre el menú «PICT-CAPTURE // EXPORT» (`share/`):
- **Share image** (PNG o JPEG):
  - captura la escena 3D con **`PixelCopy`**: la escena es una superficie de la GPU, y una captura normal de la vista saldría **negra**;
  - añade una banda «Swifty Proteins · ATP · C10H16N5O13P3 · 47 atoms · 49 bonds»;
  - abre la **hoja de compartir nativa** de Android, con un **`FileProvider`**: un enlace `content://` con permiso de lectura temporal, nunca una ruta del disco;
  - el mensaje lleva el nombre, la fórmula y el tamaño (bonus VII.5).

  Desde ahí, WhatsApp, Telegram, Gmail, Drive…
- **Save to gallery** (el «save to photos» del enunciado), al álbum **«Swifty Proteins»**:
  - en Android 10+, con **`MediaStore`**: sin permisos, con la entrada **pendiente** (`IS_PENDING`) hasta que está entera;
  - en Android 7-9, pide antes el permiso de almacenamiento.

  **Por qué la app lo hace sola:** que la hoja de compartir ofrezca «guardar imagen» depende de las apps que tenga cada móvil.
- Ficheros `.cif`, `.mol`, `.glb` y vídeo: bonus VII.5.

🎯 SHR → Save to gallery → abrir la Galería: álbum Swifty Proteins. SHR → Share image (PNG) → la hoja de compartir.

### 4.7 Cámara y luces

> *Initial camera position should show the entire molecule · Lighting should highlight the 3D structure (avoid flat appearance) · Consider multiple light sources.*

**✅**
- **Cámara** (`scene/SceneFraming.kt`), con la distancia **calculada**:
  - el **radio** de la molécula (el átomo más lejano del centro, más su bola) y el **campo de visión** (`TAN_HALF_FOV = 12/28`), con un 6 % de margen;
  - la **forma de la ventana** visible: en un móvil vertical el ancho es lo que limita (documento 21, §18).
- **Centrado en la ventana:** la molécula se centra en la **ventana entre los paneles**, no en la pantalla entera (`contentPadding` de SceneView), y los toques siguen la misma proyección.
- **Luces:** **iluminación de tres puntos**, como en fotografía, más la luz ambiental (IBL) de SceneView:

  | Luz | Dirección | Intensidad (tema oscuro) | Papel |
  |-----|-----------|--------------------------|-------|
  | **Principal** (*key*) | arriba-delante-izquierda | 10 000 lux | Da a cada bola su sombreado |
  | **Relleno** (*fill*) | delante-derecha, algo abajo | 3 000 lux | Que ningún lado sea un disco negro |
  | **Contraluz** (*rim*) | detrás y arriba | 5 000 lux | Un borde brillante que separa las bolas del fondo |

  Las luces **no se mueven** al girar la cámara, como en una habitación real. El tema claro tiene sus propios valores (`scene/SceneLook.kt`).

### 4.8 Fluido y rápido

> *The 3D rendering must be smooth and performant. No lag, stuttering, or frame drops during interaction.*

**✅** Comprobado en el A33:
- Un **contador de FPS** siempre visible: dice «60/90 FPS» al moverse e **IDLE** en reposo, porque Filament **solo dibuja cuando algo cambia**.
- Se persiguió en el móvil hasta que el reposo fue IDLE de verdad (documento 21, §18-20). Eran recomposiciones que pedían fotogramas sin que nada se moviera; se encontraron con un diagnóstico en Logcat (`SwiftyFrames`).
- Para moléculas grandes, **nivel de detalle** y montaje **por lotes** (bonus VII.4).

---

## 5. Instrucciones generales

Las que el enunciado pone fuera de las secciones y también se miran:

| Exigencia | Cómo | Estado |
|-----------|------|--------|
| **Versiones recientes** | `compileSdk`/`targetSdk` 37, Kotlin 2.4.21, AGP 9.4.1, Compose BOM 2026.09, `minSdk` 24 | ✅ (revisar antes de la defensa) |
| **Formato `.cif`** de RCSB, no `.pdb` | Parser CIF propio, 1242/1242 | ✅ |
| **Maquetación moderna y adaptable** (el enunciado nombra `ConstraintLayout`) | **Jetpack Compose**: `Row`/`Column`/`Box`/`weight`. Vertical y horizontal con su propia distribución en todas las pantallas; la orientación no está bloqueada; densidades vía `dp` | ✅ (tableta: 🟢) |
| **Errores completos**: red, *parsing*, memoria, entrada inválida | 10 casos de `LoadError` (incluido demasiado grande e id no válido); validación de formularios | ✅ |
| **Red en segundo plano**, interfaz siempre fluida | Corrutinas en `Dispatchers.IO`; Argon2 también fuera del hilo principal | ✅ |
| **Información al usuario** en cada operación | Diálogos de carga y error; tras compartir, «Pict-capture ready», «Video ready», «Saved to Pictures/Swifty Proteins»; el progreso de la carga y del montaje de la escena | ✅ |
| **Sin fugas de memoria** | El ViewModel guarda el contexto de la **aplicación**, no el de la actividad; las escenas liberan sus nodos al salir de la composición | ⚠️ sin herramienta que lo demuestre (LeakCanary) |
| **Probado en un dispositivo real** | Samsung Galaxy A33 (90 Hz, huella) | ✅ |
| **Código ordenado**, guías de la plataforma | Reglas puras separadas de Android (probadas en la JVM), Material 3, convenciones de Kotlin | ✅ |
| **Seguridad**: nada en claro, Keystore, validar la red, fallos de biometría | Apartado 2 y 3.5 | ✅ |
| **Accesibilidad** donde sea posible | Contraste medido (WCAG); descripciones para TalkBack en cada tecla y celda; estado dicho en palabras, nunca solo con color; teclas de ≥ 48 dp; respeta «quitar animaciones» | ✅ / 🟢 (pasada completa con TalkBack) |
| **Sin conexión para los ligandos ya cargados** (caché opcional) | Bonus VII.4 | ✅ |
| **Pruebas** | 618 pruebas JVM en 61 clases + mutación | ✅ |

---

# Parte de bonus

## 6. VII.1 Modelos de visualización

> *Space-filling (van der Waals radii), wireframe, stick, with real-time switching without reloading.*

**✅** **Cuatro** modelos en el desplegable **MODEL…** (`scene/Representation.kt`, `scene/VanDerWaals.kt`; documento 15):

| Modelo | Cómo se dibuja | Base |
|--------|----------------|------|
| **Ball & stick** | El obligatorio | — |
| **Space-filling** | Esferas del **radio de van der Waals**, sin varillas: los vecinos **se solapan** (ese solape es el enlace) | Radios de **Mantina et al. 2009** (extensión de Bondi 1964); H = 1,10 Å |
| **Sticks** | Varillas gruesas, **cada mitad del color de su átomo** | Como PyMOL y Jmol |
| **Wireframe** | Líneas finas, también en dos colores | Ídem |

- **Sin recargar:** cambiar de modelo vuelve a dibujar la **misma** molécula que ya está en memoria; no se descarga ni se interpreta nada (no sale el diálogo de carga).
- En espacio lleno, la zona táctil es la esfera grande: tocar su **borde** la selecciona.
- El modelo con el que se abre cada molécula se elige en **Ajustes**.

🎯 MODEL… → los cuatro en ATP.

## 7. VII.2 Interfaz avanzada

> *Custom cells, animations, dark mode (also in 3D), onboarding, settings screen.*

| Exigencia | Lo que hay | Dónde | Estado |
|-----------|-----------|-------|--------|
| **Celdas propias** | Línea de consola: «> ATP», fórmula con subíndices reales, nº de átomos, **ARCHIVED** si está en el móvil, **barra de composición** (la proporción de cada elemento en su color CPK), estrella de favorito. TalkBack lee la celda entera en una frase | `ui/LigandListScreen.kt` (`PatternRow`), `assets/ligand_index.tsv` (generado con el **parser de la propia app**) | ✅ |
| **Animaciones** | Ver la lista debajo de la tabla | documentos 21, 23 | ✅ / 🟢 |
| **Modo oscuro, también en 3D** | Tema **oscuro por defecto** (consola de fósforo verde) y **claro «pergamino»**, o «como el móvil». Incluye el **fondo de la escena 3D**, los halos y las luces, y las barras del sistema | `ui/theme/PaletteSpec.kt`, `scene/SceneLook.kt` | ✅ (3D claro: 🟢) |
| ***Onboarding*** | Tutorial de **4 páginas** (el índice, la vista 3D, las teclas, compartir), con ilustraciones, NEXT/BACK/SKIP o deslizando; se puede repetir desde Ajustes | `ui/Onboarding.kt`, `ui/OnboardingScreen.kt` | 🟢 |
| **Ajustes** | Modelo y H al abrir, **tema**, **colores de átomos** (Jmol o RasMol), sonidos, repetir el tutorial | `ui/SettingsScreen.kt`, `prefs/` | ✅ |

**Las animaciones:**
- el **cambio de pantalla como un tubo CRT** que se apaga en una línea y un punto y se enciende (`ui/CrtSwitch.kt`);
- el **destello** que recorre «Swifty Proteins» cada 6 s;
- el arranque que se escribe;
- el cráneo que flota en la carga;
- el **cursor de consola**;
- la molécula que se **desliza** al nuevo centro cuando cambia un panel;
- las páginas del tutorial que se deslizan.

**Además** (no se pide): un **diseño propio** completo, el «Culto de la Máquina», con paneles de cristal y bronce dibujados en código, tipografía gótica y de consola, y arte pintado (documentos 08a-08d). También **diez sonidos sintetizados** propios (documento 14).

⚠️ **Si preguntan por los «dos temas»:** el enunciado pide «modo oscuro». La app **es** oscura por defecto y, además, se puede cambiar (documento 21, §17.1).

## 8. VII.3 Interacción molecular

> *Highlight atoms of the same element, bond information (type and length), measure distances and angles, labels, center with double tap.*

| Exigencia | Lo que hay | Estado |
|-----------|-----------|--------|
| **Resaltar el mismo elemento** | Seleccionar un átomo pone un halo dorado en **todos** los de su elemento; o se elige el elemento en la leyenda | ✅ |
| **Información de enlaces** | Tocar una varilla: «C1 – O2 · Double bond» (sencillo, doble, triple, aromático), los elementos que une y la **longitud** en Å | ✅ |
| **Medir distancias y ángulos** | **RULER**: 2 átomos → distancia; 3 → ángulo; 4 → **torsión** (ángulo diedro), con líneas entre ellos | 🟢 |
| **Etiquetas** | **LABELS**: el símbolo de cada átomo, en 3D, siempre de cara a la cámara y en la cara visible de la bola (una imagen **por elemento**, no por átomo: 256 etiquetas serían 64 MB) | 🟢 |
| **Centrar con doble toque** | Doble toque en un átomo: la cámara **orbita alrededor de él**, deslizándose; doble toque en el vacío: vuelve a la molécula entera | ✅ |
| Además | Ocultar/mostrar los **hidrógenos** (H) | ✅ |

Dónde: `ui/MoleculeScreen.kt`, `scene/Measurement.kt`, `ui/MeasureText.kt`, `ui/BondText.kt`, `scene/AtomLabels.kt`; documentos 15, 16, 19.

## 9. VII.4 Rendimiento y caché

> *Local cache of .cif files, lazy loading, background parsing with progress, memory management, guaranteed 60 FPS.*

| Exigencia | Lo que hay | Estado |
|-----------|-----------|--------|
| **Caché local** | Cada `.cif` descargado se guarda en la caché de la app (`net/LigandCache.kt`): **30 días** sin preguntar al servidor, como mucho **20 MB**, borrando lo más viejo. **Sin conexión**, abre lo que ya se vio («AUSPEX // OFFLINE ARCHIVE») | ✅ |
| **Carga perezosa** | `LazyColumn`; la escena se **monta por lotes**: átomos primero, luego enlaces, un lote por fotograma, con un tamaño de lote que se adapta a lo que tardan los fotogramas, y una barra **ASSEMBLY 312 / 511** | ✅ |
| ***Parsing* en segundo plano con progreso** | `Dispatchers.IO`; las **etapas** Archive / Connect / Download / Parse con porcentaje y KB; Cancel en cualquier momento | ✅ |
| **Memoria** | **Nivel de detalle** según el tamaño: menos triángulos por bola cuanto más grande es la molécula, con un presupuesto de 204 800 triángulos (`scene/MeshDetail.kt`); **un material por elemento**, no por átomo; una imagen de etiqueta por elemento; tope de 2 MB por fichero descargado | ✅ |
| **60 FPS** | Contador de FPS siempre visible; **IDLE en reposo** comprobado en el A33 (documento 21, §18-20) | ✅ |

Documentos 12, 13, 20.

## 10. VII.5 Compartir y exportar ampliado

> *Custom share message (name, number of atoms, formula), multiple formats, video of the rotation, favourites, comparison view of two molecules.*

| Exigencia | Lo que hay | Estado |
|-----------|-----------|--------|
| **Mensaje personalizado** | «Ligand ATP · ADENOSINE-5'-TRIPHOSPHATE / C10H16N5O13P3 · 47 atoms · 49 bonds / Viewed in Swifty Proteins». La **fórmula** se cuenta de los átomos (Hill) y se comprobó contra la de RCSB en los **1242** ligandos: 1242 iguales | ✅ |
| **Varios formatos** | Ocho entradas en SHR: PNG, JPEG, galería, **`.cif`** original, **`.mol`** (MDL V2000, validado con RDKit en los 1242), **modelo 3D `.glb`** (glTF 2.0; validador de Khronos: 0 errores), vídeo y vídeo a la galería | 🟢 |
| **Vídeo de la rotación** | **Una vuelta completa en 6 s a 30 fps**, MP4, con el `SceneRecorder` de SceneView (sin grabar la pantalla: ni diálogo de consentimiento, ni paneles en el vídeo). Gira la **molécula**, no la cámara; el rótulo va dentro del fotograma; se comparte o va a Movies/Swifty Proteins; Cancel o Atrás lo anulan y borran | 🟢 |
| **Favoritos** | La estrella en la celda y en la molécula; el filtro «solo favoritos»; **por cuenta**, cada usuario los suyos | ✅ |
| **Comparar dos moléculas** | **CMP** → elegir otra → **CONCORDANCE**: dos escenas 3D lado a lado, cada una con su FPS; H y **SPIN** (las dos giran juntas, con el mismo ángulo en cada fotograma) para las dos. Debajo, una tabla: fórmula, **masa** (comprobada contra el CCD en los 1241, ≤ 0,06 g/mol), átomos, pesados, enlaces, **anillos**, aromáticos, carga, tamaño en Å, cada elemento (un toque lo ilumina en las dos) y el parecido de composición | 🟢 |

Dónde: `share/`, `export/`, `ui/CompareScreen.kt`, `molecule/Comparison.kt`, `molecule/AtomicMass.kt`; documentos 15, 16, 18, 22, 24.

---

## 11. Lo que falta comprobar en el móvil

Todo compila y está probado en la JVM, pero **antes de la defensa** hay que verlo en el A33:

| # | Qué | Apartado | Riesgo si falla |
|---|-----|----------|-----------------|
| 1 | La **cortina del tubo** tapa también la escena 3D | 1.2, VII.2 | Medio: la molécula se vería por encima de la cortina |
| 2 | **Guardar en la galería** (imagen) y compartir PNG | 4.6 | **Alto: es obligatorio** |
| 3 | Desplazar con **dos dedos** | 4.5 | Bajo (recomendado, no obligatorio) |
| 4 | Formatos `.cif` / `.mol` / `.glb` en otras apps | VII.5 | Bajo |
| 5 | **Vídeo**: compartir y galería; Cancel | VII.5 | Medio |
| 6 | **Comparación**: dos escenas, SPIN, la tabla | VII.5 | Medio: dos motores Filament a la vez |
| 7 | Tema claro en el 3D | VII.2 | Bajo |
| 8 | Etiquetas y regla | VII.3 | Bajo |
| 9 | Tutorial, destello y cursor | VII.2 | Bajo |
| 10 | Una pasada con **TalkBack** | 5 | Bajo |
| 11 | Compilar en el **ordenador de la defensa** y tener un **emulador** listo | — | **Alto: si no compila, la evaluación se para** |

## 12. De este documento a la presentación

Una propuesta de guion, en el orden de este documento. Cada apartado lleva su «qué enseñar» en el móvil:

1. **Portada y la idea:** un visor 3D de ligandos con el aspecto de un cogitador del *Mechanicus*.
2. **Arquitectura en una diapositiva:** reglas puras (probadas en la JVM) | Android (Compose, Keystore, red) | 3D (SceneView/Filament). 618 pruebas y pruebas de mutación.
3. **VI.1** Icono y arranque (las dos capas: la *splash* invisible y la nuestra).
4. **VI.2** Seguridad: Argon2id + Keystore + biometría con `CryptoObject` + bloqueo en segundo plano. Es el apartado con más preguntas: **dos diapositivas**.
5. **VI.3** Lista, búsqueda y carga: el estado único, los cuatro errores con sus palabras.
6. **VI.4** 3D: CPK, bolas y varillas, la cámara calculada, las tres luces, el FPS en IDLE.
7. **Bonus**, uno por diapositiva, cada uno con una demostración corta: modelos → interfaz y temas → interacción (regla, etiquetas) → rendimiento (modo avión, ASSEMBLY) → compartir, vídeo y comparación.
8. **Lo que aprendimos:** el FPS que no bajaba a IDLE, la molécula descentrada y por qué el SurfaceView no se transforma.
9. **Preguntas.**
