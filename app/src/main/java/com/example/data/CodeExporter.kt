package com.example.data

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import com.example.design.ScreenSpecs
import com.example.design.designFor
import com.example.model.AppProject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class ScreenTemplateKind {
    FILE_EXPLORER,
    APK_ANALYZER,
    MANIFEST_EDITOR,
    SHOP_STORE,
    FITNESS_WORKOUT,
    CHAT_MESSAGE,
    DASHBOARD_HOME,
    DEFAULT_FEATURES
}

object CodeExporter {

    fun detectScreenKind(screenName: String): ScreenTemplateKind {
        val tokens = ScreenSpecs.tokens(screenName)
        return when {
            matchesAny(tokens, "explorer", "file", "storage", "directory", "folder") ->
                ScreenTemplateKind.FILE_EXPLORER
            matchesAny(tokens, "analyzer", "apk", "package", "inspector") ->
                ScreenTemplateKind.APK_ANALYZER
            matchesAny(tokens, "manifest", "editor", "xml", "code") ->
                ScreenTemplateKind.MANIFEST_EDITOR
            matchesAny(tokens, "shop", "store", "cart", "product", "order") ->
                ScreenTemplateKind.SHOP_STORE
            matchesAny(tokens, "workout", "fitness", "progress", "nutrition", "gym") ->
                ScreenTemplateKind.FITNESS_WORKOUT
            matchesAny(tokens, "chat", "message", "contact", "call", "inbox") ->
                ScreenTemplateKind.CHAT_MESSAGE
            matchesAny(tokens, "dashboard", "home", "overview") ->
                ScreenTemplateKind.DASHBOARD_HOME
            else -> ScreenTemplateKind.DEFAULT_FEATURES
        }
    }

    private fun matchesAny(tokens: List<String>, vararg keywords: String): Boolean {
        return tokens.any { token ->
            keywords.any { kw ->
                token == kw ||
                    token == "${kw}s" ||
                    (kw.length >= 5 && token.startsWith(kw))
            }
        }
    }

