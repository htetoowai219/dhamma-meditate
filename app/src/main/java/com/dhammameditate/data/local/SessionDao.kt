package com.dhammameditate.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY updated_at_epoch_ms DESC")
    fun observeSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :sessionId LIMIT 1")
    fun observeSessionById(sessionId: String): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: String): SessionEntity?

    @Query("SELECT * FROM session_tracks ORDER BY session_id ASC, zone ASC, position ASC")
    fun observeAllSessionTracks(): Flow<List<SessionTrackEntity>>

    @Query("SELECT * FROM session_tracks WHERE session_id = :sessionId ORDER BY zone ASC, position ASC")
    fun observeSessionTracks(sessionId: String): Flow<List<SessionTrackEntity>>

    @Query("SELECT * FROM session_tracks WHERE session_id = :sessionId ORDER BY zone ASC, position ASC")
    suspend fun getSessionTracks(sessionId: String): List<SessionTrackEntity>

    @Query("SELECT DISTINCT session_id FROM session_tracks WHERE track_id = :trackId")
    suspend fun getSessionIdsReferencingTrack(trackId: String): List<String>

    @Upsert
    suspend fun upsertSession(session: SessionEntity)

    @Query("DELETE FROM session_tracks WHERE session_id = :sessionId")
    suspend fun deleteSessionTracksForSession(sessionId: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSessionTracks(items: List<SessionTrackEntity>)

    @Query("DELETE FROM sessions WHERE id = :sessionId")
    suspend fun deleteSessionById(sessionId: String)

    @Query("UPDATE sessions SET target_duration_ms = :targetDurationMs WHERE id = :sessionId")
    suspend fun updateTargetDuration(sessionId: String, targetDurationMs: Long)
}
