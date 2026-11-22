# 25 · Verificación punto por punto contra el enunciado

> **Para qué sirve.** Recorre el enunciado **frase a frase**: primero la parte obligatoria (VI.1–VI.4), luego las instrucciones generales y por último los bonus (VII.1–VII.5). Para cada exigencia dice **si está hecha**, **cómo**, **en qué ficheros**, **por qué así** y **qué enseñar** en la defensa. Es la base de la presentación: cada apartado puede convertirse en una o dos diapositivas.
>
> **Estado de la app a 22-11-2026:** 631 pruebas automáticas en 63 clases, todas en verde; pruebas de mutación en cada regla nueva. Las rutas de código son relativas a `app/src/main/java/dev/knowledge82/swiftyproteins/`; las de recursos, a `app/src/main/`.
>
> **Términos:** los conceptos técnicos (Argon2id, `CryptoObject`, IDLE, LOD, `SrcAtop`, número ciclomático…) y las palabras del *lore* de Warhammer 40k que usa la interfaz (Auspex, Noosphere, Pict-capture…) se explican en el [**glosario**](00-glosario.md), ordenado por temas; el *lore*, en su sección 22.
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
| VII.1 Modelos | Espacio lleno, alambre, varillas; un control para cambiar en tiempo real sin recargar | ✅ | 15, 19 |
| VII.2 Interfaz | Celdas propias, animaciones (transiciones, cargas, microinteracciones), modo oscuro también en 3D con sus luces, tutorial, ajustes (modelo, colores) | ✅ / 🟢 (transición CRT, destello) | 08c, 14, 16, 21, 23 |
| VII.3 Interacción | Resaltar el mismo elemento, tipo y longitud de enlaces, distancias y ángulos, etiquetas conmutables, centrar con doble toque animado | ✅ / 🟢 (etiquetas, regla) | 15, 16, 19 |
| VII.4 Rendimiento | Caché sin conexión, carga perezosa, *parsing* en segundo plano con progreso, memoria con LOD, 60 FPS con moléculas grandes | ✅ | 12, 13, 20, 21 §18-20 |
| VII.5 Compartir | Mensaje con nombre/átomos/fórmula, PNG/JPEG/modelo 3D, vídeo girando, favoritos con lista, dos moléculas lado a lado | ✅ / 🟢 (formatos, vídeo, comparación) | 15, 16, 18, 22, 24 |

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
   - Al abrir la app en frío, el tubo **se enciende** sobre ella: un punto que se estira en una línea y la línea que se abre. El reloj del arranque **espera** a que el tubo esté encendido, así que no se pierde ninguna línea ni su sonido (documentos 23 y 26).

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
- **Indicador:** un **diálogo modal** (`ui/LoadDialogs.kt`), visible **al menos 2 s** en una descarga para que no parpadee (documento 26), con:
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

**✅ / 🟢** La tecla **SHARE**, arriba a la derecha de la vista 3D, abre el menú «PICT-CAPTURE // EXPORT» (`share/`). Se llamaba «SHR», como en el diseño; se escribió entera porque «SHR» no se entendía. Para que cupiera, la tecla **H** bajó al panel de teclas, junto a MODEL…, RULER y LABELS (documento 23, §13).
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

🎯 SHARE → Save to gallery → abrir la Galería: álbum Swifty Proteins. SHARE → Share image (PNG) → la hoja de compartir.

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

> El enunciado (capítulo VII) tiene cinco bloques. Cada exigencia se cita **literalmente**, en inglés, y debajo va cómo la cumplimos.

## 6. VII.1 Modelos de visualización

> *Multiple Visualization Models. Implement additional molecular representation models beyond the mandatory Ball-and-Stick model. Users should be able to switch between different visualization modes.*

**✅** Cuatro modelos en total (los tres del enunciado más el obligatorio), todos en `scene/Representation.kt` (puro, probado), con los radios de van der Waals en `scene/VanDerWaals.kt`. Documentos 15 y 19.

