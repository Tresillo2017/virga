package com.tomasps.virga.md3

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Material 3 audit rules read from the source. Each test is one audit finding. Comments are stripped before a scan,
 * so a rule cannot be met by writing its keyword in a comment.
 */
class Md3SourceRulesTest {

    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }

    private val mainSources: List<File> by lazy {
        root.walkTopDown()
            .onEnter { it.name != "build" && !it.name.startsWith(".") }
            .filter { it.isFile && it.extension == "kt" && it.invariantPath().contains("/src/main/") }
            .toList()
    }

    private fun File.invariantPath(): String = path.replace(File.separatorChar, '/')

    private fun File.code(): String = stripComments(readText())

    private fun source(relative: String): String = File(root, relative).code()

    private fun isThemeOrBrand(file: File): Boolean {
        val path = file.invariantPath()
        return path.contains("/core/ui/theme/") || path.contains("/core/ui/brand/")
    }

    private fun offenders(predicate: (File, String) -> List<String>): List<String> =
        mainSources.flatMap { file -> predicate(file, file.code()).map { "${file.relativeTo(root)}: $it" } }

    private fun assertNone(rule: String, found: List<String>) {
        assertTrue("$rule\n" + found.joinToString("\n"), found.isEmpty())
    }

    @Test
    fun `shapes come from the theme, not RoundedCornerShape literals in screens`() {
        val found = offenders { file, code ->
            if (isThemeOrBrand(file)) emptyList() else lines(code, Regex("""\bRoundedCornerShape\("""))
        }
        assertNone("Use MaterialTheme.shapes (or a shape token from core:ui theme)", found)
    }

    @Test
    fun `indeterminate loading uses the expressive LoadingIndicator, not CircularProgressIndicator`() {
        val found = offenders { _, code -> lines(code, Regex("""\bCircularProgressIndicator\b""")) }
        assertNone("Use LoadingIndicator / ContainedLoadingIndicator", found)
    }

    @Test
    fun `text weight and size come from the type scale outside the theme`() {
        val found = offenders { file, code ->
            if (isThemeOrBrand(file)) emptyList() else lines(code, Regex("""\b(fontWeight|fontSize)\s*="""))
        }
        assertNone("Use a MaterialTheme.typography role (or an emphasized style)", found)
    }

    @Test
    fun `every clickable modifier states its semantic role`() {
        val found = offenders { _, code ->
            callArguments(code, Regex("""\.clickable\s*[({]""")).filterNot { Regex("""\brole\s*=""").containsMatchIn(it) }
                .map { it.lineSequence().first().take(80) }
        }
        assertNone("Pass role = Role.Button (or the right Role) to clickable", found)
    }

    @Test
    fun `every top app bar has a scroll behavior wired to nested scroll`() {
        val bar = Regex("""\b(TopAppBar|CenterAlignedTopAppBar|MediumTopAppBar|LargeTopAppBar|MediumFlexibleTopAppBar|LargeFlexibleTopAppBar)\(""")
        val found = offenders { _, code ->
            val calls = callArguments(code, bar)
            val missing = calls.filterNot { it.contains("scrollBehavior") }.map { "no scrollBehavior: " + it.take(60) }
            val unwired = if (calls.isNotEmpty() && !code.contains("nestedScroll(")) listOf("no nestedScroll") else emptyList()
            missing + unwired
        }
        assertNone("Give every top app bar a scrollBehavior and connect it with Modifier.nestedScroll", found)
    }

    @Test
    fun `grouped cards use segmented shapes with gaps, not hairline dividers`() {
        val code = source("core/ui/src/main/kotlin/com/tomasps/virga/core/ui/components/SectionCard.kt")
        assertTrue("SectionCard still draws HorizontalDivider between rows", !code.contains("HorizontalDivider"))
        assertTrue("SectionCard should give each row a segmented shape", code.contains("segmentedShape("))
    }

    @Test
    fun `the app opts in to predictive back`() {
        val manifest = File(root, "app/src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("""android:enableOnBackInvokedCallback="true""""))
    }

    @Test
    fun `screen transitions use the theme motion scheme, including predictive back`() {
        val code = source("app/src/main/kotlin/com/tomasps/virga/shell/ShellHost.kt")
        listOf("transitionSpec =", "popTransitionSpec =", "predictivePopTransitionSpec =", "motionScheme").forEach {
            assertTrue("ShellHost NavDisplay lacks $it", code.contains(it))
        }
    }

    @Test
    fun `top-level navigation adapts to the window size`() {
        val shell = File(root, "app/src/main/kotlin/com/tomasps/virga/shell").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }.joinToString("\n") { it.code() }
        assertTrue("Use NavigationSuiteScaffold so wide windows get a rail", shell.contains("NavigationSuiteScaffold("))
    }

    @Test
    fun `state changes between loading, content and error animate`() {
        val code = source("core/ui/src/main/kotlin/com/tomasps/virga/core/ui/state/StateScaffolds.kt")
        assertTrue("StateScaffolds should switch states with AnimatedContent", code.contains("AnimatedContent("))
    }

    @Test
    fun `the root applies the saved dynamic color, pure black and screenshot settings`() {
        val code = source("app/src/main/kotlin/com/tomasps/virga/shell/VirgaRoot.kt")
        listOf("dynamicColor =", "pureBlack =", "SecureWindow(", "AppLockGate(").forEach {
            assertTrue("VirgaRoot does not pass $it", code.contains(it))
        }
    }

    @Test
    fun `Home is given the default time range from settings`() {
        val code = source("feature/home/src/main/kotlin/com/tomasps/virga/feature/home/HomeFeatureModule.kt")
        assertTrue("HomeViewModel is not given a DefaultTimeRangeSource", code.contains("DefaultTimeRangeSource"))
    }

    private companion object {
        /** Removes line and block comments, leaving string literals (which may hold slashes or a MIME wildcard) alone. */
        fun stripComments(text: String): String {
            val out = StringBuilder(text.length)
            var i = 0
            var inString = false
            while (i < text.length) {
                val c = text[i]
                val next = text.getOrNull(i + 1)
                when {
                    inString -> {
                        out.append(c)
                        if (c == '\\' && next != null) { out.append(next); i++ } else if (c == '"') inString = false
                    }
                    c == '"' -> { inString = true; out.append(c) }
                    c == '\'' -> {
                        val end = text.indexOf('\'', i + if (next == '\\') 3 else 2)
                        val stop = if (end < 0) i else end
                        out.append(text, i, stop + 1)
                        i = stop + 1
                        continue
                    }
                    c == '/' && next == '/' -> { while (i < text.length && text[i] != '\n') i++; continue }
                    c == '/' && next == '*' -> {
                        val end = text.indexOf("*/", i + 2)
                        i = if (end < 0) text.length else end + 2
                        out.append(' ')
                        continue
                    }
                    else -> out.append(c)
                }
                i++
            }
            return out.toString()
        }

        fun lines(code: String, pattern: Regex): List<String> =
            code.lines().mapIndexedNotNull { index, line ->
                if (!line.trimStart().startsWith("import ") && pattern.containsMatchIn(line)) "${index + 1}: ${line.trim()}" else null
            }

        /** The text of each call that [start] opens, up to its matching bracket, plus a trailing lambda if any. */
        fun callArguments(code: String, start: Regex): List<String> =
            start.findAll(code).filterNot { lineOf(code, it.range.first).trimStart().startsWith("import ") }.map { match ->
                val open = match.range.last
                val end = matchingClose(code, open)
                var text = code.substring(open, end + 1)
                if (code[open] == '(') {
                    val rest = code.substring(end + 1).trimStart()
                    if (rest.startsWith("{")) {
                        val braceAt = code.indexOf('{', end + 1)
                        text += code.substring(braceAt, matchingClose(code, braceAt) + 1)
                    }
                }
                text
            }.toList()

        fun lineOf(code: String, index: Int): String {
            val from = code.lastIndexOf('\n', index) + 1
            val to = code.indexOf('\n', index).let { if (it < 0) code.length else it }
            return code.substring(from, to)
        }

        fun matchingClose(code: String, open: Int): Int {
            val openChar = code[open]
            val closeChar = if (openChar == '(') ')' else '}'
            var depth = 0
            for (i in open until code.length) {
                when (code[i]) {
                    openChar -> depth++
                    closeChar -> if (--depth == 0) return i
                }
            }
            return code.length - 1
        }
    }
}
