package com.example.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.apk.SigningOverallStatus
import com.example.apk.SigningSummaryReport
import com.example.model.AppProject
import com.example.model.BuildOptions
import com.example.model.BuildVariant

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BuildConfigurationScreen(
    project: AppProject?,
    onSaveOptions: (BuildOptions) -> Unit,
    onInitiateBuild: (BuildOptions) -> Unit,
    onBack: () -> Unit,
    onOpenLocalAiManager: (() -> Unit)? = null,
    onOpenLintAudit: (() -> Unit)? = null,
    onOpenDependencyGraph: (() -> Unit)? = null,
    onOpenEnvSetup: (() -> Unit)? = null,
    overrideSigningReport: SigningSummaryReport? = null,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val initialOptions = remember(project?.id) { project?.buildOptions ?: BuildOptions() }

    var selectedVariant by remember(initialOptions) { mutableStateOf(initialOptions.variant) }
    var minifyEnabled by remember(initialOptions) { mutableStateOf(initialOptions.minifyEnabled) }
    var crunchPngs by remember(initialOptions) { mutableStateOf(initialOptions.crunchPngs) }
    var includeDebuggable by remember(initialOptions) { mutableStateOf(initialOptions.includeDebuggable) }
    var shrinkResources by remember(initialOptions) { mutableStateOf(initialOptions.shrinkResources) }
    var optimizeBytecode by remember(initialOptions) { mutableStateOf(initialOptions.optimizeBytecode) }
    var targetSdk by remember(initialOptions) { mutableIntStateOf(initialOptions.targetSdk) }
    var versionName by remember(initialOptions) { mutableStateOf(initialOptions.versionName) }
    var versionCodeText by remember(initialOptions) { mutableStateOf(initialOptions.versionCode.toString()) }
    var signingScheme by remember(initialOptions) { mutableStateOf(initialOptions.signingScheme) }
    var selectedTfliteModels by remember(initialOptions) { mutableStateOf(initialOptions.bundledTfliteModels) }
    var signingReport by remember(overrideSigningReport) { mutableStateOf(overrideSigningReport) }

    androidx.compose.runtime.LaunchedEffect(project?.buildOptions?.bundledTfliteModels) {
        project?.buildOptions?.bundledTfliteModels?.let { updated ->
            if (updated != selectedTfliteModels) {
                selectedTfliteModels = updated
            }
        }
    }

    fun currentBuiltOptions(): BuildOptions {
        val parsedCode = versionCodeText.toIntOrNull()?.coerceAtLeast(1) ?: 1
        return BuildOptions(
            buildVariant = selectedVariant.id,
            minifyEnabled = minifyEnabled,
            crunchPngs = crunchPngs,
            includeDebuggable = includeDebuggable,
            shrinkResources = shrinkResources,
            optimizeBytecode = optimizeBytecode,
            targetSdk = targetSdk,
            versionName = versionName.trim().ifBlank { "1.0" },
            versionCode = parsedCode,
            signingScheme = signingScheme,
            bundledTfliteModels = selectedTfliteModels
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
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("build_config_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                    Column {
                        Text(
                            text = "Build Configuration",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("build_config_title")
                        )
                        Text(
                            text = project?.name ?: "Configure APK Build Options",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                TextButton(
                    onClick = {
                        val def = BuildOptions()
                        selectedVariant = def.variant
                        minifyEnabled = def.minifyEnabled
                        crunchPngs = def.crunchPngs
                        includeDebuggable = def.includeDebuggable
                        shrinkResources = def.shrinkResources
                        optimizeBytecode = def.optimizeBytecode
                        targetSdk = def.targetSdk
                        versionName = def.versionName
                        versionCodeText = def.versionCode.toString()
                        signingScheme = def.signingScheme
                        selectedTfliteModels = def.bundledTfliteModels
                        Toast.makeText(context, "Reset to default build configuration", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.testTag("build_config_reset_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reset")
                }
            }

            // Overview & Build Variant Card
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Build Variant",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Select compilation profile & packaging targets",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Variant Chips
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BuildVariant.entries.forEach { variant ->
                            val isSelected = (selectedVariant == variant)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedVariant = variant
                                    // Smart presets for chosen variant
                                    when (variant) {
                                        BuildVariant.RELEASE -> {
                                            minifyEnabled = true
                                            crunchPngs = true
                                            includeDebuggable = false
                                            shrinkResources = true
                                        }
                                        BuildVariant.DEBUG -> {
                                            minifyEnabled = false
                                            crunchPngs = false
                                            includeDebuggable = true
                                            shrinkResources = false
                                        }
                                        BuildVariant.BENCHMARK -> {
                                            minifyEnabled = true
                                            crunchPngs = false
                                            includeDebuggable = false
                                            shrinkResources = false
                                        }
                                    }
                                },
                                label = { Text(variant.displayName) },
                                leadingIcon = if (isSelected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else null,
                                modifier = Modifier.testTag("variant_chip_${variant.id}")
                            )
                        }
                    }

                    // Variant description note
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = when (selectedVariant) {
                                    BuildVariant.DEBUG -> Icons.Default.BugReport
                                    BuildVariant.RELEASE -> Icons.Default.RocketLaunch
                                    BuildVariant.BENCHMARK -> Icons.Default.Speed
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = selectedVariant.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Release Build Settings Panel (Minify Enabled & Crunch PNGs without editing build.gradle.kts)
            ReleaseBuildSettingsPanel(
                minifyEnabled = minifyEnabled,
                crunchPngs = crunchPngs,
                onMinifyEnabledChange = { minifyEnabled = it },
                onCrunchPngsChange = { crunchPngs = it },
                shrinkResources = shrinkResources,
                onEnableMinifyForShrink = { minifyEnabled = true }
            )

            // Resource & Bytecode Optimization Card
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Compress,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Resource & Bytecode Optimization",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Unused asset pruning & compiler optimization passes",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Shrink Resources Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = "Shrink Resources",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Removes unreferenced drawables, layouts, and assets from compiled APK",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = shrinkResources,
                            onCheckedChange = { shrinkResources = it },
                            modifier = Modifier.testTag("toggle_shrink_resources")
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                    // Bytecode Optimization Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = "Bytecode Optimization",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Aggressive inlining, class merging, and method devirtualization",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = optimizeBytecode,
                            onCheckedChange = { optimizeBytecode = it },
                            modifier = Modifier.testTag("toggle_optimize_bytecode")
                        )
                    }
                }
            }

            // Debugging & Diagnostics Card
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.BugReport,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Debugging & Diagnostics",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Runtime inspector flags & diagnostic tools",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Include Debuggable Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = "Include Debuggable Flag",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Sets android:debuggable=\"true\" in AndroidManifest.xml. Enables debugger attach & verbose Logcat.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = includeDebuggable,
                            onCheckedChange = { includeDebuggable = it },
                            modifier = Modifier.testTag("toggle_include_debuggable")
                        )
                    }

                    // Warning banner if release build has debuggable turned on
                    AnimatedVisibility(
                        visible = (selectedVariant == BuildVariant.RELEASE && includeDebuggable),
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            shape = MaterialTheme.shapes.small,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WarningAmber,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Notice: Debuggable release APKs are rejected by app stores. Disable before store distribution.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }

            // Versioning & Target SDK Card
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Versioning & SDK Target",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Target API level, version strings & signature",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Version Name & Code
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = versionName,
                            onValueChange = { versionName = it },
                            label = { Text("Version Name") },
                            placeholder = { Text("1.0.0") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1.5f)
                                .testTag("input_version_name")
                        )
                        OutlinedTextField(
                            value = versionCodeText,
                            onValueChange = { newValue ->
                                if (newValue.all { it.isDigit() }) {
                                    versionCodeText = newValue
                                }
                            },
                            label = { Text("Version Code") },
                            placeholder = { Text("1") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_version_code")
                        )
                    }

                    // Target SDK Chips
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Target SDK Version",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(34 to "API 34 (Android 14)", 35 to "API 35 (Android 15)", 36 to "API 36 (Android 16)").forEach { (api, label) ->
                                val isSelected = (targetSdk == api)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { targetSdk = api },
                                    label = { Text(label) },
                                    modifier = Modifier.testTag("target_sdk_$api")
                                )
                            }
                        }
                    }

                    // Signing Scheme
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "APK Signing Scheme",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("v1+v2" to "V1 + V2 Scheme (Recommended)", "v2" to "V2 Scheme Only").forEach { (scheme, label) ->
                                val isSelected = (signingScheme == scheme)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { signingScheme = scheme },
                                    label = { Text(label) },
                                    modifier = Modifier.testTag("signing_scheme_$scheme")
                                )
                            }
                        }
                    }
                }
            }

            // Signing Summary Card (.jks validation against .env)
            SigningSummaryCard(
                targetVariant = selectedVariant,
                signingScheme = signingScheme,
                overrideReport = overrideSigningReport,
                onValidationReportChanged = { signingReport = it },
                onOpenEnvSetup = onOpenEnvSetup
            )

            // Offline TFLite Models Bundling Card
            TfliteModelSelectorCard(
                selectedModelFileNames = selectedTfliteModels,
                onSelectionChange = { selectedTfliteModels = it },
                onOpenLocalAiManager = onOpenLocalAiManager?.let { openManager ->
                    {
                        onSaveOptions(currentBuiltOptions())
                        openManager()
                    }
                }
            )

            if (onOpenLintAudit != null) {
                OutlinedButton(
                    onClick = {
                        onSaveOptions(currentBuiltOptions())
                        onOpenLintAudit()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("build_config_lint_audit_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.FactCheck,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Run Lint & Audit Scan")
                }
            }

            if (onOpenDependencyGraph != null) {
                OutlinedButton(
                    onClick = {
                        onSaveOptions(currentBuiltOptions())
                        onOpenDependencyGraph()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("build_config_dep_graph_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountTree,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("View Dependency Graph")
                }
            }

            // Live Summary Card
            ElevatedCard(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Build Pipeline Preview",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    val tfliteSummary = if (selectedTfliteModels.isEmpty()) {
                        "None"
                    } else {
                        "${selectedTfliteModels.size} model(s) (${selectedTfliteModels.joinToString(", ")})"
                    }
                    val signingPreviewStatus = when (signingReport?.overallStatus) {
                        SigningOverallStatus.VALID -> "${signingScheme.uppercase()} • ${signingReport?.headline}"
                        SigningOverallStatus.INVALID -> "${signingScheme.uppercase()} • ${signingReport?.headline} (Check Signing Summary)"
                        SigningOverallStatus.WARNING -> "${signingScheme.uppercase()} • ${signingReport?.headline}"
                        null -> "${signingScheme.uppercase()} Scheme with local device RSA-2048 key"
                    }
                    Text(
                        text = "• Variant: ${selectedVariant.displayName.uppercase()}\n" +
                                "• Minify: ${if (minifyEnabled) "ENABLED (R8 code obfuscation & shrinking)" else "DISABLED (Raw bytecode)"}\n" +
                                "• Crunch PNGs: ${if (crunchPngs) "ENABLED (AAPT2 PNG compression)" else "DISABLED (Original PNGs)"}\n" +
                                "• Debuggable: ${if (includeDebuggable) "ENABLED (android:debuggable=true)" else "DISABLED (production mode)"}\n" +
                                "• Shrink Resources: ${if (shrinkResources) "ENABLED" else "DISABLED"}\n" +
                                "• Target: API $targetSdk • Version $versionName ($versionCodeText)\n" +
                                "• Signing: $signingPreviewStatus\n" +
                                "• Bundled TFLite Models: $tfliteSummary",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Action Buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        val options = currentBuiltOptions()
                        onSaveOptions(options)
                        onInitiateBuild(options)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("initiate_build_with_options_button"),
                    contentPadding = PaddingValues(vertical = 14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.RocketLaunch,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Initiate Build (${selectedVariant.displayName})")
                }

                OutlinedButton(
                    onClick = {
                        val options = currentBuiltOptions()
                        onSaveOptions(options)
                        Toast.makeText(context, "Build options saved successfully", Toast.LENGTH_SHORT).show()
                        onBack()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("save_build_options_button"),
                    contentPadding = PaddingValues(vertical = 14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save Options & Return")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
