# 17 · Preparación de la defensa

> **Cómo usar este documento.** Parte 1: las preguntas de la **hoja de evaluación**, una por una, en su orden. Para cada una: **qué comprueban**, **qué decir** (corto), **qué enseñar** en el móvil, **dónde está** en el código y **posibles repreguntas**. Los recuadros **Kotlin** explican el trozo de lenguaje que aparece en esa respuesta. Partes 2 y 3 (preguntas propias y las del equipo): **pendientes**.
>
> Leyenda: ✅ cumplido · ⚠️ riesgo o algo que falta · 🎯 qué enseñar.

## Índice

- [Antes de la defensa: lista de comprobación](#antes-de-la-defensa-lista-de-comprobación)
- [Parte 1 · La hoja de evaluación](#parte-1--la-hoja-de-evaluación)
  - [1. Requisitos previos](#1-requisitos-previos) · [2. Icono y *launch screen*](#2-icono-y-launch-screen) · [3. Compilación y maquetación](#3-compilación-y-maquetación)
  - [4. Biometría](#4-biometría) · [5. Fallo de la biometría](#5-fallo-de-la-biometría) · [6. Seguridad de las contraseñas](#6-seguridad-de-las-contraseñas) · [7. Sin biometría](#7-sin-biometría) · [8. Seguridad: siempre el login](#8-seguridad-siempre-el-login)
  - [9. Lista](#9-lista) · [10. Búsqueda](#10-búsqueda) · [11. Fallo de carga](#11-fallo-de-carga) · [12. Indicador de carga](#12-indicador-de-carga)
  - [13. Render 3D](#13-render-3d) · [14. Bolas y varillas, CPK](#14-bolas-y-varillas-cpk) · [15. Gestos y ficha del átomo](#15-gestos-y-ficha-del-átomo) · [16. Compartir y guardar](#16-compartir-y-guardar)
  - [17. Bonus: modelos de visualización](#17-bonus-modelos-de-visualización) · [18. Bonus adicionales (0–5)](#18-bonus-adicionales-05)
- [Parte 2 · Preguntas propias](#parte-2--preguntas-propias) (pendiente)
- [Parte 3 · Preguntas del equipo](#parte-3--preguntas-del-equipo) (pendiente)

---

## Antes de la defensa: lista de comprobación

| # | Qué | Por qué |
|---|-----|---------|
| 1 | **Guardar en la galería** probado en el A33 (SHR → Save to gallery → álbum «Swifty Proteins») | Punto 16 es **obligatorio** (documento 18) |
| 2 | Compilar en el ordenador donde se va a defender (`./gradlew assembleDebug`) | Punto 3: si no compila, la evaluación **se para** |
| 3 | Un **emulador** creado y probado (Android Studio → Device Manager) | Punto 3 dice «launches the simulator»; el 1 pide además el móvil real |
| 4 | El A33 cargado, con **huella** registrada, la app instalada y **una cuenta creada** | Puntos 4, 5, 7, 8 |
| 5 | Saber poner el **modo avión** y abrir un ligando **nunca abierto** | Punto 11 (la caché abre sin red los ya vistos) |
| 6 | Volumen a media altura o modo silencio, según la sala | Los sonidos (documento 14) |

---

# Parte 1 · La hoja de evaluación

## 1. Requisitos previos

> *Git; el estudiante explica su código; Swift/Xcode o Android SDK actual; datos `.cif` de RCSB; probado en un dispositivo real. Si algo falla, la evaluación se para.*

**Qué decir.**
- **Repositorio:** `github.com/Knowledge82/swifty-proteins`.
- **Plataforma:** Android **nativo** en **Kotlin**. `compileSdk` y `targetSdk` **37** (lo último), `minSdk` 24 (Android 7.0). Gradle con AGP 9.4.1, Kotlin 2.4.21. Interfaz en **Jetpack Compose**, 3D con **SceneView 4.69.4** sobre **Filament** (el motor de Google).
- **Datos:** `https://files.rcsb.org/ligands/view/{ID}.cif`, exactamente la URL del enunciado (`LigandRepository.DEFAULT_BASE_URL`).
- **Dispositivo real:** Samsung **Galaxy A33** (con lector de huella).

🎯 Abrir la app en el A33.

**Si preguntan…**
- *¿Por qué Kotlin y no Swift?* El enunciado permite Android; Kotlin es el lenguaje oficial de Android desde 2019.
- *¿Por qué `.cif`?* Es el formato de referencia del enunciado y el que RCSB publica para cada ligando (diccionario CCD).

## 2. Icono y *launch screen*

> *Icono en la pantalla de inicio y una pantalla de lanzamiento visible al menos 1 segundo.*

**Qué decir.**
- **Icono adaptativo** (Android 8+): dos capas, fondo y primer plano, en todas las densidades (`mipmap-mdpi` … `xxxhdpi`), más una versión **monocroma** para los iconos temáticos de Android 13. Para Android 7 hay PNG clásicos.
- ***Launch screen***: la **pantalla de arranque del cogitador** (`BootScreen`), **4,2 s**: líneas de consola, una barra de progreso y «> DONE». Se puede saltar con un toque, pero **solo pasado 1 s**: el mínimo de la hoja se cumple siempre.
- La ***splash* del sistema** (obligatoria desde Android 12) existe, pero es **invisible**: mismo color de fondo e icono transparente. Si se viera, saldrían dos logotipos seguidos.

🎯 Cerrar la app desde Recientes, tocar el icono: arranque completo con sonido.

**Dónde.** `ui/BootScreen.kt`, `boot/BootSequence.kt` (los tiempos, con pruebas), `res/mipmap-*`, `res/values/themes.xml`. Documentos 07b, 08c, 08d.

**Si preguntan…** *¿Por qué no usar la splash del sistema para el segundo?* Desde Android 12 la splash del sistema dura lo que tarda la app en dibujar su primer fotograma: retenerla a propósito va contra las guías de Android y solo puede mostrar un icono. Una pantalla propia puede durar lo que queramos y contar algo.

## 3. Compilación y maquetación

> *Compila, usa técnicas modernas de maquetación, arranca en el simulador. Si no, se para.*

**Qué decir.**
- **Compila** con `./gradlew assembleDebug` (o ▶ en Android Studio). 452 pruebas automáticas: `./gradlew testDebugUnitTest`.
- **Maquetación moderna: Jetpack Compose**, la forma **declarativa** de hacer interfaces en Android (la que Google recomienda hoy). En vez de XML con `ConstraintLayout`, la interfaz se **describe** con funciones: `Column` (vertical), `Row` (horizontal), `Box` (superpuesto), `Modifier.weight(1f)` (reparte el espacio sobrante), `fillMaxWidth()`, etc. Se adapta a cualquier tamaño de pantalla y a la rotación.
- En Android el «simulador» se llama **emulador**.

🎯 Girar el móvil en la lista y en la molécula: todo se recoloca y **nada se pierde** (búsqueda, selección, modelo).

> **Kotlin / Compose.** Una función con `@Composable` **dibuja** una parte de la pantalla. Cuando cambia un dato que lee (un *state*), Compose la vuelve a ejecutar (**recomposición**) y solo se redibuja lo que cambió.
> ```kotlin
> Row(verticalAlignment = Alignment.CenterVertically) {   // en fila, centrados en vertical
>     BackKey(onClick = onBack)                           // 48 dp
>     Column(modifier = Modifier.weight(1f)) { … }        // ocupa todo lo que sobra
>     ConsoleKey(text = "SHR", …)
> }
> ```

**Si preguntan…** *¿Dónde está el `ConstraintLayout`?* No hace falta: en Compose las filas, columnas y `weight` cubren lo mismo, y hay un `ConstraintLayout` para Compose si se necesitara.

## 4. Biometría

> *Crear una cuenta e iniciar sesión con biometría (BiometricPrompt en Android). Tras el éxito, se carga la vista siguiente.*

**Qué decir.**
1. **Crear cuenta**: nombre (3–20 caracteres, minúsculas, cifras, `_.-`) y contraseña (8–64, con letra y cifra, sin contener el nombre). Los requisitos se ven **en vivo** mientras se escribe.
2. Al crearla, la app **ofrece activar la huella** para esa cuenta (voluntario, por cuenta).
3. En el inicio de sesión aparece el botón de **huella**: abre el **`BiometricPrompt`** del sistema (biblioteca `androidx.biometric` 1.4).
4. Éxito → la **lista** de ligandos.

**El detalle de seguridad** (lo que distingue una huella «de verdad»): se exige biometría **Clase 3** (fuerte) y un **`CryptoObject`**. El éxito no es un `true` que cualquiera podría falsificar: la huella **desbloquea una clave del Keystore** que descifra un *token* de esa cuenta. Sin huella válida, la clave no se puede usar. Si cambian las huellas del móvil, la clave **se invalida sola** (`setInvalidatedByBiometricEnrollment`) y hay que volver a activarla con la contraseña.

🎯 Crear una cuenta nueva → aceptar la huella → bloquear (Home y volver) → entrar con la huella → lista.

**Dónde.** `auth/AuthController.kt` (todas las reglas, probadas), `auth/AndroidSecurity.kt` (Keystore, `Cipher`), `MainActivity.kt` (`registerForAuthenticationResult`), `ui/AuthScreens.kt`. Documentos 05 y 05a.

**Si preguntan…**
- *¿La huella se guarda en la app?* **No.** La huella nunca sale del sensor ni del hardware seguro del móvil; la app solo recibe «sí» o «no» (y, con `CryptoObject`, el uso de una clave).
- *¿Y Face ID?* En Android es «biometría»: la cara también vale si el móvil la tiene como **Clase 3**. El A33 solo tiene huella de clase fuerte.

## 5. Fallo de la biometría

> *Si la biometría falla, un popup avisa del fallo.*

**Qué decir.**
- **Un dedo que no coincide:** el propio diálogo del sistema dice «No reconocido» y deja reintentar (así funciona `BiometricPrompt`: los fallos sueltos no cierran el diálogo).
- **Cuando el intento termina en error**, la app enseña **su** popup: «Too Many Attempts» (5 fallos: bloqueo de 30 s), «Biometrics Locked» (bloqueo permanente: hay que desbloquear el móvil con PIN), «Time Out», «Sensor Unavailable», «Fingerprints Changed», o «Authentication Failed» para cualquier otro.
- **Cancelar a propósito** (botón «Use password» o Atrás) **no** es un fallo: no sale popup, se queda el formulario.

🎯 Poner un dedo no registrado **5 veces**: popup «Too Many Attempts». (Después la huella queda bloqueada 30 s: hacerlo **al final** de la parte de biometría.)

⚠️ **Riesgo:** un evaluador que espere «un popup **nuestro** tras **un** fallo» verá el «No reconocido» del sistema dentro de su diálogo. Explicar que ese ES el aviso de fallo de Android, y enseñar el popup propio con los 5 intentos.

**Dónde.** `auth/BiometricText.kt` (los textos y cuándo hay popup, probados).

## 6. Seguridad de las contraseñas

> *Cómo se guardan; nunca en claro; hash adecuado o almacén seguro (KeyStore, bcrypt…).*

**Qué decir.** Hacemos **las dos cosas** que nombra la hoja:
1. **Hash Argon2id** (Bouncy Castle), el ganador de la Password Hashing Competition y el primero que recomienda OWASP. Parámetros de OWASP: **19 MiB** de memoria, **2** pasadas, paralelismo 1, **sal aleatoria de 16 bytes por cuenta**, hash de 32 bytes. Se guarda el hash, la sal y los parámetros; **nunca** la contraseña.
2. **El fichero de cuentas está cifrado** con **AES-256-GCM** y una clave que vive en el **Android Keystore** (no se puede sacar del móvil). Está en `noBackupFilesDir`: fuera de las copias en la nube.

Detalles que suman: la comparación del hash es de **tiempo constante** (no revela cuántos bytes coinciden); la contraseña se borra del estado nada más usarla; tras 5 fallos, freno de 30 s que se duplica hasta 5 minutos; el mensaje de error es **el mismo** para «no existe» y «contraseña mala» (no revela qué cuentas hay).

**Dónde.** `auth/PasswordHasher.kt` (con el vector de prueba oficial del RFC 9106), `auth/AccountStore.kt`, `auth/Policies.kt`. Documento 05.

**Si preguntan…**
- *¿Por qué no SHA-256?* Es **rápido**: una tarjeta gráfica prueba miles de millones por segundo. Argon2id es **lento a propósito** y gasta **memoria**, lo que encarece los ataques con GPU.
- *¿Qué es la sal?* Bytes aleatorios distintos por cuenta, mezclados con la contraseña: dos personas con la misma contraseña tienen hashes distintos, y las tablas precalculadas no sirven.

## 7. Sin biometría

> *Si el móvil no tiene biometría, se puede entrar con usuario y contraseña.*

**Qué decir.** La contraseña funciona **siempre**. La huella es un extra **por cuenta**. Si el móvil no tiene sensor, no tiene huellas registradas o el sensor no está disponible, el botón de huella **no aparece** y una línea explica por qué («Add a fingerprint in the phone settings…», «This phone has no strong biometric sensor…»).

🎯 En el **emulador** (sin huella configurada) o con una cuenta sin huella: entrar con la contraseña.

**Dónde.** `BiometricText.availabilityNote`, `AuthController`.

## 8. Seguridad: siempre el login

> *Pedir siempre el login al volver a la app (Home y volver), aunque no se haya cerrado sesión.*

**Qué decir.** La app se **bloquea cada vez que pasa a segundo plano**: botón Home, otra app, pantalla apagada e incluso la hoja de «compartir». Se escucha el ciclo de vida **de todo el proceso** (`ProcessLifecycleOwner`): su evento `ON_STOP` llega cuando **ninguna** pantalla de la app está visible. **Girar** el móvil **no** bloquea (Android espera un momento antes de decidir) y el diálogo de la huella tampoco. Al volver se pide el login y luego la app **sigue donde estaba**. Además, en Recientes (Android 13+) la miniatura no enseña el contenido.

🎯 Los 4 pasos de la hoja: abrir → login → lista → Home → abrir → **login**.

**Dónde.** `ui/AuthViewModel.kt`:

> **Kotlin.**
> ```kotlin
> private val lockOnBackground = LifecycleEventObserver { _, event ->
>     if (event == Lifecycle.Event.ON_STOP) controller.lock()
> }
> init { ProcessLifecycleOwner.get().lifecycle.addObserver(lockOnBackground) }
> ```
> `{ _, event -> … }` es una **lambda** (función anónima). `_` = «este parámetro no lo uso». `init { }` es el bloque que se ejecuta al crear el objeto (como el cuerpo de un constructor).

## 9. Lista

> *Se muestran todos los ligandos de `ligands.txt`.*

**Qué decir.** `ligands.txt` va dentro de la app (`assets/`): **1243** ids. Se leen una vez al arrancar. La lista es un **`LazyColumn`**, el equivalente Compose del `RecyclerView`: solo **crea las filas que se ven** (unas 10), no las 1243. El contador de arriba dice «PATTERNS FOUND: 1,243».

🎯 Desplazarse hasta el final (ZZS).

**Dónde.** `catalog/LigandCatalog.kt`, `ui/LigandListScreen.kt`.

> **Kotlin.** `items(results, key = { it }) { id -> PatternRow(…) }`: una fila por id. La **clave** (`key`) identifica cada fila por su id, para que Compose las reutilice bien cuando la lista cambia al buscar.

## 10. Búsqueda

> *Buscar un ligando; al elegirlo, se carga el correcto.*

**Qué decir.** Búsqueda **en tiempo real** (cada tecla filtra), **sin distinguir mayúsculas**, primero los que **empiezan** por el texto y después los que lo **contienen**. Al tocar una fila se descarga **ese** id; el título de la pantalla 3D es «Pattern ATP» y la tarjeta enseña su nombre químico y su fórmula.

🎯 Escribir «atp» en minúsculas → tocar ATP → «Pattern ATP», «ADENOSINE-5'-TRIPHOSPHATE», C₁₀H₁₆N₅O₁₃P₃.

**Dónde.** `LigandCatalog.search`:

> **Kotlin.**
> ```kotlin
> val (startingWith, containing) = ids
>     .filter { it.contains(wanted, ignoreCase = true) }
>     .partition { it.startsWith(wanted, ignoreCase = true) }
> ```
> `partition` parte una lista en **dos** según una condición; `val (a, b) = …` es una **desestructuración** (recibe las dos a la vez). `it` es el nombre implícito del único parámetro de una lambda. `ignoreCase = true` compara sin depender del idioma del móvil.

## 11. Fallo de carga

> *Si falla la carga, un popup indica el motivo (sin red, URL mala…).*

**Qué decir.** Cada fallo tiene **su** popup con dibujo, título, el motivo y «Retry» cuando tiene sentido. Los cuatro del enunciado, con **sus palabras exactas** (hay una prueba que lo comprueba): sin conexión, tiempo agotado, ligando no encontrado (404), fichero corrupto. Además: error del servidor, descarga cortada, fichero demasiado grande, id no válido. Todo lo que llega de la red **se valida**: un HTML de un wifi de hotel con «200 OK» no se toma por un ligando.

🎯 **Modo avión** + un ligando **nunca abierto** → «No internet connection. Please check your network.» con Retry. (⚠️ Uno **ya abierto** se abre sin red, desde la caché: es el bonus de rendimiento, enseñarlo también.)

**Dónde.** `net/LigandRepository.kt` (clasifica el fallo), `ui/LoadErrorText.kt` (textos), `ui/LoadDialogs.kt`. Documentos 06 y 13.

> **Kotlin.** `sealed interface LoadError { data object NoConnection : LoadError; data object Timeout : LoadError; … }`. Una interfaz **sellada**: el compilador conoce **todos** los casos posibles, así que un `when` sobre ella sin `else` **no compila** si falta uno. Imposible olvidar un error nuevo.

## 12. Indicador de carga

> *Una rueda giratoria (o una animación de carga limpia) mientras carga.*

**Qué decir.** Un **diálogo modal** con una animación propia (el cráneo servidor que flota), «Invoking the Machine Spirit», «Downloading ligand ATP» y **Cancel**; suena el módem. Es la «animación limpia» que acepta la hoja. **Siempre desaparece**: la pantalla tiene **un único** estado de carga (`Idle`, `Loading`, `Loaded`, `Failed`), así que es imposible quedarse con el indicador y un error a la vez.

🎯 Un ligando nuevo con red (si la red es muy rápida, el diálogo dura un instante).

**Dónde.** `loading/LoadState.kt`, `ui/LoadDialogs.kt`, `ui/LigandsViewModel.kt`.

> **Kotlin.** `sealed interface LoadState { data object Idle; data class Loading(val id: String); data class Loaded(…); data class Failed(…) }`. `data class` = clase de datos: Kotlin genera `equals`, `toString` y `copy`. `data object` = un único valor sin datos.

## 13. Render 3D

> *Render 3D dentro de la app (marco 3D adecuado en Android); zoom y rotación.*

**Qué decir.** **SceneView** (biblioteca Compose) sobre **Filament**, el motor de render físico de Google; el enunciado **nombra Filament** entre los permitidos. Cada átomo es una esfera (`SphereNode`) y cada enlace un cilindro (`CylinderNode`), orientados con trigonometría probada con matrices. Arrastrar = girar; pellizcar = zoom, con límites en **ångström** (nunca más cerca de 4 Å, para no meterse dentro de un átomo). Iluminación de **tres puntos** (principal, relleno, contraluz) más luz ambiental: volumen, no un dibujo plano. Un contador de **FPS** arriba (60 al moverse, IDLE en reposo: Filament solo dibuja cuando algo cambia).

🎯 Girar y hacer zoom con ATP; luego con 15P (256 átomos): el contador sigue en 60.

**Dónde.** `ui/MoleculeScene.kt`, `scene/BondGeometry.kt`, `scene/SceneFraming.kt`. Documentos 02, 07, 12.

## 14. Bolas y varillas, CPK

> *Modelo bolas y varillas con colores CPK.*

**Qué decir.** Bolas pequeñas (el H más pequeño, los pesados más grandes) y varillas finas grises: se ve la **conectividad**. Colores **CPK** de la tabla de Jmol: H blanco, C gris, N azul, O rojo, S amarillo, P naranja, halógenos verdes… Abajo, la **leyenda** con el color y la **letra** de cada elemento dibujado (el color nunca va solo).

🎯 La leyenda y los colores; con el botón **H** se ocultan los hidrógenos.

**Dónde.** `scene/ElementStyle.kt` (colores y radios, probados).

## 15. Gestos y ficha del átomo

> *Al tocar un átomo, una ficha con su tipo (símbolo); desaparece al tocar en cualquier otro sitio. Girar, pellizcar, (opcional) mover con dos dedos; fluido.*

**Qué decir.**
- Tocar un átomo: un **halo** verde lo rodea (el átomo **conserva** su color: pintarlo de amarillo mentiría, el amarillo es el azufre) y la ficha de abajo dice «**C01 · Carbon (C)**» con sus coordenadas X, Y, Z.
- **Desaparece** al tocar el vacío, otro átomo o **cualquier panel** de la pantalla (literalmente «cualquier otro sitio»).
- **Girar** (un dedo), **zoom** (pellizco), **mover** (dos dedos): los tres. Con inercia y a 60 FPS.
- La zona que responde al toque nunca es más pequeña que 0,35 Å: un dedo es más ancho que un hidrógeno.

🎯 Tocar un átomo → ficha → tocar el vacío → desaparece. Arrastrar con dos dedos.

**Dónde.** `ui/MoleculeScreen.kt` (`select`, `deselect`), `scene/ScenePick.kt`.

## 16. Compartir y guardar

> *Compartir el modelo con el botón de compartir **y guardarlo en el álbum de fotos**.*

**Qué decir.** **SHR** abre el menú «PICT-CAPTURE // EXPORT» (documento 18):
- **Share image** (PNG o JPEG): captura la escena 3D con `PixelCopy` (la escena es una superficie de GPU; una captura normal saldría negra), añade una banda «Swifty Proteins · ATP · C10H16N5O13P3 · 47 atoms · 49 bonds» y abre la **hoja de compartir** de Android con un `FileProvider` (un enlace `content://` con permiso de lectura temporal, nunca una ruta del disco). El mensaje lleva nombre, fórmula y tamaño.
- **Save to gallery**: la imagen al **álbum «Swifty Proteins»** de la galería. En Android 10+ con **`MediaStore`**, sin permisos (una app siempre puede añadir sus imágenes), creando la entrada **pendiente** (`IS_PENDING`) y publicándola cuando está entera. En Android 7–9 pide el permiso de almacenamiento.
- Y además, ficheros: `.cif` original, `.mol` y modelo 3D `.glb` (bonus, punto 18).

🎯 SHR → Save to gallery → «Saved to Pictures/Swifty Proteins» → abrir la **Galería** del móvil: álbum Swifty Proteins. Luego SHR → Share image (PNG) → la hoja de compartir.

**Dónde.** `share/MoleculeSharer.kt`, `share/GallerySaver.kt`, `share/ExportFormat.kt`. Documentos 09 y 18.

**Si preguntan…** *¿Por qué no basta la hoja de compartir para guardar?* Que aparezca «guardar imagen» depende de las apps instaladas en cada móvil; la app lo hace por sí misma.

## 17. Bonus: modelos de visualización

> *Otros modelos además de bolas y varillas, con un control para cambiar, sin recargar, correctos según los estándares.*

**Qué decir.** **Cuatro** modelos en la lista desplegable **MODEL…** (documentos 15 y 19):

| Modelo | Cómo | Estándar |
|--------|------|----------|
| Bolas y varillas | El obligatorio | — |
| **Espacio lleno** (*space-filling*, CPK) | Esferas del **radio de van der Waals**, sin varillas: los vecinos se **solapan** (eso es el enlace) | Radios de **Mantina et al. 2009** (extensión de Bondi 1964); H = 1,10 Å |
| **Varillas** (*sticks*) | Varillas gruesas, **cada mitad del color de su átomo** | Como PyMOL o Jmol |
| **Alambre** (*wireframe*) | Líneas finas, también en dos colores | Ídem |

**Sin recargar:** cambiar el modelo solo vuelve a dibujar la misma molécula en memoria; no se descarga ni se interpreta nada (no aparece el diálogo de carga). El modelo con el que se abre cada molécula se elige en **Ajustes**.

🎯 MODEL… → los cuatro en ATP; en espacio lleno, tocar el **borde** de una esfera grande: se selecciona.

**Dónde.** `scene/Representation.kt`, `scene/VanDerWaals.kt`. Documento 15.

## 18. Bonus adicionales (0–5)

> *UI/UX avanzada; interacciones; rendimiento; compartir ampliado. Puntos según calidad y utilidad.*

| Categoría de la hoja | Lo que hay |
|----------------------|------------|
| **UI/UX avanzada** (celdas, animaciones, modo oscuro) | Diseño propio completo «Culto de la Máquina»: **celdas ricas** (fórmula, átomos, ARCHIVED, barra de composición), paneles con esquinas cortadas, arranque animado, cráneo flotante, cursor de consola, **destello** sobre el nombre de la app, **transiciones entre pantallas como un tubo CRT que se apaga y se enciende** (documento 23); **tema oscuro (por defecto) y claro «pergamino», también en el 3D**; si preguntan: el enunciado pide «modo oscuro», la app lo es y además se puede cambiar (documento 21, §17.1); **tutorial** de 4 páginas; **ajustes** (tema, colores Jmol/RasMol, tutorial); **sonidos sintetizados** propios (documento 14). Documento 21 |
| **Interacciones** (resaltar átomos, medir) | **Seleccionar un átomo resalta todos los de su elemento** (o elegirlo en la leyenda); **información de enlaces** (orden, longitud); **regla**: distancia, ángulo y **torsión**; **etiquetas** de elementos en 3D (LABELS); **doble toque** para orbitar un átomo; ocultar hidrógenos |
| **Rendimiento** (caché, 60 FPS) | **Caché** de `.cif` 30 días, funciona **sin conexión** (documento 13); **60 FPS comprobados en el A33** (no bajan), con su **contador** siempre visible; *parsing* fuera del hilo principal con **etapas de carga** y porcentaje; **nivel de detalle** según el tamaño, anclado a lo medido; **montaje de la escena por lotes** con barra ASSEMBLY (documento 20); lista perezosa |
| **Compartir ampliado** (mensajes, varios formatos) | **Mensaje propio** con nombre, fórmula, átomos y enlaces; **seis formatos**: PNG, JPEG, galería, `.cif` original, **`.mol`** (validado con RDKit en los 1242) **modelo 3D `.glb`** (glTF 2.0, validador de Khronos: 0 errores) — documento 18; y **vídeo MP4** de una vuelta completa (6 s, 30 fps, con el `SceneRecorder` de SceneView, sin grabar la pantalla), para compartir o a la galería — documento 22 |
| Además | **Favoritos por cuenta**; nombre y **fórmula** del ligando (comprobada contra RCSB en los 1242); **452 pruebas** automáticas y pruebas de mutación |

🎯 En dos minutos: leyenda «C» (resaltado) → RULER con 3 átomos (ángulo) → MODEL → SHR → `.glb` → modo avión con un ligando ya visto → ★ y filtro → ajustes.

---

# Parte 2 · Preguntas propias

*(Pendiente: las que un evaluador puede hacer fuera de la hoja.)*

# Parte 3 · Preguntas del equipo

*(Pendiente.)*
