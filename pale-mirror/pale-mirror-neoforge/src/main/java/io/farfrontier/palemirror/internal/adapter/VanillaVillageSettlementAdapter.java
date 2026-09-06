package io.farfrontier.palemirror.internal.adapter;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import io.farfrontier.palemirror.api.AdapterHealth;
import io.farfrontier.palemirror.api.Capability;
import io.farfrontier.palemirror.domain.WorldObjectId;
import io.farfrontier.palemirror.domain.SettlementCohort;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import io.farfrontier.palemirror.internal.settlement.RefugeeCampRuntime;

/**
 * Vanilla-signals-only village observer.  Integrated Villages structures are
 * covered without touching their internals when they contain the usual
 * villagers and bed/bell landmarks.  Missing landmarks deliberately produce
 * no candidate rather than a moving, unstable identity.
 */
public final class VanillaVillageSettlementAdapter implements SettlementAdapter {
    private static final int ENTITY_RADIUS = 96;
    private static final int RESIDENT_RADIUS = 48;
    private static final int LANDMARK_RADIUS = 16;
    private static final int POI_RADIUS = 96;
    private static final int VERTICAL_RADIUS = 48;
    private static final int RESIDENT_VERTICAL_RADIUS = 32;
    private static final int BOUNDS_RADIUS = 64;
    private static final int BOUNDS_VERTICAL_RADIUS = 32;
    private static final int MIN_VILLAGERS = 2;

    @Override public String id() { return "pale_mirror:vanilla_village_observer"; }

    @Override
    public AdapterHealth health() {
        return new AdapterHealth(AdapterHealth.Status.AVAILABLE,
                "Reads loaded vanilla village signals; compatible Integrated Villages structures need no private API",
                Set.of(Capability.SETTLEMENT_OBSERVATION));
    }

    @Override
    public List<SettlementObservation> observeNearby(ServerLevel level, BlockPos focus) {
        List<Villager> villagers = level.getEntitiesOfClass(Villager.class, searchBox(focus),
                villager -> !RefugeeCampRuntime.isRepresentative(villager));
        if (villagers.size() < MIN_VILLAGERS) return List.of();
        // The player/focus location establishes the local observation window.
        // Do not let a second loaded village win merely because its bell has a
        // smaller packed BlockPos value.
        BlockPos landmark = findLandmark(level, focus, villagers);
        if (landmark == null) return List.of();
        int residents = (int) villagers.stream().filter(villager -> residentBox(landmark).contains(villager.position())).count();
        if (residents < MIN_VILLAGERS) return List.of();
        int guards = level.getEntitiesOfClass(IronGolem.class, residentBox(landmark)).size();
        WorldObjectId id = new WorldObjectId("pale_mirror:village_" + Long.toUnsignedString(landmark.asLong(), 36));
        BlockPos min = new BlockPos(landmark.getX() - BOUNDS_RADIUS, landmark.getY() - BOUNDS_VERTICAL_RADIUS,
                landmark.getZ() - BOUNDS_RADIUS);
        BlockPos max = new BlockPos(landmark.getX() + BOUNDS_RADIUS, landmark.getY() + BOUNDS_VERTICAL_RADIUS,
                landmark.getZ() + BOUNDS_RADIUS);
        List<SettlementRepresentativeObservation> representatives = new java.util.ArrayList<>();
        villagers.stream().filter(villager -> residentBox(landmark).contains(villager.position()))
                .sorted(Comparator.comparing(villager -> villager.getUUID().toString()))
                .forEach(villager -> representatives.add(new SettlementRepresentativeObservation(
                        villager.getUUID().toString(), cohort(villager))));
        level.getEntitiesOfClass(IronGolem.class, residentBox(landmark)).stream()
                .sorted(Comparator.comparing(golem -> golem.getUUID().toString()))
                .forEach(golem -> representatives.add(new SettlementRepresentativeObservation(
                        golem.getUUID().toString(), SettlementCohort.GUARDS)));
        return List.of(new SettlementObservation(id, level.dimension().location().toString(), landmark, min, max,
                residents, guards, level.getGameTime(), fullyLoaded(level, min, max), representatives,
                "minecraft:loaded_village_signals_v2"));
    }

