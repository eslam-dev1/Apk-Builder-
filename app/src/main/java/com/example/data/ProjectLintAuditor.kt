package com.example.data

import android.content.Context
import com.example.ai.TfliteModelManager
import com.example.apk.KeystoreValidator
import com.example.apk.SigningOverallStatus
import com.example.design.ScreenSpecs
import com.example.design.designFor
import com.example.model.AppProject
import com.example.model.BuildVariant

enum class AuditSeverity(val label: String) {
    ERROR("ERROR"),
    WARNING("GRADLE WARN"),
    INFO("INFO")
}

enum class AuditCategory(val label: String) {
    GRADLE_BUILD("Gradle & Build"),
    DEPENDENCIES("Dependencies"),
    STRUCTURE_CODE("Structure & Code"),
    SIGNING_SECURITY("Signing & Security")
}

enum class AuditFixKind {
    ENABLE_MINIFY_FOR_SHRINK_RESOURCES,
    DISABLE_DEBUGGABLE_IN_RELEASE,
    ENABLE_RELEASE_MINIFY,
    UPGRADE_TARGET_SDK_36,
    UPGRADE_SIGNING_SCHEME_V1_V2,
    NORMALIZE_VERSION_METADATA,
    PRUNE_MISSING_TFLITE_MODELS,
    ADD_DEFAULT_SCREEN_SPEC,
    REPAIR_SCREEN_SPECS,
    FIX_PRIMARY_COLOR_HEX,
    PATCH_GRADLE_DEPRECATED_COMPILE,
    PATCH_GRADLE_LEGACY_SDK_DSL,
    PATCH_GRADLE_KOTLIN_COMPILER_OPTIONS,
    PATCH_GRADLE_TFLITE_NOCOMPRESS,
    PATCH_GRADLE_REMOVE_JCENTER,
    PATCH_GRADLE_SHRINK_WITHOUT_MINIFY,
    PATCH_GRADLE_ADD_COMPOSE_BOM,
    PATCH_GRADLE_DEDUPLICATE_DEPS
}

data class LintAuditIssue(
    val id: String,
    val title: String,
    val category: AuditCategory,
    val severity: AuditSeverity,
    val targetFile: String,
    val description: String,
    val recommendation: String,
    val gradleSnippet: String = "",
    val fixKind: AuditFixKind? = null,
    val fixActionLabel: String = "Apply Fix"
) {
    val hasOneClickFix: Boolean
        get() = fixKind != null
}

data class DependencyAuditItem(
    val aliasOrCoordinate: String,
    val versionLabel: String,
    val category: String,
    val statusNote: String,
    val hasWarning: Boolean = false
)

data class ProjectLintAuditReport(
    val projectId: String?,
    val projectName: String,
    val analyzedGradleScript: String,
    val issues: List<LintAuditIssue>,
    val dependencies: List<DependencyAuditItem>,
    val scannedFilesCount: Int,
    val healthScore: Int
) {
    val errorCount: Int get() = issues.count { it.severity == AuditSeverity.ERROR }
    val warningCount: Int get() = issues.count { it.severity == AuditSeverity.WARNING }
    val infoCount: Int get() = issues.count { it.severity == AuditSeverity.INFO }
    val autoFixableCount: Int get() = issues.count { it.hasOneClickFix }
}

object ProjectLintAuditor {

    fun synthesizeGradleScript(project: AppProject): String {
        val opts = project.buildOptions
        val safePkg = "com.aiappstudio.gen.${CodeExporter.toSafeFileName(project.name).lowercase()}"
        val hasTflite = opts.bundledTfliteModels.isNotEmpty()
        return buildString {
            appendLine("plugins {")
            appendLine("    alias(libs.plugins.android.application)")
            appendLine("    alias(libs.plugins.kotlin.compose)")
            appendLine("}")
            appendLine()
            appendLine("android {")
            appendLine("    namespace = \"$safePkg\"")
            appendLine("    compileSdk = 36")
            appendLine()
            appendLine("    defaultConfig {")
            appendLine("        applicationId = \"$safePkg\"")
            appendLine("        minSdk = 24")
            appendLine("        targetSdk = ${opts.targetSdk}")
            appendLine("        versionCode = ${opts.versionCode}")
            appendLine("        versionName = \"${opts.versionName}\"")
            appendLine("    }")
            appendLine()
            appendLine("    buildTypes {")
            appendLine("        ${opts.buildVariant} {")
            if (opts.variant == BuildVariant.RELEASE) {
                appendLine("            isCrunchPngs = ${opts.crunchPngs}")
            }
            appendLine("            isDebuggable = ${opts.includeDebuggable}")
            appendLine("            isMinifyEnabled = ${opts.minifyEnabled}")
            appendLine("            isShrinkResources = ${opts.shrinkResources}")
            appendLine("            proguardFiles(getDefaultProguardFile(\"proguard-android-optimize.txt\"), \"proguard-rules.pro\")")
            appendLine("        }")
            appendLine("    }")
            if (hasTflite) {
                appendLine()
                appendLine("    androidResources {")
                appendLine("        noCompress += \"tflite\"")
                appendLine("    }")
            }
            appendLine("}")
            appendLine()
            appendLine("dependencies {")
            appendLine("    implementation(platform(libs.androidx.compose.bom))")
            appendLine("    implementation(libs.androidx.core.ktx)")
            appendLine("    implementation(libs.androidx.activity.compose)")
            appendLine("    implementation(libs.androidx.compose.material3)")
            appendLine("    implementation(libs.androidx.navigation.compose)")
            if (hasTflite) {
                appendLine("    implementation(libs.litert)")
                appendLine("    implementation(libs.litert.gpu)")
            }
            appendLine("}")
        }.trimEnd()
    }

