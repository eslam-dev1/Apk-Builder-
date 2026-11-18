package com.example.data

import com.example.model.CodeIssueFix
import com.example.model.CodeIssueSeverity

/**
 * Deterministic static analyzer for generated Kotlin + Jetpack Compose code.
 * Detects syntax delimiter mismatches, unclosed strings, missing Compose imports,
 * duplicate composable functions, invalid Color hex literals, and Compose state issues.
 */
object GeneratedCodeAnalyzer {

    private data class DelimiterToken(
        val char: Char,
        val line: Int
    )

    fun analyze(code: String): List<CodeIssueFix> {
        if (code.isBlank()) {
            return listOf(
                CodeIssueFix(
                    title = "Empty Generated Code",
                    severity = CodeIssueSeverity.ERROR,
                    lineHint = "Line 1",
                    problem = "No Kotlin source code was produced for this project.",
                    suggestion = "Regenerate the project or add at least one screen tab so Compose screens can be exported."
                )
            )
        }

        val issues = mutableListOf<CodeIssueFix>()
        val sanitizedCode = stripCommentsAndStrings(code, issues)

        checkBalancedDelimiters(sanitizedCode, issues)
        checkPackageAndImports(code, issues)
        checkDuplicateComposableFunctions(sanitizedCode, issues)
        checkColorHexLiterals(sanitizedCode, issues)
        checkUnrememberedMutableState(sanitizedCode, issues)
        checkIconContentDescriptions(code, issues)

        return issues
    }

    /**
     * Replaces comments and string/char literal contents with spaces while preserving line breaks,
     * and detects unclosed double-quoted strings or block comments.
     */
    private fun stripCommentsAndStrings(
        code: String,
        issues: MutableList<CodeIssueFix>
    ): String {
        val sb = StringBuilder(code.length)
        val n = code.length
        var i = 0
        var line = 1

        while (i < n) {
            val ch = code[i]
            when {
                ch == '\n' -> {
                    sb.append('\n')
                    line++
                    i++
                }

                // Line comment // ...
                ch == '/' && i + 1 < n && code[i + 1] == '/' -> {
                    while (i < n && code[i] != '\n') {
                        sb.append(' ')
                        i++
                    }
                }

                // Block comment /* ... */
                ch == '/' && i + 1 < n && code[i + 1] == '*' -> {
                    val startLine = line
                    sb.append("  ")
                    i += 2
                    var closed = false
                    while (i < n) {
                        if (code[i] == '*' && i + 1 < n && code[i + 1] == '/') {
                            sb.append("  ")
                            i += 2
                            closed = true
                            break
                        }
                        if (code[i] == '\n') {
                            sb.append('\n')
                            line++
                        } else {
                            sb.append(' ')
                        }
                        i++
                    }
                    if (!closed) {
                        issues.add(
                            CodeIssueFix(
                                title = "Unclosed Block Comment",
                                severity = CodeIssueSeverity.ERROR,
                                lineHint = "Line $startLine",
                                problem = "A block comment starting with /* was never closed with */.",
                                suggestion = "Add a closing */ delimiter at the end of the comment block.",
                                fixedSnippet = "/* Comment text */"
                            )
                        )
                    }
                }

                // Triple-quoted raw string """ ... """
                ch == '"' && code.startsWith("\"\"\"", i) -> {
                    val startLine = line
                    sb.append("   ")
                    i += 3
                    var closed = false
                    while (i < n) {
                        if (code.startsWith("\"\"\"", i)) {
                            sb.append("   ")
                            i += 3
                            closed = true
                            break
                        }
                        if (code[i] == '\n') {
                            sb.append('\n')
                            line++
                        } else {
                            sb.append(' ')
                        }
                        i++
                    }
                    if (!closed) {
                        issues.add(
                            CodeIssueFix(
                                title = "Unclosed Raw String Literal",
                                severity = CodeIssueSeverity.ERROR,
                                lineHint = "Line $startLine",
                                problem = "A triple-quoted string starting with \"\"\" is missing its closing \"\"\".",
                                suggestion = "Close the raw string literal with \"\"\" before continuing Kotlin statements.",
                                fixedSnippet = "val text = \"\"\"\n    Content\n\"\"\".trimIndent()"
                            )
                        )
                    }
                }

                // Single-line double-quoted string "..."
                ch == '"' -> {
                    val startLine = line
                    sb.append(' ')
                    i++
                    var closed = false
                    while (i < n) {
                        val c = code[i]
                        if (c == '\n') {
                            break
                        }
                        if (c == '\\') {
                            sb.append(' ')
                            i++
                            if (i < n && code[i] != '\n') {
                                sb.append(' ')
                                i++
                            }
                            continue
                        }
                        sb.append(' ')
                        i++
                        if (c == '"') {
                            closed = true
                            break
                        }
                    }
                    if (!closed) {
                        issues.add(
                            CodeIssueFix(
                                title = "Unclosed String Literal",
                                severity = CodeIssueSeverity.ERROR,
                                lineHint = "Line $startLine",
                                problem = "A double-quoted string on line $startLine is missing its closing quotation mark (\").",
                                suggestion = "Add the closing \" or escape inner quotes using \\\".",
                                fixedSnippet = "Text(\"Sample Label\")"
                            )
                        )
                    }
                }

                // Char literal '...'
                ch == '\'' -> {
                    sb.append(' ')
                    i++
                    var chars = 0
                    while (i < n && chars < 8 && code[i] != '\n') {
                        val c = code[i]
                        if (c == '\\') {
                            sb.append(' ')
                            i++
                            if (i < n && code[i] != '\n') {
                                sb.append(' ')
                                i++
                            }
                            chars += 2
                            continue
                        }
                        sb.append(' ')
                        i++
                        chars++
                        if (c == '\'') break
                    }
                }

                else -> {
                    sb.append(ch)
                    i++
                }
            }
        }
        return sb.toString()
    }

