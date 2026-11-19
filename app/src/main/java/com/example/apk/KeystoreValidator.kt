package com.example.apk

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.BuildConfig
import com.example.model.BuildVariant
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.UnrecoverableKeyException
import java.security.cert.CertificateExpiredException
import java.security.cert.CertificateFactory
import java.security.cert.CertificateNotYetValidException
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class EnvSigningConfig(
    val keystorePath: String,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String,
    val sourceLabel: String = ".env (BuildConfig)"
) {
    val hasKeystorePath: Boolean
        get() = keystorePath.isNotBlank() && !isPlaceholder(keystorePath)

    val hasStorePassword: Boolean
        get() = storePassword.isNotBlank() && !isPlaceholder(storePassword)

    val hasKeyAlias: Boolean
        get() = keyAlias.isNotBlank() && !isPlaceholder(keyAlias)

    val hasKeyPassword: Boolean
        get() = keyPassword.isNotBlank() && !isPlaceholder(keyPassword)

    val hasAllRequiredSecrets: Boolean
        get() = hasKeystorePath && hasStorePassword && hasKeyAlias && hasKeyPassword

    companion object {
        private val PLACEHOLDERS = setOf(
            "MY_KEYSTORE_PATH",
            "MY_STORE_PASSWORD",
            "MY_KEY_ALIAS",
            "MY_KEY_PASSWORD",
            "null"
        )

        internal fun isPlaceholder(value: String): Boolean {
            val trimmed = value.trim()
            return trimmed.isEmpty() || trimmed in PLACEHOLDERS || trimmed.startsWith("MY_")
        }
    }
}

enum class KeystoreValidationStatus {
    VALID,
    INVALID_PASSWORD,
    ALIAS_NOT_FOUND,
    KEY_PASSWORD_MISMATCH,
    EXPIRED_CERT,
    CORRUPTED_FILE,
    MISSING_ENV_CREDENTIALS,
    FILE_NOT_FOUND
}

enum class SigningOverallStatus {
    VALID,
    WARNING,
    INVALID
}

data class KeystoreFileValidation(
    val fileName: String,
    val absolutePath: String,
    val sizeBytes: Long,
    val formatLabel: String,
    val status: KeystoreValidationStatus,
    val statusMessage: String,
    val matchedAlias: String? = null,
    val availableAliases: List<String> = emptyList(),
    val certificateSubject: String? = null,
    val validUntilMs: Long? = null,
    val sha256Fingerprint: String? = null
) {
    val isValid: Boolean
        get() = status == KeystoreValidationStatus.VALID
}

data class SigningSummaryReport(
    val envConfig: EnvSigningConfig,
    val targetVariant: BuildVariant,
    val validations: List<KeystoreFileValidation>,
    val overallStatus: SigningOverallStatus,
    val headline: String,
    val detailMessage: String
)

/**
 * Validates existing `.jks` keystore files against `.env` signing credentials
 * injected into [BuildConfig] via the Secrets Gradle Plugin (or an explicit `.env` file).
 */
object KeystoreValidator {

    private const val KEYSTORES_DIR_NAME = "keystores"
    private const val JKS_MAGIC = -17957139 // 0xFEEDFEED

    fun keystoreDir(context: Context): File =
        File(context.filesDir, KEYSTORES_DIR_NAME).apply { mkdirs() }

    fun loadEnvSigningConfig(context: Context? = null): EnvSigningConfig {
        val buildConfigValues = readFromBuildConfig()
        if (context == null) return buildConfigValues

        val candidateDotEnvFiles = listOf(
            File(keystoreDir(context), ".env"),
            File(context.filesDir, ".env")
        )
        val localDotEnv = candidateDotEnvFiles.firstOrNull { it.isFile && it.length() > 0L }
        if (localDotEnv != null) {
            return runCatching {
                parseDotEnv(
                    raw = localDotEnv.readText(),
                    fallback = buildConfigValues,
                    sourceLabel = ".env (Device Override)"
                )
            }.getOrDefault(buildConfigValues)
        }

        val vaultConfig = ReleaseKeystoreManager.loadActiveEnvSigningConfig(context)
        return vaultConfig ?: buildConfigValues
    }

