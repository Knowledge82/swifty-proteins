# 09 · Captura de la escena y «compartir» (H4)

> **Estado:** escrito; la parte pura (guardar el fichero, nombres, textos, estados) tiene **16 pruebas** y **14 mutantes muertos** (233 pruebas propias en total). La parte Android (captura con `PixelCopy`, `FileProvider`, hoja de compartir) **pasa el comprobador de tipos contra declaraciones escritas a mano, pero no se ha compilado ni probado en el móvil**: es la hipótesis **H4** del spike, la que más riesgo tenía. Sección 9.

## Índice

1. [Qué pide el enunciado](#1-qué-pide-el-enunciado)
2. [Teoría: por qué capturar un 3D no es «hacer una foto de la vista»](#2-teoría-por-qué-capturar-un-3d-no-es-hacer-una-foto-de-la-vista)
3. [Las opciones y la elegida](#3-las-opciones-y-la-elegida)
4. [Teoría: compartir un fichero privado (FileProvider)](#4-teoría-compartir-un-fichero-privado-fileprovider)
5. [Mapa de ficheros](#5-mapa-de-ficheros)
6. [El código, pieza a pieza](#6-el-código-pieza-a-pieza)
7. [Simulación: un toque en «Share», paso a paso](#7-simulación-un-toque-en-share-paso-a-paso)
8. [Trampas y límites conocidos](#8-trampas-y-límites-conocidos)
9. [Qué está verificado y qué no](#9-qué-está-verificado-y-qué-no)
10. [Cómo probarlo en el móvil](#10-cómo-probarlo-en-el-móvil)
11. [Pruebas y mutación](#11-pruebas-y-mutación)
12. [Alternativas](#12-alternativas)
13. [Decisiones](#13-decisiones)
14. [Qué recordar](#14-qué-recordar)

---

## 1. Qué pide el enunciado

En la vista 3D: **un botón para compartir una captura** de la molécula (correo, mensajería, etc.: lo que el móvil tenga). Es la hipótesis **H4** del spike (documento 02): «se puede obtener una imagen de la escena de SceneView y compartirla». No se había probado: era **el mayor riesgo técnico pendiente** de la parte obligatoria.

---

## 2. Teoría: por qué capturar un 3D no es «hacer una foto de la vista»

En Android, cada `View` sabe **dibujarse en un `Canvas`** (`view.draw(canvas)`). Para una pantalla normal, «hacer una foto» es crear un `Bitmap`, envolverlo en un `Canvas` y pedirle a la vista que se dibuje ahí. **Con el 3D eso da un rectángulo negro.** La razón:

- SceneView (Filament) dibuja con la **GPU** en una **superficie aparte**: un `SurfaceView`. Un `SurfaceView` **no** dibuja su contenido en el `Canvas` de la ventana: tiene **su propio búfer**, que el compositor del sistema (*SurfaceFlinger*) coloca **detrás** de la ventana, en el hueco que el `SurfaceView` deja transparente.
- `view.draw(canvas)` solo recorre lo que pasa por el `Canvas`: el hueco sale **vacío**.

**`PixelCopy`** (API 24) es la herramienta de Android para esto: **copia el último fotograma** de una superficie de GPU (`SurfaceView`, una `Window`, una `Surface`) a un `Bitmap`. Es **asíncrona**: se pide la copia y el resultado llega por un *callback* con un código (`SUCCESS`, `ERROR_SOURCE_NO_DATA`, `ERROR_TIMEOUT`…).

Una consecuencia importante: **la imagen contiene solo el 3D**. La barra de arriba, la leyenda y la tarjeta son Compose, están en la **ventana**, no en el `SurfaceView`: **no salen** en la captura. Por eso se añade una **banda con el nombre del ligando** debajo de la imagen (sección 6.4).

---

## 3. Las opciones y la elegida

| Opción | Cómo | Pros | Contras |
|--------|------|------|---------|
| **A. `PixelCopy` del `SurfaceView` (elegida)** | Buscar el `SurfaceView` en el árbol de vistas y copiarlo | API 24 (nuestro mínimo); no cambia **nada** del dibujo actual | SceneView no «entrega» su `SurfaceView`: hay que **buscarlo** recorriendo el árbol |
| B. Pasar SceneView a `TextureView` | `surfaceType = SurfaceType.TextureSurface` y `textureView.getBitmap()` | Una línea para capturar | **Cambia la tubería de dibujo** que ya está comprobada en el A33; `TextureView` es más lento (copia extra por fotograma) |
| C. `PixelCopy` de toda la ventana | `PixelCopy.request(window, ...)` | Saldría también la interfaz | **API 26+**; y **no estoy segura** de que incluya el contenido del `SurfaceView` (no comprobado) |
| D. `readPixels` de Filament | Leer el búfer de color dentro del bucle de dibujo | Exacto, sin Android de por medio | Hay que engancharse al bucle interno de SceneView; mucho más código |

**Comprobado leyendo el código de SceneView 4.69.4** (`SceneView.kt`, rama `SurfaceType.Surface`): el `SurfaceView` se crea con `AndroidView(factory = { SurfaceView(ctx) ... })`. `AndroidView` mete una **vista Android de verdad** dentro de la jerarquía de la ventana. Por eso recorrer el árbol **la encuentra**. SceneView guarda una referencia interna (`ownerViewRef`), pero es local: **no se puede leer desde fuera**. Tampoco hay ninguna función de captura en la biblioteca (se buscó `PixelCopy`, `readPixels`, `screenshot`, `capture`).

---

## 4. Teoría: compartir un fichero privado (FileProvider)

Para compartir una imagen hay que **dársela a otra app** (Gmail, WhatsApp, Telegram…). Tres piezas:

1. **Dónde guardarla:** en la **caché privada** de la app (`cacheDir`). No necesita permisos de almacenamiento y Android puede vaciarla si falta espacio (es una imagen de usar y tirar).
2. **Cómo dejar que otra app la lea:** una ruta (`/data/user/0/dev.knowledge82.../cache/shared/ligand_001.png`) **no sirve**: las otras apps no pueden leer nuestras carpetas. **`FileProvider`** (de AndroidX) convierte el fichero en una dirección **`content://dev.knowledge82.swiftyproteins.fileprovider/shared_images/ligand_001.png`** y responde a las lecturas **en nuestro nombre**. Solo puede abrir las carpetas listadas en `res/xml/file_paths.xml` (aquí: **solo** `cache/shared/`).
3. **Quién puede leer:** el *intent* lleva `FLAG_GRANT_READ_URI_PERMISSION`: la app que el usuario elija recibe permiso **de lectura, de ese fichero, mientras dure** la tarea. Nadie más.

Y el mensaje en sí es un **`Intent(ACTION_SEND)`** con tipo `image/png`, la dirección en `EXTRA_STREAM` y un texto en `EXTRA_TEXT`. `Intent.createChooser(...)` abre la **hoja de compartir del sistema** (la lista de apps).

En el manifiesto:

```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data android:name="android.support.FILE_PROVIDER_PATHS" android:resource="@xml/file_paths" />
</provider>
```

- `${applicationId}` lo sustituye Gradle al montar el manifiesto: la «autoridad» queda `dev.knowledge82.swiftyproteins.fileprovider`, la misma que el código construye con `context.packageName`.
- `exported="false"` + `grantUriPermissions="true"` es **obligatorio**: el proveedor no está abierto a todo el mundo, solo a quien reciba un permiso puntual.

---

## 5. Mapa de ficheros

| Fichero | Qué hace | Puro |
|---------|----------|:----:|
| `share/ShareImageStore.kt` | Escribe la imagen en `cache/shared/` de forma segura y deja solo la última; nombre del fichero | **Sí** (con pruebas) |
| `share/ShareText.kt` | Pie de imagen, título de la hoja, causas de fallo con su mensaje, estados del botón | **Sí** (con pruebas) |
| `share/MoleculeSharer.kt` | Busca el `SurfaceView`, `PixelCopy`, banda con el pie, PNG, `FileProvider`, hoja de compartir | No (Android) |
| `ui/MoleculeScreen.kt` | Botón «Share» en la barra, línea de estado, lanzar y cancelar el trabajo | No |
| `ui/SwiftyComponents.kt` | `IconPlateButton` acepta `enabled` (el botón se apaga mientras trabaja) | No |
| `AndroidManifest.xml`, `res/xml/file_paths.xml` | El `FileProvider` | — |
| `res/drawable/ic_share.xml` | Icono del kit, convertido (0 píxeles distintos) | — |

---

## 6. El código, pieza a pieza

### 6.1 La función entera

```kotlin
// MoleculeSharer.kt · líneas 52–70
52  suspend fun share(context: Context, root: View, molecule: Molecule) {
53      val surface = findSurfaceView(root.rootView) ?: throw CaptureException(CaptureFailure.NO_SCENE)
54      val scene = copy(surface)
55      val captioned = withContext(Dispatchers.Default) {
56          try { addCaption(context, scene, ShareText.caption(molecule)) } finally { scene.recycle() }
57      }
58      val file = withContext(Dispatchers.IO) {
59          try {
60              ShareImageStore(File(context.cacheDir, ShareImageStore.DIRECTORY)).save(molecule.id) { out ->
61                  if (!captioned.compress(Bitmap.CompressFormat.PNG, 100, out)) throw IOException("PNG compression failed")
62              }
63          } catch (e: IOException) {
64              throw CaptureException(CaptureFailure.WRITE_FAILED, e)
65          } finally {
66              captioned.recycle()
67          }
68      }
69      openShareSheet(context, file, molecule)
70  }
```

Cinco pasos, cada uno **en el hilo que le toca**:

1. **Buscar el `SurfaceView`** (hilo principal: tocar vistas solo se puede ahí). Si no hay → `NO_SCENE`.
2. **Copiar** (hilo principal; `PixelCopy` responde en el hilo principal).
3. **Dibujar la banda** (`Dispatchers.Default`: trabajo de CPU con un bitmap de ~10 MB; fuera del hilo principal para no congelar la interfaz). El `finally` libera el bitmap original (`recycle()`).
4. **Escribir el PNG** (`Dispatchers.IO`: disco). Un fallo de escritura se convierte en `WRITE_FAILED`.
5. **Abrir la hoja de compartir** (hilo principal).

`withContext(...)` **suspende** la corrutina, hace el trabajo en otro hilo y **vuelve** al hilo de antes con el resultado: el código se lee de arriba abajo aunque salte entre hilos.

### 6.2 Encontrar el `SurfaceView`

```kotlin
// MoleculeSharer.kt · líneas 73–81
73  fun findSurfaceView(view: View): SurfaceView? {
74      if (view is SurfaceView) return view
75      if (view is ViewGroup) {
76          for (i in 0 until view.childCount) {
77              findSurfaceView(view.getChildAt(i))?.let { return it }
78          }
79      }
80      return null
81  }
```

Recorrido **en profundidad** del árbol de vistas desde la raíz de la ventana (`root.rootView`). En esta pantalla hay **exactamente un** `SurfaceView` (el de SceneView); la lista y los diálogos no tienen ninguno. Si un día hubiera dos (p. ej. un vídeo), habría que distinguirlos: está en la sección 8.

### 6.3 `PixelCopy` como función `suspend`

```kotlin
// MoleculeSharer.kt · líneas 84–108
 84  private suspend fun copy(surface: SurfaceView): Bitmap {
 85      val width = surface.width
 86      val height = surface.height
 87      if (width <= 0 || height <= 0) throw CaptureException(CaptureFailure.NOT_READY)
 88      val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
 89      val result = withTimeoutOrNull(COPY_TIMEOUT_MILLIS) {
 90          suspendCancellableCoroutine { continuation ->
 91              PixelCopy.request(surface, bitmap, { code -> continuation.resume(code) }, Handler(Looper.getMainLooper()))
 92          }
 93      }
 94      if (result != PixelCopy.SUCCESS) {
 95          // After a TIMEOUT (result == null) PixelCopy may still write into the bitmap later: do not recycle it,
 96          // let the garbage collector take it. After any real answer it is safe to free it now.
 97          if (result != null) bitmap.recycle()
 98          throw CaptureException(
 99              when (result) {
100                  null, PixelCopy.ERROR_TIMEOUT -> CaptureFailure.TIMEOUT
101                  PixelCopy.ERROR_SOURCE_NO_DATA -> CaptureFailure.NOT_READY
102                  PixelCopy.ERROR_SOURCE_INVALID -> CaptureFailure.NO_SCENE
103                  else -> CaptureFailure.OTHER
104              }
105          )
106      }
107      return bitmap
108  }
```

- **`suspendCancellableCoroutine`**: convierte una API de *callback* en una función que **espera**. Se pide la copia; cuando Android llama al *callback* con el código, `continuation.resume(code)` **despierta** la corrutina con ese valor.
- **`withTimeoutOrNull(3000)`**: si en 3 s no hay respuesta, devuelve `null` en vez de esperar para siempre. Una copia normal tarda milisegundos.
- **El bitmap tras un *timeout* NO se libera:** `PixelCopy` podría escribir en él **más tarde**; liberar un bitmap mientras alguien escribe en él es un cierre. Se deja al recolector de basura. Con una respuesta real (`result != null`) sí se libera.
- **Tamaño 0** (la vista aún no se ha medido) → `NOT_READY` antes de crear un bitmap de 0×0 (que lanzaría una excepción).
- Los códigos de error se traducen a **causas legibles** (`CaptureFailure`, sección 6.6).

### 6.4 La banda con el nombre

La captura solo contiene el 3D (sección 2). `addCaption` crea un bitmap **un 9 % más alto**, pinta el fondo `void`, la escena arriba y, en la banda, el texto **«Swifty Proteins · 001 · 87 atoms · 90 bonds»** en verde fósforo con la fuente de la app (**Silkscreen**, cargada con `ResourcesCompat.getFont`; si fallara, la monoespaciada del sistema). Así la imagen compartida **dice qué ligando es**. El texto sale de `ShareText.caption` (puro, probado). **Silkscreen es ancha:** medido, la frase ocupa 1008 px de los 1002 disponibles en una captura de 1080 px, así que se **mide** (`measureText`) y, si no cabe, se **reduce** el tamaño en proporción.

### 6.5 Guardar: escribir aparte y renombrar

```kotlin
// ShareImageStore.kt · líneas 30–53
30  fun save(ligandId: String, write: (OutputStream) -> Unit): File {
31      if (!directory.isDirectory && !directory.mkdirs()) throw IOException("cannot create $directory")
32      val name = fileNameFor(ligandId)
33      val target = File(directory, name)
34      val partial = File(directory, "$name.part")
35      try {
36          partial.outputStream().use(write)
37      } catch (e: Exception) {
38          partial.delete()
39          throw if (e is IOException) e else IOException("writing $name failed", e)
40      }
41      directory.listFiles()
42          ?.filter { it != partial && (it.name.endsWith(".png") || it.name.endsWith(".part")) && it.name != name }
43          ?.forEach { it.delete() }
44      if (target.exists() && !target.delete()) {
45          partial.delete()
46          throw IOException("cannot replace $name")
47      }
48      if (!partial.renameTo(target)) {
49          partial.delete()
50          throw IOException("cannot rename to $name")
51      }
52      return target
53  }
```

- **Escritura en dos tiempos:** primero `ligand_001.png.part`; solo cuando la escritura **terminó bien**, se renombra a `ligand_001.png`. Si algo falla a medias (disco lleno, excepción al comprimir), el `.part` se borra y **nunca** existe un PNG a medias con el nombre bueno. Es la técnica clásica de «escritura atómica».
- **Solo la última imagen:** se borran los **otros** `.png` de la carpeta y cualquier **`.part` viejo** que dejara una captura cancelada a mitad de escritura (no todo lo que haya: un fichero ajeno no se toca; hay prueba). La caché no crece con cada toque.
- **Nombre seguro:**

```kotlin
// ShareImageStore.kt · líneas 63–68
63  fun fileNameFor(ligandId: String): String {
64      val safe = ligandId.map { if (it.isLetterOrDigit() && it.code < 128 || it == '_' || it == '-') it else '_' }
65          .joinToString("")
66          .ifEmpty { "unknown" }
67      return "ligand_$safe.png"
68  }
```

Los identificadores ya llegan validados (1–3 letras o cifras, documento 06), pero un nombre de fichero es el sitio clásico para un truco de ruta (`../`): todo lo que no sea letra o cifra ASCII, `_` o `-` se convierte en `_`. **Defensa en profundidad**: barata y con prueba.

### 6.6 Textos y estados

`CaptureFailure` (6 causas, cada una con un mensaje que dice **qué hacer**) y `ShareStatus`:

| Estado | Línea de estado | Botón |
|--------|-----------------|-------|
| `Idle` | (nada) | activo |
| `Working` | «Preparing pict-capture…» (diseño v2) | **apagado**, muestra «...» (`canStart = false`: un segundo toque no abre dos hojas) |
| `Done` | «Pict-capture ready» (3 s) | activo |
| `Failed(causa)` | el mensaje de la causa, en rojo de error (3 s) | activo (se puede reintentar) |

### 6.7 En la pantalla

En `MoleculeScreen`: `rememberCoroutineScope()` da un *scope* **ligado a la pantalla**: si el usuario vuelve a la lista a mitad de captura, el trabajo **se cancela** (no queda nada a medias en segundo plano). `CancellationException` **se vuelve a lanzar** (no es un fallo: es «la pantalla se fue»). Las demás excepciones se convierten en `Failed` y la causa técnica va al Logcat. Un `LaunchedEffect(share)` borra «Image ready» o el error a los 3 s.

---

## 7. Simulación: un toque en «Share», paso a paso

| t | Qué pasa | Estado | Pantalla |
|--:|----------|--------|----------|
| 0 | Toque | `Idle` → `Working` | «Preparing pict-capture…», tecla «...» apagada |
| 0 | `findSurfaceView`: decorView → … → `AndroidView` → **`SurfaceView`** 1080×2100 | | |
| ~5 ms | `PixelCopy` responde `SUCCESS` | | |
| ~60 ms | Banda dibujada (Default): bitmap 1080×2197 | | |
| ~250 ms | PNG escrito (IO): `cache/shared/ligand_001.png` (~0,5–2 MB) | | |
| ~260 ms | `FileProvider` → `content://…/shared_images/ligand_001.png`; se abre la hoja | `Done` | «Pict-capture ready» bajo la hoja |
| … | El usuario elige Telegram: Telegram **lee** el fichero con el permiso concedido | | |
| +3 s | `LaunchedEffect` | `Idle` | la línea desaparece |

*(Los tiempos son estimaciones, no medidas.)*

**Variantes:** (a) **Toque doble rápido**: el segundo toque ve `Working` y no hace nada. (b) **«Atrás» a mitad**: el *scope* se cancela; si estaba escribiendo, puede quedar un `.part` en la caché, que **el siguiente** `save` borra (hay prueba). (c) **Ninguna app puede recibir imágenes** (raro): `NO_RECEIVER`.

---

## 8. Trampas y límites conocidos

1. **La interfaz no sale en la imagen**, solo el 3D + la banda. Es intencionado (una captura «limpia» de la molécula), y está explicado en la sección 2.
2. **Un `.part` huérfano** puede quedar si la escritura se cancela en el instante exacto; **el siguiente compartir lo borra** (prueba `aPartFileLeftByACancelledCaptureIsCleanedUp`).
3. **Dos `SurfaceView` en pantalla** (no ocurre hoy) harían que se capture el primero encontrado.
4. **`PixelCopy` copia el último fotograma presentado.** SceneView dibuja **bajo demanda** (`FrameRatePolicy.OnDemand`): con la escena quieta el último fotograma es el que se ve. Si se toca «Share» **mientras se gira**, sale el fotograma de ese instante.
5. **Tamaño de la imagen:** la resolución del `SurfaceView` (en el A33 ~1080×2100): PNG de 0,5–2 MB. Si alguna app lo rechaza por tamaño, la alternativa es JPEG (12.4).
6. **`startActivity` desde un `Context` que no es una `Activity`** exigiría `FLAG_ACTIVITY_NEW_TASK`. Aquí el `Context` es `LocalContext.current`, que en `setContent` **es la actividad**. *(Creído, no comprobado en el móvil.)*
7. **El fichero se borra al compartir el siguiente.** Si la app de destino lee **tarde** (algunas suben en segundo plano), podría encontrar el fichero ya sustituido. Riesgo bajo; alternativa: nombres con hora y limpieza de los de más de un día.

---

## 9. Qué está verificado y qué no

**Verificado:**
- La parte pura: **16 pruebas** (`ShareImageStoreTest` con carpetas temporales reales, `ShareTextTest`), **14 mutantes muertos**.
- **Leído en el código de SceneView 4.69.4:** que dibuja en un `SurfaceView` creado con `AndroidView` (luego está en el árbol de vistas), y que no trae función de captura.
- El icono `ic_share`: 0 píxeles distintos frente al SVG del kit.
- El código Android pasa el comprobador de tipos contra **declaraciones escritas a mano** de `PixelCopy`, `FileProvider`, `Intent`, `Bitmap`…

**NO verificado (todo en el móvil):**
- **Que `PixelCopy` devuelva la escena** (y no negro) con el `SurfaceView` de Filament. Es **la** pregunta de H4.
- Que `findSurfaceView` lo encuentre (depende de que Compose meta la vista en el árbol como espero).
- Que la hoja de compartir se abra y que **Gmail/Telegram/WhatsApp** reciban la imagen (permiso por `ClipData` + bandera).
- Los valores de los códigos de `PixelCopy` (el código usa las constantes, no números).
- La banda: tamaño y legibilidad del texto.
- Tiempos y memoria con la pantalla del A33.

---

## 10. Cómo probarlo en el móvil

| # | Qué hacer | Qué debe pasar |
|--:|-----------|----------------|
| 1 | Abrir `001`, tocar **SHR** | «Preparing pict-capture…» y, enseguida, la **hoja de compartir** del sistema |
| 2 | Elegir **Guardar en Drive / Archivos** o enviarte un mensaje a ti mismo | La imagen: **la molécula** (no un rectángulo negro) y abajo la banda **«Swifty Proteins · 001 · 87 atoms · 90 bonds»** |
| 3 | Girar la molécula, ocultar «H» y compartir otra vez | La imagen refleja lo que se ve (sin hidrógenos) |
| 4 | Tocar Share **dos veces** muy rápido | Una sola hoja |
| 5 | Tocar Share y **«Atrás»** enseguida | Vuelve a la lista sin error ni cierre |
| 6 | Si sale **negra**: copiar el Logcat filtrando `SwiftyProteins` | Es H4 fallando; plan B: alternativa 12.1 (`TextureView`) |

---

## 11. Pruebas y mutación

| Fichero | Pruebas | Qué demuestra |
|---------|--------:|---------------|
| `ShareImageStoreTest.kt` | 10 | Nombre por ligando; `../` no sale de la carpeta; id vacío o no latino; crea la carpeta y escribe; **solo queda la última**; volver a compartir reemplaza; **un fallo a medias no deja nada y conserva la anterior**; no toca ficheros ajenos; carpeta imposible → `IOException`; **un `.part` viejo se limpia** |
| `ShareTextTest.kt` | 6 | Pie con app, id, átomos y enlaces; singular («1 atom»); título de la hoja; mensajes de fallo legibles y distintos; segundo toque ignorado; línea de estado del diseño |

| # | Avería | Resultado |
|--:|--------|-----------|
| 1 | Nombre sin limpiar caracteres | Muerto |
| 2 | Aceptar letras no ASCII | Muerto |
| 3 | Sin nombre de reserva para id vacío | Muerto |
| 4 | No borrar el `.part` al fallar | Muerto |
| 5 | Borrar también ficheros ajenos | Muerto |
| 6 | No borrar las imágenes viejas | Muerto |
| 7 | No crear la carpeta | Muerto |
| 8 | Escribir directamente con el nombre final | Muerto |
| 9 | Singular mal (`n == 0`) | Muerto |
| 10 | Permitir el segundo toque | Muerto |
| 11 | Sin «Image ready» | Muerto |
| 12 | Mensaje de «sin app receptora» genérico | Muerto |
| 13 | No limpiar los `.part` viejos | Muerto |
| 14 | Borrar también el `.part` que se está escribiendo | Muerto |

---

## 12. Alternativas

| # | Alternativa | Cuándo |
|--:|-------------|--------|
| 12.1 | **`TextureView`** (`surfaceType = SurfaceType.TextureSurface`) + `getBitmap()` | **Plan B si `PixelCopy` da negro** |
| 12.2 | `PixelCopy` de la **ventana** (API 26+) para incluir la interfaz | Si se quiere «la pantalla entera» |
| 12.3 | `readPixels` de Filament | Si 12.1 y A fallan |
| 12.4 | JPEG en vez de PNG | Ficheros 5–10 veces menores; PNG es sin pérdida y la escena tiene grandes zonas lisas (comprime bien) |
| 12.5 | Guardar en la **galería** (`MediaStore`) además de compartir | Bonus posible; necesita más código y, antes de Android 10, permiso |
| 12.6 | Compartir los **datos** (el fichero CIF) además de la imagen | Bonus posible |

---

## 13. Decisiones

| # | Decisión | Estado | Motivo |
|---|----------|--------|--------|
| D30 | La captura es **`PixelCopy` del `SurfaceView` de SceneView**, encontrado recorriendo el árbol de vistas; la imagen es **solo el 3D + una banda con el nombre** | Provisional (H4 sin probar) | API 24, sin tocar el dibujo comprobado; plan B: `TextureView` |
| D31 | Se comparte con **`FileProvider` desde la caché** (`cache/shared/`, solo la última imagen, escritura en dos tiempos) y la **hoja del sistema** | Decidida | Sin permisos de almacenamiento; acceso solo a la app elegida y solo a ese fichero |

---

## 14. Qué recordar

- **Un `SurfaceView` no se dibuja en el `Canvas` de la ventana**: `view.draw()` da negro. Para copiar una superficie de GPU está **`PixelCopy`** (asíncrono, API 24).
- **`suspendCancellableCoroutine` + `withTimeoutOrNull`** convierten un *callback* en una espera con límite; tras un *timeout*, no liberar lo que el *callback* aún podría tocar.
- **`withContext(Dispatchers.Default / IO)`**: CPU y disco fuera del hilo principal; el código sigue leyéndose de arriba abajo.
- **Escribir aparte y renombrar**: nunca hay un fichero a medias con el nombre bueno.
- **`FileProvider`** convierte un fichero privado en `content://`; **`FLAG_GRANT_READ_URI_PERMISSION`** da permiso solo a la app elegida; `exported="false"` + `grantUriPermissions="true"`.
- **El *scope* de la pantalla** cancela el trabajo si el usuario se va; `CancellationException` se relanza, no es un error.
- **H4 sigue siendo la gran incógnita**: si la imagen sale negra, plan B: `TextureView`.
