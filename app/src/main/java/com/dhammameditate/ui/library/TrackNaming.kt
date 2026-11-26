package com.dhammameditate.ui.library

import com.dhammameditate.data.local.TrackEntity

object TrackTitleValidation {
    const val BLANK_ERROR_MESSAGE = "Track name cannot be empty"

    fun normalize(raw: String): String? {
        val trimmed = raw.trim()
        return trimmed.ifEmpty { null }
    }

    fun isValid(raw: String): Boolean = normalize(raw) != null
}

data class PendingTrackRename(
    val track: TrackEntity,
    val draftTitle: String,
    val showBlankError: Boolean = false
) {
    val trimmedTitle: String?
        get() = TrackTitleValidation.normalize(draftTitle)

    val canSave: Boolean
        get() = trimmedTitle != null

    fun withDraftTitle(newTitle: String): PendingTrackRename {
        return copy(
            draftTitle = newTitle,
            showBlankError = TrackTitleValidation.normalize(newTitle) == null
        )
    }

    fun withValidationErrorIfBlank(): PendingTrackRename {
        return if (canSave) this else copy(showBlankError = true)
    }
}

sealed interface ImportRenameState {
    data object Hidden : ImportRenameState

    data class BatchPrompt(
        val tracks: List<TrackEntity>
    ) : ImportRenameState

    data class Renaming(
        val tracks: List<TrackEntity>,
        val currentIndex: Int,
        val draftTitle: String,
        val showBlankError: Boolean = false,
        val isBatch: Boolean
    ) : ImportRenameState {
        val currentTrack: TrackEntity
            get() = tracks[currentIndex]

        val totalCount: Int
            get() = tracks.size

        val stepNumber: Int
            get() = currentIndex + 1

        val isLastStep: Boolean
            get() = currentIndex == tracks.lastIndex

        val trimmedTitle: String?
            get() = TrackTitleValidation.normalize(draftTitle)

        val canSave: Boolean
            get() = trimmedTitle != null
    }
}

object ImportRenameFlow {
    fun fromImportedTracks(tracks: List<TrackEntity>): ImportRenameState {
        return when {
            tracks.isEmpty() -> ImportRenameState.Hidden
            tracks.size == 1 -> ImportRenameState.Renaming(
                tracks = tracks,
                currentIndex = 0,
                draftTitle = tracks[0].title,
                showBlankError = false,
                isBatch = false
            )
            else -> ImportRenameState.BatchPrompt(tracks = tracks)
        }
    }

    fun beginBatchRenaming(state: ImportRenameState): ImportRenameState {
        if (state !is ImportRenameState.BatchPrompt || state.tracks.isEmpty()) {
            return state
        }
        return ImportRenameState.Renaming(
            tracks = state.tracks,
            currentIndex = 0,
            draftTitle = state.tracks[0].title,
            showBlankError = false,
            isBatch = true
        )
    }

    fun updateDraftTitle(state: ImportRenameState, newTitle: String): ImportRenameState {
        if (state !is ImportRenameState.Renaming) return state
        return state.copy(
            draftTitle = newTitle,
            showBlankError = TrackTitleValidation.normalize(newTitle) == null
        )
    }

    fun revealBlankErrorIfInvalid(state: ImportRenameState): ImportRenameState {
        if (state !is ImportRenameState.Renaming) return state
        return if (state.canSave) state else state.copy(showBlankError = true)
    }

    fun advance(state: ImportRenameState): ImportRenameState {
        if (state !is ImportRenameState.Renaming) return ImportRenameState.Hidden
        val nextIndex = state.currentIndex + 1
        if (nextIndex >= state.tracks.size) {
            return ImportRenameState.Hidden
        }
        return state.copy(
            currentIndex = nextIndex,
            draftTitle = state.tracks[nextIndex].title,
            showBlankError = false
        )
    }

    fun skipAll(): ImportRenameState = ImportRenameState.Hidden
}
