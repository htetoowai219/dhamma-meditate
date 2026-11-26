package com.dhammameditate.ui.tutorial

import com.dhammameditate.R

object TutorialRegistry {

    val addAudio: TutorialDefinition = TutorialDefinition(
        id = TutorialId.ADD_AUDIO,
        titleRes = R.string.tutorial_add_audio_title,
        descriptionRes = R.string.tutorial_add_audio_description,
        startRoute = TutorialStartRoute.HOME_SESSIONS,
        steps = listOf(
            TutorialStep(
                targetId = TutorialTargetId.NAV_LIBRARY_TAB,
                titleRes = R.string.tutorial_add_audio_step1_title,
                bodyRes = R.string.tutorial_add_audio_step1_body,
                tapHintRes = R.string.tutorial_add_audio_step1_hint,
                interaction = TutorialStepInteraction.TAP_TARGET
            ),
            TutorialStep(
                targetId = TutorialTargetId.LIBRARY_IMPORT_BUTTON,
                titleRes = R.string.tutorial_add_audio_step2_title,
                bodyRes = R.string.tutorial_add_audio_step2_body,
                tapHintRes = R.string.tutorial_add_audio_step2_hint,
                interaction = TutorialStepInteraction.CONFIRM_ONLY
            )
        )
    )

    val createSession: TutorialDefinition = TutorialDefinition(
        id = TutorialId.CREATE_SESSION,
        titleRes = R.string.tutorial_create_session_title,
        descriptionRes = R.string.tutorial_create_session_description,
        startRoute = TutorialStartRoute.HOME_SESSIONS,
        steps = listOf(
            TutorialStep(
                targetId = TutorialTargetId.SESSIONS_NEW_BUTTON,
                titleRes = R.string.tutorial_create_session_step1_title,
                bodyRes = R.string.tutorial_create_session_step1_body,
                tapHintRes = R.string.tutorial_create_session_step1_hint,
                interaction = TutorialStepInteraction.TAP_TARGET
            ),
            TutorialStep(
                targetId = TutorialTargetId.EDITOR_INTRO_CARD,
                titleRes = R.string.tutorial_create_session_step2_title,
                bodyRes = R.string.tutorial_create_session_step2_body,
                tapHintRes = R.string.tutorial_hint_next,
                interaction = TutorialStepInteraction.CONFIRM_ONLY
            ),
            TutorialStep(
                targetId = TutorialTargetId.EDITOR_MAIN_CARD,
                titleRes = R.string.tutorial_create_session_step3_title,
                bodyRes = R.string.tutorial_create_session_step3_body,
                tapHintRes = R.string.tutorial_hint_next,
                interaction = TutorialStepInteraction.CONFIRM_ONLY,
                emptyLibraryNoteRes = R.string.tutorial_create_session_empty_library_note
            ),
            TutorialStep(
                targetId = TutorialTargetId.EDITOR_TIMER_CARD,
                titleRes = R.string.tutorial_create_session_step4_title,
                bodyRes = R.string.tutorial_create_session_step4_body,
                tapHintRes = R.string.tutorial_hint_next,
                interaction = TutorialStepInteraction.CONFIRM_ONLY
            ),
            TutorialStep(
                targetId = TutorialTargetId.EDITOR_SAVE_BUTTON,
                titleRes = R.string.tutorial_create_session_step5_title,
                bodyRes = R.string.tutorial_create_session_step5_body,
                tapHintRes = R.string.tutorial_create_session_step5_hint,
                interaction = TutorialStepInteraction.CONFIRM_ONLY
            )
        )
    )

    val all: List<TutorialDefinition> = listOf(
        addAudio,
        createSession
    )

    fun findById(id: TutorialId): TutorialDefinition? {
        return all.firstOrNull { it.id == id }
    }
}
