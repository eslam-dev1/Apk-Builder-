package com.example.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.apk.DotEnvChecklistItem
import com.example.apk.DotEnvConfigManager
import com.example.apk.DotEnvDraft
import com.example.apk.SigningOverallStatus
import com.example.apk.SigningSummaryReport
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.io.File

@Composable
internal fun EnvAiKeysStep(
    draft: DotEnvDraft,
    onDraftChange: (DotEnvDraft) -> Unit,
    onOpenPasteDialog: () -> Unit
) {
    var showGemini by remember { mutableStateOf(false) }
    var showOpenRouter by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
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
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Step 1: AI Provider API Keys",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    TextButton(
                        onClick = onOpenPasteDialog,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("env_setup_paste_dotenv_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Paste .env")
                    }
                }
                Text(
                    text = "Configure GEMINI_API_KEY and optional OPENROUTER_API_KEY. For shared builds, store secrets in the AI Studio Secrets panel rather than committing a populated .env file.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // GEMINI_API_KEY card
        SecretKeyFieldCard(
            envKeyName = "GEMINI_API_KEY",
            subtitle = "Powers Cloud Gemini project generation, feature ideas & Imagen icon generation",
            value = draft.geminiApiKey,
            isConfigured = draft.hasGeminiKey,
            badgeText = if (draft.hasGeminiKey) "Configured" else "Optional / Placeholder",
            placeholder = "AIzaSy...",
            helperText = if (draft.geminiApiKey.isNotBlank() && !draft.geminiApiKey.trim().startsWith("AIza")) {
                "Note: Google AI Studio Gemini keys typically start with 'AIza'."
            } else {
                "Leave blank to write MY_GEMINI_API_KEY placeholder (local/offline AI stays active)."
            },
            showValue = showGemini,
            onToggleVisibility = { showGemini = !showGemini },
            onValueChange = { onDraftChange(draft.copy(geminiApiKey = it)) },
            inputTag = "env_setup_gemini_key_input",
            toggleTag = "env_setup_toggle_gemini_visibility",
            clearTag = "env_setup_clear_gemini_button"
        )

        // OPENROUTER_API_KEY card
        SecretKeyFieldCard(
            envKeyName = "OPENROUTER_API_KEY",
            subtitle = "Optional multi-model fallback provider when OpenRouter is selected in Settings",
            value = draft.openRouterApiKey,
            isConfigured = draft.hasOpenRouterKey,
            badgeText = if (draft.hasOpenRouterKey) "Configured" else "Optional",
            placeholder = "sk-or-v1-...",
            helperText = if (draft.openRouterApiKey.isNotBlank() && !draft.openRouterApiKey.trim().startsWith("sk-or-")) {
                "Tip: OpenRouter keys usually begin with 'sk-or-v1-'."
            } else {
                "Leave blank to keep MY_OPENROUTER_API_KEY placeholder."
            },
            showValue = showOpenRouter,
            onToggleVisibility = { showOpenRouter = !showOpenRouter },
            onValueChange = { onDraftChange(draft.copy(openRouterApiKey = it)) },
            inputTag = "env_setup_openrouter_key_input",
            toggleTag = "env_setup_toggle_openrouter_visibility",
            clearTag = "env_setup_clear_openrouter_button"
        )
    }
}

