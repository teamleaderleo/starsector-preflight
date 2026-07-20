package dev.starsector.preflight.core;

import java.lang.reflect.Array;
import java.nio.file.Path;
import java.time.temporal.TemporalAccessor;
import java.util.Map;

/**
 * Minimal JSON serialization for deterministic diagnostic output.
 *
 * <p>This is the single JSON writer for every module, including report code running inside the
 * launched game JVM, so it must stay dependency-free and bounded by its callers.
 */
public final class Json {
    private Json() {
    }

    public static String object(Map<String, ?> values) {
        StringBuilder output = new StringBuilder(1_024);
        writeObject(output, values);
        return output.toString();
    }

    public static String value(Object value) {
        StringBuilder output = new StringBuilder(64);
        writeValue(output, value);
        return output.toString();
    }

    public static String quote(String text) {
        StringBuilder output = new StringBuilder(text.length() + 2);
        writeString(output, text);
        return output.toString();
    }

    private static void writeValue(StringBuilder output, Object value) {
        if (value == null) {
            output.append("null");
        } else if (value instanceof Number || value instanceof Boolean) {
            output.append(value);
        } else if (value instanceof CharSequence || value instanceof Path
                || value instanceof TemporalAccessor || value instanceof Enum<?>) {
            writeString(output, value.toString());
        } else if (value instanceof Map<?, ?> map) {
            writeObject(output, map);
        } else if (value instanceof Iterable<?> iterable) {
            writeIterable(output, iterable);
        } else if (value.getClass().isArray()) {
            writeArray(output, value);
        } else {
            writeString(output, value.toString());
        }
    }

    private static void writeObject(StringBuilder output, Map<?, ?> values) {
        output.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IllegalArgumentException("JSON object keys must be strings");
            }
            if (!first) {
                output.append(',');
            }
            first = false;
            writeString(output, key);
            output.append(':');
            writeValue(output, entry.getValue());
        }
        output.append('}');
    }

    private static void writeIterable(StringBuilder output, Iterable<?> values) {
        output.append('[');
        boolean first = true;
        for (Object value : values) {
            if (!first) {
                output.append(',');
            }
            first = false;
            writeValue(output, value);
        }
        output.append(']');
    }

    private static void writeArray(StringBuilder output, Object array) {
        output.append('[');
        int length = Array.getLength(array);
        for (int i = 0; i < length; i++) {
            if (i > 0) {
                output.append(',');
            }
            writeValue(output, Array.get(array, i));
        }
        output.append(']');
    }

    private static void writeString(StringBuilder output, String text) {
        output.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> output.append("\\\"");
                case '\\' -> output.append("\\\\");
                case '\b' -> output.append("\\b");
                case '\f' -> output.append("\\f");
                case '\n' -> output.append("\\n");
                case '\r' -> output.append("\\r");
                case '\t' -> output.append("\\t");
                default -> {
                    if (c < 0x20 || c == '\u2028' || c == '\u2029') {
                        output.append(String.format("\\u%04x", (int) c));
                    } else {
                        output.append(c);
                    }
                }
            }
        }
        output.append('"');
    }
}
