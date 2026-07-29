package top.rookiestwo.maimai_dialogue.progress;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressNodeTest {
    @Test
    void acceptsLowercaseDotSeparatedNodes() {
        ProgressNode node = new ProgressNode("quest.trader.level_1");

        assertEquals("quest.trader.level_1", node.value());
        assertEquals("quest.trader.level_1", node.toString());
    }

    @Test
    void rejectsInvalidNodes() {
        assertThrows(IllegalArgumentException.class, () -> new ProgressNode(""));
        assertThrows(IllegalArgumentException.class, () -> new ProgressNode("Quest.Started"));
        assertThrows(IllegalArgumentException.class, () -> new ProgressNode(".quest"));
        assertThrows(IllegalArgumentException.class, () -> new ProgressNode("quest..started"));
        assertThrows(IllegalArgumentException.class, () -> new ProgressNode("quest:started"));
    }

    @Test
    void codecReportsInvalidNodesWithoutThrowing() {
        assertTrue(ProgressNode.parse("quest..started").error().isPresent());
    }
}
