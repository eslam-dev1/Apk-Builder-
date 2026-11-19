package com.example.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai.DownloadStatus
import com.example.ai.ModelDownloadProgress
import com.example.ai.ModelManager
import com.example.ai.PreflightCheckResult
import com.example.apk.ApkBuilder
import com.example.apk.ReleaseBuildConfigManager
import com.example.apk.ReleaseBuildFlags
import com.example.data.BuildModeStrategy
import com.example.data.CloudAiConfig
import com.example.data.CloudAiProvider
import com.example.data.GeminiModels
import com.example.data.ai.LocalModel
import com.example.data.ai.ModelCatalog
import com.example.ui.theme.JetBrainsMonoFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    isApiKeyConfigured: Boolean,
    totalProjectsCount: Int,
    onClearAllProjects: () -> Unit,
    onOpenModelStatus: () -> Unit = {},
    onOpenEnvSetup: () -> Unit = {},
    onOpenKeystoreManager: () -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()

    var showClearDialog by remember { mutableStateOf(false) }
    var selectedQuality by remember { mutableStateOf(GeminiModels.load(context)) }
    val activeProvider by CloudAiConfig.currentProvider.collectAsStateWithLifecycle()
    val savedOpenRouterKey by CloudAiConfig.savedOpenRouterApiKey.collectAsStateWithLifecycle()
    val currentOpenRouterModel by CloudAiConfig.openRouterModel.collectAsStateWithLifecycle()
    val buildModeStrategy by CloudAiConfig.buildModeStrategy.collectAsStateWithLifecycle()
    val isOfflineMode by CloudAiConfig.isOfflineMode.collectAsStateWithLifecycle()
    val isDarkMode by CloudAiConfig.isDarkMode.collectAsStateWithLifecycle()
    val isDynamicColor by CloudAiConfig.isDynamicColor.collectAsStateWithLifecycle()

    var openRouterKeyDraft by remember(savedOpenRouterKey) { mutableStateOf(savedOpenRouterKey) }
    var showOpenRouterKey by remember { mutableStateOf(false) }
    var openRouterModelDraft by remember(currentOpenRouterModel) { mutableStateOf(currentOpenRouterModel) }

    val downloadProgress by ModelManager.downloadProgress.collectAsStateWithLifecycle()
    val installedFiles by ModelManager.installedFiles.collectAsStateWithLifecycle()
    val selectedModelFile by ModelManager.selectedModelFile.collectAsStateWithLifecycle()

    var customUrl by remember { mutableStateOf(ModelManager.getSavedCustomUrl(context)) }
    var customFileName by remember { mutableStateOf(ModelManager.getSavedCustomFileName(context)) }
    var pendingPreflight by remember { mutableStateOf<PreflightCheckResult?>(null) }
    var localModelStatusMessage by remember { mutableStateOf<String?>(null) }

    var signingFingerprint by remember { mutableStateOf("Loading certificate...") }
    var pendingExportUri by remember { mutableStateOf<Uri?>(null) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var pkcs12Password by remember { mutableStateOf("") }
    var releaseFlags by remember { mutableStateOf(ReleaseBuildConfigManager.loadSavedFlags(context)) }

    LaunchedEffect(Unit) {
        CloudAiConfig.load(context)
        ModelManager.refreshState(context)
        signingFingerprint = withContext(Dispatchers.IO) {
            runCatching { ApkBuilder.getSigningCertSha256Fingerprint(context) }
                .getOrDefault("Unavailable")
        }
    }

    // Keep screen on while an offline model download is actively running
    val isActivelyDownloading = downloadProgress.status == DownloadStatus.DOWNLOADING ||
        downloadProgress.status == DownloadStatus.PROBING ||
        downloadProgress.status == DownloadStatus.VERIFYING

    DisposableEffect(isActivelyDownloading) {
        view.keepScreenOn = isActivelyDownloading
        val window = (context as? Activity)?.window
        if (isActivelyDownloading) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            view.keepScreenOn = false
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val importModelLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val displayName = queryDisplayName(context, uri)
            coroutineScope.launch {
                val res = ModelManager.importModelFromUri(context, uri, displayName)
                res.onSuccess { savedName ->
                    localModelStatusMessage = "Imported offline model: $savedName"
                    Toast.makeText(context, "Imported $savedName", Toast.LENGTH_SHORT).show()
                }.onFailure { err ->
                    localModelStatusMessage = "Import failed: ${err.localizedMessage ?: "invalid file"}"
                }
            }
        }
    }

    val exportKeyLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/x-pkcs12")
    ) { uri: Uri? ->
        if (uri != null) {
            pkcs12Password = ""
            pendingExportUri = uri
        }
    }

    var pendingPostNotificationAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        val action = pendingPostNotificationAction
        pendingPostNotificationAction = null
        action?.invoke()
    }

    fun runWithNotificationPermission(action: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingPostNotificationAction = action
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            action()
        }
    }

    val importKeyLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            pkcs12Password = ""
            pendingImportUri = uri
        }
    }

    // Confirm download size dialog ("2.58 GB — use Wi-Fi")
    pendingPreflight?.let { preflight ->
        AlertDialog(
            onDismissRequest = { pendingPreflight = null },
            title = { Text("Download Offline AI Model") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = preflight.model.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = preflight.confirmMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.testTag("download_confirm_size_text")
                    )
                    Text(
                        text = "Destination: filesDir/models/${preflight.model.fileName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val targetModel = preflight.model
                        pendingPreflight = null
                        runWithNotificationPermission {
                            ModelManager.startDownload(context, targetModel)
                        }
                    },
                    modifier = Modifier.testTag("confirm_start_model_download_button")
                ) {
                    Text("Download Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingPreflight = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Export PKCS#12 password dialog
    pendingExportUri?.let { targetUri ->
        AlertDialog(
            onDismissRequest = { pendingExportUri = null },
            title = { Text("Export Signing Key (.p12)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Enter a password to encrypt your exported PKCS#12 (.p12) signing keystore:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = pkcs12Password,
                        onValueChange = { pkcs12Password = it },
                        label = { Text("PKCS#12 Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("export_p12_password_field")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val pwd = pkcs12Password
                        pendingExportUri = null
                        coroutineScope.launch {
                            val result = ApkBuilder.exportSigningKeyPkcs12(context, targetUri, pwd)
                            result.onSuccess {
                                Toast.makeText(
                                    context,
                                    "Signing key exported (.p12)",
                                    Toast.LENGTH_LONG
                                ).show()
                            }.onFailure { err ->
                                Toast.makeText(
                                    context,
                                    "Export failed: ${err.localizedMessage ?: "Error"}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    },
                    enabled = pkcs12Password.isNotEmpty(),
                    modifier = Modifier.testTag("confirm_export_p12_button")
                ) {
                    Text("Export")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingExportUri = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Import PKCS#12 password dialog
    pendingImportUri?.let { sourceUri ->
        AlertDialog(
            onDismissRequest = { pendingImportUri = null },
            title = { Text("Import Signing Key (.p12)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Enter the password for the selected PKCS#12 (.p12) signing key file:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = pkcs12Password,
                        onValueChange = { pkcs12Password = it },
                        label = { Text("PKCS#12 Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("import_p12_password_field")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val pwd = pkcs12Password
                        pendingImportUri = null
                        coroutineScope.launch {
                            val result = ApkBuilder.importSigningKeyPkcs12(context, sourceUri, pwd)
                            result.onSuccess { newFingerprint ->
                                signingFingerprint = newFingerprint
                                Toast.makeText(
                                    context,
                                    "Signing key imported! Fingerprint updated.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }.onFailure { err ->
                                Toast.makeText(
                                    context,
                                    "Import failed: ${err.localizedMessage ?: "Invalid password or file"}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    },
                    enabled = pkcs12Password.isNotEmpty(),
                    modifier = Modifier.testTag("confirm_import_p12_button")
                ) {
                    Text("Import")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingImportUri = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear All Projects") },
            text = {
                Text("Are you sure you want to delete all $totalProjectsCount saved projects? This cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearDialog = false
                        onClearAllProjects()
                        Toast.makeText(context, "All projects cleared", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("confirm_clear_all_button")
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearDialog = false },
                    modifier = Modifier.testTag("cancel_clear_all_button")
                ) {
                    Text("Cancel")
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.testTag("settings_title")
                )
                TextButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("settings_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Back")
                }
            }

            // General & Cloud AI Card
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
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
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "App Version",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Text(
                            text = "1.0",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("settings_app_version")
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

                    // Offline Mode Switch Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (isOfflineMode) Icons.Default.CloudOff else Icons.Default.Cloud,
                                contentDescription = null,
                                tint = if (isOfflineMode) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                            )
                            Column {
                                Text(
                                    text = "Offline Mode",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = when (buildModeStrategy) {
                                        BuildModeStrategy.AUTO ->
                                            "Auto (Offline First → ${activeProvider.displayName} fallback)"
                                        BuildModeStrategy.OFFLINE ->
                                            "Local device mode enabled (no cloud requests)"
                                        BuildModeStrategy.CLOUD ->
                                            "Direct Cloud AI enabled (${activeProvider.displayName})"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = isOfflineMode,
                            onCheckedChange = { enabled ->
                                CloudAiConfig.setOfflineMode(context, enabled)
                            },
                            modifier = Modifier.testTag("settings_offline_mode_switch")
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

                    // Dark Mode Switch Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (isDarkMode) Icons.Default.DarkMode else Icons.Default.WbSunny,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = "Dark Theme",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Toggle dark/light color scheme",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = isDarkMode,
                            onCheckedChange = { enabled ->
                                CloudAiConfig.setDarkMode(context, enabled)
                            },
                            modifier = Modifier.testTag("settings_dark_mode_switch")
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

                    // Material 3 Dynamic Color Switch Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = "Material 3 Dynamic Color",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Use system color palette on Android 12+",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = isDynamicColor,
                            onCheckedChange = { enabled ->
                                CloudAiConfig.setDynamicColor(context, enabled)
                            },
                            modifier = Modifier.testTag("settings_dynamic_color_switch")
                        )
                    }

                    // Default Build Strategy (Auto vs Offline vs Cloud)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Build Mode Strategy",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = buildModeStrategy == BuildModeStrategy.AUTO,
                                onClick = {
                                    CloudAiConfig.setBuildModeStrategy(context, BuildModeStrategy.AUTO)
                                },
                                label = { Text("Auto (Offline First)") },
                                modifier = Modifier.testTag("settings_strategy_auto_chip")
                            )
                            FilterChip(
                                selected = buildModeStrategy == BuildModeStrategy.OFFLINE,
                                onClick = {
                                    CloudAiConfig.setBuildModeStrategy(context, BuildModeStrategy.OFFLINE)
                                },
                                label = { Text("Offline Only") },
                                modifier = Modifier.testTag("settings_strategy_offline_chip")
                            )
                            FilterChip(
                                selected = buildModeStrategy == BuildModeStrategy.CLOUD,
                                onClick = {
                                    CloudAiConfig.setBuildModeStrategy(context, BuildModeStrategy.CLOUD)
                                },
                                label = { Text("Cloud AI") },
                                modifier = Modifier.testTag("settings_strategy_cloud_chip")
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

                    // Cloud AI Provider Selector (Gemini vs OpenRouter)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Cloud AI Provider",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = activeProvider == CloudAiProvider.GEMINI,
                                onClick = {
                                    CloudAiConfig.setProvider(context, CloudAiProvider.GEMINI)
                                },
                                label = { Text("Gemini") },
                                modifier = Modifier.testTag("settings_provider_gemini_chip")
                            )
                            FilterChip(
                                selected = activeProvider == CloudAiProvider.OPENROUTER,
                                onClick = {
                                    CloudAiConfig.setProvider(context, CloudAiProvider.OPENROUTER)
                                },
                                label = { Text("OpenRouter") },
                                modifier = Modifier.testTag("settings_provider_openrouter_chip")
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

                    val isGeminiOverrideActive = CloudAiConfig.hasUserSavedGeminiKey()
                    val isOpenRouterConfigured = !CloudAiConfig.isOpenRouterKeyMissing()
                    val effectiveGeminiConfigured = isApiKeyConfigured || !CloudAiConfig.isGeminiKeyMissing()
                    val effectiveActiveKeyConfigured = if (activeProvider == CloudAiProvider.OPENROUTER) {
                        isOpenRouterConfigured
                    } else {
                        effectiveGeminiConfigured
                    }

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
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Column {
                                Text(
                                    text = "API Key Status",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = if (isGeminiOverrideActive && activeProvider == CloudAiProvider.GEMINI) {
                                        "Active provider: Gemini (.env override)"
                                    } else {
                                        "Active provider: ${activeProvider.displayName}"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            color = if (effectiveActiveKeyConfigured) Color(0xFF064E3B) else Color(0xFF7F1D1D),
                            border = BorderStroke(
                                1.dp,
                                if (effectiveActiveKeyConfigured) Color(0xFF10B981) else Color(0xFFEF4444)
                            ),
                            shape = RoundedCornerShape(50)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = if (effectiveActiveKeyConfigured) {
                                        Icons.Default.CheckCircle
                                    } else {
                                        Icons.Default.ErrorOutline
                                    },
                                    contentDescription = null,
                                    tint = if (effectiveActiveKeyConfigured) Color(0xFF34D399) else Color(0xFFEF4444),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (effectiveActiveKeyConfigured) "Configured" else "Missing",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (effectiveActiveKeyConfigured) Color(0xFFECFDF5) else Color(0xFFFEE2E2),
                                    modifier = Modifier.testTag("settings_api_key_status")
                                )
                            }
                        }
                    }

                    FilledTonalButton(
                        onClick = onOpenEnvSetup,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_open_env_setup_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Guided .env Setup Wizard (API & Signing Keys)")
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Memory,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Gemini AI model",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = selectedQuality == GeminiModels.Quality.FAST,
                                onClick = {
                                    selectedQuality = GeminiModels.Quality.FAST
                                    GeminiModels.setQuality(context, GeminiModels.Quality.FAST)
                                },
                                label = { Text("Fast (3.8)") },
                                modifier = Modifier.testTag("settings_model_fast_chip")
                            )
                            FilterChip(
                                selected = selectedQuality == GeminiModels.Quality.BEST,
                                onClick = {
                                    selectedQuality = GeminiModels.Quality.BEST
                                    GeminiModels.setQuality(context, GeminiModels.Quality.BEST)
                                },
                                label = { Text("Best (4)") },
                                modifier = Modifier.testTag("settings_model_best_chip")
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

                    // OpenRouter API Key & Model Configuration
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "OpenRouter API Key",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Surface(
                                color = if (isOpenRouterConfigured) Color(0xFF064E3B) else Color(0xFF7F1D1D),
                                shape = RoundedCornerShape(50)
                            ) {
                                Text(
                                    text = when {
                                        savedOpenRouterKey.isNotBlank() -> "Configured (Saved)"
                                        CloudAiConfig.secretsOpenRouterApiKey().isNotBlank() -> "Configured (Secrets)"
                                        else -> "Not Set"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isOpenRouterConfigured) Color(0xFFECFDF5) else Color(0xFFFEE2E2),
                                    modifier = Modifier
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                        .testTag("settings_openrouter_status")
                                )
                            }
                        }

                        OutlinedTextField(
                            value = openRouterKeyDraft,
                            onValueChange = { openRouterKeyDraft = it },
                            label = { Text("OpenRouter API Key (sk-or-v1-...)") },
                            singleLine = true,
                            visualTransformation = if (showOpenRouterKey) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = { showOpenRouterKey = !showOpenRouterKey },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("settings_openrouter_key_visibility_toggle")
                                ) {
                                    Icon(
                                        imageVector = if (showOpenRouterKey) {
                                            Icons.Default.VisibilityOff
                                        } else {
                                            Icons.Default.Visibility
                                        },
                                        contentDescription = if (showOpenRouterKey) "Hide API Key" else "Show API Key"
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_openrouter_key_field")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    CloudAiConfig.setSavedOpenRouterApiKey(context, openRouterKeyDraft)
                                    Toast.makeText(context, "OpenRouter API key saved", Toast.LENGTH_SHORT).show()
                                },
                                enabled = openRouterKeyDraft.isNotBlank(),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("settings_openrouter_save_button")
                            ) {
                                Text("Save OpenRouter Key")
                            }

                            if (savedOpenRouterKey.isNotBlank()) {
                                OutlinedButton(
                                    onClick = {
                                        openRouterKeyDraft = ""
                                        CloudAiConfig.clearSavedOpenRouterApiKey(context)
                                        Toast.makeText(context, "Saved OpenRouter key cleared", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.testTag("settings_openrouter_clear_button")
                                ) {
                                    Text("Clear")
                                }
                            }
                        }

                        Text(
                            text = "You can also set OPENROUTER_API_KEY in the AI Studio Secrets panel.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text(
                            text = "OpenRouter Model",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CloudAiConfig.OPENROUTER_PRESETS.forEach { preset ->
                                FilterChip(
                                    selected = currentOpenRouterModel == preset.id,
                                    onClick = {
                                        openRouterModelDraft = preset.id
                                        CloudAiConfig.setOpenRouterModel(context, preset.id)
                                    },
                                    label = { Text(preset.label) }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = openRouterModelDraft,
                                onValueChange = { openRouterModelDraft = it },
                                label = { Text("Model ID") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("settings_openrouter_model_field")
                            )
                            FilledTonalButton(
                                onClick = {
                                    CloudAiConfig.setOpenRouterModel(context, openRouterModelDraft)
                                    Toast.makeText(context, "OpenRouter model updated", Toast.LENGTH_SHORT).show()
                                },
                                enabled = openRouterModelDraft.isNotBlank(),
                                modifier = Modifier.testTag("settings_openrouter_model_apply_button")
                            ) {
                                Text("Apply")
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

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
                                imageVector = Icons.Default.FolderSpecial,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                            Text(
                                text = "Total Projects",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Text(
                            text = totalProjectsCount.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("settings_total_projects")
                        )
                    }
                }
            }

            // Section A: Offline AI model catalog & direct downloader
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_offline_models_card")
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Offline AI Model (.litertlm)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Direct HTTPS downloads from Hugging Face (resumable, works offline once saved in filesDir/models/)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    FilledTonalButton(
                        onClick = onOpenModelStatus,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_open_model_status_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open Model Status Screen (Storage & Progress)")
                    }

                    localModelStatusMessage?.let { statusMsg ->
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = statusMsg,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    ModelCatalog.ALL_MODELS.forEach { model ->
                        val isInstalled = model.fileName in installedFiles
                        val isSelected = selectedModelFile == model.fileName
                        val partialBytes = remember(downloadProgress, installedFiles) {
                            ModelManager.hasPartialDownload(context, model.fileName)
                        }
                        val isThisModelActiveDownload = downloadProgress.modelId == model.id &&
                            downloadProgress.status != DownloadStatus.IDLE

                        OfflineModelRowCard(
                            model = model,
                            isInstalled = isInstalled,
                            isSelected = isSelected,
                            partialBytes = partialBytes,
                            progress = if (isThisModelActiveDownload) downloadProgress else null,
                            onDownloadClick = {
                                coroutineScope.launch {
                                    val check = ModelManager.preflightCheck(context, model)
                                    if (check.errorMessage != null) {
                                        localModelStatusMessage = check.errorMessage
                                    } else {
                                        pendingPreflight = check
                                    }
                                }
                            },
                            onPauseClick = { ModelManager.pauseDownload() },
                            onCancelClick = { ModelManager.cancelDownload(context, model) },
                            onDeleteClick = { ModelManager.deleteModel(context, model) },
                            onSelectClick = {
                                ModelManager.selectModel(context, model.id, model.fileName)
                            }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

                    Text(
                        text = "Custom Direct Link (.litertlm)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )

                    OutlinedTextField(
                        value = customUrl,
                        onValueChange = {
                            customUrl = it
                            ModelManager.saveCustomModelInputs(context, it, customFileName)
                        },
                        label = { Text("Direct HTTPS URL (https://.../*.litertlm)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_model_url_field")
                    )

                    OutlinedTextField(
                        value = customFileName,
                        onValueChange = {
                            customFileName = it
                            ModelManager.saveCustomModelInputs(context, customUrl, it)
                        },
                        label = { Text("Optional file name (e.g. my_model.litertlm)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_model_filename_field")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                val customModel = ModelManager.buildCustomLocalModel(customUrl, customFileName)
                                coroutineScope.launch {
                                    val check = ModelManager.preflightCheck(context, customModel)
                                    if (check.errorMessage != null) {
                                        localModelStatusMessage = check.errorMessage
                                    } else {
                                        pendingPreflight = check
                                    }
                                }
                            },
                            enabled = customUrl.isNotBlank(),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("download_custom_model_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Download Link")
                        }

                        FilledTonalButton(
                            onClick = { importModelLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("import_model_file_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileOpen,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Import from file")
                        }
                    }

                    val customProgress = downloadProgress.takeIf {
                        it.modelId.startsWith("custom_") && it.status != DownloadStatus.IDLE
                    }
                    if (customProgress != null) {
                        DownloadProgressPanel(
                            progress = customProgress,
                            onPause = { ModelManager.pauseDownload() },
                            onCancel = {
                                val customModel = ModelManager.buildCustomLocalModel(customUrl, customFileName)
                                ModelManager.cancelDownload(context, customModel)
                            }
                        )
                    }
                }
            }

            // Section B: APK signing identity & PKCS#12 export/import
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_apk_signing_card")
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
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
                            text = "APK signing",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Certificate SHA-256 fingerprint:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = signingFingerprint,
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .padding(10.dp)
                                .testTag("apk_signing_sha256_text")
                        )
                    }

                    Text(
                        text = "Key lives only on this device; uninstalling this app loses it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.testTag("apk_signing_warning_note")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { exportKeyLauncher.launch("aiappstudio_signing_key.p12") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("export_signing_key_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export signing key")
                        }

                        FilledTonalButton(
                            onClick = { importKeyLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("import_signing_key_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.UploadFile,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Import")
                        }
                    }

                    OutlinedButton(
                        onClick = onOpenKeystoreManager,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_open_keystore_manager_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Release Keystore & Signing Manager")
                    }
                }
            }

            // Section C: Studio Default Release Build Settings
            ReleaseBuildSettingsPanel(
                minifyEnabled = releaseFlags.minifyEnabled,
                crunchPngs = releaseFlags.crunchPngs,
                onMinifyEnabledChange = { enabled ->
                    releaseFlags = releaseFlags.copy(minifyEnabled = enabled)
                    ReleaseBuildConfigManager.saveFlags(context, releaseFlags)
                },
                onCrunchPngsChange = { enabled ->
                    releaseFlags = releaseFlags.copy(crunchPngs = enabled)
                    ReleaseBuildConfigManager.saveFlags(context, releaseFlags)
                },
                onSaveWorkspaceDefaults = {
                    val savedFile = ReleaseBuildConfigManager.saveFlags(context, releaseFlags)
                    Toast.makeText(
                        context,
                        "Saved release defaults (${savedFile.name})",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                tagPrefix = "settings_",
                showGradlePreviewInitially = false
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Clear All Projects Button
            Button(
                onClick = { showClearDialog = true },
                enabled = totalProjectsCount > 0,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                contentPadding = PaddingValues(vertical = 14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("clear_all_projects_button")
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Clear All Projects",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OfflineModelRowCard(
    model: LocalModel,
    isInstalled: Boolean,
    isSelected: Boolean,
    partialBytes: Long,
    progress: ModelDownloadProgress?,
    onDownloadClick: () -> Unit,
    onPauseClick: () -> Unit,
    onCancelClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onSelectClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("model_card_${model.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = model.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "File: ${model.fileName} • ${ModelCatalog.sizeDisplayLabel(model, progress?.totalBytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (progress != null && (progress.status == DownloadStatus.DOWNLOADING ||
                    progress.status == DownloadStatus.PROBING ||
                    progress.status == DownloadStatus.PAUSED ||
                    progress.status == DownloadStatus.VERIFYING ||
                    progress.status == DownloadStatus.ERROR ||
                    progress.warningMessage != null)
            ) {
                DownloadProgressPanel(
                    progress = progress,
                    onPause = onPauseClick,
                    onCancel = onCancelClick
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val isRunning = progress?.status == DownloadStatus.DOWNLOADING ||
                    progress?.status == DownloadStatus.PROBING ||
                    progress?.status == DownloadStatus.VERIFYING

                if (!isInstalled && !isRunning) {
                    Button(
                        onClick = onDownloadClick,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("download_btn_${model.id}")
                    ) {
                        Icon(
                            imageVector = if (partialBytes > 0L) Icons.Default.PlayArrow else Icons.Default.CloudDownload,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (partialBytes > 0L) {
                                "Resume (${ModelCatalog.formatBytes(partialBytes)})"
                            } else {
                                "Download"
                            }
                        )
                    }
                }

                if (partialBytes > 0L && !isRunning) {
                    OutlinedButton(
                        onClick = onCancelClick,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("cancel_btn_${model.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cancel,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Cancel")
                    }
                }

                if (isInstalled) {
                    FilterChip(
                        selected = isSelected,
                        onClick = onSelectClick,
                        label = {
                            Text(if (isSelected) "Active Offline Model ✓" else "Use Model")
                        },
                        modifier = Modifier.testTag("use_model_chip_${model.id}")
                    )

                    OutlinedButton(
                        onClick = onDeleteClick,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("delete_btn_${model.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete ${model.title}",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete")
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadProgressPanel(
    progress: ModelDownloadProgress,
    onPause: () -> Unit,
    onCancel: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val total = progress.totalBytes
        if (progress.status == DownloadStatus.DOWNLOADING ||
            progress.status == DownloadStatus.PAUSED ||
            progress.status == DownloadStatus.VERIFYING
        ) {
            if (total != null && total > 0L) {
                val fraction = (progress.bytesDownloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            val speedMb = progress.speedBytesPerSec.toDouble() / (1024.0 * 1024.0)
            val speedText = if (progress.speedBytesPerSec > 0L) {
                String.format("%.2f MB/s", speedMb)
            } else {
                "0.0 MB/s"
            }
            val etaText = progress.etaSeconds?.let { sec ->
                if (sec >= 60) "${sec / 60}m ${sec % 60}s" else "${sec}s"
            } ?: "--"

            Text(
                text = "${ModelCatalog.formatBytes(progress.bytesDownloaded)} / ${ModelCatalog.formatBytes(total)} • $speedText • ETA $etaText (${progress.status.name})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (progress.status == DownloadStatus.DOWNLOADING || progress.status == DownloadStatus.PROBING) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = onPause,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Pause,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Pause")
                }
                OutlinedButton(
                    onClick = onCancel,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Cancel,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Cancel")
                }
            }
        }

        progress.warningMessage?.let { warn ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = Color(0xFFF59E0B),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = warn,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFF59E0B)
                )
            }
        }

        progress.errorMessage?.let { err ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = err,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private fun queryDisplayName(context: android.content.Context, uri: Uri): String? {
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
