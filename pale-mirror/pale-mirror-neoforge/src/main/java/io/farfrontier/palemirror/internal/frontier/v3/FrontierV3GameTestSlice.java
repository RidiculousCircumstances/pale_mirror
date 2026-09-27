package io.farfrontier.palemirror.internal.frontier.v3;

import java.util.Objects;

/** Explicit, test-server-only selection policy for the fast Frontier v3 GameTest gate. */
public final class FrontierV3GameTestSlice {
    public static final String PROPERTY = "pale_mirror.frontier_v3.game_test_slice";
    private static final String SCENE = "scene";
    private static final String ECONOMY = "economy";
    private static final String AMBIENT_PHYSICS = "ambient-physics";
    private static final String ADAPTER_MIRRORS = "adapter-mirrors";

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
                    || batchName.startsWith("pm-frontier-v3-assembly-") || batchName.startsWith("pm-frontier-v3-scout-")
                    || batchName.equals("pm-frontier-v3-route-maintenance") || batchName.equals("pm-frontier-v3-route-patrol") || batchName.equals("pm-frontier-v3-graybox")
                    || batchName.equals("pm-frontier-v3-resource-observation");
            case ECONOMY -> batchName.equals("pm-frontier-v3-exact-consumption") || batchName.equals("pm-frontier-v3-production")
                    || batchName.equals("pm-frontier-v3-cargo-loading") || batchName.equals("pm-frontier-v3-hive-nutrient")
                    || batchName.equals("pm-frontier-v3-player-withdrawal")
                    || batchName.equals("pm-frontier-v3-object-boards") || batchName.equals("pm-frontier-v3-equipment-issue")
                    || batchName.equals("pm-frontier-v3-equipment-return") || batchName.equals("pm-frontier-v3-equipment-death")
                    || batchName.equals("pm-frontier-v3-resource-recovery") || batchName.equals("pm-frontier-v3-reference-custody")
                    || batchName.equals("pm-frontier-v3-reference-projection");
            case AMBIENT_PHYSICS -> batchName.equals("pm-frontier-v3-ambient-physics");
            case "cargo" -> batchName.equals("pm-frontier-v3-scene-cargo") || batchName.equals("pm-frontier-v3-scene-cargo-authority")
                    || batchName.equals("pm-frontier-v3-scene-cargo-interaction");
            case "cargo-authority" -> batchName.equals("pm-frontier-v3-scene-cargo-authority");
            case "cargo-interaction" -> batchName.equals("pm-frontier-v3-scene-cargo-interaction");
            case "scene-departure" -> batchName.equals("pm-frontier-v3-scene-departure") || batchName.equals("pm-frontier-v3-scene-deaths");
            case "scene-restart-reclaim" -> batchName.equals("pm-frontier-v3-scene-restart-reclaim");
            case "first-admission" -> batchName.equals("pm-frontier-v3-scene-first-admission");
            case "harvest-support" -> batchName.equals("pm-frontier-v3-scene-harvest-support");
            case "ambient-restart-absence" -> batchName.equals("pm-frontier-v3-ambient-restart-absence");
            case "ambient-prepared-recovery" -> batchName.equals("pm-frontier-v3-ambient-prepared-recovery");
            case "village-observer" -> batchName.equals("pm-village-observer");
            case "graybox-projection" -> batchName.equals("pm-frontier-v3-graybox-projection");
            case "scene-strikes" -> batchName.equals("pm-frontier-v3-scene-strikes");
            case "scene-handoff" -> batchName.equals("pm-frontier-v3-scene-handoff");
            case "scene-bodies" -> batchName.equals("pm-frontier-v3-scene-bodies");
            case "route-patrol" -> batchName.equals("pm-frontier-v3-scene-route-patrol")
                    || batchName.startsWith("pm-frontier-v3-scene-z-route-patrol-");
            case "production-work" -> batchName.equals("pm-frontier-v3-scene-production-work");
            case "local-navigation" -> batchName.equals("pm-frontier-v3-scene-local-navigation");
            case "route-construction" -> batchName.equals("pm-frontier-v3-scene-route-construction");
            case "production-effect" -> batchName.equals("pm-frontier-v3-production");
            case "resource-prefix" -> batchName.equals("pm-frontier-v3-resource-site-prefix");
            case "resource-site-cold" -> batchName.equals("pm-frontier-v3-resource-site-cold");
            case "field-turns" -> batchName.equals("pm-frontier-v3-field-turns");
            case "physical-ownership-fences" -> batchName.equals("pm-frontier-v3-object-boards")
                    || batchName.equals("pm-frontier-v3-resource-harvest");
            case ADAPTER_MIRRORS -> batchName.equals("pm-frontier-v3-graybox")
                    || batchName.equals("pm-frontier-v3-infection-overlay");
            case "reference-depot-never-visited" -> batchName.equals("pm-frontier-v3-reference-depot-never-visited");
            case "reference-depot-visited-unloaded" -> batchName.equals("pm-frontier-v3-reference-depot-visited-unloaded");
            case "reference-hive-zero-player" -> batchName.equals("pm-frontier-v3-reference-hive-zero-player");
            case "reference-conflict-restart" -> batchName.equals("pm-frontier-v3-reference-conflict-restart");
            case "reference-all" -> batchName.startsWith("pm-frontier-v3-reference-");
            case "reference-projection" -> batchName.equals("pm-frontier-v3-reference-projection");
            default -> throw new IllegalArgumentException("unsupported Frontier v3 GameTest slice: " + configuredSlice);
        };
    }
}
