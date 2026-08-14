package io.farfrontier.palemirror.visuals.genesis;

import io.farfrontier.palemirror.api.VisualPoint;
import java.util.ArrayList;
import java.util.List;

/** Pure deterministic selector with cheap horizontal ranking and a hard exact-height budget. */
public final class FrontierSiteSelector {
    static final int EXACT_SAMPLES_PER_CANDIDATE = 5;
    private static final double GOLDEN_ANGLE = Math.PI * (3D - Math.sqrt(5D));
    private final RegionPlacementProfile profile;
    private final DeterministicGenesisWorkers workers;

    public FrontierSiteSelector(RegionPlacementProfile profile) {
        this(profile, null);
    }

    public FrontierSiteSelector(RegionPlacementProfile profile, DeterministicGenesisWorkers workers) {
        this.profile = java.util.Objects.requireNonNull(profile, "profile");
        this.workers = workers;
    }

    public List<SelectedSite> select(long worldSeed, VisualPoint spawn, int count, int mapRadius,
                                     int minimumSpacing, TerrainAccess terrain) {
        return selectWithReserve(worldSeed, spawn, count, 0, mapRadius, minimumSpacing, terrain);
    }

    public List<SelectedSite> selectWithReserve(long worldSeed, VisualPoint spawn, int count, int reserve,
                                                int mapRadius, int minimumSpacing, TerrainAccess terrain) {
        validate(count, mapRadius, minimumSpacing);
        if (reserve < 0 || count + reserve > profile.search().maximumSurveyCandidates()) {
            throw new IllegalArgumentException("region count plus reserve must be between 1 and "
                    + profile.search().maximumSurveyCandidates());
        }
        int targetCandidates = count + reserve;
        // Reserve centers improve downstream site feasibility; they are not regions to be
        // materialized. Letting a large reserve select the large-world search policy turns
        // a small geography-led world into a tens-of-thousands-center scan.
        boolean largeBatch = count >= profile.search().largeBatchThreshold();
        int exactCandidatesPerRegion = profile.search().exactSettlementCandidatesPerRegion(count);
        int surveySpacing = reserve == 0 ? minimumSpacing
                : Math.min(minimumSpacing, profile.search().regionEnvelope());
        List<SelectedSite> selected = new ArrayList<>(targetCandidates);
        RegionPlacementProfile.SearchBand nearBand = profile.search().near();
        int maximumCenterRadius = mapRadius - profile.search().regionEnvelope();
        int nearMaximumDistance = Math.min(nearBand.spawnDistance().maximum(), maximumCenterRadius);
        List<Center> near = landscapeCenters(worldSeed, spawn, nearBand.candidateCount(),
                nearBand.spawnDistance().minimum(), nearMaximumDistance, 0, terrain);
        int nearTarget = Math.min(targetCandidates, profile.search().maximumNearAcceptedRegions()
                + Math.min(profile.search().nearReserveCandidates(), reserve));
        selectFrom(near, nearTarget, surveySpacing, terrain, selected,
                nearTarget * exactCandidatesPerRegion, exactCandidatesPerRegion, true, largeBatch,
                spawn, maximumCenterRadius);
        if (selected.isEmpty()) throw impossible(count, mapRadius, minimumSpacing, 0);
        if (targetCandidates > selected.size()) {
            int remaining = targetCandidates - selected.size();
            DistanceBand remoteBand = profile.search().remote().spawnDistance();
            int maximumDistance = Math.min(remoteBand.maximum(), mapRadius - profile.search().regionEnvelope());
            int minimumDistance = Math.min(maximumDistance - 1,
                    Math.max(remoteBand.minimum(), minimumSpacing + profile.search().remoteSpacingMargin()));
            if (maximumDistance <= minimumDistance) throw impossible(count, mapRadius, minimumSpacing, 1);
            int remoteCount = Math.max(profile.search().remote().candidateCount(),
                    remaining * profile.search().remoteCandidatesPerRegion(count));
            List<Center> remote = landscapeCenters(worldSeed ^ 0x6a09e667f3bcc909L, spawn, remoteCount,
                    minimumDistance, maximumDistance, nearBand.candidateCount(), terrain);
            selectFrom(remote, targetCandidates, surveySpacing, terrain, selected,
                    remaining * exactCandidatesPerRegion, exactCandidatesPerRegion, false, largeBatch,
                    spawn, maximumCenterRadius);
        }
        // Reserve centers are opportunistic. The hard contract is the requested
        // region count; rejecting an otherwise usable world because one optional
        // reserve slot missed the bounded survey defeats the purpose of a reserve.
        if (selected.size() < count) {
            throw impossible(count, mapRadius, minimumSpacing, selected.size());
        }
        return List.copyOf(selected);
    }

