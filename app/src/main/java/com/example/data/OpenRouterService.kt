package com.example.data

import com.example.model.AiFeatureIdea
import com.example.model.AppProject
import com.example.model.CodeAnalysisResult
import com.example.model.CodeIssueFix
import com.example.model.TabBuildPreference
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class MissingOpenRouterKeyException : IllegalStateException("OpenRouter API Key missing")

class OpenRouterService(
    private val geminiParser: GeminiService = GeminiService()
) {
    companion object {
        private const val CHAT_COMPLETIONS_URL = "https://openrouter.ai/api/v1/chat/completions"
    }

    fun isApiKeyMissing(): Boolean = CloudAiConfig.isOpenRouterKeyMissing()

    suspend fun generateApp(
        prompt: String,
        modelId: String = CloudAiConfig.openRouterModel.value,
        tabPreference: TabBuildPreference = TabBuildPreference()
    ): AppProject = withContext(Dispatchers.IO) {
        val apiKey = CloudAiConfig.effectiveOpenRouterApiKey()
        if (apiKey.isEmpty()) {
            throw MissingOpenRouterKeyException()
        }

        val responseBody = executeChatCompletionWithRetry(
            apiKey = apiKey,
            modelId = modelId.ifBlank { CloudAiConfig.DEFAULT_OPENROUTER_MODEL },
            systemPrompt = geminiParser.buildGenerateAppSystemPrompt(tabPreference),
            userPrompt = prompt
        )

        val cleanJson = extractChatCompletionJsonObject(responseBody)
        geminiParser.parseProjectJson(cleanJson, prompt, tabPreference)
    }

    suspend fun generateAiFeatureIdeas(
        appDescription: String,
        modelId: String = CloudAiConfig.openRouterModel.value
    ): List<AiFeatureIdea> = withContext(Dispatchers.IO) {
        val apiKey = CloudAiConfig.effectiveOpenRouterApiKey()
        if (apiKey.isEmpty()) {
            throw MissingOpenRouterKeyException()
        }

        val responseBody = executeChatCompletionWithRetry(
            apiKey = apiKey,
            modelId = modelId.ifBlank { CloudAiConfig.DEFAULT_OPENROUTER_MODEL },
            systemPrompt = geminiParser.buildFeatureIdeasSystemPrompt(),
            userPrompt = appDescription
        )

        val cleanJson = extractChatCompletionJsonObject(responseBody)
        geminiParser.parseAiFeatureIdeasJson(cleanJson)
    }

    suspend fun analyzeGeneratedCode(
        code: String,
        staticIssues: List<CodeIssueFix>,
        projectContext: String,
        modelId: String = CloudAiConfig.openRouterModel.value
    ): CodeAnalysisResult = withContext(Dispatchers.IO) {
        val apiKey = CloudAiConfig.effectiveOpenRouterApiKey()
        if (apiKey.isEmpty()) {
            throw MissingOpenRouterKeyException()
        }

        val responseBody = executeChatCompletionWithRetry(
            apiKey = apiKey,
            modelId = modelId.ifBlank { CloudAiConfig.DEFAULT_OPENROUTER_MODEL },
            systemPrompt = geminiParser.buildCodeAnalysisSystemPrompt(projectContext),
            userPrompt = geminiParser.buildCodeAnalysisUserContent(code, staticIssues, projectContext)
        )

        val cleanJson = extractChatCompletionJsonObject(responseBody)
        geminiParser.parseCodeAnalysisJson(
            jsonText = cleanJson,
            staticIssues = staticIssues,
            sourceLabel = "OpenRouter",
            providerLabel = "OpenRouter"
        )
    }

    private data class HttpResponseResult(
        val statusCode: Int,
        val body: String
    )

    private suspend fun executeChatCompletionWithRetry(
        apiKey: String,
        modelId: String,
        systemPrompt: String,
        userPrompt: String
    ): String {
        val retryableCodes = setOf(429, 500, 502, 503)
        var includeResponseFormat = true
        var attempt = 0

        while (true) {
            val payload = buildChatCompletionPayload(
                modelId = modelId,
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                includeResponseFormat = includeResponseFormat
            )
            val res = executePostRequest(apiKey = apiKey, requestJson = payload)

            if (res.statusCode in 200..299) {
                return res.body
            }

            // Some third-party OpenRouter models reject response_format=json_object with HTTP 400
            if (res.statusCode == 400 && includeResponseFormat) {
                includeResponseFormat = false
                continue
            }

            if (res.statusCode in retryableCodes && attempt < 2) {
                attempt++
                delay(1500L * attempt)
                continue
            }

            val apiErrorMessage = runCatching {
                JSONObject(res.body)
                    .optJSONObject("error")
                    ?.optString("message")
            }.getOrNull()
            throw IllegalStateException(
                apiErrorMessage?.takeIf { it.isNotBlank() }
                    ?: "OpenRouter API error (HTTP ${res.statusCode})"
            )
        }
    }

    private fun buildChatCompletionPayload(
        modelId: String,
        systemPrompt: String,
        userPrompt: String,
        includeResponseFormat: Boolean
    ): String = JSONObject().apply {
        put("model", modelId)
        put(
            "messages",
            JSONArray()
                .put(JSONObject().put("role", "system").put("content", systemPrompt))
                .put(JSONObject().put("role", "user").put("content", userPrompt))
        )
        if (includeResponseFormat) {
            put("response_format", JSONObject().put("type", "json_object"))
        }
    }.toString()

    private fun executePostRequest(
        apiKey: String,
        requestJson: String,
        readTimeoutMs: Int = 90_000
    ): HttpResponseResult {
        val connection = (URL(CHAT_COMPLETIONS_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30_000
            readTimeout = readTimeoutMs
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("HTTP-Referer", "https://aiappstudio.local")
            setRequestProperty("X-Title", "Ai App Studio")
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

    internal fun extractChatCompletionJsonObject(responseBody: String): String {
        val root = JSONObject(responseBody)
        val choices = root.optJSONArray("choices")
            ?: throw IllegalStateException("No choices returned from OpenRouter API")
        if (choices.length() == 0) {
            throw IllegalStateException("Empty choices returned from OpenRouter API")
        }

        val message = choices.optJSONObject(0)?.optJSONObject("message")
            ?: throw IllegalStateException("No message returned from OpenRouter API")
        val contentValue = message.opt("content")
        val rawContent = when (contentValue) {
            is String -> contentValue
            is JSONArray -> buildString {
                for (i in 0 until contentValue.length()) {
                    val part = contentValue.optJSONObject(i) ?: continue
                    val text = part.optString("text", "")
                    if (text.isNotEmpty()) append(text)
                }
            }
            else -> ""
        }.trim()

        val cleanedText = rawContent
            .replace(Regex("(?s)<think>.*?</think>"), "")
            .trim()
        if (cleanedText.isEmpty()) {
            throw IllegalStateException("Empty content returned from OpenRouter API")
        }

        val firstBrace = cleanedText.indexOf('{')
        val lastBrace = cleanedText.lastIndexOf('}')
        if (firstBrace == -1 || lastBrace == -1 || lastBrace < firstBrace) {
            throw IllegalStateException("OpenRouter response did not contain a valid JSON object")
        }

        return cleanedText.substring(firstBrace, lastBrace + 1)
    }
}
