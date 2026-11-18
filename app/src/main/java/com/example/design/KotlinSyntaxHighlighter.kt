package com.example.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

data class KotlinSyntaxTheme(
    val background: Color,
    val headerBackground: Color,
    val border: Color,
    val lineNumber: Color,
    val defaultText: Color,
    val keyword: Color,
    val type: Color,
    val functionCall: Color,
    val namedParameter: Color,
    val string: Color,
    val stringInterpolation: Color,
    val numberOrLiteral: Color,
    val constant: Color,
    val annotation: Color,
    val comment: Color,
    val punctuation: Color
) {
    companion object {
        val Dark = KotlinSyntaxTheme(
            background = Color(0xFF090D16),
            headerBackground = Color(0xFF111827),
            border = Color(0xFF23304A),
            lineNumber = Color(0xFF64748B),
            defaultText = Color(0xFFE2E8F0),
            keyword = Color(0xFFC084FC),
            type = Color(0xFF38BDF8),
            functionCall = Color(0xFF60A5FA),
            namedParameter = Color(0xFF93C5FD),
            string = Color(0xFF86EFAC),
            stringInterpolation = Color(0xFFFDE047),
            numberOrLiteral = Color(0xFFFB923C),
            constant = Color(0xFFF472B6),
            annotation = Color(0xFFFBBF24),
            comment = Color(0xFF75859E),
            punctuation = Color(0xFF94A3B8)
        )

        val Light = KotlinSyntaxTheme(
            background = Color(0xFFF8FAFC),
            headerBackground = Color(0xFFEFF4FB),
            border = Color(0xFFCBD5E1),
            lineNumber = Color(0xFF94A3B8),
            defaultText = Color(0xFF0F172A),
            keyword = Color(0xFF7C3AED),
            type = Color(0xFF0284C7),
            functionCall = Color(0xFF2563EB),
            namedParameter = Color(0xFF0369A1),
            string = Color(0xFF15803D),
            stringInterpolation = Color(0xFFB45309),
            numberOrLiteral = Color(0xFFC2410C),
            constant = Color(0xFFBE185D),
            annotation = Color(0xFFD97706),
            comment = Color(0xFF64748B),
            punctuation = Color(0xFF475569)
        )
    }
}

object KotlinSyntaxHighlighter {

    private val KOTLIN_KEYWORDS = setOf(
        "package", "import", "class", "interface", "object", "data", "sealed",
        "enum", "annotation", "fun", "val", "var", "const", "lateinit",
        "override", "open", "abstract", "final", "private", "protected",
        "public", "internal", "companion", "inline", "noinline", "crossinline",
        "reified", "suspend", "operator", "infix", "tailrec", "vararg",
        "return", "if", "else", "when", "for", "while", "do", "break",
        "continue", "try", "catch", "finally", "throw", "in", "out", "is",
        "as", "by", "get", "set", "this", "super", "it", "init", "constructor",
        "typealias", "where"
    )

    private val KOTLIN_LITERALS = setOf("true", "false", "null")

    private val PUNCTUATION_CHARS = setOf(
        '{', '}', '(', ')', '[', ']', '.', ',', ':', ';', '=', '+', '-', '*',
        '/', '%', '<', '>', '!', '&', '|', '?'
    )

