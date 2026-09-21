package top.rookiestwo.maimai_dialogue_editor.document;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import top.rookiestwo.maimai_dialogue.presentation.filter.SceneColor;
import top.rookiestwo.maimai_dialogue.theme.*;
import java.util.*;

// 字段清单同时供属性面板、草稿校验和临时预览使用；默认值来自主 MOD。
public final class ThemeFields {
    private ThemeFields() {}
    public record Field(String group, String name, String fallback, boolean color, int minimum, int maximum) {
        public String path() { return group + "." + name; }
        public String label() { return "theme." + path(); }
        public SceneWorkspace.NumberField number() {
            return new SceneWorkspace.NumberField(name, Integer.parseInt(fallback), minimum, maximum, true);
        }
        public JsonPrimitive parse(String raw) {
            if (raw.isBlank()) return null;
            if (color) {
                SceneColor.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive(raw)).getOrThrow();
                return new JsonPrimitive(raw);
            }
            int number = Integer.parseInt(raw.strip());
            if (number < minimum || number > maximum) throw new IllegalArgumentException("Expected " + minimum + ".." + maximum);
            return new JsonPrimitive(number);
        }
        public String error() { return color ? "scene.invalid_color" : "theme.invalid_number"; }
    }
    private static Field color(String group, String name, SceneColor fallback) {
        return new Field(group, name, fallback.serialized(), true, 0, 0);
    }
    private static Field number(String group, String name, int fallback, int minimum, int maximum) {
        return new Field(group, name, Integer.toString(fallback), false, minimum, maximum);
    }
    private static final ThemeDefinition D = ThemeDefinition.DEFAULT;
    public static final List<String> GROUPS = List.of("box", "text", "option", "spacing", "controls");
    public static final List<Field> ALL = List.of(
            color("box", "background", D.box().background()), color("box", "border", D.box().border()),
            color("box", "divider", D.box().divider()), number("box", "corner_radius", D.box().cornerRadiusDp(), 0, 64),
            number("box", "border_width", D.box().borderWidthDp(), 0, 64),
            color("text", "primary", D.text().primary()), color("text", "error", D.text().error()),
            number("text", "speaker_size", D.text().speakerSizeSp(), 8, 64), number("text", "dialogue_size", D.text().dialogueSizeSp(), 8, 64),
            number("text", "option_size", D.text().optionSizeSp(), 8, 64), number("text", "auxiliary_size", D.text().auxiliarySizeSp(), 8, 64),
            color("option", "background", D.option().background()), color("option", "hover_background", D.option().hoverBackground()),
            color("option", "pressed_background", D.option().pressedBackground()), color("option", "border", D.option().border()),
            color("option", "hover_border", D.option().hoverBorder()), number("option", "border_width", D.option().borderWidthDp(), 0, 64),
            number("option", "corner_radius", D.option().cornerRadiusDp(), 0, 64),
            number("option", "horizontal_padding", D.option().horizontalPaddingDp(), 0, 64),
            number("option", "vertical_padding", D.option().verticalPaddingDp(), 0, 64), number("option", "spacing", D.option().spacingDp(), 0, 64),
            number("spacing", "header_horizontal", D.spacing().headerHorizontalDp(), 0, 64),
            number("spacing", "header_vertical", D.spacing().headerVerticalDp(), 0, 64),
            number("spacing", "content_horizontal", D.spacing().contentHorizontalDp(), 0, 64),
            number("spacing", "content_vertical", D.spacing().contentVerticalDp(), 0, 64),
            number("spacing", "options_padding", D.spacing().optionsPaddingDp(), 0, 64),
            number("spacing", "options_collapsed_limit", D.spacing().optionsCollapsedLimit(), 1, 32),
            number("spacing", "options_expanded_limit", D.spacing().optionsExpandedLimit(), 1, 32),
            color("controls", "icon", D.controls().icon()), color("controls", "scrollbar_thumb", D.controls().scrollbarThumb()),
            color("controls", "scrollbar_track", D.controls().scrollbarTrack()),
            number("controls", "scrollbar_width", D.controls().scrollbarWidthDp(), 1, 64));

    public static Map<String, String> errors(JsonObject data) {
        var errors = new LinkedHashMap<String, String>();
        for (String group : GROUPS) if (data.has(group) && !data.get(group).isJsonObject()) errors.put(group, "edit.invalid_object");
        for (var field : ALL) {
            if (!(data.get(field.group()) instanceof JsonObject group) || !group.has(field.name())) continue;
            JsonElement value = group.get(field.name());
            try {
                if (!value.isJsonPrimitive() || (field.color() ? !value.getAsJsonPrimitive().isString() : !value.getAsJsonPrimitive().isNumber()))
                    throw new IllegalArgumentException();
                if (field.parse(value.getAsString()) == null) throw new IllegalArgumentException();
            } catch (RuntimeException invalid) { errors.put(field.path(), field.error()); }
        }
        return errors;
    }

    // 只复制变化的配置组，不解析资源、不修改共享 Theme。
    public static ThemeDefinition apply(ThemeDefinition theme, Field field, JsonPrimitive value) {
        return new Change(field, value == null ? new JsonPrimitive(field.fallback()) : value).apply(theme);
    }
    private record Change(Field field, JsonPrimitive value) {
        int n(String name, int old) { return field.name().equals(name) ? value.getAsInt() : old; }
        SceneColor c(String name, SceneColor old) {
            return field.name().equals(name) ? SceneColor.CODEC.parse(JsonOps.INSTANCE, value).getOrThrow() : old;
        }
        ThemeDefinition apply(ThemeDefinition theme) {
            var b = theme.box(); var t = theme.text(); var o = theme.option(); var s = theme.spacing(); var c = theme.controls();
            switch (field.group()) {
                case "box" -> b = new DialogueBoxTheme(c("background", b.background()), c("border", b.border()), c("divider", b.divider()),
                        n("corner_radius", b.cornerRadiusDp()), n("border_width", b.borderWidthDp()));
                case "text" -> t = new ThemeText(c("primary", t.primary()), c("error", t.error()), n("speaker_size", t.speakerSizeSp()),
                        n("dialogue_size", t.dialogueSizeSp()), n("option_size", t.optionSizeSp()), n("auxiliary_size", t.auxiliarySizeSp()));
                case "option" -> o = new ThemeOption(c("background", o.background()), c("hover_background", o.hoverBackground()),
                        c("pressed_background", o.pressedBackground()), c("border", o.border()), c("hover_border", o.hoverBorder()),
                        n("border_width", o.borderWidthDp()), n("corner_radius", o.cornerRadiusDp()), n("horizontal_padding", o.horizontalPaddingDp()),
                        n("vertical_padding", o.verticalPaddingDp()), n("spacing", o.spacingDp()));
                case "spacing" -> s = new ThemeSpacing(n("header_horizontal", s.headerHorizontalDp()), n("header_vertical", s.headerVerticalDp()),
                        n("content_horizontal", s.contentHorizontalDp()), n("content_vertical", s.contentVerticalDp()), n("options_padding", s.optionsPaddingDp()),
                        n("options_collapsed_limit", s.optionsCollapsedLimit()), n("options_expanded_limit", s.optionsExpandedLimit()));
                case "controls" -> c = new ThemeControls(c("icon", c.icon()), c("scrollbar_thumb", c.scrollbarThumb()),
                        c("scrollbar_track", c.scrollbarTrack()), n("scrollbar_width", c.scrollbarWidthDp()));
                default -> throw new IllegalArgumentException(field.group());
            }
            return new ThemeDefinition(b, t, o, s, c);
        }
    }
}
