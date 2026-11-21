# Swifty Protein

Visor 3D de ligandos (moléculas pequeñas) para Android, escrito en Kotlin.
Proyecto en pareja del bloque **Mobile** de 42 Barcelona.

> **Estado actual:** *spike* 3D **cerrado con reservas** (ADR-001, ver
> [`docs/02a-spike-gradle-dependencias.md`](docs/02a-spike-gradle-dependencias.md), sección 7). El proyecto Android ya vive en la raíz
> de `main` (paquete `dev.knowledge82.swiftyproteins`). **Etapa en curso:** carga de ligandos. El parser de CIF está **completo** (88 pruebas, verificado
> contra los 1242 ligandos presentes en el diccionario) y la **capa de red** (descarga, errores, tiempos, cancelación y conversión a molécula) está **escrita y probada en la JVM** (42 pruebas más; sin probar en dispositivo ni contra RCSB real).
> También están escritos y probados en la JVM el **catálogo con búsqueda** (`ligands.txt`, sin distinguir mayúsculas) y el **cargador** que gobierna el estado de carga (`Idle / Loading / Loaded / Failed`, con la garantía de que el indicador siempre desaparece).
> Está escrito y funciona en el móvil el **pegamento con Android** (lista con buscador, diálogos de carga y de error); 162 pruebas.
> Se ha escrito la **escena 3D de la molécula** (bolas y varillas con colores CPK, cámara orbital, tocar un átomo, interruptor de hidrógenos): su parte pura tiene 27 pruebas y mutación (198 en total con las de zoom y *splash*);
> la escena **funciona en el móvil** (confirmado con `001`, `CDL` y `15P`). Se corrigió el **tope de zoom** de los ligandos grandes (D24) y se añadió la **pantalla de inicio de ≥ 1 s** que pide la hoja de evaluación (D25; **sin compilar con la biblioteca real**). La **dirección visual** está definida (D26) y el **diseño del generador ya está aplicado** a la lista, los diálogos, la pantalla de la molécula, el icono y la *splash* (D27–D29; 217 pruebas; **sin compilar con las bibliotecas reales**). Está escrita la **captura de la escena y «compartir»** (H4: `PixelCopy` + `FileProvider`, D30–D31; **sin probar en el móvil**). El **diseño v2 «Cogitador»** (consolas CRT verdes, calaveras, servidor, servo-cráneo; Pirata One + Share Tech Mono con brillo de fósforo) está **aplicado**, con una **pantalla de arranque** propia como *launch screen* (D32–D36; **sin compilar con las bibliotecas reales**). Falta: el inicio de sesión y la medición en el A33. Documentos:
> [`docs/03-formato-cif-y-parser.md`](docs/03-formato-cif-y-parser.md) y [`docs/03a-codigo-parser.md`](docs/03a-codigo-parser.md) (parser),
> [`docs/06-red-y-carga-de-ligandos.md`](docs/06-red-y-carga-de-ligandos.md) y [`docs/06a-codigo-red.md`](docs/06a-codigo-red.md) (red),
> [`docs/06b-lista-busqueda-y-estado-de-carga.md`](docs/06b-lista-busqueda-y-estado-de-carga.md) (lista, búsqueda y estado de carga),
> [`docs/06c-pegamento-android-lista-y-carga.md`](docs/06c-pegamento-android-lista-y-carga.md) (pegamento con Android),
> [`docs/07-escena-3d-de-la-molecula.md`](docs/07-escena-3d-de-la-molecula.md) (escena 3D y límites de zoom),
> [`docs/07b-icono-y-pantalla-de-inicio.md`](docs/07b-icono-y-pantalla-de-inicio.md) (icono y *splash*),
> [`docs/08a-direccion-visual-y-prompt.md`](docs/08a-direccion-visual-y-prompt.md) (dirección visual y *prompt*),
> [`docs/08b-diseno-aplicado.md`](docs/08b-diseno-aplicado.md) (el diseño aplicado),
> [`docs/08c-diseno-v2-cogitador.md`](docs/08c-diseno-v2-cogitador.md) (diseño v2),
> [`docs/09-captura-y-compartir.md`](docs/09-captura-y-compartir.md) (captura y compartir).
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
| D21 | La paleta de colores es la tabla **«CPK» de Jmol** (escrita de memoria), con rosa fuerte para lo desconocido; los radios de bola son de **dibujo** (0,25–0,55 Å), no físicos | Provisional | Convención reconocible; cambiar una tabla es trivial. Los tonos de metales raros, sin comprobar. Detalle en [`docs/07-escena-3d-de-la-molecula.md`](docs/07-escena-3d-de-la-molecula.md), 3, 5 y 16. |
| D22 | Se dibujan **todos los átomos, hidrógenos incluidos**, con un interruptor «H»; el encuadre cuenta siempre todos | Decidida con datos | El 48 % de los átomos del corpus (25 613 de 53 241) son hidrógenos. Detalle en el documento 07, 4 y 16. |
| D23 | La escena es **un nodo por átomo y dos por enlace**, con la cámara inicial a **5,5 × radio** | Provisional | Lo más simple, con selección gratis; el rendimiento (H6) y el encuadre se miden en el A33; plan B: una malla. Detalle en el documento 07, 7, 12 y 15. |
| D24 | Los límites de zoom son **absolutos en ångström** (acercamiento mínimo 4 Å; alejamiento máximo 3 × la distancia inicial), no relativos al tamaño del ligando | Provisional | La causa del tope de `15P` se **leyó en el código de SceneView** (0,15 × la distancia inicial); el valor se ajusta en el móvil. Documento 07, 7.1. |
| D25 | La pantalla de inicio es la **del sistema** (`core-splashscreen`), **retenida 1500 ms** por una regla que solo mira el reloj | **Sustituida por D32** | Cumple «lanzamiento visible ≥ 1 s» con poco código y sin riesgo de colgarse. Documento 07b. |
| D26 | Dirección visual **Adeptus Mechanicus** reconocible (consolas CRT verdes, calaveras, servidores, gótico industrial); solo tema oscuro; emblema propio (calavera en engranaje). **v1** («Culto de la Máquina» sobrio) **rechazada en el móvil**; **v2** del prompt en curso | Dirección decidida; diseño pendiente | La v1 era demasiado prudente (sin símbolos, solo vectores, fuentes neutras). Documento 08a, sección 12. |
| D27 | El diseño se aplica **como piel** (tema de Material 3 + componentes propios finos); **la lógica no se toca** y lo que decide algo va a funciones puras con pruebas | Decidida | Romper lo que funciona es el riesgo real. Documento 08b. |
| D28 | Fuentes **incluidas** (OFL, licencias en `assets/licenses/fonts/`; **las familias cambiarán** con la v2: **Pirata One** (títulos) + **Silkscreen** (resto), elegidas por el equipo), cortes estáticos de las variables; iconos como **VectorDrawable** generados con un conversor verificado píxel a píxel | Decidida | Sin red para verse bien; compatible con API 24. Documento 08b, 4 y 5. |
| D29 | El fondo de la escena **se queda negro** (el diseño pedía `#100F10`) | Provisional | Cambiar el cielo sin perder la luz ni arriesgar un cierre requiere más trabajo. Documento 08b, 10. |
| D30 | La captura es **`PixelCopy` del `SurfaceView` de SceneView** (encontrado recorriendo el árbol de vistas); la imagen es el 3D + una banda con el nombre | Provisional (H4 sin probar) | API 24 sin tocar el dibujo comprobado; plan B: `TextureView`. Documento 09. |
| D31 | Se comparte con **`FileProvider` desde `cache/shared/`** (solo la última imagen, escritura en dos tiempos) y la **hoja del sistema** | Decidida | Sin permisos de almacenamiento; acceso solo a la app elegida y a ese fichero. Documento 09. |
| D32 | El *launch screen* es la **pantalla de arranque propia del cogitador** (4200 ms: 65 % líneas y barra, 35 % «DONE» parpadeando; **un toque la salta tras 1 s**; una vez por arranque); la *splash* del sistema ya no se retiene. **Sustituye a D25** | Decidida | Siempre ≥ 1 s (hoja de evaluación); el toque evita esperar. Documento 08c, 7. |
| D33 | Marcos **dibujados en código**; el 9-patch del kit no se usa | Decidida | Nítido a cualquier densidad y admite etiquetas. Documento 08c, 4. |
| D34 | El arte del kit se **limpia** (halo, restos, fragmentos) y va en `drawable-nodpi` | Decidida | El halo opaco ensuciaba la consola; `nodpi` evita el triple escalado. Documento 08c, 3. |
| D35 | El conversor de iconos admite `transform` y círculos por defecto; verificación con **control negativo** | Decidida | El emblema del kit los usa. Documento 08c, 5. |
| D36 | **Share Tech Mono** sustituye a Silkscreen; pasada de «luz»: brillo de fósforo, metal con degradado, cristal con resplandor, barrido tenue | Decidida | Silkscreen (fuente de píxeles) daba aspecto de consola de 8 bits. Documento 08c, 17. |
| D37 | Las compilaciones debug se firman con **una clave de depuración común en el repositorio** (`app/debug.keystore`) | Decidida | Instalar desde cualquier máquina sin desinstalar. Documento 11. |
| D38 | Icono **pictórico**: **emblema completo** (cráneo en la rueda, ligando en holograma, «42» y `101010`) en el arranque; en el lanzador, el **signo mínimo**: cráneo cibernético que proyecta la molécula, sin rueda | Decidida | Más reconocible que el plano; la molécula cumple «acorde con el tema»; sin rueda el icono deja de estar cargado. Documento 08d. |
| D39 | La *splash* del sistema (obligatoria en Android 12+) se hace **invisible**: color abismo e icono vacío | Decidida | No se puede quitar; con el icono se veían dos emblemas seguidos. Documento 08d, 12.4. |
| D40 | Contraseñas con **Argon2id** (Bouncy Castle; 19 MiB, 2 pasadas, paralelismo 1; sal de 16 bytes), parámetros guardados por cuenta | Decidida | Nombrado en el enunciado, resistente a tarjetas gráficas, OWASP. Documento 05. |
| D41 | Cuentas en un fichero propio (`noBackupFilesDir`) **cifrado con AES-GCM** con clave del **Keystore** | Decidida | Defensa en profundidad; usa el Keystore. Documento 05. |
| D42 | Biometría **por cuenta y voluntaria**, Clase 3, con **`CryptoObject`**; API nueva de `androidx.biometric` 1.4 | Decidida | La huella prueba al dueño, no la cuenta; éxito verificable. Documento 05. |
| D43 | Bloqueo en **cada** salida a segundo plano (incluida la hoja de compartir); la app conserva dónde estaba; sin miniatura en Recientes (Android 13+) | Decidida | «ALWAYS» del enunciado, sin agujeros. Documento 05. |
| D44 | Usuario 3–20 `[a-z0-9_.-]` único sin mayúsculas; contraseña 8–64 con letra y dígito y sin el usuario; mensaje único de error; freno 5 fallos → 30 s duplicando hasta 5 min | Decidida | Requisitos visibles; no revela usuarios. Documento 05. |
| D45 | Iluminación de **tres puntos** en nuestro código (principal, relleno, contraluz) + ambiente IBL | Decidida (a ajustar a ojo) | VI.4: volumen y varias luces. Documento 12. |
| D46 | **Contador de FPS** de fotogramas presentados, con estado IDLE, en **todas** las compilaciones | Decidida | VI.4/VIII.2 piden 60 y castigan < 30; el dibujo a demanda obliga a distinguir «parado». Documento 12. |
| D47 | Caché de `.cif` (`cacheDir/ligands/`): copia fresca 30 días primero, red después, copia vieja si la red falla, 404 la borra, solo se guarda lo interpretado; el origen se ve en la pestaña | Decidida | Sin conexión (instrucciones generales y VII.4); nunca datos sin validar. Documento 13. |
| D48 | **Diez sonidos sintetizados** con matemáticas, v2.1 «grimdark» (metal golpeado, órgano, sala de piedra; un módem de acceso telefónico para la espera; nada grave sostenido que haga vibrar el altavoz; cortos), **nivelados por sonoridad**, Ogg Vorbis reproducible byte a byte; un `SoundPool` de la aplicación; nunca en silencio o vibración; intervalo mínimo por sonido | Decidida | Ambientación sin licencias ajenas, explicable, discreta. Documento 14. |
| D49 | **Fórmula** contada de los átomos en orden de **Hill**; subíndices tipográficos en pantalla, dígitos planos en texto | Decidida | Coincide con RCSB en 1242/1242. Documento 15. |
| D50 | **Nombre** del ligando (`_chem_comp.name`) opcional, limpio y acotado (400) | Decidida | VII.5; dato de la red. Documento 15. |
| D51 | **Cuatro modelos** (bolas y varillas, espacio lleno con radios de Mantina 2009, varillas, alambre), cambio en vivo | Decidida | VII.1. Documento 15. |
| D52 | **Tocar un enlace** (orden y longitud) y **resaltar un elemento** desde la leyenda | Decidida | VII.3. Documento 15. |
| D53 | **Doble toque** = orbitar el átomo; en el vacío, volver al centro; zoom por doble toque de SceneView apagado | Decidida | VII.3. Documento 15. |

