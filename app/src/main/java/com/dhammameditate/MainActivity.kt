package com.dhammameditate

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dhammameditate.data.preferences.withAppLanguage
import com.dhammameditate.playback.PlaybackSnapshot
import com.dhammameditate.ui.common.LocalAppLocalizedContext
import com.dhammameditate.ui.editor.SessionEditorScreen
import com.dhammameditate.ui.editor.SessionEditorViewModel
import com.dhammameditate.ui.library.LibraryScreen
import com.dhammameditate.ui.library.LibraryViewModel
import com.dhammameditate.ui.player.PlayerScreen
import com.dhammameditate.ui.player.PlayerViewModel
import com.dhammameditate.ui.sessionplayer.SessionPlayerScreen
import com.dhammameditate.ui.sessionplayer.SessionPlayerViewModel
import com.dhammameditate.ui.sessions.SessionsScreen
import com.dhammameditate.ui.sessions.SessionsViewModel
import com.dhammameditate.ui.theme.CardSurface
import com.dhammameditate.ui.theme.DhammaMeditateTheme
import com.dhammameditate.ui.theme.InkSecondary
import com.dhammameditate.ui.theme.LightNeutral
import com.dhammameditate.ui.theme.MonasticMaroon
import com.dhammameditate.ui.theme.NavActivePill
import com.dhammameditate.ui.theme.OutlineNeutral
import com.dhammameditate.ui.tutorial.LocalTutorialAnchorRegistry
import com.dhammameditate.ui.tutorial.TutorialAnchorRegistry
import com.dhammameditate.ui.tutorial.TutorialDefinition
import com.dhammameditate.ui.tutorial.TutorialEngine
import com.dhammameditate.ui.tutorial.TutorialId
import com.dhammameditate.ui.tutorial.TutorialRegistry
import com.dhammameditate.ui.tutorial.TutorialSessionState
import com.dhammameditate.ui.tutorial.TutorialSpotlightOverlay
import com.dhammameditate.ui.tutorial.TutorialStartRoute
import com.dhammameditate.ui.tutorial.TutorialTargetId
import com.dhammameditate.ui.tutorial.TutorialTopicListDialog
import com.dhammameditate.ui.tutorial.TutorialWelcomeDialog
import com.dhammameditate.ui.tutorial.tutorialTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

private enum class HomeTab {
    SESSIONS,
    LIBRARY
}

private sealed interface AppDestination {
    data class Home(val tab: HomeTab = HomeTab.SESSIONS) : AppDestination
    data class SessionEditor(
        val sessionId: String? = null,
        val returnToPlayerSessionId: String? = null,
        val instanceId: Long = 0L
    ) : AppDestination

    data class SessionPlayer(val sessionId: String) : AppDestination
    data class QuickTrackPlayer(val trackId: String) : AppDestination
}

private val AppDestinationSaver = listSaver<AppDestination, String>(
    save = { destination ->
        when (destination) {
            is AppDestination.Home -> listOf("HOME", destination.tab.name)
            is AppDestination.SessionEditor -> listOf(
                "EDITOR",
                destination.sessionId.orEmpty(),
                destination.returnToPlayerSessionId.orEmpty(),
                destination.instanceId.toString()
            )

            is AppDestination.SessionPlayer -> listOf("SESSION_PLAYER", destination.sessionId)
            is AppDestination.QuickTrackPlayer -> listOf("QUICK_PLAYER", destination.trackId)
        }
    },
    restore = { list ->
        when (list.firstOrNull()) {
            "HOME" -> {
                val tab = list.getOrNull(1)?.let { runCatching { HomeTab.valueOf(it) }.getOrNull() }
                    ?: HomeTab.SESSIONS
                AppDestination.Home(tab)
            }

            "EDITOR" -> AppDestination.SessionEditor(
                sessionId = list.getOrNull(1)?.takeIf { it.isNotEmpty() },
                returnToPlayerSessionId = list.getOrNull(2)?.takeIf { it.isNotEmpty() },
                instanceId = list.getOrNull(3)?.toLongOrNull() ?: 0L
            )

            "SESSION_PLAYER" -> list.getOrNull(1)?.takeIf { it.isNotEmpty() }
                ?.let { AppDestination.SessionPlayer(it) }
                ?: AppDestination.Home(HomeTab.SESSIONS)

            "QUICK_PLAYER" -> list.getOrNull(1)?.takeIf { it.isNotEmpty() }
                ?.let { AppDestination.QuickTrackPlayer(it) }
                ?: AppDestination.Home(HomeTab.LIBRARY)

            else -> AppDestination.Home(HomeTab.SESSIONS)
        }
    }
)

