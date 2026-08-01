package top.rookiestwo.maimai_dialogue.client.session;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;

public sealed interface DialogueSessionEffect {
    record QueryAccess(long requestId, List<ResourceLocation> targets)
            implements DialogueSessionEffect {
        public QueryAccess {
            targets = List.copyOf(targets);
        }
    }

    record RequestTarget(long requestId, ResourceLocation target)
            implements DialogueSessionEffect {
        public RequestTarget {
            Objects.requireNonNull(target, "target");
        }
    }

    record Close() implements DialogueSessionEffect {
    }

    record ReportError(String message) implements DialogueSessionEffect {
        public ReportError {
            Objects.requireNonNull(message, "message");
        }
    }
}
