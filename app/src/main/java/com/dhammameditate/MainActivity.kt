package com.dhammameditate

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dhammameditate.ui.library.LibraryScreen
import com.dhammameditate.ui.library.LibraryViewModel
import com.dhammameditate.ui.player.PlayerScreen
import com.dhammameditate.ui.player.PlayerViewModel
import com.dhammameditate.ui.theme.DhammaMeditateTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private val openActiveSessionTrigger = MutableStateFlow(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )

        val container = (application as DhammaMeditateApp).container

        setContent {
            DhammaMeditateTheme {
                var selectedTrackId by rememberSaveable {
                    mutableStateOf(
                        container.playbackClient.state.value
                            .takeIf { it.isActive }
                            ?.activeTrackId
                    )
                }

                LaunchedEffect(Unit) {
                    openActiveSessionTrigger.collect { trigger ->
                        if (trigger > 0L) {
                            val activeId = container.playbackClient.state.value
                                .takeIf { it.isActive }
                                ?.activeTrackId
                            if (activeId != null) {
                                selectedTrackId = activeId
                            }
                        }
                    }
                }

                val currentTrackId = selectedTrackId
                if (currentTrackId == null) {
                    val libraryViewModel: LibraryViewModel = viewModel(
                        factory = LibraryViewModel.factory(
                            trackRepository = container.trackRepository,
                            audioFileImporter = container.audioFileImporter,
                            playbackClient = container.playbackClient
                        )
                    )
                    LibraryScreen(
                        viewModel = libraryViewModel,
                        onOpenTrack = { trackId -> selectedTrackId = trackId }
                    )
                } else {
                    val playerViewModel: PlayerViewModel = viewModel(
                        key = "player_$currentTrackId",
                        factory = PlayerViewModel.factory(
                            trackId = currentTrackId,
                            trackRepository = container.trackRepository,
                            playbackClient = container.playbackClient
                        )
                    )
                    PlayerScreen(
                        viewModel = playerViewModel,
                        onBack = { selectedTrackId = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_ACTIVE_SESSION, false)) {
            openActiveSessionTrigger.value = System.nanoTime()
        }
    }

    companion object {
        const val EXTRA_OPEN_ACTIVE_SESSION = "extra_open_active_session"
    }
}