### 6.1 Espacio lleno

> *Space-Filling Model (CPK Model): Atoms are represented as overlapping spheres with sizes proportional to their van der Waals radii. This shows the molecule's actual volume and surface.*

**✅** **Space-filling**: cada átomo es una esfera de su **radio de van der Waals** y no se dibujan varillas. Los vecinos **se solapan** más de un radio entero, porque un enlace C–C mide ~1,54 Å y el radio de van der Waals del C es 1,70 Å: ese solape **es** el enlace (lo fija la prueba `inSpaceFillingNeighboursOverlapThatIsTheBond`).
- **Radios:** **Mantina et al. 2009**, la extensión a todo el sistema periódico de los radios de **Bondi 1964**; H = 1,10 Å.
- **Toque:** la zona táctil es la esfera grande, así que tocar su **borde** la selecciona.
- **Detalle de malla:** más fino (`MeshDetail.bigBall`), porque en este modelo las esferas son lo único que se ve.

### 6.2 Alambre

> *Wireframe Model: Display only the bonds as lines, without atom spheres. This provides a clearer view of the molecular structure for complex molecules.*

**✅** **Wireframe**: solo los enlaces, como **líneas muy finas** (cilindros de 0,05 Å), y **cada mitad del color de su átomo**: el color de la línea dice qué une.
- ⚠️ **Matiz que explicar:** en cada átomo hay una esfera del **mismo grosor que la línea** (0,05 Å). A la vista no es una esfera: es la **unión** que redondea el codo entre dos líneas, y la zona que responde al toque, para poder tocar el átomo (que nunca baja de 0,35 Å). La única esfera visible es la de un átomo **sin ningún enlace dibujado** (un ion, o un átomo que solo tenía hidrógenos y se ocultaron): sin ella desaparecería de la pantalla.
- **Por qué cilindros y no líneas de la GPU:** Filament dibuja líneas de **1 píxel**, que no se pueden tocar ni engrosar, y los cilindros sí; además funcionan con la misma selección y el mismo halo que el resto.

### 6.3 Varillas

> *Stick Model: Similar to ball-and-stick but with very small or no spheres, emphasizing the bond structure.*

**✅** **Sticks**: varillas **gruesas** (0,2 Å), cada mitad del color de su átomo, como en PyMOL o Jmol. Las «esferas» tienen **el grosor de la varilla**: no se ven como bolas, solo cierran las uniones. Es lo que el enunciado llama «very small or no spheres».

### 6.4 Cambiar de modelo en tiempo real

> *Include a UI control (segmented control, dropdown, or buttons) to switch between models in real-time without reloading the molecule.*

**✅** Un **desplegable** (*dropdown*), la tecla **MODEL…** en el panel inferior, con el modelo actual marcado («> Ball & stick»).
- **Sin recargar:** el modelo es un **estado** de la pantalla; cambiarlo vuelve a dibujar la **misma** molécula que ya está en memoria. No se descarga ni se interpreta nada, y no sale el diálogo de carga.
- El panel dice el modelo actual («Elements / Jmol · Ball & stick»), y TalkBack lo lee.
- El modelo con el que se **abre** cada molécula se elige en Ajustes (VII.2).

🎯 MODEL… → los cuatro en ATP; luego en 15P.

## 7. VII.2 Interfaz avanzada

> *Advanced User Interface. Enhance the visual design and user experience of your application.*

**✅** Además de lo que pide cada punto, la app tiene un **diseño propio completo**, el «Culto de la Máquina» de Warhammer 40k: paneles de cristal y bronce dibujados en código, tipografía gótica y de consola, arte pintado y diez sonidos sintetizados (documentos 08a-08d y 14). Ver la nota sobre el **lore** en 7.6.

### 7.1 Celdas propias

> *Custom Table/List Cells: Design beautiful custom cells for the ligand list with icons, previews, or additional information.*

**✅** Cada celda de la lista (`PatternRow` en `ui/LigandListScreen.kt`):

