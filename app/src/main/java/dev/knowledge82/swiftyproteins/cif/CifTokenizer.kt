package dev.knowledge82.swiftyproteins.cif

/**
 * Layer 1 of the parser: cuts the text of a CIF file into tokens. It knows the SYNTAX of CIF
 * (quotes, comments, `;` text fields) and nothing about chemistry.
 *
 * Pure Kotlin, no `android.*`: it runs in plain JVM unit tests.
 */
object CifTokenizer {

    /** @throws CifFormatException if a quote or a `;` text field is never closed. */
    fun tokenize(rawText: String): List<CifToken> {
        // A UTF-8 file may start with an invisible BOM character. It is not part of the data.
        val text = rawText.removePrefix("\uFEFF")
        val n = text.length
        val tokens = ArrayList<CifToken>()

        var i = 0       // position of the next character to read
        var line = 1    // current line, for error messages

        while (i < n) {
            val c = text[i]
            when {
                // --- 1. line break: only counts lines -------------------------------------
                c == '\n' -> {
                    line++
                    i++
                }

                // --- 2. other whitespace: skipped (the '\r' of Windows line endings is here) -----
                c == ' ' || c == '\t' || c == '\r' -> i++

                // --- 3. comment: from '#' to the end of the line ----------------------------------
                // We are at the START of a token (whitespace was skipped), which is the only place
                // where '#' starts a comment. In the middle of a word, e.g. "C1#", it never gets here.
                c == '#' -> {
                    while (i < n && text[i] != '\n') i++
                }

                // --- 4. text field: ';' in COLUMN ONE, up to a line that starts with ';' --------
                c == ';' && (i == 0 || text[i - 1] == '\n') -> {
                    val startLine = line
                    // Look for the closing ";": a line break immediately followed by ';'.
                    var newline = text.indexOf('\n', i)
                    while (newline != -1 && !(newline + 1 < n && text[newline + 1] == ';')) {
                        newline = text.indexOf('\n', newline + 1)
                    }
                    if (newline == -1) {
                        throw CifFormatException(CifFailure.UnterminatedTextField(startLine))
                    }
                    // Normalize "\r\n" to "\n" so a file gives the same value with Windows or Unix line endings.
                    val body = text.substring(i + 1, newline).replace("\r\n", "\n").trim('\r', '\n')
                    tokens += CifToken(CifTokenType.VALUE, body, startLine)
                    line += text.substring(i, newline + 1).count { it == '\n' }
                    i = newline + 2   // skip the line break and the closing ';'
                }

                // --- 5. quoted string: 'text' or "text" -----------------------------------------
                // The closing quote only counts if it is followed by whitespace. That is why
                // "O5'" works: the ' inside is followed by a " and not by a space.
                c == '\'' || c == '"' -> {
                    var j = i + 1
                    while (true) {
                        if (j >= n || text[j] == '\n') {
                            throw CifFormatException(CifFailure.UnterminatedQuote(line))
                        }
                        if (text[j] == c && (j + 1 >= n || isWhitespace(text[j + 1]))) break
                        j++
                    }
                    tokens += CifToken(CifTokenType.VALUE, text.substring(i + 1, j), line)
                    i = j + 1
                }

                // --- 6. any other word: up to the next whitespace ----------------------------------
                else -> {
                    var j = i
                    while (j < n && !isWhitespace(text[j])) j++
                    val word = text.substring(i, j)
                    tokens += when {
                        word.equals("loop_", ignoreCase = true) ->
                            CifToken(CifTokenType.LOOP, word, line)
                        word.startsWith("data_", ignoreCase = true) ->
                            CifToken(CifTokenType.DATA, word.substring(5), line)
                        word[0] == '_' ->
                            CifToken(CifTokenType.TAG, word, line)
                        else ->
                            CifToken(CifTokenType.VALUE, word, line)
                    }
                    i = j
                }
            }
        }
        return tokens
    }

    private fun isWhitespace(c: Char) = c == ' ' || c == '\t' || c == '\r' || c == '\n'
}