@Composable
private fun SecretKeyFieldCard(
    envKeyName: String,
    subtitle: String,
    value: String,
    isConfigured: Boolean,
    badgeText: String,
    placeholder: String,
    helperText: String,
    showValue: Boolean,
    onToggleVisibility: () -> Unit,
    onValueChange: (String) -> Unit,
    inputTag: String,
    toggleTag: String,
    clearTag: String
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = envKeyName,
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    shape = CircleShape,
                    color = if (isConfigured) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isConfigured) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(envKeyName) },
                placeholder = { Text(placeholder) },
                singleLine = true,
                visualTransformation = if (showValue) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (value.isNotEmpty()) {
                            IconButton(
                                onClick = { onValueChange("") },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag(clearTag)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear $envKeyName"
                                )
                            }
                        }
                        IconButton(
                            onClick = onToggleVisibility,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag(toggleTag)
                        ) {
                            Icon(
                                imageVector = if (showValue) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showValue) "Hide $envKeyName" else "Show $envKeyName"
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(inputTag)
            )

            Text(
                text = helperText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EnvKeystoreStep(
    draft: DotEnvDraft,
    discoveredJksFiles: List<File>,
    signingReport: SigningSummaryReport?,
    isValidatingKeystore: Boolean,
    sameKeyPasswordAsStore: Boolean,
    onSamePasswordToggle: (Boolean) -> Unit,
    onDraftChange: (DotEnvDraft) -> Unit,
    onImportJksClick: () -> Unit,
    onValidateNow: () -> Unit
) {
    var showStorePass by remember { mutableStateOf(false) }
    var showKeyPass by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Step 2: Release Keystore Credentials",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "Configure KEYSTORE_PATH, KEY_ALIAS, STORE_PASSWORD, and KEY_PASSWORD for Gradle release signing and Signing Summary validation.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = onImportJksClick,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("env_setup_import_jks_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Import .jks")
                    }

                    OutlinedButton(
                        onClick = onValidateNow,
                        enabled = !isValidatingKeystore,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("env_setup_verify_keystore_button")
                    ) {
                        if (isValidatingKeystore) {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Verify .jks")
                    }
                }

                if (discoveredJksFiles.isNotEmpty()) {
                    Text(
                        text = "Detected .jks files (tap to fill KEYSTORE_PATH):",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        discoveredJksFiles.forEach { file ->
                            FilterChip(
                                selected = draft.keystorePath.trim() == file.name || draft.keystorePath.trim() == file.absolutePath,
                                onClick = {
                                    onDraftChange(draft.copy(keystorePath = file.name))
                                },
                                label = { Text(file.name) },
                                modifier = Modifier.testTag("env_setup_jks_chip_${file.name}")
                            )
                        }
                    }
                }
            }
        }

        Card(
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = draft.keystorePath,
                    onValueChange = { onDraftChange(draft.copy(keystorePath = it)) },
                    label = { Text("KEYSTORE_PATH") },
                    placeholder = { Text(DotEnvDraft.DEFAULT_KEYSTORE_PATH) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("env_setup_keystore_path_input")
                )

                OutlinedTextField(
                    value = draft.keyAlias,
                    onValueChange = { onDraftChange(draft.copy(keyAlias = it)) },
                    label = { Text("KEY_ALIAS") },
                    placeholder = { Text(DotEnvDraft.DEFAULT_KEY_ALIAS) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("env_setup_key_alias_input")
                )

                OutlinedTextField(
                    value = draft.storePassword,
                    onValueChange = { nextPass ->
                        val updated = if (sameKeyPasswordAsStore) {
                            draft.copy(storePassword = nextPass, keyPassword = nextPass)
                        } else {
                            draft.copy(storePassword = nextPass)
                        }
                        onDraftChange(updated)
                    },
                    label = { Text("STORE_PASSWORD") },
                    placeholder = { Text("Release keystore password") },
                    singleLine = true,
                    visualTransformation = if (showStorePass) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(
                            onClick = { showStorePass = !showStorePass },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("env_setup_toggle_store_pass_visibility")
                        ) {
                            Icon(
                                imageVector = if (showStorePass) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showStorePass) "Hide STORE_PASSWORD" else "Show STORE_PASSWORD"
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("env_setup_store_password_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "KEY_PASSWORD matches STORE_PASSWORD",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = sameKeyPasswordAsStore,
                        onCheckedChange = { checked ->
                            onSamePasswordToggle(checked)
                            if (checked) {
                                onDraftChange(draft.copy(keyPassword = draft.storePassword))
                            }
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("env_setup_match_passwords_switch")
                    )
                }

                OutlinedTextField(
                    value = draft.keyPassword,
                    onValueChange = { nextKeyPass ->
                        if (sameKeyPasswordAsStore && nextKeyPass != draft.storePassword) {
                            onSamePasswordToggle(false)
                        }
                        onDraftChange(draft.copy(keyPassword = nextKeyPass))
                    },
                    label = { Text("KEY_PASSWORD") },
                    placeholder = { Text("Upload key alias password") },
                    singleLine = true,
                    visualTransformation = if (showKeyPass) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(
                            onClick = { showKeyPass = !showKeyPass },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("env_setup_toggle_key_pass_visibility")
                        ) {
                            Icon(
                                imageVector = if (showKeyPass) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showKeyPass) "Hide KEY_PASSWORD" else "Show KEY_PASSWORD"
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("env_setup_key_password_input")
                )
            }
        }

        // Live validation feedback banner
        signingReport?.let { report ->
            val (containerColor, accentColor, statusIcon) = when (report.overallStatus) {
                SigningOverallStatus.VALID -> Triple(
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    MaterialTheme.colorScheme.primary,
                    Icons.Default.CheckCircle
                )
                SigningOverallStatus.INVALID -> Triple(
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
                    MaterialTheme.colorScheme.error,
                    Icons.Default.ErrorOutline
                )
                SigningOverallStatus.WARNING -> Triple(
                    MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f),
                    MaterialTheme.colorScheme.tertiary,
                    Icons.Default.WarningAmber
                )
            }

            Surface(
                shape = MaterialTheme.shapes.medium,
                color = containerColor,
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.6f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("env_setup_keystore_validation_card")
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = report.headline,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("env_setup_validation_headline")
                        )
                        Text(
                            text = report.detailMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EnvReviewExportStep(
    draft: DotEnvDraft,
    checklist: List<DotEnvChecklistItem>,
    maskPreviewSecrets: Boolean,
    hasWorkspaceOverride: Boolean,
    savedBannerMessage: String?,
    onToggleMaskPreview: (Boolean) -> Unit,
    onToggleIncludeComments: (Boolean) -> Unit,
    onSaveToWorkspace: () -> Unit,
    onExportDotEnvFile: () -> Unit,
    onCopyDotEnv: () -> Unit,
    onResetOverride: () -> Unit
) {
    val previewContent = remember(draft, maskPreviewSecrets) {
        DotEnvConfigManager.formatDotEnv(draft, maskSecrets = maskPreviewSecrets)
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Key Readiness Checklist
        Card(
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("env_setup_checklist_card")
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Step 3: Review & Generate .env (${draft.configuredCount} / 6 keys set)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                checklist.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("env_checklist_row_${item.key}"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = when {
                                    item.isConfigured -> Icons.Default.CheckCircle
                                    item.isRequired -> Icons.Default.WarningAmber
                                    else -> Icons.Default.Lock
                                },
                                contentDescription = null,
                                tint = when {
                                    item.isConfigured -> MaterialTheme.colorScheme.primary
                                    item.isRequired -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(16.dp)
                            )
                            Column {
                                Text(
                                    text = item.key,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = item.summary,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // Preview options + Generated .env box
        Card(
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = ".env File Preview",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${previewContent.lines().size} lines",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = maskPreviewSecrets,
                        onClick = { onToggleMaskPreview(!maskPreviewSecrets) },
                        label = { Text(if (maskPreviewSecrets) "Secrets Masked" else "Secrets Visible") },
                        modifier = Modifier.testTag("env_setup_mask_preview_chip")
                    )
                    FilterChip(
                        selected = draft.includeComments,
                        onClick = { onToggleIncludeComments(!draft.includeComments) },
                        label = { Text("Include Comments") },
                        modifier = Modifier.testTag("env_setup_comments_chip")
                    )
                }

                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("env_setup_preview_box")
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(12.dp)
                    ) {
                        Text(
                            text = previewContent,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = JetBrainsMonoFontFamily,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.testTag("env_setup_preview_text")
                        )
                    }
                }
            }
        }

        // Security Best Practices Note
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Security Tip: Never commit .env to git or share release APKs containing embedded prototype keys. Use the AI Studio Secrets panel for managed builds.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        savedBannerMessage?.let { msg ->
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("env_setup_saved_banner")
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        // Action Buttons
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onSaveToWorkspace,
                contentPadding = PaddingValues(vertical = 12.dp, horizontal = 16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("env_setup_save_workspace_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save .env to App Workspace")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = onExportDotEnvFile,
                    contentPadding = PaddingValues(vertical = 12.dp, horizontal = 12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("env_setup_export_file_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export .env")
                }

                OutlinedButton(
                    onClick = onCopyDotEnv,
                    contentPadding = PaddingValues(vertical = 12.dp, horizontal = 12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("env_setup_copy_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy .env")
                }
            }

            if (hasWorkspaceOverride) {
                OutlinedButton(
                    onClick = onResetOverride,
                    contentPadding = PaddingValues(vertical = 12.dp, horizontal = 14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("env_setup_reset_button")
                ) {
                    Text("Reset Workspace .env Override")
                }
            }
        }
    }
}
