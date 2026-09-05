package top.rookiestwo.maimai_dialogue.presentation;

import com.mojang.serialization.Codec;

import java.util.Objects;

/**
 * Reusable complete Presentation loaded from the client Resource Pack.
 */
public record PresentationDefinition(Presentation presentation) {
    public static final Codec<PresentationDefinition> CODEC =
            Presentation.INLINE_CODEC.xmap(
                    PresentationDefinition::new,
                    PresentationDefinition::presentation
            );

    public PresentationDefinition {
        Objects.requireNonNull(presentation, "presentation");
        if (presentation.referencesDefinition()) {
            throw new IllegalArgumentException(
                    "PresentationDefinition cannot reference another PresentationDefinition."
            );
        }
    }
}
