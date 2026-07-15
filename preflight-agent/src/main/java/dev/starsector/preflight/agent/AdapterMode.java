package dev.starsector.preflight.agent;

import java.util.Locale;

/** Activation level for the optional vanilla runtime adapter. */
public enum AdapterMode {
    OFF,
    PROBE,
    ENABLED;

    static AdapterMode parse(String value) {
        if (value == null || value.isBlank()) {
            return OFF;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "off", "disabled", "false", "0" -> OFF;
            case "probe", "inspect" -> PROBE;
            case "on", "enabled", "true", "1" -> ENABLED;
            default -> throw new IllegalArgumentException("Unknown adapter mode: " + value);
        };
    }

    public String optionValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}