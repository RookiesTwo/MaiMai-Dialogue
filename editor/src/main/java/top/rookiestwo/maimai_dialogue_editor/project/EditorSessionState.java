package top.rookiestwo.maimai_dialogue_editor.project;

import top.rookiestwo.maimai_dialogue_editor.resource.ResourceKey;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceKind;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceTree;

import java.util.List;
import java.util.Set;

/** Local usage preferences, deliberately separate from project content, history and exported packs. */
public record EditorSessionState(int version, Layout layout, Navigation navigation, Preview preview) {
    public static final int VERSION = 1;
    public EditorSessionState {
        if (version != VERSION) throw new IllegalArgumentException("Unsupported editor state version");
        layout = layout == null ? Layout.defaults() : layout;
        navigation = navigation == null ? Navigation.defaults() : navigation;
        preview = preview == null ? Preview.defaults() : preview;
    }
    public static EditorSessionState defaults() {
        return new EditorSessionState(VERSION, null, null, null);
    }

    public record Layout(double leftFraction, double rightFraction, double actionsDp,
                         boolean leftCollapsed, boolean rightCollapsed, Set<String> collapsedSections) {
        public Layout {
            leftFraction = bounded(leftFraction, .20, .01, .95);
            rightFraction = bounded(rightFraction, .24, .01, .95);
            actionsDp = bounded(actionsDp, 160, 1, 10000);
            collapsedSections = collapsedSections == null ? Set.of() : Set.copyOf(collapsedSections);
        }
        public static Layout defaults() { return new Layout(.20, .24, 160, false, false, Set.of()); }
        private static double bounded(double value, double fallback, double min, double max) {
            return Double.isFinite(value) && value >= min && value <= max ? value : fallback;
        }
    }

    public record Navigation(ResourceTree.Node selection, ResourceKey opened, Set<ResourceTree.Node> collapsed,
                             Set<ResourceKey> expandedDialogues, String query) {
        public Navigation {
            selection = validNode(selection) ? selection : ResourceTree.Node.project();
            collapsed = collapsed == null ? Set.of() : collapsed.stream().filter(EditorSessionState::validNode)
                    .filter(node -> switch (node.type()) { case PROJECT, CATEGORY, FOLDER -> true; default -> false; })
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            expandedDialogues = expandedDialogues == null ? Set.of() : expandedDialogues.stream()
                    .filter(key -> key != null && key.kind() == ResourceKind.DIALOGUE)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            query = query == null ? "" : query;
        }
        public static Navigation defaults() { return new Navigation(null, null, null, null, ""); }
    }

    public record Choice(ResourceKey resource, String value) {
        public Choice { java.util.Objects.requireNonNull(resource); java.util.Objects.requireNonNull(value); }
    }
    public record ActionPreview(ResourceKey resource, String scene, String target) {
        public ActionPreview {
            java.util.Objects.requireNonNull(resource);
            scene = scene == null ? "" : scene;
            target = target == null ? "dialogue" : target;
        }
    }
    public record Preview(int themeExample, List<Choice> sceneObjects, List<Choice> materialVariants,
                          List<ActionPreview> actions) {
        public Preview {
            themeExample = Math.clamp(themeExample, 0, 2);
            sceneObjects = sceneObjects == null ? List.of() : List.copyOf(sceneObjects);
            materialVariants = materialVariants == null ? List.of() : List.copyOf(materialVariants);
            actions = actions == null ? List.of() : List.copyOf(actions);
        }
        public static Preview defaults() { return new Preview(0, null, null, null); }
    }

    private static boolean validNode(ResourceTree.Node node) {
        if (node == null || node.type() == null || node.path() == null) return false;
        return switch (node.type()) {
            case PROJECT -> node.kind() == null && node.path().isEmpty();
            case CATEGORY -> node.kind() != null && node.path().isEmpty();
            case FOLDER, RESOURCE -> node.kind() != null && !node.path().isEmpty();
            case STEP -> node.kind() == ResourceKind.DIALOGUE && node.stepIndex() >= 0;
            case END -> node.kind() == ResourceKind.DIALOGUE && node.stepIndex() == -1;
        };
    }
}