```
> ATP                                               ☆
  C₁₀H₁₆N₅O₁₃P₃ · 47 ATOMS                  ARCHIVED
  ▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇
```

- **Información adicional:** la **fórmula** con subíndices reales y el **número de átomos**, **antes** de descargar nada. Vienen de un índice incluido en la app (`assets/ligand_index.tsv`), generado con **el parser de la propia app**: una celda nunca promete otra fórmula que la que se verá en 3D.
- **Vista previa:** la **barra de composición**, la proporción de cada elemento en su color CPK. De un vistazo se ve si es casi todo carbono e hidrógeno o un ion metálico.
- **Iconos:** la estrella de favorito (su propia tecla de 48 dp) y la marca **ARCHIVED** si ya está en el móvil (se abre sin conexión).
- TalkBack lee la celda entera en una frase, con la fórmula separada por elementos.

### 7.2 Animaciones

> *Smooth Animations: Add polished transitions between views, animated loading states, and micro-interactions.*

**✅ / 🟢**

| El enunciado dice | Lo que hay |
|-------------------|-----------|
| **Transitions between views** | Un televisor antiguo (`ui/CrtSwitch.kt`; documentos 23 y 26). Al **arrancar**, el tubo **se enciende**: un punto, una línea y la imagen. Entre pantallas, el **barrido del haz**: una línea de fósforo baja borrando la pantalla vieja a negro y vuelve a bajar dibujando la nueva, como un tubo que escribe su imagen (220 ms). Tras una **descarga**, el diálogo de carga ya es la transición y la molécula aparece directamente. Dentro del tutorial, las páginas **se deslizan** en la dirección del gesto |
| **Animated loading states** | El arranque que se escribe línea a línea (después de que el tubo se encienda); en la carga, el **servo-cráneo que flota** con el sonido del módem, **al menos 2 s**, las etapas que se marcan `[OK]` y la barra con porcentaje; un ligando ARCHIVED se abre sin diálogo, con un barrido; al abrir la molécula, la barra **ASSEMBLY** mientras la escena se monta por lotes |
| **Micro-interactions** | El **destello** que recorre «Swifty Proteins» cada 6 s; el **cursor de consola** que parpadea en el buscador; el **halo** al seleccionar; la molécula que **se desliza** al nuevo centro cuando cambia un panel; la cámara que **planea** al orbitar un átomo; un **clic de relé** en cada tecla |

- **Ninguna animación cuesta fotogramas en reposo:** cada una pide fotogramas solo mientras se mueve (documento 23, §6).
- Con **«quitar animaciones»** activado en el sistema, todas se paran o pasan a ser inmediatas.

### 7.3 Modo oscuro, también en 3D

> *Dark Mode: Implement a complete dark mode theme that works throughout the app, including the 3D view with appropriate lighting adjustments.*

**✅** **Oscuro por defecto** (la consola de fósforo verde), y además un tema **claro «pergamino»** o «como el móvil», en Ajustes (`ui/theme/PaletteSpec.kt`, `ui/theme/Color.kt`; documento 21):
- **En toda la app:** todos los colores salen de una paleta (`LocalSwiftyPalette`), y las barras del sistema siguen el tema.
- **En la vista 3D:** cada tema tiene su `SceneLook` (`scene/SceneLook.kt`) con el **fondo** de la escena, los **halos** y las **luces**. Son los «lighting adjustments» del enunciado:

  | Luz | Oscuro | Claro | Por qué |
  |-----|--------|-------|---------|
  | Principal | 10 000 lux | 10 000 lux | |
  | Relleno | 3 000 lux | 3 500 lux | Sobre un fondo claro, las sombras duras parecen agujeros |
  | Contraluz | 5 000 lux | 2 000 lux | Un borde brillante separa las bolas del negro; sobre pergamino solo deslumbra |

