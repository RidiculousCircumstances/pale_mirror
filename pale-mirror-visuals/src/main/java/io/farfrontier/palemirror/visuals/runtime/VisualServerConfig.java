package io.farfrontier.palemirror.visuals.runtime;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Visual-only budgets; canonical population is never reduced by these settings. */
public final class VisualServerConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue ACTIVE_RESIDENT_AI_LIMIT;
    public static final ModConfigSpec.IntValue ACTIVE_RESIDENT_AI_RADIUS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        ACTIVE_RESIDENT_AI_LIMIT = builder.comment(
                        "Maximum authored residents with ticking vanilla AI in one dimension. All residents stay visible.")
                .defineInRange("residents.activeAiLimit", 16, 0, 256);
        ACTIVE_RESIDENT_AI_RADIUS = builder.comment("Player distance in which authored resident AI may be activated.")
                .defineInRange("residents.activeAiRadius", 96, 16, 256);
        SPEC = builder.build();
    }

    private VisualServerConfig() { }
}
