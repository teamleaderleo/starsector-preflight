package dev.starsector.preflight.core;

import java.lang.reflect.Array;
import java.util.Iterator;
import java.util.Map;

/** Minimal JSON serialization for deterministic diagnostic output. */
public final class Json {
    private Json() {
    }

    public static String object(Map<String, ?> values) {
        StringBuilder output = new StringBuilder("{");
        Iterator<? extends Map.Entry<String, ?>> iterator = values.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, ?> entry = iterator.next();
            output.append(quote(entry.getKey())).append(':').append(value(entry.getValue()));
            if (iterator.hasNext()) {
                output.append(',');
            }
        }
        return output.append('}').toString();
    }

    public static String value(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof CharSequence || value instanceof java.nio.file.Path
                || value instanceof java.time.temporal.TemporalAccessor || value instanceof Enum<?>) {
            return quote(value.toString());
        }
        if (value instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, ?> stringMap = (Map<String, ?>) map;
            return object(stringMap);
        }
        if (value instanceof Iterable<?> iterable) {
            return iterable(iterable);
        }
        if (value.getClass().isArray()) {
            StringBuilder output = new StringBuilder("[");
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                if (i > 0) {
                    output.append(',');
                }
                output.append(value(Array.get(value, i)));
            }
            return output.append(']').toString();
        }
        return quote(value.toString());
    }

    private static String iterable(Iterable<?> values) {
        StringBuilder output = new StringBuilder("[");
        Iterator<?> iterator = values.iterator();
        while (iterator.hasNext()) {
            output.append(value(iterator.next()));
            if (iterator.hasNext()) {
                output.append(',');
            }
        }
        return output.append(']').toString();
    }

    public static String quote(String text) {
        StringBuilder output = new StringBuilder(text.length() + 2).append('"');
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
                    if (c < 0x20) {
                        output.append(String.format("\\u%04x", (int) c));
                    } else {
                        output.append(c);
                    }
                }
            }
        }
        return output.append('"').toString();
    }
}
