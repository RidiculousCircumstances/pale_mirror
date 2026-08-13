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
    public static final ModConfigSpec.DoubleValue RUNTIME_BUDGET_MILLIS;
    public static final ModConfigSpec.IntValue RUNTIME_WEIGHT_BUDGET;
    public static final ModConfigSpec.BooleanValue DH_CACHE_ONLY_CONTROL;
    public static final ModConfigSpec.DoubleValue DH_FAST_TRAVEL_ENTER_SPEED;
    public static final ModConfigSpec.DoubleValue DH_FAST_TRAVEL_EXIT_SPEED;
    public static final ModConfigSpec.IntValue DH_FAST_TRAVEL_RECOVERY_SECONDS;
    public static final ModConfigSpec.DoubleValue DH_NORMAL_RUNTIME_RATIO;
    public static final ModConfigSpec.DoubleValue DH_FAST_TRAVEL_RUNTIME_RATIO;

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
        RUNTIME_BUDGET_MILLIS = builder.comment(
                        "Soft Pale Mirror server-thread budget per tick. Safety-critical reconciliation may exceed it.")
                .defineInRange("runtime.softBudgetMillis", 3.0D, 0.25D, 20.0D);
        RUNTIME_WEIGHT_BUDGET = builder.comment(
                        "Maximum weighted PM operations admitted per tick before background work is deferred.")
                .defineInRange("runtime.weightBudget", 256, 16, 4096);
        DH_CACHE_ONLY_CONTROL = builder.comment(
                        "Pin server-side Distant Horizons to PRE_EXISTING_ONLY. Ready LOD cache and sync remain enabled, "
                                + "but DH never generates unknown terrain.")
                .define("distantHorizons.cacheOnlyControl", true);
        DH_FAST_TRAVEL_ENTER_SPEED = builder.comment(
                        "Horizontal player speed in blocks/second that throttles server-side DH cache construction.")
                .defineInRange("distantHorizons.fastTravelEnterBlocksPerSecond", 12.0D, 1.0D, 1024.0D);
        DH_FAST_TRAVEL_EXIT_SPEED = builder.comment(
                        "All players must remain below this horizontal speed before normal DH work resumes.")
                .defineInRange("distantHorizons.fastTravelExitBlocksPerSecond", 6.0D, 0.0D, 1024.0D);
        DH_FAST_TRAVEL_RECOVERY_SECONDS = builder.comment(
                        "Continuous slow-travel time required before normal DH work resumes.")
                .defineInRange("distantHorizons.fastTravelRecoverySeconds", 10, 1, 300);
        DH_NORMAL_RUNTIME_RATIO = builder.comment("DH worker runtime ratio during ordinary play.")
                .defineInRange("distantHorizons.normalRuntimeRatio", 0.35D, 0.01D, 1.0D);
        DH_FAST_TRAVEL_RUNTIME_RATIO = builder.comment("DH worker runtime ratio during fast travel and teleports.")
                .defineInRange("distantHorizons.fastTravelRuntimeRatio", 0.05D, 0.01D, 1.0D);
        SPEC = builder.build();
    }

    private PaleMirrorServerConfig() { }

    public static int effectiveResidentLimit(int population) {
        int configured = JOURNEY_RESIDENT_LIMIT.get();
        return configured == 0 ? population : Math.min(population, configured);
    }
}
