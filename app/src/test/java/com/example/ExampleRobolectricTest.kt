package com.example

import android.content.Context
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.data.CodeExporter
import com.example.data.GeminiService
import com.example.model.AppIconUiState
import com.example.model.AppProject
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Ai App Studio", appName)
    }

    @Test
    fun `buildProjectZip contains 4 expected files`() {
        val project = AppProject(
            id = "p1",
            name = "FitTrack",
            description = "Workout tracking app",
            prompt = "Fitness tracker",
            createdAt = 1000L,
            features = listOf("Interval timer", "Streak calendar"),
            screens = listOf("Home", "Workout Session")
        )

        val zipBytes = CodeExporter.buildProjectZip(project)
        val entryNames = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                entryNames.add(entry.name)
                entry = zis.nextEntry
            }
        }

        assertEquals(
            listOf("AppProject.kt", "ProjectScreens.kt", "ProjectViewModel.kt", "README.md"),
            entryNames
        )
        assertTrue(zipBytes.isNotEmpty())
    }

    @Test
    fun `apkBuilder builds aligned and v1 v2 signed apk verified by ApkVerifier`() = kotlinx.coroutines.test.runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val project = AppProject(
            id = "p2",
            name = "APKFileMaster",
            description = "File explorer and APK manager",
            prompt = "File explorer",
            createdAt = 1000L,
            features = listOf("Directory browser", "APK package inspector"),
            screens = listOf("FileExplorerScreen", "ApkScreen", "StorageScreen")
        )

        val steps = mutableListOf<String>()
        val result = com.example.apk.ApkBuilder.buildApk(project, context) { step ->
            steps.add(step)
        }

        assertTrue("APK build should succeed: ${result.exceptionOrNull()}", result.isSuccess)
        val signedApk = result.getOrThrow()
        assertTrue(signedApk.exists())
        assertEquals(
            listOf("Copying template...", "Injecting config...", "Aligning...", "Signing...", "Verifying...", "Done"),
            steps
        )

        val verifierResult = com.android.apksig.ApkVerifier.Builder(signedApk).build().verify()
        assertTrue("APK must pass ApkVerifier", verifierResult.isVerified)
        assertTrue("v1 signing must be verified", verifierResult.isVerifiedUsingV1Scheme)
        assertTrue("v2 signing must be verified", verifierResult.isVerifiedUsingV2Scheme)
    }

    @Test
    fun `codeExporter generates functional compose screens for keywords`() {
        val project = AppProject(
            id = "p3",
            name = "FileVault",
            description = "File manager and APK inspector",
            prompt = "File explorer and APK analyzer",
            createdAt = 1000L,
            features = listOf("Directory browser", "APK package analyzer"),
            screens = listOf(
                "FileExplorerScreen",
                "ApkAnalyzerScreen",
                "ManifestEditorScreen",
                "ShopCartScreen",
                "WorkoutProgressScreen",
                "ChatMessageScreen"
            )
        )

        val generatedCode = CodeExporter.buildProjectCode(project)
        assertTrue(generatedCode.contains("val PrimaryDesignColor = Color(0xFF"))
        assertTrue(generatedCode.contains("val SecondaryDesignColor = Color(0xFF"))
        assertTrue(generatedCode.contains("val BackgroundDesignColor = Color(0xFF"))
        assertTrue(generatedCode.contains("LazyColumn"))
        assertTrue(generatedCode.contains("OutlinedTextField"))
        assertTrue(generatedCode.contains("LinearProgressIndicator"))
        assertTrue(generatedCode.contains("Save Manifest"))
        assertTrue(generatedCode.contains("Add to Cart"))
        assertTrue(generatedCode.contains("Start Workout"))
    }

    @Test
    fun `designFor selects expected colors and layout types per app category`() {
        val storeProject = AppProject(
            id = "d1",
            name = "ShopPulse",
            description = "E-commerce clothes store",
            prompt = "Clothes shop",
            createdAt = 1000L
        )
        val storeDesign = com.example.design.designFor(storeProject)
        assertEquals(0xFFFF6B00L, storeDesign.primaryColor)
        assertEquals("GridProducts", storeDesign.layoutType)
        assertEquals("LargeImageCard", storeDesign.cardStyle)

        val fitnessProject = AppProject(
            id = "d2",
            name = "FitPulse",
            description = "Gym workout tracker",
            prompt = "Fitness app",
            createdAt = 1000L
        )
        val fitnessDesign = com.example.design.designFor(fitnessProject)
        assertEquals(0xFFE53935L, fitnessDesign.primaryColor)
        assertEquals("ProgressList", fitnessDesign.layoutType)
        assertEquals("DarkStatCard", fitnessDesign.cardStyle)

        val customHexProject = storeProject.copy(
            designStyle = "Playful",
            primaryColorHex = "#00ACC1"
        )
        val customDesign = com.example.design.designFor(customHexProject)
        assertEquals(0xFF00ACC1L, customDesign.primaryColor)
        assertEquals("Playful", customDesign.designStyle)
    }

    @Test
    fun `detectScreenKind does not classify ProfileScreen as FILE_EXPLORER`() {
        val kind = CodeExporter.detectScreenKind("ProfileScreen")
        org.junit.Assert.assertNotEquals(
            com.example.data.ScreenTemplateKind.FILE_EXPLORER,
            kind
        )
        assertEquals(
            com.example.data.ScreenTemplateKind.FILE_EXPLORER,
            CodeExporter.detectScreenKind("FileExplorerScreen")
        )
    }

    @Test
    fun `escapeKt escapes dollar backslash quotes and newlines`() {
        val raw = "Price: \$99\\item\n\"Special\"\tTab"
        val escaped = CodeExporter.escapeKt(raw)
        assertEquals("Price: \\\$99\\\\item\\n\\\"Special\\\"\\tTab", escaped)
    }

    @Test
    fun `axmlPatcher replaces manifest package activity and label on base apk`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val rawManifest = context.assets.open("base.apk").use { input ->
            val tempApk = java.io.File.createTempFile("base_test", ".apk", context.cacheDir)
            tempApk.outputStream().use { input.copyTo(it) }
            val bytes = java.util.zip.ZipFile(tempApk).use { zf ->
                zf.getInputStream(zf.getEntry("AndroidManifest.xml")).use { it.readBytes() }
            }
            tempApk.delete()
            bytes
        }

        val patched = com.example.apk.AxmlPatcher.replaceStrings(
            rawManifest,
            mapOf(
                "com.example.aiappstudio.generatedapp" to "com.aiappstudio.gen.pshop.h123abc",
                ".MainActivity" to "com.example.aiappstudio.generatedapp.MainActivity",
                "Generated App" to "My Custom Store"
            )
        )

        val strings = com.example.apk.AxmlPatcher.readStrings(patched)
        assertTrue(strings.contains("com.aiappstudio.gen.pshop.h123abc"))
        assertTrue(strings.contains("com.example.aiappstudio.generatedapp.MainActivity"))
        assertTrue(strings.contains("My Custom Store"))
        org.junit.Assert.assertFalse(strings.contains("com.example.aiappstudio.generatedapp"))
        org.junit.Assert.assertFalse(strings.contains("Generated App"))
    }

    @Test
    fun `building twice with same project yields certificates with identical SHA256 and encrypted key is not raw PKCS8`() =
        kotlinx.coroutines.test.runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val project = AppProject(
                id = "stable-sign-id",
                name = "OfflineSignTest",
                description = "Offline APK signing test",
                prompt = "Offline test",
                createdAt = 1000L,
                features = listOf("Feature A"),
                screens = listOf("HomeScreen")
            )

            val firstApk = com.example.apk.ApkBuilder.buildApk(project, context).getOrThrow()
            val firstCert = com.android.apksig.ApkVerifier.Builder(firstApk).build().verify()
                .signerCertificates.first()
            val firstSha256 = java.security.MessageDigest.getInstance("SHA-256")
                .digest(firstCert.encoded)
                .joinToString(":") { "%02X".format(java.util.Locale.US, it) }

            val secondApk = com.example.apk.ApkBuilder.buildApk(project, context).getOrThrow()
            val secondCert = com.android.apksig.ApkVerifier.Builder(secondApk).build().verify()
                .signerCertificates.first()
            val secondSha256 = java.security.MessageDigest.getInstance("SHA-256")
                .digest(secondCert.encoded)
                .joinToString(":") { "%02X".format(java.util.Locale.US, it) }

            assertEquals("Signing certificate SHA-256 must be identical across builds", firstSha256, secondSha256)
            assertEquals(firstSha256, com.example.apk.ApkBuilder.getSigningCertSha256Fingerprint(context))

            // (c) Verify encrypted key file on disk is NOT a valid raw PKCS8 key
            val encryptedKeyFile = java.io.File(
                java.io.File(context.filesDir, "apk_signing"),
                com.example.apk.ApkBuilder.ENCRYPTED_KEY_FILE_NAME
            )
            assertTrue("Encrypted key file must exist", encryptedKeyFile.exists())
            val rawEncryptedBytes = encryptedKeyFile.readBytes()
            val rawPkcs8Parse = runCatching {
                java.security.KeyFactory.getInstance("RSA")
                    .generatePrivate(java.security.spec.PKCS8EncodedKeySpec(rawEncryptedBytes))
            }
            assertTrue(
                "Encrypted key file must NOT parse as raw plaintext PKCS#8",
                rawPkcs8Parse.isFailure
            )
        }

    @Test
    fun `apk package and CodeExporter contain no java net or HttpURLConnection references`() {
        val candidateRoots = listOf(
            java.io.File("src/main/java"),
            java.io.File("app/src/main/java")
        )
        val srcRoot = candidateRoots.firstOrNull { it.exists() }
            ?: error("Could not locate src/main/java directory")

        val filesToInspect = listOf(
            java.io.File(srcRoot, "com/example/apk/ApkBuilder.kt"),
            java.io.File(srcRoot, "com/example/apk/AxmlPatcher.kt"),
            java.io.File(srcRoot, "com/example/data/CodeExporter.kt")
        )

        for (file in filesToInspect) {
            assertTrue("Expected source file to exist: ${file.path}", file.exists())
            val text = file.readText()
            org.junit.Assert.assertFalse(
                "${file.name} must not reference java.net",
                text.contains("java.net.")
            )
            org.junit.Assert.assertFalse(
                "${file.name} must not reference HttpURLConnection",
                text.contains("HttpURLConnection")
            )
            org.junit.Assert.assertFalse(
                "${file.name} must not reference firebase",
                text.lowercase(java.util.Locale.US).contains("firebase")
            )
        }
    }

    @Test
    fun `modelCatalog verifies Gemma size and ModelManager strips Qwen3 think tags`() {
        val gemma = com.example.data.ai.ModelCatalog.GEMMA_4_E2B
        assertEquals(2_588_147_712L, gemma.expectedBytes)
        assertTrue(
            "Gemma 4 E2B exact byte size must pass verification",
            com.example.ai.ModelManager.verifyExpectedSize(2_588_147_712L, gemma.expectedBytes)
        )
        org.junit.Assert.assertFalse(
            "Truncated Gemma file must fail 1% size check",
            com.example.ai.ModelManager.verifyExpectedSize(1_000_000_000L, gemma.expectedBytes)
        )

        val qwenOutputWithThink = """
            <think>
            Let me design a shopping app with 4 tabs...
            { "ignored": true }
            </think>
            {"name": "QuickMart", "description": "Fast store"}
        """.trimIndent()
        val extracted = com.example.ai.ModelManager.extractJsonFromModelOutput(qwenOutputWithThink)
        assertEquals("{\"name\": \"QuickMart\", \"description\": \"Fast store\"}", extracted)

        // Verify ModelStatusScreen status resolution for Downloaded, Downloading, Ready
        val downloadingLabel = com.example.screens.resolveModelStateLabel(
            isInstalled = false,
            isSelectedReady = false,
            partialBytes = 1024L,
            progress = com.example.ai.ModelDownloadProgress(
                modelId = "gemma4_e2b",
                status = com.example.ai.DownloadStatus.DOWNLOADING
            )
        )
        assertEquals("Downloading", downloadingLabel.label)

        val downloadedLabel = com.example.screens.resolveModelStateLabel(
            isInstalled = true,
            isSelectedReady = false,
            partialBytes = 0L,
            progress = null
        )
        assertEquals("Downloaded", downloadedLabel.label)

        val readyLabel = com.example.screens.resolveModelStateLabel(
            isInstalled = true,
            isSelectedReady = true,
            partialBytes = 0L,
            progress = null
        )
        assertEquals("Ready", readyLabel.label)
    }

    @Test
    fun `modelManager manages priority download queue reordering and global progress`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.example.ai.ModelManager.clearQueue(context)
        com.example.ai.ModelManager.refreshState(context)

        val models = com.example.data.ai.ModelCatalog.ALL_MODELS
        com.example.ai.ModelManager.setQueueOrderForTest(models)

        assertEquals(
            listOf("gemma4_e2b", "qwen3_06b", "qwen3_06b_nothink"),
            com.example.ai.ModelManager.snapshotQueueIds()
        )

        // Move qwen3_06b_nothink up from priority 3 -> 2
        com.example.ai.ModelManager.moveQueueItemUp("qwen3_06b_nothink")
        assertEquals(
            listOf("gemma4_e2b", "qwen3_06b_nothink", "qwen3_06b"),
            com.example.ai.ModelManager.snapshotQueueIds()
        )

        // Move qwen3_06b_nothink down back to priority 3
        com.example.ai.ModelManager.moveQueueItemDown("qwen3_06b_nothink")
        assertEquals(
            listOf("gemma4_e2b", "qwen3_06b", "qwen3_06b_nothink"),
            com.example.ai.ModelManager.snapshotQueueIds()
        )

        val queueState = com.example.ai.ModelManager.downloadQueue.value
        assertEquals(3, queueState.size)
        assertEquals(1, queueState[0].priority)
        assertEquals(2, queueState[1].priority)
        assertEquals(3, queueState[2].priority)

        val global = com.example.ai.ModelManager.globalProgress.value
        assertEquals(3, global.totalQueuedCount)
        assertTrue(global.totalBytesOverall >= com.example.data.ai.ModelCatalog.GEMMA_4_E2B.expectedBytes!!)

        // Removing an item updates priority numbers cleanly
        com.example.ai.ModelManager.removeFromQueue(context, "qwen3_06b")
        assertEquals(
            listOf("gemma4_e2b", "qwen3_06b_nothink"),
            com.example.ai.ModelManager.snapshotQueueIds()
        )
        com.example.ai.ModelManager.clearQueue(context)
        assertEquals(0, com.example.ai.ModelManager.downloadQueue.value.size)
    }

    @Test
    fun `modelDownloadService builds progress and pause notifications and declares dataSync service`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

        com.example.ai.ModelDownloadService.ensureNotificationChannel(context)
        val channel = nm.getNotificationChannel(com.example.ai.ModelDownloadService.CHANNEL_ID)
        org.junit.Assert.assertNotNull("model_downloads channel should exist", channel)

        val downloadingNotif = com.example.ai.ModelDownloadService.buildProgressNotification(
            context = context,
            progress = com.example.ai.ModelDownloadProgress(
                modelId = "gemma4_e2b",
                fileName = "gemma-4-E2B-it.litertlm",
                status = com.example.ai.DownloadStatus.DOWNLOADING,
                bytesDownloaded = 1_000_000_000L,
                totalBytes = 2_000_000_000L,
                speedBytesPerSec = 10L * 1024L * 1024L,
                etaSeconds = 95L
            ),
            global = com.example.ai.GlobalDownloadProgress(
                activeModelId = "gemma4_e2b",
                activeModelTitle = "Gemma 4 E2B",
                totalQueuedCount = 2,
                fraction = 0.5f,
                isDownloading = true
            )
        )

        val notifTitle = downloadingNotif.extras.getString(android.app.Notification.EXTRA_TITLE).orEmpty()
        val notifText = downloadingNotif.extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString().orEmpty()
        assertTrue(notifTitle.contains("Gemma 4 E2B"))
        assertTrue(notifTitle.contains("2 queued"))
        assertTrue(notifText.contains("50%"))
        assertTrue(notifText.contains("MB/s"))
        assertTrue(notifText.contains("ETA 1m 35s"))
        val actionTitles = downloadingNotif.actions.map { it.title.toString() }
        assertEquals(listOf("Pause", "Cancel"), actionTitles)

        val pausedNotif = com.example.ai.ModelDownloadService.buildProgressNotification(
            context = context,
            progress = com.example.ai.ModelDownloadProgress(
                modelId = "gemma4_e2b",
                fileName = "gemma-4-E2B-it.litertlm",
                status = com.example.ai.DownloadStatus.PAUSED,
                bytesDownloaded = 1_000_000_000L,
                totalBytes = 2_000_000_000L
            ),
            global = com.example.ai.GlobalDownloadProgress(
                activeModelId = "gemma4_e2b",
                activeModelTitle = "Gemma 4 E2B",
                totalQueuedCount = 1,
                isPaused = true
            )
        )
        assertEquals(listOf("Resume", "Cancel"), pausedNotif.actions.map { it.title.toString() })

        val component = android.content.ComponentName(context, com.example.ai.ModelDownloadService::class.java)
        val serviceInfo = context.packageManager.getServiceInfo(component, 0)
        assertEquals(
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            serviceInfo.foregroundServiceType
        )
    }

    @Test
    fun `inline screen spec and theme edits propagate to viewModel, designFor, and CodeExporter`() {
        val baseProject = AppProject(
            id = "edit-1",
            name = "TravelMate",
            description = "Trip planner and itinerary organizer",
            prompt = "Travel itinerary app",
            createdAt = 1000L,
            features = listOf("Flight tracker", "Packing list"),
            screens = listOf("TripsScreen", "ExploreScreen", "SavedScreen")
        )

        val viewModel = com.example.viewmodel.GenerateViewModel()
        assertEquals("#00ACC1", com.example.screens.normalizeHexColorInput("00acc1"))
        org.junit.Assert.assertNull(com.example.screens.normalizeHexColorInput("#ZZZZZZ"))

        val updatedSpecProject = com.example.screens.withUpdatedScreenSpec(
            project = baseProject,
            index = 1
        ) { spec ->
            spec.copy(
                label = "Discover",
                icon = "flight",
                purpose = "Find flights and curated getaways",
                sampleItems = listOf("Tokyo Cherry Blossom", "Amalfi Coast Pass")
            )
        }.copy(
            designStyle = "Playful",
            primaryColorHex = "#EC4899"
        )

        viewModel.updateProject(updatedSpecProject)

        val stored = viewModel.getProjectById("edit-1")
        org.junit.Assert.assertNotNull(stored)
        val specs = com.example.design.ScreenSpecs.of(stored!!)
        assertEquals("Discover", specs[1].label)
        assertEquals("flight", specs[1].icon)
        assertEquals("Find flights and curated getaways", specs[1].purpose)
        assertEquals(listOf("Tokyo Cherry Blossom", "Amalfi Coast Pass"), specs[1].sampleItems)

        val design = com.example.design.designFor(stored)
        assertEquals(0xFFEC4899L, design.primaryColor)
        assertEquals("Playful", design.designStyle)

        val exportedCode = CodeExporter.buildProjectCode(stored)
        assertTrue(exportedCode.contains("0xFFEC4899"))
    }

    @Test
    fun `modelManager persists download queue across restart and ModelDownloadService handles actions and timeout`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.example.ai.ModelManager.clearQueue(context)
        com.example.ai.ModelManager.refreshState(context)

        val models = listOf(
            com.example.data.ai.ModelCatalog.GEMMA_4_E2B,
            com.example.data.ai.ModelCatalog.QWEN3_06B_NOTHINK
        )
        com.example.ai.ModelManager.setQueueOrderForTest(models)
        assertEquals(listOf("gemma4_e2b", "qwen3_06b_nothink"), com.example.ai.ModelManager.snapshotQueueIds())

        // Simulate process death & restart
        com.example.ai.ModelManager.resetQueueForRestartSimulation()
        assertEquals(emptyList<String>(), com.example.ai.ModelManager.snapshotQueueIds())

        com.example.ai.ModelManager.restorePersistedQueueIfNeeded(context)
        assertEquals(listOf("gemma4_e2b", "qwen3_06b_nothink"), com.example.ai.ModelManager.snapshotQueueIds())

        // Verify service handles ACTION_PAUSE, onTimeout, and ACTION_CANCEL_ALL safely
        val controller = org.robolectric.Robolectric.buildService(
            com.example.ai.ModelDownloadService::class.java,
            android.content.Intent(context, com.example.ai.ModelDownloadService::class.java).apply {
                action = com.example.ai.ModelDownloadService.ACTION_PAUSE
            }
        ).create().startCommand(0, 1)

        assertTrue(com.example.ai.ModelManager.globalProgress.value.isPaused)

        controller.get().onTimeout(1, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        assertTrue(com.example.ai.ModelManager.globalProgress.value.isPaused)

        val cancelController = org.robolectric.Robolectric.buildService(
            com.example.ai.ModelDownloadService::class.java,
            android.content.Intent(context, com.example.ai.ModelDownloadService::class.java).apply {
                action = com.example.ai.ModelDownloadService.ACTION_CANCEL_ALL
            }
        ).create().startCommand(0, 2)
        org.junit.Assert.assertNotNull(cancelController.get())
        assertEquals(0, com.example.ai.ModelManager.downloadQueue.value.size)

        val component = android.content.ComponentName(context, com.example.ai.ModelDownloadService::class.java)
        val serviceInfo = context.packageManager.getServiceInfo(component, 0)
        assertEquals(0, serviceInfo.flags and android.content.pm.ServiceInfo.FLAG_STOP_WITH_TASK)
    }

    @Test
    fun `screenSpecs repairs legacy empty or invalid icons without falling back to widgets`() {
        val legacyProject = AppProject(
            id = "legacy-1",
            name = "LegacyFit",
            description = "Old saved fitness project",
            prompt = "fitness",
            createdAt = 1000L,
            features = listOf("Workout log", "Macro chart"),
            screens = listOf("WorkoutsScreen", "ProgressScreen", "NutritionScreen", "ProfileScreen"),
            screenSpecs = listOf(
                com.example.model.ScreenSpec("WorkoutsScreen", "Workouts", "", ""),
                com.example.model.ScreenSpec("ProgressScreen", "Progress", "widgets", ""),
                com.example.model.ScreenSpec("NutritionScreen", "Nutrition", "unknown_icon", ""),
                com.example.model.ScreenSpec("ProfileScreen", "Profile", "", "")
            )
        )
        val repaired = com.example.design.ScreenSpecs.all(legacyProject)
        assertEquals(listOf("fitness", "chart", "restaurant", "person"), repaired.map { it.icon })
        assertTrue(repaired.all { com.example.design.ScreenIcons.isValid(it.icon) })
        assertTrue(repaired.all { it.sampleItems.isNotEmpty() })
    }

    @Test
    fun `aiGenerationLoadingCard and icon loading display indeterminate progress bar and animated stages`() {
        composeTestRule.mainClock.autoAdvance = false

        val project = AppProject(
            id = "ui-load-1",
            name = "FitPulse",
            description = "Workout tracker",
            prompt = "Fitness app",
            createdAt = 1000L
        )

        composeTestRule.setContent {
            com.example.ui.theme.MyApplicationTheme {
                androidx.compose.foundation.layout.Column {
                    com.example.screens.AiGenerationLoadingCard(
                        title = "Generating App with Gemini AI",
                        stages = listOf(
                            "Analyzing your app idea...",
                            "Designing screen tabs..."
                        ),
                        progressBarTestTag = "create_indeterminate_progress_bar"
                    )
                    com.example.screens.AiAppIconGeneratorSection(
                        project = project,
                        iconState = AppIconUiState.Loading,
                        onGenerateIcon = {}
                    )
                }
            }
        }

        composeTestRule.mainClock.advanceTimeBy(100L)
        composeTestRule.onNodeWithText("Generating App with Gemini AI").assertIsDisplayed()
        composeTestRule.onNodeWithText("Analyzing your app idea...").assertIsDisplayed()
        composeTestRule.onNodeWithTag("create_indeterminate_progress_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ai_icon_loading_indicator").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ai_icon_indeterminate_progress_bar").assertIsDisplayed()

        // Advancing past stage interval transitions to the next status text
        composeTestRule.mainClock.advanceTimeBy(2000L)
        composeTestRule.onNodeWithText("Designing screen tabs...").assertIsDisplayed()
    }

    @Test
    fun `configureAppCheck skips cleanly without error log when Firebase is unconfigured`() {
        org.robolectric.shadows.ShadowLog.clear()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val launchIntent = android.content.Intent(context, MainActivity::class.java).apply {
            putExtra("FIREBASE_APPCHECK_DEBUG_TOKEN", "test-debug-token")
        }

        com.example.util.configureAppCheck(context, launchIntent)

        val appCheckErrors = org.robolectric.shadows.ShadowLog.getLogsForTag("AppCheckInit")
            .filter { it.type == android.util.Log.ERROR }
        assertTrue(
            "AppCheckInit must not log ERROR when Firebase is not configured: $appCheckErrors",
            appCheckErrors.isEmpty()
        )

        val controller = org.robolectric.Robolectric.buildActivity(MainActivity::class.java, launchIntent).create()
        org.junit.Assert.assertNotNull(controller.get())
        val activityLaunchErrors = org.robolectric.shadows.ShadowLog.getLogsForTag("AppCheckInit")
            .filter { it.type == android.util.Log.ERROR }
        assertTrue(
            "MainActivity.onCreate must not log AppCheckInit errors: $activityLaunchErrors",
            activityLaunchErrors.isEmpty()
        )
    }

    @Test
    fun `kotlinSyntaxHighlighter preserves text and styles Kotlin tokens accurately`() {
        val sampleCode = """
            // Generated Compose Screen
            @Composable
            fun HomeScreen(modifier: Modifier = Modifier) {
                val count = remember { mutableStateOf(0) }
                val hex = 0xFF6366F1L
                val title = "Hello ${'$'}count items"
                Text(text = title, modifier = modifier)
            }
        """.trimIndent()

        val theme = com.example.design.KotlinSyntaxTheme.Dark
        val highlighted = com.example.design.KotlinSyntaxHighlighter.highlight(sampleCode, theme)

        assertEquals(sampleCode, highlighted.text)
        assertTrue("Highlighted AnnotatedString must contain span styles", highlighted.spanStyles.isNotEmpty())

        fun colorAt(token: String): androidx.compose.ui.graphics.Color? {
            val start = sampleCode.indexOf(token)
            val end = start + token.length
            return highlighted.spanStyles
                .lastOrNull { it.start <= start && it.end >= end }
                ?.item
                ?.color
        }

        assertEquals(theme.comment, colorAt("// Generated Compose Screen"))
        assertEquals(theme.annotation, colorAt("@Composable"))
        assertEquals(theme.keyword, colorAt("fun"))
        assertEquals(theme.keyword, colorAt("val"))
        assertEquals(theme.type, colorAt("Modifier"))
        assertEquals(theme.functionCall, colorAt("remember"))
        assertEquals(theme.numberOrLiteral, colorAt("0xFF6366F1L"))
        assertEquals(theme.string, colorAt("\"Hello "))
        assertEquals(theme.stringInterpolation, colorAt("${'$'}count"))
        assertEquals(theme.namedParameter, colorAt("text"))
    }

    @Test
    fun `kotlinCodeBlock displays filename line count syntax highlighted code and action toggles`() {
        val sampleCode = (1..15).joinToString("\n") { idx ->
            "val item$idx = \"Screen $idx\""
        }

        composeTestRule.setContent {
            com.example.ui.theme.MyApplicationTheme {
                com.example.screens.KotlinCodeBlock(
                    code = sampleCode,
                    fileName = "SampleScreens.kt",
                    codeTestTag = "test_code_block",
                    initiallyExpanded = false,
                    collapsedMaxHeight = androidx.compose.ui.unit.Dp(180f)
                )
            }
        }

        composeTestRule.onNodeWithText("SampleScreens.kt").assertIsDisplayed()
        composeTestRule.onNodeWithText("15 lines").assertIsDisplayed()
        composeTestRule.onNodeWithTag("test_code_block").assertIsDisplayed()
        composeTestRule.onNodeWithTag("test_code_block_theme_toggle").assertIsDisplayed()
        composeTestRule.onNodeWithTag("test_code_block_expand_toggle").assertIsDisplayed()
        composeTestRule.onNodeWithTag("test_code_block_copy_button").assertIsDisplayed()
        composeTestRule.onNodeWithText("Copy").assertIsDisplayed()

        composeTestRule.onNodeWithTag("test_code_block_copy_button")
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Copied").assertIsDisplayed()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        assertEquals(sampleCode, clipboard.primaryClip?.getItemAt(0)?.text?.toString())
    }

    @Test
    fun `generatedCodeAnalyzer reports clean valid exported code and flags broken Kotlin issues`() {
        val validProject = AppProject(
            id = "clean-1",
            name = "CleanNotes",
            description = "Simple note organizer",
            prompt = "Notes app",
            createdAt = 1000L,
            features = listOf("Quick note", "Search tags"),
            screens = listOf("NotesScreen", "TagsScreen", "SettingsScreen")
        )
        val validCode = CodeExporter.buildProjectCode(validProject)
        val validIssues = com.example.data.GeneratedCodeAnalyzer.analyze(validCode)
        assertTrue(
            "Valid exported CodeExporter code should not have errors or warnings: $validIssues",
            validIssues.isEmpty()
        )

        val brokenCode = """
            @Composable
            fun HomeScreen() {
                val color = Color(0xFF1234)
                var count by mutableStateOf(0)
                val label = "Unclosed string
                Icon(imageVector = Icons.Default.Info)
            }
            @Composable
            fun HomeScreen() {
                // duplicate function and missing closing brace
        """.trimIndent()

        val brokenIssues = com.example.data.GeneratedCodeAnalyzer.analyze(brokenCode)
        val titles = brokenIssues.map { it.title }
        assertTrue("Expected unclosed string error: $titles", titles.any { it.contains("Unclosed String Literal") })
        assertTrue("Expected unclosed brace error: $titles", titles.any { it.contains("Unclosed '{' Block") })
        assertTrue("Expected missing Compose runtime import: $titles", titles.any { it.contains("Missing Compose Runtime Import") })
        assertTrue("Expected duplicate function error: $titles", titles.any { it.contains("Duplicate Function 'HomeScreen'") })
        assertTrue("Expected incomplete color hex warning: $titles", titles.any { it.contains("Incomplete Color ARGB Hex") })
        assertTrue("Expected state lost on recomposition warning: $titles", titles.any { it.contains("State Lost on Recomposition") })
        assertTrue("All broken issues must include corrective suggestions", brokenIssues.all { it.suggestion.isNotBlank() })
    }

    @Test
    fun `geminiService parses code analysis JSON and merges with static findings`() {
        val staticIssue = com.example.model.CodeIssueFix(
            title = "Missing Package Declaration",
            severity = com.example.model.CodeIssueSeverity.WARNING,
            lineHint = "Line 1",
            problem = "No package declaration.",
            suggestion = "Add package com.example.generated"
        )
        val aiPayloadJson = org.json.JSONObject().apply {
            put("summary", "Found 1 runtime improvement in LazyColumn keys.")
            put(
                "issues",
                org.json.JSONArray().put(
                    org.json.JSONObject().apply {
                        put("title", "Missing LazyColumn Keys")
                        put("severity", "WARNING")
                        put("lineHint", "Line 28")
                        put("problem", "Items lack stable key lambdas.")
                        put("suggestion", "Provide key = { it } in items(...) to preserve scroll state.")
                        put("fixedSnippet", "items(itemsList, key = { it.id }) { item -> }")
                    }
                )
            )
        }.toString()

        val geminiEnvelope = org.json.JSONObject().apply {
            put(
                "candidates",
                org.json.JSONArray().put(
                    org.json.JSONObject().put(
                        "content",
                        org.json.JSONObject().put(
                            "parts",
                            org.json.JSONArray().put(org.json.JSONObject().put("text", aiPayloadJson))
                        )
                    )
                )
            )
        }.toString()

        val result = GeminiService().parseCodeAnalysisResponse(geminiEnvelope, listOf(staticIssue))
        assertTrue(result.usedGeminiAi)
        assertEquals("Found 1 runtime improvement in LazyColumn keys.", result.summary)
        assertEquals(2, result.issues.size)
        assertEquals("Missing Package Declaration", result.issues[0].title)
        assertEquals("Missing LazyColumn Keys", result.issues[1].title)
        assertEquals("Gemini AI", result.issues[1].source)
        assertEquals("items(itemsList, key = { it.id }) { item -> }", result.issues[1].fixedSnippet)
    }

    @Test
    fun `codeAnalysisSection displays idle analyze button and renders issues with fix snippets`() {
        var clickedCount = 0
        val sampleFix = com.example.model.CodeIssueFix(
            title = "State Lost on Recomposition",
            severity = com.example.model.CodeIssueSeverity.WARNING,
            lineHint = "Line 14",
            problem = "mutableStateOf(0) resets during recomposition.",
            suggestion = "Wrap state creation inside remember { ... }.",
            fixedSnippet = "var count by remember { mutableStateOf(0) }"
        )
        val successState = com.example.model.CodeAnalysisUiState.Success(
            com.example.model.CodeAnalysisResult(
                issues = listOf(sampleFix),
                summary = "Detected 1 Compose state issue with a suggested fix.",
                usedGeminiAi = false,
                aiNotice = "Static analysis completed."
            )
        )

        val stateHolder = androidx.compose.runtime.mutableStateOf<com.example.model.CodeAnalysisUiState>(
            com.example.model.CodeAnalysisUiState.Idle
        )

        composeTestRule.setContent {
            com.example.ui.theme.MyApplicationTheme {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    com.example.screens.CodeAnalysisSection(
                        analysisState = stateHolder.value,
                        onAnalyzeCode = {
                            clickedCount++
                            stateHolder.value = successState
                        },
                        tagPrefix = "test"
                    )
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .testTag("test_bottom_anchor")
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("test_analyze_code_button").assertIsDisplayed()
        composeTestRule.onNodeWithText("Analyze Code Errors & Suggest Fixes").assertIsDisplayed()

        composeTestRule.onNodeWithTag("test_analyze_code_button").performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, clickedCount)
        composeTestRule.onNodeWithTag("test_code_analysis_results").assertIsDisplayed()
        composeTestRule.onNodeWithText("1 Issue(s) Found").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Static Check").assertCountEquals(2)
        composeTestRule.onNodeWithTag("test_code_issue_0").assertIsDisplayed()
        composeTestRule.onNodeWithText("State Lost on Recomposition").assertIsDisplayed()
        composeTestRule.onNodeWithText("Suggested Fix").assertIsDisplayed()
        composeTestRule.onNodeWithTag("test_bottom_anchor").performScrollTo()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("SuggestedFix.kt").assertIsDisplayed()
        composeTestRule.onNodeWithTag("test_code_issue_fix_0").assertIsDisplayed()
    }

    @Test
    fun `cloudAiConfig persists OpenRouter key, provider, model, and offlineMode and OpenRouterService extracts JSON`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.example.data.CloudAiConfig.resetForTest()
        com.example.data.CloudAiConfig.clearSavedOpenRouterApiKey(context)

        com.example.data.CloudAiConfig.setProvider(context, com.example.data.CloudAiProvider.OPENROUTER)
        com.example.data.CloudAiConfig.setSavedOpenRouterApiKey(context, "sk-or-v1-test-key-123")
        com.example.data.CloudAiConfig.setOpenRouterModel(context, "openai/gpt-4o-mini")
        com.example.data.CloudAiConfig.setOfflineMode(context, true)

        com.example.data.CloudAiConfig.resetForTest()
        com.example.data.CloudAiConfig.load(context)

        assertEquals(com.example.data.CloudAiProvider.OPENROUTER, com.example.data.CloudAiConfig.currentProvider.value)
        assertEquals("sk-or-v1-test-key-123", com.example.data.CloudAiConfig.effectiveOpenRouterApiKey())
        assertEquals("openai/gpt-4o-mini", com.example.data.CloudAiConfig.openRouterModel.value)
        assertTrue(com.example.data.CloudAiConfig.isOfflineMode.value)
        org.junit.Assert.assertFalse(com.example.data.CloudAiConfig.isOpenRouterKeyMissing())

        val openRouterEnvelope = org.json.JSONObject().apply {
            put(
                "choices",
                org.json.JSONArray().put(
                    org.json.JSONObject().put(
                        "message",
                        org.json.JSONObject().put(
                            "content",
                            "<think>planning</think>\n{\"name\":\"PocketChef\",\"description\":\"Smart meal planner\",\"features\":[\"Recipes\"],\"screens\":[{\"name\":\"HomeScreen\",\"label\":\"Home\",\"icon\":\"home\",\"purpose\":\"Browse meals\"}]}"
                        )
                    )
                )
            )
        }.toString()

        val extractedJson = com.example.data.OpenRouterService().extractChatCompletionJsonObject(openRouterEnvelope)
        val parsedProject = GeminiService().parseProjectJson(extractedJson, "Meal planner")
        assertEquals("PocketChef", parsedProject.name)
        assertEquals(listOf("HomeScreen"), parsedProject.screens)

        val codeReviewJson = org.json.JSONObject().apply {
            put("summary", "OpenRouter review clean.")
            put("issues", org.json.JSONArray())
        }.toString()
        val analysis = GeminiService().parseCodeAnalysisJson(
            jsonText = codeReviewJson,
            staticIssues = emptyList(),
            sourceLabel = "OpenRouter",
            providerLabel = "OpenRouter"
        )
        assertEquals("OpenRouter", analysis.providerLabel)
        assertTrue(analysis.usedGeminiAi)

        com.example.data.CloudAiConfig.clearSavedOpenRouterApiKey(context)
        com.example.data.CloudAiConfig.setOfflineMode(context, false)
        com.example.data.CloudAiConfig.setProvider(context, com.example.data.CloudAiProvider.GEMINI)
    }

    @Test
    fun `createScreen shows Gemini OpenRouter chips and Offline Mode switch`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.example.data.CloudAiConfig.resetForTest()
        com.example.data.CloudAiConfig.setOfflineMode(context, false)
        com.example.data.CloudAiConfig.setProvider(context, com.example.data.CloudAiProvider.GEMINI)

        val viewModel = com.example.viewmodel.GenerateViewModel()

        composeTestRule.setContent {
            com.example.ui.theme.MyApplicationTheme {
                com.example.screens.CreateScreen(
                    viewModel = viewModel,
                    onBack = {},
                    onSaveToProjects = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("create_provider_gemini_chip").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("create_provider_openrouter_chip").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("create_open_settings_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("offline_mode_switch").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Offline Mode").assertIsDisplayed()

        // Switch provider to OpenRouter
        composeTestRule.onNodeWithTag("create_provider_openrouter_chip").performClick()
        composeTestRule.waitForIdle()
        assertEquals(com.example.data.CloudAiProvider.OPENROUTER, com.example.data.CloudAiConfig.currentProvider.value)

        // Enable Offline Mode switch
        composeTestRule.onNodeWithTag("offline_mode_switch").performClick()
        composeTestRule.waitForIdle()
        assertTrue(com.example.data.CloudAiConfig.isOfflineMode.value)

        // Reset clean state
        com.example.data.CloudAiConfig.setOfflineMode(context, false)
        com.example.data.CloudAiConfig.setProvider(context, com.example.data.CloudAiProvider.GEMINI)
    }

    @Test
    fun `settingsScreen lets user select OpenRouter and save API key`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.example.data.CloudAiConfig.resetForTest()
        com.example.data.CloudAiConfig.clearSavedOpenRouterApiKey(context)
        com.example.data.CloudAiConfig.setProvider(context, com.example.data.CloudAiProvider.GEMINI)

        composeTestRule.setContent {
            com.example.ui.theme.MyApplicationTheme {
                com.example.screens.SettingsScreen(
                    isApiKeyConfigured = false,
                    totalProjectsCount = 0,
                    onClearAllProjects = {},
                    onBack = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("settings_offline_mode_switch").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_provider_openrouter_chip").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(com.example.data.CloudAiProvider.OPENROUTER, com.example.data.CloudAiConfig.currentProvider.value)

        composeTestRule.onNodeWithTag("settings_openrouter_key_field").performScrollTo().performTextInput("sk-or-v1-abc999")
        composeTestRule.onNodeWithTag("settings_openrouter_save_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        assertEquals("sk-or-v1-abc999", com.example.data.CloudAiConfig.effectiveOpenRouterApiKey())
        composeTestRule.onNodeWithTag("settings_api_key_status").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Configured").assertIsDisplayed()

        // Clean up
        com.example.data.CloudAiConfig.clearSavedOpenRouterApiKey(context)
        com.example.data.CloudAiConfig.setProvider(context, com.example.data.CloudAiProvider.GEMINI)
    }

    @Test
    fun `projectBuildPlanner assesses requirements and tries offline first before cloud fallback`() {
        val cloudEnglish = com.example.data.ProjectBuildPlanner.assessRequirements(
            "Weather & chat app with cloud sync and user login"
        )
        assertEquals(com.example.data.RequirementProfile.CLOUD_REQUIRED, cloudEnglish.profile)
        assertTrue(cloudEnglish.matchedCloudSignals.isNotEmpty())

        val cloudArabic = com.example.data.ProjectBuildPlanner.assessRequirements(
            "تطبيق متجر ومزامنة سحابية مع خرائط"
        )
        assertEquals(com.example.data.RequirementProfile.CLOUD_REQUIRED, cloudArabic.profile)

        val offlineCapable = com.example.data.ProjectBuildPlanner.assessRequirements(
            "Simple Pomodoro timer and daily habit checklist"
        )
        assertEquals(com.example.data.RequirementProfile.OFFLINE_CAPABLE, offlineCapable.profile)

        // AUTO tries offline first, then falls back to Cloud AI when offline is unavailable
        val autoFallbackPlan = com.example.data.ProjectBuildPlanner.planBuild(
            prompt = "Simple Pomodoro timer",
            strategy = com.example.data.BuildModeStrategy.AUTO,
            activeProvider = com.example.data.CloudAiProvider.OPENROUTER,
            installedFiles = emptySet(),
            selectedModelFile = null,
            isOfflineRuntimeAvailable = false
        )
        assertTrue(autoFallbackPlan.triedOfflineFirst)
        assertTrue(autoFallbackPlan.useCloudAi)
        assertEquals(com.example.data.CloudAiProvider.OPENROUTER, autoFallbackPlan.targetProvider)
        assertTrue(autoFallbackPlan.statusSummary.contains("Tried Offline first"))
        assertTrue(autoFallbackPlan.statusSummary.contains("OpenRouter"))

        // AUTO uses offline directly if runtime + model are available and prompt is offline-capable
        val autoOfflineReadyPlan = com.example.data.ProjectBuildPlanner.planBuild(
            prompt = "Simple Pomodoro timer",
            strategy = com.example.data.BuildModeStrategy.AUTO,
            activeProvider = com.example.data.CloudAiProvider.GEMINI,
            installedFiles = setOf("qwen3-0.6B.litertlm"),
            selectedModelFile = "qwen3-0.6B.litertlm",
            isOfflineRuntimeAvailable = true
        )
        assertTrue(autoOfflineReadyPlan.triedOfflineFirst)
        org.junit.Assert.assertFalse(autoOfflineReadyPlan.useCloudAi)
        assertTrue(autoOfflineReadyPlan.statusSummary.contains("qwen3-0.6B.litertlm"))

        // OFFLINE never falls back to Cloud AI
        val offlineOnlyPlan = com.example.data.ProjectBuildPlanner.planBuild(
            prompt = "Simple Pomodoro timer",
            strategy = com.example.data.BuildModeStrategy.OFFLINE,
            activeProvider = com.example.data.CloudAiProvider.GEMINI,
            installedFiles = emptySet(),
            selectedModelFile = null,
            isOfflineRuntimeAvailable = false
        )
        assertTrue(offlineOnlyPlan.triedOfflineFirst)
        org.junit.Assert.assertFalse(offlineOnlyPlan.useCloudAi)

        // CLOUD skips offline attempt
        val cloudDirectPlan = com.example.data.ProjectBuildPlanner.planBuild(
            prompt = "Simple Pomodoro timer",
            strategy = com.example.data.BuildModeStrategy.CLOUD,
            activeProvider = com.example.data.CloudAiProvider.GEMINI,
            installedFiles = emptySet(),
            selectedModelFile = null,
            isOfflineRuntimeAvailable = false
        )
        org.junit.Assert.assertFalse(cloudDirectPlan.triedOfflineFirst)
        assertTrue(cloudDirectPlan.useCloudAi)
    }

    @Test
    fun `createScreen and viewModel switch build modes and show requirement assessment and routing banner`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.example.data.CloudAiConfig.resetForTest()
        com.example.data.CloudAiConfig.setBuildModeStrategy(context, com.example.data.BuildModeStrategy.AUTO)
        com.example.data.CloudAiConfig.setProvider(context, com.example.data.CloudAiProvider.GEMINI)

        val viewModel = com.example.viewmodel.GenerateViewModel()

        composeTestRule.setContent {
            com.example.ui.theme.MyApplicationTheme {
                com.example.screens.CreateScreen(
                    viewModel = viewModel,
                    onBack = {},
                    onSaveToProjects = {}
                )
            }
        }

        assertEquals(com.example.data.BuildModeStrategy.AUTO, com.example.data.CloudAiConfig.buildModeStrategy.value)
        composeTestRule.onNodeWithTag("create_mode_auto_chip").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("create_mode_offline_chip").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("create_mode_cloud_chip").performScrollTo().assertIsDisplayed()

        // Enter offline-capable prompt and verify live assessment
        composeTestRule.onNodeWithTag("prompt_text_field").performScrollTo().performTextInput("Unit converter calculator")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("create_requirement_profile_text").performScrollTo().assertIsDisplayed()
        assertTrue(
            com.example.data.ProjectBuildPlanner.assessRequirements(viewModel.prompt.value).profile ==
                com.example.data.RequirementProfile.OFFLINE_CAPABLE
        )

        // Trigger generateProject in AUTO -> records triedOfflineFirst + cloud fallback in lastBuildRoutingPlan
        viewModel.generateProject("Unit converter calculator")
        composeTestRule.waitForIdle()
        val plan = viewModel.lastBuildRoutingPlan.value
        org.junit.Assert.assertNotNull(plan)
        assertTrue(plan!!.triedOfflineFirst)
        assertTrue(plan.useCloudAi)
        composeTestRule.onNodeWithTag("build_routing_status_banner").performScrollTo().assertIsDisplayed()

        // Switch to Offline chip -> syncs isOfflineMode = true
        composeTestRule.onNodeWithTag("create_mode_offline_chip").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(com.example.data.BuildModeStrategy.OFFLINE, com.example.data.CloudAiConfig.buildModeStrategy.value)
        assertTrue(com.example.data.CloudAiConfig.isOfflineMode.value)

        // Switch to Cloud AI chip -> syncs isOfflineMode = false
        composeTestRule.onNodeWithTag("create_mode_cloud_chip").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(com.example.data.BuildModeStrategy.CLOUD, com.example.data.CloudAiConfig.buildModeStrategy.value)
        org.junit.Assert.assertFalse(com.example.data.CloudAiConfig.isOfflineMode.value)

        // Clean up
        com.example.data.CloudAiConfig.setBuildModeStrategy(context, com.example.data.BuildModeStrategy.AUTO)
        com.example.data.CloudAiConfig.setProvider(context, com.example.data.CloudAiProvider.GEMINI)
    }

    @Test
    fun `settingsScreen build strategy chips sync with offline switch`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.example.data.CloudAiConfig.resetForTest()
        com.example.data.CloudAiConfig.setBuildModeStrategy(context, com.example.data.BuildModeStrategy.AUTO)

        composeTestRule.setContent {
            com.example.ui.theme.MyApplicationTheme {
                com.example.screens.SettingsScreen(
                    isApiKeyConfigured = true,
                    totalProjectsCount = 1,
                    onClearAllProjects = {},
                    onBack = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("settings_strategy_offline_chip").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(com.example.data.BuildModeStrategy.OFFLINE, com.example.data.CloudAiConfig.buildModeStrategy.value)
        assertTrue(com.example.data.CloudAiConfig.isOfflineMode.value)

        composeTestRule.onNodeWithTag("settings_strategy_auto_chip").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(com.example.data.BuildModeStrategy.AUTO, com.example.data.CloudAiConfig.buildModeStrategy.value)
        org.junit.Assert.assertFalse(com.example.data.CloudAiConfig.isOfflineMode.value)
    }

    @Test
    fun `screenSpecs and geminiService remove fixed default tabs and support AI or user tab counts`() {
        // Empty project returns 0 tabs (no synthetic OverviewScreen default)
        val emptyProject = AppProject(
            id = "empty-tabs",
            name = "MinimalTool",
            description = "Minimalist utility",
            prompt = "Minimalist utility",
            createdAt = 1000L
        )
        assertTrue(com.example.design.ScreenSpecs.all(emptyProject).isEmpty())
        assertTrue(com.example.design.ScreenSpecs.of(emptyProject).isEmpty())

        // GeminiService prompt in AI_DECIDES mode does NOT force "4 to 5" default tabs
        val gemini = GeminiService()
        val aiAutoPrompt = gemini.buildGenerateAppSystemPrompt(com.example.model.TabBuildPreference())
        org.junit.Assert.assertFalse(aiAutoPrompt.contains("array of 4 to 5"))
        assertTrue(aiAutoPrompt.contains("count YOU decide dynamically"))

        // User-defined exact count & custom names in prompt
        val userCountPrompt = gemini.buildGenerateAppSystemPrompt(
            com.example.model.TabBuildPreference(
                mode = com.example.model.TabSelectionMode.USER_DEFINED,
                requestedCount = 7
            )
        )
        assertTrue(userCountPrompt.contains("EXACTLY 7 screen/tab objects"))

        val customNamesPrompt = gemini.buildGenerateAppSystemPrompt(
            com.example.model.TabBuildPreference(
                mode = com.example.model.TabSelectionMode.USER_DEFINED,
                customTabNames = listOf("Store", "Cart", "Wallet")
            )
        )
        assertTrue(customNamesPrompt.contains("EXACTLY 3 screen/tab objects"))
        assertTrue(customNamesPrompt.contains("\"Store\", \"Cart\", \"Wallet\""))

        // Parsing 7 AI-determined screens preserves all 7 without truncating to 5
        val sevenScreensArray = org.json.JSONArray().apply {
            (1..7).forEach { idx ->
                put(
                    org.json.JSONObject().apply {
                        put("name", "Module${idx}Screen")
                        put("label", "Tab $idx")
                        put("icon", "dashboard")
                        put("purpose", "Module $idx purpose")
                    }
                )
            }
        }
        val sevenScreensJson = org.json.JSONObject().apply {
            put("name", "SuperSuite")
            put("description", "Modular platform")
            put("features", org.json.JSONArray().put("Analytics"))
            put("screens", sevenScreensArray)
        }.toString()

        val parsedSeven = gemini.parseProjectJson(sevenScreensJson, "Modular platform")
        assertEquals(7, parsedSeven.screens.size)
        assertEquals(7, com.example.design.ScreenSpecs.of(parsedSeven).size)

        // Parsing 1 focused AI screen keeps 1 tab
        val oneScreenJson = org.json.JSONObject().apply {
            put("name", "QuickFlash")
            put("description", "Single-screen flashlight")
            put("features", org.json.JSONArray().put("SOS pulse"))
            put(
                "screens",
                org.json.JSONArray().put(
                    org.json.JSONObject().apply {
                        put("name", "FlashlightScreen")
                        put("label", "Flashlight")
                        put("icon", "star")
                        put("purpose", "Toggle torch brightness")
                    }
                )
            )
        }.toString()
        val parsedOne = gemini.parseProjectJson(oneScreenJson, "Flashlight tool")
        assertEquals(1, parsedOne.screens.size)
        assertEquals("Flashlight", parsedOne.screenSpecs.first().label)

        // Add & Remove tab helpers work cleanly down to 0
        val added = com.example.screens.withAddedScreenSpec(parsedOne, "Settings")
        assertEquals(2, added.screens.size)
        val removedFirst = com.example.screens.withRemovedScreenSpec(added, 0)
        assertEquals(1, removedFirst.screens.size)
        val removedAll = com.example.screens.withRemovedScreenSpec(removedFirst, 0)
        assertTrue(removedAll.screens.isEmpty())
    }

    @Test
    fun `createScreen lets user choose AI-decided tabs or customize tab count and Arabic names`() {
        val viewModel = com.example.viewmodel.GenerateViewModel()

        composeTestRule.setContent {
            com.example.ui.theme.MyApplicationTheme {
                com.example.screens.CreateScreen(
                    viewModel = viewModel,
                    onBack = {},
                    onSaveToProjects = {}
                )
            }
        }

        // Default mode is AI_DECIDES with no hardcoded tab count
        assertEquals(
            com.example.model.TabSelectionMode.AI_DECIDES,
            viewModel.tabPreference.value.mode
        )
        composeTestRule.onNodeWithTag("tabs_mode_ai_chip").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("tabs_mode_summary_text").performScrollTo().assertIsDisplayed()

        // Switch to User Defined mode and increment tab count from 3 -> 4 -> 5 -> 6
        composeTestRule.onNodeWithTag("tabs_mode_custom_chip").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(
            com.example.model.TabSelectionMode.USER_DEFINED,
            viewModel.tabPreference.value.mode
        )

        composeTestRule.onNodeWithTag("tabs_count_increase").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(4, viewModel.tabPreference.value.requestedCount)

        composeTestRule.onNodeWithTag("tabs_count_decrease").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(3, viewModel.tabPreference.value.requestedCount)

        // Enter custom Arabic comma-separated tab names
        composeTestRule.onNodeWithTag("custom_tab_names_field")
            .performScrollTo()
            .performTextInput("الرئيسية، السلة، حسابي")
        composeTestRule.waitForIdle()

        assertEquals(
            listOf("الرئيسية", "السلة", "حسابي"),
            viewModel.tabPreference.value.customTabNames
        )
        assertEquals(3, viewModel.tabPreference.value.effectiveRequestedCount)

        // Verify Quick Starter Ideas and Gallery button are removed from CreateScreen
        composeTestRule.onAllNodesWithText("Quick Starter Ideas").assertCountEquals(0)
        composeTestRule.onNodeWithTag("quick_starter_ideas_row").assertDoesNotExist()
        composeTestRule.onNodeWithTag("create_open_gallery_button").assertDoesNotExist()
    }
}