    private void selectFrom(List<Center> candidates, int targetCount, int minimumSpacing,
                            TerrainAccess terrain, List<SelectedSite> selected, int exactBudget,
                            int exactCandidatesPerRegion,
                            boolean nearCandidate, boolean largeBatch, VisualPoint spawn, int maximumCenterRadius) {
        java.util.Comparator<RankedCenter> landscapeFirst = java.util.Comparator
                .comparingInt(RankedCenter::landscapeScore).reversed()
                .thenComparingInt(value -> value.biome().terrainPreference())
                .thenComparingInt(RankedCenter::originalIndex);
        java.util.Comparator<RankedCenter> terrainFirst = java.util.Comparator
                .comparingInt((RankedCenter value) -> value.biome().terrainPreference())
                .thenComparing(java.util.Comparator.comparingInt(RankedCenter::landscapeScore).reversed())
                .thenComparingInt(RankedCenter::originalIndex);
        List<RankedCenter> unsorted = mapIndexed(candidates.size(), index -> {
            Center center = candidates.get(index);
            return new RankedCenter(center, footprintBiome(center, terrain), landscapeScore(center, terrain), index);
        });
        List<RankedCenter> ranked = unsorted.stream().filter(value -> profile.settlementTerrain().acceptsBiome(value.biome())
                && value.landscapeScore() >= profile.settlementTerrain().minimumLandscapeScore())
                .sorted(largeBatch ? terrainFirst : landscapeFirst).toList();
        int exactAttempts = 0;
        int cursor = 0;
        List<HorizontalOffset> refinements = profile.search().settlementRefinementOffsets();
        int refinedCandidateCount = Math.multiplyExact(ranked.size(), refinements.size());
        java.util.Set<Long> visitedRefinements = new java.util.HashSet<>();
        while (selected.size() < targetCount && exactAttempts < exactBudget && cursor < refinedCandidateCount) {
            List<SelectedSite> fallbacks = new ArrayList<>(exactCandidatesPerRegion);
            int slotAttempts = 0;
            while (cursor < refinedCandidateCount && slotAttempts < exactCandidatesPerRegion) {
                int refinedIndex = cursor++;
                RankedCenter rankedCenter = ranked.get(refinedIndex / refinements.size());
                HorizontalOffset refinement = refinements.get(refinedIndex % refinements.size());
                Center center = new Center(align(rankedCenter.center().x() + refinement.x()),
                        align(rankedCenter.center().z() + refinement.z()));
                if (!withinRadius(center, spawn, maximumCenterRadius)
                        || !visitedRefinements.add(pack(center.x(), center.z()))) continue;
                if (!separated(center, selected, minimumSpacing)) continue;
                BiomeSample biome = refinement.equals(HorizontalOffset.ORIGIN)
                        ? rankedCenter.biome() : footprintBiome(center, terrain);
                int candidateLandscapeScore = refinement.equals(HorizontalOffset.ORIGIN)
                        ? rankedCenter.landscapeScore() : landscapeScore(center, terrain);
                if (!profile.settlementTerrain().acceptsBiome(biome)
                        || candidateLandscapeScore < profile.settlementTerrain().minimumLandscapeScore()) continue;
                exactAttempts++;
                slotAttempts++;
                TerrainCandidate candidate = detailed(center, terrain);
                if (!profile.settlementTerrain().acceptable(candidate)) {
                    terrain.recordDiscardedCandidate(candidate);
                    continue;
                }
                SelectedSite site = new SelectedSite(candidate, biome.climate(), nearCandidate);
                if (profile.settlementTerrain().preferred(candidate)) {
                    fallbacks.forEach(value -> terrain.recordDiscardedCandidate(value.terrain()));
                    selected.add(site);
                    fallbacks.clear();
                    break;
                }
                fallbacks.add(site);
            }
            if (!fallbacks.isEmpty()) {
                SelectedSite chosen = fallbacks.stream()
                        .min(java.util.Comparator.comparing(SelectedSite::terrain,
                                TerrainCandidate.ordering(profile.settlementTerrain())))
                        .orElseThrow();
                for (SelectedSite candidate : fallbacks) if (candidate != chosen) {
                    terrain.recordDiscardedCandidate(candidate.terrain());
                }
                selected.add(chosen);
            }
        }
    }

