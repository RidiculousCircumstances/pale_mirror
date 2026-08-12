package io.farfrontier.palemirror.internal.world;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

/**
 * Bounds non-canonical background ecology without touching PM encounters or explicit game mechanics.
 * Counts are refreshed from loaded entities and are deliberately not persisted.
 */
public final class AmbientSpawnThrottle {
    private static final int REFRESH_INTERVAL_TICKS = 10;
    private static final Map<ResourceKey<Level>, PopulationCounts> COUNTS = new HashMap<>();

    private AmbientSpawnThrottle() { }

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % REFRESH_INTERVAL_TICKS != 0) return;
        COUNTS.clear();
        for (ServerLevel level : server.getAllLevels()) {
            int hostile = 0;
            int peaceful = 0;
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof Mob mob) || !managedSpawnType(mob.getSpawnType())) continue;
                Population population = population(entity.getType().getCategory());
                if (population == Population.HOSTILE) hostile++;
                if (population == Population.PEACEFUL) peaceful++;
            }
            COUNTS.put(level.dimension(), new PopulationCounts(hostile, peaceful));
        }
    }

    public static void clear() {
        COUNTS.clear();
    }

    public static void evaluate(MobSpawnEvent.SpawnPlacementCheck event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        int players = (int) level.players().stream().filter(player -> !player.isSpectator()).count();
        PopulationCounts counts = COUNTS.getOrDefault(level.dimension(), PopulationCounts.EMPTY);
        Policy policy = Policy.configured();
        if (denies(population(event.getEntityType().getCategory()), managedSpawnType(event.getSpawnType()),
                event.getDefaultResult(),
                counts, players, event.getRandom().nextDouble(), policy)) {
            event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
        }
    }

    static boolean denies(Population population, boolean ambientAttempt, boolean vanillaAllows,
            PopulationCounts counts, int playerCount, double roll, Policy policy) {
        if (!vanillaAllows || playerCount <= 0 || !ambientAttempt) return false;
        return switch (population) {
            case HOSTILE -> counts.hostile() >= policy.hostileCapPerPlayer() * playerCount
                    || roll >= policy.hostileAttemptChance();
            case PEACEFUL -> counts.peaceful() >= policy.peacefulCapPerPlayer() * playerCount
                    || roll >= policy.peacefulAttemptChance();
            case UNMANAGED -> false;
        };
    }

    static boolean managedSpawnType(MobSpawnType spawnType) {
        return spawnType == MobSpawnType.NATURAL
                || spawnType == MobSpawnType.CHUNK_GENERATION
                || spawnType == MobSpawnType.PATROL;
    }

    private static Population population(MobCategory category) {
        if (category == MobCategory.MONSTER) return Population.HOSTILE;
        if (category == MobCategory.CREATURE
                || category == MobCategory.AMBIENT
                || category == MobCategory.AXOLOTLS
                || category == MobCategory.UNDERGROUND_WATER_CREATURE
                || category == MobCategory.WATER_CREATURE
                || category == MobCategory.WATER_AMBIENT) return Population.PEACEFUL;
        return Population.UNMANAGED;
    }

    enum Population {
        HOSTILE,
        PEACEFUL,
        UNMANAGED
    }

    record PopulationCounts(int hostile, int peaceful) {
        static final PopulationCounts EMPTY = new PopulationCounts(0, 0);
    }

    record Policy(double hostileAttemptChance, double peacefulAttemptChance,
            int hostileCapPerPlayer, int peacefulCapPerPlayer) {
        static Policy configured() {
            return new Policy(PaleMirrorServerConfig.HOSTILE_SPAWN_CHANCE.get(),
                    PaleMirrorServerConfig.PEACEFUL_SPAWN_CHANCE.get(),
                    PaleMirrorServerConfig.HOSTILE_AMBIENT_CAP_PER_PLAYER.get(),
                    PaleMirrorServerConfig.PEACEFUL_AMBIENT_CAP_PER_PLAYER.get());
        }
    }
}
