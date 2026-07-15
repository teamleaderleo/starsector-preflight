package dev.starsector.preflight.cli;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small strict JSON parser for Preflight's own bounded reports. */
final class StrictJson {
    private static final int MAX_INPUT_CHARS = 32 * 1024 * 1024;
    private static final int MAX_DEPTH = 128;
    private static final int MAX_COLLECTION_ITEMS = 1_000_000;

    private StrictJson() {
    }

    static Object parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("JSON text is null");
        }
        if (text.length() > MAX_INPUT_CHARS) {
            throw new IllegalArgumentException("JSON input exceeds " + MAX_INPUT_CHARS + " characters");
        }
        Parser parser = new Parser(text);
        Object value = parser.value(0);
        parser.whitespace();
        if (!parser.finished()) {
            throw parser.error("Trailing content");
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> object(String text) {
        Object value = parse(text);
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Expected a JSON object");
        }
        return (Map<String, Object>) map;
    }

    private static final class Parser {
        private final String text;
        private int offset;
        private int collectionItems;

        private Parser(String text) {
            this.text = text;
        }

        private Object value(int depth) {
            if (depth > MAX_DEPTH) {
                throw error("JSON nesting exceeds " + MAX_DEPTH);
            }
            whitespace();
            if (finished()) {
                throw error("Expected a value");
            }
            return switch (peek()) {
                case '{' -> objectValue(depth + 1);
                case '[' -> arrayValue(depth + 1);
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        private Map<String, Object> objectValue(int depth) {
            expect('{');
            whitespace();
            Map<String, Object> values = new LinkedHashMap<>();
            if (consume('}')) {
                return values;
            }
            while (true) {
                whitespace();
                if (finished() || peek() != '"') {
                    throw error("Expected an object key");
                }
                String key = string();
                whitespace();
                expect(':');
                Object value = value(depth);
                if (values.containsKey(key)) {
                    throw error("Duplicate object key: " + key);
                }
                values.put(key, value);
                item();
                whitespace();
                if (consume('}')) {
                    return values;
                }
                expect(',');
            }
        }

        private List<Object> arrayValue(int depth) {
            expect('[');
            whitespace();
            List<Object> values = new ArrayList<>();
            if (consume(']')) {
                return values;
            }
            while (true) {
                values.add(value(depth));
                item();
                whitespace();
                if (consume(']')) {
                    return values;
                }
                expect(',');
            }
        }

        private String string() {
            expect('"');
            StringBuilder value = new StringBuilder();
            while (!finished()) {
                char current = text.charAt(offset++);
                if (current == '"') {
                    return value.toString();
                }
                if (current != '\\') {
                    if (current < 0x20) {
                        throw error("Unescaped control character in string");
                    }
                    value.append(current);
                    continue;
                }
                if (finished()) {
                    throw error("Unterminated string escape");
                }
                char escaped = text.charAt(offset++);
                switch (escaped) {
                    case '"', '\\', '/' -> value.append(escaped);
                    case 'b' -> value.append('\b');
                    case 'f' -> value.append('\f');
                    case 'n' -> value.append('\n');
                    case 'r' -> value.append('\r');
                    case 't' -> value.append('\t');
                    case 'u' -> value.append(unicode());
                    default -> throw error("Unsupported escape: \\" + escaped);
                }
            }
            throw error("Unterminated string");
        }

        private char unicode() {
            if (offset + 4 > text.length()) {
                throw error("Incomplete unicode escape");
            }
            try {
                char value = (char) Integer.parseInt(text.substring(offset, offset + 4), 16);
                offset += 4;
                return value;
            } catch (NumberFormatException error) {
                throw error("Invalid unicode escape");
            }
        }

        private Object literal(String expected, Object value) {
            if (!text.startsWith(expected, offset)) {
                throw error("Expected " + expected);
            }
            offset += expected.length();
            return value;
        }

        private Number number() {
            int start = offset;
            consume('-');
            if (finished()) {
                throw error("Expected a digit");
            }
            if (consume('0')) {
                if (!finished() && Character.isDigit(peek())) {
                    throw error("Leading zeros are not allowed");
                }
            } else {
                digits(true);
            }
            boolean decimal = false;
            if (consume('.')) {
                decimal = true;
                digits(true);
            }
            if (consume('e') || consume('E')) {
                decimal = true;
                if (!consume('+')) {
                    consume('-');
                }
                digits(true);
            }
            String raw = text.substring(start, offset);
            try {
                Number value = decimal ? Double.parseDouble(raw) : Long.parseLong(raw);
                if (value instanceof Double floating && !Double.isFinite(floating)) {
                    throw error("Non-finite number: " + raw);
                }
                return value;
            } catch (NumberFormatException error) {
                throw error("Invalid number: " + raw);
            }
        }

        private void digits(boolean required) {
            int start = offset;
            while (!finished() && Character.isDigit(peek())) {
                offset++;
            }
            if (required && start == offset) {
                throw error("Expected a digit");
            }
        }

        private void item() {
            collectionItems++;
            if (collectionItems > MAX_COLLECTION_ITEMS) {
                throw error("JSON collection item limit exceeded");
            }
        }

        private void whitespace() {
            while (!finished() && Character.isWhitespace(peek())) {
                offset++;
            }
        }

        private boolean consume(char expected) {
            if (!finished() && peek() == expected) {
                offset++;
                return true;
            }
            return false;
        }

        private void expect(char expected) {
            if (!consume(expected)) {
                throw error("Expected '" + expected + "'");
            }
        }

        private char peek() {
            return text.charAt(offset);
        }

        private boolean finished() {
            return offset >= text.length();
        }

        private IllegalArgumentException error(String detail) {
            return new IllegalArgumentException(detail + " at offset " + offset);
        }
    }
}