    fun auditProject(
        project: AppProject?,
        context: Context? = null,
        customGradleScript: String? = null
    ): ProjectLintAuditReport {
        val issues = mutableListOf<LintAuditIssue>()
        val dependencies = mutableListOf<DependencyAuditItem>()
        val gradleScript = customGradleScript ?: project?.let { synthesizeGradleScript(it) } ?: defaultSampleGradleWithWarnings()

        // Base Jetpack / Compose dependency inventory
        dependencies += DependencyAuditItem(
            aliasOrCoordinate = "androidx.compose:compose-bom",
            versionLabel = "2025.09.00",
            category = "UI Framework",
            statusNote = "BOM-aligned Compose libraries"
        )
        dependencies += DependencyAuditItem(
            aliasOrCoordinate = "androidx.navigation:navigation-compose",
            versionLabel = "2.8.9",
            category = "Navigation",
            statusNote = "Type-safe route controller"
        )
        dependencies += DependencyAuditItem(
            aliasOrCoordinate = "org.jetbrains.kotlinx:kotlinx-serialization-json",
            versionLabel = "1.8.0",
            category = "Serialization",
            statusNote = "Runtime JSON config & specs"
        )

        var scannedFiles = 4 // AppProject.kt, ProjectScreens.kt, ProjectViewModel.kt, build.gradle.kts

        if (project != null) {
            val opts = project.buildOptions

            // 1. AGP shrinkResources requires minifyEnabled
            if (opts.shrinkResources && !opts.minifyEnabled) {
                issues += LintAuditIssue(
                    id = "agp_shrink_without_minify",
                    title = "Shrink Resources Enabled Without Minify",
                    category = AuditCategory.GRADLE_BUILD,
                    severity = AuditSeverity.ERROR,
                    targetFile = "app/build.gradle.kts",
                    description = "AGP fails release/debug tasks when isShrinkResources = true while isMinifyEnabled = false.",
                    recommendation = "Turn on R8 code shrinking (isMinifyEnabled = true) before stripping unused resources.",
                    gradleSnippet = "isMinifyEnabled = true\nisShrinkResources = true",
                    fixKind = AuditFixKind.ENABLE_MINIFY_FOR_SHRINK_RESOURCES,
                    fixActionLabel = "Enable Minify"
                )
            }

            // 2. Release build with isDebuggable = true
            if (opts.variant == BuildVariant.RELEASE && opts.includeDebuggable) {
                issues += LintAuditIssue(
                    id = "gradle_release_debuggable",
                    title = "Release Variant Marked Debuggable",
                    category = AuditCategory.GRADLE_BUILD,
                    severity = AuditSeverity.WARNING,
                    targetFile = "AndroidManifest.xml",
                    description = "android:debuggable=\"true\" is enabled on a Release build, triggering Lint [HardcodedDebugMode] and Google Play rejection.",
                    recommendation = "Disable debuggable symbols on Release builds.",
                    gradleSnippet = "release { isDebuggable = false }",
                    fixKind = AuditFixKind.DISABLE_DEBUGGABLE_IN_RELEASE,
                    fixActionLabel = "Disable Debuggable"
                )
            }

            // 3. Release build without minification
            if (opts.variant == BuildVariant.RELEASE && !opts.minifyEnabled) {
                issues += LintAuditIssue(
                    id = "gradle_release_minify_off",
                    title = "Release Build Lacks R8 Minification",
                    category = AuditCategory.GRADLE_BUILD,
                    severity = AuditSeverity.WARNING,
                    targetFile = "app/build.gradle.kts",
                    description = "isMinifyEnabled is false for Release variant; unused classes and bytecode remain unoptimized.",
                    recommendation = "Enable R8 minification & bytecode optimization for smaller, faster APKs.",
                    gradleSnippet = "release { isMinifyEnabled = true }",
                    fixKind = AuditFixKind.ENABLE_RELEASE_MINIFY,
                    fixActionLabel = "Enable R8 Minify"
                )
            }

            // 4. Outdated targetSdk
            if (opts.targetSdk < 35) {
                issues += LintAuditIssue(
                    id = "lint_old_target_api",
                    title = "Outdated targetSdk (${opts.targetSdk})",
                    category = AuditCategory.GRADLE_BUILD,
                    severity = AuditSeverity.WARNING,
                    targetFile = "app/build.gradle.kts",
                    description = "Gradle Lint [OldTargetApi]: targetSdk ${opts.targetSdk} is below Google Play's API 35+ policy requirement.",
                    recommendation = "Update targetSdk to API 36 to satisfy Play Console & Android 16 readiness.",
                    gradleSnippet = "targetSdk = 36",
                    fixKind = AuditFixKind.UPGRADE_TARGET_SDK_36,
                    fixActionLabel = "Bump to API 36"
                )
            }

            // 5. Legacy v1-only APK signing
            if (!opts.signingScheme.equals("v1+v2", ignoreCase = true)) {
                issues += LintAuditIssue(
                    id = "signing_scheme_legacy_v1",
                    title = "Legacy JAR-Only Signing (${opts.signingScheme})",
                    category = AuditCategory.SIGNING_SECURITY,
                    severity = AuditSeverity.WARNING,
                    targetFile = "signingConfig",
                    description = "Android 11+ (API 30+) requires APK Signature Scheme v2+ alongside v1 JAR signing.",
                    recommendation = "Switch APK signing scheme to v1+v2.",
                    gradleSnippet = "enableV1Signing = true\nenableV2Signing = true",
                    fixKind = AuditFixKind.UPGRADE_SIGNING_SCHEME_V1_V2,
                    fixActionLabel = "Use v1+v2 Signing"
                )
            }

            // 6. Version code / name check
            if (opts.versionCode < 1 || !Regex("^\\d+(\\.\\d+){0,2}$").matches(opts.versionName.trim())) {
                issues += LintAuditIssue(
                    id = "gradle_invalid_version",
                    title = "Non-Standard Version Metadata",
                    category = AuditCategory.GRADLE_BUILD,
                    severity = AuditSeverity.WARNING,
                    targetFile = "app/build.gradle.kts",
                    description = "versionName '${opts.versionName}' or versionCode ${opts.versionCode} may break Play Store version monotonically checks.",
                    recommendation = "Normalize versionName to semver format (e.g. 1.0) and versionCode >= 1.",
                    gradleSnippet = "versionCode = 1\nversionName = \"1.0\"",
                    fixKind = AuditFixKind.NORMALIZE_VERSION_METADATA,
                    fixActionLabel = "Normalize Version"
                )
            }

            // 7. Keystore validation for Release builds when context is available
            if (context != null && opts.variant == BuildVariant.RELEASE) {
                val envCfg = KeystoreValidator.loadEnvSigningConfig(context)
                val jksFiles = KeystoreValidator.discoverJksFiles(context, envCfg)
                val signingReport = KeystoreValidator.buildReport(
                    targetVariant = opts.variant,
                    envConfig = envCfg,
                    jksFiles = jksFiles
                )
                if (signingReport.overallStatus == SigningOverallStatus.INVALID) {
                    issues += LintAuditIssue(
                        id = "signing_release_keystore_invalid",
                        title = "Release Keystore Validation Failed",
                        category = AuditCategory.SIGNING_SECURITY,
                        severity = AuditSeverity.ERROR,
                        targetFile = ".env / .jks",
                        description = signingReport.detailMessage,
                        recommendation = "Update STORE_PASSWORD/KEY_PASSWORD in AI Studio Secrets or import a valid .jks in Build Config."
                    )
                } else if (signingReport.overallStatus == SigningOverallStatus.WARNING) {
                    issues += LintAuditIssue(
                        id = "signing_release_keystore_warning",
                        title = "Release Keystore Not Configured",
                        category = AuditCategory.SIGNING_SECURITY,
                        severity = AuditSeverity.INFO,
                        targetFile = ".env / keystores/",
                        description = signingReport.detailMessage,
                        recommendation = "Import your upload .jks and verify credentials in Signing Summary before distribution."
                    )
                }
            }

            // 8. Local AI / TFLite dependencies check
            if (opts.bundledTfliteModels.isNotEmpty()) {
                scannedFiles += opts.bundledTfliteModels.size
                dependencies += DependencyAuditItem(
                    aliasOrCoordinate = "com.google.ai.edge.litert:litert",
                    versionLabel = "2.3.0",
                    category = "On-Device AI",
                    statusNote = "Required for ${opts.bundledTfliteModels.size} bundled .tflite model(s)"
                )
                if (context != null) {
                    val missingModels = opts.bundledTfliteModels.filter { modelName ->
                        val file = TfliteModelManager.findModelFile(context, modelName)
                        file == null || !file.isFile || file.length() == 0L
                    }
                    if (missingModels.isNotEmpty()) {
                        issues += LintAuditIssue(
                            id = "dep_missing_tflite_assets",
                            title = "Unresolved Bundled TFLite Model(s)",
                            category = AuditCategory.DEPENDENCIES,
                            severity = AuditSeverity.ERROR,
                            targetFile = "assets/models/",
                            description = "Selected model(s) not found on device: ${missingModels.joinToString(", ")}. APK build will warn or omit inference weights.",
                            recommendation = "Prune missing .tflite model references from project build options.",
                            fixKind = AuditFixKind.PRUNE_MISSING_TFLITE_MODELS,
                            fixActionLabel = "Prune Missing Models"
                        )
                    }
                }
            }

            // 9. Structure & ScreenSpec checks
            if (project.screens.isEmpty()) {
                issues += LintAuditIssue(
                    id = "struct_empty_screens",
                    title = "No Screens Defined in Navigation Graph",
                    category = AuditCategory.STRUCTURE_CODE,
                    severity = AuditSeverity.ERROR,
                    targetFile = "ProjectScreens.kt",
                    description = "Project '${project.name}' has 0 screens; NavHost requires at least one start destination.",
                    recommendation = "Add a default MainScreen destination so the app can render and navigate.",
                    fixKind = AuditFixKind.ADD_DEFAULT_SCREEN_SPEC,
                    fixActionLabel = "Add MainScreen"
                )
            } else if (project.screenSpecs.isEmpty() || project.screenSpecs.size < project.screens.size) {
                issues += LintAuditIssue(
                    id = "struct_incomplete_screen_specs",
                    title = "Incomplete ScreenSpec Metadata",
                    category = AuditCategory.STRUCTURE_CODE,
                    severity = AuditSeverity.INFO,
                    targetFile = "AppProject.kt",
                    description = "${project.screens.size} screen(s) declared without full ScreenSpec metadata for icons and layout components.",
                    recommendation = "Generate structured ScreenSpec definitions for all screens.",
                    fixKind = AuditFixKind.REPAIR_SCREEN_SPECS,
                    fixActionLabel = "Repair ScreenSpecs"
                )
            }

            if (project.primaryColorHex.isNotBlank() &&
                !Regex("^#[0-9A-Fa-f]{6}$").matches(project.primaryColorHex.trim())
            ) {
                issues += LintAuditIssue(
                    id = "struct_invalid_primary_hex",
                    title = "Malformed Theme Primary Color Hex",
                    category = AuditCategory.STRUCTURE_CODE,
                    severity = AuditSeverity.WARNING,
                    targetFile = "Theme.kt",
                    description = "primaryColorHex '${project.primaryColorHex}' does not match #RRGGBB format and can throw Color parsing errors.",
                    recommendation = "Reset primaryColorHex to a valid Material 3 seed hex.",
                    fixKind = AuditFixKind.FIX_PRIMARY_COLOR_HEX,
                    fixActionLabel = "Fix Color Hex"
                )
            }

            // Adaptive Launcher Icon resource check
            val hasDrawable = project.iconDrawableName.isNotBlank() ||
                ProjectDrawableInstaller.findInstalledPngFile(context, project.id) != null
            if (!hasDrawable) {
                issues += LintAuditIssue(
                    id = "struct_missing_custom_icon",
                    title = "Missing Custom Adaptive Launcher Icon",
                    category = AuditCategory.STRUCTURE_CODE,
                    severity = AuditSeverity.INFO,
                    targetFile = "res/mipmap-anydpi-v26/ic_launcher.xml",
                    description = "Project does not yet have an auto-installed res/drawable adaptive launcher icon asset.",
                    recommendation = "Use the App Icon Generator (Imagen Studio) to generate and install res/drawable adaptive icon XMLs."
                )
            }

            // Static code analyzer on exported Kotlin source
            val generatedCode = CodeExporter.buildProjectCode(project)
            val codeFindings = GeneratedCodeAnalyzer.analyze(generatedCode)
            codeFindings.forEachIndexed { idx, fix ->
                val mappedSeverity = when (fix.severity) {
                    com.example.model.CodeIssueSeverity.ERROR -> AuditSeverity.ERROR
                    com.example.model.CodeIssueSeverity.WARNING -> AuditSeverity.WARNING
                    com.example.model.CodeIssueSeverity.INFO -> AuditSeverity.INFO
                }
                issues += LintAuditIssue(
                    id = "code_static_${idx}_${fix.title.lowercase().replace(Regex("[^a-z0-9]+"), "_")}",
                    title = fix.title,
                    category = AuditCategory.STRUCTURE_CODE,
                    severity = mappedSeverity,
                    targetFile = "ProjectScreens.kt (${fix.lineHint})",
                    description = fix.problem,
                    recommendation = fix.suggestion,
                    gradleSnippet = fix.fixedSnippet
                )
            }
        }

        // 10. Scan Gradle script & dependency declarations for Gradle-specific warnings
        scanGradleScript(gradleScript, project?.buildOptions?.bundledTfliteModels.orEmpty(), issues, dependencies)

        val penalty = issues.sumOf {
            when (it.severity) {
                AuditSeverity.ERROR -> 18
                AuditSeverity.WARNING -> 9
                AuditSeverity.INFO -> 3
            }
        }
        val score = (100 - penalty).coerceIn(12, 100)

        return ProjectLintAuditReport(
            projectId = project?.id,
            projectName = project?.name ?: "Gradle Workspace Audit",
            analyzedGradleScript = gradleScript,
            issues = issues,
            dependencies = dependencies.distinctBy { it.aliasOrCoordinate },
            scannedFilesCount = scannedFiles,
            healthScore = score
        )
    }

