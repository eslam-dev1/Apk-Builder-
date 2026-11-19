package com.example.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.example.ai.TfliteModelManager
import com.example.data.InstalledDrawableEntry
import com.example.data.ProjectDrawableInstaller
import com.example.model.AppIconUiState
import com.example.model.AppProject
import kotlinx.coroutines.launch

private val ICON_STYLE_PRESETS = listOf(
    "Material You",
    "Minimal Vector",
    "3D Glossy",
    "Flat Geometric",
    "Neon Tech"
)

private val PROMPT_IDEAS = listOf(
    "Minimalist origami crane on deep indigo backdrop",
    "Glowing emerald circuit compass needle",
    "Ceramic coffee cup ribbon vector emblem",
    "Sleek geometric camera shutter prism"
)

private enum class AdaptiveMaskShape(val label: String) {
    SQUIRCLE("Squircle"),
    CIRCLE("Circle"),
    ROUNDED("Rounded"),
    SQUARE("Square")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppIconGeneratorScreen(
    selectedProject: AppProject?,
    availableProjects: List<AppProject>,
    iconStates: Map<String, AppIconUiState>,
    onSelectProject: (String?) -> Unit,
    onGenerateIcon: (
        prompt: String,
        style: String,
        targetProject: AppProject?,
        drawableName: String
    ) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val stateKey = selectedProject?.id ?: ProjectDrawableInstaller.DEFAULT_WORKSPACE_ID

    var promptText by rememberSaveable(stateKey) {
        mutableStateOf(
            if (selectedProject != null) {
                "Distinctive emblem for \"${selectedProject.name}\": ${selectedProject.description.take(120)}"
            } else {
                ""
            }
        )
    }
    var selectedStyle by rememberSaveable(stateKey) {
        mutableStateOf(selectedProject?.designStyle?.takeIf { it.isNotBlank() } ?: "Material You")
    }
    var drawableNameInput by rememberSaveable(stateKey) {
        mutableStateOf(
            selectedProject?.iconDrawableName?.takeIf { it.isNotBlank() }
                ?: ProjectDrawableInstaller.DEFAULT_DRAWABLE_NAME
        )
    }
    var maskShape by remember { mutableStateOf(AdaptiveMaskShape.SQUIRCLE) }
    var showXmlPreview by remember { mutableStateOf(false) }

    var diskInstalledEntries by remember(stateKey) {
        mutableStateOf(emptyList<InstalledDrawableEntry>())
    }
    var diskBitmap by remember(stateKey) { mutableStateOf<Bitmap?>(null) }

    val activeState = iconStates[stateKey] ?: AppIconUiState.Idle

    LaunchedEffect(stateKey, activeState) {
        diskInstalledEntries = ProjectDrawableInstaller.listInstalledDrawableEntries(context, stateKey)
        if (activeState !is AppIconUiState.Success) {
            diskBitmap = ProjectDrawableInstaller.loadInstalledIconBitmap(
                context = context,
                projectId = stateKey,
                preferredDrawableName = selectedProject?.iconDrawableName.orEmpty()
            )
        }
    }

    val previewBitmap: Bitmap? = when (activeState) {
        is AppIconUiState.Success -> activeState.bitmap
        else -> diskBitmap
    }
    val previewBgHex = when {
        activeState is AppIconUiState.Success && activeState.backgroundHex.isNotBlank() ->
            activeState.backgroundHex
        selectedProject?.iconBackgroundHex?.isNotBlank() == true ->
            selectedProject.iconBackgroundHex
        previewBitmap != null -> ProjectDrawableInstaller.sampleCornerBackgroundHex(previewBitmap)
        else -> "#0B1120"
    }
    val sanitizedDrawable = ProjectDrawableInstaller.sanitizeDrawableName(drawableNameInput)

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("icon_generator_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "App Icon Generator",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("icon_generator_title")
                    )
                    Text(
                        text = "Imagen Studio • Auto-adds to res/drawable & adaptive mipmap XML",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Target Project Selector
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Target Project Resources",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedProject == null,
                            onClick = { onSelectProject(null) },
                            label = { Text("Workspace Draft") },
                            modifier = Modifier.testTag("icon_target_project_default")
                        )
                        availableProjects.forEach { proj ->
                            FilterChip(
                                selected = selectedProject?.id == proj.id,
                                onClick = { onSelectProject(proj.id) },
                                label = {
                                    Text(
                                        text = proj.name,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                modifier = Modifier.testTag("icon_target_project_${proj.id}")
                            )
                        }
                    }
                }
            }

