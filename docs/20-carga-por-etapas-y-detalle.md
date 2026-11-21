# 20 · Carga por etapas, nivel de detalle y montaje progresivo de la escena

> **Estado:** **escrito**. Reglas puras **probadas en la JVM** (524 pruebas en total; 36 nuevas; 23 mutantes: 22 muertos y 1 equivalente, eliminado del código). El diálogo, la barra y el montaje en la escena **compilan contra *stubs*** hechos con el código fuente de SceneView 4.69.4; **sin probar en el móvil** (§7).

## Índice

1. [Qué pide el enunciado](#1-qué-pide-el-enunciado)
2. [Etapas de la carga](#2-etapas-de-la-carga)
3. [Hilos: quién escribe el estado](#3-hilos-quién-escribe-el-estado)
4. [Nivel de detalle por tamaño de molécula](#4-nivel-de-detalle-por-tamaño-de-molécula)
5. [Montaje progresivo de la escena](#5-montaje-progresivo-de-la-escena)
6. [Interfaz](#6-interfaz)
7. [Cómo probarlo en el móvil](#7-cómo-probarlo-en-el-móvil)
8. [Pruebas y mutación](#8-pruebas-y-mutación)
9. [Trampas](#9-trampas)
10. [Alternativas](#10-alternativas)
11. [Decisiones](#11-decisiones)
12. [Qué recordar](#12-qué-recordar)

---

## 1. Qué pide el enunciado

VII.4 *Performance and Caching* tiene cinco puntos. Tres ya estaban hechos; este documento cubre los otros dos y completa el tercero:

| Punto | Antes | Ahora |
|-------|-------|-------|
| Caché local de los `.cif` | Hecho (documento 13) | — |
| Carga perezosa | Lista `LazyColumn` (documento 06b) | — |
| ***Parsing* en segundo plano con indicadores de progreso** | En segundo plano, pero **sin progreso**: un cráneo que se balancea | **Lista de etapas** (archivo, conexión, descarga con %, *parsing*) y **barra** |
| **Gestión de memoria para moléculas grandes** | Detalle fijo para todas | **Nivel de detalle (LOD) según el tamaño** y **montaje por lotes** |
| 60 FPS garantizados | Comprobado en el A33 (documento 12) | Nunca más pesado que lo comprobado (§4.3) |

## 2. Etapas de la carga

### 2.1 Las cinco etapas (`LoadStage`)

| Etapa | Qué pasa | Quién la anuncia |
|-------|----------|------------------|
| `Starting` | El instante entre el toque y el primer aviso del hilo de fondo | `LigandLoader` (estado inicial) |
| `Archive` | Se busca y se lee la copia del móvil (documento 13) | `LigandRepository` |
| `Connecting` | DNS, TCP, TLS, la petición y la línea de estado | `LigandRepository` |
| `Downloading(recibido, anunciado)` | Llega el cuerpo; `anunciado = -1` si el servidor no dijo el tamaño | `HttpUrlConnectionFetcher` (vía el repositorio) |
| `Parsing` | El texto se convierte en átomos y enlaces | `LigandRepository` |

Las etapas siempre van en ese orden, y alguna puede saltarse. Con una copia fresca no hay red: `Archive` y después el resultado. Un 404 llega a `Connecting` y nunca a `Parsing`.

### 2.2 El progreso de la descarga

El *fetcher* avisa una vez al recibir las cabeceras, `(0, anunciado)`, y después de cada trozo de 8 KB, `(recibido, anunciado)`.

**¿Cuándo se puede dar un porcentaje?** Solo si el tamaño anunciado **merece confianza**: hay `Content-Length` **y** no hay `Content-Encoding`. Con el cuerpo comprimido, `Content-Length` es el tamaño **comprimido** (documento 06, §2) y el porcentaje mentiría. En ese caso, y en las respuestas por trozos (*chunked*, sin longitud), se avisa con `-1`: la interfaz muestra los **kilobytes** y una barra **indeterminada**.

> **Sin comprobar:** si `files.rcsb.org` envía `Content-Length`. Desde el entorno de pruebas no se puede llegar a ese servidor. Las dos variantes están probadas contra un servidor local.

### 2.3 Lo que se ve de verdad

Un ligando pesa entre **0,7 y 34 KB** (corpus de 1242: K.cif y CDL.cif). Se descarga en uno a cinco trozos: con buena red, la descarga dura milisegundos y **casi toda la espera es `Connecting`** (la latencia y el TLS). La lista lo dice tal cual: «CONNECT · waiting for the server». Esto **no es un fallo** del indicador; dice **dónde** se va el tiempo.

**No se añaden pausas artificiales** para que las etapas «se vean». Un indicador que retrasa la carga para lucirse empeora justo lo que mide.

### 2.4 Cómo viaja la etapa

```
HttpUrlConnectionFetcher.fetch(url, isCancelled) { recibido, anunciado -> … }    ← hilo de fondo
        │  (recibido, anunciado)
LigandRepository.load(id, isCancelled) { etapa -> … }  →  Downloading(recibido, anunciado)
        │  etapa
LigandLoader: onStage(etapa)  →  scope.launch { … }  →  hilo principal              ← §3
        │
LoadState.Loading(id, etapa)  →  LoadingDialog: lista + barra
```

Para no romper los *fakes* de las pruebas, `HttpFetcher` gana un **segundo** `fetch` con progreso cuyo **cuerpo por defecto** llama al de siempre e ignora el progreso. Un *fake* que solo sabe el `fetch` de dos argumentos sigue compilando; el *fetcher* real implementa los dos.

## 3. Hilos: quién escribe el estado

### 3.1 La garantía que había (documento 06b)

> Después de `load`, el estado es `Loading` hasta que el trabajo termina, y **después es SIEMPRE otra cosa**. La carga de un `load` cancelado o sustituido **nunca** aparece.

El progreso amenaza esa garantía: llega desde el **hilo de fondo** y **en cualquier momento**, también **después** de cancelar.

### 3.2 La solución: un solo hilo escribe

Las etapas **no se escriben** en el hilo de fondo. Cada una se **entrega al hilo principal** (`scope.launch`) y allí se escribe **solo si**:

1. el estado **sigue siendo** `Loading` (no `Idle` tras cancelar, ni `Loaded` tras terminar), **y**
2. esta carga **sigue siendo la actual** (`job === mine`: el trabajo vigente es el que creó esta etapa).

Todas las escrituras del estado ocurren en el **mismo hilo**: no hay carrera. Ninguna comprobación, ni de memoria ni de orden, depende de la suerte.

### 3.3 Los tres casos peligrosos y su prueba

| Caso | Sin la regla | Prueba |
|------|-------------|--------|
| Etapa tardía de una carga **cancelada** | `Idle` → `Loading` otra vez: **diálogo fantasma** que nadie cierra | `aLateStageOfACancelledLoadNeverBringsLoadingBack` |
| Etapa tardía de la carga **sustituida** (A, después B) | La lista de B muestra el progreso de A | `aLateStageOfTheOldLoadNeverOverwritesTheNewOne` |
| Etapa que llega **después del resultado** | `Loaded` → `Loading`: se vuelve a la lista | `aStageArrivingAfterTheResultIsIgnored` |

Cada mitad de la condición tiene su mutante, y los dos mueren (§8).

### 3.4 ¿Y el sonido?

El sonido de módem (documento 14) suena al **entrar** en `Loading`. Su etiqueta es `loading:<id>`, **sin** la etapa (`SoundTags`), así que los cambios de etapa **no** lo vuelven a disparar.

## 4. Nivel de detalle por tamaño de molécula

### 4.1 Teoría: qué cuesta una bola

SceneView construye cada esfera como una malla de `detalle × detalle` bandas (leído en su código, `Sphere.kt`):

- **Vértices:** (detalle + 1)² (la costura se repite).
- **Triángulos:** 2 · detalle · (detalle − 1). Los polos son abanicos: se salta un triángulo por franja en cada polo.
- **GPU:** 36 bytes por vértice (posición 12, tangentes 16, UV 8) y 4 bytes por índice (`UINT`).
- **Montón de Java:** SceneView **guarda** también los vértices como objetos (`List<Vertex>`) y los índices como `List<List<Int>>` (**enteros en caja**) para poder actualizar la malla. Es memoria del recolector de basura, no de la GPU.

| Detalle | Vértices | Triángulos | GPU por bola | Montón por bola (**estimación**) |
|---------|----------|------------|--------------|----------------------------------|
| 32 | 1089 | 1984 | 62 KB | ≈ 250 KB |
| 24 | 625 | 1104 | 35 KB | ≈ 140 KB |
| 20 | 441 | 760 | 25 KB | ≈ 100 KB |
| 12 | 169 | 264 | 9 KB | ≈ 35 KB |

> La columna del montón es una **estimación**: unos 120 bytes por `Vertex` con sus tres objetos y unos 20 bytes por entero en caja. No está medida con un *profiler*.

**CDL** (256 átomos, el mayor del catálogo) con 32 bandas para todo: ≈ 16 MB de GPU y ≈ 64 MB de montón, **solo en bolas**. Con 20 bandas: ≈ 6 MB y ≈ 25 MB. Además del tamaño, cada objeto es trabajo para el **recolector de basura**, y sus pausas son tirones.

### 4.2 La regla (`MeshDetail`)

Un **presupuesto fijo de triángulos** para las bolas, repartido entre los átomos de la molécula:

```
detalle = √(PRESUPUESTO / (2 × átomos)),  entre 12 y 32
```

| Átomos | Ejemplo | Bola | Espacio lleno (+⅕) | Pequeña, H (⅗) | Palo (¾) |
|--------|---------|------|--------------------|----------------|----------|
| 1 | K | 32 | 32 | 19 | 24 |
| 47 | ATP | 32 | 32 | 19 | 24 |
| 87 | 001 | 32 | 32 | 19 | 24 |
| 128 | — | 28 | 32 | 16 | 21 |
| 256 | CDL | **20** | **24** | **12** | 15 |

### 4.3 Por qué ese presupuesto: anclado a lo medido

`PRESUPUESTO = 2 × 256 × 20² = 204 800`. Es exactamente la configuración que **ya existía** (bolas de 20, espacio lleno 24, hidrógenos 12) y con la que se vieron **60 FPS** en el A33. Consecuencia:

- La molécula **más grande** recibe **lo mismo** que antes: ni un triángulo más.
- **Todas** las demás reciben **más detalle**, nunca menos: ATP pasa de 20 a **32** bandas. Se nota en las esferas grandes, donde antes se veían las facetas.
- Los palos nunca pasan de **24 lados**, el valor por defecto de SceneView que se usaba antes para todos. En CDL bajan a 15: son finos y no se nota.

Así, «60 FPS garantizados» sigue apoyándose en una **medición**, no en una esperanza.

### 4.4 Por átomos de la molécula, no por átomos dibujados

El detalle depende de **cuántos átomos tiene la molécula**, no de cuántos se ven. Si dependiera de los dibujados, ocultar los hidrógenos (más de la mitad de los átomos) cambiaría el detalle de **todas** las bolas, y SceneView reconstruiría cada malla en el hilo principal: un tirón justo al tocar un botón. Un detalle un poco más bajo de lo posible sin hidrógenos es un precio pequeño.

### 4.5 Halos, regla y palos

Los halos (selección, elemento, regla) usan el detalle de bola normal. Los cilindros (enlaces, el tubo del enlace elegido, la línea de la regla) usan `cylinderSides`.

## 5. Montaje progresivo de la escena

### 5.1 El problema

Cada átomo y cada palo es un **nodo** de SceneView, y crear un nodo construye su malla **en el hilo principal**: Filament lo exige («*Must be called on the main thread*», `Sphere.kt`). CDL en bolas y palos son unos **800 nodos**: 256 bolas y 2 × 255 mitades de palo. Creados de golpe son **un fotograma congelado**, largo (centenares de ms, **sin medir**), y sin nada en pantalla que diga por qué. Peor: mientras el hilo principal está bloqueado no se dibuja **nada**, así que ningún indicador puesto «antes» llegaría a verse.

### 5.2 La solución: por lotes (`Assembly`)

Las piezas se numeran: primero los átomos (0 … átomos − 1) y después los enlaces (átomos … átomos + enlaces − 1), en el orden del fichero. Cada fotograma se añade un **lote**:

```
lote = 16
mientras construidos < total:
    construidos = min(total, construidos + lote)
    esperar al siguiente fotograma, medir cuánto tardó
    fotograma > 24 ms → lote / 2 (mínimo 4)
    fotograma < 18 ms → lote × 2 (máximo 512)
```

- El lote **se adapta al móvil**: en uno rápido crece enseguida (16, 32, 64, 128, 256…) y CDL está completo en **6 fotogramas**, unos 100 ms. En uno lento se reduce y la pantalla sigue respondiendo.
- Se espera con `withFrameNanos`, el reloj de fotogramas de Compose, el mismo que usan sus animaciones.
- Una molécula nueva o un **modelo nuevo** empiezan de cero: todos los nodos son nuevos en ese caso (la clave lleva el modelo, documento 19). **Ocultar hidrógenos no** reinicia: el contador va por índice de átomo, no por «átomos dibujados».
- Los halos, las etiquetas y los enlaces de un átomo **aún no construido** tampoco se crean.

### 5.3 Lo que se ve

Debajo de la escena aparece un panel **PATTERN ASSEMBLY · «ASSEMBLY 312 / 511»** con una barra; desaparece al terminar. La molécula aparece **por partes**: primero las bolas y después los palos. Encaja con el estilo de «cogitador que ensambla el patrón».

**SHR no hace nada mientras se monta**: una imagen de media molécula no es lo que el usuario pidió.

## 6. Interfaz

### 6.1 Diálogo de carga

```
       [cráneo servidor]
   Invoking the Machine Spirit
          Ligand HEM

  [OK] ARCHIVE    OK
  [OK] CONNECT    OK
  [>>] DOWNLOAD   62 % · 4.6 / 7.4 KB
  [  ] PARSE

  ▓▓▓▓▓▓▓▓▓▓▓▓░░░░░░░░    (real solo con % conocido)
           [ Cancel ]
```

- Marca **de texto** (`[OK]`, `[>>]`, `[  ]`) además del color: nunca solo color.
- TalkBack lee cada línea entera: «Download: in progress, 62 % · 4.6 / 7.4 KB».
- El porcentaje se **redondea hacia abajo**: 100 % solo cuando todo llegó. Nunca pasa de 100, aunque un servidor anuncie menos de lo que manda.

### 6.2 Por qué el texto está en `LoadStageText` y no en el diálogo

Es Kotlin puro: se prueba en la JVM (orden de las líneas, marcas, porcentaje, división por cero, lo que oye TalkBack). El diálogo solo dibuja lo que esa función devuelve.

## 7. Cómo probarlo en el móvil

1. **Un ligando nunca abierto, con wifi:** la lista pasa por ARCHIVE, CONNECT, DOWNLOAD y PARSE. Puede ser muy rápido; con datos móviles y poca cobertura se ve mejor.
2. **El mismo ligando otra vez:** solo ARCHIVE (copia fresca, sin red) y se abre.
3. **Modo avión con un ligando nunca abierto:** ARCHIVE, CONNECT y el error de siempre. El diálogo **nunca** se queda abierto.
4. **Cancelar en CONNECT:** se vuelve a la lista, y no aparece ningún diálogo fantasma después.
5. **CDL o 15P:** el panel ASSEMBLY con la barra y la molécula apareciendo por partes, rápido. Tocar SHR durante el montaje no hace nada.
6. **Cambiar de modelo con MODEL…:** se vuelve a montar.
7. **Ocultar hidrógenos:** **no** se vuelve a montar.
8. **ATP en espacio lleno:** las esferas, más lisas que antes (32 bandas en vez de 24).
9. **CDL girando:** el contador sigue en 60 FPS.

## 8. Pruebas y mutación

| Fichero | Pruebas nuevas | Qué fija |
|---------|----------------|----------|
| `HttpUrlConnectionFetcherTest` | 4 | De 0 al total sin bajar nunca; sin longitud → −1; comprimido → −1; un 404 no avisa |
| `LigandRepositoryCacheTest` | 5 | Copia fresca → solo `Archive`; descarga → `Archive, Connecting, Parsing`; un 404 no llega a `Parsing`; un id inválido no avisa; el progreso del *fetcher* se vuelve `Downloading` |
| `LigandLoaderTest` | 4 | La etapa se ve y el resultado la sustituye; las tres etapas tardías del §3.3 |
| `LoadStageTextTest` | 10 | Orden, marcas, detalle, sin longitud, **longitud 0**, redondeo, barra, texto de marcas, TalkBack |
| `MeshDetailTest` | 7 | CDL igual que antes; más detalle para las pequeñas; nunca crece con el tamaño; presupuesto; límites; palos ≤ 24; fórmulas de la malla |
| `AssemblyTest` | 6 | Átomos antes que enlaces; el lote dobla y divide; límites; nunca pasa del final; CDL en ≤ 7 fotogramas; el texto desaparece |

**Mutantes: 23.** 22 muertos, a la primera o tras reforzar las pruebas, y 1 equivalente:

- **«longitud 0 = desconocida» cambiado por «< 0»:** sobrevivía y era un **fallo real**: `Content-Length: 0` habría dividido por cero en el diálogo. Ahora tiene prueba.
- **«confiar en la longitud aunque venga comprimida»:** sobrevivía; ahora hay una prueba con `Content-Encoding: gzip`.
- **El límite superior de los palos (24):** un mutante **equivalente**: con bolas de 32 como máximo, ¾ nunca pasa de 24. Era código muerto: se quitó, y una prueba recorre de 0 a 2000 átomos para comprobar que nunca pasa de 24 (si alguien sube el máximo de las bolas, falla).

**Sin prueba automática:** el diálogo, el panel, los lotes reales por fotograma y el bloqueo de SHR. Ver §7.

## 9. Trampas

1. **Escribir el estado desde el hilo de fondo:** una etapa tardía resucita un diálogo cancelado.
2. **Comprobar solo «sigue cargando»:** una carga sustituida pinta su progreso en la siguiente.
3. **Lambda final de Kotlin:** `load("XYZ") { flag }` **ya no** pasa `isCancelled`: la lambda final va al **último** parámetro, que ahora es `onStage`. Lo detectó una prueba que empezó a fallar; se corrigió a `load("XYZ", { flag })`. El código de producción pasa los tres por nombre de posición.
4. **Porcentaje con `Content-Encoding`:** se compara lo descomprimido con un total comprimido.
5. **Dividir por un `Content-Length: 0`.**
6. **Pausas artificiales** para que las etapas «se vean».
7. **Detalle por átomos dibujados:** ocultar hidrógenos reconstruiría todas las mallas.
8. **Un indicador «mientras se monta» sin montar por lotes:** con el hilo principal bloqueado no se dibuja nunca.
9. **Capturar la imagen a medio montar.**

## 10. Alternativas

| Alternativa | Por qué no |
|-------------|-----------|
| Pedir al servidor solo `HEAD` para saber el tamaño antes | Una petición más (más latencia) para un fichero de pocos KB |
| `StateFlow.update { }` desde el hilo de fondo, con un contador de generación | Funciona, pero la igualdad de `data class` permite un caso ABA (dos cargas del mismo id con la misma etapa); con un solo hilo escritor no hay nada que razonar |
| **Una sola malla compartida** por todas las bolas del mismo tamaño (`MeshNode` con el mismo `VertexBuffer`) | Es lo más eficiente en memoria. Pero hay que generar los vértices a mano, escalar cada nodo y revisar que el toque (`collisionShape`) siga funcionando con escala: un cambio grande en la parte más delicada. Queda como mejora |
| **Instancias** de Filament (`RenderableManager.Builder.instances`) | Lo que usan los visores profesionales para miles de átomos. Requiere salir de los nodos de SceneView |
| LOD **por distancia a la cámara** | Útil con miles de átomos; con 256 como máximo, el presupuesto por molécula ya basta, y cambiar mallas al hacer zoom causaría tirones |
| **Impostores** (esferas dibujadas como cuadrados con un *shader*) | La técnica de PyMOL y VMD para proteínas enteras; necesita un material propio de Filament |
| Montar todo de golpe tras un fotograma con «ASSEMBLING…» | Se ve el texto, pero después se congela igual |

## 11. Decisiones

| # | Decisión | Estado | Motivo |
|---|----------|--------|--------|
| D65 | **Etapas de carga** (`LoadStage`) con porcentaje solo si la longitud merece confianza; escritas **solo en el hilo principal** si la carga sigue viva y es la actual | Decidida | VII.4 «progreso»; mantiene la garantía del documento 06b |
| D66 | **Nivel de detalle** por número de átomos con un presupuesto de triángulos **anclado** a lo medido a 60 FPS | Decidida | VII.4 «memoria» y «60 FPS»; las moléculas pequeñas ganan calidad |
| D67 | **Montaje de la escena por lotes adaptativos** con panel ASSEMBLY; SHR bloqueado mientras tanto | Decidida | Sin fotograma congelado; progreso también de la última etapa |

## 12. Qué recordar

- Un indicador de progreso tiene que decir la **verdad**: si casi todo es latencia, que diga «conectando». Sin porcentajes inventados ni pausas para lucirse.
- **Un solo hilo escribe el estado**: es la manera más barata de no tener carreras.
- Cuando se añade un parámetro al final de una función, **la lambda final cambia de dueño**.
- Leer el código de la biblioteca da números: (n + 1)² vértices, 36 bytes por vértice, listas de objetos en el montón.
- Un presupuesto **anclado a una medición** vale más que un número elegido a ojo.
- El trabajo pesado del hilo principal se **reparte en fotogramas**; si no, ningún indicador llega a pintarse.
