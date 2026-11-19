package com.example.data

import android.content.Context
import com.example.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class CloudAiProvider(val displayName: String) {
    GEMINI("Gemini"),
    OPENROUTER("OpenRouter")
}

data class OpenRouterModelPreset(
    val id: String,
    val label: String
)

object CloudAiConfig {
    private const val PREFS_NAME = "cloud_ai_prefs"
    private const val KEY_PROVIDER = "cloud_ai_provider"
    private const val KEY_OPENROUTER_MODEL = "openrouter_model"
    private const val KEY_GEMINI_API_KEY = "gemini_api_key"
    private const val KEY_OPENROUTER_API_KEY = "openrouter_api_key"
    private const val KEY_OFFLINE_MODE = "offline_mode_enabled"
    private const val KEY_BUILD_MODE_STRATEGY = "build_mode_strategy"
    private const val KEY_DARK_MODE = "dark_mode_enabled"
    private const val KEY_DYNAMIC_COLOR = "dynamic_color_enabled"

    const val DEFAULT_OPENROUTER_MODEL = "google/gemini-2.5-flash"

    val OPENROUTER_PRESETS = listOf(
        OpenRouterModelPreset("google/gemini-2.5-flash", "Gemini 2.5 Flash"),
        OpenRouterModelPreset("openai/gpt-4o-mini", "GPT-4o Mini"),
        OpenRouterModelPreset("anthropic/claude-3.5-sonnet", "Claude 3.5"),
        OpenRouterModelPreset("meta-llama/llama-3.3-70b-instruct", "Llama 3.3 70B")
    )

    private val _currentProvider = MutableStateFlow(CloudAiProvider.GEMINI)
    val currentProvider: StateFlow<CloudAiProvider> = _currentProvider.asStateFlow()

    private val _openRouterModel = MutableStateFlow(DEFAULT_OPENROUTER_MODEL)
    val openRouterModel: StateFlow<String> = _openRouterModel.asStateFlow()

    private val _savedGeminiApiKey = MutableStateFlow("")
    val savedGeminiApiKey: StateFlow<String> = _savedGeminiApiKey.asStateFlow()

    private val _savedOpenRouterApiKey = MutableStateFlow("")
    val savedOpenRouterApiKey: StateFlow<String> = _savedOpenRouterApiKey.asStateFlow()

    private val _buildModeStrategy = MutableStateFlow(BuildModeStrategy.AUTO)
    val buildModeStrategy: StateFlow<BuildModeStrategy> = _buildModeStrategy.asStateFlow()

    private val _isOfflineMode = MutableStateFlow(false)
    val isOfflineMode: StateFlow<Boolean> = _isOfflineMode.asStateFlow()

    private val _isDarkMode = MutableStateFlow(true)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _isDynamicColor = MutableStateFlow(true)
    val isDynamicColor: StateFlow<Boolean> = _isDynamicColor.asStateFlow()

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rawProvider = prefs.getString(KEY_PROVIDER, CloudAiProvider.GEMINI.name)
        _currentProvider.value = runCatching {
            CloudAiProvider.valueOf(rawProvider ?: CloudAiProvider.GEMINI.name)
        }.getOrDefault(CloudAiProvider.GEMINI)

        val rawModel = prefs.getString(KEY_OPENROUTER_MODEL, DEFAULT_OPENROUTER_MODEL).orEmpty().trim()
        _openRouterModel.value = rawModel.ifBlank { DEFAULT_OPENROUTER_MODEL }

        val rawGeminiKey = prefs.getString(KEY_GEMINI_API_KEY, "").orEmpty().trim()
        _savedGeminiApiKey.value = rawGeminiKey

        val rawKey = prefs.getString(KEY_OPENROUTER_API_KEY, "").orEmpty().trim()
        _savedOpenRouterApiKey.value = rawKey

        val offlineFlag = prefs.getBoolean(KEY_OFFLINE_MODE, false)
        val rawStrategy = prefs.getString(KEY_BUILD_MODE_STRATEGY, null)
        val resolvedStrategy = if (rawStrategy != null) {
            runCatching { BuildModeStrategy.valueOf(rawStrategy) }
                .getOrDefault(if (offlineFlag) BuildModeStrategy.OFFLINE else BuildModeStrategy.AUTO)
        } else {
            if (offlineFlag) BuildModeStrategy.OFFLINE else BuildModeStrategy.AUTO
        }
        _buildModeStrategy.value = resolvedStrategy
        _isOfflineMode.value = resolvedStrategy == BuildModeStrategy.OFFLINE

