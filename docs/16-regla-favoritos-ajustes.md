# 16 · Bonus: regla (distancias, ángulos, torsión), favoritos por cuenta y ajustes

> **Estado:** **escrito**. Lógica pura **probada en la JVM**: 42 pruebas nuevas (**452** en total) y **18 mutantes, todos muertos** (uno sobrevivió primero y obligó a corregir una prueba). Pantallas y escena **compilan contra *stubs***; **sin probar en el móvil** (sección 8).

## Índice

1. [Qué cierra este documento](#1-qué-cierra-este-documento)
2. [La regla: teoría](#2-la-regla-teoría)
3. [La regla: código y pantalla](#3-la-regla-código-y-pantalla)
4. [Favoritos por cuenta](#4-favoritos-por-cuenta)
5. [Ajustes](#5-ajustes)
6. [El controlador común: `PreferencesController`](#6-el-controlador-común-preferencescontroller)
7. [Pruebas y mutación](#7-pruebas-y-mutación)
8. [Cómo probarlo en el móvil](#8-cómo-probarlo-en-el-móvil)
9. [Trampas](#9-trampas)
10. [Alternativas](#10-alternativas)
11. [Decisiones](#11-decisiones)
12. [Qué recordar](#12-qué-recordar)

---

## 1. Qué cierra este documento

| Bloque | Punto | Antes (documento 15) | Ahora |
|--------|-------|----------------------|-------|
| VII.3 Interacción | Medir **distancias y ángulos** | Solo la longitud de un enlace | **Regla**: 2 átomos → distancia; 3 → ángulo; 4 → **torsión** |
| VII.5 Compartir | **Favoritos** | — | Estrella en la lista y en la molécula; filtro «solo favoritos»; **por cuenta** |
| VII.2 Interfaz | Pantalla de **ajustes** | — | Modelo al abrir, hidrógenos al abrir, **sonidos sí/no** |

Con esto, de la lista del enunciado solo quedan sin hacer: etiquetas en 3D (VII.3), *onboarding* (VII.2), varios formatos, vídeo y comparación (VII.5). Motivos en la sección 10.

---

## 2. La regla: teoría

Tres medidas clásicas de la geometría molecular, según cuántos átomos se toquen:

| Átomos | Medida | Qué dice | Valores típicos |
|-------:|--------|----------|-----------------|
| 2 | **Distancia** A–B | Si son vecinos, la longitud del enlace; si no, la separación | C–C 1,54 Å; C=O 1,23 Å; C–H 1,09 Å |
| 3 | **Ángulo** A–B–C (en el átomo **central**) | La forma alrededor de B | Tetraédrico (sp³) **109,47°**; plano (sp²) 120°; lineal (sp) 180° |
| 4 | **Ángulo de torsión** (diedro) A–B–C–D | El **giro** alrededor del eje B–C | *cis* 0°; *gauche* ±60°; *trans* 180° |

### 2.1 Distancia y ángulo

- **Distancia:** √(Δx² + Δy² + Δz²).
- **Ángulo en B:** con u = A − B y v = C − B, **θ = arccos( u·v / (|u|·|v|) )**. El producto escalar divide la «proyección» entre las longitudes; arccos devuelve 0–180°. El cociente se **acota a [−1, 1]**: con redondeo puede salir 1,0000000002, y arccos de eso es NaN.

> Simulación: metano, C en el centro de un cubo y dos H en esquinas alternas, (1, 1, 1) y (−1, −1, 1). u·v = −1 −1 +1 = −1; |u| = |v| = √3; cos θ = −1/3; **θ = 109,47°**, el ángulo tetraédrico. Es una de las pruebas.

### 2.2 Torsión: la fórmula y el signo

Mirando **a lo largo del eje B → C**, la torsión es cuánto hay que girar el enlace **B–A** (el de delante) para que tape al **C–D** (el de detrás). Convención **IUPAC**: **positivo si el giro es en el sentido de las agujas del reloj**, entre −180° y 180°.

Fórmula (Blondel y Karplus, 1996), con b₁ = B − A, b₂ = C − B, b₃ = D − C:

```
φ = atan2( |b₂| · b₁·(b₂×b₃) ,  (b₁×b₂)·(b₂×b₃) )
```

- (b₁×b₂) y (b₂×b₃) son las **normales** de los dos planos A-B-C y B-C-D; el ángulo entre los planos es el ángulo entre sus normales.
- **¿Por qué atan2 y no arccos?** arccos del coseno pierde **el signo** (no distingue +60° de −60°) y pierde precisión cerca de 0° y 180°, justo donde están *cis* y *trans*. atan2(seno, coseno) da el ángulo con signo y es estable en todo el círculo.
- **Indefinida** si tres átomos están en **línea**: una de las normales es nula (no hay plano). Se devuelve `null` y la pantalla dice «undefined», en vez de inventar un número.

> Comprobación del signo con números: B en el origen, C en +z, A en +x, D en (0, 1, 1,5). Mirando a lo largo de +z, +x aparece a la **izquierda** y +y **arriba**: llevar el enlace de delante (+x) hasta +y es un giro **horario** → debe dar **+90°**. La fórmula: b₁ = (−1, 0, 0), b₂ = (0, 0, 1,5), b₃ = (0, 1, 0); b₂×b₃ = (−1,5, 0, 0); seno ∝ 1,5 · (−1)(−1,5) = 2,25 > 0; coseno = (b₁×b₂)·(b₂×b₃) = (0, 1,5, 0)·(−1,5, 0, 0) = 0 → atan2(2,25, 0) = **+90°**. ✔ Es la prueba `theSignFollowsIupacClockwiseIsPositive`.

---

## 3. La regla: código y pantalla

### 3.1 `Measurement` (puro)

| Función | Qué hace |
|---------|----------|
| `tap(lista, i)` | Un átomo **ya medido** se **quita** (segundo toque = deshacer); con la lista **llena** (4) empieza otra con este; si no, se añade al final |
| `visible(lista, molécula, hidrógenos)` | Quita los átomos que ya no se dibujan (hidrógenos recién ocultados): una medida de algo invisible mentiría |
| `distance`, `angle`, `torsion` | Las fórmulas de la sección 2, en `Double` |

**Orden de las reglas de `tap`:** «quitar» va **antes** que «lista llena». Con 4 átomos, tocar uno de ellos lo quita (deshacer), no empieza una medida nueva. Lo fija una prueba.

### 3.2 `MeasureText` (puro): lo que dice la tarjeta

| Átomos | Título | Líneas |
|-------:|--------|--------|
| 0 | «Ruler» | Qué hacer: 2 → distancia, 3 → ángulo, 4 → torsión |
| 1 | «C1» | «Tap another atom» |
| 2 | «C1 – O2» | Distance 1.230 Å |
| 3 | «C1 – C2 – O3» | C1–C2 · C2–O3 · **Angle** |
| 4 | «C1 – C2 – C3 – C4» | **Torsion** · Angle at C2 · Angle at C3 |

Con 4 átomos se enseñan también los **dos ángulos** que forman la torsión: si uno se acerca a 180°, la torsión es poco fiable (casi no hay plano), y así se ve por qué.

### 3.3 En pantalla

- **Tecla RULER** en la cabecera de la leyenda, junto a **MODEL** (el modelo actual pasa a escribirse en texto, encima). Encendida: borde y letra fósforo, y TalkBack dice «on».
- Con la regla encendida, **la regla manda en los toques**: un átomo entra o sale de la medida; el vacío la borra (como borra una selección); un enlace no hace nada. La selección normal se apaga (no se ven las dos tarjetas a la vez).
- **En la escena:** halo verde en cada átomo medido y una **línea** verde (cilindro de 0,06 Å, 70 % opaco, sin luz) de cada átomo al siguiente: A–B, B–C, C–D. Halos y líneas **no se pueden tocar** (como todos los halos, documento 15).
- **La tarjeta** «AUSPEX // RULER» sustituye a «AUSPEX READOUT».
- **Se guarda** al girar el móvil: la lista va en `rememberSaveable` como `IntArray`, un tipo que un `Bundle` sabe guardar.

---

## 4. Favoritos por cuenta

### 4.1 Qué hace

- **Estrella** en cada línea de la lista (a la derecha de `[INSPECT]`) y en la tarjeta de la molécula (junto al nombre y la fórmula).
- **Filtro** «solo favoritos»: una estrella junto al buscador. Se combina con la búsqueda («ATP» entre los favoritos). El contador dice «FAVOURITES FOUND: n». Vacío: «No favourites yet. Tap the star of a ligand to keep it here.» y un botón «Show all».
- **Por cuenta:** cada usuario ve **sus** estrellas. Al bloquearse la app (cada salida a segundo plano, documento 05) las estrellas se **borran de la memoria al instante**: la siguiente persona que entre no ve, ni un fotograma, las del anterior.

### 4.2 La estrella: un vector, no un carácter

Se comprobó con `fontTools`: **ninguna** de nuestras fuentes tiene ★ (U+2605) ni ☆ (U+2606), igual que pasaba con los subíndices (documento 15). Son dos `VectorDrawable` (`ic_star`, `ic_star_outline`) con una estrella **calculada**, no copiada de ningún juego de iconos: diez vértices alternando radio exterior 10,5 y radio interior 10,5 × 0,381966 (la proporción de la estrella regular {5/2}), cada 36°. Así no hay licencia que comprobar.

Cada estrella es una tecla de **48 dp** con descripción («Favourite ATP») y estado en palabras («favourite» / «not favourite»).

### 4.3 Dónde y cómo se guardan: `FileFavoritesStore`

| Decisión | Por qué |
|----------|---------|
| Un fichero de texto **por cuenta** en `noBackupFilesDir/favorites` | Junto a las cuentas; fuera de las copias en la nube (documento 05) |
| Nombre del fichero = **SHA-256 del nombre de usuario** (32 cifras hex): `fav_3f9a….txt` | Las cuentas están cifradas (documento 05); un fichero `fav_adept42.txt` revelaría **qué cuentas existen** con solo listar la carpeta |
| Un id por línea, ordenados | Legible al depurar, estable para comparar |
| **Cada id se valida al LEER** con la misma regla que una descarga (`LigandRepository.isValidId`, ahora pública) | El fichero es nuestro, pero un fichero en disco sigue siendo una **entrada**: un `../../etc` o un `<script>` no deben llegar a la lista ni a una petición de red |
| Máximo **2000** | Más que el catálogo (1243): techo contra un fichero dañado o fabricado |
| Escritura **atómica** (temporal + renombrar) | El mismo patrón que la caché y las cuentas: nunca un fichero a medias |
| **Nunca lanza** | Si no se puede leer: «sin favoritos». Si no se puede escribir: `false`, y el cambio sigue en memoria hasta que se cierre la app. Son una comodidad |

¿Por qué no dentro del fichero cifrado de las cuentas? Mezclaría dos cosas con ritmos distintos: cada estrella obligaría a **volver a cifrar todas las cuentas**, y un fallo al guardar una estrella podría tocar el fichero más delicado de la app. Los favoritos no son secretos; **quién** tiene cuenta sí, y eso lo protege el hash del nombre.

---

## 5. Ajustes

### 5.1 Qué se puede ajustar

| Ajuste | Valores | Efecto |
|--------|---------|--------|
| **Model at start** | Los cuatro modelos (tecla CHANGE) | El modelo con el que **se abre** cada molécula; la tecla MODEL sigue cambiándolo dentro |
| **Hydrogens at start** | ON / OFF | Si los hidrógenos se ven al abrir |
| **Sounds** | ON / OFF | Los sonidos del cogitador (documento 14). Con ON, el **modo silencio del móvil sigue mandando** |

«Applies to the next molecule you open»: la molécula abierta no cambia. La pantalla de la molécula lee el ajuste **una vez**, como valor inicial de su `rememberSaveable`.

Se llega con la **rueda dentada** (`ic_cog`, ya del diseño) en la cabecera de la lista. **Sin botón «Guardar»**: cada cambio se guarda en el acto, como en los ajustes de Android.

### 5.2 Para toda la app, no por cuenta

Los ajustes dicen **cómo se comporta la app en este móvil**: no son privados. Los favoritos sí son de cada persona. Hacer los ajustes por cuenta obligaría a mostrar valores por defecto en la pantalla de acceso y a cambiarlos al entrar: sin ganancia.

### 5.3 `SettingsCodec`: leer con perdón

Los ajustes se guardan como **texto** en `SharedPreferences` (`settings.xml`), pasando por un códec puro:

- El modelo se guarda por **nombre** (`"SPACE_FILLING"`), no por posición: si mañana se inserta un modelo, las posiciones cambian y los nombres no.
- Al leer, **cada ajuste por separado**: una clave que falta, un modelo desconocido (una versión futura, un fichero dañado) o un booleano que no es exactamente `true`/`false` devuelve **el valor por defecto de ese ajuste**, y los demás se conservan. `toBooleanStrictOrNull` (no `toBoolean`, que convierte cualquier cosa rara en `false`).

### 5.4 Silenciar: `SoundPlayer.setMuted`

`CogitatorSounds` tiene ahora un `muted` `@Volatile` que cuenta igual que el modo silencio del móvil: **cualquiera de los dos** basta para callar (`SoundGate` no cambia). Al silenciar, se paran los sonidos en curso: el módem no debe seguir sonando después de «OFF».

---

## 6. El controlador común: `PreferencesController`

Ajustes y favoritos viven en un controlador **sin Android** (como `AuthController`), dentro de un `PreferencesViewModel` que lo mantiene vivo en los giros.

| Responsabilidad | Cómo |
|-----------------|------|
| Cargar los ajustes al empezar | En el hilo de disco |
| `updateSettings(cambio)` | Recibe una **función** del valor actual, no un valor nuevo: dos toques rápidos en CHANGE avanzan **dos** modelos, nunca uno (los dos no parten del mismo valor viejo) |
| `onUserChanged(nombre?)` | `SwiftyApp` lo llama con `AuthUiState.signedInAs`, campo nuevo: **quién** ha entrado (no el campo `username` del formulario, que guarda el último nombre por comodidad) |
| Carga tardía | Cada cambio de usuario incrementa una **generación**; una carga que termina cuando el usuario ya cambió **se tira** |
| Estrella antes de cargar | Hasta que llegan los favoritos de **esta** generación (`ready`), un toque de estrella **se ignora**. Si no, la carga lo sobrescribiría en memoria, y su guardado podría pisar el fichero real con una lista casi vacía. La carga dura milisegundos |
| Disco en orden | `Dispatchers.IO.limitedParallelism(1)`: **un solo hilo** para el disco. Dos estrellas seguidas se guardan **en el orden** en que se tocaron; nunca dos escrituras del mismo fichero a la vez. Estable desde kotlinx.coroutines **1.9.0**, la versión del proyecto |

---

## 7. Pruebas y mutación

### 7.1 Pruebas nuevas (42)

| Clase | Pruebas | Qué fija |
|-------|--------:|----------|
| `MeasurementTest` | 12 | Hasta 4 y volver a empezar; quitar con un segundo toque (también con la lista llena); hidrógenos ocultos; distancia; ángulos conocidos (90, 180, 0, 60); **tetraédrico 109,47°**; el ángulo es del átomo central; ángulo indefinido; *cis* 0 / *trans* 180; **signo IUPAC**; la torsión no depende de las longitudes; tres en línea → indefinida |
| `MeasureTextTest` | 5 | 0, 1, 2, 3 y 4 átomos; «undefined» y menos ASCII |
| `FileFavoritesStoreTest` | 10 | Ida y vuelta; sin fichero; un fichero por cuenta; **el nombre no revela la cuenta**; formato y sin temporales; **fichero manipulado** (`../../etc`, `<script>`, minúsculas, demasiado largo, espacios); no se escriben ids malos; techo de 2000; conjunto vacío; carpeta imposible sin excepciones |
| `SettingsAndFavoritesTest` | 12 | Códec: ida y vuelta, vacío, un valor raro solo resetea el suyo, por nombre; estrella y filtro; controlador: carga y guardado, cambios encadenados, **los favoritos siguen al usuario**, la estrella se guarda para quien ha entrado, sin nadie no hace nada, **estrella antes de cargar**, **carga tardía tirada** |
| `AuthControllerTest` | 1 | `signedInAs`: el nombre al entrar, `null` al bloquear, y el campo del formulario no cambia |
| `ListTextsTest` | 2 | El contador con el filtro; el mensaje del vacío |

Las dos pruebas de **concurrencia** usan un despachador manual: las tareas se guardan en una lista y se ejecutan cuando la prueba quiere. Así se fabrica a voluntad el instante peligroso (el toque llega antes que la carga, el usuario cambia antes de que termine la lectura) sin `sleep` ni suerte.

### 7.2 Mutantes (18, todos muertos)

| Pieza | Mutantes |
|-------|----------|
| Regla | Sin deshacer · lista llena con `>` · ángulo en A en vez de en B · torsión sin signo · signo invertido · sin comprobar tres en línea · sin filtrar invisibles · **ángulo de la tarjeta con los átomos equivocados** |
| Favoritos | Sin generación · sin `ready` · bloquear no borra · nombre de fichero en claro · leer sin validar · sin techo · escribir sin validar |
| Ajustes | No guardar · modelo por posición · booleano permisivo (`toBoolean`) |

**El superviviente.** «Ángulo en C3 calculado con C1–C3–C4 en vez de C2–C3–C4» pasó la primera vez: en la geometría de la prueba **los dos daban 90°**. La prueba no distinguía el bueno del malo. Se movió C4 (de z = 1,5 a 2,5): el correcto da **135°** y el equivocado ≈ 126°, y la torsión sigue en 90°. Moraleja: **una geometría de prueba demasiado simétrica tapa errores**; un ángulo de 90° coincide con muchos otros 90°.

**Sin prueba automática:** la escena (halos y líneas de la regla), las pantallas, `PrefsSettingsStore` (Android) y el silencio de `CogitatorSounds`. Sección 8.

**Toda la batería: 452 verdes.**

---

## 8. Cómo probarlo en el móvil

**Regla**
1. Abrir `ATP`, tecla **RULER** (se enciende). Tocar dos carbonos **enlazados**: ~1,5 Å y una línea verde entre ellos.
2. Un tercero enlazado al segundo: el ángulo (≈ 109° en un carbono sp³ de la ribosa; ≈ 120° en uno del anillo aromático de la adenina).
3. Un cuarto: la torsión con signo y los dos ángulos.
4. Tocar uno de los medidos: sale de la medida. Tocar el vacío: se borra todo. Tocar una varilla: nada.
5. Ocultar los hidrógenos con uno medido: desaparece de la medida. Girar el móvil: la medida sigue.
6. RULER apagada: vuelve la selección normal.

**Favoritos**
7. En la lista, estrella en `ATP` y `HEM`; filtro: solo esos dos, «FAVOURITES FOUND: 2»; escribir «A» con el filtro: solo `ATP`.
8. Abrir `HEM`: la estrella de la tarjeta está llena; quitarla, volver: ya no está en el filtro.
9. **Otra cuenta:** bloquear (Home y volver), crear otra cuenta: **ninguna** estrella. Volver a la primera: las suyas.
10. Cerrar la app del todo (Recientes) y abrir: las estrellas siguen.

**Ajustes**
11. Rueda dentada → Model at start: **Space-filling**, Hydrogens: **OFF**. Abrir una molécula: se abre así. La molécula que ya estaba abierta no cambió.
12. Sounds **OFF**: ni teclas ni módem; ON: vuelven (y con el móvil en silencio, siguen callados).
13. Cerrar y abrir la app: los ajustes siguen.

---

## 9. Trampas

1. **arccos para la torsión:** pierde el signo y la precisión cerca de 0° y 180°. atan2 con seno y coseno.
2. **Cociente fuera de [−1, 1] por redondeo:** arccos da NaN. Acotar.
3. **Tres átomos en línea:** torsión indefinida. Decirlo («undefined»), no inventarlo.
4. **Pruebas geométricas demasiado simétricas:** ángulos de 90° que coinciden aunque el cálculo esté mal. Usar geometrías donde el error cambie el número.
5. **Guardar por cuenta con el nombre en claro en el nombre del fichero:** revela las cuentas. Hash.
6. **Confiar en un fichero propio:** validar al leer, con techo.
7. **Carga asíncrona y toques tempranos:** el resultado de la carga pisa el toque. Generación + `ready`.
8. **Escrituras concurrentes del mismo fichero:** un solo hilo para el disco.
9. **Un `enum` guardado por posición:** se rompe al insertar un valor. Por nombre.
10. **`toBoolean()` permisivo:** «maybe» se vuelve `false` en silencio. `toBooleanStrictOrNull()` y valor por defecto.
11. **Actualizar ajustes con valores en vez de funciones:** dos toques rápidos parten del mismo valor viejo y uno se pierde.
12. **Bloquear sin vaciar los favoritos en memoria:** la siguiente persona vería, un instante, las estrellas de la anterior.
13. **Glifos que la fuente no tiene** (★): Android los busca en otra fuente. Vector.

---

## 10. Alternativas

| Alternativa | Por qué no |
|-------------|-----------|
| **Room** (SQLite) para favoritos | Para decenas de ids por cuenta, un fichero de texto es más simple, igual de fiable con escritura atómica y se prueba con una carpeta temporal |
| **DataStore** (Preferences) para ajustes | Es la recomendación moderna (asíncrono, sin los bloqueos de `SharedPreferences`), pero añade una dependencia por tres valores. `SharedPreferences` + `apply()` en el hilo de disco evita los bloqueos aquí. Si crecieran los ajustes, DataStore |
| Favoritos dentro del fichero **cifrado** de las cuentas | Cada estrella re-cifraría todas las cuentas y arriesgaría el fichero más delicado (§4.3) |
| Favoritos globales (no por cuenta) | La app tiene cuentas: lo natural es que cada uno tenga lo suyo; y no filtra nada entre personas |
| Regla con **pulsación larga** (sin modo) | No se descubre sola y choca con el gesto de girar; una tecla con estado es explícita y accesible |
| Medir con **etiquetas en 3D** sobre las líneas | Texto en la escena (`ViewNode`, texturas): coste de rendimiento; la tarjeta ya da los números |
| **Etiquetas** de átomos (VII.3) | Mismo coste, multiplicado por 256 |
| ***Onboarding*** (VII.2) | La app ya explica sus gestos en la tarjeta y en la pantalla de arranque; poco valor para la defensa |
| Varios **formatos**, **vídeo**, **comparar** dos moléculas (VII.5) | Mucho trabajo (codificación de vídeo, dos escenas) para poco valor frente a lo hecho |

---

## 11. Decisiones

| # | Decisión | Estado | Motivo |
|---|----------|--------|--------|
| D54 | **Regla**: 2/3/4 átomos → distancia / ángulo / torsión (atan2, signo IUPAC, `null` si indefinida); tecla RULER con estado; toques de la regla aparte de la selección | Decidida | VII.3 «medir distancias y ángulos»; la torsión sale casi gratis |
| D55 | **Favoritos por cuenta** en `noBackupFilesDir/favorites`, un fichero por cuenta con **nombre = SHA-256** del usuario, ids validados al leer y al escribir, techo 2000, escritura atómica; vacíos al bloquear | Decidida | VII.5; no revela cuentas; entrada validada |
| D56 | **Ajustes** de toda la app (modelo y hidrógenos al abrir, sonidos) en `SharedPreferences` por un códec puro y permisivo campo a campo; cambios como funciones; disco en un solo hilo | Decidida | VII.2; seguro ante datos raros y toques rápidos |

---

## 12. Qué recordar

- **Distancia, ángulo, torsión** = 2, 3, 4 puntos. Producto escalar para el ángulo; producto vectorial (normales) y **atan2** para la torsión con signo.
- **Lo indefinido se dice**, no se inventa.
- **Geometrías de prueba asimétricas**: si el error y el acierto dan el mismo número, la prueba no prueba nada.
- **Un nombre de fichero también filtra información**: hash.
- **Todo lo que se lee de disco es entrada**: validar y acotar, aunque lo escribiera la propia app.
- **Asincronía**: generación para tirar resultados viejos, `ready` para no aceptar cambios antes de cargar, un solo hilo para escribir en orden.
- **Cambios como funciones** del estado actual, no como valores.
- **Leer con perdón**: cada ajuste con su valor por defecto; un dato raro no tumba los demás.
