package com.example.ai

import android.content.Context
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.Environment
import com.google.ai.edge.litert.LiteRtException
import com.google.ai.edge.litert.TensorBuffer
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

data class TfliteBenchmarkResult(
    val fileName: String,
    val isVerified: Boolean,
    val acceleratorUsed: String,
    val compileMs: Long,
    val inferenceMs: Long,
    val inputTensorCount: Int,
    val outputTensorCount: Int,
    val statusNote: String,
    val errorMessage: String? = null
)

/**
 * Executes real LiteRT CompiledModel inspection, warm-up, and latency benchmarking on
 * local `.tflite` files using a dedicated single-thread executor and proper TensorBuffer cleanup.
 */
object LiteRtRuntimeManager {

    private val singleThreadExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "LiteRtCompiledModelThread").apply { isDaemon = true }
    }
    private val runtimeDispatcher = singleThreadExecutor.asCoroutineDispatcher()

    private val environment by lazy { Environment.create() }

    private val _benchmarks = MutableStateFlow<Map<String, TfliteBenchmarkResult>>(emptyMap())
    val benchmarks: StateFlow<Map<String, TfliteBenchmarkResult>> = _benchmarks.asStateFlow()

    private val _testingFile = MutableStateFlow<String?>(null)
    val testingFile: StateFlow<String?> = _testingFile.asStateFlow()

    suspend fun inspectAndBenchmarkModel(
        context: Context,
        fileName: String,
        preferGpu: Boolean = true
    ): TfliteBenchmarkResult = withContext(runtimeDispatcher) {
        _testingFile.value = fileName
        try {
            val modelFile = TfliteModelManager.findModelFile(context, fileName)
            if (modelFile == null || !modelFile.isFile || modelFile.length() == 0L) {
                val missing = TfliteBenchmarkResult(
                    fileName = fileName,
                    isVerified = false,
                    acceleratorUsed = "None",
                    compileMs = 0L,
                    inferenceMs = 0L,
                    inputTensorCount = 0,
                    outputTensorCount = 0,
                    statusNote = "Model file missing on device",
                    errorMessage = "File not found: $fileName"
                )
                _benchmarks.update { it + (fileName to missing) }
                return@withContext missing
            }

            val result = tryCompileAndWarmup(
                context = context,
                modelFile = modelFile,
                fileName = fileName,
                preferGpu = preferGpu
            )
            _benchmarks.update { it + (fileName to result) }
            result
        } finally {
            _testingFile.value = null
        }
    }

    private fun tryCompileAndWarmup(
        context: Context,
        modelFile: File,
        fileName: String,
        preferGpu: Boolean
    ): TfliteBenchmarkResult {
        val candidates = if (preferGpu) {
            listOf(Accelerator.GPU to "GPU", Accelerator.CPU to "CPU (XNNPACK)")
        } else {
            listOf(Accelerator.CPU to "CPU (XNNPACK)")
        }

        var lastError: Throwable? = null
        for ((accelerator, label) in candidates) {
            var compiledModel: CompiledModel? = null
            val inputs = mutableListOf<TensorBuffer>()
            val outputs = mutableListOf<TensorBuffer>()
            try {
                val compileStart = System.currentTimeMillis()
                val options = CompiledModel.Options(accelerator).apply {
                    if (accelerator == Accelerator.CPU) {
                        val cacheFile = File(
                            context.codeCacheDir,
                            "${fileName}_${modelFile.length()}.xnnpack_cache"
                        )
                        cpuOptions = CompiledModel.CpuOptions(
                            numThreads = 4,
                            xnnPackFlags = 0x23,
                            xnnPackWeightCachePath = cacheFile.path
                        )
                    }
                }
                compiledModel = CompiledModel.create(modelFile.absolutePath, options, environment)
                val compileMs = (System.currentTimeMillis() - compileStart).coerceAtLeast(1L)

                inputs += compiledModel.createInputBuffers()
                outputs += compiledModel.createOutputBuffers()

                val inferStart = System.currentTimeMillis()
                compiledModel.run(inputs, outputs)
                // Read back first output tensor if float-compatible, otherwise ignore readback error
                outputs.firstOrNull()?.let { outBuf ->
                    runCatching { outBuf.readFloat() }
                }
                val inferenceMs = (System.currentTimeMillis() - inferStart).coerceAtLeast(1L)

                return TfliteBenchmarkResult(
                    fileName = fileName,
                    isVerified = true,
                    acceleratorUsed = label,
                    compileMs = compileMs,
                    inferenceMs = inferenceMs,
                    inputTensorCount = inputs.size,
                    outputTensorCount = outputs.size,
                    statusNote = "Verified on $label • ${inputs.size} in / ${outputs.size} out • Load ${compileMs}ms • Run ${inferenceMs}ms"
                )
            } catch (e: LiteRtException) {
                lastError = e
            } catch (t: Throwable) {
                lastError = t
            } finally {
                (inputs + outputs).forEach { buf -> runCatching { buf.close() } }
                runCatching { compiledModel?.close() }
            }
        }

        val errMsg = lastError?.localizedMessage ?: "Unsupported or invalid .tflite model graph"
        return TfliteBenchmarkResult(
            fileName = fileName,
            isVerified = false,
            acceleratorUsed = "Failed",
            compileMs = 0L,
            inferenceMs = 0L,
            inputTensorCount = 0,
            outputTensorCount = 0,
            statusNote = "LiteRT verification failed",
            errorMessage = errMsg
        )
    }

    fun clearResult(fileName: String) {
        _benchmarks.update { it - fileName }
    }
}
