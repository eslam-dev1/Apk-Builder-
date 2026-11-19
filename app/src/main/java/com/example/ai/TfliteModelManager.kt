package com.example.ai

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class LocalTfliteModel(
    val id: String,
    val fileName: String,
    val displayName: String,
    val taskCategory: String,
    val sizeBytes: Long,
    val hasValidFlatBufferHeader: Boolean,
    val absolutePath: String,
    val lastModifiedMs: Long
)

data class TfliteDownloadState(
    val isDownloading: Boolean = false,
    val fileName: String = "",
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long? = null,
    val errorMessage: String? = null
)

object TfliteModelManager {

    private const val TFLITE_DIR_NAME = "tflite_models"
    private const val LEGACY_MODELS_DIR_NAME = "models"
    private const val TFLITE_EXT = ".tflite"
    private const val PREFS_NAME = "tflite_model_manager_prefs"
    private const val KEY_DEFAULT_BUNDLED = "default_bundled_tflite_models"
    private const val CONNECT_TIMEOUT_MS = 20_000
    private const val READ_TIMEOUT_MS = 60_000

    private val _localModels = MutableStateFlow<List<LocalTfliteModel>>(emptyList())
    val localModels: StateFlow<List<LocalTfliteModel>> = _localModels.asStateFlow()

    private val _downloadState = MutableStateFlow(TfliteDownloadState())
    val downloadState: StateFlow<TfliteDownloadState> = _downloadState.asStateFlow()

    private val _defaultBundledModels = MutableStateFlow<List<String>>(emptyList())
    val defaultBundledModels: StateFlow<List<String>> = _defaultBundledModels.asStateFlow()

    fun loadDefaultBundledModels(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getStringSet(KEY_DEFAULT_BUNDLED, emptySet())
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.sorted()
            ?: emptyList()
        _defaultBundledModels.value = stored
        return stored
    }

