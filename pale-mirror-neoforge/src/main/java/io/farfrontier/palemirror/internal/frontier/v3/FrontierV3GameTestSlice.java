package io.farfrontier.palemirror.internal.frontier.v3;

import java.util.Objects;

/** Explicit, test-server-only selection policy for the fast Frontier v3 GameTest gate. */
public final class FrontierV3GameTestSlice {
    public static final String PROPERTY = "pale_mirror.frontier_v3.game_test_slice";
    private static final String SCENE = "scene";

    private FrontierV3GameTestSlice() { }

    /**
     * Leaves normal and full GameTest launches untouched. The one supported fast slice focuses
     * on exact scene leasing, recovery, movement, cargo and combat boundaries.
     */
    public static boolean includes(String configuredSlice, String batchName) {
        Objects.requireNonNull(configuredSlice, "configured GameTest slice");
        Objects.requireNonNull(batchName, "GameTest batch");
        if (configuredSlice.isBlank()) return true;
        if (!SCENE.equals(configuredSlice)) {
            throw new IllegalArgumentException("unsupported Frontier v3 GameTest slice: " + configuredSlice);
        }
        return batchName.startsWith("pm-frontier-v3-scene-");
    }
}