    internal fun scanGradleScript(
        gradleText: String,
        bundledModels: List<String>,
        issues: MutableList<LintAuditIssue>,
        dependencies: MutableList<DependencyAuditItem>
    ) {
        if (gradleText.isBlank()) return

        // Deprecated 'compile ' / 'testCompile ' configuration
        if (Regex("""^\s*(compile|testCompile|androidTestCompile)\s*[\("']""", RegexOption.MULTILINE).containsMatchIn(gradleText)) {
            issues += LintAuditIssue(
                id = "gradle_deprecated_compile_config",
                title = "Deprecated 'compile' Dependency Configuration",
                category = AuditCategory.DEPENDENCIES,
                severity = AuditSeverity.WARNING,
                targetFile = "app/build.gradle.kts",
                description = "Legacy 'compile' / 'testCompile' configurations were removed in AGP 7+ and raise Gradle build errors/warnings.",
                recommendation = "Migrate 'compile' to 'implementation' and 'testCompile' to 'testImplementation'.",
                gradleSnippet = "implementation(libs.androidx.core.ktx)",
                fixKind = AuditFixKind.PATCH_GRADLE_DEPRECATED_COMPILE,
                fixActionLabel = "Migrate to implementation"
            )
        }

        // Deprecated compileSdkVersion / targetSdkVersion / minSdkVersion DSL
        if (Regex("""\b(compileSdkVersion|targetSdkVersion|minSdkVersion)\b""").containsMatchIn(gradleText)) {
            issues += LintAuditIssue(
                id = "gradle_legacy_sdk_dsl",
                title = "Deprecated AGP SDK Property Names",
                category = AuditCategory.GRADLE_BUILD,
                severity = AuditSeverity.WARNING,
                targetFile = "app/build.gradle.kts",
                description = "compileSdkVersion / minSdkVersion / targetSdkVersion are deprecated in AGP 8/9; use compileSdk =, minSdk =, targetSdk =.",
                recommendation = "Update property names to modern AGP 9 Kotlin DSL assignments.",
                gradleSnippet = "compileSdk = 36\nminSdk = 24\ntargetSdk = 36",
                fixKind = AuditFixKind.PATCH_GRADLE_LEGACY_SDK_DSL,
                fixActionLabel = "Update SDK DSL"
            )
        }

        // Script targetSdk below API 35 (when not already emitted for project.buildOptions)
        val scriptTargetMatch = Regex("""\btargetSdk\s*=\s*(\d+)""").find(gradleText)
        val scriptTargetSdk = scriptTargetMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (scriptTargetSdk != null && scriptTargetSdk < 35 &&
            issues.none { it.id == "lint_old_target_api" }
        ) {
            issues += LintAuditIssue(
                id = "gradle_script_old_target_api",
                title = "Gradle Script targetSdk Below API 35 ($scriptTargetSdk)",
                category = AuditCategory.GRADLE_BUILD,
                severity = AuditSeverity.WARNING,
                targetFile = "app/build.gradle.kts",
                description = "targetSdk = $scriptTargetSdk in build.gradle.kts triggers Lint [OldTargetApi] and misses Android 15/16 enforcement.",
                recommendation = "Bump targetSdk to 36.",
                gradleSnippet = "targetSdk = 36",
                fixKind = AuditFixKind.UPGRADE_TARGET_SDK_36,
                fixActionLabel = "Bump to API 36"
            )
        }

        // Legacy kotlinOptions { jvmTarget = ... }
        if (gradleText.contains("kotlinOptions")) {
            issues += LintAuditIssue(
                id = "gradle_deprecated_kotlin_options",
                title = "Deprecated kotlinOptions DSL",
                category = AuditCategory.GRADLE_BUILD,
                severity = AuditSeverity.WARNING,
                targetFile = "app/build.gradle.kts",
                description = "kotlinOptions { jvmTarget = ... } emits deprecation warnings in Kotlin 2.2+; use kotlin { compilerOptions { ... } }.",
                recommendation = "Migrate kotlinOptions block to compilerOptions DSL.",
                gradleSnippet = "kotlin {\n    compilerOptions {\n        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)\n    }\n}",
                fixKind = AuditFixKind.PATCH_GRADLE_KOTLIN_COMPILER_OPTIONS,
                fixActionLabel = "Migrate compilerOptions"
            )
        }

        // Deprecated jcenter() repository
        if (gradleText.contains("jcenter()")) {
            issues += LintAuditIssue(
                id = "gradle_deprecated_jcenter",
                title = "Deprecated jcenter() Repository",
                category = AuditCategory.DEPENDENCIES,
                severity = AuditSeverity.WARNING,
                targetFile = "settings.gradle.kts / build.gradle.kts",
                description = "jcenter() is shut down and causes Gradle sync warnings and slow network timeouts.",
                recommendation = "Replace jcenter() with mavenCentral().",
                gradleSnippet = "mavenCentral()",
                fixKind = AuditFixKind.PATCH_GRADLE_REMOVE_JCENTER,
                fixActionLabel = "Replace with mavenCentral()"
            )
        }

        // Missing noCompress += "tflite" in Gradle script when litert/tflite is present
        val usesTfliteInGradle = bundledModels.isNotEmpty() ||
            gradleText.contains("litert", ignoreCase = true) ||
            gradleText.contains("tensorflow", ignoreCase = true)
        if (usesTfliteInGradle && !gradleText.contains("noCompress") && !gradleText.contains("tflite")) {
            issues += LintAuditIssue(
                id = "gradle_tflite_nocompress_missing",
                title = "Missing noCompress += \"tflite\" Rule",
                category = AuditCategory.DEPENDENCIES,
                severity = AuditSeverity.WARNING,
                targetFile = "app/build.gradle.kts",
                description = "AAPT compresses asset files by default; compressed .tflite weights break AssetFileDescriptor memory-mapping in LiteRT.",
                recommendation = "Declare androidResources { noCompress += \"tflite\" } in android { ... }.",
                gradleSnippet = "androidResources {\n    noCompress += \"tflite\"\n}",
                fixKind = AuditFixKind.PATCH_GRADLE_TFLITE_NOCOMPRESS,
                fixActionLabel = "Add noCompress Rule"
            )
        }

        // Compose dependencies declared without BOM
        val usesComposeArtifacts = gradleText.contains("androidx.compose", ignoreCase = true)
        val hasComposeBom = gradleText.contains("compose-bom", ignoreCase = true) ||
            gradleText.contains("compose.bom", ignoreCase = true)
        if (usesComposeArtifacts && !hasComposeBom) {
            issues += LintAuditIssue(
                id = "gradle_missing_compose_bom",
                title = "Compose Artifacts Unpinned by BOM",
                category = AuditCategory.DEPENDENCIES,
                severity = AuditSeverity.WARNING,
                targetFile = "app/build.gradle.kts",
                description = "Jetpack Compose artifacts declared without platform(libs.androidx.compose.bom) risk mismatched runtime versions.",
                recommendation = "Add implementation(platform(libs.androidx.compose.bom)) at the top of dependencies { }.",
                gradleSnippet = "implementation(platform(libs.androidx.compose.bom))",
                fixKind = AuditFixKind.PATCH_GRADLE_ADD_COMPOSE_BOM,
                fixActionLabel = "Add Compose BOM"
            )
        }

        // Shrink resources in script without minify
        if (gradleText.contains("isShrinkResources = true") && gradleText.contains("isMinifyEnabled = false")) {
            issues += LintAuditIssue(
                id = "gradle_script_shrink_no_minify",
                title = "Gradle Script ShrinkResources Conflict",
                category = AuditCategory.GRADLE_BUILD,
                severity = AuditSeverity.ERROR,
                targetFile = "app/build.gradle.kts",
                description = "buildTypes sets isShrinkResources = true while isMinifyEnabled = false.",
                recommendation = "Set isMinifyEnabled = true in the same build type.",
                gradleSnippet = "isMinifyEnabled = true",
                fixKind = AuditFixKind.PATCH_GRADLE_SHRINK_WITHOUT_MINIFY,
                fixActionLabel = "Fix Gradle Minify"
            )
        }

        // Duplicate dependency lines
        val depLines = gradleText.lines()
            .map { it.trim() }
            .filter {
                (it.startsWith("implementation(") || it.startsWith("testImplementation(")) &&
                    !it.contains("platform(")
            }
        val dupDep = depLines.groupingBy { it }.eachCount().entries.firstOrNull { it.value > 1 }?.key
        if (dupDep != null) {
            issues += LintAuditIssue(
                id = "gradle_duplicate_dependency",
                title = "Duplicate Gradle Dependency Entry",
                category = AuditCategory.DEPENDENCIES,
                severity = AuditSeverity.WARNING,
                targetFile = "app/build.gradle.kts",
                description = "Duplicate declaration detected: $dupDep",
                recommendation = "Remove duplicate dependency entries to keep dependency resolution clean.",
                gradleSnippet = dupDep,
                fixKind = AuditFixKind.PATCH_GRADLE_DEDUPLICATE_DEPS,
                fixActionLabel = "Deduplicate Dependencies"
            )
        }

        // Extract explicit implementation(...) items into DependencyAuditItem inventory
        val depRegex = Regex("""(?:implementation|api|ksp)\s*\(\s*([^\)]+)\s*\)""")
        depRegex.findAll(gradleText).forEach { m ->
            val expr = m.groupValues[1].trim()
            if (!expr.startsWith("platform(")) {
                dependencies += DependencyAuditItem(
                    aliasOrCoordinate = expr.removeSurrounding("\""),
                    versionLabel = if (expr.startsWith("libs.")) "Catalog" else "Inline",
                    category = "Declared in Script",
                    statusNote = "Scanned from build.gradle.kts",
                    hasWarning = expr.contains(":+")
                )
            }
        }
    }

