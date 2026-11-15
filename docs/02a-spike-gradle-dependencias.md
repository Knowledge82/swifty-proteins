# 02a · Spike 3D: entorno, Gradle y dependencias

Este documento complementa a [`02-spike-3d.md`](02-spike-3d.md). Allí está el **plan** del
spike; aquí está lo que **hemos hecho de verdad** en los pasos 0 y 1, la **teoría** que
hace falta para entenderlo (Gradle, Maven Central, catálogo de versiones, BOM) y las
**decisiones de versiones** que salieron del experimento.

## Índice

1. [Para qué sirve este documento](#1-para-qué-sirve-este-documento)
2. [Estado del spike](#2-estado-del-spike)
3. [Gradle y Maven Central](#3-gradle-y-maven-central)
4. [Catálogo de versiones y BOM](#4-catálogo-de-versiones-y-bom)
5. [Resolución de conflictos y lectura del árbol de dependencias](#5-resolución-de-conflictos-y-lectura-del-árbol-de-dependencias)
6. [Anatomía del proyecto generado](#6-anatomía-del-proyecto-generado)
7. [Decisiones y versiones (ADR-001, aceptado con reservas)](#7-decisiones-y-versiones-adr-001-aceptado-con-reservas)
8. [Registro de lo que hicimos](#8-registro-de-lo-que-hicimos)
9. [Pasos 2 y 3: esfera, enlaces y cámara](#9-pasos-2-y-3-esfera-enlaces-y-cámara)
10. [Riesgos y trampas](#10-riesgos-y-trampas)
11. [Diagnóstico](#11-diagnóstico)
12. [Pendiente](#12-pendiente)
13. [Qué recordar](#13-qué-recordar)
14. [Glosario](#14-glosario)

---

## 1. Para qué sirve este documento

El paso 1 del spike («integrar la librería») parecía una sola línea de Gradle. En la práctica
fue un ejercicio de **alinear versiones** de cuatro piezas que se condicionan entre sí
(Android Gradle Plugin, Kotlin, Compose y SceneView). Dejamos constancia de:

- qué es cada pieza y cómo se relacionan (teoría);
- qué comprobamos y con qué comandos (para poder repetirlo);
- qué decidimos y por qué (para defenderlo);
- qué **no** hemos verificado todavía (para no engañarnos).

---

## 2. Estado del spike

| Paso | Qué | Estado |
|------|-----|--------|
| 0 | Proyecto vacío de Compose ejecutado en el Galaxy A33 | Hecho: se ve «Hello Android!» |
| 1a | Inspección de los ficheros que genera Android Studio | Hecho |
| 1b | Añadir SceneView y subir el objetivo de Java a 21 | Hecho y confirmado al compilar el paso 2 |
| 1c | Subir Kotlin a 2.4.21 | Hecho y **verificado** con `buildEnvironment` |
| 1d | Alinear Compose con el BOM 2026.09.00 | Hecho y **verificado** con `dependencies` |
| H1 | La app arranca en el A33 con todas las versiones nuevas | **Confirmado**: arranca («Hello Android!») y la escena de SceneView se dibuja |
| 2 | Primera esfera en pantalla | Hecho: esfera gris que gira, con varias luces (ver sección 9.6) |
| 3a | Dos átomos y un enlace (cilindro), colores CPK | Hecho (sección 9.8) |
| 3b | «Trípode»: enlaces sobre los ejes X, Y y Z | Hecho, comprobado desde un solo punto de vista (sección 9.8) |
| 3c | Cámara inicial con `orbitRadius = 9.5f` | Hecho: la molécula de prueba queda centrada y cabe en pantalla (capturas del 15/11, 14:28 a 14:31); valor ajustado a ojo |
| 3d | Enlaces en diagonal | **Hecho** con nodos anidados (sección 9.10); verificado en 4 capturas desde ángulos distintos |

Hipótesis de [`02-spike-3d.md`](02-spike-3d.md): H1 cumplida. H2 **cumplida** (esferas, cilindros sobre ejes y en diagonal, colores y luces) y H3 parcialmente (giro, zoom con pellizco y desplazamiento confirmados; falta el toque sobre un átomo).
H4 a H6 sin probar.

---

## 3. Gradle y Maven Central

### 3.1 Gradle

**Gradle** es el sistema de construcción de Android. Hace dos trabajos:

1. **Construir:** compila Kotlin, procesa recursos, empaqueta y firma el APK.
2. **Gestionar dependencias:** descarga las librerías de las que depende el proyecto.

Para quien viene de C, la analogía es:

| En C | En Android |
|------|------------|
| `make` y `Makefile` | Gradle y `build.gradle.kts` |
| `apt install libfoo-dev` | línea `implementation("…")` |
| Un `versions.h` con los `#define` de versión | `libs.versions.toml` |
| «Usa exactamente este compilador» | Gradle Wrapper (`gradlew`) |

Los scripts son código Kotlin (de ahí la extensión `.kts`). La alternativa histórica es Groovy
(`build.gradle`). Kotlin DSL ofrece autocompletado y comprobación de tipos en Android Studio,
y es lo que genera la plantilla actual; no hay motivo para cambiarlo.

### 3.2 Gradle Wrapper

`gradlew` es un pequeño script del repositorio que descarga **la versión exacta de Gradle** que
indica `gradle/wrapper/gradle-wrapper.properties` (en nuestro caso 9.6.0, con su suma de
comprobación SHA-256) y la guarda en `~/.gradle/wrapper/dists`. Así, cualquiera que clone el
repositorio construye con el mismo Gradle. Es el equivalente a «vendorizar» el compilador.

### 3.3 Qué ocurre al pulsar «Sync» (simulación paso a paso)

1. Android Studio ejecuta `gradlew`, que lee `gradle-wrapper.properties` y, si hace falta, descarga Gradle 9.6.0.
2. Gradle lee `settings.gradle.kts`: de dónde sacar **plugins** y **librerías**, y qué módulos hay (`:app`).
3. Resuelve los plugins (Android Gradle Plugin 9.4.1, plugin de Compose…).
4. Lee `libs.versions.toml` y genera accesos tipados: `androidx-core-ktx` pasa a escribirse `libs.androidx.core.ktx`.
5. Por cada línea `implementation(...)` busca las coordenadas en los repositorios declarados (`google()`, `mavenCentral()`), descarga el `.aar`/`.jar` y su descriptor, y los guarda en `~/.gradle/caches`.
6. Cada librería trae sus propias dependencias (transitivas). Si dos piden versiones distintas de la misma, se **resuelve el conflicto** (sección 5).
7. El resultado es el *classpath* con el que se compila y se ejecuta la app.

La primera sincronización es lenta porque rellena la caché de `~/.gradle`, que puede ocupar
muchos gigas. En el campus, donde la partición personal es pequeña, hay que mover esa caché a
`~/goinfre` (ver [`02-spike-3d.md`](02-spike-3d.md), sección 7).

### 3.4 Maven Central

**Maven Central** es un almacén público de librerías ya compiladas para Java y Kotlin, algo
parecido a un repositorio de paquetes de `apt`. No hay que confundirlo con **Apache Maven**,
que es otro sistema de construcción (competidor de Gradle) y que **no usamos**.

Cada librería se identifica con tres **coordenadas**:

```
io.github.sceneview : sceneview : 4.69.4
      grupo            artefacto     versión
```

Los repositorios que Gradle consulta se declaran en `settings.gradle.kts`. Los artefactos
llegan como `.aar` (librería Android) o `.jar`, acompañados de un descriptor (`.pom` y/o
`.module`) que enumera sus dependencias y las versiones que exigen. En la página de cada versión
en Maven Central, la pestaña **Dependencies** muestra ese descriptor de forma legible, y la
columna *scope* indica:

- `COMPILE`: la librería expone esa dependencia a quien la use (equivale a `api`).
- `RUNTIME`: la usa por dentro y solo viaja con la app (equivale a `implementation`).

---

## 4. Catálogo de versiones y BOM

### 4.1 Catálogo de versiones (`libs.versions.toml`)

Un único fichero con tres secciones:

```toml
[versions]       # nombres y números de versión
kotlin = "2.4.21"

[libraries]      # qué librerías hay, y a qué versión se enlazan
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }

[plugins]        # plugins de construcción
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

**Ventaja:** cada versión vive en un solo sitio y los scripts usan nombres tipados (`libs.…`).
**Alternativa:** escribir la versión directamente en cada `implementation("grupo:nombre:versión")`.
Funciona, pero con muchas dependencias se pierde el control de qué versión es cuál.

### 4.2 BOM (*Bill of Materials*)

Un **BOM** es un «paquete» especial que **no contiene código**: solo una lista de versiones
**probadas juntas** de muchas librerías de una misma familia. La analogía en C es una *release*
de distribución (una versión de Debian es un conjunto de paquetes que se sabe que funcionan
juntos) o un único `versions.h` mantenido por el autor.

Compose tiene decenas de librerías (`ui`, `foundation`, `runtime`, `animation`, `material3`…)
que deben mantenerse **coherentes entre sí**. Por eso Android publica el **Compose BOM**
(`androidx.compose:compose-bom`). Se usa así:

```kotlin
implementation(platform(libs.androidx.compose.bom))   // importa el BOM
implementation(libs.androidx.compose.ui)               // ← sin versión: la pone el BOM
```

Por eso en `libs.versions.toml` las entradas de Compose no llevan `version.ref`.

**`platform(...)` frente a `enforcedPlatform(...)`.** Dos formas de importar un BOM con
consecuencias distintas:

| Forma | Qué hace | Riesgo |
|-------|----------|--------|
| `platform(bom)` | Añade las versiones del BOM como **restricciones**. Si otra librería pide una versión **más alta**, gana la más alta | Mezcla de versiones si el BOM es más antiguo que lo que pide otra librería |
| `enforcedPlatform(bom)` | **Fuerza** las versiones del BOM, incluso rebajando las que piden otras librerías | Si el BOM es más antiguo que lo que necesita SceneView, rebaja Compose y puede causar fallos en ejecución |

Usamos `platform(...)` (es lo que genera la plantilla) y **alineamos el BOM con lo que pide SceneView**.

Cada versión del BOM tiene una tabla de correspondencia con las versiones de cada librería, en la
página «BOM to library version mapping» de Android Developers (hay un desplegable para elegir BOM).

---

## 5. Resolución de conflictos y lectura del árbol de dependencias

**Regla de Gradle:** cuando varias rutas piden versiones distintas de la misma librería, gana
**la versión más alta**, salvo que alguien la fije con una regla estricta.

Se inspecciona con dos comandos (desde `spike-3d/`):

```bash
# Con qué versión de Kotlin Gradle Plugin se construye el propio proyecto
./gradlew buildEnvironment | grep "kotlin-gradle-plugin" | sort -u

# Qué versiones de Compose entran realmente en la app
./gradlew :app:dependencies --configuration debugRuntimeClasspath \
  | grep -E "compose\.(ui:ui|foundation:foundation|material3:material3|runtime:runtime)[ :]" | sort -u
```

Cómo leer la salida:

| Marca | Significado |
|-------|-------------|
| `A:1.7.0 -> 1.12.1` | Alguien pidió 1.7.0 y Gradle la subió a 1.12.1. **Es normal**, no es un error |
| `(c)` | La versión viene de una **restricción** (*constraint*), por ejemplo del BOM |
| `(*)` | Este subárbol ya se mostró más arriba; Gradle no lo repite |

**Lo que hay que mirar** es la versión que queda **a la derecha** de las flechas: lo problemático
es que librerías de la misma familia acaben con versiones **distintas**, no que haya flechas.

Resultado real en nuestro proyecto:

- `kotlin-gradle-plugin:2.2.10 -> 2.4.21`, y el resto de piezas del plugin en 2.4.21.
- `ui`, `foundation` y `runtime` de Compose, todas en **1.12.1**.
- `material3` pasa de 1.3.1 a **1.4.0** (la versión que fija el BOM 2026.09.00).

---

## 6. Anatomía del proyecto generado

Ubicación: `spike-3d/` dentro del repositorio, solo en la rama `spike/3d`.

| Fichero | Qué contiene |
|---------|--------------|
| `settings.gradle.kts` | Repositorios (`google()`, `mavenCentral()`), nombre del proyecto y módulos (`:app`) |
| `build.gradle.kts` (raíz) | Plugins comunes, declarados con `apply false` |
| `app/build.gradle.kts` | Configuración del módulo: `compileSdk`, `minSdk`, opciones de Java, dependencias |
| `gradle/libs.versions.toml` | Catálogo de versiones |
| `gradle/wrapper/gradle-wrapper.properties` | Versión de Gradle (9.6.0) |
| `app/src/main/java/dev/knowledge82/spike3d/MainActivity.kt` | Punto de entrada de la app |

Detalles de `settings.gradle.kts` que conviene conocer:

- `repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)`: **los repositorios solo se pueden
  declarar en este fichero**. Si se pega un bloque `repositories { … }` en `app/build.gradle.kts`
  (muy habitual en tutoriales antiguos), la construcción falla.
- `foojay-resolver-convention`: plugin que permite a Gradle **descargar un JDK** si el proyecto
  pide una *toolchain*. Hoy no la usamos.
- En `pluginManagement`, los plugins de Kotlin se resuelven desde `gradlePluginPortal()`.

Detalles de `app/build.gradle.kts`:

- `compileSdk = 37`: contra qué API se compila. `minSdk = 24`: versión mínima de Android en la que
  se instala. `targetSdk = 37`: para qué versión se ha adaptado el comportamiento.
- `compileOptions`: versión de Java (pasa de 11 a **21**). Con el Kotlin integrado de AGP 9,
  el `jvmTarget` de Kotlin toma por defecto el valor de `targetCompatibility`
  (según la guía oficial «Migrate to built-in Kotlin»).
- **No hay plugin `kotlin-android`.** Desde AGP 9.0, Kotlin viene integrado y ese plugin ya no se aplica.
- `implementation(platform(libs.androidx.compose.bom))`: importa el BOM (sección 4.2).

---

## 7. Decisiones y versiones (ADR-001, aceptado con reservas)

**Estado: ACEPTADO CON RESERVAS (15/11/2026).**
Plantilla de la sección 9 de [`02-spike-3d.md`](02-spike-3d.md).

**Decisión:** el proyecto usa **Kotlin + Jetpack Compose + SceneView 4.69.4** (Filament). El plan B (`GLSurfaceView` con OpenGL ES) queda como alternativa
si alguna reserva se convierte en fallo.

### Resultado de las hipótesis

| Hipótesis | Resultado | Evidencia y reservas |
|-----------|-----------|----------------------|
| H1 · Compila y arranca | **Pasa** | Galaxy A33; versiones alineadas y verificadas con `buildEnvironment` y `dependencies` |
| H2 · Esferas, cilindros orientados y luces | **Pasa** | Esferas, enlaces sobre los ejes y en diagonal (7 enlaces, 4 ángulos de cámara), colores CPK, luces. Doc. 02a, 9.8 a 9.10 |
| H3 · Girar, acercar, tocar | **Pasa en lo funcional** | El toque identifica el átomo y lo resalta; la zona sensible por defecto (AABB) es grande. Se aplicó la forma de colisión esférica (commit `a1427d7`); **no se registró una comparación cuantitativa** antes/después. Doc. 02b |
| H4 · Captura a imagen | **No probada** | Pasa a ser la primera tarea del proyecto real (la alternativa habitual es copiar la superficie con `PixelCopy`; sin verificar) |
| H5 · Ciclo de vida | **Pasa con reserva** | El usuario informó de que giro de pantalla, segundo plano y bloqueo no dieron fallos; en el registro solo hubo mensajes del sistema (`perf_hint`, `WindowManager`). **Pendiente de verificar el caso más duro: «No conservar actividades» (*Don't keep activities*), que recrea la Activity** |
| H6 · Rendimiento (~200 átomos) | **No probada** | Pasa a ser tarea del proyecto real; si falla: menos segmentos por esfera, esferas impostoras o plan B |

**Desviación respecto al criterio escrito.** El documento 02 dice «SÍ: H1, H2, H3 y H5 pasan, y H4 y H6 **no tienen un problema grave**».
Aquí H4 y H6 **no se han probado** y H5 está incompleta, por decisión del equipo para no retrasar el proyecto. Se acepta con estas reservas y se
**compromete su comprobación antes de dar por buena la vista 3D** (no antes de la defensa):

1. **«No conservar actividades»** (H5): prueba obligatoria antes de cualquier demo. Es el caso que más se parece a que Android mate el proceso en segundo plano, y un fallo ahí es un fallo en la evaluación.
2. **H4** (captura) antes de implementar el botón de compartir.
3. **H6** (rendimiento) en cuanto se cargue un ligando real grande.

**Qué fallo cambiaría la decisión:** un cierre inesperado al recrear la Activity o un rendimiento inservible con ligandos reales. En ese caso se evalúa el plan B
(la capa 3D está pensada para que se pueda cambiar sin tocar el resto: recibe un modelo de datos y devuelve eventos de selección).

| Pieza | Valor | Origen / motivo |
|-------|-------|-----------------|
| SceneView | **4.69.4** | Es la versión que describe la documentación que hemos leído (`nodes` y `cheatsheet`). Existe la 4.69.5 (publicada el 13-11-2026); no pudimos leer su registro de cambios |
| Android Gradle Plugin | 9.4.1 | Generado por la plantilla; cumple el mínimo 9.4.0 que indica el README de SceneView |
| Gradle | 9.6.0 | Generado por la plantilla |
| Kotlin | **2.4.21** | SceneView 4.69.x se publica con `kotlin-stdlib` 2.4.21 (visto en Maven Central) |
| Compose BOM | **2026.09.00** (Compose 1.12.1) | SceneView depende de `ui` y `foundation` 1.12.1; el BOM 2026.09.00 las fija en 1.12.1 |
| Java (objetivo) | **21** | El README de SceneView indica JVM 21 |
| compileSdk / minSdk | 37 / 24 | compileSdk 37 y minSdk 24 o superior, según el README de SceneView |

### Alternativas consideradas para la versión de SceneView

| Opción | Por qué no |
|--------|------------|
| 3.2.0 (la que muestra su *quickstart*) | Documentación claramente anticuada frente a la línea 4.x |
| 4.22.0 (la que muestra una página de Maven Central) | Era una vista antigua; no es la actual |
| 4.65.0 (README en GitHub) | Es la que lista los requisitos, pero hay versiones posteriores |
| 4.69.5 (la más reciente vista) | Salió el día anterior; sin registro de cambios, no sabemos qué cambió |
| Versión dinámica (`+`, `latest.release`) | Construcciones **no reproducibles**: hoy compila y mañana no |

**Lección:** SceneView publica varias versiones por día y su documentación va por detrás
(vimos 3.2.0, 4.22.0, 4.65.0 y 4.69.4 en cuatro sitios distintos). Por eso **fijamos una versión
concreta** y solo la cambiamos de forma consciente, anotándolo aquí.

### Qué se comprobó y cómo

| Afirmación | Fuente | Estado |
|------------|--------|--------|
| SceneView necesita AGP 9.4+, Kotlin 2.4, JVM 21, compileSdk 37 | README de SceneView (una sola fuente) | Parcialmente confirmado: Maven Central muestra `kotlin-stdlib` 2.4.21 y Compose 1.12.1 como dependencias |
| El Kotlin del proyecto es 2.4.21 | `buildEnvironment` | Confirmado |
| Compose queda alineado en 1.12.1 | `dependencies` | Confirmado |
| Las clases de SceneView compilan con nuestro Kotlin | Compilación del paso 2 | Confirmado |
| La app arranca en el A33 con estas versiones | Ejecución en el dispositivo | Confirmado (H1) |

---

## 8. Registro de lo que hicimos

**Paso 0.** Se creó el proyecto desde la plantilla *Empty Activity* de Android Studio, dentro de
`spike-3d/`, con paquete `dev.knowledge82.spike3d`, Kotlin y minSdk 24. En el móvil se activaron las
opciones de desarrollador y la depuración por USB; `adb devices` mostró el dispositivo como
`device`. La app vacía se ejecutó y mostró «Hello Android!». Solo había cambios sin confirmar en
`spike-3d/`.

**Paso 1a.** Leímos `libs.versions.toml`, `app/build.gradle.kts`, `build.gradle.kts` raíz y
`gradle-wrapper.properties`. La plantilla traía AGP 9.4.1, Gradle 9.6.0, Kotlin 2.2.10,
Compose BOM 2026.02.01, compileSdk 37, minSdk 24 y Java 11.

**Paso 1b.** Contraste con los requisitos de SceneView: coincidían compileSdk, minSdk y AGP;
**faltaban Kotlin (2.2.10 frente a 2.4) y Java (11 frente a 21)**. Se añadieron al catálogo la
versión y la librería de SceneView, `implementation(libs.sceneview)` en el módulo, y se subió
`compileOptions` a Java 21.

**Paso 1c.** Se subió `kotlin` en el catálogo a 2.4.21. Comprobación:

```
org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.10 -> 2.4.21
```

El `2.2.10` de la plantilla coincide con el Kotlin que AGP 9 pide por defecto (es una
inferencia nuestra). Gradle toma el más alto, de modo que **una sola línea del catálogo cambia
la versión de Kotlin de todo el proyecto**.

**Paso 1d.** En Maven Central, la pestaña *Dependencies* de SceneView mostró `kotlin-stdlib` 2.4.21
(COMPILE) y `androidx.compose.ui` / `foundation` 1.12.1 (RUNTIME). El Compose BOM de 2026.02.01
quedaba por debajo, así que se buscó en la tabla de correspondencias el BOM que fija Compose en 1.12.1:
**2026.09.00** (el BOM 2026.08.00 es el que introdujo Compose 1.12, según el blog oficial de Android).
Se actualizó `composeBom` y la comprobación mostró `ui`, `foundation` y `runtime` en 1.12.1.

**Notas de método:**

- No se pudo leer el registro de cambios de SceneView ni su API de referencia por completo (algunas
  páginas no eran accesibles desde el entorno del asistente), así que **las firmas de la API se
  confirmarán en el IDE** y no se han dado por válidas desde la documentación.
- Las capturas de Maven Central las aportó el equipo.

---

## 9. Pasos 2 y 3: esfera, enlaces y cámara

**Estado: hecho.** El código, escrito a partir de la documentación de SceneView 4.65-4.69, compiló y se
ejecutó en el A33 una vez añadidos los imports (resultado real en 9.6).

### 9.1 Fichero nuevo `MoleculeScene.kt`

Ruta: `app/src/main/java/dev/knowledge82/spike3d/MoleculeScene.kt`

```kotlin
package dev.knowledge82.spike3d

// SceneView imports: let Android Studio add them (Alt+Enter on each red name):
//   SceneView, rememberEngine, rememberMaterialLoader, rememberCameraManipulator, SphereNode

@Composable
fun MoleculeScene(modifier: Modifier = Modifier) {
    val engine = rememberEngine()
    val materialLoader = rememberMaterialLoader(engine)
    val gray = remember(materialLoader) {
        materialLoader.createColorInstance(Color(0.56f, 0.56f, 0.56f, 1f))
    }

    SceneView(
        modifier = modifier.fillMaxSize(),
        engine = engine,
        cameraManipulator = rememberCameraManipulator(),
    ) {
        SphereNode(radius = 0.5f, materialInstance = gray)
    }
}
```

(El fichero entregado al equipo incluye además los imports de Compose y de Android, que sí son seguros.)

### 9.2 Cambio mínimo en `MainActivity.kt`

Dentro de `setContent`, el `Scaffold` llamaba a `Greeting`; ahora llama a `MoleculeScene`:

```diff
-                    Greeting(
-                        name = "Android",
-                        modifier = Modifier.padding(innerPadding)
-                    )
+                    MoleculeScene(
+                        modifier = Modifier.padding(innerPadding)
+                    )
```

`Greeting` y su `@Preview` se quedan sin tocar (siguen compilando aunque no se usen). `MoleculeScene` está
en el mismo paquete, así que no hace falta ningún import nuevo.

### 9.3 Explicación línea a línea

| Línea | Qué hace | Por qué |
|-------|----------|---------|
| `rememberEngine()` | Crea el motor 3D (Filament) y ata su ciclo de vida al de la pantalla | Al salir el composable, el motor se libera: evita fugas de memoria, un requisito del enunciado |
| `rememberMaterialLoader(engine)` | Cargador de materiales | Hace falta para crear colores |
| `remember(materialLoader) { createColorInstance(...) }` | Crea **un** material de color gris | Sin `remember`, se crearía un material nuevo en cada redibujado (fuga) |
| `SceneView(...) { ... }` | La vista 3D; dentro de las llaves se declaran los nodos de la escena | Es la API declarativa de SceneView |
| `rememberCameraManipulator()` | Cámara orbital lista para usar | Giro, zoom y desplazamiento con los dedos sin escribir código |
| `SphereNode(radius = 0.5f, materialInstance = gray)` | Esfera gris de radio 0,5 en el origen | Primer «átomo» de prueba |

### 9.4 Resultado esperado y diagnóstico

- **Esperado:** una esfera gris que se puede girar con un dedo y acercar con el pellizco.
- **Pantalla negra o esfera plana como un disco:** probablemente falta iluminación (es el paso 4); no es un fallo.
- **Errores de compilación por nombres o parámetros:** usar las herramientas del IDE para leer la API real
  (sección 11) y anotar las diferencias frente a la documentación.

### 9.5 Lo que la documentación nos adelanta (sin comprobar aún)

- `SphereNode(radius, center, stacks, slices, materialInstance, …)`; `CylinderNode(radius, height, center, sideCount, materialInstance, …)`
  (en el cilindro, `radius` y `height` son obligatorios).
- Los colores se aplican con un **material** (`createColorInstance`), no con un parámetro de color.
- Los toques sobre nodos se escuchan con `onGestureListener = rememberOnGestureListener(onSingleTapConfirmed = { event, node -> … })`.
- Las luces son nodos: `LightNode(type = …, apply = { intensity(…) })`.
- **Giros en grados de Euler:** `rotation = Rotation(x, y, z)`. Nuestro cálculo para orientar cilindros (documento 02, sección 5.4)
  produce **eje y ángulo**; habrá que ver si el nodo admite eje-ángulo o cuaternión, o convertirlo.

### 9.6 Resultado real (15/11/2026)

- **Compiló y se ejecutó en el A33.** Los nombres y parámetros usados (`SceneView(modifier, engine, cameraManipulator) { … }`,
  `SphereNode(radius, materialInstance)`, `rememberEngine`, `rememberMaterialLoader`, `rememberCameraManipulator`,
  `createColorInstance`) **coincidían con la documentación**; solo faltaban los imports.
- **Los seis errores iniciales eran en realidad uno solo más una cascada:** cinco `Unresolved reference` de nombres
  de SceneView y un `Cannot infer type for type parameter 'T'`, que era consecuencia de que `rememberMaterialLoader`
  no se resolvía. Los errores de compilación se leen **de arriba abajo**.
- **Imports:** en Linux, el atajo **Alt+Enter no hizo nada**, pero al **hacer clic** en la sugerencia de Android Studio
  el import se aplicaba. Causa sin confirmar (puede interceptarlo el entorno de escritorio; se puede revisar en
  *Settings, Keymap, «Show Context Actions»*). Imports reales: `io.github.sceneview.SceneView`, `io.github.sceneview.rememberEngine`,
  `io.github.sceneview.rememberMaterialLoader` e `io.github.sceneview.rememberCameraManipulator` (los cuatro en el paquete raíz
  `io.github.sceneview`). **`SphereNode` no necesita import**: se resuelve dentro de la lambda de `SceneView { … }`.
- **Imagen obtenida:** esfera gris con varios reflejos (varias luces por defecto) que gira al arrastrar. Fondo negro.
- **Facetas visibles en el contorno.** Una esfera 3D es siempre una malla de triángulos; con pocos segmentos el contorno
  se ve poligonal aunque el sombreado sea suave. `SphereNode` tiene los parámetros `stacks` y `slices`; la documentación
  sugiere 8/8 para «poca resolución» y 32/32 para «suave», y avisa de que cambiarlos **reconstruye la malla**.
  Coste aproximado: unos `stacks × slices × 2` triángulos por esfera (32 × 32 ≈ 2000). Con muchos átomos habrá que
  ajustar los segmentos al tamaño de la molécula (relacionado con H6 y con el bonus VII.4).
- **Encuadre inicial (comprobado arrancando sin tocar la pantalla):** la cámara por defecto queda **centrada pero muy
  cerca del origen**; la molécula de dos átomos no cabe a lo ancho hasta que se aleja con el pellizco. (La primera captura
  de la esfera no permitía concluirlo porque ya se había hecho zoom; esa lectura apresurada se retiró y esta es la
  comprobada.) La cámara inicial se fijará según la sección 5.3 de [`02-spike-3d.md`](02-spike-3d.md); ver 9.9.
- **Confirmado después:** giro con un dedo, zoom con pellizco y desplazamiento con dos dedos. **Sin comprobar todavía:**
  toque sobre un átomo.

**Alternativa a las esferas de malla:** los visores moleculares profesionales dibujan «esferas impostoras»: un cuadrado
en el que el *fragment shader* calcula una esfera perfecta, con muy pocos triángulos. Exige escribir un material propio,
así que es más natural en el plan B (OpenGL ES) que con SceneView; de momento no la necesitamos.

### 9.7 Parámetros reales de `SphereNode` (leídos en el IDE con Ctrl+P)

```
radius: Float = Sphere.DEFAULT_RADIUS
materialInstance: MaterialInstance? = null
center: Position = Sphere.DEFAULT_CENTER
stacks: Int = Sphere.DEFAULT_STACKS
slices: Int = Sphere.DEFAULT_SLICES
position: Position = Position(x = 0f)
rotation: Rotation = Rotation(x = 0f)
scale: Scale = Scale(1f)
apply: SphereNode.() -> Unit = {}
content: @Composable (NodeScope.() -> Unit)? = null
```

Conclusiones:

- Coincide con la documentación. Los parámetros de transformación son `position`, `rotation` y `scale`.
- **No hay parámetro de cuaternión.** El giro por parámetro es `rotation`, en ángulos de Euler (grados). Si el nodo admite
  cuaternión o `lookAt`, habrá que buscarlo dentro del bloque `apply`: pendiente de comprobar en el IDE (Ctrl+Espacio dentro de `apply = { }`).
- `content` permite **nodos hijos**, útil para agrupar un enlace bajo un nodo pivote.
- Los giros sobre **un solo eje** (por ejemplo `Rotation(z = 90f)`) no dependen del orden de los ejes de Euler; los giros
  en diagonal sí. Por eso el paso 3 empieza con enlaces alineados con un eje.
- `CylinderNode` comparte los parámetros de transformación de `SphereNode` (`position`, `rotation`): **comprobado** en el paso 3.

### 9.8 Paso 3: enlaces (cilindros) y colores CPK (15/11/2026)

**3a. Dos átomos y un enlace.** Esferas con `radius = 0.4f, stacks = 32, slices = 32` en `x = ±0.75` (gris y rojo) y un
`CylinderNode(radius = 0.12f, height = 1.5f, rotation = Rotation(z = 90f))` entre ambas. **Resultado: correcto.**

- El eje por defecto del cilindro es **Y** (confirmado a la vista: sin giro queda vertical). Para tumbarlo sobre X basta
  `Rotation(z = 90f)`.
- Con 32/32 las facetas del contorno dejan de notarse.
- Colores CPK correctos para C (gris) y O (rojo). Los enlaces son más finos que los átomos, como pide el modelo *ball-and-stick*.

**3b. «Trípode»: 1 átomo central y 3 átomos rojos, uno por eje.** Esferas en `(0,0,0)`, `(1.5,0,0)`, `(0,1.5,0)` y `(0,0,1.5)`;
cilindros de longitud 1,5 centrados en el punto medio de cada enlace (`0.75` sobre el eje): eje X con `Rotation(z = 90f)`,
eje Y sin giro, eje Z con `Rotation(x = 90f)`. **Resultado: correcto** (foto del 15/11 a las 13:02: tres enlaces que parten del
átomo central hacia cada átomo rojo).

Qué demuestra y qué no:

- **Demuestra:** posición y giro de cilindros sobre los tres ejes principales; que el centro del cilindro es su punto medio
  (por eso se coloca en el medio del enlace); que la convención de ejes coincide con la de nuestro documento 02.
- **No demuestra todavía:** enlaces **en diagonal**. Con giros de un solo eje el orden de Euler no importa; con un enlace
  arbitrario sí, y es el caso real de cualquier ligando. Es **la** pregunta técnica abierta de H2.
- Además, la foto es de **un solo punto de vista**: un enlace Z visto de frente puede parecer correcto aunque estuviera mal
  orientado. Hay que girar la escena y comprobar que los tres enlaces siguen uniendo los átomos desde cualquier ángulo.

### 9.9 Cámara inicial: firmas reales de `rememberCameraManipulator`

Leídas en el IDE (Ctrl+P). Hay cuatro sobrecargas:

```
(orbitRadius: Float, targetPosition: Position? = null, inertia: Inertia = Inertia.Default, key: Any? = null, creator)
(orbitRadius: Float, targetPosition: Position? = null, inertia: Inertia = Inertia.Default, creator)        // sin key
(orbitHomePosition: Position? = null, targetPosition: Position? = null, inertia: Inertia = Inertia.Default, key: Any? = null, creator)
(orbitHomePosition: Position? = null, targetPosition: Position? = null, inertia: Inertia = Inertia.Default, creator)  // sin key
```

- `orbitRadius` + `targetPosition` equivale a nuestra cámara orbital del documento 02: distancia al objetivo y punto que se mira.
  Es la opción más directa: `rememberCameraManipulator(orbitRadius = d, targetPosition = Position(0f, 0f, 0f))`.
- `orbitHomePosition` fija la posición exacta de partida de la cámara (útil si queremos un ángulo inicial distinto del frontal).
- `key`: **inferencia, sin comprobar**: probablemente fuerza a recrear el manipulador cuando cambia (por ejemplo, al cargar otra
  molécula con otro radio). Se comprobará leyendo la documentación (Ctrl+Q) o probándolo.
- La distancia correcta depende del radio de la molécula y del campo de visión (documento 02, sección 5.3: `d = r / sin(FOV/2)`
  con el FOV **menor**, que en vertical es el horizontal). Asumimos un FOV por defecto que **no hemos leído**; el 9,5 de la
  primera prueba es un valor de partida a ajustar a ojo, no una fórmula verificada.

### 9.10 Enlaces en diagonal: lo que falló, por qué y la solución (15/11/2026)

**Intento A (falló): cuaternión en `apply`.** Se calculó con una función propia (arco más corto: `normalizar(Y × d, 1 + Y·d)`,
verificada en Python con 100 000 direcciones) y se asignó dentro de `apply = { quaternion = q }`. Resultado: los cilindros
diagonales quedaron **verticales y flotando** en el punto medio del enlace, con la longitud correcta. Diagnóstico por la imagen:
posición y longitud bien, **giro no aplicado**.

**Causa (confirmada leyendo el código fuente del tag `v4.69.4`, no la documentación).** En `SceneScope.kt`, el composable
`CylinderNode` (y `Node` igual) hace:

```kotlin
val node = remember(engine) { CylinderNodeImpl(...).apply(apply) }   // 1) apply: UNA vez, al crear
...
DisposableEffect(node, rotation.x, rotation.y, rotation.z) { node.rotation = rotation }  // 2) después, el parámetro
```

El parámetro `rotation` tiene valor por defecto `Rotation(0f)` y se escribe **después** de `apply`, así que lo borra. Lo mismo
ocurriría con `lookTowards(...)` dentro de `apply` (intento C, descartado sin probar). Regla: **en SceneView 4.69.4 lo que se
pone en `apply` se pisa con los parámetros `position`, `rotation` y `scale`**; la orientación debe viajar por parámetros.

**Solución (B, verificada): dos nodos anidados con giros de un solo eje.**

- Un `Node` pivote en el punto medio del enlace gira `spin` grados alrededor de Y.
- Dentro, el `CylinderNode` (centrado en el pivote) se inclina `tilt` grados alrededor de Z.
- `tilt = acos(dy/len)` y `spin = atan2(dz, -dx)` (coordenadas esféricas de la dirección del enlace).
- Como el mundo es `padre × hijo`, el orden de los giros lo fija la anidación y no hay ambigüedad de Euler.
- Casos límite sin división por cero: dirección `±Y` da `tilt` 0 o 180 y cualquier `spin`.
- Verificación numérica en Python (100 000 direcciones, error 1e-14, asumiendo giros dextrógiros) y **visual en el A33**:
  7 enlaces (3 sobre los ejes, 4 diagonales incluido `(0,-1.5,0)`), ninguno flotando desde 4 ángulos. Un signo equivocado
  habría dejado el cilindro de `(1,1,0)` cruzado por su punto medio, sin llegar al átomo; no se observó.

**Longitud.** `height` es la **altura total** (`Cylinder.kt`: «Total height», se reparte `height/2` a cada lado del centro). Con la
longitud igual a la distancia entre centros y el pivote en el punto medio, los extremos caen exactamente en los centros de los
átomos. La parte visible es `longitud − 2·radio del átomo`.

### 9.11 Lo que dice el código fuente de SceneView 4.69.4 (verificado)

Se clonó el tag `v4.69.4` (commit `5bfe25d`) y se leyó. Esto sustituye a suposiciones anteriores:

- **Ángulos de `rotation`:** grados, **orden ZYX**; el getter es `quaternion.toEulerAngles()` y el setter `Quaternion.fromEuler(value)`.
- **`position` y `rotation` de un nodo son locales** respecto a su padre (hay `worldPosition`/`worldRotation` aparte). La documentación
  de los composables dice «World-space», lo cual **induce a error** dentro de un `Node { }`.
- **`Node(position, rotation, scale, isVisible, isEditable, apply, content)`** es un miembro de `SceneScope`; su `content` recibe un
  `NodeScope` (donde también están `SphereNode`, `CylinderNode`…). No necesita import.
- **`Node.quaternion`** existe y es asignable; **`lookAt`** (a un nodo o a una posición mundial) y **`lookTowards(dirección)`** existen
  y orientan el eje «adelante». Ambos tienen `smooth` (por defecto `isSmoothTransformEnabled`) y **no son útiles dentro de `apply`**
  por lo explicado en 9.10.
- **Gestos:** `SceneView(onGestureListener = rememberOnGestureListener(onSingleTapConfirmed = { e, node -> … }))`; `node` es el nodo
  impactado o `null`. También existe `SceneView(onTouchEvent = { e, hitResult -> … })` con prioridad absoluta.
- **Detección de toques (riesgo para H3):** `collisionSystem.hitTest(event)` usa la forma de colisión del nodo, y para los nodos
  renderizables se **deriva de la caja envolvente alineada con los ejes (AABB)**, no de la esfera. Con esferas de radio `r`, la
  zona sensible es un **cubo de lado `2r`**: tocar cerca de una esquina del cubo, fuera de la esfera visible, también «acierta», y
  con átomos solapados en pantalla puede elegirse el átomo equivocado. Hay que **medirlo en el dispositivo** antes de decidir si
  basta o si hace falta una selección propia por rayo-esfera (documento 02).
- **Ciclo de vida:** el `DisposableEffect` de `NodeLifecycle` hace `attach`/`detach` y `node.destroy()` al salir de la composición.

### 9.12 Lecciones del proceso

- El autocompletado dentro de los **paréntesis de una llamada** muestra funciones globales, no miembros del nodo; los miembros se buscan
  con **Ctrl+clic** en un miembro conocido (abre la clase) y **Alt+7** (*Structure*), no con Ctrl+F12 (interceptado en su Linux).
- Cuando una API cambia a diario, **se lee el código del tag exacto** (`git clone --depth 1 --branch vX.Y.Z`), no la documentación.
  Un repositorio público se puede clonar aunque `WebFetch` o `gh` estén restringidos.
- Un parche que «parece aplicarse» puede estar siendo pisado: comprobar el **orden** en que la librería aplica `apply` y parámetros.

---

## 10. Riesgos y trampas

| Riesgo | Efecto | Mitigación |
|--------|--------|------------|
| SceneView publica versiones casi cada hora y su documentación va retrasada | Código que no coincide con la versión | Fijar versión; consultar la API **en el IDE** |
| Mezcla de versiones de Compose | Fallos en ejecución (`NoSuchMethodError`) | Alinear el BOM; verificar con `dependencies` |
| Usar `enforcedPlatform` con un BOM antiguo | Rebaja Compose por debajo de lo que pide SceneView | Mantener `platform(...)` |
| Kotlin más antiguo que el de la librería | El compilador rechaza leer sus clases («incompatible version of Kotlin») | Kotlin ≥ 2.4. Regla general de Kotlin: solo se garantiza leer metadatos de la misma versión menor o la siguiente (no verificada en este proyecto) |
| Bloques `repositories { }` en `build.gradle.kts` | Falla la construcción por `FAIL_ON_PROJECT_REPOS` | Declarar repositorios solo en `settings.gradle.kts` |
| Gradle sin JDK 21 | Error del tipo `invalid source release: 21` | Ajustar *Gradle JDK* en Android Studio (Settings, Build Tools, Gradle) |
| Caché de Gradle en el campus | Llena la partición personal | Mover `GRADLE_USER_HOME` y `ANDROID_HOME` a `~/goinfre` |
| Librerías antiguas de la plantilla (`activity-compose` 1.8.0, `lifecycle-runtime-ktx` 2.6.1, `core-ktx` 1.10.1) | Posibles avisos o subidas automáticas por dependencias transitivas | De momento no se tocan; se revisarán si dan problemas |

---

## 11. Diagnóstico

| Síntoma | Qué comprobar |
|---------|---------------|
| `Could not find io.github.sceneview:sceneview:…` | Versión escrita con exactitud; `mavenCentral()` en `settings.gradle.kts`; conexión |
| Error de sincronización con `plugin ... not found` | Versión del plugin en el catálogo; `gradlePluginPortal()` en `pluginManagement` |
| `invalid source release: 21` | *Gradle JDK* a 21 en los ajustes de Android Studio |
| Error «incompatible version of Kotlin» al compilar | `buildEnvironment`: ¿está Kotlin en 2.4.x? |
| Pantalla negra al arrancar la escena | Cámara dentro del objeto, falta de luz, nodos sin añadir a la escena (ver `02-spike-3d.md`, sección 8) |
| Alt+Enter no hace nada en Linux | Puede interceptarlo el entorno de escritorio; usar el clic en la sugerencia de Android Studio, o revisar el atajo en Keymap |
| Quieres ver las firmas de una API en el IDE | **Ctrl+P** dentro de los paréntesis (parámetros), **Ctrl+Q** sobre el nombre (documentación), **Ctrl+clic** (código fuente), **Alt+Enter** (imports) |

---

## 12. Pendiente

1. ~~Subir `MoleculeScene.kt` a `spike/3d`~~ (hecho el 15/11 con el commit de todo el grupo de ficheros sin versionar; incluyó también
   `.idea/inspectionProfiles/Project_Default.xml`, que sobra: se puede quitar con `git rm --cached`).
2. Commit del `MoleculeScene.kt` actual (nodos anidados y 7 enlaces) en `spike/3d`.
3. ~~H3, toque sobre un átomo~~: hecho con la detección integrada (zonas grandes); decisión D8 y alternativas en [`02b-spike-seleccion-atomos.md`](02b-spike-seleccion-atomos.md).
4. **Cerrado el spike con reservas (sección 7).** Pendientes, ya dentro del proyecto real: H5 con «No conservar actividades», H4 (captura) y H6 (rendimiento).
5. Registrar versión de Android Studio y versión de Android del A33 en el README.
6. Archivar `spike/3d` con la etiqueta `spike-3d-final` (**sin fusionarla**: contiene una copia antigua de `README.md` y `docs/`) y crear el proyecto real en `main`.

### Hallazgos sobre el repositorio (15/11/2026, rama `spike/3d`)

- **Higiene correcta:** no hay `build/`, `.gradle/` ni `local.properties` versionados; el `.gitignore` es el estándar de Android Studio.
- `spike-3d/.idea/*` **sí** está versionado (valor por defecto de la plantilla). Para el proyecto real conviene revisar qué ficheros de
  `.idea/` aportan algo al equipo (suelen sobrar casi todos y generan conflictos entre dos desarrolladores).
- `spike-3d/gradle/gradle-daemon-jvm.properties` está versionado: lo genera `updateDaemonJvm` y fija `toolchainVersion=25` con
  descargas vía *foojay* para cada sistema. Es relevante para el campus: sin permisos de administrador, Gradle descargará su propio
  JDK. Conviene decidir si el proyecto real lo conserva (JDK 25 para el demonio y Java 21 como objetivo de compilación son dos cosas
  distintas, y es fácil confundirlas).

---

## 13. Qué recordar

- **Gradle** es «make + gestor de paquetes»; **Maven Central** es el almacén de librerías, y cada una se identifica con `grupo:artefacto:versión`.
- Las versiones viven en `libs.versions.toml` y se usan como `libs.<nombre>`.
- Un **BOM** es una lista de versiones probadas juntas; con `platform(...)` Gradle las trata como recomendaciones, y la versión **más alta** gana.
- Una flecha `->` en el árbol de dependencias es una subida normal de versión; hay que mirar lo que queda a su derecha.
- Con AGP 9 **Kotlin va integrado**: no existe `kotlin-android`, y la línea `kotlin` del catálogo cambia el Kotlin de todo el proyecto.
- Las versiones se **verifican con comandos** (`buildEnvironment`, `dependencies`), no se dan por buenas.
- SceneView cambia muy deprisa: **versión fija** y firmas de la API leídas en el IDE.
- Que la sincronización pase y que el código compile y arranque en el móvil son **tres pruebas distintas**.
- Los errores de compilación se leen **de arriba abajo**: los últimos suelen ser consecuencia de los primeros.
- Una esfera 3D es una malla de triángulos: la suavidad se compra con más segmentos (`stacks`/`slices`) y cuesta memoria.
- La cámara inicial por defecto **no encuadra** nuestra escena (arranca centrada pero muy cerca): hay que fijarla nosotros con `orbitRadius`.
- El cilindro de SceneView tiene el eje **Y** por defecto; sus giros se dan en grados de Euler y solo los giros de un eje son inequívocos.
- Un spike se da por cerrado por **criterios escritos de antemano**, no por sensación: lo que queda abierto son los casos que pueden hundir el proyecto (diagonal, toque, ciclo de vida).
- Antes de decir «está en el repositorio», **se mira el repositorio**.
- En SceneView 4.69.4 **`apply` se ejecuta una vez y los parámetros `position`/`rotation`/`scale` se escriben después**: la orientación debe ir por parámetros (nodos anidados), no en `apply`.
- `height` de `CylinderNode` es la longitud total; los giros de `rotation` son Euler ZYX en grados y son **locales** al padre.

---

## 14. Glosario

- **AGP (Android Gradle Plugin):** plugin de Gradle que sabe construir aplicaciones Android.
- **BOM (*Bill of Materials*):** lista de versiones de librerías probadas juntas, sin código propio.
- **Catálogo de versiones:** fichero `libs.versions.toml` con versiones, librerías y plugins.
- **Coordenadas:** `grupo:artefacto:versión`, identificador de una librería en un repositorio.
- **Dependencia transitiva:** librería que no pedimos directamente, pero necesita otra que sí pedimos.
- **Gradle Wrapper:** script `gradlew` que descarga la versión exacta de Gradle del proyecto.
- **Maven Central:** almacén público de librerías Java y Kotlin.
- **Restricción (*constraint*):** versión recomendada por un BOM o una librería; no impide que otra pida una más alta.
- **Sincronización (*sync*):** paso en el que Android Studio ejecuta Gradle para resolver plugins y dependencias.
- **Toolchain:** JDK que Gradle usa para compilar, gestionado de forma explícita.
