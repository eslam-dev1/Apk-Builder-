package com.example.model

import android.graphics.Bitmap
import org.json.JSONArray
import org.json.JSONObject

data class AppProject(
    val id: String,
    val name: String,
    val description: String,
    val prompt: String,
    val createdAt: Long,
    val features: List<String> = emptyList(),
    val screens: List<String> = emptyList(),
    val designStyle: String = "",
    val primaryColorHex: String = "",
    val screenSpecs: List<ScreenSpec> = emptyList(),
    val buildOptions: BuildOptions = BuildOptions(),
    val iconDrawableName: String = "",
    val iconBackgroundHex: String = ""
) {
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("name", name)
            put("description", description)
            put("prompt", prompt)
            put("createdAt", createdAt)
            put("features", JSONArray(features))
            put("screens", JSONArray(screens))
            put("designStyle", designStyle)
            put("primaryColorHex", primaryColorHex)
            put("screenSpecs", JSONArray().apply { screenSpecs.forEach { put(it.toJsonObject()) } })
            put("buildOptions", buildOptions.toJsonObject())
            if (iconDrawableName.isNotBlank()) put("iconDrawableName", iconDrawableName)
            if (iconBackgroundHex.isNotBlank()) put("iconBackgroundHex", iconBackgroundHex)
        }
    }

    companion object {
        private fun parseScreenSpecs(array: JSONArray?): List<ScreenSpec> {
            if (array == null) return emptyList()
            return (0 until array.length()).mapNotNull { idx ->
                array.optJSONObject(idx)?.let { ScreenSpec.fromJsonObject(it) }
            }
        }

        fun fromJsonObject(json: JSONObject): AppProject {
            val featuresArray = json.optJSONArray("features")
            val screensArray = json.optJSONArray("screens")

            val featuresList = if (featuresArray != null) {
                (0 until featuresArray.length()).mapNotNull {
                    featuresArray.optString(it).takeIf { s -> s.isNotBlank() }
                }
            } else {
                emptyList()
            }

            val screensList = if (screensArray != null) {
                (0 until screensArray.length()).mapNotNull {
                    screensArray.optString(it).takeIf { s -> s.isNotBlank() }
                }
            } else {
                emptyList()
            }

            return AppProject(
                id = json.optString("id"),
                name = json.optString("name"),
                description = json.optString("description"),
                prompt = json.optString("prompt"),
                createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                features = featuresList,
                screens = screensList,
                designStyle = json.optString("designStyle", ""),
                primaryColorHex = json.optString("primaryColorHex", ""),
                screenSpecs = parseScreenSpecs(json.optJSONArray("screenSpecs")),
                buildOptions = BuildOptions.fromJsonObject(json.optJSONObject("buildOptions")),
                iconDrawableName = json.optString("iconDrawableName", ""),
                iconBackgroundHex = json.optString("iconBackgroundHex", "")
            )
        }
    }
}

enum class BuildVariant(val id: String, val displayName: String, val description: String) {
    DEBUG("debug", "Debug", "Includes debuggable flags, symbols & faster compilation"),
    RELEASE("release", "Release", "Optimized production build with code minification"),
    BENCHMARK("benchmark", "Benchmark", "Profileable build for performance & macrobenchmark analysis");

    companion object {
        fun fromId(id: String): BuildVariant = entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: DEBUG
    }
}

data class BuildOptions(
    val buildVariant: String = "debug",
    val minifyEnabled: Boolean = false,
    val crunchPngs: Boolean = false,
    val includeDebuggable: Boolean = true,
    val shrinkResources: Boolean = false,
    val optimizeBytecode: Boolean = false,
    val targetSdk: Int = 36,
    val versionName: String = "1.0",
    val versionCode: Int = 1,
    val signingScheme: String = "v1+v2",
    val bundledTfliteModels: List<String> = emptyList()
) {
    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("buildVariant", buildVariant)
        put("minifyEnabled", minifyEnabled)
        put("crunchPngs", crunchPngs)
        put("includeDebuggable", includeDebuggable)
        put("shrinkResources", shrinkResources)
        put("optimizeBytecode", optimizeBytecode)
        put("targetSdk", targetSdk)
        put("versionName", versionName)
        put("versionCode", versionCode)
        put("signingScheme", signingScheme)
        put("bundledTfliteModels", JSONArray(bundledTfliteModels))
    }

    val variant: BuildVariant
        get() = BuildVariant.fromId(buildVariant)

    companion object {
        fun fromJsonObject(json: JSONObject?): BuildOptions {
            if (json == null) return BuildOptions()
            val modelsArray = json.optJSONArray("bundledTfliteModels")
            val modelsList = if (modelsArray != null) {
                (0 until modelsArray.length()).mapNotNull {
                    modelsArray.optString(it).takeIf { name -> name.isNotBlank() }
                }.distinct()
            } else {
                emptyList()
            }
            return BuildOptions(
                buildVariant = json.optString("buildVariant", "debug"),
                minifyEnabled = json.optBoolean("minifyEnabled", false),
                crunchPngs = json.optBoolean("crunchPngs", false),
                includeDebuggable = json.optBoolean("includeDebuggable", true),
                shrinkResources = json.optBoolean("shrinkResources", false),
                optimizeBytecode = json.optBoolean("optimizeBytecode", false),
                targetSdk = json.optInt("targetSdk", 36),
                versionName = json.optString("versionName", "1.0"),
                versionCode = json.optInt("versionCode", 1),
                signingScheme = json.optString("signingScheme", "v1+v2"),
                bundledTfliteModels = modelsList
            )
        }
    }
}