---

## Estructura del repositorio

```
app/                  proyecto Android (paquete dev.knowledge82.swiftyproteins)
docs/                 un documento por etapa (en español de España)
tools/art/            limpieza del arte (documento 08c)
tools/corpus-check/   comprobación del parser contra el corpus
tools/sound/          síntesis de los sonidos: make_sounds.py (documento 14)
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
| [`docs/05-acceso-y-seguridad.md`](docs/05-acceso-y-seguridad.md) | Acceso y seguridad: hash frente a cifrado (sal, lentitud, memoria), el Android Keystore, clases de biometría y `CryptoObject`, `ProcessLifecycleOwner`; decisiones (Argon2id, fichero cifrado con el Keystore, huella por cuenta, bloqueo en cada salida, reglas y freno), mensajes, flujo, arquitectura, dependencias, trampas y plan de pruebas | **Escrito** (comprobado en el A33) |
| [`docs/05a-codigo-acceso.md`](docs/05a-codigo-acceso.md) | El acceso **línea a línea**: reglas, Argon2id (vector de RFC 9106), formato y fichero cifrado con AES-GCM, libro de cuentas con freno, el flujo entero (`AuthController`: hilos, `generation`, petición en vuelo, huella con `CryptoObject`), claves del Keystore, `ProcessLifecycleOwner`, pantallas; simulación, pruebas y mutantes, versiones, lista para el móvil | **Escrito** (comprobado en el A33) |
| [`docs/06-red-y-carga-de-ligandos.md`](docs/06-red-y-carga-de-ligandos.md) | HTTP en cinco minutos, todo lo que puede salir mal en una descarga (y su mensaje), tres relojes, ¿llegó entero?, errores como valores, hilos y cancelación, permiso `INTERNET`, alternativas (OkHttp...), cómo probar sin Internet, qué está verificado | **Escrito** (capa de red; lista y búsqueda, pendientes) |
| [`docs/06a-codigo-red.md`](docs/06a-codigo-red.md) | El código de la capa de red **línea a línea**: `FetchResult`, `HttpUrlConnectionFetcher`, `LigandLoadResult`, `LigandRepository`, el servidor casero de pruebas y las 42 pruebas una a una, con mutación | **Escrito** |
| [`docs/06b-lista-busqueda-y-estado-de-carga.md`](docs/06b-lista-busqueda-y-estado-de-carga.md) | `LigandCatalog` (lista y búsqueda en tiempo real), `LoadState`, `LigandLoader` (corrutinas, cancelación, la garantía del indicador), simulaciones, 25 pruebas con mutación, qué falta para enchufarlo a Android | **Escrito** (lógica; pegamento con Android, pendiente) |
| [`docs/06c-pegamento-android-lista-y-carga.md`](docs/06c-pegamento-android-lista-y-carga.md) | El pegamento con Android **línea a línea**: dependencias, `ViewModel` y su fábrica, la raíz (navegar por estado), lista con buscador, diálogos de carga y de error, pantalla provisional; simulaciones, cómo probar los cuatro errores en el móvil, alternativas, qué está verificado y qué no | **Escrito** (comprobado en el móvil: lista, búsqueda, carga) |
| [`docs/07-escena-3d-de-la-molecula.md`](docs/07-escena-3d-de-la-molecula.md) | La escena 3D **línea a línea**: teoría (CPK, bolas y varillas, orientación de cilindros con un ejemplo numérico, cámara, selección), datos del corpus (48 % hidrógenos, máximo 256 átomos), `ElementStyle`, `BondGeometry`, `SceneFraming`, `MoleculeScene`, `MoleculeScreen`; cómo probarlo y qué constantes ajustar; mutación; alternativas | **Escrito** (comprobado en el móvil; límites de zoom en ångström, sección 7.1) |
| [`docs/07b-icono-y-pantalla-de-inicio.md`](docs/07b-icono-y-pantalla-de-inicio.md) | Qué pide la hoja de evaluación (icono y lanzamiento ≥ 1 s), cómo funciona la pantalla de inicio en Android, `core-splashscreen`, `SplashHold` y `MainActivity` línea a línea, línea de tiempo con números, trampas, cómo medirlo, alternativas | **Escrito** (regla del tiempo probada; resto sin compilar) |
| [`docs/08a-direccion-visual-y-prompt.md`](docs/08a-direccion-visual-y-prompt.md) | Dirección visual «Culto de la Máquina», paleta con **contraste medido**, restricciones técnicas, aviso de propiedad intelectual, **el prompt completo** para el generador y cómo se trasladará a Compose | **Escrito** (generador sin usar) |
| [`docs/08b-diseno-aplicado.md`](docs/08b-diseno-aplicado.md) | El diseño aplicado (paso A): revisión del kit, fuentes OFL y cortes estáticos, conversión SVG → VectorDrawable verificada píxel a píxel, icono adaptable y de Android 7, tema de Material 3, componentes, pantallas, desvíos conscientes, lista de comprobación en el móvil | **Escrito** (lógica probada; interfaz sin compilar) |
| [`docs/08c-diseno-v2-cogitador.md`](docs/08c-diseno-v2-cogitador.md) | Diseño v2 «Cogitador»: revisión del kit, limpieza del arte (halo medido, algoritmo), marcos dibujados en código frente al 9-patch, conversor con `transform` y control negativo, fuentes Pirata One + Share Tech Mono (Silkscreen descartada, sección 17), contraste recalculado, pantalla de arranque como *launch screen*, componentes, pantallas, mutantes supervivientes que delataron pruebas débiles | **Escrito** (lógica probada; interfaz sin compilar) |
| [`docs/08d-icono-v3-craneo.md`](docs/08d-icono-v3-craneo.md) | Icono v3 pictórico: el cráneo del arranque probado como icono a 48/96/192 px, teoría del icono adaptativo (108/72/66 dp, monocromo, icono antiguo), jerarquía de legibilidad por tamaño, el «42» con números propios y `101010`, tres composiciones, **el prompt completo**, el resultado medido (zona segura, alfa, halo, vistas a tamaño real), dos versiones (emblema/icono), integración por densidad, monocromo vectorial y trampas | **Escrito** (integrado y medido; sin probar en el móvil) |
| [`docs/11-firma-de-depuracion.md`](docs/11-firma-de-depuracion.md) | Firma de APK y por qué Android rechaza una actualización con otra firma; la clave de depuración por máquina; una clave común en el repositorio (`signingConfigs`); seguridad, migración, cómo comprobarlo | **Escrito** (clave comprobada con `keytool`; instalación sin probar) |
| [`docs/12-luz-y-fps.md`](docs/12-luz-y-fps.md) | La luz por defecto de SceneView leída en su código, luz física en Filament (lux, direccional, IBL), iluminación de tres puntos; qué es «FPS» con dibujo a demanda, el contador `FpsMeter` (anillo sin objetos por fotograma, estado IDLE, niveles), pruebas y mutantes, cómo ajustarlo en el móvil | **Escrito** (contador probado; luz sin ver en el móvil) |
| [`docs/13-cache-sin-conexion.md`](docs/13-cache-sin-conexion.md) | Caché de `.cif` para funcionar sin conexión: estrategias (caché primero, red primero, TTL, «vieja antes que nada»), las reglas elegidas (fresca 30 días, validar antes de guardar, 404 frente a fallo de red), `cacheDir`, escritura atómica, límite de 20 MB, el origen en la pestaña, pruebas, mutantes y la demostración en modo avión | **Escrito** (probado en la JVM; sin probar en el móvil) |
| [`docs/14-sonidos.md`](docs/14-sonidos.md) | Sonidos del cogitador **sintetizados desde cero**: teoría del sonido digital (muestreo, Nyquist, dBFS, clics, espectrograma), el altavoz del móvil y la fundamental ausente, pico frente a sonoridad; las piezas (osciladores, fase acumulada, síntesis modal, formantes, órgano, saturación, reverberación por convolución); por qué se rehízo (v1 → v2.0 → v2.1, con medidas: el altavoz que vibra); el módem fase a fase (DTMF, V.8, V.21, V.34); **las diez recetas** con sus números, hoja de espectrogramas, WAV → Ogg Vorbis reproducible, `SoundPool`, carga asíncrona, ganchos con `LaunchedEffect`, la regla del silencio, pruebas, mutantes, trampas y alternativas | **Escrito** (regla probada en la JVM; v2.1 aprobada en el móvil) |
| [`docs/15-bonus.md`](docs/15-bonus.md) | Bonus: **fórmula** (sistema de Hill, comprobada contra RCSB en los 1242 ligandos, subíndices tipográficos), **nombre** del ligando (limpio y acotado), mensaje de compartir; **cuatro modelos** (radios de van der Waals de Mantina 2009, mitades de color, átomos solitarios, por qué recrear los nodos); **tocar un enlace** (orden, longitud); **resaltar un elemento**; **doble toque** para orbitar un átomo; pruebas, 19 mutantes, comprobación en el móvil, trampas y alternativas | **Escrito** (lógica probada; escena sin probar en el móvil) |
| [`docs/09-captura-y-compartir.md`](docs/09-captura-y-compartir.md) | Captura del 3D y «compartir» (H4): por qué `view.draw()` da negro con un `SurfaceView`, `PixelCopy` como `suspend`, banda con el nombre, escritura atómica en la caché, `FileProvider` y permisos de URI, hoja del sistema, simulación, plan B | **Escrito** (parte pura probada; captura sin probar en el móvil) |
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

### Compilar e instalar sin Android Studio

El *wrapper* de Gradle (`./gradlew`) descarga la versión de Gradle del proyecto y compila desde la terminal. Hace falta un **JDK 21** (`JAVA_HOME`; sirve el `jbr` que trae Android Studio) y el **Android SDK** (`sdk.dir` en `local.properties` o `ANDROID_HOME`). En el campus, el SDK, el JDK y la caché de Gradle (`GRADLE_USER_HOME`) van en `~/goinfre` (la carpeta personal es pequeña).

```bash
./gradlew :app:testDebugUnitTest                              # pruebas en la JVM
./gradlew :app:testDebugUnitTest --tests '*BootSequenceTest'  # una clase
./gradlew :app:installDebug                                   # compilar e instalar en el móvil (USB)
adb shell am start -n dev.knowledge82.swiftyproteins/.MainActivity
adb logcat -s SwiftyProteins                                  # los avisos de la app
```

`:app:` es la ruta completa de la tarea (proyecto raíz → módulo `app`); sin ella, Gradle ejecuta la tarea en **todos** los módulos que la tengan (hoy solo hay uno). Las compilaciones *debug* se firman con la clave común del repositorio: se pueden instalar desde cualquier máquina sin desinstalar ([`docs/11-firma-de-depuracion.md`](docs/11-firma-de-depuracion.md)).

---

## Autores

- `<login 42 del miembro 1>`
- `<login 42 del miembro 2>`
