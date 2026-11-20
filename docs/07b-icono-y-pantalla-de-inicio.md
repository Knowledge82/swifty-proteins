# 07b · Icono y pantalla de inicio (*splash*): qué pide la hoja de evaluación y cómo se cumple

> **Nota posterior (documento [`08c-diseno-v2-cogitador.md`](08c-diseno-v2-cogitador.md), decisión D32, que sustituye a D25):** la *splash* del sistema **ya no se retiene**; el *launch screen* de más de un segundo es ahora la **pantalla de arranque del cogitador** (1600 ms, una vez por arranque). `SplashHold` y sus pruebas se **borraron**. El icono es el de la v2. La teoría de este documento (qué es la ventana de arranque, `core-splashscreen`, `installSplashScreen()` antes de `super.onCreate`) **sigue valiendo**; el código citado abajo es el de esta etapa.

> **Estado:** escrito y con la parte pura probada en el ordenador (5 pruebas, con mutación). **El resto no se ha compilado ni visto en el móvil**: la biblioteca nueva no se pudo resolver desde este entorno (sin acceso a Maven). Sección 9.

## Índice

1. [Qué pide la hoja de evaluación](#1-qué-pide-la-hoja-de-evaluación)
2. [Estado del icono](#2-estado-del-icono)
3. [Teoría: qué es una pantalla de inicio en Android](#3-teoría-qué-es-una-pantalla-de-inicio-en-android)
4. [La solución: cuatro piezas](#4-la-solución-cuatro-piezas)
5. [`SplashHold.kt` y `MainActivity.kt` línea a línea](#5-splashholdkt-y-mainactivitykt-línea-a-línea)
6. [Simulación: una línea de tiempo con números](#6-simulación-una-línea-de-tiempo-con-números)
7. [Trampas y límites conocidos](#7-trampas-y-límites-conocidos)
8. [Cómo probarlo en el móvil](#8-cómo-probarlo-en-el-móvil)
9. [Qué está verificado y qué no](#9-qué-está-verificado-y-qué-no)
10. [Pruebas y mutación](#10-pruebas-y-mutación)
11. [Alternativas](#11-alternativas)
12. [Decisiones](#12-decisiones)
13. [Qué recordar](#13-qué-recordar)

---

## 1. Qué pide la hoja de evaluación

Textualmente: *«Verify that the application has an icon on the home screen and a launch screen that remains visible for at least 1 second.»* Son **dos comprobaciones**:

| # | Comprobación | Cómo la hará el evaluador | Estado |
|--:|--------------|---------------------------|--------|
| 1 | La app tiene **icono** en la pantalla de inicio del móvil | Mirar el lanzador | **Cumplido** (sección 2). Cuando llegue el diseño visual, se **sustituye** por el definitivo |
| 2 | Hay una **pantalla de lanzamiento** y se ve **al menos 1 segundo** | Abrir la app desde cero y contar | **Era el hueco**: sin nada, Android enseña un rectángulo vacío unos 200 ms. Se cumple con las secciones 4 y 5 |

**Por qué hay que tomárselo en serio:** es una pregunta de *sí/no* de la hoja; si el evaluador cuenta «uno-dos» y la pantalla desaparece antes, **se pierde ese punto** por algo que se arregla con unas líneas.

---

## 2. Estado del icono

- **Existe** y es un icono **adaptable** (*adaptive icon*): `mipmap-anydpi-v26/ic_launcher.xml` y `ic_launcher_round.xml` dicen «fondo = un color, primer plano = una imagen». Hay imágenes para cinco densidades (`mdpi` … `xxxhdpi`) y el manifiesto lo enlaza (`android:icon`, `android:roundIcon`).
- **Actualización (documento 08b, sección 5):** el icono **ya es el del diseño nuevo**: tres capas **vectoriales** (fondo hierro `#17171A`, emblema, y una capa **monocroma** para los iconos «temáticos» de Android 13+), con el emblema medido dentro de la zona segura (30,7 de 33 dp), e imágenes PNG aparte para Android 7. Las reglas técnicas son las de siempre: lienzo de **108 dp**, lo importante dentro de los **66 dp centrales** (el lanzador recorta con círculo, cuadrado redondeado, etc.).
- **Un fallo que se corrigió aquí:** en el repositorio había **dos copias** de los recursos: `app/src/main/res/mipmap-*` (las buenas) y `app/src/main/res/res/mipmap-*` (**una carpeta `res` dentro de `res`**, resto de descomprimir un archivo en el sitio equivocado). Se comprobó que los **18 ficheros eran idénticos** y se **borró** la copia anidada. `res/res` **no es un directorio de recursos válido** (los nombres válidos son `drawable`, `mipmap`, `values`, `xml`…); la sesión anterior compiló igualmente, pero es basura que puede dar un aviso o un error con otra versión de las herramientas.

---

## 3. Teoría: qué es una pantalla de inicio en Android

Cuando tocas el icono, pasan cosas **antes** de que se ejecute una sola línea de nuestro código:

```
toque ─► el sistema crea el proceso ─► (hueco: nuestro código aún no corre) ─► Application ─► MainActivity.onCreate ─► primer fotograma
          └──── el sistema ya dibuja una «ventana de arranque» (*starting window*) con el tema de la actividad ───────┘
```

Esa **ventana de arranque** es la pantalla de inicio. Es **del sistema**: se dibuja con el **tema** de la actividad (colores e imagen), **no** con Compose. Hay dos épocas:

- **Antes de Android 12** (API < 31): la ventana de arranque usa el atributo `windowBackground` del tema. Cada app pone el suyo; es una imagen estática.
- **Android 12 en adelante:** el sistema **impone** su propia pantalla de inicio (*SplashScreen API*): **color de fondo + icono** (por defecto, el icono de la app). **Dura lo que tarde la app en dibujar su primer fotograma**: si la app arranca en 200 ms, la pantalla se ve **200 ms**.

**La biblioteca `androidx.core:core-splashscreen`** trae la API de Android 12 **a todas las versiones** (nosotros: `minSdk = 24`). Hace tres cosas:

1. Un **tema padre** (`Theme.SplashScreen`) con los atributos: fondo, icono, tema al que pasar después.
2. `installSplashScreen()`: al llamarla, **cambia** el tema «de arranque» por el normal (`postSplashScreenTheme`) y nos da el control.
3. **`setKeepOnScreenCondition { ... }`**: *«mientras esta función devuelva `true`, no quites la pantalla de inicio»*. El sistema **la consulta antes de dibujar cada fotograma**; cuando devuelve `false`, quita la pantalla (con una animación de salida).

**Esa tercera pieza es la que nos da el segundo mínimo.** Y hay una condición de oro: **la condición se consulta en el hilo principal en cada fotograma, así que debe ser barata y, sobre todo, tiene que acabar siempre.** Atarla a «ya cargó la red» sería peligroso (un fallo y la app queda **para siempre** tapada). La nuestra depende **solo del reloj**.

---

## 4. La solución: cuatro piezas

| Pieza | Fichero | Qué hace |
|-------|---------|----------|
| Dependencia | `gradle/libs.versions.toml`, `app/build.gradle.kts` | `androidx.core:core-splashscreen:1.2.0` (versión estable según la página oficial de versiones de AndroidX; **no se pudo resolver aquí**) |
| Tema de arranque | `res/values/themes.xml` | `Theme.SwiftyProteins.Starting`: fondo = hierro (`swifty_iron`), icono = el emblema vectorial, después → `Theme.SwiftyProteins` (oscuro desde el documento 08b) |
| Manifiesto | `AndroidManifest.xml` | La **actividad** (no la aplicación) usa el tema de arranque |
| Lógica y enlace | `splash/SplashHold.kt`, `MainActivity.kt` | «Mantén la pantalla al menos 1500 ms» |

El tema (`themes.xml`):

```xml
<style name="Theme.SwiftyProteins.Starting" parent="Theme.SplashScreen">
    <item name="windowSplashScreenBackground">@color/swifty_iron</item>
    <item name="windowSplashScreenAnimatedIcon">@drawable/ic_launcher_foreground</item>
    <item name="postSplashScreenTheme">@style/Theme.SwiftyProteins</item>
</style>
```

Y en el manifiesto, dentro de `<activity>`: `android:theme="@style/Theme.SwiftyProteins.Starting"`. Es la actividad la que arranca con el tema de arranque; al llamar a `installSplashScreen()` pasa al tema normal.

---

## 5. `SplashHold.kt` y `MainActivity.kt` línea a línea

### 5.1 La regla del tiempo (pura, probada en el ordenador)

```kotlin
// SplashHold.kt · líneas 12–32
12  class SplashHold(
13      private val startMillis: Long,
14      private val minVisibleMillis: Long = MIN_VISIBLE_MILLIS,
15  ) {
16
17      /** `true` while the splash must stay. It ALWAYS ends: it depends only on the elapsed time, never on loading anything. */
18      fun shouldKeep(nowMillis: Long): Boolean {
19          val elapsed = nowMillis - startMillis
20          // A clock that goes backwards (it should not: uptime is monotonic) must not freeze the app behind the splash.
21          if (elapsed < 0) return false
22          return elapsed < minVisibleMillis
23      }
24
25      companion object {
26          /**
27           * Hold time counted from the moment our activity starts. The system splash is already visible a little BEFORE
28           * (process start), so the real visible time is longer than this; 1500 leaves margin over the required 1000.
29           */
30          const val MIN_VISIBLE_MILLIS = 1500L
31      }
32  }
```

- **Línea 13, `startMillis`:** cuándo empezó la cuenta. **Se la damos desde fuera**: la clase no lee ningún reloj. Así se prueba con números inventados.
- **Línea 14, `minVisibleMillis`:** el mínimo, por defecto 1500.
- **Líneas 18–23, `shouldKeep(nowMillis)`:** `true` = «sigue la pantalla». Es **solo una resta y una comparación**.
  - **Línea 21:** si el reloj fuese hacia atrás (`elapsed < 0`) devuelve `false`: **jamás** debe congelar la app. El reloj que usamos (`uptimeMillis`) es monótono, así que es un cinturón de seguridad más que un caso real; **hay una prueba** y su mutante.
- **Línea 30, `1500`:** la hoja pide 1000. **El conteo empieza en `onCreate`**, pero la pantalla ya llevaba visible desde que se creó el proceso, así que el tiempo **real** visible es **mayor**. 1500 deja margen para que el evaluador no «cuente justo». **Es una estimación de gusto**: si parece largo, 1200 aún cumple (hay una prueba que obliga a ≥ 1200).

### 5.2 El enlace con Android

```kotlin
// MainActivity.kt · líneas 23–41
23  override fun onCreate(savedInstanceState: Bundle?) {
24      // MUST come before super.onCreate: it swaps the "starting" theme for the normal one and takes over the splash.
25      // The system removes the splash when the first frame is ready; we ask it to stay at least SplashHold.MIN_VISIBLE_MILLIS.
26      val hold = SplashHold(startMillis = SystemClock.uptimeMillis())
27      installSplashScreen().setKeepOnScreenCondition { hold.shouldKeep(SystemClock.uptimeMillis()) }
28
29      super.onCreate(savedInstanceState)
30      // The app is always dark (D26): light icons on the system bars, whatever the phone's own theme. With the
31      // default, a phone in LIGHT mode would draw dark icons on our dark background (invisible clock and battery).
32      enableEdgeToEdge(
33          statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
34          navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
35      )
36      setContent {
37          SwiftyProteinsTheme {
38              SwiftyApp(viewModel)
39          }
40      }
41  }
```

- **Línea 26:** se anota la hora de inicio con `SystemClock.uptimeMillis()` (milisegundos desde el arranque del móvil, **sin saltos** si el usuario cambia la hora).
- **Línea 27, `installSplashScreen().setKeepOnScreenCondition { ... }`:** la lambda **se evalúa antes de cada fotograma**; devolvemos lo que diga `shouldKeep`. Cuando pasan 1500 ms devuelve `false` y el sistema quita la pantalla.
- **Orden (líneas 24–29): `installSplashScreen()` va ANTES de `super.onCreate`.** Es el requisito de la biblioteca: ahí es donde cambia el tema de arranque por el normal. Si se llama después, el tema no se cambia bien y puede verse mal o fallar.
- **Líneas 30–40:** todo lo demás queda **igual**: `enableEdgeToEdge(...)` (desde el documento 08b, con las barras del sistema **siempre oscuras**) y el contenido de Compose. **Mientras la pantalla de inicio está encima, la app ya se está construyendo debajo** (la lista, el `ViewModel`): el segundo extra **no retrasa** nada, solo lo tapa.

---

## 6. Simulación: una línea de tiempo con números

*(Los milisegundos son **ilustrativos**; lo que importa es el orden.)*

**Arranque en frío** (la app no estaba en memoria):

| t (ms) | Qué pasa | ¿Se ve la pantalla de inicio? |
|-------:|----------|:---:|
| 0 | Toque en el icono. El sistema pinta la ventana de arranque (fondo + icono) | **sí** |
| 0–180 | Se crea el proceso y la `Application` | sí |
| 180 | `onCreate`: se anota `start = 180`; se llama a `installSplashScreen` | sí |
| 180–400 | Se crea el `ViewModel`, se lee `ligands.txt` (4,9 KB), Compose compone la lista | sí |
| 400 | **La lista ya está lista**, pero `shouldKeep(400)`: `400 − 180 = 220 < 1500` → `true` | **sí** (sin la regla, aquí desaparecería) |
| 1680 | `shouldKeep(1680)`: `1500 < 1500` es falso → `false`. Animación de salida | se va |
| ≈ 1800 | Se ve la lista | no |

**Tiempo visible total ≈ 1,7 s ≥ 1 s.** ✔

**Girar el móvil con la app abierta:** la actividad se recrea y `onCreate` se ejecuta otra vez; se crea un `SplashHold` **nuevo**, pero **no hay ventana de arranque** (no es un arranque), así que **no se ve nada**. *(No se ha comprobado en el móvil.)*

**Volver desde segundo plano (la app seguía viva):** es un arranque «en caliente»; Android 12+ **sí** puede mostrar la pantalla de inicio, y nuestra condición la mantiene si hay `onCreate`. *(Sin comprobar.)*

---

## 7. Trampas y límites conocidos

1. **`installSplashScreen()` después de `super.onCreate`:** mal. Va antes (5.2).
2. **Atar la condición a algo que pueda no terminar** (red, base de datos): la app quedaría tapada. La nuestra depende solo del reloj.
3. **El icono se recorta en círculo** en Android 12+: lo importante tiene que estar dentro del círculo central (2/3 del lienzo). El primer plano actual **se dibujó para** respetar la zona segura (66 de 108 dp = 61 %), pero **no se ha visto en el móvil con la pantalla de inicio**.
4. **Android 12+ ignora el diseño propio de «ventana de arranque»**: solo se pueden poner **color, icono (animado o no) y, según la versión, una imagen de marca**. **Una pantalla de inicio muy elaborada no cabe aquí**; para eso está la alternativa 11.2.
5. **Cada arranque en frío cuesta 1,5 s** al usuario real. Es el precio de cumplir la hoja; lo ponemos como constante para poder bajarlo.
6. ~~El primer plano es una imagen de píxeles~~ **Resuelto en el documento 08b:** ahora es un **vector**, nítido a cualquier tamaño.
7. **Marca «Swifty Proteins» ausente:** la pantalla de inicio del sistema solo enseña el icono. Si se quiere el nombre y animación, ver 11.2.

---

## 8. Cómo probarlo en el móvil

1. **Cerrar la app del todo** (arrastrarla fuera de «recientes» o *Forzar detención*) y tocar el icono.
2. **Qué debe verse:** primero el **color de fondo con el icono**, **~1,5 s**, luego la lista. **Se puede grabar la pantalla** y contar fotogramas (a 60 fps, 90 fotogramas = 1,5 s).
3. **Alternativa con medición exacta**, desde el ordenador con el móvil conectado: `adb shell am start -W -n dev.knowledge82.swiftyproteins/.MainActivity` (con la app cerrada). La línea `TotalTime` es el tiempo hasta el primer fotograma; **debería** salir ≥ 1500. *(El significado exacto de `TotalTime` con la API de pantalla de inicio no está comprobado: úsalo como orientación, no como prueba.)*
4. **Que no se quede colgada:** repetir 5 veces; si alguna vez no sale la lista, **es un fallo**: apuntar el mensaje del Logcat.
5. Probar también **en modo oscuro** y con el móvil **girado** al abrir (sección 6).

---

## 9. Qué está verificado y qué no

**Verificado (en el ordenador):** la regla del tiempo (5 pruebas, 4 mutantes muertos). El código de `MainActivity` pasa el comprobador de tipos contra **declaraciones mínimas escritas a mano** de la API (no contra la biblioteca real).

**NO verificado (honesto):**
- **Que la biblioteca `core-splashscreen:1.2.0` se resuelva y compile.** La versión sale de la página oficial de versiones de AndroidX; **no se pudo bajar** desde este entorno. Si Gradle protesta, pega el error. Además, la biblioteca puede **subir la versión de `androidx.core`** que tira del proyecto (hoy declarado: 1.10.1): es lo normal, pero es un cambio de dependencias.
- **Que `setKeepOnScreenCondition { ... }` acepte una lambda** (es una interfaz funcional en Kotlin; es el uso estándar que recuerdo, **no lo he comprobado** contra el código de la biblioteca).
- **Que se **vea** bien:** icono recortado, color de fondo, animación de salida. Todo en el móvil.
- **Que dure ≥ 1 s de verdad:** por la aritmética sí; **medirlo** (sección 8).
- **El comportamiento en otros fabricantes y versiones** (el A33 trae Android 12–14 según la actualización).

---

## 10. Pruebas y mutación

| Fichero | Pruebas | Qué demuestra |
|---------|--------:|---------------|
| `SplashHoldTest.kt` | 5 | La pantalla se mantiene al inicio y hasta el último milisegundo anterior al mínimo; se quita justo en él; **siempre termina** (aunque pasen horas); un reloj hacia atrás no la congela; el valor por defecto está por encima del segundo exigido |

| # | Avería | Resultado |
|--:|--------|-----------|
| 1 | Quitar la guarda del reloj hacia atrás | **Muerto** (1 prueba falla) |
| 2 | `elapsed < min` → `elapsed <= 0` (no mantener nunca) | **Muerto** |
| 3 | `elapsed < min` → `elapsed < min + 1` (un ms de más) | **Muerto** |
| 4 | `MIN_VISIBLE_MILLIS` 1500 → 500 (incumpliría la hoja) | **Muerto** |

---

## 11. Alternativas

| # | Alternativa | Ventajas | Inconvenientes |
|--:|-------------|----------|----------------|
| 11.1 | **Lo elegido:** pantalla de inicio del sistema + retención por tiempo | Es la forma **estándar**; sin parpadeos; pocas líneas | Solo color + icono |
| 11.2 | **Una pantalla de inicio propia en Compose** (primera «pantalla» de la app: logo, nombre, animación de engranajes, 1,5 s y luego la lista) | **Libertad total de diseño** (encaja con la dirección visual); se puede animar | Se ve **después** de la del sistema (un parpadeo del fondo si no coinciden); más código; **lo más probable cuando llegue el diseño** |
| 11.3 | `Thread.sleep(1500)` en `onCreate` | Una línea | **Bloquea el hilo principal** (la app congelada); puede disparar el aviso «la aplicación no responde». **Descartada** |
| 11.4 | Atar la pantalla a «lista cargada» | Desaparece cuando hay algo que enseñar | **No garantiza el segundo mínimo** (si carga en 200 ms, falla la hoja) y puede **no acabar** |
| 11.5 | `windowBackground` propio (sin biblioteca) | Sin dependencia | Solo funciona bien **antes** de Android 12; en 12+ el sistema pone la suya igualmente |

---

## 12. Decisiones

| # | Decisión | Estado | Motivo |
|---|----------|--------|--------|
| D25 | La pantalla de inicio es la **del sistema** (`core-splashscreen`), mantenida **1500 ms** por una regla que **solo mira el reloj** | Provisional | Cumple la hoja con el mínimo código y sin riesgo de colgarse; si el diseño visual pide una pantalla más rica se pasa a la alternativa 11.2 |

---

## 13. Qué recordar

- La hoja pide **icono + pantalla de lanzamiento ≥ 1 s**. Icono: ya existía. Pantalla: la **añadimos**.
- La pantalla de inicio **es del sistema** y desaparece **cuando la app dibuja su primer fotograma**; por eso se ve ~200 ms si no hacemos nada.
- **`installSplashScreen()` antes de `super.onCreate()`**, y **`setKeepOnScreenCondition` solo con el reloj**: debe **terminar siempre**.
- **1500 ms** (el mínimo de la hoja es 1000): la cuenta empieza en `onCreate`, que ya es **más tarde** que el toque.
- **No compilado con la biblioteca real**: si Gradle protesta con la versión, pega el error.
- Se **borró** la carpeta `res/res` (copia idéntica por error).
