package dev.starsector.preflight.cli;

import dev.starsector.preflight.agent.AdapterMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Locale;

final class AgentInjection {
    private AgentInjection() {
    }

    static String append(String existing, Path agentJar, Path destination) {
        return append(existing, agentJar, destination, AdapterMode.OFF, null, null, null, null, null);
    }

    static String append(
            String existing,
            Path agentJar,
            Path destination,
            AdapterMode adapterMode,
            Path adapterReport,
            Path adapterTargets) {
        return append(
                existing,
                agentJar,
                destination,
                adapterMode,
                adapterReport,
                adapterTargets,
                null,
                null,
                null);
    }

    static String append(
            String existing,
            Path agentJar,
            Path destination,
            AdapterMode adapterMode,
            Path adapterReport,
            Path adapterTargets,
            Path textureCacheRoot,
            Path textureManifest,
            Path resourceIndex) {
        String current = existing == null ? "" : existing.trim();
        String lower = current.toLowerCase(Locale.ROOT);
        if (lower.contains("-javaagent:") && lower.contains("preflight")) {
            throw new IllegalArgumentException("JAVA_TOOL_OPTIONS already contains a Preflight javaagent");
        }

        StringBuilder arguments = new StringBuilder("dest64=")
                .append(encodedPath(destination))
                .append(",adapter=")
                .append(adapterMode.optionValue());
        appendPath(arguments, "adapterReport64", adapterReport);
        appendPath(arguments, "targets64", adapterTargets);
        appendPath(arguments, "textureCache64", textureCacheRoot);
        appendPath(arguments, "textureManifest64", textureManifest);
        appendPath(arguments, "resourceIndex64", resourceIndex);
        String option = "-javaagent:"
                + quoteJvmOptionValue(agentJar.toAbsolutePath().normalize().toString())
                + "="
                + arguments;
        return current.isEmpty() ? option : current + " " + option;
    }

    private static void appendPath(StringBuilder arguments, String key, Path path) {
        if (path != null) {
            arguments.append(',').append(key).append('=').append(encodedPath(path));
        }
    }

    private static String encodedPath(Path value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.toAbsolutePath().normalize().toString().getBytes(StandardCharsets.UTF_8));
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
