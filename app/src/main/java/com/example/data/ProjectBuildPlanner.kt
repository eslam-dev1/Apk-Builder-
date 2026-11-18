package com.example.data

enum class BuildModeStrategy(val displayName: String) {
    AUTO("Auto (Offline First)"),
    OFFLINE("Offline Only"),
    CLOUD("Cloud AI")
}

enum class RequirementProfile(val label: String) {
    OFFLINE_CAPABLE("Offline-Capable"),
    CLOUD_REQUIRED("Cloud-Connected")
}

data class ProjectRequirementAssessment(
    val profile: RequirementProfile,
    val matchedCloudSignals: List<String> = emptyList(),
    val reason: String
)

data class OfflineAttemptResult(
    val succeeded: Boolean,
    val failureReason: String,
    val installedModelFile: String? = null
)

data class BuildRoutingPlan(
    val strategy: BuildModeStrategy,
    val assessment: ProjectRequirementAssessment,
    val triedOfflineFirst: Boolean,
    val offlineAttempt: OfflineAttemptResult?,
    val useCloudAi: Boolean,
    val targetProvider: CloudAiProvider,
    val statusSummary: String
)

/**
 * Inspects project prompt requirements, attempts Offline build/generation first when in AUTO or
 * OFFLINE mode, and automatically falls back to Cloud AI (Gemini or OpenRouter) when offline
 * generation is unavailable or the project requires online capabilities.
 */
object ProjectBuildPlanner {

    private val CLOUD_SIGNAL_KEYWORDS = listOf(
        // English signals
        "api", "backend", "server", "cloud", "sync", "realtime", "real-time", "firebase",
        "supabase", "auth", "login", "signup", "oauth", "chat", "messaging", "social",
        "multiplayer", "live ", "weather", "maps", "gps", "location", "stocks", "crypto",
        "payment", "stripe", "checkout", "e-commerce", "store", "shop", "news feed",
        "streaming", "ai assistant", "chatbot", "gemini", "openrouter", "openai",
        // Arabic signals
        "سحابي", "سحابة", "مزامنة", "سيرفر", "خادم", "انترنت", "إنترنت", "أونلاين",
        "دردشة", "تواصل", "تسجيل دخول", "حسابات", "متعدد اللاعبين", "خرائط", "طقس",
        "مباشر", "دفع", "متجر", "تجارة إلكترونية", "ذكاء اصطناعي", "أخبار"
    )

    fun assessRequirements(prompt: String): ProjectRequirementAssessment {
        val normalized = prompt.trim().lowercase()
        if (normalized.isEmpty()) {
            return ProjectRequirementAssessment(
                profile = RequirementProfile.OFFLINE_CAPABLE,
                matchedCloudSignals = emptyList(),
                reason = "No prompt entered yet."
            )
        }

        val matches = CLOUD_SIGNAL_KEYWORDS
            .filter { keyword -> normalized.contains(keyword) }
            .map { it.trim() }
            .distinct()

        return if (matches.isNotEmpty()) {
            ProjectRequirementAssessment(
                profile = RequirementProfile.CLOUD_REQUIRED,
                matchedCloudSignals = matches.take(4),
                reason = "Detected online/cloud project requirements (${matches.take(3).joinToString(", ")})."
            )
        } else {
            ProjectRequirementAssessment(
                profile = RequirementProfile.OFFLINE_CAPABLE,
                matchedCloudSignals = emptyList(),
                reason = "Project requirements can work locally without external cloud dependencies."
            )
        }
    }

    fun attemptOfflineGeneration(
        installedFiles: Set<String>,
        selectedModelFile: String?,
        assessment: ProjectRequirementAssessment,
        isOfflineRuntimeAvailable: Boolean = false
    ): OfflineAttemptResult {
        if (assessment.profile == RequirementProfile.CLOUD_REQUIRED) {
            return OfflineAttemptResult(
                succeeded = false,
                failureReason = "Offline skipped: ${assessment.reason}",
                installedModelFile = null
            )
        }

        val resolvedModel = selectedModelFile?.takeIf { it in installedFiles }
            ?: installedFiles.firstOrNull()

        if (resolvedModel == null) {
            return OfflineAttemptResult(
                succeeded = false,
                failureReason = "Offline unavailable: no local .litertlm model is downloaded yet.",
                installedModelFile = null
            )
        }

        if (!isOfflineRuntimeAvailable) {
            return OfflineAttemptResult(
                succeeded = false,
                failureReason = "Offline unavailable: local model ($resolvedModel) is downloaded, but no on-device inference runtime is active.",
                installedModelFile = resolvedModel
            )
        }

        return OfflineAttemptResult(
            succeeded = true,
            failureReason = "",
            installedModelFile = resolvedModel
        )
    }

    fun planBuild(
        prompt: String,
        strategy: BuildModeStrategy,
        activeProvider: CloudAiProvider,
        installedFiles: Set<String>,
        selectedModelFile: String?,
        isOfflineRuntimeAvailable: Boolean = false
    ): BuildRoutingPlan {
        val assessment = assessRequirements(prompt)

        return when (strategy) {
            BuildModeStrategy.OFFLINE -> {
                val attempt = attemptOfflineGeneration(
                    installedFiles = installedFiles,
                    selectedModelFile = selectedModelFile,
                    assessment = assessment,
                    isOfflineRuntimeAvailable = isOfflineRuntimeAvailable
                )
                BuildRoutingPlan(
                    strategy = BuildModeStrategy.OFFLINE,
                    assessment = assessment,
                    triedOfflineFirst = true,
                    offlineAttempt = attempt,
                    useCloudAi = false,
                    targetProvider = activeProvider,
                    statusSummary = if (attempt.succeeded) {
                        "Offline build using ${attempt.installedModelFile}"
                    } else {
                        "Offline Only: ${attempt.failureReason}"
                    }
                )
            }

            BuildModeStrategy.CLOUD -> {
                BuildRoutingPlan(
                    strategy = BuildModeStrategy.CLOUD,
                    assessment = assessment,
                    triedOfflineFirst = false,
                    offlineAttempt = null,
                    useCloudAi = true,
                    targetProvider = activeProvider,
                    statusSummary = "Direct Cloud AI mode (${activeProvider.displayName}) — ${assessment.profile.label}"
                )
            }

            BuildModeStrategy.AUTO -> {
                val attempt = attemptOfflineGeneration(
                    installedFiles = installedFiles,
                    selectedModelFile = selectedModelFile,
                    assessment = assessment,
                    isOfflineRuntimeAvailable = isOfflineRuntimeAvailable
                )
                if (attempt.succeeded) {
                    BuildRoutingPlan(
                        strategy = BuildModeStrategy.AUTO,
                        assessment = assessment,
                        triedOfflineFirst = true,
                        offlineAttempt = attempt,
                        useCloudAi = false,
                        targetProvider = activeProvider,
                        statusSummary = "Tried Offline first → Ready with ${attempt.installedModelFile}"
                    )
                } else {
                    BuildRoutingPlan(
                        strategy = BuildModeStrategy.AUTO,
                        assessment = assessment,
                        triedOfflineFirst = true,
                        offlineAttempt = attempt,
                        useCloudAi = true,
                        targetProvider = activeProvider,
                        statusSummary = "Tried Offline first (${attempt.failureReason}) → Falling back to Cloud AI (${activeProvider.displayName})"
                    )
                }
            }
        }
    }
}
