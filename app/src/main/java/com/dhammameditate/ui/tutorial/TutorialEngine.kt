package com.dhammameditate.ui.tutorial

import java.util.Locale

object TutorialEngine {

    fun shouldShowFirstTimeOffer(
        hasSeenOffer: Boolean,
        hasActivePlayback: Boolean,
        hasActiveTutorial: Boolean
    ): Boolean {
        return !hasSeenOffer && !hasActivePlayback && !hasActiveTutorial
    }

    fun start(definition: TutorialDefinition): TutorialSessionState {
        return TutorialSessionState(
            definition = definition,
            currentStepIndex = 0
        )
    }

    fun onTargetTapped(
        state: TutorialSessionState,
        targetId: TutorialTargetId
    ): TutorialSessionState? {
        if (targetId != state.currentStep.targetId) {
            return state
        }
        return if (state.isLastStep) {
            null
        } else {
            state.copy(currentStepIndex = state.currentStepIndex + 1)
        }
    }

    fun confirmCurrentStep(state: TutorialSessionState): TutorialSessionState? {
        return if (state.isLastStep) {
            null
        } else {
            state.copy(currentStepIndex = state.currentStepIndex + 1)
        }
    }

    fun skip(): TutorialSessionState? = null

    fun formatCount(value: Int): String {
        return String.format(Locale.US, "%d", value)
    }

    fun formatStepNumbers(stepNumber: Int, totalSteps: Int): Pair<String, String> {
        return formatCount(stepNumber) to formatCount(totalSteps)
    }
}
