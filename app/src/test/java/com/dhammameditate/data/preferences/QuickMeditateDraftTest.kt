package com.dhammameditate.data.preferences

import com.dhammameditate.data.local.TrackEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickMeditateDraftTest {

    private fun track(id: String, durationMs: Long): TrackEntity = TrackEntity(
        id = id,
        title = "Track $id",
        localFileName = "$id.mp3",
        mimeType = "audio/mpeg",
        durationMs = durationMs,
        createdAtEpochMs = 1_000L
    )

    @Test
    fun codecRoundTripsEmptySingleAndDuplicateIds() {
        assertEquals("", QuickMeditateDraftCodec.encodeTrackIds(emptyList()))
        assertEquals(emptyList<String>(), QuickMeditateDraftCodec.decodeTrackIds(""))
        assertEquals(emptyList<String>(), QuickMeditateDraftCodec.decodeTrackIds(null))

        val ids = listOf("a", "b", "a", "c")
        val encoded = QuickMeditateDraftCodec.encodeTrackIds(ids)
        assertEquals("a,b,a,c", encoded)
        assertEquals(ids, QuickMeditateDraftCodec.decodeTrackIds(encoded))
    }

    @Test
    fun codecTrimsAndDropsBlankEntries() {
        val decoded = QuickMeditateDraftCodec.decodeTrackIds(" a , , b ,, c ")
        assertEquals(listOf("a", "b", "c"), decoded)
    }

    @Test
    fun hasAnySelectionReflectsEmptyAndPopulatedStates() {
        assertFalse(QuickMeditateDraft().hasAnySelection)
        assertTrue(QuickMeditateDraft(introTrackIds = listOf("i1")).hasAnySelection)
        assertTrue(QuickMeditateDraft(mainTrackIds = listOf("m1")).hasAnySelection)
        assertTrue(QuickMeditateDraft(targetDurationMs = 60_000L).hasAnySelection)
    }

    @Test
    fun reconcilePreservesOrderAndDuplicatesWhenAllTracksExist() {
        val intro = track("i1", 5 * 60_000L)
        val mainA = track("m1", 20 * 60_000L)
        val mainB = track("m2", 15 * 60_000L)
        val library = listOf(intro, mainA, mainB).associateBy { it.id }

        val draft = QuickMeditateDraft(
            introTrackIds = listOf("i1", "i1"),
            mainTrackIds = listOf("m1", "m2", "m1"),
            targetDurationMs = 30 * 60_000L
        )

        val resolved = draft.reconcile(library)

        assertEquals(listOf(intro, intro), resolved.introTracks)
        assertEquals(listOf(mainA, mainB, mainA), resolved.mainTracks)
        assertEquals(30 * 60_000L, resolved.targetDurationMs)
        assertFalse(resolved.wasCapped)
        assertFalse(resolved.droppedMissingTracks)
    }

    @Test
    fun reconcileDropsDeletedTracksAndClampsTimerToRemainingMainTotal() {
        val mainA = track("m1", 10 * 60_000L)
        val library = mapOf(mainA.id to mainA)

        val draft = QuickMeditateDraft(
            introTrackIds = listOf("deleted-intro"),
            mainTrackIds = listOf("m1", "deleted-main"),
            targetDurationMs = 25 * 60_000L
        )

        val resolved = draft.reconcile(library)

        assertTrue(resolved.introTracks.isEmpty())
        assertEquals(listOf(mainA), resolved.mainTracks)
        assertEquals(10 * 60_000L, resolved.targetDurationMs)
        assertTrue(resolved.wasCapped)
        assertTrue(resolved.droppedMissingTracks)
        assertEquals(
            QuickMeditateDraft(
                introTrackIds = emptyList(),
                mainTrackIds = listOf("m1"),
                targetDurationMs = 10 * 60_000L
            ),
            resolved.toReconciledDraft()
        )
    }

    @Test
    fun reconcileResetsTimerToZeroWhenAllMainTracksAreDeleted() {
        val intro = track("i1", 5 * 60_000L)
        val library = mapOf(intro.id to intro)

        val draft = QuickMeditateDraft(
            introTrackIds = listOf("i1"),
            mainTrackIds = listOf("deleted-main"),
            targetDurationMs = 15 * 60_000L
        )

        val resolved = draft.reconcile(library)

        assertEquals(listOf(intro), resolved.introTracks)
        assertTrue(resolved.mainTracks.isEmpty())
        assertEquals(0L, resolved.targetDurationMs)
        assertFalse(resolved.wasCapped)
        assertTrue(resolved.droppedMissingTracks)
    }

    @Test
    fun reconcileDefaultsTimerWhenDraftTargetIsZeroAndMainTracksExist() {
        val mainA = track("m1", 20 * 60_000L)
        val library = mapOf(mainA.id to mainA)

        val draft = QuickMeditateDraft(
            mainTrackIds = listOf("m1"),
            targetDurationMs = 0L
        )

        val resolved = draft.reconcile(library)

        assertEquals(20 * 60_000L, resolved.targetDurationMs)
        assertFalse(resolved.wasCapped)
        assertFalse(resolved.droppedMissingTracks)
    }
}
