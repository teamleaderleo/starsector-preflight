package dev.starsector.preflight.agent;

import java.util.Locale;

/** Selects the independently rollbackable TextureLoader cache consumer. */
public enum TextureAdapterMode {
    COMPATIBILITY,
    PREPARED_PIXELS;

    static TextureAdapterMode parse(String value) {
        if (value == null || value.isBlank()) {
            return COMPATIBILITY;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "compatibility", "decoded-image", "image" -> COMPATIBILITY;
            case "prepared-pixels", "pixels", "upload-ready" -> PREPARED_PIXELS;
            default -> throw new IllegalArgumentException("Unknown texture adapter mode: " + value);
        };
    }

    public String optionValue() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
