# Swifty Protein

Visor 3D de ligandos (moléculas pequeñas) para Android, escrito en Kotlin.
Proyecto en pareja del bloque **Mobile** de 42 Barcelona.

> **Estado actual:** *spike* 3D en curso (rama `spike/3d`). Las versiones de Kotlin, Compose y
> SceneView están alineadas; ya se dibujan esferas y enlaces (cilindros) sobre los ejes y en diagonal,
> con colores CPK y cámara inicial, y todo verificado en el dispositivo. El toque sobre un átomo funciona
> (zonas sensibles grandes, ver D8). Faltan el ciclo de vida, la captura y el rendimiento. Todavía no hay código de la aplicación final.

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
| D3 | Interfaz: **Jetpack Compose** | Provisional | Muchos estados (login, carga, error, tema oscuro). Versiones alineadas y H1 confirmada en el dispositivo; se cierra al terminar el spike. |
| D4 | Capa 3D: **SceneView 4.69.4 (Filament)** | Provisional | Compila y dibuja esferas y cilindros en el A33; la selección de átomos funciona con zonas grandes (D8); faltan ciclo de vida, captura y rendimiento. Plan B: `GLSurfaceView` con OpenGL ES. |
| D5 | Cuentas de usuario: **almacenamiento local** con hash de contraseña | Provisional | Menos dependencias externas y más aprendizaje que Firebase. |
| D6 | Idioma de README y `docs/`: **español de España** | Cerrada | Ambos miembros del equipo lo leen. |
| D7 | Versiones fijadas del spike: SceneView 4.69.4, Kotlin 2.4.21, AGP 9.4.1, Compose BOM 2026.09.00, Java 21 | Provisional | SceneView publica a diario y exige Kotlin 2.4, Compose 1.12.1 y Java 21; se fija una versión concreta. Detalle en [`docs/02a-spike-gradle-dependencias.md`](docs/02a-spike-gradle-dependencias.md). |
| D8 | Selección de átomos: **detección integrada de SceneView (AABB)** con resaltado por estado | Provisional | Funciona y no añade código, pero las zonas son grandes. Mejora prevista y barata: `collisionShape = Sphere(radio)`. Se revisa con una molécula real. Detalle en [`docs/02b-spike-seleccion-atomos.md`](docs/02b-spike-seleccion-atomos.md). |

---

## Documentación

Cada documento explica la teoría, los riesgos, las alternativas y las trampas de su etapa.

| Documento | Contenido | Estado |
|-----------|-----------|--------|
| [`docs/01-tarea-y-punto-de-partida.md`](docs/01-tarea-y-punto-de-partida.md) | Problema, requisitos, no-objetivos, ambigüedades, criterios de éxito, riesgos y decisiones | Escrito |
| [`docs/02-spike-3d.md`](docs/02-spike-3d.md) | Plan del spike: qué validamos, cómo, criterios de paso y teoría de 3D necesaria | Escrito |
| [`docs/02a-spike-gradle-dependencias.md`](docs/02a-spike-gradle-dependencias.md) | Teoría de Gradle, Maven Central y BOM; registro de los pasos 0 a 3; ADR-001 (borrador) | Escrito |
| [`docs/02b-spike-seleccion-atomos.md`](docs/02b-spike-seleccion-atomos.md) | Teoría del picking por rayo, cómo lo hace SceneView, implementación, resultado y alternativas de mayor precisión | Escrito |
| 03 · Formato CIF y parser | Estructura del fichero, casos límite, modelo de datos | Pendiente |
| 04 · Arquitectura y decisiones (ADR) | Capas, módulos y registro de decisiones | Pendiente |
| 05 · Autenticación y seguridad | Hash de contraseñas, Keystore, biometría, bloqueo al volver de segundo plano | Pendiente |
| 06 · Lista, búsqueda y red | Carga asíncrona, errores, estados de la interfaz | Pendiente |
| 07 · Vista 3D | Escena, cámara, gestos, selección de átomos | Pendiente |
| 08 · Compartir y acabado | Captura, diseño adaptable, accesibilidad | Pendiente |
| 09 · Bonus | Solo cuando la parte obligatoria sea sólida | Pendiente |
| 10 · Pruebas y defensa | Lista de comprobación en dispositivo real y guion de defensa | Pendiente |

---

## Cómo trabajamos

- **El código lo escribe el equipo.** La documentación y las explicaciones sirven para
  entender cada línea antes de escribirla. Este es un proyecto de aprendizaje, no de producción.
- **Un documento por etapa**, en `docs/`, enlazado desde este README y actualizado al
  cerrar cada etapa.
- **Los experimentos viven en ramas** (por ejemplo `spike/3d`) y no se fusionan en `main`.
- **La documentación se ha redactado con ayuda de un asistente de IA.** Los autores revisan
  y entienden su contenido.

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
