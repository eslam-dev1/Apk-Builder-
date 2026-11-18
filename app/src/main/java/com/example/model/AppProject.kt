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
    val screenSpecs: List<ScreenSpec> = emptyList()
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
                screenSpecs = parseScreenSpecs(json.optJSONArray("screenSpecs"))
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
    data class Success(val bitmap: Bitmap, val styleUsed: String) : AppIconUiState
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
