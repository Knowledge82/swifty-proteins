# Swifty Protein

Visor 3D de ligandos (moléculas pequeñas) para Android, escrito en Kotlin.
Proyecto en pareja del bloque **Mobile** de 42 Barcelona.

> **Estado actual:** *spike* 3D **cerrado con reservas** (ADR-001, ver
> [`docs/02a-spike-gradle-dependencias.md`](docs/02a-spike-gradle-dependencias.md), sección 7). El proyecto Android ya vive en la raíz
> de `main` (paquete `dev.knowledge82.swiftyproteins`). **Etapa en curso:** carga de ligandos. El parser de CIF está **completo** (88 pruebas, verificado
> contra los 1242 ligandos presentes en el diccionario) y la **capa de red** (descarga, errores, tiempos, cancelación y conversión a molécula) está **escrita y probada en la JVM** (42 pruebas más; sin probar en dispositivo ni contra RCSB real).
> También están escritos y probados en la JVM el **catálogo con búsqueda** (`ligands.txt`, sin distinguir mayúsculas) y el **cargador** que gobierna el estado de carga (`Idle / Loading / Loaded / Failed`, con la garantía de que el indicador siempre desaparece).
> Está escrito el **pegamento con Android** (leer los *assets*, `ViewModel`, lista con buscador, diálogos de carga y de error, pantalla provisional con los datos de la molécula): **sin compilar con el SDK de Android** (los textos de error, con 7 pruebas, sí están probados en la JVM; 162 pruebas en total).
> Falta la primera compilación y prueba en el móvil, el inicio de sesión, y la escena 3D con una molécula real. Documentos:
> [`docs/03-formato-cif-y-parser.md`](docs/03-formato-cif-y-parser.md) y [`docs/03a-codigo-parser.md`](docs/03a-codigo-parser.md) (parser),
> [`docs/06-red-y-carga-de-ligandos.md`](docs/06-red-y-carga-de-ligandos.md) y [`docs/06a-codigo-red.md`](docs/06a-codigo-red.md) (red),
> [`docs/06b-lista-busqueda-y-estado-de-carga.md`](docs/06b-lista-busqueda-y-estado-de-carga.md) (lista, búsqueda y estado de carga),
> [`docs/06c-pegamento-android-lista-y-carga.md`](docs/06c-pegamento-android-lista-y-carga.md) (pegamento con Android).
> Pendientes con compromiso del spike: H5 con «No conservar actividades», H4 (captura) y H6 (rendimiento).

---

## Qué es

La aplicación permite:

1. Iniciar sesión (usuario y contraseña, o biometría si el dispositivo la soporta).
2. Ver una lista de ligandos (leída de `ligands.txt`) con búsqueda en tiempo real.
3. Descargar el fichero `.cif` del ligando elegido desde RCSB, interpretarlo y
   mostrarlo en 3D con el modelo *ball-and-stick* y colores CPK.
4. Girar, acercar y tocar átomos, y compartir una captura de pantalla.

Los detalles del enunciado, qué es obligatorio y qué es bonus están en
[`docs/01-tarea-y-punto-de-partida.md`](docs/01-tarea-y-punto-de-partida.md).

---

## Decisiones tomadas hasta ahora

