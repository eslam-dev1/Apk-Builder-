package com.example.apk

import android.content.Context
import java.io.File

data class ReleaseBuildFlags(
    val minifyEnabled: Boolean = false,
    val crunchPngs: Boolean = false,
    val shrinkResources: Boolean = false
)

object ReleaseBuildConfigManager {

    private const val PREFS_NAME = "release_build_config_prefs"
    private const val KEY_MINIFY_ENABLED = "release_is_minify_enabled"
    private const val KEY_CRUNCH_PNGS = "release_is_crunch_pngs"
    private const val KEY_SHRINK_RESOURCES = "release_is_shrink_resources"
    private const val GRADLE_DIR_NAME = "gradle"
    private const val OVERRIDE_FILE_NAME = "release_build.gradle.kts"

    fun workspaceReleaseGradleFile(context: Context): File {
        val dir = File(context.filesDir, GRADLE_DIR_NAME).apply { mkdirs() }
        return File(dir, OVERRIDE_FILE_NAME)
    }

    fun loadSavedFlags(context: Context): ReleaseBuildFlags {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.contains(KEY_MINIFY_ENABLED) || prefs.contains(KEY_CRUNCH_PNGS)) {
            return ReleaseBuildFlags(
                minifyEnabled = prefs.getBoolean(KEY_MINIFY_ENABLED, false),
                crunchPngs = prefs.getBoolean(KEY_CRUNCH_PNGS, false),
                shrinkResources = prefs.getBoolean(KEY_SHRINK_RESOURCES, false)
            )
        }
        val overrideFile = workspaceReleaseGradleFile(context)
        if (overrideFile.isFile) {
            return parseReleaseFlags(runCatching { overrideFile.readText() }.getOrDefault(""))
        }
        return ReleaseBuildFlags()
    }

    fun saveFlags(
        context: Context,
        flags: ReleaseBuildFlags
    ): File {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_MINIFY_ENABLED, flags.minifyEnabled)
            .putBoolean(KEY_CRUNCH_PNGS, flags.crunchPngs)
            .putBoolean(KEY_SHRINK_RESOURCES, flags.shrinkResources)
            .apply()

        val target = workspaceReleaseGradleFile(context)
        target.writeText(
            buildReleaseBlockSnippet(
                minifyEnabled = flags.minifyEnabled,
                crunchPngs = flags.crunchPngs,
                shrinkResources = flags.shrinkResources
            ) + "\n",
            Charsets.UTF_8
        )
        return target
    }

    fun clearSavedFlags(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
        val file = workspaceReleaseGradleFile(context)
        if (file.exists()) {
            file.delete()
        }
    }

    fun buildReleaseBlockSnippet(
        minifyEnabled: Boolean,
        crunchPngs: Boolean,
        shrinkResources: Boolean? = null
    ): String = buildString {
        appendLine("buildTypes {")
        appendLine("    release {")
        appendLine("        isCrunchPngs = $crunchPngs")
        appendLine("        isMinifyEnabled = $minifyEnabled")
        if (shrinkResources != null) {
            appendLine("        isShrinkResources = $shrinkResources")
        }
        appendLine("        proguardFiles(getDefaultProguardFile(\"proguard-android-optimize.txt\"), \"proguard-rules.pro\")")
        appendLine("        signingConfig = signingConfigs.getByName(\"release\")")
        appendLine("    }")
        appendLine("}")
    }.trimEnd()

    fun parseReleaseFlags(gradleText: String): ReleaseBuildFlags {
        val scope = extractReleaseBlockContent(gradleText) ?: gradleText
        val minify = Regex("""\bisMinifyEnabled\s*=\s*(true|false)""")
            .find(scope)?.groupValues?.get(1)?.toBooleanStrictOrNull()
            ?: Regex("""\bminifyEnabled\s+(true|false)""")
                .find(scope)?.groupValues?.get(1)?.toBooleanStrictOrNull()
            ?: false
        val crunch = Regex("""\bisCrunchPngs\s*=\s*(true|false)""")
            .find(scope)?.groupValues?.get(1)?.toBooleanStrictOrNull()
            ?: Regex("""\bcrunchPngs\s+(true|false)""")
                .find(scope)?.groupValues?.get(1)?.toBooleanStrictOrNull()
            ?: false
        val shrink = Regex("""\bisShrinkResources\s*=\s*(true|false)""")
            .find(scope)?.groupValues?.get(1)?.toBooleanStrictOrNull()
            ?: Regex("""\bshrinkResources\s+(true|false)""")
                .find(scope)?.groupValues?.get(1)?.toBooleanStrictOrNull()
            ?: false
        return ReleaseBuildFlags(
            minifyEnabled = minify,
            crunchPngs = crunch,
            shrinkResources = shrink
        )
    }

    /**
     * Updates or inserts `isCrunchPngs` and `isMinifyEnabled` (and optionally `isShrinkResources`)
     * inside the `release { ... }` block of a `build.gradle.kts` file without altering signingConfigs.
     */
    fun applyFlagsToGradleScript(
        gradleText: String,
        minifyEnabled: Boolean,
        crunchPngs: Boolean,
        shrinkResources: Boolean? = null
    ): String {
        val releaseHeader = Regex("""\brelease\s*\{""").find(gradleText)
            ?: return if (gradleText.isBlank()) {
                buildReleaseBlockSnippet(minifyEnabled, crunchPngs, shrinkResources)
            } else {
                "$gradleText\n\n${buildReleaseBlockSnippet(minifyEnabled, crunchPngs, shrinkResources)}"
            }

        val blockStart = releaseHeader.range.last
        val blockEnd = findMatchingBrace(gradleText, blockStart) ?: return gradleText
        val inner = gradleText.substring(blockStart + 1, blockEnd)

        val baseIndent = detectInnerIndent(inner)
        var patchedInner = inner

        patchedInner = upsertBooleanProperty(
            blockBody = patchedInner,
            kotlinProperty = "isCrunchPngs",
            groovyProperty = "crunchPngs",
            value = crunchPngs,
            indent = baseIndent
        )
        patchedInner = upsertBooleanProperty(
            blockBody = patchedInner,
            kotlinProperty = "isMinifyEnabled",
            groovyProperty = "minifyEnabled",
            value = minifyEnabled,
            indent = baseIndent
        )
        if (shrinkResources != null) {
            patchedInner = upsertBooleanProperty(
                blockBody = patchedInner,
                kotlinProperty = "isShrinkResources",
                groovyProperty = "shrinkResources",
                value = shrinkResources,
                indent = baseIndent
            )
        }

        return gradleText.substring(0, blockStart + 1) +
            patchedInner +
            gradleText.substring(blockEnd)
    }

    fun defaultReleaseGradleTemplate(
        minifyEnabled: Boolean = false,
        crunchPngs: Boolean = false,
        shrinkResources: Boolean = false
    ): String = """
        android {
            compileSdk = 36

            defaultConfig {
                minSdk = 24
                targetSdk = 36
                versionCode = 1
                versionName = "1.0"
            }

            buildTypes {
                release {
                    isCrunchPngs = $crunchPngs
                    isMinifyEnabled = $minifyEnabled
                    isShrinkResources = $shrinkResources
                    proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
                    signingConfig = signingConfigs.getByName("release")
                }
                debug {
                    signingConfig = signingConfigs.getByName("debugConfig")
                }
            }
        }
    """.trimIndent()

    private fun extractReleaseBlockContent(gradleText: String): String? {
        val releaseMatch = Regex("""\brelease\s*\{""").find(gradleText) ?: return null
        val start = releaseMatch.range.last
        val end = findMatchingBrace(gradleText, start) ?: return null
        return gradleText.substring(start + 1, end)
    }

    private fun findMatchingBrace(text: String, openBraceIndex: Int): Int? {
        if (openBraceIndex !in text.indices || text[openBraceIndex] != '{') return null
        var depth = 0
        for (i in openBraceIndex until text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return null
    }

    private fun detectInnerIndent(inner: String): String {
        val firstProp = inner.lines().firstOrNull { it.trim().isNotEmpty() } ?: return "      "
        val ws = firstProp.takeWhile { it == ' ' || it == '\t' }
        return ws.ifEmpty { "      " }
    }

    private fun upsertBooleanProperty(
        blockBody: String,
        kotlinProperty: String,
        groovyProperty: String,
        value: Boolean,
        indent: String
    ): String {
        val kotlinRegex = Regex("""(?m)^([ \t]*)$kotlinProperty\s*=\s*(true|false)""")
        if (kotlinRegex.containsMatchIn(blockBody)) {
            return kotlinRegex.replaceFirst(blockBody, "$1$kotlinProperty = $value")
        }
        val groovyRegex = Regex("""(?m)^([ \t]*)$groovyProperty\s+(true|false)""")
        if (groovyRegex.containsMatchIn(blockBody)) {
            return groovyRegex.replaceFirst(blockBody, "$1$kotlinProperty = $value")
        }
        val insertion = "\n$indent$kotlinProperty = $value"
        return if (blockBody.startsWith("\n")) {
            insertion + blockBody
        } else {
            "$insertion\n$blockBody"
        }
    }
}
