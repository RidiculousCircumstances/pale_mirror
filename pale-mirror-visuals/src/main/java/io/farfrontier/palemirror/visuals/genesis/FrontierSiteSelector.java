package io.farfrontier.palemirror.visuals.genesis;

import io.farfrontier.palemirror.api.VisualPoint;
import java.util.ArrayList;
import java.util.List;

/** Pure deterministic selector with cheap horizontal ranking and a hard exact-height budget. */
public final class FrontierSiteSelector {
    static final int EXACT_SAMPLES_PER_CANDIDATE = 5;
    static final int MAX_EXACT_CANDIDATES_PER_REGION = 6;
    private static final int SURVEY_RADIUS = 72;
    private static final int NEAR_CANDIDATES = 24;
    private static final int CANDIDATES_PER_REMOTE_REGION = 32;
    private static final int REMOTE_CANDIDATE_FLOOR = 128;
    private static final int REGION_ENVELOPE = 900;
    private static final double GOLDEN_ANGLE = Math.PI * (3D - Math.sqrt(5D));

    public List<SelectedSite> select(long worldSeed, VisualPoint spawn, int count, int mapRadius,
                                     int minimumSpacing, TerrainAccess terrain) {
        validate(count, mapRadius, minimumSpacing);
        List<SelectedSite> selected = new ArrayList<>(count);
        List<Center> near = candidateCenters(worldSeed, spawn, NEAR_CANDIDATES, 1_024, 2_048, 0);
        selectFrom(near, 1, minimumSpacing, terrain, selected,
                MAX_EXACT_CANDIDATES_PER_REGION);
        if (selected.isEmpty()) throw impossible(count, mapRadius, minimumSpacing, 0);
        if (count > 1) {
            int remaining = count - 1;
            int maximumDistance = mapRadius - REGION_ENVELOPE;
            int minimumDistance = Math.min(maximumDistance - 1, Math.max(2_600, minimumSpacing + 1_000));
            if (maximumDistance <= minimumDistance) throw impossible(count, mapRadius, minimumSpacing, 1);
            int remoteCount = Math.max(REMOTE_CANDIDATE_FLOOR, remaining * CANDIDATES_PER_REMOTE_REGION);
            List<Center> remote = candidateCenters(worldSeed ^ 0x6a09e667f3bcc909L, spawn, remoteCount,
                    minimumDistance, maximumDistance, NEAR_CANDIDATES);
            selectFrom(remote, count, minimumSpacing, terrain, selected,
                    remaining * MAX_EXACT_CANDIDATES_PER_REGION);
        }
        if (selected.size() != count) throw impossible(count, mapRadius, minimumSpacing, selected.size());
        return List.copyOf(selected);
    }

    private static void selectFrom(List<Center> candidates, int targetCount, int minimumSpacing,
                                   TerrainAccess terrain, List<SelectedSite> selected, int exactBudget) {
        List<RankedCenter> ranked = java.util.stream.IntStream.range(0, candidates.size()).mapToObj(index -> {
            Center center = candidates.get(index);
            return new RankedCenter(center, footprintBiome(center, terrain), index);
        }).filter(value -> value.biome().suitable()).sorted(java.util.Comparator
                .comparingInt((RankedCenter value) -> value.biome().terrainPreference())
                .thenComparingInt(RankedCenter::originalIndex)).toList();
        int exactAttempts = 0;
        int cursor = 0;
        while (selected.size() < targetCount && exactAttempts < exactBudget && cursor < ranked.size()) {
            List<SelectedSite> fallbacks = new ArrayList<>(MAX_EXACT_CANDIDATES_PER_REGION);
            int slotAttempts = 0;
            while (cursor < ranked.size() && slotAttempts < MAX_EXACT_CANDIDATES_PER_REGION) {
                RankedCenter rankedCenter = ranked.get(cursor++);
                Center center = rankedCenter.center();
                if (!separated(center, selected, minimumSpacing)) continue;
                BiomeSample biome = rankedCenter.biome();
                exactAttempts++;
                slotAttempts++;
                TerrainCandidate candidate = detailed(center, terrain);
                if (!candidate.acceptable()) {
                    terrain.recordDiscardedCandidate(candidate);
                    continue;
                }
                SelectedSite site = new SelectedSite(candidate, biome.climate());
                if (candidate.preferred()) {
                    fallbacks.forEach(value -> terrain.recordDiscardedCandidate(value.terrain()));
                    selected.add(site);
                    fallbacks.clear();
                    break;
                }
                fallbacks.add(site);
            }
            if (!fallbacks.isEmpty()) {
                SelectedSite chosen = fallbacks.stream()
                        .min(java.util.Comparator.comparing(SelectedSite::terrain, TerrainCandidate.ordering()))
                        .orElseThrow();
                for (SelectedSite candidate : fallbacks) if (candidate != chosen) {
                    terrain.recordDiscardedCandidate(candidate.terrain());
                }
                selected.add(chosen);
            }
        }
    }

