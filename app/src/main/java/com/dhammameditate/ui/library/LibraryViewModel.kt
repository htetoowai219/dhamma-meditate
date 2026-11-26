package com.dhammameditate.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dhammameditate.R
import com.dhammameditate.data.importer.AudioFileImporter
import com.dhammameditate.data.importer.ImportResult
import com.dhammameditate.data.local.TrackEntity
import com.dhammameditate.data.repository.SessionRepository
import com.dhammameditate.data.repository.TrackReferenceSummary
import com.dhammameditate.data.repository.TrackRepository
import com.dhammameditate.playback.PlaybackClient
import com.dhammameditate.playback.PlaybackSnapshot
import com.dhammameditate.ui.common.UiText
import com.dhammameditate.ui.common.toUiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ImportFailureItem(
    val fileName: UiText,
    val reason: UiText
)

data class PendingTrackDeletion(
    val track: TrackEntity,
    val referenceSummary: TrackReferenceSummary
)

data class LibraryUiState(
    val tracks: List<TrackEntity> = emptyList(),
    val isImporting: Boolean = false,
    val importFailures: List<ImportFailureItem> = emptyList(),
    val pendingTrackDeletion: PendingTrackDeletion? = null,
    val pendingTrackRename: PendingTrackRename? = null,
    val importRenameState: ImportRenameState = ImportRenameState.Hidden,
    val playback: PlaybackSnapshot = PlaybackSnapshot()
)

private data class LibraryModalState(
    val pendingTrackDeletion: PendingTrackDeletion? = null,
    val pendingTrackRename: PendingTrackRename? = null,
    val importRenameState: ImportRenameState = ImportRenameState.Hidden
)

class LibraryViewModel(
    private val trackRepository: TrackRepository,
    private val sessionRepository: SessionRepository,
    private val audioFileImporter: AudioFileImporter,
    private val playbackClient: PlaybackClient
) : ViewModel() {

    private val isImporting = MutableStateFlow(false)
    private val importFailures = MutableStateFlow<List<ImportFailureItem>>(emptyList())
    private val pendingTrackDeletion = MutableStateFlow<PendingTrackDeletion?>(null)
    private val pendingTrackRename = MutableStateFlow<PendingTrackRename?>(null)
    private val importRenameState = MutableStateFlow<ImportRenameState>(ImportRenameState.Hidden)

    private val modalState = combine(
        pendingTrackDeletion,
        pendingTrackRename,
        importRenameState
    ) { deletion, rename, importRename ->
        LibraryModalState(
            pendingTrackDeletion = deletion,
            pendingTrackRename = rename,
            importRenameState = importRename
        )
    }

    val uiState: StateFlow<LibraryUiState> = combine(
        trackRepository.observeTracks(),
        isImporting,
        importFailures,
        modalState,
        playbackClient.state
    ) { tracks, importing, failures, modals, playback ->
        LibraryUiState(
            tracks = tracks,
            isImporting = importing,
            importFailures = failures,
            pendingTrackDeletion = modals.pendingTrackDeletion,
            pendingTrackRename = modals.pendingTrackRename,
            importRenameState = modals.importRenameState,
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
            pendingTrackDeletion.value = null
            pendingTrackRename.value = null
            importRenameState.value = ImportRenameState.Hidden
            try {
                val results = audioFileImporter.importUris(uris)
                val succeededTracks = mutableListOf<TrackEntity>()
                val failures = mutableListOf<ImportFailureItem>()
                results.forEach { result ->
                    when (result) {
                        is ImportResult.Success -> succeededTracks.add(result.track)
                        is ImportResult.Failure -> failures.add(
                            ImportFailureItem(
                                fileName = result.displayName?.let { UiText.Dynamic(it) }
                                    ?: UiText.Resource(R.string.import_fallback_file_name),
                                reason = result.reason.toUiText()
                            )
                        )
                    }
                }
                importFailures.value = failures
                importRenameState.value = ImportRenameFlow.fromImportedTracks(succeededTracks)
            } finally {
                isImporting.value = false
            }
        }
    }

    fun dismissImportErrors() {
        importFailures.value = emptyList()
    }

    fun beginBatchImportRename() {
        importRenameState.update { ImportRenameFlow.beginBatchRenaming(it) }
    }

    fun onImportRenameDraftChanged(newTitle: String) {
        importRenameState.update { ImportRenameFlow.updateDraftTitle(it, newTitle) }
    }

    fun confirmCurrentImportRename() {
        val current = importRenameState.value as? ImportRenameState.Renaming ?: return
        val trimmed = current.trimmedTitle
        if (trimmed == null) {
            importRenameState.value = ImportRenameFlow.revealBlankErrorIfInvalid(current)
            return
        }
        val track = current.currentTrack
        importRenameState.value = ImportRenameFlow.advance(current)
        if (trimmed != track.title) {
            viewModelScope.launch {
                val persisted = trackRepository.renameTrack(track.id, trimmed)
                if (persisted != null) {
                    playbackClient.notifyTrackRenamed(track.id, persisted)
                }
            }
        }
    }

    fun skipCurrentImportRename() {
        importRenameState.update { ImportRenameFlow.advance(it) }
    }

    fun skipAllImportRenames() {
        importRenameState.value = ImportRenameFlow.skipAll()
    }

    fun requestRenameTrack(track: TrackEntity) {
        pendingTrackDeletion.value = null
        importRenameState.value = ImportRenameState.Hidden
        pendingTrackRename.value = PendingTrackRename(
            track = track,
            draftTitle = track.title
        )
    }

    fun onExistingTrackRenameDraftChanged(newTitle: String) {
        pendingTrackRename.update { it?.withDraftTitle(newTitle) }
    }

    fun dismissRenameTrack() {
        pendingTrackRename.value = null
    }

    fun confirmRenameTrack() {
        val pending = pendingTrackRename.value ?: return
        val trimmed = pending.trimmedTitle
        if (trimmed == null) {
            pendingTrackRename.value = pending.withValidationErrorIfBlank()
            return
        }
        pendingTrackRename.value = null
        if (trimmed != pending.track.title) {
            viewModelScope.launch {
                val persisted = trackRepository.renameTrack(pending.track.id, trimmed)
                if (persisted != null) {
                    playbackClient.notifyTrackRenamed(pending.track.id, persisted)
                }
            }
        }
    }

    fun requestDeleteTrack(track: TrackEntity) {
        pendingTrackRename.value = null
        importRenameState.value = ImportRenameState.Hidden
        viewModelScope.launch {
            val summary = sessionRepository.getTrackReferenceSummary(track.id)
            pendingTrackDeletion.value = PendingTrackDeletion(
                track = track,
                referenceSummary = summary
            )
        }
    }

    fun dismissDeleteTrack() {
        pendingTrackDeletion.value = null
    }

    fun confirmDeleteTrack() {
        val pending = pendingTrackDeletion.value ?: return
        pendingTrackDeletion.value = null
        viewModelScope.launch {
            playbackClient.stopIfActiveTrackAndWait(pending.track.id)
            sessionRepository.deleteTrackSafely(pending.track)
        }
    }

    companion object {
        fun factory(
            trackRepository: TrackRepository,
            sessionRepository: SessionRepository,
            audioFileImporter: AudioFileImporter,
            playbackClient: PlaybackClient
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return LibraryViewModel(
                    trackRepository = trackRepository,
                    sessionRepository = sessionRepository,
                    audioFileImporter = audioFileImporter,
                    playbackClient = playbackClient
                ) as T
            }
        }
    }
}
