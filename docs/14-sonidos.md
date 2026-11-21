# 14 · Sonidos del cogitador: síntesis desde cero y reproducción

> **Estado:** **escrito**, **versión 2** («grimdark», §1.1). Los diez sonidos están **generados** por `tools/sound/make_sounds.py` (numpy + scipy + ffmpeg) y guardados en `app/src/main/res/raw/`; el script es **reproducible byte a byte**. La regla de cuándo suena algo (`SoundGate`) y la etiqueta que evita repetir sonidos al girar la pantalla (`loadSoundTag`) están **probadas en la JVM** (8 + 5 pruebas, 10 mutantes muertos). El reproductor (`CogitatorSounds`) y los ganchos en las pantallas **compilan contra *stubs***. La v1 se oyó en el A33 (todo suena; demasiado «ciencia ficción» y alta); la v2, **pendiente de oír en el móvil** (sección 9).

## Índice

1. [Qué pide el enunciado (y qué nos pedimos)](#1-qué-pide-el-enunciado-y-qué-nos-pedimos)
2. [Teoría: el sonido digital en siete ideas](#2-teoría-el-sonido-digital-en-siete-ideas)
3. [Las piezas de la síntesis](#3-las-piezas-de-la-síntesis)
4. [Las diez recetas](#4-las-diez-recetas)
5. [Verlos: forma de onda y espectrograma](#5-verlos-forma-de-onda-y-espectrograma)
6. [Del número al fichero: WAV → Ogg Vorbis](#6-del-número-al-fichero-wav--ogg-vorbis)
7. [Cómo suenan en la app](#7-cómo-suenan-en-la-app)
8. [Pruebas y mutación](#8-pruebas-y-mutación)
9. [Cómo probarlo en el móvil](#9-cómo-probarlo-en-el-móvil)
10. [Trampas](#10-trampas)
11. [Alternativas](#11-alternativas)
12. [Decisión](#12-decisión)
13. [Qué recordar](#13-qué-recordar)

---

## 1. Qué pide el enunciado (y qué nos pedimos)

- **El enunciado no pide sonido.** Es un añadido del equipo (documento 01, §15.4, punto 6): el cogitador de la dirección visual (documentos 08a y 08c) es una máquina que **zumba, teclea y responde**; sin sonido, la mitad de la ambientación se queda en la pantalla.
- **Requisitos que nos ponemos:**
  1. **Nada de muestras ajenas.** Ni grabaciones ni bancos de sonidos: cada sonido sale de **matemáticas** (osciladores, ruido, filtros, envolventes). Así no hay ninguna licencia que justificar, y el proceso se puede **explicar** en la defensa.
  2. **Reproducible:** el mismo script da los mismos ficheros, byte a byte.
  3. **Discreto:** cortos, por debajo del volumen máximo, y **nunca con el móvil en silencio o vibración** (la defensa es en una sala llena de gente).
  4. **Ligero:** los diez ficheros juntos pesan **124 KiB**.
  5. **Nunca bloquea nada:** si un sonido no está listo, no se espera.
  6. **No distrae:** los sonidos **acompañan**; lo que suena a menudo es lo más bajo.

### 1.1 Dos versiones: por qué se rehízo

| | v1 «ciencia ficción» | v2 «grimdark» (la actual) |
|-|----------------------|---------------------------|
| Material | Senos, cuadradas, FM: pitidos, *pings*, glissandos agudos | Metal golpeado (síntesis modal), coro (formantes), órgano, saturación, sala de piedra |
| Registro | 1–2,6 kHz | Fundamentales de 150–450 Hz, nada por encima de ~3 kHz |
| Nivel | Normalizado por **pico** | Normalizado por **sonoridad**, en cuatro niveles |
| Veredicto en el móvil | Todo se oye, pero **molesta**: «sonidos de nave espacial de los 80», no el Culto de la Máquina; y **demasiado alto** | Pendiente |

Tres lecciones del cambio, cada una con su sección:

1. **«Oscuro» no es «grave»** (§2, idea 6). El primer intento de la v2 era de verdad grave: entre el 93 y el 98 % de la energía de la mayoría de sonidos, por debajo de 300 Hz. En auriculares, magnífico; por el **altavoz del móvil**, casi nada. Se movió la oscuridad al timbre y se añadió un indicador al script (`>300 Hz`, el porcentaje de energía que el altavoz puede tocar): hoy, del 44 al 100 %.
2. **El pico no dice cuánto suena** (§2, idea 7). Con el mismo pico que la v1, la tecla de la v2 sonaba **9 dB más fuerte** (sus golpes saturados son densos) y la campana 5 dB más débil. Por eso `finish` ahora iguala **sonoridad**.
3. **Más bajo que la v1.** Medido con la misma vara (`speaker_loudness`) sobre los ficheros de las dos versiones:

   | Sonido | v1 | v2 | Diferencia |
   |--------|---:|---:|-----------:|
   | tecla | −34,8 | −35,8 | −1,0 dB (el único que ya estaba bien) |
   | átomo, línea del arranque | −25,4 / −25,5 | −36,0 | −10,5 dB |
   | barrido de carga | −17,6 | −33,0 | −15,4 dB |
   | entrada «42» | −13,7 | −29,0 | −15,3 dB |
   | rechazo | −22,8 | −29,1 | −6,3 dB |
   | molécula lista | −14,1 | −29,0 | −14,9 dB |
   | captura | −24,9 | −29,0 | −4,1 dB |
   | arranque (máquina y órgano) | −25,6 | −27,0 | −1,3 dB |
   | campana del «DONE» | −13,2 | −27,0 | −13,8 dB |

   La v1 tenía una diferencia de **21 dB** entre su sonido más suave y el más fuerte (la tecla y la entrada), sin ningún criterio: era un efecto de igualar picos. La v2 tiene **9 dB**, y por niveles.

---

## 2. Teoría: el sonido digital en siete ideas

**1. Muestreo.** Un sonido es una presión del aire que cambia con el tiempo. En digital se guarda como una lista de números (**muestras**), tomados a intervalos fijos. Usamos **44 100 muestras por segundo** (la frecuencia del CD). Un segundo de sonido = 44 100 números entre −1 y 1.

**2. Nyquist.** Con *N* muestras por segundo solo se pueden representar frecuencias hasta **N/2**: 22 050 Hz, por encima del oído humano (≈ 20 kHz). Una onda cuadrada o de sierra «perfecta» tiene armónicos infinitos: los que pasan de 22 050 Hz se **pliegan** a frecuencias falsas (*aliasing*, un silbido metálico). Por eso nuestras ondas ricas en armónicos se **filtran** (sección 3).

**3. Cuantización y dBFS.** El fichero guarda cada muestra como un entero de **16 bits** (−32 768 … 32 767). El máximo representable es **0 dBFS** (*decibelios relativos a la escala completa*). Los decibelios son logarítmicos: −6 dB ≈ la mitad de amplitud, −20 dB = una décima.

> Ejemplo: el pico de `snd_key` está en −24 dBFS = 10^(−24/20) = **0,063** de la escala completa. Con 16 bits eso aún deja unos 2000 valores distintos por encima y por debajo de cero: de sobra para un golpe de 60 ms.

**4. Clics.** Un salto brusco de la señal (empezar en 0,5 o cortar mientras aún suena) **contiene todas las frecuencias** a la vez: se oye un clic. Por eso cada sonido empieza y acaba en cero con un **fundido** de unos milisegundos. En el espectrograma un clic se ve como una **raya vertical** de arriba abajo.

**5. Espectrograma.** La forma de onda (amplitud frente a tiempo) dice poco del timbre. El **espectrograma** corta la señal en trocitos (1024 muestras ≈ 23 ms), calcula en cada uno qué frecuencias hay (transformada de Fourier) y pinta: tiempo en horizontal, frecuencia en vertical, intensidad en color. Ahí se **ven** un barrido, un armónico o un clic.

**6. El altavoz del móvil no tiene graves.** Un altavoz de unos milímetros no mueve el aire necesario para frecuencias bajas: por debajo de **~300 Hz** casi no suena (la cifra exacta depende del modelo; la del A33, **sin medir**). Un sonido «grave y oscuro» hecho de 50–150 Hz suena estupendo en auriculares y es un **susurro** en el altavoz. Dos salidas:
   - Poner la oscuridad en el **timbre**, no en el grave: fundamentales de 150–450 Hz y **sin agudos** (filtro paso bajo hacia 1,5–3 kHz). «Sordo» = falta de agudos; «oscuro» = intervalos menores, metal, reverberación.
   - La **fundamental ausente** (*missing fundamental*): si suenan 220, 330, 440 Hz (armónicos 2, 3, 4 de 110 Hz), el oído **reconstruye** el 110 Hz aunque no esté. Por eso saturamos los tonos graves (§3.7): la saturación fabrica esos armónicos, que el altavoz sí reproduce.

**7. Pico frente a sonoridad.** El **pico** es la muestra más alta; la **sonoridad** es cuánta energía hay durante un rato (lo que el oído percibe como «fuerte»). Un golpe corto y un zumbido largo con el **mismo pico** suenan con fuerzas muy distintas: el zumbido, mucho más fuerte. Igualar picos da sonoridades desiguales (§1.1 cuenta cómo nos pasó).

---

## 3. Las piezas de la síntesis

Todo está en `tools/sound/make_sounds.py`. Cada pieza es una función de pocas líneas que devuelve un `numpy.ndarray` de muestras.

### 3.1 Osciladores: de dónde sale el tono

| Pieza | Fórmula | Timbre | Para qué |
|-------|---------|--------|----------|
| `sine(f)` | sen(fase) | Puro, sin armónicos | El sonar del auspex, el *ping*; la materia de los modos (§3.4) y del órgano (§3.6) |
| `saw(f)` | 2·(p − ⌊p + ½⌋), p = fase/2π | Brillante y áspero (todos los armónicos) | La máquina que arranca, el clacson, el servo, la «voz» del coro (§3.5) |
| `noise(n)` | uniforme en [−1, 1] | Todas las frecuencias | El golpe del martillo en los modos, la cola de la reverberación, la molienda del servo |

(La v1 usaba además una cuadrada redondeada, `tanh(6·sen)`, para los pitidos. En la v2 no hay pitidos.)

**La fase acumulada** (`phase_of`) es el detalle que hace posibles los **glissandos** (frecuencia que cambia: la máquina que arranca, el servo que frena). La tentación es escribir `sin(2π·f(t)·t)`, pero eso es incorrecto cuando *f* varía: la frecuencia que se oye es la **derivada** de la fase, y la derivada de f(t)·t no es f(t). Lo correcto es **sumar** la frecuencia muestra a muestra:

```
fase[n] = 2π · (f[0] + f[1] + … + f[n]) / 44 100
```

> Simulación con números redondos: un tono de 1000 Hz avanza 2π·1000/44 100 = **0,1425 rad** por muestra. Si a mitad la frecuencia pasa a 2000 Hz, con la suma la fase sigue desde donde iba y avanza 0,285 rad por muestra: la onda se **comprime sin saltar**. Con `sin(2π·f·t)`, en ese instante el argumento salta de golpe de 2π·1000·t a 2π·2000·t: un **clic**.

**El ruido con semilla.** `np.random.default_rng(42)`: el generador pseudoaleatorio con una semilla fija da **siempre la misma secuencia**. Por eso el ruido es «aleatorio» al oído pero idéntico en cada ejecución (y la semilla es 42, claro).

### 3.2 Filtros: esculpir el timbre

Filtros de **Butterworth** (`scipy.signal.butter` + `lfilter`): respuesta plana en la banda que pasa y caída fuera de ella (24 dB por octava con orden 4; 12 dB con orden 2).

| Filtro | Deja pasar | Uso |
|--------|-----------|-----|
| `lowpass(x, fc)` | Por debajo de *fc* | **El filtro de la oscuridad:** casi todo pasa por uno entre 1,1 y 3 kHz. Doma la sierra; oscurece la cola de la reverberación |
| `highpass(x, fc)` | Por encima de *fc* | El **suelo** de 140 Hz de cada sonido (§3.9); la medida de sonoridad «del altavoz» (300 Hz) |
| `bandpass(x, lo, hi)` | Entre *lo* y *hi* | Las **formantes** de la voz (§3.5), la molienda del servo (180–1100 Hz) |

La frecuencia de corte se da a scipy **normalizada** a Nyquist: `fc / (SR/2)`. Ejemplo: 1200 Hz → 1200 / 22 050 = **0,0544**.

### 3.3 Envolventes: cómo nace y muere el sonido

- **ADSR** (`adsr(n, a, d, s, r)`): **A**taque (de 0 a 1), **D**ecaimiento (de 1 al nivel S), **S**ostenimiento (nivel constante), **R**elajación (a 0). Tramos lineales. Es la envolvente clásica de los sintetizadores.
- **Caída exponencial** (`exp_decay(n, τ)` = e^(−t/τ)): cómo muere algo **golpeado** (una campana, un contacto). Con τ = 0,12 s, a los 0,12 s queda el 37 %, a 0,36 s el 5 %.
- **`fade_out`**: los últimos 12 ms van linealmente a cero. Una parte que se corta mientras suena daría un clic (§2, idea 4). Se descubrió en la v1: el espectrograma de la primera campana mostraba una raya vertical al final.

### 3.4 Síntesis modal: el metal golpeado

Un objeto golpeado (una barra, una campana, la carcasa de una tecla) vibra a la vez en varios **modos**, cada uno con su frecuencia y su ritmo de apagarse. Su sonido es **la suma de senos amortiguados**:

```
sonido(t) = Σ  aᵢ · sen(2π·fᵢ·t) · e^(−t/τᵢ)     + unos ms de ruido filtrado (el martillo)
```

`modal(segundos, [(f, a, τ), …])`. Lo que hace que un golpe suene a **hierro** y no a madera o a cuerda es la **proporción** entre las frecuencias:

| Objeto | Modos (× el primero) | De dónde sale |
|--------|----------------------|---------------|
| **Barra de metal libre** (`BAR`) | 1 · 2,756 · 5,404 · 8,933 | La teoría de la viga de Euler-Bernoulli: no son múltiplos enteros, por eso **no** suena a nota musical sino a «clang» |
| **Campana de iglesia** (`BELL`) | 0,5 (*hum*) · 1 (*prime*) · **1,2** (*tierce*) · 1,5 · 2 · 2,5 · 3 | Los campaneros los afinan así; el **1,2 es una tercera menor**: por eso las campanas suenan **solemnes, tristes** |
| **Tecla, tipo, obturador** | dos o tres modos elegidos a oído (p. ej. 420, 1130, 2600 Hz) | Un cuerpo pequeño y amortiguado: τ de milisegundos = «thock» sordo, no «clic» |

Los modos altos mueren **antes** (τ más corto): el golpe empieza brillante y acaba oscuro, como en la realidad. Es lo mismo que hacía la FM de la v1 (un índice que decae), pero con el control directo de cada parcial.

### 3.5 Formantes: el coro de la máquina

Una vocal es un **filtro**: la boca y la garganta refuerzan ciertas bandas (las **formantes** F1, F2, F3) del zumbido de las cuerdas vocales. Cambiar la vocal es cambiar las formantes, no la nota.

```python
VOWELS = {'o': (400, 750, 2400), 'u': (330, 650, 2300), 'a': (650, 1080, 2450)}   # voz grave masculina, Hz
src  = Σ saw(f0 · desafinación · vibrato)          # 3 «voces» desafinadas ±0,6 %, vibrato de ~5 Hz
voz  = bandpass(src, F1) + 0,6·bandpass(src, F2) + 0,15·bandpass(src, F3)
```

`chant(f0, segundos, vocal)`. Tres voces ligeramente desafinadas (±0,6 %) con vibratos distintos = un **coro**, no un hombre: los batidos entre ellas dan el «ancho» de un canto gregoriano. En el universo de la estética, el Culto de la Máquina **canta** en binario: el «101010» de la entrada (§4.5) se canta con esto.

### 3.6 Órgano aditivo

`organ(notas, segundos)`: cada nota es la suma de los armónicos 1, 2, 3, 4, 6 y 8 con amplitudes decrecientes (1; 0,55; 0,3; 0,25; 0,12; 0,08). Es lo que hacen los **registros** de un órgano de tubos (8′, 4′, 2⅔′, 2′, 1⅓′, 1′): cada registro es una fila de tubos que suena un armónico de la tecla. Las notas se desafinan un 0,08 % entre sí para que el acorde «respire».

### 3.7 Saturación

`drive(x, k) = tanh(k·x) / tanh(k)`: aplasta los picos con suavidad, como un amplificador de válvulas al límite. Dos efectos:

1. **Añade armónicos** a un tono grave: de un 110 Hz salen 330, 550 Hz… que el altavoz sí reproduce (§2, idea 6). Es la herramienta principal para que lo grave **se oiga** en el móvil.
2. Da **densidad** («jugoso», «gordo»): la diferencia entre un tono limpio de laboratorio y un clacson viejo.

### 3.8 Reverberación por convolución: la sala de piedra

Una sala se describe con su **respuesta al impulso** (IR): lo que se graba si en ella suena un chasquido instantáneo. Cualquier sonido en esa sala = **convolución** del sonido con la IR (cada muestra deja detrás el eco de la sala). `hall_ir` la fabrica sin grabar nada:

- **Reflexiones tempranas:** cuatro impulsos a 11, 23, 37 y 51 ms (las paredes cercanas).
- **Cola:** ruido que se apaga exponencialmente (τ de 0,1 a 0,35 s) y **filtrado paso bajo** (1,5–2,2 kHz): en una sala real los agudos se absorben antes, así que la cola es **oscura**.
- **Final en cero:** el último 25 % de la IR se funde a cero. Sin eso, la cola se cortaba de golpe al acabar la IR, y se veía (y oía) como un final brusco en el espectrograma de la campana.

`reverb(x, wet)` mezcla seco y mojado (`wet` de 0,2 a 0,4) con `scipy.signal.fftconvolve` (la convolución por FFT: miles de veces más rápida que la directa con IR de 44 000 muestras).

### 3.9 Mezcla y acabado: nivel por sonoridad

- **`place(total, (t0, x), (t1, y), …)`**: coloca cada parte en su instante y las **suma**.
- **`finish(x, sonoridad)`**, en este orden:
  1. **Paso alto a 140 Hz.** Lo que hay por debajo no lo reproduce el altavoz, pero **ocupa** amplitud: sin quitarlo, el pico lo marcaría un retumbar inaudible y todo lo audible quedaría más bajo.
  2. Resta la media (**componente continua**) y fundidos de 4 ms.
  3. **Ganancia para llegar a la sonoridad pedida**, medida con `speaker_loudness`: paso alto a 300 Hz (lo que suena por el altavoz) y el **RMS máximo en 100 ms**, en dBFS. No es un estándar (LUFS añadiría una curva de audición y umbrales), pero sirve para comparar nuestros sonidos entre sí.
  4. **Techo de pico** a −3 dBFS: si para llegar a la sonoridad el pico pasara de ahí, manda el pico (nunca satura). Con los niveles actuales ningún sonido llega (el pico más alto, −12 dBFS).

Cuatro **niveles**, porque lo que suena a menudo debe ser lo más bajo:

| Nivel | Sonoridad | Sonidos |
|-------|----------:|---------|
| `QUIET` | −36 dB | tecla, átomo, línea del arranque |
| `BACKGROUND` | −33 dB | barrido de carga (suena mientras se espera) |
| `EVENT` | −29 dB | entrada, rechazo, molécula lista, captura |
| `CEREMONY` | −27 dB | arranque (una vez por inicio) |

Cada nivel está **3–4 dB** por encima del anterior: una diferencia que se nota sin que nada «salte». Y encima de los ficheros queda un mando en el código, `MASTER_VOLUME` (§7.2), para bajarlos todos a la vez.

---

## 4. Las diez recetas

| Fichero | Momento | Duración | Nivel | Receta en una línea |
|---------|---------|---------:|-------|---------------------|
| `snd_boot_hum` | Aparece la pantalla de arranque | 2,4 s | CEREMONY | Golpe de relé (barra de hierro), máquina que arranca (sierra saturada 55 → 110 Hz) y **acorde de órgano en re menor** que crece en la sala |
| `snd_boot_line` | Cada línea del arranque | 0,11 s | QUIET | Tipo de máquina de escribir pesada: «thock» modal 340/910/2100 Hz, saturado |
| `snd_boot_done` | Aparece «> DONE» | 2,0 s | CEREMONY | **Campana de catedral** (*prime* re4, 293,7 Hz; *hum* una octava abajo), un golpe, en la sala |
| `snd_key` | Cualquier tecla o botón | 0,06 s | QUIET | Tecla mecánica pesada: «thock» modal 420/1130/2600 Hz, saturado |
| `snd_signed_in` | Sesión abierta | 1,6 s | EVENT | **101010 cantado** por un coro de máquina (1 = la2 «o», 0 = mi2 «u») y una quinta de órgano |
| `snd_denied` | Contraseña mala, bloqueo, carga fallida | 0,7 s | EVENT | Clacson en **tritono** (re3 + sol♯3), saturado y sordo, que baja un poco y late a 9 Hz |
| `snd_scan` | Cargando un ligando | 1,2 s | BACKGROUND | Sonar que late a 4 Hz (seno saturado 330 → 470 → 330 Hz) sobre la molienda de un servo |
| `snd_loaded` | La molécula está lista | 1,2 s | EVENT | Un mecanismo que **encaja** (dos golpes de barra de 260 Hz) y un *ping* grave (la4) en la sala |
| `snd_select` | Se toca un átomo | 0,15 s | QUIET | «Toc» de una barrita de metal de 330 Hz, sordo |
| `snd_shutter` | Compartir (captura) | 0,5 s | EVENT | Golpe de obturador, servo que frena (sierra 240 → 90 Hz), segundo golpe |

### 4.1 `snd_boot_hum`: el cogitador despierta

```python
spin  = drive(lowpass(saw(np.linspace(55, 110, n)), 1200) * 1.6, 3.0) * adsr(n, 0.25, 0.4, 0.5, 0.8) * 0.45
chord = organ((146.8, 220.0, 293.7, 349.2), d) / 4      # re3 la3 re4 fa4: re menor
chord = lowpass(chord, 1800) * adsr(n, 0.6, 0.2, 0.85, 0.9) * 0.8
clank = bar(185, 0.6, …) * 0.9                            # barra de hierro: 185, 510, 1000, 1653 Hz
dry   = place(1.9, (0, drive(clank, 1.5)), (0.05, spin), (0.15, chord))
reverb(dry, 0.35, …)
```

- **El golpe:** un relé enorme que cierra. Barra de 185 Hz con sus modos de Euler-Bernoulli.
- **La máquina:** una sierra que sube una octava (55 → 110 Hz), saturada fuerte: el altavoz no reproduce los 55 Hz, pero sí sus armónicos, y el oído oye «algo grande girando» (§2, idea 6).
- **El acorde:** re menor (re–la–re–fa) en **posición abierta**: la quinta abajo (re–la) da peso, la tercera menor arriba (re–fa) da la tristeza. Entra a los 0,15 s y crece durante 0,6 s.
- En la v1 era un CRT que se enciende (50 Hz de red); la v2 es una **catedral-máquina**.

### 4.2 `snd_boot_line`: el tipo que golpea

Tres modos (340, 910, 2100 Hz) con τ de 18, 10 y 5 ms y un martillo de 3 ms de ruido por debajo de 2,5 kHz, saturado. Suena con cada línea del arranque, cada **546 ms** (documento 08d): 110 ms de sonido y casi medio segundo de silencio, nunca se solapan.

### 4.3 `snd_boot_done`: la campana

`bell(293.7, 1.6, length=1.2)`: los siete parciales de `BELL` sobre re4. El *hum* (146,8 Hz) suena más tiempo que nadie (τ = 1,2 s); el *tierce* (352 Hz, la tercera menor) es lo que hace que sea **campana de catedral** y no campanilla. Paso bajo a 3 kHz y sala con `wet` 0,4. Un solo golpe: «hecho», con solemnidad.

### 4.4 `snd_key`: la tecla pesada

Modos 420 / 1130 / 2600 Hz con τ de 11, 7 y 3 ms: muere en ~40 ms. La v1 era un relé con dos chasquidos agudos (> 3 kHz): «clic». Ahora no hay nada por encima de ~3 kHz: **«thock»**, el sonido sordo de una tecla mecánica grande. Es el que más se repite: nivel QUIET.

### 4.5 `snd_signed_in`: la máquina canta «42»

```python
for i, bit in enumerate("101010"):                  # 42 = 32 + 8 + 2 = 0b101010
    f0, vowel = (110.0, 'o') if bit == "1" else (82.4, 'u')
    parts.append((i * 0.12, drive(chant(f0, 0.11, vowel) * 2.0, 1.5) * adsr(n, 0.015, 0.02, 0.8, 0.03)))
fifth = organ((110.0, 164.8, 220.0), 0.75) …          # la2 mi3 la3: quinta vacía
```

Seis sílabas de 110 ms cada 120 ms: **«o»-«u»-«o»-«u»-«o»-«u»**, la2 y mi2 (una cuarta). La fundamental (82–110 Hz) no sale por el altavoz, pero las **formantes** (330–750 Hz) sí: se oye la vocal y se adivina la nota. Después, una **quinta vacía** (sin tercera: ni mayor ni menor, arcaica, medieval) en el órgano. Es el mismo «101010» del icono (documento 08d).

### 4.6 `snd_denied`: el espíritu de la máquina se niega

Dos sierras a re3 (146,8 Hz) y sol♯3 (207,7 Hz): un **tritono**, el intervalo que la teoría medieval llamaba *diabolus in musica* por lo inestable. Paso bajo a 1,6 kHz, **saturación fuerte** (k = 3) y un latido de 9 Hz (el volumen oscila entre 0,4 y 1,0). La frecuencia baja un 8 % en 0,55 s: un «no» que se hunde. Sala corta.

### 4.7 `snd_scan`: el auspex busca

f(t) = 400 + 70·sen(2π·t/1,2 − π/2): de 330 Hz sube a 470 y vuelve en 1,2 s, saturado (k = 2). El volumen **late a 4 Hz** (de 0,55 a 1), como un radar que barre. Debajo, ruido de 350–1100 Hz que tiembla a 23 Hz: la **molienda** de un servo viejo. Todo bajo 1,6 kHz. Nivel BACKGROUND: acompaña la espera sin cansar. Se corta en cuanto la carga termina (§7.3).

### 4.8 `snd_loaded`: encajado y contacto

Dos golpes de barra de 260 Hz con 60 ms de separación (un cerrojo que **encaja**: «cla-clac»), y a los 120 ms un *ping* de 440 Hz con su octava al 20 %, τ = 0,22 s, en la sala. Cierra la frase del barrido: buscar → **fijar** → confirmar.

### 4.9 `snd_select`: tocar un átomo

`bar(330, 0.15, …)`: la misma barra que el arranque pero pequeña (330, 909, 1783, 2948 Hz) y muy amortiguada (τ de 50 a 6 ms), con paso bajo a 2,5 kHz. Un «toc» metálico, sordo y corto. Nivel QUIET.

### 4.10 `snd_shutter`: la pict-captura

Golpe modal (360 / 940 / 2300 Hz) del obturador, servo que **frena** (sierra 240 → 90 Hz filtrada a 1,4 kHz y saturada, 220 ms) y otro golpe a los 0,3 s, con una sala muy corta. El ciclo de una cámara mecánica pesada.

---

## 5. Verlos: forma de onda y espectrograma

`python3 tools/sound/make_sounds.py --plots hoja.png` dibuja los diez: forma de onda a la izquierda, espectrograma (**0–3 kHz**: ya no hay nada interesante por encima) a la derecha.

![Forma de onda y espectrograma de los diez sonidos](img_14_sonidos.webp)

Qué buscar en la imagen:

- **Casi nada por encima de 2–3 kHz:** así se **ve** «sordo».
- **`snd_boot_hum`:** rayas horizontales (los armónicos del órgano) que aparecen a los 0,15 s y duran; el golpe del principio.
- **`snd_boot_done`:** líneas horizontales (los parciales de la campana); las altas se apagan antes, el *hum* sigue hasta el final.
- **`snd_signed_in`:** **seis bloques** alternos (las formantes de «o» y «u» están a alturas distintas) y después las rayas del órgano.
- **`snd_scan`:** un **arco** (sube y baja) con sus armónicos encima (la saturación), troceado por el latido.
- **`snd_shutter`:** dos golpes verticales cortos y, entre ellos, armónicos que **bajan** (el servo frenando).
- **Ninguna raya vertical** al final de los sonidos: no hay clics.

---

## 6. Del número al fichero: WAV → Ogg Vorbis

1. **WAV PCM de 16 bits** (`write_wav`): cada muestra × 32 767, redondeada y recortada; mono, 44,1 kHz. Es el formato «sin pérdida» de partida.
2. **Ogg Vorbis, calidad 5** (`to_ogg`, ffmpeg con `libvorbis`): compresión con pérdida, de **tasa variable**. Calidad 5 es transparente para estos sonidos (no se distingue del WAV).
3. **Sin metadatos** (`-map_metadata -1`) y en **modo exacto** (`-fflags +bitexact -flags:a +bitexact`).

**¿Por qué Ogg Vorbis?** Android lo decodifica en **todas** las versiones (desde la 1), sin patentes, y `SoundPool` lo acepta. MP3 tiene un **retardo** al principio (el *encoder delay*, ~25 ms de silencio) que en un clic de tecla se nota. WAV sin comprimir pesaría ~1 MB en total. Opus sería más eficiente, pero su soporte en Android depende de la versión y del contenedor (Opus dentro de Ogg, solo en versiones recientes; **sin comprobar** el detalle), y el ahorro aquí es despreciable.

**El peso real.** Los diez ficheros: **124 KiB** (la v1, con sonidos más cortos, 69 KiB). Curiosidad: `snd_key` dura 60 ms y pesa 4,5 KiB, casi como uno de 0,3 s. Un Vorbis lleva en su cabecera los **libros de códigos** del codificador (≈ 3,5–4 KiB) en **cada** fichero: en sonidos tan cortos la cabecera pesa más que el sonido. Por eso la «tasa de bits» que da `ffprobe` para los cortos (600 kbit/s en `snd_key`) no significa nada.

**Reproducibilidad, comprobada.** Dos ejecuciones dan:

- **WAV idénticos** (10 de 10): semilla fija del ruido, nada depende de la hora.
- Sin `bitexact`, **OGG distintos en 24 bytes**: el contenedor Ogg pone a cada flujo un **número de serie aleatorio** (y su suma de control cambia con él). El audio decodificado era idéntico, pero el fichero no, y `git` vería «cambios» cada vez. Con `bitexact` el número de serie es fijo: **OGG idénticos** (10 de 10).
- Matiz: «idéntico» con la **misma versión** de ffmpeg/libvorbis (aquí ffmpeg 6.1.1, numpy 2.5.3, scipy 1.18.1). Otra versión del codificador puede dar otros bytes con el mismo sonido.

**Regenerar:**

```bash
python3 tools/sound/make_sounds.py                       # escribe app/src/main/res/raw/snd_*.ogg
python3 tools/sound/make_sounds.py /tmp/x --wav-dir /tmp/w --plots /tmp/hoja.png   # para escuchar y mirar
```

---

## 7. Cómo suenan en la app

### 7.1 Las piezas

| Pieza | Fichero | Qué hace | Probada |
|-------|---------|----------|---------|
| `SoundCue` | `sound/SoundCue.kt` | Los diez sonidos, cada uno con su **intervalo mínimo** de repetición | JVM |
| `SoundPlayer` (+ `Silent`) | `sound/SoundCue.kt` | La interfaz que conocen las pantallas: `play`, `stop` | — |
| `SoundGate` | `sound/SoundCue.kt` | **Si** un sonido puede sonar ahora (silencio del móvil, intervalo) | JVM, 8 pruebas |
| `CogitatorSounds` | `sound/CogitatorSounds.kt` | El reproductor real con `SoundPool` | Compila (*stubs*) |
| `LocalSounds` | `sound/CogitatorSounds.kt` | Cómo llega el reproductor a cualquier pantalla de Compose | Compila |
| `SoundOnChange` | `sound/SoundOnChange.kt` | «Suena una vez por cambio **real**», también tras girar la pantalla (§7.4) | Compila |
| `loadSoundTag` | `sound/SoundTags.kt` | Nombre corto del estado de carga para `SoundOnChange` | JVM, 5 pruebas |

### 7.2 `SoundPool`: el reproductor de sonidos cortos

Android tiene varios reproductores; para efectos de interfaz el correcto es **`SoundPool`**:

- **Decodifica una vez** cada fichero a PCM en memoria (`load`), y `play` arranca en pocos milisegundos. `MediaPlayer` decodifica **cada vez**: decenas de milisegundos de retraso, inaceptable para un clic que tiene que coincidir con el dedo.
- **Varios a la vez** (`setMaxStreams(4)`): un clic de tecla mientras suena el barrido de carga.
- **`AudioAttributes`:** `USAGE_ASSISTANCE_SONIFICATION` + `CONTENT_TYPE_SONIFICATION` = «sonidos de la interfaz». Android los trata como los demás sonidos del sistema (no como música: no se mezclan con el reproductor de música del usuario ni piden el «foco de audio»).
- **Una sola instancia para toda la app** (`CogitatorSounds.of(context)`, *singleton* con doble comprobación y `@Volatile`), creada con el **contexto de la aplicación**: vive lo que vive el proceso, no retiene ninguna actividad (no hay fuga) y **no se recrea al girar** la pantalla (recargar los diez ficheros en cada giro sería tirar trabajo). Decodificados ocupan unos **875 KB** (9,9 s de audio en total × 44 100 muestras × 2 bytes).
- **`MASTER_VOLUME`** (hoy `1f`): el volumen que se pasa a `play`, de 0 a 1, **lineal en amplitud** (0,5 = −6 dB, más o menos «la mitad de fuerte» al oído). Mueve **todos** los sonidos a la vez sin tocar el equilibrio entre ellos (que está en los ficheros) y se ajusta probando en el móvil sin regenerar nada. Solo puede bajar: 1 es el nivel de los ficheros.
- **Cada sonido → su fichero** con un `when` **sin `else`**: si mañana se añade un `SoundCue`, el código **no compila** hasta que tenga su `R.raw`. Y la prueba `everyCueHasItsSoundFile` comprueba que el fichero existe (§8).

### 7.3 Carga asíncrona: el primer sonido llega tarde

`SoundPool.load` **no espera**: decodifica en otro hilo y avisa con `OnLoadCompleteListener`. Pero el gruñido del arranque se pide en el **primer fotograma** de `BootScreen`, justo cuando los ficheros empiezan a cargarse: en un arranque en frío casi seguro que todavía no está listo.

- **Primera versión:** «si no está listo, se salta». Consecuencia: el sonido más importante (el arranque) **se perdería casi siempre** en la primera apertura. Detectado al repasar el código, antes de oírlo.
- **Solución:** un sonido pedido antes de tiempo queda **en espera** (`waiting`, con la hora en que se pidió). Cuando su fichero termina de cargar, **suena si no han pasado más de 400 ms** (`LATE_MILLIS`); si han pasado más, ya no tendría sentido (sonaría a destiempo) y se descarta. Nada espera nunca a un sonido.
- **`stop` también cancela la espera.** Caso real: un ligando de la **caché** (documento 13) se abre en milisegundos. Sin esto, el barrido de carga (en espera) podría empezar **después** del *ping* de «listo».

### 7.4 Cuándo suena cada uno: los ganchos

| Dónde | Código | Sonido |
|-------|--------|--------|
| `ConsoleButton`, `ConsoleKey`, `BackKey` (`SwiftyComponents.kt`) | `onClick = { sounds.play(KEY); onClick() }` | Cualquier botón o tecla |
| `BootScreen` | `LaunchedEffect(Unit)` / `(shown)` / `(complete)` | Gruñido al aparecer; tic por cada línea; campanas con «> DONE» |
| `SwiftyApp` | `SoundOnChange("unlocked" / "locked")` | «42» al entrar |
| `SwiftyApp` | `SoundOnChange(formError)` | «No» con cada error del formulario |
| `SwiftyApp` | `SoundOnChange(loadSoundTag(state))`: `Loading` / `Loaded` / `Failed` / `Idle` | Barrido / corta el barrido + *ping* / corta + «no» / corta |
| `MoleculeScreen` | al seleccionar un átomo (no al quitar la selección) | Blip |
| `MoleculeScreen` | al empezar a compartir | Obturador |

**`LaunchedEffect` con el estado como clave** es la forma de Compose de decir «haz esto **una vez cada vez que cambie** este valor». La recomposición (que puede ocurrir decenas de veces) no vuelve a sonar; un cambio real, sí. Hacer `sounds.play(...)` directamente en el cuerpo de una función `@Composable` sería un error: sonaría en **cada** recomposición.

**Pero `LaunchedEffect` solo no basta: el giro de pantalla.** La primera versión usaba `LaunchedEffect(authState.unlocked)` y `LaunchedEffect(state)`. Al girar el móvil, Android **destruye y recrea la actividad**: toda la interfaz se compone **desde cero** y cada `LaunchedEffect` arranca otra vez. El estado no ha cambiado (vive en el `ViewModel`), pero el «42» y el *ping* habrían **vuelto a sonar** con cada giro. Detectado al revisar los ganchos, antes de oírlo.

**`SoundOnChange(tag) { … }`** lo arregla: guarda la última etiqueta con **`rememberSaveable`**, que **sobrevive** a la recreación (va al `Bundle` del estado guardado de la actividad, igual que el texto del buscador). Tras el giro la etiqueta es la misma que la guardada y no suena nada.

> Simulación: se abre `ATP` → etiqueta `loading:ATP` (≠ guardada `""`) → **barrido**, se guarda. Llega la molécula → `loaded:ATP` → **ping**, se guarda. Se gira el móvil → todo se compone otra vez, `LaunchedEffect` arranca, pero `loaded:ATP` == guardada → **silencio**. Se vuelve a la lista → `idle` → se corta el barrido (si sonaba). Se abre `ATP` otra vez → `loading:ATP` ≠ `idle` → suena.

**La etiqueta tiene que ser corta:** el `Bundle` tiene un límite (del orden de 1 MB para **toda** la transacción, y Android aconseja mucho menos) y un `toString()` de `Loaded` deletrearía la molécula entera, átomo por átomo. Por eso `loadSoundTag` devuelve `loaded:ATP` y, para un fallo, `failed:ATP:Timeout` (el **tipo** de error, no su informe completo). El origen (red o caché) **no** entra: es la misma molécula y el mismo *ping*.

**El arranque no necesita esto:** si se gira durante el arranque, `BootScreen` vuelve a empezar (su reloj usa `remember`, no `rememberSaveable`), y con él sus sonidos: coherente.

**`CompositionLocal`:** `MainActivity` envuelve la app en `CompositionLocalProvider(LocalSounds provides CogitatorSounds.of(this))` y cualquier pantalla lo lee con `LocalSounds.current`, sin pasar el reproductor como parámetro por diez funciones. El valor por defecto es `SoundPlayer.Silent`: las vistas previas de Android Studio y las pruebas **no hacen ruido** ni necesitan Android. Es `static` porque el valor no cambia nunca (más barato que `compositionLocalOf`, que vigila cambios).

### 7.5 Las dos reglas de `SoundGate`

```kotlin
fun allow(cue: SoundCue, phoneSilent: Boolean): Boolean {
    if (phoneSilent) return false                                         // 1
    val t = now()
    val last = lastPlayed[cue.ordinal]
    if (last != NEVER && t - last < cue.minGapMillis) return false        // 2
    lastPlayed[cue.ordinal] = t
    return true
}
```

1. **Móvil en silencio o en vibración → nada.** `AudioManager.ringerMode != RINGER_MODE_NORMAL`, comprobado **en cada** `play` (el usuario lo cambia cuando quiere). Un efecto de sonido no es algo que se imponga.
2. **El mismo sonido no se repite antes de su intervalo.** Tecla y átomo: 60 ms; «no»: 300 ms; el resto, 0.

> Simulación (reloj en ms): tecla a las 10 000 → **suena** (se apunta 10 000). Otra a las 10 059 → 59 < 60, **no suena** y **no se apunta** (el intervalo sigue contando desde 10 000). Otra a las 10 060 → 60, **suena**. Un dedo que tamborilea no se convierte en una ametralladora; dos pulsaciones normales (≥ 60 ms entre ellas: nadie pulsa más rápido de ~15 veces por segundo) suenan las dos.

Con el «no» importa por otra razón: un fallo de carga hace sonar `DENIED` desde el estado de carga, y casi a la vez puede llegar un error de formulario; 300 ms evitan que se oigan **dos** «no» encima.

**Un silencio no cuenta como reproducción:** con el móvil en silencio no se apunta la hora; al quitar el silencio, el siguiente suena enseguida.

---

## 8. Pruebas y mutación

**`SoundGateTest`** (8, JVM, reloj falso):

| Prueba | Qué fija |
|--------|----------|
| `nothingSoundsWhenThePhoneIsSilent` | Regla 1, para los diez |
| `theFirstTimeAlwaysSounds` | Nada se bloquea sin motivo |
| `aKeyIsNotRepeatedWithinItsGap` | 59 ms no, 60 ms sí (el borde exacto) |
| `aRefusedPlayDoesNotRestartTheGap` | El intervalo cuenta desde el que **sonó** |
| `cuesDoNotBlockEachOther` | Cada sonido tiene su propio reloj |
| `aSilentMomentIsNotCountedAsAPlay` | El silencio no gasta el intervalo |
| `cuesThatMayRepeatFreelyHaveNoGap` | Las líneas del arranque suenan todas |
| `everyCueHasItsSoundFile` | Existe `res/raw/snd_<cue>.ogg` para cada uno, **y ni uno más** (un fichero huérfano engorda el APK) |

**Mutantes** (cada uno compilado y probado por separado):

| Mutante | Resultado |
|---------|-----------|
| Quitar la regla del silencio | Muerto (2 pruebas) |
| `<` → `<=` en el intervalo | Muerto (3) |
| Apuntar la hora también cuando se rechaza | Muerto (2) |
| Un solo reloj para todos los sonidos | Muerto (2) |
| Intervalo de la tecla a 0 | Muerto (1) |

**Toda la batería:** **365 pruebas verdes** (antes de los sonidos, 352).

**`SoundTagsTest`** (5): el mismo estado da la misma etiqueta (lo que ve un giro); cada paso de una carga, otra; otra molécula, otra; el origen no cuenta; la etiqueta es corta con 256 átomos y con un informe de `Corrupt`. **5 mutantes** (sin id en `loaded`, `toString()`, sin el tipo de error, con el origen, sin id en `loading`): **todos muertos**.

**Sin prueba automática:** `CogitatorSounds` (necesita `SoundPool` real), `SoundOnChange` (necesita Compose) y los ganchos. Se comprueban a oído (sección 9).

---

## 9. Cómo probarlo en el móvil

Volumen del sistema a la mitad, modo **sonido** (no vibración), **por el altavoz** (no con auriculares: §2, idea 6):

1. **Arranque en frío** (cerrar la app desde Recientes y abrir): golpe + máquina + órgano; un «thock» por línea; la campana con «DONE». *Lo crítico: que el arranque **se oiga** en la primera apertura (§7.3).*
2. **Pulsar teclas** del formulario: un «thock» por pulsación; tamborilear rápido no da una ráfaga.
3. **Contraseña mala:** «no». **Entrar:** el «42» (seis sílabas «o-u» ×3 y la quinta del órgano).
4. **Abrir un ligando nuevo:** sonar que late mientras carga, se **corta**, y «cla-clac» + *ping*.
5. **Abrir el mismo otra vez** (de la caché, instantáneo): **solo** «cla-clac» + *ping*, sin sonar tras él.
6. **Modo avión + ligando nuevo:** sonar cortado y el clacson.
7. **Tocar átomos:** «toc»; tocar el vacío (deseleccionar): nada.
8. **Compartir:** obturador.
9. **Girar el móvil** con una molécula abierta y con la sesión abierta: **ningún** sonido (ni *ping* ni «42»).
10. **¿Distraen?** Usar la app cinco minutos seguidos. Si cansan, bajar `MASTER_VOLUME` (0,7 = −3 dB; 0,5 = −6 dB) y reinstalar; si solo molesta uno, su nivel en `make_sounds.py`.
11. **Modo vibración o silencio:** **nada** de lo anterior suena.
12. **Volumen:** ¿qué botón de volumen lo cambia? En la mayoría de los móviles, los sonidos de interfaz siguen el volumen del **sistema** o el de **notificaciones**; depende del fabricante (**sin comprobar** en el A33).

---

## 10. Trampas

1. **`sin(2π·f(t)·t)` para un glissando:** clics y una frecuencia que no es la que se quería. Hay que **acumular la fase** (§3.1).
2. **Cortar un sonido que aún suena:** clic. Fundido al final de cada parte (`fade_out`) y del total (`finish`).
3. **Ondas cuadradas o de sierra «perfectas»:** *aliasing*. Redondear (tanh) o filtrar.
4. **Normalizar solo por pico:** sonoridades desiguales (la tecla de la v2 salía 9 dB más fuerte que la de la v1 con el mismo pico). **Solo por sonoridad, sin techo:** puede saturar. Las dos cosas: sonoridad como objetivo, pico como límite (§3.9).
5. **Componente continua:** una señal desplazada del cero da un golpe al empezar y desperdicia rango; se resta la media.
6. **`MediaPlayer` para clics:** retraso de decodificación en cada reproducción.
7. **`SoundPool` por pantalla o por actividad:** recargar en cada giro; con el contexto de la actividad, además, **fuga** de memoria.
8. **Pedir un sonido antes de que cargue:** se pierde en silencio, y justo el primero (§7.3).
9. **Sonar en el cuerpo de un `@Composable`:** suena en cada recomposición. `LaunchedEffect` con clave o en el `onClick`.
10. **`LaunchedEffect` solo, para algo que debe sonar una vez:** vuelve a sonar al girar la pantalla. Recordar lo ya sonado con `rememberSaveable` (`SoundOnChange`).
11. **Ignorar el modo silencio:** el móvil de un evaluador sonando en mitad de la sala.
12. **Ficheros no reproducibles:** sin `bitexact`, cada regeneración «cambia» los diez ficheros en `git` aunque suenen igual.
13. **Mayúsculas o guiones en `res/raw`:** Android solo admite `[a-z0-9_]` en nombres de recursos; `snd-Key.ogg` no compila. Por eso `snd_key`.
14. **Diseñar en auriculares un sonido para el altavoz:** lo grave desaparece en el móvil. Fundamentales por encima de ~150 Hz, saturación para los armónicos, y **escuchar en el altavoz** (§2, idea 6).
15. **Un subgrave que no se oye pero ocupa:** fija el pico y baja todo lo demás. Paso alto antes de normalizar (§3.9).

---

## 11. Alternativas

| Alternativa | A favor | Por qué no (aquí) |
|-------------|---------|-------------------|
| **Muestras de bancos libres** (Freesound, etc.) | Realismo inmediato | Licencias que comprobar una por una (CC-BY obliga a citar; algunas prohíben uso comercial); no se puede explicar «cómo se hizo» |
| **sfxr / jsfxr / ChipTone** (generadores de efectos de videojuego) | Muy rápido, también es síntesis | Suena a 8 bits; parámetros en una interfaz gráfica, no en código revisable y reproducible |
| **Sintetizar en el móvil** (`AudioTrack` con las mismas fórmulas en Kotlin) | Cero ficheros; se podría variar cada sonido | Más código en la app y CPU en cada reproducción para sonidos que nunca cambian; latencia de arranque de `AudioTrack` |
| **`ToneGenerator`** (tonos DTMF del sistema) | Cero ficheros, una línea | Solo pitidos telefónicos: nada de cogitador |
| **`View.playSoundEffect(SoundEffectConstants.CLICK)`** | El clic del sistema; respeta el ajuste «Sonidos al tocar» | Un clic genérico, distinto en cada fabricante |
| **Respetar «Sonidos al tocar»** (`Settings.System.SOUND_EFFECTS_ENABLED`) | Más coherente con el sistema | Según el fabricante puede venir **desactivado** (sin comprobar en el A33): la app sería muda sin que el usuario sepa por qué. Queda como idea si se añade un ajuste propio |
| **Interruptor de sonido en la app** | Control explícito | No hay pantalla de ajustes todavía; candidato si se hace la del bonus VII.2 |
| **Opus** en lugar de Vorbis | Mejor compresión | Ahorro despreciable (la cabecera domina) y compatibilidad menos clara en Android antiguo (minSdk 24) |
| **Vibración háptica** en vez de (o además de) sonido | Funciona en silencio | Otro trabajo; posible complemento para la tecla |
| **Suno «Sounds»** (beta de enero de 2026, tipo *One Shot*) | Realismo (coros, campanas grabadas «de verdad»); con suscripción Pro/Premier el resultado es del usuario, con uso comercial | Resultado poco controlable (dos variantes por petición); límite de descargas al mes en los planes de pago; en golpes de 50 ms la síntesis es mejor. **Plan B** para los sonidos largos si la v2 no convence |
| **ElevenLabs** (efectos por texto) | Muy buenos efectos | Sus condiciones **prohíben** distribuir el resultado como ficheros de audio sueltos: nuestros `.ogg` están en un repositorio público |
| **Sonniss GDC** (paquete gratuito anual, 7,5 GB) | Grabaciones profesionales, sin regalías | Sin escucha previa cómoda; no está claro si se pueden publicar los ficheros sueltos en un repositorio |
| **FM** (v1: campanas con modulación de frecuencia) | Mucho timbre con dos senos | Sin control parcial a parcial; la síntesis modal da campanas más creíbles con proporciones reales |

---

## 12. Decisión

| # | Decisión | Estado | Motivo |
|---|----------|--------|--------|
| D48 | **Diez sonidos sintetizados** con matemáticas (`tools/sound/make_sounds.py`, v2 «grimdark»: síntesis modal, formantes, órgano aditivo, saturación, reverberación por convolución; fundamentales que el altavoz del móvil puede tocar), **nivelados por sonoridad** en cuatro niveles, Ogg Vorbis q5 reproducible byte a byte (124 KiB); `MASTER_VOLUME` en el código; reproducidos con **un `SoundPool`** de la aplicación (sonificación, 4 flujos); **nunca en silencio o vibración**; intervalo mínimo por sonido; un sonido pedido antes de cargar suena si llega en ≤ 400 ms; lo que suena una vez por cambio de estado se recuerda con `rememberSaveable` (no se repite al girar) | Decidida | Ambientación del cogitador sin licencias ajenas; explicable en la defensa; discreto; sin retrasos |

---

## 13. Qué recordar

- **Sonido digital = lista de números.** 44 100 por segundo, entre −1 y 1; frecuencias hasta la mitad (Nyquist).
- **Todo timbre se construye** con osciladores, ruido, filtros y una envolvente. **Metal golpeado** = suma de senos amortiguados en las proporciones de sus modos; **voz** = zumbido filtrado por formantes; **sala** = convolución con su respuesta al impulso.
- **La fase se acumula** cuando la frecuencia cambia; si no, clics.
- **Nada empieza ni acaba de golpe:** fundidos de milisegundos.
- **«Oscuro» es timbre, no grave:** el altavoz del móvil no toca por debajo de ~300 Hz; la saturación da los armónicos que sí toca.
- **Igualar sonoridad, limitar el pico**, en niveles por importancia: lo frecuente, más bajo. Sonidos que acompañan, no que distraen.
- **`SoundPool`, uno, con el contexto de la aplicación**, para sonidos cortos; la carga es asíncrona, así que el primer sonido hay que **esperarlo un poco** o se pierde.
- **Respetar el silencio del móvil**, siempre.
- **Un giro de pantalla recompone todo y relanza cada `LaunchedEffect`:** lo que debe ocurrir una vez por cambio se recuerda con `rememberSaveable`, y con una clave **corta**.
- **Reproducible** = semilla fija + codificador en modo exacto.
