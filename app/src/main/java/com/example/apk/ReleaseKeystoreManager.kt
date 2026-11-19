package com.example.apk

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.android.apksig.ApkSigner
import com.android.apksig.ApkVerifier
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.UnrecoverableKeyException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class KeystoreAlreadyExistsException(
    val existingFileName: String
) : IllegalStateException(
    "A release keystore ('$existingFileName') already exists. Confirm replacement before overwriting."
)

data class KeystoreGenerationConfig(
    val fileName: String = "",
    val keyAlias: String = "",
    val commonName: String = "AiAppStudio Release",
    val organizationalUnit: String = "Mobile Engineering",
    val organization: String = "AiAppStudio",
    val locality: String = "San Francisco",
    val state: String = "CA",
    val country: String = "US",
    val keySizeBits: Int = 2048,
    val validityYears: Int = 25
)

data class KeystoreInfo(
    val fileName: String,
    val keystorePath: String,
    val fileSizeBytes: Long,
    val format: String,
    val keyAlias: String,
    val availableAliases: List<String>,
    val keyAlgorithm: String,
    val certificateSubject: String,
    val certificateIssuer: String,
    val serialNumberHex: String,
    val validFromMs: Long,
    val validUntilMs: Long,
    val daysRemaining: Long,
    val sha256Fingerprint: String,
    val sha1Fingerprint: String,
    val createdAtMs: Long,
    val lastBackedUpAtMs: Long?,
    val hasEnvVariablesConfigured: Boolean,
    val isGradleAliasCompatible: Boolean
) {
    val isBackedUp: Boolean
        get() = lastBackedUpAtMs != null && lastBackedUpAtMs > 0L
}

data class SigningVerificationCheck(
    val name: String,
    val passed: Boolean,
    val detail: String
)

data class SigningTestReport(
    val passed: Boolean,
    val keystoreUnlocked: Boolean,
    val keypairVerified: Boolean,
    val apkSigned: Boolean,
    val apkVerified: Boolean,
    val verifiedV1: Boolean,
    val verifiedV2: Boolean,
    val verifiedV3: Boolean,
    val signerSha256: String?,
    val checks: List<SigningVerificationCheck>,
    val summary: String,
    val testedAtMs: Long = System.currentTimeMillis()
)

data class ActiveReleaseSigningPair(
    val privateKey: PrivateKey,
    val certificate: X509Certificate,
    val alias: String,
    val keystoreFileName: String,
    val sha256Fingerprint: String
) {
    override fun toString(): String =
        "ActiveReleaseSigningPair(alias='$alias', keystoreFileName='$keystoreFileName', sha256='$sha256Fingerprint', privateKey=[REDACTED])"
}

/**
 * Manages automatic generation, persistent local storage, AES-256-GCM encrypted credential storage,
 * import, inspection, backup, and real Android ApkSigner/ApkVerifier validation of release Keystores.
 *
 * Security invariants:
 * - Passwords are generated via [SecureRandom] and NEVER hardcoded or logged.
 * - Credentials (`KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) are stored encrypted
 *   with AES-256-GCM backed by AndroidKeyStore (no plaintext passwords on disk).
 * - Private keys are never exposed to UI models or external services.
 * - Existing keystores are NEVER overwritten without explicit confirmation (`allowOverwrite = true`).
 */
object ReleaseKeystoreManager {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val VAULT_AES_KEY_ALIAS = "AiAppStudioReleaseVaultAes256"
    private const val VAULT_FILE_NAME = ".release_keystore_vault.enc"
    private const val FALLBACK_WRAP_FILE_NAME = ".release_vault_wrap.key"
    private const val GCM_IV_LENGTH_BYTES = 12
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val MS_PER_DAY = 86_400_000L

    private val UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    private val LOWER = "abcdefghijkmnopqrstuvwxyz"
    private val DIGITS = "23456789"
    private val SYMBOLS = "!@#%^*_-+="
    private val ALL_PASSWORD_CHARS = UPPER + LOWER + DIGITS + SYMBOLS

    private data class VaultRecord(
        val keystorePath: String,
        val storePassword: String,
        val keyAlias: String,
        val keyPassword: String,
        val format: String,
        val createdAtMs: Long,
        val lastBackedUpAtMs: Long? = null
    ) {
        override fun toString(): String =
            "VaultRecord(keystorePath='$keystorePath', keyAlias='$keyAlias', storePassword=[REDACTED], keyPassword=[REDACTED], format='$format')"
    }

    fun keystoreStorageDir(context: Context): File =
        KeystoreValidator.keystoreDir(context)

    fun vaultFile(context: Context): File =
        File(keystoreStorageDir(context), VAULT_FILE_NAME)

    fun hasActiveKeystore(context: Context): Boolean {
        val record = readVaultRecord(context) ?: return false
        val file = File(record.keystorePath)
        return file.isFile && file.length() > 0L
    }

    /**
     * Generates a cryptographically strong random password with guaranteed character class diversity.
     */
    fun generateSecurePassword(length: Int = 24): String {
        val effectiveLength = length.coerceAtLeast(16)
        val rng = SecureRandom()
        val chars = CharArray(effectiveLength)
        chars[0] = UPPER[rng.nextInt(UPPER.length)]
        chars[1] = UPPER[rng.nextInt(UPPER.length)]
        chars[2] = LOWER[rng.nextInt(LOWER.length)]
        chars[3] = LOWER[rng.nextInt(LOWER.length)]
        chars[4] = DIGITS[rng.nextInt(DIGITS.length)]
        chars[5] = DIGITS[rng.nextInt(DIGITS.length)]
        chars[6] = SYMBOLS[rng.nextInt(SYMBOLS.length)]
        chars[7] = SYMBOLS[rng.nextInt(SYMBOLS.length)]
        for (i in 8 until effectiveLength) {
            chars[i] = ALL_PASSWORD_CHARS[rng.nextInt(ALL_PASSWORD_CHARS.length)]
        }
        // Fisher-Yates shuffle using SecureRandom
        for (i in effectiveLength - 1 downTo 1) {
            val j = rng.nextInt(i + 1)
            val tmp = chars[i]
            chars[i] = chars[j]
            chars[j] = tmp
        }
        return String(chars)
    }

