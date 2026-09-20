package top.rookiestwo.maimai_dialogue_editor.resource;

/** Authoring categories. Availability is independent of the runtime definition registry. */
public enum ResourceKind {
    DIALOGUE("dialogues", "dialogue", true),
    SPEAKER("speakers", "speaker", true),
    PRESENTATION("presentations", "presentation", false),
    SCENE("scenes", "scene", true),
    VISUAL_ASSET("visual_assets", "visual_asset", true),
    ACTION("actions", "action", false),
    THEME("themes", "theme", false),
    IMAGE("images", "image", true),
    SOUND("sounds", "sound", true);

    private final String directory;
    private final String key;
    private final boolean available;

    ResourceKind(String directory, String key, boolean available) {
        this.directory = directory;
        this.key = key;
        this.available = available;
    }

    public String directory() { return directory; }
    public String key() { return key; }
    public boolean available() { return available; }
    public boolean material() { return this == IMAGE || this == SOUND; }
    public boolean creatable() { return available && !material(); }
}
