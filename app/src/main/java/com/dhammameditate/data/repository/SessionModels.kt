package com.dhammameditate.data.repository

import com.dhammameditate.data.local.QueueZone
import com.dhammameditate.data.local.SessionEntity
import com.dhammameditate.data.local.TrackEntity

data class QueueTrackItem(
    val entryId: String,
    val zone: QueueZone,
    val position: Int,
    val track: TrackEntity
)

data class SessionWithQueues(
    val session: SessionEntity,
    val introItems: List<QueueTrackItem>,
    val mainItems: List<QueueTrackItem>
) {
    val id: String get() = session.id
    val name: String get() = session.name
    val targetDurationMs: Long get() = session.targetDurationMs
    val introTotalDurationMs: Long get() = introItems.sumOf { it.track.durationMs }
    val mainTotalDurationMs: Long get() = mainItems.sumOf { it.track.durationMs }
    val allItems: List<QueueTrackItem> get() = introItems + mainItems
    val needsMainTrack: Boolean get() = mainItems.isEmpty()
    val isPlayable: Boolean
        get() = session.name.isNotBlank() &&
            mainItems.isNotEmpty() &&
            session.targetDurationMs in 1L..mainTotalDurationMs
}

data class AffectedSessionInfo(
    val sessionId: String,
    val sessionName: String,
    val willEmptyMainQueue: Boolean
)

data class TrackReferenceSummary(
    val trackId: String,
    val affectedSessions: List<AffectedSessionInfo>
) {
    val isReferenced: Boolean get() = affectedSessions.isNotEmpty()
}

data class SessionDraft(
    val sessionId: String? = null,
    val name: String,
    val introTrackIds: List<String>,
    val mainTrackIds: List<String>,
    val targetDurationMs: Long
)