    private static BiomeSample footprintBiome(Center center, TerrainAccess terrain) {
        BiomeSample centerBiome = terrain.biome(center.x(), center.z());
        boolean water = centerBiome.water()
                || terrain.biome(center.x() - SURVEY_RADIUS, center.z()).water()
                || terrain.biome(center.x() + SURVEY_RADIUS, center.z()).water()
                || terrain.biome(center.x(), center.z() - SURVEY_RADIUS).water()
                || terrain.biome(center.x(), center.z() + SURVEY_RADIUS).water();
        return new BiomeSample(centerBiome.climate(), centerBiome.suitable() && !water,
                water, centerBiome.terrainPreference());
    }

    private static TerrainCandidate detailed(Center center, TerrainAccess terrain) {
        List<TerrainSample> samples = new ArrayList<>(EXACT_SAMPLES_PER_CANDIDATE);
        samples.add(terrain.exactSample(center.x(), center.z()));
        samples.add(terrain.exactSample(center.x() - SURVEY_RADIUS, center.z()));
        samples.add(terrain.exactSample(center.x() + SURVEY_RADIUS, center.z()));
        samples.add(terrain.exactSample(center.x(), center.z() - SURVEY_RADIUS));
        samples.add(terrain.exactSample(center.x(), center.z() + SURVEY_RADIUS));
        return TerrainCandidate.evaluate(center.x(), center.z(), samples);
    }

    private static List<Center> candidateCenters(long seed, VisualPoint spawn, int count, int minimumDistance,
                                                  int maximumDistance, int addressOffset) {
        List<Center> result = new ArrayList<>(count);
        java.util.Set<Long> unique = new java.util.LinkedHashSet<>();
        double phase = unit(mix(seed)) * Math.PI * 2D;
        for (int index = 0; result.size() < count && index < count * 3; index++) {
            long address = mix(seed + (long) (index + addressOffset) * 0x9E3779B97F4A7C15L);
            double fraction = (index + 0.5D) / count;
            double radial = Math.sqrt(minimumDistance * (double) minimumDistance
                    + fraction * (maximumDistance * (double) maximumDistance
                    - minimumDistance * (double) minimumDistance));
            double angle = phase + index * GOLDEN_ANGLE + (unit(address) - 0.5D) * 0.24D;
            int x = align(spawn.x() + (int) Math.round(Math.cos(angle) * radial));
            int z = align(spawn.z() + (int) Math.round(Math.sin(angle) * radial));
            if (unique.add(pack(x, z))) result.add(new Center(x, z));
        }
        return List.copyOf(result);
    }

    private static boolean separated(Center candidate, List<SelectedSite> selected, int spacing) {
        long required = (long) spacing * spacing;
        for (SelectedSite existing : selected) {
            long dx = (long) candidate.x() - existing.terrain().anchor().x();
            long dz = (long) candidate.z() - existing.terrain().anchor().z();
            if (dx * dx + dz * dz < required) return false;
        }
        return true;
    }

    private static void validate(int count, int mapRadius, int minimumSpacing) {
        if (count < 1 || count > 64) throw new IllegalArgumentException("region count must be between 1 and 64");
        if (mapRadius < 3_000) throw new IllegalArgumentException("map radius must be at least 3000 blocks");
        if (minimumSpacing < 1_024) throw new IllegalArgumentException("region spacing must be at least 1024 blocks");
    }

    private static IllegalStateException impossible(int count, int radius, int spacing, int selected) {
        return new IllegalStateException("Cannot place " + count + " authored regions inside radius " + radius
                + " with spacing " + spacing + "; only " + selected + " valid sites were selected within the exact survey budget");
    }

    private static int align(int coordinate) { return Math.floorDiv(coordinate, 16) * 16 + 8; }
    private static long pack(int x, int z) { return (long) x << 32 ^ Integer.toUnsignedLong(z); }
    private static double unit(long value) { return (value >>> 11) * 0x1.0p-53; }

    private static long mix(long value) {
        value ^= value >>> 30; value *= 0xbf58476d1ce4e5b9L;
        value ^= value >>> 27; value *= 0x94d049bb133111ebL;
        return value ^ value >>> 31;
    }

    public interface TerrainAccess {
        BiomeSample biome(int x, int z);
        TerrainSample exactSample(int x, int z);
        default void recordDiscardedCandidate(TerrainCandidate candidate) { }
    }

    public record BiomeSample(FrontierClimate climate, boolean suitable, boolean water, int terrainPreference) {
        public BiomeSample {
            if (terrainPreference < 0) throw new IllegalArgumentException("terrainPreference must be non-negative");
        }
    }
    public record SelectedSite(TerrainCandidate terrain, FrontierClimate climate) { }
    private record Center(int x, int z) { }
    private record RankedCenter(Center center, BiomeSample biome, int originalIndex) { }
}
