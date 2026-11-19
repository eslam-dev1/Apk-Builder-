package com.example.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
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
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
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
import com.example.model.AppProject
import kotlinx.coroutines.launch

enum class LocalAiManagerFilter(val label: String, val tag: String) {
    ALL("All", "manager_filter_all"),
    ENABLED("Enabled", "manager_filter_enabled"),
    VISION("Vision", "manager_filter_vision"),
    AUDIO("Audio", "manager_filter_audio"),
    TEXT("Text / NLP", "manager_filter_text"),
    CUSTOM("Custom", "manager_filter_custom")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LocalAiManagerScreen(
    project: AppProject? = null,
    availableProjects: List<AppProject> = emptyList(),
    enabledModelFileNames: List<String>? = null,
    onSelectProject: (String) -> Unit = {},
    onUpdateEnabledModels: (projectId: String?, enabledModels: List<String>) -> Unit = { _, _ -> },
    onOpenBuildConfig: ((String) -> Unit)? = null,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val localModels by TfliteModelManager.localModels.collectAsStateWithLifecycle()
    val downloadState by TfliteModelManager.downloadState.collectAsStateWithLifecycle()
    val defaultBundledModels by TfliteModelManager.defaultBundledModels.collectAsStateWithLifecycle()
    val benchmarks by LiteRtRuntimeManager.benchmarks.collectAsStateWithLifecycle()
    val testingFile by LiteRtRuntimeManager.testingFile.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        TfliteModelManager.refreshModels(context)
        TfliteModelManager.loadDefaultBundledModels(context)
    }

    val activeEnabledModels = remember(enabledModelFileNames, project, defaultBundledModels) {
        (enabledModelFileNames
            ?: project?.buildOptions?.bundledTfliteModels
            ?: defaultBundledModels).distinct()
    }

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf(LocalAiManagerFilter.ALL) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isStatusError by remember { mutableStateOf(false) }
    var showUrlDownloadDialog by remember { mutableStateOf(false) }
    var urlInput by remember { mutableStateOf("") }
    var customFileNameInput by remember { mutableStateOf("") }

