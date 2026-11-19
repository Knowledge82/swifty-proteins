# 03a · Código del parser: tokenizador y lector de tablas (línea a línea)

> Estado: **ESCRITO**. Describe el código completo del parser: `Molecule.kt`, `CifToken.kt`, `CifFailure.kt`, `CifTokenizer.kt`,
> `CifTable.kt`, `CifTableReader.kt`, `CifExtractor.kt` y `CifParser.kt`, más sus cuatro ficheros de pruebas (88 pruebas) y la comprobación
> contra el corpus de 1242 ligandos. **No** incluye todavía la integración con la red ni con la interfaz (sección 14).
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
9. [`CifExtractor.kt`: capa 3, de las tablas a la molécula](#9-cifextractorkt-capa-3-de-las-tablas-a-la-molécula)
10. [`CifParser.kt`: la puerta de entrada](#10-cifparserkt-la-puerta-de-entrada)
11. [Simulación de extremo a extremo](#11-simulación-de-extremo-a-extremo)
12. [Las pruebas: qué hay, cómo se ejecutan, qué demuestran](#12-las-pruebas-qué-hay-cómo-se-ejecutan-qué-demuestran)
13. [Comprobación contra el corpus completo](#13-comprobación-contra-el-corpus-completo)
14. [Qué falta](#14-qué-falta)
15. [Alternativas y por qué no se eligieron](#15-alternativas-y-por-qué-no-se-eligieron)
16. [Trampas y límites conocidos](#16-trampas-y-límites-conocidos)
17. [Qué está verificado y qué no](#17-qué-está-verificado-y-qué-no)
18. [Qué recordar](#18-qué-recordar)

---

## 1. Mapa: qué fichero hace qué y dónde vive

Rutas relativas a la raíz del repositorio. El paquete base es `dev.knowledge82.swiftyproteins`, que en disco es la carpeta
`dev/knowledge82/swiftyproteins/`. **La carpeta tiene que coincidir con la línea `package` de cada fichero**; Kotlin lo tolera,
pero Android Studio protesta y es una fuente de confusión.

| Fichero | Ruta | Líneas | Capa | Para qué |
|---------|------|-------:|------|----------|
| `Molecule.kt` | `app/src/main/java/dev/knowledge82/swiftyproteins/molecule/` | 62 | Dominio | Lo que el visor 3D necesita: átomos, enlaces, molécula. No sabe nada de CIF. |
| `CifToken.kt` | `app/src/main/java/dev/knowledge82/swiftyproteins/cif/` | 27 | Capa 1 | Tipos `CifTokenType` y `CifToken`: la pieza en que se corta el texto. |
| `CifFailure.kt` | `.../cif/` | 71 | Todas | Los motivos de fallo (`CifFailure`, 16 variantes) y la excepción interna que los transporta. |
| `CifTokenizer.kt` | `.../cif/` | 97 | Capa 1 | Texto → lista de tokens. Conoce la **sintaxis** (comillas, comentarios, campos `;`). |
| `CifTable.kt` | `.../cif/` | 31 | Capa 2 | `CifTable` (columnas con nombre + filas) y `CifBlock` (nombre + tablas). |
| `CifTableReader.kt` | `.../cif/` | 82 | Capa 2 | Lista de tokens → `CifBlock`. Conoce la **estructura** (`loop_`, pares sueltos). |
| `CifExtractor.kt` | `.../cif/` | 189 | Capa 3 | `CifBlock` → `Molecule`. Conoce la **química**: qué columna es el nombre, el elemento, las coordenadas; qué juego de coordenadas elegir; centrado. |
| `CifParser.kt` | `.../cif/` | 38 | Puerta de entrada | `ParseResult` y `CifParser.parse(texto)`: lo **único** que usará el resto de la app. Captura las excepciones. |
| `CifTokenizerTest.kt` | `app/src/test/java/dev/knowledge82/swiftyproteins/cif/` | 217 | Pruebas | 21 pruebas de la capa 1. |
| `CifTableReaderTest.kt` | `.../cif/` (en `src/test`) | 217 | Pruebas | 20 pruebas de la capa 2. |
| `CifExtractorTest.kt` | `.../cif/` (en `src/test`) | 446 | Pruebas | 36 pruebas de la capa 3. |
| `CifParserTest.kt` | `.../cif/` (en `src/test`) | 135 | Pruebas | 11 pruebas de la puerta de entrada, incluidas las de robustez (truncado y ruido aleatorio). |
| `001.cif` | `app/src/test/resources/cif/` | 275 | Datos de prueba | El ligando real `001`, **sin modificar**. Lo leen varias pruebas. |

Flujo de datos completo:

```
 String (el .cif entero)
        │
        ▼
 CifParser.parse ──── (1) tamaño ≤ 2 000 000 caracteres ───────────────► si no: Failure(TooBig)
        │
        ▼
 CifTokenizer.tokenize ──────────►  List<CifToken>            capa 1, sintaxis
        │
        ▼
 CifTableReader.read ────────────►  CifBlock (tablas)          capa 2, estructura
        │
        ▼
 CifExtractor.extract ───────────►  Molecule                   capa 3, química
        │
        ▼
 ParseResult.Success(molecule)

 Cualquiera de las tres capas lanza CifFormatException(failure)  ──►  CifParser la captura  ──►  ParseResult.Failure(failure)
 Cualquier OTRA excepción                                          ──►  CifParser la captura  ──►  ParseResult.Failure(Unexpected(...))
```

Dependencias entre ficheros (una flecha significa «usa»): `CifParser` → `CifTokenizer`, `CifTableReader`, `CifExtractor`, `CifFailure`, `Molecule`;
`CifExtractor` → `CifTable`/`CifBlock`, `CifFailure`, `Molecule`; `CifTableReader` → `CifToken`, `CifTable`, `CifFailure`; `CifTokenizer` →
`CifToken`, `CifFailure`; `CifTable` y `Molecule` no usan nada. Por eso se pueden probar capa por capa. **El resto de la app solo debe tocar
`CifParser`, `ParseResult`, `CifFailure` y `Molecule`.**

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
| `x ?: throw ...` y `x ?: return null` | Si `x` es `null`, **lanza** o **sale de la función**. Es el «guard clause» de Kotlin: tras esa línea el compilador **sabe** que `x` ya no es `null`. | `CifExtractor` |
| `const val` | Constante conocida al compilar. Los números «mágicos» (`2_000`) se nombran así. `2_000` es `2000` (el `_` solo ayuda a leer). | `CifExtractor`, `CifParser` |
| `private class Point` | Una clase visible solo dentro del `object`. Un tipo de ayuda que nadie más debe conocer. | `CifExtractor` |
| `for ((index, row) in rows.withIndex())` | Recorre una lista con su posición; los paréntesis **desestructuran** el par (índice, elemento). | `CifExtractor` |
| `rows.indices.map { ... }` | `indices` es el rango `0 until size`; `map` convierte cada elemento y devuelve una **lista nueva**. | `CifExtractor` |
| `sumOf { ... }`, `maxOfOrNull { ... }` | Suma o máximo de lo que devuelve el bloque; `maxOfOrNull` devuelve `null` si la lista está vacía (por eso va con `?: 0.0`). | `CifExtractor` |
| `return` dentro de `map { ... }` | `map` es una función **inline**: un `return` en su bloque sale de la función **de fuera** (no solo del bloque). | `CifExtractor.readCompleteSet` |
| Función de extensión `fun CifTable.column(...)` | Añade un método a una clase existente **sin modificarla**; dentro, `this` es la tabla. | `CifExtractor` |
| `it in "0123456789+-.eE"` y `'A'..'Z'` | `Char in String` pregunta «¿está este carácter en el texto?»; `'A'..'Z'` es un **rango** de caracteres. | `CifExtractor` |
| `raw?.uppercase()` | `?.` (llamada segura): si `raw` es `null`, el resultado es `null` y no se llama. | `CifExtractor` |
| `try { ... } catch (e: X) { ... }` como **expresión** | `try` devuelve un valor: el del bloque, o el del `catch` que se ejecute. Se usa con `return try {`. | `CifParser` |

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
// CifFailure.kt · líneas 3–31
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
```

- **Línea 4** `sealed interface CifFailure`: una lista **cerrada** de motivos de fallo. Cada `data class`/`data object` de dentro es
  una variante. Ventaja sobre un `String` de error: el código que reacciona al fallo (la interfaz de usuario) puede hacer un `when`
  y el compilador **avisa si se añade un motivo nuevo y alguien olvida tratarlo**.
- Cada variante lleva solo los datos útiles: dónde (`line`), qué (`tag`, `value`).
- **Capa 1** (líneas 5–11): `UnterminatedQuote`, `UnterminatedTextField`. **Capa 2** (líneas 13–31): `NoDataBlock`, `LoopWithoutColumns`,
  `TruncatedRow`, `MissingValue`, `StrayValue`.

```kotlin
// CifFailure.kt · líneas 33–62
33  // ---- layer 3: extractor ------------------------------------------------------------------
34
35  /** A column we cannot live without is not in the file (or there is no atom table at all). */
36  data class MissingColumn(val tag: String) : CifFailure
37
38  /** The atom table exists but has no rows. */
39  data object NoAtoms : CifFailure
40
41  /** Neither the ideal nor the model coordinates are complete for every atom (see decision D10). */
42  data object NoCompleteCoordinates : CifFailure
43
44  /** The element of an atom is empty, `?`, `.` or not made of letters. */
45  data class BadElement(val atom: String, val element: String) : CifFailure
46
47  /** Two atoms of the same ligand have the same name, so a bond could not tell them apart. */
48  data class DuplicateAtomName(val name: String) : CifFailure
49
50  /** A bond names an atom that is not in the atom table. */
51  data class UnknownAtomInBond(val name: String) : CifFailure
52
53  /** A bond joins an atom with itself. */
54  data class BondToItself(val name: String) : CifFailure
55
56  // ---- entry point: CifParser ----------------------------------------------------------------
57
58  /** Something is bigger than the limit we accept: the text, the atoms or the bonds. */
59  data class TooBig(val what: String, val count: Int, val max: Int) : CifFailure
60
61  /** Safety net: an exception nobody expected. It means a bug in the parser, not a bad file. */
62  data class Unexpected(val description: String) : CifFailure
```

- **Capa 3** (líneas 33–54): `MissingColumn` (falta una columna imprescindible o no hay tabla de átomos), `NoAtoms`, `NoCompleteCoordinates`,
  `BadElement`, `DuplicateAtomName`, `UnknownAtomInBond`, `BondToItself`.
- **Puerta de entrada** (líneas 56–62): `TooBig` (el texto, los átomos o los enlaces superan el límite) y `Unexpected` (red de seguridad: una
  excepción que nadie esperaba; **significa un fallo del parser, no del fichero**).
- Dos variantes usan `data object` (`NoDataBlock`, `NoAtoms`, `NoCompleteCoordinates`) porque no necesitan datos: son un valor único.
- **Relación con el error de usuario**: de los cuatro errores de carga que exige la tarea (sin red, 404, fichero corrupto,
  *timeout*), **todo lo de este fichero es «fichero corrupto»**. Sin red, 404 y *timeout* se detectan antes, en la capa de red.
  La interfaz solo necesita un mensaje («Fichero corrupto») y el `failure` concreto va al registro.

```kotlin
// CifFailure.kt · líneas 65–71
65  /**
66   * The only exception the parser layers throw. It carries a typed [failure], not just a message.
67   *
68   * It is thrown INSIDE the parser's layers and caught at its public entry point, which turns it into a
69   * result value. The rest of the app never sees this exception.
70   */
71  class CifFormatException(val failure: CifFailure) : Exception(failure.toString())
```

- **Línea 71** `class CifFormatException(val failure: CifFailure) : Exception(failure.toString())`: la **única** excepción de las capas
  del parser. Hereda de `Exception` y lleva el `failure` tipado, además del mensaje de texto.
- **Por qué existe una excepción si D9 dice «sin excepciones»**: dentro de las capas, lanzar es la forma más sencilla de abandonar
  un análisis en cualquier profundidad (si el bucle del tokenizador detecta una comilla sin cerrar, no tiene un valor de retorno
  cómodo). La regla se cumple en el **borde**: el punto de entrada público (`CifParser.parse`, sección 10) captura
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

## 9. `CifExtractor.kt`: capa 3, de las tablas a la molécula

### 9.1 Idea general

Hasta aquí todo era **texto**: tokens, y tablas de celdas de texto. Esta capa es la única que sabe **qué significa** cada columna. Recibe
un `CifBlock` y produce una `Molecule` o lanza `CifFormatException`. Los pasos, en el orden en que el código los ejecuta:

1. Buscar la tabla de átomos **por el nombre de una columna** (`_chem_comp_atom.atom_id`).
2. Leer **nombre** y **elemento** de cada átomo; construir el mapa «nombre → posición».
3. Leer los **enlaces** y traducir los nombres a posiciones.
4. Elegir **un juego de coordenadas** para toda la molécula.
5. **Centrar** la molécula en su centroide y construir los `Atom`.
6. Construir la `Molecule`.

Política para datos dudosos: **estricta** (decisión D12). Ante la duda, `CifFormatException` con un motivo, nunca una molécula dibujada «a medias»
sin avisar.

### 9.2 Constantes

```kotlin
// CifExtractor.kt · líneas 17–33
17  object CifExtractor {
18
19      const val MAX_ATOMS = 2_000
20      const val MAX_BONDS = 5_000
21
22      // The real ligands have at most 256 atoms and a radius of 41 Å. A coordinate beyond this is garbage.
23      private const val MAX_ABS_COORDINATE = 10_000.0
24
25      // A covalent bond is 1-2.5 Å long. In the 1242 real ligands the longest correct one is below 3 Å, while
26      // five ligands (HEM, HEA, HEC, B12, AV2) have an IDEAL set with bonds of 8-14 Å. 5 Å separates both worlds.
27      private const val MAX_PLAUSIBLE_BOND_LENGTH = 5.0
28
29      private const val ATOM_ID = "_chem_comp_atom.atom_id"
30      private const val ELEMENT = "_chem_comp_atom.type_symbol"
31      private const val BOND_FIRST = "_chem_comp_bond.atom_id_1"
32      private const val BOND_SECOND = "_chem_comp_bond.atom_id_2"
33      private const val BOND_ORDER = "_chem_comp_bond.value_order"
```

- **Líneas 19–20** `MAX_ATOMS` y `MAX_BONDS`: topes de seguridad. El mayor ligando real tiene 256 átomos y 256 enlaces (en el corpus de la lista). Cada
  átomo y cada enlace serán nodos de la escena 3D, y una respuesta absurda de 100 000 átomos dejaría la aplicación sin memoria. Los topes están muy por
  encima de lo real y muy por debajo de lo peligroso. `2_000` es `2000`: el `_` solo ayuda a leer.
- **Líneas 22–23** `MAX_ABS_COORDINATE`: una coordenada de más de 10 000 Å es basura (el radio máximo real es 41 Å, la distancia máxima del centroide al
  origen 226 Å). Es `10_000.0` con `.0` porque es un `Double`.
- **Líneas 25–27** `MAX_PLAUSIBLE_BOND_LENGTH = 5.0`: el umbral de la decisión D13. Se explica en 9.7. El comentario guarda **de dónde sale** el número
  (los datos), porque una constante sin origen es la que dentro de seis meses nadie se atreve a tocar.
- **Líneas 29–33**: los nombres de columna como constantes. En minúsculas, porque `CifTable.indexOf` compara **sin distinguir mayúsculas**
  (sección 7). Una errata en una cadena suelta no la detecta el compilador; en una constante, se escribe **una vez**.

### 9.3 Los dos juegos de coordenadas y el tipo `Point`

```kotlin
// CifExtractor.kt · líneas 35–49
35  // Two candidate coordinate sets, x/y/z each. The order of this list IS the policy: first the ideal one.
36  private val COORDINATE_SETS = listOf(
37      listOf(
38          "_chem_comp_atom.pdbx_model_cartn_x_ideal",
39          "_chem_comp_atom.pdbx_model_cartn_y_ideal",
40          "_chem_comp_atom.pdbx_model_cartn_z_ideal",
41      ),
42      listOf(
43          "_chem_comp_atom.model_cartn_x",
44          "_chem_comp_atom.model_cartn_y",
45          "_chem_comp_atom.model_cartn_z",
46      ),
47  )
48
49  private class Point(val x: Double, val y: Double, val z: Double)
```

- **Líneas 36–47**: una lista de dos listas; cada una tiene los tres nombres de columna (x, y, z) de un juego. **El orden de esta lista ES la política:**
  primero las ideales, luego las de modelo. Para cambiar de preferencia bastaría con intercambiarlas (y una de las pruebas lo detectaría).
- **Línea 49** `private class Point(val x: Double, val y: Double, val z: Double)`: una posición. Es `private`: solo el extractor la conoce.
  Se usa `Double` (64 bits) **dentro** del extractor, para promediar sin pérdida de precisión, y se convierte a `Float` **solo al final**, al crear cada `Atom`.

### 9.4 `extract`: átomos

```kotlin
// CifExtractor.kt · líneas 51–74
51  /** @throws CifFormatException if the block does not describe a usable molecule. */
52  fun extract(block: CifBlock): Molecule {
53      // ---- atoms -------------------------------------------------------------------------
54      val atomTable = block.tableWith(ATOM_ID) ?: throw fail(CifFailure.MissingColumn(ATOM_ID))
55      val rows = atomTable.rows
56      if (rows.isEmpty()) throw fail(CifFailure.NoAtoms)
57      if (rows.size > MAX_ATOMS) throw fail(CifFailure.TooBig("atoms", rows.size, MAX_ATOMS))
58
59      val nameColumn = atomTable.column(ATOM_ID)
60      val elementColumn = atomTable.column(ELEMENT)
61
62      // Names and elements first: the bonds refer to atoms BY NAME and need this map.
63      val indexByName = HashMap<String, Int>()
64      val names = ArrayList<String>(rows.size)
65      val elements = ArrayList<String>(rows.size)
66      for ((index, row) in rows.withIndex()) {
67          val name = row[nameColumn]
68          val element = normalizeElement(row[elementColumn])
69              ?: throw fail(CifFailure.BadElement(name, row[elementColumn]))
70          // put() returns the previous value of that key: not null means the name was already taken.
71          if (indexByName.put(name, index) != null) throw fail(CifFailure.DuplicateAtomName(name))
72          names += name
73          elements += element
74      }
```

- **Línea 54**: `block.tableWith(ATOM_ID) ?: throw fail(...)`. `tableWith` devuelve `CifTable?` (puede ser `null`). El operador `?:` («y si es `null`…»)
  lanza `MissingColumn`. **A partir de esta línea `atomTable` es un `CifTable` a secas**: el compilador lo sabe.
- **Línea 55**: `rows` es la lista de filas de la tabla de átomos (cada fila, una lista de textos).
- **Línea 56**: tabla sin filas → `NoAtoms`. Una molécula sin átomos no se puede dibujar ni centrar (dividiríamos por cero).
- **Línea 57**: demasiados átomos → `TooBig`.
- **Líneas 59–60**: las posiciones de las **dos columnas obligatorias**. `column(...)` (sección 9.8, al final) devuelve un `Int` o lanza `MissingColumn` con el nombre exacto
  que falta. Se piden **antes** del bucle: si falta una columna, se falla una vez, no en cada fila.
- **Líneas 63–65**: el mapa nombre → posición y dos listas, una de nombres y otra de elementos. `ArrayList<String>(rows.size)` reserva ya el tamaño final.
- **Línea 66** `for ((index, row) in rows.withIndex())`: recorre las filas con su posición (0, 1, 2…). Los paréntesis desestructuran el par.
- **Línea 67**: `row[nameColumn]` es la celda de esa fila en la columna del nombre. **Aquí se usa la posición que dio `column`**; por eso es seguro (existe) y no depende de
  que el fichero ordene las columnas de una forma concreta.
- **Líneas 68–69**: el elemento se normaliza (9.8); si no puede ser un símbolo (`?`, `.`, `C1`, vacío) → `BadElement`, con el **nombre del átomo** y el valor
  malo, para poder encontrarlo en el fichero.
- **Líneas 70–71**: `indexByName.put(name, index)` devuelve el valor **anterior** de esa clave, o `null` si no había. Si **no** es `null`, el nombre ya estaba
  ocupado: `DuplicateAtomName`. Se comprueba y se inserta **en una sola operación**.
- **Líneas 72–73**: se guardan nombre y elemento; los `Atom` se crean más tarde porque todavía no se sabe qué coordenadas usar.

**Por qué el mapa va por nombre**: el fichero escribe los enlaces con **nombres** de átomo (`C1 O5'`). El mapa los traduce a posiciones **una vez**.

### 9.5 `extract`: enlaces, coordenadas y centrado

```kotlin
// CifExtractor.kt · líneas 76–101
 76      // A ligand with a single atom (an ion) has no bonds and may not have the table at all.
 77      val bondTable = block.tableWith(BOND_FIRST)
 78      val bonds = if (bondTable == null) emptyList() else readBonds(bondTable, indexByName)
 79
 80      // The bonds come BEFORE the coordinates because they are needed to judge which set is believable.
 81      val points = chooseCoordinates(atomTable, bonds) ?: throw fail(CifFailure.NoCompleteCoordinates)
 82
 83      // The centroid is the average position. Moving it to (0, 0, 0) puts the molecule in front of
 84      // the camera whatever the reference frame of the file was (it can be 226 Å away from the origin).
 85      val centerX = points.sumOf { it.x } / points.size
 86      val centerY = points.sumOf { it.y } / points.size
 87      val centerZ = points.sumOf { it.z } / points.size
 88
 89      val atoms = rows.indices.map { index ->
 90          val p = points[index]
 91          Atom(
 92              name = names[index],
 93              element = elements[index],
 94              x = (p.x - centerX).toFloat(),
 95              y = (p.y - centerY).toFloat(),
 96              z = (p.z - centerZ).toFloat(),
 97          )
 98      }
 99
100      return Molecule(block.name, atoms, bonds)
101  }
```

- **Líneas 77–78**: se busca la tabla de enlaces **por una de sus columnas**. Si **no existe** (un ion de un solo átomo puede no tenerla), la lista de enlaces
  es vacía y **no es un error**. Esto cubre los **39 ligandos de un solo átomo** del corpus. `emptyList()` es la lista vacía, de solo lectura.
- **Línea 81**: se elige el juego de coordenadas (9.7). Si no hay ninguno completo, `NoCompleteCoordinates`. **Los enlaces se leen antes** porque la comprobación de
  «creíble» necesita saber qué átomos están unidos.
- **Líneas 85–87**: el **centroide** es la posición media: suma de todas las x dividida por el número de átomos, y lo mismo para y y z.
  `sumOf { it.x }` suma el campo `x` de cada `Point`.
- **Por qué centrar**: los dos juegos de coordenadas viven en sistemas de referencia arbitrarios; el centroide de las ideales llega a estar a **226 Å del origen**
  (`OMO`). Sin centrar, la cámara apuntaría al vacío. Centrar es **trasladar** todos los puntos por el mismo vector: **no cambia ninguna distancia**.
- **Líneas 89–98**: se crea un `Atom` por posición. `rows.indices.map { index -> ... }` recorre `0 until rows.size` y devuelve la lista nueva. Cada coordenada es
  `(p.x - centerX).toFloat()`: se resta el centroide **en Double** y se convierte a `Float` al final. Los argumentos van **con nombre** (`name = ...`) para que
  nadie confunda `x` con `z`.
- **Línea 100**: `Molecule(block.name, atoms, bonds)`. Recuerda el `init` de `Molecule` (sección 3.4): comprueba que cada enlace apunta a átomos válidos. Aquí **no
  puede fallar**, porque `readBonds` ya validó cada nombre (9.6); si fallara, sería un fallo del propio parser y `CifParser` lo convertiría en `Unexpected`.

### 9.6 `readBonds`

```kotlin
// CifExtractor.kt · líneas 103–117
103  private fun readBonds(table: CifTable, indexByName: Map<String, Int>): List<Bond> {
104      if (table.rows.size > MAX_BONDS) throw fail(CifFailure.TooBig("bonds", table.rows.size, MAX_BONDS))
105      val firstColumn = table.column(BOND_FIRST)
106      val secondColumn = table.column(BOND_SECOND)
107      val orderColumn = table.indexOf(BOND_ORDER)   // optional: without it every bond is OTHER
108
109      return table.rows.map { row ->
110          val firstName = row[firstColumn]
111          val secondName = row[secondColumn]
112          val from = indexByName[firstName] ?: throw fail(CifFailure.UnknownAtomInBond(firstName))
113          val to = indexByName[secondName] ?: throw fail(CifFailure.UnknownAtomInBond(secondName))
114          if (from == to) throw fail(CifFailure.BondToItself(firstName))
115          Bond(from, to, bondOrderOf(orderColumn?.let { row[it] }))
116      }
117  }
```

- **Línea 104**: demasiadas filas → `TooBig`.
- **Líneas 105–106**: las dos columnas de átomos son **obligatorias** (si falta una → `MissingColumn`).
- **Línea 107**: la columna del orden de enlace es **opcional**: `indexOf` devuelve `Int?` y **se deja como nullable** a propósito. Sin ella, todos los enlaces
  serán `OTHER`, pero la molécula se dibuja.
- **Líneas 109–116** `table.rows.map { row -> ... }`: convierte cada fila en un `Bond`.
  - Líneas 112–113: `indexByName[firstName] ?: throw ...`. Un nombre que no está en el mapa → `UnknownAtomInBond` con el nombre. **Distingue mayúsculas**: `c1` no es `C1`
    (hay una prueba).
  - Línea 114: un enlace de un átomo consigo mismo → `BondToItself`. Sin esta comprobación saltaría el `require` de `Molecule` con una excepción de otro tipo.
  - Línea 115: `orderColumn?.let { row[it] }`: si hay columna, la celda; si no, `null`. `bondOrderOf` (9.8) lo traduce.

### 9.7 Elegir las coordenadas (decisiones D10 y D13)

Es la parte con más historia del fichero. Primero el código:

```kotlin
// CifExtractor.kt · líneas 119–136
119  /**
120   * Picks ONE coordinate set for the whole ligand (decisions D10 and D13). The candidates are tried in order
121   * (ideal, then model) and the first one that is COMPLETE (every atom has the three numbers) and
122   * BELIEVABLE (no bond longer than [MAX_PLAUSIBLE_BOND_LENGTH]) wins. If there is a complete set but none
123   * is believable, the first complete one is used anyway: a stray hydrogen is better than no molecule.
124   * Null if no set is complete.
125   *
126   * The two sets live in different reference frames, so mixing them atom by atom would scatter the molecule.
127   */
128  private fun chooseCoordinates(table: CifTable, bonds: List<Bond>): List<Point>? {
129      var firstComplete: List<Point>? = null
130      for (tags in COORDINATE_SETS) {
131          val points = readCompleteSet(table, tags) ?: continue
132          if (longestBond(points, bonds) <= MAX_PLAUSIBLE_BOND_LENGTH) return points
133          if (firstComplete == null) firstComplete = points
134      }
135      return firstComplete
136  }
```

La regla, en palabras: se prueban los juegos **en orden** (ideales, modelo). El primero que sea **completo** (todos los átomos tienen los tres números) y **creíble**
(ningún enlace de más de 5 Å) gana. Si hay juegos completos pero **ninguno** creíble, se usa **el primero completo** (un hidrógeno suelto es mejor que no mostrar la molécula).
Si ninguno es completo, `null` y de ahí sale `NoCompleteCoordinates`.

- **Línea 129**: `firstComplete` recuerda el primer juego completo, por si ninguno es creíble. Es un `var` con tipo `List<Point>?`.
- **Línea 131**: `readCompleteSet(...) ?: continue`: si ese juego no está completo, **pasa al siguiente** de la lista.
- **Línea 132**: si es creíble, **termina aquí** con ese juego.
- **Línea 133**: si no es creíble, pero es el primero completo que se ve, se guarda.
- **Línea 135**: fin del bucle sin éxito: se devuelve el primero completo (o `null`).

**Por qué UN juego entero y no átomo a átomo** (D10): las dos familias de coordenadas están en **sistemas de referencia distintos** (en `001`, a unos 30 Å). Mezclarlas
dentro de una molécula colocaría unos átomos en un sitio y otros a decenas de ångström. Hay una prueba específica (`theTwoSetsAreNeverMixed`).

**Por qué «creíble»** (D13): al medir los enlaces de todo el corpus aparecieron cinco ligandos (`HEM`, `HEC`, `HEA`, `B12`, `AV2`) cuyas **ideales** colocan algunos hidrógenos a
8–14 Å de su oxígeno, mientras las de modelo están bien (sección 13). Los enlaces correctos miden menos de 3 Å; los rotos, más de 8: el umbral de 5 Å va en medio.

```kotlin
// CifExtractor.kt · líneas 138–158
138  /** The x/y/z of every atom for one set, or null if a column is absent or ANY atom lacks a usable number. */
139  private fun readCompleteSet(table: CifTable, tags: List<String>): List<Point>? {
140      val xColumn = table.indexOf(tags[0]) ?: return null
141      val yColumn = table.indexOf(tags[1]) ?: return null
142      val zColumn = table.indexOf(tags[2]) ?: return null
143      return table.rows.map { row ->
144          // One unusable number in ONE atom and the whole set is discarded.
145          Point(
146              parseCoordinate(row[xColumn]) ?: return null,
147              parseCoordinate(row[yColumn]) ?: return null,
148              parseCoordinate(row[zColumn]) ?: return null,
149          )
150      }
151  }
152
153  private fun longestBond(points: List<Point>, bonds: List<Bond>): Double =
154      bonds.maxOfOrNull { bond ->
155          val a = points[bond.from]
156          val b = points[bond.to]
157          sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y) + (a.z - b.z) * (a.z - b.z))
158      } ?: 0.0   // no bonds (an ion): nothing to doubt
```

- **`readCompleteSet`** (líneas 139–151): busca las tres columnas del juego; si **alguna no existe**, el juego no está disponible y devuelve `null` (líneas 140–142).
  Después construye un `Point` por fila con `table.rows.map`. Si **cualquier** número de **cualquier** átomo es inutilizable, `?: return null` **abandona la función entera**:
  un `return` dentro de `map` sale de `readCompleteSet`, no solo del bloque (porque `map` es inline). Resultado: un solo `?` en un solo átomo descarta el juego **completo**.
- **`longestBond`** (líneas 153–158): para cada enlace, la distancia euclídea entre sus dos átomos
  (`sqrt((ax-bx)² + (ay-by)² + (az-bz)²)`), y se queda con la mayor. `maxOfOrNull` devuelve `null` si no hay enlaces, y entonces `?: 0.0`: **sin enlaces (un ion) no hay nada que
  dudar**, el juego es creíble.

### 9.8 Números, elementos, órdenes de enlace y ayudas

```kotlin
// CifExtractor.kt · líneas 160–167
160  /** A usable number, or null for `?`, `.`, text, NaN, infinities and absurd magnitudes. */
161  private fun parseCoordinate(text: String): Double? {
162      // toDoubleOrNull alone also accepts "NaN", "Infinity", "0x1p3" and "1.5f". Only these characters
163      // can be part of a plain decimal number such as -3.053 or 1e-3.
164      if (text.isEmpty() || !text.all { it in "0123456789+-.eE" }) return null
165      val value = text.toDoubleOrNull() ?: return null
166      return if (value.isFinite() && abs(value) <= MAX_ABS_COORDINATE) value else null
167  }
```

- **Línea 164**: `text.all { it in "0123456789+-.eE" }`: **todos** los caracteres deben ser de esa lista. Esto **descarta** `NaN`, `Infinity`, `0x1p3` (hexadecimal) y `1.5f` / `1.5d`,
  porque `toDoubleOrNull` de Kotlin **los acepta como números válidos** (usa la gramática de Java). Es una trampa real que una prueba cubre. `?` y `.` también caen aquí (la `?` no está en
  la lista) y un texto vacío (`''`).
- **Línea 165**: `toDoubleOrNull()` devuelve `null` si no es un número (`--1`, `1e`, `.`). No usa la configuración regional: `1,5` ya no habría pasado el filtro.
- **Línea 166**: el valor debe ser **finito** (`1e999` se convierte en infinito) y de módulo ≤ 10 000.

```kotlin
// CifExtractor.kt · líneas 169–182
169  /** "CL" -> "Cl", "fe" -> "Fe", "C" -> "C". Null if it cannot be an element symbol. */
170  private fun normalizeElement(raw: String): String? {
171      if (raw.length !in 1..3) return null
172      if (!raw.all { it in 'A'..'Z' || it in 'a'..'z' }) return null
173      return raw.substring(0, 1).uppercase() + raw.substring(1).lowercase()
174  }
175
176  private fun bondOrderOf(raw: String?): BondOrder = when (raw?.uppercase()) {
177      "SING" -> BondOrder.SINGLE
178      "DOUB" -> BondOrder.DOUBLE
179      "TRIP" -> BondOrder.TRIPLE
180      "AROM" -> BondOrder.AROMATIC
181      else -> BondOrder.OTHER   // "QUAD", "DELO", missing column... a bond is drawn anyway
182  }
```

- **`normalizeElement`**: acepta de 1 a 3 letras (`raw.length !in 1..3`, un rango) y solo letras ASCII. `"CL"` → `"C"` en mayúscula + `"l"` en minúscula = `"Cl"`; `"fe"` → `"Fe"`.
  Devuelve `null` para lo imposible. El corpus solo tiene símbolos de 1–2 letras en mayúsculas; aceptar 3 letras es holgura.
- **`bondOrderOf`**: un `when` con una **cadena** como sujeto: `"SING"` → `SINGLE`, etc. `raw?.uppercase()` primero pasa a mayúsculas (así `doub` también vale) y no falla si `raw` es `null`.
  Lo desconocido (`QUAD`, `DELO`, columna ausente) es `OTHER`: **no se falla por un adjetivo**; el enlace se dibuja igual. En el corpus solo hay `SING`, `DOUB` y `TRIP`.

```kotlin
// CifExtractor.kt · líneas 184–189
184      /** The position of a REQUIRED column, or a failure that names it. */
185      private fun CifTable.column(tag: String): Int =
186          indexOf(tag) ?: throw fail(CifFailure.MissingColumn(tag))
187
188      private fun fail(failure: CifFailure) = CifFormatException(failure)
189  }
```

- **Línea 185** `private fun CifTable.column(tag: String): Int`: una **función de extensión**: añade el método `column` a `CifTable` **sin tocar la clase**. Dentro, `indexOf(tag)` significa
  `this.indexOf(tag)`. Hace lo mismo que `indexOf`, pero para columnas **obligatorias**: si falta, lanza el fallo que **nombra la columna**.
- **Línea 188** `fail(...)`: construye la excepción. Se escribe `throw fail(x)` para que el `throw` se vea siempre en el sitio donde se lanza.

---

## 10. `CifParser.kt`: la puerta de entrada

```kotlin
// CifParser.kt · líneas 5–9
5  /** What [CifParser.parse] returns: a molecule, or the reason why there is none. Never an exception. */
6  sealed interface ParseResult {
7      data class Success(val molecule: Molecule) : ParseResult
8      data class Failure(val failure: CifFailure) : ParseResult
9  }
```

- **`ParseResult`**: el resultado, un `sealed interface` con **dos** variantes: `Success(molecule)` y `Failure(failure)`. Quien llama **no puede olvidar** el caso de fallo: si hace un `when`
  y se deja uno, **no compila**. (El nombre `Failure` está dentro de `ParseResult`, no choca con `CifFailure`.)

```kotlin
// CifParser.kt · líneas 16–38
16  object CifParser {
17
18      /** Real ligand files are well under 100 KB. A body above this limit is not a ligand. */
19      const val MAX_INPUT_CHARS = 2_000_000
20
21      /** Never throws because of its input: every problem comes back as [ParseResult.Failure]. */
22      fun parse(text: String): ParseResult {
23          if (text.length > MAX_INPUT_CHARS) {
24              return ParseResult.Failure(CifFailure.TooBig("characters", text.length, MAX_INPUT_CHARS))
25          }
26          return try {
27              val tokens = CifTokenizer.tokenize(text)         // layer 1: syntax
28              val block = CifTableReader.read(tokens)          // layer 2: structure
29              ParseResult.Success(CifExtractor.extract(block)) // layer 3: chemistry
30          } catch (e: CifFormatException) {
31              ParseResult.Failure(e.failure)                   // the expected way to fail
32          } catch (e: Exception) {
33              // A bug of ours (or something nobody foresaw). The app must not die in front of the evaluator:
34              // it becomes a failure we can log. Errors such as OutOfMemoryError are NOT caught on purpose.
35              ParseResult.Failure(CifFailure.Unexpected(e.toString()))
36          }
37      }
38  }
```

- **Líneas 18–19**: `MAX_INPUT_CHARS = 2_000_000`. Un fichero real pesa muy por debajo de 100 KB. Se comprueba **antes de hacer nada**: un cuerpo gigante no llega a tokenizarse.
- **Líneas 23–25**: si el texto es más largo, `Failure(TooBig(...))` y se vuelve. Fíjate en `>`: el texto de **exactamente** 2 000 000 se procesa (hay una prueba de cada lado).
- **Línea 26** `return try { ... }`: `try` es una **expresión**: su valor es el de la última línea del bloque, o el del `catch` que se ejecute.
- **Líneas 27–29**: las tres capas, **en orden**. Cada una se llama con el resultado de la anterior.
- **Líneas 30–31** `catch (e: CifFormatException)`: el **fallo esperado** (cualquiera de las tres capas). `e.failure` es el motivo tipado.
- **Líneas 32–36** `catch (e: Exception)`: la **red de seguridad**. Si hay un fallo en algún sitio (un `IndexOutOfBounds` por un error nuestro, el `require` de `Molecule`), la aplicación **no se cierra delante del evaluador**:
  se convierte en `Unexpected` con la descripción, para que quede en el registro.
- **El orden de los `catch` importa**: Kotlin los prueba **de arriba abajo** y ejecuta el primero que encaja. `CifFormatException` es una `Exception`, así que si el `catch (Exception)` fuera primero se
  «comería» todos los fallos esperados y todo saldría como `Unexpected`.
- **Qué NO se captura**: `Exception` no incluye los `Error` de la JVM (como `OutOfMemoryError`). No hay que ocultarlos: el tope de tamaño de la línea 23 es lo que evita llegar ahí.
- **Matiz**: si algún día `parse` se llama desde una corrutina y esta se cancela, `CancellationException` (que es una `Exception`) **no debe tragarse**. Hoy `parse` no es `suspend` y no puede lanzarla;
  si se vuelve `suspend`, habrá que relanzarla (documento 06).
- **Estado de esta red de seguridad**: **ninguna entrada válida ni inválida la activa por diseño**, así que ninguna prueba la ejercita directamente (sección 12.5). Sí se vio funcionar al introducir un fallo a
  propósito en un enlace durante la comprobación del corpus: el `IllegalArgumentException` de `Molecule` salió como `Failure(Unexpected(...))` y no como un cierre de la aplicación.

---

## 11. Simulación de extremo a extremo

El fichero de juguete (`mini.cif`; no forma parte del repositorio, es solo para este documento). Contiene un comentario, un campo de texto `;` de dos líneas, un átomo con comilla en el
nombre, un elemento en mayúsculas (`CL`), dos juegos de coordenadas **distintos** y dos `loop_`:

```
 1  data_MIN
 2  # fichero de juguete
 3  _chem_comp.id    MIN
 4  _chem_comp.name
 5  ;ETHANOL
 6  DE JUGUETE
 7  ;
 8  loop_
 9  _chem_comp_atom.atom_id
10  _chem_comp_atom.type_symbol
11  _chem_comp_atom.model_Cartn_x
12  _chem_comp_atom.model_Cartn_y
13  _chem_comp_atom.model_Cartn_z
14  _chem_comp_atom.pdbx_model_Cartn_x_ideal
15  _chem_comp_atom.pdbx_model_Cartn_y_ideal
16  _chem_comp_atom.pdbx_model_Cartn_z_ideal
17  "O5'" O  10.0 20.0 30.0   1.0 0.0 0.0
18  CL1   CL 11.5 20.0 30.0   3.0 0.0 0.0
19  loop_
20  _chem_comp_bond.atom_id_1
21  _chem_comp_bond.atom_id_2
22  _chem_comp_bond.value_order
23  CL1 "O5'" SING
```

### 11.1 Capa 1: lo que hace el tokenizador

| Qué hay en `text[i]` | Rama | Resultado |
|----------------------|------|-----------|
| `data_MIN` (línea 1) | 6 (palabra) | `DATA "MIN"` |
| `\n` | 1 | cuenta línea (ahora línea 2) |
| `# fichero de juguete` | 3 (comentario) | nada; salta hasta el `\n` |
| `_chem_comp.id`, `MIN` | 6 | `TAG`, `VALUE` |
| `_chem_comp.name` | 6 | `TAG "_chem_comp.name"` |
| `;` en columna uno (línea 5) | 4 (campo) | busca `\n;` → lo encuentra tras `DE JUGUETE`; contenido `ETHANOL\nDE JUGUETE`; **un** `VALUE`; suma 3 líneas |
| `loop_` | 6 | `LOOP "loop_"` |
| ocho etiquetas de átomo | 6 | ocho `TAG` |
| `"O5'"` (línea 17) | 5 (comillas) | abre `"`; el `'` interior va seguido de `"` → no cierra; el `"` final va seguido de espacio → cierra; `VALUE "O5'"` |
| `O`, `10.0`, ..., `0.0` | 6 | siete `VALUE` más |
| línea 18 | 6 | ocho `VALUE` (`CL1`, `CL`, `11.5`...) |
| `loop_` + tres etiquetas de enlace | 6 | `LOOP` y tres `TAG` |
| `CL1`, `"O5'"`, `SING` | 6, 5, 6 | tres `VALUE` |

Lista de tokens resultante (salida **real** del código; el número es la **línea del fichero** donde empieza cada token): **37 tokens**.

```
DATA   1  MIN
TAG    3  _chem_comp.id               VALUE  3  MIN
TAG    4  _chem_comp.name             VALUE  5  ETHANOL\nDE JUGUETE        (un solo valor, con salto dentro)
LOOP   8  loop_
TAG    9..16  _chem_comp_atom.atom_id, .type_symbol, .model_Cartn_x/y/z, .pdbx_model_Cartn_x/y/z_ideal   (8 etiquetas)
VALUE 17  O5'   O   10.0   20.0   30.0   1.0   0.0   0.0            (8 valores en la línea 17)
VALUE 18  CL1   CL  11.5   20.0   30.0   3.0   0.0   0.0            (8 valores en la línea 18)
LOOP  19  loop_
TAG   20..22  _chem_comp_bond.atom_id_1, .atom_id_2, .value_order
VALUE 23  CL1   O5'   SING
```

Observa que el comentario **no** produjo ningún token y que `O5'` perdió las comillas dobles pero **conservó** el apóstrofo.

### 11.2 Capa 2: lo que hace el lector

Posiciones en la lista de tokens: 0 = `DATA`; 1–2 = `_chem_comp.id` y su valor; 3–4 = `_chem_comp.name` y su valor; 5 = `LOOP`; 6–13 = ocho etiquetas; 14–29 = dieciséis valores; 30 = `LOOP`; 31–33 = tres etiquetas;
34–36 = tres valores.

| `i` | Token | Rama | Efecto |
|----:|-------|------|--------|
| 0 | `DATA MIN` | cabecera | `header` = ese token; `i = 1` |
| 1 | `TAG _chem_comp.id` | `TAG` | siguiente es `VALUE MIN` ✔; categoría `_chem_comp`; `pairs["_chem_comp"] = [(id, MIN)]`; `i = 3` |
| 3 | `TAG _chem_comp.name` | `TAG` | siguiente `VALUE` ✔; la categoría pasa a 2 pares; `i = 5` |
| 5 | `LOOP` | `LOOP` | columnas: 8 etiquetas; valores: 16; 16 % 8 = 0 ✔; `chunked(8)` → 2 filas; `i = 30` |
| 30 | `LOOP` | `LOOP` | columnas: 3; valores: 3; 3 % 3 = 0 ✔; 1 fila; `i = 37` (fin) |
| 37 | — | sale del `while` | convierte `pairs` en tablas |

Resultado (salida **real**):

| Tabla | Columnas | Filas |
|-------|---------:|------:|
| `_chem_comp_atom...` | 8 | 2 (`[O5', O, 10.0, 20.0, 30.0, 1.0, 0.0, 0.0]`, `[CL1, CL, 11.5, 20.0, 30.0, 3.0, 0.0, 0.0]`) |
| `_chem_comp_bond...` | 3 | 1 (`[CL1, O5', SING]`) |
| `_chem_comp...` (de los pares sueltos) | 2 | 1 (`[MIN, ETHANOL\nDE JUGUETE]`) |

La tabla de pares sale **la última**, aunque estaba la primera en el fichero. La capa 3 no lo nota porque busca con `block.tableWith("_chem_comp_atom.atom_id")`.

### 11.3 Capa 3: lo que hace el extractor

| Paso (línea de `CifExtractor.kt`) | Qué ocurre con el ejemplo |
|-----------------------------------|---------------------------|
| 54: tabla de átomos | `tableWith("_chem_comp_atom.atom_id")` → la tabla de 8 columnas y 2 filas |
| 56–57: tamaño | 2 filas: ni 0 ni más de 2000 |
| 59–60: columnas obligatorias | `atom_id` en la posición 0, `type_symbol` en la 1 |
| 66–74: nombres y elementos | fila 0: nombre `O5'`, elemento `O` → `"O"`; fila 1: nombre `CL1`, elemento `CL` → `"Cl"`; mapa `{O5' → 0, CL1 → 1}` |
| 77–78: enlaces | `readBonds`: fila `[CL1, O5', SING]` → `from = 1`, `to = 0`, `SING` → `SINGLE` → `Bond(1, 0, SINGLE)` |
| 81: coordenadas, 1.er candidato (ideales) | completas: `(1,0,0)` y `(3,0,0)`. Enlace más largo: 2,0 Å ≤ 5 → **creíble** → **gana**; las de modelo (10, 20, 30 y 11,5, 20, 30) no se miran |
| 85–87: centroide | x: (1 + 3) / 2 = 2; y: 0; z: 0 |
| 89–98: átomos | `O5'`: x = 1 − 2 = **−1**; `CL1`: x = 3 − 2 = **+1** |
| 100: molécula | `Molecule(id = "MIN", ...)` |

Resultado (salida **real** de `CifParser.parse`):

```
Success(molecule=Molecule(id=MIN,
        atoms=[Atom(name=O5', element=O,  x=-1.0, y=0.0, z=0.0),
               Atom(name=CL1, element=Cl, x=1.0,  y=0.0, z=0.0)],
        bonds=[Bond(from=1, to=0, order=SINGLE)]))
```

Si las ideales fueran `(1,0,0)` y `(30,0,0)` (un enlace de 29 Å, como en `HEM`), la línea 132 las declararía **no creíbles**, se guardarían como `firstComplete` y el bucle probaría las de modelo: `(10,20,30)` y
`(11.5,20,30)`, enlace de 1,5 Å → **creíbles → ganan**. Cubierto por la prueba `anIdealSetWithAnAbsurdBondFallsBackToModel`.

---

## 12. Las pruebas: qué hay, cómo se ejecutan, qué demuestran

### 12.1 Qué hay

**88 pruebas unitarias**, repartidas en cuatro ficheros de `app/src/test/java/dev/knowledge82/swiftyproteins/cif/`:

| Fichero | Pruebas | Qué cubre |
|---------|--------:|-----------|
| `CifTokenizerTest.kt` | 21 | lo básico (vacío, etiqueta+valor, `loop_`, mayúsculas); comillas (`"O5'"`, cierre solo con espacio, vacías, sin cerrar); comentarios; campos `;` (multilínea, con línea propia, fuera de columna uno, recuento de líneas, sin cerrar); finales de línea Windows; BOM; **el fichero real 001** |
| `CifTableReaderTest.kt` | 20 | `loop_` y filas por conteo; pares sueltos y categorías; búsqueda de columnas por nombre sin distinguir mayúsculas ni orden; solo el primer bloque; entradas malas (vacía, página HTML, fila truncada, `loop_` sin columnas, etiqueta sin valor, valor sin etiqueta); **el fichero real 001** |
| `CifExtractorTest.kt` | 36 | molécula pequeña con su enlace; id del bloque; orden de columnas; nombre con comilla; normalización de elementos y elementos imposibles; **elección de coordenadas** (ideales; modelo si falta una ideal; **nunca mezclar**; sin columnas ideales; ninguno completo; **ideales absurdas → modelo**; sin juego creíble → primer completo; enlace largo pero posible; sin enlaces; ideal absurda frente a modelo incompleta); números que no son números (`NaN`, `Infinity`, `1.5f`, `0x10`, `1e999`…); centrado; ion como pares sueltos; sin tabla de enlaces o con tabla vacía; órdenes de enlace; fallos (sin tabla de átomos, sin columna de elemento, sin filas, nombres duplicados, enlace a átomo desconocido, mayúsculas en nombres, enlace consigo mismo, columna de enlace ausente); límites de átomos; **el fichero real 001** (87 átomos, 90 enlaces, ideales elegidas, centrado, **todas** las longitudes de enlace entre 0,8 y 2,3 Å) |
| `CifParserTest.kt` | 11 | 001 de texto a molécula; BOM + finales Windows dan la **misma** molécula; vacío; página HTML; sin átomos; límite de tamaño (a un lado y a otro); los fallos de las capas llegan por la puerta; **truncado del fichero real cada 7 caracteres** (nunca lanza); **3000 ficheros dañados al azar** (semilla fija) y **3000 textos de ruido**: ninguno lanza ni cae en `Unexpected` |

Hay además un fichero de plantilla de Android Studio, `ExampleUnitTest.kt` (comprueba que `2 + 2 == 4`). No aporta nada y se puede borrar; no estorba.

Cada prueba es una función marcada con `@Test`. El patrón es siempre el mismo: **preparar una entrada, ejecutar, comparar con lo esperado** (`assertEquals(esperado, obtenido)`).
Para los fallos se usa `assertThrows(CifFormatException::class.java) { ... }`, que exige que el bloque lance esa excepción, y luego se comprueba el `failure` concreto.

**Cómo se construyen las entradas en `CifExtractorTest`.** Una función auxiliar `ligand(atoms, bonds, columns, name)` escribe el texto de un ligando pequeño con una fila por átomo. Así cada prueba
muestra **solo lo que importa** (dos filas, un `?`) y no un fichero de 100 líneas.

**El fichero de datos reales.** Varias pruebas leen `001.cif` con `javaClass.getResourceAsStream("/cif/001.cif")!!`. Eso busca `cif/001.cif` en el *classpath* de las pruebas; la carpeta
`app/src/test/resources/` se añade automáticamente. **Si el fichero no está en `app/src/test/resources/cif/001.cif`, esas pruebas fallan** con un `NullPointerException` (por el `!!`).

**Pruebas de robustez** (en `CifParserTest`): los criterios son aquí distintos. No se comprueba **qué** devuelve, sino que **nunca lanza** y que, además, **la red de seguridad no es la que salva**:
si el resultado es un fallo, no puede ser `Unexpected` (eso significaría un fallo escondido en una capa). Se usa `java.util.Random(42)` con **semilla fija**: el «azar» es el mismo en cada ejecución, así
que una prueba que falla se puede reproducir.

### 12.2 Cómo ejecutarlas en Android Studio

1. Abre el proyecto y espera a que termine la sincronización de Gradle.
2. En la vista **Project → Android** se ven las pruebas bajo `app` → `java` → `dev.knowledge82.swiftyproteins (test)`. En la vista **Project → Project Files** están en `app/src/test/java/...`.
3. **Todas las pruebas**: clic derecho sobre la carpeta `cif` → **Run 'Tests in 'dev.knowledge82.swiftyproteins.cif''**.
4. **Un fichero**: clic derecho sobre el fichero → **Run 'CifExtractorTest''**.
5. **Una prueba**: icono verde al lado de la función `@Test`.
6. Abajo se abre el panel **Run** con un árbol: ✔ verde por prueba, ✘ rojo con el mensaje y la línea si falla.

### 12.3 Cómo ejecutarlas desde la terminal (Gradle)

Desde la raíz del repositorio:

```bash
./gradlew :app:testDebugUnitTest                                       # todas las pruebas unitarias (variante debug)
./gradlew :app:testDebugUnitTest --tests "*CifExtractorTest"           # solo un fichero
./gradlew :app:testDebugUnitTest --tests "*CifExtractorTest.realLigand001"   # solo una prueba
./gradlew :app:testDebugUnitTest --rerun-tasks                         # forzar a repetir aunque no haya cambios
```

- Si nada cambió desde la última vez, Gradle responde `UP-TO-DATE` y **no vuelve a ejecutar**; para forzarlo, `--rerun-tasks` (o `clean`).
- Gradle **no imprime una lista** de pruebas aprobadas por defecto: si todo va bien, solo ves `BUILD SUCCESSFUL`. El detalle está en el informe HTML:
  `app/build/reports/tests/testDebugUnitTest/index.html` (ábrelo en el navegador) y en los XML de `app/build/test-results/testDebugUnitTest/`.
- `./gradlew test` ejecuta además la variante `release`; da lo mismo para estas pruebas, tarda más.

### 12.4 Cómo saber que las pruebas **de verdad** prueban algo (prueba de mutación manual)

Un test que nunca puede fallar no prueba nada. La técnica: **estropear el código a propósito** y comprobar que alguna prueba se pone en rojo. Averías que se probaron en `CifExtractor.kt` y
`CifParser.kt` **con el código final**, y qué pruebas las detectaron (resultado real):

| Avería introducida | Pruebas que fallan |
|--------------------|--------------------|
| Probar primero las coordenadas de modelo (líneas 36–47) | **6** pruebas, entre ellas `idealCoordinatesAreUsedWhenAllAtomsHaveThem`, `aLongButPossibleBondDoesNotDiscreditTheIdealSet` y `realLigand001` |
| No restar el centroide en x (línea 94) | **4**, entre ellas `theMoleculeIsCenteredOnItsCentroid` y `realLigand001` |
| Permitir nombres duplicados (línea 71) | `duplicateAtomNamesFail` |
| `DOUB` → `SINGLE` (línea 178) | `smallMoleculeWithItsBond`, `bondOrdersAreTranslated` |
| Quitar el filtro de caracteres de los números (línea 164) | `valuesThatAreNotPlainNumbersCountAsMissing` |
| Permitir un enlace de un átomo consigo mismo (línea 114) | `aBondFromAnAtomToItselfFails` |
| No poner en minúscula el resto del elemento (línea 173) | `elementsAreNormalizedToFirstCapitalThenLowerCase`, `aSingleAtomWrittenAsLooseTagValuePairs` |
| Si falta un número, usar 0.0 en vez de descartar el juego (línea 146) | **3**: `valuesThatAreNotPlainNumbersCountAsMissing`, `noCompleteSetIsAFailure`, `anAbsurdButCompleteIdealSetBeatsAnIncompleteModelSet` |
| Desactivar la comprobación de «creíble» (línea 132) | `anIdealSetWithAnAbsurdBondFallsBackToModel` |
| Umbral de 5 Å → 50 Å (línea 27) | `anIdealSetWithAnAbsurdBondFallsBackToModel` |
| **Quitar el `catch (Exception)` de `CifParser` (línea 32)** | **ninguna**: **la avería sobrevive** (ver 12.5) |

Y en el tokenizador (ya en la versión anterior): hacer que **cualquier** comilla cierre la cadena (línea 67) hace fallar **una** prueba de 41, `quoteOnlyClosesWhenFollowedByWhitespace`: el fichero
real `001` **no** ejerce ese caso, y por eso existen las pruebas sintéticas.

**Ejercicio para ti** (2 minutos): haz una de esas averías en tu copia, ejecuta las pruebas, comprueba que se pone roja y **deshaz el cambio**. La más didáctica es cambiar
`MAX_PLAUSIBLE_BOND_LENGTH` de `5.0` a `50.0`.

### 12.5 Qué demuestran y qué **no**

**Demuestran**: que cada capa hace lo que dice este documento con las entradas probadas, incluido un fichero **real** de RCSB; que los fallos se producen con la información esperada y salen como
`CifFailure`; que `CifParser.parse` **no lanza** con textos truncados, dañados o de ruido.

**No demuestran**:

- Que funcione con **los 1243 ligandos descargados de RCSB**: con ficheros de texto reales solo se probó `001`. Para el resto, la sección 13 compara con una copia **binaria** del diccionario, y eso comprueba
  la **extracción**, no la **grafía exacta** del texto que sirve RCSB.
- **La red de seguridad `catch (Exception)`**: ninguna prueba la ejercita (la avería «quitarla» sobrevive). Es una red por definición para lo que no se ha previsto; no hay entrada que la active a propósito. Se
  considera correcta por inspección y por haberla visto actuar una vez, no por una prueba automática.
- Nada de red, interfaz ni 3D.
- Nada de rendimiento **en el móvil**: en la JVM del entorno de redacción, los 1242 ligandos del corpus se procesan en menos de un segundo (arranque de la JVM incluido), pero **no se ha medido en el A33**.
- Las pruebas se ejecutaron **con `kotlinc` + JUnit en JVM** en el entorno donde se escribió el código; **tu ejecución en Android Studio es la verificación definitiva**.

---

## 13. Comprobación contra el corpus completo

### 13.1 Qué se hizo y por qué

Las pruebas unitarias usan **un** fichero real y varios inventados. Para mirar los **1242 ligandos** de `ligands.txt` presentes en el diccionario (no se pudo descargar de RCSB desde el entorno de redacción):

1. Se tomó la copia **binaria** del diccionario de componentes (`biotite` de PyPI), ya validada contra el `001` real.
2. Un script (`tools/corpus-check/gen_corpus.py`) **escribió un `.cif` de texto por ligando** imitando el formato del fichero real: las 18 columnas de átomos y las 7 de enlaces en el mismo orden, nombres con apóstrofo
   entre comillas dobles, `?` donde falta un dato, tres decimales.
3. **Se comprobó que el generador es fiel**: para `001`, las 87 filas de átomos y las 90 de enlaces coinciden **celda a celda** con el fichero real de RCSB en todas las columnas que el generador sabe rellenar.
4. Se generaron **dos variantes**: en la **A** todo va en `loop_`; en la **B** las categorías de una sola fila (los iones) van como pares `etiqueta valor`. Así se cubren las dos maneras posibles de escribir un ion,
   que **no se ha podido verificar** cuál usa RCSB.
5. `CifParser.parse` se ejecutó sobre los 2 × 1242 ficheros (`Corpus.kt`).
6. El resultado se comparó con una **segunda implementación independiente escrita en Python** (otra elección del juego de coordenadas, otro centrado, otro mapeo de nombres a posiciones): mismo número de átomos y
   enlaces, mismos nombres, elementos, posiciones y órdenes.

### 13.2 Resultado

| Medida | Variante A | Variante B |
|--------|-----------:|-----------:|
| Ligandos | 1242 | 1242 |
| Parseados con éxito | **1242** | **1242** |
| Diferencias con la implementación independiente | **0** | **0** |
| Átomos comparados | 53 241 | 53 241 |
| Enlaces comparados | 54 527 | 54 527 |
| Diferencia máxima de coordenadas | 2·10⁻⁶ Å | 2·10⁻⁶ Å |

Juegos de coordenadas elegidos: **1220** ligandos con las ideales y **22** con las de modelo (17 con ideales incompletas y 5 con ideales rotas).
Tiempo: unos 0,8 s para los 1242 en la JVM del entorno de redacción, arranque incluido.

### 13.3 La comprobación se comprobó (y falló una vez)

Una comprobación que siempre dice «todo bien» no vale nada. Se rompió el parser a propósito de tres maneras y se miró si la comparación lo notaba:

| Avería | Resultado de la comparación |
|--------|-----------------------------|
| `TRIP` leído como `DOUBLE` | 27 ligandos con diferencias |
| Un enlace apunta al átomo equivocado | 1122 ligandos con diferencias |
| Coordenadas de modelo antes que ideales | **0 diferencias** (¡no lo notó!) → **fallo del comparador** |

El tercer caso reveló que **el comparador calculaba la diferencia de coordenadas pero no la contaba como problema**. Se corrigió (umbral 10⁻³ Å) y, repetida la prueba, la misma avería dio 1135 ligandos
con diferencias. Después se repitieron las dos variantes con el parser bueno: 0 diferencias.

### 13.4 El hallazgo: cinco ligandos con las ideales rotas

Como comprobación **adicional e independiente de la segunda implementación** se midió la longitud de **todos los enlaces** de lo que devolvía el parser. La primera medición dio **9 enlaces de 8 a 14 Å**,
todos O–H, en `HEM`, `HEC`, `HEA`, `B12` y `AV2`. No era un fallo del parser (ambas implementaciones coincidían): estaba **en los datos**, en las coordenadas ideales. Con las de modelo, esos mismos enlaces
miden unos 0,95 Å. De ahí la decisión D13 (sección 9.7). Después: **54 527 enlaces entre 0,86 y 2,65 Å**, mediana 1,31 Å.

### 13.5 Qué no demuestra

- El texto lo escribe **nuestro generador**, no RCSB. Una diferencia en la grafía real (otro tipo de comilla, un ion de otra forma, una categoría ausente) **no se vería aquí**. Se cierra con una descarga real
  de la lista (documento 03, sección 10.2).
- Las dos implementaciones las ha escrito la **misma persona**: un malentendido compartido del formato pasaría desapercibido. Lo mitiga que los datos de partida son independientes del código (la copia
  binaria) y que el generador reproduce el fichero real.
- `UNL` no está en el diccionario: no hay nada que comparar; la respuesta del servidor para `UNL.cif` **no se ha podido comprobar**.

Cómo repetirlo: `tools/corpus-check/README.md`.

---

## 14. Qué falta

| Pieza | Qué hará | Estado |
|-------|----------|--------|
| **Capa de red** | Descarga de `https://files.rcsb.org/ligands/view/{ID}.cif`; los tres errores de red (sin conexión, 404, *timeout*); **leer la respuesta entera y comparar con `Content-Length`** (el parser no puede detectar un corte justo en el límite de una fila, 16.5); caso `UNL` | Sin escribir (documento 06) |
| **Hilo** | Llamar a `CifParser.parse` fuera del hilo principal (`Dispatchers.Default`) | Sin escribir (documento 06) |
| **Mensajes de error** | Traducir `ParseResult.Failure` a «Fichero corrupto» + registro del motivo | Sin escribir (documento 06) |
| **Escena 3D con `Molecule`** | Sustituir el `MoleculeScene.kt` con 7 átomos fijos del spike por una escena que reciba una `Molecule` (colores CPK, esferas, cilindros) | Sin escribir (documento 07) |
| **Descarga real de la lista** | Comprobar el parser con los ficheros de texto reales de RCSB (ion, categoría de enlaces ausente, `UNL`) | Pendiente |
| **Rendimiento en el A33** | Medir el tiempo de `parse` con el ligando mayor (256 átomos) y de la escena con ~768 nodos | Pendiente |

---

## 15. Alternativas y por qué no se eligieron

| Alternativa | Qué es | Por qué no |
|-------------|--------|-----------|
| **Expresiones regulares** sobre todo el fichero | Una regex para cada línea de átomo | Las filas no se delimitan por líneas; las comillas con apóstrofos y los campos `;` rompen cualquier regex razonable. Es el camino típico a un parser que «funciona con 10 ficheros» |
| **`split` por espacios** | `texto.split(" ")` | Pierde `"O5'"` y los nombres con espacios entre comillas; no distingue comentarios |
| **Una biblioteca de CIF** (Java/Kotlin) | Dependencia externa | Añadiría una dependencia pesada y opaca para un subconjunto pequeño; además el objetivo de aprendizaje es **entender** el formato (D2, D9) |
| **Generador de parsers** (ANTLR, etc.) | Describir la gramática y generar código | Herramienta y dependencia nuevas para una gramática de 6 reglas |
| **Un solo bucle que haga todo** (sin capas) | Tokenizar y agrupar a la vez | Más corto, pero un fallo de comillas y un fallo de estructura se confunden y no se pueden probar por separado |
| **Parser «en flujo»** (leer del `InputStream` sin cargar el texto entero) | Menos memoria | Los ficheros pesan unos pocos KB; la complejidad no compensa |
| **Devolver `null` en lugar de excepciones/`CifFailure`** | Más sencillo | Pierde **por qué** falló; sin eso no se puede dar un mensaje útil ni diagnosticar |
| **`Result<Molecule>` de Kotlin** en lugar de `ParseResult` | Tipo estándar | Su fallo es un `Throwable` cualquiera, sin tipo cerrado: la interfaz no podría hacer un `when` exhaustivo sobre los motivos |
| **Política tolerante** con enlaces rotos (descartar el enlace y seguir) | Dibujar algo siempre | Con el corpus real no aporta nada y dibuja **en silencio** una molécula incompleta (D12) |
| **Mezclar coordenadas átomo a átomo** | Ideales y, donde falten, las de modelo | Sistemas de referencia distintos: átomos a decenas de ångström (D10) |
| **Usar siempre las de modelo** | Evitar el problema de las ideales rotas | 48 ligandos las tienen incompletas, y las ideales son las «limpias» en el resto (D13) |
| **Corregir a mano los 5 ligandos con ideales rotas** | Una lista de excepciones | Frágil; no generaliza a otros ligandos con el mismo defecto |
| **Tokens como rangos del texto original** en vez de `String` | Menos asignaciones | Optimización prematura; no hay medida que la pida |

---

## 16. Trampas y límites conocidos

1. **Palabras clave no tratadas**: `save_`, `global_`, `stop_` (propias de otros usos de CIF) se tokenizan como `VALUE`. No aparecen en ficheros de ligandos; **no verificado** con el texto real servido por RCSB.
2. **Un segundo `data_`** corta la lectura sin aviso: se ignora lo que siga. Intencional.
3. **`trim` de campos `;`** quita todos los saltos de línea de los extremos, no solo uno. Irrelevante para nuestros datos.
4. **Valores con `_` inicial**: una palabra que empieza por `_` siempre es una etiqueta; el estándar no permite un valor sin comillas que empiece así.
5. **`.` y `?` son valores normales en las capas 1 y 2**; la capa 3 los trata como «número no utilizable» (descartan el juego de coordenadas) y como elemento imposible (`BadElement`).
6. **Carpeta y `package` deben coincidir** (sección 1), y `001.cif` debe estar en `app/src/test/resources/cif/`.
7. **`toDoubleOrNull` acepta más de lo esperado** (`NaN`, `Infinity`, `0x1p3`, `1.5f`): por eso el filtro de caracteres de la línea 164.
8. **Un número CIF con incertidumbre, como `1.234(5)`**, **no** pasa el filtro de caracteres (los paréntesis no están) y se trata como «no utilizable». Los ligandos del corpus no los usan; si algún día los usaran,
   todos los juegos serían «incompletos» y saldría `NoCompleteCoordinates`.
9. **Descarga cortada justo entre dos filas de un `loop_`**: el parser **no puede notarlo**. Una tabla con menos filas es válida; la molécula sale con menos átomos o menos enlaces **con éxito**.
   Se detecta por una vía distinta: comparar con `Content-Length` en la capa de red. (Un corte **a mitad de una fila** sí da `TruncatedRow`.) Se probó añadir la regla «≥ 2 átomos ⇒ al menos 1 enlace»; se descartó:
   en el diccionario completo hay 3 ligandos con 2 o más átomos y 0 enlaces (ninguno en la lista), y solo cubriría una parte de los cortes.
10. **El máximo real de átomos es 256** (corpus de la lista); el diccionario completo llega a 440. Los topes del parser (2000 átomos) cubren ambos.
11. **Nombres de átomo sensibles a mayúsculas**: `c1` no es `C1`. En el corpus no hay ningún caso que lo necesite; la prueba `atomNamesAreCaseSensitive` lo fija.
12. **Las pruebas de rendimiento no existen**: el tiempo de parsear 256 átomos en el A33 no se ha medido.
13. **Dos implementaciones, un solo autor** (13.5): un malentendido compartido no se detectaría.

---

## 17. Qué está verificado y qué no

| Afirmación | Estado |
|------------|--------|
| Los ocho ficheros de código compilan y las **88 pruebas** pasan con `kotlinc` 2.4.21 + JUnit 4.13.2 en JVM | **Verificado** (en el entorno donde se escribió) |
| Las 41 pruebas de las dos primeras capas pasan en Android Studio con el Gradle/AGP del proyecto | **Verificado por el equipo** (19/11/2026). Las 47 nuevas (capa 3 y `CifParser`): **sin verificar** hasta la primera ejecución |
| Tokenizador y lector dan los mismos números que un tokenizador independiente en Python sobre el fichero 001 (5 `loop_`, 68 etiquetas, 2282 valores, 87×18 y 90×7) | **Verificado** |
| El parser completo coincide con una implementación independiente en los **1242 ligandos**, en dos formas de escribir el texto (53 241 átomos, 54 527 enlaces, diferencia máxima 2·10⁻⁶ Å) | **Verificado** |
| El generador de texto reproduce el `001` real celda a celda (87 + 90 filas) | **Verificado** |
| Los 54 527 enlaces tras el parser miden entre 0,86 y 2,65 Å | **Verificado** |
| Cinco ligandos con ideales rotas (`HEM`, `HEC`, `HEA`, `B12`, `AV2`) | **Verificado en la copia binaria**; en texto de RCSB, no |
| Las pruebas detectan 10 de 11 averías introducidas a propósito en el extractor y `CifParser`; la que no: quitar el `catch (Exception)` | **Verificado** |
| La comparación con el corpus detecta parsers rotos (tras corregirla; la primera versión no detectaba el cambio de juego de coordenadas) | **Verificado** |
| La simulación de la sección 11 (tokens, tablas y molécula) | **Verificado**: salida real del código |
| `app/src/test/resources` entra en el *classpath* de las pruebas con AGP 9.4.1 | **Verificado**: el equipo ejecutó las pruebas del fichero real `001` con éxito |
| Cómo escribe el texto de RCSB los iones o ligandos sin enlaces (¿pares sueltos o `loop_`?) | **Sin verificar** (se cubren las dos formas con los generadores) |
| Respuesta del servidor para `UNL` | **Sin verificar** |
| Rendimiento en el dispositivo | **Sin medir** |
| `CancellationException` y `parse` desde una corrutina | Razonado, no probado: hoy `parse` no es `suspend` |

---

## 18. Qué recordar

1. **Tres capas y una puerta**: sintaxis (tokenizador), estructura (lector de tablas), química (extractor) y `CifParser.parse`, lo único que usa el resto de la app. Cada capa se prueba sola.
2. **El significado de un carácter depende de dónde está**: `#` es comentario solo al principio de un token; `;` es campo de texto solo en la columna uno; una comilla cierra solo si va seguida de espacio.
3. **Las filas de un `loop_` se cortan contando valores**, nunca por saltos de línea.
4. **Las columnas se buscan por nombre**, sin distinguir mayúsculas, y la búsqueda devuelve `Int?`: «puede no existir» es parte del tipo.
5. **Dentro de las capas se lanza `CifFormatException(failure)`; en el borde se captura** y se convierte en `ParseResult.Failure`. El orden de los `catch` importa: lo específico primero.
6. **Cada rama del tokenizador avanza el cursor**: garantiza que no hay bucles infinitos con un fichero malo.
7. **`Molecule` se valida al construirse**: un enlace hacia un átomo inexistente es un fallo del parser y salta junto a su causa.
8. **Un `when` sobre un `enum`/`sealed` exhaustivo es una red de seguridad**: añadir un caso y olvidarse de tratarlo no compila.
9. **Un solo juego de coordenadas por molécula**, nunca mezclados; el primero **completo y creíble** (ideales, luego modelo); siempre **centrado en el centroide**.
10. **Un juego completo no es necesariamente bueno**: medir las longitudes de los enlaces encontró cinco ligandos con las ideales rotas (`HEM` entre ellos).
11. **Política estricta**: ante la duda, `Failure` con un motivo, nunca una molécula incompleta dibujada en silencio.
12. **`toDoubleOrNull` acepta `NaN`, `Infinity`, `0x10` y `1.5f`**: hay que filtrar los caracteres antes.
13. **Probar de verdad**: si una prueba no puede fallar, no prueba nada. Estropear el código a propósito (mutación) lo comprueba; y **también hay que probar los comprobadores** (el comparador del corpus tenía un fallo).
14. **El parser no puede notar un corte entre dos filas**: esa defensa es de la capa de red (`Content-Length`).
15. **Falta**: red, hilos, mensajes, escena con `Molecule`, descarga real y medir en el A33.