    fun setDefaultBundledModels(context: Context, fileNames: List<String>) {
        val cleaned = fileNames.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        _defaultBundledModels.value = cleaned
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_DEFAULT_BUNDLED, cleaned.toSet())
            .apply()
    }

    fun tfliteDir(context: Context): File =
        File(context.filesDir, TFLITE_DIR_NAME).apply { mkdirs() }

    fun findModelFile(context: Context, fileName: String): File? {
        val sanitized = sanitizeTfliteFileName(fileName)
        val primary = File(tfliteDir(context), sanitized)
        if (primary.isFile && primary.length() > 0L) return primary

        val legacyDir = File(context.filesDir, LEGACY_MODELS_DIR_NAME)
        val legacy = File(legacyDir, sanitized)
        if (legacy.isFile && legacy.length() > 0L) return legacy

        val rawPrimary = File(tfliteDir(context), fileName.substringAfterLast('/'))
        if (rawPrimary.isFile && rawPrimary.length() > 0L) return rawPrimary

        return null
    }

    fun refreshModels(context: Context): List<LocalTfliteModel> {
        val primaryDir = tfliteDir(context)
        val legacyDir = File(context.filesDir, LEGACY_MODELS_DIR_NAME)

        val candidates = buildList {
            primaryDir.listFiles()?.filter { it.isFile && it.name.endsWith(TFLITE_EXT, ignoreCase = true) }
                ?.let { addAll(it) }
            legacyDir.listFiles()?.filter { it.isFile && it.name.endsWith(TFLITE_EXT, ignoreCase = true) }
                ?.let { addAll(it) }
        }

        val seenNames = HashSet<String>()
        val models = candidates
            .sortedByDescending { it.lastModified() }
            .mapNotNull { file ->
                if (file.length() <= 0L) return@mapNotNull null
                val key = file.name.lowercase(Locale.US)
                if (!seenNames.add(key)) return@mapNotNull null

                LocalTfliteModel(
                    id = "tflite_$key",
                    fileName = file.name,
                    displayName = formatDisplayName(file.name),
                    taskCategory = inferTaskCategory(file.name),
                    sizeBytes = file.length(),
                    hasValidFlatBufferHeader = hasValidTfliteHeader(file),
                    absolutePath = file.absolutePath,
                    lastModifiedMs = file.lastModified()
                )
            }
            .sortedBy { it.displayName.lowercase(Locale.US) }

        _localModels.value = models
        return models
    }

    suspend fun importTfliteFromUri(
        context: Context,
        sourceUri: Uri,
        suggestedName: String? = null
    ): Result<LocalTfliteModel> = withContext(Dispatchers.IO) {
        runCatching {
            val resolvedName = suggestedName
                ?: queryDisplayName(context, sourceUri)
                ?: sourceUri.lastPathSegment
                ?: "model.tflite"

            if (!resolvedName.endsWith(TFLITE_EXT, ignoreCase = true)) {
                throw IllegalArgumentException("Please select a .tflite model file (selected: $resolvedName)")
            }

            val safeName = sanitizeTfliteFileName(resolvedName)
            val destFile = File(tfliteDir(context), safeName)
            val tempFile = File(tfliteDir(context), "$safeName.tmp")
            if (tempFile.exists()) tempFile.delete()

            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output, bufferSize = 128 * 1024)
                }
            } ?: throw IOException("Unable to read selected .tflite file.")

            if (!tempFile.exists() || tempFile.length() == 0L) {
                tempFile.delete()
                throw IOException("Selected .tflite file is empty.")
            }

            if (destFile.exists()) destFile.delete()
            if (!tempFile.renameTo(destFile)) {
                tempFile.copyTo(destFile, overwrite = true)
                tempFile.delete()
            }

            val updated = refreshModels(context)
            updated.firstOrNull { it.fileName == safeName }
                ?: LocalTfliteModel(
                    id = "tflite_${safeName.lowercase(Locale.US)}",
                    fileName = safeName,
                    displayName = formatDisplayName(safeName),
                    taskCategory = inferTaskCategory(safeName),
                    sizeBytes = destFile.length(),
                    hasValidFlatBufferHeader = hasValidTfliteHeader(destFile),
                    absolutePath = destFile.absolutePath,
                    lastModifiedMs = destFile.lastModified()
                )
        }
    }

    suspend fun downloadTfliteFromUrl(
        context: Context,
        rawUrl: String,
        customFileName: String? = null
    ): Result<LocalTfliteModel> = withContext(Dispatchers.IO) {
        val trimmedUrl = rawUrl.trim()
        if (!trimmedUrl.startsWith("https://", ignoreCase = true)) {
            return@withContext Result.failure(
                IllegalArgumentException("Only HTTPS URLs are allowed for downloading .tflite models.")
            )
        }

        val derivedName = customFileName?.takeIf { it.isNotBlank() }
            ?: trimmedUrl.substringBefore('?').substringAfterLast('/').ifBlank { "downloaded_model.tflite" }
        val safeName = sanitizeTfliteFileName(derivedName)
        val destFile = File(tfliteDir(context), safeName)
        val tempFile = File(tfliteDir(context), "$safeName.part")
        if (tempFile.exists()) tempFile.delete()

        _downloadState.value = TfliteDownloadState(
            isDownloading = true,
            fileName = safeName,
            bytesDownloaded = 0L,
            totalBytes = null,
            errorMessage = null
        )

        runCatching {
            var currentUrl = trimmedUrl
            var connection: HttpURLConnection? = null
            var redirects = 0

            while (redirects < 5) {
                val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    requestMethod = "GET"
                    setRequestProperty("Accept-Encoding", "identity")
                }
                val code = conn.responseCode
                if (code in listOf(301, 302, 303, 307, 308)) {
                    val location = conn.getHeaderField("Location")
                    conn.disconnect()
                    if (location.isNullOrBlank()) {
                        throw IOException("HTTP redirect missing Location header ($code)")
                    }
                    currentUrl = URL(URL(currentUrl), location).toString()
                    redirects++
                } else {
                    connection = conn
                    break
                }
            }

            val activeConn = connection ?: throw IOException("Too many HTTP redirects")
            try {
                val code = activeConn.responseCode
                if (code !in 200..299) {
                    throw IOException("HTTP error $code while downloading $safeName")
                }

                val totalLen = activeConn.contentLengthLong.takeIf { it > 0L }
                _downloadState.value = _downloadState.value.copy(totalBytes = totalLen)

                var downloaded = 0L
                activeConn.inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            _downloadState.value = _downloadState.value.copy(
                                bytesDownloaded = downloaded
                            )
                        }
                    }
                }

                if (!tempFile.exists() || tempFile.length() == 0L) {
                    tempFile.delete()
                    throw IOException("Downloaded .tflite file was empty.")
                }

                if (destFile.exists()) destFile.delete()
                if (!tempFile.renameTo(destFile)) {
                    tempFile.copyTo(destFile, overwrite = true)
                    tempFile.delete()
                }

                val models = refreshModels(context)
                _downloadState.value = TfliteDownloadState(isDownloading = false)
                models.firstOrNull { it.fileName == safeName }
                    ?: LocalTfliteModel(
                        id = "tflite_${safeName.lowercase(Locale.US)}",
                        fileName = safeName,
                        displayName = formatDisplayName(safeName),
                        taskCategory = inferTaskCategory(safeName),
                        sizeBytes = destFile.length(),
                        hasValidFlatBufferHeader = hasValidTfliteHeader(destFile),
                        absolutePath = destFile.absolutePath,
                        lastModifiedMs = destFile.lastModified()
                    )
            } finally {
                activeConn.disconnect()
            }
        }.onFailure { err ->
            if (tempFile.exists()) tempFile.delete()
            _downloadState.value = TfliteDownloadState(
                isDownloading = false,
                fileName = safeName,
                errorMessage = err.localizedMessage ?: "Download failed"
            )
        }
    }

    fun deleteModel(context: Context, fileName: String): Boolean {
        val sanitized = sanitizeTfliteFileName(fileName)
        var deleted = false
        val primary = File(tfliteDir(context), sanitized)
        if (primary.exists() && primary.delete()) {
            deleted = true
        }
        val legacy = File(File(context.filesDir, LEGACY_MODELS_DIR_NAME), sanitized)
        if (legacy.exists() && legacy.delete()) {
            deleted = true
        }
        refreshModels(context)
        return deleted
    }

    fun sanitizeTfliteFileName(raw: String): String {
        val trimmed = raw.trim().substringAfterLast('/').substringAfterLast('\\')
        val cleaned = trimmed.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return when {
            cleaned.isBlank() -> "custom_model.tflite"
            cleaned.endsWith(TFLITE_EXT, ignoreCase = true) -> cleaned
            else -> "$cleaned$TFLITE_EXT"
        }
    }

    fun formatDisplayName(fileName: String): String {
        val base = fileName.removeSuffix(".tflite").removeSuffix(".TFLITE")
        return base
            .replace('_', ' ')
            .replace('-', ' ')
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(" ") { token ->
                token.replaceFirstChar { ch ->
                    if (ch.isLowerCase()) ch.titlecase(Locale.US) else ch.toString()
                }
            }
            .ifBlank { fileName }
    }

    fun inferTaskCategory(fileName: String): String {
        val lower = fileName.lowercase(Locale.US)
        return when {
            listOf("mobilenet", "efficientnet", "resnet", "yolo", "ssd", "vision", "image", "detect", "segment", "pose", "face", "ocr")
                .any { lower.contains(it) } -> "Vision"
            listOf("bert", "text", "nlp", "embed", "sentiment", "toxicity", "qa", "token", "gemma", "qwen")
                .any { lower.contains(it) } -> "Text / NLP"
            listOf("yamnet", "whisper", "audio", "speech", "vad", "sound", "voice")
                .any { lower.contains(it) } -> "Audio"
            else -> "Custom TFLite"
        }
    }

    fun inferModelPurpose(fileName: String): String {
        val lower = fileName.lowercase(Locale.US)
        return when {
            listOf("yolo", "ssd", "detect", "efficientdet").any { lower.contains(it) } ->
                "Object detection & bounding-box localization in images and camera frames"
            listOf("segment", "deeplab", "selfie").any { lower.contains(it) } ->
                "Pixel-level image segmentation and foreground/background masking"
            listOf("pose", "movenet", "blazepose", "face").any { lower.contains(it) } ->
                "Human pose estimation, facial landmarks & keypoint tracking"
            listOf("ocr", "text_recognition").any { lower.contains(it) } ->
                "Offline optical character recognition & document text extraction"
            listOf("mobilenet", "efficientnet", "resnet", "inception", "vit", "vision", "image").any { lower.contains(it) } ->
                "On-device image classification & visual feature extraction"
            listOf("whisper", "speech", "asr").any { lower.contains(it) } ->
                "Offline speech recognition & spoken-audio transcription"
            listOf("yamnet", "vad", "audio", "sound", "voice").any { lower.contains(it) } ->
                "Audio event classification, acoustic tagging & voice activity detection"
            listOf("embed", "universal_sentence", "gecko").any { lower.contains(it) } ->
                "Dense vector text embeddings for local semantic search & RAG"
            listOf("bert", "qa", "sentiment", "toxicity", "nlp", "text", "token", "gemma", "qwen").any { lower.contains(it) } ->
                "On-device natural language understanding, classification & Q&A"
            else ->
                "General-purpose on-device TensorFlow Lite FlatBuffer inference"
        }
    }

    fun bundledAssetPath(fileName: String): String =
        "assets/models/${sanitizeTfliteFileName(fileName)}"

    fun hasValidTfliteHeader(file: File): Boolean {
        if (!file.isFile || file.length() < 8L) return false
        return runCatching {
            val header = ByteArray(8)
            FileInputStream(file).use { input ->
                val read = input.read(header)
                read >= 8 && hasValidTfliteHeaderBytes(header)
            }
        }.getOrDefault(false)
    }

    internal fun hasValidTfliteHeaderBytes(bytes: ByteArray): Boolean {
        if (bytes.size < 8) return false
        // Standard TensorFlow Lite FlatBuffer identifier is "TFL3" at offset 4..7
        return bytes[4] == 'T'.code.toByte() &&
            bytes[5] == 'F'.code.toByte() &&
            bytes[6] == 'L'.code.toByte() &&
            bytes[7] == '3'.code.toByte()
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 KB"
        val kb = bytes.toDouble() / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
            mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
            else -> String.format(Locale.US, "%.0f KB", kb.coerceAtLeast(1.0))
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx) else null
                    } else {
                        null
                    }
                }
        }.getOrNull()
    }
}
