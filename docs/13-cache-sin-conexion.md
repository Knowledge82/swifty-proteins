# 13 · Caché de ligandos: la app funciona sin conexión

> **Estado:** **escrito** y **probado en la JVM**: 7 pruebas de la caché en disco (carpeta temporal real), 11 de las reglas del repositorio (red falsa), 1 del cargador; 8 mutantes, todos muertos (uno sobrevivió primero y se mató con una prueba nueva). **Sin probar en el móvil** (sección 8).

## Índice

1. [Qué pide el enunciado](#1-qué-pide-el-enunciado)
2. [Teoría: estrategias de caché](#2-teoría-estrategias-de-caché)
3. [Las reglas elegidas](#3-las-reglas-elegidas)
4. [Dónde y cómo se guarda](#4-dónde-y-cómo-se-guarda)
5. [El código](#5-el-código)
6. [En pantalla: de dónde vino la molécula](#6-en-pantalla-de-dónde-vino-la-molécula)
7. [Pruebas y mutación](#7-pruebas-y-mutación)
8. [Cómo probarlo en el móvil (la demostración)](#8-cómo-probarlo-en-el-móvil-la-demostración)
9. [Trampas](#9-trampas)
10. [Alternativas](#10-alternativas)
11. [Decisión](#11-decisión)
12. [Qué recordar](#12-qué-recordar)

---

## 1. Qué pide el enunciado

- **Instrucciones generales:** «la aplicación debe funcionar **sin conexión** para los ligandos ya cargados (**caché opcional**)». Redacción ambigua («debe» + «opcional»): haciéndolo, la ambigüedad deja de importar.
- **Bonus VII.4:** «guardar en local los `.cif` descargados para que los ligandos ya vistos se abran sin conexión».
- **Seguridad:** «validar todos los datos recibidos de la red»: lo que se guarda tiene que haber pasado la validación.

---

## 2. Teoría: estrategias de caché

| Estrategia | Cómo | A favor | En contra |
|------------|------|---------|-----------|
| **Caché primero** | Si hay copia, se usa; la red solo si no hay | Instantáneo; sin red funciona | La copia puede quedarse **vieja** para siempre |
| **Red primero** | Siempre a la red; la copia solo si la red falla | Siempre lo último | Con mala red, el usuario **espera el tiempo de espera entero** (decenas de segundos) antes de ver la copia |
| **Caché con caducidad (TTL)** | Copia «fresca» durante un tiempo; después, a la red | Lo mejor de ambos si los datos cambian poco | Hay que elegir el tiempo |
| **«Vieja antes que nada»** (*stale-if-error*, como la cabecera HTTP) | Si la red falla, se acepta una copia caducada | Sin red nunca se queda en blanco | Puede enseñar algo antiguo (se avisa) |

Los ligandos del diccionario de componentes químicos de RCSB **casi nunca cambian**. Encaja **caducidad larga + vieja antes que nada**.

---

## 3. Las reglas elegidas

1. **Copia de menos de 30 días → se usa al momento, sin red.** Instantáneo y sin conexión.
2. **Si no, se descarga.** Solo se guarda un fichero que **se ha interpretado bien** (el *parser* es la validación): una página de inicio de sesión de un wifi de hotel que llega como «200 OK» **nunca** se guarda.
3. **Si la descarga falla por la RED** (sin conexión, tiempo agotado, error del servidor, descarga cortada, otro fallo de red) **y hay copia vieja → se usa la vieja**, avisando de que es copia sin conexión.
4. **Un 404 no es un fallo de la red: es la RESPUESTA del servidor** («no existe»). Se borra la copia y se dice «not found».
5. **Una copia que ya no se interpreta** (dañada en el disco) se **borra** y se trata como si no existiera.
6. **La caché nunca hace fallar una carga:** si no se puede leer o escribir (disco lleno, carpeta borrada), la app sigue como si no hubiera caché.

---

## 4. Dónde y cómo se guarda

- **Carpeta:** `cacheDir/ligands/`, un `<ID>.cif` por ligando. `cacheDir` es **privada** de la app, **no** va a las copias de seguridad, y **Android puede vaciarla** si falta espacio: correcto para una copia que siempre se puede volver a descargar (si se vacía, se pierde el modo sin conexión de esos ligandos, nada más).
- **El nombre del fichero es el id**, que el repositorio ya validó (solo `A–Z` y `0–9`, como mucho 5): imposible un `../` que saliera de la carpeta.
- **Escritura atómica:** fichero temporal + renombrar (el mismo patrón que la imagen de compartir y las cuentas).
- **La fecha** es la de modificación del fichero (`lastModified`), puesta al guardar.
- **Límite de 20 MB:** tras cada escritura, si la carpeta pasa del límite se borran los **más antiguos** primero. Un `.cif` de ligando pesa de unos pocos kB a unos cientos: caben cientos.
- **Se guarda el TEXTO**, no la molécula ya interpretada: el formato del texto es el de RCSB y no cambia con nuestras versiones; interpretarlo de nuevo cuesta milisegundos.

---

## 5. El código

| Pieza | Fichero | Qué hace |
|-------|---------|----------|
| `LigandCache` (interfaz), `NoLigandCache`, `CachedLigand` | `net/LigandCache.kt` | El contrato: `read`, `write`, `remove`; «nunca lanza» |
| `FileLigandCache` | `net/LigandCache.kt` | Ficheros en una carpeta, escritura atómica, límite de tamaño. `java.io` puro: se prueba en la JVM |
| `LigandRepository` | `net/LigandRepository.kt` | Las reglas 1–5 alrededor de la descarga; recibe la caché y el reloj por parámetro |
| `Origin` | `net/LigandLoadResult.kt` | `NETWORK`, `ARCHIVE` (copia fresca), `ARCHIVE_OFFLINE` (copia usada porque la red falló) |
| `LoadState.Loaded(molecule, origin)` | `loading/LoadState.kt` | El origen viaja hasta la pantalla |
| Fábrica del `ViewModel` | `ui/LigandsViewModel.kt` | `FileLigandCache(File(cacheDir, "ligands"))` |

El repositorio **ya bloqueaba** el hilo (red + *parser*) y se llama desde `Dispatchers.IO`: leer y escribir ficheros pequeños ahí es lo correcto. Los parámetros nuevos tienen valor por defecto (`NoLigandCache`, el reloj del sistema): **ninguna prueba antigua cambió**.

---

## 6. En pantalla: de dónde vino la molécula

La pestaña del panel superior de la molécula:

| Origen | Pestaña |
|--------|---------|
| Descargada ahora | **AUSPEX // LIVE** |
| Copia del móvil (fresca) | **AUSPEX // ARCHIVE** |
| Copia del móvil porque la red falló | **AUSPEX // OFFLINE ARCHIVE** |

Así la demostración sin conexión **se ve**: el evaluador no tiene que creer que «viene de la caché».

---

## 7. Pruebas y mutación

- **`FileLigandCacheTest`** (7): escribir y leer con su fecha; ausente → null; crea la carpeta; no deja temporales; por encima del límite se van los más antiguos; `remove`; una «carpeta» que es un fichero no lanza nada.
- **`LigandRepositoryCacheTest`** (11): se guarda lo descargado; copia reciente sin red (0 llamadas); **la demostración** (descargar, modo avión, abrir otra vez); copia vieja + red → se renueva; copia vieja antes que cinco tipos de error de red; 404 borra la copia; lo que no se interpreta no se guarda; copia dañada → se borra y se pide a la red; también sin red; sin copia, el error de siempre; un id no válido no toca nada.
- **`LigandLoaderTest`:** el origen llega al estado de la pantalla.

**Mutantes:** quitar la regla 1, guardar sin interpretar, no borrar la copia con 404, no usar la copia vieja si la red falla, no borrar la copia dañada (**sobrevivió**: la prueba existente descargaba después y tapaba el fallo; nueva prueba «copia dañada **sin red**»), no recortar por tamaño, recortar los más nuevos en vez de los más viejos. **Todos muertos.**

---

## 8. Cómo probarlo en el móvil (la demostración)

1. Con red, abrir un ligando (p. ej. `001`): pestaña **AUSPEX // LIVE**.
2. Volver a la lista, **modo avión**, abrir el **mismo**: se abre al instante, pestaña **AUSPEX // ARCHIVE**.
3. Con modo avión, abrir uno **nunca abierto**: «No internet connection. Please check your network.».
4. (Para ver **OFFLINE ARCHIVE** haría falta una copia de más de 30 días; las pruebas lo cubren.)
5. Ajustes → Apps → Swifty Proteins → Almacenamiento → **Borrar caché**: el paso 2 vuelve a fallar sin red (y las **cuentas siguen**: viven en otra carpeta).

---

## 9. Trampas

1. **Guardar antes de validar:** el portal cautivo de un hotel (HTML con «200 OK») quedaría guardado como «el ligando». Por eso se guarda **después** de interpretar.
2. **Confundir 404 con «sin red»:** con una copia vieja, un 404 no debe tapar la verdad.
3. **Red primero con mala red:** el usuario esperaría todo el tiempo de espera antes de ver la copia. Por eso, copia fresca primero.
4. **Usar `filesDir`:** crecería sin control y sobreviviría a «Borrar caché», que es justo lo que el usuario espera que la vacíe.
5. **Nombre de fichero a partir de datos externos:** solo es seguro porque el id ya pasó la validación del repositorio.
6. **Cambiar el reloj del móvil** puede hacer parecer fresca una copia vieja o al revés: como mucho, una descarga de más o una copia de más. Sin riesgo.

---

## 10. Alternativas

| Alternativa | Por qué no |
|-------------|-----------|
| Caché HTTP de OkHttp | Usamos `HttpURLConnection` (documento 06); además respeta las cabeceras del servidor, que no controlamos, y no da el «vieja antes que nada» para la app entera |
| Room / base de datos | Para ficheros de texto independientes, una carpeta es más simple y cada fichero se lee entero |
| Guardar la molécula ya interpretada | Ata la caché a nuestras clases: una versión nueva de la app tendría que tirar la caché |
| `WorkManager` para descargar todos los ligandos | 1243 descargas sin que el usuario lo pida: tráfico y batería ajenos al enunciado |

---

## 11. Decisión

| # | Decisión | Estado | Motivo |
|---|----------|--------|--------|
| D47 | Caché de `.cif` en `cacheDir/ligands/`: **copia fresca 30 días primero**, red después, **copia vieja si la red falla**, 404 borra, solo se guarda lo que se interpretó, 20 MB con los más antiguos fuera; el origen se ve en la pestaña | Decidida | Funciona sin conexión (instrucciones generales y VII.4), instantáneo, nunca guarda datos no validados |

---

## 12. Qué recordar

- **Caché primero con caducidad + vieja antes que nada** cuando los datos casi no cambian.
- **Validar antes de guardar.** Lo que entra en la caché tiene que haber pasado el mismo filtro que lo que se enseña.
- **404 es una respuesta; «sin red» es un fallo.** Se tratan distinto.
- **`cacheDir` para lo que se puede volver a descargar**; `filesDir` / `noBackupFilesDir` para lo que no (las cuentas).
- Una caché **nunca** debe hacer fallar lo que sin ella funcionaría.
