# 02b · Spike 3D: toque y selección de átomos (H3)

> Estado: **H3 resuelta con una solución provisional** (detección integrada de SceneView, zonas grandes).
> Mejora identificada y **no aplicada todavía**: forma de colisión esférica (sección 7).
> Documento redactado el 15/11/2026. Lo verificado en el código fuente (tag `v4.69.4`) se distingue de lo verificado en el dispositivo.

## Índice

1. [Qué se quiere conseguir](#1-qué-se-quiere-conseguir)
2. [Teoría: cómo se «toca» un objeto 3D](#2-teoría-cómo-se-toca-un-objeto-3d)
3. [Cómo lo hace SceneView 4.69.4 (leído en el código)](#3-cómo-lo-hace-sceneview-4694-leído-en-el-código)
4. [Implementación del spike](#4-implementación-del-spike)
5. [Resultado](#5-resultado)
6. [Decisión provisional (D8) y riesgos](#6-decisión-provisional-d8-y-riesgos)
7. [Alternativas con mayor precisión](#7-alternativas-con-mayor-precisión)
8. [Cómo se probará con moléculas reales](#8-cómo-se-probará-con-moléculas-reales)
9. [Qué recordar](#9-qué-recordar)

---

## 1. Qué se quiere conseguir

Requisito del enunciado: al tocar un átomo, el usuario debe ver **cuál es** (y, más adelante, su información). Para el spike, la
hipótesis **H3** (documento 02) se cumple si:

- un toque sobre un átomo lo identifica de forma unívoca,
- el átomo seleccionado se distingue visualmente,
- un toque en el vacío (o sobre un enlace) deselecciona,
- **girar, acercar y desplazar sigue funcionando** y un toque no mueve la cámara.

La precisión «fina» (qué pasa con átomos solapados) no era criterio de paso, pero sí un riesgo que había que medir.

## 2. Teoría: cómo se «toca» un objeto 3D

La pantalla es plana; la escena no. Para saber qué hay bajo el dedo se hace **picking por rayo** (*ray casting*):

1. Se convierte el punto tocado `(x, y)` en píxeles en un **rayo**: origen en la cámara, dirección hacia ese punto de la escena.
2. Se **intersecta** el rayo con la forma de colisión de cada objeto.
3. Entre los objetos alcanzados se elige el que esté **más cerca de la cámara** (la menor distancia `t` a lo largo del rayo).

La forma de colisión no tiene por qué coincidir con lo que se dibuja; se elige por **coste y precisión**:

| Forma | Intersección | Precisión para un átomo |
|-------|--------------|-------------------------|
| Caja alineada con los ejes (AABB) | Muy barata | Baja: es un cubo de lado `2r` alrededor de una esfera de radio `r` |
| Esfera | Barata (una ecuación de segundo grado) | Exacta: coincide con lo dibujado |
| Malla de triángulos | Cara | Exacta pero innecesaria para esferas |

**Ray-esfera.** Con el rayo `o + t·d` (`d` unitario) y la esfera de centro `c` y radio `r`, se resuelve `|o + t·d − c|² = r²`, es decir
`t² + 2t·(d·(o−c)) + |o−c|² − r² = 0`. Si el discriminante es negativo no hay corte; si no, el menor `t ≥ 0` es el punto de impacto
visible. Es exactamente lo que implementa la clase `Sphere` de SceneView (sección 3).

**Por qué la caja engaña.** Los cubos de dos átomos vecinos se **solapan** en pantalla mucho antes que las esferas, y como se elige la caja
cuyo primer punto de impacto está más cerca, puede ganar la caja de un átomo que **no es el que se ve bajo el dedo**.

## 3. Cómo lo hace SceneView 4.69.4 (leído en el código)

Verificado en el código del tag `v4.69.4` (commit `5bfe25d`), no en la documentación:

- **API:** `SceneView(onGestureListener = rememberOnGestureListener(onSingleTapConfirmed = { e, node -> … }))`; `node` es el nodo impactado o `null`.
  Existe también `SceneView(onTouchEvent = { e, hitResult -> … })`, con prioridad absoluta.
- **Flujo (`SceneView.kt`):** `collisionSystem.hitTest(event).firstOrNull { it.node.isTouchable }` → el nodo se pasa al detector de gestos.
- **`CollisionSystem.hitTest`:** intersecta el rayo con el colisionador de cada nodo, descarta los de `isHittable = false` y **ordena por distancia**.
- **Forma de colisión por defecto:** para nodos renderizables se deriva de la **caja envolvente alineada con los ejes** (`updateCollisionShape()` usa
  `axisAlignedBoundingBox.toVector3Box()`).
- **`Node.collisionShape`** es **asignable** (`var collisionShape: CollisionShape?`). Asignarla **desactiva la actualización automática** del colisionador
  (la forma puesta a mano sobrevive a cambios de geometría).
- **Existe `io.github.sceneview.collision.Sphere(radius)`** (y `Sphere(radius, center)`), cuya `rayIntersection` devuelve «la intersección no negativa más
  cercana» con la ecuación de la sección 2.
- **Nombre del nodo:** `open var name: String?` en `Node`; se puede fijar en `apply` (no es un parámetro del composable, así que no lo pisa nada; ver 02a, sección 9.10).
- **Cambiar el material por parámetro** (`materialInstance = …`) en una recomposición se propaga al nodo sin reconstruir la geometría.
- **No hay modo de depuración de colisiones** en la biblioteca: hay que dibujarlas a mano.

## 4. Implementación del spike

Fichero `MoleculeScene.kt` de `spike-3d` (no es código de la aplicación final):

- **Estado:** `var selected by remember { mutableStateOf<Int?>(null) }`, índice del átomo seleccionado o `null`.
- **Nombres:** cada átomo recibe `name = "atom <id>"` (el central es el 0) y cada enlace `"bond <id>"`, fijados en `apply`.
- **Toque:** `selected = node?.name?.removePrefix("atom ")?.toIntOrNull()`. Todo lo que no es un átomo (enlace, vacío, nodo sin nombre) da `null` y deselecciona.
- **Resaltado:** `materialInstance = if (selected == id) highlight else red` (amarillo frente a rojo y gris). El material amarillo se crea **una vez** con `remember`.
- **Depuración de la zona sensible:** con `DEBUG_HITBOX = true` se dibuja un `CubeNode` translúcido (verde, material *unlit* con alfa < 1) de lado `2·radio` por átomo,
  con `isHittable = false` y `isTouchable = false` para que no intercepte los toques. El tamaño está **calculado**, no leído del colisionador real
  (puede diferir por la teselación de la malla); sirve para ver el orden de magnitud.

Primera versión descartada: mostrar el nombre en un `Text` superpuesto. Servía para depurar pero no era lo que hace falta en la aplicación (ver y resaltar el átomo).

## 5. Resultado

**Verificado en el dispositivo (Galaxy A33, 15/11/2026):**

- El toque identifica el átomo y lo resalta en amarillo; el toque en el vacío deselecciona.
- Con los cubos de depuración visibles, el usuario comprobó que **las zonas sensibles son grandes** y la selección resulta «tosca».

**No registrado:** la tabla completa de 7 casos de la sección 8 no se rellenó con resultados, y la prueba con átomos solapados en una molécula real se aplaza (el spike solo tiene 8 átomos).
Por tanto: **H3 cumplida en lo funcional; la precisión queda sin cuantificar.**

## 6. Decisión provisional (D8) y riesgos

**D8 (provisional).** Mantener por ahora la detección integrada (AABB). Motivos: funciona, no añade código, y la precisión solo puede evaluarse bien con
una molécula real y densa. Se revisa en cuanto se pueda cargar un ligando `.cif` completo.

| Riesgo | Efecto | Mitigación |
|--------|--------|------------|
| Cajas solapadas en moléculas densas | Se selecciona un átomo vecino, o uno oculto detrás | Alternativa A (7.1), de coste mínimo |
| Selección «a distancia» (en las esquinas de la caja) | Se acierta tocando fuera de la esfera visible | Alternativa A |
| Átomos muy pequeños con zoom lejano | Zona táctil diminuta (también con la esfera) | Radio mínimo de selección en píxeles (se resolvería con la alternativa B) |
| Deselección involuntaria al tocar un enlace | Molesta si el enlace pasa por donde se quiere pulsar | Que el toque en un enlace no cambie la selección, o seleccione sus átomos |

Criterio para **dejar de aplazar**: si en una molécula real se elige el átomo equivocado de forma apreciable, se aplica la alternativa A.

## 7. Alternativas con mayor precisión

### 7.1 Alternativa A (la candidata): `collisionShape = Sphere(radio)`

```kotlin
// import io.github.sceneview.collision.Sphere   // ¡no confundir con io.github.sceneview.geometries.Sphere!
SphereNode(
    radius = ATOM_RADIUS,
    ...
    apply = { this.name = "atom $id"; this.collisionShape = Sphere(ATOM_RADIUS) },
)
```

- **Ventajas:** una línea por átomo; la intersección es la exacta rayo-esfera (sección 2); se sigue usando todo el sistema de SceneView
  (`isHittable`, orden por distancia, listener con el nodo).
- **Inconvenientes / sin verificar:** no se ha ejecutado. Hay que comprobar que (1) compila con ese import (existen **dos clases `Sphere`**: la de colisión y la de geometría),
  (2) asignarla dentro de `apply`, antes de que el nodo esté en la escena, surte efecto, y (3) la forma sigue al nodo cuando cambia su posición.
  Al asignarla, el colisionador **deja de seguir** los cambios de geometría del nodo: si el radio del átomo cambia, hay que actualizar la forma a mano.
- **Coste:** mínimo. Es la primera opción cuando haga falta más precisión.

### 7.2 Alternativa B: selección propia con rayo-esfera

Usar `onTouchEvent = { e, hitResult -> … }` (o el `GestureDetector` propio) y, con la cámara de la escena, calcular el rayo del toque y la intersección con cada
átomo de nuestro **modelo de datos**, no con los nodos.

- **Ventajas:** control total; permite radio mínimo en píxeles, elegir entre átomos solapados con otra regla, y **no depende del nombre del nodo ni de la
  biblioteca de colisiones**. Es lo que haría un visor profesional y el modelo de datos de `docs/03` lo soporta (átomos con posición y radio).
- **Inconvenientes:** más código y más cosas que probar (conversión píxel a rayo, coordenadas con el zoom de la cámara). Coste lineal en el número de átomos
  por cada toque (irrelevante con cientos).
- **Cuándo:** si la A falla o si hace falta una regla de selección especial.

### 7.3 Alternativa C: *color picking*

Dibujar la escena en un buffer aparte donde cada átomo tiene un color único y leer el píxel tocado. Exacta y barata en CPU, pero exige un pase de render
adicional y trabajar con Filament a bajo nivel. **No recomendada** salvo que A y B fallen.

### 7.4 Alternativa D: dejar las cajas

Si con moléculas reales (decenas de átomos) el error es raro y tolerable, no se hace nada. Es válida mientras la prueba de la sección 8 lo respalde.

## 8. Cómo se probará con moléculas reales

Con el ligando cargado, tocar y anotar el nombre del átomo resaltado frente al esperado:

| # | Dónde tocar | Resultado deseado |
|---|-------------|-------------------|
| 1 | Centro de un átomo | Ese átomo |
| 2 | Borde del átomo, dentro del contorno | Ese átomo |
| 3 | Fuera del contorno, cerca (esquina del cubo) | Nada (vacío) |
| 4 | Dos átomos solapados en pantalla: parte visible del delantero | El delantero |
| 5 | Dos átomos solapados: parte visible del trasero | El trasero |
| 6 | Enlace | No cambia la selección (decisión pendiente) |
| 7 | Fondo | Deselecciona |

Medida de éxito: por ejemplo, en una molécula de unos 30 átomos, **al menos 9 de cada 10 toques sobre la parte visible de un átomo seleccionan ese átomo**.
Las cifras concretas son una propuesta; se ajustarán cuando haya datos.

## 9. Qué recordar

- Tocar un objeto 3D es **lanzar un rayo** desde la cámara y elegir la forma de colisión más cercana.
- Una **caja** alrededor de una esfera es barata pero **imprecisa y engañosa** cuando los átomos se solapan: gana la caja más cercana, no el átomo que se ve.
- SceneView permite **sustituir la forma de colisión** de un nodo (`collisionShape`), y trae una `Sphere` con la intersección exacta.
- El **nombre del nodo** sirve para enlazar el nodo tocado con el dato (el átomo); en el proyecto real el enlace será el `id` del átomo del modelo.
- El estado de selección es un valor (`selected`); el aspecto (amarillo/rojo) **se deriva** de él, no se pinta a mano.
- El código de depuración (cubos verdes) se esconde tras una constante.
- Dos clases `Sphere` distintas existen en SceneView (colisión y geometría): fijarse en el import.
- Lo verificado en el código fuente no es lo verificado en el dispositivo: la alternativa A **aún no se ha ejecutado**.
