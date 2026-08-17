package top.rookiestwo.maimai_dialogue.api;

public enum DialogueOpenResult {
    SENT,
    DIALOGUE_NOT_FOUND,
    REQUIREMENTS_NOT_MET,
    PROGRESS_UNAVAILABLE,
    PENDING_DIALOGUE_CONFLICT,
    PERSISTENCE_FAILED,
    INTERNAL_ERROR
}
