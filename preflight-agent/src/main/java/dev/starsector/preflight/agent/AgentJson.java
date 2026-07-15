package dev.starsector.preflight.agent;

import java.nio.file.Path;
import java.time.temporal.TemporalAccessor;
import java.util.List;
import java.util.Map;

/** Small dependency-free JSON serializer for bounded agent diagnostics. */
final class AgentJson {
    private AgentJson() {
    }

    static String object(Map<String, ?> values) {
        StringBuilder output = new StringBuilder(16_384);
        writeObject(output, values);
        return output.toString();
    }

    private static void writeValue(StringBuilder output, Object value) {
        if (value == null) {
            output.append("null");
        } else if (value instanceof String text) {
            string(output, text);
        } else if (value instanceof Number || value instanceof Boolean) {
            output.append(value);
        } else if (value instanceof Path || value instanceof TemporalAccessor || value instanceof Enum<?>) {
            string(output, value.toString());
        } else if (value instanceof Map<?, ?> map) {
            writeObject(output, map);
        } else if (value instanceof List<?> list) {
            writeArray(output, list);
        } else {
            string(output, value.toString());
        }
    }

    private static void writeObject(StringBuilder output, Map<?, ?> values) {
        output.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IllegalArgumentException("Agent JSON object keys must be strings");
            }
            if (!first) output.append(',');
            first = false;
            string(output, key);
            output.append(':');
            writeValue(output, entry.getValue());
        }
        output.append('}');
    }

    private static void writeArray(StringBuilder output, List<?> values) {
        output.append('[');
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) output.append(',');
            writeValue(output, values.get(i));
        }
        output.append(']');
    }

    private static void string(StringBuilder output, String value) {
        output.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
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
                        output.append("\\u");
                        String hex = Integer.toHexString(c);
                        output.append("0".repeat(4 - hex.length())).append(hex);
                    } else {
                        output.append(c);
                    }
                }
            }
        }
        output.append('"');
    }
}
