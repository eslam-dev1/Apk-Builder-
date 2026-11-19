package com.example.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.OfflineBolt
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai.ModelManager
import com.example.ai.OfflineSpeechDictation
import com.example.data.BuildModeStrategy
import com.example.data.CloudAiConfig
import com.example.data.CloudAiProvider
import com.example.data.CodeExporter
import com.example.data.ProjectBuildPlanner
import com.example.data.RequirementProfile
import com.example.design.ScreenIcons
import com.example.design.ScreenSpecs
import com.example.design.designFor
import com.example.model.AppIconUiState
import com.example.model.CodeAnalysisUiState
import com.example.model.TabBuildPreference
import com.example.model.TabSelectionMode
import com.example.viewmodel.GenerateViewModel

private val ErrorRedBackground = Color(0xFF7F1D1D)
private val ErrorRedBorder = Color(0xFFEF4444)
private val ErrorRedText = Color(0xFFFEE2E2)

private val SuccessGreenBackground = Color(0xFF064E3B)
private val SuccessGreenBorder = Color(0xFF10B981)
private val SuccessGreenAccent = Color(0xFF34D399)
private val SuccessGreenText = Color(0xFFECFDF5)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateScreen(
    viewModel: GenerateViewModel,
    onBack: () -> Unit,
    onSaveToProjects: () -> Unit,
    onPreviewNow: (String) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenModels: () -> Unit = {},
    onOpenIconStudio: (String?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val prompt by viewModel.prompt.collectAsStateWithLifecycle()
    val generatedProject by viewModel.generatedProject.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isScanningImage by viewModel.isScanningImage.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val appIconStates by viewModel.appIconStates.collectAsStateWithLifecycle()
    val codeAnalysisStates by viewModel.codeAnalysisStates.collectAsStateWithLifecycle()
    val voiceState by OfflineSpeechDictation.state.collectAsStateWithLifecycle()

    val activeProvider by CloudAiConfig.currentProvider.collectAsStateWithLifecycle()
    val buildModeStrategy by CloudAiConfig.buildModeStrategy.collectAsStateWithLifecycle()
    val isOfflineMode by CloudAiConfig.isOfflineMode.collectAsStateWithLifecycle()
    val lastBuildRoutingPlan by viewModel.lastBuildRoutingPlan.collectAsStateWithLifecycle()
    val tabPreference by viewModel.tabPreference.collectAsStateWithLifecycle()
    val customTabNamesInput by viewModel.customTabNamesInput.collectAsStateWithLifecycle()
    val installedFiles by ModelManager.installedFiles.collectAsStateWithLifecycle()
    val selectedModelFile by ModelManager.selectedModelFile.collectAsStateWithLifecycle()
    val requirementAssessment = remember(prompt) { ProjectBuildPlanner.assessRequirements(prompt) }

    val startVoiceCapture = remember(context, prompt) {
        {
            OfflineSpeechDictation.startListening(context) { recognized ->
                val merged = if (prompt.isBlank()) recognized else "${prompt.trim()} $recognized"
                viewModel.onPromptChange(merged)
            }
        }
    }

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startVoiceCapture()
        } else {
            viewModel.clearError()
        }
    }

    val imageOcrPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.extractPromptFromImage(context, uri)
        }
    }

    LaunchedEffect(Unit) {
        CloudAiConfig.load(context)
        ModelManager.refreshState(context)
    }

    DisposableEffect(Unit) {
        onDispose {
            OfflineSpeechDictation.stopListening()
        }
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
                .padding(vertical = 20.dp)
        ) {
            // Top Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Create App",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.testTag("create_title")
                )
                TextButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("back_button")
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

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                OutlinedTextField(
                    value = prompt,
                    onValueChange = viewModel::onPromptChange,
                    label = { Text("Describe your app") },
                    placeholder = { Text("Example: Clothes store app") },
                    minLines = 3,
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("prompt_text_field")
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Local Multimodal AI Input Tools (Offline Voice & Offline Screenshot OCR)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AssistChip(
                        onClick = {
                            if (voiceState.isListening) {
                                OfflineSpeechDictation.stopListening()
                            } else {
                                val hasMic = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasMic) {
                                    startVoiceCapture()
                                } else {
                                    recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        },
                        enabled = !isLoading,
                        leadingIcon = {
                            Icon(
                                imageVector = if (voiceState.isListening) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = if (voiceState.isListening) "Stop dictation" else "Voice dictate prompt",
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = {
                            Text(if (voiceState.isListening) "Stop Listening" else "Voice Dictate")
                        },
                        modifier = Modifier.testTag("create_voice_dictate_button")
                    )

                    AssistChip(
                        onClick = {
                            imageOcrPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        enabled = !isLoading && !isScanningImage,
                        leadingIcon = {
                            if (isScanningImage) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.DocumentScanner,
                                    contentDescription = "Extract app prompt from screenshot or sketch",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        },
                        label = {
                            Text(if (isScanningImage) "Scanning Image..." else "Scan UI (OCR)")
                        },
                        modifier = Modifier.testTag("create_ocr_image_button")
                    )
                }

                if (voiceState.isListening || voiceState.partialText.isNotBlank() || voiceState.errorMessage != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = when {
                            voiceState.isListening && voiceState.partialText.isNotBlank() ->
                                "Listening: \"${voiceState.partialText}\""
                            voiceState.isListening ->
                                "Listening offline via SpeechRecognizer..."
                            voiceState.errorMessage != null ->
                                voiceState.errorMessage.orEmpty()
                            else -> ""
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (voiceState.errorMessage != null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.testTag("create_voice_status_text")
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // AI Provider Selector (Gemini vs OpenRouter)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = activeProvider == CloudAiProvider.GEMINI,
                            onClick = {
                                CloudAiConfig.setProvider(context, CloudAiProvider.GEMINI)
                                viewModel.clearError()
                            },
                            enabled = !isLoading && !isOfflineMode,
                            label = { Text("Gemini") },
                            modifier = Modifier.testTag("create_provider_gemini_chip")
                        )
                        FilterChip(
                            selected = activeProvider == CloudAiProvider.OPENROUTER,
                            onClick = {
                                CloudAiConfig.setProvider(context, CloudAiProvider.OPENROUTER)
                                viewModel.clearError()
                            },
                            enabled = !isLoading && !isOfflineMode,
                            label = { Text("OpenRouter") },
                            modifier = Modifier.testTag("create_provider_openrouter_chip")
                        )
                    }

                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("create_open_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Configure API Keys"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Build Mode & Offline Mode Switch Card
                Surface(
                    color = if (isOfflineMode) {
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    },
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(
                        1.dp,
                        if (isOfflineMode) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("offline_mode_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
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
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = when (buildModeStrategy) {
                                            BuildModeStrategy.AUTO ->
                                                "Auto: Try Offline first → Fallback to ${activeProvider.displayName}"
                                            BuildModeStrategy.CLOUD ->
                                                "Cloud AI: ${activeProvider.displayName}"
                                            BuildModeStrategy.OFFLINE -> when {
                                                selectedModelFile != null && selectedModelFile in installedFiles ->
                                                    "Offline model ready: $selectedModelFile"
                                                installedFiles.isNotEmpty() ->
                                                    "Offline model ready: ${installedFiles.first()}"
                                                else ->
                                                    "No offline .litertlm model downloaded yet"
                                            }
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (isOfflineMode && installedFiles.isEmpty()) {
                                        TextButton(
                                            onClick = onOpenModels,
                                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp),
                                            modifier = Modifier.testTag("create_open_models_button")
                                        ) {
                                            Text("Download Offline Models")
                                        }
                                    }
                                }
                            }

                            Switch(
                                checked = isOfflineMode,
                                onCheckedChange = { enabled ->
                                    CloudAiConfig.setOfflineMode(context, enabled)
                                    viewModel.clearError()
                                },
                                enabled = !isLoading,
                                modifier = Modifier.testTag("offline_mode_switch")
                            )
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = buildModeStrategy == BuildModeStrategy.AUTO,
                                onClick = {
                                    CloudAiConfig.setBuildModeStrategy(context, BuildModeStrategy.AUTO)
                                    viewModel.clearError()
                                },
                                enabled = !isLoading,
                                label = { Text("Auto (Offline First)") },
                                modifier = Modifier.testTag("create_mode_auto_chip")
                            )
                            FilterChip(
                                selected = buildModeStrategy == BuildModeStrategy.OFFLINE,
                                onClick = {
                                    CloudAiConfig.setBuildModeStrategy(context, BuildModeStrategy.OFFLINE)
                                    viewModel.clearError()
                                },
                                enabled = !isLoading,
                                label = { Text("Offline") },
                                modifier = Modifier.testTag("create_mode_offline_chip")
                            )
                            FilterChip(
                                selected = buildModeStrategy == BuildModeStrategy.CLOUD,
                                onClick = {
                                    CloudAiConfig.setBuildModeStrategy(context, BuildModeStrategy.CLOUD)
                                    viewModel.clearError()
                                },
                                enabled = !isLoading,
                                label = { Text("Cloud AI") },
                                modifier = Modifier.testTag("create_mode_cloud_chip")
                            )
                        }

                        if (prompt.isNotBlank()) {
                            val isCloudRequired = requirementAssessment.profile == RequirementProfile.CLOUD_REQUIRED
                            Text(
                                text = "${requirementAssessment.profile.label}: ${requirementAssessment.reason}" +
                                    if (buildModeStrategy == BuildModeStrategy.AUTO) {
                                        if (isCloudRequired) {
                                            " Will verify Offline first, then route to ${activeProvider.displayName}."
                                        } else {
                                            " Will try Offline first, then fallback to ${activeProvider.displayName} if unavailable."
                                        }
                                    } else {
                                        ""
                                    },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isCloudRequired) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.testTag("create_requirement_profile_text")
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Dynamic Tabs / Screens Selector (AI Decides vs User Defined)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tab_config_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ViewCarousel,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Project Tabs / Screens",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = tabPreference.mode == TabSelectionMode.AI_DECIDES,
                                onClick = { viewModel.setTabSelectionMode(TabSelectionMode.AI_DECIDES) },
                                enabled = !isLoading,
                                label = { Text("Let AI Decide") },
                                modifier = Modifier.testTag("tabs_mode_ai_chip")
                            )
                            FilterChip(
                                selected = tabPreference.mode == TabSelectionMode.USER_DEFINED,
                                onClick = { viewModel.setTabSelectionMode(TabSelectionMode.USER_DEFINED) },
                                enabled = !isLoading,
                                label = { Text("Custom (User)") },
                                modifier = Modifier.testTag("tabs_mode_custom_chip")
                            )
                        }

                        if (tabPreference.mode == TabSelectionMode.AI_DECIDES) {
                            Text(
                                text = "No fixed default tabs — AI automatically selects the optimal number of tabs for your project.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("tabs_mode_summary_text")
                            )
                        } else {
                            val effectiveCount = tabPreference.effectiveRequestedCount ?: tabPreference.requestedCount
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (tabPreference.customTabNames.isNotEmpty()) {
                                        "Custom names count: $effectiveCount tabs"
                                    } else {
                                        "Number of tabs: $effectiveCount"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.testTag("tabs_count_value")
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            viewModel.setRequestedTabCount(tabPreference.requestedCount - 1)
                                        },
                                        enabled = !isLoading &&
                                            tabPreference.customTabNames.isEmpty() &&
                                            tabPreference.requestedCount > TabBuildPreference.MIN_USER_TABS,
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("tabs_count_decrease")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Remove,
                                            contentDescription = "Decrease tab count"
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            viewModel.setRequestedTabCount(tabPreference.requestedCount + 1)
                                        },
                                        enabled = !isLoading &&
                                            tabPreference.customTabNames.isEmpty() &&
                                            tabPreference.requestedCount < TabBuildPreference.MAX_USER_TABS,
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("tabs_count_increase")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Increase tab count"
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = customTabNamesInput,
                                onValueChange = viewModel::onCustomTabNamesChange,
                                label = { Text("Custom tab names (optional)") },
                                placeholder = { Text("e.g. Home, Catalog, Cart") },
                                singleLine = true,
                                enabled = !isLoading,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("custom_tab_names_field")
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = { viewModel.generateProject(prompt, context) },
                    enabled = prompt.isNotBlank() && !isLoading,
                    contentPadding = PaddingValues(vertical = 14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("generate_button")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Generating...")
                    } else {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Generate")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                FilledTonalButton(
                    onClick = { viewModel.generateOfflineScaffold(prompt) },
                    enabled = prompt.isNotBlank() && !isLoading,
                    contentPadding = PaddingValues(vertical = 12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("generate_offline_scaffold_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.OfflineBolt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Instant Offline Scaffold (Local RAG)")
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Build Routing Plan Banner
                lastBuildRoutingPlan?.let { routingPlan ->
                    if (!isLoading) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("build_routing_status_banner")
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = if (routingPlan.useCloudAi) Icons.Default.Cloud else Icons.Default.CloudOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = routingPlan.statusSummary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                // Red Error Banner
                error?.let { errorMsg ->
                    Surface(
                        color = ErrorRedBackground,
                        border = BorderStroke(1.5.dp, ErrorRedBorder),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("error_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = "Error",
                                tint = ErrorRedBorder
                            )
                            Text(
                                text = errorMsg,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = ErrorRedText,
                                modifier = Modifier.testTag("error_message_text")
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                AnimatedVisibility(
                    visible = isLoading,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    AiGenerationLoadingCard(
                        title = "Generating App with ${activeProvider.displayName}",
                        stages = listOf(
                            "Checking offline capability & project requirements...",
                            "Determining optimal screen tabs for your app...",
                            "Selecting Material 3 palette and visual style...",
                            "Populating realistic sample data and Kotlin code..."
                        ),
                        progressBarTestTag = "create_indeterminate_progress_bar",
                        subtitleHint = lastBuildRoutingPlan?.statusSummary
                            ?: "Generating tailored blueprint via ${activeProvider.displayName}...",
                        modifier = Modifier
                            .padding(vertical = 8.dp)
                            .testTag("loading_indicator")
                    )
                }

                // Dynamic Design Result Card
                generatedProject?.let { project ->
                    if (!isLoading) {
                        val appDesign = remember(project) { designFor(project) }
                        val primaryAccent = Color(appDesign.primaryColor)
                        val cardContainer = MaterialTheme.colorScheme.surface
                        val cardContent = MaterialTheme.colorScheme.onSurface

                        Card(
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(
                                containerColor = cardContainer
                            ),
                            border = BorderStroke(1.5.dp, primaryAccent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("generated_result_card")
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        color = primaryAccent.copy(alpha = 0.15f),
                                        border = BorderStroke(1.dp, primaryAccent),
                                        shape = RoundedCornerShape(50),
                                        modifier = Modifier.testTag("design_style_badge")
                                    ) {
                                        Text(
                                            text = "${appDesign.designStyle} • ${appDesign.layoutType}",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = primaryAccent,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Success",
                                        tint = primaryAccent,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Text(
                                        text = project.name,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = primaryAccent,
                                        modifier = Modifier.testTag("generated_project_name")
                                    )
                                }

                                Text(
                                    text = project.description,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = cardContent,
                                    modifier = Modifier.testTag("generated_project_description")
                                )

                                AiAppIconGeneratorSection(
                                    project = project,
                                    iconState = appIconStates[project.id] ?: AppIconUiState.Idle,
                                    onGenerateIcon = { targetProject ->
                                        viewModel.generateProjectIcon(targetProject, context = context)
                                    },
                                    onGenerateCustomIcon = { customPrompt, style ->
                                        viewModel.generateProjectIcon(
                                            project = project,
                                            customPrompt = customPrompt,
                                            styleOverride = style,
                                            context = context
                                        )
                                    },
                                    onOpenIconStudio = { onOpenIconStudio(project.id) },
                                    accentColor = primaryAccent
                                )

                                if (project.features.isNotEmpty()) {
                                    HorizontalDivider(color = primaryAccent.copy(alpha = 0.35f))
                                    Text(
                                        text = "Features",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = primaryAccent
                                    )
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.testTag("generated_features_list")
                                    ) {
                                        project.features.forEach { feature ->
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircleOutline,
                                                    contentDescription = null,
                                                    tint = primaryAccent,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Text(
                                                    text = feature,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = cardContent
                                                )
                                            }
                                        }
                                    }
                                }

                                if (project.screens.isNotEmpty()) {
                                    val screenSpecs = remember(project) { ScreenSpecs.all(project) }
                                    HorizontalDivider(color = primaryAccent.copy(alpha = 0.35f))
                                    Text(
                                        text = "Screens (${screenSpecs.size})",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = primaryAccent
                                    )
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.testTag("generated_screens_list")
                                    ) {
                                        screenSpecs.forEach { spec ->
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                border = BorderStroke(
                                                    1.dp,
                                                    primaryAccent.copy(alpha = 0.6f)
                                                ),
                                                color = primaryAccent.copy(alpha = 0.1f)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = ScreenIcons.vector(spec.icon),
                                                        contentDescription = null,
                                                        tint = primaryAccent,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Text(
                                                        text = ScreenSpecs.labelFor(project, spec.name),
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = cardContent
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                val generatedKotlinCode = remember(project) {
                                    CodeExporter.buildProjectCode(project)
                                }
                                val safeFileBase = remember(project.name) {
                                    CodeExporter.toSafeFileName(project.name)
                                }

                                HorizontalDivider(color = primaryAccent.copy(alpha = 0.35f))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Generated Kotlin Code",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = primaryAccent
                                    )
                                    Text(
                                        text = "Syntax Highlighted",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                KotlinCodeBlock(
                                    code = generatedKotlinCode,
                                    fileName = "${safeFileBase}Screens.kt",
                                    codeTestTag = "create_generated_code_preview",
                                    borderColor = primaryAccent.copy(alpha = 0.45f),
                                    initiallyExpanded = false,
                                    collapsedMaxHeight = 220.dp
                                )

                                CodeAnalysisSection(
                                    analysisState = codeAnalysisStates[project.id] ?: CodeAnalysisUiState.Idle,
                                    onAnalyzeCode = { viewModel.analyzeGeneratedCode(project) },
                                    tagPrefix = "create",
                                    accentColor = primaryAccent
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Button(
                                    onClick = {
                                        if (viewModel.saveCurrentProject()) {
                                            onSaveToProjects()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = primaryAccent,
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("save_to_projects_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.BookmarkAdd,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Save to Projects",
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                OutlinedButton(
                                    onClick = { onPreviewNow(project.id) },
                                    border = BorderStroke(1.dp, primaryAccent),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = primaryAccent
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("preview_now_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Visibility,
                                        contentDescription = null,
                                        tint = primaryAccent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Preview Now",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