    fun highlight(
        code: String,
        theme: KotlinSyntaxTheme = KotlinSyntaxTheme.Dark
    ): AnnotatedString {
        if (code.isEmpty()) return AnnotatedString("")

        val keywordStyle = SpanStyle(color = theme.keyword, fontWeight = FontWeight.SemiBold)
        val typeStyle = SpanStyle(color = theme.type, fontWeight = FontWeight.Medium)
        val functionStyle = SpanStyle(color = theme.functionCall)
        val namedParamStyle = SpanStyle(color = theme.namedParameter)
        val stringStyle = SpanStyle(color = theme.string)
        val interpolationStyle = SpanStyle(color = theme.stringInterpolation, fontWeight = FontWeight.Medium)
        val numberStyle = SpanStyle(color = theme.numberOrLiteral)
        val constantStyle = SpanStyle(color = theme.constant, fontWeight = FontWeight.Medium)
        val annotationStyle = SpanStyle(color = theme.annotation, fontWeight = FontWeight.Medium)
        val commentStyle = SpanStyle(color = theme.comment, fontStyle = FontStyle.Italic)
        val punctuationStyle = SpanStyle(color = theme.punctuation)

        return buildAnnotatedString {
            append(code)

            val n = code.length
            var i = 0
            while (i < n) {
                val ch = code[i]
                when {
                    ch.isWhitespace() -> {
                        i++
                    }

                    // Line comment: // ...
                    ch == '/' && i + 1 < n && code[i + 1] == '/' -> {
                        val nl = code.indexOf('\n', i + 2)
                        val end = if (nl >= 0) nl else n
                        addStyle(commentStyle, i, end)
                        i = end
                    }

                    // Block / KDoc comment: /* ... */
                    ch == '/' && i + 1 < n && code[i + 1] == '*' -> {
                        val close = code.indexOf("*/", i + 2)
                        val end = if (close >= 0) close + 2 else n
                        addStyle(commentStyle, i, end)
                        i = end
                    }

                    // Triple-quoted raw string: """ ... """
                    ch == '"' && code.startsWith("\"\"\"", i) -> {
                        val close = code.indexOf("\"\"\"", i + 3)
                        val end = if (close >= 0) close + 3 else n
                        addStyle(stringStyle, i, end)
                        val contentEnd = if (close >= 0) close else end
                        highlightStringInterpolations(code, i + 3, contentEnd, interpolationStyle, this)
                        i = end
                    }

                    // Standard double-quoted string: "..."
                    ch == '"' -> {
                        var j = i + 1
                        var end = n
                        while (j < n) {
                            val c = code[j]
                            if (c == '\\') {
                                j = (j + 2).coerceAtMost(n)
                            } else if (c == '"') {
                                end = j + 1
                                break
                            } else if (c == '\n') {
                                end = j
                                break
                            } else {
                                j++
                            }
                        }
                        addStyle(stringStyle, i, end)
                        val contentEnd = if (end > i + 1 && code[end - 1] == '"') end - 1 else end
                        highlightStringInterpolations(code, i + 1, contentEnd, interpolationStyle, this)
                        i = end
                    }

                    // Char literal: 'a', '\n'
                    ch == '\'' -> {
                        var j = i + 1
                        var closed = false
                        while (j < n && j - i <= 10 && code[j] != '\n') {
                            if (code[j] == '\\') {
                                j = (j + 2).coerceAtMost(n)
                            } else if (code[j] == '\'') {
                                closed = true
                                j++
                                break
                            } else {
                                j++
                            }
                        }
                        if (closed) {
                            addStyle(stringStyle, i, j)
                            i = j
                        } else {
                            i++
                        }
                    }

                    // Annotation: @Composable, @OptIn, ...
                    ch == '@' && i + 1 < n && (code[i + 1].isLetter() || code[i + 1] == '_') -> {
                        var j = i + 2
                        while (j < n && isIdentPart(code[j])) {
                            j++
                        }
                        addStyle(annotationStyle, i, j)
                        i = j
                    }

                    // Numeric literals: 0xFF..., 0b1010, 12.5f, 16, 100L
                    ch.isDigit() -> {
                        val end = scanNumberEnd(code, i)
                        addStyle(numberStyle, i, end)
                        i = end
                    }

                    // Identifiers, keywords, types, functions, constants, named params
                    isIdentStart(ch) -> {
                        var j = i + 1
                        while (j < n && isIdentPart(code[j])) {
                            j++
                        }
                        val word = code.substring(i, j)
                        when {
                            word in KOTLIN_KEYWORDS -> {
                                addStyle(keywordStyle, i, j)
                            }
                            word in KOTLIN_LITERALS -> {
                                addStyle(numberStyle, i, j)
                            }
                            word[0].isUpperCase() -> {
                                val isAllCapsConst = word.length > 1 &&
                                    word.any { it.isLetter() } &&
                                    word.none { it.isLowerCase() }
                                addStyle(if (isAllCapsConst) constantStyle else typeStyle, i, j)
                            }
                            else -> {
                                val nextIdx = skipHorizontalSpaces(code, j)
                                when {
                                    nextIdx < n && (code[nextIdx] == '(' || code[nextIdx] == '{') -> {
                                        addStyle(functionStyle, i, j)
                                    }
                                    nextIdx < n && code[nextIdx] == '=' &&
                                        (nextIdx + 1 >= n || (code[nextIdx + 1] != '=' && code[nextIdx + 1] != '>')) -> {
                                        addStyle(namedParamStyle, i, j)
                                    }
                                }
                            }
                        }
                        i = j
                    }

                    ch in PUNCTUATION_CHARS -> {
                        addStyle(punctuationStyle, i, i + 1)
                        i++
                    }

                    else -> {
                        i++
                    }
                }
            }
        }
    }

