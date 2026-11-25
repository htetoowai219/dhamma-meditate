package com.dhammameditate.data.importer

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.dhammameditate.data.local.TrackEntity
import com.dhammameditate.data.repository.TrackRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID

sealed interface ImportResult {
    data class Success(val track: TrackEntity) : ImportResult
    data class Failure(val displayName: String, val reason: String) : ImportResult
}

class AudioFileImporter(
    private val context: Context,
    private val trackRepository: TrackRepository
) {
    suspend fun importUris(uris: List<Uri>): List<ImportResult> = withContext(Dispatchers.IO) {
        uris.map { uri -> importSingleUri(uri) }
    }

    private suspend fun importSingleUri(uri: Uri): ImportResult {
        val displayName = queryDisplayName(uri) ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Selected file"
        val resolverMime = context.contentResolver.getType(uri)?.lowercase(Locale.ROOT)
        val extension = resolveSupportedExtension(displayName, resolverMime)
            ?: return ImportResult.Failure(
                displayName = displayName,
                reason = "Unsupported format. Supported: MP3, M4A/AAC, WAV, FLAC, OGG."
            )

        val audioDir = trackRepository.audioDir
        if (!audioDir.exists() && !audioDir.mkdirs()) {
            return ImportResult.Failure(
                displayName = displayName,
                reason = "Unable to prepare app-private audio storage."
            )
        }

        val trackId = UUID.randomUUID().toString()
        val tempFile = File(audioDir, "$trackId.tmp")
        var finalFile: File? = null

        return try {
            val bytesCopied = copyUriToFile(uri, tempFile)
            if (bytesCopied <= 0L) {
                return ImportResult.Failure(
                    displayName = displayName,
                    reason = "File is empty or unreadable."
                )
            }

            val probe = probeAudioFile(tempFile, displayName, extension, resolverMime)
                ?: return ImportResult.Failure(
                    displayName = displayName,
                    reason = "File is corrupt, DRM-protected, or contains no readable audio."
                )

            val localFileName = "$trackId.$extension"
            val targetFile = File(audioDir, localFileName)
            finalFile = targetFile
            moveAtomically(tempFile, targetFile)

            val track = TrackEntity(
                id = trackId,
                title = probe.title,
                localFileName = localFileName,
                mimeType = probe.mimeType,
                durationMs = probe.durationMs,
                createdAtEpochMs = System.currentTimeMillis()
            )
            trackRepository.insertTrack(track)
            ImportResult.Success(track)
        } catch (ce: CancellationException) {
            finalFile?.delete()
            throw ce
        } catch (e: AudioValidationException) {
            finalFile?.delete()
            ImportResult.Failure(displayName = displayName, reason = e.message ?: "Unreadable audio file.")
        } catch (e: Exception) {
            finalFile?.delete()
            ImportResult.Failure(
                displayName = displayName,
                reason = "Could not import file: file may be corrupt or unsupported."
            )
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx)?.takeIf { it.isNotBlank() } else null
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun copyUriToFile(uri: Uri, destination: File): Long {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw AudioValidationException("Unable to open selected file.")
        return input.use { inStream ->
            FileOutputStream(destination).use { outStream ->
                val copied = inStream.copyTo(outStream, bufferSize = 16 * 1024)
                outStream.fd.sync()
                copied
            }
        }
    }

    private fun moveAtomically(source: File, target: File) {
        if (source.renameTo(target)) return
        source.inputStream().use { input ->
            FileOutputStream(target).use { output ->
                input.copyTo(output, bufferSize = 16 * 1024)
                output.fd.sync()
            }
        }
        source.delete()
    }

    private fun probeAudioFile(
        file: File,
        displayName: String,
        extension: String,
        resolverMime: String?
    ): ProbedAudio? {
        var probedAudioMime: String? = null
        var extractorDurationMs = 0L

        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            if (extractor.drmInitData != null) {
                throw AudioValidationException("DRM-protected audio files are not supported.")
            }

            var hasAudioTrack = false
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)?.lowercase(Locale.ROOT) ?: continue
                if (mime.startsWith("video/")) {
                    throw AudioValidationException("Video files are not supported. Select an audio file.")
                }
                if (mime.startsWith("audio/")) {
                    hasAudioTrack = true
                    if (probedAudioMime == null) {
                        probedAudioMime = mime
                    }
                    if (format.containsKey(MediaFormat.KEY_DURATION)) {
                        val durationUs = format.getLong(MediaFormat.KEY_DURATION)
                        if (durationUs > 0L) {
                            extractorDurationMs = maxOf(extractorDurationMs, durationUs / 1000L)
                        }
                    }
                }
            }

            if (!hasAudioTrack) {
                throw AudioValidationException("No readable audio stream found in file.")
            }
        } catch (e: AudioValidationException) {
            throw e
        } catch (_: Exception) {
            return null
        } finally {
            extractor.release()
        }

        var retrieverDurationMs = 0L
        var embeddedTitle: String? = null
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            retrieverDurationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.takeIf { it > 0L }
                ?: 0L
            embeddedTitle = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            // Fall back to MediaExtractor duration & display name when retriever fails on valid audio
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
                // Ignore release error
            }
        }

        val finalDurationMs = if (retrieverDurationMs > 0L) retrieverDurationMs else extractorDurationMs
        if (finalDurationMs <= 0L) {
            throw AudioValidationException("Audio duration could not be determined or is zero.")
        }

        val fallbackTitle = stripLastExtension(displayName).trim().ifBlank { "Untitled Track" }
        val finalTitle = embeddedTitle ?: fallbackTitle
        val finalMime = probedAudioMime
            ?: resolverMime?.takeIf { it.startsWith("audio/") }
            ?: EXTENSION_TO_MIME[extension]
            ?: "audio/mpeg"

        return ProbedAudio(
            title = finalTitle,
            mimeType = finalMime,
            durationMs = finalDurationMs
        )
    }

    private fun resolveSupportedExtension(displayName: String, resolverMime: String?): String? {
        val extFromName = displayName
            .substringAfterLast('.', missingDelimiterValue = "")
            .lowercase(Locale.ROOT)
            .takeIf { it in SUPPORTED_EXTENSIONS }
        if (extFromName != null) return extFromName

        return resolverMime?.let { MIME_TO_EXTENSION[it] }
    }

    private fun stripLastExtension(name: String): String {
        val dotIndex = name.lastIndexOf('.')
        return if (dotIndex > 0) name.substring(0, dotIndex) else name
    }

    private data class ProbedAudio(
        val title: String,
        val mimeType: String,
        val durationMs: Long
    )

    private class AudioValidationException(message: String) : Exception(message)

    companion object {
        val SUPPORTED_EXTENSIONS = setOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "oga")

        private val MIME_TO_EXTENSION = mapOf(
            "audio/mpeg" to "mp3",
            "audio/mp3" to "mp3",
            "audio/mp4" to "m4a",
            "audio/m4a" to "m4a",
            "audio/x-m4a" to "m4a",
            "audio/aac" to "aac",
            "audio/aacp" to "aac",
            "audio/wav" to "wav",
            "audio/x-wav" to "wav",
            "audio/wave" to "wav",
            "audio/vnd.wave" to "wav",
            "audio/flac" to "flac",
            "audio/x-flac" to "flac",
            "audio/ogg" to "ogg",
            "application/ogg" to "ogg",
            "audio/vorbis" to "ogg",
            "audio/opus" to "ogg"
        )

        private val EXTENSION_TO_MIME = mapOf(
            "mp3" to "audio/mpeg",
            "m4a" to "audio/mp4",
            "aac" to "audio/aac",
            "wav" to "audio/wav",
            "flac" to "audio/flac",
            "ogg" to "audio/ogg",
            "oga" to "audio/ogg"
        )
    }
}
