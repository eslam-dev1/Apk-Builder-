package com.example.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai.LiteRtRuntimeManager
import com.example.ai.LocalTfliteModel
import com.example.ai.TfliteBenchmarkResult
import com.example.ai.TfliteModelManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TfliteModelSelectorCard(
    selectedModelFileNames: List<String>,
    onSelectionChange: (List<String>) -> Unit,
    onOpenLocalAiManager: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val localModels by TfliteModelManager.localModels.collectAsStateWithLifecycle()
    val downloadState by TfliteModelManager.downloadState.collectAsStateWithLifecycle()
    val benchmarks by LiteRtRuntimeManager.benchmarks.collectAsStateWithLifecycle()
    val testingFile by LiteRtRuntimeManager.testingFile.collectAsStateWithLifecycle()

    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isStatusError by remember { mutableStateOf(false) }
    var showUrlDownloadDialog by remember { mutableStateOf(false) }
    var urlInput by remember { mutableStateOf("") }
    var customFileNameInput by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        TfliteModelManager.refreshModels(context)
    }

    val importTfliteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val result = TfliteModelManager.importTfliteFromUri(context, uri)
                result.onSuccess { imported ->
                    isStatusError = false
                    statusMessage = "Imported ${imported.fileName} (${TfliteModelManager.formatBytes(imported.sizeBytes)})"
                    if (imported.fileName !in selectedModelFileNames) {
                        onSelectionChange(selectedModelFileNames + imported.fileName)
                    }
                    Toast.makeText(
                        context,
                        "Imported ${imported.fileName} for APK bundling",
                        Toast.LENGTH_SHORT
                    ).show()
                }.onFailure { err ->
                    isStatusError = true
                    statusMessage = err.localizedMessage ?: "Failed to import .tflite file"
                }
            }
        }
    }

    val availableByFileName = remember(localModels) {
        localModels.associateBy { it.fileName }
    }
    val selectedMissingModels = remember(selectedModelFileNames, availableByFileName) {
        selectedModelFileNames.filter { it !in availableByFileName }
    }
    val totalSelectedBytes = remember(selectedModelFileNames, availableByFileName) {
        selectedModelFileNames.sumOf { availableByFileName[it]?.sizeBytes ?: 0L }
    }

    if (showUrlDownloadDialog) {
        AlertDialog(
            onDismissRequest = { if (!downloadState.isDownloading) showUrlDownloadDialog = false },
            title = { Text("Download .tflite Model") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Enter a direct HTTPS URL to a TensorFlow Lite (.tflite) model file. It will be saved locally for offline APK bundling.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text("HTTPS Model URL") },
                        placeholder = { Text("https://.../model.tflite") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("tflite_url_input")
                    )
                    OutlinedTextField(
                        value = customFileNameInput,
                        onValueChange = { customFileNameInput = it },
                        label = { Text("File Name (Optional)") },
                        placeholder = { Text("mobilenet_v2.tflite") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("tflite_filename_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val url = urlInput.trim()
                        val customName = customFileNameInput.trim().takeIf { it.isNotEmpty() }
                        showUrlDownloadDialog = false
                        coroutineScope.launch {
                            val result = TfliteModelManager.downloadTfliteFromUrl(context, url, customName)
                            result.onSuccess { downloaded ->
                                isStatusError = false
                                statusMessage = "Downloaded ${downloaded.fileName} (${TfliteModelManager.formatBytes(downloaded.sizeBytes)})"
                                if (downloaded.fileName !in selectedModelFileNames) {
                                    onSelectionChange(selectedModelFileNames + downloaded.fileName)
                                }
                            }.onFailure { err ->
                                isStatusError = true
                                statusMessage = err.localizedMessage ?: "Download failed"
                            }
                        }
                    },
                    enabled = urlInput.trim().startsWith("https://", ignoreCase = true),
                    modifier = Modifier.testTag("confirm_tflite_url_download_button")
                ) {
                    Text("Download")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showUrlDownloadDialog = false },
                    modifier = Modifier.testTag("cancel_tflite_url_download_button")
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("tflite_model_selector_card")
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
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Memory,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Offline TFLite Models (.tflite)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Bundle uncompressed models into APK assets/models/",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = {
                        TfliteModelManager.refreshModels(context)
                        statusMessage = null
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("refresh_tflite_models_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh local TFLite models"
                    )
                }
            }

            // Selection Summary Pill Row
            Surface(
                shape = MaterialTheme.shapes.small,
                color = if (selectedModelFileNames.isNotEmpty()) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selectedModelFileNames.isEmpty()) {
                            "No .tflite models selected for bundling"
                        } else {
                            "${selectedModelFileNames.size} selected • ${TfliteModelManager.formatBytes(totalSelectedBytes)} payload"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = if (selectedModelFileNames.isNotEmpty()) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.testTag("tflite_selection_summary_text")
                    )

                    if (localModels.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (selectedModelFileNames.size < localModels.size) {
                                TextButton(
                                    onClick = {
                                        onSelectionChange(localModels.map { it.fileName })
                                    },
                                    modifier = Modifier.testTag("tflite_select_all_button")
                                ) {
                                    Text("Select All")
                                }
                            }
                            if (selectedModelFileNames.isNotEmpty()) {
                                TextButton(
                                    onClick = { onSelectionChange(emptyList()) },
                                    modifier = Modifier.testTag("tflite_clear_all_button")
                                ) {
                                    Text("Clear")
                                }
                            }
                        }
                    }
                }
            }

            // Action Buttons (Import from Device via SAF / Download HTTPS URL)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = { importTfliteLauncher.launch(arrayOf("*/*")) },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("import_tflite_model_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Import .tflite")
                }

                OutlinedButton(
                    onClick = {
                        urlInput = ""
                        customFileNameInput = ""
                        showUrlDownloadDialog = true
                    },
                    enabled = !downloadState.isDownloading,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("fetch_tflite_url_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Fetch URL")
                }

                if (onOpenLocalAiManager != null) {
                    OutlinedButton(
                        onClick = onOpenLocalAiManager,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("open_local_ai_manager_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open Local AI Manager")
                    }
                }
            }

            // Active URL Download Progress
            AnimatedVisibility(
                visible = downloadState.isDownloading,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tflite_download_progress_box")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val total = downloadState.totalBytes
                        val downloaded = downloadState.bytesDownloaded
                        val pctText = if (total != null && total > 0L) {
                            val pct = ((downloaded.toFloat() / total.toFloat()) * 100f).toInt().coerceIn(0, 100)
                            " ($pct%)"
                        } else {
                            ""
                        }
                        Text(
                            text = "Downloading ${downloadState.fileName}$pctText • ${TfliteModelManager.formatBytes(downloaded)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                        if (total != null && total > 0L) {
                            LinearProgressIndicator(
                                progress = { (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }

            // Feedback Banner
            statusMessage?.let { msg ->
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (isStatusError) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tflite_status_banner")
                ) {
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isStatusError) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        },
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            // Missing-on-disk selected models warning
            if (selectedMissingModels.isNotEmpty()) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tflite_missing_models_warning")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Selected model(s) missing from device storage:",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        selectedMissingModels.forEach { missingName ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = missingName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = {
                                        onSelectionChange(selectedModelFileNames.filterNot { it == missingName })
                                    },
                                    modifier = Modifier.testTag("remove_missing_tflite_$missingName")
                                ) {
                                    Text("Remove")
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

            // Local TFLite Models List or Empty State
            if (localModels.isEmpty()) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tflite_empty_state")
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddCircleOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "No local .tflite models found on device",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Import a .tflite file from storage or fetch one via HTTPS URL. Selected models are injected uncompressed (STORED, 4-byte aligned) into assets/models/ for TensorFlow Lite.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    localModels.forEach { model ->
                        val isSelected = model.fileName in selectedModelFileNames
                        TfliteModelRowItem(
                            model = model,
                            isSelected = isSelected,
                            benchmark = benchmarks[model.fileName],
                            isTesting = testingFile == model.fileName,
                            onToggle = { checked ->
                                val next = if (checked) {
                                    (selectedModelFileNames + model.fileName).distinct()
                                } else {
                                    selectedModelFileNames.filterNot { it == model.fileName }
                                }
                                onSelectionChange(next)
                            },
                            onBenchmark = {
                                coroutineScope.launch {
                                    val res = LiteRtRuntimeManager.inspectAndBenchmarkModel(
                                        context = context,
                                        fileName = model.fileName
                                    )
                                    isStatusError = !res.isVerified
                                    statusMessage = if (res.isVerified) {
                                        "${model.fileName}: ${res.statusNote}"
                                    } else {
                                        "${model.fileName}: ${res.errorMessage ?: res.statusNote}"
                                    }
                                }
                            },
                            onDelete = {
                                TfliteModelManager.deleteModel(context, model.fileName)
                                LiteRtRuntimeManager.clearResult(model.fileName)
                                onSelectionChange(selectedModelFileNames.filterNot { it == model.fileName })
                                isStatusError = false
                                statusMessage = "Removed ${model.fileName}"
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TfliteModelRowItem(
    model: LocalTfliteModel,
    isSelected: Boolean,
    benchmark: TfliteBenchmarkResult?,
    isTesting: Boolean,
    onToggle: (Boolean) -> Unit,
    onBenchmark: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        },
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable { onToggle(!isSelected) }
            .testTag("tflite_model_row_${model.fileName}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = onToggle,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("tflite_checkbox_${model.fileName}")
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = model.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "assets/models/${model.fileName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = model.taskCategory,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = TfliteModelManager.formatBytes(model.sizeBytes),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    if (model.hasValidFlatBufferHeader) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Verified,
                                    contentDescription = "Verified TFL3 FlatBuffer",
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "TFL3",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }
                    if (benchmark != null) {
                        Surface(
                            shape = CircleShape,
                            color = if (benchmark.isVerified) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.errorContainer
                            }
                        ) {
                            Text(
                                text = if (benchmark.isVerified) {
                                    "${benchmark.acceleratorUsed} • ${benchmark.inferenceMs}ms"
                                } else {
                                    "Invalid Graph"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (benchmark.isVerified) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onErrorContainer
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            IconButton(
                onClick = onBenchmark,
                enabled = !isTesting,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("benchmark_tflite_${model.fileName}")
            ) {
                if (isTesting) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = "Verify and benchmark ${model.displayName}",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("delete_tflite_${model.fileName}")
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Delete ${model.displayName}",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
