package com.example.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddModerator
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.apk.KeystoreGenerationConfig
import com.example.apk.KeystoreInfo
import com.example.apk.ReleaseKeystoreManager
import com.example.apk.SigningTestReport
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class PendingOverwriteAction {
    GENERATE,
    IMPORT
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KeystoreManagerScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var keystoreInfo by remember { mutableStateOf<KeystoreInfo?>(null) }
    var isLoadingInfo by remember { mutableStateOf(true) }
    var isWorking by remember { mutableStateOf(false) }
    var statusBanner by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var testReport by remember { mutableStateOf<SigningTestReport?>(null) }

    // Generate dialog state
    var showGenerateDialog by remember { mutableStateOf(false) }
    var genCommonName by remember { mutableStateOf("AiAppStudio Release") }
    var genOrg by remember { mutableStateOf("AiAppStudio") }
    var genCountry by remember { mutableStateOf("US") }
    var genKeyBits by remember { mutableIntStateOf(2048) }
    var genUseGradleUploadAlias by remember { mutableStateOf(false) }
    var pendingGenerateConfig by remember { mutableStateOf<KeystoreGenerationConfig?>(null) }

    // Import dialog state
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var importStorePassword by remember { mutableStateOf("") }
    var importKeyAlias by remember { mutableStateOf("") }
    var importSameKeyPassword by remember { mutableStateOf(true) }
    var importKeyPassword by remember { mutableStateOf("") }
    var showImportPasswords by remember { mutableStateOf(false) }

    // Overwrite confirmation dialog state
    var pendingOverwriteAction by remember { mutableStateOf<PendingOverwriteAction?>(null) }

    // Secrets / .env export dialog state
    var showEnvSnippetDialog by remember { mutableStateOf(false) }
    var revealEnvPasswords by remember { mutableStateOf(false) }

    fun refreshKeystoreInfo() {
        coroutineScope.launch {
            isLoadingInfo = true
            keystoreInfo = withContext(Dispatchers.IO) {
                ReleaseKeystoreManager.getActiveKeystoreInfo(context)
            }
            isLoadingInfo = false
        }
    }

    LaunchedEffect(Unit) {
        refreshKeystoreInfo()
    }

    fun executeGenerate(config: KeystoreGenerationConfig, allowOverwrite: Boolean) {
        coroutineScope.launch {
            isWorking = true
            statusBanner = null
            val result = ReleaseKeystoreManager.generateNewReleaseKeystore(
                context = context,
                config = config,
                allowOverwrite = allowOverwrite
            )
            result.onSuccess { info ->
                keystoreInfo = info
                testReport = null
                statusBanner = true to "Generated release keystore '${info.fileName}' with alias '${info.keyAlias}'. Back it up securely!"
                showToastOnMain(context, "Release keystore generated & encrypted", Toast.LENGTH_SHORT)
            }.onFailure { err ->
                statusBanner = false to (err.localizedMessage ?: "Failed to generate release keystore.")
            }
            isWorking = false
        }
    }

    fun executeImport(allowOverwrite: Boolean) {
        val uri = pendingImportUri ?: return
        val storePwd = importStorePassword
        val alias = importKeyAlias.trim().ifBlank { null }
        val keyPwd = if (importSameKeyPassword) storePwd else importKeyPassword
        coroutineScope.launch {
            isWorking = true
            statusBanner = null
            val result = ReleaseKeystoreManager.importExistingKeystore(
                context = context,
                sourceUri = uri,
                storePassword = storePwd,
                keyAlias = alias,
                keyPassword = keyPwd,
                allowOverwrite = allowOverwrite
            )
            // Clear sensitive text fields immediately after import attempt
            importStorePassword = ""
            importKeyPassword = ""
            pendingImportUri = null

            result.onSuccess { info ->
                keystoreInfo = info
                testReport = null
                statusBanner = true to "Imported keystore '${info.fileName}' (alias '${info.keyAlias}'). Credentials stored encrypted."
                showToastOnMain(context, "Keystore imported successfully", Toast.LENGTH_SHORT)
            }.onFailure { err ->
                statusBanner = false to (err.localizedMessage ?: "Failed to import keystore.")
            }
            isWorking = false
        }
    }

    val importPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            importStorePassword = ""
            importKeyPassword = ""
            importKeyAlias = ""
            importSameKeyPassword = true
            showImportPasswords = false
            pendingImportUri = uri
        }
    }

    val backupExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                isWorking = true
                val result = ReleaseKeystoreManager.backupKeystoreToUri(context, uri)
                result.onSuccess { updated ->
                    keystoreInfo = updated
                    statusBanner = true to "Keystore backed up securely. Store your backup file and Secrets credentials in a safe location."
                    showToastOnMain(context, "Keystore backup saved", Toast.LENGTH_LONG)
                }.onFailure { err ->
                    statusBanner = false to (err.localizedMessage ?: "Failed to back up keystore.")
                }
                isWorking = false
            }
        }
    }

    // Dialog 1: Generate New Keystore Configuration
    if (showGenerateDialog) {
        AlertDialog(
            onDismissRequest = { showGenerateDialog = false },
            title = { Text("Generate New Release Keystore") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Cryptographically strong 24-character STORE_PASSWORD and KEY_PASSWORD values are generated automatically via SecureRandom and encrypted locally with AES-256-GCM.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = genCommonName,
                        onValueChange = { genCommonName = it },
                        label = { Text("Certificate Common Name (CN)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("keystore_gen_cn_input")
                    )
                    OutlinedTextField(
                        value = genOrg,
                        onValueChange = { genOrg = it },
                        label = { Text("Organization (O)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("keystore_gen_org_input")
                    )
                    OutlinedTextField(
                        value = genCountry,
                        onValueChange = { genCountry = it.take(2).uppercase(Locale.US) },
                        label = { Text("Country Code (C)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "RSA Key Strength",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = genKeyBits == 2048,
                            onClick = { genKeyBits = 2048 },
                            label = { Text("RSA 2048-bit (Fast)") },
                            modifier = Modifier.testTag("keystore_gen_bits_2048")
                        )
                        FilterChip(
                            selected = genKeyBits == 4096,
                            onClick = { genKeyBits = 4096 },
                            label = { Text("RSA 4096-bit") },
                            modifier = Modifier.testTag("keystore_gen_bits_4096")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = "Use Gradle 'upload' alias",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = if (genUseGradleUploadAlias) {
                                    "Alias set to 'upload' to match Gradle release signingConfig"
                                } else {
                                    "Generates a unique release_YYYYMMDD_xxxxxxxx alias"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = genUseGradleUploadAlias,
                            onCheckedChange = { genUseGradleUploadAlias = it },
                            modifier = Modifier.testTag("keystore_gen_upload_alias_switch")
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val config = KeystoreGenerationConfig(
                            keyAlias = if (genUseGradleUploadAlias) "upload" else "",
                            commonName = genCommonName.trim().ifBlank { "AiAppStudio Release" },
                            organization = genOrg.trim().ifBlank { "AiAppStudio" },
                            country = genCountry.trim().ifBlank { "US" },
                            keySizeBits = genKeyBits,
                            validityYears = 25
                        )
                        showGenerateDialog = false
                        if (keystoreInfo != null) {
                            pendingGenerateConfig = config
                            pendingOverwriteAction = PendingOverwriteAction.GENERATE
                        } else {
                            executeGenerate(config, allowOverwrite = false)
                        }
                    },
                    modifier = Modifier.testTag("keystore_confirm_generate_button")
                ) {
                    Text("Generate")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGenerateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog 2: Import Existing Keystore Credentials
    if (pendingImportUri != null && pendingOverwriteAction != PendingOverwriteAction.IMPORT) {
        AlertDialog(
            onDismissRequest = {
                importStorePassword = ""
                importKeyPassword = ""
                pendingImportUri = null
            },
            title = { Text("Import Existing Keystore") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Enter credentials to verify and unlock your keystore locally. Passwords are encrypted with AES-256-GCM and never logged or uploaded.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = importStorePassword,
                        onValueChange = { importStorePassword = it },
                        label = { Text("Store Password (STORE_PASSWORD)") },
                        singleLine = true,
                        visualTransformation = if (showImportPasswords) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(
                                onClick = { showImportPasswords = !showImportPasswords },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = if (showImportPasswords) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showImportPasswords) "Hide passwords" else "Show passwords"
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("keystore_import_store_password_input")
                    )
                    OutlinedTextField(
                        value = importKeyAlias,
                        onValueChange = { importKeyAlias = it },
                        label = { Text("Key Alias (optional, auto-detected if blank)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("keystore_import_alias_input")
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Key password same as store password",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = importSameKeyPassword,
                            onCheckedChange = { importSameKeyPassword = it }
                        )
                    }
                    if (!importSameKeyPassword) {
                        OutlinedTextField(
                            value = importKeyPassword,
                            onValueChange = { importKeyPassword = it },
                            label = { Text("Key Password (KEY_PASSWORD)") },
                            singleLine = true,
                            visualTransformation = if (showImportPasswords) VisualTransformation.None else PasswordVisualTransformation(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("keystore_import_key_password_input")
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (keystoreInfo != null) {
                            pendingOverwriteAction = PendingOverwriteAction.IMPORT
                        } else {
                            executeImport(allowOverwrite = false)
                        }
                    },
                    enabled = importStorePassword.isNotEmpty() && (importSameKeyPassword || importKeyPassword.isNotEmpty()),
                    modifier = Modifier.testTag("keystore_confirm_import_button")
                ) {
                    Text("Verify & Import")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        importStorePassword = ""
                        importKeyPassword = ""
                        pendingImportUri = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog 3: Explicit Overwrite Confirmation (NEVER overwrite existing Keystore without confirmation)
    pendingOverwriteAction?.let { action ->
        AlertDialog(
            onDismissRequest = {
                pendingOverwriteAction = null
                pendingGenerateConfig = null
            },
            title = { Text("Replace Existing Release Keystore?") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.testTag("keystore_overwrite_confirm_dialog")
                ) {
                    Text(
                        text = "An active release keystore ('${keystoreInfo?.fileName ?: "existing"}') is already configured.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Overwriting your signing keystore without a backup will prevent you from updating previously signed release APKs. Back up the current keystore first before replacing it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingOverwriteAction = null
                        when (action) {
                            PendingOverwriteAction.GENERATE -> {
                                val cfg = pendingGenerateConfig ?: KeystoreGenerationConfig()
                                pendingGenerateConfig = null
                                executeGenerate(cfg, allowOverwrite = true)
                            }
                            PendingOverwriteAction.IMPORT -> {
                                executeImport(allowOverwrite = true)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("keystore_confirm_overwrite_button")
                ) {
                    Text("Overwrite Keystore")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingOverwriteAction = null
                        pendingGenerateConfig = null
                    },
                    modifier = Modifier.testTag("keystore_cancel_overwrite_button")
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog 4: Gradle / AI Studio Secrets Environment Variables Snippet
    if (showEnvSnippetDialog) {
        val snippet = remember(keystoreInfo, revealEnvPasswords) {
            ReleaseKeystoreManager.formatEnvSnippet(context, revealPasswords = revealEnvPasswords)
                ?: "No active keystore configured."
        }
        AlertDialog(
            onDismissRequest = {
                revealEnvPasswords = false
                showEnvSnippetDialog = false
            },
            title = { Text("Signing Environment Variables") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "To sign Gradle release builds in AI Studio, add these variables in the Secrets panel (never commit passwords to git or send them to external AI services):",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = snippet,
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .padding(12.dp)
                                .testTag("keystore_env_snippet_text")
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Reveal passwords for secure backup",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Switch(
                            checked = revealEnvPasswords,
                            onCheckedChange = { revealEnvPasswords = it },
                            modifier = Modifier.testTag("keystore_reveal_env_passwords_switch")
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val copyText = ReleaseKeystoreManager.formatEnvSnippet(
                            context,
                            revealPasswords = revealEnvPasswords
                        )
                        if (copyText != null) {
                            copyToClipboard(context, "Release Signing Env", copyText)
                            Toast.makeText(
                                context,
                                if (revealEnvPasswords) "Copied signing variables to clipboard" else "Copied masked template (toggle Reveal to copy passwords)",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    modifier = Modifier.testTag("keystore_copy_env_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        revealEnvPasswords = false
                        showEnvSnippetDialog = false
                    }
                ) {
                    Text("Close")
                }
            }
        )
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("keystore_manager_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                    Column {
                        Text(
                            text = "Keystore & APK Signing",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("keystore_manager_title")
                        )
                        Text(
                            text = "Automatic release keystore, vault & ApkVerifier",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = { refreshKeystoreInfo() },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("keystore_refresh_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh keystore status"
                    )
                }
            }

            // Security & Backup Warning Banner
            val needsBackup = keystoreInfo != null && keystoreInfo?.isBackedUp == false
            val warningColor = if (needsBackup) Color(0xFFF59E0B) else MaterialTheme.colorScheme.primary
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = warningColor.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, warningColor.copy(alpha = 0.55f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("keystore_backup_warning_banner")
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = if (needsBackup) Icons.Default.WarningAmber else Icons.Default.Lock,
                        contentDescription = null,
                        tint = warningColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = if (needsBackup) {
                                "Action Recommended: Back Up Your Keystore Securely"
                            } else {
                                "Local AES-256-GCM Keystore Protection"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (needsBackup) {
                                "Your release keystore has not been backed up yet. Export a backup copy to offline storage—losing your keystore prevents publishing updates to signed APKs. Credentials stay encrypted on-device and are never logged or uploaded to Gemini."
                            } else {
                                "Keystores persist privately on this device with AES-256-GCM encrypted credentials. Passwords and private keys are never logged, hardcoded, or uploaded to Gemini or external services."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Operation Feedback Banner
            statusBanner?.let { (isSuccess, message) ->
                val accent = if (isSuccess) Color(0xFF10B981) else MaterialTheme.colorScheme.error
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = accent.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("keystore_status_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Primary Actions Card (Generate, Import, Test Signing, Backup, Secrets Config)
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.VpnKey,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Keystore Actions",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Generate, import, verify & securely back up your release key",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (isWorking) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                            Text(
                                text = "Processing cryptographic operation...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Button(
                        onClick = { showGenerateDialog = true },
                        enabled = !isWorking,
                        contentPadding = PaddingValues(vertical = 12.dp, horizontal = 16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("keystore_generate_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddModerator,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Generate New Keystore")
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilledTonalButton(
                            onClick = { importPickerLauncher.launch(arrayOf("*/*")) },
                            enabled = !isWorking,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("keystore_import_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Import Existing")
                        }

                        FilledTonalButton(
                            onClick = {
                                coroutineScope.launch {
                                    isWorking = true
                                    statusBanner = null
                                    val report = ReleaseKeystoreManager.testSigningConfiguration(context)
                                    testReport = report
                                    statusBanner = report.passed to report.summary
                                    isWorking = false
                                }
                            },
                            enabled = !isWorking && keystoreInfo != null,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("keystore_test_signing_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Test Signing")
                        }

                        OutlinedButton(
                            onClick = {
                                val suggestedName = keystoreInfo?.fileName ?: "release-keystore-backup.jks"
                                backupExportLauncher.launch(suggestedName)
                            },
                            enabled = !isWorking && keystoreInfo != null,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("keystore_backup_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Backup,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Backup Securely")
                        }

                        OutlinedButton(
                            onClick = {
                                revealEnvPasswords = false
                                showEnvSnippetDialog = true
                            },
                            enabled = !isWorking && keystoreInfo != null,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("keystore_env_export_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Gradle / Secrets Env")
                        }
                    }
                }
            }

            // Active Keystore Information Card
            KeystoreInfoCard(
                info = keystoreInfo,
                isLoading = isLoadingInfo,
                onCopyFingerprint = { label, value ->
                    copyToClipboard(context, label, value)
                    Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
                }
            )

            // Signing Verification Report Card
            testReport?.let { report ->
                SigningTestReportCard(report = report)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeystoreInfoCard(
    info: KeystoreInfo?,
    isLoading: Boolean,
    onCopyFingerprint: (String, String) -> Unit
) {
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("keystore_info_card")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = "Keystore Information",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (info != null) {
                    val badgeColor = Color(0xFF10B981)
                    Surface(
                        shape = CircleShape,
                        color = badgeColor.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.7f)),
                        modifier = Modifier.testTag("keystore_active_badge")
                    ) {
                        Text(
                            text = "ACTIVE (${info.format})",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (isLoading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    Text(
                        text = "Inspecting persistent keystore storage...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (info == null) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("keystore_empty_state")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "No Release Keystore Configured Yet",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Tap 'Generate New Keystore' above to automatically create an RSA release keystore with cryptographically random STORE_PASSWORD, KEY_PASSWORD, and unique KEY_ALIAS—or import an existing .jks / .p12 keystore.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val backupColor = if (info.isBackedUp) Color(0xFF10B981) else Color(0xFFF59E0B)
                    Surface(
                        shape = CircleShape,
                        color = backupColor.copy(alpha = 0.14f),
                        modifier = Modifier.testTag("keystore_backup_status_badge")
                    ) {
                        Text(
                            text = if (info.isBackedUp) "Backed Up ✓" else "Not Backed Up Yet",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = backupColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = info.keyAlgorithm,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    if (info.isGradleAliasCompatible) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF10B981).copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = "Gradle 'upload' alias",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF10B981),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                KeystoreMetaRow("Keystore File", info.fileName, tag = "keystore_info_filename")
                KeystoreMetaRow("Key Alias", info.keyAlias, tag = "keystore_info_alias")
                KeystoreMetaRow(
                    "File Size",
                    Formatter.formatShortFileSize(context, info.fileSizeBytes)
                )
                KeystoreMetaRow("Subject", info.certificateSubject)
                KeystoreMetaRow(
                    "Validity",
                    "${dateFormat.format(Date(info.validFromMs))} → ${dateFormat.format(Date(info.validUntilMs))} (${info.daysRemaining} days left)"
                )
                KeystoreMetaRow("Serial (Hex)", info.serialNumberHex)

                // Fingerprints (Safe public metadata — never private keys)
                FingerprintBox(
                    label = "SHA-256 Certificate Fingerprint",
                    value = info.sha256Fingerprint,
                    tag = "keystore_info_sha256",
                    onCopy = { onCopyFingerprint("SHA-256", info.sha256Fingerprint) }
                )
                FingerprintBox(
                    label = "SHA-1 Certificate Fingerprint",
                    value = info.sha1Fingerprint,
                    tag = "keystore_info_sha1",
                    onCopy = { onCopyFingerprint("SHA-1", info.sha1Fingerprint) }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                // 4 Environment Variables Status Checklist (Passwords always masked)
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("keystore_env_vars_status_panel")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Configured Signing Environment Variables",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        KeystoreEnvStatusRow("KEYSTORE_PATH", info.fileName)
                        KeystoreEnvStatusRow("KEY_ALIAS", info.keyAlias)
                        KeystoreEnvStatusRow("STORE_PASSWORD", "•••••••••••• (Encrypted)")
                        KeystoreEnvStatusRow("KEY_PASSWORD", "•••••••••••• (Encrypted)")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SigningTestReportCard(report: SigningTestReport) {
    val accent = if (report.passed) Color(0xFF10B981) else MaterialTheme.colorScheme.error

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.5.dp, accent.copy(alpha = 0.7f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("keystore_test_report_card")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (report.passed) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = accent
                    )
                    Text(
                        text = "Signing Verification Report",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = accent.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = if (report.passed) "VERIFIED" else "FAILED",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = accent,
                        modifier = Modifier
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                            .testTag("keystore_test_report_badge")
                    )
                }
            }

            Text(
                text = report.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Signature Scheme Badges (V1, V2, V3)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SchemeStatusChip("APK Sig v1", report.verifiedV1)
                SchemeStatusChip("APK Sig v2", report.verifiedV2)
                SchemeStatusChip("APK Sig v3", report.verifiedV3)
            }

            report.signerSha256?.let { sha ->
                Text(
                    text = "Verified Signer SHA-256: ${sha.take(35)}…",
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                report.checks.forEach { check ->
                    val checkColor = if (check.passed) Color(0xFF10B981) else MaterialTheme.colorScheme.error
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = if (check.passed) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = checkColor,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(16.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = check.name,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = check.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SchemeStatusChip(label: String, verified: Boolean) {
    val color = if (verified) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        shape = CircleShape,
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Text(
            text = if (verified) "$label ✓" else "$label —",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun KeystoreMetaRow(
    label: String,
    value: String,
    tag: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = if (tag != null) Modifier.testTag(tag) else Modifier
        )
    }
}

@Composable
private fun FingerprintBox(
    label: String,
    value: String,
    tag: String,
    onCopy: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = value,
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(tag)
                )
                IconButton(
                    onClick = onCopy,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy $label",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun KeystoreEnvStatusRow(name: String, status: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF10B981),
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = name,
                fontFamily = JetBrainsMonoFontFamily,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = status,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun copyToClipboard(context: Context, label: String, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText(label, value))
}

private fun showToastOnMain(context: Context, message: String, duration: Int) {
    Handler(Looper.getMainLooper()).post {
        Toast.makeText(context, message, duration).show()
    }
}
