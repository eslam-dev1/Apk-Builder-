package com.example.ai

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.example.model.AppProject
import com.example.model.ScreenSpec
import com.example.model.TabBuildPreference
import com.example.model.TabSelectionMode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class LocalAiCategory(val label: String) {
    LLM_ENGINE("On-Device LLMs"),
    TFLITE_RUNTIME("TFLite Runtime"),
    DEVICE_TOOLS("Device AI Tools")
}

data class LocalAiCapabilitySummary(
    val id: String,
    val title: String,
    val engineName: String,
    val category: LocalAiCategory,
    val description: String,
    val statusBadge: String,
    val isReady: Boolean,
    val requiresDownload: Boolean
)

data class VoiceDictationState(
    val isListening: Boolean = false,
    val partialText: String = "",
    val errorMessage: String? = null
)

object OfflineSpeechDictation {

    private var activeRecognizer: SpeechRecognizer? = null

    private val _state = MutableStateFlow(VoiceDictationState())
    val state: StateFlow<VoiceDictationState> = _state.asStateFlow()

    fun isAvailable(context: Context): Boolean =
        SpeechRecognizer.isRecognitionAvailable(context)

    fun startListening(
        context: Context,
        onRecognizedText: (String) -> Unit
    ) {
        if (!isAvailable(context)) {
            _state.value = VoiceDictationState(
                isListening = false,
                errorMessage = "Speech recognition service unavailable on this device."
            )
            return
        }

        stopListening()

        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        activeRecognizer = recognizer

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _state.value = VoiceDictationState(isListening = true)
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                _state.value = _state.value.copy(isListening = false)
            }

            override fun onError(error: Int) {
                val msg = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
                    else -> "Voice dictation error ($error)"
                }
                _state.value = VoiceDictationState(isListening = false, errorMessage = msg)
                stopListening()
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val finalText = matches?.firstOrNull().orEmpty().trim()
                _state.value = VoiceDictationState(isListening = false)
                if (finalText.isNotEmpty()) {
                    onRecognizedText(finalText)
                }
                stopListening()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                _state.value = _state.value.copy(partialText = partial)
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        _state.value = VoiceDictationState(isListening = true)
        recognizer.startListening(intent)
    }

    fun stopListening() {
        runCatching {
            activeRecognizer?.stopListening()
            activeRecognizer?.destroy()
        }
        activeRecognizer = null
        _state.value = _state.value.copy(isListening = false)
    }
}

object OfflineImageOcrExtractor {

    suspend fun extractAppPromptFromImage(
        context: Context,
        imageUri: Uri
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = decodeReadableBitmap(context, imageUri)
                ?: throw IllegalStateException("Unable to decode selected image.")

            val text = recognizeText(bitmap).trim()
            if (text.isEmpty()) {
                throw IllegalStateException("No readable UI text found in selected image.")
            }

            val lines = text.lines()
                .map { it.trim() }
                .filter { it.length >= 2 }
                .distinct()
                .take(16)

            "Build an Android app inspired by these detected screen labels and sections: ${lines.joinToString(", ")}"
        }
    }

    private fun decodeReadableBitmap(context: Context, uri: Uri): Bitmap? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = true
            }
        } else {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        }
    }

    private suspend fun recognizeText(bitmap: Bitmap): String =
        suspendCancellableCoroutine { continuation ->
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    recognizer.close()
                    if (continuation.isActive) {
                        continuation.resume(visionText.text.orEmpty())
                    }
                }
                .addOnFailureListener { exc ->
                    recognizer.close()
                    if (continuation.isActive) {
                        continuation.resumeWithException(exc)
                    }
                }
        }
}

object LocalPromptMatcher {

    private data class DomainPattern(
        val keywords: List<String>,
        val appName: String,
        val designStyle: String,
        val primaryColorHex: String,
        val features: List<String>,
        val defaultScreens: List<ScreenSpec>
    )

