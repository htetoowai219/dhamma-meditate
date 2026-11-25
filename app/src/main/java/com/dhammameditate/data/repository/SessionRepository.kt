package com.dhammameditate.data.repository

import androidx.room.withTransaction
import com.dhammameditate.data.local.AppDatabase
import com.dhammameditate.data.local.QueueZone
import com.dhammameditate.data.local.SessionDao
import com.dhammameditate.data.local.SessionEntity
import com.dhammameditate.data.local.SessionTrackEntity
import com.dhammameditate.data.local.TrackDao
import com.dhammameditate.data.local.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.util.UUID

class SessionRepository(
    private val database: AppDatabase,
    private val sessionDao: SessionDao,
    private val trackDao: TrackDao,
    private val trackRepository: TrackRepository
) {
    fun observeSessions(): Flow<List<SessionWithQueues>> = combine(
        sessionDao.observeSessions(),
        sessionDao.observeAllSessionTracks(),
        trackDao.observeAll()
    ) { sessions, sessionTracks, tracks ->
        val tracksById = tracks.associateBy { it.id }
        val entriesBySession = sessionTracks.groupBy { it.sessionId }
        sessions.map { session ->
            assembleSession(
                session = session,
                entries = entriesBySession[session.id].orEmpty(),
                tracksById = tracksById
            )
        }
    }

    fun observeSession(sessionId: String): Flow<SessionWithQueues?> = combine(
        sessionDao.observeSessionById(sessionId),
        sessionDao.observeSessionTracks(sessionId),
        trackDao.observeAll()
    ) { session, entries, tracks ->
        session?.let {
            assembleSession(
                session = it,
                entries = entries,
                tracksById = tracks.associateBy { track -> track.id }
            )
        }
    }

    suspend fun getSession(sessionId: String): SessionWithQueues? {
        val session = sessionDao.getSessionById(sessionId) ?: return null
        val entries = sessionDao.getSessionTracks(sessionId)
        val trackIds = entries.map { it.trackId }.distinct()
        val tracksById = if (trackIds.isEmpty()) {
            emptyMap()
        } else {
            trackDao.getByIds(trackIds).associateBy { it.id }
        }
        return assembleSession(session, entries, tracksById)
    }

    suspend fun saveSession(draft: SessionDraft): String {
        val trimmedName = draft.name.trim()
        require(trimmedName.isNotBlank()) { "Session name must not be blank" }
        require(draft.mainTrackIds.isNotEmpty()) { "Main Queue must contain at least 1 track" }

        return withContext(Dispatchers.IO) {
            database.withTransaction {
                val allTrackIds = (draft.introTrackIds + draft.mainTrackIds).distinct()
                val tracksById = trackDao.getByIds(allTrackIds).associateBy { it.id }

                val missingTrackId = allTrackIds.firstOrNull { it !in tracksById }
                require(missingTrackId == null) {
                    "Referenced track no longer exists in the library"
                }

                val mainTotalDurationMs = draft.mainTrackIds.sumOf { id ->
                    tracksById.getValue(id).durationMs
                }
                require(mainTotalDurationMs > 0L) {
                    "Main Queue combined duration must be positive"
                }
                require(draft.targetDurationMs in 1L..mainTotalDurationMs) {
                    "Timer duration must be between 1ms and combined Main Queue duration ($mainTotalDurationMs ms)"
                }

                val now = System.currentTimeMillis()
                val existing = draft.sessionId?.let { sessionDao.getSessionById(it) }
                val sessionId = existing?.id ?: draft.sessionId ?: UUID.randomUUID().toString()
                val createdAt = existing?.createdAtEpochMs ?: now

                sessionDao.upsertSession(
                    SessionEntity(
                        id = sessionId,
                        name = trimmedName,
                        targetDurationMs = draft.targetDurationMs,
                        createdAtEpochMs = createdAt,
                        updatedAtEpochMs = now
                    )
                )

                sessionDao.deleteSessionTracksForSession(sessionId)

                val introEntities = draft.introTrackIds.mapIndexed { index, trackId ->
                    SessionTrackEntity(
                        id = UUID.randomUUID().toString(),
                        sessionId = sessionId,
                        trackId = trackId,
                        zone = QueueZone.INTRO.toDbValue(),
                        position = index
                    )
                }
                val mainEntities = draft.mainTrackIds.mapIndexed { index, trackId ->
                    SessionTrackEntity(
                        id = UUID.randomUUID().toString(),
                        sessionId = sessionId,
                        trackId = trackId,
                        zone = QueueZone.MAIN.toDbValue(),
                        position = index
                    )
                }

                sessionDao.insertSessionTracks(introEntities + mainEntities)
                sessionId
            }
        }
    }

    suspend fun deleteSession(sessionId: String) {
        withContext(Dispatchers.IO) {
            sessionDao.deleteSessionById(sessionId)
        }
    }

    suspend fun getTrackReferenceSummary(trackId: String): TrackReferenceSummary {
        return withContext(Dispatchers.IO) {
            val sessionIds = sessionDao.getSessionIdsReferencingTrack(trackId)
            if (sessionIds.isEmpty()) {
                return@withContext TrackReferenceSummary(trackId, emptyList())
            }
            val affected = sessionIds.mapNotNull { sessionId ->
                val session = sessionDao.getSessionById(sessionId) ?: return@mapNotNull null
                val entries = sessionDao.getSessionTracks(sessionId)
                val mainEntries = entries.filter { it.zone == QueueZone.MAIN.toDbValue() }
                val hasTrackInMain = mainEntries.any { it.trackId == trackId }
                val remainingMainCount = mainEntries.count { it.trackId != trackId }
                val willEmptyMainQueue = hasTrackInMain && remainingMainCount == 0
                session.updatedAtEpochMs to AffectedSessionInfo(
                    sessionId = session.id,
                    sessionName = session.name,
                    willEmptyMainQueue = willEmptyMainQueue
                )
            }.sortedByDescending { it.first }
                .map { it.second }

            TrackReferenceSummary(
                trackId = trackId,
                affectedSessions = affected
            )
        }
    }

    /**
     * Atomically removes the library track (cascading its session_tracks rows),
     * re-indexes remaining positions in every referencing session, auto-clamps
     * shortened Main Queue timers (or sets 0L if Main becomes empty) without
     * altering session updated_at_epoch_ms, and then deletes the app-private audio file.
     */
    suspend fun deleteTrackSafely(track: TrackEntity) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val affectedSessionIds = sessionDao.getSessionIdsReferencingTrack(track.id)
                trackDao.deleteById(track.id)

                for (sessionId in affectedSessionIds) {
                    val session = sessionDao.getSessionById(sessionId) ?: continue
                    val remainingEntries = sessionDao.getSessionTracks(sessionId)

                    val remainingIntro = remainingEntries
                        .filter { it.zone == QueueZone.INTRO.toDbValue() }
                        .sortedBy { it.position }
                        .mapIndexed { index, entry -> entry.copy(position = index) }

                    val remainingMain = remainingEntries
                        .filter { it.zone == QueueZone.MAIN.toDbValue() }
                        .sortedBy { it.position }
                        .mapIndexed { index, entry -> entry.copy(position = index) }

                    sessionDao.deleteSessionTracksForSession(sessionId)
                    val reindexed = remainingIntro + remainingMain
                    if (reindexed.isNotEmpty()) {
                        sessionDao.insertSessionTracks(reindexed)
                    }

                    val newTargetDurationMs = if (remainingMain.isEmpty()) {
                        0L
                    } else {
                        val mainTrackIds = remainingMain.map { it.trackId }.distinct()
                        val mainTracksById = trackDao.getByIds(mainTrackIds).associateBy { it.id }
                        val remainingMainTotalMs = remainingMain.sumOf { entry ->
                            mainTracksById[entry.trackId]?.durationMs ?: 0L
                        }
                        if (remainingMainTotalMs <= 0L) {
                            0L
                        } else if (session.targetDurationMs > remainingMainTotalMs) {
                            remainingMainTotalMs
                        } else {
                            session.targetDurationMs
                        }
                    }

                    if (newTargetDurationMs != session.targetDurationMs) {
                        sessionDao.updateTargetDuration(sessionId, newTargetDurationMs)
                    }
                }
            }

            trackRepository.deleteAudioFile(track)
        }
    }

    private fun assembleSession(
        session: SessionEntity,
        entries: List<SessionTrackEntity>,
        tracksById: Map<String, TrackEntity>
    ): SessionWithQueues {
        val introItems = entries
            .filter { it.zone == QueueZone.INTRO.toDbValue() }
            .sortedBy { it.position }
            .mapNotNull { entry ->
                tracksById[entry.trackId]?.let { track ->
                    QueueTrackItem(
                        entryId = entry.id,
                        zone = QueueZone.INTRO,
                        position = entry.position,
                        track = track
                    )
                }
            }

        val mainItems = entries
            .filter { it.zone == QueueZone.MAIN.toDbValue() }
            .sortedBy { it.position }
            .mapNotNull { entry ->
                tracksById[entry.trackId]?.let { track ->
                    QueueTrackItem(
                        entryId = entry.id,
                        zone = QueueZone.MAIN,
                        position = entry.position,
                        track = track
                    )
                }
            }

        return SessionWithQueues(
            session = session,
            introItems = introItems,
            mainItems = mainItems
        )
    }
}
