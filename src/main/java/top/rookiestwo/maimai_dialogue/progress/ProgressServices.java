package top.rookiestwo.maimai_dialogue.progress;

public final class ProgressServices {
    private static final PlayerProgressRepository REPOSITORY =
            new PlayerProgressRepository();

    private ProgressServices() {
    }

    public static PlayerProgressRepository repository() {
        return REPOSITORY;
    }
}