- **Contraste medido** (WCAG) en los dos temas: una prueba comprueba que cada color de texto sobre cada fondo llega al menos a **4,5:1**.
- ⚠️ Si preguntan «¿por qué dos temas?»: el enunciado pide modo oscuro; la app lo es, y además se puede cambiar (documento 21, §17.1).

### 7.4 Tutorial de bienvenida

> *Onboarding: Create an onboarding flow for first-time users explaining how to use the app and its features.*

**🟢** Cuatro páginas (`ui/Onboarding.kt`, `ui/OnboardingScreen.kt`), cada una con su ilustración:

1. **El índice:** buscar, cargar, la copia en el móvil, los favoritos.
2. **La vista 3D:** girar, zoom, tocar un átomo o un enlace, el doble toque.
3. **Las teclas:** MODEL…, H, LABELS, RULER.
4. **Compartir:** SHARE (imagen, vídeo, ficheros), CMP (comparar) y los Ajustes.

- Se pasa con **NEXT / BACK / SKIP** o deslizando, y dice «2 / 4».
- Sale **la primera vez**, después del arranque y antes del acceso (no enseña nada privado). Se puede **repetir** desde Ajustes.
- Una prueba comprueba que el tutorial **nombra cada tecla** de la vista 3D (MODEL, H, LABELS, RULER, SHARE, CMP).

### 7.5 Ajustes

> *Settings Screen: Add a settings view where users can customize preferences (default visualization model, color schemes, etc.).*

**✅** La rueda dentada de la lista abre los Ajustes (`ui/SettingsScreen.kt`, `prefs/`), con **exactamente** los dos ejemplos del enunciado y más:

| Grupo | Ajuste | Opciones |
|-------|--------|----------|
| AUSPEX DEFAULTS | **Modelo al abrir** («default visualization model») | Los cuatro de VII.1 |
| | Hidrógenos al abrir | Visibles / ocultos |
| PICT-DISPLAY | Tema | Oscuro / claro / como el móvil |
| | **Colores de los átomos** («color schemes») | **Jmol** o **RasMol** (las dos tablas CPK de referencia, comprobadas el 21-11-2026) |
| VOX-CASTER | Sonidos | Sí / no |
| NOVITIATE | Repetir el tutorial | |

Se guardan en el móvil y se aplican al abrir la siguiente molécula.

### 7.6 Una nota sobre el lore

La app habla **en el idioma del Mechanicus**: «Pict-capture ready», «AUSPEX READOUT», «The Noosphere is Silent», «Data-Vault Index». Es una decisión de diseño, no un descuido. Pero los evaluadores **pueden no conocer** Warhammer 40k, y esto es ante todo un proyecto de 42. La regla que sigue la app:

- **El lore va en lo decorativo:** títulos de paneles, pestañas, títulos de diálogos.
- **La información va en palabras claras**, y donde el enunciado da el texto, **con su texto exacto**: el diálogo de un error se titula «The Noosphere is Silent», pero su mensaje es «No internet connection. Please check your network.».

En la defensa basta una frase: *«el estilo es el de un ordenador del universo Warhammer 40k: el lenguaje del decorado viene de ahí; los mensajes que importan están en inglés llano»*.

## 8. VII.3 Interacción molecular

> *Enhanced Molecular Interactions. Add advanced interaction features for exploring molecular structures.*

Dónde: `ui/MoleculeScreen.kt`, `scene/Measurement.kt`, `ui/MeasureText.kt`, `ui/BondText.kt`, `scene/AtomLabels.kt`, `scene/SceneFraming.kt`; documentos 15, 16 y 19.

### 8.1 Resaltar los átomos del mismo elemento

> *Atom Highlighting: When hovering over or selecting an atom, highlight all atoms of the same element type.*

**✅** Al **seleccionar** un átomo (en un móvil no hay *hover*), cada átomo **de su elemento** recibe un **halo dorado**; el seleccionado, uno verde. Los átomos **conservan** su color CPK: el halo va alrededor. Además se puede elegir el elemento **en la leyenda** sin tocar ningún átomo; tocarlo otra vez lo apaga.

