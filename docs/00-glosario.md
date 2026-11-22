# 00 · Glosario

> Estado: **vivo**. Se amplía cuando aparece un término nuevo en los documentos. Cada entrada indica dónde se desarrolla.
> Lo que no está verificado en esta sesión se marca como tal. Redactado el 18/11/2026; ampliado el 22/11/2026 con las secciones 14-22 (documentos 05 y 12-25).

## Índice

1. [Datos y organizaciones](#1-datos-y-organizaciones)
2. [Formato del fichero](#2-formato-del-fichero)
3. [Química y geometría](#3-química-y-geometría)
4. [Términos del 3D y del spike](#4-términos-del-3d-y-del-spike)
5. [Tecnologías del proyecto y términos de arquitectura](#5-tecnologías-del-proyecto-y-términos-de-arquitectura)
6. [Herramientas usadas solo para analizar](#6-herramientas-usadas-solo-para-analizar)
7. [Red y HTTP](#7-red-y-http)
8. [Android y Compose: el pegamento con la pantalla](#8-android-y-compose-el-pegamento-con-la-pantalla)
9. [La escena 3D de la molécula](#9-la-escena-3d-de-la-molécula)
10. [Icono y pantalla de inicio](#10-icono-y-pantalla-de-inicio)
11. [Captura y compartir](#11-captura-y-compartir)
12. [Diseño v2 «Cogitador»](#12-diseño-v2-cogitador)
13. [Firma de la app](#13-firma-de-la-app)
14. [Acceso y seguridad](#14-acceso-y-seguridad)
15. [Luz, fotogramas y rendimiento](#15-luz-fotogramas-y-rendimiento)
16. [Caché y carga por etapas](#16-caché-y-carga-por-etapas)
17. [Sonidos](#17-sonidos)
18. [Modelos e interacción molecular](#18-modelos-e-interacción-molecular)
19. [Interfaz avanzada: temas y animación](#19-interfaz-avanzada-temas-y-animación)
20. [Exportar y vídeo](#20-exportar-y-vídeo)
21. [Comparación de dos moléculas](#21-comparación-de-dos-moléculas)
22. [Vocabulario del Mechanicus (el *lore*)](#22-vocabulario-del-mechanicus-el-lore)

---

## 1. Datos y organizaciones

| Término | Qué es |
|---------|--------|
| **PDB** (*Protein Data Bank*) | Archivo mundial de estructuras 3D de proteínas, ácidos nucleicos y complejos. Nuestros ligandos son pequeñas moléculas que aparecen dentro de esas estructuras. |
| **wwPDB** (*worldwide PDB*) | Consorcio que **gestiona** el archivo PDB y define sus formatos. Lo forman, que sepamos, RCSB PDB (EE. UU.), PDBe (Europa), PDBj (Japón) y BMRB (RMN). *(Composición recordada de memoria, no releída aquí.)* |
| **RCSB PDB** | Miembro estadounidense del wwPDB y **el sitio del que descarga nuestra app**: `files.rcsb.org/ligands/view/{ID}.cif`. Es el host de ficheros; la web con buscador es `rcsb.org`. |
| **PDBe** (*PDB in Europe*) | Miembro europeo (EMBL-EBI). Publica sus propias versiones enriquecidas de los ficheros de ligandos (con columnas extra). Lo usamos solo como referencia en el documento 03; la app no lo usa. |
| **CCD** (*Chemical Component Dictionary*) | Diccionario con la definición de **cada** componente químico (ligando) del PDB: átomos, enlaces, coordenadas. Cada `.cif` de ligando es una entrada del CCD. |
| **Ligando** | Molécula pequeña (ion, azúcar, fármaco, cofactor...) asociada a una proteína. Se identifica con un código de 1 a 3 caracteres (`ATP`, `HEM`, `CL`, `001`). |
| **`ligands.txt`** | Lista de 1243 ids que acompaña al enunciado. Es lo que ve el usuario en la pantalla de lista. |

Se desarrolla en: documento 03, sección 3.4 y 3.5.

## 2. Formato del fichero

| Término | Qué es |
|---------|--------|
| **CIF** / **mmCIF** | Formato de texto de datos científicos (CIF) y su variante para macromoléculas (mmCIF/PDBx). Es el formato de nuestros ficheros. Detalle en el documento 03, sección 2. |
| **BinaryCIF** | Versión binaria y comprimida del mismo modelo de datos. **La app no la usa**; se menciona porque el análisis del corpus completo (documento 03, 3.5) se hizo sobre una copia binaria del CCD. |
| **Categoría** | Grupo de datos con un prefijo, p. ej. `_chem_comp_atom` (los átomos) o `_chem_comp_bond` (los enlaces). |
| **Tag** (etiqueta) | Nombre de una columna, p. ej. `_chem_comp_atom.atom_id`. |
| **`loop_`** | Palabra que abre una **tabla** de varias filas. Las filas no se separan por saltos de línea: se reconocen **contando columnas**. |
| **Token / tokenizador** | Token = pieza del texto (palabra, valor entre comillas, etiqueta...). Tokenizador = el código que corta el texto en tokens. Código en `CifTokenizer.kt`. |

## 3. Química y geometría

### 3.1 Coordenadas «de modelo» e «ideales»

Cada átomo trae **dos juegos** de posiciones x, y, z, en ångström (1 Å = 0,1 nm):

| Juego | Columnas | Qué es |
|-------|----------|--------|
| **Modelo** (*model*) | `model_Cartn_x/y/z` | Posiciones del ligando **en una estructura experimental concreta** (medida, por ejemplo, por cristalografía) que sirvió de referencia. En el fichero real `001` el campo `pdbx_model_coordinates_db_code` apunta a la entrada `1J4R`. Están expresadas en el sistema de coordenadas de **esa** estructura, por eso en `001` el centro de la molécula queda a unos 30 Å del origen. |
| **Ideales** (*ideal*) | `pdbx_model_Cartn_x/y/z_ideal` | Posiciones **calculadas** por software para una geometría «ideal» de la molécula, no medidas. Según la descripción general de wwPDB; *no hemos releído la fuente en esta sesión*. Suelen estar cerca del origen, pero **no siempre** (en el corpus hay ligandos a más de 200 Å). |

Ambos juegos son válidos y dan longitudes de enlace razonables, pero **están en sistemas de referencia distintos y no se pueden mezclar** dentro de una misma molécula. Algunos ligandos tienen `?` (dato ausente) en uno de los dos. La regla del proyecto (D10) es elegir **un juego completo por ligando** y centrarlo. Se desarrolla en el documento 03, secciones 3.4, 3.5 y 7.1.

### 3.2 Orden de enlace (`value_order`)

El **orden** de un enlace es cuántos pares de electrones comparten los dos átomos:

| Valor en el fichero | Significado | Ejemplo | Cuántos hay en `ligands.txt` |
|---------------------|-------------|---------|------------------------------|
| `SING` | enlace **sencillo** (1 par) | C–C, C–H | 48 437 |
| `DOUB` | enlace **doble** (2 pares) | C=O | 6 061 |
| `TRIP` | enlace **triple** (3 pares) | C≡N | 29 |
| `AROM` | enlace **aromático** (electrones repartidos por un anillo, como el benceno) | — | **0**: no aparece en ninguno de los 1243 |

Los anillos aromáticos **sí** aparecen, pero escritos de la forma clásica de Kekulé (enlaces sencillos y dobles alternados) y con una columna aparte, `pdbx_aromatic_flag = Y`. Por eso `AROM` no sale en `value_order`. El modelo del proyecto guarda el orden (`BondOrder`), pero la primera versión dibuja **un cilindro por enlace**, sea del orden que sea; dibujar dobles y triples como cilindros paralelos es un bonus posible.

### 3.3 Otros

| Término | Qué es |
|---------|--------|
| **CPK** | Esquema de colores por elemento (Corey-Pauling-Koltun): C gris o negro, H blanco, O rojo, N azul, S amarillo, P naranja... |
| **Ball-and-stick** (bolas y varillas) | Representación: una **esfera** por átomo y un **cilindro** por enlace. |
| **Centroide** | Punto medio de las posiciones de todos los átomos. Se resta para que la molécula quede centrada en el origen y la cámara gire alrededor de ella. |
| **Elemento** | Símbolo químico del átomo. El fichero lo escribe en mayúsculas (`CL`, `FE`); la app lo normaliza a `Cl`, `Fe`. |
| **Ion** | Átomo suelto con carga (`CL`, `ZN`, `NA`). En `ligands.txt` hay **39 de un solo átomo y sin enlaces**. |

## 4. Términos del 3D y del spike

| Término | Qué es | Dónde se desarrolla |
|---------|--------|---------------------|
| **Nodo** (*node*) | Objeto de la escena 3D de SceneView (una esfera, un cilindro o un contenedor). Puede tener nodos hijos. | Documento 02a |
| **Nodo pivote** (*pivot node*) | Nodo **vacío** colocado en el **punto medio de un enlace**, que gira alrededor del eje Y (`spin`). Dentro lleva el cilindro del enlace, inclinado alrededor de Z (`tilt`). Dos giros encadenados orientan el cilindro en **cualquier** dirección. Es como una bisagra: el pivote gira y el cilindro hijo se inclina dentro de él. Hizo falta porque colocar el giro directamente en el cilindro no funcionaba (SceneView lo sobrescribía). | Documento 02a, 9.10 y 9.11 |
| **Picking** | Averiguar qué objeto 3D hay bajo el dedo: se lanza un rayo desde el toque y se mira qué toca. | Documento 02b |
| **Collider / forma de colisión** | Forma invisible que se usa para ese rayo. Por defecto es una caja; la sustituimos por una esfera (más precisa) en D8. | Documento 02b |

## 5. Tecnologías del proyecto y términos de arquitectura

| Término | Qué es | En el proyecto |
|---------|--------|----------------|
| **Jetpack Compose** | Herramienta de Google para construir interfaces de Android **de forma declarativa**, en Kotlin. En vez de crear vistas y modificarlas a mano, se escriben funciones `@Composable` que **describen** la pantalla a partir de un *estado*; cuando el estado cambia, Compose vuelve a dibujar solo lo afectado (*recomposición*). Alternativa clásica: vistas XML («Views»). | D3. Compose BOM 2026.09.00 (Compose 1.12.1). El login, la lista, los avisos de error y la integración con la escena 3D se escriben con él. |
| **SceneView** | Librería de 3D (y realidad aumentada) para Android, construida sobre Filament, con una API pensada para Compose: la escena se declara como elementos (`SceneView { SphereNode(...) }`). Ahorra el código repetitivo de usar Filament «a pelo». | D4. Versión 4.69.4. Se leyó su código fuente en el spike. Documentos 02, 02a y 02b. |
| **Filament** | Motor de renderizado 3D de Google para Android y otras plataformas (el que realmente dibuja). SceneView es una capa por encima. *Que por debajo use OpenGL ES o Vulkan es lo que recordamos de su documentación; no se ha releído en esta sesión.* | Aparece de forma explícita entre las opciones del enunciado. Documento 02, sección 1. |
| **OpenGL ES** | API gráfica estándar para móviles (versión reducida de OpenGL). Se programa la GPU con *shaders* y se dibuja con listas de vértices. Muy potente y de **bajo nivel**: hay que escribir las mallas, las luces y la cámara. | Plan B del spike (no usado, porque SceneView pasó). Documento 02, sección 6. |
| **`GLSurfaceView`** | Vista clásica de Android que gestiona el contexto de OpenGL ES y un hilo de dibujo. Es la base de dibujar con OpenGL ES. Riesgo documentado: al pasar la app a segundo plano **el contexto de OpenGL puede perderse** y hay que recrear los recursos. | Plan B del spike. Documento 02, sección 6. |
| **ADR** (*Architecture Decision Record*) | Nota breve que registra **una decisión técnica**: qué se decidió, qué alternativas había, por qué y qué consecuencias tiene. Sirve para no olvidar el motivo y poder revisarlo con datos. | ADR-001 (capa 3D) en el documento 02a, sección 7. El documento 04 reunirá todos. |
| **Spike** | Experimento corto, con tiempo limitado, para **reducir una incertidumbre** (aquí: ¿SceneView sirve?). Su código es **provisional**: se aprovecha solo lo que quedó comprobado y se anota qué es temporal. | Documento 02, secciones 1 y 10. |

## 6. Herramientas usadas solo para analizar

| Término | Qué es |
|---------|--------|
| **PyPI** (*Python Package Index*) | Repositorio público de paquetes de Python (el que usa `pip install`). **No forma parte del proyecto.** Se mencionó porque el entorno de análisis no llega a RCSB, wwPDB ni PDBe, pero sí a PyPI; de ahí se descargó el paquete `biotite`, que incluye una copia binaria del CCD, para calcular las estadísticas del documento 03, 3.5. |
| **`cif_stats.py`** | Script desechable de Python que analiza ficheros `.cif`. No es código del proyecto. |
| **Prueba de mutación (manual)** | Estropear el código a propósito (p. ej. cambiar un `<=` por `true`) y comprobar que alguna prueba se pone en rojo. Si ninguna falla, esa parte del código no está protegida. |
| **Red de seguridad (`catch (Exception)`)** | Captura final en `CifParser.parse` que convierte cualquier excepción inesperada en un `Failure(Unexpected)` para que la aplicación no se cierre. Marca un fallo del parser, no del fichero. |
| **Enlace creíble / juego de coordenadas creíble** | Juego de coordenadas cuyo enlace más largo no pasa de 5 Å (D13). Un juego puede estar completo y aun así ser absurdo. |
| **Política estricta** | Ante un dato dudoso, fallar con un motivo en lugar de adivinar o descartar en silencio (D12). |
| **Corpus** (*corpus*) | El **conjunto completo de datos sobre el que se prueba**: aquí, los **1242 ligandos** de `ligands.txt` presentes en el Diccionario de Componentes Químicos (el 1243.º, `UNL`, no está en él). Se usa para medir cosas que un solo fichero no enseña (tamaño máximo, cuántos ligandos son iones, cuáles tienen coordenadas rotas) y para comprobar el parser **con todos**. Los datos del corpus salen de una copia **binaria**, no de ficheros de texto descargados (documento 03, 3.5). |
| **`CifFailure`** | Tipo cerrado (`sealed interface`, fichero `cif/CifFailure.kt`) con **todos los motivos** por los que un fichero CIF no se pudo convertir en molécula (16 variantes: comilla sin cerrar, fila truncada, falta una columna...). Viaja dentro de `ParseResult.Failure`. La interfaz solo muestra «Fichero corrupto»; el motivo concreto va al registro. Ver documento 03a, sección 5. |
| **`ParseResult`** | Lo que devuelve `CifParser.parse`: `Success(molecule)` o `Failure(failure)`. Nunca una excepción. Documento 03a, sección 10. |
| **`sealed interface`** | Interfaz de Kotlin con un **conjunto cerrado y conocido** de variantes. Un `when` sobre ella obliga a tratarlas todas: si se añade una variante y se olvida un caso, **no compila**. |

## 7. Red y HTTP

| Término | Qué es | En el proyecto |
|---------|--------|----------------|
| **HTTP** | Protocolo con el que un cliente pide algo a un servidor. Es **texto** sobre una conexión TCP: una petición (método, ruta, cabeceras) y una respuesta (código de estado, cabeceras, cuerpo). | Pedimos un fichero con un `GET`. Documento 06, sección 2. |
| **HTTPS / TLS** | HTTP dentro de una conexión **cifrada y autenticada** (el servidor demuestra con un certificado quién es). Si el certificado no vale, la conexión **falla** (`SSLHandshakeException`). | La URL de RCSB es `https`. No se desactiva nunca la comprobación del certificado. |
| **Código de estado** | Número de tres cifras con que el servidor resume su respuesta. 2xx éxito, 3xx redirección, 4xx fallo del cliente (`404` no existe), 5xx fallo del servidor. | `404` y `410` → «Ligando no encontrado»; el resto de errores → `ServerError`. |
| **Cabecera** (*header*) | Línea `Nombre: valor` de una petición o respuesta. | Enviamos `Accept-Encoding: identity` y `User-Agent`. |
| **`Content-Length`** | Cabecera con el **tamaño exacto del cuerpo** en bytes. Es la única forma de saber si el cuerpo **llegó entero**. | Si recibimos menos de lo anunciado, es `Incomplete`. Documento 06, sección 5. |
| **`Transfer-Encoding: chunked`** | El servidor no anuncia el tamaño: manda el cuerpo en trozos, cada uno precedido de su tamaño en hexadecimal, y acaba con un trozo de tamaño 0. | La librería lo reensambla; un cuerpo `chunked` sin trozo final lanza `IOException`. |
| **Compresión (`Content-Encoding: gzip`)** | El cuerpo viaja comprimido. Entonces `Content-Length` cuenta los bytes comprimidos. | Pedimos `identity` (sin compresión); con `Content-Encoding` no se compara el tamaño. |
| **Redirección** | Respuesta 3xx con una cabecera `Location`: «está en otra dirección». | La sigue la librería sola (probado http a http). |
| **Portal cautivo** (*captive portal*) | Red (hotel, aeropuerto) que responde **cualquier** petición con una página HTML de inicio de sesión y código `200`. | Es el caso en que la red «va bien» y el contenido no es un CIF: lo detecta el parser. Documento 06, sección 3, caso 11. |
| **Tiempo de espera** (*timeout*) | Límite de una espera. Hay **tres** distintas: de **conexión**, de **lectura** (silencio entre dos llegadas de datos) y **total** (todo el cuerpo). | 10 s, 10 s y 30 s (estimados). Solo el total para a un servidor que «gotea». Documento 06, sección 4. |
| **Goteo** (*slow drip*) | Servidor que manda un byte cada poco tiempo: nunca dispara el tiempo de lectura pero nunca acaba. | Lo para el reloj total. |
| **Hilo principal** (*main thread*) | El hilo que dibuja la pantalla y recibe los toques. Si se bloquea, la app se congela. Hacer red en él lanza `NetworkOnMainThreadException`. | `fetch` y `load` **bloquean**: se llamarán desde `Dispatchers.IO`. |
| **Bloqueante** | Una función que **para** el hilo que la llama hasta tener resultado. | `HttpFetcher.fetch` y `LigandRepository.load`. |
| **`Dispatchers.IO`** | Grupo de hilos de las corrutinas de Kotlin pensado para trabajo que espera (red, disco). | Lo usa el `ViewModel` para el trabajo lento del cargador (`LigandsViewModel`). |
| **Cancelación** | Parar un trabajo porque el resultado ya no interesa (el usuario salió de la pantalla). | Función `isCancelled: () -> Boolean` consultada antes de empezar y en cada vuelta de lectura. `Cancelled` no es un error. |
| **Permiso `INTERNET`** | Permiso de Android (`<uses-permission android:name="android.permission.INTERNET" />`) sin el cual la app no puede usar la red. Se concede al instalar, sin cuadro de diálogo (según recordamos de la documentación; no releído). | **Pendiente de añadir** en `AndroidManifest.xml`. |
| **`HttpURLConnection`** | Clase del JDK y de Android para hacer peticiones HTTP. API antigua y de bajo nivel, sin dependencias. | D14: es la que usamos. |
| **OkHttp** | Librería de red muy usada, con cancelación inmediata y límite de llamada completa. Se dice que `HttpURLConnection` de Android se apoya en ella (recordado, no verificado). | Alternativa a D14 si la prueba real lo pide. |
| **Descargador falso** (*fake*) | Implementación de la interfaz `HttpFetcher` que devuelve lo que se le diga, sin red. | Prueba del repositorio sin Internet. |
| **Servidor de prueba / bucle local** (*loopback*) | Servidor HTTP casero que solo escucha en `127.0.0.1` (el propio ordenador). | `TestHttpServer`, sobre `ServerSocket`. Documento 06a, sección 9. |
| **`ServerSocket` / `Socket`** | Clases del JDK para **esperar** conexiones (`ServerSocket`) y para **una** conexión ya establecida (`Socket`). | Base del servidor de prueba. |
| **Prueba de extremo a extremo** (*end to end*) | Prueba que atraviesa **todas** las capas reales (aquí: servidor casero → descargador → parser → molécula). | `LigandRepositoryTest`, sección «De extremo a extremo». |
| **Punto de inyección** | Parámetro (a menudo una función) que permite sustituir una pieza por otra en las pruebas. | `open: (URL) -> HttpURLConnection` del descargador. |
| **`data object`** | Objeto único de Kotlin (`object`) con `toString` y `equals` útiles. Se usa para los casos sin datos de una `sealed interface`. | `FetchResult.Timeout`, `LoadError.NotFound`... |
| **Retorno no local** | `return` dentro de una lambda *inline* (como la de `use { }`) que sale de la **función que la contiene**. | `return FetchResult.Cancelled` dentro del bucle de lectura. |
| **Error como valor** | Devolver el fallo como un resultado (de un tipo cerrado) en lugar de lanzar una excepción. El compilador obliga a tratar todos los casos. | D16. |
| **Corrutina** (*coroutine*) | Forma de escribir trabajo asíncrono **como código normal, de arriba abajo**: puede **suspenderse** (esperar sin bloquear el hilo) y reanudarse, incluso en otro hilo. Mucho más barata que un hilo. | `LigandLoader`. Documento 06b, sección 3. |
| **`CoroutineScope`** (ámbito) | El **dueño** de un grupo de corrutinas: si se cancela, se cancelan todas (*concurrencia estructurada*). | `viewModelScope` en la app; un ámbito de un solo hilo en las pruebas. |
| **`launch` / `Job`** | `launch` arranca una corrutina y devuelve un `Job`, su asa: `job.cancel()` la cancela. | `LigandLoader.load` guarda el `Job` para poder cancelarlo. |
| **`withContext` / *dispatcher*** | `withContext(dispatcher) { ... }` ejecuta un bloque **en otro hilo** y espera el resultado. El *dispatcher* decide en qué hilos: `Main` (principal), `IO` (esperas de red y disco), `Default` (cálculo). | El trabajo lento va en `workDispatcher` (`Dispatchers.IO` en la app). |
| **Cancelación cooperativa** | Cancelar **no mata** el hilo: las funciones de suspensión notan la cancelación y lanzan `CancellationException`. El código **bloqueante** no se entera: hay que preguntar (`isActive`). | `{ !isActive }` es el puente hacia el `isCancelled` del descargador. |
| **`CancellationException`** | La excepción con que una corrutina sabe que fue cancelada. **Hay que dejarla pasar**; si un `catch (Exception)` la traga, la corrutina cancelada sigue como si nada. | `catch (e: CancellationException) { throw e }` antes del `catch` general. |
| **`StateFlow`** | Contenedor observable con **siempre un valor actual**; avisa a los observadores cuando cambia (solo si el valor nuevo es distinto por `equals`). Hace que Compose redibuje solo. | `LigandLoader.state`. |
| **`typealias`** | Un **apodo** para un tipo (aquí, un tipo de función). No crea un tipo nuevo. | `LigandSource`. |
| **Constructor privado + fábrica** | La clase solo se crea por una función de su `companion object`, que garantiza que los datos pasan por la limpieza. | `LigandCatalog.fromText`. |
| **`ViewModel`** | Clase de Android que **sobrevive a los giros de pantalla** (la actividad se destruye y recrea; el `ViewModel`, no). Trae `viewModelScope`. | Envoltura mínima de `LigandLoader` y `LigandCatalog` (`LigandsViewModel`, documento 06c, sección 6). |
| **`LazyColumn`** | Lista de Compose que solo dibuja los elementos **visibles**. | Para mostrar hasta 1243 ids. |
| **Mutante equivalente** | Avería que **no cambia el comportamiento** (p. ej. quitar `requestMethod = "GET"`, que ya es el valor por defecto): ninguna prueba puede detectarla. | Documento 06a, sección 12. |

---

## 8. Android y Compose: el pegamento con la pantalla

Desarrollado en [`06c-pegamento-android-lista-y-carga.md`](06c-pegamento-android-lista-y-carga.md), sección 3.

| Término | Qué es | En el proyecto |
|---------|--------|----------------|
| **`Activity` y su recreación** | La actividad es la «ventana» de la app. Al **girar el móvil** (u otros cambios de configuración) Android la **destruye y crea otra**; lo que viva en sus variables se pierde. | Por eso el estado de la carga vive en el `ViewModel` y el texto buscado en `rememberSaveable`. |
| **`@Composable`** | Anotación de las funciones que **describen** una parte de la interfaz a partir de datos. No se llaman «a mano» para modificar la pantalla: Compose las ejecuta cuando los datos cambian. | Todas las pantallas de `ui/`. |
| **Recomposición** | Volver a ejecutar una función `@Composable` porque cambió un dato que **leyó**. Compose solo recompone lo afectado. Por eso el cuerpo de una función composable **se ejecuta muchas veces** y no debe tener efectos (escribir en el log, lanzar descargas) sin protegerlos. | `LaunchedEffect` para el log de errores. |
| **`State` / `mutableStateOf`** | Contenedor cuyo cambio provoca recomposición. Con `by` se lee y escribe como una variable. | `query` del buscador. |
| **`remember` / `remember(claves)`** | Guarda un valor **entre recomposiciones**; con claves, lo **recalcula solo si cambian**. No sobrevive a un giro. | El filtrado de la lista (`remember(catalog, query)`). |
| **`rememberSaveable`** | Como `remember`, pero guarda el valor en un `Bundle` y **sobrevive al giro y a que Android mate el proceso** (valores pequeños). | El texto del buscador. |
| **Elevar el estado** (*state hoisting*) | Que una pantalla **reciba** su estado y un `onCambio`, en vez de guardarlo. Quien está encima decide dónde vive. | `LigandListScreen` recibe `query`; vive en `SwiftyApp`. |
| **`collectAsStateWithLifecycle`** | Observa un `StateFlow` desde Compose y **deja de escuchar con la app fuera de pantalla**. | `SwiftyApp`. |
| **`LaunchedEffect(clave)`** | Ejecuta un bloque (con corrutinas) **una vez cuando la clave cambia**, no en cada recomposición. | Escribir el error técnico en el Logcat. |
| **`Scaffold` / márgenes del sistema (*insets*)** | `Scaffold` es la estructura estándar de Material 3 y entrega el margen que dejan las barras del sistema (`innerPadding`). Los *insets* son esos márgenes (barras, teclado). `imePadding()` añade el del teclado; `consumeWindowInsets` avisa de que ya se contó. | `SwiftyApp` y `LigandListScreen`. |
| **`AlertDialog` (diálogo modal)** | Ventana emergente que **bloquea** lo de debajo; «Atrás» la cierra. | Diálogos de «cargando» y de error. |
| **`BackHandler`** | Captura el botón «Atrás» del sistema mientras la pantalla está dibujada. | `MoleculeSummaryScreen`. |
| **Fábrica de `ViewModel`** (*factory*) | Objeto que le enseña a Android a **construir** un `ViewModel` con argumentos. | `LigandsViewModelFactory`. |
| **`assets/`** | Carpeta que se empaqueta tal cual dentro de la app; se lee con `context.assets.open(nombre)`. | `ligands.txt`. |
| **Función de extensión** | Función que se escribe fuera de una clase pero se llama como si fuera suya (`error.userMessage(id)`). | `LoadError.userMessage` y `canRetry`. |
| ***Smart cast*** | Tras comprobar `is T`, el compilador **trata el valor como `T`** sin conversión. No funciona con propiedades delegadas (`by`): hay que copiarlas a un `val`. | `val current = state` en `SwiftyApp`. |
| **Referencia a función** (`viewModel::open`) | Pasar una función como valor; equivale a `{ id -> viewModel.open(id) }`. | `SwiftyApp`. |
| **Logcat** | Registro de mensajes de la app que se ve en Android Studio (`Log.w`, `Log.e`), con filtro por etiqueta. | Etiqueta `SwiftyProteins`. |
| **ANR** (*Application Not Responding*) | Aviso de Android cuando el hilo principal está bloqueado varios segundos. | Motivo de no hacer red en el hilo principal. |
| **Relleno** (*stub*) | Versión mínima y falsa de una librería, escrita solo para que el compilador compruebe **nuestro** código. No prueba que la librería real tenga esa firma. | Comprobación de tipos de `ui/` (06c, sección 15). |

---

## 9. La escena 3D de la molécula

Desarrollado en [`07-escena-3d-de-la-molecula.md`](07-escena-3d-de-la-molecula.md). *(CPK, bolas y varillas, nodo pivote y forma de colisión están en la sección 4.)*

| Término | Qué es | En el proyecto |
|---------|--------|----------------|
| **Tabla de colores de Jmol** | Los colores por elemento del visor Jmol: la versión más usada de «CPK». No es un estándar oficial. | `ElementStyle` (D21); escrita de memoria. |
| **Ángulo de inclinación (*tilt*) y de giro (*spin*)** | Los dos ángulos con que se pone un cilindro vertical a apuntar de un átomo a otro: se inclina `tilt` alrededor de Z y se gira `spin` alrededor de Y. | `BondGeometry`; demostrado con matrices. |
| **Malla y detalle (`stacks`, `slices`)** | Una esfera 3D es una malla de triángulos; `stacks` y `slices` dicen en cuántas franjas se corta. Más franjas = más suave y más pesada. | 20 para las bolas, 12 para los hidrógenos (estimación). |
| **Material (Filament)** | El «color» y el aspecto de la superficie de un objeto 3D. Se crea uno por elemento y se reutiliza. | `createColorInstance`, un `HashMap` por elemento. |
| **Campo de visión (*field of view*)** | El ángulo que abarca la cámara. Con él se calcula a qué distancia cabe una molécula de un radio dado. | No comprobado en SceneView; de ahí que `DISTANCE_PER_RADIUS` sea una estimación. |
| **Plano lejano (*far plane*)** | Distancia máxima a la que la cámara dibuja; lo que está más allá se recorta. | En SceneView 4.69.4 vale **1000** (leído en el código); `15P` con los límites de zoom cabe (≈ 725 como máximo). Documento 07, 7 y 7.1. |
| **`key(...)` en Compose** | Da a un trozo de la interfaz una **identidad** propia, para que Compose no lo confunda con otro cuando cambia la lista de hijos. | `key(i)` por átomo, `key(-k - 1)` por enlace. |
| **`rememberUpdatedState`** | Guarda siempre **el último valor** de un parámetro para usarlo desde una lambda que se creó una sola vez. | `currentOnSelect` en `MoleculeScene`. |
| **Selección por rayo (*picking*)** | Averiguar qué objeto se toca lanzando un rayo desde la cámara. | Nombre de nodo `atom N` → índice del átomo. |
| **Hidrógeno oculto** | Un átomo H que no se dibuja (ni sus varillas), pero **sigue en el `Molecule`** y en el encuadre. | Interruptor «H» (D22). |
| **Distancia «*home*» y límites de zoom** | SceneView mide la distancia inicial de la cámara y limita el zoom como **múltiplo** de ella (por defecto, de 0,15 × a 8 ×). Con ligandos grandes el tope de acercamiento crece con ellos. | Corregido con límites **absolutos** en ångström (`MIN_CLOSE_DISTANCE`, `MAX_ZOOM_OUT_FACTOR`; D24). Documento 07, 7.1. |

## 10. Icono y pantalla de inicio

Desarrollado en [`07b-icono-y-pantalla-de-inicio.md`](07b-icono-y-pantalla-de-inicio.md) y [`08a-direccion-visual-y-prompt.md`](08a-direccion-visual-y-prompt.md).

| Término | Qué es | En el proyecto |
|---------|--------|----------------|
| **Pantalla de inicio (*splash*)** | Lo que se ve **mientras la app arranca**, antes de que dibuje su primer fotograma. La dibuja **el sistema** con el tema de la actividad. | La hoja de evaluación pide que se vea **≥ 1 s**. Se mantiene 1500 ms (D25). |
| **`core-splashscreen`** | Biblioteca de AndroidX que ofrece la API de pantalla de inicio de Android 12 en **todas** las versiones. | `installSplashScreen()` + `setKeepOnScreenCondition`. Versión 1.2.0 (sin resolver desde este entorno). |
| **Ventana de arranque (*starting window*)** | La ventana que el sistema pinta nada más tocar el icono, antes de ejecutar nuestro código. | Su tema: `Theme.SwiftyProteins.Starting`. |
| **Icono adaptable (*adaptive icon*)** | Icono en **dos capas** (fondo + primer plano) sobre un lienzo de 108 dp; el lanzador lo recorta con la forma que prefiera. La **zona segura** es el círculo central de 66 dp. | `mipmap-anydpi-v26/ic_launcher.xml`. Provisional; se rehará con el diseño nuevo. |
| **Contraste WCAG** | Cociente de luminancias entre texto y fondo; mínimo **4,5 : 1** (texto normal) o **3 : 1** (grande e iconos). | Se midió toda la paleta (documento 08a, 3); el rojo **solo como relleno**. |
| **`CutCornerShape`** | Forma de Compose con **esquinas biseladas** (cortadas). | Base del lenguaje de formas «Culto de la Máquina» (D26). |
| **VectorDrawable** | El formato vectorial **propio de Android** (un XML con caminos `pathData`). Android **no dibuja SVG**: hay que convertirlo. La sintaxis de `pathData` es la misma que la de `d` en SVG. | 18 dibujos en `res/drawable/`, convertidos con un conversor verificado (documento 08b, 5). |
| **Icono de píxeles (*legacy*)** | Las versiones anteriores a Android 8 no conocen los iconos adaptables: usan imágenes PNG por densidad. | `mipmap-*/ic_launcher.png` y `ic_launcher_round.png`. |
| **Fuente variable** | Un solo fichero con un **eje** (p. ej. grosor 400–900). Elegir un punto del eje desde Compose necesita API 26. | Cinzel se incluye en **dos cortes estáticos** (D28). |
| **OFL y «Reserved Font Name»** | Licencia libre de fuentes; permite incluirlas en una app con su licencia. Si la fuente declara un nombre reservado, una versión **modificada** no puede usarlo. | Share Tech Mono (RFN «Share») va **sin tocar**. |
| **Roles de color de Material 3** | Material pinta sus componentes por **papel** (`primary`, `surface`, `onSurface`, `outline`…), no por color fijo. | `Theme.kt` asigna cada rol a la paleta. |
| **Color dinámico** | En Android 12+, Material puede tomar los colores del **fondo de pantalla** del usuario. | **Desactivado**: la app tiene un solo aspecto. |
| **`SystemBarStyle`** | Cómo se pintan la barra de estado y la de navegación con `enableEdgeToEdge`. Por defecto sigue el modo claro/oscuro **del sistema**. | Forzado a oscuro: si no, iconos oscuros sobre fondo oscuro. |
| **`Dialog` frente a `AlertDialog`** | `AlertDialog` tiene una disposición fija (título, texto, botones a la derecha); `Dialog` es una ventana vacía donde se pone lo que se quiera. | Los diálogos de carga y error usan `Dialog` + panel propio. |

## 11. Captura y compartir

Desarrollado en [`09-captura-y-compartir.md`](09-captura-y-compartir.md).

| Término | Qué es | En el proyecto |
|---------|--------|----------------|
| **`SurfaceView`** | Una vista con **su propio búfer de GPU**, que el compositor del sistema pone detrás de la ventana. No se dibuja en el `Canvas` de la ventana: `view.draw()` la ve **negra**. | Donde dibuja SceneView (Filament). |
| **`TextureView`** | Alternativa que sí se dibuja dentro de la ventana (y se puede leer con `getBitmap()`), a costa de una copia extra por fotograma. | Plan B de la captura. |
| **`PixelCopy`** | API de Android (24+) que copia el último fotograma de una superficie de GPU a un `Bitmap`; responde por *callback*. | `MoleculeSharer.copy`. |
| **`suspendCancellableCoroutine`** | Convierte una API de *callback* en una función `suspend` que espera la respuesta. | `PixelCopy` con límite de 3 s. |
| **`FileProvider`** | Componente de AndroidX que da a un fichero privado una dirección `content://` y responde a las lecturas en nombre de la app. | `cache/shared/`, autoridad `<paquete>.fileprovider`. |
| **Permiso de URI** | `FLAG_GRANT_READ_URI_PERMISSION`: permiso de lectura **de un fichero concreto** para la app que recibe el *intent*. | En el *intent* de compartir, con `ClipData`. |
| **Escritura atómica** | Escribir en un fichero temporal y **renombrarlo** al terminar: nunca existe un fichero a medias con el nombre bueno. | `ShareImageStore.save` (`.part` → `.png`). |
| **Hoja de compartir** | La lista de apps que abre `Intent.createChooser(ACTION_SEND)`. | «Share ligand 001». |

## 12. Diseño v2 «Cogitador»

Desarrollado en [`08c-diseno-v2-cogitador.md`](08c-diseno-v2-cogitador.md).

| Término | Qué es | En el proyecto |
|---------|--------|----------------|
| **Pantalla de arranque (*boot screen*)** | Una pantalla propia de la app que se muestra al arrancar, antes del contenido. | El cogitador «despertando», 1600 ms: es el *launch screen* de la hoja de evaluación (D32). |
| **9-patch** | PNG con guías de 1 px que dicen qué franjas estiran; las esquinas quedan fijas. | El del kit no se usa (esquinas de 140 px, pixelado); marcos dibujados en código (D33). |
| **`drawable-nodpi`** | Carpeta de imágenes que Android **no escala** según la densidad de la pantalla. | Ilustraciones y texturas: el tamaño lo decide el código. |
| **Halo (*fringe*)** | Restos de fondo alrededor de un objeto recortado. | El del kit era **opaco**: se quitó por inundación a través de píxeles grises neutros (D34). |
| **Relleno por inundación (*flood fill*)** | Recorrer píxeles vecinos desde un punto mientras cumplan una condición. | Limpieza del halo, desde el borde de la imagen. |
| **Grupo de VectorDrawable** | `<group>` con traslación, escala y rotación para sus hijos. | Cada función de un `transform` de SVG = un grupo anidado (D35). |
| **Control negativo** | Un caso **deliberadamente erróneo** que la prueba **debe** detectar; demuestra que la prueba distingue. | Radio desviado 0,5: 182/255 frente al peor caso real 10/255. |
| **`ShaderBrush` / `ImageShader`** | Pincel de Compose que pinta repitiendo una imagen. | Texturas CRT y de hierro. |
| **Líneas de barrido (*scanlines*)** | Líneas horizontales finas que imitan una pantalla CRT. | Solo dentro de paneles, nunca sobre el 3D. |
| **`withFrameMillis`** | Suspende hasta el siguiente fotograma y da su hora. | Bucle de la pantalla de arranque sin consumir CPU. |
| **Movimiento reducido** | Preferencia del sistema (escala de animaciones a 0). | Para el servo-cráneo, el cursor y la escritura del arranque. |
| **Brillo de fósforo (*glow*)** | Una sombra de texto del **mismo color**, desenfocada y sin desplazamiento: el texto parece un tubo encendido. | `TextStyle.glowing` (D36). |
| **Fuente de píxeles** | Fuente dibujada en una rejilla de cuadrados (Silkscreen): a tamaño de lectura los cuadrados se ven. | Retirada: daba aspecto de 8 bits (D36). |

## 13. Firma de la app

Desarrollado en [`11-firma-de-depuracion.md`](11-firma-de-depuracion.md).

| Término | Qué es | En el proyecto |
|---------|--------|----------------|
| **Firma de un APK** | Huella criptográfica con una clave privada; el APK lleva el certificado. «Misma app» = mismo paquete + mismo certificado. | Una actualización con otra firma se rechaza (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`). |
| **Clave de depuración (*debug keystore*)** | La que el SDK crea sola en `~/.android/debug.keystore`, **una por máquina**, para firmar las compilaciones debug. | Sustituida por una **común** en `app/debug.keystore` (D37). |
| **`signingConfigs`** | Bloque de Gradle que dice con qué clave se firma cada tipo de compilación. | `getByName("debug")` apunta a la clave del repositorio. |
| **PKCS12** | Formato estándar de almacén de claves (desde Java 9); la contraseña del almacén y la de la clave coinciden. | Formato de `app/debug.keystore`. |
| **Clave de publicación (*release*)** | La que firmaría las versiones oficiales; **nunca** en el repositorio. | No existe: el proyecto no se publica. |

## 14. Acceso y seguridad

Desarrollado en [`05-acceso-y-seguridad.md`](05-acceso-y-seguridad.md) y [`05a-codigo-acceso.md`](05a-codigo-acceso.md).

| Término | Qué es | En el proyecto |
|---------|--------|----------------|
| **Hash de contraseña** | Función de un solo sentido: de la contraseña sale un resumen, y del resumen no se puede volver atrás. Al entrar se repite el cálculo y se comparan los resúmenes. | Nunca se guarda la contraseña, solo su hash. |
| **Argon2id** | Función de hash de contraseñas que ganó la *Password Hashing Competition* (2015), descrita en el RFC 9106. Es **lenta y gasta memoria** a propósito, lo que encarece los ataques con tarjetas gráficas. | 19 MiB, 2 pasadas, 1 carril: la configuración mínima de OWASP (`auth/PasswordHasher.kt`, Bouncy Castle). |
| **Sal** (*salt*) | Bytes aleatorios distintos por cuenta que se mezclan con la contraseña. Dos personas con la misma contraseña tienen hashes distintos, y las tablas precalculadas no sirven. | 16 bytes por cuenta, guardados junto al hash. |
| **OWASP** | Fundación que publica guías de seguridad; su *Password Storage Cheat Sheet* recomienda Argon2id y sus parámetros. | De ahí salen los parámetros. |
| **Comparación en tiempo constante** | Comparar dos secuencias tardando lo mismo, coincidan en uno o en todos los bytes, para no dar pistas por el tiempo de respuesta. | `MessageDigest.isEqual`. |
| **Android Keystore** | Almacén de llaves del sistema: la llave se usa **dentro** del móvil (a menudo en hardware seguro) y **no se puede sacar**. | Una llave para el fichero de cuentas y otra por cuenta para la huella (`auth/AndroidSecurity.kt`). |
| **AES-256-GCM** | Cifrado simétrico (AES con llave de 256 bits) en modo GCM, que además **detecta cualquier cambio** en lo cifrado (cifrado autenticado, AEAD). | El fichero de cuentas. |
| **`noBackupFilesDir`** | Carpeta privada de la app que Android **no** incluye en las copias de seguridad en la nube. | Ahí vive el fichero de cuentas. |
| **`BiometricPrompt`** | El diálogo **del sistema** para huella, cara o iris (biblioteca `androidx.biometric`). La app no ve la huella: recibe el resultado. | La tecla de huella del acceso. |
| **Biometría de clase 3** (*strong*) | La categoría más segura de Android (`BIOMETRIC_STRONG`): la única que puede desbloquear llaves del Keystore. | La única que se acepta. |
| **`CryptoObject`** | Un `Cipher` que el `BiometricPrompt` **desbloquea** con el dedo. El éxito no es un `true` falsificable: sin huella válida la llave no funciona. | La huella descifra un *token* de la cuenta. |
| **`setInvalidatedByBiometricEnrollment`** | Opción de una llave del Keystore: si se añade o quita una huella del móvil, la llave **deja de valer**. | Hay que volver a activar la huella con la contraseña. |
| **`ProcessLifecycleOwner`** | El ciclo de vida **del proceso entero**: su `ON_STOP` llega cuando **ninguna** pantalla de la app está visible, y espera un momento, así que un giro no lo dispara. | `ON_STOP` → `lock()`: el acceso siempre al volver (D43). |
| **Freno de intentos** (*throttle*) | Espera obligatoria tras varios fallos seguidos. | 5 fallos → 30 s, que se duplica hasta 5 min (`LoginThrottle`). |
| **`setRecentsScreenshotEnabled(false)`** | Android 13+: la miniatura de la app en Recientes sale en blanco. | El contenido no se ve en Recientes. No se usa `FLAG_SECURE`, que también dejaría en negro nuestras capturas. |
| **Autorrelleno** (`ContentType`) | Decirle al sistema qué es cada campo (usuario, contraseña, contraseña nueva) para que el gestor de contraseñas lo rellene. | Los tres campos del acceso. |

## 15. Luz, fotogramas y rendimiento

Desarrollado en [`12-luz-y-fps.md`](12-luz-y-fps.md), [`20-carga-por-etapas-y-detalle.md`](20-carga-por-etapas-y-detalle.md) y [`21-interfaz-avanzada.md`](21-interfaz-avanzada.md), §18-20.

| Término | Qué es | En el proyecto |
|---------|--------|----------------|
| **FPS** (*frames per second*) | Fotogramas que llegan a la pantalla por segundo. El enunciado pide 60; el A33 da 90. | El contador de la vista 3D, en todas las compilaciones (`scene/FpsMeter.kt`). |
| **IDLE / dibujo a demanda** | SceneView solo dibuja cuando **algo cambia**. Sin cambios no hay fotogramas, y el contador dice IDLE. | La prueba de que en reposo no se gasta nada; costó tres rondas en el móvil. |
| **Iluminación de tres puntos** | La de fotografía y cine: luz **principal** (*key*), de **relleno** (*fill*) y **contraluz** (*rim*). | 10 000 / 3 000 / 5 000 lux en el tema oscuro. |
| **Lux** | Unidad de iluminación. Filament usa unidades físicas: las luces se dan en lux. | `SceneLook`. |
| **IBL** (*image-based lighting*) | Luz ambiental que sale de una imagen del entorno, no de una lámpara. | La que SceneView añade por defecto, bajo las tres luces. |
| ***Tone mapper*** | Paso final de Filament que convierte la luz física en colores de pantalla. **Cambia** los colores que se le dan. | PBR Neutral; por eso el fondo claro se compensa (`SceneLook.skyboxColor`). |
| ***Skybox*** | El fondo de la escena 3D, detrás de todo. | Negro en el tema oscuro, pergamino en el claro. |
| **LOD** (*level of detail*) | Menos triángulos cuando hay muchos objetos. | `scene/MeshDetail.kt`: según el tamaño de la molécula, con un presupuesto de 204 800 triángulos. |
| **Montaje por lotes** | Crear los nodos de la escena unos cuantos por fotograma, no todos a la vez. | `scene/Assembly.kt` y la barra ASSEMBLY; el lote se adapta a lo que tardan los fotogramas. |
| **`contentPadding`** (SceneView) | Las partes de la vista tapadas por paneles: la cámara apunta a lo que queda. | La molécula se centra en la **ventana** entre paneles (`scene/Viewport.kt`). |
| **`autoCenterContent`** | Opción de SceneView que mueve todo el contenido **una vez**, en el primer fotograma con algo. | Apagada: con el montaje por lotes centraba solo los primeros átomos. |
| **`BoxWithConstraints`** | Contenedor de Compose que da su tamaño al contenido; **vuelve a ejecutar** su contenido en cada medida. | Una escena 3D dentro de uno no llegaba nunca a IDLE: las escenas están **fuera**. |
| **`@LayoutScopeMarker`** (`@DslMarker`) | Prohíbe usar, dentro de un ámbito de Compose, el receptor implícito de un ámbito **exterior**. | El error de Gradle con `maxWidth` dentro de un `Row` (documento 21, §18.6). |
| **Diagnóstico de fotogramas** | Una línea por segundo en Logcat (etiqueta `SwiftyFrames`): fotogramas, recomposiciones y qué parámetro cambió. | `scene/FrameDiagnosis.kt`; encontró la causa del FPS sin IDLE. |

## 16. Caché y carga por etapas

Desarrollado en [`13-cache-sin-conexion.md`](13-cache-sin-conexion.md) y [`20-carga-por-etapas-y-detalle.md`](20-carga-por-etapas-y-detalle.md).

| Término | Qué es | En el proyecto |
|---------|--------|----------------|
| **Caché de disco** | Copia local de lo descargado, para no volver a pedirlo. | `net/LigandCache.kt`: los `.cif`, en la carpeta de caché de la app. |
| **Caducidad** (*TTL*) | Tiempo durante el que una copia se usa sin preguntar al servidor. | 30 días. |
| **Tope de tamaño** | Al pasar de un límite, se borra lo más viejo. | 20 MB. |
| **Archivo sin conexión** | Abrir una copia de la caché cuando no hay red. | La pestaña dice «AUSPEX // OFFLINE ARCHIVE»; la celda, ARCHIVED. |
| **Etapa de carga** (`LoadStage`) | En qué punto está una carga: archivo, conexión, descarga, análisis, con los bytes recibidos. | El diálogo de carga, con porcentaje y KB. |
| **Estado único de carga** (`LoadState`) | `Idle`, `Loading`, `Loaded` o `Failed`, **uno solo** a la vez. | Imposible enseñar el indicador y un error a la vez. |
| **Carga silenciosa** | Abrir un ligando archivado sin diálogo ni sonido, salvo que tarde más de 600 ms. | `LoadingPolicy`, `LoadState.Loading.quiet` (documento 26). |
| **Tiempo mínimo del indicador** | Mantener el diálogo de carga visible un tiempo mínimo aunque el trabajo termine antes, para que no parpadee. | 2 s en una descarga, aplicado en el `LigandLoader` (documento 26). |

## 17. Sonidos

Desarrollado en [`14-sonidos.md`](14-sonidos.md).

| Término | Qué es | En el proyecto |
|---------|--------|----------------|
| **Síntesis de sonido** | Generar el sonido con cálculo (ondas, ruido, filtros), sin grabarlo. | Los diez sonidos, con `tools/sound/make_sounds.py` (numpy, scipy), reproducibles byte a byte. |
| **Síntesis modal** | Imitar un objeto golpeado sumando sus «modos»: senos que se apagan cada uno a su ritmo. | El metal golpeado de varios sonidos del cogitador. |
| **Ogg Vorbis** | Formato de audio comprimido y libre. | `res/raw/snd_*.ogg`. |
| **`SoundPool`** | Reproductor de Android para sonidos **cortos**: los decodifica una vez en memoria y los lanza casi sin retraso. | `sound/CogitatorSounds.kt`; `MediaPlayer` decodificaría cada vez. |
| **`SoundOnChange`** | Hacer sonar algo solo cuando cambia una etiqueta, no al recomponer ni al girar la pantalla. | Los sonidos de carga, acceso y error. |

## 18. Modelos e interacción molecular

Desarrollado en [`15-bonus.md`](15-bonus.md), [`16-regla-favoritos-ajustes.md`](16-regla-favoritos-ajustes.md) y [`19-etiquetas-y-resaltado.md`](19-etiquetas-y-resaltado.md).

| Término | Qué es | En el proyecto |
|---------|--------|----------------|
| **Espacio lleno** (*space-filling*, modelo CPK) | Cada átomo, una esfera de su radio de van der Waals; los vecinos se solapan. Enseña el **volumen** de la molécula. | Modelo «Space-filling». |
| **Radio de van der Waals** | La mitad de la distancia mínima entre dos átomos **no enlazados**: el «tamaño» de un átomo que no comparte electrones (C: 1,70 Å). | `scene/VanDerWaals.kt`, radios de **Mantina et al. 2009**, que extienden los de **Bondi 1964**. |
| **Varillas** (*sticks*) | Solo varillas gruesas, cada mitad del color de su átomo. | Modelo «Sticks». |
| **Alambre** (*wireframe*) | Solo los enlaces, como líneas finas. | Modelo «Wireframe»: cilindros de 0,05 Å. |
| **Fórmula de Hill** | Orden de la fórmula química: C, luego H, luego el resto alfabéticamente (todo alfabético si no hay carbono). | `molecule/MolecularFormula.kt`; igual que la de RCSB en los 1242. |
| **Orden de enlace** | Sencillo, doble, triple o aromático. | La ficha del enlace (sección 3.2). |
| **Ángulo de enlace** | El ángulo entre dos enlaces que comparten un átomo (A–B–C). | RULER con 3 átomos. |
| **Torsión** (ángulo diedro) | El giro entre dos planos A–B–C y B–C–D, de −180° a 180°. | RULER con 4 átomos (`atan2`, con signo). |
| **Billboard** | Un plano 3D que siempre mira a la cámara. | Las etiquetas de los elementos (LABELS). |
| **Órbita en un átomo** | Que la cámara gire alrededor de un átomo y no del centro de la molécula. | El doble toque. |
| **Favorito por cuenta** | La lista de favoritos va con el usuario que ha entrado. | `prefs/FavoritesStore.kt`. |

## 19. Interfaz avanzada: temas y animación

Desarrollado en [`21-interfaz-avanzada.md`](21-interfaz-avanzada.md) y [`23-tubo-destello-y-cursor.md`](23-tubo-destello-y-cursor.md).

| Término | Qué es | En el proyecto |
|---------|--------|----------------|
| **`CompositionLocal`** | Un valor que Compose pasa **implícitamente** a todo lo que hay debajo, sin parámetros. | La paleta del tema (`LocalSwiftyPalette`). |
| **`@ReadOnlyComposable`** | Marca de una función de Compose que solo **lee** (no dibuja): más barata. | Los colores `SwiftyColors.*`. No se pueden leer dentro de un `Canvas`: la paleta se lee antes. |
| **`SceneLook`** | El aspecto del 3D en cada tema: fondo, halos y luces. | Los «lighting adjustments» del modo oscuro (VII.2). |
| **Índice incluido** | Un fichero con la fórmula y los átomos de cada ligando, generado de antemano con el parser de la app. | `assets/ligand_index.tsv`: celdas ricas sin descargar nada. |
| **Barra de composición** | Una tira partida en la proporción de cada elemento, en su color. | La vista previa de cada celda. |
| **Tutorial** (*onboarding*) | Páginas de bienvenida que explican la app la primera vez. | Cuatro páginas; se repite desde Ajustes. |
| **Encendido del tubo** | Un televisor antiguo al encenderse: un punto que se estira en una línea y la línea que se abre en la imagen. | Solo en el arranque en frío (420 ms), y el arranque espera a que termine (documento 26). |
| **Barrido del haz** | Una línea brillante baja por la pantalla y la borra a negro; luego vuelve a bajar y dibuja la pantalla nueva, como el haz de un tubo que escribe la imagen línea a línea. | Todo cambio de pantalla salvo el arranque y una descarga (220 ms, `ui/ChannelChange.kt`; documento 26). |
| **Haz** (*beam*) | El chorro de electrones que recorre la pantalla de un tubo de arriba abajo y enciende el fósforo. | El color `BEAM` de la línea del barrido y del encendido. |
| **Corte** | Cambiar de pantalla sin ningún efecto. | Tras una descarga: el diálogo de carga ya fue la transición. |
| **Nieve** (*snow*) | La estática de un televisor sin señal. | La primera versión del cambio de pantalla; descartada porque evocaba un televisor de la Tierra, no el cogitador (documento 26, §3.3). Después se descartaron un pulso de luz verde pálido (demasiado brillante) un parpadeo a negro con resplandor (no se notaba en el móvil) y un pulso oscuro (no gustó). |
| **Cortina** | Lo que se **dibuja encima** de la pantalla en vez de transformarla. Un `SurfaceView` no sigue las transformaciones de Compose. | La cortina del tubo tapa también el 3D. |
| **Cobertura** | Un solo número de 0 (encendida) a 1 (apagada) del que sale todo el dibujo del tubo. | `CrtTransition.frame`. |
| ***Ease-in* / *smoothstep*** | Curvas de animación: `t²` (empieza lenta y acelera) y `t²(3−2t)` (suave en los dos extremos). | El tubo se cierra cada vez más deprisa; el destello se suaviza en los bordes. |
| **Capa *offscreen*** (`CompositingStrategy.Offscreen`) | Dibujar algo en su propio búfer antes de mezclarlo con el resto. | Necesaria para el destello. |
| **`BlendMode.SrcAtop`** | «Dibuja solo donde ya hay algo dibujado». | La luz del destello cae solo sobre las letras de «Swifty Proteins». |
| **Fase de dibujo** | La última de las tres fases de Compose (composición, medida, dibujo). Un valor leído **solo** en el dibujo repinta sin recomponer. | El tubo, el destello y el cursor no recomponen nada. |

## 20. Exportar y vídeo

Desarrollado en [`18-exportar-y-galeria.md`](18-exportar-y-galeria.md) y [`22-video-del-giro.md`](22-video-del-giro.md).

| Término | Qué es | En el proyecto |
|---------|--------|----------------|
| **`MediaStore`** | La base de datos de fotos, vídeos y audio del móvil. En Android 10+ una app añade sus ficheros **sin permisos**. | Pictures/ y Movies/Swifty Proteins. |
| **`IS_PENDING`** | Marca de una entrada de `MediaStore` que aún se está escribiendo: la galería no la enseña hasta que se quita. | Nunca aparece una imagen a medias. |
| **Tipo MIME** | La etiqueta del tipo de un fichero (`image/png`, `video/mp4`, `model/gltf-binary`). | Decide qué apps ofrece la hoja de compartir. |
| **MDL Molfile (`.mol`, V2000)** | Formato clásico de química: átomos, coordenadas y enlaces. | Validado con **RDKit** en los 1242. |
| **RDKit** | Biblioteca libre de química computacional, la referencia para leer y validar moléculas. | Solo para comprobar nuestros `.mol` (no va en la app). |
| **glTF 2.0 / `.glb`** | El formato estándar de modelos 3D («el JPEG del 3D»); `.glb` es su versión binaria en un solo fichero. | «Share 3D model»; el validador oficial de Khronos da 0 errores. |
| **`SceneRecorder`** | Grabador de vídeo de SceneView: el motor dibuja la escena **una segunda vez** en el codificador. | El vídeo de una vuelta. |
| **`SurfaceMirrorer`** | La pieza de SceneView que copia cada fotograma a otra superficie (aquí, la del codificador). | Sin coste cuando no se graba. |
| **`MediaProjection`** | API de Android para grabar **la pantalla**: pide consentimiento y un servicio en primer plano. | No se usa. |
| **H.264 / MP4** | El códec de vídeo más extendido y su contenedor. | 30 fps, una vuelta de 6 s. |
| **Plataforma giratoria** (*turntable*) | Girar el objeto, no la cámara, a velocidad constante. | `scene/Turntable.kt`: el vídeo y el SPIN de la comparación. |

## 21. Comparación de dos moléculas

Desarrollado en [`24-comparar-dos-moleculas.md`](24-comparar-dos-moleculas.md).

| Término | Qué es | En el proyecto |
|---------|--------|----------------|
| **Peso atómico estándar** | La masa media de un elemento con sus isótopos naturales, publicada por la IUPAC (C = 12,011). | `molecule/AtomicMass.kt`, los 50 elementos del catálogo. |
| **Masa molar** | La suma de los pesos atómicos de una molécula, en g/mol. | ATP: 507,2 g/mol. |
| **`formula_weight`** | La masa que el CCD da a cada ligando. | Nuestra referencia independiente: ≤ 0,06 g/mol en los 1241. |
| **Átomos pesados** | Los que no son hidrógeno. | Una fila de la tabla. |
| **Número ciclomático** | Enlaces − átomos + piezas: el número de **anillos** independientes de un grafo. | ATP: 49 − 47 + 1 = 3. |
| ***Union-find*** | Estructura para unir conjuntos y saber si dos elementos están en el mismo, casi en tiempo constante. | Cuenta las piezas de la molécula. |
| **Índice de Tanimoto (Jaccard)** | Parecido entre dos conjuntos: lo común dividido entre el total. | «Composition match»: solo la fórmula, no la forma. |
| **`Math.floorMod`** | El resto que nunca es negativo (`%` sí lo es con números negativos). | El ángulo del SPIN. |

## 22. Vocabulario del Mechanicus (el *lore*)

El estilo de la app es el de un **cogitador**, un ordenador del universo **Warhammer 40 000**, la estética del *Adeptus Mechanicus* (documento 08c). Las palabras del lore van en lo **decorativo** (paneles, pestañas, títulos); los mensajes que importan están en inglés llano (documento 25, §7.6). Esta tabla sirve a quien no conozca el universo:

| En la app | Del lore | Significa aquí |
|-----------|----------|----------------|
| **Cogitator** | El ordenador del Imperio | La app entera; «COGITATOR // MK.VII» en la cabecera |
| **Machine Spirit** (Espíritu Máquina) | El alma que habita cada máquina | El sistema: «MACHINE SPIRIT ... AWAKE» en el arranque |
| **Litany of Ignition** | La oración con que se enciende una máquina | La pantalla de arranque |
| **Noosphere** (Noosfera) | La red de datos del Mechanicus | La red y el acceso: «NOOSPHERE ACCESS // IDENTIFY», «The Noosphere is Silent» (sin conexión) |
| **Adept** (Adepto) | Un miembro del Mechanicus | El usuario: «ADEPT IDENTIFICATION» (entrar), «NEW ADEPT REGISTRY» (crear cuenta) |
| **Bio-auspex** | Un escáner biológico | La huella |
| **Data-Vault** | Un archivo de datos | La lista de ligandos: «Data-Vault Index» |
| **Pattern** | Un patrón; en el lore, las plantillas sagradas de fabricación (STC) | Un ligando: «Pattern ATP», «PATTERNS FOUND» |
| **Auspex** | El escáner o sensor | La vista 3D y su panel: «AUSPEX READOUT» (información), «AUSPEX QUERY» (búsqueda) |
| **Pict / pict-capture** | Imagen / captura | La imagen exportada: «PICT-CAPTURE // EXPORT», «Pict-capture ready» |
| **Vid-capture** | Grabación de vídeo | La grabación del giro |
| **Vox-caster** | El emisor de voz y sonido | Los ajustes de sonido |
| **Pict-display** | La pantalla | Los ajustes del tema y los colores |
| **Novitiate** (Noviciado) | La formación de un aprendiz | El tutorial: «NOVITIATE INSTRUCTION», «RITE I-IV» |
| **Rite** (Rito) | Un ritual | Cada página del tutorial; «RITES OF CONFIGURATION» (ajustes guardados) |
| **Servo-skull** (servo-cráneo) | Un cráneo con motores que vuela y asiste | La ilustración de la carga (flota) y del icono |
| **Servitor / Tech-priest** | Un siervo cibernético / un sacerdote de la máquina | Ilustraciones del acceso |
| **Binharic** | El idioma de las máquinas (binario sagrado) | Decorado: «BINHARIC PATTERN REGISTRY», «01001011» |
| **Forge** | Una forja, un mundo-fábrica | Decorado: «FORGE-NET ACTIVE»; «The Forge is Troubled» (error del servidor) |
| **Concordance** | Concordancia, puesta en común | La comparación de dos moléculas |
