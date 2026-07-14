package dev.starsector.preflight.cli;

import java.util.Locale;

enum Platform {
    MAC,
    LINUX,
    WINDOWS,
    OTHER;

    static Platform current() {
        return from(System.getProperty("os.name", ""));
    }

    static Platform from(String osName) {
        String value = osName.toLowerCase(Locale.ROOT);
        if (value.contains("mac") || value.contains("darwin")) {
            return MAC;
        }
        if (value.contains("win")) {
            return WINDOWS;
        }
        if (value.contains("linux") || value.contains("unix")) {
            return LINUX;
        }
        return OTHER;
    }
}
