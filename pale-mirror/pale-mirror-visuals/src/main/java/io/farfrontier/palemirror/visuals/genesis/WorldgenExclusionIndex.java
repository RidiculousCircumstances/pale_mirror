package io.farfrontier.palemirror.visuals.genesis;

import io.farfrontier.palemirror.api.AuthoredRegionSeed;
import io.farfrontier.palemirror.api.SiteEnvironmentPlan;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Immutable, chunk-indexed fresh-world reservation used from parallel worldgen threads. */
public final class WorldgenExclusionIndex {
    private static final AtomicReference<Snapshot> SNAPSHOT = new AtomicReference<>(Snapshot.empty());
    private static final AtomicLong REJECTED_FEATURES = new AtomicLong();
    private static final AtomicLong REJECTED_WRITES = new AtomicLong();
    private static final AtomicLong REJECTED_STRUCTURES = new AtomicLong();

    private WorldgenExclusionIndex() { }

    public static void install(ChunkGenerator generator, List<AuthoredRegionSeed> regions) {
        List<SiteEnvironmentPlan> zones = new ArrayList<>();
        for (AuthoredRegionSeed region : regions) {
            zones.add(region.settlementSite().environment());
            zones.add(region.primaryMineSite().environment());
            zones.add(region.alternateMineSite().environment());
        }
        Map<Long, List<SiteEnvironmentPlan>> byChunk = new LinkedHashMap<>();
        for (SiteEnvironmentPlan zone : zones) {
            int minimumChunkX = Math.floorDiv(zone.center().x() - zone.transitionRadius(), 16);
            int maximumChunkX = Math.floorDiv(zone.center().x() + zone.transitionRadius(), 16);
            int minimumChunkZ = Math.floorDiv(zone.center().z() - zone.transitionRadius(), 16);
            int maximumChunkZ = Math.floorDiv(zone.center().z() + zone.transitionRadius(), 16);
            for (int chunkX = minimumChunkX; chunkX <= maximumChunkX; chunkX++) {
                for (int chunkZ = minimumChunkZ; chunkZ <= maximumChunkZ; chunkZ++) {
                    byChunk.computeIfAbsent(ChunkPos.asLong(chunkX, chunkZ), ignored -> new ArrayList<>()).add(zone);
                }
            }
        }
        Map<Long, List<SiteEnvironmentPlan>> immutable = new LinkedHashMap<>();
        byChunk.forEach((key, value) -> immutable.put(key, List.copyOf(value)));
        IdentityHashMap<ChunkGenerator, Boolean> generators = new IdentityHashMap<>();
        generators.put(generator, Boolean.TRUE);
        SNAPSHOT.set(new Snapshot(List.copyOf(zones), Map.copyOf(immutable), generators));
    }

    public static void clear() {
        SNAPSHOT.set(Snapshot.empty());
        REJECTED_FEATURES.set(0L); REJECTED_WRITES.set(0L); REJECTED_STRUCTURES.set(0L);
    }

    static boolean appliesTo(ChunkGenerator generator) {
        return SNAPSHOT.get().generators().containsKey(generator);
    }

    static boolean allowFeatureOrigin(ChunkGenerator generator, FeatureCategory category,
                                      String featureId, BlockPos origin) {
        if (!appliesTo(generator) || category == FeatureCategory.PRESERVE
                || category == FeatureCategory.UNKNOWN || category == FeatureCategory.PM_AUTHORED) return true;
        SiteEnvironmentPlan zone = nearest(origin.getX(), origin.getZ());
        if (zone == null) return true;
        double distance = Math.hypot((double) origin.getX() - zone.center().x(),
                (double) origin.getZ() - zone.center().z());
        if (distance <= zone.hardRadius()) return rejectedFeature();
        if (distance > zone.transitionRadius()) return true;
        double start = category == FeatureCategory.TREE ? zone.hardRadius() + 16D : zone.hardRadius();
        if (distance <= start) return rejectedFeature();
        double normalized = Math.min(1D, Math.max(0D,
                (distance - start) / (zone.transitionRadius() - start)));
        double retention = normalized * normalized * (3D - 2D * normalized);
        long hash = mix(origin.getX(), origin.getZ(), featureId.hashCode(), zone.policyId().hashCode());
        double sample = (hash >>> 11) * 0x1.0p-53;
        return sample < retention || rejectedFeature();
    }

