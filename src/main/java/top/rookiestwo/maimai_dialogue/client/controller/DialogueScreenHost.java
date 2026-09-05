package top.rookiestwo.maimai_dialogue.client.controller;

@FunctionalInterface
public interface DialogueScreenHost {
    DialogueScreenHandle create(DialogueUiActions actions);
}