    fun applyProjectFix(
        project: AppProject,
        fixKind: AuditFixKind,
        context: Context? = null
    ): AppProject {
        val opts = project.buildOptions
        return when (fixKind) {
            AuditFixKind.ENABLE_MINIFY_FOR_SHRINK_RESOURCES ->
                project.copy(buildOptions = opts.copy(minifyEnabled = true))

            AuditFixKind.DISABLE_DEBUGGABLE_IN_RELEASE ->
                project.copy(buildOptions = opts.copy(includeDebuggable = false))

            AuditFixKind.ENABLE_RELEASE_MINIFY ->
                project.copy(buildOptions = opts.copy(minifyEnabled = true, optimizeBytecode = true))

            AuditFixKind.UPGRADE_TARGET_SDK_36 ->
                project.copy(buildOptions = opts.copy(targetSdk = 36))

            AuditFixKind.UPGRADE_SIGNING_SCHEME_V1_V2 ->
                project.copy(buildOptions = opts.copy(signingScheme = "v1+v2"))

            AuditFixKind.NORMALIZE_VERSION_METADATA ->
                project.copy(
                    buildOptions = opts.copy(
                        versionCode = opts.versionCode.coerceAtLeast(1),
                        versionName = if (Regex("^\\d+(\\.\\d+){0,2}$").matches(opts.versionName.trim())) {
                            opts.versionName.trim()
                        } else {
                            "1.0"
                        }
                    )
                )

            AuditFixKind.PRUNE_MISSING_TFLITE_MODELS -> {
                if (context == null) return project
                val validModels = opts.bundledTfliteModels.filter { modelName ->
                    val file = TfliteModelManager.findModelFile(context, modelName)
                    file != null && file.isFile && file.length() > 0L
                }
                project.copy(buildOptions = opts.copy(bundledTfliteModels = validModels))
            }

            AuditFixKind.ADD_DEFAULT_SCREEN_SPEC -> {
                val baseScreens = listOf("MainScreen")
                val patched = project.copy(screens = baseScreens)
                patched.copy(screenSpecs = ScreenSpecs.all(patched))
            }

            AuditFixKind.REPAIR_SCREEN_SPECS ->
                project.copy(screenSpecs = ScreenSpecs.all(project))

            AuditFixKind.FIX_PRIMARY_COLOR_HEX -> {
                val cleaned = project.primaryColorHex.trim().removePrefix("#")
                    .filter { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
                val validHex = if (cleaned.length >= 6) {
                    "#${cleaned.takeLast(6).uppercase()}"
                } else {
                    designFor(project).toHexLiteral(designFor(project).primaryColor)
                        .let { hex -> "#${hex.takeLast(6)}" }
                }
                project.copy(primaryColorHex = validHex)
            }

            else -> project
        }
    }

    fun applyGradleScriptFix(
        gradleText: String,
        fixKind: AuditFixKind
    ): String {
        return when (fixKind) {
            AuditFixKind.PATCH_GRADLE_DEPRECATED_COMPILE -> gradleText
                .replace(Regex("""(?m)^(\s*)compile(\s*[\("'])"""), "$1implementation$2")
                .replace(Regex("""(?m)^(\s*)testCompile(\s*[\("'])"""), "$1testImplementation$2")
                .replace(Regex("""(?m)^(\s*)androidTestCompile(\s*[\("'])"""), "$1androidTestImplementation$2")

            AuditFixKind.PATCH_GRADLE_LEGACY_SDK_DSL -> gradleText
                .replace(Regex("""\bcompileSdkVersion\s*\(?\s*(\d+)\s*\)?"""), "compileSdk = $1")
                .replace(Regex("""\btargetSdkVersion\s*\(?\s*(\d+)\s*\)?"""), "targetSdk = $1")
                .replace(Regex("""\bminSdkVersion\s*\(?\s*(\d+)\s*\)?"""), "minSdk = $1")

            AuditFixKind.PATCH_GRADLE_KOTLIN_COMPILER_OPTIONS -> gradleText.replace(
                Regex("""kotlinOptions\s*\{[^}]*\}"""),
                """
                kotlin {
                    compilerOptions {
                        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
                    }
                }
                """.trimIndent()
            )

            AuditFixKind.PATCH_GRADLE_REMOVE_JCENTER ->
                gradleText.replace("jcenter()", "mavenCentral()")

            AuditFixKind.PATCH_GRADLE_TFLITE_NOCOMPRESS -> {
                if (gradleText.contains("noCompress")) return gradleText
                val block = "\n    androidResources {\n        noCompress += \"tflite\"\n    }"
                val androidClosingIdx = gradleText.indexOf("buildTypes")
                if (androidClosingIdx != -1) {
                    val insertAt = gradleText.indexOf("dependencies")
                    val targetBrace = if (insertAt != -1) gradleText.lastIndexOf('}', insertAt) else gradleText.lastIndexOf('}')
                    if (targetBrace > 0) {
                        gradleText.substring(0, targetBrace) + block + "\n" + gradleText.substring(targetBrace)
                    } else {
                        "$gradleText\nandroid {$block\n}"
                    }
                } else {
                    "$gradleText\nandroid {$block\n}"
                }
            }

            AuditFixKind.PATCH_GRADLE_ADD_COMPOSE_BOM -> {
                if (gradleText.contains("compose.bom") || gradleText.contains("compose-bom")) return gradleText
                gradleText.replaceFirst(
                    Regex("""dependencies\s*\{"""),
                    "dependencies {\n    implementation(platform(libs.androidx.compose.bom))"
                )
            }

            AuditFixKind.PATCH_GRADLE_SHRINK_WITHOUT_MINIFY ->
                gradleText.replace("isMinifyEnabled = false", "isMinifyEnabled = true")

            AuditFixKind.PATCH_GRADLE_DEDUPLICATE_DEPS -> {
                val seenDeps = mutableSetOf<String>()
                gradleText.lines().filter { line ->
                    val trimmed = line.trim()
                    val isDep = (trimmed.startsWith("implementation(") || trimmed.startsWith("testImplementation(")) &&
                        !trimmed.contains("platform(")
                    if (!isDep) true else seenDeps.add(trimmed)
                }.joinToString("\n")
            }

            AuditFixKind.ENABLE_MINIFY_FOR_SHRINK_RESOURCES,
            AuditFixKind.ENABLE_RELEASE_MINIFY ->
                gradleText.replace("isMinifyEnabled = false", "isMinifyEnabled = true")

            AuditFixKind.DISABLE_DEBUGGABLE_IN_RELEASE ->
                gradleText.replace("isDebuggable = true", "isDebuggable = false")

            AuditFixKind.UPGRADE_TARGET_SDK_36 ->
                gradleText
                    .replace(Regex("""targetSdk\s*=\s*\d+"""), "targetSdk = 36")
                    .replace(Regex("""targetSdkVersion\s*\(?\s*\d+\s*\)?"""), "targetSdk = 36")

            AuditFixKind.NORMALIZE_VERSION_METADATA ->
                gradleText
                    .replace(Regex("""versionCode\s*=\s*-?\d+"""), "versionCode = 1")
                    .replace(Regex("""versionName\s*=\s*"[^"]*""""), "versionName = \"1.0\"")

            else -> gradleText
        }
    }

    fun defaultSampleGradleWithWarnings(): String = """
        repositories {
            google()
            jcenter()
        }

        android {
            compileSdkVersion 36
            defaultConfig {
                minSdkVersion 24
                targetSdkVersion 34
                versionCode = 1
                versionName = "1.0"
            }
            buildTypes {
                release {
                    isMinifyEnabled = false
                    isShrinkResources = true
                }
            }
            kotlinOptions {
                jvmTarget = "11"
            }
        }

        dependencies {
            compile(libs.androidx.core.ktx)
            implementation("androidx.compose.ui:ui")
            implementation(libs.litert)
            implementation(libs.litert)
        }
    """.trimIndent()
}
