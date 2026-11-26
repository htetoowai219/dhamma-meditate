package com.dhammameditate.ui.tutorial

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class TutorialEngineTest {

    private val sampleDefinition = TutorialDefinition(
        id = TutorialId.ADD_AUDIO,
        titleRes = 1,
        descriptionRes = 2,
        startRoute = TutorialStartRoute.HOME_SESSIONS,
        steps = listOf(
            TutorialStep(
                targetId = TutorialTargetId.NAV_LIBRARY_TAB,
                titleRes = 10,
                bodyRes = 11,
                tapHintRes = 12,
                interaction = TutorialStepInteraction.TAP_TARGET
            ),
            TutorialStep(
                targetId = TutorialTargetId.LIBRARY_IMPORT_BUTTON,
                titleRes = 20,
                bodyRes = 21,
                tapHintRes = 22,
                interaction = TutorialStepInteraction.CONFIRM_ONLY
            )
        )
    )

    @Test
    fun `shouldShowFirstTimeOffer returns true only when unseen and neither playback nor tutorial is active`() {
        assertTrue(
            TutorialEngine.shouldShowFirstTimeOffer(
                hasSeenOffer = false,
                hasActivePlayback = false,
                hasActiveTutorial = false
            )
        )
        assertFalse(
            TutorialEngine.shouldShowFirstTimeOffer(
                hasSeenOffer = true,
                hasActivePlayback = false,
                hasActiveTutorial = false
            )
        )
        assertFalse(
            TutorialEngine.shouldShowFirstTimeOffer(
                hasSeenOffer = false,
                hasActivePlayback = true,
                hasActiveTutorial = false
            )
        )
        assertFalse(
            TutorialEngine.shouldShowFirstTimeOffer(
                hasSeenOffer = false,
                hasActivePlayback = false,
                hasActiveTutorial = true
            )
        )
    }

    @Test
    fun `start initializes at step 1 of total steps`() {
        val state = TutorialEngine.start(sampleDefinition)
        assertEquals(0, state.currentStepIndex)
        assertEquals(1, state.stepNumber)
        assertEquals(2, state.totalSteps)
        assertFalse(state.isLastStep)
        assertEquals(TutorialTargetId.NAV_LIBRARY_TAB, state.currentStep.targetId)
    }

    @Test
    fun `onTargetTapped ignores wrong target and advances on matching target`() {
        val step1 = TutorialEngine.start(sampleDefinition)

        val unchanged = TutorialEngine.onTargetTapped(step1, TutorialTargetId.LIBRARY_IMPORT_BUTTON)
        assertEquals(step1, unchanged)

        val step2 = TutorialEngine.onTargetTapped(step1, TutorialTargetId.NAV_LIBRARY_TAB)
        assertNotNull(step2)
        assertEquals(1, step2!!.currentStepIndex)
        assertEquals(2, step2.stepNumber)
        assertTrue(step2.isLastStep)
        assertEquals(TutorialTargetId.LIBRARY_IMPORT_BUTTON, step2.currentStep.targetId)

        val finished = TutorialEngine.onTargetTapped(step2, TutorialTargetId.LIBRARY_IMPORT_BUTTON)
        assertNull(finished)
    }

    @Test
    fun `confirmCurrentStep advances non-final steps and completes final step`() {
        val step1 = TutorialEngine.start(sampleDefinition)
        val step2 = TutorialEngine.confirmCurrentStep(step1)
        assertNotNull(step2)
        assertEquals(2, step2!!.stepNumber)

        val finished = TutorialEngine.confirmCurrentStep(step2)
        assertNull(finished)
    }

    @Test
    fun `skip always terminates the active tutorial`() {
        assertNull(TutorialEngine.skip())
    }

    @Test
    fun `formatStepNumbers always produces Western ASCII digits regardless of default locale`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("my-MM"))
            assertEquals("1" to "2", TutorialEngine.formatStepNumbers(1, 2))
            assertEquals("12", TutorialEngine.formatCount(12))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `TutorialRegistry exposes ADD_AUDIO and CREATE_SESSION with expected step definitions`() {
        assertEquals(2, TutorialRegistry.all.size)

        val addAudio = TutorialRegistry.findById(TutorialId.ADD_AUDIO)
        assertNotNull(addAudio)
        assertEquals(TutorialStartRoute.HOME_SESSIONS, addAudio!!.startRoute)
        assertEquals(2, addAudio.steps.size)
        assertEquals(TutorialTargetId.NAV_LIBRARY_TAB, addAudio.steps[0].targetId)
        assertEquals(TutorialStepInteraction.TAP_TARGET, addAudio.steps[0].interaction)
        assertEquals(TutorialTargetId.LIBRARY_IMPORT_BUTTON, addAudio.steps[1].targetId)
        assertEquals(TutorialStepInteraction.CONFIRM_ONLY, addAudio.steps[1].interaction)

        val createSession = TutorialRegistry.findById(TutorialId.CREATE_SESSION)
        assertNotNull(createSession)
        assertEquals(TutorialStartRoute.HOME_SESSIONS, createSession!!.startRoute)
        assertEquals(5, createSession.steps.size)
        assertEquals(TutorialTargetId.SESSIONS_NEW_BUTTON, createSession.steps[0].targetId)
        assertEquals(TutorialStepInteraction.TAP_TARGET, createSession.steps[0].interaction)
        assertNull(createSession.steps[0].emptyLibraryNoteRes)

        assertEquals(TutorialTargetId.EDITOR_INTRO_CARD, createSession.steps[1].targetId)
        assertEquals(TutorialStepInteraction.CONFIRM_ONLY, createSession.steps[1].interaction)
        assertNull(createSession.steps[1].emptyLibraryNoteRes)

        assertEquals(TutorialTargetId.EDITOR_MAIN_CARD, createSession.steps[2].targetId)
        assertEquals(TutorialStepInteraction.CONFIRM_ONLY, createSession.steps[2].interaction)
        assertNotNull(createSession.steps[2].emptyLibraryNoteRes)

        assertEquals(TutorialTargetId.EDITOR_TIMER_CARD, createSession.steps[3].targetId)
        assertEquals(TutorialStepInteraction.CONFIRM_ONLY, createSession.steps[3].interaction)
        assertNull(createSession.steps[3].emptyLibraryNoteRes)

        assertEquals(TutorialTargetId.EDITOR_SAVE_BUTTON, createSession.steps[4].targetId)
        assertEquals(TutorialStepInteraction.CONFIRM_ONLY, createSession.steps[4].interaction)
        assertNull(createSession.steps[4].emptyLibraryNoteRes)
    }

    @Test
    fun `CREATE_SESSION advances cleanly across all five steps and terminates after final confirmation`() {
        val step1 = TutorialEngine.start(TutorialRegistry.createSession)
        assertEquals(1, step1.stepNumber)
        assertEquals(5, step1.totalSteps)
        assertFalse(step1.isLastStep)

        val step2 = TutorialEngine.onTargetTapped(step1, TutorialTargetId.SESSIONS_NEW_BUTTON)
        assertNotNull(step2)
        assertEquals(2, step2!!.stepNumber)
        assertEquals(TutorialTargetId.EDITOR_INTRO_CARD, step2.currentStep.targetId)
        assertFalse(step2.isLastStep)

        val step3 = TutorialEngine.confirmCurrentStep(step2)
        assertNotNull(step3)
        assertEquals(3, step3!!.stepNumber)
        assertEquals(TutorialTargetId.EDITOR_MAIN_CARD, step3.currentStep.targetId)
        assertFalse(step3.isLastStep)

        val step4 = TutorialEngine.confirmCurrentStep(step3)
        assertNotNull(step4)
        assertEquals(4, step4!!.stepNumber)
        assertEquals(TutorialTargetId.EDITOR_TIMER_CARD, step4.currentStep.targetId)
        assertFalse(step4.isLastStep)

        val step5 = TutorialEngine.confirmCurrentStep(step4)
        assertNotNull(step5)
        assertEquals(5, step5!!.stepNumber)
        assertEquals(TutorialTargetId.EDITOR_SAVE_BUTTON, step5.currentStep.targetId)
        assertTrue(step5.isLastStep)

        val finished = TutorialEngine.confirmCurrentStep(step5)
        assertNull(finished)
    }
}
