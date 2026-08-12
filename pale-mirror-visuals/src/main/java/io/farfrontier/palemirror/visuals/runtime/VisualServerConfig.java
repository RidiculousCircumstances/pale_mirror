package io.farfrontier.palemirror.visuals.runtime;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Visual-only budgets; canonical population is never reduced by these settings. */
public final class VisualServerConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue ACTIVE_RESIDENT_AI_LIMIT;
    public static final ModConfigSpec.IntValue ACTIVE_RESIDENT_AI_RADIUS;
    public static final ModConfigSpec.IntValue GENESIS_REGION_COUNT;
    public static final ModConfigSpec.IntValue GENESIS_MAP_RADIUS;
    public static final ModConfigSpec.IntValue GENESIS_MINIMUM_SPACING;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        ACTIVE_RESIDENT_AI_LIMIT = builder.comment(
                        "Maximum authored residents with ticking vanilla AI in one dimension. All residents stay visible.")
                .defineInRange("residents.activeAiLimit", 16, 0, 256);
        ACTIVE_RESIDENT_AI_RADIUS = builder.comment("Player distance in which authored resident AI may be activated.")
                .defineInRange("residents.activeAiRadius", 96, 16, 256);
        GENESIS_REGION_COUNT = builder.comment("Number of authored regions selected together for a fresh world.")
                .defineInRange("genesis.regionCount", 3, 1, 64);
        GENESIS_MAP_RADIUS = builder.comment(
                        "Maximum authored-region center radius around world spawn. Static geometry stays inside it.")
                .defineInRange("genesis.mapRadius", 10_000, 3_000, 100_000);
        GENESIS_MINIMUM_SPACING = builder.comment("Minimum center-to-center spacing between authored regions.")
                .defineInRange("genesis.minimumSpacing", 1_400, 1_024, 10_000);
        SPEC = builder.build();
    }

    private VisualServerConfig() { }
}