        _isDarkMode.value = prefs.getBoolean(KEY_DARK_MODE, true)
        _isDynamicColor.value = prefs.getBoolean(KEY_DYNAMIC_COLOR, true)
    }

    fun setDarkMode(context: Context, enabled: Boolean) {
        _isDarkMode.value = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DARK_MODE, enabled)
            .apply()
    }

    fun setDynamicColor(context: Context, enabled: Boolean) {
        _isDynamicColor.value = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DYNAMIC_COLOR, enabled)
            .apply()
    }

    fun setProvider(context: Context, provider: CloudAiProvider) {
        _currentProvider.value = provider
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PROVIDER, provider.name)
            .apply()
    }

    fun setBuildModeStrategy(context: Context, strategy: BuildModeStrategy) {
        _buildModeStrategy.value = strategy
        _isOfflineMode.value = (strategy == BuildModeStrategy.OFFLINE)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_BUILD_MODE_STRATEGY, strategy.name)
            .putBoolean(KEY_OFFLINE_MODE, strategy == BuildModeStrategy.OFFLINE)
            .apply()
    }

    fun setOfflineMode(context: Context, enabled: Boolean) {
        setBuildModeStrategy(
            context,
            if (enabled) BuildModeStrategy.OFFLINE else BuildModeStrategy.AUTO
        )
    }

    fun setOpenRouterModel(context: Context, modelId: String) {
        val cleaned = modelId.trim().ifBlank { DEFAULT_OPENROUTER_MODEL }
        _openRouterModel.value = cleaned
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_OPENROUTER_MODEL, cleaned)
            .apply()
    }

    fun setSavedGeminiApiKey(context: Context, apiKey: String) {
        val cleaned = apiKey.trim()
        _savedGeminiApiKey.value = cleaned
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_GEMINI_API_KEY, cleaned)
            .apply()
    }

    fun setSavedOpenRouterApiKey(context: Context, apiKey: String) {
        val cleaned = apiKey.trim()
        _savedOpenRouterApiKey.value = cleaned
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_OPENROUTER_API_KEY, cleaned)
            .apply()
    }

    fun clearSavedOpenRouterApiKey(context: Context) {
        setSavedOpenRouterApiKey(context, "")
    }

    fun secretsGeminiApiKey(): String {
        val key = BuildConfig.GEMINI_API_KEY.trim()
        return if (key.isEmpty() || key == "MY_GEMINI_API_KEY" || key == "null") "" else key
    }

    fun effectiveGeminiApiKey(): String {
        val userKey = _savedGeminiApiKey.value.trim()
        if (userKey.isNotEmpty() && userKey != "MY_GEMINI_API_KEY" && userKey != "null") {
            return userKey
        }
        return secretsGeminiApiKey()
    }

    fun isGeminiKeyMissing(): Boolean = effectiveGeminiApiKey().isEmpty()

    fun hasUserSavedGeminiKey(): Boolean = _savedGeminiApiKey.value.trim().isNotEmpty()

    fun secretsOpenRouterApiKey(): String {
        val key = BuildConfig.OPENROUTER_API_KEY.trim()
        return if (key.isEmpty() || key == "MY_OPENROUTER_API_KEY" || key == "null") "" else key
    }

    fun effectiveOpenRouterApiKey(): String {
        val userKey = _savedOpenRouterApiKey.value.trim()
        if (userKey.isNotEmpty() && userKey != "MY_OPENROUTER_API_KEY" && userKey != "null") {
            return userKey
        }
        return secretsOpenRouterApiKey()
    }

    fun isOpenRouterKeyMissing(): Boolean = effectiveOpenRouterApiKey().isEmpty()

    fun hasUserSavedOpenRouterKey(): Boolean = _savedOpenRouterApiKey.value.trim().isNotEmpty()

    fun isActiveProviderKeyMissing(
        provider: CloudAiProvider = _currentProvider.value
    ): Boolean = when (provider) {
        CloudAiProvider.GEMINI -> isGeminiKeyMissing()
        CloudAiProvider.OPENROUTER -> isOpenRouterKeyMissing()
    }

    internal fun resetForTest() {
        _currentProvider.value = CloudAiProvider.GEMINI
        _openRouterModel.value = DEFAULT_OPENROUTER_MODEL
        _savedGeminiApiKey.value = ""
        _savedOpenRouterApiKey.value = ""
        _buildModeStrategy.value = BuildModeStrategy.AUTO
        _isOfflineMode.value = false
    }
}