    private static AABB searchBox(BlockPos center) {
        return new AABB(center).inflate(ENTITY_RADIUS, VERTICAL_RADIUS, ENTITY_RADIUS);
    }

    private static AABB residentBox(BlockPos center) {
        return new AABB(center).inflate(RESIDENT_RADIUS, RESIDENT_VERTICAL_RADIUS, RESIDENT_RADIUS);
    }

    private static BlockPos findLandmark(ServerLevel level, BlockPos focus, List<Villager> villagers) {
        BlockPos meeting = bestPoi(level, focus, villagers, PoiTypes.MEETING);
        if (meeting != null) return meeting;
        BlockPos home = bestPoi(level, focus, villagers, PoiTypes.HOME);
        if (home != null) return home;
        // A freshly placed GameTest or another mod may not have updated the POI manager yet.
        // Keep a tiny block fallback; never scan a complete large village volume every observation tick.
        return BlockPos.betweenClosedStream(focus.offset(-LANDMARK_RADIUS, -VERTICAL_RADIUS, -LANDMARK_RADIUS),
                        focus.offset(LANDMARK_RADIUS, VERTICAL_RADIUS, LANDMARK_RADIUS))
                .map(BlockPos::immutable)
                .filter(level::hasChunkAt)
                .filter(position -> level.getBlockState(position).is(Blocks.BELL)
                        || level.getBlockState(position).is(BlockTags.BEDS))
                .sorted(Comparator.comparingDouble((BlockPos position) -> position.distSqr(focus))
                        .thenComparingInt(position -> level.getBlockState(position).is(Blocks.BELL) ? 0 : 1)
                        .thenComparingLong(BlockPos::asLong))
                .findFirst().orElse(null);
    }

    private static BlockPos bestPoi(ServerLevel level, BlockPos focus, List<Villager> villagers,
                                    ResourceKey<PoiType> type) {
        return level.getPoiManager().findAll(holder -> holder.is(type), level::hasChunkAt, focus, POI_RADIUS,
                        PoiManager.Occupancy.ANY)
                .map(BlockPos::immutable)
                .filter(position -> Math.abs(position.getY() - focus.getY()) <= VERTICAL_RADIUS)
                .filter(position -> residentCount(villagers, position) >= MIN_VILLAGERS)
                .sorted(Comparator.comparingInt((BlockPos position) -> -residentCount(villagers, position))
                        .thenComparingDouble(position -> position.distSqr(focus))
                        .thenComparingLong(BlockPos::asLong))
                .findFirst().orElse(null);
    }

    private static int residentCount(List<Villager> villagers, BlockPos landmark) {
        return (int) villagers.stream().filter(villager -> residentBox(landmark).contains(villager.position())).count();
    }

    private static SettlementCohort cohort(Villager villager) {
        if (villager.isBaby()) return SettlementCohort.CHILDREN;
        var profession = villager.getVillagerData().getProfession();
        if (profession == VillagerProfession.LIBRARIAN || profession == VillagerProfession.CLERIC) {
            return SettlementCohort.SPECIALISTS;
        }
        return profession == VillagerProfession.NONE || profession == VillagerProfession.NITWIT
                ? SettlementCohort.CIVILIANS : SettlementCohort.WORKERS;
    }

    private static boolean fullyLoaded(ServerLevel level, BlockPos min, BlockPos max) {
        int minChunkX = Math.floorDiv(min.getX(), 16);
        int maxChunkX = Math.floorDiv(max.getX(), 16);
        int minChunkZ = Math.floorDiv(min.getZ(), 16);
        int maxChunkZ = Math.floorDiv(max.getZ(), 16);
        for (int x = minChunkX; x <= maxChunkX; x++) {
            for (int z = minChunkZ; z <= maxChunkZ; z++) {
                if (!level.hasChunkAt(new BlockPos(x << 4, min.getY(), z << 4))) return false;
            }
        }
        return true;
    }
}
