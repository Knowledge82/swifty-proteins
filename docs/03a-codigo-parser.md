# 03a · Código del parser: tokenizador y lector de tablas (línea a línea)

> Estado: **ESCRITO**. Describe el código que existe hoy: `Molecule.kt`, `CifToken.kt`, `CifFailure.kt`, `CifTokenizer.kt`,
> `CifTable.kt` y `CifTableReader.kt`, más sus dos ficheros de pruebas. **Falta** la tercera capa (el extractor, que convierte tablas
> en una `Molecule`) y el punto de entrada público `CifParser.parse`; se describen en la sección 11.
> Las citas de código de este documento se generan **desde los ficheros reales** por rango de líneas; si se edita un fichero hay que
> regenerar el documento (los números de línea dejarían de coincidir).
> Redactado el 19/11/2026. El diseño y los motivos están en [`03-formato-cif-y-parser.md`](03-formato-cif-y-parser.md); aquí está el
> **código**.

## Índice

1. [Mapa: qué fichero hace qué y dónde vive](#1-mapa-qué-fichero-hace-qué-y-dónde-vive)
2. [Conceptos de Kotlin que aparecen](#2-conceptos-de-kotlin-que-aparecen)
3. [`Molecule.kt`: el modelo de datos](#3-moleculekt-el-modelo-de-datos)
4. [`CifToken.kt`: la pieza mínima](#4-ciftokenkt-la-pieza-mínima)
5. [`CifFailure.kt`: los fallos tipados](#5-ciffailurekt-los-fallos-tipados)
6. [`CifTokenizer.kt`: capa 1, del texto a los tokens](#6-ciftokenizerkt-capa-1-del-texto-a-los-tokens)
7. [`CifTable.kt`: tabla y bloque](#7-ciftablekt-tabla-y-bloque)
8. [`CifTableReader.kt`: capa 2, de los tokens a las tablas](#8-ciftablereaderkt-capa-2-de-los-tokens-a-las-tablas)
9. [Simulación de extremo a extremo](#9-simulación-de-extremo-a-extremo)
10. [Las pruebas: qué hay, cómo se ejecutan, qué demuestran](#10-las-pruebas-qué-hay-cómo-se-ejecutan-qué-demuestran)
11. [Qué falta](#11-qué-falta)
12. [Alternativas y por qué no se eligieron](#12-alternativas-y-por-qué-no-se-eligieron)
13. [Trampas y límites conocidos](#13-trampas-y-límites-conocidos)
14. [Qué está verificado y qué no](#14-qué-está-verificado-y-qué-no)
15. [Qué recordar](#15-qué-recordar)

---

## 1. Mapa: qué fichero hace qué y dónde vive

Rutas relativas a la raíz del repositorio. El paquete base es `dev.knowledge82.swiftyproteins`, que en disco es la carpeta
`dev/knowledge82/swiftyproteins/`. **La carpeta tiene que coincidir con la línea `package` de cada fichero**; Kotlin lo tolera,
pero Android Studio protesta y es una fuente de confusión.

| Fichero | Ruta | Líneas | Capa | Para qué |
|---------|------|-------:|------|----------|
| `Molecule.kt` | `app/src/main/java/dev/knowledge82/swiftyproteins/molecule/` | 62 | Dominio | Lo que el visor 3D necesita: átomos, enlaces, molécula. No sabe nada de CIF. |
| `CifToken.kt` | `app/src/main/java/dev/knowledge82/swiftyproteins/cif/` | 27 | Capa 1 | Tipos `CifTokenType` y `CifToken`: la pieza en que se corta el texto. |
| `CifFailure.kt` | `.../cif/` | 40 | Capas 1 y 2 | Los motivos de fallo (`CifFailure`) y la excepción interna que los transporta. |
| `CifTokenizer.kt` | `.../cif/` | 97 | Capa 1 | Texto → lista de tokens. Conoce la **sintaxis** (comillas, comentarios, campos `;`). |
| `CifTable.kt` | `.../cif/` | 31 | Capa 2 | `CifTable` (columnas con nombre + filas) y `CifBlock` (nombre + tablas). |
| `CifTableReader.kt` | `.../cif/` | 82 | Capa 2 | Lista de tokens → `CifBlock`. Conoce la **estructura** (`loop_`, pares sueltos). |
| `CifTokenizerTest.kt` | `app/src/test/java/dev/knowledge82/swiftyproteins/cif/` | 217 | Pruebas | 21 pruebas de la capa 1. |
| `CifTableReaderTest.kt` | `.../cif/` (en `src/test`) | 217 | Pruebas | 20 pruebas de la capa 2. |
| `001.cif` | `app/src/test/resources/cif/` | 275 | Datos de prueba | El ligando real `001`, **sin modificar**. Lo leen dos pruebas. |

Flujo de datos, con lo que existe y lo que falta:

```
 String (el .cif entero)
        │
        ▼
 CifTokenizer.tokenize ───────────────►  List<CifToken>
   (capa 1, sintaxis)                          │
        │ puede lanzar                         ▼
        │ CifFormatException            CifTableReader.read ───────►  CifBlock
        │ (comilla o ';' sin cerrar)      (capa 2, estructura)         ├─ CifTable  _chem_comp_atom  (87 filas × 18 col.)
        │                                       │ puede lanzar         ├─ CifTable  _chem_comp_bond  (90 filas × 7 col.)
        │                                       │ CifFormatException   └─ ... (7 tablas en el fichero 001)
        │                                       │ (sin data_, fila truncada...)         │
        ▼                                       ▼                                       ▼
  [FALTA] CifParser.parse(text): captura la excepción y devuelve éxito/fallo      [FALTA] extractor (capa 3)
                                                                                         │
                                                                                         ▼
                                                                                  Molecule  (Molecule.kt)
```

Dependencias entre ficheros (una flecha significa «usa»): `CifTokenizer` → `CifToken`, `CifFailure`; `CifTableReader` → `CifToken`,
`CifTable`, `CifFailure`; `CifTable` no usa nada; `Molecule` no usa nada y **nadie de la capa CIF lo usa todavía** (lo usará el
extractor). Por eso se pueden probar capa por capa.

---

## 2. Conceptos de Kotlin que aparecen

Se explican aquí una vez; en las secciones siguientes se remite a este apartado.

| Concepto | Qué es | Dónde se ve |
|----------|--------|-------------|
| `object X { ... }` | Una **clase con una única instancia**, creada por el lenguaje. Se usa como `CifTokenizer.tokenize(...)`, sin `new` ni variable. Es lo adecuado para funciones sin estado. | `CifTokenizer`, `CifTableReader` |
| `data class` | Clase pensada para **guardar datos**: el compilador genera `equals`, `hashCode`, `toString` y `copy`. Dos `CifToken` con los mismos campos son iguales; eso es lo que permite escribir `assertEquals` en las pruebas. | `CifToken`, `Atom`, `Bond`, `Molecule`, varios `CifFailure` |
| `data object` | Lo mismo para un `object` (un fallo sin datos). Da un `toString` legible: `NoDataBlock`. | `CifFailure.NoDataBlock` |
| `enum class` | Un conjunto cerrado de valores con nombre. | `CifTokenType`, `BondOrder` |
| `sealed interface` | Una interfaz con un **conjunto cerrado de implementaciones**, todas en el mismo paquete y módulo. El compilador sabe cuáles son, así que un `when` sobre ella puede exigir que se traten todas. | `CifFailure` |
| `when` | El `switch` de Kotlin, pero más potente: acepta condiciones arbitrarias (`c == ' ' \|\| ...`) y puede **devolver un valor**. Se evalúan las ramas **en orden** y se ejecuta la **primera** que se cumple. | `CifTokenizer` |
| `val` / `var` | `val` no se reasigna; `var` sí. Se usa `val` siempre que se puede. | todo |
| `Int?` | Un entero **que puede ser `null`**. El compilador obliga a tratar el caso nulo antes de usar el valor. Es la forma de decir «puede no existir» sin excepciones. | `CifTable.indexOf` |
| `?:` y `!!` | `a ?: b` = «`a`, y si es `null`, `b`». `x!!` = «afirmo que no es `null`; si lo es, que explote». Solo se usa `!!` en pruebas. | pruebas |
| `List` y `ArrayList` | `List<T>` es la vista de **solo lectura**; `ArrayList<T>` es la lista modificable concreta. Se construye con `ArrayList` y se devuelve como `List`. | todo |
| `+=` sobre una lista | Con una lista **modificable** declarada con `val`, `lista += x` llama a `add(x)`. No reasigna la variable. | tokenizador, lector |
| Etiqueta (`readLoop@`) y `break@readLoop` | Permite salir de un bucle **concreto** desde dentro de un `when` anidado. | `CifTableReader` |
| `require(cond) { mensaje }` | Lanza `IllegalArgumentException` con ese mensaje si `cond` es falsa. Sirve para validar argumentos. | `Molecule` |
| `init { ... }` | Bloque que se ejecuta **al construir** el objeto, justo después del constructor primario. | `Molecule` |
| `buildMap { ... }` | Crea un `Map` de solo lectura rellenándolo dentro del bloque. | `CifTable` |
| `chunked(n)` | Parte una lista en trozos de `n` elementos: `[1,2,3,4].chunked(2)` → `[[1,2],[3,4]]`. El último trozo puede ser más corto. | `CifTableReader` |
| `substringBefore('.')` | Texto hasta la primera aparición del carácter (o todo el texto si no aparece). | `CifTableReader` |

**Verbos de texto que se repiten:** `text[i]` es el carácter en la posición `i` (empieza en 0); `text.substring(a, b)` toma desde `a`
**incluido** hasta `b` **excluido**; `text.indexOf('\n', i)` busca el siguiente salto de línea desde `i` y devuelve `-1` si no hay.

---

## 3. `Molecule.kt`: el modelo de datos

Este fichero es el **contrato** entre el parser y el visor. El parser produce una `Molecule`; el visor la consume. Ninguno de los dos
sabe cómo trabaja el otro. Si mañana cambiamos el formato de entrada (JSON, mmCIF completo), el visor ni se entera.

**Sin Android, sin red, sin SceneView, sin CIF**: es Kotlin puro. Por eso se prueba en la JVM sin móvil.

### 3.1 `BondOrder`

```kotlin
// Molecule.kt · líneas 10–11
10  /** Multiplicity of a bond, as written in the `value_order` column of the file. */
11  enum class BondOrder { SINGLE, DOUBLE, TRIPLE, AROMATIC, OTHER }
```

- **Línea 10** es un comentario KDoc (`/** ... */`): documentación que Android Studio muestra al pasar el ratón.
- **Línea 11**: cinco valores posibles. `SINGLE`, `DOUBLE` y `TRIPLE` son los únicos que aparecen en los 1243 ligandos del corpus
  (`SING`, `DOUB`, `TRIP` en el fichero). `AROMATIC` está previsto por si el fichero dijera `AROM`; `OTHER` recoge cualquier
  valor desconocido para que el parser **no falle por un adjetivo** (un enlace desconocido se dibuja como sencillo).

### 3.2 `Atom`

```kotlin
// Molecule.kt · líneas 21–27
21  data class Atom(
22      val name: String,
23      val element: String,
24      val x: Float,
25      val y: Float,
26      val z: Float,
27  )
```

- `data class Atom(...)`: una clase de datos. Entre paréntesis, el **constructor primario**: cada `val` es a la vez parámetro y
  propiedad. No hay cuerpo `{ }` porque no hace falta.
- `name`: el nombre **dentro del ligando** (`C01`, `O5'`). Es lo que se mostrará al tocar el átomo.
- `element`: ya **normalizado** (`Cl`, no `CL`). El fichero escribe los elementos en mayúsculas; normalizar en el parser deja el resto
  de la app sin saber nada de ese detalle.
- `x`, `y`, `z`: `Float` (32 bits) en ångström. `Float` basta: la precisión de 32 bits (≈7 cifras) es mucho mayor que los 3
  decimales de los ficheros, y es lo que usan Filament y SceneView.

### 3.3 `Bond`

```kotlin
// Molecule.kt · líneas 35–39
35  data class Bond(
36      val from: Int,
37      val to: Int,
38      val order: BondOrder,
39  )
```

- `from` y `to` son **índices** en `Molecule.atoms`, no nombres. El parser usa nombres para *encontrar* los átomos (así lo escribe el
  fichero) y los convierte a índices **una sola vez**. Después `molecule.atoms[bond.from]` es acceso directo; con nombres habría que
  buscar en la lista por cada enlace en cada fotograma.
- El orden de `from`/`to` no importa.

### 3.4 `Molecule` y su `init`

```kotlin
// Molecule.kt · líneas 48–62
48  data class Molecule(
49      val id: String,
50      val atoms: List<Atom>,
51      val bonds: List<Bond>,
52  ) {
53      init {
54          require(atoms.isNotEmpty()) { "A molecule needs at least one atom" }
55          for (bond in bonds) {
56              require(bond.from in atoms.indices && bond.to in atoms.indices) {
57                  "Bond $bond points outside the ${atoms.size} atoms"
58              }
59              require(bond.from != bond.to) { "Bond $bond joins an atom with itself" }
60          }
61      }
62  }
```

- **Línea 53** `init { ... }`: se ejecuta cada vez que se construye una `Molecule`, sea quien sea el que la construye.
- **Línea 54**: una molécula sin átomos no se puede dibujar (ni calcular su centro). `require` lanza `IllegalArgumentException`.
- **Líneas 55–60**: para **cada** enlace se comprueba (1) que `from` y `to` son posiciones válidas de `atoms` (`bond.from in atoms.indices`
  es «`from` está entre 0 y `size - 1`») y (2) que no une un átomo consigo mismo.
- **Por qué está aquí y no en el visor**: si el parser tuviera un fallo y generase un enlace hacia un átomo inexistente, el error saltaría
  **aquí, junto a su causa**, con el mensaje «Bond Bond(from=3, to=99, ...) points outside the 5 atoms». Sin esto, saltaría más tarde
  como un `IndexOutOfBoundsException` dentro del hilo de dibujo de la escena 3D, que es mucho más difícil de rastrear. Y en el
  proyecto, un cierre inesperado durante la evaluación es un suspenso.
- **Nota de diseño**: `require` lanza una excepción; la regla «el parser no lanza por entrada mala» (D9) no se rompe, porque una
  `IllegalArgumentException` aquí significaría un **fallo del propio parser**, no un fichero defectuoso. Los ficheros defectuosos se
  detectan antes y se devuelven como `CifFailure`.

---

## 4. `CifToken.kt`: la pieza mínima

```kotlin
// CifToken.kt · líneas 3–16
 3  /** The four kinds of piece a CIF file is cut into. */
 4  enum class CifTokenType {
 5      /** The word `loop_`: a table is about to start. */
 6      LOOP,
 7
 8      /** `data_NAME`: start of a data block. [CifToken.text] holds NAME, without the `data_` prefix. */
 9      DATA,
10
11      /** A data name, e.g. `_chem_comp_atom.atom_id`. It always starts with an underscore. */
12      TAG,
13
14      /** Any value: a bare word, a quoted string (quotes removed) or a `;` text field. */
15      VALUE,
16  }
```

Un fichero CIF es texto. Un **token** es una pieza con sentido: una palabra, un texto entre comillas, un campo `;`. Las cuatro clases:

| Tipo | Ejemplo en el fichero | `text` del token |
|------|-----------------------|------------------|
| `LOOP` | `loop_` | `loop_` (tal cual lo escribió el fichero) |
| `DATA` | `data_001` | `001` (**sin** el prefijo `data_`) |
| `TAG` | `_chem_comp_atom.atom_id` | el nombre completo, con el `_` |
| `VALUE` | `C01`, `"O5'"`, `1.5`, `.`, `?` o un campo `;...;` | el contenido **sin comillas** |

Detalles importantes:

- `.` y `?` son **valores** (`VALUE`) en esta capa. Significan «no aplica» y «desconocido», pero decidir eso es cosa de la capa 3.
- Todos los valores siguen siendo **texto**. `26.108` es la cadena `"26.108"`; convertirla a `Float` es de la capa 3, que sabe en qué
  columna está.

```kotlin
// CifToken.kt · líneas 18–27
18  /**
19   * One piece of the file.
20   *
21   * @param line 1-based line where the token starts. It exists only to make error messages useful.
22   */
23  data class CifToken(
24      val type: CifTokenType,
25      val text: String,
26      val line: Int,
27  )
```

- `line` es la línea (desde 1) donde empieza el token. **No se usa para parsear**: existe solo para que un mensaje de error diga
  «línea 214» y no «algo falló».

---

## 5. `CifFailure.kt`: los fallos tipados

```kotlin
// CifFailure.kt · líneas 3–32
 3  /** Why a file could not be understood. New reasons are added here as the parser grows. */
 4  sealed interface CifFailure {
 5      // ---- layer 1: tokenizer ------------------------------------------------------------------
 6
 7      /** A quote was opened and the line ended before it was closed. */
 8      data class UnterminatedQuote(val line: Int) : CifFailure
 9
10      /** A `;` text field was opened and the file ended before the closing `;`. */
11      data class UnterminatedTextField(val line: Int) : CifFailure
12
13      // ---- layer 2: table reader ---------------------------------------------------------------
14
15      /** The file does not start with `data_NAME`. An HTML error page or an empty body ends up here. */
16      data object NoDataBlock : CifFailure
17
18      /** A `loop_` with no column names after it. */
19      data class LoopWithoutColumns(val line: Int) : CifFailure
20
21      /**
22       * The values of a `loop_` do not fill whole rows (e.g. 29 values for 10 columns).
23       * @param firstTag the first column of the loop, to tell which table is broken.
24       */
25      data class TruncatedRow(val firstTag: String, val line: Int) : CifFailure
26
27      /** A data name that should be followed by a value is followed by something else, or by the end. */
28      data class MissingValue(val tag: String, val line: Int) : CifFailure
29
30      /** A value that belongs to no data name and to no loop. */
31      data class StrayValue(val value: String, val line: Int) : CifFailure
32  }
```

- **Línea 4** `sealed interface CifFailure`: una lista **cerrada** de motivos de fallo. Cada `data class`/`data object` de dentro es
  una variante. Ventaja sobre un `String` de error: el código que reacciona al fallo (la interfaz de usuario) puede hacer un `when`
  y el compilador **avisa si se añade un motivo nuevo y alguien olvida tratarlo**.
- Cada variante lleva solo los datos útiles: dónde (`line`), qué (`tag`, `value`).
- Reparto por capas:
  - capa 1: `UnterminatedQuote`, `UnterminatedTextField`;
  - capa 2: `NoDataBlock`, `LoopWithoutColumns`, `TruncatedRow`, `MissingValue`, `StrayValue`;
  - capa 3 (todavía no existe): aquí se añadirán `MissingColumn`, `BadNumber`, `NoAtoms`, etc.
- **Relación con el error de usuario**: de los cuatro errores de carga que exige la tarea (sin red, 404, fichero corrupto,
  *timeout*), **todo lo de esta capa es «fichero corrupto»**. Sin red, 404 y *timeout* se detectan antes, en la capa de red.

```kotlin
// CifFailure.kt · líneas 34–40
34  /**
35   * The only exception the parser layers throw. It carries a typed [failure], not just a message.
36   *
37   * It is thrown INSIDE the parser's layers and caught at its public entry point, which turns it into a
38   * result value. The rest of the app never sees this exception.
39   */
40  class CifFormatException(val failure: CifFailure) : Exception(failure.toString())
```

- **Línea 40** `class CifFormatException(val failure: CifFailure) : Exception(failure.toString())`: la **única** excepción de las capas
  del parser. Hereda de `Exception` y lleva el `failure` tipado, además del mensaje de texto.
- **Por qué existe una excepción si D9 dice «sin excepciones»**: dentro de las capas, lanzar es la forma más sencilla de abandonar
  un análisis en cualquier profundidad (si el bucle del tokenizador detecta una comilla sin cerrar, no tiene un valor de retorno
  cómodo). La regla se cumple en el **borde**: el punto de entrada público (`CifParser.parse`, por escribir) captura
  `CifFormatException` y devuelve un resultado. El resto de la aplicación **nunca** ve esta excepción.

---

## 6. `CifTokenizer.kt`: capa 1, del texto a los tokens

### 6.1 Idea general

Un tokenizador recorre el texto **carácter a carácter** con un índice `i`, decide qué clase de pieza empieza en `text[i]`, la consume
entera, produce un token y sigue. No entiende de química ni de tablas; solo de sintaxis.

La clave de la sintaxis CIF para entender el código: **el significado de un carácter depende de dónde está**.

- `#` empieza un comentario solo si está **al principio de un token** (después de espacios). `C1#x` es una palabra.
- `;` abre un campo de texto solo si está en la **columna uno**. `a;b` es una palabra.
- `'` o `"` abre una cadena solo al principio de un token, y la cadena **solo se cierra** con la misma comilla **seguida de
  espacio**. Por eso `"O5'"` funciona y `O5'` también.

### 6.2 Preparación

```kotlin
// CifTokenizer.kt · líneas 9–22
 9  object CifTokenizer {
10
11      /** @throws CifFormatException if a quote or a `;` text field is never closed. */
12      fun tokenize(rawText: String): List<CifToken> {
13          // A UTF-8 file may start with an invisible BOM character. It is not part of the data.
14          val text = rawText.removePrefix("\uFEFF")
15          val n = text.length
16          val tokens = ArrayList<CifToken>()
17
18          var i = 0       // position of the next character to read
19          var line = 1    // current line, for error messages
20
21          while (i < n) {
22              val c = text[i]
```

- **Línea 9**: `object`, ver sección 2: una sola instancia, se llama `CifTokenizer.tokenize(texto)`.
- **Línea 12**: la función recibe el texto entero y devuelve la lista de tokens. `rawText` («texto en bruto») se llama así porque
  se va a limpiar en la línea siguiente.
- **Línea 14**: algunos ficheros UTF-8 empiezan por un carácter invisible, el **BOM** (`U+FEFF`). Si no se quita, el primer token
  sería `﻿data_001` en lugar de `data_001`, y el lector fallaría con `NoDataBlock` en un fichero perfectamente válido.
  `removePrefix` solo quita si **está** al principio. Se escribe con la secuencia de escape `"﻿"` y no con el carácter
  literal, para que el fichero fuente no contenga un carácter invisible que alguien borre sin querer.
- **Línea 15**: `n` guarda la longitud para no recalcularla.
- **Línea 16**: lista **modificable** donde se irán acumulando los tokens.
- **Línea 18**: `i` es el **cursor**: posición del próximo carácter por leer.
- **Línea 19**: `line` cuenta líneas, solo para los mensajes de error.
- **Línea 21**: `while (i < n)`: el bucle termina cuando el cursor llega al final.
- **Línea 22**: `c` es el carácter actual. Es lo que mira el `when` de la línea 23.

**Invariante clave del bucle**: en **cada** rama del `when`, `i` **avanza al menos una posición**. Si alguna rama dejase `i` igual,
el bucle sería infinito y la app se quedaría colgada en un fichero malo. Se verifica rama por rama:

| Rama | ¿Cómo avanza `i`? |
|------|-------------------|
| 1 salto de línea | `i++` |
| 2 espacio/tab/`\r` | `i++` |
| 3 comentario | avanza hasta el `\n` (que luego consume la rama 1); como `text[i]` era `#`, avanza **al menos** el propio `#` |
| 4 campo `;` | `i = newline + 2`, y `newline >= i` |
| 5 comillas | `i = j + 1`, y `j >= i + 1` |
| 6 palabra | `i = j`, y `j > i` porque `c` no es espacio, así que la palabra tiene al menos un carácter |

### 6.3 Ramas 1 y 2: saltos de línea y espacios

```kotlin
// CifTokenizer.kt · líneas 24–31
24  // --- 1. line break: only counts lines -------------------------------------
25  c == '\n' -> {
26      line++
27      i++
28  }
29
30  // --- 2. other whitespace: skipped (the '\r' of Windows line endings is here) -----
31  c == ' ' || c == '\t' || c == '\r' -> i++
```

- **Rama 1**: un `\n` aumenta el contador de líneas y avanza. No genera token: **los saltos de línea no significan nada** en CIF
  (salvo para la columna uno de los campos `;`). Las filas de un `loop_` se separan **contando valores**, no mirando saltos.
- **Rama 2**: espacio, tabulador y `\r` se saltan. El `\r` es la mitad de un final de línea de Windows (`\r\n`); tratarlo como
  espacio hace que un fichero con finales de línea Windows dé los mismos tokens que uno Unix (hay una prueba para esto).
- `c == ' ' || c == '\t' || c == '\r' -> i++`: en un `when`, `->` separa la condición de la acción. Aquí la acción es una sola
  expresión, así que no hacen falta llaves.

### 6.4 Rama 3: comentarios

```kotlin
// CifTokenizer.kt · líneas 33–38
33  // --- 3. comment: from '#' to the end of the line ----------------------------------
34  // We are at the START of a token (whitespace was skipped), which is the only place
35  // where '#' starts a comment. In the middle of a word, e.g. "C1#", it never gets here.
36  c == '#' -> {
37      while (i < n && text[i] != '\n') i++
38  }
```

- Estamos al **principio de un token** (los espacios ya se saltaron en la rama 2), que es el único sitio donde `#` es comentario.
  En `C1#x` esta rama nunca se alcanza: la rama 6 leyó la palabra entera, `#` incluido.
- El bucle `while` avanza hasta **antes** del `\n` (sin consumirlo). La rama 1 lo consumirá en la vuelta siguiente y contará la línea.
  Así el recuento de líneas vive en **un solo sitio**.
- Si el comentario es la última línea y no hay `\n`, `i < n` falla y el bucle acaba solo.

### 6.5 Rama 4: campos de texto `;`

```kotlin
// CifTokenizer.kt · líneas 40–56
40  // --- 4. text field: ';' in COLUMN ONE, up to a line that starts with ';' --------
41  c == ';' && (i == 0 || text[i - 1] == '\n') -> {
42      val startLine = line
43      // Look for the closing ";": a line break immediately followed by ';'.
44      var newline = text.indexOf('\n', i)
45      while (newline != -1 && !(newline + 1 < n && text[newline + 1] == ';')) {
46          newline = text.indexOf('\n', newline + 1)
47      }
48      if (newline == -1) {
49          throw CifFormatException(CifFailure.UnterminatedTextField(startLine))
50      }
51      // Normalize "\r\n" to "\n" so a file gives the same value with Windows or Unix line endings.
52      val body = text.substring(i + 1, newline).replace("\r\n", "\n").trim('\r', '\n')
53      tokens += CifToken(CifTokenType.VALUE, body, startLine)
54      line += text.substring(i, newline + 1).count { it == '\n' }
55      i = newline + 2   // skip the line break and the closing ';'
56  }
```

Un campo `;` sirve para textos largos o con saltos de línea (en el fichero real, el nombre químico del ligando 001):

```
_chem_comp.name
;1-[2,2-DIFLUORO-...
...
;
```

- **Línea 41**: la condición exige `;` **y** columna uno: `i == 0` (primer carácter del texto) o el carácter anterior es `\n`.
  Si `;` aparece en otro sitio, esta condición es falsa y el `when` continúa hacia abajo hasta la rama 6, que lo trata como parte de
  una palabra (hay una prueba: `a;b` y `;x`).
- **Línea 42**: se guarda la línea donde **empieza** el campo; es la que se informa si no se cierra.
- **Líneas 44–47**: se busca el cierre, que es **un salto de línea seguido inmediatamente de `;`**.
  `indexOf('\n', i)` da el primer salto; mientras no vaya seguido de `;`, se busca el siguiente. Se sale con `newline` apuntando al
  salto previo al `;` de cierre, o con `-1` si se acabó el texto. La comprobación `newline + 1 < n` evita leer fuera del texto cuando
  el salto es el último carácter.
- **Líneas 48–50**: sin cierre → `UnterminatedTextField` con la línea de inicio.
- **Línea 52**: el contenido es `text.substring(i + 1, newline)`: **desde después del `;` de apertura hasta antes del salto de
  cierre**. Luego:
  - `.replace("\r\n", "\n")` convierte finales Windows en Unix **dentro** del contenido, para que el valor sea idéntico con
    cualquiera de los dos tipos de fichero (esto lo descubrió una prueba que falló: salía `"multi\r\nline"`);
  - `.trim('\r', '\n')` quita saltos de línea sobrantes al principio y al final (el que sigue al `;` de apertura, y un `\r` suelto
    antes del salto de cierre en ficheros Windows). Límite conocido: quita **todos** los saltos extremos, no solo uno; para nuestros
    datos es irrelevante.
- **Línea 53**: se añade **un** token `VALUE` (el campo entero, saltos incluidos, es un único valor).
- **Línea 54**: se cuentan los `\n` desde el `;` de apertura hasta el salto de cierre (incluido) y se suman a `line`. Así los números
  de línea de los tokens posteriores siguen siendo correctos (prueba `linesAreCountedInsideTextFields`).
- **Línea 55**: `i = newline + 2` salta el `\n` y el `;` de cierre. Lo que quede en esa línea después del `;` (normalmente nada) se
  tokeniza con normalidad.

### 6.6 Rama 5: cadenas con comillas

```kotlin
// CifTokenizer.kt · líneas 58–72
58  // --- 5. quoted string: 'text' or "text" -----------------------------------------
59  // The closing quote only counts if it is followed by whitespace. That is why
60  // "O5'" works: the ' inside is followed by a " and not by a space.
61  c == '\'' || c == '"' -> {
62      var j = i + 1
63      while (true) {
64          if (j >= n || text[j] == '\n') {
65              throw CifFormatException(CifFailure.UnterminatedQuote(line))
66          }
67          if (text[j] == c && (j + 1 >= n || isWhitespace(text[j + 1]))) break
68          j++
69      }
70      tokens += CifToken(CifTokenType.VALUE, text.substring(i + 1, j), line)
71      i = j + 1
72  }
```

- **Línea 61**: `c == '\'' || c == '"'`: comilla simple o doble. En Kotlin, un carácter se escribe con comillas simples, y la
  comilla simple se escapa: `'\''`.
- **Línea 62**: `j` empieza justo **después** de la comilla de apertura y busca la de cierre.
- **Línea 63** `while (true)`: bucle infinito **controlado**; solo se sale por `break` (línea 67) o por excepción (línea 65).
- **Líneas 64–66**: si se llega al final del texto o a un `\n` sin haber cerrado, es `UnterminatedQuote`. **Una cadena entre
  comillas no puede cruzar líneas** en CIF; por eso un `'` suelto no se «traga» el resto del fichero: el error salta al final de
  **su** línea.
- **Línea 67**: la condición de cierre: el carácter es **la misma comilla** que abrió (`text[j] == c`) **y** lo siguiente es el final
  del texto o un espacio. Es la regla que hace posible `"O5'"`: el `'` interior va seguido de `"`, no de espacio, así que no cierra; el
  cierre es el `"` final.
- **Línea 70**: el token es `text.substring(i + 1, j)`: **sin** las comillas. Un `''` vacío da un valor vacío (hay una prueba).
- **Línea 71**: `i = j + 1` continúa tras la comilla de cierre.

### 6.7 Rama 6: cualquier otra palabra

```kotlin
// CifTokenizer.kt · líneas 74–90
74  // --- 6. any other word: up to the next whitespace ----------------------------------
75  else -> {
76      var j = i
77      while (j < n && !isWhitespace(text[j])) j++
78      val word = text.substring(i, j)
79      tokens += when {
80          word.equals("loop_", ignoreCase = true) ->
81              CifToken(CifTokenType.LOOP, word, line)
82          word.startsWith("data_", ignoreCase = true) ->
83              CifToken(CifTokenType.DATA, word.substring(5), line)
84          word[0] == '_' ->
85              CifToken(CifTokenType.TAG, word, line)
86          else ->
87              CifToken(CifTokenType.VALUE, word, line)
88      }
89      i = j
90  }
```

- **Línea 75** `else`: lo que no encajó en ninguna rama anterior. Aquí cae casi todo: números, nombres, etiquetas, `loop_`, `data_...`,
  un `;` que no está en la columna uno, etc.
- **Líneas 76–77**: se avanza `j` hasta el siguiente espacio. Una palabra termina **solo** con espacio; por eso un `#` o un `'` dentro
  de una palabra (`C1#x`, `O5'`) forman parte de ella.
- **Línea 78**: `word` es el texto de la palabra.
- **Líneas 79–88**: un `when` que **devuelve un valor** (el token) y se suma a la lista con `+=`. Se clasifica así, en este orden:
  1. `loop_` en cualquier combinación de mayúsculas (`ignoreCase = true`) → `LOOP`. Los nombres y palabras clave de CIF no distinguen
     mayúsculas.
  2. empieza por `data_` (sin distinguir mayúsculas) → `DATA`; el texto del token es lo que sigue a esos 5 caracteres
     (`substring(5)`).
  3. empieza por `_` → `TAG`.
  4. cualquier otra cosa → `VALUE`.
- `word[0]` es seguro: la palabra tiene al menos un carácter (ver la tabla de invariantes).
- **Qué pasa con `.` y `?`**: caen en el último caso y son `VALUE` (`"."` y `"?"`).

### 6.8 Final

```kotlin
// CifTokenizer.kt · líneas 93–97
93          return tokens
94      }
95
96      private fun isWhitespace(c: Char) = c == ' ' || c == '\t' || c == '\r' || c == '\n'
97  }
```

- **Línea 93**: se devuelve la lista (como `List<CifToken>`, solo lectura para el que llama).
- **Línea 96**: `isWhitespace` es una función **privada** (solo visible dentro del `object`). Se escribe a mano en lugar de usar
  `Char.isWhitespace()` de Kotlin porque el estándar CIF define los separadores como espacio, tabulador y finales de línea; la función
  de Kotlin acepta también otros espacios Unicode (p. ej. el espacio de no separación), que en un fichero CIF serían parte de una
  palabra.

---

## 7. `CifTable.kt`: tabla y bloque

```kotlin
// CifTable.kt · líneas 3–23
 3  /**
 4   * A table of the file: named columns and rows of text.
 5   *
 6   * It comes from a `loop_` (many rows) or from data names written one by one (a single row). From the
 7   * outside both look the same, which is the point of this class.
 8   *
 9   * Every value is still TEXT here. Turning "26.108" into a Float is the job of the next layer, which
10   * knows what each column means.
11   */
12  class CifTable(rawColumns: List<String>, val rows: List<List<String>>) {
13
14      /** Full column names in lower case, e.g. `_chem_comp_atom.atom_id`. CIF names ignore case. */
15      val columns: List<String> = rawColumns.map { it.lowercase() }
16
17      // column name -> position. If a name were repeated, the first one wins.
18      private val positions: Map<String, Int> = buildMap {
19          columns.forEachIndexed { position, name -> putIfAbsent(name, position) }
20      }
21
22      /** Position of a column, or null if this table does not have it. Ignores upper/lower case. */
23      fun indexOf(tag: String): Int? = positions[tag.lowercase()]
```

El problema que resuelve esta clase: en CIF una tabla puede venir de **dos** formas (un `loop_` con muchas filas, o etiquetas sueltas
con una sola fila) y la capa 3 no debería tener que saber cuál de las dos fue. `CifTable` las presenta igual: **columnas con
nombre** y **filas de texto**.

- **Línea 12** `class CifTable(rawColumns: List<String>, val rows: List<List<String>>)`:
  - `rawColumns` no tiene `val`: es solo un parámetro del constructor, no una propiedad. Se usa en la línea siguiente.
  - `rows` sí tiene `val`: es público. Cada fila es una lista de textos, en el mismo orden que las columnas.
- **Línea 15** `val columns = rawColumns.map { it.lowercase() }`: los nombres de CIF no distinguen mayúsculas, así que se guardan en
  minúsculas. Es lo que permite que una columna `_chem_comp_atom.model_Cartn_x` (con `C` mayúscula en el fichero real) se encuentre
  buscando `_chem_comp_atom.model_cartn_x`.
- **Líneas 18–20** `positions`: un mapa «nombre de columna → posición». Se construye con `buildMap` y `putIfAbsent`: si un nombre
  se repitiera, **gana el primero**.
- **Línea 23** `fun indexOf(tag: String): Int?`: devuelve la posición de la columna, o `null` si esta tabla no la tiene. `tag.lowercase()`
  hace que la búsqueda tampoco distinga mayúsculas. Quien la use **no puede olvidar** el caso `null`: el compilador no le deja usar un
  `Int?` como `Int`.
- **Por qué buscar por nombre y no por posición**: el fichero 001 tiene 18 columnas de átomos; si una versión futura del diccionario añade
  una columna en medio, una posición fija (`row[4]`) leería otro dato **sin dar error**; un nombre falla de forma visible o sigue
  funcionando.

```kotlin
// CifTable.kt · líneas 26–31
26  /** What the file says: its name (`001`) and its tables. */
27  class CifBlock(val name: String, val tables: List<CifTable>) {
28
29      /** The table that has this column, or null. Columns are found by NAME, never by position. */
30      fun tableWith(tag: String): CifTable? = tables.firstOrNull { it.indexOf(tag) != null }
31  }
```

- `CifBlock` es lo que devuelve el lector: el **nombre** del bloque (`001`) y la lista de tablas.
- `tableWith(tag)` devuelve la **primera** tabla que contiene esa columna, o `null`. Se usará así: `block.tableWith("_chem_comp_atom.atom_id")`
  da la tabla de átomos **sin importar cuántas tablas tenga el fichero ni en qué orden vengan**.

---

## 8. `CifTableReader.kt`: capa 2, de los tokens a las tablas

### 8.1 Idea general

Recibe la lista de tokens y la agrupa en tablas. Tres situaciones posibles, según el tipo del token actual (`when (token.type)`):

| Token actual | Significa | Qué hace el lector |
|--------------|-----------|--------------------|
| `LOOP` | empieza una tabla | lee las etiquetas (columnas), luego todos los valores, y los corta en filas contando |
| `TAG` (fuera de un `loop_`) | un par `etiqueta valor` suelto | toma el valor siguiente y lo guarda en el grupo de su categoría |
| `VALUE` | un valor sin etiqueta ni `loop_` | el fichero está mal formado: `StrayValue` |
| `DATA` | empieza **otro** bloque | se detiene: solo se lee el primero |

### 8.2 Preparación

```kotlin
// CifTableReader.kt · líneas 20–30
20  val header = tokens.firstOrNull()
21  if (header == null || header.type != DATA) throw CifFormatException(CifFailure.NoDataBlock)
22
23  val tables = ArrayList<CifTable>()
24  // Loose "tag value" pairs, grouped by category (the part before the dot):
25  //   "_chem_comp.id", "_chem_comp.name"  -> category "_chem_comp"
26  // LinkedHashMap keeps the order in which the categories appeared.
27  val pairs = LinkedHashMap<String, MutableList<Pair<String, String>>>()
28
29  var i = 1   // position in the token list; token 0 was the data_ line
30  readLoop@ while (i < tokens.size) {
```

- **Líneas 20–21**: el primer token **tiene que ser** `data_NOMBRE`. Si la lista está vacía (`firstOrNull()` devuelve `null`) o el
  primer token es de otro tipo, es `NoDataBlock`. Ejemplo real: si el servidor devuelve una **página HTML de error** con código 200,
  el primer token sería `<!DOCTYPE` (un `VALUE`) y se detectaría aquí, sin llegar a mirar átomos.
- **Línea 23**: `tables` acumula las tablas que vienen de `loop_`.
- **Líneas 24–27**: `pairs` agrupa los pares sueltos por **categoría** (lo que va antes del punto en la etiqueta):
  `_chem_comp.id` y `_chem_comp.name` pertenecen a la categoría `_chem_comp`. `LinkedHashMap` conserva el **orden de aparición**.
  El tipo `MutableList<Pair<String, String>>` es «lista modificable de pares (etiqueta, valor)».
- **Línea 29**: `i` empieza en 1 porque el token 0 era `data_`.
- **Línea 30**: `readLoop@ while (...)` es un bucle con **etiqueta**, para poder salir de él desde dentro del `when` (línea 33).

### 8.3 Rama `DATA`

```kotlin
// CifTableReader.kt · líneas 32–33
32  when (token.type) {
33      DATA -> break@readLoop   // start of a second block: we do not need it
```

- Si aparece un segundo `data_`, el bucle acaba y **todo lo que sigue se ignora**. Los ficheros de ligandos tienen un solo bloque;
  esto es una defensa contra ficheros raros.
- `break@readLoop` (con etiqueta) y no un `break` a secas: dentro de un `when` anidado en un `while`, la etiqueta deja claro **qué**
  bucle se abandona.

### 8.4 Rama `LOOP`

```kotlin
// CifTableReader.kt · líneas 35–58
35  LOOP -> {
36      // 1) the column names: the TAG tokens right after `loop_`
37      var j = i + 1
38      val columns = ArrayList<String>()
39      while (j < tokens.size && tokens[j].type == TAG) {
40          columns += tokens[j].text
41          j++
42      }
43      if (columns.isEmpty()) {
44          throw CifFormatException(CifFailure.LoopWithoutColumns(token.line))
45      }
46      // 2) the values: every VALUE token that follows, until something else appears
47      val values = ArrayList<String>()
48      while (j < tokens.size && tokens[j].type == VALUE) {
49          values += tokens[j].text
50          j++
51      }
52      // 3) rows are NOT separated by line breaks: we know where one ends by counting.
53      if (values.size % columns.size != 0) {
54          throw CifFormatException(CifFailure.TruncatedRow(columns[0], token.line))
55      }
56      tables += CifTable(columns, values.chunked(columns.size))
57      i = j
58  }
```

Se ejecuta en cuatro fases, con un cursor auxiliar `j` que empieza justo después de `loop_` (línea 37):

1. **Columnas (líneas 38–42)**: mientras el token sea `TAG`, se anota su nombre. Se para en el primer token que no sea `TAG`.
2. **Comprobación (líneas 43–45)**: un `loop_` sin ninguna etiqueta no tiene sentido → `LoopWithoutColumns`.
3. **Valores (líneas 46–51)**: mientras el token sea `VALUE`, se anota su texto. Se para en un `TAG`, un `LOOP`, un `DATA` o el final.
4. **Filas (líneas 52–56)**: el cierre del `loop_` **no está marcado**; los valores vienen seguidos y la longitud de cada fila se
   conoce por el número de columnas.
   - Si `values.size % columns.size != 0` (el resto de dividir es distinto de cero), los valores **no llenan filas completas**: un valor
     falta o sobra. Es `TruncatedRow`, y se informa la **primera columna** del bucle para saber qué tabla está rota. Ejemplo: 29
     valores para 10 columnas → resto 9 → fichero cortado a mitad de fila.
   - Si no, `values.chunked(columns.size)` corta en filas: con 3 columnas, `[a,b,c,d,e,f]` → `[[a,b,c],[d,e,f]]`.

**Línea 57** `i = j`: el cursor principal salta a donde terminó el bucle, para que la vuelta siguiente lea el siguiente token **sin
   releer** los ya consumidos.

**Por qué esto es el corazón del lector**: un `loop_` real del fichero 001 se parece a esto:

```
loop_
_chem_comp_atom.comp_id                  ← 18 etiquetas (líneas 34 a 51 del fichero)
_chem_comp_atom.atom_id
...
_chem_comp_atom.pdbx_ordinal
001  C01   C01   C  0  1  Y  N  N  26.108  12.501  25.848   0.484  -0.006  -3.053  C01   001   1
001  C02   C02   C  0  1  Y  N  N  25.498  13.476  26.660   0.579   1.363  -3.213  C02   001   2
```
(Filas reales de `001.cif`, líneas 52 y 53.) Cada fila ocupa una línea **en este fichero**, pero eso es casualidad de formato.

El estándar permite partir una fila en varias líneas o poner varias filas en una. Si el lector dependiera de los saltos de línea, un
fichero reformateado daría datos corruptos **sin error**. Contando valores, el resultado es el mismo.

### 8.5 Rama `TAG`: pares sueltos

```kotlin
// CifTableReader.kt · líneas 60–70
60  TAG -> {
61      // outside a loop, a tag must be followed by its value
62      val value = tokens.getOrNull(i + 1)
63      if (value == null || value.type != VALUE) {
64          throw CifFormatException(CifFailure.MissingValue(token.text, token.line))
65      }
66      val category = token.text.substringBefore('.').lowercase()
67      pairs.getOrPut(category) { ArrayList() } += token.text to value.text
68      i += 2
69  }
70
```

- **Líneas 62–65**: fuera de un `loop_`, una etiqueta va **siempre** seguida de su valor. `tokens.getOrNull(i + 1)` devuelve `null` si no
  hay siguiente token (en lugar de lanzar una excepción por salirse de la lista). Si no hay valor siguiente, o es otra cosa, es
  `MissingValue` con el nombre de la etiqueta y su línea.
- **Línea 66**: la categoría es el texto antes del primer `.`, en minúsculas.
- **Línea 67** `pairs.getOrPut(category) { ArrayList() } += token.text to value.text`: «dame la lista de esta categoría; si no existe,
  créala vacía y guárdala; añade el par (etiqueta, valor)». `a to b` crea un `Pair(a, b)`.
- **Línea 68** `i += 2`: se consumen **dos** tokens (etiqueta y valor).

### 8.6 Rama `VALUE`: valor huérfano

```kotlin
// CifTableReader.kt · líneas 71–73
71      // a value with no tag before it and no loop around it: the file is malformed
72      VALUE -> throw CifFormatException(CifFailure.StrayValue(token.text, token.line))
73  }
```

- Un valor al que ninguna etiqueta ni `loop_` reclama es una anomalía: `StrayValue` con el valor y la línea. Es mejor fallar que
  **adivinar** a qué pertenecía.
- El `when` sobre un `enum` usado como sentencia debe ser **exhaustivo** (desde Kotlin 1.7). Tiene las cuatro ramas (`DATA`, `LOOP`,
  `TAG`, `VALUE`); si alguien añade un quinto tipo a `CifTokenType` y olvida tratarlo aquí, **no compila**. Es una red de seguridad
  gratuita.

### 8.7 Cierre: los pares se convierten en tablas

```kotlin
// CifTableReader.kt · líneas 76–80
76  // each category of loose pairs becomes a table with ONE row
77  for ((_, list) in pairs) {
78      tables += CifTable(list.map { it.first }, listOf(list.map { it.second }))
79  }
80  return CifBlock(header.text, tables)
```

- Cada categoría de pares sueltos se convierte en una `CifTable` de **una fila**: las columnas son las etiquetas, y la única fila los
  valores. Es lo que hace que `_chem_comp.id`, `_chem_comp.name`... (los datos generales del ligando) y un `loop_` se vean igual desde fuera.
- `for ((_, list) in pairs)`: recorre el mapa ignorando la clave (`_`), porque aquí solo interesa la lista.
- **Estas tablas van detrás de las de `loop_`** en `block.tables`. No importa porque la capa 3 busca por nombre (`tableWith`).
- Se devuelve `CifBlock(header.text, tables)`: el nombre del bloque es el texto del token `DATA` inicial.

---

## 9. Simulación de extremo a extremo

El fichero de juguete (`mini.cif`; no forma parte del repositorio, es solo para este documento). Contiene un comentario, un campo
de texto `;` de dos líneas, un átomo con comilla en el nombre y dos `loop_`:

```
data_MIN
# fichero de juguete
_chem_comp.id    MIN
_chem_comp.name
;ETHANOL
DE JUGUETE
;
loop_
_chem_comp_atom.atom_id
_chem_comp_atom.type_symbol
_chem_comp_atom.model_Cartn_x
"O5'" O 1.5
C1    C 0.0
loop_
_chem_comp_bond.atom_id_1
_chem_comp_bond.atom_id_2
_chem_comp_bond.value_order
C1 "O5'" SING
```

### 9.1 Capa 1: lo que hace el tokenizador

| Qué hay en `text[i]` | Rama | Resultado |
|----------------------|------|-----------|
| `data_MIN` (línea 1) | 6 (palabra) | `DATA "MIN"` |
| `\n` | 1 | cuenta línea (ahora línea 2) |
| `# fichero de juguete` | 3 (comentario) | nada; salta hasta el `\n` |
| `_chem_comp.id` | 6 | `TAG "_chem_comp.id"` |
| `MIN` | 6 | `VALUE "MIN"` |
| `_chem_comp.name` | 6 | `TAG "_chem_comp.name"` |
| `;` en columna uno (línea 6) | 4 (campo) | busca `\n;` → lo encuentra tras `DE JUGUETE`; contenido `ETHANOL\nDE JUGUETE`; `VALUE`; suma 3 líneas; `i` salta tras el `;` de cierre |
| `loop_` | 6 | `LOOP "loop_"` |
| tres etiquetas de átomo | 6 | tres `TAG` |
| `"O5'"` | 5 (comillas) | abre `"`; el `'` interior va seguido de `"` → no cierra; el `"` final va seguido de espacio → cierra; `VALUE "O5'"` |
| `O`, `1.5`, `C1`, `C`, `0.0` | 6 | cinco `VALUE` |
| `loop_` + tres etiquetas de enlace | 6 | `LOOP` y tres `TAG` |
| `C1`, `"O5'"`, `SING` | 6, 5, 6 | tres `VALUE` |

Lista de tokens resultante (salida **real** del código, no escrita a mano):

```
DATA   MIN
TAG    _chem_comp.id                      VALUE  MIN
TAG    _chem_comp.name                    VALUE  "ETHANOL\nDE JUGUETE"     (un solo valor, con salto dentro)
LOOP   loop_
TAG    _chem_comp_atom.atom_id
TAG    _chem_comp_atom.type_symbol
TAG    _chem_comp_atom.model_Cartn_x
VALUE  O5'   VALUE  O   VALUE  1.5
VALUE  C1    VALUE  C   VALUE  0.0
LOOP   loop_
TAG    _chem_comp_bond.atom_id_1
TAG    _chem_comp_bond.atom_id_2
TAG    _chem_comp_bond.value_order
VALUE  C1    VALUE  O5'   VALUE  SING
```

Observa que el comentario **no** produjo ningún token y que `O5'` perdió las comillas dobles pero **conservó** el apóstrofo.

### 9.2 Capa 2: lo que hace el lector

| `i` | Token | Rama | Efecto |
|----:|-------|------|--------|
| 0 | `DATA MIN` | cabecera | `header` = ese token; `i = 1` |
| 1 | `TAG _chem_comp.id` | `TAG` | siguiente es `VALUE MIN` ✔; categoría `_chem_comp`; `pairs["_chem_comp"] = [(id, MIN)]`; `i = 3` |
| 3 | `TAG _chem_comp.name` | `TAG` | siguiente `VALUE` ✔; `pairs["_chem_comp"]` pasa a 2 pares; `i = 5` |
| 5 | `LOOP` | `LOOP` | columnas: 3 etiquetas; valores: 6 (`O5'`,`O`,`1.5`,`C1`,`C`,`0.0`); 6 % 3 = 0 ✔; `chunked(3)` → 2 filas; `i = 15` |
| 15 | `LOOP` | `LOOP` | columnas: 3; valores: 3; 3 % 3 = 0 ✔; 1 fila; `i` = fin |
| fin | — | sale del `while` | convierte `pairs` en tablas |

Resultado (salida **real**):

| Tabla | Columnas | Filas |
|-------|---------:|------:|
| `_chem_comp_atom...` | 3 | 2 (`[O5', O, 1.5]`, `[C1, C, 0.0]`) |
| `_chem_comp_bond...` | 3 | 1 (`[C1, O5', SING]`) |
| `_chem_comp...` (de los pares sueltos) | 2 | 1 (`[MIN, ETHANOL\nDE JUGUETE]`) |

La tabla de pares sale **la última**, aunque estaba la primera en el fichero. La capa 3 no lo nota porque busca con
`block.tableWith("_chem_comp_atom.atom_id")`.

### 9.3 Qué haría la capa 3 (todavía sin escribir) con este resultado

1. Buscar la tabla de átomos por nombre y las columnas `atom_id`, `type_symbol` y las coordenadas, por nombre.
2. Convertir `"1.5"` en `Float`; un valor que no sea número es un fallo (`BadNumber`).
3. Normalizar el elemento (`CL` → `Cl`).
4. Crear `Atom("O5'", "O", ...)`, `Atom("C1", "C", ...)`.
5. En los enlaces, traducir los **nombres** `C1` y `O5'` a **índices** 1 y 0, y `SING` a `BondOrder.SINGLE`. Si un nombre no existe en los
   átomos, es un fallo.
6. Centrar la molécula en su centroide y devolver `Molecule`.

---

## 10. Las pruebas: qué hay, cómo se ejecutan, qué demuestran

### 10.1 Qué hay

**41 pruebas unitarias**, repartidas en dos ficheros de `app/src/test/java/dev/knowledge82/swiftyproteins/cif/`:

| Fichero | Pruebas | Qué cubre |
|---------|--------:|-----------|
| `CifTokenizerTest.kt` | 21 | lo básico (vacío, etiqueta+valor, `loop_`, mayúsculas); comillas (`"O5'"`, cierre solo con espacio, vacías, sin cerrar); comentarios; campos `;` (multilínea, con línea propia, fuera de columna uno, recuento de líneas, sin cerrar); finales de línea Windows; BOM; **el fichero real 001** |
| `CifTableReaderTest.kt` | 20 | `loop_` y filas por conteo; pares sueltos y categorías; búsqueda de columnas por nombre sin distinguir mayúsculas ni orden; solo el primer bloque; entradas malas (vacía, página HTML, fila truncada, `loop_` sin columnas, etiqueta sin valor, valor sin etiqueta); **el fichero real 001** |

Hay además un fichero de plantilla de Android Studio, `ExampleUnitTest.kt` (comprueba que `2 + 2 == 4`). No aporta nada y se puede
borrar; no estorba.

Cada prueba es una función marcada con `@Test`. El patrón es siempre el mismo: **preparar una entrada, ejecutar, comparar con lo
esperado** (`assertEquals(esperado, obtenido)`). Para los fallos se usa `assertThrows(CifFormatException::class.java) { ... }`, que
exige que el bloque lance esa excepción, y luego se comprueba el `failure` concreto.

**El fichero de datos reales.** Dos pruebas (una por capa) leen `001.cif` con
`javaClass.getResourceAsStream("/cif/001.cif")!!`. Eso busca `cif/001.cif` en el *classpath* de las pruebas; la carpeta
`app/src/test/resources/` se añade automáticamente. **Si el fichero no está en `app/src/test/resources/cif/001.cif`, esas dos pruebas
fallan** con un `NullPointerException` (por el `!!`); las otras 39 no lo necesitan.

**De dónde salen los números esperados del fichero real** (87 átomos, 90 enlaces, 5 `loop_`, 68 etiquetas, 2282 valores): de un
tokenizador **independiente escrito en Python** en la fase de análisis, no del código Kotlin. Si ambos coinciden, es muy improbable que
compartan el mismo fallo.

### 10.2 Cómo ejecutarlas en Android Studio

1. Abre el proyecto y espera a que termine la sincronización de Gradle.
2. En la vista **Project → Android** se ven las pruebas bajo `app` → `java` → `dev.knowledge82.swiftyproteins (test)`. En la vista
   **Project → Project Files** están en `app/src/test/java/...`.
3. **Todas las pruebas**: clic derecho sobre la carpeta `cif` → **Run 'Tests in 'dev.knowledge82.swiftyproteins.cif''**.
4. **Un fichero**: clic derecho sobre `CifTokenizerTest.kt` → **Run 'CifTokenizerTest''**.
5. **Una prueba**: icono verde al lado de la función `@Test`.
6. Abajo se abre el panel **Run** con un árbol: ✔ verde por prueba, ✘ rojo con el mensaje y la línea si falla.

### 10.3 Cómo ejecutarlas desde la terminal (Gradle)

Desde la raíz del repositorio:

```bash
./gradlew :app:testDebugUnitTest                                       # todas las pruebas unitarias (variante debug)
./gradlew :app:testDebugUnitTest --tests "*CifTokenizerTest"           # solo un fichero
./gradlew :app:testDebugUnitTest --tests "*CifTokenizerTest.realLigand001"   # solo una prueba
./gradlew :app:testDebugUnitTest --rerun-tasks                         # forzar a repetir aunque no haya cambios
```

- Si nada cambió desde la última vez, Gradle responde `UP-TO-DATE` y **no vuelve a ejecutar**; para forzarlo, `--rerun-tasks` (o `clean`).
- Gradle **no imprime una lista** de pruebas aprobadas por defecto: si todo va bien, solo ves `BUILD SUCCESSFUL`. El detalle está
  en el informe HTML: `app/build/reports/tests/testDebugUnitTest/index.html` (ábrelo en el navegador) y en los XML de
  `app/build/test-results/testDebugUnitTest/`.
- `./gradlew test` ejecuta además la variante `release`; da lo mismo para estas pruebas, tarda más.

### 10.4 Cómo saber que las pruebas **de verdad** prueban algo

Un test que nunca puede fallar no prueba nada. Comprobación de 2 minutos (**prueba de mutación manual**):

1. En `CifTokenizer.kt`, línea 67, cambia `(j + 1 >= n || isWhitespace(text[j + 1]))` por `true`. Guarda. (Significa: «cualquier comilla
   cierra la cadena», que es justo el error que la regla de «seguida de espacio» evita.)
2. Ejecuta las pruebas. **Resultado real al hacer esta mutación: falla 1 prueba de 41**, `quoteOnlyClosesWhenFollowedByWhitespace`
   (la que prueba `'a'b'`). Las otras 40 siguen en verde. Dos lecciones: la red de seguridad **funciona** (el error se detecta), y
   **el fichero real 001 no ejerce este caso** (la mutación no hizo fallar sus pruebas), así que dependemos de la
   prueba sintética. Por eso se escribieron pruebas con casos que el fichero real no cubre.
3. **Deshaz el cambio** y vuelve a ejecutar: 41 verdes.
4. Repite con otra mutación para coger práctica, p. ej. en `CifTableReader.kt`, línea 53, cambia `values.size % columns.size != 0` por `false`:
   deberían fallar las pruebas de fila truncada.

### 10.5 Qué demuestran y qué **no**

**Demuestran**: que el tokenizador y el lector hacen lo que dice este documento con las entradas probadas, incluido un fichero
**real** de RCSB; que los 7 tipos de fallo se producen con la información esperada; que cada fallo **sale como `CifFailure`**.

**No demuestran**:

- Que funcione con **los 1243 ligandos**: solo se probó con el fichero 001 de texto real. La capa 3 se contrastará con el corpus
  completo (sección 11), pero ese corpus es una copia **binaria** del diccionario: contrasta la extracción, no la **ortografía exacta**
  del texto que sirve RCSB.
- Nada sobre red, interfaz ni 3D.
- Nada de rendimiento (un fichero de ≈ 300 átomos son decenas de miles de tokens; no se ha medido en el móvil).
- Las pruebas se ejecutaron **con `kotlinc` + JUnit en JVM** en el entorno donde se escribió el código, **no con el Gradle de este
  proyecto**. La primera ejecución en Android Studio es la verificación definitiva; si hay un error de compilación o una prueba
  roja, hay que contarlo tal cual.

---

## 11. Qué falta

| Pieza | Qué hará | Estado |
|-------|----------|--------|
| **Extractor** (capa 3) | Buscar columnas por nombre; `String` → `Float`; normalizar elementos; elegir **un juego de coordenadas** por ligando (D10: ideales si están completas, si no modelo, nunca mezclados); centrar en el centroide; nombres → índices; `value_order` → `BondOrder`; construir `Molecule` | Sin escribir |
| **`CifParser.parse(text)`** | Punto de entrada público: llama a las tres capas, **captura** `CifFormatException` y cualquier otro fallo previsible y devuelve éxito (`Molecule`) o fallo (`CifFailure`); límite de tamaño de entrada | Sin escribir |
| Nuevos `CifFailure` | `MissingColumn`, `BadNumber`, `NoAtoms`, `UnknownAtomInBond`, etc. | Sin escribir |
| Contraste con el corpus | Generar un `.cif` de texto por cada uno de los 1243 ligandos y comprobar que el parser extrae lo mismo que la copia binaria | Pendiente |
| Capa de red | Descarga, errores sin red / 404 / *timeout*, UNL | Fuera de este documento (documento 06, «Lista, búsqueda y red») |

---

## 12. Alternativas y por qué no se eligieron

| Alternativa | Qué es | Por qué no |
|-------------|--------|-----------|
| **Expresiones regulares** sobre todo el fichero | Una regex para cada línea de átomo | Las filas no se delimitan por líneas; las comillas con apóstrofos y los campos `;` rompen cualquier regex razonable. Es el camino típico a un parser que «funciona con 10 ficheros» |
| **`split` por espacios** | `texto.split(" ")` | Pierde `"O5'"` y los nombres con espacios entre comillas; no distingue comentarios |
| **Una biblioteca de CIF** (Java/Kotlin) | Dependencia externa | Añadiría una dependencia pesada y opaca para un subconjunto pequeño; además el objetivo de aprendizaje es **entender** el formato (D2, D9) |
| **Generador de parsers** (ANTLR, etc.) | Describir la gramática y generar código | Herramienta y dependencia nuevas para una gramática de 6 reglas |
| **Un solo bucle que haga todo** (sin capas) | Tokenizar y agrupar a la vez | Más corto, pero un fallo de comillas y un fallo de estructura se confunden y no se pueden probar por separado |
| **Parser «en flujo»** (leer del `InputStream` sin cargar el texto entero) | Menos memoria | Los ficheros pesan unos pocos KB; la complejidad no compensa |
| **Devolver `null` en lugar de excepciones/`CifFailure`** | Más sencillo | Pierde **por qué** falló; sin eso no se puede dar un mensaje útil ni diagnosticar |
| **Tokens como índices** (rangos del texto original en vez de `String`) | Menos asignaciones | Optimización prematura; no hay medida que la pida |

---

## 13. Trampas y límites conocidos

1. **Palabras clave no tratadas**: `save_`, `global_`, `stop_` (propias de otros usos de CIF) se tokenizan como `VALUE`. No aparecen
   en ficheros de ligandos; **no verificado** con el texto real servido por RCSB.
2. **Un segundo `data_`** corta la lectura sin aviso: se ignora lo que siga. Intencional.
3. **`trim` de campos `;`** quita todos los saltos de línea de los extremos, no solo uno. Irrelevante para nuestros datos.
4. **Valores con `_` inicial**: una palabra que empieza por `_` siempre es una etiqueta; el estándar no permite un valor sin comillas que
   empiece así, así que no es un problema.
5. **Sin límite de tamaño** en estas dos capas: si se les pasara un fichero enorme, lo procesarían. El límite se pondrá en `CifParser.parse`.
6. **`.` y `?` son valores normales aquí**: la capa 3 debe tratarlos (p. ej. una coordenada `?` es un dato ausente).
7. **Carpeta y `package` deben coincidir** (sección 1), y `001.cif` debe estar en `app/src/test/resources/cif/`.
8. **Las pruebas de rendimiento no existen**: el tiempo de parsear 256 átomos en el A33 no se ha medido.

---

## 14. Qué está verificado y qué no

| Afirmación | Estado |
|------------|--------|
| Los seis ficheros compilan y las **41 pruebas** pasan con `kotlinc` 2.4.21 + JUnit 4.13.2 en JVM | **Verificado** (en el entorno donde se escribió) |
| Tokenizador y lector dan los mismos números que un tokenizador independiente en Python sobre el fichero 001 (5 `loop_`, 68 etiquetas, 2282 valores, 87×18 y 90×7) | **Verificado** |
| La simulación de la sección 9 (tokens y tablas) | **Verificado**: salida real del código |
| Las pruebas pasan con el Gradle/AGP **de este proyecto** en Android Studio | **Sin verificar** hasta la primera ejecución |
| `app/src/test/resources` entra en el *classpath* de las pruebas unitarias con AGP 9.4.1 | **Esperado** (es la convención estándar de Gradle/Android), sin verificar en este proyecto |
| Cómo escribe el texto de RCSB los iones o ligandos sin enlaces (¿pares sueltos o `loop_`?) | **Sin verificar** (el corpus está en copia binaria) |
| Rendimiento en el dispositivo | **Sin medir** |

---

## 15. Qué recordar

1. **Tres capas, cada una con su trabajo**: la capa 1 conoce la sintaxis; la capa 2, la estructura; la capa 3 (pendiente), la química.
   Cada una se prueba sola.
2. **El significado de un carácter depende de dónde está**: `#` es comentario solo al principio de un token; `;` es campo de texto solo
   en la columna uno; una comilla cierra solo si va seguida de espacio.
3. **Las filas de un `loop_` se cortan contando valores**, nunca por saltos de línea.
4. **Las columnas se buscan por nombre**, sin distinguir mayúsculas, y la búsqueda devuelve `Int?`: «puede no existir» es parte del tipo.
5. **Dentro de las capas se lanza `CifFormatException(failure)`; en el borde se captura** y se convierte en un resultado. La app nunca ve la excepción.
6. **Cada rama del tokenizador avanza el cursor**: es lo que garantiza que no hay bucles infinitos con un fichero malo.
7. **`Molecule` se valida al construirse**: un enlace hacia un átomo inexistente es un fallo del parser y salta junto a su causa.
8. **Un `when` sobre un `enum`/`sealed` exhaustivo es una red de seguridad**: añadir un caso y olvidarse de tratarlo no compila.
9. **Probar de verdad**: si una prueba no puede fallar, no prueba nada (sección 10.4).
10. **Falta el extractor y `CifParser.parse`**; hasta que existan, nada de esta carpeta se usa desde la interfaz.
