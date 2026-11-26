package com.dhammameditate.ui.tutorial

import androidx.annotation.StringRes

enum class TutorialId {
    ADD_AUDIO,
    CREATE_SESSION
}

enum class TutorialTargetId {
    NAV_LIBRARY_TAB,
    LIBRARY_IMPORT_BUTTON,
    SESSIONS_NEW_BUTTON,
    EDITOR_INTRO_CARD,
    EDITOR_MAIN_CARD,
    EDITOR_TIMER_CARD,
    EDITOR_SAVE_BUTTON
}

enum class TutorialStartRoute {
    HOME_SESSIONS,
    HOME_LIBRARY
}

enum class TutorialStepInteraction {
    TAP_TARGET,
    CONFIRM_ONLY
}

data class TutorialStep(
    val targetId: TutorialTargetId,
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int,
    @StringRes val tapHintRes: Int?,
    val interaction: TutorialStepInteraction,
    @StringRes val emptyLibraryNoteRes: Int? = null
)

data class TutorialDefinition(
    val id: TutorialId,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val startRoute: TutorialStartRoute,
    val steps: List<TutorialStep>
) {
    init {
        require(steps.isNotEmpty()) { "TutorialDefinition must contain at least one step" }
    }
}

data class TutorialSessionState(
    val definition: TutorialDefinition,
    val currentStepIndex: Int
) {
    init {
        require(currentStepIndex in definition.steps.indices) {
            "currentStepIndex=$currentStepIndex out of bounds for ${definition.steps.size} steps"
        }
    }

    val currentStep: TutorialStep
        get() = definition.steps[currentStepIndex]

    val stepNumber: Int
        get() = currentStepIndex + 1

    val totalSteps: Int
        get() = definition.steps.size

    val isLastStep: Boolean
        get() = currentStepIndex == definition.steps.lastIndex
}