| # | Decisión | Estado | Motivo resumido |
|---|----------|--------|-----------------|
| D1 | Plataforma: **Android nativo, Kotlin** | Cerrada | La biometría, el almacén de claves, el ciclo de vida y el menú de compartir son APIs nativas, y el 3D multiplataforma disponible es inmaduro para un proyecto donde un fallo equivale a suspender. |
| D2 | Formato de datos: **`.cif`** de RCSB | Cerrada | Es el formato que pide el enunciado y escribir el parser es una parte central del aprendizaje. |
| D3 | Interfaz: **Jetpack Compose** | Cerrada | Muchos estados (login, carga, error, tema oscuro). Versiones alineadas, H1 confirmada en el dispositivo y escena 3D integrada sin fricción con Compose. |
| D4 | Capa 3D: **SceneView 4.69.4 (Filament)** | Aceptada con reservas | H1 a H3 pasan y H5 pasa con reserva; H4 (captura) y H6 (rendimiento) sin probar, con compromiso de comprobarlas (ADR-001). Plan B: `GLSurfaceView` con OpenGL ES. |
| D5 | Cuentas de usuario: **almacenamiento local** con hash de contraseña | Provisional | Menos dependencias externas y más aprendizaje que Firebase. |
| D6 | Idioma de README y `docs/`: **español de España** | Cerrada | Ambos miembros del equipo lo leen. |
| D7 | Versiones fijadas del spike: SceneView 4.69.4, Kotlin 2.4.21, AGP 9.4.1, Compose BOM 2026.09.00, Java 21 | Provisional | SceneView publica a diario y exige Kotlin 2.4, Compose 1.12.1 y Java 21; se fija una versión concreta. Detalle en [`docs/02a-spike-gradle-dependencias.md`](docs/02a-spike-gradle-dependencias.md). |
| D8 | Selección de átomos: forma de colisión **esférica** (`collision.Sphere`) con resaltado por estado | Provisional | Sustituye a la caja (AABB) por defecto, cuyas zonas eran grandes. Aplicada en el spike; se mide con una molécula real. Detalle en [`docs/02b-spike-seleccion-atomos.md`](docs/02b-spike-seleccion-atomos.md). |
| D9 | Parser de CIF **escrito a mano**, en Kotlin puro (sin `android.*`), en tres capas, con resultado éxito/fallo y sin excepciones por la entrada | Provisional | Es el objetivo de aprendizaje (D2) y permite probarlo en JVM. Las políticas abiertas (coordenadas, datos defectuosos) se cierran con el corpus real. Detalle en [`docs/03-formato-cif-y-parser.md`](docs/03-formato-cif-y-parser.md). |
| D10 | Coordenadas: se elige **un juego entero por ligando** (ideales si están completas; si no, modelo) y siempre se centra en el centroide | Decidida con datos | De 1243 ligandos, 1225 tienen ideales completas y los otros 17 modelo completa; ninguno falla. Los dos juegos están en sistemas de referencia distintos, así que no se mezclan. Detalle en [`docs/03-formato-cif-y-parser.md`](docs/03-formato-cif-y-parser.md), 7.1. |
| D11 | El proyecto Android del spike se **copió** a la raíz de `main` como esqueleto del proyecto real; el código demostrativo (`MoleculeScene.kt` con átomos fijos) es **temporal** y se reescribirá | Decidida | La configuración y la integración de SceneView estaban comprobadas en dispositivo; rehacerlas desde cero no aportaba nada. Antes, la documentación decía lo contrario; se corrigió. Detalle y lista de limpieza en [`docs/02-spike-3d.md`](docs/02-spike-3d.md), sección 10. |
| D12 | Datos defectuosos: política **estricta**. Ante la duda, `Failure` con un motivo; sin avisos ni molécula incompleta dibujada en silencio | Decidida | Con el corpus real la política tolerante no aporta nada (0 casos), cuesta código y su riesgo es dibujar sin avisar. Detalle en [`docs/03-formato-cif-y-parser.md`](docs/03-formato-cif-y-parser.md), 7.2. |
| D13 | Coordenadas (refina D10): se elige el primer juego **completo y creíble** (ningún enlace de más de 5 Å); si ninguno es creíble, el primero completo | Decidida con datos | Cinco ligandos (`HEM`, `HEC`, `HEA`, `B12`, `AV2`) tienen las ideales rotas, con hidrógenos a 8–14 Å de su oxígeno. Con D13, los 54 527 enlaces del corpus miden 0,86–2,65 Å. Detalle en [`docs/03-formato-cif-y-parser.md`](docs/03-formato-cif-y-parser.md), 3.6 y 7.1. |
| D14 | Cliente HTTP: `java.net.HttpURLConnection`, **sin dependencias nuevas**, detrás de la interfaz `HttpFetcher` | Decidida | Hace lo que necesitamos (un `GET` de un fichero pequeño), viene con Android y la JVM, y se prueba sin emulador. OkHttp es la alternativa si la prueba real descubre problemas; solo habría que cambiar una clase. Detalle en [`docs/06-red-y-carga-de-ligandos.md`](docs/06-red-y-carga-de-ligandos.md), 10 y 11. |
| D15 | Red: tres relojes (10 s conexión, 10 s lectura, 30 s total), tamaño máximo 2 000 000 bytes y comprobación de `Content-Length` | Decidida; valores **estimados**, no medidos | Un servidor lento o mentiroso no puede dejar el indicador de carga girando ni llenar la memoria. Sin `Content-Length` el parser deja pasar el 10,4 % de los cortes posibles del `001` (medido). Detalle en el documento 06, secciones 4 y 5. |
| D16 | Los errores de carga son **valores** (`FetchResult`, `LigandLoadResult`, `LoadError`) en dos niveles, con una red de seguridad `catch (Exception)` en el repositorio | Decidida | Un cierre inesperado suspende; con tipos cerrados el compilador obliga a tratar todos los casos. Detalle en el documento 06, sección 6. |
| D17 | Búsqueda: el texto **contenido** en el id, sin distinguir mayúsculas, con los ids que **empiezan** por el texto primero | Decidida | Con ids de 3 letras «contiene» es más útil que solo «empieza por», y el prefijo primero pone arriba lo más probable. 57 µs en la JVM de análisis (sin medir en el A33). Detalle en [`docs/06b-lista-busqueda-y-estado-de-carga.md`](docs/06b-lista-busqueda-y-estado-de-carga.md), 4, 5 y 15. |
| D18 | Las reglas de la carga viven en `LigandLoader` (Kotlin + corrutinas) y el estado es una `sealed interface` (`LoadState`); el `ViewModel` es una envoltura mínima | Decidida | Se prueba en la JVM sin Android; el indicador no puede coexistir con un error. Detalle en el documento 06b, 6, 7 y 9, y en el 06c, 6. |
| D19 | La pantalla se decide con un `if` sobre el `LoadState` (**sin librería de navegación**); el texto buscado y la posición de la lista viven en la raíz (`SwiftyApp`) | Provisional | Hay dos pantallas y el estado de carga ya es la verdad. Se revisará al llegar el inicio de sesión y la escena 3D. Detalle en [`docs/06c-pegamento-android-lista-y-carga.md`](docs/06c-pegamento-android-lista-y-carga.md), 7, 16 y 18. |
| D20 | Los textos de la interfaz van **en inglés, escritos en el código** (no en `strings.xml`) | Provisional | Menos ceremonia ahora; cambiarlo después es mecánico. **Supuesto no confirmado:** que la app deba estar en inglés. Detalle en el documento 06c, 18. |

