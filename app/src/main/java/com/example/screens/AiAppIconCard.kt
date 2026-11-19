package com.example.screens

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.CodeExporter
import com.example.design.designFor
import com.example.model.AppIconUiState
import com.example.model.AppProject
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AiAppIconGeneratorSection(
    project: AppProject,
    iconState: AppIconUiState,
    onGenerateIcon: (AppProject) -> Unit,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    onGenerateCustomIcon: ((prompt: String, style: String) -> Unit)? = null,
    onOpenIconStudio: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val effectiveStyle = project.designStyle.ifBlank { designFor(project).designStyle }
    var inlinePrompt by rememberSaveable(project.id) { mutableStateOf("") }

    val triggerGeneration: () -> Unit = {
        val custom = inlinePrompt.trim()
        if (custom.isNotEmpty() && onGenerateCustomIcon != null) {
            onGenerateCustomIcon(custom, effectiveStyle)
        } else {
            onGenerateIcon(project)
        }
    }

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("ai_app_icon_section")
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "AI App Icon Studio",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                    Text(
                        text = "Imagen Generator • Auto-adds to project res/drawable/",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (onOpenIconStudio != null) {
                    OutlinedButton(
                        onClick = onOpenIconStudio,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("open_icon_studio_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "Open App Icon Generator",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Studio", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            when (iconState) {
                is AppIconUiState.Idle -> {
                    if (onGenerateCustomIcon != null) {
                        OutlinedTextField(
                            value = inlinePrompt,
                            onValueChange = { inlinePrompt = it },
                            label = { Text("Icon Prompt (Optional)") },
                            placeholder = { Text("Describe symbol & lighting, or leave blank for auto") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ai_icon_custom_prompt_field")
                        )
                    }
                    Button(
                        onClick = triggerGeneration,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentColor,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("generate_ai_icon_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Generate App Icon ($effectiveStyle)",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                is AppIconUiState.Loading -> {
                    val iconStages = remember(project.name, effectiveStyle) {
                        listOf(
                            "Composing \"$effectiveStyle\" concept for ${project.name}...",
                            "Shaping centered vector-inspired emblem...",
                            "Rendering adaptive icon artwork (may take 30–60s)..."
                        )
                    }
                    var iconStageIdx by remember(iconStages) { mutableIntStateOf(0) }
                    LaunchedEffect(iconStages) {
                        while (true) {
                            delay(2200L)
                            iconStageIdx = (iconStageIdx + 1) % iconStages.size
                        }
                    }

                    val iconPulseTransition = rememberInfiniteTransition(label = "icon_pulse")
                    val iconBoxScale by iconPulseTransition.animateFloat(
                        initialValue = 0.94f,
                        targetValue = 1.06f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 950, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "icon_box_scale"
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp)
                            .testTag("ai_icon_loading_indicator"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .scale(iconBoxScale)
                                .clip(RoundedCornerShape(22.dp))
                                .background(accentColor.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = accentColor,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        AnimatedContent(
                            targetState = iconStages[iconStageIdx],
                            transitionSpec = {
                                fadeIn(animationSpec = tween(250)) togetherWith
                                    fadeOut(animationSpec = tween(200))
                            },
                            label = "icon_stage_text"
                        ) { stageLabel ->
                            Text(
                                text = stageLabel,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        LinearProgressIndicator(
                            color = accentColor,
                            trackColor = accentColor.copy(alpha = 0.18f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(50))
                                .testTag("ai_icon_indeterminate_progress_bar")
                        )
                    }
                }

                is AppIconUiState.Success -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            shadowElevation = 6.dp,
                            border = BorderStroke(1.5.dp, accentColor),
                            modifier = Modifier
                                .size(96.dp)
                                .testTag("generated_ai_icon_preview")
                        ) {
                            Image(
                                bitmap = iconState.bitmap.asImageBitmap(),
                                contentDescription = "${project.name} AI App Icon",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "${iconState.styleUsed} • ${iconState.engineUsed}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            if (iconState.drawableResources.isNotEmpty()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Added to Drawables",
                                        tint = accentColor,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Added to ${iconState.drawableResources.first()}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = accentColor
                                    )
                                }
                            }

                            FilledTonalButton(
                                onClick = {
                                    coroutineScope.launch {
                                        val savedResult = saveIconBitmapToDownloads(
                                            context = context,
                                            projectName = project.name,
                                            bitmap = iconState.bitmap
                                        )
                                        savedResult.onSuccess { path ->
                                            Toast.makeText(
                                                context,
                                                "Saved icon: $path",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }.onFailure { err ->
                                            Toast.makeText(
                                                context,
                                                "Save failed: ${err.localizedMessage ?: "Error"}",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                },
                                contentPadding = PaddingValues(vertical = 8.dp, horizontal = 12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("save_ai_icon_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save Icon PNG")
                            }

                            FilledTonalButton(
                                onClick = triggerGeneration,
                                contentPadding = PaddingValues(vertical = 8.dp, horizontal = 12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("regenerate_ai_icon_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Regenerate Icon")
                            }
                        }
                    }
                }

                is AppIconUiState.Error -> {
                    Surface(
                        color = Color(0xFF7F1D1D),
                        border = BorderStroke(1.dp, Color(0xFFEF4444)),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ai_icon_error_banner")
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = "Icon Error",
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = iconState.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFFEE2E2)
                                )
                            }
                            FilledTonalButton(
                                onClick = { onGenerateIcon(project) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("retry_ai_icon_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Retry Icon Generation")
                            }
                        }
                    }
                }
            }
        }
    }
}

internal suspend fun saveIconBitmapToDownloads(
    context: Context,
    projectName: String,
    bitmap: Bitmap
): Result<String> = withContext(Dispatchers.IO) {
    runCatching {
        val safeName = CodeExporter.toSafeFileName(projectName)
        val fileName = "${safeName}_icon.png"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    "${Environment.DIRECTORY_DOWNLOADS}/AiAppStudio"
                )
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                ?: throw IllegalStateException("Could not create MediaStore entry for icon")
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            } ?: throw IllegalStateException("Could not open MediaStore output stream")
            "Downloads/AiAppStudio/$fileName"
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "AiAppStudio"
            ).apply { mkdirs() }
            val outFile = File(dir, fileName)
            FileOutputStream(outFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            "Downloads/AiAppStudio/$fileName"
        }
    }
}
