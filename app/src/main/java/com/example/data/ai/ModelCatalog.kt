package com.example.data.ai

import android.content.Context
import java.io.File
import java.util.Locale

data class LocalModel(
    val id: String,
    val title: String,
    val fileName: String,
    val url: String,
    val expectedBytes: Long?,
    val sha256: String?
)

object ModelCatalog {

    val GEMMA_4_E2B = LocalModel(
        id = "gemma4_e2b",
        title = "Gemma 4 E2B — best quality, Arabic OK (needs ~6 GB RAM)",
        fileName = "gemma-4-E2B-it.litertlm",
        url = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
        expectedBytes = 2_588_147_712L,
        sha256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c"
    )

    val QWEN3_06B = LocalModel(
        id = "qwen3_06b",
        title = "Qwen3 0.6B — light, low-end devices",
        fileName = "Qwen3-0.6B.litertlm",
        url = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B.litertlm",
        expectedBytes = null,
        sha256 = null
    )

    val QWEN3_06B_NOTHINK = LocalModel(
        id = "qwen3_06b_nothink",
        title = "Qwen3 0.6B fast (no \"thinking\", best for JSON)",
        fileName = "qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm",
        url = "https://huggingface.co/litert-community/Qwen3-0.6B-int4/resolve/main/qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm",
        expectedBytes = null,
        sha256 = null
    )

    val ALL_MODELS: List<LocalModel> = listOf(
        GEMMA_4_E2B,
        QWEN3_06B,
        QWEN3_06B_NOTHINK
    )

    const val DEFAULT_PREFERRED_MODEL_ID = "qwen3_06b_nothink"

    fun byId(id: String): LocalModel? = ALL_MODELS.find { it.id == id }

    fun modelsDir(context: Context): File =
        File(context.filesDir, "models").apply { mkdirs() }

    fun modelFile(context: Context, fileName: String): File =
        File(modelsDir(context), sanitizeFileName(fileName))

    fun partFile(context: Context, fileName: String): File =
        File(modelsDir(context), "${sanitizeFileName(fileName)}.part")

    fun sanitizeFileName(raw: String): String {
        val trimmed = raw.trim().substringAfterLast('/')
        val cleaned = trimmed.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return when {
            cleaned.isBlank() -> "custom_model.litertlm"
            cleaned.endsWith(".litertlm", ignoreCase = true) -> cleaned
            else -> "$cleaned.litertlm"
        }
    }

    fun formatBytes(bytes: Long?): String {
        if (bytes == null || bytes <= 0L) return "Size via Content-Length"
        val mb = bytes.toDouble() / (1024.0 * 1024.0)
        val gb = bytes.toDouble() / (1000.0 * 1000.0 * 1000.0)
        return if (bytes >= 1_000_000_000L) {
            String.format(Locale.US, "%.2f GB", gb)
        } else {
            String.format(Locale.US, "%.0f MB", mb)
        }
    }

    fun sizeDisplayLabel(model: LocalModel, probedBytes: Long? = null): String {
        val effective = probedBytes ?: model.expectedBytes
        return when {
            effective != null && effective > 0L -> formatBytes(effective)
            model.id == QWEN3_06B.id -> "~586 MB"
            else -> "Content-Length on download"
        }
    }
}
