package io.eventdriven.core.messaging;

import org.jspecify.annotations.Nullable;

public final class RoutingPattern {
    private RoutingPattern() {}

    public static boolean matches(String pattern, @Nullable String key) {
        var words = key != null ? key.split("\\.") : new String[0];
        return matches(pattern.split("\\."), 0, words, 0);
    }

    private static boolean matches(String[] pattern, int p, String[] words, int w) {
        if (p == pattern.length) return w == words.length;

        if (pattern[p].equals("#"))
            return matches(pattern, p + 1, words, w) || (w < words.length && matches(pattern, p, words, w + 1));

        return w < words.length
                && (pattern[p].equals("*") || pattern[p].equals(words[w]))
                && matches(pattern, p + 1, words, w + 1);
    }
}
