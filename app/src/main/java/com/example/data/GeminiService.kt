package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.example.BuildConfig
import com.example.design.ScreenIcons
import com.example.design.ScreenSpecs
import com.example.model.AiFeatureIdea
import com.example.model.AppProject
import com.example.model.CodeAnalysisResult
import com.example.model.CodeIssueFix
import com.example.model.CodeIssueSeverity
import com.example.model.ScreenSpec
import com.example.model.TabBuildPreference
import com.example.model.TabSelectionMode
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object GeminiModels {
    const val FAST = "gemini-3.8-flash"
    const val BEST = "gemini-4-argon"
    const val FALLBACK = "gemini-3.5-flash"
    const val IMAGE_MODEL = "gemini-3-pro-image"
    const val IMAGEN_PRIMARY = "imagen-4.0-generate-001"
    const val IMAGEN_FAST = "imagen-4.0-fast-generate-001"
    const val IMAGEN_3 = "imagen-3.0-generate-002"

    private const val PREFS_NAME = "gemini_models_prefs"
    private const val KEY_QUALITY = "quality"

    enum class Quality {
        FAST,
        BEST
    }

    @Volatile
    var currentQuality: Quality = Quality.FAST
        private set

    fun load(context: Context): Quality {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_QUALITY, Quality.FAST.name)
        val resolved = runCatching { Quality.valueOf(raw ?: Quality.FAST.name) }
            .getOrDefault(Quality.FAST)
        currentQuality = resolved
        return resolved
    }

    fun setQuality(context: Context, q: Quality) {
        currentQuality = q
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_QUALITY, q.name)
            .apply()
    }

    fun textModelChain(): List<String> {
        val selected = if (currentQuality == Quality.BEST) BEST else FAST
        return listOf(selected, FAST, FALLBACK).distinct()
    }
}

class MissingApiKeyException : IllegalStateException("API Key missing")

class GeminiService {

    private val openRouterService: OpenRouterService by lazy { OpenRouterService(this) }

    fun isApiKeyMissing(): Boolean = CloudAiConfig.isActiveProviderKeyMissing()

    fun isGeminiKeyMissing(): Boolean = CloudAiConfig.isGeminiKeyMissing()

    suspend fun generateApp(
        prompt: String,
        tabPreference: TabBuildPreference = TabBuildPreference()
    ): AppProject {
        if (CloudAiConfig.currentProvider.value == CloudAiProvider.OPENROUTER) {
            return openRouterService.generateApp(prompt = prompt, tabPreference = tabPreference)
        }
        return withContext(Dispatchers.IO) {
            if (CloudAiConfig.isGeminiKeyMissing()) {
                throw MissingApiKeyException()
            }

            val apiKey = CloudAiConfig.effectiveGeminiApiKey()
            val requestPayload = buildRequestJson(prompt, tabPreference)
            val result = executeWithChainAndRetry(
                models = GeminiModels.textModelChain(),
                apiKey = apiKey,
                requestJson = requestPayload
            )

            parseGeminiResponse(result.body, prompt, tabPreference)
        }
    }

    suspend fun generateAppIcon(
        projectName: String,
        designStyle: String,
        description: String = ""
    ): Bitmap {
        val defaultPrompt = buildString {
            append("Android app launcher icon for \"$projectName\"")
            if (description.isNotBlank()) {
                append(", concept: $description")
            }
        }
        return generateLauncherIconWithImagen(
            userPrompt = defaultPrompt,
            designStyle = designStyle,
            projectName = projectName,
            description = description
        ).first
    }

