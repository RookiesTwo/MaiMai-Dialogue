package top.rookiestwo.maimai_dialogue.dialogue;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Resolves a Dialogue Presentation reference into an inline Presentation.
 */
public final class PresentationResolver {
    private PresentationResolver() {
    }

    public static Result resolve(
            Presentation presentation,
            Function<ResourceLocation, Optional<PresentationDefinition>> lookup
    ) {
        Objects.requireNonNull(presentation, "presentation");
        Objects.requireNonNull(lookup, "lookup");

        if (!presentation.referencesDefinition()) {
            return new Result(presentation, List.of());
        }

        ResourceLocation definitionId = presentation.reference()
                .orElseThrow();
        Optional<PresentationDefinition> definition = lookup.apply(
                definitionId
        );
        if (definition.isEmpty()) {
            return new Result(
                    new Presentation(Presentation.DEFAULT_THEME_ID),
                    List.of("Dialogue references missing Presentation "
                            + definitionId + ".")
            );
        }

        Presentation resolved = definition.orElseThrow().presentation();
        if (resolved.referencesDefinition()) {
            return new Result(
                    new Presentation(Presentation.DEFAULT_THEME_ID),
                    List.of("Presentation " + definitionId
                            + " cannot reference another Presentation.")
            );
        }
        return new Result(resolved, List.of());
    }

    public record Result(
            Presentation presentation,
            List<String> errors
    ) {
        public Result {
            Objects.requireNonNull(presentation, "presentation");
            Objects.requireNonNull(errors, "errors");
            errors = List.copyOf(errors);
        }
    }
}
