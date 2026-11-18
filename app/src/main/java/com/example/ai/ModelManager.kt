package com.example.ai

import android.content.Context
import android.net.Uri
import android.os.StatFs
import com.example.data.ai.LocalModel
import com.example.data.ai.ModelCatalog
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.security.MessageDigest
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class DownloadStatus {
    IDLE,
    PROBING,
    DOWNLOADING,
    PAUSED,
    VERIFYING,
    COMPLETED,
    ERROR
}

data class ModelDownloadProgress(
    val modelId: String = "",
    val fileName: String = "",
    val status: DownloadStatus = DownloadStatus.IDLE,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long? = null,
    val speedBytesPerSec: Long = 0L,
    val etaSeconds: Long? = null,
    val warningMessage: String? = null,
    val errorMessage: String? = null
)

data class QueuedDownloadItem(
    val model: LocalModel,
    val priority: Int,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long? = model.expectedBytes,
    val isActive: Boolean = false,
    val isPaused: Boolean = false
)

data class GlobalDownloadProgress(
    val activeModelId: String? = null,
    val activeModelTitle: String? = null,
    val totalQueuedCount: Int = 0,
    val completedInBatchCount: Int = 0,
    val totalBytesDownloaded: Long = 0L,
    val totalBytesOverall: Long = 0L,
    val fraction: Float = 0f,
    val speedBytesPerSec: Long = 0L,
    val etaSeconds: Long? = null,
    val isDownloading: Boolean = false,
    val isPaused: Boolean = false
)

data class PreflightCheckResult(
    val model: LocalModel,
    val resolvedSizeBytes: Long?,
    val freeSpaceBytes: Long,
    val hasEnoughSpace: Boolean,
    val confirmMessage: String,
    val errorMessage: String? = null
)

object ModelManager {

    private const val PREFS_NAME = "offline_ai_model_prefs"
    private const val KEY_ACTIVE_MODEL_ID = "active_model_id"
    private const val KEY_ACTIVE_MODEL_FILE = "active_model_file"
    private const val KEY_CUSTOM_URL = "custom_model_url"
    private const val KEY_CUSTOM_FILENAME = "custom_model_filename"
    private const val KEY_PERSISTED_QUEUE_JSON = "persisted_download_queue_json"
    private const val KEY_PERSISTED_QUEUE_PAUSED = "persisted_download_queue_paused"

    private const val CONNECT_TIMEOUT_MS = 20_000
    private const val READ_TIMEOUT_MS = 60_000
    private const val MAX_REDIRECT_HOPS = 5
    private const val MAX_RETRY_ATTEMPTS = 3
    private const val EXTRA_FREE_SPACE_BYTES = 300L * 1024L * 1024L // 300 MB

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeJob: Job? = null
    private var appContextRef: Context? = null

    private val queueLock = Any()
    private val mutableQueue = mutableListOf<LocalModel>()
    private val resolvedSizeCache = mutableMapOf<String, Long>()
    private val batchCompletedIds = mutableSetOf<String>()
    private val batchCompletedBytes = mutableMapOf<String, Long>()
    private var isQueuePaused = false
    private var hasRestoredPersistedQueue = false

    private val _downloadProgress = MutableStateFlow(ModelDownloadProgress())
    val downloadProgress: StateFlow<ModelDownloadProgress> = _downloadProgress.asStateFlow()

    private val _downloadQueue = MutableStateFlow<List<QueuedDownloadItem>>(emptyList())
    val downloadQueue: StateFlow<List<QueuedDownloadItem>> = _downloadQueue.asStateFlow()

    private val _globalProgress = MutableStateFlow(GlobalDownloadProgress())
    val globalProgress: StateFlow<GlobalDownloadProgress> = _globalProgress.asStateFlow()

    private val _installedFiles = MutableStateFlow<Set<String>>(emptySet())
    val installedFiles: StateFlow<Set<String>> = _installedFiles.asStateFlow()

    private val _selectedModelFile = MutableStateFlow<String?>(null)
    val selectedModelFile: StateFlow<String?> = _selectedModelFile.asStateFlow()

