package dev.starsector.preflight.cli;

import java.util.ArrayList;
import java.util.List;

/** Reads string fields from object arrays in Starsector's comment-tolerant metadata dialect. */
final class JsonObjectArrays {
    private JsonObjectArrays() {
    }

    static List<String> stringFields(String json, String arrayKey, String fieldKey) {
        String text = stripComments(json);
        int value = findKeyValue(text, arrayKey);
        if (value < 0) {
            return List.of();
        }
        int arrayStart = skipWhitespace(text, value);
        if (arrayStart >= text.length() || text.charAt(arrayStart) != '[') {
            return List.of();
        }

        List<String> values = new ArrayList<>();
        int arrayDepth = 0;
        int objectDepth = 0;
        int objectStart = -1;
        boolean string = false;
        boolean escaped = false;
        for (int i = arrayStart; i < text.length(); i++) {
            char c = text.charAt(i);
            if (string) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    string = false;
                }
                continue;
            }
            if (c == '"') {
                string = true;
                continue;
            }
            if (c == '[') {
                arrayDepth++;
                continue;
            }
            if (c == ']') {
                arrayDepth--;
                if (arrayDepth == 0) {
                    return List.copyOf(values);
                }
                continue;
            }
            if (arrayDepth != 1) {
                continue;
            }
            if (c == '{') {
                if (objectDepth++ == 0) {
                    objectStart = i;
                }
            } else if (c == '}' && objectDepth > 0 && --objectDepth == 0) {
                String object = text.substring(objectStart, i + 1);
                String valueString = JsonText.string(object, fieldKey);
                if (valueString != null && !valueString.isBlank()) {
                    values.add(valueString);
                }
                objectStart = -1;
            }
        }
        throw new IllegalArgumentException("Unterminated object array for key " + arrayKey);
    }

    private static int findKeyValue(String text, String key) {
        boolean string = false;
        boolean escaped = false;
        int stringStart = -1;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (string) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    String candidate = text.substring(stringStart, i);
                    string = false;
                    int colon = skipWhitespace(text, i + 1);
                    if (candidate.equals(key) && colon < text.length() && text.charAt(colon) == ':') {
                        return colon + 1;
                    }
                }
            } else if (c == '"') {
                string = true;
                stringStart = i + 1;
            }
        }
        return -1;
    }

    private static int skipWhitespace(String text, int offset) {
        while (offset < text.length() && Character.isWhitespace(text.charAt(offset))) {
            offset++;
        }
        return offset;
    }

    private static String stripComments(String text) {
        StringBuilder output = new StringBuilder(text.length());
        boolean string = false;
        boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (string) {
                output.append(c);
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    string = false;
                }
                continue;
            }
            if (c == '"') {
                string = true;
                output.append(c);
                continue;
            }
            if (c == '#') {
                output.append(' ');
                while (i + 1 < text.length() && text.charAt(i + 1) != '\n' && text.charAt(i + 1) != '\r') {
                    output.append(' ');
                    i++;
                }
                continue;
            }
            if (c == '/' && i + 1 < text.length() && text.charAt(i + 1) == '/') {
                output.append("  ");
                i++;
                while (i + 1 < text.length() && text.charAt(i + 1) != '\n' && text.charAt(i + 1) != '\r') {
                    output.append(' ');
                    i++;
                }
                continue;
            }
            if (c == '/' && i + 1 < text.length() && text.charAt(i + 1) == '*') {
                output.append("  ");
                i++;
                while (i + 1 < text.length()) {
                    char next = text.charAt(i + 1);
                    if (text.charAt(i) == '*' && next == '/') {
                        output.append(' ');
                        i++;
                        break;
                    }
                    output.append(text.charAt(i) == '\n' || text.charAt(i) == '\r' ? text.charAt(i) : ' ');
                    i++;
                }
                continue;
            }
            output.append(c);
        }
        return output.toString();
    }
}
