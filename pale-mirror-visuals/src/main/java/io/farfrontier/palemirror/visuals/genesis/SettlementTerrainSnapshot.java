package io.farfrontier.palemirror.visuals.genesis;

import io.farfrontier.palemirror.api.VisualBounds;
import io.farfrontier.palemirror.api.VisualPoint;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.IntBinaryOperator;

/** Immutable coarse settlement relief plus a bounded exact pad validator. */
final class SettlementTerrainSnapshot {
    static final int GRID_STEP = 16;
    static final int MAX_UNIQUE_PROBES = 768;
    private final VisualPoint anchor;
    private final Map<Long, Integer> coarseHeights;
    private final IntBinaryOperator exactHeight;
    private final BiPredicate<Integer, Integer> exactWater;
    private final Map<Long, Integer> exactHeights = new java.util.HashMap<>();
    private final Map<Long, Boolean> exactWaters = new java.util.HashMap<>();
    private final Map<PadKey, PadResolution> padResolutions = new java.util.HashMap<>();
    private final java.util.Set<Long> exactColumns = new java.util.HashSet<>();

    SettlementTerrainSnapshot(VisualPoint anchor, Map<Long, Integer> coarseHeights,
                              IntBinaryOperator exactHeight, BiPredicate<Integer, Integer> exactWater) {
        this.anchor = anchor;
        this.coarseHeights = Map.copyOf(coarseHeights);
        this.exactHeight = exactHeight;
        this.exactWater = exactWater;
        if (coarseHeights.isEmpty() || coarseHeights.size() > MAX_UNIQUE_PROBES) {
            throw new IllegalArgumentException("settlement snapshot probe count is invalid: " + coarseHeights.size());
        }
    }

    static SettlementTerrainSnapshot flat(VisualPoint anchor) {
        return new SettlementTerrainSnapshot(anchor, Map.of(key(anchor.x(), anchor.z()), anchor.y()),
                (x, z) -> anchor.y(), (x, z) -> false);
    }

    int approximateHeight(int x, int z) {
        return coarseHeights.entrySet().stream().min(Comparator
                        .comparingLong((Map.Entry<Long, Integer> entry) -> distance(entry.getKey(), x, z))
                        .thenComparingLong(Map.Entry::getKey))
                .map(Map.Entry::getValue).orElse(anchor.y());
    }

    /** Minecraft base height is first air; authored paving owns the solid block below it. */
    int surfaceHeight(int x, int z) {
        return approximateHeight(x, z) - 1;
    }

    PadResolution resolvePad(VisualBounds horizontal) {
        PadKey key = new PadKey(horizontal.min().x(), horizontal.max().x(),
                horizontal.min().z(), horizontal.max().z());
        PadResolution cached = padResolutions.get(key);
        if (cached != null) return cached;
        int minX = horizontal.min().x();
        int maxX = horizontal.max().x();
        int minZ = horizontal.min().z();
        int maxZ = horizontal.max().z();
        int centerX = (minX + maxX) / 2;
        int centerZ = (minZ + maxZ) / 2;
        List<VisualPoint> samples = new ArrayList<>(9);
        for (int x : new int[]{minX, centerX, maxX}) for (int z : new int[]{minZ, centerZ, maxZ}) {
            int height = exactHeightAt(x, z);
            samples.add(new VisualPoint(x, height, z));
        }
        List<Integer> heights = samples.stream().map(VisualPoint::y).sorted().toList();
        int target = heights.get(heights.size() / 2);
        int minimum = heights.getFirst();
        int maximum = heights.getLast();
        if (maximum - target > 4) return remember(key,
                new PadResolution(target, maximum - minimum, "cut"));
        if (target - minimum > 4) return remember(key,
                new PadResolution(target, maximum - minimum, "fill"));
        for (VisualPoint sample : samples) {
            if (exactWaterAt(sample.x(), sample.z())) return remember(key,
                    new PadResolution(target, maximum - minimum, "water"));
        }
        return remember(key, new PadResolution(target, maximum - minimum, ""));
    }

    boolean roughlyAccepts(VisualBounds horizontal) {
        int minX = horizontal.min().x();
        int maxX = horizontal.max().x();
        int minZ = horizontal.min().z();
        int maxZ = horizontal.max().z();
        List<Integer> heights = new ArrayList<>(9);
        for (int x : new int[]{minX, (minX + maxX) / 2, maxX}) {
            for (int z : new int[]{minZ, (minZ + maxZ) / 2, maxZ}) heights.add(approximateHeight(x, z));
        }
        heights.sort(Integer::compareTo);
        int target = heights.get(heights.size() / 2);
        return heights.getLast() - target <= 4 && target - heights.getFirst() <= 4;
    }

    int exactProbes() { return exactColumns.size(); }

    boolean waterAt(int x, int z) {
        return exactWaterAt(x, z);
    }

    int exactSurfaceHeight(int x, int z) {
        return exactHeightAt(x, z) - 1;
    }

    /**
     * Authoritative full-masterplan check. The earlier selector uses a bounded
     * 24-block grid for ranking; this denser immutable snapshot prevents a
     * narrow canyon or broken shelf between those samples from reaching the
     * layout grammar.
     */
    TerrainCandidate requireSuitable(SettlementTerrainPolicy policy) {
        List<TerrainSample> samples = coarseHeights.entrySet().stream()
                .map(entry -> new TerrainSample((int) (entry.getKey() >> 32), (int) (long) entry.getKey(),
                        entry.getValue(), false))
                .toList();
        TerrainCandidate candidate = TerrainCandidate.evaluate(anchor.x(), anchor.z(), samples,
                policy.surfaceSuitability().buildableHeightTolerance());
        if (!policy.acceptable(candidate)) {
            LandscapeSurfaceQuality quality = candidate.surfaceQuality();
            throw new DryMineSiteUnavailableException("Township surface rejected at "
                    + anchor.x() + "," + anchor.z() + ": relief=" + candidate.relief()
                    + ", localGrade=" + quality.maximumLocalGrade()
                    + ", roughness=" + quality.maximumRoughness()
                    + ", depression=" + quality.depressionDepth()
                    + ", buildable=" + quality.buildablePercent() + "%");
        }
        return candidate;
    }

    private int exactHeightAt(int x, int z) {
        long key = key(x, z);
        return exactHeights.computeIfAbsent(key, ignored -> {
            admit(key);
            return exactHeight.applyAsInt(x, z);
        });
    }

    private boolean exactWaterAt(int x, int z) {
        long key = key(x, z);
        return exactWaters.computeIfAbsent(key, ignored -> {
            admit(key);
            return exactWater.test(x, z);
        });
    }

    private void admit(long key) {
        if (exactColumns.contains(key)) return;
        if (coarseHeights.size() + exactColumns.size() >= MAX_UNIQUE_PROBES) {
            throw new DryMineSiteUnavailableException("Settlement terrain snapshot exceeded "
                    + MAX_UNIQUE_PROBES + " bounded probes");
        }
        exactColumns.add(key);
    }

    private PadResolution remember(PadKey key, PadResolution resolution) {
        padResolutions.put(key, resolution);
        return resolution;
    }

    private static long distance(long key, int x, int z) {
        long dx = (long) (int) (key >> 32) - x;
        long dz = (long) (int) key - z;
        return dx * dx + dz * dz;
    }

    static long key(int x, int z) { return (long) x << 32 ^ Integer.toUnsignedLong(z); }

    record PadResolution(int targetY, int relief, String failure) {
        boolean accepted() { return failure.isEmpty(); }
    }

    private record PadKey(int minX, int maxX, int minZ, int maxZ) { }
}