    private BiomeSample footprintBiome(Center center, TerrainAccess terrain) {
        int radius = profile.settlementTerrain().surveyRadius();
        BiomeSample centerBiome = terrain.biome(center.x(), center.z());
        boolean water = centerBiome.water()
                || terrain.biome(center.x() - radius, center.z()).water()
                || terrain.biome(center.x() + radius, center.z()).water()
                || terrain.biome(center.x(), center.z() - radius).water()
                || terrain.biome(center.x(), center.z() + radius).water();
        return new BiomeSample(centerBiome.climate(), centerBiome.suitable() && !water,
                water, centerBiome.terrainPreference());
    }

    /** Cheap biome-only evidence that the shelf has both a nearby primary mass and a separate remote mass. */
    private int landscapeScore(Center center, TerrainAccess terrain) {
        if (profile.settlementTerrain().landscapeAffinity()
                != SettlementTerrainPolicy.LandscapeAffinity.MOUNTAIN_FOOTHILL) {
            throw new IllegalStateException("Unsupported landscape affinity "
                    + profile.settlementTerrain().landscapeAffinity());
        }
        java.util.Map<String, Integer> directionMasks = new java.util.LinkedHashMap<>();
        int score = 0;
        int weight = profile.requiredSites().size() * 2;
        for (SitePlacementRequirement site : profile.requiredSites()) {
            int mask = 0;
            for (int direction = 0; direction < 4; direction++) {
                if (mountainOnRay(center, direction, site.landscapeEvidenceDistances(), terrain)) mask |= 1 << direction;
            }
            directionMasks.put(site.role(), mask);
            score += Integer.bitCount(mask) * weight;
            weight = Math.max(2, weight - 2);
        }
        for (SitePlacementRequirement site : profile.requiredSites()) {
            if (site.relatedSiteRole().isBlank() || !site.requireSeparateCardinalSector()) continue;
            int current = directionMasks.getOrDefault(site.role(), 0);
            int reference = directionMasks.getOrDefault(site.relatedSiteRole(), 0);
            if (hasSeparateDirections(reference, current)) score += 8;
        }
        return score;
    }

    private static boolean hasSeparateDirections(int first, int second) {
        for (int a = 0; a < 4; a++) for (int b = 0; b < 4; b++) {
            if ((first & 1 << a) != 0 && (second & 1 << b) != 0 && a != b) return true;
        }
        return false;
    }

    private static boolean mountainOnRay(Center center, int direction, List<Integer> distances,
                                         TerrainAccess terrain) {
        for (int distance : distances) {
            int x = center.x() + (direction == 0 ? distance : direction == 2 ? -distance : 0);
            int z = center.z() + (direction == 1 ? distance : direction == 3 ? -distance : 0);
            if (terrain.biome(x, z).mountainEvidence()) return true;
        }
        return false;
    }