    internal fun readFromBuildConfig(): EnvSigningConfig {
        val path = runCatching { BuildConfig.KEYSTORE_PATH }.getOrDefault("").trim()
        val storePass = runCatching { BuildConfig.STORE_PASSWORD }.getOrDefault("").trim()
        val alias = runCatching { BuildConfig.KEY_ALIAS }.getOrDefault("").trim()
        val keyPass = runCatching { BuildConfig.KEY_PASSWORD }.getOrDefault("").trim()
        return EnvSigningConfig(
            keystorePath = path,
            storePassword = storePass,
            keyAlias = alias,
            keyPassword = keyPass,
            sourceLabel = ".env (BuildConfig)"
        )
    }

    fun parseDotEnv(
        raw: String,
        fallback: EnvSigningConfig = EnvSigningConfig("", "", "", ""),
        sourceLabel: String = ".env"
    ): EnvSigningConfig {
        val parsed = mutableMapOf<String, String>()
        raw.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEach
            val idx = trimmed.indexOf('=')
            if (idx <= 0) return@forEach
            val key = trimmed.substring(0, idx).trim()
            var value = trimmed.substring(idx + 1).trim()
            if ((value.startsWith("\"") && value.endsWith("\"") && value.length >= 2) ||
                (value.startsWith("'") && value.endsWith("'") && value.length >= 2)
            ) {
                value = value.substring(1, value.length - 1)
            }
            parsed[key] = value
        }

