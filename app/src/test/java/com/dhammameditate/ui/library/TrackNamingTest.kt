package com.dhammameditate.ui.library

import com.dhammameditate.data.local.TrackEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackNamingTest {

    private fun sampleTrack(id: String, title: String, durationMs: Long = 600_000L): TrackEntity {
        return TrackEntity(
            id = id,
            title = title,
            localFileName = "$id.mp3",
            mimeType = "audio/mpeg",
            durationMs = durationMs,
            createdAtEpochMs = 1_700_000_000_000L
        )
    }

    @Test
    fun `normalize trims leading and trailing whitespace and preserves inner spaces`() {
        assertEquals("Morning Metta", TrackTitleValidation.normalize("  Morning Metta \t\n "))
        assertTrue(TrackTitleValidation.isValid("  Morning Metta  "))
    }

    @Test
    fun `normalize rejects empty and whitespace-only strings`() {
        assertNull(TrackTitleValidation.normalize(""))
        assertNull(TrackTitleValidation.normalize("   "))
        assertNull(TrackTitleValidation.normalize("\t\n "))
        assertFalse(TrackTitleValidation.isValid("   "))
    }

    @Test
    fun `normalize preserves Burmese Unicode text cleanly`() {
        val burmese = "  မေတ္တာပို့ တရားတော် (ပထမပိုင်း)  "
        assertEquals("မေတ္တာပို့ တရားတော် (ပထမပိုင်း)", TrackTitleValidation.normalize(burmese))
        assertTrue(TrackTitleValidation.isValid(burmese))
    }

    @Test
    fun `fromImportedTracks returns Hidden when list is empty`() {
        val state = ImportRenameFlow.fromImportedTracks(emptyList())
        assertEquals(ImportRenameState.Hidden, state)
    }

    @Test
    fun `fromImportedTracks opens single Renaming dialog for one track`() {
        val track = sampleTrack("t1", "Raw_Audio_01")
        val state = ImportRenameFlow.fromImportedTracks(listOf(track))

        assertTrue(state is ImportRenameState.Renaming)
        val renaming = state as ImportRenameState.Renaming
        assertFalse(renaming.isBatch)
        assertEquals(0, renaming.currentIndex)
        assertEquals(1, renaming.stepNumber)
        assertEquals(1, renaming.totalCount)
        assertTrue(renaming.isLastStep)
        assertEquals("Raw_Audio_01", renaming.draftTitle)
        assertFalse(renaming.showBlankError)
        assertTrue(renaming.canSave)
    }

    @Test
    fun `fromImportedTracks opens BatchPrompt for two or more tracks`() {
        val tracks = listOf(
            sampleTrack("t1", "Track 1"),
            sampleTrack("t2", "Track 2")
        )
        val state = ImportRenameFlow.fromImportedTracks(tracks)

        assertTrue(state is ImportRenameState.BatchPrompt)
        assertEquals(tracks, (state as ImportRenameState.BatchPrompt).tracks)
    }

    @Test
    fun `batch flow supports Skip all directly from BatchPrompt`() {
        val tracks = listOf(
            sampleTrack("t1", "Track 1"),
            sampleTrack("t2", "Track 2")
        )
        val prompt = ImportRenameFlow.fromImportedTracks(tracks)
        assertTrue(prompt is ImportRenameState.BatchPrompt)

        assertEquals(ImportRenameState.Hidden, ImportRenameFlow.skipAll())
    }

    @Test
    fun `batch step-through advances in import order and closes after final track`() {
        val t1 = sampleTrack("t1", "Intro Bell")
        val t2 = sampleTrack("t2", "Main Talk")
        val t3 = sampleTrack("t3", "Closing Chant")
        val prompt = ImportRenameFlow.fromImportedTracks(listOf(t1, t2, t3))

        val step1 = ImportRenameFlow.beginBatchRenaming(prompt) as ImportRenameState.Renaming
        assertTrue(step1.isBatch)
        assertEquals(1, step1.stepNumber)
        assertEquals(3, step1.totalCount)
        assertFalse(step1.isLastStep)
        assertEquals(t1, step1.currentTrack)
        assertEquals("Intro Bell", step1.draftTitle)

        // Blank input sets error flag and disables save
        val blankStep1 = ImportRenameFlow.updateDraftTitle(step1, "   ") as ImportRenameState.Renaming
        assertTrue(blankStep1.showBlankError)
        assertFalse(blankStep1.canSave)
        assertNull(blankStep1.trimmedTitle)

        // Valid input clears error flag
        val validStep1 = ImportRenameFlow.updateDraftTitle(blankStep1, "  Opening Bell  ") as ImportRenameState.Renaming
        assertFalse(validStep1.showBlankError)
        assertTrue(validStep1.canSave)
        assertEquals("Opening Bell", validStep1.trimmedTitle)

        // Advance to step 2 (also resets blank error and pre-fills track 2)
        val step2 = ImportRenameFlow.advance(validStep1) as ImportRenameState.Renaming
        assertEquals(2, step2.stepNumber)
        assertEquals(3, step2.totalCount)
        assertFalse(step2.isLastStep)
        assertEquals(t2, step2.currentTrack)
        assertEquals("Main Talk", step2.draftTitle)
        assertFalse(step2.showBlankError)

        // Advance (e.g., Skip) to final step 3
        val step3 = ImportRenameFlow.advance(step2) as ImportRenameState.Renaming
        assertEquals(3, step3.stepNumber)
        assertEquals(3, step3.totalCount)
        assertTrue(step3.isLastStep)
        assertEquals(t3, step3.currentTrack)
        assertEquals("Closing Chant", step3.draftTitle)

        // Advance past final step returns Hidden
        assertEquals(ImportRenameState.Hidden, ImportRenameFlow.advance(step3))
    }

    @Test
    fun `skipAll exits batch step-through mid-way`() {
        val tracks = listOf(
            sampleTrack("t1", "Track 1"),
            sampleTrack("t2", "Track 2"),
            sampleTrack("t3", "Track 3")
        )
        val step1 = ImportRenameFlow.beginBatchRenaming(ImportRenameFlow.fromImportedTracks(tracks))
        assertTrue(step1 is ImportRenameState.Renaming)
        assertEquals(ImportRenameState.Hidden, ImportRenameFlow.skipAll())
    }

    @Test
    fun `PendingTrackRename tracks draft validation cleanly`() {
        val track = sampleTrack("t1", "Original Title")
        val initial = PendingTrackRename(track = track, draftTitle = track.title)
        assertTrue(initial.canSave)
        assertFalse(initial.showBlankError)

        val cleared = initial.withDraftTitle("   ")
        assertFalse(cleared.canSave)
        assertTrue(cleared.showBlankError)

        val updated = cleared.withDraftTitle("  တရားတော်  ")
        assertTrue(updated.canSave)
        assertFalse(updated.showBlankError)
        assertEquals("တရားတော်", updated.trimmedTitle)
    }
}
