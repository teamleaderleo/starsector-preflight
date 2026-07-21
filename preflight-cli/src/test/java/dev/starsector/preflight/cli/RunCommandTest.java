package dev.starsector.preflight.cli;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RunCommandTest {
    @Test
    void defaultRunDirectoriesRemainDistinctWithinOneMillisecond() {
        Path home = Path.of("synthetic-home");
        Instant started = Instant.parse("2026-07-21T10:42:03.123Z");

        Path first = RunCommand.defaultRunDirectory(home, started, "aaaaaaaa");
        Path second = RunCommand.defaultRunDirectory(home, started, "bbbbbbbb");

        assertNotEquals(first, second);
        assertTrue(first.getFileName().toString().startsWith("20260721-104203-123-"));
        assertTrue(second.getFileName().toString().startsWith("20260721-104203-123-"));
    }

    @Test
    void defaultRunDirectoryRejectsUnsafeNonceText() {
        assertThrows(IllegalArgumentException.class, () -> RunCommand.defaultRunDirectory(
                Path.of("synthetic-home"),
                Instant.EPOCH,
                "../escape"));
    }
}
