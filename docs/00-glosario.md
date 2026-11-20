# 00 · Glosario

> Estado: **vivo**. Se amplía cuando aparece un término nuevo en los documentos. Cada entrada indica dónde se desarrolla.
> Lo que no está verificado en esta sesión se marca como tal. Redactado el 18/11/2026.

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
| **Plano lejano (*far plane*)** | Distancia máxima a la que la cámara dibuja; lo que está más allá se recorta. | Riesgo para `15P` (≈ 230 Å de distancia); sin comprobar. |
| **`key(...)` en Compose** | Da a un trozo de la interfaz una **identidad** propia, para que Compose no lo confunda con otro cuando cambia la lista de hijos. | `key(i)` por átomo, `key(-k - 1)` por enlace. |
| **`rememberUpdatedState`** | Guarda siempre **el último valor** de un parámetro para usarlo desde una lambda que se creó una sola vez. | `currentOnSelect` en `MoleculeScene`. |
| **Selección por rayo (*picking*)** | Averiguar qué objeto se toca lanzando un rayo desde la cámara. | Nombre de nodo `atom N` → índice del átomo. |
| **Hidrógeno oculto** | Un átomo H que no se dibuja (ni sus varillas), pero **sigue en el `Molecule`** y en el encuadre. | Interruptor «H» (D22). |