    internal fun escapeKt(text: String): String {
        val sb = StringBuilder(text.length + 8)
        for (ch in text) {
            when (ch) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '$' -> sb.append("\\$")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    internal fun oneLine(text: String): String =
        text.replace(Regex("[\\r\\n]+"), " ").trim()

    fun buildProjectCode(project: AppProject): String {
        val sanitizedScreens = getSanitizedScreenNames(project)
        val appDesign = designFor(project)

        return buildString {
            appendLine("// ==========================================")
            appendLine("// Project: ${oneLine(project.name)}")
            appendLine("// Description: ${oneLine(project.description)}")
            appendLine("// Design Style: ${oneLine(appDesign.designStyle)} | Layout: ${oneLine(appDesign.layoutType)} | Card: ${oneLine(appDesign.cardStyle)}")
            appendLine("// Original Prompt: ${oneLine(project.prompt)}")
            appendLine("//")
            appendLine("// Features:")
            if (project.features.isEmpty()) {
                appendLine("// - Core Android UI")
            } else {
                project.features.forEach { feature ->
                    appendLine("// - ${oneLine(feature)}")
                }
            }
            appendLine("// ==========================================")
            appendLine()
            appendLine(buildScreensFileContent(project, sanitizedScreens))
        }.trimEnd()
    }

    /**
     * Generates an in-memory ZIP archive containing 4 small, clean files:
     * 1. AppProject.kt
     * 2. ProjectScreens.kt
     * 3. ProjectViewModel.kt
     * 4. README.md
     */
    fun buildProjectZip(project: AppProject): ByteArray {
        val sanitizedScreens = getSanitizedScreenNames(project)
        val filesMap = linkedMapOf(
            "AppProject.kt" to buildModelFileContent(project),
            "ProjectScreens.kt" to buildScreensFileContent(project, sanitizedScreens),
            "ProjectViewModel.kt" to buildViewModelFileContent(project, sanitizedScreens),
            "README.md" to buildReadmeFileContent(project, sanitizedScreens)
        )

        val byteOut = ByteArrayOutputStream()
        ZipOutputStream(byteOut).use { zipOut ->
            filesMap.forEach { (fileName, content) ->
                val entry = ZipEntry(fileName)
                zipOut.putNextEntry(entry)
                zipOut.write(content.toByteArray(Charsets.UTF_8))
                zipOut.closeEntry()
            }
        }
        return byteOut.toByteArray()
    }

    private fun buildModelFileContent(project: AppProject): String {
        val appDesign = designFor(project)
        val escapedName = escapeKt(project.name)
        val escapedDesc = escapeKt(project.description)
        val featuresLiteral = project.features.joinToString(", ") { "\"${escapeKt(it)}\"" }
        val screensLiteral = project.screens.joinToString(", ") { "\"${escapeKt(it)}\"" }

        return buildString {
            appendLine("package com.example.generated")
            appendLine()
            appendLine("data class AppProject(")
            appendLine("    val name: String = \"$escapedName\",")
            appendLine("    val description: String = \"$escapedDesc\",")
            appendLine("    val designStyle: String = \"${escapeKt(appDesign.designStyle)}\",")
            appendLine("    val layoutType: String = \"${escapeKt(appDesign.layoutType)}\",")
            appendLine("    val cardStyle: String = \"${escapeKt(appDesign.cardStyle)}\",")
            appendLine("    val features: List<String> = listOf($featuresLiteral),")
            appendLine("    val screens: List<String> = listOf($screensLiteral)")
            appendLine(")")
        }.trimEnd()
    }

    private fun buildScreensFileContent(
        project: AppProject,
        sanitizedScreens: List<String>
    ): String {
        val appDesign = designFor(project)
        val primaryHex = appDesign.toHexLiteral(appDesign.primaryColor)
        val secondaryHex = appDesign.toHexLiteral(appDesign.secondaryColor)
        val backgroundHex = appDesign.toHexLiteral(appDesign.backgroundColor)

        return buildString {
            appendLine("package com.example.generated")
            appendLine()
            appendLine("import androidx.compose.foundation.BorderStroke")
            appendLine("import androidx.compose.foundation.background")
            appendLine("import androidx.compose.foundation.layout.*")
            appendLine("import androidx.compose.foundation.lazy.LazyColumn")
            appendLine("import androidx.compose.foundation.lazy.grid.GridCells")
            appendLine("import androidx.compose.foundation.lazy.grid.LazyVerticalGrid")
            appendLine("import androidx.compose.foundation.lazy.grid.items")
            appendLine("import androidx.compose.foundation.lazy.items")
            appendLine("import androidx.compose.material.icons.Icons")
            appendLine("import androidx.compose.material.icons.filled.Description")
            appendLine("import androidx.compose.material3.*")
            appendLine("import androidx.compose.runtime.*")
            appendLine("import androidx.compose.ui.Alignment")
            appendLine("import androidx.compose.ui.Modifier")
            appendLine("import androidx.compose.ui.graphics.Color")
            appendLine("import androidx.compose.ui.unit.dp")
            appendLine()
            appendLine("// AppDesign Theme Constants (${appDesign.designStyle} / ${appDesign.layoutType} / ${appDesign.cardStyle})")
            appendLine("val PrimaryDesignColor = Color($primaryHex)")
            appendLine("val SecondaryDesignColor = Color($secondaryHex)")
            appendLine("val BackgroundDesignColor = Color($backgroundHex)")
            appendLine()

            val resolvedSpecs = ScreenSpecs.all(project)
            sanitizedScreens.forEachIndexed { idx, screenFunName ->
                val matchedSpec = resolvedSpecs.firstOrNull {
                    toComposableIdentifier(it.name, idx + 1) == screenFunName
                } ?: resolvedSpecs.getOrNull(idx)
                appendLine(generateComposableForScreen(project, screenFunName, matchedSpec))
                appendLine()
            }
        }.trimEnd()
    }

    private fun generateComposableForScreen(
        project: AppProject,
        screenFunName: String,
        spec: com.example.model.ScreenSpec?
    ): String {
        val escapedProjectName = escapeKt(project.name)
        val escapedDescription = escapeKt(project.description)
        val screenLabel = escapeKt(spec?.label?.ifBlank { null } ?: ScreenSpecs.prettyLabel(screenFunName))
        val screenPurpose = escapeKt(spec?.purpose?.ifBlank { null } ?: project.description)
        val featureItems = when {
            spec != null && spec.sampleItems.isNotEmpty() -> spec.sampleItems
            project.features.isNotEmpty() -> project.features
            else -> listOf("Core Feature 1", "Core Feature 2", "Core Feature 3")
        }
        val featuresLiteral = featureItems.joinToString(", ") { "\"${escapeKt(it)}\"" }

        return when (detectScreenKind(screenFunName)) {
            ScreenTemplateKind.FILE_EXPLORER -> """
                @Composable
                fun $screenFunName(modifier: Modifier = Modifier) {
                    var searchQuery by remember { mutableStateOf("") }
                    val files = remember {
                        listOf(
                            "AndroidManifest.xml" to "12 KB",
                            "classes.dex" to "4.2 MB",
                            "resources.arsc" to "860 KB",
                            "config.json" to "4 KB",
                            "README.md" to "8 KB"
                        )
                    }
                    val filtered = files.filter { it.first.contains(searchQuery, ignoreCase = true) }
                    Column(
                        modifier = modifier.fillMaxSize().background(BackgroundDesignColor).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            label = { Text("Search files") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Card(border = BorderStroke(1.dp, PrimaryDesignColor), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Storage Usage: 42.5 GB / 64 GB", color = PrimaryDesignColor, style = MaterialTheme.typography.titleSmall)
                                LinearProgressIndicator(progress = { 0.66f }, color = PrimaryDesignColor, modifier = Modifier.fillMaxWidth())
                            }
                        }
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(filtered) { (fileName, fileSize) ->
                                Card(border = BorderStroke(1.dp, SecondaryDesignColor), modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Description, contentDescription = null, tint = PrimaryDesignColor)
                                            Text(fileName, style = MaterialTheme.typography.bodyLarge)
                                        }
                                        Text(fileSize, color = PrimaryDesignColor, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
            """.trimIndent()

            ScreenTemplateKind.APK_ANALYZER -> """
                @Composable
                fun $screenFunName(modifier: Modifier = Modifier) {
                    var statusText by remember { mutableStateOf("Select an APK to inspect") }
                    val apks = remember {
                        listOf(
                            Triple("$escapedProjectName", "com.example.generated", "8.4 MB"),
                            Triple("System File Tools", "com.android.filetools", "5.1 MB"),
                            Triple("Media Player Pro", "com.example.mediaplayer", "12.7 MB")
                        )
                    }
                    Column(
                        modifier = modifier.fillMaxSize().background(BackgroundDesignColor).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(statusText, style = MaterialTheme.typography.titleSmall, color = PrimaryDesignColor)
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(apks) { (appName, pkgName, size) ->
                                Card(border = BorderStroke(1.dp, PrimaryDesignColor), modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("${'$'}appName (${'$'}size)", style = MaterialTheme.typography.titleMedium)
                                        Text(pkgName, style = MaterialTheme.typography.bodySmall)
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(
                                                onClick = { statusText = "Analyzed: " + appName },
                                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryDesignColor)
                                            ) { Text("Analyze") }
                                            OutlinedButton(onClick = { statusText = "Extracted: " + pkgName }) {
                                                Text("Extract", color = PrimaryDesignColor)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            """.trimIndent()

            ScreenTemplateKind.MANIFEST_EDITOR -> """
                @Composable
                fun $screenFunName(modifier: Modifier = Modifier) {
                    var manifestXml by remember {
                        mutableStateOf(
                            "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                            "    package=\"com.example.generated\">\n" +
                            "    <application android:label=\"$escapedProjectName\" />\n" +
                            "</manifest>"
                        )
                    }
                    var status by remember { mutableStateOf("Ready to validate XML") }
                    Column(
                        modifier = modifier.fillMaxSize().background(BackgroundDesignColor).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Manifest XML Editor", style = MaterialTheme.typography.titleMedium, color = PrimaryDesignColor)
                        OutlinedTextField(
                            value = manifestXml,
                            onValueChange = { manifestXml = it },
                            minLines = 6,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { status = "Manifest saved (${'$'}{manifestXml.length} chars)" },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryDesignColor)
                            ) { Text("Save Manifest") }
                            OutlinedButton(onClick = { status = if (manifestXml.contains("<manifest")) "Valid XML" else "Invalid XML" }) {
                                Text("Validate", color = PrimaryDesignColor)
                            }
                        }
                        Card(border = BorderStroke(1.dp, SecondaryDesignColor), modifier = Modifier.fillMaxWidth()) {
                            Text(status, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            """.trimIndent()

            ScreenTemplateKind.SHOP_STORE -> {
                val productPairsLiteral = if (spec != null && spec.sampleItems.isNotEmpty()) {
                    spec.sampleItems.mapIndexed { idx, item ->
                        val parts = item.split(" • ", limit = 2)
                        val title = escapeKt(parts.first().trim())
                        val price = escapeKt(parts.getOrNull(1)?.trim() ?: "$${(idx + 1) * 24 + 15}.00")
                        "\"$title\" to \"$price\""
                    }.joinToString(",\n                            ")
                } else {
                    """
                            "Pro Wireless Headphones" to "${'$'}129.00",
                            "Minimalist Everyday Backpack" to "${'$'}74.50",
                            "Smart Fitness WatchBand" to "${'$'}39.99",
                            "Portable USB-C Hub" to "${'$'}49.00",
                            "Insulated Travel Bottle" to "${'$'}28.00"
                    """.trimIndent()
                }
                """
                @Composable
                fun $screenFunName(modifier: Modifier = Modifier) {
                    var cartCount by remember { mutableStateOf(0) }
                    val products = remember {
                        listOf(
                            $productPairsLiteral
                        )
                    }
                    Column(
                        modifier = modifier.fillMaxSize().background(BackgroundDesignColor).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("$escapedProjectName • $screenLabel (Cart: ${'$'}cartCount)", color = PrimaryDesignColor, style = MaterialTheme.typography.titleMedium)
                        Text("$screenPurpose", style = MaterialTheme.typography.bodySmall)
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(products) { (name, price) ->
                                Card(border = BorderStroke(1.dp, PrimaryDesignColor), modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(name, style = MaterialTheme.typography.bodyMedium)
                                        Text(price, style = MaterialTheme.typography.titleSmall, color = PrimaryDesignColor)
                                        Button(
                                            onClick = { cartCount++ },
                                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryDesignColor),
                                            modifier = Modifier.fillMaxWidth()
                                        ) { Text("Add to Cart") }
                                    }
                                }
                            }
                        }
                    }
                }
                """.trimIndent()
            }

            ScreenTemplateKind.FITNESS_WORKOUT -> {
                val workoutItemsLiteral = if (spec != null && spec.sampleItems.isNotEmpty()) {
                    spec.sampleItems.joinToString(",\n                            ") { "\"${escapeKt(it)}\"" }
                } else {
                    """
                            "Full Body HIIT Circuit - 25 min",
                            "Upper Body Strength - 35 min",
                            "Core & Mobility Flow - 15 min",
                            "Endurance Tempo Run - 30 min"
                    """.trimIndent()
                }
                """
                @Composable
                fun $screenFunName(modifier: Modifier = Modifier) {
                    var progress by remember { mutableStateOf(0.65f) }
                    val workouts = remember {
                        listOf(
                            $workoutItemsLiteral
                        )
                    }
                    Column(
                        modifier = modifier.fillMaxSize().background(BackgroundDesignColor).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Card(border = BorderStroke(1.dp, PrimaryDesignColor), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("$screenLabel • Goal Progress: ${'$'}{(progress * 100).toInt()}%", color = PrimaryDesignColor, style = MaterialTheme.typography.titleSmall)
                                Text("$screenPurpose", style = MaterialTheme.typography.bodySmall)
                                LinearProgressIndicator(progress = { progress }, color = PrimaryDesignColor, modifier = Modifier.fillMaxWidth())
                            }
                        }
                        Button(
                            onClick = { progress = (progress + 0.1f).coerceAtMost(1f) },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryDesignColor),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Start Workout") }
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(workouts) { workout ->
                                Card(border = BorderStroke(1.dp, SecondaryDesignColor), modifier = Modifier.fillMaxWidth()) {
                                    Text(workout, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }
                }
                """.trimIndent()
            }

            ScreenTemplateKind.CHAT_MESSAGE -> {
                val chatSeedLiteral = if (spec != null && spec.sampleItems.isNotEmpty()) {
                    spec.sampleItems.mapIndexed { idx, msg ->
                        "${idx % 2 == 1} to \"${escapeKt(msg)}\""
                    }.joinToString(",\n                                ")
                } else {
                    """
                                false to "Alex: Welcome to $escapedProjectName!",
                                true to "You: Ready for today's update!",
                                false to "Jordan: Shared the latest design notes.",
                                true to "You: Looks great, let's ship it!"
                    """.trimIndent()
                }
                """
                @Composable
                fun $screenFunName(modifier: Modifier = Modifier) {
                    var draft by remember { mutableStateOf("") }
                    var messages by remember {
                        mutableStateOf(
                            listOf(
                                $chatSeedLiteral
                            )
                        )
                    }
                    Column(
                        modifier = modifier.fillMaxSize().background(BackgroundDesignColor).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("$screenLabel • $screenPurpose", style = MaterialTheme.typography.labelMedium, color = PrimaryDesignColor)
                        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(messages) { (isMe, msg) ->
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart
                                ) {
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isMe) PrimaryDesignColor else MaterialTheme.colorScheme.surface
                                        ),
                                        border = BorderStroke(1.dp, PrimaryDesignColor)
                                    ) {
                                        Text(msg, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = draft,
                                onValueChange = { draft = it },
                                placeholder = { Text("Type a message...") },
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    if (draft.isNotBlank()) {
                                        messages = messages + (true to ("You: " + draft))
                                        draft = ""
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryDesignColor)
                            ) { Text("Send") }
                        }
                    }
                }
                """.trimIndent()
            }

            ScreenTemplateKind.DASHBOARD_HOME -> """
                @Composable
                fun $screenFunName(modifier: Modifier = Modifier) {
                    val itemsList = remember { listOf($featuresLiteral) }
                    Column(
                        modifier = modifier.fillMaxSize().background(BackgroundDesignColor).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = PrimaryDesignColor),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("$escapedProjectName • $screenLabel", color = Color.White, style = MaterialTheme.typography.headlineSmall)
                                Text("$screenPurpose", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                                LinearProgressIndicator(progress = { 0.65f }, color = Color.White, modifier = Modifier.fillMaxWidth())
                            }
                        }
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(itemsList) { entry ->
                                Card(border = BorderStroke(1.dp, PrimaryDesignColor), modifier = Modifier.fillMaxWidth()) {
                                    Text(entry, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }
                }
            """.trimIndent()

            ScreenTemplateKind.DEFAULT_FEATURES -> """
                @Composable
                fun $screenFunName(modifier: Modifier = Modifier) {
                    var selectedItem by remember { mutableStateOf("$screenLabel") }
                    val itemsList = remember { listOf($featuresLiteral) }
                    Column(
                        modifier = modifier.fillMaxSize().background(BackgroundDesignColor).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("$screenLabel", color = PrimaryDesignColor, style = MaterialTheme.typography.titleMedium)
                        Text("$screenPurpose", style = MaterialTheme.typography.bodyMedium)
                        LazyColumn(modifier = Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(itemsList) { entry ->
                                Card(border = BorderStroke(1.dp, PrimaryDesignColor), modifier = Modifier.fillMaxWidth()) {
                                    Text(entry, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                        Button(
                            onClick = { selectedItem = itemsList.firstOrNull() ?: "$escapedProjectName" },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryDesignColor),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Open Details (" + selectedItem.take(18) + ")")
                        }
                    }
                }
            """.trimIndent()
        }
    }

    private fun buildViewModelFileContent(
        project: AppProject,
        sanitizedScreens: List<String>
    ): String {
        val defaultScreen = sanitizedScreens.firstOrNull() ?: "MainScreen"
        return buildString {
            appendLine("package com.example.generated")
            appendLine()
            appendLine("import androidx.lifecycle.ViewModel")
            appendLine("import kotlinx.coroutines.flow.MutableStateFlow")
            appendLine("import kotlinx.coroutines.flow.StateFlow")
            appendLine("import kotlinx.coroutines.flow.asStateFlow")
            appendLine()
            appendLine("class ProjectViewModel : ViewModel() {")
            appendLine("    private val _project = MutableStateFlow(AppProject())")
            appendLine("    val project: StateFlow<AppProject> = _project.asStateFlow()")
            appendLine()
            appendLine("    private val _currentScreen = MutableStateFlow(\"$defaultScreen\")")
            appendLine("    val currentScreen: StateFlow<String> = _currentScreen.asStateFlow()")
            appendLine()
            appendLine("    fun selectScreen(screenName: String) {")
            appendLine("        _currentScreen.value = screenName")
            appendLine("    }")
            appendLine("}")
        }.trimEnd()
    }

    private fun buildReadmeFileContent(
        project: AppProject,
        sanitizedScreens: List<String>
    ): String {
        val appDesign = designFor(project)
        return buildString {
            appendLine("# ${project.name}")
            appendLine()
            appendLine("${project.description}")
            appendLine()
            appendLine("- **Design Style**: ${appDesign.designStyle}")
            appendLine("- **Layout Type**: ${appDesign.layoutType}")
            appendLine("- **Card Style**: ${appDesign.cardStyle}")
            appendLine("- **Primary Color**: `${appDesign.toHexLiteral(appDesign.primaryColor)}`")
            appendLine()
            appendLine("## Features")
            if (project.features.isEmpty()) {
                appendLine("- Core Jetpack Compose UI")
            } else {
                project.features.forEach { appendLine("- $it") }
            }
            appendLine()
            appendLine("## Included Files")
            appendLine("1. `AppProject.kt` - Data class holding the project metadata, design system config, features, and screens.")
            appendLine("2. `ProjectScreens.kt` - Functional Jetpack Compose `@Composable` screens styled with `AppDesign` color constants (${sanitizedScreens.joinToString(", ")}).")
            appendLine("3. `ProjectViewModel.kt` - MVVM `ViewModel` managing project and screen state via `StateFlow`.")
            appendLine("4. `README.md` - Setup and usage guide.")
            appendLine()
            appendLine("## How to Use")
            appendLine("1. Open your Android Studio Kotlin + Jetpack Compose project.")
            appendLine("2. Copy `AppProject.kt`, `ProjectScreens.kt`, and `ProjectViewModel.kt` into your package directory.")
            appendLine("3. Call the generated screen composables from your `MainActivity` or `NavHost`.")
        }.trimEnd()
    }

    fun copyToClipboard(context: Context, project: AppProject) {
        val code = buildProjectCode(project)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("${project.name} Code", code))
        Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show()
    }

    fun shareCode(context: Context, project: AppProject) {
        val code = buildProjectCode(project)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "${project.name}.kt")
            putExtra(Intent.EXTRA_TEXT, code)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Kotlin Code"))
    }