private val TutorialSessionStateSaver = listSaver<TutorialSessionState?, String>(
    save = { state ->
        if (state == null) {
            emptyList()
        } else {
            listOf(state.definition.id.name, state.currentStepIndex.toString())
        }
    },
    restore = { list ->
        val idName = list.getOrNull(0) ?: return@listSaver null
        val stepIndex = list.getOrNull(1)?.toIntOrNull() ?: return@listSaver null
        val tutorialId = runCatching { TutorialId.valueOf(idName) }.getOrNull() ?: return@listSaver null
        val definition = TutorialRegistry.findById(tutorialId) ?: return@listSaver null
        if (stepIndex in definition.steps.indices) {
            TutorialSessionState(definition = definition, currentStepIndex = stepIndex)
        } else {
            null
        }
    }
)

private fun activePlaybackDestination(playback: PlaybackSnapshot): AppDestination? {
    if (!playback.isActive) return null
    val sessionId = playback.activeSessionId
    if (sessionId != null) {
        return AppDestination.SessionPlayer(sessionId)
    }
    val trackId = playback.activeTrackId
    if (trackId != null) {
        return AppDestination.QuickTrackPlayer(trackId)
    }
    return null
}

class MainActivity : ComponentActivity() {

    private val openActiveSessionTrigger = MutableStateFlow(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )

        if (intent?.getBooleanExtra(EXTRA_OPEN_ACTIVE_SESSION, false) == true) {
            intent.removeExtra(EXTRA_OPEN_ACTIVE_SESSION)
            openActiveSessionTrigger.value = System.nanoTime()
        }

        val container = (application as DhammaMeditateApp).container