    /**
     * Generates a 1:1 Android launcher icon using Imagen (`:predict`) first,
     * falling back automatically to Gemini Image generation if Imagen `:predict` is unavailable on the API tier.
     * Returns Pair(bitmap, engineLabel).
     */
    suspend fun generateLauncherIconWithImagen(
        userPrompt: String,
        designStyle: String = "Modern",
        projectName: String = "",
        description: String = ""
    ): Pair<Bitmap, String> = withContext(Dispatchers.IO) {
        if (CloudAiConfig.isGeminiKeyMissing()) {
            if (CloudAiConfig.currentProvider.value == CloudAiProvider.OPENROUTER) {
                throw IllegalStateException(
                    "Imagen app icon generation requires GEMINI_API_KEY in AI Studio Secrets (OpenRouter handles text and code generation)."
                )
            }
            throw MissingApiKeyException()
        }

        val apiKey = CloudAiConfig.effectiveGeminiApiKey()
        val effectiveStyle = designStyle.ifBlank { "Modern" }
        val composedPrompt = buildLauncherIconImagenPrompt(
            userPrompt = userPrompt,
            designStyle = effectiveStyle,
            projectName = projectName,
            description = description
        )

        val predictJson = buildImagenPredictRequestJson(prompt = composedPrompt, aspectRatio = "1:1")
        val imagenModels = listOf(
            GeminiModels.IMAGEN_PRIMARY to "Imagen 4",
            GeminiModels.IMAGEN_FAST to "Imagen 4 Fast",
            GeminiModels.IMAGEN_3 to "Imagen 3"
        )

        var lastNon404Error: String? = null
        for ((modelId, friendlyLabel) in imagenModels) {
            val res = runCatching {
                executePredictRequest(
                    modelName = modelId,
                    apiKey = apiKey,
                    requestJson = predictJson
                )
            }.getOrNull() ?: continue

            if (res.statusCode in 200..299) {
                val bitmap = parseImagenPredictBitmap(res.body)
                return@withContext bitmap to friendlyLabel
            }

            if (res.statusCode !in setOf(400, 403, 404)) {
                lastNon404Error = runCatching {
                    JSONObject(res.body).optJSONObject("error")?.optString("message")
                }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Imagen API error (HTTP ${res.statusCode})"
            }
        }

        // Fallback to Gemini Pro Image (:generateContent)
        val contentPayload = JSONObject().apply {
            put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", composedPrompt))
                    )
                )
            )
            put(
                "generationConfig",
                JSONObject().put(
                    "responseModalities",
                    JSONArray().put("TEXT").put("IMAGE")
                )
            )
        }.toString()

        val result = try {
            executeWithChainAndRetry(
                models = listOf(GeminiModels.IMAGE_MODEL),
                apiKey = apiKey,
                requestJson = contentPayload
            )
        } catch (err: Exception) {
            if (!lastNon404Error.isNullOrBlank()) {
                throw IllegalStateException(lastNon404Error, err)
            }
            throw err
        }

        parseGeneratedIconBitmap(result.body) to "Gemini Image"
    }

    internal fun buildLauncherIconImagenPrompt(
        userPrompt: String,
        designStyle: String,
        projectName: String = "",
        description: String = ""
    ): String {
        val coreSubject = userPrompt.trim().ifBlank {
            if (projectName.isNotBlank()) {
                "distinctive symbol representing $projectName"
            } else {
                "modern mobile utility emblem"
            }
        }
        val styleNote = designStyle.ifBlank { "Modern" }
        return buildString {
            append("Professional Android mobile app launcher icon, 1:1 square artwork. ")
            append("Main subject: $coreSubject. ")
            if (projectName.isNotBlank() && !coreSubject.contains(projectName, ignoreCase = true)) {
                append("Designed for app \"$projectName\". ")
            }
            if (description.isNotBlank() && !coreSubject.contains(description.take(20), ignoreCase = true)) {
                append("App concept: ${description.take(140)}. ")
            }
            append("Visual style: $styleNote, clean centered focal emblem with generous uniform background padding for Android adaptive icon masking, ")
            append("crisp geometric edges, balanced studio lighting, rich harmonious colors, high contrast silhouette, zero text, zero letters, no watermarks.")
        }
    }

    internal fun buildImagenPredictRequestJson(
        prompt: String,
        aspectRatio: String = "1:1"
    ): String {
        return JSONObject().apply {
            put(
                "instances",
                JSONArray().put(
                    JSONObject().put("prompt", prompt)
                )
            )
            put(
                "parameters",
                JSONObject().apply {
                    put("sampleCount", 1)
                    put("aspectRatio", aspectRatio)
                }
            )
        }.toString()
    }

    internal fun parseImagenPredictBitmap(responseBody: String): Bitmap {
        val root = JSONObject(responseBody)
        val predictions = root.optJSONArray("predictions")
            ?: root.optJSONArray("generatedImages")
            ?: throw IllegalStateException("No image predictions returned from Imagen API.")
        if (predictions.length() == 0) {
            throw IllegalStateException("Imagen API returned an empty predictions list.")
        }

        for (i in 0 until predictions.length()) {
            val item = predictions.optJSONObject(i) ?: continue
            val b64 = item.optString("bytesBase64Encoded").trim()
                .ifBlank { item.optJSONObject("image")?.optString("bytesBase64Encoded").orEmpty().trim() }
                .ifBlank { item.optJSONObject("image")?.optString("imageBytes").orEmpty().trim() }
            if (b64.isNotEmpty()) {
                val decoded = Base64.decode(b64, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(decoded, 0, decoded.size)
                if (bitmap != null) return bitmap
            }
        }
        throw IllegalStateException("Imagen API response did not contain a valid encoded image.")
    }

    internal fun buildFeatureIdeasSystemPrompt(): String = """
        You are a senior mobile product strategist. Analyze the Android app description and return 4 specific AI-powered feature ideas in the same language as the description.
        Return a JSON object with a single key "ideas", which is an array of 4 objects each having "title", "description", and "benefit".
    """.trimIndent()

    suspend fun generateAiFeatureIdeas(appDescription: String): List<AiFeatureIdea> {
        if (CloudAiConfig.currentProvider.value == CloudAiProvider.OPENROUTER) {
            return openRouterService.generateAiFeatureIdeas(appDescription)
        }
        return withContext(Dispatchers.IO) {
            if (CloudAiConfig.isGeminiKeyMissing()) {
                throw MissingApiKeyException()
            }

            val apiKey = CloudAiConfig.effectiveGeminiApiKey()
            val systemText = buildFeatureIdeasSystemPrompt()

            val requestPayload = JSONObject().apply {
                put(
                    "systemInstruction",
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", systemText))
                    )
                )
                put(
                    "contents",
                    JSONArray().put(
                        JSONObject().put(
                            "parts",
                            JSONArray().put(JSONObject().put("text", appDescription))
                        )
                    )
                )
                put(
                    "generationConfig",
                    JSONObject().put("responseMimeType", "application/json")
                )
            }.toString()

            val result = executeWithChainAndRetry(
                models = GeminiModels.textModelChain(),
                apiKey = apiKey,
                requestJson = requestPayload
            )

            val jsonText = extractJsonObjectText(result.body)
            parseAiFeatureIdeasJson(jsonText)
        }
    }

    internal fun parseAiFeatureIdeasJson(jsonText: String): List<AiFeatureIdea> {
        val ideasJson = JSONObject(jsonText).optJSONArray("ideas") ?: JSONArray()
        return (0 until ideasJson.length()).mapNotNull { idx ->
            val obj = ideasJson.optJSONObject(idx) ?: return@mapNotNull null
            val title = obj.optString("title").trim()
            val desc = obj.optString("description").trim()
            val benefit = obj.optString("benefit").trim()
            if (title.isNotEmpty() && desc.isNotEmpty()) {
                AiFeatureIdea(title = title, description = desc, benefit = benefit)
            } else {
                null
            }
        }.take(5)
    }

    internal fun buildCodeAnalysisSystemPrompt(projectContext: String): String = """
        You are a senior Kotlin & Jetpack Compose static code reviewer.
        Inspect the generated Kotlin source code for compilation errors, missing imports, Compose state/recomposition bugs, layout or accessibility issues, and runtime edge cases.
        Match the language of the project context ($projectContext) when writing "summary", "title", "problem", and "suggestion". Keep "fixedSnippet" as valid Kotlin code.
        Return a JSON object with:
        - "summary": 1-2 sentences summarizing code health.
        - "issues": array of objects (up to 6 most important issues; empty array [] if clean), each having:
          - "title": short issue name
          - "severity": one of ["ERROR", "WARNING", "INFO"]
          - "lineHint": e.g. "Line 42" or "Imports" or "File"
          - "problem": concise explanation of why this breaks or degrades the app
          - "suggestion": concrete corrective fix instruction
          - "fixedSnippet": short valid Kotlin snippet showing the fix (or "" if not needed)
    """.trimIndent()

    internal fun buildCodeAnalysisUserContent(
        code: String,
        staticIssues: List<CodeIssueFix>,
        projectContext: String
    ): String {
        val numberedCode = code.lines()
            .take(400)
            .mapIndexed { idx, line -> "${idx + 1}: $line" }
            .joinToString("\n")

        val staticFindingsText = if (staticIssues.isEmpty()) {
            "None"
        } else {
            staticIssues.joinToString("\n") { "- [${it.severity}] ${it.lineHint}: ${it.title} — ${it.problem}" }
        }

        return """
            Project: $projectContext
            Static pre-check findings:
            $staticFindingsText

            Generated Kotlin code:
            ```kotlin
            $numberedCode
            ```
        """.trimIndent()
    }

    suspend fun analyzeGeneratedCode(
        code: String,
        staticIssues: List<CodeIssueFix>,
        projectContext: String
    ): CodeAnalysisResult {
        if (CloudAiConfig.currentProvider.value == CloudAiProvider.OPENROUTER) {
            return openRouterService.analyzeGeneratedCode(code, staticIssues, projectContext)
        }
        return withContext(Dispatchers.IO) {
            if (CloudAiConfig.isGeminiKeyMissing()) {
                throw MissingApiKeyException()
            }

            val apiKey = CloudAiConfig.effectiveGeminiApiKey()
            val systemText = buildCodeAnalysisSystemPrompt(projectContext)
            val userContent = buildCodeAnalysisUserContent(code, staticIssues, projectContext)

            val requestPayload = JSONObject().apply {
                put(
                    "systemInstruction",
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", systemText))
                    )
                )
                put(
                    "contents",
                    JSONArray().put(
                        JSONObject().put(
                            "parts",
                            JSONArray().put(JSONObject().put("text", userContent))
                        )
                    )
                )
                put(
                    "generationConfig",
                    JSONObject().put("responseMimeType", "application/json")
                )
            }.toString()

            val result = executeWithChainAndRetry(
                models = GeminiModels.textModelChain(),
                apiKey = apiKey,
                requestJson = requestPayload
            )

            parseCodeAnalysisResponse(result.body, staticIssues)
        }
    }

    internal fun parseCodeAnalysisResponse(
        responseBody: String,
        staticIssues: List<CodeIssueFix>
    ): CodeAnalysisResult {
        val jsonText = extractJsonObjectText(responseBody)
        return parseCodeAnalysisJson(
            jsonText = jsonText,
            staticIssues = staticIssues,
            sourceLabel = "Gemini AI",
            providerLabel = "Gemini AI"
        )
    }

    internal fun parseCodeAnalysisJson(
        jsonText: String,
        staticIssues: List<CodeIssueFix>,
        sourceLabel: String = "Gemini AI",
        providerLabel: String = "Gemini AI"
    ): CodeAnalysisResult {
        val root = JSONObject(jsonText)
        val summary = root.optString("summary").trim()
            .ifBlank { "AI review completed." }
        val issuesArray = root.optJSONArray("issues") ?: JSONArray()

        val aiIssues = (0 until issuesArray.length()).mapNotNull { idx ->
            val obj = issuesArray.optJSONObject(idx) ?: return@mapNotNull null
            val title = obj.optString("title").trim()
            val problem = obj.optString("problem").trim()
            val suggestion = obj.optString("suggestion").trim()
            if (title.isEmpty() || problem.isEmpty() || suggestion.isEmpty()) {
                return@mapNotNull null
            }
            val severity = when (obj.optString("severity").trim().uppercase()) {
                "ERROR" -> CodeIssueSeverity.ERROR
                "WARNING" -> CodeIssueSeverity.WARNING
                else -> CodeIssueSeverity.INFO
            }
            CodeIssueFix(
                title = title,
                severity = severity,
                lineHint = obj.optString("lineHint").trim().ifBlank { "Code" },
                problem = problem,
                suggestion = suggestion,
                fixedSnippet = obj.optString("fixedSnippet").trim(),
                source = sourceLabel
            )
        }.take(6)

        val merged = (staticIssues + aiIssues).distinctBy {
            "${it.title.lowercase()}|${it.lineHint.lowercase()}"
        }

        return CodeAnalysisResult(
            issues = merged,
            summary = summary,
            usedGeminiAi = true,
            providerLabel = providerLabel
        )
    }

    private data class HttpResponseResult(
        val statusCode: Int,
        val body: String
    )

    private suspend fun executeWithChainAndRetry(
        models: List<String>,
        apiKey: String,
        requestJson: String
    ): HttpResponseResult {
        var lastResult: HttpResponseResult? = null
        val retryableCodes = setOf(429, 500, 503)

        for (modelName in models) {
            var attempt = 0
            while (true) {
                val res = executeModelRequest(
                    modelName = modelName,
                    apiKey = apiKey,
                    requestJson = requestJson
                )
                lastResult = res

                if (res.statusCode in 200..299) {
                    return res
                }

                if (res.statusCode == 404) {
                    // Move to next model in fallback chain
                    break
                }

                if (res.statusCode in retryableCodes && attempt < 2) {
                    attempt++
                    delay(1500L * attempt)
                    continue
                }

                // Non-retryable or retries exhausted for this model
                val apiErrorMessage = runCatching {
                    JSONObject(res.body)
                        .optJSONObject("error")
                        ?.optString("message")
                }.getOrNull()
                throw IllegalStateException(
                    apiErrorMessage?.takeIf { it.isNotBlank() }
                        ?: "Gemini API error (HTTP ${res.statusCode})"
                )
            }
        }

        if (lastResult?.statusCode == 404) {
            throw IllegalStateException("Model not available, please update app")
        }

        throw IllegalStateException("Gemini API request failed")
    }

    internal fun buildGenerateAppSystemPrompt(
        tabPreference: TabBuildPreference = TabBuildPreference()
    ): String {
        val allowedIcons = ScreenIcons.keys.joinToString(", ")
        val tabInstruction = when {
            tabPreference.mode == TabSelectionMode.USER_DEFINED && tabPreference.effectiveCustomNames.isNotEmpty() -> {
                val namesList = tabPreference.effectiveCustomNames.joinToString(", ") { "\"$it\"" }
                "array of EXACTLY ${tabPreference.effectiveCustomNames.size} screen/tab objects matching these user-defined tabs in order: [$namesList]"
            }
            tabPreference.mode == TabSelectionMode.USER_DEFINED -> {
                val count = tabPreference.effectiveRequestedCount ?: tabPreference.requestedCount.coerceIn(1, TabBuildPreference.MAX_USER_TABS)
                "array of EXACTLY $count screen/tab objects tailored to the project (the user explicitly requested $count tabs)"
            }
            else -> {
                "array of screen/tab objects whose count YOU decide dynamically (between 1 and 8 screens) strictly based on the actual scope and complexity of the user's app idea — NEVER force a fixed default 4 or 5 tab count (e.g., focused tools may need 1-2 screens, medium apps 3-4, larger platforms 5-8)"
            }
        }
        return """
            You are an expert Android app architect. Analyze the user's app idea deeply and return a single JSON object.
            Rules:
            - Write `name`, `description`, `features`, and each screen's `label`, `purpose`, `components`, and `sampleItems` in the SAME LANGUAGE as the user's idea (e.g., if the user's idea is in Arabic, write all user-facing text in Arabic; if English, write in English).
            - If the user's idea is brief or unclear, assume the most sensible, concrete interpretation for a mobile app; NEVER ask questions in the description.
            - `name`: 2-3 words, unique to the idea.
            - `description`: 2 specific sentences describing what the app does.
            - `features`: array of strings tied to concrete functions of the app idea.
            - `designStyle`: one of ["Modern", "Minimal", "Bold", "Playful", "Professional"].
            - `primaryColorHex`: hex color string matching `^#[0-9A-Fa-f]{6}$` tailored to the app's domain.
            - `screens`: $tabInstruction (the first item is the main screen). Each object in `screens` MUST have:
              - `"name"`: PascalCase ASCII identifier ending in "Screen" (e.g. "ProductCatalogScreen").
              - `"label"`: natural short tab title (max 16 chars) in the user's language.
              - `"icon"`: one of [$allowedIcons].
              - `"purpose"`: one sentence explaining what the screen does.
              - `"components"`: array of 3-4 specific UI elements shown on this screen.
              - `"sampleItems"`: array of 4 realistic sample rows with concrete values (names, prices, dates, or statuses).
            Return valid JSON with keys: "name", "description", "features", "screens", "designStyle", "primaryColorHex".
        """.trimIndent()
    }

    private fun buildRequestJson(
        prompt: String,
        tabPreference: TabBuildPreference = TabBuildPreference()
    ): String {
        val systemPrompt = buildGenerateAppSystemPrompt(tabPreference)

        return JSONObject().apply {
            put(
                "systemInstruction",
                JSONObject().put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", systemPrompt))
                )
            )
            put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", prompt))
                    )
                )
            )
            put(
                "generationConfig",
                JSONObject().put("responseMimeType", "application/json")
            )
        }.toString()
    }

    private fun executePredictRequest(
        modelName: String,
        apiKey: String,
        requestJson: String,
        readTimeoutMs: Int = 90_000
    ): HttpResponseResult {
        val endpoint =
            "https://generativelanguage.googleapis.com/v1beta/models/$modelName:predict"
        return postJsonRequest(
            endpoint = endpoint,
            apiKey = apiKey,
            requestJson = requestJson,
            readTimeoutMs = readTimeoutMs
        )
    }

    private fun executeModelRequest(
        modelName: String,
        apiKey: String,
        requestJson: String,
        readTimeoutMs: Int = 90_000
    ): HttpResponseResult {
        val endpoint =
            "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent"
        return postJsonRequest(
            endpoint = endpoint,
            apiKey = apiKey,
            requestJson = requestJson,
            readTimeoutMs = readTimeoutMs
        )
    }

    private fun postJsonRequest(
        endpoint: String,
        apiKey: String,
        requestJson: String,
        readTimeoutMs: Int = 90_000
    ): HttpResponseResult {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30_000
            readTimeout = readTimeoutMs
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("x-goog-api-key", apiKey)
        }

        return try {
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(requestJson)
                writer.flush()
            }

            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val responseBody = stream?.let {
                BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { reader ->
                    reader.readText()
                }
            }.orEmpty()

            HttpResponseResult(statusCode = statusCode, body = responseBody)
        } finally {
            connection.disconnect()
        }
    }

    internal fun extractJsonObjectText(responseBody: String): String {
        val root = JSONObject(responseBody)
        val candidates = root.optJSONArray("candidates")
            ?: throw IllegalStateException("No response candidates returned from Gemini API")
        if (candidates.length() == 0) {
            throw IllegalStateException("Empty candidates returned from Gemini API")
        }

        val parts = candidates.getJSONObject(0)
            .optJSONObject("content")
            ?.optJSONArray("parts")
            ?: throw IllegalStateException("No content parts returned from Gemini API")

        val joinedText = buildString {
            for (i in 0 until parts.length()) {
                val part = parts.optJSONObject(i) ?: continue
                if (part.optBoolean("thought", false)) continue
                val text = part.optString("text", "")
                if (text.isNotEmpty()) {
                    append(text)
                }
            }
        }.trim()

        val cleanedText = joinedText.replace(Regex("(?s)<think>.*?</think>"), "").trim()
        if (cleanedText.isEmpty()) {
            throw IllegalStateException("Empty text returned from Gemini API")
        }

        val firstBrace = cleanedText.indexOf('{')
        val lastBrace = cleanedText.lastIndexOf('}')
        if (firstBrace == -1 || lastBrace == -1 || lastBrace < firstBrace) {
            throw IllegalStateException("Response did not contain a valid JSON object")
        }

        return cleanedText.substring(firstBrace, lastBrace + 1)
    }

    internal fun parseGeneratedIconBitmap(responseBody: String): Bitmap {
        val root = JSONObject(responseBody)
        val candidates = root.optJSONArray("candidates")
            ?: throw IllegalStateException("No candidates returned from image generation model.")
        if (candidates.length() == 0) {
            throw IllegalStateException("Empty candidates returned from image generation model.")
        }

        val parts = candidates.optJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts")
            ?: throw IllegalStateException("No content parts returned from image generation model.")

        for (i in 0 until parts.length()) {
            val part = parts.optJSONObject(i) ?: continue
            if (part.optBoolean("thought", false)) continue
            val inlineData = part.optJSONObject("inlineData") ?: part.optJSONObject("inline_data")
            val base64Data = inlineData?.optString("data").orEmpty().trim()
            if (base64Data.isNotEmpty()) {
                val imageBytes = Base64.decode(base64Data, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                if (bitmap != null) {
                    return bitmap
                }
            }
        }

        throw IllegalStateException("No image was returned by the model.")
    }

    internal fun parseGeminiResponse(
        responseBody: String,
        originalPrompt: String,
        tabPreference: TabBuildPreference = TabBuildPreference()
    ): AppProject {
        val cleanJson = extractJsonObjectText(responseBody)
        return parseProjectJson(cleanJson, originalPrompt, tabPreference)
    }

    internal fun parseProjectJson(
        cleanJson: String,
        originalPrompt: String,
        tabPreference: TabBuildPreference = TabBuildPreference()
    ): AppProject {
        val ideaJson = JSONObject(cleanJson)

        val name = ideaJson.optString("name").ifBlank { originalPrompt.take(24) }
        val description = ideaJson.optString("description").ifBlank { "Generated from: $originalPrompt" }
        val features = ideaJson.optJSONArray("features").toStringList().take(8)
        val designStyle = ideaJson.optString("designStyle", "Modern")
        val rawHex = ideaJson.optString("primaryColorHex", "").trim()
        val primaryColorHex = if (Regex("^#[0-9A-Fa-f]{6}$").matches(rawHex)) rawHex else ""

        val screensArray = ideaJson.optJSONArray("screens")
        val explicitSpecsArray = ideaJson.optJSONArray("screenSpecs")

        val maxParsedSpecs = tabPreference.effectiveRequestedCount ?: 10
        val usedNames = mutableSetOf<String>()
        val parsedSpecs = mutableListOf<ScreenSpec>()

        val sourceSpecsArray = when {
            screensArray != null && screensArray.length() > 0 && screensArray.optJSONObject(0) != null ->
                screensArray
            explicitSpecsArray != null && explicitSpecsArray.length() > 0 ->
                explicitSpecsArray
            else -> null
        }

        if (sourceSpecsArray != null) {
            for (i in 0 until sourceSpecsArray.length()) {
                val obj = sourceSpecsArray.optJSONObject(i) ?: continue
                val rawName = obj.optString("name").ifBlank { obj.optString("label") }
                val sanitizedName = sanitizeUniqueScreenName(rawName, i + 1, usedNames)
                val rawLabel = obj.optString("label").trim()
                val label = (if (rawLabel.isNotBlank()) rawLabel else ScreenSpecs.prettyLabel(sanitizedName))
                    .take(16)
                val rawIcon = obj.optString("icon").trim()
                val validIcon = if (ScreenIcons.isValid(rawIcon)) {
                    rawIcon.lowercase()
                } else {
                    ScreenSpecs.guessIcon(sanitizedName)
                }
                val purpose = obj.optString("purpose").trim()
                val components = obj.optJSONArray("components").toStringList().take(4)
                val sampleItems = obj.optJSONArray("sampleItems").toStringList().take(5)

                parsedSpecs.add(
                    ScreenSpec(
                        name = sanitizedName,
                        label = label,
                        icon = validIcon,
                        purpose = purpose,
                        components = components,
                        sampleItems = sampleItems
                    )
                )
                if (parsedSpecs.size >= maxParsedSpecs) break
            }
        } else if (screensArray != null) {
            // Backward compatibility: array of strings
            val rawNames = screensArray.toStringList().take(maxParsedSpecs)
            rawNames.forEachIndexed { idx, raw ->
                val sanitizedName = sanitizeUniqueScreenName(raw, idx + 1, usedNames)
                parsedSpecs.add(
                    ScreenSpec(
                        name = sanitizedName,
                        label = ScreenSpecs.prettyLabel(sanitizedName).take(16),
                        icon = ScreenSpecs.guessIcon(sanitizedName),
                        purpose = features.getOrNull(idx) ?: description,
                        components = features.drop(idx).take(3),
                        sampleItems = emptyList()
                    )
                )
            }
        }

        // Align with user's custom tab names when explicitly provided
        val customNames = tabPreference.effectiveCustomNames
        if (customNames.isNotEmpty()) {
            val alignedSpecs = customNames.mapIndexed { idx, userLabel ->
                val existing = parsedSpecs.getOrNull(idx)
                if (existing != null) {
                    existing.copy(label = userLabel.take(16))
                } else {
                    val sanitizedName = sanitizeUniqueScreenName(userLabel, idx + 1, usedNames)
                    ScreenSpec(
                        name = sanitizedName,
                        label = userLabel.take(16),
                        icon = ScreenSpecs.guessIcon("$sanitizedName $userLabel"),
                        purpose = features.getOrNull(idx) ?: description,
                        components = emptyList(),
                        sampleItems = emptyList()
                    )
                }
            }
            parsedSpecs.clear()
            parsedSpecs.addAll(alignedSpecs)
        }

        val screenNames = if (parsedSpecs.isNotEmpty()) {
            parsedSpecs.map { it.name }
        } else {
            screensArray.toStringList().take(maxParsedSpecs)
        }

        return AppProject(
            id = UUID.randomUUID().toString(),
            name = name,
            description = description,
            prompt = originalPrompt,
            createdAt = System.currentTimeMillis(),
            features = features,
            screens = screenNames,
            designStyle = designStyle,
            primaryColorHex = primaryColorHex,
            screenSpecs = parsedSpecs
        )
    }

    private fun sanitizeUniqueScreenName(
        rawName: String,
        fallbackIndex: Int,
        usedNames: MutableSet<String>
    ): String {
        val words = rawName
            .replace(Regex("[^A-Za-z0-9]+"), " ")
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

        val pascal = words.joinToString("") { w ->
            w.replaceFirstChar { it.uppercaseChar() }
        }

        var base = if (pascal.isEmpty() || !pascal.first().isLetter()) {
            "Tab${fallbackIndex}Screen"
        } else if (pascal.endsWith("Screen")) {
            pascal
        } else {
            "${pascal}Screen"
        }

        if (base in usedNames) {
            val prefix = base.removeSuffix("Screen")
            var suffix = 2
            while ("${prefix}${suffix}Screen" in usedNames) {
                suffix++
            }
            base = "${prefix}${suffix}Screen"
        }
        usedNames.add(base)
        return base
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { index ->
            val item = opt(index)
            when (item) {
                is String -> item.takeIf { it.isNotBlank() }
                is JSONObject -> (
                    item.optString("name").ifBlank { item.optString("title") }
                    ).takeIf { it.isNotBlank() }
                else -> item?.toString()?.takeIf { it.isNotBlank() }
            }
        }
    }
}
