package com.example

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.core.AppContainer
import com.example.core.configureAppCheck
import com.example.data.firebase.AuthManager
import com.example.data.local.CreationEntity
import com.example.data.local.NoteEntity
import com.example.ui.navigation.Routes
import com.example.ui.navigation.mainTopTabs
import com.example.ui.screens.AnimatedSplashScreen
import com.example.ui.screens.ChatStudioScreen
import com.example.ui.screens.HomeDashboardScreen
import com.example.ui.screens.ImageStudioScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.PhotoUnderstandingScreen
import com.example.ui.screens.ProfileSettingsScreen
import com.example.ui.screens.StudiosHubScreen
import com.example.ui.screens.ToolboxScreen
import com.example.ui.screens.ToolboxToolHostScreen
import com.example.ui.screens.VaultScreen
import com.example.ui.screens.VideoStudioScreen
import com.example.ui.screens.VoiceStudioScreen
import com.example.ui.screens.WritingStudioScreen
import com.example.ui.theme.ProjectXTheme
import com.example.ui.theme.ThemeMode
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Mandatory App Check setup with launch intent before any UI or ViewModels
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        val container = AppContainer(applicationContext)

        setContent {
            val themeMode by container.preferencesManager.themeModeFlow
                .collectAsStateWithLifecycle(initialValue = ThemeMode.DARK)

            ProjectXTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ProjectXRoot(
                        container = container,
                        themeMode = themeMode
                    )
                }
            }
        }
    }
}