    private fun highlightStringInterpolations(
        code: String,
        start: Int,
        end: Int,
        style: SpanStyle,
        builder: AnnotatedString.Builder
    ) {
        var idx = start
        while (idx < end) {
            val ch = code[idx]
            if (ch == '\\') {
                idx += 2
                continue
            }
            if (ch == '$' && idx + 1 < end) {
                val next = code[idx + 1]
                if (next == '{') {
                    var depth = 1
                    var k = idx + 2
                    while (k < end && depth > 0) {
                        when (code[k]) {
                            '{' -> depth++
                            '}' -> depth--
                        }
                        k++
                    }
                    builder.addStyle(style, idx, k.coerceAtMost(end))
                    idx = k
                    continue
                } else if (isIdentStart(next)) {
                    var k = idx + 2
                    while (k < end && isIdentPart(code[k])) {
                        k++
                    }
                    builder.addStyle(style, idx, k)
                    idx = k
                    continue
                }
            }
            idx++
        }
    }

    private fun scanNumberEnd(code: String, start: Int): Int {
        val n = code.length
        var j = start
        if (code[j] == '0' && j + 1 < n && (code[j + 1] == 'x' || code[j + 1] == 'X')) {
            j += 2
            while (j < n && (code[j] in '0'..'9' || code[j] in 'a'..'f' || code[j] in 'A'..'F' || code[j] == '_')) {
                j++
            }
        } else if (code[j] == '0' && j + 1 < n && (code[j + 1] == 'b' || code[j + 1] == 'B')) {
            j += 2
            while (j < n && (code[j] == '0' || code[j] == '1' || code[j] == '_')) {
                j++
            }
        } else {
            while (j < n && (code[j].isDigit() || code[j] == '_')) {
                j++
            }
            if (j + 1 < n && code[j] == '.' && code[j + 1].isDigit()) {
                j++
                while (j < n && (code[j].isDigit() || code[j] == '_')) {
                    j++
                }
            }
            if (j < n && (code[j] == 'e' || code[j] == 'E')) {
                var exp = j + 1
                if (exp < n && (code[exp] == '+' || code[exp] == '-')) exp++
                if (exp < n && code[exp].isDigit()) {
                    j = exp + 1
                    while (j < n && (code[j].isDigit() || code[j] == '_')) {
                        j++
                    }
                }
            }
        }
        // Optional numeric suffix (L, f, F, d, D, u, U, uL)
        while (j < n && code[j] in charArrayOf('L', 'l', 'f', 'F', 'd', 'D', 'u', 'U')) {
            j++
        }
        return j
    }

    private fun skipHorizontalSpaces(code: String, start: Int): Int {
        var idx = start
        while (idx < code.length && (code[idx] == ' ' || code[idx] == '\t')) {
            idx++
        }
        return idx
    }

    private fun isIdentStart(ch: Char): Boolean = ch.isLetter() || ch == '_'
    private fun isIdentPart(ch: Char): Boolean = ch.isLetterOrDigit() || ch == '_'
}
