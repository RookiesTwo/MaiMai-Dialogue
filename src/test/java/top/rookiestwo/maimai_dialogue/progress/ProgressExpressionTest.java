package top.rookiestwo.maimai_dialogue.progress;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressExpressionTest {
    @Test
    void appliesNotAndOrPrecedence() {
        ProgressExpression expression = ProgressExpression.parseOrThrow(
                "quest.started || quest.ready && !quest.blocked"
        );

        assertTrue(expression.evaluate(Set.of(new ProgressNode("quest.started"))));
        assertTrue(expression.evaluate(Set.of(new ProgressNode("quest.ready"))));
        assertFalse(expression.evaluate(Set.of(
                new ProgressNode("quest.ready"),
                new ProgressNode("quest.blocked")
        )));
    }

    @Test
    void supportsNestedParenthesesAndWhitespace() {
        ProgressExpression expression = ProgressExpression.parseOrThrow(
                "  (quest.started || quest.ready) && !quest.finished "
        );

        assertTrue(expression.evaluate(Set.of(new ProgressNode("quest.ready"))));
        assertFalse(expression.evaluate(Set.of(
                new ProgressNode("quest.ready"),
                new ProgressNode("quest.finished")
        )));
    }

    @Test
    void evaluationShortCircuits() {
        ProgressExpression expression = ProgressExpression.parseOrThrow(
                "quest.started || quest.unreachable"
        );
        AtomicInteger queriedNodes = new AtomicInteger();

        boolean result = expression.evaluate(node -> {
            queriedNodes.incrementAndGet();
            if (node.value().equals("quest.unreachable")) {
                throw new AssertionError("OR right side should have short-circuited.");
            }
            return node.value().equals("quest.started");
        });

        assertTrue(result);
        assertEquals(1, queriedNodes.get());
    }

    @Test
    void rejectsMalformedExpressions() {
        assertTrue(ProgressExpression.parse("").error().isPresent());
        assertTrue(ProgressExpression.parse("quest.started &&").error().isPresent());
        assertTrue(ProgressExpression.parse("(quest.started").error().isPresent());
        assertTrue(ProgressExpression.parse("quest.started | quest.ready").error().isPresent());
        assertTrue(ProgressExpression.parse("Quest.started").error().isPresent());
    }
}
