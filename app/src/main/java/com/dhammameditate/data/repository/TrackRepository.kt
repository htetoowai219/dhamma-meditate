package com.dhammameditate.data.repository

import android.content.Context
import com.dhammameditate.data.local.TrackDao
import com.dhammameditate.data.local.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class TrackRepository(
    context: Context,
    private val trackDao: TrackDao
) {
    val audioDir: File = File(context.applicationContext.filesDir, AUDIO_DIR_NAME)

    fun observeTracks(): Flow<List<TrackEntity>> = trackDao.observeAll()

    suspend fun getTrack(id: String): TrackEntity? = trackDao.getById(id)

    fun resolveAudioFile(track: TrackEntity): File {
        val safeName = File(track.localFileName).name
        return File(audioDir, safeName)
    }

    suspend fun insertTrack(track: TrackEntity) {
        trackDao.insert(track)
    }

    suspend fun deleteTrack(track: TrackEntity) {
        withContext(Dispatchers.IO) {
            trackDao.deleteById(track.id)
            val file = resolveAudioFile(track)
            if (file.exists()) {
                file.delete()
            }
        }
    }

    companion object {
        const val AUDIO_DIR_NAME = "audio"
    }
}
