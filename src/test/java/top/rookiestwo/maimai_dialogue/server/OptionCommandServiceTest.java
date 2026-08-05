package top.rookiestwo.maimai_dialogue.server;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OptionCommandServiceTest {
    @Test
    void executesEveryCommandInOrder() {
        List<String> commands = List.of("first", "second", "third");
        List<String> executed = new ArrayList<>();

        OptionCommandService.CommandSequenceResult result =
                OptionCommandService.executeSequence(commands, command -> {
                    executed.add(command);
                    return true;
                });

        assertTrue(result.successful());
        assertEquals(-1, result.failedCommandIndex());
        assertNull(result.error());
        assertEquals(commands, executed);
    }

    @Test
    void stopsAtFirstFailedCommand() {
        List<String> executed = new ArrayList<>();

        OptionCommandService.CommandSequenceResult result =
                OptionCommandService.executeSequence(
                        List.of("first", "second", "third"),
                        command -> {
                            executed.add(command);
                            return !command.equals("second");
                        }
                );

        assertFalse(result.successful());
        assertEquals(1, result.failedCommandIndex());
        assertNull(result.error());
        assertEquals(List.of("first", "second"), executed);
    }

    @Test
    void stopsAtFirstCrashedCommand() {
        List<String> executed = new ArrayList<>();
        IllegalStateException failure = new IllegalStateException("failed");

        OptionCommandService.CommandSequenceResult result =
                OptionCommandService.executeSequence(
                        List.of("first", "second", "third"),
                        command -> {
                            executed.add(command);
                            if (command.equals("second")) {
                                throw failure;
                            }
                            return true;
                        }
                );

        assertFalse(result.successful());
        assertEquals(1, result.failedCommandIndex());
        assertSame(failure, result.error());
        assertEquals(List.of("first", "second"), executed);
    }
}