---

## Estructura del repositorio

```
app/                  proyecto Android (paquete dev.knowledge82.swiftyproteins)
docs/                 un documento por etapa (en español de España)
gradle/, gradlew      wrapper de Gradle
build.gradle.kts, settings.gradle.kts, gradle.properties
```

El proyecto Android se movió a la raíz al cerrar el spike (la rama `spike/3d` se conserva con la etiqueta `spike-3d-final`).
`.kotlin/` y `/.idea/` no se versionan.

---

## Documentación

Cada documento explica la teoría, los riesgos, las alternativas y las trampas de su etapa.

| Documento | Contenido | Estado |
|-----------|-----------|--------|
| [`docs/00-glosario.md`](docs/00-glosario.md) | Términos: RCSB, wwPDB, PDBe, CCD, coordenadas de modelo e ideales, orden de enlace, nodo pivote, Compose, SceneView, Filament, OpenGL ES, ADR, etc. | Vivo |
| [`docs/01-tarea-y-punto-de-partida.md`](docs/01-tarea-y-punto-de-partida.md) | Problema, requisitos, no-objetivos, ambigüedades, criterios de éxito, riesgos y decisiones | Escrito |
| [`docs/02-spike-3d.md`](docs/02-spike-3d.md) | Plan del spike: qué validamos, cómo, criterios de paso y teoría de 3D necesaria | Escrito |
| [`docs/02a-spike-gradle-dependencias.md`](docs/02a-spike-gradle-dependencias.md) | Teoría de Gradle, Maven Central y BOM; registro de los pasos 0 a 3; ADR-001 (borrador) | Escrito |
| [`docs/02b-spike-seleccion-atomos.md`](docs/02b-spike-seleccion-atomos.md) | Teoría del picking por rayo, cómo lo hace SceneView, implementación, resultado y alternativas de mayor precisión | Escrito |
| [`docs/03-formato-cif-y-parser.md`](docs/03-formato-cif-y-parser.md) | Sintaxis CIF, estructura de un ligando, modelo de datos, diseño del parser en tres capas, políticas cerradas con datos, errores, casos límite y qué está verificado | **Escrito** (verificado con 1242 ligandos en copia binaria y un fichero real) |
| [`docs/03a-codigo-parser.md`](docs/03a-codigo-parser.md) | El código del parser **línea a línea**: `Molecule`, tokenizador, lector de tablas, extractor y `CifParser`; simulación completa, las 88 pruebas (cómo ejecutarlas y qué demuestran), comprobación con el corpus, alternativas, límites | **Escrito** |
| 04 · Arquitectura y decisiones (ADR) | Capas, módulos y registro de decisiones | Pendiente |
| 05 · Autenticación y seguridad | Hash de contraseñas, Keystore, biometría, bloqueo al volver de segundo plano | Pendiente |
| [`docs/06-red-y-carga-de-ligandos.md`](docs/06-red-y-carga-de-ligandos.md) | HTTP en cinco minutos, todo lo que puede salir mal en una descarga (y su mensaje), tres relojes, ¿llegó entero?, errores como valores, hilos y cancelación, permiso `INTERNET`, alternativas (OkHttp...), cómo probar sin Internet, qué está verificado | **Escrito** (capa de red; lista y búsqueda, pendientes) |
| [`docs/06a-codigo-red.md`](docs/06a-codigo-red.md) | El código de la capa de red **línea a línea**: `FetchResult`, `HttpUrlConnectionFetcher`, `LigandLoadResult`, `LigandRepository`, el servidor casero de pruebas y las 42 pruebas una a una, con mutación | **Escrito** |
| [`docs/06b-lista-busqueda-y-estado-de-carga.md`](docs/06b-lista-busqueda-y-estado-de-carga.md) | `LigandCatalog` (lista y búsqueda en tiempo real), `LoadState`, `LigandLoader` (corrutinas, cancelación, la garantía del indicador), simulaciones, 25 pruebas con mutación, qué falta para enchufarlo a Android | **Escrito** (lógica; pegamento con Android, pendiente) |
| [`docs/06c-pegamento-android-lista-y-carga.md`](docs/06c-pegamento-android-lista-y-carga.md) | El pegamento con Android **línea a línea**: dependencias, `ViewModel` y su fábrica, la raíz (navegar por estado), lista con buscador, diálogos de carga y de error, pantalla provisional; simulaciones, cómo probar los cuatro errores en el móvil, alternativas, qué está verificado y qué no | **Escrito** (sin compilar con el SDK de Android) |
| 07 · Vista 3D | Escena, cámara, gestos, selección de átomos | Pendiente |
| 08 · Compartir y acabado | Captura, diseño adaptable, accesibilidad | Pendiente |
| 09 · Bonus | Solo cuando la parte obligatoria sea sólida | Pendiente |
| 10 · Pruebas y defensa | Lista de comprobación en dispositivo real y guion de defensa | Pendiente |