    private fun checkBalancedDelimiters(
        sanitizedCode: String,
        issues: MutableList<CodeIssueFix>
    ) {
        val stack = ArrayDeque<DelimiterToken>()
        var line = 1

        for (ch in sanitizedCode) {
            if (ch == '\n') {
                line++
                continue
            }
            when (ch) {
                '{', '(', '[' -> stack.addLast(DelimiterToken(ch, line))
                '}', ')', ']' -> {
                    val expectedOpen = when (ch) {
                        '}' -> '{'
                        ')' -> '('
                        else -> '['
                    }
                    if (stack.isEmpty()) {
                        issues.add(
                            CodeIssueFix(
                                title = "Unexpected Closing '$ch'",
                                severity = CodeIssueSeverity.ERROR,
                                lineHint = "Line $line",
                                problem = "Found an extra '$ch' on line $line with no matching opening '$expectedOpen'.",
                                suggestion = "Remove the extra '$ch' or add the missing '$expectedOpen' earlier in the block."
                            )
                        )
                        return
                    }
                    val top = stack.removeLast()
                    if (top.char != expectedOpen) {
                        issues.add(
                            CodeIssueFix(
                                title = "Mismatched Delimiter '${top.char}' / '$ch'",
                                severity = CodeIssueSeverity.ERROR,
                                lineHint = "Line $line",
                                problem = "Opened '${top.char}' on line ${top.line}, but encountered '$ch' on line $line.",
                                suggestion = "Match '${top.char}' with its corresponding closing delimiter before closing '$ch'."
                            )
                        )
                        return
                    }
                }
            }
        }

        if (stack.isNotEmpty()) {
            val unclosed = stack.last()
            val expectedClose = when (unclosed.char) {
                '{' -> '}'
                '(' -> ')'
                else -> ']'
            }
            issues.add(
                CodeIssueFix(
                    title = "Unclosed '${unclosed.char}' Block",
                    severity = CodeIssueSeverity.ERROR,
                    lineHint = "Line ${unclosed.line}",
                    problem = "The '${unclosed.char}' opened on line ${unclosed.line} is never closed with '$expectedClose'.",
                    suggestion = "Add a matching '$expectedClose' at the end of the function or parameter list."
                )
            )
        }
    }

    private fun checkPackageAndImports(
        code: String,
        issues: MutableList<CodeIssueFix>
    ) {
        val hasPackage = code.lineSequence().any { it.trim().startsWith("package ") }
        if (!hasPackage) {
            issues.add(
                CodeIssueFix(
                    title = "Missing Package Declaration",
                    severity = CodeIssueSeverity.WARNING,
                    lineHint = "Line 1",
                    problem = "The file does not declare a package statement at the top.",
                    suggestion = "Add a package declaration on the first line so Android Studio places the file cleanly.",
                    fixedSnippet = "package com.example.generated"
                )
            )
        }

        if (code.contains("@Composable") &&
            !code.contains("androidx.compose.runtime")
        ) {
            issues.add(
                CodeIssueFix(
                    title = "Missing Compose Runtime Import",
                    severity = CodeIssueSeverity.ERROR,
                    lineHint = "Imports",
                    problem = "@Composable is used without importing androidx.compose.runtime.*.",
                    suggestion = "Add the Jetpack Compose runtime wildcard import at the top of the file.",
                    fixedSnippet = "import androidx.compose.runtime.*"
                )
            )
        }

        if (Regex("\\bColor\\s*\\(").containsMatchIn(code) &&
            !code.contains("androidx.compose.ui.graphics.Color")
        ) {
            issues.add(
                CodeIssueFix(
                    title = "Missing Compose Color Import",
                    severity = CodeIssueSeverity.ERROR,
                    lineHint = "Imports",
                    problem = "Color(...) is referenced without importing androidx.compose.ui.graphics.Color.",
                    suggestion = "Import Compose Color to avoid resolving android.graphics.Color by mistake.",
                    fixedSnippet = "import androidx.compose.ui.graphics.Color"
                )
            )
        }

        if (Regex("\\.\\s*dp\\b").containsMatchIn(code) &&
            !code.contains("androidx.compose.ui.unit.dp")
        ) {
            issues.add(
                CodeIssueFix(
                    title = "Missing .dp Extension Import",
                    severity = CodeIssueSeverity.ERROR,
                    lineHint = "Imports",
                    problem = "Dimension values use .dp without importing androidx.compose.ui.unit.dp.",
                    suggestion = "Add the Compose unit .dp extension import.",
                    fixedSnippet = "import androidx.compose.ui.unit.dp"
                )
            )
        }
    }

