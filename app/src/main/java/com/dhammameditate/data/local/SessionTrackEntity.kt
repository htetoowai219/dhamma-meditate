package com.dhammameditate.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "session_tracks",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["track_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(
            name = "index_session_tracks_session_id",
            value = ["session_id"]
        ),
        Index(
            name = "index_session_tracks_track_id",
            value = ["track_id"]
        ),
        Index(
            name = "index_session_tracks_session_id_zone_position",
            value = ["session_id", "zone", "position"],
            unique = true
        )
    ]
)
data class SessionTrackEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "session_id")
    val sessionId: String,
    @ColumnInfo(name = "track_id")
    val trackId: String,
    @ColumnInfo(name = "zone")
    val zone: String,
    @ColumnInfo(name = "position")
    val position: Int
)