    suspend fun saveToDownloads(context: Context, project: AppProject): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val code = buildProjectCode(project)
                val safeFileName = toSafeFileName(project.name) + ".kt"
                saveBytesToDownloads(
                    context = context,
                    fileName = safeFileName,
                    mimeType = "text/plain",
                    bytes = code.toByteArray(Charsets.UTF_8)
                )
            }
        }

    suspend fun saveZipToDownloads(context: Context, project: AppProject): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val zipBytes = buildProjectZip(project)
                val safeZipName = toSafeFileName(project.name) + ".zip"
                saveBytesToDownloads(
                    context = context,
                    fileName = safeZipName,
                    mimeType = "application/zip",
                    bytes = zipBytes
                )
            }
        }

    private fun saveBytesToDownloads(
        context: Context,
        fileName: String,
        mimeType: String,
        bytes: ByteArray
    ): String {
        val relativeSubPath = "${Environment.DIRECTORY_DOWNLOADS}/AiAppStudio"

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, relativeSubPath)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }

            val uri = resolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                contentValues
            ) ?: throw IllegalStateException("Could not create file in Downloads")

            resolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(bytes)
                outputStream.flush()
            } ?: throw IllegalStateException("Could not open file output stream")

            val completeValues = ContentValues().apply {
                put(MediaStore.Downloads.IS_PENDING, 0)
            }
            resolver.update(uri, completeValues, null, null)
            "Downloads/AiAppStudio/$fileName"
        } else {
            val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: context.filesDir
            val targetDir = File(baseDir, "AiAppStudio").apply { mkdirs() }
            val outFile = File(targetDir, fileName)
            outFile.writeBytes(bytes)
            "Downloads/AiAppStudio/${outFile.name}"
        }
    }

    private fun getSanitizedScreenNames(project: AppProject): List<String> {
        return if (project.screens.isEmpty()) {
            listOf("MainScreen")
        } else {
            project.screens.mapIndexed { index, raw ->
                toComposableIdentifier(raw, fallbackIndex = index + 1)
            }.distinct()
        }
    }

    internal fun toComposableIdentifier(rawName: String, fallbackIndex: Int): String {
        val words = rawName
            .replace(Regex("[^A-Za-z0-9]+"), " ")
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

        val pascal = words.joinToString("") { word ->
            word.replaceFirstChar { ch -> ch.uppercaseChar() }
        }

        val cleaned = if (pascal.isEmpty() || !pascal.first().isLetter()) {
            "Screen$fallbackIndex$pascal"
        } else {
            pascal
        }

        return if (cleaned.endsWith("Screen")) cleaned else "${cleaned}Screen"
    }

    internal fun toSafeFileName(rawName: String): String {
        val cleaned = rawName
            .replace(Regex("[^A-Za-z0-9]+"), " ")
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString("") { word ->
                word.replaceFirstChar { ch -> ch.uppercaseChar() }
            }
        return cleaned.ifBlank { "GeneratedApp" }
    }
}