### 8.2 Información del enlace

> *Bond Information: Display bond types (single, double, triple) and bond lengths when tapping on bonds.*

**✅** Tocar una **varilla**: un tubo translúcido la rodea y el panel dice «C1 – O2 · **Double bond**» (también *Single*, *Triple*, *Aromatic*), los elementos que une y la **longitud** en Å, calculada de las coordenadas. El tipo viene de la columna `value_order` del `.cif`. La zona táctil de la varilla es la propia varilla, así que se distingue de los átomos.

### 8.3 Medir

> *Measurement Tools: Allow users to measure distances between atoms or angles between bonds.*

**🟢** La tecla **RULER** cambia los toques a «medir»:

| Átomos tocados | Medida |
|----------------|--------|
| 2 | **Distancia** (Å) |
| 3 | **Ángulo** en el átomo del medio (°): el ángulo entre dos enlaces cuando los tres están enlazados |
| 4 | **Torsión** (ángulo diedro, °), que el enunciado no pide |

Los átomos medidos tienen halo y van unidos por una línea; el panel RULER enseña los átomos y las medidas. Tocar el vacío empieza de nuevo. Las fórmulas (producto escalar, `atan2` para la torsión con su signo) son puras y están probadas.

### 8.4 Etiquetas

> *Atom Labels: Option to display element symbols directly on atoms in the 3D view (toggleable).*

**🟢** La tecla **LABELS** (encendida / apagada) escribe el **símbolo** de cada átomo **sobre él, en 3D**:
- de cara a la cámara siempre (*billboard*);
- colocada en la **cara visible** de la bola, para que la bola no la tape;
- cuando la cámara gira, solo se mueven las etiquetas que lo necesitan.
- **Memoria:** una imagen **por elemento** compartida por todos sus átomos. 256 etiquetas de texto serían 256 imágenes, unos 64 MB.

### 8.5 Centrar en un átomo

> *Center on Atom: Double-tap an atom to center the camera on it with a smooth animation.*

**✅** **Doble toque** en un átomo: la cámara **planea** (~0,6 s) hasta tenerlo en el centro y desde entonces **gira alrededor de él**; el átomo queda seleccionado. **Doble toque en el vacío:** vuelve, también planeando, a la molécula entera. El doble toque de SceneView para hacer zoom está desactivado a propósito, porque aquí el doble toque significa «centrar».

## 9. VII.4 Rendimiento y caché

> *Performance and Caching. Optimize the application for better performance and offline capability.*

Documentos 12, 13 y 20, y el documento 21, §18-20.

### 9.1 Caché local

> *Local Caching: Cache downloaded .cif files locally so previously viewed ligands can be accessed offline.*

**✅** Cada `.cif` descargado se guarda en la caché de la app (`net/LigandCache.kt`, `FileLigandCache`):
- **30 días** sin volver a preguntar al servidor (las definiciones del CCD casi nunca cambian);
- como mucho **20 MB**: al pasar, se borran los más viejos;
- se escribe en un fichero temporal y luego se **renombra**, así que un corte a medias nunca deja un fichero roto;
- **sin conexión**, abre cualquier ligando ya visto, y la pestaña lo dice: «AUSPEX // OFFLINE ARCHIVE»;
- en la lista, esos ligandos llevan **ARCHIVED**.

🎯 Modo avión → abrir un ligando ya visto (se abre) y uno nuevo (diálogo «No internet connection»).

### 9.2 Carga perezosa

> *Lazy Loading: Implement efficient lazy loading for the ligand list if it contains many items.*

**✅** La lista es un **`LazyColumn`**: solo **compone las filas visibles** (unas diez de 1243), y las reutiliza por su clave. La información de cada celda se lee de un índice local; **nada** se descarga para pintar la lista. Además, la escena 3D también es «perezosa»: se **monta por lotes** (9.4).

### 9.3 *Parsing* en segundo plano con progreso

