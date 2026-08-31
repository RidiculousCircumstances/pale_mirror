package io.farfrontier.palemirror.internal.frontier.v3;

import java.util.Objects;

/** Explicit, test-server-only selection policy for the fast Frontier v3 GameTest gate. */
public final class FrontierV3GameTestSlice {
    public static final String PROPERTY = "pale_mirror.frontier_v3.game_test_slice";
    private static final String SCENE = "scene";
    private static final String ECONOMY = "economy";

    private FrontierV3GameTestSlice() { }

    /**
     * Leaves normal and full GameTest launches untouched. Fast slices name one causal boundary:
     * scenes cover HOT/COLD execution; economy covers exact inventory and readable owned stores.
     */
    public static boolean includes(String configuredSlice, String batchName) {
        Objects.requireNonNull(configuredSlice, "configured GameTest slice");
        Objects.requireNonNull(batchName, "GameTest batch");
        if (configuredSlice.isBlank()) return true;
        return switch (configuredSlice) {
            case SCENE -> batchName.startsWith("pm-frontier-v3-scene-") || batchName.startsWith("pm-frontier-v3-ambient-")
                    || batchName.startsWith("pm-frontier-v3-scout-");
            case ECONOMY -> batchName.equals("pm-frontier-v3-exact-consumption") || batchName.equals("pm-frontier-v3-production")
                    || batchName.equals("pm-frontier-v3-cargo-loading") || batchName.equals("pm-frontier-v3-object-boards");
            default -> throw new IllegalArgumentException("unsupported Frontier v3 GameTest slice: " + configuredSlice);
        };
    }
}