    fun refreshState(context: Context) {
        appContextRef = context.applicationContext
        val dir = ModelCatalog.modelsDir(context)
        val files = dir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".litertlm", ignoreCase = true) }
            ?.map { it.name }
            ?.toSet()
            .orEmpty()
        _installedFiles.value = files

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedFile = prefs.getString(KEY_ACTIVE_MODEL_FILE, null)
        val nothinkFile = ModelCatalog.QWEN3_06B_NOTHINK.fileName

        val resolvedActive = when {
            savedFile != null && savedFile in files -> savedFile
            nothinkFile in files -> nothinkFile
            files.isNotEmpty() -> files.first()
            else -> savedFile
        }
        _selectedModelFile.value = resolvedActive
        restorePersistedQueueIfNeeded(context.applicationContext)
        emitQueueAndGlobalProgress(context.applicationContext)
    }

    fun selectModel(context: Context, modelId: String, fileName: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ACTIVE_MODEL_ID, modelId)
            .putString(KEY_ACTIVE_MODEL_FILE, fileName)
            .apply()
        _selectedModelFile.value = fileName
    }

    fun getSavedCustomUrl(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CUSTOM_URL, "")
            .orEmpty()

    fun getSavedCustomFileName(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CUSTOM_FILENAME, "")
            .orEmpty()

    fun saveCustomModelInputs(context: Context, url: String, fileName: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CUSTOM_URL, url.trim())
            .putString(KEY_CUSTOM_FILENAME, fileName.trim())
            .apply()
    }

    fun buildCustomLocalModel(url: String, customFileName: String): LocalModel {
        val cleanUrl = url.trim()
        val rawName = customFileName.trim().ifBlank {
            cleanUrl.substringBefore('?').substringAfterLast('/').ifBlank { "custom_model.litertlm" }
        }
        val safeName = ModelCatalog.sanitizeFileName(rawName)
        return LocalModel(
            id = "custom_${safeName.lowercase(Locale.US)}",
            title = "Custom Model ($safeName)",
            fileName = safeName,
            url = cleanUrl,
            expectedBytes = null,
            sha256 = null
        )
    }

    fun hasPartialDownload(context: Context, fileName: String): Long {
        val part = ModelCatalog.partFile(context, fileName)
        return if (part.exists()) part.length() else 0L
    }

    /**
     * Performs HTTPS check, probes HEAD / Content-Length (following redirects), and verifies
     * free storage space via StatFs >= size + 300 MB before showing the confirmation dialog.
     */
    suspend fun preflightCheck(
        context: Context,
        model: LocalModel
    ): PreflightCheckResult = withContext(Dispatchers.IO) {
        val cleanUrl = model.url.trim()
        if (!cleanUrl.startsWith("https://", ignoreCase = true)) {
            return@withContext PreflightCheckResult(
                model = model,
                resolvedSizeBytes = model.expectedBytes,
                freeSpaceBytes = 0L,
                hasEnoughSpace = false,
                confirmMessage = "",
                errorMessage = "HTTPS only: http:// links are rejected for security."
            )
        }

        val dir = ModelCatalog.modelsDir(context)
        val freeBytes = runCatching { StatFs(dir.absolutePath).availableBytes }.getOrDefault(Long.MAX_VALUE)

        var probedLength: Long? = model.expectedBytes
        var probeError: String? = null

        runCatching {
            val headLen = probeRemoteContentLength(cleanUrl)
            if (headLen != null && headLen > 0L) {
                probedLength = headLen
            }
        }.onFailure { err ->
            if (probedLength == null) {
                probeError = mapExceptionToHumanMessage(err)
            }
        }

        if (probedLength != null && probedLength!! > 0L) {
            synchronized(queueLock) {
                resolvedSizeCache[model.id] = probedLength!!
            }
        }

        val alreadyDownloaded = hasPartialDownload(context, model.fileName)
        val remainingBytes = ((probedLength ?: 0L) - alreadyDownloaded).coerceAtLeast(0L)
        val requiredBytes = remainingBytes + EXTRA_FREE_SPACE_BYTES
        val enoughSpace = freeBytes >= requiredBytes

        val sizeLabel = when {
            probedLength != null && probedLength!! > 0L -> ModelCatalog.formatBytes(probedLength)
            model.id == ModelCatalog.QWEN3_06B.id -> "~586 MB"
            else -> "size determined during download"
        }

        val resumeSuffix = if (alreadyDownloaded > 0L) {
            " (resuming from ${ModelCatalog.formatBytes(alreadyDownloaded)})"
        } else {
            ""
        }

        val errorMsg = when {
            !enoughSpace -> {
                val neededFormatted = ModelCatalog.formatBytes(requiredBytes)
                val freeFormatted = ModelCatalog.formatBytes(freeBytes)
                "Not enough storage space. Requires $neededFormatted (model + 300 MB buffer), but only $freeFormatted is available."
            }
            probeError != null && model.expectedBytes == null -> probeError
            else -> null
        }

        PreflightCheckResult(
            model = model,
            resolvedSizeBytes = probedLength,
            freeSpaceBytes = freeBytes,
            hasEnoughSpace = enoughSpace,
            confirmMessage = "$sizeLabel — use Wi-Fi$resumeSuffix",
            errorMessage = errorMsg
        )
    }

    /**
     * Queues a model download at the end of the priority queue (or starts immediately if idle).
     */
    fun enqueueDownload(context: Context, model: LocalModel) {
        val appContext = context.applicationContext
        appContextRef = appContext
        val shouldStart = synchronized(queueLock) {
            if (mutableQueue.isEmpty()) {
                batchCompletedIds.clear()
                batchCompletedBytes.clear()
            }
            batchCompletedIds.remove(model.id)
            batchCompletedBytes.remove(model.id)
            if (mutableQueue.none { it.id == model.id }) {
                mutableQueue.add(model)
            }
            isQueuePaused = false
            activeJob?.isActive != true
        }
        emitQueueAndGlobalProgress(appContext)
        if (shouldStart) {
            launchQueueWorker(appContext)
        }
    }

    /**
     * Queues multiple models in their given priority order without resetting any currently active download.
     */
    fun enqueueAll(context: Context, models: List<LocalModel>) {
        if (models.isEmpty()) return
        val appContext = context.applicationContext
        appContextRef = appContext
        val shouldStart = synchronized(queueLock) {
            if (mutableQueue.isEmpty()) {
                batchCompletedIds.clear()
                batchCompletedBytes.clear()
            }
            models.forEach { model ->
                batchCompletedIds.remove(model.id)
                batchCompletedBytes.remove(model.id)
                if (mutableQueue.none { it.id == model.id }) {
                    mutableQueue.add(model)
                }
            }
            isQueuePaused = false
            activeJob?.isActive != true && mutableQueue.isNotEmpty()
        }
        emitQueueAndGlobalProgress(appContext)
        if (shouldStart) {
            launchQueueWorker(appContext)
        }
    }

    /**
     * Starts downloading a model immediately (places it at priority #1 if queued/idle,
     * preserving any other queued downloads behind it).
     */
    fun startDownload(context: Context, model: LocalModel) {
        val appContext = context.applicationContext
        appContextRef = appContext
        var needsRestart = false
        synchronized(queueLock) {
            if (mutableQueue.isEmpty()) {
                batchCompletedIds.clear()
                batchCompletedBytes.clear()
            }
            batchCompletedIds.remove(model.id)
            batchCompletedBytes.remove(model.id)
            val existingIdx = mutableQueue.indexOfFirst { it.id == model.id }
            if (existingIdx == -1) {
                mutableQueue.add(0, model)
                needsRestart = activeJob?.isActive == true
            } else if (existingIdx > 0) {
                mutableQueue.removeAt(existingIdx)
                mutableQueue.add(0, model)
                needsRestart = activeJob?.isActive == true
            }
            isQueuePaused = false
        }
        emitQueueAndGlobalProgress(appContext)
        if (needsRestart) {
            activeJob?.cancel()
            activeJob = null
        }
        if (activeJob?.isActive != true) {
            launchQueueWorker(appContext)
        }
    }

    /**
     * Moves a queued model one position higher in priority.
     * If moved into index 0 while another download is active, preempts the active download
     * (preserving its `.part` file on disk for instant resume later) and starts the promoted model.
     */
    fun moveQueueItemUp(modelId: String) {
        val appContext = appContextRef
        var switchedHead = false
        synchronized(queueLock) {
            val idx = mutableQueue.indexOfFirst { it.id == modelId }
            if (idx > 0) {
                val item = mutableQueue.removeAt(idx)
                mutableQueue.add(idx - 1, item)
                if (idx - 1 == 0 && activeJob?.isActive == true) {
                    switchedHead = true
                }
            }
        }
        appContext?.let { ctx ->
            emitQueueAndGlobalProgress(ctx)
            if (switchedHead) {
                activeJob?.cancel()
                activeJob = null
                launchQueueWorker(ctx)
            }
        }
    }

    /**
     * Moves a queued model one position lower in priority.
     */
    fun moveQueueItemDown(modelId: String) {
        val appContext = appContextRef
        var switchedHead = false
        synchronized(queueLock) {
            val idx = mutableQueue.indexOfFirst { it.id == modelId }
            if (idx in 0 until mutableQueue.lastIndex) {
                val item = mutableQueue.removeAt(idx)
                mutableQueue.add(idx + 1, item)
                if (idx == 0 && activeJob?.isActive == true) {
                    switchedHead = true
                }
            }
        }
        appContext?.let { ctx ->
            emitQueueAndGlobalProgress(ctx)
            if (switchedHead) {
                activeJob?.cancel()
                activeJob = null
                launchQueueWorker(ctx)
            }
        }
    }

    /**
     * Promotes any queued model directly to priority #1 (active).
     */
    fun promoteToTop(context: Context, modelId: String) {
        val target = synchronized(queueLock) { mutableQueue.find { it.id == modelId } } ?: return
        startDownload(context, target)
    }

    /**
     * Removes a model from the download queue. If it was actively downloading, immediately advances
     * to the next queued model.
     */
    fun removeFromQueue(context: Context, modelId: String, deletePartial: Boolean = true) {
        val appContext = context.applicationContext
        appContextRef = appContext
        var removedModel: LocalModel? = null
        var wasHeadActive = false
        var hasMore = false

        synchronized(queueLock) {
            val idx = mutableQueue.indexOfFirst { it.id == modelId }
            if (idx >= 0) {
                removedModel = mutableQueue.removeAt(idx)
                wasHeadActive = (idx == 0 && activeJob?.isActive == true)
                hasMore = mutableQueue.isNotEmpty()
            }
        }

        if (wasHeadActive) {
            activeJob?.cancel()
            activeJob = null
        }

        if (deletePartial && removedModel != null) {
            val part = ModelCatalog.partFile(appContext, removedModel!!.fileName)
            if (part.exists()) {
                part.delete()
            }
        }

        if (!hasMore) {
            _downloadProgress.value = ModelDownloadProgress(status = DownloadStatus.IDLE)
        }
        emitQueueAndGlobalProgress(appContext)
        refreshState(appContext)

        if (wasHeadActive && hasMore && !isQueuePaused) {
            launchQueueWorker(appContext)
        }
    }

    fun resumeQueue(context: Context) {
        val appContext = context.applicationContext
        appContextRef = appContext
        val hasItems = synchronized(queueLock) {
            isQueuePaused = false
            mutableQueue.isNotEmpty()
        }
        emitQueueAndGlobalProgress(appContext)
        if (hasItems && activeJob?.isActive != true) {
            launchQueueWorker(appContext)
        }
    }

    fun pauseDownload() {
        synchronized(queueLock) {
            isQueuePaused = true
        }
        activeJob?.cancel()
        activeJob = null
        _downloadProgress.update { current ->
            if (current.status == DownloadStatus.DOWNLOADING || current.status == DownloadStatus.PROBING) {
                current.copy(
                    status = DownloadStatus.PAUSED,
                    speedBytesPerSec = 0L,
                    etaSeconds = null
                )
            } else {
                current
            }
        }
        appContextRef?.let { emitQueueAndGlobalProgress(it) }
    }

    fun clearQueue(context: Context) {
        val appContext = context.applicationContext
        appContextRef = appContext
        val clearedModels = synchronized(queueLock) {
            val copy = mutableQueue.toList()
            mutableQueue.clear()
            batchCompletedIds.clear()
            batchCompletedBytes.clear()
            isQueuePaused = false
            copy
        }
        activeJob?.cancel()
        activeJob = null
        clearedModels.forEach { model ->
            val part = ModelCatalog.partFile(appContext, model.fileName)
            if (part.exists()) part.delete()
        }
        _downloadProgress.value = ModelDownloadProgress(status = DownloadStatus.IDLE)
        emitQueueAndGlobalProgress(appContext)
        refreshState(appContext)
    }

    fun cancelDownload(context: Context, model: LocalModel) {
        removeFromQueue(context, model.id, deletePartial = true)
    }

    fun deleteModel(context: Context, model: LocalModel) {
        val appContext = context.applicationContext
        val wasInQueue = synchronized(queueLock) { mutableQueue.any { it.id == model.id } }
        if (wasInQueue) {
            removeFromQueue(appContext, model.id, deletePartial = true)
        } else if (_downloadProgress.value.modelId == model.id) {
            activeJob?.cancel()
            activeJob = null
            _downloadProgress.value = ModelDownloadProgress()
        }
        synchronized(queueLock) {
            batchCompletedIds.remove(model.id)
            batchCompletedBytes.remove(model.id)
        }
        val finalFile = ModelCatalog.modelFile(appContext, model.fileName)
        val partFile = ModelCatalog.partFile(appContext, model.fileName)
        if (finalFile.exists()) finalFile.delete()
        if (partFile.exists()) partFile.delete()
        refreshState(appContext)
    }

    suspend fun importModelFromUri(
        context: Context,
        sourceUri: Uri,
        suggestedName: String?
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val fileName = ModelCatalog.sanitizeFileName(suggestedName ?: "imported_model.litertlm")
            val destFile = ModelCatalog.modelFile(context, fileName)
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output, bufferSize = 256 * 1024)
                }
            } ?: throw IOException("Unable to open selected model file.")

            if (!destFile.exists() || destFile.length() == 0L) {
                destFile.delete()
                throw IOException("Imported file is empty.")
            }

            selectModel(context, "imported_$fileName", destFile.name)
            refreshState(context)
            destFile.name
        }
    }

    /**
     * Test helper / deterministic seed for queue state in unit tests without needing network IO.
     */
    internal fun setQueueOrderForTest(models: List<LocalModel>) {
        synchronized(queueLock) {
            hasRestoredPersistedQueue = true
            mutableQueue.clear()
            mutableQueue.addAll(models)
        }
        appContextRef?.let { emitQueueAndGlobalProgress(it) }
    }

    internal fun resetQueueForRestartSimulation() {
        synchronized(queueLock) {
            hasRestoredPersistedQueue = false
            mutableQueue.clear()
            isQueuePaused = false
        }
        _downloadProgress.value = ModelDownloadProgress(status = DownloadStatus.IDLE)
    }

    internal fun snapshotQueueIds(): List<String> =
        synchronized(queueLock) { mutableQueue.map { it.id } }

    internal fun hasPendingOrActiveDownloads(): Boolean = synchronized(queueLock) {
        mutableQueue.isNotEmpty() || activeJob?.isActive == true
    }

    internal fun restorePersistedQueueIfNeeded(context: Context) {
        val appContext = context.applicationContext
        appContextRef = appContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rawJson = prefs.getString(KEY_PERSISTED_QUEUE_JSON, null)
        val savedPaused = prefs.getBoolean(KEY_PERSISTED_QUEUE_PAUSED, false)

        var restoredHead: LocalModel? = null
        synchronized(queueLock) {
            if (hasRestoredPersistedQueue) return
            hasRestoredPersistedQueue = true
            if (mutableQueue.isEmpty() && !rawJson.isNullOrBlank()) {
                val parsed = deserializeQueue(rawJson).filter { model ->
                    val installed = ModelCatalog.modelFile(appContext, model.fileName)
                    !(installed.exists() && installed.length() > 0L)
                }
                if (parsed.isNotEmpty()) {
                    mutableQueue.addAll(parsed)
                    isQueuePaused = savedPaused
                    restoredHead = parsed.first()
                }
            }
        }

        val head = restoredHead
        if (head != null && _downloadProgress.value.status == DownloadStatus.IDLE) {
            val partLen = hasPartialDownload(appContext, head.fileName)
            _downloadProgress.value = ModelDownloadProgress(
                modelId = head.id,
                fileName = head.fileName,
                status = if (savedPaused) DownloadStatus.PAUSED else DownloadStatus.PROBING,
                bytesDownloaded = partLen,
                totalBytes = head.expectedBytes ?: synchronized(queueLock) { resolvedSizeCache[head.id] }
            )
            emitQueueAndGlobalProgress(appContext)
        }
    }

    internal fun persistQueueSnapshot(context: Context) {
        val appContext = context.applicationContext
        val (snapshot, paused) = synchronized(queueLock) {
            mutableQueue.toList() to isQueuePaused
        }
        val array = JSONArray()
        snapshot.forEach { model ->
            array.put(
                JSONObject().apply {
                    put("id", model.id)
                    put("title", model.title)
                    put("fileName", model.fileName)
                    put("url", model.url)
                    if (model.expectedBytes != null) {
                        put("expectedBytes", model.expectedBytes)
                    }
                    if (!model.sha256.isNullOrBlank()) {
                        put("sha256", model.sha256)
                    }
                }
            )
        }
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PERSISTED_QUEUE_JSON, array.toString())
            .putBoolean(KEY_PERSISTED_QUEUE_PAUSED, paused)
            .apply()
    }

    private fun deserializeQueue(rawJson: String): List<LocalModel> {
        return runCatching {
            val array = JSONArray(rawJson)
            (0 until array.length()).mapNotNull { idx ->
                val obj = array.optJSONObject(idx) ?: return@mapNotNull null
                val id = obj.optString("id")
                val fileName = obj.optString("fileName")
                val url = obj.optString("url")
                if (id.isBlank() || fileName.isBlank() || url.isBlank()) return@mapNotNull null
                LocalModel(
                    id = id,
                    title = obj.optString("title").ifBlank { fileName },
                    fileName = fileName,
                    url = url,
                    expectedBytes = if (obj.has("expectedBytes")) obj.optLong("expectedBytes").takeIf { it > 0L } else null,
                    sha256 = obj.optString("sha256").takeIf { it.isNotBlank() }
                )
            }
        }.getOrDefault(emptyList())
    }

    internal fun ensureWorkerRunning(context: Context) {
        val appContext = context.applicationContext
        appContextRef = appContext
        restorePersistedQueueIfNeeded(appContext)
        val shouldStart = synchronized(queueLock) {
            !isQueuePaused && mutableQueue.isNotEmpty() && activeJob?.isActive != true
        }
        if (shouldStart) {
            launchQueueWorkerInternal(appContext)
        }
    }

    private fun launchQueueWorker(appContext: Context) {
        hasRestoredPersistedQueue = true
        launchQueueWorkerInternal(appContext)
        ModelDownloadService.startOrSync(appContext)
    }

    private fun launchQueueWorkerInternal(appContext: Context) {
        activeJob?.cancel()
        activeJob = scope.launch {
            while (isActive) {
                val nextModel = synchronized(queueLock) {
                    if (isQueuePaused) null else mutableQueue.firstOrNull()
                } ?: break

                val finalFile = ModelCatalog.modelFile(appContext, nextModel.fileName)
                if (finalFile.exists() && finalFile.length() > 0L) {
                    synchronized(queueLock) {
                        mutableQueue.removeAll { it.id == nextModel.id }
                        batchCompletedIds.add(nextModel.id)
                        batchCompletedBytes[nextModel.id] = finalFile.length()
                    }
                    emitQueueAndGlobalProgress(appContext)
                    continue
                }

                val succeeded = executeResumableDownload(appContext, nextModel)
                synchronized(queueLock) {
                    if (succeeded) {
                        mutableQueue.removeAll { it.id == nextModel.id }
                        batchCompletedIds.add(nextModel.id)
                        val completedSize = ModelCatalog.modelFile(appContext, nextModel.fileName).length()
                        batchCompletedBytes[nextModel.id] = completedSize
                    } else if (!isQueuePaused) {
                        // On error, pause the failed head item or move past it so queue stays inspectable
                        isQueuePaused = true
                    }
                }
                emitQueueAndGlobalProgress(appContext)
                if (!succeeded) break
            }
        }
    }

    private suspend fun executeResumableDownload(context: Context, model: LocalModel): Boolean {
        val cleanUrl = model.url.trim()
        if (!cleanUrl.startsWith("https://", ignoreCase = true)) {
            _downloadProgress.value = ModelDownloadProgress(
                modelId = model.id,
                fileName = model.fileName,
                status = DownloadStatus.ERROR,
                errorMessage = "HTTPS only: http:// links are rejected for security."
            )
            return false
        }

        val partFile = ModelCatalog.partFile(context, model.fileName)
        val finalFile = ModelCatalog.modelFile(context, model.fileName)

        _downloadProgress.value = ModelDownloadProgress(
            modelId = model.id,
            fileName = model.fileName,
            status = DownloadStatus.PROBING,
            bytesDownloaded = if (partFile.exists()) partFile.length() else 0L,
            totalBytes = model.expectedBytes ?: synchronized(queueLock) { resolvedSizeCache[model.id] }
        )
        emitQueueAndGlobalProgress(context)

        var attempt = 0
        while (attempt <= MAX_RETRY_ATTEMPTS) {
            try {
                val existingBytes = if (partFile.exists()) partFile.length() else 0L
                val conn = openRedirectFollowingConnection(
                    initialUrl = cleanUrl,
                    method = "GET",
                    rangeStartBytes = existingBytes.takeIf { it > 0L }
                )

                try {
                    val code = conn.responseCode
                    if (code == 404) {
                        throw NonRetryableDownloadException("Link no longer valid (HTTP 404), paste another link")
                    }
                    if (code == 401 || code == 403) {
                        throw NonRetryableDownloadException("Repository needs login (HTTP $code)")
                    }
                    if (code == 416) {
                        conn.disconnect()
                        return finalizeDownloadedFile(context, model, partFile, finalFile)
                    }
                    if (code !in listOf(200, 206)) {
                        throw IOException("Server returned HTTP $code")
                    }

                    val isResume = (code == 206 && existingBytes > 0L)
                    val startOffset = if (isResume) existingBytes else 0L
                    val contentLengthHeader = conn.contentLengthLong.takeIf { it > 0L }
                    val resolvedTotal = when {
                        contentLengthHeader != null -> startOffset + contentLengthHeader
                        model.expectedBytes != null -> model.expectedBytes
                        else -> synchronized(queueLock) { resolvedSizeCache[model.id] }
                    }
                    if (resolvedTotal != null && resolvedTotal > 0L) {
                        synchronized(queueLock) {
                            resolvedSizeCache[model.id] = resolvedTotal
                        }
                    }

                    val freeSpace = runCatching {
                        StatFs(ModelCatalog.modelsDir(context).absolutePath).availableBytes
                    }.getOrDefault(Long.MAX_VALUE)
                    val neededSpace = (contentLengthHeader ?: ((model.expectedBytes ?: 0L) - startOffset).coerceAtLeast(0L)) + EXTRA_FREE_SPACE_BYTES
                    if (freeSpace < neededSpace) {
                        throw NonRetryableDownloadException(
                            "Not enough storage space. Need ${ModelCatalog.formatBytes(neededSpace)} free."
                        )
                    }

                    var currentBytes = startOffset
                    var speedWindowStartMs = System.currentTimeMillis()
                    var speedWindowBytes = 0L
                    var currentSpeed = 0L

                    _downloadProgress.value = ModelDownloadProgress(
                        modelId = model.id,
                        fileName = model.fileName,
                        status = DownloadStatus.DOWNLOADING,
                        bytesDownloaded = currentBytes,
                        totalBytes = resolvedTotal
                    )
                    emitQueueAndGlobalProgress(context)

                    conn.inputStream.use { input ->
                        FileOutputStream(partFile, isResume).use { output ->
                            val buffer = ByteArray(128 * 1024)
                            var read = input.read(buffer)
                            while (read != -1) {
                                if (!scope.isActive) throw CancellationException()
                                output.write(buffer, 0, read)
                                currentBytes += read
                                speedWindowBytes += read

                                val now = System.currentTimeMillis()
                                val elapsedMs = now - speedWindowStartMs
                                if (elapsedMs >= 500L) {
                                    currentSpeed = (speedWindowBytes * 1000L) / elapsedMs
                                    speedWindowStartMs = now
                                    speedWindowBytes = 0L

                                    val eta = if (currentSpeed > 0L && resolvedTotal != null && resolvedTotal > currentBytes) {
                                        (resolvedTotal - currentBytes) / currentSpeed
                                    } else {
                                        null
                                    }

                                    _downloadProgress.value = ModelDownloadProgress(
                                        modelId = model.id,
                                        fileName = model.fileName,
                                        status = DownloadStatus.DOWNLOADING,
                                        bytesDownloaded = currentBytes,
                                        totalBytes = resolvedTotal,
                                        speedBytesPerSec = currentSpeed,
                                        etaSeconds = eta
                                    )
                                    emitQueueAndGlobalProgress(context)
                                }
                                read = input.read(buffer)
                            }
                            output.flush()
                        }
                    }

                    return finalizeDownloadedFile(context, model, partFile, finalFile)
                } finally {
                    conn.disconnect()
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (nonRetry: NonRetryableDownloadException) {
                _downloadProgress.update {
                    it.copy(
                        status = DownloadStatus.ERROR,
                        speedBytesPerSec = 0L,
                        etaSeconds = null,
                        errorMessage = nonRetry.message
                    )
                }
                return false
            } catch (ioe: IOException) {
                attempt++
                if (attempt > MAX_RETRY_ATTEMPTS) {
                    _downloadProgress.update {
                        it.copy(
                            status = DownloadStatus.ERROR,
                            speedBytesPerSec = 0L,
                            etaSeconds = null,
                            errorMessage = mapExceptionToHumanMessage(ioe)
                        )
                    }
                    return false
                }
                delay(1500L * attempt)
            } catch (e: Exception) {
                _downloadProgress.update {
                    it.copy(
                        status = DownloadStatus.ERROR,
                        speedBytesPerSec = 0L,
                        etaSeconds = null,
                        errorMessage = mapExceptionToHumanMessage(e)
                    )
                }
                return false
            }
        }
        return false
    }

    private suspend fun finalizeDownloadedFile(
        context: Context,
        model: LocalModel,
        partFile: File,
        finalFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        val actualSize = partFile.length()
        _downloadProgress.update {
            it.copy(
                status = DownloadStatus.VERIFYING,
                bytesDownloaded = actualSize,
                speedBytesPerSec = 0L,
                etaSeconds = 0L
            )
        }
        emitQueueAndGlobalProgress(context)

        if (!verifyExpectedSize(actualSize, model.expectedBytes)) {
            _downloadProgress.update {
                it.copy(
                    status = DownloadStatus.ERROR,
                    errorMessage = "File size mismatch: got ${ModelCatalog.formatBytes(actualSize)} ($actualSize B), expected ${ModelCatalog.formatBytes(model.expectedBytes)} (${model.expectedBytes} B within 1%)."
                )
            }
            return@withContext false
        }

        var shaWarning: String? = null
        if (!model.sha256.isNullOrBlank()) {
            val computedSha = computeSha256(partFile)
            if (!computedSha.equals(model.sha256, ignoreCase = true)) {
                shaWarning = "Warning: file differs from the expected version (SHA-256 mismatch), but is still enabled for use."
            }
        }

        if (finalFile.exists()) {
            finalFile.delete()
        }
        val renamed = partFile.renameTo(finalFile)
        if (!renamed) {
            partFile.copyTo(finalFile, overwrite = true)
            partFile.delete()
        }

        selectModel(context, model.id, finalFile.name)
        refreshState(context)

        _downloadProgress.value = ModelDownloadProgress(
            modelId = model.id,
            fileName = model.fileName,
            status = DownloadStatus.COMPLETED,
            bytesDownloaded = finalFile.length(),
            totalBytes = finalFile.length(),
            warningMessage = shaWarning
        )
        true
    }

    private fun emitQueueAndGlobalProgress(context: Context) {
        val currentProgress = _downloadProgress.value
        val (queueSnapshot, completedSnapshot, pausedFlag, cachedSizes) = synchronized(queueLock) {
            QueueSnapshot(
                queue = mutableQueue.toList(),
                completed = batchCompletedBytes.toMap(),
                paused = isQueuePaused,
                sizes = resolvedSizeCache.toMap()
            )
        }

        val items = queueSnapshot.mapIndexed { index, model ->
            val isHead = index == 0
            val activeForThis = isHead && !pausedFlag && (
                currentProgress.modelId == model.id && (
                    currentProgress.status == DownloadStatus.DOWNLOADING ||
                        currentProgress.status == DownloadStatus.PROBING ||
                        currentProgress.status == DownloadStatus.VERIFYING
                    )
                )
            val partialLen = hasPartialDownload(context, model.fileName)
            val bytes = when {
                activeForThis && currentProgress.bytesDownloaded > partialLen -> currentProgress.bytesDownloaded
                isHead && currentProgress.modelId == model.id && currentProgress.bytesDownloaded > partialLen ->
                    currentProgress.bytesDownloaded
                else -> partialLen
            }
            val total = when {
                isHead && currentProgress.modelId == model.id && (currentProgress.totalBytes ?: 0L) > 0L ->
                    currentProgress.totalBytes
                else -> cachedSizes[model.id] ?: model.expectedBytes ?: estimateBytesForModel(model)
            }
            QueuedDownloadItem(
                model = model,
                priority = index + 1,
                bytesDownloaded = bytes,
                totalBytes = total,
                isActive = activeForThis,
                isPaused = isHead && pausedFlag
            )
        }
        _downloadQueue.value = items

        val activeItem = items.firstOrNull()
        val sumCompletedBytes = completedSnapshot.values.sum()
        val sumQueueDownloaded = items.sumOf { it.bytesDownloaded }
        val sumQueueTotal = items.sumOf { it.totalBytes ?: estimateBytesForModel(it.model) }
        val totalDownloadedAll = sumCompletedBytes + sumQueueDownloaded
        val totalExpectedAll = sumCompletedBytes + sumQueueTotal

        val globalFraction = if (totalExpectedAll > 0L) {
            (totalDownloadedAll.toFloat() / totalExpectedAll.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

        val speed = if (!pausedFlag && activeItem?.isActive == true) currentProgress.speedBytesPerSec else 0L
        val remainingBytes = (totalExpectedAll - totalDownloadedAll).coerceAtLeast(0L)
        val globalEta = if (speed > 0L && remainingBytes > 0L) remainingBytes / speed else null

        _globalProgress.value = GlobalDownloadProgress(
            activeModelId = activeItem?.model?.id,
            activeModelTitle = activeItem?.model?.title,
            totalQueuedCount = items.size,
            completedInBatchCount = completedSnapshot.size,
            totalBytesDownloaded = totalDownloadedAll,
            totalBytesOverall = totalExpectedAll,
            fraction = globalFraction,
            speedBytesPerSec = speed,
            etaSeconds = globalEta,
            isDownloading = items.any { it.isActive },
            isPaused = pausedFlag && items.isNotEmpty()
        )
        persistQueueSnapshot(context)
    }

    private fun estimateBytesForModel(model: LocalModel): Long {
        return model.expectedBytes ?: when (model.id) {
            ModelCatalog.QWEN3_06B.id -> 586_000_000L
            ModelCatalog.QWEN3_06B_NOTHINK.id -> 480_000_000L
            else -> 500_000_000L
        }
    }

    private data class QueueSnapshot(
        val queue: List<LocalModel>,
        val completed: Map<String, Long>,
        val paused: Boolean,
        val sizes: Map<String, Long>
    )

    internal fun verifyExpectedSize(actualBytes: Long, expectedBytes: Long?): Boolean {
        if (expectedBytes == null) return actualBytes > 0L
        val tolerance = (expectedBytes * 0.01).toLong().coerceAtLeast(1L)
        return kotlin.math.abs(actualBytes - expectedBytes) <= tolerance
    }

    internal fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(1024 * 1024) // 1 MB streaming buffer
        FileInputStream(file).use { fis ->
            var read = fis.read(buffer)
            while (read != -1) {
                digest.update(buffer, 0, read)
                read = fis.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(Locale.US, it) }
    }

    /**
     * Follows up to 5 HTTPS redirect hops manually, re-sending the `Range` header across
     * cross-host redirects (such as Hugging Face -> CDN). Rejects any downgrade to `http://`.
     */
    private fun openRedirectFollowingConnection(
        initialUrl: String,
        method: String,
        rangeStartBytes: Long?
    ): HttpURLConnection {
        var currentUrl = initialUrl
        var hops = 0

        while (hops <= MAX_REDIRECT_HOPS) {
            if (!currentUrl.startsWith("https://", ignoreCase = true)) {
                throw NonRetryableDownloadException("HTTPS only: redirect to non-HTTPS URL rejected.")
            }

            val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                requestMethod = method
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("User-Agent", "AiAppStudio-Android/1.0")
                if (rangeStartBytes != null && rangeStartBytes > 0L) {
                    setRequestProperty("Range", "bytes=$rangeStartBytes-")
                }
            }

            val code = conn.responseCode
            if (code in listOf(301, 302, 303, 307, 308)) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrBlank()) {
                    throw IOException("Redirect (HTTP $code) missing Location header")
                }
                val resolvedUrl = URL(URL(currentUrl), location).toString()
                currentUrl = resolvedUrl
                hops++
                continue
            }

            return conn
        }

        throw IOException("Too many redirects (exceeded $MAX_REDIRECT_HOPS hops)")
    }

    private fun probeRemoteContentLength(url: String): Long? {
        val conn = openRedirectFollowingConnection(
            initialUrl = url,
            method = "HEAD",
            rangeStartBytes = null
        )
        return try {
            val code = conn.responseCode
            when {
                code == 404 -> throw NonRetryableDownloadException("Link no longer valid (HTTP 404), paste another link")
                code == 401 || code == 403 -> throw NonRetryableDownloadException("Repository needs login (HTTP $code)")
                code in 200..299 -> conn.contentLengthLong.takeIf { it > 0L }
                else -> null
            }
        } finally {
            conn.disconnect()
        }
    }

    internal fun mapExceptionToHumanMessage(err: Throwable): String {
        return when (err) {
            is NonRetryableDownloadException -> err.message ?: "Download failed"
            is UnknownHostException, is ConnectException ->
                "No internet connection. Please connect to Wi-Fi and try again."
            is SocketTimeoutException ->
                "Connection timed out. Tap Download to resume from where it stopped."
            else -> {
                val msg = err.message.orEmpty()
                when {
                    msg.contains("404") -> "Link no longer valid (HTTP 404), paste another link"
                    msg.contains("401") || msg.contains("403") -> "Repository needs login"
                    msg.contains("ENOSPC", ignoreCase = true) || msg.contains("No space", ignoreCase = true) ->
                        "Not enough free storage space on device"
                    else -> "Download error: ${msg.ifBlank { "check your internet connection and retry" }}"
                }
            }
        }
    }

    private val THINK_BLOCK_REGEX = Regex("(?s)<think>.*?</think>")

    /**
     * Strips Qwen3 `<think>...</think>` blocks (including multiline) before JSON extraction.
     */
    fun stripThinkTags(rawText: String): String =
        rawText.replace(THINK_BLOCK_REGEX, "").trim()

    /**
     * Strips `<think>...</think>` and extracts the JSON object between the first `{` and last `}`.
     */
    fun extractJsonFromModelOutput(rawOutput: String): String {
        val withoutThink = stripThinkTags(rawOutput)
        val start = withoutThink.indexOf('{')
        val end = withoutThink.lastIndexOf('}')
        if (start == -1 || end == -1 || end < start) {
            throw IllegalArgumentException("Model output did not contain a JSON object")
        }
        return withoutThink.substring(start, end + 1)
    }

    /**
     * Prefers `qwen3_06b_nothink` for fast structured JSON blueprint generation when installed.
     */
    fun preferredBlueprintModelId(installedModelIds: Set<String>): String {
        return when {
            "qwen3_06b_nothink" in installedModelIds -> "qwen3_06b_nothink"
            "gemma4_e2b" in installedModelIds -> "gemma4_e2b"
            "qwen3_06b" in installedModelIds -> "qwen3_06b"
            else -> "qwen3_06b_nothink"
        }
    }

    private class NonRetryableDownloadException(message: String) : IOException(message)
}
