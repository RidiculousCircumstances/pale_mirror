package io.farfrontier.palemirror.internal.world;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Operational budgets; zero residents means the whole canonical group, as requested for the private profile. */
public final class PaleMirrorServerConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue JOURNEY_RESIDENT_LIMIT;
    public static final ModConfigSpec.IntValue JOURNEY_SPAWN_BUDGET;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        JOURNEY_RESIDENT_LIMIT = builder.comment(
                        "Maximum stable residents physically shown for one nearby journey. 0 materializes the full population.")
                .defineInRange("journeys.maxMaterializedResidents", 0, 0, 1024);
        JOURNEY_SPAWN_BUDGET = builder.comment("Maximum journey residents spawned during one server tick.")
                .defineInRange("journeys.spawnBudgetPerTick", 4, 1, 64);
        SPEC = builder.build();
    }

    private PaleMirrorServerConfig() { }

    public static int effectiveResidentLimit(int population) {
        int configured = JOURNEY_RESIDENT_LIMIT.get();
        return configured == 0 ? population : Math.min(population, configured);
    }
}
