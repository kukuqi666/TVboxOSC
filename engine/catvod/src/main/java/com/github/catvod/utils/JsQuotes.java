package com.github.catvod.utils;

/** Minimal JSON string quoting for source jar helpers. */
public final class JsQuotes {

    private JsQuotes() {}

    public static String quote(String text) {
        if (text == null) return "null";
        StringBuilder builder = new StringBuilder(text.length() + 16);
        builder.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                default -> {
                    if (c < 0x20 || c == 0x2028 || c == 0x2029) builder.append(String.format("\\u%04x", (int) c));
                    else builder.append(c);
                }
            }
        }
        builder.append('"');
        return builder.toString();
    }
}