        return EnvSigningConfig(
            keystorePath = parsed["KEYSTORE_PATH"] ?: fallback.keystorePath,
            storePassword = parsed["STORE_PASSWORD"] ?: fallback.storePassword,
            keyAlias = parsed["KEY_ALIAS"] ?: fallback.keyAlias,
            keyPassword = parsed["KEY_PASSWORD"] ?: fallback.keyPassword,
            sourceLabel = sourceLabel
        )
    }

    fun discoverJksFiles(context: Context, envConfig: EnvSigningConfig): List<File> {
        val seenCanonicalPaths = HashSet<String>()
        val found = mutableListOf<File>()

        fun addIfJks(candidate: File) {
            if (!candidate.isFile || candidate.length() <= 0L) return
            val canonical = runCatching { candidate.canonicalPath }.getOrDefault(candidate.absolutePath)
            if (seenCanonicalPaths.add(canonical)) {
                found.add(candidate)
            }
        }

        // 1. Check explicit KEYSTORE_PATH if present on disk or relative to app directories
        if (envConfig.hasKeystorePath) {
            val rawPath = envConfig.keystorePath
            val direct = File(rawPath)
            addIfJks(direct)
            val fileNameOnly = direct.name
            addIfJks(File(keystoreDir(context), fileNameOnly))
            addIfJks(File(context.filesDir, fileNameOnly))
            addIfJks(File(File(context.filesDir, "apk_signing"), fileNameOnly))
        }

        // 2. Scan managed keystore directory and app filesDir for existing .jks / .keystore files
        listOf(
            keystoreDir(context),
            File(context.filesDir, "apk_signing"),
            context.filesDir
        ).forEach { dir ->
            dir.listFiles()
                ?.filter {
                    it.isFile &&
                        !it.name.startsWith(".") &&
                        (it.name.endsWith(".jks", ignoreCase = true) ||
                            it.name.endsWith(".keystore", ignoreCase = true))
                }
                ?.sortedBy { it.name.lowercase(Locale.US) }
                ?.forEach { addIfJks(it) }
        }

        return found
    }

    suspend fun evaluateSigningSummary(
        context: Context,
        targetVariant: BuildVariant,
        overrideEnvConfig: EnvSigningConfig? = null
    ): SigningSummaryReport = withContext(Dispatchers.IO) {
        val envConfig = overrideEnvConfig ?: loadEnvSigningConfig(context)
        val jksFiles = discoverJksFiles(context, envConfig)
        buildReport(targetVariant = targetVariant, envConfig = envConfig, jksFiles = jksFiles)
    }

    internal fun buildReport(
        targetVariant: BuildVariant,
        envConfig: EnvSigningConfig,
        jksFiles: List<File>
    ): SigningSummaryReport {
        val validations = jksFiles.map { validateKeystoreFile(it, envConfig) }

        val validCount = validations.count { it.isValid }
        val errorCount = validations.count {
            it.status != KeystoreValidationStatus.VALID &&
                it.status != KeystoreValidationStatus.MISSING_ENV_CREDENTIALS
        }

        val overallStatus: SigningOverallStatus
        val headline: String
        val detailMessage: String

        when {
            validations.isEmpty() -> {
                if (targetVariant == BuildVariant.RELEASE) {
                    overallStatus = SigningOverallStatus.INVALID
                    headline = "No .jks Keystore Found for Release"
                    detailMessage = "Expected '${envConfig.keystorePath.ifBlank { "my-upload-key.jks" }}' from .env. Import an existing .jks keystore file or configure KEYSTORE_PATH, STORE_PASSWORD, and KEY_PASSWORD in the AI Studio Secrets panel."
                } else {
                    overallStatus = SigningOverallStatus.WARNING
                    headline = "No .jks File Found (Local Fallback Active)"
                    detailMessage = "No .jks file detected in app storage or at KEYSTORE_PATH. Debug builds can sign with the local debug RSA-2048 identity, or you can import a .jks file to validate release credentials."
                }
            }

            errorCount > 0 && validCount == 0 -> {
                overallStatus = SigningOverallStatus.INVALID
                headline = "Keystore Validation Failed"
                detailMessage = validations.first().statusMessage
            }

            !envConfig.hasStorePassword || !envConfig.hasKeyPassword -> {
                overallStatus = if (targetVariant == BuildVariant.RELEASE) {
                    SigningOverallStatus.INVALID
                } else {
                    SigningOverallStatus.WARNING
                }
                headline = "Missing .env Signing Passwords"
                detailMessage = "Found ${validations.size} .jks file(s), but STORE_PASSWORD or KEY_PASSWORD in .env is missing or still using a placeholder. Configure them in the AI Studio Secrets panel."
            }

            validCount > 0 && errorCount == 0 -> {
                overallStatus = SigningOverallStatus.VALID
                headline = if (validCount == 1) {
                    "Keystore Validated Against .env"
                } else {
                    "$validCount Keystores Validated Against .env"
                }
                val primary = validations.first { it.isValid }
                detailMessage = "${primary.fileName} unlocked with .env credentials (alias '${primary.matchedAlias ?: envConfig.keyAlias}'). Ready for APK signing."
            }

            else -> {
                overallStatus = SigningOverallStatus.WARNING
                headline = "$validCount Valid • $errorCount Invalid Keystore(s)"
                detailMessage = "At least one .jks file passed validation, while another failed .env credential checks. Review the per-file details below."
            }
        }

        return SigningSummaryReport(
            envConfig = envConfig,
            targetVariant = targetVariant,
            validations = validations,
            overallStatus = overallStatus,
            headline = headline,
            detailMessage = detailMessage
        )
    }

    fun validateKeystoreFile(file: File, envConfig: EnvSigningConfig): KeystoreFileValidation {
        val fileName = file.name
        val path = file.absolutePath
        if (!file.isFile || file.length() == 0L) {
            return KeystoreFileValidation(
                fileName = fileName,
                absolutePath = path,
                sizeBytes = 0L,
                formatLabel = "Missing",
                status = KeystoreValidationStatus.FILE_NOT_FOUND,
                statusMessage = "Keystore file '$fileName' does not exist or is empty."
            )
        }

        val rawBytes = runCatching { file.readBytes() }.getOrElse { err ->
            return KeystoreFileValidation(
                fileName = fileName,
                absolutePath = path,
                sizeBytes = file.length(),
                formatLabel = "Unreadable",
                status = KeystoreValidationStatus.CORRUPTED_FILE,
                statusMessage = "Unable to read '$fileName': ${err.localizedMessage ?: "I/O error"}"
            )
        }

        val formatLabel = detectKeystoreFormat(rawBytes)
        if (formatLabel == "Unknown") {
            return KeystoreFileValidation(
                fileName = fileName,
                absolutePath = path,
                sizeBytes = rawBytes.size.toLong(),
                formatLabel = formatLabel,
                status = KeystoreValidationStatus.CORRUPTED_FILE,
                statusMessage = "'$fileName' is neither a valid JKS (0xFEEDFEED) nor PKCS#12 keystore."
            )
        }

        if (!envConfig.hasStorePassword || !envConfig.hasKeyPassword) {
            return KeystoreFileValidation(
                fileName = fileName,
                absolutePath = path,
                sizeBytes = rawBytes.size.toLong(),
                formatLabel = formatLabel,
                status = KeystoreValidationStatus.MISSING_ENV_CREDENTIALS,
                statusMessage = "Set STORE_PASSWORD and KEY_PASSWORD in .env (Secrets panel) to unlock and verify '$fileName'."
            )
        }

        val preferredTypes = when (formatLabel) {
            "JKS" -> listOf("JKS", "PKCS12")
            else -> listOf("PKCS12", "JKS")
        }

        var loadedWithProvider: KeyStore? = null
        var loadedType = formatLabel
        var loadFailure: Throwable? = null

        for (ksType in preferredTypes) {
            val ks = runCatching { KeyStore.getInstance(ksType) }.getOrNull() ?: continue
            try {
                ByteArrayInputStream(rawBytes).use { input ->
                    ks.load(input, envConfig.storePassword.toCharArray())
                }
                loadedWithProvider = ks
                loadedType = ksType
                loadFailure = null
                break
            } catch (t: Throwable) {
                loadFailure = t
                if (isPasswordFailure(t)) {
                    return KeystoreFileValidation(
                        fileName = fileName,
                        absolutePath = path,
                        sizeBytes = rawBytes.size.toLong(),
                        formatLabel = ksType,
                        status = KeystoreValidationStatus.INVALID_PASSWORD,
                        statusMessage = "STORE_PASSWORD in .env failed integrity check for '$fileName'."
                    )
                }
            }
        }

        if (loadedWithProvider != null) {
            return validateLoadedJavaKeyStore(
                keyStore = loadedWithProvider,
                fileName = fileName,
                path = path,
                sizeBytes = rawBytes.size.toLong(),
                formatLabel = loadedType,
                envConfig = envConfig
            )
        }

        // Pure-Kotlin JKS v1/v2 binary fallback when Android's Security Provider omits "JKS"
        if (formatLabel == "JKS") {
            return validateLegacyJksBinary(
                rawBytes = rawBytes,
                fileName = fileName,
                path = path,
                envConfig = envConfig
            )
        }

        return KeystoreFileValidation(
            fileName = fileName,
            absolutePath = path,
            sizeBytes = rawBytes.size.toLong(),
            formatLabel = formatLabel,
            status = KeystoreValidationStatus.CORRUPTED_FILE,
            statusMessage = loadFailure?.localizedMessage ?: "Failed to parse keystore '$fileName'."
        )
    }

    private fun validateLoadedJavaKeyStore(
        keyStore: KeyStore,
        fileName: String,
        path: String,
        sizeBytes: Long,
        formatLabel: String,
        envConfig: EnvSigningConfig
    ): KeystoreFileValidation {
        val aliases = keyStore.aliases().toList()
        val targetAlias = envConfig.keyAlias.trim().ifBlank { "upload" }
        val matchedAlias = aliases.firstOrNull { it.equals(targetAlias, ignoreCase = true) }

        if (matchedAlias == null) {
            return KeystoreFileValidation(
                fileName = fileName,
                absolutePath = path,
                sizeBytes = sizeBytes,
                formatLabel = formatLabel,
                status = KeystoreValidationStatus.ALIAS_NOT_FOUND,
                statusMessage = "KEY_ALIAS '$targetAlias' not found in '$fileName'. Available aliases: ${aliases.ifEmpty { listOf("none") }.joinToString(", ")}.",
                availableAliases = aliases
            )
        }

        val privateKey: PrivateKey? = try {
            keyStore.getKey(matchedAlias, envConfig.keyPassword.toCharArray()) as? PrivateKey
        } catch (_: UnrecoverableKeyException) {
            return KeystoreFileValidation(
                fileName = fileName,
                absolutePath = path,
                sizeBytes = sizeBytes,
                formatLabel = formatLabel,
                status = KeystoreValidationStatus.KEY_PASSWORD_MISMATCH,
                statusMessage = "KEY_PASSWORD in .env does not match private key entry '$matchedAlias' in '$fileName'.",
                matchedAlias = matchedAlias,
                availableAliases = aliases
            )
        } catch (t: Throwable) {
            return KeystoreFileValidation(
                fileName = fileName,
                absolutePath = path,
                sizeBytes = sizeBytes,
                formatLabel = formatLabel,
                status = KeystoreValidationStatus.CORRUPTED_FILE,
                statusMessage = "Could not unlock key '$matchedAlias': ${t.localizedMessage ?: "Key error"}",
                matchedAlias = matchedAlias,
                availableAliases = aliases
            )
        }

        if (privateKey == null) {
            return KeystoreFileValidation(
                fileName = fileName,
                absolutePath = path,
                sizeBytes = sizeBytes,
                formatLabel = formatLabel,
                status = KeystoreValidationStatus.ALIAS_NOT_FOUND,
                statusMessage = "Alias '$matchedAlias' in '$fileName' does not hold a signing private key.",
                matchedAlias = matchedAlias,
                availableAliases = aliases
            )
        }

        val cert = keyStore.getCertificate(matchedAlias) as? X509Certificate
        return finalizeCertCheck(
            cert = cert,
            fileName = fileName,
            path = path,
            sizeBytes = sizeBytes,
            formatLabel = formatLabel,
            matchedAlias = matchedAlias,
            aliases = aliases
        )
    }

    /**
     * Verifies legacy JKS (magic 0xFEEDFEED) store password via Sun JKS SHA-1 integrity pre-image
     * and extracts aliases + X.509 certificate metadata without requiring a platform JKS provider.
     */
    internal fun validateLegacyJksBinary(
        rawBytes: ByteArray,
        fileName: String,
        path: String,
        envConfig: EnvSigningConfig
    ): KeystoreFileValidation {
        if (rawBytes.size < 32) {
            return KeystoreFileValidation(
                fileName = fileName,
                absolutePath = path,
                sizeBytes = rawBytes.size.toLong(),
                formatLabel = "JKS",
                status = KeystoreValidationStatus.CORRUPTED_FILE,
                statusMessage = "'$fileName' is truncated and cannot be a valid JKS file."
            )
        }

        val bodyLen = rawBytes.size - 20
        val expectedDigest = rawBytes.copyOfRange(bodyLen, rawBytes.size)
        val md = MessageDigest.getInstance("SHA-1")
        val passBytes = ByteArray(envConfig.storePassword.length * 2)
        envConfig.storePassword.forEachIndexed { idx, ch ->
            passBytes[idx * 2] = (ch.code ushr 8).toByte()
            passBytes[idx * 2 + 1] = ch.code.toByte()
        }
        md.update(passBytes)
        md.update("Mighty Aphrodite".toByteArray(Charsets.UTF_8))
        md.update(rawBytes, 0, bodyLen)
        val computedDigest = md.digest()

        if (!MessageDigest.isEqual(expectedDigest, computedDigest)) {
            return KeystoreFileValidation(
                fileName = fileName,
                absolutePath = path,
                sizeBytes = rawBytes.size.toLong(),
                formatLabel = "JKS",
                status = KeystoreValidationStatus.INVALID_PASSWORD,
                statusMessage = "STORE_PASSWORD in .env failed JKS integrity check for '$fileName'."
            )
        }

        return runCatching {
            val dis = DataInputStream(ByteArrayInputStream(rawBytes, 0, bodyLen))
            val magic = dis.readInt()
            val version = dis.readInt()
            if (magic != JKS_MAGIC || (version != 1 && version != 2)) {
                throw IOException("Unsupported JKS version $version")
            }
            val count = dis.readInt()
            val aliases = mutableListOf<String>()
            val certsByAlias = mutableMapOf<String, X509Certificate?>()
            val certFactory = CertificateFactory.getInstance("X.509")

            repeat(count) {
                val tag = dis.readInt()
                val alias = dis.readUTF()
                dis.readLong() // timestamp
                aliases += alias
                when (tag) {
                    1 -> {
                        val encKeyLen = dis.readInt()
                        if (encKeyLen > 0) dis.skipBytes(encKeyLen)
                        val chainLen = dis.readInt()
                        var firstCert: X509Certificate? = null
                        repeat(chainLen) { certIdx ->
                            if (version == 2) dis.readUTF()
                            val certBytes = ByteArray(dis.readInt())
                            dis.readFully(certBytes)
                            if (certIdx == 0) {
                                firstCert = runCatching {
                                    certFactory.generateCertificate(ByteArrayInputStream(certBytes)) as? X509Certificate
                                }.getOrNull()
                            }
                        }
                        certsByAlias[alias] = firstCert
                    }
                    2 -> {
                        if (version == 2) dis.readUTF()
                        val certBytes = ByteArray(dis.readInt())
                        dis.readFully(certBytes)
                        certsByAlias[alias] = runCatching {
                            certFactory.generateCertificate(ByteArrayInputStream(certBytes)) as? X509Certificate
                        }.getOrNull()
                    }
                    else -> throw IOException("Unknown JKS entry tag $tag")
                }
            }

            val targetAlias = envConfig.keyAlias.trim().ifBlank { "upload" }
            val matchedAlias = aliases.firstOrNull { it.equals(targetAlias, ignoreCase = true) }
            if (matchedAlias == null) {
                return KeystoreFileValidation(
                    fileName = fileName,
                    absolutePath = path,
                    sizeBytes = rawBytes.size.toLong(),
                    formatLabel = "JKS",
                    status = KeystoreValidationStatus.ALIAS_NOT_FOUND,
                    statusMessage = "KEY_ALIAS '$targetAlias' not found in '$fileName'. Available aliases: ${aliases.ifEmpty { listOf("none") }.joinToString(", ")}.",
                    availableAliases = aliases
                )
            }

            finalizeCertCheck(
                cert = certsByAlias[matchedAlias],
                fileName = fileName,
                path = path,
                sizeBytes = rawBytes.size.toLong(),
                formatLabel = "JKS",
                matchedAlias = matchedAlias,
                aliases = aliases
            )
        }.getOrElse { err ->
            KeystoreFileValidation(
                fileName = fileName,
                absolutePath = path,
                sizeBytes = rawBytes.size.toLong(),
                formatLabel = "JKS",
                status = KeystoreValidationStatus.CORRUPTED_FILE,
                statusMessage = "Failed parsing JKS entries in '$fileName': ${err.localizedMessage ?: "Malformed binary"}"
            )
        }
    }

    private fun finalizeCertCheck(
        cert: X509Certificate?,
        fileName: String,
        path: String,
        sizeBytes: Long,
        formatLabel: String,
        matchedAlias: String,
        aliases: List<String>
    ): KeystoreFileValidation {
        if (cert == null) {
            return KeystoreFileValidation(
                fileName = fileName,
                absolutePath = path,
                sizeBytes = sizeBytes,
                formatLabel = formatLabel,
                status = KeystoreValidationStatus.VALID,
                statusMessage = "Verified store password and key alias '$matchedAlias' in '$fileName'.",
                matchedAlias = matchedAlias,
                availableAliases = aliases
            )
        }

        val sha256 = ApkBuilder.formatSha256Fingerprint(cert.encoded)
        val subject = cert.subjectX500Principal?.name ?: cert.subjectDN?.name
        val expiryMs = cert.notAfter?.time
        val expiryStr = expiryMs?.let {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(it))
        } ?: "Unknown"

        try {
            cert.checkValidity()
        } catch (_: CertificateExpiredException) {
            return KeystoreFileValidation(
                fileName = fileName,
                absolutePath = path,
                sizeBytes = sizeBytes,
                formatLabel = formatLabel,
                status = KeystoreValidationStatus.EXPIRED_CERT,
                statusMessage = "Certificate for '$matchedAlias' in '$fileName' expired on $expiryStr.",
                matchedAlias = matchedAlias,
                availableAliases = aliases,
                certificateSubject = subject,
                validUntilMs = expiryMs,
                sha256Fingerprint = sha256
            )
        } catch (_: CertificateNotYetValidException) {
            return KeystoreFileValidation(
                fileName = fileName,
                absolutePath = path,
                sizeBytes = sizeBytes,
                formatLabel = formatLabel,
                status = KeystoreValidationStatus.EXPIRED_CERT,
                statusMessage = "Certificate for '$matchedAlias' in '$fileName' is not valid yet.",
                matchedAlias = matchedAlias,
                availableAliases = aliases,
                certificateSubject = subject,
                validUntilMs = expiryMs,
                sha256Fingerprint = sha256
            )
        }

        return KeystoreFileValidation(
            fileName = fileName,
            absolutePath = path,
            sizeBytes = sizeBytes,
            formatLabel = formatLabel,
            status = KeystoreValidationStatus.VALID,
            statusMessage = "Verified $formatLabel keystore, store/key passwords, and alias '$matchedAlias' (valid until $expiryStr).",
            matchedAlias = matchedAlias,
            availableAliases = aliases,
            certificateSubject = subject,
            validUntilMs = expiryMs,
            sha256Fingerprint = sha256
        )
    }

    suspend fun importJksFromUri(
        context: Context,
        sourceUri: Uri
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val rawName = queryDisplayName(context, sourceUri)
                ?: sourceUri.lastPathSegment
                ?: "my-upload-key.jks"
            val cleanedName = rawName.substringAfterLast('/').substringAfterLast('\\')
                .replace(Regex("[^A-Za-z0-9._-]"), "_")
            if (!cleanedName.endsWith(".jks", ignoreCase = true) &&
                !cleanedName.endsWith(".keystore", ignoreCase = true)
            ) {
                throw IllegalArgumentException("Please select a .jks or .keystore file (selected: $cleanedName)")
            }

            val dest = File(keystoreDir(context), cleanedName)
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(dest).use { output ->
                    input.copyTo(output, bufferSize = 32 * 1024)
                }
            } ?: throw IOException("Unable to read selected keystore file.")

            if (!dest.exists() || dest.length() == 0L) {
                dest.delete()
                throw IOException("Selected keystore file is empty.")
            }
            dest
        }
    }

    fun deleteImportedKeystore(context: Context, filePath: String): Boolean {
        val target = File(filePath)
        val managedDir = runCatching { keystoreDir(context).canonicalPath }.getOrNull() ?: return false
        val targetCanonical = runCatching { target.canonicalPath }.getOrNull() ?: return false
        if (!targetCanonical.startsWith(managedDir)) return false
        return target.delete()
    }

    internal fun detectKeystoreFormat(bytes: ByteArray): String {
        if (bytes.size >= 4) {
            val magic = ((bytes[0].toInt() and 0xFF) shl 24) or
                ((bytes[1].toInt() and 0xFF) shl 16) or
                ((bytes[2].toInt() and 0xFF) shl 8) or
                (bytes[3].toInt() and 0xFF)
            if (magic == JKS_MAGIC) return "JKS"
        }
        if (bytes.isNotEmpty() && bytes[0] == 0x30.toByte()) {
            return "PKCS12"
        }
        return "Unknown"
    }

    private fun isPasswordFailure(error: Throwable): Boolean {
        val msg = (error.message.orEmpty() + " " + error.cause?.message.orEmpty()).lowercase(Locale.US)
        return error.cause is UnrecoverableKeyException ||
            msg.contains("password was incorrect") ||
            msg.contains("tampered") ||
            msg.contains("mac check") ||
            msg.contains("wrong password")
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
