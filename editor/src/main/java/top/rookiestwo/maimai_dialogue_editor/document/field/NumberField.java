package top.rookiestwo.maimai_dialogue_editor.document.field;

/** Data constraints only. Parsing and omission semantics belong to the owning resource. */
public record NumberField(String name, float fallback, float minimum, float maximum, boolean integer) {
    public NumberField(String name, float fallback, float minimum, float maximum) {
        this(name, fallback, minimum, maximum, false);
    }
}
