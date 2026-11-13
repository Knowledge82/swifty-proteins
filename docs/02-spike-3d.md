# 02 · Spike 3D: ¿aguanta SceneView nuestro proyecto?

## Índice

1. [Qué es un spike y por qué lo hacemos](#1-qué-es-un-spike-y-por-qué-lo-hacemos)
2. [Pregunta, límite de tiempo y reglas](#2-pregunta-límite-de-tiempo-y-reglas)
3. [Hipótesis y criterios de paso](#3-hipótesis-y-criterios-de-paso)
4. [Plan paso a paso](#4-plan-paso-a-paso)
5. [Teoría necesaria](#5-teoría-necesaria)
6. [Plan B: `GLSurfaceView` con OpenGL ES](#6-plan-b-glsurfaceview-con-opengl-es)
7. [Entorno de trabajo](#7-entorno-de-trabajo)
8. [Diagnóstico](#8-diagnóstico)
9. [Qué haremos con el resultado](#9-qué-haremos-con-el-resultado)

---

## 1. Qué es un spike y por qué lo hacemos

Un **spike** es un experimento corto, con tiempo limitado, cuyo único objetivo es
**reducir una incertidumbre**. Su código es desechable: no se fusiona en `main` ni se
«arregla» para quedarse. Lo que se conserva es lo aprendido y la decisión que se toma.

La incertidumbre aquí es la capa 3D. De los cinco ámbitos del proyecto (formato, red, 3D,
seguridad, dominio) es el que más depende de una librería externa y el que más puede
bloquearnos tarde. El parser, el modelo de datos, el login y la lista **no dependen** de
esta decisión, así que perder unas horas en validarla es barato.

**Por qué SceneView.** Es una librería de 3D (y AR) para Android construida sobre
**Filament**, el motor de renderizado de Google. En su documentación se presenta como
nativa de Jetpack Compose: los objetos de la escena se declaran como elementos de la
interfaz. Filament aparece de forma explícita entre las opciones del enunciado
para Android, y SceneView ahorra el código repetitivo de usarlo «a pelo».

**Advertencia importante:** el API de SceneView cambió entre versiones mayores (en distintas
páginas de su documentación aparecen líneas 2.x y 4.x). **No se escribe código por memoria**:
la primera tarea del spike es leer la documentación y los ejemplos de la versión
que resuelva Gradle.

---

## 2. Pregunta, límite de tiempo y reglas

**Pregunta del spike:**

> ¿Podemos dibujar una molécula de prueba con SceneView dentro de una app Compose, en el
> Galaxy A33, con giro, zoom, selección de átomos y captura, de forma estable?

**Límite de tiempo:** 2 horas netas como objetivo, **4 horas como tope duro**. Si al llegar
al tope no hay un «sí» claro, la respuesta es «no» y se pasa al plan B (sección 6).

**Reglas:**

- Rama `spike/3d`, que **no se fusiona en `main`**.
- El código lo escribe el equipo; cada paso se entiende antes de escribirse.
- Datos de prueba **fabricados a mano** (unos pocos átomos). No hace falta descargar ni interpretar
  ningún `.cif` todavía.
- Se anota cada fallo y cada sorpresa: serán la materia del documento 07.

---

## 3. Hipótesis y criterios de paso

| # | Hipótesis | Cómo se comprueba | Pasa si… |
|---|-----------|-------------------|----------|
| H1 | La librería se integra en un proyecto Compose y compila con nuestro entorno | Añadir la dependencia, sincronizar Gradle y compilar | Compila y arranca en el A33 sin trucos raros |
| H2 | Se pueden dibujar esferas y un cilindro orientado entre ellas, con colores y dos luces | Escena con 3 átomos y 2 enlaces | Los cilindros unen exactamente los centros, y la imagen se ve con volumen |
| H3 | Rotación orbital, zoom con pellizco y toque en un átomo funcionan | Probar con los dedos | Giro y zoom fluidos; el toque identifica el átomo correcto |
| H4 | Se puede capturar la vista 3D como imagen | Generar un `Bitmap` y guardarlo | La imagen resultante contiene la escena 3D (no sale negra ni vacía) |
| H5 | Resiste el ciclo de vida | Girar el móvil y pasar a segundo plano y volver, 10 veces | Sin cierres, sin pantalla negra, sin pérdida de escena |
| H6 | Rendimiento suficiente | Escena sintética de unos 200 átomos y 200 enlaces con contador de FPS propio | De forma sostenida, cerca de 60 FPS en el A33 |

**Sobre H6:** no sabemos aún cuántos átomos tienen los ligandos reales de `ligands.txt`;
la intuición es que van de unos pocos a algunos cientos. Lo comprobaremos en el documento 03 y
ajustaremos esta prueba entonces.

**Criterio global:**

- **SÍ (se adopta SceneView):** H1, H2, H3 y H5 pasan, y H4 y H6 no tienen un problema grave.
- **NO (se pasa al plan B):** falla H1, H2, H3 o H5 de forma que no se resuelva dentro del tope de tiempo.
- **H4 y H6** pueden fallar sin condenar la librería si existe una alternativa razonable
  (por ejemplo, otra forma de capturar la imagen), pero se anota el coste.

---

## 4. Plan paso a paso

Cada paso tiene un punto de control. No se pasa al siguiente sin cumplirlo.

### Paso 0 · Preparar el entorno

- Crear un proyecto Android nuevo con Compose (plantilla «Empty Activity» de Android Studio).
- Crear la rama `spike/3d`.
- Comprobar que la plantilla vacía arranca en el A33 por USB.
- **Control:** aparece la pantalla de ejemplo en el móvil. Sin esto, nada de lo demás tiene sentido.

### Paso 1 · Integrar la librería (H1)

- Leer el README y los ejemplos de SceneView de la **versión actual** antes de escribir nada.
- Añadir la dependencia, sincronizar Gradle, compilar.
- **Control:** compila. Si hay conflictos de versiones (Kotlin, Compose, AGP), anotarlos: es información valiosa.

### Paso 2 · Una escena mínima

- Mostrar la vista 3D de la librería ocupando la pantalla.
- Colocar **una** esfera en el origen con un color.
- **Control:** se ve una esfera sombreada, con sensación de volumen.

### Paso 3 · Dos átomos y un enlace (H2)

- Dos esferas separadas, por ejemplo en (0,0,0) y (1.5,0,0).
- Un cilindro entre ambas, más fino que las esferas.
- Para orientar el cilindro, usar la simulación de la sección 5.4.
- **Control:** el cilindro toca los centros de las dos esferas. Probar después con enlaces en otras direcciones (en Y, en −Y, en diagonal): los casos degenerados son donde fallan las primeras versiones.

### Paso 4 · Colores y luz (H2)

- Colores CPK para C, H, O, N.
- Dos luces con direcciones distintas.
- Fondo oscuro (el hidrógeno blanco sobre blanco no se ve).
- **Control:** se distinguen los elementos y la forma tiene profundidad.

### Paso 5 · Cámara orbital y zoom (H3)

- Un dedo gira la cámara alrededor del centro; el pellizco acerca y aleja.
- Limitar la inclinación y la distancia (secciones 5.2 y 5.3).
- **Control:** el giro es fluido y el zoom nunca atraviesa la molécula.

### Paso 6 · Selección de átomos (H3)

- A partir de un toque en pantalla, calcular un rayo y buscar la esfera más cercana que corta (sección 5.5).
- Mostrar el símbolo del elemento en una etiqueta.
- Cerrar la etiqueta al tocar fuera.
- **Control:** tocar cada átomo muestra el elemento correcto, y tocar el vacío no hace nada raro.

### Paso 7 · Captura (H4)

- Obtener la imagen de la vista 3D y guardarla en un fichero.
- **Control:** abrir el fichero y comprobar que muestra la escena (no negro).

### Paso 8 · Ciclo de vida (H5)

- Girar el móvil 10 veces, pasar a segundo plano y volver 10 veces.
- **Control:** escena intacta, sin cierres. Revisar `logcat` buscando excepciones.

### Paso 9 · Rendimiento (H6)

- Generar de forma programática unos 200 átomos y 200 enlaces.
- Contar fotogramas con un contador propio y registrar los FPS cada segundo.
- **Control:** FPS sostenidos y sin tirones al girar.

### Paso 10 · Decidir y registrar

- Rellenar la plantilla de la sección 9, con la decisión y los motivos.

---

## 5. Teoría necesaria

### 5.1 Grafo de escena, cámara y luz

Una escena 3D es un **árbol de nodos**: cada objeto (una esfera, un cilindro) es un nodo con
posición, rotación y escala. La **cámara** es otro nodo que define desde dónde se mira y con
qué campo de visión (FOV). Las **luces** también son nodos. Sin luces la imagen sale plana,
porque el sombreado depende del ángulo entre la superficie y la luz.

Con una sola luz, las caras que no la miran quedan casi negras. Con dos luces desde
direcciones distintas, o una luz más una ambiental tenue, se distingue mejor la forma.

### 5.2 Cámara orbital

En lugar de manipular posición y rotación a mano, guardamos tres números: **guiñada** (*yaw*),
**cabeceo** (*pitch*) y **distancia**. La posición de la cámara, mirando siempre al objetivo, es:

```
posición = objetivo + distancia · ( cos(pitch)·sin(yaw),
                                    sin(pitch),
                                    cos(pitch)·cos(yaw) )
```

Comprobación: con `yaw = 0` y `pitch = 0`, la cámara está sobre el eje +Z a esa distancia y mira
hacia el origen. Un arrastre horizontal modifica `yaw` y uno vertical modifica `pitch`.

**Dos protecciones imprescindibles:**

- Limitar `pitch` a, por ejemplo, ±89°. En ±90° la cámara queda sobre el eje vertical y la orientación
  se vuelve inestable (el famoso *gimbal lock*).
- Limitar la distancia entre un mínimo y un máximo. Sin mínimo, el zoom entra dentro de la molécula.

**El objetivo debe ser el centro de la molécula.** Por eso, al cargar una molécula, se
restan las coordenadas de su centro a todos los átomos: así el centro queda en el origen y
«girar alrededor del centro» es literalmente girar alrededor del origen.

### 5.3 Encuadrar toda la molécula

Calculamos el radio `r` de la molécula (la distancia máxima de un átomo al centro, más el radio
de su esfera) y colocamos la cámara a una distancia suficiente para que esa esfera quepa en el
campo de visión:

```
distancia = r / sin(FOV / 2)        (con un pequeño margen)
```

**Trampa:** el FOV de Android suele darse como **vertical**. En vertical (móvil en vertical), la dimensión
limitante es la **horizontal**, que es más estrecha. La relación es:

```
tan(FOV_horizontal / 2) = relación_de_aspecto · tan(FOV_vertical / 2)
```

Ejemplo con FOV vertical de 45°, pantalla vertical con relación de aspecto 0,5 (ancho/alto) y `r = 5 Å`:

```
tan(22.5°) = 0.4142                       → mitad del FOV vertical
tan(FOV_h / 2) = 0.5 · 0.4142 = 0.2071    → FOV_h / 2 ≈ 11.7°, sin ≈ 0.2028

distancia usando solo el FOV vertical   = 5 / sin(22.5°) ≈ 13.1 Å
distancia usando el FOV horizontal      = 5 / 0.2028     ≈ 24.6 Å
```

Con la distancia «vertical» la molécula se saldría por los lados en el móvil en vertical.
Regla: usar el **menor** de los dos FOV. Además, al girar la pantalla la relación de
aspecto cambia, así que hay que recalcularlo.

### 5.4 Orientar un cilindro entre dos átomos

Los cilindros de las librerías suelen construirse a lo largo del eje **Y**, centrados en el origen. Para
unir dos átomos A y B hay que:

1. **Trasladar** el cilindro al punto medio de A y B.
2. **Escalar** su longitud a la distancia |AB|.
3. **Rotar** el eje Y hasta que apunte en la dirección `d = (B − A) / |B − A|`.

Para el paso 3 se necesita un **eje de rotación** y un **ángulo**:

```
eje    = Y × d          (producto vectorial, con Y = (0,1,0))
ángulo = acos(Y · d)    (producto escalar)
```

**Simulación con números** (en los bloques de código usamos punto decimal para no
confundirlo con la coma que separa coordenadas). A = (0, 0, 0) y B = (1.5, 0, 0):

```
punto medio = (0.75, 0, 0)
longitud    = 1.5
d           = (1, 0, 0)
eje         = (0,1,0) × (1,0,0) = ( 1·0 − 0·0,  0·1 − 0·0,  0·0 − 1·1 ) = (0, 0, −1)
ángulo      = acos(0) = 90°
```

Girar el eje Y 90° alrededor de (0,0,−1) lo lleva a (1,0,0), que es justo `d`. ✔

**Dos casos degenerados que hay que tratar aparte:**

- `d ≈ +Y`: el eje sale (0,0,0) y no hace falta girar nada.
- `d ≈ −Y`: el eje también sale nulo, pero ahora hay que girar **180°** alrededor de cualquier
  eje perpendicular (por ejemplo, el eje X).

Si no se tratan, el síntoma es típico: la molécula parece correcta salvo un enlace que
«apunta a cualquier parte». Por eso el paso 3 pide probar enlaces verticales.

### 5.5 Selección de un átomo (rayo y esfera)

Al tocar la pantalla se construye un **rayo** desde la cámara que atraviesa ese punto. Para cada
átomo (una esfera de centro `c` y radio `r`) se resuelve si el rayo `o + t·d` (con `d` normalizado) la corta:

```
b    = d · (o − c)
cc   = |o − c|² − r²
disc = b² − cc

si disc < 0  → el rayo no toca la esfera
si no        → t = −b − √disc     (la intersección más cercana)
```

Se elige el átomo con menor `t ≥ 0`. Con unos cientos de átomos el coste es despreciable.

**Simulación con números.** Rayo con origen `o = (0,0,5)`, dirección `d = (0,0,−1)`; esfera en `c = (0,0,0)` y `r = 1`:

```
o − c = (0,0,5)
b     = (0,0,−1)·(0,0,5) = −5
cc    = 25 − 1 = 24
disc  = 25 − 24 = 1
t     = 5 − 1 = 4          → punto de impacto (0,0,1)
```

El rayo toca la esfera por su cara frontal, en z = 1. ✔

**Alternativa:** muchos motores ofrecen su propia detección de toques sobre nodos. Es más cómoda, pero depende de la
librería. Conviene saber hacerlo a mano porque funciona igual con cualquier motor, incluido el plan B.

### 5.6 Colores CPK y radios (referencia)

| Elemento | Color (obligatorio en el enunciado) | Radio covalente aprox. (Å) | Radio de van der Waals aprox. (Å) |
|----------|-------------------------------------|----------------------------|-----------------------------------|
| C | gris o negro | 0,77 | 1,70 |
| H | blanco | 0,37 | 1,20 |
| N | azul | 0,75 | 1,55 |
| O | rojo | 0,73 | 1,52 |
| S | amarillo | — | 1,80 |
| P | naranja | — | 1,80 |

Para el modelo *ball-and-stick* basta un radio uniforme o escalado a partir de estos valores. Los radios de van der
Waals se usarán en el bonus de espacio lleno. Para los demás elementos usaremos un esquema estándar (por ejemplo el
de Jmol) y un color por defecto para los desconocidos.

---

## 6. Plan B: `GLSurfaceView` con OpenGL ES

Si SceneView no pasa el spike, la alternativa es dibujar con OpenGL ES dentro de un `GLSurfaceView`.
VI.4 lo permite explícitamente para Android.

**Qué implica escribir nosotros:**

- La malla de una esfera y de un cilindro (vértices, normales, índices) y su subida a la GPU.
- Un *vertex shader* y un *fragment shader* en GLSL ES con iluminación (idealmente dos luces).
- Las matrices de proyección y de vista, y la cámara orbital de la sección 5.2.
- La selección con rayos (sección 5.5), sin ayuda del motor.
- La captura de pantalla, leyendo los píxeles y volteando la imagen (OpenGL tiene el eje Y invertido respecto a las imágenes).
- Para dibujar todo con pocas llamadas, instanciación (OpenGL ES 3.0).

**Trampas conocidas:**

- Los toques llegan al hilo de la interfaz y el dibujo ocurre en otro hilo. Sin sincronización
  (por ejemplo, `queueEvent`) hay condiciones de carrera.
- Al pasar a segundo plano **el contexto de OpenGL se puede perder** y todos los recursos
  deben recrearse. Como el proyecto exige volver al login desde segundo plano, este caso ocurrirá continuamente.
- Si falta algo, la pantalla sale negra y la depuración es «a ciegas». Conviene avanzar en pasos mínimos.

**Qué se aprovecha del spike:** la cámara orbital, el cálculo de orientación de cilindros, la selección con rayos y el
modelo de datos son matemáticas y lógica independientes del motor. Solo cambia la capa de dibujo.

**Qué no cambia con la decisión:** el parser, el modelo de moléculas, el login, la lista y la red.

---

## 7. Entorno de trabajo

- **En casa:** Android Studio y el Galaxy A33 conectado por USB con depuración activada.
- **En el campus:** el espacio en el directorio personal es pequeño, así que conviene colocar el SDK de Android y
  la caché de Gradle en `~/goinfre` (por ejemplo mediante las variables de entorno `ANDROID_HOME` y
  `GRADLE_USER_HOME`). Los equipos del campus son lentos y, según la experiencia previa, no soportan bien el
  emulador, por lo que se prueba directamente en el móvil.
- **Persistencia:** el código vive en GitHub. `goinfre` puede borrarse, así que nada importante
  se guarda solo allí.

---

## 8. Diagnóstico

| Síntoma | Qué comprobar |
|---------|---------------|
| Falla la compilación al añadir la librería | Versiones de Kotlin, del plugin de Android Gradle y de Compose; leer el mensaje de Gradle completo, que casi siempre nombra el conflicto |
| La app se cierra al abrir la vista 3D | `adb logcat` filtrando por el nombre del paquete y buscando `FATAL EXCEPTION` |
| Pantalla negra o vacía | Posición de la cámara (¿está dentro de la molécula?), luces, y que los nodos se hayan añadido a la escena |
| Un cilindro apunta mal | Casos degenerados de la sección 5.4 |
| El toque selecciona el átomo equivocado | Rayo mal construido (coordenadas de pantalla frente a coordenadas del mundo) o molécula no centrada |
| Captura en negro | La vista 3D suele dibujarse en una superficie propia: la captura de la interfaz normal no la incluye. Hay que usar el método de captura que corresponda a esa superficie |
| FPS bajos | Muchos objetos individuales (considerar compartir mallas), materiales duplicados o trabajo pesado en cada fotograma |

**Sobre medir FPS:** `adb shell dumpsys gfxinfo <paquete>` aporta datos del renderizado de la interfaz,
pero la vista 3D se dibuja en una superficie aparte, así que **no conviene fiarse solo de esa herramienta**. Un contador
propio, que cuente fotogramas en la llamada de actualización de la escena y lo registre cada segundo, es más fiable.

Comandos útiles:

```bash
adb devices                          # el móvil aparece como "device"
adb logcat --pid=$(adb shell pidof -s <paquete>)    # log solo de nuestra app
```

---

## 9. Qué haremos con el resultado

Al terminar se rellena esta plantilla (será la base del documento 04, de arquitectura y decisiones):

```
ADR-001: Capa 3D
Fecha:
Decisión:  [ ] SceneView (Filament)    [ ] GLSurfaceView (OpenGL ES)
Resultado de cada hipótesis:  H1 _  H2 _  H3 _  H4 _  H5 _  H6 _
Versión de la librería probada:
Tiempo invertido:
Problemas encontrados:
Alternativas descartadas y por qué:
Consecuencias para la interfaz (¿Compose obligatorio?):
Pendiente / riesgos que quedan:
```

Después del spike confirmamos o cambiamos las decisiones provisionales D3 y D4 del README,
y pasamos al documento 03: el formato CIF y el parser, empezando por descargar todos los
ligandos de `ligands.txt` para ver los casos reales.
