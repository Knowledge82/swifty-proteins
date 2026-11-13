# Swifty Protein

Visor 3D de ligandos (moléculas pequeñas) para Android, escrito en Kotlin.
Proyecto en pareja del bloque **Mobile** de 42 Barcelona.

> **Estado actual:** fase de preparación. El enunciado está analizado, las primeras
> decisiones están tomadas y el siguiente paso es un *spike* (experimento corto) para
> validar la librería 3D. Todavía no hay código de la aplicación.

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
| D3 | Interfaz: **Jetpack Compose** | Provisional | Muchos estados (login, carga, error, tema oscuro). Se confirma en el spike. |
| D4 | Capa 3D: **SceneView (Filament)** | Provisional | Se valida en el spike. Plan B: `GLSurfaceView` con OpenGL ES. |
| D5 | Cuentas de usuario: **almacenamiento local** con hash de contraseña | Provisional | Menos dependencias externas y más aprendizaje que Firebase. |
| D6 | Idioma de README y `docs/`: **español de España** | Cerrada | Ambos miembros del equipo lo leen. |

---

## Documentación

Cada documento explica la teoría, los riesgos, las alternativas y las trampas de su etapa.

| Documento | Contenido | Estado |
|-----------|-----------|--------|
| [`docs/01-tarea-y-punto-de-partida.md`](docs/01-tarea-y-punto-de-partida.md) | Problema, requisitos, no-objetivos, ambigüedades, criterios de éxito, riesgos y decisiones | Escrito |
| [`docs/02-spike-3d.md`](docs/02-spike-3d.md) | Plan del spike: qué validamos, cómo, criterios de paso y teoría de 3D necesaria | Escrito |
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

*Pendiente de completar cuando se cree el proyecto Android* (versiones de Android Studio,
SDK y Gradle, y configuración para el campus).

Prueba en dispositivo real: Samsung Galaxy A33 (con lector de huellas).

---

## Autores

- `<login 42 del miembro 1>`
- `<login 42 del miembro 2>`