    private fun checkDuplicateComposableFunctions(
        sanitizedCode: String,
        issues: MutableList<CodeIssueFix>
    ) {
        val funRegex = Regex("""\bfun\s+([A-Za-z_][A-Za-z0-9_]*)\s*\(""")
        val seen = mutableMapOf<String, Int>()
        sanitizedCode.lines().forEachIndexed { idx, lineText ->
            val match = funRegex.find(lineText) ?: return@forEachIndexed
            val fnName = match.groupValues[1]
            val lineNum = idx + 1
            val firstLine = seen[fnName]
            if (firstLine != null) {
                issues.add(
                    CodeIssueFix(
                        title = "Duplicate Function '$fnName'",
                        severity = CodeIssueSeverity.ERROR,
                        lineHint = "Line $lineNum",
                        problem = "Function '$fnName' is declared more than once (first at line $firstLine, again at line $lineNum).",
                        suggestion = "Rename one of the functions to keep every screen composable identifier unique.",
                        fixedSnippet = "@Composable\nfun ${fnName}Tab(modifier: Modifier = Modifier) {\n    // ...\n}"
                    )
                )
            } else {
                seen[fnName] = lineNum
            }
        }
    }

    private fun checkColorHexLiterals(
        sanitizedCode: String,
        issues: MutableList<CodeIssueFix>
    ) {
        val hexRegex = Regex("""\bColor\s*\(\s*(0[xX][0-9A-Fa-f]+)\s*\)""")
        sanitizedCode.lines().forEachIndexed { idx, lineText ->
            hexRegex.findAll(lineText).forEach { match ->
                val literal = match.groupValues[1]
                val digits = literal.substring(2)
                if (digits.length != 8) {
                    issues.add(
                        CodeIssueFix(
                            title = "Incomplete Color ARGB Hex ($literal)",
                            severity = CodeIssueSeverity.WARNING,
                            lineHint = "Line ${idx + 1}",
                            problem = "Compose Color($literal) has ${digits.length} hex digits; missing the alpha channel (FF) can render transparent.",
                            suggestion = "Prefix 6-digit RGB colors with FF for full opacity (8 ARGB hex digits).",
                            fixedSnippet = "Color(0xFF${digits.takeLast(6).padStart(6, '0').uppercase()})"
                        )
                    )
                }
            }
        }
    }

    private fun checkUnrememberedMutableState(
        sanitizedCode: String,
        issues: MutableList<CodeIssueFix>
    ) {
        val stateRegex = Regex("""\bmutableStateOf\s*\(""")
        val lines = sanitizedCode.lines()
        lines.forEachIndexed { idx, lineText ->
            val prevLine = if (idx > 0) lines[idx - 1] else ""
            if (stateRegex.containsMatchIn(lineText) &&
                !lineText.contains("remember") &&
                !prevLine.contains("remember")
            ) {
                issues.add(
                    CodeIssueFix(
                        title = "State Lost on Recomposition",
                        severity = CodeIssueSeverity.WARNING,
                        lineHint = "Line ${idx + 1}",
                        problem = "mutableStateOf(...) on line ${idx + 1} is not wrapped in remember { ... }, which resets state on every recomposition.",
                        suggestion = "Wrap mutableStateOf inside remember { ... } inside @Composable functions.",
                        fixedSnippet = "var state by remember { mutableStateOf(\"\") }"
                    )
                )
            }
        }
    }

    private fun checkIconContentDescriptions(
        code: String,
        issues: MutableList<CodeIssueFix>
    ) {
        val iconCallRegex = Regex("""\b(?:Icon|Image)\s*\(""")
        var match = iconCallRegex.find(code)
        while (match != null) {
            val openParenIdx = match.range.last
            val callBody = extractBalancedParenContent(code, openParenIdx)
            if (callBody != null && !callBody.contains("contentDescription")) {
                val lineNum = code.substring(0, match.range.first).count { it == '\n' } + 1
                issues.add(
                    CodeIssueFix(
                        title = "Missing Accessibility contentDescription",
                        severity = CodeIssueSeverity.INFO,
                        lineHint = "Line $lineNum",
                        problem = "An Icon or Image call at line $lineNum omits the contentDescription parameter.",
                        suggestion = "Pass a descriptive label or null (if decorative) for TalkBack compatibility.",
                        fixedSnippet = "Icon(\n    imageVector = Icons.Default.Info,\n    contentDescription = \"Details\"\n)"
                    )
                )
                break // Report once concisely rather than repeating
            }
            match = iconCallRegex.find(code, match.range.last + 1)
        }
    }

    private fun extractBalancedParenContent(code: String, openParenIdx: Int): String? {
        var depth = 0
        var i = openParenIdx
        while (i < code.length) {
            when (code[i]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) {
                        return code.substring(openParenIdx + 1, i)
                    }
                }
            }
            i++
        }
        return null
    }
}