@Composable
private fun ProjectXRoot(
    container: AppContainer,
    themeMode: ThemeMode
) {
    val onboardingDone by container.preferencesManager.onboardingDoneFlow
        .collectAsStateWithLifecycle(initialValue = true)
    val languageCode by container.preferencesManager.languageCodeFlow
        .collectAsStateWithLifecycle(initialValue = "en")
    val guestName by container.preferencesManager.guestNameFlow
        .collectAsStateWithLifecycle(initialValue = "Guest Creator")
    val recentTools by container.preferencesManager.recentToolsFlow
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val isOnline by container.networkMonitor.isOnlineFlow
        .collectAsStateWithLifecycle(initialValue = container.networkMonitor.isCurrentlyOnline())
    val savedCreations by container.localRepository.observeCreations()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val currentUser by AuthManager.authStateFlow(Firebase.auth)
        .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var splashFinished by remember { mutableStateOf(false) }

    // Silent auto-sign-in attempt on startup if user previously authorized Google Sign-In
    LaunchedEffect(Unit) {
        AuthManager.attemptAutoSignIn(
            context = context,
            credentialManager = CredentialManager.create(context),
            scope = scope
        )
    }

    // Active Firestore cloud sync jobs when signed in, cancelled cleanly before signOut
    var syncJobs by remember { mutableStateOf<List<Job>>(emptyList()) }
    val activeUid = currentUser?.uid
    LaunchedEffect(activeUid) {
        syncJobs.forEach { it.cancel() }
        if (!activeUid.isNullOrBlank()) {
            val j1 = launch {
                try {
                    container.firestoreSyncRepository.observeCloudCreations(activeUid).collect { docs ->
                        docs.forEach { doc ->
                            val id = doc.localId.ifBlank { doc.id }
                            if (id.isNotBlank()) {
                                container.localRepository.upsertCreation(
                                    CreationEntity(
                                        id = id,
                                        type = doc.type,
                                        title = doc.title,
                                        content = doc.content,
                                        prompt = doc.prompt.orEmpty(),
                                        language = doc.language ?: "en",
                                        isFavorite = doc.favorite,
                                        createdAtMillis = doc.createdAt?.toDate()?.time ?: System.currentTimeMillis(),
                                        updatedAtMillis = doc.updatedAt?.toDate()?.time ?: System.currentTimeMillis()
                                    )
                                )
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("CloudSync", "Creations sync error: ${e.message}")
                }
            }
            val j2 = launch {
                try {
                    container.firestoreSyncRepository.observeCloudNotes(activeUid).collect { docs ->
                        docs.forEach { doc ->
                            val id = doc.localId.ifBlank { doc.id }
                            if (id.isNotBlank()) {
                                container.localRepository.upsertNote(
                                    NoteEntity(
                                        id = id,
                                        title = doc.title,
                                        body = doc.body,
                                        category = doc.category,
                                        isPinned = doc.pinned,
                                        isFavorite = doc.favorite,
                                        createdAtMillis = doc.createdAt?.toDate()?.time ?: System.currentTimeMillis(),
                                        updatedAtMillis = doc.updatedAt?.toDate()?.time ?: System.currentTimeMillis()
                                    )
                                )
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("CloudSync", "Notes sync error: ${e.message}")
                }
            }
            syncJobs = listOf(j1, j2)
        } else {
            syncJobs = emptyList()
        }
    }

    when {
        !splashFinished -> {
            AnimatedSplashScreen(onFinished = { splashFinished = true })
        }
        !onboardingDone -> {
            OnboardingScreen(
                onComplete = {
                    scope.launch { container.preferencesManager.setOnboardingDone(true) }
                }
            )
        }
        else -> {
            key(activeUid ?: "guest") {
                MainWorkspaceScaffold(
                    container = container,
                    currentUser = currentUser,
                    displayName = currentUser?.displayName ?: guestName,
                    guestName = guestName,
                    themeMode = themeMode,
                    languageCode = languageCode,
                    isOnline = isOnline,
                    recentTools = recentTools,
                    savedCreations = savedCreations,
                    onBeforeSignOut = {
                        syncJobs.forEach { it.cancel() }
                        syncJobs = emptyList()
                    }
                )
            }
        }
    }
}

@Composable
private fun MainWorkspaceScaffold(
    container: AppContainer,
    currentUser: com.google.firebase.auth.FirebaseUser?,
    displayName: String,
    guestName: String,
    themeMode: ThemeMode,
    languageCode: String,
    isOnline: Boolean,
    recentTools: List<String>,
    savedCreations: List<CreationEntity>,
    onBeforeSignOut: () -> Unit
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    var pendingImagePrompt by remember { mutableStateOf("") }
    var pendingVoiceScript by remember { mutableStateOf("") }

    val topLevelRoutes = remember { mainTopTabs.map { it.route }.toSet() }
    val showTopLevelNav = currentRoute in topLevelRoutes

    fun navigateTo(route: String, toolId: String?) {
        if (!toolId.isNullOrBlank()) {
            scope.launch { container.preferencesManager.recordToolUsed(toolId) }
        }
        if (route in topLevelRoutes) {
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        } else {
            navController.navigate(route)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (showTopLevelNav && !useRail) {
                    NavigationBar {
                        mainTopTabs.forEach { tab ->
                            NavigationBarItem(
                                selected = currentRoute == tab.route,
                                onClick = { navigateTo(tab.route, null) },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("nav_tab_${tab.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (showTopLevelNav && useRail) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        mainTopTabs.forEach { tab ->
                            NavigationRailItem(
                                selected = currentRoute == tab.route,
                                onClick = { navigateTo(tab.route, null) },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("rail_tab_${tab.route}")
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = Routes.HOME,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(Routes.HOME) {
                        HomeDashboardScreen(
                            displayName = displayName,
                            isSignedIn = currentUser != null,
                            isOnline = isOnline,
                            recentToolIds = recentTools,
                            savedCreations = savedCreations,
                            onNavigate = { route, toolId -> navigateTo(route, toolId) },
                            onOpenProfileSettings = { navigateTo(Routes.PROFILE_SETTINGS, null) }
                        )
                    }

                    composable(Routes.CHAT) {
                        ChatStudioScreen(
                            container = container,
                            isOnline = isOnline,
                            languageCode = languageCode,
                            snackbarHostState = snackbarHostState
                        )
                    }

                    composable(Routes.STUDIOS) {
                        StudiosHubScreen(
                            isOnline = isOnline,
                            onOpenStudio = { route, id -> navigateTo(route, id) }
                        )
                    }

                    composable(Routes.TOOLBOX) {
                        ToolboxScreen(
                            onOpenTool = { shortId ->
                                navigateTo(Routes.toolboxTool(shortId), "tool_$shortId")
                            }
                        )
                    }

                    composable(Routes.VAULT) {
                        VaultScreen(
                            container = container,
                            snackbarHostState = snackbarHostState
                        )
                    }

                    composable(Routes.IMAGE_STUDIO) {
                        ImageStudioScreen(
                            container = container,
                            isOnline = isOnline,
                            initialPrompt = pendingImagePrompt,
                            snackbarHostState = snackbarHostState,
                            onBack = {
                                pendingImagePrompt = ""
                                navController.popBackStack()
                            }
                        )
                    }

                    composable(Routes.VIDEO_STUDIO) {
                        VideoStudioScreen(
                            container = container,
                            isOnline = isOnline,
                            snackbarHostState = snackbarHostState,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(Routes.VOICE_STUDIO) {
                        VoiceStudioScreen(
                            container = container,
                            isOnline = isOnline,
                            initialScript = pendingVoiceScript,
                            defaultLanguageCode = languageCode,
                            snackbarHostState = snackbarHostState,
                            onBack = {
                                pendingVoiceScript = ""
                                navController.popBackStack()
                            }
                        )
                    }

                    composable(Routes.WRITING_STUDIO) {
                        WritingStudioScreen(
                            container = container,
                            isOnline = isOnline,
                            defaultLanguageCode = languageCode,
                            snackbarHostState = snackbarHostState,
                            onBack = { navController.popBackStack() },
                            onSendToVoice = { script ->
                                pendingVoiceScript = script
                                navigateTo(Routes.VOICE_STUDIO, "ai_voice")
                            }
                        )
                    }

                    composable(Routes.PHOTO_STUDIO) {
                        PhotoUnderstandingScreen(
                            container = container,
                            isOnline = isOnline,
                            snackbarHostState = snackbarHostState,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(Routes.PROFILE_SETTINGS) {
                        ProfileSettingsScreen(
                            container = container,
                            currentUser = currentUser,
                            guestName = guestName,
                            themeMode = themeMode,
                            languageCode = languageCode,
                            snackbarHostState = snackbarHostState,
                            onBeforeSignOut = onBeforeSignOut,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(
                        route = Routes.TOOLBOX_TOOL,
                        arguments = listOf(navArgument("toolId") { type = NavType.StringType })
                    ) { entry ->
                        val toolId = entry.arguments?.getString("toolId") ?: "calculator"
                        ToolboxToolHostScreen(
                            toolId = toolId,
                            container = container,
                            snackbarHostState = snackbarHostState,
                            onSendPromptToImageStudio = { generatedPrompt ->
                                pendingImagePrompt = generatedPrompt
                                navigateTo(Routes.IMAGE_STUDIO, "ai_image")
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
