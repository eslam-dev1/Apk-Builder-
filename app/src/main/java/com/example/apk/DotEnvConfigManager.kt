package com.example.apk

import android.content.Context
import com.example.data.CloudAiConfig
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DotEnvDraft(
    val geminiApiKey: String = "",
    val openRouterApiKey: String = "",
    val keystorePath: String = DEFAULT_KEYSTORE_PATH,
    val storePassword: String = "",
    val keyAlias: String = DEFAULT_KEY_ALIAS,
    val keyPassword: String = "",
    val includeComments: Boolean = true
) {
    val hasGeminiKey: Boolean get() = isRealSecret(geminiApiKey, "MY_GEMINI_API_KEY")
    val hasOpenRouterKey: Boolean get() = isRealSecret(openRouterApiKey, "MY_OPENROUTER_API_KEY")
    val hasKeystorePath: Boolean get() = keystorePath.trim().isNotEmpty() && !EnvSigningConfig.isPlaceholder(keystorePath)
    val hasStorePassword: Boolean get() = isRealSecret(storePassword, "MY_STORE_PASSWORD")
    val hasKeyAlias: Boolean get() = keyAlias.trim().isNotEmpty() && !EnvSigningConfig.isPlaceholder(keyAlias)
    val hasKeyPassword: Boolean get() = isRealSecret(keyPassword, "MY_KEY_PASSWORD")

    val hasAllSigningSecrets: Boolean
        get() = hasKeystorePath && hasStorePassword && hasKeyAlias && hasKeyPassword

    val configuredCount: Int
        get() = listOf(
            hasGeminiKey,
            hasOpenRouterKey,
            hasKeystorePath,
            hasKeyAlias,
            hasStorePassword,
            hasKeyPassword
        ).count { it }

    fun toEnvSigningConfig(sourceLabel: String = ".env (Guided Setup)"): EnvSigningConfig =
        EnvSigningConfig(
            keystorePath = keystorePath.trim(),
            storePassword = storePassword,
            keyAlias = keyAlias.trim(),
            keyPassword = keyPassword,
            sourceLabel = sourceLabel
        )

    companion object {
        const val DEFAULT_KEYSTORE_PATH = "my-upload-key.jks"
        const val DEFAULT_KEY_ALIAS = "upload"

        internal fun isRealSecret(value: String, defaultPlaceholder: String): Boolean {
            val trimmed = value.trim()
            return trimmed.isNotEmpty() &&
                trimmed != defaultPlaceholder &&
                !EnvSigningConfig.isPlaceholder(trimmed)
        }
    }
}

data class DotEnvChecklistItem(
    val key: String,
    val category: String,
    val isConfigured: Boolean,
    val isRequired: Boolean,
    val summary: String
)

/**
 * Loads, parses, validates, formats, and persists `.env` files compatible with
 * the Secrets Gradle Plugin (`.env` / `.env.example`), [CloudAiConfig], and [KeystoreValidator].
 */
object DotEnvConfigManager {

    private const val PLACEHOLDER_GEMINI = "MY_GEMINI_API_KEY"
    private const val PLACEHOLDER_OPENROUTER = "MY_OPENROUTER_API_KEY"
    private const val PLACEHOLDER_STORE_PASS = "MY_STORE_PASSWORD"
    private const val PLACEHOLDER_KEY_PASS = "MY_KEY_PASSWORD"

    fun workspaceDotEnvFile(context: Context): File =
        File(KeystoreValidator.keystoreDir(context), ".env")

    fun hasWorkspaceDotEnvOverride(context: Context): Boolean {
        val f1 = workspaceDotEnvFile(context)
        val f2 = File(context.filesDir, ".env")
        return (f1.isFile && f1.length() > 0L) || (f2.isFile && f2.length() > 0L)
    }

    fun loadInitialDraft(context: Context? = null): DotEnvDraft {
        val buildSigning = KeystoreValidator.readFromBuildConfig()
        val baseDraft = DotEnvDraft(
            geminiApiKey = sanitizeSecretInput(CloudAiConfig.effectiveGeminiApiKey()),
            openRouterApiKey = sanitizeSecretInput(CloudAiConfig.effectiveOpenRouterApiKey()),
            keystorePath = buildSigning.keystorePath.takeIf { it.isNotBlank() && !EnvSigningConfig.isPlaceholder(it) }
                ?: DotEnvDraft.DEFAULT_KEYSTORE_PATH,
            storePassword = sanitizeSecretInput(buildSigning.storePassword),
            keyAlias = buildSigning.keyAlias.takeIf { it.isNotBlank() && !EnvSigningConfig.isPlaceholder(it) }
                ?: DotEnvDraft.DEFAULT_KEY_ALIAS,
            keyPassword = sanitizeSecretInput(buildSigning.keyPassword)
        )
        if (context == null) return baseDraft

        val candidateFiles = listOf(
            workspaceDotEnvFile(context),
            File(context.filesDir, ".env")
        )
        val existingFile = candidateFiles.firstOrNull { it.isFile && it.length() > 0L }
            ?: return baseDraft

        return runCatching {
            parseDotEnvToDraft(existingFile.readText(), fallback = baseDraft)
        }.getOrDefault(baseDraft)
    }

