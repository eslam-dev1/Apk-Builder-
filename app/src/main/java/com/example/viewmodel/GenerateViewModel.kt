package com.example.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ai.LocalLlmEngine
import com.example.ai.LocalPromptMatcher
import com.example.ai.ModelManager
import com.example.ai.OfflineImageOcrExtractor
import com.example.data.BuildModeStrategy
import com.example.data.BuildRoutingPlan
import com.example.data.CloudAiConfig
import com.example.data.CloudAiProvider
import com.example.data.CodeExporter
import com.example.data.GeminiService
import com.example.data.GeneratedCodeAnalyzer
import com.example.data.MissingApiKeyException
import com.example.data.MissingOpenRouterKeyException
import com.example.data.OfflineAttemptResult
import com.example.data.ProjectBuildPlanner
import com.example.data.ProjectDrawableInstaller
import com.example.data.ProjectRepository
import com.example.data.RequirementProfile
import com.example.design.designFor
import com.example.model.AiFeatureIdea
import com.example.model.AppIconUiState
import com.example.model.AppProject
import com.example.model.CodeAnalysisResult
import com.example.model.CodeAnalysisUiState
import com.example.model.TabBuildPreference
import com.example.model.TabSelectionMode
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class GenerateViewModel(
    private val projectRepository: ProjectRepository? = null,
    private val geminiService: GeminiService = GeminiService()
) : ViewModel() {

    private val _prompt = MutableStateFlow("")
    val prompt: StateFlow<String> = _prompt.asStateFlow()

    private val _generatedProject = MutableStateFlow<AppProject?>(null)
    val generatedProject: StateFlow<AppProject?> = _generatedProject.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isScanningImage = MutableStateFlow(false)
    val isScanningImage: StateFlow<Boolean> = _isScanningImage.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _savedProjects = MutableStateFlow<List<AppProject>>(emptyList())
    val savedProjects: StateFlow<List<AppProject>> = _savedProjects.asStateFlow()

    private val _aiFeatureSuggestions = MutableStateFlow<Map<String, List<AiFeatureIdea>>>(emptyMap())
    val aiFeatureSuggestions: StateFlow<Map<String, List<AiFeatureIdea>>> = _aiFeatureSuggestions.asStateFlow()

    private val _isAnalyzingFeatures = MutableStateFlow(false)
    val isAnalyzingFeatures: StateFlow<Boolean> = _isAnalyzingFeatures.asStateFlow()

    private val _appIconStates = MutableStateFlow<Map<String, AppIconUiState>>(emptyMap())
    val appIconStates: StateFlow<Map<String, AppIconUiState>> = _appIconStates.asStateFlow()

    private val _codeAnalysisStates = MutableStateFlow<Map<String, CodeAnalysisUiState>>(emptyMap())
    val codeAnalysisStates: StateFlow<Map<String, CodeAnalysisUiState>> = _codeAnalysisStates.asStateFlow()

    private val _lastBuildRoutingPlan = MutableStateFlow<BuildRoutingPlan?>(null)
    val lastBuildRoutingPlan: StateFlow<BuildRoutingPlan?> = _lastBuildRoutingPlan.asStateFlow()

    private val _tabPreference = MutableStateFlow(TabBuildPreference())
    val tabPreference: StateFlow<TabBuildPreference> = _tabPreference.asStateFlow()

    private val _customTabNamesInput = MutableStateFlow("")
    val customTabNamesInput: StateFlow<String> = _customTabNamesInput.asStateFlow()

    private val _isFirstLaunch = MutableStateFlow<Boolean?>(
        if (projectRepository == null) false else null
    )
    val isFirstLaunch: StateFlow<Boolean?> = _isFirstLaunch.asStateFlow()

    init {
        projectRepository?.let { repo ->
            viewModelScope.launch {
                repo.isFirstLaunch.collect { firstLaunch ->
                    _isFirstLaunch.value = firstLaunch
                }
            }
            viewModelScope.launch {
                repo.savedProjects.collect { persistedList ->
                    _savedProjects.value = persistedList
                }
            }
        }
    }

    fun completeOnboarding() {
        _isFirstLaunch.value = false
        viewModelScope.launch {
            projectRepository?.setFirstLaunchCompleted()
        }
    }

    fun isApiKeyConfigured(): Boolean = !geminiService.isApiKeyMissing()

    fun activeProvider(): CloudAiProvider = CloudAiConfig.currentProvider.value

    private fun missingKeyErrorMessage(): String = when (CloudAiConfig.currentProvider.value) {
        CloudAiProvider.OPENROUTER ->
            "OpenRouter API Key missing. Enter it in Settings or set OPENROUTER_API_KEY in AI Studio Secrets."
        CloudAiProvider.GEMINI ->
            "API Key missing"
    }

    private fun offlineGenerationMessage(): String {
        val installed = ModelManager.installedFiles.value
        val activeFile = ModelManager.selectedModelFile.value?.takeIf { it in installed }
            ?: installed.firstOrNull()
        return if (activeFile == null) {
            "Offline Mode is enabled, but no offline .litertlm model is downloaded yet. Open Models to download Gemma 4 or Qwen 3, or turn off Offline Mode to use ${CloudAiConfig.currentProvider.value.displayName}."
        } else {
            "Offline Mode is active with downloaded model ($activeFile). Native .litertlm inference is unavailable in this build — turn off Offline Mode to generate blueprints via Gemini or OpenRouter, or run static code analysis and APK builds offline."
        }
    }

    private fun buildPlanFor(cleanPrompt: String): BuildRoutingPlan {
        return ProjectBuildPlanner.planBuild(
            prompt = cleanPrompt,
            strategy = CloudAiConfig.buildModeStrategy.value,
            activeProvider = CloudAiConfig.currentProvider.value,
            installedFiles = ModelManager.installedFiles.value,
            selectedModelFile = ModelManager.selectedModelFile.value,
            isOfflineRuntimeAvailable = LocalLlmEngine.isNativeRuntimeSupported()
        )
    }

    fun onPromptChange(newPrompt: String) {
        _prompt.value = newPrompt
        if (newPrompt.isBlank()) {
            _lastBuildRoutingPlan.value = null
        }
        if (_error.value != null) {
            _error.value = null
        }
    }

    fun setTabSelectionMode(mode: TabSelectionMode) {
        _tabPreference.update { it.copy(mode = mode) }
    }

    fun setRequestedTabCount(count: Int) {
        val clamped = count.coerceIn(
            TabBuildPreference.MIN_USER_TABS,
            TabBuildPreference.MAX_USER_TABS
        )
        _tabPreference.update {
            it.copy(
                mode = TabSelectionMode.USER_DEFINED,
                requestedCount = clamped
            )
        }
    }

    fun onCustomTabNamesChange(raw: String) {
        _customTabNamesInput.value = raw
        val parsed = TabBuildPreference.parseCustomTabNames(raw)
        _tabPreference.update { current ->
            current.copy(
                mode = TabSelectionMode.USER_DEFINED,
                customTabNames = parsed,
                requestedCount = if (parsed.isNotEmpty()) parsed.size else current.requestedCount
            )
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun generateOfflineScaffold(promptInput: String = _prompt.value) {
        val cleanPrompt = promptInput.trim()
        if (cleanPrompt.isBlank()) {
            _error.value = "Please enter a prompt first"
            return
        }
        val project = LocalPromptMatcher.buildOfflineScaffold(
            prompt = cleanPrompt,
            tabPreference = _tabPreference.value
        )
        _lastBuildRoutingPlan.value = BuildRoutingPlan(
            strategy = BuildModeStrategy.OFFLINE,
            assessment = ProjectBuildPlanner.assessRequirements(cleanPrompt),
            triedOfflineFirst = true,
            offlineAttempt = OfflineAttemptResult(
                succeeded = true,
                failureReason = "",
                installedModelFile = "Local Scaffold RAG"
            ),
            useCloudAi = false,
            targetProvider = CloudAiConfig.currentProvider.value,
            statusSummary = "Generated offline using Local Scaffold RAG (${project.screenSpecs.size} screens)"
        )
        _error.value = null
        _generatedProject.value = project
    }

    fun extractPromptFromImage(context: Context, imageUri: Uri) {
        viewModelScope.launch {
            _isScanningImage.value = true
            _error.value = null
            val result = OfflineImageOcrExtractor.extractAppPromptFromImage(context, imageUri)
            _isScanningImage.value = false
            result.onSuccess { extractedPrompt ->
                _prompt.value = extractedPrompt
            }.onFailure { err ->
                _error.value = err.localizedMessage ?: "Failed to extract text from selected image."
            }
        }
    }

    fun generateProject(
        promptInput: String = _prompt.value,
        context: Context? = null
    ) {
        val cleanPrompt = promptInput.trim()
        if (cleanPrompt.isBlank()) {
            _error.value = "Please enter a prompt first"
            return
        }

        val plan = buildPlanFor(cleanPrompt)
        _lastBuildRoutingPlan.value = plan
        val activeTabPref = _tabPreference.value

        if (!plan.useCloudAi) {
            val offlineModelFile = plan.offlineAttempt?.installedModelFile
            if (plan.offlineAttempt?.succeeded == true && offlineModelFile != null && context != null) {
                viewModelScope.launch {
                    _isLoading.value = true
                    _error.value = null
                    try {
                        val offlineResult = LocalLlmEngine.generateAppProjectOffline(
                            context = context,
                            modelFileName = offlineModelFile,
                            prompt = cleanPrompt,
                            tabPreference = activeTabPref,
                            geminiService = geminiService
                        )
                        offlineResult.onSuccess { project ->
                            _generatedProject.value = project
                        }.onFailure {
                            // Fall back to deterministic Local Scaffold RAG so Offline Mode completes cleanly
                            _generatedProject.value = LocalPromptMatcher.buildOfflineScaffold(
                                prompt = cleanPrompt,
                                tabPreference = activeTabPref
                            )
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } finally {
                        _isLoading.value = false
                    }
                }
            } else {
                _error.value = offlineGenerationMessage()
                _generatedProject.value = null
            }
            return
        }

        if (geminiService.isApiKeyMissing()) {
            _error.value = if (plan.triedOfflineFirst) {
                "${plan.statusSummary}. ${missingKeyErrorMessage()}"
            } else {
                missingKeyErrorMessage()
            }
            _generatedProject.value = null
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val project = geminiService.generateApp(cleanPrompt, activeTabPref)
                _generatedProject.value = project
            } catch (e: CancellationException) {
                throw e
            } catch (e: MissingApiKeyException) {
                _error.value = "API Key missing"
                _generatedProject.value = null
            } catch (e: MissingOpenRouterKeyException) {
                _error.value = missingKeyErrorMessage()
                _generatedProject.value = null
            } catch (e: IOException) {
                _error.value = e.message?.takeIf { it.isNotBlank() }
                    ?: "Network error: unable to reach ${CloudAiConfig.currentProvider.value.displayName}. Check your internet connection."
                _generatedProject.value = null
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to generate app idea"
                _generatedProject.value = null
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun regenerateProject(
        projectId: String,
        newPromptInput: String,
        overrideTabPreference: TabBuildPreference = _tabPreference.value,
        context: Context? = null
    ) {
        val cleanPrompt = newPromptInput.trim()
        if (cleanPrompt.isBlank()) {
            _error.value = "Please enter a prompt first"
            return
        }

        val plan = buildPlanFor(cleanPrompt)
        _lastBuildRoutingPlan.value = plan

        if (!plan.useCloudAi) {
            val offlineModelFile = plan.offlineAttempt?.installedModelFile
            if (plan.offlineAttempt?.succeeded == true && offlineModelFile != null && context != null) {
                viewModelScope.launch {
                    _isLoading.value = true
                    _error.value = null
                    try {
                        val offlineResult = LocalLlmEngine.generateAppProjectOffline(
                            context = context,
                            modelFileName = offlineModelFile,
                            prompt = cleanPrompt,
                            tabPreference = overrideTabPreference,
                            geminiService = geminiService
                        )
                        val generated = offlineResult.getOrElse {
                            LocalPromptMatcher.buildOfflineScaffold(cleanPrompt, overrideTabPreference)
                        }
                        updateSavedProjectInList(
                            projectId,
                            generated.copy(id = projectId, prompt = cleanPrompt, createdAt = System.currentTimeMillis())
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } finally {
                        _isLoading.value = false
                    }
                }
            } else {
                _error.value = offlineGenerationMessage()
            }
            return
        }

        if (geminiService.isApiKeyMissing()) {
            _error.value = if (plan.triedOfflineFirst) {
                "${plan.statusSummary}. ${missingKeyErrorMessage()}"
            } else {
                missingKeyErrorMessage()
            }
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val freshGenerated = geminiService.generateApp(cleanPrompt, overrideTabPreference)
                val updatedProject = freshGenerated.copy(
                    id = projectId,
                    prompt = cleanPrompt,
                    createdAt = System.currentTimeMillis()
                )
                updateSavedProjectInList(projectId, updatedProject)
            } catch (e: CancellationException) {
                throw e
            } catch (e: MissingApiKeyException) {
                _error.value = "API Key missing"
            } catch (e: MissingOpenRouterKeyException) {
                _error.value = missingKeyErrorMessage()
            } catch (e: IOException) {
                _error.value = e.message?.takeIf { it.isNotBlank() }
                    ?: "Network error: unable to reach ${CloudAiConfig.currentProvider.value.displayName}. Check your internet connection."
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to regenerate app idea"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun generateProjectIcon(
        project: AppProject,
        customPrompt: String? = null,
        styleOverride: String? = null,
        drawableName: String = ProjectDrawableInstaller.DEFAULT_DRAWABLE_NAME,
        context: Context? = null
    ) {
        val effectiveStyle = styleOverride?.takeIf { it.isNotBlank() }
            ?: project.designStyle.ifBlank { designFor(project).designStyle }
        val effectivePrompt = customPrompt?.takeIf { it.isNotBlank() }
            ?: "Android app launcher icon for \"${project.name}\": ${project.description}"
        generateLauncherIconInternal(
            stateKey = project.id,
            targetProject = project,
            prompt = effectivePrompt,
            style = effectiveStyle,
            drawableName = drawableName,
            context = context
        )
    }

    fun generateLauncherIconFromStudio(
        prompt: String,
        style: String,
        targetProject: AppProject?,
        drawableName: String = ProjectDrawableInstaller.DEFAULT_DRAWABLE_NAME,
        context: Context? = null
    ) {
        val key = targetProject?.id ?: ProjectDrawableInstaller.DEFAULT_WORKSPACE_ID
        val effectivePrompt = prompt.trim().ifBlank {
            if (targetProject != null) {
                "Android app launcher icon for \"${targetProject.name}\": ${targetProject.description}"
            } else {
                ""
            }
        }
        if (effectivePrompt.isBlank()) {
            _appIconStates.update {
                it + (key to AppIconUiState.Error("Please enter a prompt describing the launcher icon."))
            }
            return
        }
        generateLauncherIconInternal(
            stateKey = key,
            targetProject = targetProject,
            prompt = effectivePrompt,
            style = style.ifBlank { "Modern" },
            drawableName = drawableName,
            context = context
        )
    }

    private fun generateLauncherIconInternal(
        stateKey: String,
        targetProject: AppProject?,
        prompt: String,
        style: String,
        drawableName: String,
        context: Context?
    ) {
        viewModelScope.launch {
            _appIconStates.update { it + (stateKey to AppIconUiState.Loading) }
            try {
                val (bitmap, engineLabel) = withTimeout(120_000L) {
                    geminiService.generateLauncherIconWithImagen(
                        userPrompt = prompt,
                        designStyle = style,
                        projectName = targetProject?.name.orEmpty(),
                        description = targetProject?.description.orEmpty()
                    )
                }

                var installedPaths = emptyList<String>()
                var backgroundHex = ""
                var drawableDirPath = ""
                if (context != null) {
                    val installed = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        ProjectDrawableInstaller.installLauncherIcon(
                            context = context,
                            projectId = stateKey,
                            bitmap = bitmap,
                            resourceName = drawableName
                        )
                    }
                    installedPaths = installed.relativePaths
                    backgroundHex = installed.backgroundHex
                    drawableDirPath = installed.resDir.absolutePath

                    if (targetProject != null) {
                        val updated = targetProject.copy(
                            iconDrawableName = installed.drawableName,
                            iconBackgroundHex = installed.backgroundHex
                        )
                        updateSavedProjectInList(targetProject.id, updated)
                    }
                }

                _appIconStates.update {
                    it + (
                        stateKey to AppIconUiState.Success(
                            bitmap = bitmap,
                            styleUsed = style,
                            engineUsed = engineLabel,
                            promptUsed = prompt,
                            drawableResources = installedPaths,
                            backgroundHex = backgroundHex,
                            drawableDirPath = drawableDirPath
                        )
                    )
                }
            } catch (e: TimeoutCancellationException) {
                _appIconStates.update {
                    it + (stateKey to AppIconUiState.Error("Icon generation timed out after 120 seconds."))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: MissingApiKeyException) {
                _appIconStates.update {
                    it + (stateKey to AppIconUiState.Error("API Key missing. Configure GEMINI_API_KEY in AI Studio Secrets."))
                }
            } catch (e: Exception) {
                _appIconStates.update {
                    it + (stateKey to AppIconUiState.Error(e.message ?: "Failed to generate AI app icon."))
                }
            }
        }
    }

    fun analyzeAiFeaturesForProject(project: AppProject) {
        val contextText = "${project.name}: ${project.description}. ${project.prompt}".trim()
        if (CloudAiConfig.isOfflineMode.value) {
            _error.value = "Offline Mode is enabled. Disable Offline Mode to request cloud AI feature suggestions."
            return
        }
        if (geminiService.isApiKeyMissing()) {
            _error.value = missingKeyErrorMessage()
            return
        }

        viewModelScope.launch {
            _isAnalyzingFeatures.value = true
            _error.value = null
            try {
                val ideas = geminiService.generateAiFeatureIdeas(contextText)
                _aiFeatureSuggestions.update { it + (project.id to ideas) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: MissingApiKeyException) {
                _error.value = "API Key missing"
            } catch (e: MissingOpenRouterKeyException) {
                _error.value = missingKeyErrorMessage()
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to analyze AI feature ideas"
            } finally {
                _isAnalyzingFeatures.value = false
            }
        }
    }

    fun analyzeGeneratedCode(project: AppProject) {
        viewModelScope.launch {
            _codeAnalysisStates.update { it + (project.id to CodeAnalysisUiState.Loading) }
            val generatedCode = CodeExporter.buildProjectCode(project)
            val staticIssues = GeneratedCodeAnalyzer.analyze(generatedCode)
            val providerName = CloudAiConfig.currentProvider.value.displayName
            val strategy = CloudAiConfig.buildModeStrategy.value

            if (strategy == BuildModeStrategy.OFFLINE || CloudAiConfig.isOfflineMode.value) {
                val summary = if (staticIssues.isEmpty()) {
                    "Offline static check passed with no syntax or import errors."
                } else {
                    "Offline static check detected ${staticIssues.size} issue(s) with suggested fixes."
                }
                _codeAnalysisStates.update {
                    it + (
                        project.id to CodeAnalysisUiState.Success(
                            CodeAnalysisResult(
                                issues = staticIssues,
                                summary = summary,
                                usedGeminiAi = false,
                                aiNotice = "Offline Mode enabled — local static analysis completed without cloud requests."
                            )
                        )
                    )
                }
                return@launch
            }

            if (geminiService.isApiKeyMissing()) {
                val summary = if (staticIssues.isEmpty()) {
                    "Static check passed with no syntax or import errors."
                } else {
                    "Static check detected ${staticIssues.size} issue(s) with suggested fixes."
                }
                _codeAnalysisStates.update {
                    it + (
                        project.id to CodeAnalysisUiState.Success(
                            CodeAnalysisResult(
                                issues = staticIssues,
                                summary = summary,
                                usedGeminiAi = false,
                                aiNotice = "$providerName deep review skipped (API key missing). Showing local static analysis."
                            )
                        )
                    )
                }
                return@launch
            }

            try {
                val aiResult = geminiService.analyzeGeneratedCode(
                    code = generatedCode,
                    staticIssues = staticIssues,
                    projectContext = "${project.name}: ${project.description}"
                )
                _codeAnalysisStates.update {
                    it + (project.id to CodeAnalysisUiState.Success(aiResult))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val summary = if (staticIssues.isEmpty()) {
                    "Static check passed with no syntax or import errors."
                } else {
                    "Static check detected ${staticIssues.size} issue(s) with suggested fixes."
                }
                _codeAnalysisStates.update {
                    it + (
                        project.id to CodeAnalysisUiState.Success(
                            CodeAnalysisResult(
                                issues = staticIssues,
                                summary = summary,
                                usedGeminiAi = false,
                                aiNotice = "$providerName deep review unavailable (${e.message ?: "network error"}). Showing local static analysis."
                            )
                        )
                    )
                }
            }
        }
    }

    fun addSuggestedFeatureToProject(projectId: String, idea: AiFeatureIdea) {
        val current = getProjectById(projectId) ?: return
        val formattedFeature = "${idea.title}: ${idea.description}"
        if (current.features.any { it.startsWith(idea.title, ignoreCase = true) }) return
        val updated = current.copy(features = current.features + formattedFeature)
        updateSavedProjectInList(projectId, updated)
    }

    fun updateProject(updatedProject: AppProject) {
        updateSavedProjectInList(updatedProject.id, updatedProject)
    }

    private fun updateSavedProjectInList(projectId: String, updatedProject: AppProject) {
        _savedProjects.update { list ->
            if (list.any { it.id == projectId }) {
                list.map { if (it.id == projectId) updatedProject else it }
            } else {
                listOf(updatedProject) + list
            }
        }
        if (_generatedProject.value?.id == projectId) {
            _generatedProject.value = updatedProject
        }
        viewModelScope.launch {
            projectRepository?.saveProject(updatedProject)
        }
    }

    fun saveCurrentProject(): Boolean {
        val current = _generatedProject.value ?: return false
        _savedProjects.update { existing ->
            if (existing.any { it.id == current.id }) {
                existing
            } else {
                listOf(current) + existing
            }
        }
        viewModelScope.launch {
            projectRepository?.saveProject(current)
        }
        return true
    }

    fun deleteProject(projectId: String) {
        _savedProjects.update { list -> list.filterNot { it.id == projectId } }
        viewModelScope.launch {
            projectRepository?.deleteProject(projectId)
        }
    }

    fun clearAllProjects() {
        _savedProjects.value = emptyList()
        _generatedProject.value = null
        viewModelScope.launch {
            projectRepository?.clearAllProjects()
        }
    }

    fun getProjectById(projectId: String): AppProject? {
        return _savedProjects.value.find { it.id == projectId }
            ?: _generatedProject.value?.takeIf { it.id == projectId }
    }
}

class GenerateViewModelFactory(
    private val projectRepository: ProjectRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GenerateViewModel::class.java)) {
            return GenerateViewModel(projectRepository = projectRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