> *Background Parsing: Parse .cif files on a background thread with progress indication.*

**✅** La descarga y el *parsing* corren en **`Dispatchers.IO`** (`loading/LigandLoader.kt`); el hilo principal no se bloquea nunca. El **progreso** se ve en el diálogo de carga (`loading/LoadStage.kt`, `ui/LoadStageText.kt`):

```
[OK] Archive     (¿está en el móvil?)
[OK] Connect
[..] Download    62 % · 4.6 / 7.4 KB
[  ] Parse       atoms and bonds
```

**Cancel** detiene el trabajo en cualquier momento. Cada etapa llega del hilo de trabajo y **se escribe en el hilo principal**: una etapa que llega tarde, de una carga cancelada, no puede volver a enseñar el indicador.

### 9.4 Memoria y nivel de detalle

> *Memory Management: Implement efficient memory management for large molecules, possibly using level-of-detail (LOD) techniques.*

**✅**
- **LOD** (`scene/MeshDetail.kt`): cuantos más átomos tiene la molécula, **menos triángulos** por bola y menos caras por varilla, dentro de un **presupuesto de 204 800 triángulos**. Se decide por el tamaño de la **molécula**, no de lo visible: ocultar los hidrógenos no reconstruye todas las mallas.
- **Montaje por lotes** (`scene/Assembly.kt`): los nodos se crean **un lote por fotograma**, primero los átomos y luego los enlaces, con un lote que **se adapta** a lo que tardan los fotogramas. La pantalla no se congela con una molécula grande, y la barra **ASSEMBLY 312 / 511** lo enseña.
- **Compartir lo compartible:** un **material por elemento**, no por átomo; una **imagen de etiqueta por elemento**.
- **Topes:** 2 MB por fichero descargado (más es imposible para un ligando: se rechaza como `TooLarge`); 20 MB de caché.

### 9.5 60 FPS garantizados

> *60 FPS Guarantee: Ensure smooth 60 FPS rendering even for complex molecules with many atoms.*

**✅** Comprobado en el A33:
- Un **contador de FPS** siempre visible (`scene/FpsMeter.kt`): verde ≥ 55, ámbar, rojo < 30, e **IDLE** en reposo. Filament solo dibuja cuando algo cambia, así que en reposo no gasta batería.
- **Con moléculas grandes** (15P), el giro va a la frecuencia de la pantalla.
- El IDLE **de verdad** costó tres rondas en el móvil, con un diagnóstico en Logcat (`SwiftyFrames`). Eran recomposiciones que pedían fotogramas sin que nada se moviera: el texto del contador, que cambiaba de ancho, y la escena dentro de un `BoxWithConstraints` (documento 21, §18-20). Es una buena historia para la presentación.

## 10. VII.5 Compartir y exportar ampliado

> *Extended Sharing and Export. Enhance the sharing capabilities of your application.*

Dónde: `share/`, `export/`, `ui/CompareScreen.kt`, `molecule/Comparison.kt`, `molecule/AtomicMass.kt`, `prefs/FavoritesStore.kt`; documentos 15, 16, 18, 22 y 24.

### 10.1 Mensaje propio

> *Custom Share Messages: When sharing, include a custom message with the ligand name, number of atoms, and molecular formula.*

**✅** Con cada imagen o fichero va este texto (`share/ShareText.kt`):

```
Ligand ATP · ADENOSINE-5'-TRIPHOSPHATE      ← el nombre
C10H16N5O13P3 · 47 atoms · 49 bonds         ← la fórmula y el número de átomos (y de enlaces)
Viewed in Swifty Proteins
```

- **El nombre** viene del propio `.cif` (`_chem_comp.name`), limpio.
- **La fórmula** se **cuenta** de los átomos (orden de Hill) y se comprobó contra la de RCSB en los **1242** ligandos: **1242 iguales**.
- La imagen lleva además una **banda** con los mismos datos.

### 10.2 Varios formatos

