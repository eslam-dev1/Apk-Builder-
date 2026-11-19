package com.example.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.apk.DotEnvConfigManager
import com.example.apk.DotEnvDraft
import com.example.apk.KeystoreValidator
import com.example.apk.SigningSummaryReport
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class EnvSetupStep(
    val stepIndex: Int,
    val title: String,
    val tag: String
) {
    AI_KEYS(0, "1. AI Keys", "env_step_tab_ai"),
    KEYSTORE(1, "2. Keystore", "env_step_tab_keystore"),
    REVIEW_EXPORT(2, "3. Generate", "env_step_tab_review")
}

@Composable
fun EnvSetupScreen(
    initialDraft: DotEnvDraft? = null,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var currentStep by rememberSaveable { mutableStateOf(EnvSetupStep.AI_KEYS) }
    var draft by remember {
        mutableStateOf(initialDraft ?: DotEnvConfigManager.loadInitialDraft(context))
    }
    var discoveredJksFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var signingReport by remember { mutableStateOf<SigningSummaryReport?>(null) }
    var isValidatingKeystore by remember { mutableStateOf(false) }
    var sameKeyPasswordAsStore by rememberSaveable {
        mutableStateOf(
            draft.storePassword.isNotEmpty() && draft.storePassword == draft.keyPassword
        )
    }
    var maskPreviewSecrets by rememberSaveable { mutableStateOf(true) }
    var hasWorkspaceOverride by remember {
        mutableStateOf(DotEnvConfigManager.hasWorkspaceDotEnvOverride(context))
    }
    var savedBannerMessage by remember { mutableStateOf<String?>(null) }
    var validationTick by remember { mutableIntStateOf(0) }

    var showPasteDialog by remember { mutableStateOf(false) }
    var pasteRawText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (initialDraft == null) {
            draft = DotEnvConfigManager.loadInitialDraft(context)
        }
        hasWorkspaceOverride = DotEnvConfigManager.hasWorkspaceDotEnvOverride(context)
    }

    LaunchedEffect(
        draft.keystorePath,
        draft.keyAlias,
        draft.storePassword,
        draft.keyPassword,
        validationTick
    ) {
        isValidatingKeystore = true
        val envCfg = draft.toEnvSigningConfig()
        discoveredJksFiles = withContext(Dispatchers.IO) {
            KeystoreValidator.discoverJksFiles(context, envCfg)
        }
        signingReport = DotEnvConfigManager.validateSigningAgainstKeystores(context, draft)
        isValidatingKeystore = false
    }

    val importJksLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val res = KeystoreValidator.importJksFromUri(context, uri)
                runOnMainLooper {
                    res.onSuccess { imported ->
                        draft = draft.copy(keystorePath = imported.name)
                        validationTick++
                        Toast.makeText(context, "Imported ${imported.name}", Toast.LENGTH_SHORT).show()
                    }.onFailure { err ->
                        Toast.makeText(
                            context,
                            err.localizedMessage ?: "Failed to import .jks file",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    val exportDotEnvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val content = DotEnvConfigManager.formatDotEnv(draft, maskSecrets = false) + "\n"
                val writeResult = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            out.write(content.toByteArray(Charsets.UTF_8))
                        } ?: error("Unable to open destination file.")
                    }
                }
                runOnMainLooper {
                    writeResult.onSuccess {
                        savedBannerMessage = "Exported .env file to selected location."
                        Toast.makeText(context, ".env exported", Toast.LENGTH_SHORT).show()
                    }.onFailure { err ->
                        Toast.makeText(
                            context,
                            "Export failed: ${err.localizedMessage ?: "I/O error"}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    if (showPasteDialog) {
        AlertDialog(
            onDismissRequest = { showPasteDialog = false },
            title = { Text("Paste Existing .env Content") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Paste KEY=VALUE lines (GEMINI_API_KEY, OPENROUTER_API_KEY, KEYSTORE_PATH, STORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD) to pre-fill the wizard:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = pasteRawText,
                        onValueChange = { pasteRawText = it },
                        label = { Text(".env snippet") },
                        minLines = 5,
                        maxLines = 8,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("env_setup_paste_dialog_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        draft = DotEnvConfigManager.parseDotEnvToDraft(pasteRawText, fallback = draft)
                        sameKeyPasswordAsStore =
                            draft.storePassword.isNotEmpty() && draft.storePassword == draft.keyPassword
                        showPasteDialog = false
                        savedBannerMessage = null
                        validationTick++
                    },
                    enabled = pasteRawText.isNotBlank(),
                    modifier = Modifier.testTag("env_setup_paste_confirm_button")
                ) {
                    Text("Apply Values")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    val checklist = remember(draft, signingReport) {
        DotEnvConfigManager.evaluateChecklist(draft, signingReport)
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                            .testTag("env_setup_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                    Column {
                        Text(
                            text = "Guided .env Setup",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("env_setup_title")
                        )
                        Text(
                            text = "Step ${currentStep.stepIndex + 1} of 3 • ${draft.configuredCount}/6 configured",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("env_setup_subtitle")
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VpnKey,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = ".env",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            // Progress Bar & Step Selector Chips
            LinearProgressIndicator(
                progress = { (currentStep.stepIndex + 1f) / EnvSetupStep.entries.size.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("env_setup_progress_bar")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EnvSetupStep.entries.forEach { step ->
                    val selected = currentStep == step
                    FilterChip(
                        selected = selected,
                        onClick = { currentStep = step },
                        label = {
                            Text(
                                text = step.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingIcon = if (step.stepIndex < currentStep.stepIndex) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else {
                            null
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag(step.tag)
                    )
                }
            }

            // Step Body
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (currentStep) {
                    EnvSetupStep.AI_KEYS -> EnvAiKeysStep(
                        draft = draft,
                        onDraftChange = {
                            draft = it
                            savedBannerMessage = null
                        },
                        onOpenPasteDialog = {
                            pasteRawText = ""
                            showPasteDialog = true
                        }
                    )

                    EnvSetupStep.KEYSTORE -> EnvKeystoreStep(
                        draft = draft,
                        discoveredJksFiles = discoveredJksFiles,
                        signingReport = signingReport,
                        isValidatingKeystore = isValidatingKeystore,
                        sameKeyPasswordAsStore = sameKeyPasswordAsStore,
                        onSamePasswordToggle = { sameKeyPasswordAsStore = it },
                        onDraftChange = {
                            draft = it
                            savedBannerMessage = null
                        },
                        onImportJksClick = { importJksLauncher.launch(arrayOf("*/*")) },
                        onValidateNow = { validationTick++ }
                    )

                    EnvSetupStep.REVIEW_EXPORT -> EnvReviewExportStep(
                        draft = draft,
                        checklist = checklist,
                        maskPreviewSecrets = maskPreviewSecrets,
                        hasWorkspaceOverride = hasWorkspaceOverride,
                        savedBannerMessage = savedBannerMessage,
                        onToggleMaskPreview = { maskPreviewSecrets = it },
                        onToggleIncludeComments = {
                            draft = draft.copy(includeComments = it)
                            savedBannerMessage = null
                        },
                        onSaveToWorkspace = {
                            coroutineScope.launch {
                                val res = DotEnvConfigManager.saveDraftToWorkspace(context, draft)
                                runOnMainLooper {
                                    res.onSuccess { savedFile ->
                                        hasWorkspaceOverride = true
                                        savedBannerMessage = "Saved ${savedFile.name} to app workspace & synced signing/AI config."
                                        Toast.makeText(context, ".env saved to workspace", Toast.LENGTH_SHORT).show()
                                    }.onFailure { err ->
                                        Toast.makeText(
                                            context,
                                            "Failed to save .env: ${err.localizedMessage ?: "Error"}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            }
                        },
                        onExportDotEnvFile = { exportDotEnvLauncher.launch(".env") },
                        onCopyDotEnv = {
                            val content = DotEnvConfigManager.formatDotEnv(draft, maskSecrets = false)
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            clipboard?.setPrimaryClip(ClipData.newPlainText(".env", content))
                            savedBannerMessage = "Copied raw .env content to clipboard."
                            runOnMainLooper {
                                Toast.makeText(context, ".env copied to clipboard", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onResetOverride = {
                            DotEnvConfigManager.resetWorkspaceDotEnv(context)
                            hasWorkspaceOverride = false
                            draft = DotEnvConfigManager.loadInitialDraft(context)
                            validationTick++
                            savedBannerMessage = "Cleared workspace .env override; restored BuildConfig defaults."
                        }
                    )
                }
            }

            // Bottom Wizard Navigation
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            when (currentStep) {
                                EnvSetupStep.AI_KEYS -> onBack()
                                EnvSetupStep.KEYSTORE -> currentStep = EnvSetupStep.AI_KEYS
                                EnvSetupStep.REVIEW_EXPORT -> currentStep = EnvSetupStep.KEYSTORE
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("env_setup_prev_step_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (currentStep == EnvSetupStep.AI_KEYS) "Close" else "Previous")
                    }

                    if (currentStep != EnvSetupStep.REVIEW_EXPORT) {
                        Button(
                            onClick = {
                                currentStep = if (currentStep == EnvSetupStep.AI_KEYS) {
                                    EnvSetupStep.KEYSTORE
                                } else {
                                    EnvSetupStep.REVIEW_EXPORT
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("env_setup_next_step_button")
                        ) {
                            Text(if (currentStep == EnvSetupStep.AI_KEYS) "Next: Keystore" else "Next: Review .env")
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    val res = DotEnvConfigManager.saveDraftToWorkspace(context, draft)
                                    runOnMainLooper {
                                        res.onSuccess {
                                            hasWorkspaceOverride = true
                                            Toast.makeText(context, ".env saved", Toast.LENGTH_SHORT).show()
                                            onBack()
                                        }.onFailure { err ->
                                            Toast.makeText(
                                                context,
                                                "Failed to save .env: ${err.localizedMessage ?: "Error"}",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("env_setup_finish_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save & Done")
                        }
                    }
                }
            }
        }
    }
}

private fun runOnMainLooper(action: () -> Unit) {
    if (Looper.myLooper() == Looper.getMainLooper()) {
        action()
    } else {
        Handler(Looper.getMainLooper()).post(action)
    }
}
