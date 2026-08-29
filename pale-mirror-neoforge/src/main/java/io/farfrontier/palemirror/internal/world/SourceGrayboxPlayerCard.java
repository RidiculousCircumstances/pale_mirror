package io.farfrontier.palemirror.internal.world;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import io.farfrontier.palemirror.internal.presentation.PlayerContextCard;

/** Converts a read-only graybox briefing into the bounded replace-not-stack object-card contract. */
final class SourceGrayboxPlayerCard {
    private static final int MAX_LINES = 3;

    private SourceGrayboxPlayerCard() { }

    static PlayerContextCard fromBriefing(String briefing) {
        String[] visible = Arrays.stream(Objects.requireNonNull(briefing, "briefing").split("\\n"))
                .map(String::trim).filter(line -> !line.isBlank()).limit(MAX_LINES).toArray(String[]::new);
        if (visible.length == 0) throw new IllegalArgumentException("briefing must contain visible text");
        List<String> details = Arrays.stream(visible).skip(1).toList();
        return new PlayerContextCard(visible[0], details, accent(visible[0]));
    }

    private static int accent(String title) {
        if (title.startsWith("[HIVE]")) return 0xAA4CFF;
        if (title.startsWith("[ACTION]")) return 0xFFAA28;
        if (title.startsWith("[ROUTE]")) return 0x38BDF8;
        if (title.startsWith("[SETTLEMENT]")) return 0x5EE27A;
        return 0xD7D7D7;
    }
}
