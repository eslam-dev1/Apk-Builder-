package com.example

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.ProjectRepository
import com.example.model.AppIconUiState
import com.example.screens.CreateScreen
import com.example.screens.HomeScreen
import com.example.screens.LivePreviewScreen
import com.example.screens.ModelStatusScreen
import com.example.screens.OnboardingScreen
import com.example.screens.ProjectDetailsScreen
import com.example.screens.ProjectsScreen
import com.example.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.GenerateViewModel
import com.example.viewmodel.GenerateViewModelFactory

const val ROUTE_ONBOARDING = "onboarding"
const val ROUTE_HOME = "home"
const val ROUTE_CREATE = "create"
const val ROUTE_MODEL_STATUS = "modelStatus"
const val ROUTE_PROJECTS = "projects"
const val ROUTE_DETAILS = "details/{projectId}"
const val ROUTE_LIVE_PREVIEW = "livePreview/{projectId}"
const val ROUTE_SETTINGS = "settings"

@Composable
fun AppNavGraph() {
    val context = LocalContext.current.applicationContext
    val projectRepository = remember { ProjectRepository(context) }
    val generateViewModel: GenerateViewModel = viewModel(
        factory = GenerateViewModelFactory(projectRepository)
    )

    MyApplicationTheme {
        val isFirstLaunch by generateViewModel.isFirstLaunch.collectAsStateWithLifecycle()

        // Wait briefly while DataStore reads firstLaunch flag
        if (isFirstLaunch == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@MyApplicationTheme
        }

        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route
            ?: if (isFirstLaunch == true) ROUTE_ONBOARDING else ROUTE_HOME

        val savedProjects by generateViewModel.savedProjects.collectAsStateWithLifecycle()
        val isLoading by generateViewModel.isLoading.collectAsStateWithLifecycle()
        val error by generateViewModel.error.collectAsStateWithLifecycle()
        val aiFeatureSuggestions by generateViewModel.aiFeatureSuggestions.collectAsStateWithLifecycle()
        val isAnalyzingFeatures by generateViewModel.isAnalyzingFeatures.collectAsStateWithLifecycle()
        val appIconStates by generateViewModel.appIconStates.collectAsStateWithLifecycle()
        val codeAnalysisStates by generateViewModel.codeAnalysisStates.collectAsStateWithLifecycle()

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (currentRoute != ROUTE_ONBOARDING && currentRoute != ROUTE_LIVE_PREVIEW) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        NavigationBarItem(
                            selected = currentRoute == ROUTE_HOME,
                            onClick = {
                                navController.navigate(ROUTE_HOME) {
                                    popUpTo(ROUTE_HOME) { inclusive = false }
                                    launchSingleTop = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (currentRoute == ROUTE_HOME) Icons.Filled.Home else Icons.Outlined.Home,
                                    contentDescription = "Home"
                                )
                            },
                            label = { Text("Home") },
                            modifier = Modifier.testTag("nav_home")
                        )

                        NavigationBarItem(
                            selected = currentRoute == ROUTE_CREATE,
                            onClick = {
                                navController.navigate(ROUTE_CREATE) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (currentRoute == ROUTE_CREATE) Icons.Filled.AddCircle else Icons.Outlined.AddCircleOutline,
                                    contentDescription = "Create"
                                )
                            },
                            label = { Text("Create") },
                            modifier = Modifier.testTag("nav_create")
                        )

                        NavigationBarItem(
                            selected = currentRoute == ROUTE_MODEL_STATUS,
                            onClick = {
                                navController.navigate(ROUTE_MODEL_STATUS) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (currentRoute == ROUTE_MODEL_STATUS) Icons.Filled.Memory else Icons.Outlined.Memory,
                                    contentDescription = "Models"
                                )
                            },
                            label = { Text("Models") },
                            modifier = Modifier.testTag("nav_models")
                        )

                        NavigationBarItem(
                            selected = currentRoute == ROUTE_PROJECTS || currentRoute.startsWith("details/"),
                            onClick = {
                                navController.navigate(ROUTE_PROJECTS) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                BadgedBox(
                                    badge = {
                                        if (savedProjects.isNotEmpty()) {
                                            Badge { Text(savedProjects.size.toString()) }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (currentRoute == ROUTE_PROJECTS || currentRoute.startsWith("details/")) {
                                            Icons.Filled.Bookmarks
                                        } else {
                                            Icons.Outlined.Bookmarks
                                        },
                                        contentDescription = "Projects"
                                    )
                                }
                            },
                            label = { Text("Projects") },
                            modifier = Modifier.testTag("nav_projects")
                        )
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = if (isFirstLaunch == true) ROUTE_ONBOARDING else ROUTE_HOME,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(ROUTE_ONBOARDING) {
                    OnboardingScreen(
                        onGetStarted = {
                            generateViewModel.completeOnboarding()
                            navController.navigate(ROUTE_HOME) {
                                popUpTo(ROUTE_ONBOARDING) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    )
                }

                composable(ROUTE_HOME) {
                    HomeScreen(
                        projects = savedProjects,
                        onCreateClick = { navController.navigate(ROUTE_CREATE) },
                        onProjectsClick = { navController.navigate(ROUTE_PROJECTS) },
                        onModelStatusClick = { navController.navigate(ROUTE_MODEL_STATUS) },
                        onOpenProject = { projectId ->
                            generateViewModel.clearError()
                            navController.navigate("details/$projectId")
                        },
                        onSettingsClick = { navController.navigate(ROUTE_SETTINGS) }
                    )
                }

                composable(ROUTE_CREATE) {
                    CreateScreen(
                        viewModel = generateViewModel,
                        onBack = { navController.popBackStack() },
                        onSaveToProjects = {
                            navController.navigate(ROUTE_PROJECTS) {
                                launchSingleTop = true
                            }
                        },
                        onPreviewNow = { projectId ->
                            navController.navigate("livePreview/$projectId")
                        },
                        onOpenSettings = {
                            navController.navigate(ROUTE_SETTINGS)
                        },
                        onOpenModels = {
                            navController.navigate(ROUTE_MODEL_STATUS)
                        }
                    )
                }

                composable(ROUTE_PROJECTS) {
                    ProjectsScreen(
                        viewModel = generateViewModel,
                        onProjectClick = { projectId ->
                            generateViewModel.clearError()
                            navController.navigate("details/$projectId")
                        },
                        onBack = {
                            navController.navigate(ROUTE_HOME) {
                                popUpTo(ROUTE_HOME) { inclusive = false }
                                launchSingleTop = true
                            }
                        }
                    )
                }

                composable(
                    route = ROUTE_DETAILS,
                    arguments = listOf(navArgument("projectId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val projectId = backStackEntry.arguments?.getString("projectId").orEmpty()
                    val project = savedProjects.find { it.id == projectId }
                        ?: generateViewModel.getProjectById(projectId)
                    ProjectDetailsScreen(
                        project = project,
                        isRegenerating = isLoading,
                        errorMessage = error,
                        appIconState = appIconStates[projectId] ?: AppIconUiState.Idle,
                        onGenerateAppIcon = { targetProject ->
                            generateViewModel.generateProjectIcon(targetProject)
                        },
                        aiFeatureIdeas = aiFeatureSuggestions[projectId].orEmpty(),
                        isAnalyzingFeatures = isAnalyzingFeatures,
                        onAnalyzeAiFeatures = { targetProject ->
                            generateViewModel.analyzeAiFeaturesForProject(targetProject)
                        },
                        onAddSuggestedFeature = { id, idea ->
                            generateViewModel.addSuggestedFeatureToProject(id, idea)
                        },
                        codeAnalysisState = codeAnalysisStates[projectId] ?: com.example.model.CodeAnalysisUiState.Idle,
                        onAnalyzeCode = { targetProject ->
                            generateViewModel.analyzeGeneratedCode(targetProject)
                        },
                        onRegenerateProject = { id, newPrompt ->
                            generateViewModel.regenerateProject(id, newPrompt)
                        },
                        onUpdateProject = { updatedProject ->
                            generateViewModel.updateProject(updatedProject)
                        },
                        onLivePreview = { id ->
                            navController.navigate("livePreview/$id")
                        },
                        onDeleteProject = { idToDelete ->
                            generateViewModel.deleteProject(idToDelete)
                            navController.navigate(ROUTE_PROJECTS) {
                                popUpTo(ROUTE_HOME) { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                        // Back from Details always goes to Projects
                        onBack = {
                            navController.navigate(ROUTE_PROJECTS) {
                                popUpTo(ROUTE_HOME) { inclusive = false }
                                launchSingleTop = true
                            }
                        }
                    )
                }

                composable(
                    route = ROUTE_LIVE_PREVIEW,
                    arguments = listOf(navArgument("projectId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val projectId = backStackEntry.arguments?.getString("projectId").orEmpty()
                    val project = savedProjects.find { it.id == projectId }
                        ?: generateViewModel.getProjectById(projectId)
                    LivePreviewScreen(
                        project = project,
                        onClose = { navController.popBackStack() }
                    )
                }

                composable(ROUTE_MODEL_STATUS) {
                    ModelStatusScreen(
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(ROUTE_SETTINGS) {
                    SettingsScreen(
                        isApiKeyConfigured = generateViewModel.isApiKeyConfigured(),
                        totalProjectsCount = savedProjects.size,
                        onClearAllProjects = { generateViewModel.clearAllProjects() },
                        onOpenModelStatus = { navController.navigate(ROUTE_MODEL_STATUS) },
                        // Back from Settings always goes to Home
                        onBack = {
                            navController.navigate(ROUTE_HOME) {
                                popUpTo(ROUTE_HOME) { inclusive = false }
                                launchSingleTop = true
                            }
                        }
                    )
                }
            }
        }
    }
}
