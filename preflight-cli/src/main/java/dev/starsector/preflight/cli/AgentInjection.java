package dev.starsector.preflight.cli;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Locale;

final class AgentInjection {
    private AgentInjection() {
    }

    static String append(String existing, Path agentJar, Path destination) {
        String current = existing == null ? "" : existing.trim();
        String lower = current.toLowerCase(Locale.ROOT);
        if (lower.contains("-javaagent:") && lower.contains("preflight")) {
            throw new IllegalArgumentException("JAVA_TOOL_OPTIONS already contains a Preflight javaagent");
        }

        String encodedDestination = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(destination.toAbsolutePath().normalize().toString().getBytes(StandardCharsets.UTF_8));
        String option = "-javaagent:"
                + quoteJvmOptionValue(agentJar.toAbsolutePath().normalize().toString())
                + "=dest64="
                + encodedDestination;
        return current.isEmpty() ? option : current + " " + option;
    }

    static String quoteJvmOptionValue(String value) {
        if (value.indexOf('"') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("Unsupported character in javaagent path: " + value);
        }
        if (value.chars().anyMatch(Character::isWhitespace)) {
            return '"' + value + '"';
        }
        return value;
    }
}