            // Prompt & Drawable Name Card
            Card(
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = promptText,
                        onValueChange = { promptText = it },
                        label = { Text("Imagen Launcher Icon Prompt") },
                        placeholder = {
                            Text("e.g., Minimalist cobalt shield with glowing geometric spark")
                        },
                        minLines = 3,
                        maxLines = 5,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("icon_generator_prompt_field")
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PROMPT_IDEAS.forEachIndexed { idx, idea ->
                            AssistChip(
                                onClick = { promptText = idea },
                                label = { Text(idea, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                modifier = Modifier.testTag("icon_prompt_idea_$idx")
                            )
                        }
                    }

                    Text(
                        text = "Visual Style",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ICON_STYLE_PRESETS.forEach { style ->
                            FilterChip(
                                selected = selectedStyle == style,
                                onClick = { selectedStyle = style },
                                label = { Text(style) },
                                modifier = Modifier.testTag("icon_style_chip_${style.lowercase().replace(' ', '_')}")
                            )
                        }
                    }

                    OutlinedTextField(
                        value = drawableNameInput,
                        onValueChange = { drawableNameInput = it },
                        label = { Text("Drawable Resource Name") },
                        supportingText = {
                            Text("Saves as res/drawable/$sanitizedDrawable.png + adaptive XMLs")
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("icon_drawable_name_field")
                    )

                    Button(
                        onClick = {
                            onGenerateIcon(
                                promptText,
                                selectedStyle,
                                selectedProject,
                                sanitizedDrawable
                            )
                        },
                        enabled = activeState !is AppIconUiState.Loading && promptText.isNotBlank(),
                        contentPadding = PaddingValues(vertical = 14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("icon_generator_submit_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (activeState is AppIconUiState.Loading) {
                                "Generating with Imagen..."
                            } else {
                                "Generate Icon & Add to Drawables"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Loading / Error States
            if (activeState is AppIconUiState.Loading) {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("icon_generator_loading")
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(34.dp))
                        Text(
                            text = "Synthesizing launcher icon via Imagen & installing drawables...",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            } else if (activeState is AppIconUiState.Error) {
                Surface(
                    color = Color(0xFF7F1D1D),
                    border = BorderStroke(1.dp, Color(0xFFEF4444)),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("icon_generator_error")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = "Generation Error",
                            tint = Color(0xFFEF4444)
                        )
                        Text(
                            text = activeState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFEE2E2),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Live Adaptive Preview & Auto-Added Project Drawable Resources
            if (previewBitmap != null) {
                val clipShape = when (maskShape) {
                    AdaptiveMaskShape.SQUIRCLE -> RoundedCornerShape(28.dp)
                    AdaptiveMaskShape.CIRCLE -> CircleShape
                    AdaptiveMaskShape.ROUNDED -> RoundedCornerShape(18.dp)
                    AdaptiveMaskShape.SQUARE -> RectangleShape
                }

                Card(
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("icon_generator_preview")
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Adaptive Launcher Preview",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            val engineBadge = (activeState as? AppIconUiState.Success)?.engineUsed ?: "Saved Drawable"
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(50)
                            ) {
                                Text(
                                    text = engineBadge,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Surface(
                                shape = clipShape,
                                shadowElevation = 6.dp,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                                modifier = Modifier.size(104.dp)
                            ) {
                                Image(
                                    bitmap = previewBitmap.asImageBitmap(),
                                    contentDescription = "Generated Adaptive Launcher Icon",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Bg Tint: $previewBgHex",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontFamily = FontFamily.Monospace
                                )
                                FilledTonalButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            val namePrefix = selectedProject?.name ?: sanitizedDrawable
                                            saveIconBitmapToDownloads(context, namePrefix, previewBitmap)
                                                .onSuccess { savedPath ->
                                                    Toast.makeText(context, "Saved: $savedPath", Toast.LENGTH_LONG).show()
                                                }.onFailure { err ->
                                                    Toast.makeText(
                                                        context,
                                                        "Save failed: ${err.localizedMessage ?: "Error"}",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("icon_studio_save_downloads_button")
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Export PNG")
                                }

                                FilledTonalButton(
                                    onClick = {
                                        if (promptText.isNotBlank()) {
                                            onGenerateIcon(promptText, selectedStyle, selectedProject, sanitizedDrawable)
                                        }
                                    },
                                    enabled = activeState !is AppIconUiState.Loading,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Regenerate")
                                }
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AdaptiveMaskShape.entries.forEach { shapeOption ->
                                FilterChip(
                                    selected = maskShape == shapeOption,
                                    onClick = { maskShape = shapeOption },
                                    label = { Text(shapeOption.label) }
                                )
                            }
                        }
                    }
                }
            }

            if (diskInstalledEntries.isNotEmpty()) {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("icon_added_drawables_card")
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
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Auto Added",
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Added to Project Drawables (${diskInstalledEntries.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            IconButton(
                                onClick = { showXmlPreview = !showXmlPreview },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Code,
                                    contentDescription = "Toggle Adaptive XML Preview"
                                )
                            }
                        }

                        diskInstalledEntries.forEach { entry ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                                        MaterialTheme.shapes.small
                                    )
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (entry.relativePath.endsWith(".png")) Icons.Default.Image else Icons.Default.Code,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = entry.relativePath,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "${entry.kindLabel} • ${TfliteModelManager.formatBytes(entry.sizeBytes)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        if (showXmlPreview) {
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = MaterialTheme.shapes.small,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = ProjectDrawableInstaller.buildAdaptiveForegroundXml(sanitizedDrawable) +
                                        "\n" + ProjectDrawableInstaller.buildAdaptiveBackgroundXml(previewBgHex),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
