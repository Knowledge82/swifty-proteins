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
7. [Decisiones y versiones (ADR-001, borrador)](#7-decisiones-y-versiones-adr-001-borrador)
8. [Registro de lo que hicimos](#8-registro-de-lo-que-hicimos)
9. [Paso 2: primera esfera](#9-paso-2-primera-esfera)
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

Hipótesis de [`02-spike-3d.md`](02-spike-3d.md): H1 cumplida. H2 y H3 parcialmente (esfera, luces,
giro, zoom con pellizco y desplazamiento confirmados; faltan el cilindro y el toque sobre el átomo). H4 a H6 sin probar.

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

## 7. Decisiones y versiones (ADR-001, borrador)

Este es el borrador de la plantilla de la sección 9 de [`02-spike-3d.md`](02-spike-3d.md).
**No está cerrado**: faltan H2 a H6.

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

## 9. Paso 2: primera esfera

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
- **Encuadre inicial: sin conclusión.** En la captura la esfera llena el ancho de la pantalla, pero el usuario ya había
  hecho zoom, así que no se puede deducir cómo encuadra la cámara por defecto (una primera lectura apresurada decía lo
  contrario y se descartó). Se comprobará arrancando la app sin tocar la pantalla; en cualquier caso, la cámara inicial se
  fijará según la sección 5.3 de [`02-spike-3d.md`](02-spike-3d.md).
- **Confirmado después:** giro con un dedo, zoom con pellizco y desplazamiento con dos dedos. **Sin comprobar todavía:**
  toque sobre la esfera.

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
- Se asume (sin comprobar) que `CylinderNode` comparte los parámetros de transformación de `SphereNode`.

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

1. Confirmar el `git commit` y `git push` de la rama `spike/3d` (con `MoleculeScene.kt` y el cambio en `MainActivity.kt`).
2. Anotar los imports de `Position`, `Rotation` y `CylinderNode` cuando aparezcan en el paso 3.
3. Subir la resolución de la esfera (`stacks`/`slices`) y comprobar el encuadre inicial de la cámara (arrancar sin tocar
   la pantalla); fijarla si hace falta.
4. Pasos 3 y 4 del spike: dos átomos y un enlace (cilindro), colores y luces.
5. Comprobar zoom con pellizco, desplazamiento y toque sobre un átomo.
6. Registrar versión de Android Studio y versión de Android del A33 en el README.
7. Cerrar el ADR-001 cuando se completen H2 a H6.

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
- La cámara inicial por defecto **no encuadra** nuestra escena: hay que fijarla nosotros.

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
