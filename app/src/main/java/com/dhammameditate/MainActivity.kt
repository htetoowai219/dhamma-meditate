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
import kotlinx.coroutines.flow.MutableStateFlow

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
            val localizedContext = remember(this@MainActivity, currentLanguage) {
                this@MainActivity.withAppLanguage(currentLanguage)
            }

            CompositionLocalProvider(
                LocalActivityResultRegistryOwner provides this@MainActivity,
                LocalAppLocalizedContext provides localizedContext,
                LocalContext provides localizedContext,
                LocalConfiguration provides localizedContext.resources.configuration
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

                val openActivePlayback: () -> Unit = {
                    activePlaybackDestination(container.playbackClient.state.value)?.let { target ->
                        destination = target
                    }
                }

                LaunchedEffect(Unit) {
                    openActiveSessionTrigger.collect { trigger ->
                        if (trigger > 0L) {
                            openActivePlayback()
                        }
                    }
                }

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
                                            onOpenActivePlayback = openActivePlayback
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
                                            onOpenActivePlayback = openActivePlayback
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
