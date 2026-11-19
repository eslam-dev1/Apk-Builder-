package com.example.screens

import android.net.Uri
import android.text.format.Formatter
import android.widget.Toast
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.apk.KeystoreFileValidation
import com.example.apk.KeystoreValidationStatus
import com.example.apk.KeystoreValidator
import com.example.apk.SigningOverallStatus
import com.example.apk.SigningSummaryReport
import com.example.model.BuildVariant
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SigningSummaryCard(
    targetVariant: BuildVariant,
    signingScheme: String,
    overrideReport: SigningSummaryReport? = null,
    onValidationReportChanged: (SigningSummaryReport?) -> Unit = {},
    onOpenEnvSetup: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var liveReport by remember(overrideReport) { mutableStateOf(overrideReport) }
    var isValidating by remember { mutableStateOf(overrideReport == null) }
    var refreshTick by remember { mutableIntStateOf(0) }
    var importFeedback by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(targetVariant, refreshTick, overrideReport) {
        if (overrideReport != null) {
            liveReport = overrideReport
            isValidating = false
            onValidationReportChanged(overrideReport)
            return@LaunchedEffect
        }
        isValidating = true
        val evaluated = KeystoreValidator.evaluateSigningSummary(
            context = context,
            targetVariant = targetVariant
        )
        liveReport = evaluated
        isValidating = false
        onValidationReportChanged(evaluated)
    }

    val importJksLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val res = KeystoreValidator.importJksFromUri(context, uri)
                res.onSuccess { importedFile ->
                    importFeedback = "Imported ${importedFile.name}; validating against .env..."
                    refreshTick++
                    Toast.makeText(context, "Imported ${importedFile.name}", Toast.LENGTH_SHORT).show()
                }.onFailure { err ->
                    importFeedback = err.localizedMessage ?: "Failed to import .jks file"
                }
            }
        }
    }

    val report = liveReport
    val overallColors = when (report?.overallStatus) {
        SigningOverallStatus.VALID -> Triple(
            Color(0xFF10B981),
            Color(0xFF10B981).copy(alpha = 0.14f),
            Icons.Default.CheckCircle
        )
        SigningOverallStatus.INVALID -> Triple(
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
            Icons.Default.ErrorOutline
        )
        else -> Triple(
            Color(0xFFF59E0B),
            Color(0xFFF59E0B).copy(alpha = 0.14f),
            Icons.Default.WarningAmber
        )
    }

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
            .fillMaxWidth()
            .testTag("signing_summary_card")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Card Header
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
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Signing Summary",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.testTag("signing_summary_title")
                        )
                        Text(
                            text = "Pre-build .jks keystore validation against .env",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (report != null) {
                    val statusLabel = when (report.overallStatus) {
                        SigningOverallStatus.VALID -> "VALID"
                        SigningOverallStatus.WARNING -> "CHECK"
                        SigningOverallStatus.INVALID -> "INVALID"
                    }
                    Surface(
                        shape = CircleShape,
                        color = overallColors.second,
                        border = BorderStroke(1.dp, overallColors.first.copy(alpha = 0.7f)),
                        modifier = Modifier.testTag("signing_summary_status_badge")
                    ) {
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = overallColors.first,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (isValidating && report == null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    Text(
                        text = "Validating .jks keystores against .env...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (report != null) {
                // Overall Feedback Banner
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = overallColors.second,
                    border = BorderStroke(1.dp, overallColors.first.copy(alpha = 0.55f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = overallColors.third,
                            contentDescription = null,
                            tint = overallColors.first,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                text = report.headline,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.testTag("signing_summary_headline")
                            )
                            Text(
                                text = report.detailMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("signing_summary_detail")
                            )
                            Text(
                                text = "Variant: ${targetVariant.displayName} • Scheme: ${signingScheme.uppercase(Locale.US)} • Source: ${report.envConfig.sourceLabel}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // .env Signing Variables Checklist
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("signing_env_checklist")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = ".env Signing Configuration",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        EnvCheckRow(
                            label = "KEYSTORE_PATH",
                            valueText = if (report.envConfig.hasKeystorePath) {
                                report.envConfig.keystorePath
                            } else {
                                "Not set in .env"
                            },
                            isConfigured = report.envConfig.hasKeystorePath,
                            tag = "signing_env_keystore_path_status"
                        )
                        EnvCheckRow(
                            label = "KEY_ALIAS",
                            valueText = if (report.envConfig.hasKeyAlias) {
                                report.envConfig.keyAlias
                            } else {
                                "Not set in .env"
                            },
                            isConfigured = report.envConfig.hasKeyAlias,
                            tag = "signing_env_key_alias_status"
                        )
                        EnvCheckRow(
                            label = "STORE_PASSWORD",
                            valueText = if (report.envConfig.hasStorePassword) {
                                "Configured (••••••)"
                            } else {
                                "Missing or placeholder in .env"
                            },
                            isConfigured = report.envConfig.hasStorePassword,
                            tag = "signing_env_store_password_status"
                        )
                        EnvCheckRow(
                            label = "KEY_PASSWORD",
                            valueText = if (report.envConfig.hasKeyPassword) {
                                "Configured (••••••)"
                            } else {
                                "Missing or placeholder in .env"
                            },
                            isConfigured = report.envConfig.hasKeyPassword,
                            tag = "signing_env_key_password_status"
                        )

                        Text(
                            text = "Set STORE_PASSWORD, KEY_PASSWORD, KEYSTORE_PATH, and KEY_ALIAS in the AI Studio Secrets panel (.env).",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Discovered .jks Keystore Files Validation Results
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Detected .jks Keystores (${report.validations.size})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (report.validations.isEmpty()) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("signing_empty_jks_state")
                        ) {
                            Text(
                                text = "No .jks keystore files found in app storage or at KEYSTORE_PATH. Import an existing .jks file below to validate it before building.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    } else {
                        report.validations.forEach { validation ->
                            KeystoreValidationRow(
                                validation = validation,
                                onDelete = if (validation.absolutePath.contains("/keystores/")) {
                                    {
                                        KeystoreValidator.deleteImportedKeystore(context, validation.absolutePath)
                                        refreshTick++
                                    }
                                } else {
                                    null
                                }
                            )
                        }
                    }
                }
            }

            importFeedback?.let { msg ->
                Text(
                    text = msg,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = { importJksLauncher.launch(arrayOf("*/*")) },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("signing_import_jks_button")
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
                    onClick = { refreshTick++ },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("signing_summary_revalidate_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Re-validate")
                }

                if (onOpenEnvSetup != null) {
                    OutlinedButton(
                        onClick = onOpenEnvSetup,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("signing_open_env_setup_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Configure .env")
                    }
                }
            }
        }
    }
}

@Composable
private fun EnvCheckRow(
    label: String,
    valueText: String,
    isConfigured: Boolean,
    tag: String
) {
    val accentColor = if (isConfigured) Color(0xFF10B981) else MaterialTheme.colorScheme.error
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = if (isConfigured) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = valueText,
            style = MaterialTheme.typography.labelSmall,
            color = if (isConfigured) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun KeystoreValidationRow(
    validation: KeystoreFileValidation,
    onDelete: (() -> Unit)?
) {
    val context = LocalContext.current
    val (statusColor, statusBadgeText) = when (validation.status) {
        KeystoreValidationStatus.VALID -> Color(0xFF10B981) to "VALID"
        KeystoreValidationStatus.MISSING_ENV_CREDENTIALS -> Color(0xFFF59E0B) to "NEEDS .ENV SECRETS"
        KeystoreValidationStatus.INVALID_PASSWORD -> MaterialTheme.colorScheme.error to "WRONG STORE PASS"
        KeystoreValidationStatus.KEY_PASSWORD_MISMATCH -> MaterialTheme.colorScheme.error to "WRONG KEY PASS"
        KeystoreValidationStatus.ALIAS_NOT_FOUND -> MaterialTheme.colorScheme.error to "ALIAS MISMATCH"
        KeystoreValidationStatus.EXPIRED_CERT -> MaterialTheme.colorScheme.error to "EXPIRED CERT"
        KeystoreValidationStatus.CORRUPTED_FILE -> MaterialTheme.colorScheme.error to "CORRUPTED"
        KeystoreValidationStatus.FILE_NOT_FOUND -> MaterialTheme.colorScheme.error to "NOT FOUND"
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (validation.isValid) {
            Color(0xFF10B981).copy(alpha = 0.08f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        },
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.6f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("signing_jks_item_${validation.fileName}")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
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
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Column {
                        Text(
                            text = validation.fileName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${validation.formatLabel} • ${Formatter.formatShortFileSize(context, validation.sizeBytes)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = statusColor.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = statusBadgeText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    if (onDelete != null) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("signing_delete_jks_${validation.fileName}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Remove ${validation.fileName}",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Text(
                text = validation.statusMessage,
                style = MaterialTheme.typography.bodySmall,
                color = if (validation.isValid) MaterialTheme.colorScheme.onSurface else statusColor
            )

            if (validation.certificateSubject != null || validation.sha256Fingerprint != null) {
                val expiryText = validation.validUntilMs?.let {
                    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(it))
                } ?: "N/A"
                Text(
                    text = buildString {
                        validation.certificateSubject?.let { append("Cert: $it • ") }
                        append("Expires: $expiryText")
                        validation.sha256Fingerprint?.let { append("\nSHA-256: ${it.take(29)}…") }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
