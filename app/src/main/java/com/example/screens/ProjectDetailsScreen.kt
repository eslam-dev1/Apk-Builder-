package com.example.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Download
import com.example.model.BuildOptions
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.apk.ApkBuilder
import com.example.data.CodeExporter
import com.example.design.ScreenIcons
import com.example.design.ScreenSpecs
import com.example.design.designFor
import com.example.model.AiFeatureIdea
import com.example.model.AppIconUiState
import com.example.model.AppProject
import com.example.model.ScreenSpec
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.io.File
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProjectDetailsScreen(
    project: AppProject?,
    isRegenerating: Boolean = false,
    errorMessage: String? = null,
    appIconState: AppIconUiState = AppIconUiState.Idle,
    onGenerateAppIcon: (AppProject) -> Unit = {},
    onGenerateCustomAppIcon: ((AppProject, String, String) -> Unit)? = null,
    onOpenIconStudio: ((String) -> Unit)? = null,
    aiFeatureIdeas: List<AiFeatureIdea> = emptyList(),
    isAnalyzingFeatures: Boolean = false,
    onAnalyzeAiFeatures: (AppProject) -> Unit = {},
    onAddSuggestedFeature: (String, AiFeatureIdea) -> Unit = { _, _ -> },
    codeAnalysisState: com.example.model.CodeAnalysisUiState = com.example.model.CodeAnalysisUiState.Idle,
    onAnalyzeCode: (AppProject) -> Unit = {},
    onRegenerateProject: (String, String) -> Unit = { _, _ -> },
    onUpdateProject: (AppProject) -> Unit = {},
    onLivePreview: (String) -> Unit = {},
    onConfigureBuild: (String) -> Unit = {},
    triggerBuildOnResume: Boolean = false,
    onResetTriggerBuild: () -> Unit = {},
    onDeleteProject: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isSavingTxt by remember { mutableStateOf(false) }
    var isSavingZip by remember { mutableStateOf(false) }
    var isBuildingApk by remember { mutableStateOf(false) }
    var apkProgressSteps by remember(project?.id) { mutableStateOf<String?>(null) }
    var apkErrorMessage by remember(project?.id) { mutableStateOf<String?>(null) }
    var builtApkFile by remember(project?.id) { mutableStateOf<File?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRegenerateDialog by remember { mutableStateOf(false) }
    var showApiKeyValidationDialog by remember { mutableStateOf(false) }
    val buildLogs = remember(project?.id) { mutableStateOf(listOf<String>()) }
    var showBuildLogs by remember(project?.id) { mutableStateOf(false) }
    var editedPrompt by remember(project?.prompt) { mutableStateOf(project?.prompt.orEmpty()) }
    var selectedEditScreenIndex by remember(project?.id) { mutableStateOf<Int?>(null) }
    var newSampleItemDraft by remember(project?.id, selectedEditScreenIndex) { mutableStateOf("") }

    if (showRegenerateDialog && project != null) {
        AlertDialog(
            onDismissRequest = { showRegenerateDialog = false },
            title = { Text("Regenerate Project") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Edit the prompt below to regenerate \"${project.name}\" with ${com.example.data.CloudAiConfig.currentProvider.value.displayName}:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = editedPrompt,
                        onValueChange = { editedPrompt = it },
                        label = { Text("App Prompt") },
                        minLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("regenerate_prompt_field")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRegenerateDialog = false
                        onRegenerateProject(project.id, editedPrompt)
                    },
                    enabled = editedPrompt.isNotBlank(),
                    modifier = Modifier.testTag("confirm_regenerate_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Regenerate")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRegenerateDialog = false },
                    modifier = Modifier.testTag("cancel_regenerate_button")
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeleteDialog && project != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Project") },
            text = {
                Text("Are you sure you want to delete \"${project.name}\"? This action cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        onDeleteProject(project.id)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("confirm_delete_button")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialog = false },
                    modifier = Modifier.testTag("cancel_delete_button")
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    LaunchedEffect(triggerBuildOnResume) {
        if (triggerBuildOnResume && !isBuildingApk) {
            onResetTriggerBuild()
            showApiKeyValidationDialog = true
        }
    }

    if (showApiKeyValidationDialog && project != null) {
        ApiKeyValidationBottomSheet(
            onDismiss = { showApiKeyValidationDialog = false },
            onProceedToBuild = {
                showApiKeyValidationDialog = false
                if (!isBuildingApk) {
                    isBuildingApk = true
                    apkErrorMessage = null
                    builtApkFile = null
                    buildLogs.value = emptyList()
                    val timeFormat = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                    buildLogs.value = buildLogs.value + "[${timeFormat.format(java.util.Date())}] INFO: Triggering APK build for '${project?.name ?: ""}'"
                    showBuildLogs = true

                    val observedSteps = mutableListOf<String>()
                    apkProgressSteps = "Copying template..."
                    coroutineScope.launch {
                        val result = ApkBuilder.buildApk(
                            project = project,
                            context = context,
                            onProgress = { step ->
                                if (!observedSteps.contains(step)) {
                                    observedSteps.add(step)
                                }
                                apkProgressSteps = observedSteps.joinToString(" ")
                            },
                            onLog = { logLine ->
                                buildLogs.value = buildLogs.value + logLine
                            }
                        )
                        isBuildingApk = false
                        result.onSuccess { signedFile ->
                            builtApkFile = signedFile
                            apkProgressSteps =
                                "Copying template... Injecting config... Aligning... Signing... Verifying... Done"
                            Toast.makeText(
                                context,
                                "Saved APK: Downloads/AiAppStudio/${signedFile.name}",
                                Toast.LENGTH_LONG
                            ).show()
                        }.onFailure { err ->
                            apkErrorMessage = err.message
                                ?: "Base APK template missing. Place real signed base.apk in assets."
                        }
                    }
                }
            },
            buildOptions = project.buildOptions,
            onConfigureOptions = {
                showApiKeyValidationDialog = false
                onConfigureBuild(project.id)
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
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Project Details",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.testTag("project_details_title")
                )
                TextButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("details_back_button")
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

            errorMessage?.let { errMsg ->
                Surface(
                    color = Color(0xFF7F1D1D),
                    border = BorderStroke(1.5.dp, Color(0xFFEF4444)),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("details_error_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = "Error",
                            tint = Color(0xFFEF4444)
                        )
                        Text(
                            text = errMsg,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFFEE2E2)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            AnimatedVisibility(
                visible = isRegenerating,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    AiGenerationLoadingCard(
                        title = "Regenerating Project",
                        stages = listOf(
                            "Re-evaluating your updated prompt...",
                            "Updating screens, icons, and tab specifications...",
                            "Refreshing Live Preview and Kotlin export..."
                        ),
                        progressBarTestTag = "regenerate_indeterminate_progress_bar",
                        subtitleHint = "Updating blueprint with AI...",
                        modifier = Modifier.testTag("regenerating_indicator")
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                }
            }

            if (project == null) {
                Text(
                    text = "Project not found.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val exportedKotlinCode = remember(project) {
                    CodeExporter.buildProjectCode(project)
                }
                val safeBaseName = remember(project.name) {
                    CodeExporter.toSafeFileName(project.name)
                }

                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("project_details_card")
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = project.name,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("details_project_name")
                            )

                            FilledTonalButton(
                                onClick = {
                                    editedPrompt = project.prompt
                                    showRegenerateDialog = true
                                },
                                enabled = !isRegenerating,
                                modifier = Modifier.testTag("regenerate_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Regenerate")
                            }
                        }

                        Text(
                            text = project.description,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.testTag("details_project_description")
                        )

                        if (project.prompt.isNotBlank()) {
                            Text(
                                text = "Prompt: \"${project.prompt}\"",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Button(
                            onClick = { onLivePreview(project.id) },
                            contentPadding = PaddingValues(vertical = 13.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("live_preview_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Live Preview")
                        }

                        AiAppIconGeneratorSection(
                            project = project,
                            iconState = appIconState,
                            onGenerateIcon = onGenerateAppIcon,
                            onGenerateCustomIcon = onGenerateCustomAppIcon?.let { callback ->
                                { prompt, style -> callback(project, prompt, style) }
                            },
                            onOpenIconStudio = onOpenIconStudio?.let { opener ->
                                { opener(project.id) }
                            },
                            accentColor = MaterialTheme.colorScheme.primary
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

                        Text(
                            text = "Features",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.secondary
                        )

                        if (project.features.isEmpty()) {
                            Text(
                                text = "No specific features listed.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.testTag("details_features_list")
                            ) {
                                project.features.forEach { feature ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircleOutline,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = feature,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        FilledTonalButton(
                            onClick = { onAnalyzeAiFeatures(project) },
                            enabled = !isAnalyzingFeatures,
                            contentPadding = PaddingValues(vertical = 12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("suggest_ai_features_button")
                        ) {
                            if (isAnalyzingFeatures) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Analyzing App Description for AI Ideas...")
                            } else {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Suggest AI-Powered Feature Ideas (3–5)")
                            }
                        }

                        AnimatedVisibility(
                            visible = isAnalyzingFeatures,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            AiGenerationLoadingCard(
                                title = "Analyzing App for AI Ideas",
                                stages = listOf(
                                    "Reviewing app workflow and user intent...",
                                    "Brainstorming high-impact Gemini & ML features...",
                                    "Ranking practical product benefits..."
                                ),
                                accentColor = MaterialTheme.colorScheme.secondary,
                                progressBarTestTag = "ai_features_indeterminate_progress_bar",
                                subtitleHint = "Evaluating smart feature opportunities...",
                                modifier = Modifier.testTag("ai_features_loading_indicator")
                            )
                        }

                        if (aiFeatureIdeas.isNotEmpty()) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("ai_feature_ideas_section")
                            ) {
                                Text(
                                    text = "AI-Powered Feature Ideas",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                aiFeatureIdeas.forEachIndexed { idx, idea ->
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                        ),
                                        border = BorderStroke(
                                            1.dp,
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("ai_feature_idea_card_$idx")
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
                                                Text(
                                                    text = "${idx + 1}. ${idea.title}",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                TextButton(
                                                    onClick = {
                                                        onAddSuggestedFeature(project.id, idea)
                                                        Toast.makeText(
                                                            context,
                                                            "Added \"${idea.title}\" to project features",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    },
                                                    modifier = Modifier.testTag("add_ai_idea_button_$idx")
                                                ) {
                                                    Text("+ Add")
                                                }
                                            }
                                            Text(
                                                text = idea.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Potential Benefit: ${idea.benefit}",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

                        InlineThemeStyleEditor(
                            project = project,
                            onUpdateProject = onUpdateProject
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

                        val screenSpecs = remember(project) { ScreenSpecs.all(project) }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Screens (${screenSpecs.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                                Text(
                                    text = "Tap to edit • Updates Live Preview & Export",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(
                                onClick = {
                                    val updated = withAddedScreenSpec(project)
                                    onUpdateProject(updated)
                                    selectedEditScreenIndex = ScreenSpecs.all(updated).lastIndex
                                },
                                modifier = Modifier.testTag("add_screen_tab_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add tab",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Tab")
                            }
                        }

                        if (screenSpecs.isEmpty()) {
                            Text(
                                text = "No screens",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("no_screens_text")
                            )
                        } else {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.testTag("details_screens_list")
                            ) {
                                screenSpecs.forEachIndexed { index, spec ->
                                    val isSelectedForEdit = selectedEditScreenIndex == index
                                    FilterChip(
                                        selected = isSelectedForEdit,
                                        onClick = {
                                            selectedEditScreenIndex = if (isSelectedForEdit) null else index
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = ScreenIcons.vector(spec.icon),
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        trailingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Edit ${spec.label}",
                                                modifier = Modifier.size(14.dp)
                                            )
                                        },
                                        label = { Text(ScreenSpecs.labelFor(project, spec.name)) },
                                        modifier = Modifier.testTag("screen_chip_$index")
                                    )
                                }
                            }

                            val activeEditIndex = selectedEditScreenIndex?.takeIf { it in screenSpecs.indices }
                            if (activeEditIndex != null) {
                                val editingSpec = screenSpecs[activeEditIndex]
                                InlineScreenSpecEditorCard(
                                    index = activeEditIndex,
                                    spec = editingSpec,
                                    newSampleItemDraft = newSampleItemDraft,
                                    onNewSampleItemDraftChange = { newSampleItemDraft = it },
                                    onSpecChange = { updatedSpec ->
                                        onUpdateProject(
                                            withUpdatedScreenSpec(
                                                project = project,
                                                index = activeEditIndex,
                                                transform = { updatedSpec }
                                            )
                                        )
                                    },
                                    onRemoveTab = {
                                        onUpdateProject(
                                            withRemovedScreenSpec(
                                                project = project,
                                                index = activeEditIndex
                                            )
                                        )
                                        selectedEditScreenIndex = null
                                    },
                                    onDone = { selectedEditScreenIndex = null }
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

                        Text(
                            text = "Generated Kotlin Code Preview",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        KotlinCodeBlock(
                            code = exportedKotlinCode,
                            fileName = "${safeBaseName}Screens.kt",
                            codeTestTag = "exported_code_preview",
                            initiallyExpanded = false,
                            collapsedMaxHeight = 320.dp
                        )

                        CodeAnalysisSection(
                            analysisState = codeAnalysisState,
                            onAnalyzeCode = { onAnalyzeCode(project) },
                            tagPrefix = "details"
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Export Code",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { CodeExporter.copyToClipboard(context, project) },
                                contentPadding = PaddingValues(vertical = 12.dp, horizontal = 12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("copy_code_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Copy Code")
                            }

                            FilledTonalButton(
                                onClick = { CodeExporter.shareCode(context, project) },
                                contentPadding = PaddingValues(vertical = 12.dp, horizontal = 12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("share_code_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Share Code")
                            }
                        }

                        FilledTonalButton(
                            onClick = {
                                if (!isSavingTxt) {
                                    isSavingTxt = true
                                    coroutineScope.launch {
                                        val result = CodeExporter.saveToDownloads(context, project)
                                        isSavingTxt = false
                                        result.onSuccess { savedPath ->
                                            Toast.makeText(
                                                context,
                                                "Saved: $savedPath",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }.onFailure { err ->
                                            Toast.makeText(
                                                context,
                                                "Save failed: ${err.localizedMessage ?: "Unknown error"}",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
                            },
                            enabled = !isSavingTxt && !isSavingZip,
                            contentPadding = PaddingValues(vertical = 13.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("save_file_button")
                        ) {
                            if (isSavingTxt) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Saving .kt...")
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Save as .txt ($safeBaseName.kt)")
                            }
                        }

                        Button(
                            onClick = {
                                if (!isSavingZip) {
                                    isSavingZip = true
                                    coroutineScope.launch {
                                        val result = CodeExporter.saveZipToDownloads(context, project)
                                        isSavingZip = false
                                        result.onSuccess { savedZipPath ->
                                            Toast.makeText(
                                                context,
                                                "Saved ZIP: $savedZipPath",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }.onFailure { err ->
                                            Toast.makeText(
                                                context,
                                                "ZIP export failed: ${err.localizedMessage ?: "Unknown error"}",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
                            },
                            enabled = !isSavingZip && !isSavingTxt && !isBuildingApk,
                            contentPadding = PaddingValues(vertical = 14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("export_zip_button")
                        ) {
                            if (isSavingZip) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Building ZIP...")
                            } else {
                                Icon(
                                    imageVector = Icons.Default.FolderZip,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Export as ZIP ($safeBaseName.zip)")
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

                        Text(
                            text = "Real APK Builder (v1 + v2 Signed)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text(
                            text = "APK is built from base template + your config, install to test",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.testTag("apk_template_info_text")
                        )

                        // Build Options Profile Card & Configure Button
                        val currentBuildOptions = project?.buildOptions ?: BuildOptions()
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("build_options_summary_card")
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
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Tune,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Build Settings & Profile",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    TextButton(
                                        onClick = { if (project != null) onConfigureBuild(project.id) },
                                        modifier = Modifier.testTag("configure_build_options_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Tune,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Configure")
                                    }
                                }

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = when (currentBuildOptions.buildVariant.lowercase()) {
                                            "release" -> MaterialTheme.colorScheme.primaryContainer
                                            "benchmark" -> MaterialTheme.colorScheme.tertiaryContainer
                                            else -> MaterialTheme.colorScheme.secondaryContainer
                                        }
                                    ) {
                                        Text(
                                            text = "Variant: ${currentBuildOptions.buildVariant.uppercase()}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                    Surface(
                                        shape = CircleShape,
                                        color = if (currentBuildOptions.minifyEnabled) Color(0xFF14532D) else MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Text(
                                            text = if (currentBuildOptions.minifyEnabled) "Minify: ON" else "Minify: OFF",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (currentBuildOptions.minifyEnabled) Color(0xFF86EFAC) else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                    Surface(
                                        shape = CircleShape,
                                        color = if (currentBuildOptions.crunchPngs) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Text(
                                            text = if (currentBuildOptions.crunchPngs) "Crunch PNGs: ON" else "Crunch PNGs: OFF",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (currentBuildOptions.crunchPngs) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                    Surface(
                                        shape = CircleShape,
                                        color = if (currentBuildOptions.includeDebuggable) Color(0xFF1E3A8A) else MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Text(
                                            text = if (currentBuildOptions.includeDebuggable) "Debuggable: ON" else "Debuggable: OFF",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (currentBuildOptions.includeDebuggable) Color(0xFF93C5FD) else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Text(
                                            text = "API ${currentBuildOptions.targetSdk}",
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                    Surface(
                                        shape = CircleShape,
                                        color = if (currentBuildOptions.bundledTfliteModels.isNotEmpty()) {
                                            MaterialTheme.colorScheme.tertiaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        }
                                    ) {
                                        Text(
                                            text = if (currentBuildOptions.bundledTfliteModels.isNotEmpty()) {
                                                "TFLite: ${currentBuildOptions.bundledTfliteModels.size} bundled"
                                            } else {
                                                "TFLite: None"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (currentBuildOptions.bundledTfliteModels.isNotEmpty()) {
                                                MaterialTheme.colorScheme.onTertiaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = {
                                if (!isBuildingApk) {
                                    showApiKeyValidationDialog = true
                                }
                            },
                            enabled = !isBuildingApk && !isSavingZip && !isSavingTxt,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary
                            ),
                            contentPadding = PaddingValues(vertical = 14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("build_apk_real_button")
                        ) {
                            if (isBuildingApk) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.onSecondary,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Building Real APK...")
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Build,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Build APK (${currentBuildOptions.buildVariant.replaceFirstChar { it.uppercase() }})")
                            }
                        }

                        // Real-Time Build Processing & Loading Line Banner
                        if (isBuildingApk || apkProgressSteps != null) {
                            val progressFraction = remember(apkProgressSteps) {
                                val msg = apkProgressSteps.orEmpty()
                                when {
                                    msg.contains("Done") || msg.contains("Verifying") -> 0.95f
                                    msg.contains("Signing") -> 0.75f
                                    msg.contains("Aligning") -> 0.55f
                                    msg.contains("Injecting") -> 0.35f
                                    msg.contains("Copying") -> 0.15f
                                    else -> if (isBuildingApk) 0.05f else 0.0f
                                }
                            }
                            val animatedProgress by animateFloatAsState(
                                targetValue = progressFraction,
                                label = "RealtimeApkProgress"
                            )
                            val percentInt = (animatedProgress * 100).toInt()
                            val latestLog = buildLogs.value.lastOrNull() ?: "Initializing offline build pipeline..."

                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                shape = MaterialTheme.shapes.medium,
                                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("apk_build_realtime_loading_card")
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
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            if (isBuildingApk) {
                                                CircularProgressIndicator(
                                                    strokeWidth = 2.dp,
                                                    modifier = Modifier.size(16.dp),
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            Text(
                                                text = if (isBuildingApk) "Real-Time Build Processing..." else "Build Completed Successfully",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Text(
                                            text = "$percentInt%",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    // Real-time animated loading line
                                    LinearProgressIndicator(
                                        progress = { animatedProgress },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(CircleShape),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )

                                    // Step progress info
                                    apkProgressSteps?.let { stepText ->
                                        Text(
                                            text = "Phase: $stepText",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    // Live console streaming log ticker
                                    Surface(
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                        shape = MaterialTheme.shapes.small,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Terminal,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = latestLog,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("apk_build_live_log_ticker")
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Expandable Build Logs Panel
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("build_logs_card")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showBuildLogs = !showBuildLogs },
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Terminal,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = "Build Logs & Console Output (${buildLogs.value.size})",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Icon(
                                        imageVector = if (showBuildLogs) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = if (showBuildLogs) "Collapse" else "Expand",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (showBuildLogs) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                                    if (buildLogs.value.isEmpty()) {
                                        Text(
                                            text = "No build logs yet. Trigger a build to view real-time console output.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        )
                                    } else {
                                        val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                                        val scrollState = rememberScrollState()

                                        LaunchedEffect(buildLogs.value.size) {
                                            coroutineScope.launch {
                                                scrollState.animateScrollTo(scrollState.maxValue)
                                            }
                                        }

                                        Surface(
                                            color = Color(0xFF0F172A),
                                            shape = MaterialTheme.shapes.small,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(200.dp)
                                                .verticalScroll(scrollState)
                                                .padding(8.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(8.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                for (line in buildLogs.value) {
                                                    val lineColor = when {
                                                        line.contains("SUCCESS") -> Color(0xFF4ADE80)
                                                        line.contains("ERROR") -> Color(0xFFF87171)
                                                        line.contains("INFO") -> Color(0xFF60A5FA)
                                                        else -> Color(0xFFE2E8F0)
                                                    }
                                                    Text(
                                                        text = line,
                                                        style = MaterialTheme.typography.bodySmall.copy(
                                                            fontFamily = JetBrainsMonoFontFamily,
                                                            fontSize = 11.sp
                                                        ),
                                                        color = lineColor
                                                    )
                                                }
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            TextButton(
                                                onClick = { buildLogs.value = emptyList() },
                                                modifier = Modifier.testTag("clear_logs_button")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.DeleteSweep,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Clear")
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            TextButton(
                                                onClick = {
                                                    clipboardManager.setText(AnnotatedString(buildLogs.value.joinToString("\n")))
                                                    Toast.makeText(context, "Build logs copied to clipboard", Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.testTag("copy_logs_button")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ContentCopy,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Copy Logs")
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        apkErrorMessage?.let { apkErr ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFF7F1D1D)
                                ),
                                border = BorderStroke(1.5.dp, Color(0xFFEF4444)),
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("apk_error_card")
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ErrorOutline,
                                            contentDescription = "APK Build Error",
                                            tint = Color(0xFFEF4444)
                                        )
                                        Text(
                                            text = apkErr,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFFFEE2E2)
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            if (!isSavingZip) {
                                                isSavingZip = true
                                                coroutineScope.launch {
                                                    val zipResult = CodeExporter.saveZipToDownloads(context, project)
                                                    isSavingZip = false
                                                    zipResult.onSuccess { savedZipPath ->
                                                        Toast.makeText(
                                                            context,
                                                            "Saved ZIP: $savedZipPath",
                                                            Toast.LENGTH_LONG
                                                        ).show()
                                                    }
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFFEE2E2),
                                            contentColor = Color(0xFF7F1D1D)
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("export_zip_instead_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.FolderZip,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Export ZIP Instead",
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        builtApkFile?.let { readyApkFile ->
                            Surface(
                                color = Color(0xFF064E3B),
                                border = BorderStroke(1.dp, Color(0xFF10B981)),
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("apk_offline_verified_badge")
                            ) {
                                Text(
                                    text = "APK: built offline on this device · signed v1+v2 · verified ✓",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFECFDF5),
                                    modifier = Modifier.padding(12.dp)
                                )
                            }

                            FilledTonalButton(
                                onClick = { ApkBuilder.installApk(context, readyApkFile) },
                                contentPadding = PaddingValues(vertical = 12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("install_apk_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Android,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Install APK (${readyApkFile.name})")
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

                        OutlinedButton(
                            onClick = { showDeleteDialog = true },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.7f)),
                            contentPadding = PaddingValues(vertical = 12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("delete_project_details_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Delete Project")
                        }
                    }
                }
            }
        }
    }
}

private val PRESET_STYLE_OPTIONS = listOf("Modern", "Minimal", "Bold", "Playful", "Professional")
private val PRESET_COLOR_HEXES = listOf(
    "#FF6B00",
    "#E53935",
    "#25D366",
    "#1A237E",
    "#1565C0",
    "#00ACC1",
    "#00897B",
    "#6200EE",
    "#7C3AED",
    "#EC4899"
)

internal fun normalizeHexColorInput(raw: String): String? {
    val clean = raw.trim().removePrefix("#").removePrefix("0x").removePrefix("0X")
    if (clean.length != 6) return null
    if (!clean.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
    return "#${clean.uppercase(Locale.US)}"
}

internal fun withUpdatedScreenSpec(
    project: AppProject,
    index: Int,
    transform: (ScreenSpec) -> ScreenSpec
): AppProject {
    val currentSpecs = ScreenSpecs.all(project).toMutableList()
    if (index !in currentSpecs.indices) return project
    currentSpecs[index] = transform(currentSpecs[index])
    return project.copy(
        screens = currentSpecs.map { it.name },
        screenSpecs = currentSpecs
    )
}

internal fun withAddedScreenSpec(
    project: AppProject,
    label: String = ""
): AppProject {
    val currentSpecs = ScreenSpecs.all(project).toMutableList()
    val nextNumber = currentSpecs.size + 1
    val resolvedLabel = label.trim().ifBlank { "Tab $nextNumber" }.take(16)
    val existingNames = currentSpecs.map { it.name }.toSet()
    var candidateNum = nextNumber
    var candidateName = "CustomTab${candidateNum}Screen"
    while (candidateName in existingNames) {
        candidateNum++
        candidateName = "CustomTab${candidateNum}Screen"
    }
    currentSpecs.add(
        ScreenSpec(
            name = candidateName,
            label = resolvedLabel,
            icon = ScreenIcons.keys.firstOrNull { it == "dashboard" } ?: "star",
            purpose = "Custom screen for $resolvedLabel"
        )
    )
    return project.copy(
        screens = currentSpecs.map { it.name },
        screenSpecs = currentSpecs
    )
}

internal fun withRemovedScreenSpec(
    project: AppProject,
    index: Int
): AppProject {
    val currentSpecs = ScreenSpecs.all(project).toMutableList()
    if (index !in currentSpecs.indices) return project
    currentSpecs.removeAt(index)
    return project.copy(
        screens = currentSpecs.map { it.name },
        screenSpecs = currentSpecs
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InlineThemeStyleEditor(
    project: AppProject,
    onUpdateProject: (AppProject) -> Unit
) {
    val effectiveDesign = remember(project) { designFor(project) }
    val effectiveHex = remember(effectiveDesign.primaryColor) {
        "#%06X".format(Locale.US, effectiveDesign.primaryColor and 0xFFFFFFL)
    }
    var hexInput by remember(project.id, effectiveHex) { mutableStateOf(effectiveHex) }
    val isHexValid = normalizeHexColorInput(hexInput) != null

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("inline_theme_style_editor")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Palette,
                contentDescription = null,
                tint = Color(effectiveDesign.primaryColor),
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "Theme & Design Style",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Text(
            text = "Design Style",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PRESET_STYLE_OPTIONS.forEach { style ->
                val selected = effectiveDesign.designStyle.equals(style, ignoreCase = true)
                FilterChip(
                    selected = selected,
                    onClick = {
                        onUpdateProject(project.copy(designStyle = style))
                    },
                    label = { Text(style) },
                    modifier = Modifier.testTag("edit_design_style_${style.lowercase(Locale.US)}")
                )
            }
        }

        Text(
            text = "Primary Color ($effectiveHex)",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PRESET_COLOR_HEXES.forEach { hex ->
                val parsedColor = Color(0xFF000000L or hex.removePrefix("#").toLong(16))
                val isSelected = effectiveHex.equals(hex, ignoreCase = true)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(parsedColor)
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                            shape = CircleShape
                        )
                        .clickable(role = Role.Button) {
                            hexInput = hex
                            onUpdateProject(project.copy(primaryColorHex = hex))
                        }
                        .testTag("color_swatch_${hex.removePrefix("#")}")
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected color $hex",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        OutlinedTextField(
            value = hexInput,
            onValueChange = { raw ->
                hexInput = raw.take(7)
                normalizeHexColorInput(raw)?.let { normalized ->
                    onUpdateProject(project.copy(primaryColorHex = normalized))
                }
            },
            label = { Text("Primary Color Hex (#RRGGBB)") },
            singleLine = true,
            isError = hexInput.isNotBlank() && !isHexValid,
            supportingText = if (hexInput.isNotBlank() && !isHexValid) {
                { Text("Enter 6 hex digits, e.g. #00ACC1") }
            } else {
                null
            },
            leadingIcon = {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color(effectiveDesign.primaryColor))
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("edit_primary_color_field")
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InlineScreenSpecEditorCard(
    index: Int,
    spec: ScreenSpec,
    newSampleItemDraft: String,
    onNewSampleItemDraftChange: (String) -> Unit,
    onSpecChange: (ScreenSpec) -> Unit,
    onRemoveTab: () -> Unit,
    onDone: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("screen_spec_editor_card")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Editing Tab #${index + 1} (${spec.name})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = onRemoveTab,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.testTag("remove_screen_tab_button")
                    ) {
                        Text("Remove")
                    }
                    TextButton(
                        onClick = onDone,
                        modifier = Modifier.testTag("save_screen_spec_button")
                    ) {
                        Text("Done Editing")
                    }
                }
            }

            OutlinedTextField(
                value = spec.label,
                onValueChange = { updated ->
                    onSpecChange(spec.copy(label = updated.take(16)))
                },
                label = { Text("Tab Label (max 16 chars)") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("edit_screen_label_field")
            )

            OutlinedTextField(
                value = spec.purpose,
                onValueChange = { updated ->
                    onSpecChange(spec.copy(purpose = updated))
                },
                label = { Text("Screen Purpose") },
                minLines = 2,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("edit_screen_purpose_field")
            )

            Text(
                text = "Tab Icon (${spec.icon})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ScreenIcons.keys.forEach { iconKey ->
                    val isIconSelected = spec.icon.equals(iconKey, ignoreCase = true)
                    Surface(
                        color = if (isIconSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        shape = CircleShape,
                        border = BorderStroke(
                            1.dp,
                            if (isIconSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            }
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable(role = Role.Button) {
                                onSpecChange(spec.copy(icon = iconKey))
                            }
                            .testTag("edit_screen_icon_$iconKey")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = ScreenIcons.vector(iconKey),
                                contentDescription = "Select $iconKey icon",
                                tint = if (isIconSelected) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            Text(
                text = "Sample Items (${spec.sampleItems.size})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            spec.sampleItems.forEachIndexed { itemIdx, itemText ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = itemText,
                        onValueChange = { updatedText ->
                            val updatedItems = spec.sampleItems.toMutableList()
                            updatedItems[itemIdx] = updatedText
                            onSpecChange(spec.copy(sampleItems = updatedItems))
                        },
                        label = { Text("Item ${itemIdx + 1}") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("edit_sample_item_field_$itemIdx")
                    )
                    IconButton(
                        onClick = {
                            val updatedItems = spec.sampleItems.toMutableList().apply {
                                removeAt(itemIdx)
                            }
                            onSpecChange(spec.copy(sampleItems = updatedItems))
                        },
                        modifier = Modifier.testTag("remove_sample_item_button_$itemIdx")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove sample item ${itemIdx + 1}",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = newSampleItemDraft,
                    onValueChange = onNewSampleItemDraftChange,
                    label = { Text("Add sample item...") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("new_sample_item_field")
                )
                FilledTonalButton(
                    onClick = {
                        val trimmed = newSampleItemDraft.trim()
                        if (trimmed.isNotEmpty()) {
                            onSpecChange(spec.copy(sampleItems = spec.sampleItems + trimmed))
                            onNewSampleItemDraftChange("")
                        }
                    },
                    enabled = newSampleItemDraft.isNotBlank(),
                    modifier = Modifier.testTag("add_sample_item_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add sample item",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add")
                }
            }
        }
    }
}