    static boolean allowWrite(ChunkGenerator generator, FeatureCategory category,
                              BlockPos position, BlockState state) {
        if (!appliesTo(generator) || category == FeatureCategory.PM_AUTHORED
                || category == FeatureCategory.PRESERVE) return true;
        for (SiteEnvironmentPlan zone : zones(position.getX(), position.getZ())) {
            if (!zone.insideHard(position.getX(), position.getZ())) continue;
            if (category.surfaceDecoration() || category == FeatureCategory.UNKNOWN
                    && FrontierWorldgenFeature.naturalVegetation(state)) {
                REJECTED_WRITES.incrementAndGet();
                return false;
            }
        }
        return true;
    }

    public static boolean rejectSurfaceStructure(ChunkGenerator generator, BoundingBox bounds) {
        if (!appliesTo(generator)) return false;
        for (SiteEnvironmentPlan zone : SNAPSHOT.get().zones()) {
            if (intersectsSurfaceStructure(zone, bounds)) {
                REJECTED_STRUCTURES.incrementAndGet();
                return true;
            }
        }
        return false;
    }

    static boolean intersectsSurfaceStructure(SiteEnvironmentPlan zone, BoundingBox bounds) {
        if (bounds.maxY() < zone.minimumSurfaceY() - 8) return false;
        int x = Math.max(bounds.minX(), Math.min(zone.center().x(), bounds.maxX()));
        int z = Math.max(bounds.minZ(), Math.min(zone.center().z(), bounds.maxZ()));
        return zone.insideStructureClearance(x, z);
    }

    public static String metrics() {
        Snapshot snapshot = SNAPSHOT.get();
        return "exclusionZones=" + snapshot.zones().size() + ", rejectedFeatures=" + REJECTED_FEATURES.get()
                + ", rejectedSurfaceWrites=" + REJECTED_WRITES.get()
                + ", rejectedSurfaceStructures=" + REJECTED_STRUCTURES.get();
    }

    private static SiteEnvironmentPlan nearest(int x, int z) {
        SiteEnvironmentPlan nearest = null;
        long nearestDistance = Long.MAX_VALUE;
        for (SiteEnvironmentPlan zone : zones(x, z)) {
            long dx = (long) x - zone.center().x(); long dz = (long) z - zone.center().z();
            long distance = dx * dx + dz * dz;
            if (distance < nearestDistance) { nearest = zone; nearestDistance = distance; }
        }
        return nearest;
    }

    private static List<SiteEnvironmentPlan> zones(int x, int z) {
        return SNAPSHOT.get().byChunk().getOrDefault(ChunkPos.asLong(Math.floorDiv(x, 16),
                Math.floorDiv(z, 16)), List.of());
    }

    private static boolean rejectedFeature() {
        REJECTED_FEATURES.incrementAndGet();
        return false;
    }

    private static long mix(int x, int z, int first, int second) {
        long value = ((long) x << 32) ^ (z & 0xffffffffL) ^ ((long) first << 17) ^ second;
        value ^= value >>> 33; value *= 0xff51afd7ed558ccdl;
        value ^= value >>> 33; value *= 0xc4ceb9fe1a85ec53l;
        return value ^ value >>> 33;
    }

    enum FeatureCategory {
        PM_AUTHORED,
        TREE,
        SMALL_DECORATION,
        SURFACE_FORMATION,
        PRESERVE,
        UNKNOWN;

        boolean surfaceDecoration() {
            return this == TREE || this == SMALL_DECORATION || this == SURFACE_FORMATION;
        }
    }

    private record Snapshot(List<SiteEnvironmentPlan> zones, Map<Long, List<SiteEnvironmentPlan>> byChunk,
                            IdentityHashMap<ChunkGenerator, Boolean> generators) {
        private static Snapshot empty() { return new Snapshot(List.of(), Map.of(), new IdentityHashMap<>()); }
    }
}
