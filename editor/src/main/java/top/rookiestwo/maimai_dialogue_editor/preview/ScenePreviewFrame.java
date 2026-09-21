package top.rookiestwo.maimai_dialogue_editor.preview;

import top.rookiestwo.maimai_dialogue.client.scene.*;
import top.rookiestwo.maimai_dialogue.presentation.DialogueBoxLayout;
import top.rookiestwo.maimai_dialogue.presentation.filter.*;
import top.rookiestwo.maimai_dialogue.presentation.scene.SceneDefinition;
import top.rookiestwo.maimai_dialogue_editor.document.SceneWorkspace.NumberPreview;
import java.util.Optional;

/** Small immutable runtime values for the current preview instance; no Codec, resource lookup or IO. */
public record ScenePreviewFrame(SceneState state, DialogueBoxLayout layout, Optional<SceneFilter> filter) {
    public static ScenePreviewFrame initial(SceneDefinition scene) {
        return new ScenePreviewFrame(SceneState.initial(scene), scene.dialogueBox(), scene.filter());
    }
    public ScenePreviewFrame withState(SceneState next) { return new ScenePreviewFrame(next, layout, filter); }
    public ScenePreviewFrame withNumber(NumberPreview edit) {
        return switch (edit.part()) {
            case OBJECT -> {
                var object = state.objects().get(edit.objectId());
                if (object == null) yield this;
                var next = object.withAnimated(value(edit, "x", object.x()), value(edit, "y", object.y()),
                        value(edit, "scale", object.scale()), value(edit, "opacity", object.opacity()), object.variant(), object.visible())
                        .withAxisScale(value(edit, "scale_x", object.scaleX()), value(edit, "scale_y", object.scaleY()));
                yield withState(state.with(edit.objectId(), next));
            }
            case BACKGROUND -> state.background().map(background -> withState(state.withBackground(new SceneBackgroundState(
                    background.variants(), background.variant(), background.fit(), value(edit, "opacity", background.opacity()))))).orElse(this);
            case BOX -> {
                var next = new DialogueBoxLayout(value(edit, "x", layout.x()), value(edit, "y", layout.y()),
                        value(edit, "width", layout.width()), value(edit, "max_height", layout.maxHeight()), layout.anchor());
                yield new ScenePreviewFrame(state.withDialogueBox(DialogueBoxState.initial(next, state.dialogueOpacity())), next, filter);
            }
            case FILTER -> new ScenePreviewFrame(state, layout, filter.map(current -> switch (current) {
                case ColorAdjustFilter color -> new ColorAdjustFilter(value(edit, "brightness", color.brightness()),
                        value(edit, "contrast", color.contrast()), value(edit, "saturation", color.saturation()), color.tint());
                case CrtFilter crt -> new CrtFilter(value(edit, "curvature", crt.curvature()), value(edit, "scanline_strength", crt.scanlineStrength()),
                        value(edit, "mask_strength", crt.maskStrength()), value(edit, "chromatic_aberration", crt.chromaticAberration()),
                        value(edit, "vignette", crt.vignette()), value(edit, "noise", crt.noise()), value(edit, "flicker", crt.flicker()),
                        value(edit, "bloom", crt.bloom()), value(edit, "edge_feather", crt.edgeFeather()));
            }));
        };
    }
    private static float value(NumberPreview edit, String field, float original) {
        return edit.field().equals(field) ? edit.value() : original;
    }

    /** Image bindings and draw order must match before reusing an already mounted scene. */
    public static boolean sameBindings(SceneDefinition before, SceneDefinition after) {
        if (before.background().isPresent() != after.background().isPresent()
                || !before.visualObjects().keySet().equals(after.visualObjects().keySet())) return false;
        if (before.background().isPresent()) {
            var a = before.background().orElseThrow(); var b = after.background().orElseThrow();
            if (!a.variants().equals(b.variants()) || a.sampling() != b.sampling() || !a.initialVariant().equals(b.initialVariant())) return false;
        }
        for (var entry : before.visualObjects().entrySet()) {
            var a = entry.getValue(); var b = after.visualObjects().get(entry.getKey());
            if (!a.variants().equals(b.variants()) || a.sampling() != b.sampling() || a.zIndex() != b.zIndex()
                    || !a.initialVariant().equals(b.initialVariant())) return false;
        }
        return true;
    }
}