    private TerrainCandidate detailed(Center center, TerrainAccess terrain) {
        List<TerrainSample> samples = new ArrayList<>(EXACT_SAMPLES_PER_CANDIDATE);
        samples.add(terrain.exactSample(center.x(), center.z()));
        int radius = profile.settlementTerrain().surveyRadius();
        samples.add(terrain.exactSample(center.x() - radius, center.z()));
        samples.add(terrain.exactSample(center.x() + radius, center.z()));
        samples.add(terrain.exactSample(center.x(), center.z() - radius));
        samples.add(terrain.exactSample(center.x(), center.z() + radius));
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

    /**
     * Converts cheap mountain-biome samples into nearby shelf candidates before any noise height is requested.
     * Random shelf samples remain as deterministic fallback candidates for broad modded mountain biomes.
     */
    private List<Center> landscapeCenters(long seed, VisualPoint spawn, int count, int minimumDistance,
                                          int maximumDistance, int addressOffset, TerrainAccess terrain) {
        if (profile.settlementTerrain().landscapeAffinity()
                != SettlementTerrainPolicy.LandscapeAffinity.MOUNTAIN_FOOTHILL) {
            throw new IllegalStateException("Unsupported landscape affinity "
                    + profile.settlementTerrain().landscapeAffinity());
        }
        List<Center> samples = candidateCenters(seed, spawn, count, minimumDistance, maximumDistance, addressOffset);
        java.util.LinkedHashSet<Center> candidates = new java.util.LinkedHashSet<>();
        for (Center sample : samples) {
            if (terrain.biome(sample.x(), sample.z()).mountainEvidence()) {
                for (int distance : profile.search().landscapeProjectionDistances()) for (int direction = 0; direction < 4; direction++) {
                    int x = align(sample.x() + (direction == 0 ? distance : direction == 2 ? -distance : 0));
                    int z = align(sample.z() + (direction == 1 ? distance : direction == 3 ? -distance : 0));
                    long dx = (long) x - spawn.x();
                    long dz = (long) z - spawn.z();
                    long radialSquared = dx * dx + dz * dz;
                    if (radialSquared >= (long) minimumDistance * minimumDistance
                            && radialSquared <= (long) maximumDistance * maximumDistance) candidates.add(new Center(x, z));
                }
            }
            candidates.add(sample);
        }
        return List.copyOf(candidates);
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

    private static boolean withinRadius(Center candidate, VisualPoint spawn, int radius) {
        long dx = (long) candidate.x() - spawn.x();
        long dz = (long) candidate.z() - spawn.z();
        return dx * dx + dz * dz <= (long) radius * radius;
    }

    private void validate(int count, int mapRadius, int minimumSpacing) {
        if (count < 1 || count > profile.search().maximumRegions()) throw new IllegalArgumentException(
                "region count must be between 1 and " + profile.search().maximumRegions());
        if (mapRadius < profile.search().minimumMapRadius()) throw new IllegalArgumentException(
                "map radius must be at least " + profile.search().minimumMapRadius() + " blocks");
        if (minimumSpacing < profile.search().minimumRegionSpacing()) throw new IllegalArgumentException(
                "region spacing must be at least " + profile.search().minimumRegionSpacing() + " blocks");
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

    private <T> List<T> mapIndexed(int size, java.util.function.IntFunction<? extends T> mapper) {
        if (workers != null) return workers.mapIndexed(size, mapper);
        List<T> result = new ArrayList<>(size);
        for (int index = 0; index < size; index++) result.add(mapper.apply(index));
        return List.copyOf(result);
    }

    public interface TerrainAccess {
        BiomeSample biome(int x, int z);
        TerrainSample exactSample(int x, int z);
        default boolean exactWater(int x, int z) { return exactSample(x, z).water(); }
        default void recordDiscardedCandidate(TerrainCandidate candidate) { }
    }

    public record BiomeSample(FrontierClimate climate, boolean suitable, boolean water, int terrainPreference,
                              boolean mountainEvidence) {
        public BiomeSample(FrontierClimate climate, boolean suitable, boolean water, int terrainPreference) {
            this(climate, suitable, water, terrainPreference, false);
        }
        public BiomeSample {
            if (terrainPreference < 0) throw new IllegalArgumentException("terrainPreference must be non-negative");
        }
    }
    public record SelectedSite(TerrainCandidate terrain, FrontierClimate climate, boolean nearCandidate) {
        public SelectedSite(TerrainCandidate terrain, FrontierClimate climate) {
            this(terrain, climate, false);
        }
    }
    private record Center(int x, int z) { }
    private record RankedCenter(Center center, BiomeSample biome, int landscapeScore, int originalIndex) { }
}
