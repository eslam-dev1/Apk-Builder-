package com.example.ai

import android.content.Context
import android.os.Build
import com.example.data.GeminiService
import com.example.model.AppProject
import com.example.model.TabBuildPreference
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class LlmBackendChoice(val label: String) {
    AUTO_GPU_FALLBACK("Auto (GPU → CPU)"),
    GPU("GPU"),
    CPU("CPU")
}

data class LocalLlmState(
    val isLoading: Boolean = false,
    val isReady: Boolean = false,
    val isBusy: Boolean = false,
    val loadedModelFile: String? = null,
    val activeBackend: String = "Idle",
    val lastLatencyMs: Long? = null,
    val lastOutputPreview: String = "",
    val errorMessage: String? = null
)

/**
 * Single-owner manager for on-device `.litertlm` LLM inference using Google AI Edge LiteRT-LM.
 * Follows the strict lifecycle guarantees:
 * - Initialization, inference, cancellation, and closing all execute on one dedicated executor.
 * - `Engine` and `Conversation` are closed cleanly once via Mutex synchronization.
 * - Falls back automatically from GPU to CPU if GPU drivers/OpenCL are unavailable.
 */
object LocalLlmEngine {

    private val singleThreadExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "LocalLlmEngineThread").apply { isDaemon = true }
    }
    private val engineDispatcher = singleThreadExecutor.asCoroutineDispatcher()
    private val engineScope = CoroutineScope(SupervisorJob() + engineDispatcher)
    private val lifecycleMutex = Mutex()

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var loadedModelPath: String? = null

    private val _state = MutableStateFlow(LocalLlmState())
    val state: StateFlow<LocalLlmState> = _state.asStateFlow()

    fun isNativeRuntimeSupported(): Boolean {
        return runCatching {
            val abi = Build.SUPPORTED_ABIS?.firstOrNull().orEmpty()
            abi.contains("arm64", ignoreCase = true) ||
                abi.contains("armeabi", ignoreCase = true) ||
                abi.contains("x86_64", ignoreCase = true)
        }.getOrDefault(false)
    }

    suspend fun loadModel(
        context: Context,
        fileName: String,
        backendChoice: LlmBackendChoice = LlmBackendChoice.AUTO_GPU_FALLBACK,
        systemInstruction: String = "You are a concise, expert Android developer assistant."
    ): Result<String> = withContext(engineDispatcher) {
        lifecycleMutex.withLock {
            val modelFile = File(com.example.data.ai.ModelCatalog.modelsDir(context), fileName)
            if (!modelFile.isFile || modelFile.length() == 0L) {
                val err = IllegalStateException("Local model file not found: $fileName")
                _state.update { it.copy(errorMessage = err.message, isLoading = false) }
                return@withLock Result.failure(err)
            }

            if (engine != null && loadedModelPath == modelFile.absolutePath && _state.value.isReady) {
                return@withLock Result.success(_state.value.activeBackend)
            }

            releaseLocked()
            _state.update {
                it.copy(
                    isLoading = true,
                    isReady = false,
                    loadedModelFile = fileName,
                    errorMessage = null
                )
            }

            val backendsToTry = when (backendChoice) {
                LlmBackendChoice.AUTO_GPU_FALLBACK -> listOf(Backend.GPU() to "GPU", Backend.CPU() to "CPU")
                LlmBackendChoice.GPU -> listOf(Backend.GPU() to "GPU")
                LlmBackendChoice.CPU -> listOf(Backend.CPU() to "CPU")
            }

            val cachePath = context.applicationContext.cacheDir.path
            var lastError: Throwable? = null

            for ((backend, label) in backendsToTry) {
                var candidateEngine: Engine? = null
                try {
                    val config = EngineConfig(
                        modelPath = modelFile.absolutePath,
                        backend = backend,
                        cacheDir = cachePath
                    )
                    candidateEngine = Engine(config).also { it.initialize() }
                    val newConversation = candidateEngine.createConversation(
                        ConversationConfig(
                            systemInstruction = Contents.of(systemInstruction),
                            samplerConfig = SamplerConfig(
                                topK = 20,
                                topP = 0.9,
                                temperature = 0.35
                            )
                        )
                    )
                    engine = candidateEngine
                    conversation = newConversation
                    loadedModelPath = modelFile.absolutePath
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isReady = true,
                            loadedModelFile = fileName,
                            activeBackend = label,
                            errorMessage = null
                        )
                    }
                    return@withLock Result.success(label)
                } catch (t: Throwable) {
                    lastError = t
                    runCatching { candidateEngine?.close() }
                }
            }

            val finalMsg = lastError?.localizedMessage ?: "Failed to initialize LiteRT-LM engine."
            _state.update {
                it.copy(
                    isLoading = false,
                    isReady = false,
                    activeBackend = "Error",
                    errorMessage = finalMsg
                )
            }
            Result.failure(lastError ?: IllegalStateException(finalMsg))
        }
    }

    suspend fun generateText(
        context: Context,
        modelFileName: String,
        prompt: String,
        systemInstruction: String = "You are an expert Android engineer. Be clear and concise.",
        backendChoice: LlmBackendChoice = LlmBackendChoice.AUTO_GPU_FALLBACK,
        onStreamChunk: (String) -> Unit = {}
    ): Result<String> = withContext(engineDispatcher) {
        val loadRes = loadModel(
            context = context,
            fileName = modelFileName,
            backendChoice = backendChoice,
            systemInstruction = systemInstruction
        )
        if (loadRes.isFailure) {
            return@withContext Result.failure(loadRes.exceptionOrNull()!!)
        }

        lifecycleMutex.withLock {
            val activeEngine = engine
                ?: return@withLock Result.failure(IllegalStateException("LiteRT-LM engine is not loaded"))

            _state.update { it.copy(isBusy = true, errorMessage = null) }
            val startMs = System.currentTimeMillis()
            val responseBuilder = StringBuilder()

            try {
                // Fresh conversation keeps prompt context bounded & avoids stale history across generations
                conversation?.let { oldConv ->
                    runCatching { oldConv.close() }
                    conversation = null
                }
                val freshConv = activeEngine.createConversation(
                    ConversationConfig(
                        systemInstruction = Contents.of(systemInstruction),
                        samplerConfig = SamplerConfig(
                            topK = 20,
                            topP = 0.9,
                            temperature = 0.3
                        )
                    )
                )
                conversation = freshConv

                freshConv.sendMessageAsync(prompt).collect { chunk ->
                    val piece = chunk.toString()
                    responseBuilder.append(piece)
                    onStreamChunk(piece)
                }

                val cleaned = ModelManager.stripThinkTags(responseBuilder.toString())
                val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
                _state.update {
                    it.copy(
                        isBusy = false,
                        lastLatencyMs = elapsed,
                        lastOutputPreview = cleaned.take(240)
                    )
                }
                Result.success(cleaned)
            } catch (e: CancellationException) {
                runCatching { conversation?.cancelProcess() }
                _state.update { it.copy(isBusy = false) }
                throw e
            } catch (t: Throwable) {
                val msg = t.localizedMessage ?: "Offline LLM inference failed"
                _state.update { it.copy(isBusy = false, errorMessage = msg) }
                Result.failure(t)
            }
        }
    }

    suspend fun generateAppProjectOffline(
        context: Context,
        modelFileName: String,
        prompt: String,
        tabPreference: TabBuildPreference,
        geminiService: GeminiService
    ): Result<AppProject> {
        val systemPrompt = geminiService.buildGenerateAppSystemPrompt(tabPreference)
        val rawResult = generateText(
            context = context,
            modelFileName = modelFileName,
            prompt = prompt,
            systemInstruction = systemPrompt
        )
        return rawResult.mapCatching { rawText ->
            val firstBrace = rawText.indexOf('{')
            val lastBrace = rawText.lastIndexOf('}')
            if (firstBrace == -1 || lastBrace <= firstBrace) {
                throw IllegalStateException("Local model did not return a JSON project object.")
            }
            val jsonSlice = rawText.substring(firstBrace, lastBrace + 1)
            geminiService.parseProjectJson(
                cleanJson = jsonSlice,
                originalPrompt = prompt,
                tabPreference = tabPreference
            )
        }
    }

    suspend fun unload() = withContext(engineDispatcher) {
        lifecycleMutex.withLock {
            releaseLocked()
        }
    }

    private suspend fun releaseLocked() {
        _state.update { it.copy(isReady = false, isLoading = false) }
        if (_state.value.isBusy) {
            runCatching { conversation?.cancelProcess() }
            delay(100L)
        }
        conversation?.let { conv ->
            runCatching { conv.close() }
        }
        conversation = null
        engine?.let { eng ->
            runCatching { eng.close() }
        }
        engine = null
        loadedModelPath = null
        _state.update {
            it.copy(
                isReady = false,
                isBusy = false,
                loadedModelFile = null,
                activeBackend = "Idle"
            )
        }
    }
}
