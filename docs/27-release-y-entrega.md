# 27 · Compilación *release* y entrega en el repositorio del campus

> **Estado:** **escrito**. La configuración de Gradle **no se pudo compilar aquí** (sin el SDK de Android): se comprueba con la primera `./gradlew :app:assembleRelease` (§9). El cambio en el código (el diagnóstico de fotogramas solo en *debug*) compila contra los *stubs*; 632 pruebas en verde.

## Índice

1. [Debug y release: qué cambia](#1-debug-y-release-qué-cambia)
2. [La firma de la release](#2-la-firma-de-la-release)
3. [Crear la clave (una vez)](#3-crear-la-clave-una-vez)
4. [Compilar, instalar y comprobar](#4-compilar-instalar-y-comprobar)
5. [R8: por qué sigue apagado](#5-r8-por-qué-sigue-apagado)
6. [Lo que se quitó de la release](#6-lo-que-se-quitó-de-la-release)
7. [Qué va al repositorio del campus](#7-qué-va-al-repositorio-del-campus)
8. [Cómo copiarlo sin arrastrar basura](#8-cómo-copiarlo-sin-arrastrar-basura)
9. [Comprobaciones antes de la defensa](#9-comprobaciones-antes-de-la-defensa)
10. [Trampas](#10-trampas)
11. [Alternativas](#11-alternativas)
12. [Decisiones](#12-decisiones)
13. [Qué recordar](#13-qué-recordar)

---

## 1. Debug y release: qué cambia

| | Debug (`installDebug`) | Release (`assembleRelease`) |
|---|------------------------|------------------------------|
| **Depurable** (`debuggable`) | Sí: Android Studio se engancha, el *runtime* de Compose añade comprobaciones | **No**: Compose y Kotlin corren sin comprobaciones de depuración; **más rápido**, sobre todo al recomponer |
| **Firma** | La clave de depuración común del repositorio (`app/debug.keystore`, documento 11) | **Una clave propia**, fuera del repositorio (§2) |
| **Diagnóstico de fotogramas** (`SwiftyFrames`) | Sí (documento 21, §19) | **No** (§6) |
| **Contador de FPS** | Sí | **Sí**: es parte de lo que se enseña (VII.4) |
| **R8** | No | **No** (§5) |

**Para la defensa:** el FPS de una release es el **real** de la app. Una debug puede bajar de 60 por las comprobaciones de depuración, no por nuestro código.

## 2. La firma de la release

Android **solo instala APK firmados**. La firma dice «esta app es de quien tiene esta clave»: una actualización con **otra** clave se rechaza (documento 11).

**La regla:** la clave de una release **nunca** va al repositorio. Quien la tiene puede publicar actualizaciones en nombre de la app.

**Cómo está hecho** (`app/build.gradle.kts`):

```kotlin
val releaseKeys: Properties? = rootProject.file("keystore.properties").takeIf { it.isFile }?.let { file ->
    Properties().apply { file.inputStream().use { load(it) } }
}
...
signingConfigs {
    if (releaseKeys != null) {
        create("release") {
            storeFile = rootProject.file(releaseKeys.getProperty("storeFile"))
            storePassword = releaseKeys.getProperty("storePassword")
            keyAlias = releaseKeys.getProperty("keyAlias")
            keyPassword = releaseKeys.getProperty("keyPassword")
        }
    }
}
buildTypes {
    release {
        if (releaseKeys != null) signingConfig = signingConfigs.getByName("release")
    }
}
```

- **`keystore.properties`**, en la raíz del proyecto, guarda las contraseñas y la ruta. Está en `.gitignore`; en el repositorio solo hay un **ejemplo** (`keystore.properties.example`).
- **El fichero de la clave** (`.jks`) vive **fuera** del repositorio, por ejemplo en `~/keys/`. `.gitignore` rechaza también `*.jks` y `*.keystore` (salvo `app/debug.keystore`), por si acaso.
- **Sin `keystore.properties`**, la release se compila igual, pero **sin firmar** (`app-release-unsigned.apk`): no se puede instalar, y tampoco hace falta ningún secreto para que el proyecto compile. Quien clone el repositorio puede compilar y probar en *debug*.

## 3. Crear la clave (una vez)

```bash
mkdir -p ~/keys
keytool -genkeypair -v \
  -keystore ~/keys/swifty-proteins-release.jks \
  -alias swifty-proteins \
  -keyalg RSA -keysize 4096 -validity 10000
```

- `keytool` viene con el JDK (en Android Studio: `<Android Studio>/jbr/bin/keytool`).
- Pide una contraseña para el almacén y unos datos (nombre, organización…) que van en el certificado; para este proyecto sirven los del equipo.
- **RSA de 4096 bits, 10 000 días** (unos 27 años): lo que recomienda Google para una clave de app.
- **En el campus:** `~/keys` está en la carpeta personal, que sobrevive entre sesiones (a diferencia de `goinfre`). Lo importante es que **no** esté dentro del proyecto.

Después, en la raíz del proyecto:

```bash
cp keystore.properties.example keystore.properties
# y rellenar storeFile (ruta ABSOLUTA a la .jks), storePassword, keyAlias, keyPassword
```

**Si se pierde la clave**, la app instalada no se puede **actualizar**: hay que desinstalarla, y con ella se borran las cuentas y la caché del móvil. Para un proyecto del campus no es grave, pero conviene guardar la `.jks` en un sitio seguro.

## 4. Compilar, instalar y comprobar

```bash
./gradlew :app:assembleRelease
# → app/build/outputs/apk/release/app-release.apk

adb uninstall dev.knowledge82.swiftyproteins          # SOLO si hay una debug instalada (otra firma)
adb install app/build/outputs/apk/release/app-release.apk

# Comprobar con qué clave está firmada (apksigner va en el SDK: build-tools/<versión>/)
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

- **Una debug y una release no conviven:** mismo paquete, distinta firma. Instalar una sobre la otra da `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. Desinstalar borra los datos de la app (las cuentas también): después hay que **crear la cuenta otra vez** y activar la huella.
- **`versionCode`** es 1. Para instalar una release nueva **encima** de otra release, con la misma clave, no hace falta subirlo; para publicar sí.

## 5. R8: por qué sigue apagado

**R8** encoge el código (quita lo que no se usa), lo ofusca (renombra clases) y lo optimiza. El APK sería bastante más pequeño. **No se activa**, por esta razón:
- **Filament** (el motor 3D) es C++ y llama a sus clases Kotlin/Java por **JNI**, buscándolas **por nombre**.
- **SceneView** también usa partes de su código por reflexión.
- Si R8 renombra o quita una de esas clases, la app **compila** pero el 3D **falla solo en release**, en tiempo de ejecución.
- Hacerlo bien exige **reglas `-keep`** comprobadas en el móvil, pantalla por pantalla.

**El precio:** un APK más grande. No es un requisito del enunciado. La velocidad de una release viene de **no ser depurable**, no de R8.

## 6. Lo que se quitó de la release

**El diagnóstico de fotogramas** (`FrameDiagnosis`, documento 21, §19) escribía en Logcat una línea por segundo mientras la escena dibujaba, y comparaba los parámetros de la escena en cada recomposición. Fue la herramienta que encontró por qué el FPS no llegaba a IDLE, pero **en una release sobra**.

Ahora `MoleculeScene` mira si la app es depurable:

```kotlin
val debuggable = remember(context) { (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0 }
...
if (debuggable) diagnosis.onFrame(...)?.let { Log.d(FrameDiagnosis.LOG_TAG, it) }
```

- **Por qué un *flag* y no `BuildConfig.DEBUG`:** con AGP 9, `BuildConfig` ya no se genera por defecto (habría que activarlo en `buildFeatures`). `FLAG_DEBUGGABLE` lo pone Android en la propia app instalada: dice la verdad del APK que corre.
- `FLAG_DEBUGGABLE` es un **bit** dentro del entero `flags`: `flags and FLAG` es distinto de 0 si el bit está puesto (la misma técnica de máscaras que en C).

**Lo que se queda en la release a propósito:**
- el **contador de FPS** (VII.4: se enseña);
- los `Log.w` de los errores, que no dicen nada privado (ids de ligandos y tipos de error).

## 7. Qué va al repositorio del campus

**Todo lo que git ya sigue, y nada más.** Para compilar, probar y defender hace falta:

| Qué | Para qué | ¿Obligatorio? |
|-----|----------|---------------|
| `app/` (código, recursos, `assets/ligands.txt`, pruebas) | La app | **Sí** |
| `app/build.gradle.kts`, `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties` | La configuración de la compilación | **Sí** |
| `gradle/` (`libs.versions.toml`, `wrapper/gradle-wrapper.jar` y `.properties`, `gradle-daemon-jvm.properties`), `gradlew`, `gradlew.bat` | El *wrapper*: compila **sin** tener Gradle instalado, con la versión exacta del proyecto | **Sí** |
| `app/debug.keystore` | Instalar *debug* desde cualquier máquina sin desinstalar (documento 11) | Muy recomendable |
| `README.md`, `docs/` | La documentación y la preparación de la defensa | Recomendable |
| `tools/` | Cómo se generaron el índice, los sonidos y el arte, y la comprobación del corpus: los datos de la app son **reproducibles** | Recomendable |
| `keystore.properties.example`, `.gitignore` | Cómo firmar una release; qué no se sube | Recomendable |

**Lo que NO va:**

| Qué | Por qué |
|-----|---------|
| `keystore.properties`, `*.jks` | **Secretos**: la clave de la release |
| `local.properties` | La ruta del SDK **de tu máquina** (`sdk.dir`); otra máquina tiene otra |
| `build/`, `app/build/`, `.gradle/`, `.kotlin/`, `.idea/` | Resultados de compilación y cachés del IDE: se regeneran, ocupan mucho y cambian en cada compilación |
| Los APK | Se generan desde el código; un binario en el repositorio no se puede revisar ni defender línea a línea. Para la defensa, la app va **instalada** en el móvil |
| `__pycache__/`, `*.pyc` | *Bytecode* de Python generado al ejecutar las herramientas (había uno subido por error: se quitó) |

**El repositorio pesa** unos 5,8 MB de ficheros (sin `.git`), sobre todo arte (`webp`) y las imágenes de los documentos.

## 8. Cómo copiarlo sin arrastrar basura

**La forma segura: exportar solo lo que git sigue.** `git archive` empaqueta **exactamente** los ficheros versionados de un *commit*. No puede colarse nada ignorado, aunque esté en la carpeta (un `keystore.properties`, un `build/`, un APK).

```bash
# 1. El repositorio del campus, clonado en otro sitio
git clone <URL-del-repositorio-del-campus> ~/campus/swifty-proteins

# 2. Los ficheros de GitHub, tal como están en main, sin historia
cd ~/Projects/swifty-proteins
git archive --format=tar HEAD | tar -x -C ~/campus/swifty-proteins

# 3. Revisar y subir
cd ~/campus/swifty-proteins
git status                    # que no haya keystore.properties, .jks, build/, local.properties
git add -A
git commit -m "Swifty Proteins"
git push
```

**La otra forma: subir la historia entera.** Se añade el repositorio del campus como un segundo `remote` y se empuja `main` con todos sus *commits*:

```bash
git remote add campus <URL-del-repositorio-del-campus>
git push campus main
```

| | Copia con `git archive` | Historia entera |
|---|-------------------------|-----------------|
| Lo que llega | Una foto del proyecto | Todos los *commits* desde el *spike*, con sus mensajes y sus etiquetas de autoría |
| Seguridad | Imposible subir algo ignorado | Igual de segura (solo sube lo versionado) |
| Para la defensa | Limpia | Enseña la evolución, por ejemplo el FPS que no llegaba a IDLE |

**Elegir es del equipo.** Conviene revisar antes las **normas del campus** sobre el repositorio de entrega (rama, estructura, tamaño) y sobre el uso de herramientas de IA, y estar listos para explicar cualquier parte del código, que es lo que se evalúa.

**Antes de subir**, rellenar en el README la sección **«Autores»**, que aún tiene `<login 42 del miembro 1>` y `<login 42 del miembro 2>`.

## 9. Comprobaciones antes de la defensa

1. **En una copia limpia** (como la verá un evaluador): `git clone` del repositorio del campus en otra carpeta, y `./gradlew :app:testDebugUnitTest :app:assembleDebug`. Si compila ahí, compila en cualquier sitio.
2. `./gradlew :app:assembleRelease` **con** `keystore.properties`: sale `app-release.apk`, y `apksigner verify --print-certs` enseña **tu** certificado (no el de depuración).
3. **Sin** `keystore.properties` (se mueve un momento): sale `app-release-unsigned.apk`, sin errores.
4. La release instalada en el A33:
   - crear cuenta, huella, Home → acceso;
   - una molécula, el FPS a 60 y luego IDLE;
   - SHARE a la galería.
5. **Nada de `SwiftyFrames`** en `adb logcat` con la release.

## 10. Trampas

1. **La clave en el repositorio**: cualquiera podría firmar «actualizaciones» de la app. Con un fichero ignorado, nunca está en git.
2. **`local.properties` subido**: en otra máquina el SDK está en otra ruta y la compilación falla de forma confusa.
3. **Copiar la carpeta a mano** (`cp -r`): arrastra `build/`, `.gradle/`, `local.properties` y el `keystore.properties`. `git archive` copia solo lo versionado.
4. **R8 activado sin probar**: el 3D falla solo en release.
5. **Instalar una release sobre una debug**: `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, y al desinstalar se pierden las cuentas.
6. **Juzgar el FPS en debug**: las comprobaciones de depuración lo bajan.
7. **Una `.pyc` en el repositorio**: un binario generado que cambia con cada versión de Python.

## 11. Alternativas

| Alternativa | Por qué no (ahora) |
|-------------|--------------------|
| Firmar la release con la clave de depuración | Funciona, pero es justo lo que la clave de depuración **no** debe hacer (documento 11): cualquiera con el repositorio firmaría como nosotros |
| Variables de entorno para las contraseñas (`System.getenv`) | Igual de válido; un fichero ignorado es más fácil de usar en el campus y en Android Studio |
| *Android App Bundle* (`bundleRelease`, `.aab`) | Es el formato para Google Play; para instalar en un móvil hace falta un APK |
| R8 con reglas `-keep` para Filament y SceneView | APK más pequeño, pero hay que probarlo todo en el móvil; no lo pide el enunciado |
| `BuildConfig.DEBUG` para el diagnóstico | Hay que activar `buildFeatures.buildConfig`; `FLAG_DEBUGGABLE` ya está en la app |

## 12. Decisiones

| # | Decisión | Estado | Motivo |
|---|----------|--------|--------|
| D94 | **Firma de la release** con una clave propia, leída de `keystore.properties` (ignorado); la clave fuera del repositorio; sin el fichero, release sin firmar | Decidida (sin compilar con Gradle aquí) | Nada secreto en git; el proyecto compila sin secretos |
| D95 | **R8 apagado** también en release | Decidida | Filament y SceneView usan JNI y reflexión; reglas sin probar romperían el 3D solo en release |
| D96 | **Diagnóstico de fotogramas solo en *debug*** (`FLAG_DEBUGGABLE`); el contador de FPS se queda | Decidida | Una release no escribe su diagnóstico; el FPS se enseña (VII.4) |

## 13. Qué recordar

- **Debug** es para trabajar, **release** para enseñar: más rápida y firmada con una clave propia.
- **Un secreto nunca entra en git**: va en un fichero ignorado, y el proyecto debe compilar sin él.
- **`git archive`** copia exactamente lo versionado.
- **R8 y JNI** no se llevan bien sin reglas probadas.
- **Las máscaras de bits** (`flags and FLAG`) funcionan en Kotlin igual que en C.
