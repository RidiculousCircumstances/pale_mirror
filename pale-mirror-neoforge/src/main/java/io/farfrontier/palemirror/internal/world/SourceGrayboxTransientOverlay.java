package io.farfrontier.palemirror.internal.world;

import java.util.Arrays;
import java.util.Objects;

/**
 * Bounded transient player aid derived from a source graybox briefing.
 *
 * <p>Exact detail remains on the world board and can be reconstructed on the
 * next interaction. Replaying a five-line briefing into Minecraft's retained
 * system chat made repeated ordinary clicks hide the world the player was
 * trying to inspect. This overlay retains title, current state and immediate
 * risk while replacing—not appending to—the prior player aid.</p>
 */
final class SourceGrayboxTransientOverlay {
    private static final int MAX_LINES = 3;

    private SourceGrayboxTransientOverlay() { }

    static String from(String briefing) {
        String required = Objects.requireNonNull(briefing, "briefing");
        String result = Arrays.stream(required.split("\\n"))
                .filter(line -> !line.isBlank())
                .limit(MAX_LINES)
                .reduce((first, second) -> first + "\n" + second)
                .orElse("");
        if (result.isBlank()) throw new IllegalArgumentException("briefing must contain visible text");
        return result;
    }
}
