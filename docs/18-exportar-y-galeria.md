# 18 · Exportar: galería, PNG, JPEG, `.cif`, `.mol` y modelo 3D `.glb`

> **Estado:** **escrito**. Lógica pura **probada en la JVM** (476 + 6 pruebas; 13 mutantes, 12 muertos y 1 **equivalente**, §7). **Validado con herramientas externas** sobre el corpus: los `.mol` de los **1242** ligandos leídos por **RDKit**; 25 `.glb` (5 ligandos × 4 modelos + sin hidrógenos) con el **validador oficial de Khronos**: **0 errores, 0 avisos**. La galería y el menú **compilan contra *stubs***; **sin probar en el móvil** (§8).

## Índice

1. [Por qué este documento](#1-por-qué-este-documento)
2. [El menú de exportación](#2-el-menú-de-exportación)
3. [Guardar en la galería](#3-guardar-en-la-galería)
4. [`.mol`: el formato de los químicos](#4-mol-el-formato-de-los-químicos)
5. [`.glb`: un modelo 3D estándar](#5-glb-un-modelo-3d-estándar)
6. [Cargas formales: un dato que faltaba](#6-cargas-formales-un-dato-que-faltaba)
7. [Pruebas, mutación y validación externa](#7-pruebas-mutación-y-validación-externa)
8. [Cómo probarlo en el móvil](#8-cómo-probarlo-en-el-móvil)
9. [Trampas](#9-trampas)
10. [Alternativas](#10-alternativas)
11. [Decisiones](#11-decisiones)
12. [Qué recordar](#12-qué-recordar)

---

## 1. Por qué este documento

- **Hoja de evaluación, punto 16 (obligatorio):** «compartir el modelo con el botón de compartir **y guardarlo en el álbum de fotos**». Había «compartir», no «guardar en el álbum». Desde la hoja de compartir a veces se puede guardar, pero **depende del móvil**. Con la parte obligatoria imperfecta, **los bonus no se evalúan**: era el fallo más caro del proyecto.
- **Enunciado VII.5:** «exportar la vista en distintos formatos de **imagen** (PNG, JPEG) **o incluso un fichero de modelo 3D**».

## 2. El menú de exportación

**SHARE** abre «PICT-CAPTURE // EXPORT» con seis opciones (`ExportFormat`):

| Opción | Qué hace | Fichero |
|--------|----------|---------|
| Share image · PNG | La escena con su banda (documento 09), sin pérdida | `ligand_ATP.png` |
| Share image · JPEG | Lo mismo, calidad 92: unas tres veces más pequeño, las varillas y el texto siguen nítidos | `ligand_ATP.jpg` |
| **Save to gallery** | La imagen PNG al **álbum «Swifty Proteins»** de la galería | `SwiftyProteins_ATP_20261121_162400.png` |
| Share ligand file · `.cif` | El fichero **original** de RCSB (de la caché, documento 13) | `ligand_ATP.cif` |
| Share chemical model · `.mol` | La molécula en **MDL Molfile V2000** (§4) | `ligand_ATP.mol` |
| Share 3D model · `.glb` | **Lo que se ve** (modelo actual, con o sin hidrógenos) en **glTF 2.0** (§5) | `ligand_ATP.glb` |

- **Una sola carpeta compartida** (`cacheDir/shared`, la única que abre el `FileProvider`) y **solo el último fichero**: `ShareImageStore` acepta ahora cualquier extensión **de una lista cerrada** (`png`, `jpg`, `cif`, `mol`, `glb`, `mp4`). Una extensión que viniera de fuera nunca formaría un nombre de fichero.
- **Tipo MIME del *intent*:** las imágenes van como `image/png` / `image/jpeg`. Los ficheros químicos tienen tipos registrados (`chemical/x-mdl-molfile`, `model/gltf-binary`), pero **casi ninguna app del móvil los anuncia**: un *intent* que nadie acepta abre una hoja de compartir **vacía**. Van como `application/octet-stream` (correo, Drive, Telegram, WhatsApp los aceptan) y la extensión dice qué son.
- **El `.cif` se copia** a la carpeta compartida: el `FileProvider` nunca abre la caché de ligandos. Si el usuario vació la caché, «The original file is gone. Open the ligand again with a connection.».
- El estado: «Preparing pict-capture…» / «Preparing .mol file…», y luego «Pict-capture ready», «File ready» o **«Saved to Pictures/Swifty Proteins»**.

## 3. Guardar en la galería

Android cambió las reglas del almacenamiento en la **versión 10** (*scoped storage*). `GallerySaver` hace las dos cosas:

| Android | Cómo | Permiso |
|---------|------|---------|
| **10 o más** (el A33) | **`MediaStore`**, la base de datos de medios del sistema: se inserta una entrada con nombre, tipo y `RELATIVE_PATH = Pictures/Swifty Proteins` | **Ninguno**: una app siempre puede **añadir** sus propias imágenes |
| 7 – 9 | Un fichero en `Pictures/Swifty Proteins` y aviso al **escáner de medios** para que la galería lo vea al instante | `WRITE_EXTERNAL_STORAGE`, declarado con **`maxSdkVersion="28"`** (en Android 10+ ni aparece) y pedido en el momento, la primera vez |

**`IS_PENDING`:** la entrada se crea **pendiente** (invisible para la galería), se escribe la imagen y solo entonces se publica (`IS_PENDING = 0`). Si algo falla a medias, la entrada **se borra**: la galería nunca muestra una imagen vacía o rota.

**Nombre:** `SwiftyProteins_<id>_<fecha y hora UTC>.png` (`GalleryName`): dos guardados no se pisan, y el orden por nombre es el orden por fecha. El id se limpia: nada de `/` ni `..` en un nombre de fichero.

## 4. `.mol`: el formato de los químicos

**MDL Molfile V2000**: el formato de intercambio de toda la química (RDKit, Open Babel, ChemDraw, PyMOL, Avogadro). Texto con **columnas de ancho fijo** (viene de la época de las tarjetas perforadas: la **posición** de cada carácter importa, espacios incluidos):

```
ATP                                                 ← nombre
  SwiftyPr          3D                              ← programa; «3D» en las columnas 21-22
ADENOSINE-5'-TRIPHOSPHATE                           ← comentario
 47 49  0  0  0  0  0  0  0  0999 V2000             ← recuento: átomos, enlaces, … «999 V2000»
    1.2340   -0.5000    2.0000 P   0  0  0 …        ← un átomo: x y z (10 columnas cada uno, 4 decimales), símbolo
  1  2  2  0  0  0  0                               ← un enlace: átomo, átomo (desde 1), tipo
M  CHG  2   5  -1  12   1                           ← cargas formales
M  END
```

- **Tipos de enlace:** 1 sencillo, 2 doble, 3 triple, 4 aromático, **8 «cualquiera»** para lo que leímos como `OTHER` (no se adivina).
- **Índices desde 1** en el fichero (los nuestros empiezan en 0): el error más clásico del formato, con su prueba y su mutante.
- **Cargas dos veces:** el código antiguo del bloque de átomos (3 = +1, 5 = −1…) para lectores muy viejos, y las líneas **`M  CHG`** (hasta 8 átomos por línea), que mandan cuando existen y admiten −15…+15.
- **Sin fecha** en la línea del programa: la misma molécula da **siempre el mismo fichero** (reproducible, comparable).
- **Límite:** V2000 cuenta en 3 columnas, máximo **999** átomos o enlaces. Nuestros ligandos llegan a 256; más se rechaza (haría falta V3000).

## 5. `.glb`: un modelo 3D estándar

**glTF 2.0** (Khronos Group, el consorcio de OpenGL y Vulkan) es el formato abierto de modelos 3D para transmitir y mostrar: «el JPEG del 3D». `.glb` es su variante **binaria de un solo fichero**. Lo abren Blender, el Visor 3D de Windows, el Scene Viewer de Android, three.js…

### 5.1 Qué se exporta

**Exactamente lo que se ve**: el modelo actual (bolas y varillas, espacio lleno, varillas, alambre) y los hidrógenos solo si se ven. 1 unidad = 1 Å. **Una malla por color**: todos los carbonos son **una** malla gris, todas las varillas grises otra (en varillas y alambre, cada mitad va a la malla de su elemento). Así un ligando de 256 átomos son 4–8 mallas, no 500 objetos.

### 5.2 Las mallas, en Kotlin puro (`Mesh`)

- **Esfera UV:** `stacks` bandas de polo a polo y `slices` alrededor. El vértice (i, j) está en el ángulo polar φ = π·i/stacks y el azimut θ = 2π·j/slices; su **normal** es el vector unitario desde el centro. En la primera y la última banda, uno de los dos triángulos de cada cuadrilátero **no tiene área** (todos sus vértices de arriba son el polo): no se escribe.
- **Cilindro abierto** entre dos puntos: un anillo de `segments` lados construido con dos vectores unitarios **u**, **v** perpendiculares al eje y entre sí (**u** = eje × referencia, **v** = eje × **u**). La referencia es (0, 0, 1) salvo que el eje sea casi vertical; entonces (1, 0, 0), para que el producto vectorial no sea nulo. Sin tapas: cada extremo queda dentro de un átomo.
- **Orientación:** glTF dibuja la cara **delantera** de un triángulo cuando sus vértices van en sentido **antihorario visto desde fuera**. Comprobado analíticamente para la esfera: (∂/∂θ) × (∂/∂φ) = sen φ · **n**, que apunta hacia fuera. Las pruebas lo comprueban triángulo a triángulo.

### 5.3 El fichero (`GlbWriter`)

```
cabecera (12 bytes)   "glTF" · versión 2 · longitud total            — enteros little-endian
trozo JSON            la descripción: escena, nodos, mallas, materiales, «accessors» y «bufferViews»
trozo BIN             los números: posiciones y normales (float32), índices de triángulos (uint32)
```

- Cada trozo mide un múltiplo de **4 bytes**: el JSON se rellena con **espacios** (sigue siendo JSON válido), el binario con ceros.
- **`min` y `max` exactos** de las posiciones: el estándar los exige, y el validador los **compara con los datos**. Se escriben con `Float.toString()`, el texto más corto que se relee como **el mismo** float. Una primera versión redondeaba a 6 decimales: habría dado error.
- **Colores en luz lineal.** Los colores se escriben en sRGB (0x909090), pero `baseColorFactor` de glTF es **lineal**. Sin convertir, en cualquier visor todo saldría **desvaído**: el gris 0x90 es 0,565 en sRGB y **0,279** en lineal. Fórmula sRGB → lineal: c/12,92 si c ≤ 0,04045; si no, ((c + 0,055)/1,055)^2,4.
- Material PBR: metálico 0, rugosidad 0,5.
- **Tamaño:** 15P (256 átomos) en bolas y varillas, 2,2 MB; espacio lleno, 3 MB; sin hidrógenos, 1,4 MB.

## 6. Cargas formales: un dato que faltaba

El `.mol` lleva las cargas, y nuestro *parser* **no las leía**: 175 de los 1242 ligandos tienen algún átomo cargado (un carboxilato −1, un amonio +1). Sin ellas, cada ion se exportaría químicamente **mal**.

- `Atom.charge` (por defecto 0): ninguna llamada ni prueba anterior cambió.
- El *parser* lee la columna opcional `_chem_comp_atom.charge` de forma **indulgente** (a diferencia de coordenadas y elementos, que son estrictos): no cambia lo que se dibuja, así que `?`, `.`, texto o un valor fuera de −15…+15 dan **0**, no un fallo.
- La ficha del átomo la enseña: «N1 · Nitrogen (N) · **charge +1**».

## 7. Pruebas, mutación y validación externa

### 7.1 Pruebas nuevas (30)

| Clase | Pruebas | Qué fija |
|-------|--------:|----------|
| `MolfileWriterTest` | 9 | Cabecera con «3D» en su columna; recuento de ancho fijo; línea de átomo exacta (69 caracteres); enlaces desde 1 con su tipo; `M  CHG` y `M  END`; más de 8 cargas en varias líneas; todos los códigos; `*` para lo desconocido y un nombre que no rompe el diseño; más de 999, rechazado |
| `MeshAndGlbTest` | 9 | Esfera cerrada, hacia fuera, en su radio y con normales unitarias; cilindro en tres direcciones (también **sobre el eje z**, la referencia de reserva); cilindro de longitud cero; cabecera y trozos alineados; una malla por color; hidrógenos ocultos fuera; índices dentro de su malla; **sRGB → lineal**; JSON escapado |
| `CifExtractorTest` | 3 | Cargas leídas; una carga rara es 0, no un fallo; sin columna, todos neutros |
| `AtomTextTest` | 1 | La carga en la ficha, con signo |
| `ShareImageStoreTest` | 2 | Cualquier exportación sustituye a la anterior y respeta otros ficheros; extensiones fuera de la lista, rechazadas |
| `ExportFormatTest` | 6 | Todo formato se puede guardar; tipos MIME; las seis opciones en orden; nombres de galería con la hora UTC; dos guardados nunca comparten nombre; un id raro no forma una ruta |
| `ShareTextTest` | (cambiadas) | Etiquetas de estado por formato, «Saved to Pictures/Swifty Proteins» |

### 7.2 Mutantes (13: 12 muertos, 1 equivalente)

Índices desde 0 · sin `M  CHG` · 9 cargas por línea · código de carga cambiado · «2D» · esfera hacia dentro · triángulos sin área · cilindro hacia dentro · referencia fija (falla con el eje z) · colores sin linealizar · hidrógenos ocultos exportados · JSON sin escapar: **muertos**.

**Equivalente:** «quitar el relleno a 4 bytes entre `bufferViews`». Todos nuestros números son de 4 bytes (`float32`, `uint32`), así que el binario **siempre** está alineado y el bucle nunca se ejecuta. Se deja como defensa: si un día los índices fueran de 2 bytes (`uint16`, la mitad de tamaño), sería imprescindible. Un mutante que no se puede matar porque no cambia el comportamiento no indica una prueba débil.

### 7.3 Validación con herramientas reales

**`.mol` × 1242, con RDKit 2026.09** (la biblioteca química de referencia, de código abierto):

| Comprobación | Resultado |
|--------------|-----------|
| RDKit lee el fichero | **1242 / 1242** |
| Número de átomos y de enlaces | **1242 / 1242** |
| Carga de cada átomo = CCD = lo que lee RDKit | **1242 / 1242** |
| Suma de cargas = `pdbx_formal_charge` del CCD | **1242 / 1242** |
| «Sanitización» de RDKit (valencias químicas) | 1214 / 1242 |
| Fórmula tras sanitizar = fórmula del CCD | 1207 / 1214 |

- Los **28 que no sanitizan** son complejos con metales y valencias poco comunes (boro con 6 enlaces en jaulas de boro, cloro u oxígeno unidos a un metal): RDKit comprueba con reglas de química **orgánica**, y el CCD escribe esos complejos con sus propias convenciones. **No es un error de formato**: el fichero se lee, con sus átomos, enlaces y cargas.
- Las **7 fórmulas distintas** son ligandos que en el CCD **no llevan hidrógenos** (el ligando «O», «NH2», «NO»…): RDKit, al sanitizar, añade los hidrógenos **implícitos** que faltan por valencia (del «O» sale H₂O). Es un comportamiento de RDKit, no un dato nuestro.

**`.glb` × 25, con el glTF Validator oficial de Khronos** (paquete npm `gltf-validator`): 001, 15P, ATP, HEM y ZN, en los cuatro modelos y sin hidrógenos: **0 errores, 0 avisos, 0 informaciones** en los 25.

## 8. Cómo probarlo en el móvil

1. ATP → SHARE → **Save to gallery**: «Saved to Pictures/Swifty Proteins». Abrir la **Galería** del Samsung: álbum **Swifty Proteins** con la imagen y su banda. *(Es el punto 16 de la hoja: ensayarlo.)*
2. Share image **PNG** y **JPEG**: la hoja de compartir con la imagen y el mensaje.
3. Share **.cif**, **.mol**, **.glb**: enviarlos a uno mismo (correo o Telegram). Abrir el `.glb` en un ordenador con el Visor 3D de Windows, Blender o <https://gltf-viewer.donmccurdy.com/>: la molécula con sus colores CPK.
4. Exportar el `.glb` en **espacio lleno sin hidrógenos**: el fichero tiene exactamente eso.
5. Ajustes del móvil → Apps → Swifty Proteins → Almacenamiento → **Borrar caché**, volver a la molécula abierta, **.cif**: «The original file is gone…».
6. Un ligando con cargas (por ejemplo, uno de los cationes o aniones de la lista): tocar un átomo cargado, «charge +1».

## 9. Trampas

1. **Confiar en la hoja de compartir para «guardar en la galería»:** depende de las apps del móvil. Hacerlo en la app.
2. **`WRITE_EXTERNAL_STORAGE` sin `maxSdkVersion`:** en Android 10+ no sirve, y pedirlo asusta al usuario y a quien revise el *manifest*.
3. **Una entrada de `MediaStore` sin `IS_PENDING`:** la galería puede enseñar una imagen a medio escribir; y un fallo deja una entrada vacía.
4. **Tipos MIME «correctos» que nadie acepta:** hoja de compartir vacía.
5. **Índices del molfile desde 0.**
6. **Columnas del molfile desplazadas** por un nombre con salto de línea: cada línea siguiente se leería mal.
7. **Colores sRGB en glTF:** todo desvaído.
8. **`min`/`max` redondeados:** el validador los rechaza.
9. **Triángulos en sentido horario:** con *backface culling* se verían las caras de dentro.
10. **Exportar sin cargas:** iones químicamente falsos.

## 10. Alternativas

| Alternativa | Por qué no |
|-------------|-----------|
| **OBJ** (+ MTL) para el 3D | Dos ficheros (geometría y materiales) y sin estándar de color por vértice: uno solo es más fácil de compartir |
| **PLY** con color por vértice | Un fichero, pero menos visores; glTF es el estándar actual de Khronos |
| **USDZ** | El formato 3D de Apple: en Android casi no se abre |
| **SDF** en vez de MOL | Es MOL con `$$$$` y propiedades: para una molécula, MOL basta |
| **Mol V3000** | Necesario a partir de 999 átomos; los ligandos llegan a 256 |
| Una biblioteca de glTF (JglTF) o de química (CDK) | Dependencias grandes para escribir unos cientos de bytes de JSON y un fichero de texto; así cada byte se entiende y se prueba |
| Pedir siempre el permiso de almacenamiento | Innecesario en Android 10+: `MediaStore` sin permiso es lo recomendado |

## 11. Decisiones

| # | Decisión | Estado | Motivo |
|---|----------|--------|--------|
| D57 | **Menú de exportación** en SHARE: PNG, JPEG, **galería**, `.cif` original, `.mol`, `.glb`; una carpeta compartida con extensiones de una lista cerrada; ficheros químicos como `application/octet-stream` | Decidida | Punto 16 de la hoja (obligatorio) y VII.5 «varios formatos… incluso un modelo 3D» |
| D58 | **Galería** con `MediaStore` + `IS_PENDING` en Android 10+ (sin permiso) y fichero + escáner con `WRITE_EXTERNAL_STORAGE` (`maxSdkVersion` 28) en 7–9 | Decidida | La forma recomendada en cada versión |
| D59 | **`.mol` V2000** propio, con cargas (`M  CHG`), tipo 8 para órdenes desconocidos, sin fecha (reproducible) | Decidida | Validado con RDKit en 1242/1242 |
| D60 | **`.glb`** (glTF 2.0) propio de lo que se ve: una malla por color, colores lineales, `min`/`max` exactos | Decidida | Validador de Khronos: 0 errores en 25 ficheros |
| D61 | **Cargas formales** en `Atom` y en la ficha; lectura indulgente | Decidida | 175 ligandos con cargas; el `.mol` las necesita |

## 12. Qué recordar

- **La hoja de evaluación se cumple al pie de la letra**: «guardar en el álbum» es guardar en el álbum.
- **Android 10 cambió el almacenamiento:** `MediaStore` sin permiso para añadir medios propios; `IS_PENDING` para publicarlos enteros.
- **Formatos de texto con columnas fijas:** la posición es el dato; nada de saltos de línea en un campo.
- **glTF:** cabecera + JSON + binario, todo alineado a 4; colores **lineales**; `min`/`max` exactos; triángulos **antihorarios** vistos desde fuera.
- **Validar con la herramienta de otro** (RDKit, el validador de Khronos) vale más que cualquier prueba propia: comprueba lo que uno no sabía que había que comprobar.
- **Un mutante equivalente** no se «mata»: se explica.
