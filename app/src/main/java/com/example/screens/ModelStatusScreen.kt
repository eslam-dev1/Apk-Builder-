package com.example.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.StatFs
import android.view.WindowManager
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LowPriority
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Queue
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VerticalAlignTop
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai.DownloadStatus
import com.example.ai.GlobalDownloadProgress
import com.example.ai.LiteRtRuntimeManager
import com.example.ai.LocalAiCapabilitiesList
import com.example.ai.LocalAiCategory
import com.example.ai.LocalLlmEngine
import com.example.ai.ModelDownloadProgress
import com.example.ai.ModelManager
import com.example.ai.PreflightCheckResult
import com.example.ai.QueuedDownloadItem
import com.example.ai.TfliteModelManager
import com.example.data.ai.LocalModel
import com.example.data.ai.ModelCatalog
import java.util.Locale
import kotlinx.coroutines.launch

enum class OfflineModelStateLabel(val label: String) {
    READY("Ready"),
    DOWNLOADED("Downloaded"),
    DOWNLOADING("Downloading")
}

@Composable
fun ModelStatusScreen(
    onBack: () -> Unit,
    onOpenCreate: (() -> Unit)? = null,
    onOpenLocalAiManager: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()

    val downloadProgress by ModelManager.downloadProgress.collectAsStateWithLifecycle()
    val downloadQueue by ModelManager.downloadQueue.collectAsStateWithLifecycle()
    val globalProgress by ModelManager.globalProgress.collectAsStateWithLifecycle()
    val installedFiles by ModelManager.installedFiles.collectAsStateWithLifecycle()
    val selectedModelFile by ModelManager.selectedModelFile.collectAsStateWithLifecycle()
    val localLlmState by LocalLlmEngine.state.collectAsStateWithLifecycle()
    val localTfliteModels by TfliteModelManager.localModels.collectAsStateWithLifecycle()
    val tfliteDownloadState by TfliteModelManager.downloadState.collectAsStateWithLifecycle()
    val tfliteBenchmarks by LiteRtRuntimeManager.benchmarks.collectAsStateWithLifecycle()
    val testingTfliteFile by LiteRtRuntimeManager.testingFile.collectAsStateWithLifecycle()

    var selectedFilterTab by remember { mutableStateOf(LocalAiFilterTab.ALL) }
    var showLlmTesterDialog by remember { mutableStateOf(false) }
    var tfliteStatusMessage by remember { mutableStateOf<String?>(null) }
    var isTfliteStatusError by remember { mutableStateOf(false) }

    var availableBytes by remember { mutableLongStateOf(0L) }
    var totalBytes by remember { mutableLongStateOf(0L) }
    var modelsDirUsedBytes by remember { mutableLongStateOf(0L) }
    var pendingPreflight by remember { mutableStateOf<PreflightCheckResult?>(null) }
    var pendingBatchQueueModels by remember { mutableStateOf<List<LocalModel>?>(null) }
    var statusBannerMessage by remember { mutableStateOf<String?>(null) }
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

    fun refreshStorageMetrics() {
        ModelManager.refreshState(context)
        TfliteModelManager.refreshModels(context)
        val dir = ModelCatalog.modelsDir(context)
        runCatching {
            val stat = StatFs(dir.absolutePath)
            availableBytes = stat.availableBytes
            totalBytes = stat.totalBytes
        }
        modelsDirUsedBytes = dir.listFiles()
            ?.filter { it.isFile }
            ?.sumOf { it.length() } ?: 0L
    }

    LaunchedEffect(installedFiles, downloadProgress.status, downloadProgress.bytesDownloaded, downloadQueue.size) {
        refreshStorageMetrics()
    }

    val isActivelyDownloading = globalProgress.isDownloading ||
        downloadProgress.status == DownloadStatus.DOWNLOADING ||
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

    val allModels = remember(installedFiles) {
        val catalogFileNames = ModelCatalog.ALL_MODELS.map { it.fileName }.toSet()
        val extraModels = installedFiles
            .filter { it !in catalogFileNames }
            .map { fileName ->
                LocalModel(
                    id = "imported_${fileName.lowercase(Locale.US)}",
                    title = "Imported Model ($fileName)",
                    fileName = fileName,
                    url = "",
                    expectedBytes = null,
                    sha256 = null
                )
            }
        ModelCatalog.ALL_MODELS + extraModels
    }

    val queuedIds = remember(downloadQueue) { downloadQueue.map { it.model.id }.toSet() }
    val unqueuedMissingModels = remember(allModels, installedFiles, queuedIds) {
        allModels.filter { model ->
            model.url.isNotBlank() && model.fileName !in installedFiles && model.id !in queuedIds
        }
    }

    pendingPreflight?.let { preflight ->
        val willQueueBehindActive = downloadQueue.isNotEmpty()
        AlertDialog(
            onDismissRequest = { pendingPreflight = null },
            title = {
                Text(if (willQueueBehindActive) "Queue Model Download" else "Confirm Model Download")
            },
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
                        fontWeight = FontWeight.SemiBold
                    )
                    if (willQueueBehindActive) {
                        Text(
                            text = "This model will be added at priority #${downloadQueue.size + 1} in the download queue.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Text(
                        text = "Available storage: ${ModelCatalog.formatBytes(preflight.freeSpaceBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = preflight.model
                        pendingPreflight = null
                        runWithNotificationPermission {
                            ModelManager.enqueueDownload(context, target)
                        }
                    },
                    modifier = Modifier.testTag("model_status_confirm_download_button")
                ) {
                    Text(if (willQueueBehindActive) "Add to Queue" else "Start Download")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingPreflight = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    pendingBatchQueueModels?.let { modelsToQueue ->
        AlertDialog(
            onDismissRequest = { pendingBatchQueueModels = null },
            title = { Text("Queue ${modelsToQueue.size} Offline Models") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "The following models will be queued in priority order (use Wi-Fi):",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    modelsToQueue.forEachIndexed { idx, model ->
                        Text(
                            text = "${idx + 1}. ${model.title} (${ModelCatalog.sizeDisplayLabel(model)})",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(
                        text = "Available storage: ${ModelCatalog.formatBytes(availableBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val batch = modelsToQueue
                        pendingBatchQueueModels = null
                        runWithNotificationPermission {
                            ModelManager.enqueueAll(context, batch)
                        }
                    },
                    modifier = Modifier.testTag("confirm_queue_all_button")
                ) {
                    Text("Queue All")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingBatchQueueModels = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    val verifiedTfliteCount = remember(localTfliteModels, tfliteBenchmarks) {
        localTfliteModels.count { tfliteBenchmarks[it.fileName]?.isVerified == true }
    }

    val allCapabilities = remember(
        installedFiles,
        selectedModelFile,
        localLlmState,
        localTfliteModels.size,
        verifiedTfliteCount
    ) {
        LocalAiCapabilitiesList.buildCapabilityList(
            context = context,
            installedLlmFiles = installedFiles,
            selectedLlmFile = selectedModelFile,
            llmState = localLlmState,
            tfliteModelsCount = localTfliteModels.size,
            verifiedTfliteCount = verifiedTfliteCount
        )
    }

    val filteredCapabilities = remember(allCapabilities, selectedFilterTab) {
        when (selectedFilterTab) {
            LocalAiFilterTab.ALL -> allCapabilities
            LocalAiFilterTab.DEVICE_TOOLS -> allCapabilities.filter { it.category == LocalAiCategory.DEVICE_TOOLS }
            LocalAiFilterTab.ON_DEVICE_LLMS -> allCapabilities.filter { it.category == LocalAiCategory.LLM_ENGINE }
            LocalAiFilterTab.TFLITE_MODELS -> allCapabilities.filter { it.category == LocalAiCategory.TFLITE_RUNTIME }
        }
    }

    if (showLlmTesterDialog) {
        LocalLlmQuickPromptDialog(
            installedFiles = installedFiles,
            selectedModelFile = selectedModelFile,
            llmState = localLlmState,
            onDismiss = { showLlmTesterDialog = false }
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
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row with Title, Refresh & Back
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
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "Local AI Hub & Models",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("model_status_title")
                        )
                        Text(
                            text = "On-device LLMs, TFLite runtime, OCR, voice & RAG tools",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { refreshStorageMetrics() },
                        modifier = Modifier.testTag("model_status_refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh storage status"
                        )
                    }
                    TextButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("model_status_back_button")
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
            }

            // Global Download Progress Indicator in Header
            GlobalDownloadHeaderCard(
                globalProgress = globalProgress,
                downloadQueue = downloadQueue,
                unqueuedMissingCount = unqueuedMissingModels.size,
                onPauseAll = { ModelManager.pauseDownload() },
                onResumeAll = { ModelManager.resumeQueue(context) },
                onClearQueue = {
                    ModelManager.clearQueue(context)
                    refreshStorageMetrics()
                },
                onQueueAllMissing = {
                    statusBannerMessage = null
                    pendingBatchQueueModels = unqueuedMissingModels
                }
            )

            statusBannerMessage?.let { msg ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            // Filter Chips to arrange Local AI capabilities and models cleanly
            LocalAiFilterChipRow(
                selectedTab = selectedFilterTab,
                onSelectTab = { selectedFilterTab = it }
            )

            if (onOpenLocalAiManager != null) {
                FilledTonalButton(
                    onClick = onOpenLocalAiManager,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("model_status_open_local_ai_manager_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open Local AI Manager (.tflite Build Bundling)")
                }
            }

            // Scrollable Arranged List: Storage + Capabilities + Priority Queue + LLM Catalog + TFLite Catalog
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("model_status_lazy_column"),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Available Storage Space Card
                item(key = "storage_overview") {
                    StorageSpaceOverviewCard(
                        availableBytes = availableBytes,
                        totalBytes = totalBytes,
                        modelsDirUsedBytes = modelsDirUsedBytes
                    )
                }

                // Status Summary Legend Row (Ready, Downloaded, Downloading)
                item(key = "status_summary") {
                    StatusSummaryRow(
                        models = allModels,
                        installedFiles = installedFiles,
                        selectedModelFile = selectedModelFile,
                        downloadProgress = downloadProgress,
                        downloadQueue = downloadQueue,
                        context = context
                    )
                }

                // Priority-Based Download Queue Section
                if (downloadQueue.isNotEmpty()) {
                    item(key = "priority_queue_section") {
                        PriorityQueueCard(
                            queueItems = downloadQueue,
                            onMoveUp = { modelId -> ModelManager.moveQueueItemUp(modelId) },
                            onMoveDown = { modelId -> ModelManager.moveQueueItemDown(modelId) },
                            onPromoteTop = { modelId -> ModelManager.promoteToTop(context, modelId) },
                            onRemove = { modelId ->
                                ModelManager.removeFromQueue(context, modelId, deletePartial = false)
                                refreshStorageMetrics()
                            }
                        )
                    }
                }

                // Section 1: Arranged Local AI Engines & Tools List
                if (filteredCapabilities.isNotEmpty()) {
                    item(key = "local_ai_capabilities_header") {
                        Text(
                            text = "Local AI Engines & Tools (${filteredCapabilities.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .testTag("local_ai_capabilities_header")
                        )
                    }

                    items(filteredCapabilities, key = { it.id }) { capability ->
                        LocalAiCapabilityListCard(
                            capability = capability,
                            llmState = localLlmState,
                            hasInstalledLlm = installedFiles.isNotEmpty(),
                            onOpenLlmTester = { showLlmTesterDialog = true },
                            onUnloadLlm = {
                                coroutineScope.launch { LocalLlmEngine.unload() }
                            },
                            onViewTfliteTab = { selectedFilterTab = LocalAiFilterTab.TFLITE_MODELS },
                            onViewLlmTab = { selectedFilterTab = LocalAiFilterTab.ON_DEVICE_LLMS },
                            onOpenCreate = onOpenCreate
                        )
                    }
                }

                // Section 2: On-Device .litertlm Catalog
                if (selectedFilterTab == LocalAiFilterTab.ALL || selectedFilterTab == LocalAiFilterTab.ON_DEVICE_LLMS) {
                    item(key = "catalog_header") {
                        Text(
                            text = "On-Device LLM Models (.litertlm) (${allModels.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .testTag("llm_catalog_header")
                        )
                    }

                    items(allModels, key = { it.id }) { model ->
                        val isInstalled = model.fileName in installedFiles
                        val isSelectedReady = isInstalled && (selectedModelFile == model.fileName)
                        val partialBytes = remember(downloadProgress, installedFiles, downloadQueue) {
                            ModelManager.hasPartialDownload(context, model.fileName)
                        }
                        val activeModelProgress = downloadProgress.takeIf {
                            it.modelId == model.id && it.status != DownloadStatus.IDLE
                        }
                        val queueEntry = downloadQueue.find { it.model.id == model.id }

                        val currentStateLabel = resolveModelStateLabel(
                            isInstalled = isInstalled,
                            isSelectedReady = isSelectedReady,
                            partialBytes = partialBytes,
                            progress = activeModelProgress
                        )

                        val onDiskBytes = remember(installedFiles, partialBytes, activeModelProgress?.bytesDownloaded) {
                            val finalFile = ModelCatalog.modelFile(context, model.fileName)
                            when {
                                finalFile.exists() -> finalFile.length()
                                activeModelProgress != null && activeModelProgress.bytesDownloaded > 0L ->
                                    activeModelProgress.bytesDownloaded
                                else -> partialBytes
                            }
                        }

                        ModelStatusListItemCard(
                            model = model,
                            stateLabel = currentStateLabel,
                            isInstalled = isInstalled,
                            isSelectedReady = isSelectedReady,
                            queueEntry = queueEntry,
                            hasOtherActiveOrQueued = downloadQueue.isNotEmpty() && queueEntry == null,
                            onDiskBytes = onDiskBytes,
                            progress = activeModelProgress,
                            onDownloadOrQueue = {
                                statusBannerMessage = null
                                coroutineScope.launch {
                                    val check = ModelManager.preflightCheck(context, model)
                                    if (check.errorMessage != null) {
                                        statusBannerMessage = check.errorMessage
                                    } else {
                                        pendingPreflight = check
                                    }
                                }
                            },
                            onPause = { ModelManager.pauseDownload() },
                            onCancel = {
                                ModelManager.cancelDownload(context, model)
                                refreshStorageMetrics()
                            },
                            onDelete = {
                                ModelManager.deleteModel(context, model)
                                refreshStorageMetrics()
                            },
                            onSelectReady = {
                                ModelManager.selectModel(context, model.id, model.fileName)
                            }
                        )
                    }
                }

                // Section 3: Local .tflite Models List
                if (selectedFilterTab == LocalAiFilterTab.ALL || selectedFilterTab == LocalAiFilterTab.TFLITE_MODELS) {
                    item(key = "tflite_hub_header") {
                        Text(
                            text = "Local TFLite Models (.tflite) (${localTfliteModels.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .testTag("tflite_catalog_header")
                        )
                    }

                    item(key = "tflite_hub_toolbar") {
                        TfliteHubToolbarCard(
                            localModelsCount = localTfliteModels.size,
                            downloadState = tfliteDownloadState,
                            statusMessage = tfliteStatusMessage,
                            isStatusError = isTfliteStatusError,
                            onStatusUpdate = { msg, err ->
                                tfliteStatusMessage = msg
                                isTfliteStatusError = err
                            }
                        )
                    }

                    items(localTfliteModels, key = { "hub_${it.id}" }) { tfliteModel ->
                        HubTfliteModelListItemCard(
                            model = tfliteModel,
                            benchmark = tfliteBenchmarks[tfliteModel.fileName],
                            isTesting = testingTfliteFile == tfliteModel.fileName,
                            onBenchmark = {
                                coroutineScope.launch {
                                    val res = LiteRtRuntimeManager.inspectAndBenchmarkModel(
                                        context = context,
                                        fileName = tfliteModel.fileName
                                    )
                                    isTfliteStatusError = !res.isVerified
                                    tfliteStatusMessage = if (res.isVerified) {
                                        "${tfliteModel.fileName}: ${res.statusNote}"
                                    } else {
                                        "${tfliteModel.fileName}: ${res.errorMessage ?: res.statusNote}"
                                    }
                                }
                            },
                            onDelete = {
                                TfliteModelManager.deleteModel(context, tfliteModel.fileName)
                                LiteRtRuntimeManager.clearResult(tfliteModel.fileName)
                                isTfliteStatusError = false
                                tfliteStatusMessage = "Removed ${tfliteModel.fileName}"
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GlobalDownloadHeaderCard(
    globalProgress: GlobalDownloadProgress,
    downloadQueue: List<QueuedDownloadItem>,
    unqueuedMissingCount: Int,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    onClearQueue: () -> Unit,
    onQueueAllMissing: () -> Unit
) {
    val hasActiveQueue = downloadQueue.isNotEmpty()
    val pct = (globalProgress.fraction * 100).toInt()

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (hasActiveQueue) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            }
        ),
        border = BorderStroke(
            1.dp,
            if (hasActiveQueue) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("global_download_progress_header")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                        imageVector = if (hasActiveQueue) Icons.Default.CloudDownload else Icons.Default.Queue,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = when {
                                globalProgress.isDownloading ->
                                    "Global Download ($pct%) • ${downloadQueue.size} in queue"
                                globalProgress.isPaused ->
                                    "Download Queue Paused ($pct%) • ${downloadQueue.size} in queue"
                                else -> "Global Download Queue Idle"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("global_progress_title")
                        )
                        val subtitle = when {
                            globalProgress.activeModelTitle != null -> {
                                val speedMb = globalProgress.speedBytesPerSec.toDouble() / (1024.0 * 1024.0)
                                val speedTxt = if (globalProgress.speedBytesPerSec > 0L) {
                                    String.format(Locale.US, " • %.2f MB/s", speedMb)
                                } else {
                                    ""
                                }
                                val etaTxt = globalProgress.etaSeconds?.let { sec ->
                                    if (sec >= 60) " • ETA ${sec / 60}m ${sec % 60}s" else " • ETA ${sec}s"
                                }.orEmpty()
                                "Active: ${globalProgress.activeModelTitle} (${
                                    ModelCatalog.formatBytes(globalProgress.totalBytesDownloaded)
                                } / ${
                                    ModelCatalog.formatBytes(globalProgress.totalBytesOverall)
                                })$speedTxt$etaTxt"
                            }
                            unqueuedMissingCount > 0 ->
                                "$unqueuedMissingCount offline model(s) ready to queue"
                            else -> "All catalog models are downloaded on device"
                        }
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("global_progress_subtitle")
                        )
                    }
                }

                if (!hasActiveQueue && unqueuedMissingCount > 0) {
                    FilledTonalButton(
                        onClick = onQueueAllMissing,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("queue_all_models_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Queue,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Queue All ($unqueuedMissingCount)")
                    }
                }
            }

            if (hasActiveQueue) {
                LinearProgressIndicator(
                    progress = { globalProgress.fraction },
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .testTag("global_progress_bar")
                )

                if (globalProgress.isDownloading) {
                    Text(
                        text = "Background foreground service active — downloads continue if you switch apps",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("background_service_active_text")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (globalProgress.isDownloading) {
                        FilledTonalButton(
                            onClick = onPauseAll,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("global_pause_button")
                        ) {
                            Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Pause Queue")
                        }
                    } else if (globalProgress.isPaused) {
                        Button(
                            onClick = onResumeAll,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("global_resume_button")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Resume Queue")
                        }
                    }

                    OutlinedButton(
                        onClick = onClearQueue,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("global_clear_queue_button")
                    ) {
                        Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear Queue")
                    }

                    if (unqueuedMissingCount > 0) {
                        TextButton(
                            onClick = onQueueAllMissing,
                            modifier = Modifier.testTag("queue_remaining_models_button")
                        ) {
                            Text("+Queue All ($unqueuedMissingCount)")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PriorityQueueCard(
    queueItems: List<QueuedDownloadItem>,
    onMoveUp: (String) -> Unit,
    onMoveDown: (String) -> Unit,
    onPromoteTop: (String) -> Unit,
    onRemove: (String) -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("priority_queue_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LowPriority,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = "Download Priority Queue (${queueItems.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Use arrows to reorder download priority (#1 downloads first)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            queueItems.forEachIndexed { index, item ->
                val isFirst = index == 0
                val isLast = index == queueItems.lastIndex
                val totalForItem = item.totalBytes
                val itemPct = if (totalForItem != null && totalForItem > 0L) {
                    ((item.bytesDownloaded.toFloat() / totalForItem.toFloat()) * 100).toInt().coerceIn(0, 100)
                } else {
                    0
                }

                Surface(
                    color = if (item.isActive) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    },
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(
                        1.dp,
                        if (item.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("queue_item_${item.model.id}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                color = if (isFirst) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "#${item.priority}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isFirst) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.model.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val statusText = when {
                                    item.isActive -> "Downloading ($itemPct%)"
                                    item.isPaused -> "Paused ($itemPct%)"
                                    item.bytesDownloaded > 0L -> "Waiting ($itemPct% saved)"
                                    else -> "Waiting in queue"
                                }
                                Text(
                                    text = "$statusText • ${ModelCatalog.formatBytes(item.bytesDownloaded)} / ${
                                        ModelCatalog.sizeDisplayLabel(item.model, totalForItem)
                                    }",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (index > 1) {
                                IconButton(
                                    onClick = { onPromoteTop(item.model.id) },
                                    modifier = Modifier.testTag("queue_promote_top_${item.model.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.VerticalAlignTop,
                                        contentDescription = "Download ${item.model.title} next",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            IconButton(
                                onClick = { onMoveUp(item.model.id) },
                                enabled = !isFirst,
                                modifier = Modifier.testTag("queue_move_up_${item.model.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowUpward,
                                    contentDescription = "Move ${item.model.title} up in priority",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = { onMoveDown(item.model.id) },
                                enabled = !isLast,
                                modifier = Modifier.testTag("queue_move_down_${item.model.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDownward,
                                    contentDescription = "Move ${item.model.title} down in priority",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = { onRemove(item.model.id) },
                                modifier = Modifier.testTag("queue_remove_${item.model.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove ${item.model.title} from queue",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun resolveModelStateLabel(
    isInstalled: Boolean,
    isSelectedReady: Boolean,
    partialBytes: Long,
    progress: ModelDownloadProgress?
): OfflineModelStateLabel {
    val isDownloadingNow = progress != null && (
        progress.status == DownloadStatus.DOWNLOADING ||
            progress.status == DownloadStatus.PROBING ||
            progress.status == DownloadStatus.VERIFYING ||
            progress.status == DownloadStatus.PAUSED
        )
    return when {
        isDownloadingNow || (!isInstalled && partialBytes > 0L) -> OfflineModelStateLabel.DOWNLOADING
        isSelectedReady -> OfflineModelStateLabel.READY
        isInstalled -> OfflineModelStateLabel.DOWNLOADED
        else -> OfflineModelStateLabel.DOWNLOADED
    }
}

@Composable
private fun StorageSpaceOverviewCard(
    availableBytes: Long,
    totalBytes: Long,
    modelsDirUsedBytes: Long
) {
    val usedBytes = (totalBytes - availableBytes).coerceAtLeast(0L)
    val usedFraction = if (totalBytes > 0L) {
        (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("model_storage_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                        imageVector = Icons.Default.Storage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Available Storage Space",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "${ModelCatalog.formatBytes(availableBytes)} free",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("available_storage_text")
                )
            }

            LinearProgressIndicator(
                progress = { usedFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .testTag("storage_usage_progress")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Models folder used: ${
                        if (modelsDirUsedBytes > 0L) ModelCatalog.formatBytes(modelsDirUsedBytes) else "0 MB"
                    }",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("models_dir_usage_text")
                )
                if (totalBytes > 0L) {
                    Text(
                        text = "Total: ${ModelCatalog.formatBytes(totalBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusSummaryRow(
    models: List<LocalModel>,
    installedFiles: Set<String>,
    selectedModelFile: String?,
    downloadProgress: ModelDownloadProgress,
    downloadQueue: List<QueuedDownloadItem>,
    context: Context
) {
    val queuedIds = downloadQueue.map { it.model.id }.toSet()
    val downloadedCount = models.count { it.fileName in installedFiles }
    val readyCount = models.count { it.fileName in installedFiles && it.fileName == selectedModelFile }
    val downloadingCount = models.count { model ->
        val isActive = downloadProgress.modelId == model.id && (
            downloadProgress.status == DownloadStatus.DOWNLOADING ||
                downloadProgress.status == DownloadStatus.PROBING ||
                downloadProgress.status == DownloadStatus.VERIFYING ||
                downloadProgress.status == DownloadStatus.PAUSED
            )
        val hasPartial = ModelManager.hasPartialDownload(context, model.fileName) > 0L
        isActive || model.id in queuedIds || (model.fileName !in installedFiles && hasPartial)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("model_status_summary_row"),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatusSummaryPill(
            label = "Ready",
            count = readyCount,
            tint = Color(0xFF10B981),
            modifier = Modifier.weight(1f)
        )
        StatusSummaryPill(
            label = "Downloaded",
            count = downloadedCount,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        StatusSummaryPill(
            label = "Downloading",
            count = downloadingCount,
            tint = Color(0xFFF59E0B),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatusSummaryPill(
    label: String,
    count: Int,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = tint.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = tint
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = tint
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelStatusListItemCard(
    model: LocalModel,
    stateLabel: OfflineModelStateLabel,
    isInstalled: Boolean,
    isSelectedReady: Boolean,
    queueEntry: QueuedDownloadItem?,
    hasOtherActiveOrQueued: Boolean,
    onDiskBytes: Long,
    progress: ModelDownloadProgress?,
    onDownloadOrQueue: () -> Unit,
    onPause: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onSelectReady: () -> Unit
) {
    val isDownloadingNow = stateLabel == OfflineModelStateLabel.DOWNLOADING || queueEntry?.isActive == true
    val isWaitingInQueue = !isInstalled && queueEntry != null && !queueEntry.isActive
    val displayStatusText = when {
        isDownloadingNow -> "Downloading"
        isWaitingInQueue -> "Queued #${queueEntry!!.priority}"
        isSelectedReady -> "Ready"
        isInstalled -> "Downloaded"
        else -> "Not Downloaded"
    }

    val badgeColor = when {
        displayStatusText == "Ready" -> Color(0xFF10B981)
        displayStatusText == "Downloaded" -> MaterialTheme.colorScheme.primary
        displayStatusText == "Downloading" || displayStatusText.startsWith("Queued") -> Color(0xFFF59E0B)
        else -> MaterialTheme.colorScheme.outline
    }

    val familyTag = when {
        model.id.startsWith("gemma", ignoreCase = true) -> "Gemma 4"
        model.id.startsWith("qwen", ignoreCase = true) -> "Qwen 3"
        else -> "Custom LiteRT"
    }

    val targetTotalBytes = progress?.totalBytes ?: queueEntry?.totalBytes ?: model.expectedBytes ?: when (model.id) {
        ModelCatalog.QWEN3_06B.id -> 586_000_000L
        else -> null
    }

    val progressFraction = when {
        isInstalled -> 1f
        targetTotalBytes != null && targetTotalBytes > 0L ->
            (onDiskBytes.toFloat() / targetTotalBytes.toFloat()).coerceIn(0f, 1f)
        else -> 0f
    }

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = if (isSelectedReady || queueEntry?.isActive == true) 1.5.dp else 1.dp,
            color = when {
                isSelectedReady -> Color(0xFF10B981)
                queueEntry?.isActive == true -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.outline
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("model_status_item_${model.id}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = familyTag,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        if (model.id == ModelCatalog.DEFAULT_PREFERRED_MODEL_ID) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "Preferred for JSON",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = model.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = model.fileName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Status Badge (Downloaded, Downloading, Queued #N, Ready)
                Surface(
                    color = badgeColor.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, badgeColor),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.testTag("model_status_badge_${model.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = when {
                                displayStatusText == "Ready" -> Icons.Default.CheckCircle
                                displayStatusText == "Downloaded" -> Icons.Default.CloudDone
                                displayStatusText.startsWith("Queued") -> Icons.Default.Schedule
                                else -> Icons.Default.CloudDownload
                            },
                            contentDescription = null,
                            tint = badgeColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = displayStatusText,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor
                        )
                    }
                }
            }

            // Download Progress Bar & Byte Counter
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val isIndeterminate = isDownloadingNow &&
                    (targetTotalBytes == null || targetTotalBytes <= 0L) &&
                    progress?.status == DownloadStatus.DOWNLOADING

                if (isIndeterminate) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .testTag("model_progress_bar_${model.id}")
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { progressFraction },
                        color = badgeColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .testTag("model_progress_bar_${model.id}")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val progressDescription = when {
                        isInstalled -> {
                            "100% • ${ModelCatalog.formatBytes(onDiskBytes)} on device (Downloaded)"
                        }
                        isWaitingInQueue -> {
                            val pct = (progressFraction * 100).toInt()
                            "Queued at #${queueEntry!!.priority} • $pct% (${ModelCatalog.formatBytes(onDiskBytes)} / ${
                                ModelCatalog.sizeDisplayLabel(model, targetTotalBytes)
                            })"
                        }
                        isDownloadingNow -> {
                            val pct = (progressFraction * 100).toInt()
                            val speedMb = (progress?.speedBytesPerSec ?: 0L).toDouble() / (1024.0 * 1024.0)
                            val speedPart = if ((progress?.speedBytesPerSec ?: 0L) > 0L) {
                                String.format(Locale.US, " • %.2f MB/s", speedMb)
                            } else {
                                ""
                            }
                            val etaPart = progress?.etaSeconds?.let { sec ->
                                if (sec >= 60) " • ETA ${sec / 60}m ${sec % 60}s" else " • ETA ${sec}s"
                            }.orEmpty()
                            "$pct% • ${ModelCatalog.formatBytes(onDiskBytes)} / ${
                                ModelCatalog.sizeDisplayLabel(model, targetTotalBytes)
                            }$speedPart$etaPart"
                        }
                        else -> {
                            "0% • Expected size: ${ModelCatalog.sizeDisplayLabel(model, targetTotalBytes)}"
                        }
                    }

                    Text(
                        text = progressDescription,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("model_progress_text_${model.id}")
                    )
                }
            }

            progress?.warningMessage?.let { warn ->
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

            progress?.errorMessage?.let { err ->
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

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

            // Action Buttons Row
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val isActivelyRunning = progress?.status == DownloadStatus.DOWNLOADING ||
                    progress?.status == DownloadStatus.PROBING ||
                    progress?.status == DownloadStatus.VERIFYING

                if (!isInstalled && !isActivelyRunning && queueEntry == null && model.url.isNotBlank()) {
                    Button(
                        onClick = onDownloadOrQueue,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("status_download_btn_${model.id}")
                    ) {
                        Icon(
                            imageVector = when {
                                hasOtherActiveOrQueued -> Icons.Default.Queue
                                onDiskBytes > 0L -> Icons.Default.PlayArrow
                                else -> Icons.Default.CloudDownload
                            },
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            when {
                                hasOtherActiveOrQueued -> "Add to Queue"
                                onDiskBytes > 0L -> "Resume Download"
                                else -> "Download"
                            }
                        )
                    }
                }

                if (isActivelyRunning) {
                    FilledTonalButton(
                        onClick = onPause,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("status_pause_btn_${model.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Pause,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pause")
                    }
                }

                if (isActivelyRunning || queueEntry != null || (!isInstalled && onDiskBytes > 0L)) {
                    OutlinedButton(
                        onClick = onCancel,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("status_cancel_btn_${model.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cancel,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isWaitingInQueue) "Remove from Queue" else "Cancel")
                    }
                }

                if (isInstalled) {
                    FilterChip(
                        selected = isSelectedReady,
                        onClick = onSelectReady,
                        label = {
                            Text(if (isSelectedReady) "Status: Ready ✓" else "Set as Ready")
                        },
                        modifier = Modifier.testTag("status_ready_chip_${model.id}")
                    )

                    OutlinedButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("status_delete_btn_${model.id}")
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
