package top.rookiestwo.maimai_dialogue.speaker;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import top.rookiestwo.maimai_dialogue.speaker.resource.SpeakerResourceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpeakerDefinitionTest {
    @Test
    void decodesNonBlankSpeakerName() {
        SpeakerDefinition definition = SpeakerDefinition.CODEC.parse(
                JsonOps.INSTANCE,
                JsonParser.parseString("""
                        {
                          "name": "Guide"
                        }
                        """)
        ).getOrThrow(AssertionError::new);

        assertEquals("Guide", definition.name());
    }

    @Test
    void rejectsBlankSpeakerName() {
        var result = SpeakerDefinition.CODEC.parse(
                JsonOps.INSTANCE,
                JsonParser.parseString("""
                        {
                          "name": "  "
                        }
                        """)
        );

        assertTrue(result.error().isPresent());
    }

    @Test
    void derivesSpeakerIdFromResourcePath() {
        ResourceLocation resourceId = ResourceLocation.parse(
                "maimai_dialogue:speakers/demo/guide.json"
        );

        assertEquals(
                ResourceLocation.parse("maimai_dialogue:demo/guide"),
                SpeakerResourceLoader.toSpeakerId(resourceId)
        );
    }
}
