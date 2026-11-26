package com.dhammameditate.di

import android.content.Context
import com.dhammameditate.data.importer.AudioFileImporter
import com.dhammameditate.data.local.AppDatabase
import com.dhammameditate.data.preferences.AppLanguageStore
import com.dhammameditate.data.repository.SessionRepository
import com.dhammameditate.data.repository.TrackRepository
import com.dhammameditate.playback.AndroidDistractionChecker
import com.dhammameditate.playback.DistractionChecker
import com.dhammameditate.playback.PlaybackClient
import com.dhammameditate.playback.PlaybackStateStore

class AppContainer(context: Context) {
    private val appContext: Context = context.applicationContext

    val database: AppDatabase by lazy {
        AppDatabase.create(appContext)
    }

    val trackRepository: TrackRepository by lazy {
        TrackRepository(appContext, database.trackDao())
    }

    val sessionRepository: SessionRepository by lazy {
        SessionRepository(
            database = database,
            sessionDao = database.sessionDao(),
            trackDao = database.trackDao(),
            trackRepository = trackRepository
        )
    }

    val audioFileImporter: AudioFileImporter by lazy {
        AudioFileImporter(appContext, trackRepository)
    }

    val playbackStateStore: PlaybackStateStore by lazy {
        PlaybackStateStore()
    }

    val playbackClient: PlaybackClient by lazy {
        PlaybackClient(appContext, playbackStateStore)
    }

    val distractionChecker: DistractionChecker by lazy {
        AndroidDistractionChecker(appContext)
    }

    val appLanguageStore: AppLanguageStore by lazy {
        AppLanguageStore(appContext)
    }
}
