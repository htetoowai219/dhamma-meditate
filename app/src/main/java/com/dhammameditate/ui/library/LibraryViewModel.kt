package com.dhammameditate.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dhammameditate.data.importer.AudioFileImporter
import com.dhammameditate.data.importer.ImportResult
import com.dhammameditate.data.local.TrackEntity
import com.dhammameditate.data.repository.TrackRepository
import com.dhammameditate.playback.PlaybackClient
import com.dhammameditate.playback.PlaybackSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ImportFailureItem(
    val fileName: String,
    val reason: String
)

data class LibraryUiState(
    val tracks: List<TrackEntity> = emptyList(),
    val isImporting: Boolean = false,
    val importFailures: List<ImportFailureItem> = emptyList(),
    val playback: PlaybackSnapshot = PlaybackSnapshot()
)

class LibraryViewModel(
    private val trackRepository: TrackRepository,
    private val audioFileImporter: AudioFileImporter,
    private val playbackClient: PlaybackClient
) : ViewModel() {

    private val isImporting = MutableStateFlow(false)
    private val importFailures = MutableStateFlow<List<ImportFailureItem>>(emptyList())

    val uiState: StateFlow<LibraryUiState> = combine(
        trackRepository.observeTracks(),
        isImporting,
        importFailures,
        playbackClient.state
    ) { tracks, importing, failures, playback ->
        LibraryUiState(
            tracks = tracks,
            isImporting = importing,
            importFailures = failures,
            playback = playback
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = LibraryUiState()
    )

    fun importFiles(uris: List<Uri>) {
        if (uris.isEmpty() || isImporting.value) return
        viewModelScope.launch {
            isImporting.value = true
            importFailures.value = emptyList()
            try {
                val results = audioFileImporter.importUris(uris)
                val failures = results.mapNotNull { result ->
                    when (result) {
                        is ImportResult.Success -> null
                        is ImportResult.Failure -> ImportFailureItem(
                            fileName = result.displayName,
                            reason = result.reason
                        )
                    }
                }
                importFailures.value = failures
            } finally {
                isImporting.value = false
            }
        }
    }

    fun dismissImportErrors() {
        importFailures.value = emptyList()
    }

    fun deleteTrack(track: TrackEntity) {
        viewModelScope.launch {
            playbackClient.stopIfActiveTrackAndWait(track.id)
            trackRepository.deleteTrack(track)
        }
    }

    companion object {
        fun factory(
            trackRepository: TrackRepository,
            audioFileImporter: AudioFileImporter,
            playbackClient: PlaybackClient
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return LibraryViewModel(
                    trackRepository = trackRepository,
                    audioFileImporter = audioFileImporter,
                    playbackClient = playbackClient
                ) as T
            }
        }
    }
}