    /**
     * Generates a unique release key alias such as `release_20261119_a4f9c2`.
     */
    fun generateUniqueKeyAlias(prefix: String = "release"): String {
        val safePrefix = prefix.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9_]"), "")
            .ifBlank { "release" }
            .take(16)
        val datePart = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
        val randomBytes = ByteArray(4).also { SecureRandom().nextBytes(it) }
        val hexSuffix = randomBytes.joinToString("") { "%02x".format(Locale.US, it) }
        return "${safePrefix}_${datePart}_$hexSuffix"
    }

    private fun generateUniqueKeystoreFileName(): String {
        val datePart = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
        val randomBytes = ByteArray(3).also { SecureRandom().nextBytes(it) }
        val hexSuffix = randomBytes.joinToString("") { "%02x".format(Locale.US, it) }
        return "release-key-$datePart-$hexSuffix.jks"
    }

    /**
     * Automatically generates a brand-new Android release Keystore with:
     * - Secure random store password
     * - Distinct secure random key password
     * - Unique key alias (or user-provided alias)
     * - RSA 2048/4096-bit keypair + self-signed X.509v3 certificate
     * - Persistent storage in `filesDir/keystores/` with owner-only permissions
     * - Encrypted credential vault (`KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`)
     */
    suspend fun generateNewReleaseKeystore(
        context: Context,
        config: KeystoreGenerationConfig = KeystoreGenerationConfig(),
        allowOverwrite: Boolean = false
    ): Result<KeystoreInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val existingRecord = readVaultRecord(context)
            if (existingRecord != null && File(existingRecord.keystorePath).isFile && !allowOverwrite) {
                throw KeystoreAlreadyExistsException(File(existingRecord.keystorePath).name)
            }

            val sanitizedAlias = config.keyAlias.trim()
                .replace(Regex("[^A-Za-z0-9._-]"), "_")
                .ifBlank { generateUniqueKeyAlias() }

            val rawFileName = config.fileName.trim()
                .substringAfterLast('/')
                .substringAfterLast('\\')
                .replace(Regex("[^A-Za-z0-9._-]"), "_")
                .ifBlank { generateUniqueKeystoreFileName() }
            val finalFileName = if (
                rawFileName.endsWith(".jks", ignoreCase = true) ||
                rawFileName.endsWith(".keystore", ignoreCase = true)
            ) {
                rawFileName
            } else {
                "$rawFileName.jks"
            }

            val keyBits = if (config.keySizeBits >= 4096) 4096 else 2048
            val validityYears = config.validityYears.coerceIn(1, 50)

            val storePassword = generateSecurePassword(24)
            var keyPassword = generateSecurePassword(24)
            while (keyPassword == storePassword) {
                keyPassword = generateSecurePassword(24)
            }

            val keyPair = KeyPairGenerator.getInstance("RSA").apply {
                initialize(keyBits, SecureRandom())
            }.generateKeyPair()

            val certDerBytes = generateReleaseSelfSignedX509Der(
                keyPair = keyPair,
                config = config,
                validityYears = validityYears
            )
            val certificate = CertificateFactory.getInstance("X.509")
                .generateCertificate(ByteArrayInputStream(certDerBytes)) as X509Certificate

            val targetDir = keystoreStorageDir(context)
            val targetFile = File(targetDir, finalFileName)
            if (targetFile.exists() && !allowOverwrite) {
                throw KeystoreAlreadyExistsException(targetFile.name)
            }

            val (savedFormat, effectiveKeyPassword) = writeAndVerifyKeystoreFile(
                targetFile = targetFile,
                alias = sanitizedAlias,
                privateKey = keyPair.private,
                certificate = certificate,
                storePassword = storePassword,
                keyPassword = keyPassword
            )

            hardenFilePermissions(targetFile)

            val now = System.currentTimeMillis()
            val newRecord = VaultRecord(
                keystorePath = targetFile.absolutePath,
                storePassword = storePassword,
                keyAlias = sanitizedAlias,
                keyPassword = effectiveKeyPassword,
                format = savedFormat,
                createdAtMs = now,
                lastBackedUpAtMs = null
            )
            writeVaultRecord(context, newRecord)

            // Remove previous managed keystore file if we replaced it with a new file name
            if (allowOverwrite && existingRecord != null && existingRecord.keystorePath != targetFile.absolutePath) {
                val oldFile = File(existingRecord.keystorePath)
                if (isInsideManagedDir(context, oldFile) && oldFile.exists()) {
                    oldFile.delete()
                }
            }

            inspectVaultRecord(newRecord)
        }
    }

    /**
     * Imports an existing `.jks`, `.keystore`, or `.p12` keystore from a Storage Access Framework URI,
     * verifies its store password, key alias, private key password, and certificate, then persists
     * the keystore and encrypted credentials locally.
     */
    suspend fun importExistingKeystore(
        context: Context,
        sourceUri: Uri,
        storePassword: String,
        keyAlias: String?,
        keyPassword: String,
        allowOverwrite: Boolean = false
    ): Result<KeystoreInfo> = withContext(Dispatchers.IO) {
        runCatching {
            require(storePassword.isNotEmpty()) { "Store password is required." }
            val effectiveKeyPass = keyPassword.ifEmpty { storePassword }

            val existingRecord = readVaultRecord(context)
            if (existingRecord != null && File(existingRecord.keystorePath).isFile && !allowOverwrite) {
                throw KeystoreAlreadyExistsException(File(existingRecord.keystorePath).name)
            }

            val rawBytes = context.contentResolver.openInputStream(sourceUri)?.use { input ->
                input.readBytes()
            } ?: throw IOException("Unable to read selected keystore file.")
            require(rawBytes.isNotEmpty()) { "Selected keystore file is empty." }

            val detectedFormat = KeystoreValidator.detectKeystoreFormat(rawBytes)
            require(detectedFormat != "Unknown") {
                "Selected file is not a valid JKS or PKCS#12 Android Keystore."
            }

            val unlocked = unlockKeystoreBytes(
                rawBytes = rawBytes,
                detectedFormat = detectedFormat,
                storePassword = storePassword,
                requestedAlias = keyAlias?.trim().orEmpty(),
                keyPassword = effectiveKeyPass
            )

            val displayName = queryDisplayName(context, sourceUri)
                ?: sourceUri.lastPathSegment
                ?: "imported-release-key.jks"
            val cleanedName = displayName
                .substringAfterLast('/')
                .substringAfterLast('\\')
                .replace(Regex("[^A-Za-z0-9._-]"), "_")
                .ifBlank { "imported-release-key.jks" }
            val finalFileName = if (
                cleanedName.endsWith(".jks", ignoreCase = true) ||
                cleanedName.endsWith(".keystore", ignoreCase = true) ||
                cleanedName.endsWith(".p12", ignoreCase = true)
            ) {
                cleanedName
            } else {
                "$cleanedName.jks"
            }

            val targetFile = File(keystoreStorageDir(context), finalFileName)
            targetFile.writeBytes(rawBytes)
            hardenFilePermissions(targetFile)

            val newRecord = VaultRecord(
                keystorePath = targetFile.absolutePath,
                storePassword = storePassword,
                keyAlias = unlocked.matchedAlias,
                keyPassword = effectiveKeyPass,
                format = unlocked.loadedFormat,
                createdAtMs = System.currentTimeMillis(),
                lastBackedUpAtMs = null
            )
            writeVaultRecord(context, newRecord)

            if (allowOverwrite && existingRecord != null && existingRecord.keystorePath != targetFile.absolutePath) {
                val oldFile = File(existingRecord.keystorePath)
                if (isInsideManagedDir(context, oldFile) && oldFile.exists()) {
                    oldFile.delete()
                }
            }

            inspectVaultRecord(newRecord)
        }
    }

    /**
     * Returns safe, password-free metadata about the currently active release keystore, or null if none exists.
     */
    fun getActiveKeystoreInfo(context: Context): KeystoreInfo? {
        val record = readVaultRecord(context) ?: return null
        val file = File(record.keystorePath)
        if (!file.isFile || file.length() == 0L) return null
        return runCatching { inspectVaultRecord(record) }.getOrNull()
    }

    /**
     * Loads the active signing credentials as an [EnvSigningConfig] for [KeystoreValidator] and Gradle previews.
     */
    fun loadActiveEnvSigningConfig(context: Context): EnvSigningConfig? {
        val record = readVaultRecord(context) ?: return null
        val file = File(record.keystorePath)
        if (!file.isFile || file.length() == 0L) return null
        return EnvSigningConfig(
            keystorePath = record.keystorePath,
            storePassword = record.storePassword,
            keyAlias = record.keyAlias,
            keyPassword = record.keyPassword,
            sourceLabel = "Secure Keystore Vault"
        )
    }

    /**
     * Loads the unlocked [PrivateKey] and [X509Certificate] for signing release APKs inside [ApkBuilder].
     * Never exposes key bytes or passwords to UI or logs.
     */
    fun loadActiveSigningCredentials(context: Context): ActiveReleaseSigningPair? {
        val record = readVaultRecord(context) ?: return null
        val file = File(record.keystorePath)
        if (!file.isFile || file.length() == 0L) return null
        val rawBytes = runCatching { file.readBytes() }.getOrNull() ?: return null
        val detectedFormat = KeystoreValidator.detectKeystoreFormat(rawBytes)
        val unlocked = runCatching {
            unlockKeystoreBytes(
                rawBytes = rawBytes,
                detectedFormat = detectedFormat,
                storePassword = record.storePassword,
                requestedAlias = record.keyAlias,
                keyPassword = record.keyPassword
            )
        }.getOrNull() ?: return null

        return ActiveReleaseSigningPair(
            privateKey = unlocked.privateKey,
            certificate = unlocked.certificate,
            alias = unlocked.matchedAlias,
            keystoreFileName = file.name,
            sha256Fingerprint = ApkBuilder.formatSha256Fingerprint(unlocked.certificate.encoded)
        )
    }

    /**
     * Ensures a release keystore exists (generating one automatically if missing) and returns its signing pair.
     */
    suspend fun getOrCreateActiveSigningCredentials(
        context: Context
    ): Result<Pair<ActiveReleaseSigningPair, Boolean>> = withContext(Dispatchers.IO) {
        runCatching {
            val existing = loadActiveSigningCredentials(context)
            if (existing != null) {
                return@runCatching existing to false
            }
            generateNewReleaseKeystore(context = context, allowOverwrite = false).getOrThrow()
            val generated = loadActiveSigningCredentials(context)
                ?: throw IllegalStateException("Newly generated release keystore could not be unlocked.")
            generated to true
        }
    }

    /**
     * Runs a comprehensive end-to-end signing & verification test:
     * 1. Verifies encrypted environment variables (`KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`)
     * 2. Unlocks keystore and private key
     * 3. Executes cryptographic RSA-SHA256 sign & verify challenge + certificate validity check
     * 4. Signs a real test APK using `com.android.apksig.ApkSigner` (v1 + v2 + v3)
     * 5. Verifies the signed APK using `com.android.apksig.ApkVerifier` and matches the signer SHA-256
     */
    suspend fun testSigningConfiguration(context: Context): SigningTestReport = withContext(Dispatchers.IO) {
        val checks = mutableListOf<SigningVerificationCheck>()
        val record = readVaultRecord(context)
        if (record == null) {
            checks += SigningVerificationCheck(
                name = "Encrypted Credentials Vault",
                passed = false,
                detail = "No active release keystore configured. Generate or import a keystore first."
            )
            return@withContext SigningTestReport(
                passed = false,
                keystoreUnlocked = false,
                keypairVerified = false,
                apkSigned = false,
                apkVerified = false,
                verifiedV1 = false,
                verifiedV2 = false,
                verifiedV3 = false,
                signerSha256 = null,
                checks = checks,
                summary = "No release keystore found. Tap 'Generate New Keystore' to create one automatically."
            )
        }

        val allEnvPresent = record.keystorePath.isNotBlank() &&
            record.storePassword.isNotBlank() &&
            record.keyAlias.isNotBlank() &&
            record.keyPassword.isNotBlank()
        checks += SigningVerificationCheck(
            name = "Environment Variables Configured",
            passed = allEnvPresent,
            detail = if (allEnvPresent) {
                "KEYSTORE_PATH, STORE_PASSWORD, KEY_ALIAS, and KEY_PASSWORD are securely stored (AES-256-GCM)."
            } else {
                "One or more required signing credentials are missing."
            }
        )

        val keystoreFile = File(record.keystorePath)
        if (!keystoreFile.isFile || keystoreFile.length() == 0L) {
            checks += SigningVerificationCheck(
                name = "Persistent Keystore File",
                passed = false,
                detail = "Keystore file not found at ${keystoreFile.name}."
            )
            return@withContext SigningTestReport(
                passed = false,
                keystoreUnlocked = false,
                keypairVerified = false,
                apkSigned = false,
                apkVerified = false,
                verifiedV1 = false,
                verifiedV2 = false,
                verifiedV3 = false,
                signerSha256 = null,
                checks = checks,
                summary = "Keystore file is missing from persistent storage."
            )
        }

        val unlocked = runCatching {
            val rawBytes = keystoreFile.readBytes()
            unlockKeystoreBytes(
                rawBytes = rawBytes,
                detectedFormat = KeystoreValidator.detectKeystoreFormat(rawBytes),
                storePassword = record.storePassword,
                requestedAlias = record.keyAlias,
                keyPassword = record.keyPassword
            )
        }.getOrElse { err ->
            checks += SigningVerificationCheck(
                name = "Keystore & Private Key Unlock",
                passed = false,
                detail = err.localizedMessage ?: "Failed to unlock keystore with stored credentials."
            )
            return@withContext SigningTestReport(
                passed = false,
                keystoreUnlocked = false,
                keypairVerified = false,
                apkSigned = false,
                apkVerified = false,
                verifiedV1 = false,
                verifiedV2 = false,
                verifiedV3 = false,
                signerSha256 = null,
                checks = checks,
                summary = "Keystore unlock failed."
            )
        }

        val expectedSha256 = ApkBuilder.formatSha256Fingerprint(unlocked.certificate.encoded)
        checks += SigningVerificationCheck(
            name = "Keystore & Private Key Unlock",
            passed = true,
            detail = "Unlocked ${unlocked.loadedFormat} keystore '${keystoreFile.name}' (alias '${unlocked.matchedAlias}')."
        )

        val keypairOk = runCatching {
            unlocked.certificate.checkValidity()
            val challenge = ByteArray(32).also { SecureRandom().nextBytes(it) }
            val sig = Signature.getInstance("SHA256withRSA")
            sig.initSign(unlocked.privateKey)
            sig.update(challenge)
            val signedBytes = sig.sign()

            val verifySig = Signature.getInstance("SHA256withRSA")
            verifySig.initVerify(unlocked.certificate.publicKey)
            verifySig.update(challenge)
            verifySig.verify(signedBytes)
        }.getOrDefault(false)

        checks += SigningVerificationCheck(
            name = "Certificate & Keypair Cryptographic Check",
            passed = keypairOk,
            detail = if (keypairOk) {
                "Certificate is valid and SHA256withRSA sign/verify challenge succeeded."
            } else {
                "Certificate validity or RSA cryptographic challenge failed."
            }
        )

        if (!keypairOk) {
            return@withContext SigningTestReport(
                passed = false,
                keystoreUnlocked = true,
                keypairVerified = false,
                apkSigned = false,
                apkVerified = false,
                verifiedV1 = false,
                verifiedV2 = false,
                verifiedV3 = false,
                signerSha256 = expectedSha256,
                checks = checks,
                summary = "Cryptographic keypair check failed."
            )
        }

        // Step 4 & 5: Real APK signing + Android ApkVerifier verification
        val tempDir = File(context.cacheDir, "signing_self_test").apply { mkdirs() }
        val tempRawApk = File(tempDir, "test_raw.apk")
        val tempAlignedApk = File(tempDir, "test_aligned.apk")
        val tempSignedApk = File(tempDir, "test_signed_release.apk")

        var apkSigned = false
        var apkVerified = false
        var v1Ok = false
        var v2Ok = false
        var v3Ok = false
        var verifiedSignerSha256: String? = null

        try {
            ApkBuilder.prepareUnsignedAlignedTestApk(context, tempRawApk, tempAlignedApk)

            val signerConfig = ApkSigner.SignerConfig.Builder(
                unlocked.matchedAlias,
                unlocked.privateKey,
                listOf(unlocked.certificate)
            ).build()

            ApkSigner.Builder(listOf(signerConfig))
                .setInputApk(tempAlignedApk)
                .setOutputApk(tempSignedApk)
                .setMinSdkVersion(24)
                .setV1SigningEnabled(true)
                .setV2SigningEnabled(true)
                .setV3SigningEnabled(true)
                .build()
                .sign()

            apkSigned = tempSignedApk.isFile && tempSignedApk.length() > 0L
            checks += SigningVerificationCheck(
                name = "Release APK Signing (ApkSigner)",
                passed = apkSigned,
                detail = if (apkSigned) {
                    "Signed test release APK (${tempSignedApk.length()} bytes) with v1 + v2 + v3 schemes."
                } else {
                    "Signed test APK output was empty."
                }
            )

            if (apkSigned) {
                val verification = ApkVerifier.Builder(tempSignedApk)
                    .setMinCheckedPlatformVersion(23)
                    .build()
                    .verify()

                v1Ok = verification.isVerifiedUsingV1Scheme
                v2Ok = verification.isVerifiedUsingV2Scheme
                v3Ok = verification.isVerifiedUsingV3Scheme
                val signerCert = verification.signerCertificates.firstOrNull()
                verifiedSignerSha256 = signerCert?.let { ApkBuilder.formatSha256Fingerprint(it.encoded) }
                val certMatches = verifiedSignerSha256 != null && verifiedSignerSha256 == expectedSha256
                apkVerified = verification.isVerified && v2Ok && certMatches && verification.errors.isEmpty()

                val schemesText = buildList {
                    if (v1Ok) add("v1")
                    if (v2Ok) add("v2")
                    if (v3Ok) add("v3")
                }.joinToString("+").ifBlank { "none" }

                checks += SigningVerificationCheck(
                    name = "Android ApkVerifier Signature Check",
                    passed = apkVerified,
                    detail = if (apkVerified) {
                        "Signature verified ($schemesText), 0 errors, certificate SHA-256 matches active keystore."
                    } else {
                        val errCount = verification.errors.size
                        "Verification failed (verified=${verification.isVerified}, schemes=$schemesText, errors=$errCount, certMatch=$certMatches)."
                    }
                )
            }
        } catch (t: Throwable) {
            checks += SigningVerificationCheck(
                name = "APK Signing & Verification Pipeline",
                passed = false,
                detail = t.localizedMessage ?: "APK signing verification threw an error."
            )
        } finally {
            tempRawApk.delete()
            tempAlignedApk.delete()
            tempSignedApk.delete()
        }

        val allPassed = allEnvPresent && keypairOk && apkSigned && apkVerified
        SigningTestReport(
            passed = allPassed,
            keystoreUnlocked = true,
            keypairVerified = keypairOk,
            apkSigned = apkSigned,
            apkVerified = apkVerified,
            verifiedV1 = v1Ok,
            verifiedV2 = v2Ok,
            verifiedV3 = v3Ok,
            signerSha256 = verifiedSignerSha256 ?: expectedSha256,
            checks = checks,
            summary = if (allPassed) {
                "Signing configuration verified! Release APK signed & passed Android ApkVerifier (v1+v2+v3)."
            } else {
                "Signing verification encountered an issue. Review the checklist details below."
            }
        )
    }

    /**
     * Copies the persistent keystore file to a user-selected backup destination via Storage Access Framework
     * and records the backup timestamp in the encrypted vault.
     */
    suspend fun backupKeystoreToUri(
        context: Context,
        destinationUri: Uri
    ): Result<KeystoreInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val record = readVaultRecord(context)
                ?: throw IllegalStateException("No active release keystore to back up.")
            val sourceFile = File(record.keystorePath)
            if (!sourceFile.isFile || sourceFile.length() == 0L) {
                throw IOException("Keystore file is missing and cannot be backed up.")
            }

            context.contentResolver.openOutputStream(destinationUri)?.use { output ->
                sourceFile.inputStream().use { input ->
                    input.copyTo(output, bufferSize = 32 * 1024)
                }
                output.flush()
            } ?: throw IOException("Could not open backup destination for writing.")

            val updatedRecord = record.copy(lastBackedUpAtMs = System.currentTimeMillis())
            writeVaultRecord(context, updatedRecord)
            inspectVaultRecord(updatedRecord)
        }
    }

    /**
     * Produces the Gradle / AI Studio Secrets environment variable block for the active keystore.
     * Passwords are masked by default unless [revealPasswords] is explicitly requested by the user.
     */
    fun formatEnvSnippet(
        context: Context,
        revealPasswords: Boolean = false
    ): String? {
        val record = readVaultRecord(context) ?: return null
        val fileName = File(record.keystorePath).name
        val storePassOut = if (revealPasswords) record.storePassword else "••••••••••••••••"
        val keyPassOut = if (revealPasswords) record.keyPassword else "••••••••••••••••"
        return buildString {
            appendLine("# Configure these in the AI Studio Secrets panel (never commit .env to git)")
            appendLine("KEYSTORE_PATH=\"$fileName\"")
            appendLine("STORE_PASSWORD=\"$storePassOut\"")
            appendLine("KEY_ALIAS=\"${record.keyAlias}\"")
            append("KEY_PASSWORD=\"$keyPassOut\"")
        }
    }

    fun deleteActiveKeystore(context: Context): Boolean {
        val record = readVaultRecord(context)
        var removed = false
        if (record != null) {
            val file = File(record.keystorePath)
            if (isInsideManagedDir(context, file) && file.exists()) {
                removed = file.delete() || removed
            }
        }
        val vault = vaultFile(context)
        if (vault.exists()) {
            removed = vault.delete() || removed
        }
        return removed
    }

    private data class UnlockedKeystore(
        val privateKey: PrivateKey,
        val certificate: X509Certificate,
        val matchedAlias: String,
        val availableAliases: List<String>,
        val loadedFormat: String
    )

    private fun writeAndVerifyKeystoreFile(
        targetFile: File,
        alias: String,
        privateKey: PrivateKey,
        certificate: X509Certificate,
        storePassword: String,
        keyPassword: String
    ): Pair<String, String> {
        val storeChars = storePassword.toCharArray()
        val keyChars = keyPassword.toCharArray()
        try {
            // Try modern standard PKCS12 with distinct storePassword & keyPassword first.
            val pkcs12Attempt = runCatching {
                val ks = KeyStore.getInstance("PKCS12")
                ks.load(null, null)
                ks.setKeyEntry(alias, privateKey, keyChars, arrayOf(certificate))
                val outBytes = ByteArrayOutputStream()
                ks.store(outBytes, storeChars)
                val bytes = outBytes.toByteArray()
                // Self-verify unlocking with BOTH passwords on the current runtime
                unlockKeystoreBytes(
                    rawBytes = bytes,
                    detectedFormat = "PKCS12",
                    storePassword = storePassword,
                    requestedAlias = alias,
                    keyPassword = keyPassword
                )
                targetFile.writeBytes(bytes)
                "PKCS12" to keyPassword
            }.getOrNull()

            if (pkcs12Attempt != null) {
                return pkcs12Attempt
            }

            // Fallback to JKS if the runtime supports JKS and enforces distinct key passwords there
            val jksAttempt = runCatching {
                val ks = KeyStore.getInstance("JKS")
                ks.load(null, null)
                ks.setKeyEntry(alias, privateKey, keyChars, arrayOf(certificate))
                val outBytes = ByteArrayOutputStream()
                ks.store(outBytes, storeChars)
                val bytes = outBytes.toByteArray()
                unlockKeystoreBytes(
                    rawBytes = bytes,
                    detectedFormat = "JKS",
                    storePassword = storePassword,
                    requestedAlias = alias,
                    keyPassword = keyPassword
                )
                targetFile.writeBytes(bytes)
                "JKS" to keyPassword
            }.getOrNull()

            if (jksAttempt != null) {
                return jksAttempt
            }

            // Final standard PKCS12 compatibility fallback for older Android providers that require
            // identical store & key MAC password inside PKCS12 bags
            val ks = KeyStore.getInstance("PKCS12")
            ks.load(null, null)
            ks.setKeyEntry(alias, privateKey, storeChars, arrayOf(certificate))
            FileOutputStream(targetFile).use { out ->
                ks.store(out, storeChars)
                out.flush()
            }
            return "PKCS12" to storePassword
        } finally {
            storeChars.fill('\u0000')
            keyChars.fill('\u0000')
        }
    }

    private fun unlockKeystoreBytes(
        rawBytes: ByteArray,
        detectedFormat: String,
        storePassword: String,
        requestedAlias: String,
        keyPassword: String
    ): UnlockedKeystore {
        val typesToTry = when (detectedFormat) {
            "JKS" -> listOf("JKS", "PKCS12")
            else -> listOf("PKCS12", "JKS")
        }

        val storePassChars = storePassword.toCharArray()
        val keyPassChars = keyPassword.toCharArray()
        var lastError: Throwable? = null

        try {
            for (ksType in typesToTry) {
                val ks = runCatching { KeyStore.getInstance(ksType) }.getOrNull() ?: continue
                try {
                    ByteArrayInputStream(rawBytes).use { input ->
                        ks.load(input, storePassChars)
                    }
                    val aliases = ks.aliases().toList()
                    val keyAliases = aliases.filter { ks.isKeyEntry(it) }
                    val matchedAlias = if (requestedAlias.isBlank()) {
                        keyAliases.firstOrNull()
                    } else {
                        keyAliases.firstOrNull { it.equals(requestedAlias, ignoreCase = true) }
                            ?: aliases.firstOrNull { it.equals(requestedAlias, ignoreCase = true) }
                    } ?: throw IllegalArgumentException(
                        if (requestedAlias.isBlank()) {
                            "No private key entry found in keystore."
                        } else {
                            "Key alias '$requestedAlias' not found. Available aliases: ${aliases.ifEmpty { listOf("none") }.joinToString(", ")}."
                        }
                    )

                    val privateKey = try {
                        ks.getKey(matchedAlias, keyPassChars) as? PrivateKey
                    } catch (e: UnrecoverableKeyException) {
                        throw IllegalArgumentException("Key password does not match alias '$matchedAlias'.", e)
                    } ?: throw IllegalArgumentException("Alias '$matchedAlias' does not contain a private key.")

                    val cert = ks.getCertificate(matchedAlias) as? X509Certificate
                        ?: throw IllegalArgumentException("Alias '$matchedAlias' does not contain an X.509 certificate.")

                    return UnlockedKeystore(
                        privateKey = privateKey,
                        certificate = cert,
                        matchedAlias = matchedAlias,
                        availableAliases = aliases,
                        loadedFormat = ksType
                    )
                } catch (e: IllegalArgumentException) {
                    throw e
                } catch (t: Throwable) {
                    lastError = t
                }
            }
        } finally {
            storePassChars.fill('\u0000')
            keyPassChars.fill('\u0000')
        }

        val msg = lastError?.localizedMessage?.lowercase(Locale.US).orEmpty()
        if (msg.contains("password") || msg.contains("mac") || msg.contains("tampered")) {
            throw IllegalArgumentException("Invalid keystore store password.")
        }
        throw IllegalStateException("Could not open keystore file: ${lastError?.localizedMessage ?: "Unsupported format"}")
    }

    private fun inspectVaultRecord(record: VaultRecord): KeystoreInfo {
        val file = File(record.keystorePath)
        val rawBytes = file.readBytes()
        val detectedFormat = KeystoreValidator.detectKeystoreFormat(rawBytes)
        val unlocked = unlockKeystoreBytes(
            rawBytes = rawBytes,
            detectedFormat = detectedFormat,
            storePassword = record.storePassword,
            requestedAlias = record.keyAlias,
            keyPassword = record.keyPassword
        )
        val cert = unlocked.certificate
        val rsaBits = (cert.publicKey as? RSAPublicKey)?.modulus?.bitLength()
        val algoLabel = if (rsaBits != null) "${cert.publicKey.algorithm} $rsaBits-bit" else cert.publicKey.algorithm
        val now = System.currentTimeMillis()
        val validUntilMs = cert.notAfter?.time ?: now
        val daysRemaining = ((validUntilMs - now) / MS_PER_DAY).coerceAtLeast(0L)

        return KeystoreInfo(
            fileName = file.name,
            keystorePath = file.absolutePath,
            fileSizeBytes = file.length(),
            format = unlocked.loadedFormat,
            keyAlias = unlocked.matchedAlias,
            availableAliases = unlocked.availableAliases,
            keyAlgorithm = algoLabel,
            certificateSubject = cert.subjectX500Principal?.name ?: "CN=Unknown",
            certificateIssuer = cert.issuerX500Principal?.name ?: "CN=Unknown",
            serialNumberHex = cert.serialNumber.toString(16).uppercase(Locale.US),
            validFromMs = cert.notBefore?.time ?: now,
            validUntilMs = validUntilMs,
            daysRemaining = daysRemaining,
            sha256Fingerprint = formatFingerprint(cert.encoded, "SHA-256"),
            sha1Fingerprint = formatFingerprint(cert.encoded, "SHA-1"),
            createdAtMs = record.createdAtMs,
            lastBackedUpAtMs = record.lastBackedUpAtMs,
            hasEnvVariablesConfigured = record.keystorePath.isNotBlank() &&
                record.storePassword.isNotBlank() &&
                record.keyAlias.isNotBlank() &&
                record.keyPassword.isNotBlank(),
            isGradleAliasCompatible = unlocked.matchedAlias == "upload"
        )
    }

    private fun formatFingerprint(certDerBytes: ByteArray, algorithm: String): String {
        val digest = MessageDigest.getInstance(algorithm).digest(certDerBytes)
        return digest.joinToString(":") { "%02X".format(Locale.US, it) }
    }

    // ==================== AES-256-GCM Encrypted Vault Persistence ====================

    private fun writeVaultRecord(context: Context, record: VaultRecord) {
        val json = JSONObject().apply {
            put("keystorePath", record.keystorePath)
            put("storePassword", record.storePassword)
            put("keyAlias", record.keyAlias)
            put("keyPassword", record.keyPassword)
            put("format", record.format)
            put("createdAtMs", record.createdAtMs)
            if (record.lastBackedUpAtMs != null) {
                put("lastBackedUpAtMs", record.lastBackedUpAtMs)
            }
        }.toString()

        val plainBytes = json.toByteArray(Charsets.UTF_8)
        val encrypted = encryptVaultBytes(context, plainBytes)
        plainBytes.fill(0)

        val target = vaultFile(context)
        target.parentFile?.mkdirs()
        target.writeBytes(encrypted)
        hardenFilePermissions(target)
    }

    private fun readVaultRecord(context: Context): VaultRecord? {
        val file = vaultFile(context)
        if (!file.isFile || file.length() <= GCM_IV_LENGTH_BYTES + 16) return null
        return runCatching {
            val decrypted = decryptVaultBytes(context, file.readBytes())
            val jsonText = decrypted.toString(Charsets.UTF_8)
            decrypted.fill(0)
            val obj = JSONObject(jsonText)
            val path = obj.optString("keystorePath").trim()
            val storePass = obj.optString("storePassword")
            val alias = obj.optString("keyAlias").trim()
            val keyPass = obj.optString("keyPassword")
            if (path.isBlank() || storePass.isEmpty() || alias.isBlank() || keyPass.isEmpty()) {
                return null
            }
            VaultRecord(
                keystorePath = path,
                storePassword = storePass,
                keyAlias = alias,
                keyPassword = keyPass,
                format = obj.optString("format", "PKCS12"),
                createdAtMs = obj.optLong("createdAtMs", file.lastModified()),
                lastBackedUpAtMs = if (obj.has("lastBackedUpAtMs")) obj.optLong("lastBackedUpAtMs") else null
            )
        }.getOrNull()
    }

    private fun encryptVaultBytes(context: Context, plaintext: ByteArray): ByteArray {
        val secretKey = getOrCreateVaultAesKey(context)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val isHardwareKey = secretKey.encoded == null
        if (isHardwareKey) {
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        } else {
            val iv = ByteArray(GCM_IV_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        }
        val iv = cipher.iv
        require(iv != null && iv.size == GCM_IV_LENGTH_BYTES) { "Expected 12-byte GCM IV" }
        val ciphertext = cipher.doFinal(plaintext)
        return ByteArray(GCM_IV_LENGTH_BYTES + ciphertext.size).apply {
            System.arraycopy(iv, 0, this, 0, GCM_IV_LENGTH_BYTES)
            System.arraycopy(ciphertext, 0, this, GCM_IV_LENGTH_BYTES, ciphertext.size)
        }
    }

    private fun decryptVaultBytes(context: Context, ivAndCiphertext: ByteArray): ByteArray {
        require(ivAndCiphertext.size > GCM_IV_LENGTH_BYTES + 16) { "Encrypted vault payload is too short" }
        val iv = ivAndCiphertext.copyOfRange(0, GCM_IV_LENGTH_BYTES)
        val ciphertext = ivAndCiphertext.copyOfRange(GCM_IV_LENGTH_BYTES, ivAndCiphertext.size)
        val secretKey = getOrCreateVaultAesKey(context)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun getOrCreateVaultAesKey(context: Context): SecretKey {
        val hardwareAes = runCatching {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (!ks.containsAlias(VAULT_AES_KEY_ALIAS)) {
                val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                val spec = KeyGenParameterSpec.Builder(
                    VAULT_AES_KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGen.init(spec)
                keyGen.generateKey()
            } else {
                ks.getKey(VAULT_AES_KEY_ALIAS, null) as SecretKey
            }
        }.getOrNull()

        if (hardwareAes != null) return hardwareAes

        val wrapFile = File(keystoreStorageDir(context), FALLBACK_WRAP_FILE_NAME)
        val rawKey = if (wrapFile.isFile && wrapFile.length() == 32L) {
            wrapFile.readBytes()
        } else {
            ByteArray(32).also {
                SecureRandom().nextBytes(it)
                wrapFile.writeBytes(it)
                hardenFilePermissions(wrapFile)
            }
        }
        return SecretKeySpec(rawKey, "AES")
    }

    private fun hardenFilePermissions(file: File) {
        runCatching {
            file.setReadable(false, false)
            file.setWritable(false, false)
            file.setExecutable(false, false)
            file.setReadable(true, true)
            file.setWritable(true, true)
        }
    }

    private fun isInsideManagedDir(context: Context, file: File): Boolean {
        val managed = runCatching { keystoreStorageDir(context).canonicalPath }.getOrNull() ?: return false
        val target = runCatching { file.canonicalPath }.getOrNull() ?: return false
        return target.startsWith(managed)
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

    // ==================== X.509v3 Self-Signed Release Certificate DER Builder ====================

    internal fun generateReleaseSelfSignedX509Der(
        keyPair: KeyPair,
        config: KeystoreGenerationConfig,
        validityYears: Int = 25
    ): ByteArray {
        val now = System.currentTimeMillis()
        val notBefore = Date(now - MS_PER_DAY)
        val notAfter = Date(now + validityYears.toLong() * 365L * MS_PER_DAY)

        // OID 1.2.840.113549.1.1.11 (sha256WithRSAEncryption)
        val sha256WithRsaAlgId = derSequence(
            derOid(byteArrayOf(0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x01, 0x0B)),
            derNull()
        )

        val rdns = mutableListOf<ByteArray>()
        fun addRdn(oidLastByte: Byte, value: String, printableOnly: Boolean = false) {
            val trimmed = value.trim()
            if (trimmed.isEmpty()) return
            val encodedVal = if (printableOnly) {
                derPrintableString(trimmed.uppercase(Locale.US).take(2))
            } else {
                derUtf8String(trimmed.take(64))
            }
            rdns += derSet(
                derSequence(
                    derOid(byteArrayOf(0x55, 0x04, oidLastByte)),
                    encodedVal
                )
            )
        }

        addRdn(0x03, config.commonName.ifBlank { "AiAppStudio Release" }) // CN
        addRdn(0x0B, config.organizationalUnit) // OU
        addRdn(0x0A, config.organization) // O
        addRdn(0x07, config.locality) // L
        addRdn(0x08, config.state) // ST
        addRdn(0x06, config.country, printableOnly = true) // C

        val subjectName = derSequence(*rdns.toTypedArray())

        val validity = derSequence(
            derGeneralizedTime(notBefore),
            derGeneralizedTime(notAfter)
        )

        val randomSerial = BigInteger(62, SecureRandom()).abs().add(BigInteger.ONE)
        val versionExplicit = derExplicitTag(0, derInteger(byteArrayOf(0x02))) // X.509 v3

        val tbsCertificate = derSequence(
            versionExplicit,
            derInteger(randomSerial.toByteArray()),
            sha256WithRsaAlgId,
            subjectName,
            validity,
            subjectName,
            keyPair.public.encoded
        )

        val signer = Signature.getInstance("SHA256withRSA")
        signer.initSign(keyPair.private)
        signer.update(tbsCertificate)
        val signatureBytes = signer.sign()

        return derSequence(
            tbsCertificate,
            sha256WithRsaAlgId,
            derBitString(signatureBytes)
        )
    }

    private fun derSequence(vararg elements: ByteArray): ByteArray {
        val body = ByteArrayOutputStream()
        elements.forEach { body.write(it) }
        return derTag(0x30, body.toByteArray())
    }

    private fun derSet(vararg elements: ByteArray): ByteArray {
        val body = ByteArrayOutputStream()
        elements.forEach { body.write(it) }
        return derTag(0x31, body.toByteArray())
    }

    private fun derExplicitTag(tagNumber: Int, content: ByteArray): ByteArray =
        derTag(0xA0 or tagNumber, content)

    private fun derInteger(value: ByteArray): ByteArray {
        val normalized = if (value.isNotEmpty() && (value[0].toInt() and 0x80) != 0) {
            byteArrayOf(0x00) + value
        } else {
            value
        }
        return derTag(0x02, normalized)
    }

    private fun derBitString(bytes: ByteArray): ByteArray =
        derTag(0x03, byteArrayOf(0x00) + bytes)

    private fun derNull(): ByteArray = byteArrayOf(0x05, 0x00)

    private fun derOid(encodedOidBody: ByteArray): ByteArray = derTag(0x06, encodedOidBody)

    private fun derUtf8String(text: String): ByteArray =
        derTag(0x0C, text.toByteArray(Charsets.UTF_8))

    private fun derPrintableString(text: String): ByteArray =
        derTag(0x13, text.toByteArray(Charsets.US_ASCII))

    private fun derGeneralizedTime(date: Date): ByteArray {
        val sdf = SimpleDateFormat("yyyyMMddHHmmss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return derTag(0x18, sdf.format(date).toByteArray(Charsets.US_ASCII))
    }

    private fun derTag(tag: Int, content: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(tag)
        val len = content.size
        if (len < 128) {
            out.write(len)
        } else {
            val lenBytes = mutableListOf<Byte>()
            var temp = len
            while (temp > 0) {
                lenBytes.add(0, (temp and 0xFF).toByte())
                temp = temp ushr 8
            }
            out.write(0x80 or lenBytes.size)
            lenBytes.forEach { out.write(it.toInt() and 0xFF) }
        }
        out.write(content)
        return out.toByteArray()
    }
}