        setContent {
            val currentLanguage by container.appLanguageStore.language.collectAsStateWithLifecycle()
            val hasSeenTutorialOffer by container.tutorialPreferencesStore.hasSeenTutorialOffer.collectAsStateWithLifecycle()
            val hasLibraryTracks by remember(container.trackRepository) {
                container.trackRepository.observeTracks().map { it.isNotEmpty() }
            }.collectAsStateWithLifecycle(initialValue = false)
            val tutorialAnchorRegistry = remember { TutorialAnchorRegistry() }
            val localizedContext = remember(this@MainActivity, currentLanguage) {
                this@MainActivity.withAppLanguage(currentLanguage)
            }

            CompositionLocalProvider(
                LocalActivityResultRegistryOwner provides this@MainActivity,
                LocalAppLocalizedContext provides localizedContext,
                LocalContext provides localizedContext,
                LocalConfiguration provides localizedContext.resources.configuration,
                LocalTutorialAnchorRegistry provides tutorialAnchorRegistry
            ) {
            DhammaMeditateTheme {
                var destination by rememberSaveable(stateSaver = AppDestinationSaver) {
                    mutableStateOf(
                        activePlaybackDestination(container.playbackClient.state.value)
                            ?: AppDestination.Home(HomeTab.SESSIONS)
                    )
                }
                var editorCounter by rememberSaveable { mutableLongStateOf(1L) }
                var askedNotificationPermission by rememberSaveable { mutableStateOf(false) }
                var showTutorialWelcomeDialog by remember { mutableStateOf(false) }
                var showTutorialTopicList by rememberSaveable { mutableStateOf(false) }
                var activeTutorial by rememberSaveable(stateSaver = TutorialSessionStateSaver) {
                    mutableStateOf<TutorialSessionState?>(null)
                }

                val isActivePlaybackDestination = destination is AppDestination.SessionPlayer ||
                    destination is AppDestination.QuickTrackPlayer

                LaunchedEffect(hasSeenTutorialOffer, isActivePlaybackDestination) {
                    if (
                        TutorialEngine.shouldShowFirstTimeOffer(
                            hasSeenOffer = hasSeenTutorialOffer,
                            hasActivePlayback = isActivePlaybackDestination,
                            hasActiveTutorial = activeTutorial != null
                        ) && !showTutorialTopicList
                    ) {
                        showTutorialWelcomeDialog = true
                    }
                }

                val openActivePlayback: () -> Unit = {
                    activePlaybackDestination(container.playbackClient.state.value)?.let { target ->
                        activeTutorial = null
                        showTutorialWelcomeDialog = false
                        showTutorialTopicList = false
                        destination = target
                    }
                }

                val startTutorial: (TutorialDefinition) -> Unit = { definition ->
                    showTutorialWelcomeDialog = false
                    showTutorialTopicList = false
                    destination = when (definition.startRoute) {
                        TutorialStartRoute.HOME_SESSIONS -> AppDestination.Home(HomeTab.SESSIONS)
                        TutorialStartRoute.HOME_LIBRARY -> AppDestination.Home(HomeTab.LIBRARY)
                    }
                    activeTutorial = TutorialEngine.start(definition)
                }

                LaunchedEffect(Unit) {
                    openActiveSessionTrigger.collect { trigger ->
                        if (trigger > 0L) {
                            openActivePlayback()
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                when (val current = destination) {
                    is AppDestination.Home -> {
                        val sessionsViewModel: SessionsViewModel = viewModel(
                            factory = SessionsViewModel.factory(
                                sessionRepository = container.sessionRepository,
                                trackRepository = container.trackRepository,
                                playbackClient = container.playbackClient
                            )
                        )
                        val libraryViewModel: LibraryViewModel = viewModel(
                            factory = LibraryViewModel.factory(
                                trackRepository = container.trackRepository,
                                sessionRepository = container.sessionRepository,
                                audioFileImporter = container.audioFileImporter,
                                playbackClient = container.playbackClient
                            )
                        )

                        Scaffold(
                            containerColor = LightNeutral,
                            bottomBar = {
                                HomeBottomNavigationBar(
                                    selectedTab = current.tab,
                                    onSelectTab = { tab ->
                                        destination = AppDestination.Home(tab)
                                        val tutorial = activeTutorial
                                        if (
                                            tab == HomeTab.LIBRARY &&
                                            tutorial?.currentStep?.targetId == TutorialTargetId.NAV_LIBRARY_TAB
                                        ) {
                                            activeTutorial = TutorialEngine.onTargetTapped(
                                                tutorial,
                                                TutorialTargetId.NAV_LIBRARY_TAB
                                            )
                                        }
                                    }
                                )
                            }
                        ) { innerPadding ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(bottom = innerPadding.calculateBottomPadding())
                            ) {
                                when (current.tab) {
                                    HomeTab.SESSIONS -> {
                                        SessionsScreen(
                                            viewModel = sessionsViewModel,
                                            currentLanguage = currentLanguage,
                                            onSelectLanguage = container.appLanguageStore::setLanguage,
                                            onNewSession = {
                                                val instanceId = editorCounter++
                                                destination = AppDestination.SessionEditor(
                                                    sessionId = null,
                                                    returnToPlayerSessionId = null,
                                                    instanceId = instanceId
                                                )
                                                val tutorial = activeTutorial
                                                if (tutorial?.currentStep?.targetId == TutorialTargetId.SESSIONS_NEW_BUTTON) {
                                                    activeTutorial = TutorialEngine.onTargetTapped(
                                                        tutorial,
                                                        TutorialTargetId.SESSIONS_NEW_BUTTON
                                                    )
                                                }
                                            },
                                            onOpenSession = { sessionId ->
                                                destination = AppDestination.SessionPlayer(sessionId)
                                            },
                                            onEditSession = { sessionId ->
                                                val instanceId = editorCounter++
                                                destination = AppDestination.SessionEditor(
                                                    sessionId = sessionId,
                                                    returnToPlayerSessionId = null,
                                                    instanceId = instanceId
                                                )
                                            },
                                            onOpenActivePlayback = openActivePlayback,
                                            onOpenTutorials = { showTutorialTopicList = true }
                                        )
                                    }

                                    HomeTab.LIBRARY -> {
                                        LibraryScreen(
                                            viewModel = libraryViewModel,
                                            currentLanguage = currentLanguage,
                                            onSelectLanguage = container.appLanguageStore::setLanguage,
                                            onOpenTrack = { trackId ->
                                                destination = AppDestination.QuickTrackPlayer(trackId)
                                            },
                                            onOpenActivePlayback = openActivePlayback,
                                            onOpenTutorials = { showTutorialTopicList = true }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    is AppDestination.SessionEditor -> {
                        val editorViewModel: SessionEditorViewModel = viewModel(
                            key = "editor_${current.sessionId ?: "new"}_${current.instanceId}",
                            factory = SessionEditorViewModel.factory(
                                sessionId = current.sessionId,
                                sessionRepository = container.sessionRepository,
                                trackRepository = container.trackRepository,
                                playbackClient = container.playbackClient
                            )
                        )
                        SessionEditorScreen(
                            viewModel = editorViewModel,
                            activeTutorialTargetId = activeTutorial?.currentStep?.targetId,
                            onBack = {
                                destination = current.returnToPlayerSessionId?.let {
                                    AppDestination.SessionPlayer(it)
                                } ?: AppDestination.Home(HomeTab.SESSIONS)
                            },
                            onSaved = { savedId ->
                                destination = if (current.returnToPlayerSessionId != null) {
                                    AppDestination.SessionPlayer(savedId)
                                } else {
                                    AppDestination.Home(HomeTab.SESSIONS)
                                }
                            }
                        )
                    }

                    is AppDestination.SessionPlayer -> {
                        val sessionPlayerViewModel: SessionPlayerViewModel = viewModel(
                            key = "session_player_${current.sessionId}",
                            factory = SessionPlayerViewModel.factory(
                                sessionId = current.sessionId,
                                sessionRepository = container.sessionRepository,
                                playbackClient = container.playbackClient
                            )
                        )
                        SessionPlayerScreen(
                            viewModel = sessionPlayerViewModel,
                            distractionChecker = container.distractionChecker,
                            askedNotificationPermission = askedNotificationPermission,
                            onNotificationPermissionAsked = { askedNotificationPermission = true },
                            onEditSession = { sessionId ->
                                val instanceId = editorCounter++
                                destination = AppDestination.SessionEditor(
                                    sessionId = sessionId,
                                    returnToPlayerSessionId = sessionId,
                                    instanceId = instanceId
                                )
                            },
                            onBack = {
                                destination = AppDestination.Home(HomeTab.SESSIONS)
                            }
                        )
                    }

                    is AppDestination.QuickTrackPlayer -> {
                        val playerViewModel: PlayerViewModel = viewModel(
                            key = "player_${current.trackId}",
                            factory = PlayerViewModel.factory(
                                trackId = current.trackId,
                                trackRepository = container.trackRepository,
                                playbackClient = container.playbackClient
                            )
                        )
                        PlayerScreen(
                            viewModel = playerViewModel,
                            distractionChecker = container.distractionChecker,
                            askedNotificationPermission = askedNotificationPermission,
                            onNotificationPermissionAsked = { askedNotificationPermission = true },
                            onBack = {
                                destination = AppDestination.Home(HomeTab.LIBRARY)
                            }
                        )
                    }
                }

                activeTutorial?.let { tutorialState ->
                    TutorialSpotlightOverlay(
                        state = tutorialState,
                        anchorRegistry = tutorialAnchorRegistry,
                        hasLibraryTracks = hasLibraryTracks,
                        onTargetTapped = { targetId ->
                            val currentState = activeTutorial ?: return@TutorialSpotlightOverlay
                            if (currentState.currentStep.targetId != targetId) {
                                return@TutorialSpotlightOverlay
                            }
                            when (targetId) {
                                TutorialTargetId.NAV_LIBRARY_TAB -> {
                                    destination = AppDestination.Home(HomeTab.LIBRARY)
                                }

                                TutorialTargetId.SESSIONS_NEW_BUTTON -> {
                                    val instanceId = editorCounter++
                                    destination = AppDestination.SessionEditor(
                                        sessionId = null,
                                        returnToPlayerSessionId = null,
                                        instanceId = instanceId
                                    )
                                }

                                TutorialTargetId.LIBRARY_IMPORT_BUTTON,
                                TutorialTargetId.EDITOR_INTRO_CARD,
                                TutorialTargetId.EDITOR_MAIN_CARD,
                                TutorialTargetId.EDITOR_TIMER_CARD,
                                TutorialTargetId.EDITOR_SAVE_BUTTON -> Unit
                            }
                            activeTutorial = TutorialEngine.onTargetTapped(currentState, targetId)
                        },
                        onConfirmStep = {
                            activeTutorial = activeTutorial?.let(TutorialEngine::confirmCurrentStep)
                        },
                        onSkip = {
                            activeTutorial = TutorialEngine.skip()
                        }
                    )
                }

                if (showTutorialWelcomeDialog && !hasSeenTutorialOffer && !isActivePlaybackDestination) {
                    TutorialWelcomeDialog(
                        onViewTutorials = {
                            container.tutorialPreferencesStore.markOfferSeen()
                            showTutorialWelcomeDialog = false
                            showTutorialTopicList = true
                        },
                        onSkip = {
                            container.tutorialPreferencesStore.markOfferSeen()
                            showTutorialWelcomeDialog = false
                        }
                    )
                }

                if (showTutorialTopicList) {
                    TutorialTopicListDialog(
                        tutorials = TutorialRegistry.all,
                        onSelectTutorial = startTutorial,
                        onDismiss = { showTutorialTopicList = false }
                    )
                }
                }
            }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_ACTIVE_SESSION, false)) {
            intent.removeExtra(EXTRA_OPEN_ACTIVE_SESSION)
            openActiveSessionTrigger.value = System.nanoTime()
        }
    }

    companion object {
        const val EXTRA_OPEN_ACTIVE_SESSION = "extra_open_active_session"
    }
}

@Composable
private fun HomeBottomNavigationBar(
    selectedTab: HomeTab,
    onSelectTab: (HomeTab) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(thickness = 1.dp, color = OutlineNeutral)
        NavigationBar(
            containerColor = CardSurface,
            tonalElevation = 0.dp
        ) {
            val sessionsLabel = stringResource(R.string.nav_sessions)
            val libraryLabel = stringResource(R.string.nav_library)
            NavigationBarItem(
                selected = selectedTab == HomeTab.SESSIONS,
                onClick = { onSelectTab(HomeTab.SESSIONS) },
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.SelfImprovement,
                        contentDescription = sessionsLabel
                    )
                },
                label = {
                    Text(
                        text = sessionsLabel,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MonasticMaroon,
                    selectedTextColor = MonasticMaroon,
                    indicatorColor = NavActivePill,
                    unselectedIconColor = InkSecondary,
                    unselectedTextColor = InkSecondary
                )
            )
            NavigationBarItem(
                selected = selectedTab == HomeTab.LIBRARY,
                onClick = { onSelectTab(HomeTab.LIBRARY) },
                modifier = Modifier.tutorialTarget(TutorialTargetId.NAV_LIBRARY_TAB),
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.LibraryMusic,
                        contentDescription = libraryLabel
                    )
                },
                label = {
                    Text(
                        text = libraryLabel,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MonasticMaroon,
                    selectedTextColor = MonasticMaroon,
                    indicatorColor = NavActivePill,
                    unselectedIconColor = InkSecondary,
                    unselectedTextColor = InkSecondary
                )
            )
        }
    }
}
