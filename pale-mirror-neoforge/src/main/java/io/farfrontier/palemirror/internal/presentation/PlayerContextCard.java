package io.farfrontier.palemirror.internal.presentation;

import java.util.List;
import java.util.Objects;

/** Pure bounded content for the one replace-not-stack contextual card. */
public record PlayerContextCard(String title, List<String> lines, int accentRgb) {
    public PlayerContextCard {
        title = bounded(title, 72, "card title");
        lines = List.copyOf(Objects.requireNonNull(lines, "card lines"));
        if (lines.size() > 2) throw new IllegalArgumentException("context card may contain at most two detail lines");
        lines = lines.stream().map(line -> bounded(line, 112, "card line")).toList();
    }

    private static String bounded(String value, int limit, String label) {
        String required = Objects.requireNonNull(value, label);
        if (required.indexOf('\n') >= 0 || required.indexOf('\r') >= 0) throw new IllegalArgumentException(label + " must be one line");
        String result = required.replaceAll("\\s+", " ").trim();
        if (result.isBlank() || result.length() > limit) throw new IllegalArgumentException(label + " must be 1.." + limit + " characters");
        return result;
    }
}