---

## Cómo trabajamos

- **Cada pieza de código se explica línea a línea** y se incorpora al repositorio solo cuando todo el equipo la entiende. Este es un
  proyecto de aprendizaje y en la defensa hay que poder explicar cada línea.
- **Un documento por etapa**, en `docs/`, enlazado desde este README y actualizado al
  cerrar cada etapa.
- **Los experimentos viven en ramas** (por ejemplo `spike/3d`) y **no se fusionan** en `main`. Si se aprovecha algo de un experimento, se **copia de forma explícita**, se registra como decisión (D11) y se anota qué es temporal.

---

## Entorno de desarrollo

Versiones del spike (detalle y motivos en [`docs/02a-spike-gradle-dependencias.md`](docs/02a-spike-gradle-dependencias.md)):

| Pieza | Versión |
|-------|---------|
| Sistema de desarrollo | Debian 13 (en casa) |
| Android Gradle Plugin | 9.4.1 |
| Gradle (wrapper) | 9.6.0 |
| Kotlin | 2.4.21 |
| Compose BOM | 2026.09.00 (Compose 1.12.1) |
| SceneView | 4.69.4 |
| Java (objetivo) | 21 |
| compileSdk / minSdk | 37 / 24 |
| Android Studio | `<pendiente de anotar>` |
| `adb` (platform-tools) | 37.0.1 |

Prueba en dispositivo real: Samsung Galaxy A33 (con lector de huellas), conectado por USB.
Versión de Android del dispositivo: `<pendiente de anotar>`.

---

## Autores

- `<login 42 del miembro 1>`
- `<login 42 del miembro 2>`