    fun updateEnabledModels(updated: List<String>) {
        val normalized = updated.distinct()
        if (project == null && enabledModelFileNames == null) {
            TfliteModelManager.setDefaultBundledModels(context, normalized)
        }
        onUpdateEnabledModels(project?.id, normalized)
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val res = TfliteModelManager.importTfliteFromUri(context, uri)
                res.onSuccess { imported ->
                    isStatusError = false
                    statusMessage = "Imported ${imported.fileName} (${TfliteModelManager.formatBytes(imported.sizeBytes)}) and enabled for build."
                    updateEnabledModels(activeEnabledModels + imported.fileName)
                    Toast.makeText(context, "Enabled ${imported.fileName} for build", Toast.LENGTH_SHORT).show()
                }.onFailure { err ->
                    isStatusError = true
                    statusMessage = err.localizedMessage ?: "Failed to import .tflite file"
                }
            }
        }
    }

    val availableByName = remember(localModels) { localModels.associateBy { it.fileName } }
    val missingEnabledModels = remember(activeEnabledModels, availableByName) {
        activeEnabledModels.filter { it !in availableByName }
    }
    val enabledExistingModels = remember(localModels, activeEnabledModels) {
        localModels.filter { it.fileName in activeEnabledModels }
    }
    val enabledPayloadBytes = remember(enabledExistingModels) {
        enabledExistingModels.sumOf { it.sizeBytes }
    }

    val filteredModels = remember(localModels, activeEnabledModels, selectedFilter, searchQuery) {
        val query = searchQuery.trim().lowercase()
        localModels.filter { model ->
            val matchesFilter = when (selectedFilter) {
                LocalAiManagerFilter.ALL -> true
                LocalAiManagerFilter.ENABLED -> model.fileName in activeEnabledModels
                LocalAiManagerFilter.VISION -> model.taskCategory == "Vision"
                LocalAiManagerFilter.AUDIO -> model.taskCategory == "Audio"
                LocalAiManagerFilter.TEXT -> model.taskCategory == "Text / NLP"
                LocalAiManagerFilter.CUSTOM -> model.taskCategory == "Custom TFLite"
            }
            val matchesSearch = if (query.isEmpty()) {
                true
            } else {
                val purpose = TfliteModelManager.inferModelPurpose(model.fileName)
                model.displayName.lowercase().contains(query) ||
                    model.fileName.lowercase().contains(query) ||
                    model.taskCategory.lowercase().contains(query) ||
                    purpose.lowercase().contains(query)
            }
            matchesFilter && matchesSearch
        }
    }

    if (showUrlDownloadDialog) {
        AlertDialog(
            onDismissRequest = { if (!downloadState.isDownloading) showUrlDownloadDialog = false },
            title = { Text("Fetch .tflite Model via HTTPS") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Download a TensorFlow Lite (.tflite) model directly to on-device storage and toggle it for APK bundling.",
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
                            .testTag("manager_url_input")
                    )
                    OutlinedTextField(
                        value = customFileNameInput,
                        onValueChange = { customFileNameInput = it },
                        label = { Text("File Name (Optional)") },
                        placeholder = { Text("mobilenet_v2.tflite") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("manager_filename_input")
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
                            val res = TfliteModelManager.downloadTfliteFromUrl(context, url, customName)
                            res.onSuccess { dl ->
                                isStatusError = false
                                statusMessage = "Downloaded ${dl.fileName} (${TfliteModelManager.formatBytes(dl.sizeBytes)}) and enabled for build."
                                updateEnabledModels(activeEnabledModels + dl.fileName)
                            }.onFailure { err ->
                                isStatusError = true
                                statusMessage = err.localizedMessage ?: "Download failed"
                            }
                        }
                    },
                    enabled = urlInput.trim().startsWith("https://", ignoreCase = true),
                    modifier = Modifier.testTag("manager_confirm_url_download")
                ) {
                    Text("Download")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUrlDownloadDialog = false }) {
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
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Top Header
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
                            .testTag("local_ai_manager_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                    Column {
                        Text(
                            text = "Local AI Manager",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("local_ai_manager_title")
                        )
                        Text(
                            text = project?.let { "Target build: ${it.name}" }
                                ?: "Default TFLite APK bundle profile",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("local_ai_manager_target_subtitle")
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
                        .testTag("local_ai_manager_refresh_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh local TFLite models"
                    )
                }
            }

            // Summary & Controls Hero Card
            ElevatedCard(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("local_ai_manager_summary_card")
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                                    MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)
                                )
                            )
                        )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${enabledExistingModels.size} / ${localModels.size} Enabled for Build",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.testTag("manager_enabled_count_text")
                                )
                                Text(
                                    text = "Bundled APK payload: ${TfliteModelManager.formatBytes(enabledPayloadBytes)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.testTag("manager_payload_size_text")
                                )
                            }

                            if (onOpenBuildConfig != null && project != null) {
                                FilledTonalButton(
                                    onClick = { onOpenBuildConfig(project.id) },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("manager_open_build_config_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Build,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Build Config")
                                }
                            }
                        }

                        if (availableProjects.size > 1) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Project:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                availableProjects.forEach { proj ->
                                    FilterChip(
                                        selected = project?.id == proj.id,
                                        onClick = { onSelectProject(proj.id) },
                                        label = {
                                            Text(
                                                text = proj.name,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        },
                                        modifier = Modifier.testTag("manager_project_chip_${proj.id}")
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledTonalButton(
                                onClick = { importLauncher.launch(arrayOf("*/*")) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("manager_import_tflite_button")
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
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("manager_fetch_url_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudDownload,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Fetch URL")
                            }

                            if (localModels.isNotEmpty() && enabledExistingModels.size < localModels.size) {
                                TextButton(
                                    onClick = { updateEnabledModels(localModels.map { it.fileName }) },
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("manager_enable_all_button")
                                ) {
                                    Text("Enable All")
                                }
                            }
                            if (activeEnabledModels.isNotEmpty()) {
                                TextButton(
                                    onClick = { updateEnabledModels(emptyList()) },
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("manager_disable_all_button")
                                ) {
                                    Text("Disable All")
                                }
                            }
                        }
                    }
                }
            }

            // Active URL download progress
            AnimatedVisibility(
                visible = downloadState.isDownloading,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val total = downloadState.totalBytes
                        val downloaded = downloadState.bytesDownloaded
                        Text(
                            text = "Downloading ${downloadState.fileName} • ${TfliteModelManager.formatBytes(downloaded)}",
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

            statusMessage?.let { msg ->
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (isStatusError) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("manager_status_banner")
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

            // Category & Enabled Filters
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .testTag("manager_filter_row"),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LocalAiManagerFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter.label) },
                        modifier = Modifier.testTag(filter.tag)
                    )
                }
            }

            // Models List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("local_ai_manager_list"),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (localModels.isNotEmpty()) {
                    item(key = "manager_search_item") {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            label = { Text("Search models by name, task or purpose") },
                            leadingIcon = {
                                Icon(imageVector = Icons.Default.Search, contentDescription = "Search models")
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear search")
                                    }
                                }
                            },
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("manager_search_field")
                        )
                    }
                }
                if (missingEnabledModels.isNotEmpty()) {
                    item(key = "missing_models_warning") {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("manager_missing_models_warning")
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
                                        text = "Enabled model(s) not found in local storage:",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                                missingEnabledModels.forEach { missingName ->
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
                                                updateEnabledModels(activeEnabledModels.filterNot { it == missingName })
                                            },
                                            modifier = Modifier.testTag("manager_remove_missing_$missingName")
                                        ) {
                                            Text("Disable")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (localModels.isEmpty()) {
                    item(key = "empty_state") {
                        Card(
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("local_ai_manager_empty_state")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Memory,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "No TFLite Models Available Yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Import a .tflite FlatBuffer file from device storage or fetch one via HTTPS. Enabled models are packaged uncompressed into assets/models/ inside your generated APK with a matching LocalTfliteRunner helper.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { importLauncher.launch(arrayOf("*/*")) },
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) {
                                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Import .tflite")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            urlInput = ""
                                            customFileNameInput = ""
                                            showUrlDownloadDialog = true
                                        },
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) {
                                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Fetch URL")
                                    }
                                }
                            }
                        }
                    }
                } else if (filteredModels.isEmpty()) {
                    item(key = "no_filter_matches") {
                        Card(
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("local_ai_manager_no_matches")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "No models match your current filter",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                OutlinedButton(
                                    onClick = {
                                        searchQuery = ""
                                        selectedFilter = LocalAiManagerFilter.ALL
                                    },
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("manager_reset_filters_button")
                                ) {
                                    Text("Reset Filters")
                                }
                            }
                        }
                    }
                } else {
                    items(filteredModels, key = { it.id }) { model ->
                        val isEnabled = model.fileName in activeEnabledModels
                        LocalAiManagerModelCard(
                            model = model,
                            isEnabled = isEnabled,
                            benchmark = benchmarks[model.fileName],
                            isTesting = testingFile == model.fileName,
                            onToggleEnabled = { checked ->
                                val next = if (checked) {
                                    (activeEnabledModels + model.fileName).distinct()
                                } else {
                                    activeEnabledModels.filterNot { it == model.fileName }
                                }
                                updateEnabledModels(next)
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
                                if (model.fileName in activeEnabledModels) {
                                    updateEnabledModels(activeEnabledModels.filterNot { it == model.fileName })
                                }
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
private fun LocalAiManagerModelCard(
    model: LocalTfliteModel,
    isEnabled: Boolean,
    benchmark: TfliteBenchmarkResult?,
    isTesting: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onBenchmark: () -> Unit,
    onDelete: () -> Unit
) {
    val taskIcon = when (model.taskCategory) {
        "Vision" -> Icons.Default.Image
        "Audio" -> Icons.Default.GraphicEq
        "Text / NLP" -> Icons.Default.TextFields
        else -> Icons.Default.Memory
    }
    val purpose = remember(model.fileName) { TfliteModelManager.inferModelPurpose(model.fileName) }
    val sizeText = remember(model.sizeBytes) { TfliteModelManager.formatBytes(model.sizeBytes) }
    val bundlePath = remember(model.fileName) { TfliteModelManager.bundledAssetPath(model.fileName) }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (isEnabled) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            width = if (isEnabled) 1.5.dp else 1.dp,
            color = if (isEnabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("local_ai_model_card_${model.fileName}")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Top Row: Icon + Name + Bundle Path + Enable/Disable Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isEnabled) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = taskIcon,
                                contentDescription = null,
                                tint = if (isEnabled) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = model.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = bundlePath,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(horizontalAlignment = Alignment.End) {
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = onToggleEnabled,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("manager_toggle_${model.fileName}")
                    )
                    Text(
                        text = if (isEnabled) "Bundled" else "Disabled",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.testTag("manager_status_label_${model.fileName}")
                    )
                }
            }

            // Explicit Purpose Metadata
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Purpose",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = purpose,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("manager_purpose_${model.fileName}")
                    )
                }
            }

            // Metadata Badges (Size, Category, TFL3 Header, LiteRT Benchmark)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = "Size: $sizeText",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                            .testTag("manager_size_${model.fileName}")
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = model.taskCategory,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = if (model.hasValidFlatBufferHeader) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (model.hasValidFlatBufferHeader) Icons.Default.Verified else Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = if (model.hasValidFlatBufferHeader) {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer
                            },
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = if (model.hasValidFlatBufferHeader) "TFL3 Valid" else "Unverified Header",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (model.hasValidFlatBufferHeader) {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer
                            }
                        )
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
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (benchmark.isVerified) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                            Text(
                                text = if (benchmark.isVerified) {
                                    "${benchmark.acceleratorUsed} • ${benchmark.inferenceMs}ms (${benchmark.inputTensorCount}→${benchmark.outputTensorCount})"
                                } else {
                                    "Graph Failed"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (benchmark.isVerified) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onErrorContainer
                                }
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

            // Footer Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onBenchmark,
                    enabled = !isTesting,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("manager_benchmark_${model.fileName}")
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Verifying...")
                    } else {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Verify & Benchmark")
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("manager_delete_${model.fileName}")
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
}