/**
 * One screen (bottom-navigation tab) of a generated app.
 * [name] is a PascalCase ASCII identifier (used for generated code),
 * [label] is the short human readable tab title (any language),
 * [icon] is a key from ScreenIcons.
 */
data class ScreenSpec(
    val name: String,
    val label: String,
    val icon: String,
    val purpose: String,
    val components: List<String> = emptyList(),
    val sampleItems: List<String> = emptyList()
) {
    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("name", name)
        put("label", label)
        put("icon", icon)
        put("purpose", purpose)
        put("components", JSONArray(components))
        put("sampleItems", JSONArray(sampleItems))
    }

    companion object {
        private fun JSONArray?.strings(): List<String> {
            if (this == null) return emptyList()
            return (0 until length()).mapNotNull { optString(it).takeIf { s -> s.isNotBlank() } }
        }

        fun fromJsonObject(json: JSONObject): ScreenSpec? {
            val name = json.optString("name")
            if (name.isBlank()) return null
            return ScreenSpec(
                name = name,
                label = json.optString("label").ifBlank { name },
                icon = json.optString("icon"),
                purpose = json.optString("purpose"),
                components = json.optJSONArray("components").strings(),
                sampleItems = json.optJSONArray("sampleItems").strings()
            )
        }
    }
}

data class AiFeatureIdea(
    val title: String,
    val description: String,
    val benefit: String
)

sealed interface AppIconUiState {
    data object Idle : AppIconUiState
    data object Loading : AppIconUiState
    data class Success(
        val bitmap: Bitmap,
        val styleUsed: String,
        val engineUsed: String = "Imagen",
        val promptUsed: String = "",
        val drawableResources: List<String> = emptyList(),
        val backgroundHex: String = "",
        val drawableDirPath: String = ""
    ) : AppIconUiState
    data class Error(val message: String) : AppIconUiState
}

enum class CodeIssueSeverity {
    ERROR,
    WARNING,
    INFO
}

data class CodeIssueFix(
    val title: String,
    val severity: CodeIssueSeverity,
    val lineHint: String,
    val problem: String,
    val suggestion: String,
    val fixedSnippet: String = "",
    val source: String = "Static Check"
)

data class CodeAnalysisResult(
    val issues: List<CodeIssueFix>,
    val summary: String,
    val usedGeminiAi: Boolean = false,
    val aiNotice: String? = null,
    val providerLabel: String = if (usedGeminiAi) "Gemini AI" else "Static Check"
)

sealed interface CodeAnalysisUiState {
    data object Idle : CodeAnalysisUiState
    data object Loading : CodeAnalysisUiState
    data class Success(val result: CodeAnalysisResult) : CodeAnalysisUiState
    data class Error(val message: String) : CodeAnalysisUiState
}

enum class TabSelectionMode(val displayName: String) {
    AI_DECIDES("AI Decides"),
    USER_DEFINED("Custom")
}

data class TabBuildPreference(
    val mode: TabSelectionMode = TabSelectionMode.AI_DECIDES,
    val requestedCount: Int = 3,
    val customTabNames: List<String> = emptyList()
) {
    val effectiveRequestedCount: Int?
        get() = when {
            mode == TabSelectionMode.AI_DECIDES -> null
            customTabNames.isNotEmpty() -> customTabNames.size
            else -> requestedCount.coerceIn(1, MAX_USER_TABS)
        }

    val effectiveCustomNames: List<String>
        get() = if (mode == TabSelectionMode.USER_DEFINED) customTabNames else emptyList()

    companion object {
        const val MIN_USER_TABS = 1
        const val MAX_USER_TABS = 8

        fun parseCustomTabNames(raw: String): List<String> {
            return raw.split(',', '،', '\n')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .take(MAX_USER_TABS)
        }
    }
}

data class PotentialFeature(
    val title: String,
    val summary: String = "",
    val priority: String = "MVP",
    val category: String = ""
)

data class CodeFileBlueprint(
    val path: String,
    val purpose: String = "",
    val code: String = ""
)

data class ProjectCodeStructure(
    val architecturePattern: String = "MVVM + Jetpack Compose",
    val packageName: String = "com.example.generated",
    val dependencies: List<String> = emptyList(),
    val files: List<CodeFileBlueprint> = emptyList()
)

