package io.farfrontier.palemirror.internal.world;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Operational budgets; zero residents means the whole canonical group, as requested for the private profile. */
public final class PaleMirrorServerConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue JOURNEY_RESIDENT_LIMIT;
    public static final ModConfigSpec.IntValue JOURNEY_SPAWN_BUDGET;
    public static final ModConfigSpec.DoubleValue HOSTILE_SPAWN_CHANCE;
    public static final ModConfigSpec.DoubleValue PEACEFUL_SPAWN_CHANCE;
    public static final ModConfigSpec.IntValue HOSTILE_AMBIENT_CAP_PER_PLAYER;
    public static final ModConfigSpec.IntValue PEACEFUL_AMBIENT_CAP_PER_PLAYER;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        JOURNEY_RESIDENT_LIMIT = builder.comment(
                        "Maximum stable residents physically shown for one nearby journey. 0 materializes the full population.")
                .defineInRange("journeys.maxMaterializedResidents", 0, 0, 1024);
        JOURNEY_SPAWN_BUDGET = builder.comment("Maximum journey residents spawned during one server tick.")
                .defineInRange("journeys.spawnBudgetPerTick", 4, 1, 64);
        HOSTILE_SPAWN_CHANCE = builder.comment(
                        "Acceptance chance for an otherwise valid natural hostile spawn attempt. "
                                + "PM encounters, structures, spawners, events and commands are exempt.")
                .defineInRange("ambientSpawns.hostileAttemptChance", 0.20D, 0.0D, 1.0D);
        PEACEFUL_SPAWN_CHANCE = builder.comment(
                        "Acceptance chance for an otherwise valid natural peaceful/ambient spawn attempt.")
                .defineInRange("ambientSpawns.peacefulAttemptChance", 0.55D, 0.0D, 1.0D);
        HOSTILE_AMBIENT_CAP_PER_PLAYER = builder.comment(
                        "Maximum loaded naturally spawned hostile mobs per non-spectator player and dimension.")
                .defineInRange("ambientSpawns.hostileCapPerPlayer", 24, 0, 512);
        PEACEFUL_AMBIENT_CAP_PER_PLAYER = builder.comment(
                        "Maximum loaded naturally spawned peaceful, ambient and water mobs per non-spectator player and dimension.")
                .defineInRange("ambientSpawns.peacefulCapPerPlayer", 32, 0, 512);
        SPEC = builder.build();
    }

    private PaleMirrorServerConfig() { }

    public static int effectiveResidentLimit(int population) {
        int configured = JOURNEY_RESIDENT_LIMIT.get();
        return configured == 0 ? population : Math.min(population, configured);
    }
}
