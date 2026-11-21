# 01 · La tarea y por dónde empezar

Este documento responde a tres preguntas antes de escribir una sola línea de código:
**qué nos piden**, **por qué** y **cómo vamos a empezar**. Si algo del enunciado cambia o
descubrimos una interpretación mejor, este es el primer documento que se actualiza.

## Índice

1. [Resumen en una frase](#1-resumen-en-una-frase)
2. [Contexto: por qué existe el proyecto](#2-contexto-por-qué-existe-el-proyecto)
3. [El problema y el flujo de la aplicación](#3-el-problema-y-el-flujo-de-la-aplicación)
4. [Cómo se evalúa](#4-cómo-se-evalúa)
5. [Requisitos obligatorios](#5-requisitos-obligatorios)
6. [Requisitos transversales](#6-requisitos-transversales)
7. [Bonus](#7-bonus)
8. [No-objetivos](#8-no-objetivos)
9. [Ambigüedades del enunciado y lectura adoptada](#9-ambigüedades-del-enunciado-y-lectura-adoptada)
10. [Criterios de éxito](#10-criterios-de-éxito)
11. [Riesgos principales](#11-riesgos-principales)
12. [Decisiones y estado](#12-decisiones-y-estado)
13. [Primer paso: el spike](#13-primer-paso-el-spike)
14. [Glosario](#14-glosario)
15. [Revisión contra el enunciado completo (21-11-2026)](#15-revisión-contra-el-enunciado-completo-21-11-2026)

---

## 1. Resumen en una frase

Una aplicación Android que, tras un inicio de sesión seguro, muestra una lista de
ligandos, descarga el que elijas desde la base de datos RCSB, interpreta su fichero `.cif`
y lo dibuja en 3D (átomos como esferas, enlaces como cilindros) para que puedas girarlo,
acercarlo, tocar un átomo y compartir una captura.

---

## 2. Contexto: por qué existe el proyecto

El enunciado cuenta una historia ficticia (un laboratorio farmacéutico necesita un visor
de proteínas). La historia es un envoltorio. Lo que se pretende es que **un solo proyecto
obligue a combinar cinco áreas** que aparecen juntas en el trabajo real de un desarrollador
móvil:

| Área | Qué se practica | Dónde aparece |
|------|-----------------|---------------|
| **Formato de datos** | Escribir un parser para un formato textual con excepciones | Interpretar `.cif` (átomos y enlaces) |
| **Red y asincronía** | Descargas, tiempos de espera, errores, la interfaz nunca se bloquea | Descargar desde RCSB |
| **Gráficos 3D** | Escena, cámara, luz, materiales, gestos, selección de objetos | Vista de la molécula |
| **Seguridad** | Hash de contraseñas, almacén de claves, biometría, ciclo de vida | Pantalla de login |
| **Dominio científico** | Colores CPK, radios atómicos, modelo *ball-and-stick* | Representación de ligandos |

Un **ligando** es una molécula pequeña que se une a una proteína y suele ser la base del
diseño de fármacos. No vamos a dibujar proteínas enteras (cientos de miles de átomos), sino
ligandos, que son mucho más pequeños. Esto importa para el rendimiento: el problema no es
la cantidad de geometría sino la corrección y la estabilidad.

---

## 3. El problema y el flujo de la aplicación

```
Inicio de la app
   │
   ▼
Login (SIEMPRE visible al abrir o al volver de segundo plano)
   │   usuario + contraseña  ──o──  huella / cara (si el dispositivo lo soporta)
   ▼
Lista de ligandos  (leída de ligands.txt, con búsqueda en tiempo real)
   │   el usuario toca un ligando
   ▼
Indicador de carga ──► descarga  https://files.rcsb.org/ligands/view/{id}.cif
   │                       │
   │                       ├─ sin conexión / 404 / tiempo agotado ──► aviso con mensaje claro
   │                       ▼
   │                  interpretación del .cif ── fichero corrupto ──► aviso con mensaje claro
   ▼
Vista 3D  (ball-and-stick, colores CPK, giro, zoom, toque en átomo, compartir captura)
```

Todo el flujo debe ser **robusto**: cualquier camino de error debe terminar en un mensaje
comprensible, nunca en un cierre inesperado.

---

## 4. Cómo se evalúa

Según el enunciado:

- La evaluación la hacen **personas** (compañeros), no un corrector automático.
- Se juzgan funcionalidad, calidad del código, gestión de errores, experiencia de usuario,
  seguridad, rendimiento y exactitud del 3D.
- **Una aplicación que se cierra, se congela o muestra errores durante la evaluación
  suspende, sin importar cuántas funciones tenga.**

Consecuencia práctica: un conjunto pequeño de funciones perfectamente estables vale más
que muchas funciones con un fallo escondido. Por eso los bonus van **al final** y solo si la
parte obligatoria es sólida.

---

## 5. Requisitos obligatorios

Marcamos cada punto con la sección del enunciado (VI.x) para poder verificarlo en la defensa.

### VI.1 Icono y pantalla de inicio

- [ ] Icono acorde con el tema (molecular, científico o médico), con todas las resoluciones necesarias.
- [ ] Pantalla de inicio visible al menos 1-2 segundos, con aspecto de marca y no de «carga eterna».

### VI.2 Pantalla de login

- [ ] Crear cuenta con **nombre de usuario único** y **contraseña** que cumpla requisitos mínimos.
- [ ] Iniciar sesión con **biometría** (en Android, `BiometricPrompt`).
- [ ] Si la biometría falla: **diálogo claro** que explique el motivo (por ejemplo, «Demasiados intentos»).
- [ ] Si el dispositivo no soporta biometría: login con usuario y contraseña, y la opción biométrica oculta o desactivada.
- [ ] **La pantalla de login se muestra siempre**: primer arranque, regreso desde segundo plano, tras pulsar Inicio, incluso si el usuario ya estaba autenticado.
- [ ] Las contraseñas **nunca en texto plano** (hash con algoritmo adecuado o almacenamiento seguro de la plataforma).
- [ ] Interfaz clara: etiquetas, tipo de teclado correcto, mensajes de error útiles.

### VI.3 Lista de ligandos

- [ ] Mostrar **todos** los ligandos de `ligands.txt` (uno por línea) en una lista desplazable y fluida.
- [ ] Barra de búsqueda que filtra **mientras se escribe**, **sin distinguir mayúsculas**, sobre el identificador.
- [ ] Al tocar un ligando: indicador de carga, descarga del `.cif`, interpretación (coordenadas y enlaces) y navegación a la vista 3D.
- [ ] Si falla la carga: aviso con mensaje claro. El enunciado cita cuatro casos: sin conexión, no encontrado (404), fichero corrupto y tiempo de espera agotado.
- [ ] El indicador de carga **desaparece siempre**, tanto si hay éxito como si hay error.

### VI.4 Vista de proteína (3D)

- [ ] Colores **CPK**: C gris o negro, H blanco, O rojo, N azul, S amarillo, P naranja, el resto según el esquema estándar.
- [ ] Modelo **ball-and-stick**: átomos como esferas, enlaces como cilindros **más finos** que las esferas.
- [ ] **Toque en un átomo**: ventana o etiqueta con, como mínimo, el símbolo del elemento. Desaparece al tocar en cualquier otro sitio o en otro átomo.
- [ ] Gestos: **rotación** alrededor del centro (obligatoria), **zoom con pellizco** (obligatorio), desplazamiento con dos dedos (opcional pero recomendado). Objetivo de 60 FPS.
- [ ] Botón **Compartir**: captura de la vista 3D y menú nativo de compartir.
- [ ] Cámara inicial que muestre **toda** la molécula, e iluminación que dé sensación de volumen (se recomiendan varias luces).
- [ ] Prohibido usar motores de juego completos (Unity, Unreal).

---

## 6. Requisitos transversales

Aparecen en las «instrucciones generales» y también se revisan:

- **Versiones recientes** del SDK, lenguaje e IDE.
- **Formato `.cif`** de RCSB. El formato `.pdb` antiguo se menciona expresamente como trampa. El enunciado admite otros formatos abiertos como `.sdf` si representan bien la estructura.
- **Diseño adaptable**: distintos tamaños de pantalla (móvil y tableta), ambas orientaciones, distintas densidades. El enunciado nombra `ConstraintLayout` para Android.
- **Gestión de errores completa**: red, parsing, memoria con moléculas grandes, entrada de usuario inválida.
- **Toda la red en segundo plano**; la interfaz no se bloquea nunca.
- **Retroalimentación al usuario** en cada operación: cargas, errores comprensibles, confirmaciones cuando proceda.
- **Sin fugas de memoria.**
- **Pruebas en dispositivo real**, no solo en emulador.
- **Código ordenado** y siguiendo las guías de la plataforma (Material Design y convenciones de Kotlin).
- **Seguridad**: nada sensible en texto plano, almacenamiento seguro (Keystore), validar los datos de red, tratar con seguridad los fallos de biometría.
- **Accesibilidad** «donde sea posible» (contraste suficiente, lector de pantalla).
- **Caché sin conexión** para ligandos ya cargados: figura como *opcional* en las instrucciones generales y como bonus en VII.4.

---

## 7. Bonus

Cinco bloques (VII.1 a VII.5). El enunciado no explica cómo puntúan, así que asumimos
el peor caso: **no cuentan hasta que lo obligatorio sea impecable**.

| Bloque | Contenido |
|--------|-----------|
| VII.1 Modelos de visualización | Espacio lleno (radios de van der Waals), alambre, *stick*, con cambio en tiempo real sin recargar |
| VII.2 Interfaz avanzada | Celdas personalizadas, animaciones, modo oscuro (también en el 3D), *onboarding*, pantalla de ajustes |
| VII.3 Interacción molecular | Resaltar átomos del mismo elemento, información de enlaces (tipo y longitud), medir distancias y ángulos, etiquetas, centrar con doble toque |
| VII.4 Rendimiento y caché | Caché local de `.cif`, carga perezosa, parsing en segundo plano con progreso, gestión de memoria, 60 FPS garantizados |
| VII.5 Compartir y exportar | Mensaje personalizado (nombre, nº de átomos, fórmula), varios formatos, vídeo, favoritos, comparación de dos moléculas |

Orden de prioridad propuesto (mejor relación esfuerzo/impacto): caché local → modos de
visualización → etiquetas, resaltado y centrado → favoritos, celdas personalizadas y modo
oscuro → vídeo y comparación.

---

## 8. No-objetivos

Para no dispersarnos, **no** vamos a hacer lo siguiente:

- Una versión iOS ni una solución multiplataforma.
- Visualizar proteínas completas ni estructuras de cientos de miles de átomos.
- Usar el formato `.pdb` antiguo.
- Usar un motor de juego completo.
- Montar un servidor propio para las cuentas.
- Cualquier bonus antes de tener la parte obligatoria estable.
- Preparar la aplicación para producción (publicación en tienda, analítica, etc.).

---

## 9. Ambigüedades del enunciado y lectura adoptada

El enunciado tiene tensiones internas. Fijamos nuestra lectura **ahora** para poder
justificarla en la defensa.

| # | Ambigüedad | Lectura adoptada |
|---|------------|------------------|
| 1 | La introducción pide un framework 3D de «alto nivel», pero VI.4 permite también OpenGL ES en Android. | Usar una librería reconocida (Filament, vía SceneView) evita la discusión. Si hay que caer en OpenGL ES, queda justificado por VI.4. |
| 2 | El login debe aparecer siempre al volver de segundo plano, pero el menú de compartir y el diálogo biométrico también sacan la app del primer plano. | ~~Distinguir entre «el usuario abandonó la app» y «la app lanzó un diálogo del sistema».~~ **Revisado (documento 05, D43):** se bloquea en **cada** salida a segundo plano, también al volver de compartir; el diálogo de la huella no saca la app de primer plano. |
| 3 | Términos de Apple (Auto Layout, ARC, VoiceOver, Face ID) en un enunciado válido también para Android. | Traducir: `ConstraintLayout` o equivalente, ciclo de vida de Android, TalkBack, `BiometricPrompt`. |
| 4 | «Sistema de cuentas»: se admiten Firebase, almacenamiento seguro de plataforma o backend propio. | Almacenamiento local con hash de contraseña. Es legítimo y enseña más. |
| 5 | «Probar en dispositivo real» frente a un único dispositivo disponible. | Un Samsung Galaxy A33 para lo principal. Tableta y horizontal, en emulador en casa (en el campus los equipos no lo soportan bien). |

---

## 10. Criterios de éxito

El proyecto está «terminado» cuando, **sin ayuda y en el dispositivo real**, todo esto se cumple:

1. La app arranca con icono y pantalla de inicio correctos.
2. Se puede registrar un usuario, y se rechazan nombres repetidos y contraseñas débiles.
3. El login por huella funciona; si se falla a propósito, aparece el diálogo con su motivo.
4. Al pasar la app a segundo plano y volver, se vuelve a pedir el login.
5. La lista muestra todos los ligandos y la búsqueda filtra al escribir, sin mayúsculas.
6. **Cada** ligando de `ligands.txt` se descarga, se interpreta y se dibuja sin cierres.
7. Los cuatro errores de carga (sin red, 404, corrupto, tiempo agotado) muestran su aviso y el indicador desaparece.
8. En 3D: colores CPK correctos, cilindros más finos que esferas, giro, zoom, toque en átomo y compartir funcionan con fluidez.
9. Girar el móvil y volver desde segundo plano varias veces no provoca cierres ni degradación visible.
10. No hay contraseñas en texto plano en ningún sitio (código, preferencias, logs).

---

## 11. Riesgos principales

| Riesgo | Impacto | Mitigación |
|--------|---------|------------|
| Un cierre inesperado durante la evaluación | Suspenso directo | Probar todos los ligandos, todos los errores y todos los cambios de ciclo de vida antes de la defensa |
| Casos raros del `.cif` (comillas en identificadores, campos multilínea, átomos sueltos sin enlaces) | Parser roto en algunos ligandos | Descargar todos los ficheros de `ligands.txt` y usarlos como pruebas (documento 03) |
| La librería 3D no cumple lo esperado o su API cambia | Bloqueo a mitad del proyecto | Spike previo y plan B con `GLSurfaceView` (documento 02) |
| El login al volver de segundo plano interfiere con el menú de compartir | Se pierde la escena o se expulsa al usuario | Diseñar el mecanismo desde el principio (documento 05) |
| Fugas de memoria al cambiar de ligando | Degradación o cierre tras varios usos | Liberar los recursos 3D al salir de la vista y revisar con el Profiler |
| Entorno de desarrollo lento o con poco espacio en el campus | Pérdida de tiempo | Mover SDK y cachés a `~/goinfre`, compilar en casa cuando sea posible |

---

## 12. Decisiones y estado

Resumen de las decisiones (el detalle y su motivo están en el [README](../README.md)):

- **Cerradas:** Android nativo con Kotlin; formato `.cif`; documentación en español de España.
- **Provisionales (a confirmar en el spike):** Jetpack Compose, SceneView sobre Filament, cuentas locales con hash.

**Preguntas abiertas**

1. ¿SceneView funciona bien en Compose con nuestra versión de Android Studio y en el A33? *(spike)*
2. ¿Cómo se estructura exactamente el `.cif` de los ligandos reales? *(documento 03, con los ficheros descargados)*
3. ¿Idioma de la interfaz de la app: español, inglés o ambos?
4. ¿Cómo repartimos el trabajo entre los dos miembros del equipo para no pisarnos en el repositorio?

---

## 13. Primer paso: el spike

Antes de construir nada, hacemos un experimento corto que responde a la
pregunta más arriesgada: **¿podemos dibujar y manipular una molécula con SceneView en un
dispositivo real, de forma estable?** El plan completo, los criterios de paso y la teoría
de 3D necesaria están en [`02-spike-3d.md`](02-spike-3d.md).

---

## 14. Glosario

- **Ligando:** molécula pequeña que se une a una proteína.
- **RCSB / PDB:** base de datos mundial de estructuras 3D de moléculas biológicas.
- **CIF:** formato textual con datos de átomos (coordenadas) y enlaces. Es el estándar actual de RCSB.
- **CPK:** esquema de colores por elemento químico (Corey-Pauling-Koltun).
- **Ball-and-stick:** representación con esferas (átomos) y cilindros (enlaces).
- **Espacio lleno:** representación con esferas solapadas del tamaño de van der Waals, que muestra el volumen de la molécula.
- **Spike:** experimento corto con tiempo limitado para reducir una incertidumbre. Su código es provisional: se reutiliza solo lo que quedó comprobado y se anota qué es temporal (documento 02, sección 10).
- **ADR (*Architecture Decision Record*):** nota breve que registra una decisión, sus alternativas y su motivo.
- **Estado de interfaz:** en Compose, los datos de los que depende lo que se ve en pantalla.

---

## 15. Revisión contra el enunciado completo (21-11-2026)

Este documento se escribió al principio, resumiendo el enunciado. Al acercarnos al final se releyó **el enunciado completo** (objetivos, instrucciones generales, VI, VII, criterios de evaluación y VIII.2 «errores comunes») y se comparó punto por punto con el código. Leyenda: ✅ hecho · 🟡 hecho pero sin comprobar en el móvil, o con un matiz · 🔴 falta.

### 15.1 Detalles del enunciado que el resumen no recogía

| Detalle | Dónde | Consecuencia |
|---------|-------|--------------|
| El motor permitido en Android: «ViroCore, **filament** u OpenGL ES» | VI.4 | Filament (vía SceneView) está **nombrado** en el enunciado: argumento fuerte en la defensa |
| Hash de contraseñas: «**bcrypt, Argon2** o almacenamiento seguro de la plataforma»; «algoritmos de hash **débiles**» es un error común (VIII.2) | VI.2, VIII.2 | El algoritmo se elige en el documento 05; uno de los nombrados evita discutir |
| «Usar el almacenamiento seguro de la plataforma (**KeyStore**)» | Instrucciones generales, seguridad | Además del hash, algo debe apoyarse en el Android Keystore (documento 05) |
| Los **textos** de los cuatro errores de carga vienen dados («No internet connection. Please check your network.», etc.) | VI.3 | Nuestros textos dicen lo mismo con otras palabras; conviene acercarlos para no discutir |
| La ficha del átomo desaparece al tocar «**en cualquier otro sitio de la pantalla**» u otro átomo | VI.4 | Hoy desaparece al tocar el vacío u otro átomo; **tocar un panel** (la lectura de abajo) no la quita |
| «**Confirmaciones de éxito** cuando proceda» | Instrucciones generales | Cuenta creada, imagen lista para compartir (esta ya existe) |
| La app «debe funcionar sin conexión para los ligandos ya cargados (**caché opcional**)» | Instrucciones generales y VII.4 | Redacción ambigua («debe» + «opcional»): hacer la caché elimina la discusión |
| `ConstraintLayout` como técnica de maquetación | Instrucciones generales | Usamos Compose (sus `Row`/`Column`/`Box` y `weight` son la técnica moderna equivalente); argumento para la defensa |
| Iluminación: «considerar **varias** fuentes de luz» | VI.4 | Hoy, la luz por defecto de SceneView, sin comprobar qué incluye |
| 60 FPS como objetivo; «< 30 FPS» como error común | VI.4, VIII.2 | **No medido**: hace falta una forma de enseñarlo |

### 15.2 Estado punto por punto

| Requisito | Estado | Nota |
|-----------|--------|------|
| VI.1 Icono en todas las resoluciones | ✅ | Documento 08d |
| VI.1 *Launch screen* ≥ 1–2 s, de marca, que no parezca «carga eterna» | ✅ | `BootScreen`, 4,2 s, salto con toque tras 1 s; la *splash* del sistema invisible (08d, 12.4) |
| VI.2 Cuentas, contraseña con requisitos, biometría, diálogo de fallo, alternativa sin biometría, **login siempre**, hash, teclados y etiquetas | ✅ | Documentos 05 y 05a; comprobado en el A33 |
| VI.3 Todos los ligandos, lista desplazable (`LazyColumn` ≡ `RecyclerView`), búsqueda en tiempo real sin mayúsculas | ✅ | Documento 06b/06c |
| VI.3 Carga: indicador, descarga, *parsing*, navegación; el indicador desaparece siempre | ✅ | Probado en el móvil (06c) |
| VI.3 Avisos de error claros | ✅ / 🟡 | Hechos; acercar los textos a los del enunciado |
| VI.4 CPK, bolas y varillas, radios proporcionales, varillas más finas | ✅ | Documento 07 |
| VI.4 Ficha del átomo y cuándo desaparece | ✅ / 🟡 | Falta: tocar un panel también debe quitarla |
| VI.4 Giro y zoom | ✅ | Comprobado en el móvil |
| VI.4 Desplazamiento con dos dedos (recomendado) | 🟡 | Sin comprobar (07, sección 12) |
| VI.4 Compartir captura | 🟡 | Escrito (09), sin probar en el móvil |
| VI.4 Cámara inicial con toda la molécula | ✅ | `SceneFraming` |
| VI.4 Iluminación con volumen, varias luces | 🟡 | Tres luces + ambiente, en nuestro código (documento 12); a ajustar a ojo en el móvil |
| VI.4 Fluido, 60 FPS | 🟡 | Contador de FPS en pantalla, en todas las compilaciones (documento 12); falta leerlo en el móvil |
| Errores: red, *parsing*, memoria (moléculas grandes), entrada inválida | ✅ | `LoadError`: 10 casos, incluido `TooLarge` e `InvalidId` |
| Red en segundo plano, UI siempre fluida | ✅ | Corrutinas en `Dispatchers.IO` |
| Sin fugas de memoria | 🟡 | Sin herramienta que lo demuestre (LeakCanary en depuración lo haría) |
| Pantallas distintas, horizontal, tableta, densidades | 🟡 | La orientación no está bloqueada; sin comprobar horizontal ni tableta |
| Seguridad: nada en texto plano, Keystore, validar datos de red, fallos de biometría | ✅ | Documentos 05/05a; comprobado en el A33 |
| Sin conexión para ligandos ya cargados | 🔴 | Caché de `.cif` |
| Accesibilidad | 🟡 | Contraste medido (08a/08c); falta una pasada con TalkBack |
| Versiones más recientes | ✅ | Revisar justo antes de la defensa |

### 15.3 Bonus: lo que ya cuenta y lo barato

| Bloque | Ya hecho | Barato de añadir |
|--------|----------|------------------|
| VII.1 Modelos | — | Espacio lleno y *stick*: solo cambian los radios; cambio sin recargar |
| VII.2 Interfaz | Celdas propias (líneas de consola), animaciones (cursor, vaivén, arranque), tema oscuro en toda la app y el 3D | Ajustes (modelo por defecto) si se hace VII.1 |
| VII.3 Interacción | — | Resaltar átomos del mismo elemento; centrar con doble toque (resuelve además ver de cerca los extremos, 07) |
| VII.4 Rendimiento y caché | Lista perezosa (`LazyColumn`), *parsing* fuera del hilo principal | Caché local de `.cif` (además cubre la instrucción general) |
| VII.5 Compartir | Mensaje con nombre, átomos y enlaces | **Fórmula** molecular en el mensaje: completa el punto; favoritos |

### 15.4 Orden de trabajo resultante

1. **Login** (documento 05: teoría y decisiones; código; pruebas).
2. **Luces explícitas** y **contador de FPS** en depuración; **textos de error** como los del enunciado; **tocar un panel quita la ficha**.
3. **Caché de `.cif`** (sin conexión).
4. **Ronda de comprobación en el móvil**: desplazamiento, horizontal, tableta (emulador), TalkBack, compartir, LeakCanary, compilación *release* (también la *splash* invisible).
5. **Bonus** baratos: fórmula, resaltado y doble toque, modelos de visualización, favoritos.
6. **Añadidos del equipo** (21-11-2026): contador de FPS **también en *release*** (proyecto de aprendizaje: se enseña siempre); **sonidos** del cogitador (no se piden; calidad); **la selección de un átomo deja de ser amarilla** (el amarillo es el azufre en CPK).
7. **Documento de pruebas completo** (para enseñar a los evaluadores): todas las pruebas automáticas por área, los casos límite y situaciones raras que cubren, la mutación, y la lista de pruebas manuales en el móvil con su resultado.
8. Documento de **defensa** y **presentación**.