    fun parseDotEnvToDraft(
        raw: String,
        fallback: DotEnvDraft = DotEnvDraft()
    ): DotEnvDraft {
        val map = mutableMapOf<String, String>()
        raw.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEach
            val eqIdx = trimmed.indexOf('=')
            if (eqIdx <= 0) return@forEach
            val key = trimmed.substring(0, eqIdx).trim()
            var value = trimmed.substring(eqIdx + 1).trim()
            if ((value.startsWith("\"") && value.endsWith("\"") && value.length >= 2) ||
                (value.startsWith("'") && value.endsWith("'") && value.length >= 2)
            ) {
                value = value.substring(1, value.length - 1)
            }
            value = value.replace("\\\"", "\"")
            map[key] = value
        }

        val resolvedPath = map["KEYSTORE_PATH"]?.takeIf {
            it.isNotBlank() && !EnvSigningConfig.isPlaceholder(it)
        } ?: fallback.keystorePath
        val resolvedAlias = map["KEY_ALIAS"]?.takeIf {
            it.isNotBlank() && !EnvSigningConfig.isPlaceholder(it)
        } ?: fallback.keyAlias

        return DotEnvDraft(
            geminiApiKey = sanitizeSecretInput(map["GEMINI_API_KEY"] ?: fallback.geminiApiKey),
            openRouterApiKey = sanitizeSecretInput(map["OPENROUTER_API_KEY"] ?: fallback.openRouterApiKey),
            keystorePath = resolvedPath,
            storePassword = sanitizeSecretInput(map["STORE_PASSWORD"] ?: fallback.storePassword),
            keyAlias = resolvedAlias,
            keyPassword = sanitizeSecretInput(map["KEY_PASSWORD"] ?: fallback.keyPassword),
            includeComments = fallback.includeComments
        )
    }

    fun formatDotEnv(
        draft: DotEnvDraft,
        maskSecrets: Boolean = false
    ): String {
        val geminiVal = resolveOutputValue(
            raw = draft.geminiApiKey,
            placeholder = PLACEHOLDER_GEMINI,
            mask = maskSecrets
        )
        val openRouterVal = resolveOutputValue(
            raw = draft.openRouterApiKey,
            placeholder = PLACEHOLDER_OPENROUTER,
            mask = maskSecrets
        )
        val ksPathVal = draft.keystorePath.trim().ifBlank { DotEnvDraft.DEFAULT_KEYSTORE_PATH }
        val ksAliasVal = draft.keyAlias.trim().ifBlank { DotEnvDraft.DEFAULT_KEY_ALIAS }
        val storePassVal = resolveOutputValue(
            raw = draft.storePassword,
            placeholder = PLACEHOLDER_STORE_PASS,
            mask = maskSecrets
        )
        val keyPassVal = resolveOutputValue(
            raw = draft.keyPassword,
            placeholder = PLACEHOLDER_KEY_PASS,
            mask = maskSecrets
        )

        return buildString {
            if (draft.includeComments) {
                appendLine("# Generated by Ai App Studio Guided .env Setup")
                appendLine("# Keep .env out of git version control and configure production keys in AI Studio Secrets.")
                appendLine()
                appendLine("# Cloud AI Provider API Keys")
            }
            appendLine("OPENROUTER_API_KEY=\"${escapeDotEnvValue(openRouterVal)}\"")
            appendLine("GEMINI_API_KEY=\"${escapeDotEnvValue(geminiVal)}\"")
            if (draft.includeComments) {
                appendLine()
                appendLine("# Android Release Signing Credentials")
            }
            appendLine("KEYSTORE_PATH=\"${escapeDotEnvValue(ksPathVal)}\"")
            appendLine("STORE_PASSWORD=\"${escapeDotEnvValue(storePassVal)}\"")
            appendLine("KEY_ALIAS=\"${escapeDotEnvValue(ksAliasVal)}\"")
            append("KEY_PASSWORD=\"${escapeDotEnvValue(keyPassVal)}\"")
        }
    }

    suspend fun saveDraftToWorkspace(
        context: Context,
        draft: DotEnvDraft
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val content = formatDotEnv(draft, maskSecrets = false) + "\n"
            val target = workspaceDotEnvFile(context)
            target.parentFile?.mkdirs()
            target.writeText(content, Charsets.UTF_8)

            CloudAiConfig.setSavedGeminiApiKey(
                context,
                if (draft.hasGeminiKey) draft.geminiApiKey.trim() else ""
            )
            CloudAiConfig.setSavedOpenRouterApiKey(
                context,
                if (draft.hasOpenRouterKey) draft.openRouterApiKey.trim() else ""
            )
            target
        }
    }

    fun resetWorkspaceDotEnv(context: Context): Boolean {
        var deleted = false
        val primary = workspaceDotEnvFile(context)
        if (primary.exists() && primary.delete()) deleted = true
        val legacy = File(context.filesDir, ".env")
        if (legacy.exists() && legacy.delete()) deleted = true
        CloudAiConfig.setSavedGeminiApiKey(context, "")
        CloudAiConfig.setSavedOpenRouterApiKey(context, "")
        return deleted
    }

    suspend fun validateSigningAgainstKeystores(
        context: Context,
        draft: DotEnvDraft
    ): SigningSummaryReport = withContext(Dispatchers.IO) {
        val envCfg = draft.toEnvSigningConfig(".env (Draft)")
        val jksFiles = KeystoreValidator.discoverJksFiles(context, envCfg)
        KeystoreValidator.buildReport(
            targetVariant = com.example.model.BuildVariant.RELEASE,
            envConfig = envCfg,
            jksFiles = jksFiles
        )
    }

    fun evaluateChecklist(
        draft: DotEnvDraft,
        signingReport: SigningSummaryReport? = null
    ): List<DotEnvChecklistItem> {
        val validKeystoreCount = signingReport?.validations?.count { it.isValid } ?: 0
        return listOf(
            DotEnvChecklistItem(
                key = "GEMINI_API_KEY",
                category = "Cloud AI",
                isConfigured = draft.hasGeminiKey,
                isRequired = false,
                summary = if (draft.hasGeminiKey) {
                    "Configured (${maskSummary(draft.geminiApiKey)})"
                } else {
                    "Placeholder (offline/local AI mode works without a key)"
                }
            ),
            DotEnvChecklistItem(
                key = "OPENROUTER_API_KEY",
                category = "Cloud AI",
                isConfigured = draft.hasOpenRouterKey,
                isRequired = false,
                summary = if (draft.hasOpenRouterKey) {
                    "Configured (${maskSummary(draft.openRouterApiKey)})"
                } else {
                    "Optional (required only when OpenRouter provider is active)"
                }
            ),
            DotEnvChecklistItem(
                key = "KEYSTORE_PATH",
                category = "Signing",
                isConfigured = draft.hasKeystorePath,
                isRequired = true,
                summary = if (draft.hasKeystorePath) {
                    draft.keystorePath.trim()
                } else {
                    "Set path to .jks file (e.g. my-upload-key.jks)"
                }
            ),
            DotEnvChecklistItem(
                key = "KEY_ALIAS",
                category = "Signing",
                isConfigured = draft.hasKeyAlias,
                isRequired = true,
                summary = if (draft.hasKeyAlias) {
                    "Alias '${draft.keyAlias.trim()}'"
                } else {
                    "Missing key alias"
                }
            ),
            DotEnvChecklistItem(
                key = "STORE_PASSWORD",
                category = "Signing",
                isConfigured = draft.hasStorePassword,
                isRequired = true,
                summary = when {
                    !draft.hasStorePassword -> "Required for release APK signing"
                    validKeystoreCount > 0 -> "Verified against .jks"
                    else -> "Configured (••••••)"
                }
            ),
            DotEnvChecklistItem(
                key = "KEY_PASSWORD",
                category = "Signing",
                isConfigured = draft.hasKeyPassword,
                isRequired = true,
                summary = when {
                    !draft.hasKeyPassword -> "Required to unlock signing key entry"
                    validKeystoreCount > 0 -> "Verified against .jks"
                    else -> "Configured (••••••)"
                }
            )
        )
    }

    private fun sanitizeSecretInput(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.isEmpty() || EnvSigningConfig.isPlaceholder(trimmed)) "" else trimmed
    }

    private fun resolveOutputValue(
        raw: String,
        placeholder: String,
        mask: Boolean
    ): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || EnvSigningConfig.isPlaceholder(trimmed)) {
            return placeholder
        }
        return if (mask) maskValue(trimmed) else trimmed
    }

    internal fun maskValue(value: String): String {
        if (value.length <= 6) return "••••••"
        return "${value.take(3)}••••••${value.takeLast(2)}"
    }

    private fun maskSummary(value: String): String {
        val t = value.trim()
        return if (t.length <= 8) "••••••" else "${t.take(4)}…${t.takeLast(3)}"
    }

    private fun escapeDotEnvValue(raw: String): String =
        raw.replace("\\", "\\\\").replace("\"", "\\\"")
}
