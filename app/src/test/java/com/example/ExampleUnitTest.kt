package com.example

import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testBuildOptionsDefaultValues() {
    val options = com.example.model.BuildOptions()
    assertEquals("debug", options.buildVariant)
    assertFalse(options.minifyEnabled)
    assertTrue(options.includeDebuggable)
    assertFalse(options.shrinkResources)
    assertEquals(36, options.targetSdk)
    assertEquals("1.0", options.versionName)
    assertEquals(1, options.versionCode)
    assertTrue(options.bundledTfliteModels.isEmpty())
    assertEquals(com.example.model.BuildVariant.DEBUG, options.variant)
  }

  @Test
  fun testTfliteModelManagerHelpers() {
    assertEquals(
      "mobilenet_v2.tflite",
      com.example.ai.TfliteModelManager.sanitizeTfliteFileName("/sdcard/Download/mobilenet_v2.tflite")
    )
    assertEquals(
      "custom_model.tflite",
      com.example.ai.TfliteModelManager.sanitizeTfliteFileName("custom_model")
    )
    assertEquals(
      "Vision",
      com.example.ai.TfliteModelManager.inferTaskCategory("mobilenet_v3_large.tflite")
    )
    assertEquals(
      "Audio",
      com.example.ai.TfliteModelManager.inferTaskCategory("yamnet_classification.tflite")
    )
    assertEquals(
      "Text / NLP",
      com.example.ai.TfliteModelManager.inferTaskCategory("mobilebert_qa.tflite")
    )

    val validHeader = byteArrayOf(0x1C, 0x00, 0x00, 0x00, 'T'.code.toByte(), 'F'.code.toByte(), 'L'.code.toByte(), '3'.code.toByte())
    val invalidHeader = byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x01, 0x02, 0x03, 0x04)
    assertTrue(com.example.ai.TfliteModelManager.hasValidTfliteHeaderBytes(validHeader))
    assertFalse(com.example.ai.TfliteModelManager.hasValidTfliteHeaderBytes(invalidHeader))
  }

  @Test
  fun testApkConfigIncludesBundledTfliteModels() {
    val project = com.example.model.AppProject(
      id = "proj_1",
      name = "Vision Scanner",
      description = "Offline image classifier",
      prompt = "Build an offline image scanner",
      createdAt = 1000L,
      buildOptions = com.example.model.BuildOptions(
        buildVariant = "release",
        bundledTfliteModels = listOf("mobilenet_v2.tflite")
      )
    )
    val configJson = com.example.apk.ApkBuilder.buildConfigJson(project)
    assertTrue(configJson.contains("\"bundledTfliteModels\""))
    assertTrue(configJson.contains("mobilenet_v2.tflite"))
  }

  @Test
  fun testLocalPromptMatcherOfflineScaffold() {
    val fitnessApp = com.example.ai.LocalPromptMatcher.buildOfflineScaffold(
      prompt = "Workout interval timer with habit streak"
    )
    assertEquals("PulseTrack Studio", fitnessApp.name)
    assertEquals(3, fitnessApp.screens.size)
    assertTrue(fitnessApp.features.isNotEmpty())

    val customTabsPref = com.example.model.TabBuildPreference(
      mode = com.example.model.TabSelectionMode.USER_DEFINED,
      customTabNames = listOf("Feed", "Scan", "Notes")
    )
    val customApp = com.example.ai.LocalPromptMatcher.buildOfflineScaffold(
      prompt = "Offline document note scanner",
      tabPreference = customTabsPref
    )
    assertEquals(3, customApp.screenSpecs.size)
    assertEquals(listOf("Feed", "Scan", "Notes"), customApp.screenSpecs.map { it.label })
  }

  @Test
  fun testTfliteModelPurposeAndAssetPath() {
    assertTrue(
      com.example.ai.TfliteModelManager.inferModelPurpose("yolov8_detect.tflite")
        .contains("Object detection", ignoreCase = true)
    )
    assertTrue(
      com.example.ai.TfliteModelManager.inferModelPurpose("mobilenet_v3_large.tflite")
        .contains("image classification", ignoreCase = true)
    )
    assertTrue(
      com.example.ai.TfliteModelManager.inferModelPurpose("yamnet.tflite")
        .contains("Audio event classification", ignoreCase = true)
    )
    assertTrue(
      com.example.ai.TfliteModelManager.inferModelPurpose("text_embed_gecko.tflite")
        .contains("embeddings", ignoreCase = true)
    )
    assertTrue(
      com.example.ai.TfliteModelManager.inferModelPurpose("sensor_anomaly.tflite")
        .contains("TensorFlow Lite", ignoreCase = true)
    )
    assertEquals(
      "assets/models/mobilenet_v2.tflite",
      com.example.ai.TfliteModelManager.bundledAssetPath("/tmp/mobilenet_v2.tflite")
    )
  }

  @Test
  fun testKeystoreValidatorAgainstDotEnv() {
    val dotEnvText = """
      # Release signing credentials
      KEYSTORE_PATH="my-upload-key.jks"
      STORE_PASSWORD="super-secret-store-pass"
      KEY_ALIAS="upload"
      KEY_PASSWORD="super-secret-key-pass"
    """.trimIndent()
    val envConfig = com.example.apk.KeystoreValidator.parseDotEnv(dotEnvText)
    assertTrue(envConfig.hasKeystorePath)
    assertTrue(envConfig.hasStorePassword)
    assertTrue(envConfig.hasKeyAlias)
    assertTrue(envConfig.hasKeyPassword)
    assertTrue(envConfig.hasAllRequiredSecrets)

    // Create a genuine temporary .jks keystore
    val keyPair = java.security.KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    val certDer = com.example.apk.ApkBuilder.generateSelfSignedX509Der(keyPair)
    val cert = java.security.cert.CertificateFactory.getInstance("X.509")
      .generateCertificate(java.io.ByteArrayInputStream(certDer)) as java.security.cert.X509Certificate

    val tempJks = kotlin.io.path.createTempFile("my-upload-key", ".jks").toFile()
    try {
      val ks = java.security.KeyStore.getInstance("JKS")
      ks.load(null, null)
      ks.setKeyEntry(
        "upload",
        keyPair.private,
        "super-secret-key-pass".toCharArray(),
        arrayOf(cert)
      )
      tempJks.outputStream().use { out ->
        ks.store(out, "super-secret-store-pass".toCharArray())
      }

      val validResult = com.example.apk.KeystoreValidator.validateKeystoreFile(tempJks, envConfig)
      assertTrue("Expected VALID keystore: $validResult", validResult.isValid)
      assertEquals("JKS", validResult.formatLabel)
      assertEquals("upload", validResult.matchedAlias)
      assertNotNull(validResult.sha256Fingerprint)

      // Legacy JKS pure-Kotlin verifier path also passes with matching password & alias
      val legacyVerified = com.example.apk.KeystoreValidator.validateLegacyJksBinary(
        rawBytes = tempJks.readBytes(),
        fileName = tempJks.name,
        path = tempJks.absolutePath,
        envConfig = envConfig
      )
      assertTrue("Expected legacy JKS verifier to pass: $legacyVerified", legacyVerified.isValid)

      // Wrong store password
      val wrongStoreResult = com.example.apk.KeystoreValidator.validateKeystoreFile(
        tempJks,
        envConfig.copy(storePassword = "wrong-store-password")
      )
      assertEquals(
        com.example.apk.KeystoreValidationStatus.INVALID_PASSWORD,
        wrongStoreResult.status
      )

      // Wrong key alias
      val wrongAliasResult = com.example.apk.KeystoreValidator.validateKeystoreFile(
        tempJks,
        envConfig.copy(keyAlias = "missing-alias")
      )
      assertEquals(
        com.example.apk.KeystoreValidationStatus.ALIAS_NOT_FOUND,
        wrongAliasResult.status
      )

      // Wrong key password
      val wrongKeyPassResult = com.example.apk.KeystoreValidator.validateKeystoreFile(
        tempJks,
        envConfig.copy(keyPassword = "wrong-key-pass")
      )
      assertEquals(
        com.example.apk.KeystoreValidationStatus.KEY_PASSWORD_MISMATCH,
        wrongKeyPassResult.status
      )

      // Placeholder .env password flagged as missing credentials
      val placeholderConfig = com.example.apk.KeystoreValidator.parseDotEnv(
        """
        KEYSTORE_PATH="my-upload-key.jks"
        STORE_PASSWORD="MY_STORE_PASSWORD"
        KEY_ALIAS="upload"
        KEY_PASSWORD="MY_KEY_PASSWORD"
        """.trimIndent()
      )
      assertFalse(placeholderConfig.hasStorePassword)
      assertFalse(placeholderConfig.hasKeyPassword)
      val missingCredsResult = com.example.apk.KeystoreValidator.validateKeystoreFile(tempJks, placeholderConfig)
      assertEquals(
        com.example.apk.KeystoreValidationStatus.MISSING_ENV_CREDENTIALS,
        missingCredsResult.status
      )
    } finally {
      tempJks.delete()
    }
  }

  @Test
  fun testImagenRequestPayloadAndDrawableXmlHelpers() {
    val service = com.example.data.GeminiService()
    val composed = service.buildLauncherIconImagenPrompt(
      userPrompt = "Emerald origami compass",
      designStyle = "Material You",
      projectName = "TrailFinder"
    )
    assertTrue(composed.contains("Emerald origami compass"))
    assertTrue(composed.contains("Material You"))
    assertTrue(composed.contains("1:1 square artwork"))

    assertEquals(
      "ic_launcher_trail_finder",
      com.example.data.ProjectDrawableInstaller.sanitizeDrawableName("IC Launcher Trail-Finder!")
    )

    val fgXml = com.example.data.ProjectDrawableInstaller.buildAdaptiveForegroundXml("ic_launcher_trail")
    assertTrue(fgXml.contains("@drawable/ic_launcher_trail"))
    assertTrue(fgXml.contains("66dp"))

    val bgXml = com.example.data.ProjectDrawableInstaller.buildAdaptiveBackgroundXml("#1A2B3C")
    assertTrue(bgXml.contains("#1A2B3C"))
  }

  @Test
  fun testProjectLintAuditorFlagsAndFixesGradleWarnings() {
    val buggyProject = com.example.model.AppProject(
      id = "audit-proj-1",
      name = "AuditSample",
      description = "Sample app",
      prompt = "Tracker",
      createdAt = 1000L,
      screens = listOf("HomeScreen"),
      primaryColorHex = "badhex",
      buildOptions = com.example.model.BuildOptions(
        buildVariant = "release",
        minifyEnabled = false,
        includeDebuggable = true,
        shrinkResources = true,
        targetSdk = 34,
        signingScheme = "v1"
      )
    )

    val report = com.example.data.ProjectLintAuditor.auditProject(
      project = buggyProject,
      customGradleScript = com.example.data.ProjectLintAuditor.defaultSampleGradleWithWarnings()
    )

    val ids = report.issues.map { it.id }.toSet()
    assertTrue(ids.contains("agp_shrink_without_minify"))
    assertTrue(ids.contains("gradle_release_debuggable"))
    assertTrue(ids.contains("lint_old_target_api"))
    assertTrue(ids.contains("signing_scheme_legacy_v1"))
    assertTrue(ids.contains("struct_invalid_primary_hex"))
    assertTrue(ids.contains("gradle_deprecated_compile_config"))
    assertTrue(ids.contains("gradle_legacy_sdk_dsl"))
    assertTrue(ids.contains("gradle_deprecated_jcenter"))
    assertTrue(ids.contains("gradle_deprecated_kotlin_options"))
    assertTrue(ids.contains("gradle_tflite_nocompress_missing"))
    assertTrue(ids.contains("gradle_missing_compose_bom"))
    assertTrue(ids.contains("gradle_duplicate_dependency"))

    // Apply every auto-fix to project and Gradle script
    var fixedProject = buggyProject
    var fixedGradle = report.analyzedGradleScript
    report.issues.forEach { issue ->
      val kind = issue.fixKind ?: return@forEach
      fixedProject = com.example.data.ProjectLintAuditor.applyProjectFix(fixedProject, kind)
      fixedGradle = com.example.data.ProjectLintAuditor.applyGradleScriptFix(fixedGradle, kind)
    }

    assertTrue(fixedProject.buildOptions.minifyEnabled)
    assertFalse(fixedProject.buildOptions.includeDebuggable)
    assertEquals(36, fixedProject.buildOptions.targetSdk)
    assertEquals("v1+v2", fixedProject.buildOptions.signingScheme)
    assertTrue(Regex("^#[0-9A-Fa-f]{6}$").matches(fixedProject.primaryColorHex))

    val reAudit = com.example.data.ProjectLintAuditor.auditProject(
      project = fixedProject,
      customGradleScript = fixedGradle
    )
    assertEquals(
      "Expected all auto-fixable Gradle/build warnings resolved, remaining: ${reAudit.issues.filter { it.hasOneClickFix }.map { it.id }}",
      0,
      reAudit.autoFixableCount
    )
  }

  @Test
  fun testGradleDependencyGraphDetectsAndResolvesConflictsAndOutdatedLibs() {
    val demoScript = com.example.data.GradleDependencyGraphAnalyzer.defaultConflictDemoGradleScript()
    val graph = com.example.data.GradleDependencyGraphAnalyzer.analyze(
      project = null,
      customGradleScript = demoScript
    )

    assertTrue(graph.nodes.any { it.id == "app" && it.layer == 0 })
    assertTrue(graph.conflictCount >= 2)
    assertTrue(graph.outdatedCount >= 2)

    val litertNode = graph.nodes.first { it.id == "litert" }
    assertEquals(com.example.data.DependencyNodeStatus.CONFLICT, litertNode.status)

    val okhttpNode = graph.nodes.first { it.id == "okhttp" }
    assertEquals(com.example.data.DependencyNodeStatus.CONFLICT, okhttpNode.status)

    val coreKtxNode = graph.nodes.first { it.id == "core-ktx" }
    assertEquals(com.example.data.DependencyNodeStatus.OUTDATED, coreKtxNode.status)

    var patchedScript = demoScript
    graph.nodes.filter { it.status != com.example.data.DependencyNodeStatus.HEALTHY }.forEach { node ->
      patchedScript = com.example.data.GradleDependencyGraphAnalyzer.applyNodeFix(patchedScript, node)
    }

    val updatedGraph = com.example.data.GradleDependencyGraphAnalyzer.analyze(
      project = null,
      customGradleScript = patchedScript
    )
    assertEquals(0, updatedGraph.conflictCount)
    assertEquals(0, updatedGraph.outdatedCount)
  }

  @Test
  fun testDotEnvConfigManagerFormatsParsesAndEvaluatesChecklist() {
    val draft = com.example.apk.DotEnvDraft(
      geminiApiKey = "AIzaSyExampleKey987654",
      openRouterApiKey = "",
      keystorePath = "release-upload.jks",
      storePassword = "strongStorePassword",
      keyAlias = "upload",
      keyPassword = "strongKeyPassword",
      includeComments = true
    )
    assertEquals(5, draft.configuredCount)
    assertTrue(draft.hasAllSigningSecrets)

    val rawDotEnv = com.example.apk.DotEnvConfigManager.formatDotEnv(draft, maskSecrets = false)
    assertTrue(rawDotEnv.contains("GEMINI_API_KEY=\"AIzaSyExampleKey987654\""))
    assertTrue(rawDotEnv.contains("OPENROUTER_API_KEY=\"MY_OPENROUTER_API_KEY\""))
    assertTrue(rawDotEnv.contains("KEYSTORE_PATH=\"release-upload.jks\""))
    assertTrue(rawDotEnv.contains("STORE_PASSWORD=\"strongStorePassword\""))
    assertTrue(rawDotEnv.contains("KEY_ALIAS=\"upload\""))
    assertTrue(rawDotEnv.contains("KEY_PASSWORD=\"strongKeyPassword\""))

    val maskedDotEnv = com.example.apk.DotEnvConfigManager.formatDotEnv(draft, maskSecrets = true)
    assertFalse(maskedDotEnv.contains("AIzaSyExampleKey987654"))
    assertFalse(maskedDotEnv.contains("strongStorePassword"))

    val reparsed = com.example.apk.DotEnvConfigManager.parseDotEnvToDraft(rawDotEnv)
    assertEquals("AIzaSyExampleKey987654", reparsed.geminiApiKey)
    assertEquals("", reparsed.openRouterApiKey)
    assertEquals("release-upload.jks", reparsed.keystorePath)
    assertEquals("strongStorePassword", reparsed.storePassword)
    assertEquals("upload", reparsed.keyAlias)
    assertEquals("strongKeyPassword", reparsed.keyPassword)

    val checklist = com.example.apk.DotEnvConfigManager.evaluateChecklist(draft)
    assertEquals(6, checklist.size)
    assertTrue(checklist.first { it.key == "GEMINI_API_KEY" }.isConfigured)
    assertFalse(checklist.first { it.key == "OPENROUTER_API_KEY" }.isConfigured)
    assertTrue(checklist.first { it.key == "STORE_PASSWORD" }.isConfigured)
  }

  @Test
  fun testReleaseBuildConfigManagerPatchesParsesAndSerializes() {
    val defaultTemplate = com.example.apk.ReleaseBuildConfigManager.defaultReleaseGradleTemplate(
      minifyEnabled = false,
      crunchPngs = false
    )
    val initialFlags = com.example.apk.ReleaseBuildConfigManager.parseReleaseFlags(defaultTemplate)
    assertFalse(initialFlags.minifyEnabled)
    assertFalse(initialFlags.crunchPngs)

    val patched = com.example.apk.ReleaseBuildConfigManager.applyFlagsToGradleScript(
      gradleText = defaultTemplate,
      minifyEnabled = true,
      crunchPngs = true,
      shrinkResources = true
    )
    assertTrue(patched.contains("isMinifyEnabled = true"))
    assertTrue(patched.contains("isCrunchPngs = true"))
    assertTrue(patched.contains("isShrinkResources = true"))
    assertTrue(patched.contains("signingConfigs.getByName(\"release\")"))

    val reparsed = com.example.apk.ReleaseBuildConfigManager.parseReleaseFlags(patched)
    assertTrue(reparsed.minifyEnabled)
    assertTrue(reparsed.crunchPngs)
    assertTrue(reparsed.shrinkResources)

    val snippet = com.example.apk.ReleaseBuildConfigManager.buildReleaseBlockSnippet(
      minifyEnabled = true,
      crunchPngs = false,
      shrinkResources = true
    )
    assertTrue(snippet.contains("isCrunchPngs = false"))
    assertTrue(snippet.contains("isMinifyEnabled = true"))
    assertTrue(snippet.contains("isShrinkResources = true"))
  }
}
