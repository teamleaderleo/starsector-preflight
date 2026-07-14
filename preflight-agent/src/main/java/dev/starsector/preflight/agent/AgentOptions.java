package dev.starsector.preflight.agent;

import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

record AgentOptions(Path destination, String settings) {
    static AgentOptions parse(String raw) {
        Map<String, String> values = new HashMap<>();
        if (raw != null && !raw.isBlank()) {
            for (String token : raw.split(",")) {
                String trimmed = token.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                int separator = trimmed.indexOf('=');
                if (separator <= 0 || separator == trimmed.length() - 1) {
                    throw new IllegalArgumentException("Invalid agent option: " + trimmed);
                }
                values.put(trimmed.substring(0, separator).trim(), trimmed.substring(separator + 1).trim());
            }
        }

        String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now()).replace(':', '-');
        Path destination = Path.of(values.getOrDefault("dest", "preflight-startup-" + timestamp + ".jfr"));
        String settings = values.getOrDefault("settings", "profile");
        return new AgentOptions(destination, settings);
    }
}