    private val PATTERNS = listOf(
        DomainPattern(
            keywords = listOf("fitness", "workout", "gym", "run", "health", "habit", "timer", "pomodoro"),
            appName = "PulseTrack Studio",
            designStyle = "Bold",
            primaryColorHex = "#10B981",
            features = listOf("Active Session Timer", "Daily Streak Tracker", "Offline Progress Log", "Custom Routine Builder"),
            defaultScreens = listOf(
                ScreenSpec("TodayDashboardScreen", "Today", "home", "Shows daily goals, streak count, and active routine summary."),
                ScreenSpec("WorkoutSessionScreen", "Workout", "timer", "Runs interval timers and logs completed sets offline."),
                ScreenSpec("ProgressStatsScreen", "Stats", "chart", "Displays weekly completion charts and personal records.")
            )
        ),
        DomainPattern(
            keywords = listOf("shop", "store", "cart", "product", "clothes", "ecommerce", "order", "catalog"),
            appName = "CatalogCraft Shop",
            designStyle = "Modern",
            primaryColorHex = "#6366F1",
            features = listOf("Product Catalog Grid", "Local Wishlist & Cart", "Category Filters", "Order Summary Preview"),
            defaultScreens = listOf(
                ScreenSpec("StoreHomeScreen", "Discover", "home", "Highlights featured items and curated collections."),
                ScreenSpec("CatalogBrowseScreen", "Catalog", "search", "Browse and filter products by category and price."),
                ScreenSpec("ShoppingBagScreen", "Bag", "cart", "Manage selected cart items and calculate totals offline.")
            )
        ),
        DomainPattern(
            keywords = listOf("note", "todo", "task", "journal", "checklist", "study", "flashcard", "budget", "expense"),
            appName = "NoteFlow Planner",
            designStyle = "Minimal",
            primaryColorHex = "#0EA5E9",
            features = listOf("Instant Offline Capture", "Tag & Priority Filtering", "Local Search Index", "Daily Summary Cards"),
            defaultScreens = listOf(
                ScreenSpec("InboxTasksScreen", "Inbox", "list", "Lists active tasks, notes, and pinned entries."),
                ScreenSpec("QuickCaptureScreen", "Capture", "add", "Create structured notes or entries with categories."),
                ScreenSpec("PlannerInsightsScreen", "Summary", "star", "Summarizes completed items and upcoming reminders.")
            )
        ),
        DomainPattern(
            keywords = listOf("scan", "camera", "vision", "ocr", "tflite", "model", "ai", "audio", "speech", "file", "apk"),
            appName = "EdgeTool Kit",
            designStyle = "Professional",
            primaryColorHex = "#14B8A6",
            features = listOf("On-Device Inference Runner", "Local Asset Inspector", "Latency Telemetry", "Zero-Cloud Execution"),
            defaultScreens = listOf(
                ScreenSpec("WorkspaceHomeScreen", "Workspace", "home", "Overview of local tools, inputs, and recent runs."),
                ScreenSpec("InspectorToolScreen", "Inspector", "settings", "Configure parameters and inspect local files offline."),
                ScreenSpec("HistoryResultsScreen", "Results", "chart", "Review processed outputs and execution timings.")
            )
        )
    )

    fun buildOfflineScaffold(
        prompt: String,
        tabPreference: TabBuildPreference = TabBuildPreference()
    ): AppProject {
        val cleanPrompt = prompt.trim()
        val lower = cleanPrompt.lowercase(Locale.US)

        val matched = PATTERNS.firstOrNull { pattern ->
            pattern.keywords.any { lower.contains(it) }
        } ?: DomainPattern(
            keywords = emptyList(),
            appName = deriveTitleFromPrompt(cleanPrompt),
            designStyle = "Modern",
            primaryColorHex = "#3B82F6",
            features = listOf("Clean Material 3 Navigation", "Offline Local Data State", "Search & Filter Controls", "Custom Settings Panel"),
            defaultScreens = listOf(
                ScreenSpec("MainOverviewScreen", "Home", "home", "Primary overview dashboard tailored to the user request."),
                ScreenSpec("ExploreItemsScreen", "Explore", "search", "Interactive searchable list of core items and actions."),
                ScreenSpec("PreferencesScreen", "Settings", "settings", "Configure app preferences and offline behavior.")
            )
        )

        val finalSpecs = resolveSpecsWithTabPreference(matched.defaultScreens, tabPreference)

        return AppProject(
            id = UUID.randomUUID().toString(),
            name = matched.appName,
            description = "Offline-ready Android app generated locally for: ${cleanPrompt.take(120)}",
            prompt = cleanPrompt,
            createdAt = System.currentTimeMillis(),
            features = matched.features,
            screens = finalSpecs.map { it.name },
            designStyle = matched.designStyle,
            primaryColorHex = matched.primaryColorHex,
            screenSpecs = finalSpecs
        )
    }

