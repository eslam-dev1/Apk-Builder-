package com.example.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.OfflineBolt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.ai.LiteRtRuntimeManager
import com.example.ai.LlmBackendChoice
import com.example.ai.LocalAiCapabilitySummary
import com.example.ai.LocalAiCategory
import com.example.ai.LocalLlmEngine
import com.example.ai.LocalLlmState
import com.example.ai.LocalTfliteModel
import com.example.ai.TfliteBenchmarkResult
import com.example.ai.TfliteDownloadState
import com.example.ai.TfliteModelManager
import kotlinx.coroutines.launch

enum class LocalAiFilterTab(val label: String, val tag: String) {
    ALL("All", "local_ai_filter_all"),
    DEVICE_TOOLS("Device AI Tools", "local_ai_filter_tools"),
    ON_DEVICE_LLMS("On-Device LLMs", "local_ai_filter_llms"),
    TFLITE_MODELS("TFLite Models", "local_ai_filter_tflite")
}

@Composable
fun LocalAiFilterChipRow(
    selectedTab: LocalAiFilterTab,
    onSelectTab: (LocalAiFilterTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .testTag("local_ai_filter_row"),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LocalAiFilterTab.entries.forEach { tab ->
            FilterChip(
                selected = selectedTab == tab,
                onClick = { onSelectTab(tab) },
                label = { Text(tab.label) },
                modifier = Modifier.testTag(tab.tag)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LocalAiCapabilityListCard(
    capability: LocalAiCapabilitySummary,
    llmState: LocalLlmState,
    hasInstalledLlm: Boolean,
    onOpenLlmTester: () -> Unit,
    onUnloadLlm: () -> Unit,
    onViewTfliteTab: () -> Unit,
    onViewLlmTab: () -> Unit,
    onOpenCreate: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val iconVector = when (capability.id) {
        "cap_litert_lm" -> Icons.Default.Psychology
        "cap_litert_compiled" -> Icons.Default.Memory
        "cap_mlkit_ocr" -> Icons.Default.DocumentScanner
        "cap_speech_dictation" -> Icons.Default.Mic
        else -> Icons.Default.OfflineBolt
    }
    val statusColor = if (capability.isReady) Color(0xFF10B981) else MaterialTheme.colorScheme.secondary

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("local_ai_capability_${capability.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        modifier = Modifier.size(38.dp)
                    ) {
                        androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = iconVector,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = capability.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${capability.category.label} • ${capability.engineName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    shape = CircleShape,
                    color = statusColor.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.7f))
                ) {
                    Text(
                        text = capability.statusBadge,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = capability.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                when (capability.category) {
                    LocalAiCategory.LLM_ENGINE -> {
                        if (hasInstalledLlm) {
                            FilledTonalButton(
                                onClick = onOpenLlmTester,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("cap_action_test_llm")
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Test / Load LLM")
                            }
                            if (llmState.isReady) {
                                OutlinedButton(
                                    onClick = onUnloadLlm,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("cap_action_unload_llm")
                                ) {
                                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Unload Engine")
                                }
                            }
                        } else {
                            OutlinedButton(
                                onClick = onViewLlmTab,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("cap_action_browse_llms")
                            ) {
                                Text("Browse .litertlm Catalog")
                            }
                        }
                    }

                    LocalAiCategory.TFLITE_RUNTIME -> {
                        FilledTonalButton(
                            onClick = onViewTfliteTab,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("cap_action_open_tflite")
                        ) {
                            Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Manage & Benchmark .tflite")
                        }
                    }

                    LocalAiCategory.DEVICE_TOOLS -> {
                        if (onOpenCreate != null) {
                            FilledTonalButton(
                                onClick = onOpenCreate,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("cap_action_open_create_${capability.id}")
                            ) {
                                Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Use in Create")
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TfliteHubToolbarCard(
    localModelsCount: Int,
    downloadState: TfliteDownloadState,
    statusMessage: String?,
    isStatusError: Boolean,
    onStatusUpdate: (String?, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showUrlDialog by remember { mutableStateOf(false) }
    var urlInput by remember { mutableStateOf("") }
    var customNameInput by remember { mutableStateOf("") }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val res = TfliteModelManager.importTfliteFromUri(context, uri)
                res.onSuccess { imported ->
                    onStatusUpdate("Imported ${imported.fileName} (${TfliteModelManager.formatBytes(imported.sizeBytes)})", false)
                }.onFailure { err ->
                    onStatusUpdate(err.localizedMessage ?: "Failed to import .tflite model", true)
                }
            }
        }
    }

    if (showUrlDialog) {
        AlertDialog(
            onDismissRequest = { if (!downloadState.isDownloading) showUrlDialog = false },
            title = { Text("Download .tflite Model") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Download any TensorFlow Lite (.tflite) model via HTTPS to verify with LiteRT CompiledModel and bundle inside APKs.",
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
                            .testTag("hub_tflite_url_input")
                    )
                    OutlinedTextField(
                        value = customNameInput,
                        onValueChange = { customNameInput = it },
                        label = { Text("File Name (Optional)") },
                        placeholder = { Text("mobilenet_v2.tflite") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("hub_tflite_filename_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val url = urlInput.trim()
                        val customName = customNameInput.trim().takeIf { it.isNotEmpty() }
                        showUrlDialog = false
                        coroutineScope.launch {
                            val res = TfliteModelManager.downloadTfliteFromUrl(context, url, customName)
                            res.onSuccess { dl ->
                                onStatusUpdate("Downloaded ${dl.fileName} (${TfliteModelManager.formatBytes(dl.sizeBytes)})", false)
                            }.onFailure { err ->
                                onStatusUpdate(err.localizedMessage ?: "Download failed", true)
                            }
                        }
                    },
                    enabled = urlInput.trim().startsWith("https://", ignoreCase = true)
                ) {
                    Text("Download")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUrlDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("tflite_hub_toolbar_card")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = if (localModelsCount == 0) {
                    "No local .tflite models imported yet. Import from device storage or fetch via HTTPS."
                } else {
                    "$localModelsCount local .tflite model(s) ready to verify on GPU/XNNPACK CPU or bundle in APKs."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = { importLauncher.launch(arrayOf("*/*")) },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("hub_import_tflite_button")
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Import .tflite")
                }

                OutlinedButton(
                    onClick = {
                        urlInput = ""
                        customNameInput = ""
                        showUrlDialog = true
                    },
                    enabled = !downloadState.isDownloading,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("hub_fetch_tflite_url_button")
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Fetch URL")
                }
            }

            if (downloadState.isDownloading) {
                val total = downloadState.totalBytes
                val downloaded = downloadState.bytesDownloaded
                Text(
                    text = "Downloading ${downloadState.fileName} • ${TfliteModelManager.formatBytes(downloaded)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
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

            statusMessage?.let { msg ->
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (isStatusError) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    },
                    modifier = Modifier.fillMaxWidth()
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
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HubTfliteModelListItemCard(
    model: LocalTfliteModel,
    benchmark: TfliteBenchmarkResult?,
    isTesting: Boolean,
    onBenchmark: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("hub_tflite_item_${model.fileName}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = model.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = model.fileName,
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
                    .testTag("hub_benchmark_tflite_${model.fileName}")
            ) {
                if (isTesting) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
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
                    .testTag("hub_delete_tflite_${model.fileName}")
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LocalLlmQuickPromptDialog(
    installedFiles: Set<String>,
    selectedModelFile: String?,
    llmState: LocalLlmState,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val defaultFile = remember(installedFiles, selectedModelFile) {
        selectedModelFile?.takeIf { it in installedFiles } ?: installedFiles.firstOrNull().orEmpty()
    }
    var chosenFile by remember(defaultFile) { mutableStateOf(defaultFile) }
    var backendChoice by remember { mutableStateOf(LlmBackendChoice.AUTO_GPU_FALLBACK) }
    var promptInput by remember {
        mutableStateOf("Suggest 3 focused screens for an offline fitness timer app.")
    }
    var streamedOutput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!llmState.isLoading && !llmState.isBusy) onDismiss() },
        title = { Text("On-Device LiteRT-LM Console") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Engine: ${llmState.activeBackend}" +
                        (llmState.loadedModelFile?.let { " • $it" }.orEmpty()) +
                        (llmState.lastLatencyMs?.let { " • ${it}ms" }.orEmpty()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                if (installedFiles.size > 1) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        installedFiles.forEach { fileName ->
                            FilterChip(
                                selected = chosenFile == fileName,
                                onClick = { chosenFile = fileName },
                                enabled = !llmState.isLoading && !llmState.isBusy,
                                label = { Text(fileName, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            )
                        }
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LlmBackendChoice.entries.forEach { choice ->
                        FilterChip(
                            selected = backendChoice == choice,
                            onClick = { backendChoice = choice },
                            enabled = !llmState.isLoading && !llmState.isBusy,
                            label = { Text(choice.label) }
                        )
                    }
                }

                OutlinedTextField(
                    value = promptInput,
                    onValueChange = { promptInput = it },
                    label = { Text("Test Prompt") },
                    minLines = 2,
                    enabled = !llmState.isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("local_llm_test_prompt_field")
                )

                llmState.errorMessage?.let { err ->
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                val displayOutput = streamedOutput.ifBlank { llmState.lastOutputPreview }
                if (displayOutput.isNotBlank()) {
                    HorizontalDivider()
                    Text(
                        text = displayOutput,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (chosenFile.isNotBlank() && promptInput.isNotBlank()) {
                        streamedOutput = ""
                        coroutineScope.launch {
                            LocalLlmEngine.generateText(
                                context = context,
                                modelFileName = chosenFile,
                                prompt = promptInput.trim(),
                                backendChoice = backendChoice,
                                onStreamChunk = { chunk -> streamedOutput += chunk }
                            )
                        }
                    }
                },
                enabled = chosenFile.isNotBlank() && promptInput.isNotBlank() && !llmState.isLoading && !llmState.isBusy,
                modifier = Modifier.testTag("local_llm_run_prompt_button")
            ) {
                Text(if (llmState.isLoading || llmState.isBusy) "Running..." else "Run Prompt")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !llmState.isLoading && !llmState.isBusy
            ) {
                Text("Close")
            }
        }
    )
}