> *Multiple Export Formats: Allow exporting the view as different image formats (PNG, JPEG) or even as a 3D model file.*

**🟢** El menú SHARE tiene **ocho** entradas (`share/ExportFormat.kt`):

| Entrada | Formato | Nota |
|---------|---------|------|
| Share image | **PNG** | Sin pérdida |
| Share image | **JPEG** | Más ligero |
| Save to gallery | PNG | Álbum «Swifty Proteins» |
| Share ligand file | `.cif` | El original de RCSB |
| Share chemical model | **`.mol`** (MDL V2000) | Abre en cualquier programa de química; validado con **RDKit** en los 1242 |
| Share 3D model | **`.glb`** (glTF 2.0) | **El «3D model file» del enunciado**: el modelo con el modelo de visualización y los colores actuales; el validador oficial de Khronos da **0 errores** |
| Share video / Save video to gallery | MP4 | 10.3 |

### 10.3 Vídeo

> *Video Recording: Record a short video of the molecule rotating and share it.*

**🟢** **Una vuelta completa en 6 s, a 30 fps**, en MP4 (documento 22):
- Con el **`SceneRecorder`** de SceneView: Filament dibuja la escena **una segunda vez** directamente en el codificador de vídeo. **No** se graba la pantalla: no hay diálogo de consentimiento de Android ni servicio en primer plano, y el vídeo no lleva paneles ni barras del sistema.
- **Gira la molécula, no la cámara**, a velocidad constante: el vídeo se puede repetir en bucle sin salto.
- El **rótulo** (nombre, fórmula, átomos) va **dentro** del fotograma.
- Mientras graba: «● REC 2.4 / 6.0 s» y **Cancel**, y los toques se ignoran.
- Se **comparte** (`video/mp4`: WhatsApp, Telegram…) o se **guarda** en Movies/Swifty Proteins. Cancel o Atrás lo anulan y borran el fichero a medias.

### 10.4 Favoritos

> *Favorites System: Allow users to favorite ligands and maintain a favorites list for quick access.*

**✅**
- **Marcar:** la estrella ☆/★ en cada celda y en la molécula.
- **La lista de acceso rápido:** la estrella junto al buscador muestra **solo los favoritos**, y se puede seguir buscando dentro de ellos.
- **Por cuenta:** cada usuario tiene los suyos, y al bloquear la app desaparecen hasta que alguien entra.
- Se guardan en el móvil (`prefs/FavoritesStore.kt`).

### 10.5 Comparar dos moléculas

> *Comparison View: Display two molecules side-by-side for comparison.*

**🟢** **CMP** en la molécula → elegir otra (el mismo buscador) → **CONCORDANCE** (documento 24):
- **Lado a lado:** dos escenas 3D, cada una con su **FPS**. **H** actúa en las dos; **SPIN** las hace **girar juntas**, con el mismo ángulo en cada fotograma.
- **Para comparar:** una tabla en dos columnas, la izquierda y la derecha como las escenas:
  - fórmula;
  - **masa** (pesos de la IUPAC, comprobados contra el CCD en los **1241** ligandos: ≤ 0,06 g/mol);
  - átomos, átomos pesados, enlaces;
  - **anillos** (número ciclomático: enlaces − átomos + piezas);
  - enlaces aromáticos, carga, tamaño en Å;
  - cada elemento con sus dos cuentas, y un toque en él lo ilumina **en las dos** escenas;
  - el **parecido de composición** (Tanimoto de las fórmulas).

  El valor mayor de cada fila va en oro.

---

## 11. Lo que falta comprobar en el móvil

Todo compila y está probado en la JVM, pero **antes de la defensa** hay que verlo en el A33:

| # | Qué | Apartado | Riesgo si falla |
|---|-----|----------|-----------------|
| 1 | El **negro del barrido** tapa también la escena 3D; el arranque espera al tubo; un ligando ARCHIVED se abre sin diálogo | 1.2, 3.4, VII.2 | Medio: la molécula se vería por encima del negro |
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