    private fun resolveSpecsWithTabPreference(
        defaults: List<ScreenSpec>,
        pref: TabBuildPreference
    ): List<ScreenSpec> {
        if (pref.mode == TabSelectionMode.AI_DECIDES) return defaults
        if (pref.customTabNames.isNotEmpty()) {
            return pref.customTabNames.mapIndexed { idx, tabLabel ->
                val pascal = tabLabel
                    .replace(Regex("[^A-Za-z0-9]"), "")
                    .ifBlank { "Tab${idx + 1}" }
                    .replaceFirstChar { it.uppercase() } + "Screen"
                val icon = when (idx) {
                    0 -> "home"
                    1 -> "list"
                    2 -> "search"
                    else -> "star"
                }
                ScreenSpec(
                    name = pascal,
                    label = tabLabel.take(16),
                    icon = icon,
                    purpose = "Handles $tabLabel content and local actions."
                )
            }
        }
        val count = (pref.effectiveRequestedCount ?: defaults.size).coerceIn(1, TabBuildPreference.MAX_USER_TABS)
        return List(count) { idx ->
            defaults.getOrElse(idx) {
                ScreenSpec(
                    name = "Tab${idx + 1}Screen",
                    label = "Tab ${idx + 1}",
                    icon = "list",
                    purpose = "Additional custom workspace tab ${idx + 1}."
                )
            }
        }
    }

    private fun deriveTitleFromPrompt(prompt: String): String {
        val words = prompt.split(Regex("\\s+"))
            .map { it.replace(Regex("[^A-Za-z0-9]"), "") }
            .filter { it.length >= 3 }
            .take(2)
        return if (words.isEmpty()) {
            "Local Studio App"
        } else {
            words.joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } } + " App"
        }
    }
}

object LocalAiCapabilitiesList {

    fun buildCapabilityList(
        context: Context,
        installedLlmFiles: Set<String>,
        selectedLlmFile: String?,
        llmState: LocalLlmState,
        tfliteModelsCount: Int,
        verifiedTfliteCount: Int
    ): List<LocalAiCapabilitySummary> {
        val hasLlm = installedLlmFiles.isNotEmpty()
        val speechAvailable = OfflineSpeechDictation.isAvailable(context)

        return listOf(
            LocalAiCapabilitySummary(
                id = "cap_litert_lm",
                title = "LiteRT-LM On-Device LLM Engine",
                engineName = "litertlm-android 0.18.0",
                category = LocalAiCategory.LLM_ENGINE,
                description = if (hasLlm) {
                    "Runs ${selectedLlmFile ?: installedLlmFiles.first()} locally on GPU/CPU for offline app generation."
                } else {
                    "Ready for .litertlm text models (Gemma 4 / Qwen 3). Download one model below to enable."
                },
                statusBadge = when {
                    llmState.isReady -> "Loaded (${llmState.activeBackend})"
                    hasLlm -> "Model Ready"
                    else -> "Needs .litertlm"
                },
                isReady = hasLlm || llmState.isReady,
                requiresDownload = true
            ),
            LocalAiCapabilitySummary(
                id = "cap_litert_compiled",
                title = "LiteRT CompiledModel (.tflite) Runtime",
                engineName = "litert 2.3.0 + GPU",
                category = LocalAiCategory.TFLITE_RUNTIME,
                description = "Inspects, warms up, and benchmarks local .tflite models on GPU/XNNPACK CPU before APK bundling.",
                statusBadge = if (tfliteModelsCount > 0) {
                    "$verifiedTfliteCount/$tfliteModelsCount Verified"
                } else {
                    "Runtime Ready"
                },
                isReady = true,
                requiresDownload = false
            ),
            LocalAiCapabilitySummary(
                id = "cap_mlkit_ocr",
                title = "Sketch & Screenshot UI OCR",
                engineName = "ML Kit Text Recognition",
                category = LocalAiCategory.DEVICE_TOOLS,
                description = "Extracts screen titles, labels, and buttons from wireframe or app screenshots offline via Photo Picker.",
                statusBadge = "Ready Offline",
                isReady = true,
                requiresDownload = false
            ),
            LocalAiCapabilitySummary(
                id = "cap_speech_dictation",
                title = "Voice-to-App Speech Dictation",
                engineName = "Android SpeechRecognizer",
                category = LocalAiCategory.DEVICE_TOOLS,
                description = "Dictate app ideas directly into the Create prompt using offline-preferred speech recognition.",
                statusBadge = if (speechAvailable) "Available" else "Unavailable",
                isReady = speechAvailable,
                requiresDownload = false
            ),
            LocalAiCapabilitySummary(
                id = "cap_local_rag",
                title = "Offline Prompt Pattern Matcher",
                engineName = "Local Scaffold RAG",
                category = LocalAiCategory.DEVICE_TOOLS,
                description = "Zero-download semantic domain matcher producing instant multi-screen AppProject scaffolds offline.",
                statusBadge = "Always Ready",
                isReady = true,
                requiresDownload = false
            )
        )
    }
}
