package io.farfrontier.palemirror.visuals.genesis;

import io.farfrontier.palemirror.api.VisualPoint;
import java.util.Comparator;
import java.util.List;

/** Deterministic terrain score; lower is better and water-heavy sites fail closed. */
public record TerrainCandidate(VisualPoint anchor, int relief, int cutFillCost, int waterSamples, int score,
                               int slopeX, int slopeZ) {
    public TerrainCandidate(VisualPoint anchor, int relief, int cutFillCost, int waterSamples, int score) {
        this(anchor, relief, cutFillCost, waterSamples, score, 0, 0);
    }

    public static TerrainCandidate evaluate(int centerX, int centerZ, List<TerrainSample> samples) {
        if (samples.isEmpty()) throw new IllegalArgumentException("Terrain sample set is empty");
        List<Integer> heights = samples.stream().map(TerrainSample::height).sorted().toList();
        int median = heights.get(heights.size() / 2);
        int min = heights.getFirst();
        int max = heights.getLast();
        int cost = samples.stream().mapToInt(sample -> Math.abs(sample.height() - median)).sum();
        int water = (int) samples.stream().filter(TerrainSample::water).count();
        int score = (max - min) * 1_000 + cost * 8 + water * 100_000;
        int west = heightAt(samples, samples.stream().mapToInt(TerrainSample::x).min().orElse(centerX), centerZ, median);
        int east = heightAt(samples, samples.stream().mapToInt(TerrainSample::x).max().orElse(centerX), centerZ, median);
        int north = heightAt(samples, centerX, samples.stream().mapToInt(TerrainSample::z).min().orElse(centerZ), median);
        int south = heightAt(samples, centerX, samples.stream().mapToInt(TerrainSample::z).max().orElse(centerZ), median);
        return new TerrainCandidate(new VisualPoint(centerX, median, centerZ), max - min, cost, water, score,
                east - west, south - north);
    }

    public int uphillQuarterTurns() {
        if (Math.abs(slopeX) >= Math.abs(slopeZ)) return slopeX >= 0 ? 0 : 2;
        return slopeZ >= 0 ? 1 : 3;
    }

    private static int heightAt(List<TerrainSample> samples, int x, int z, int fallback) {
        return samples.stream().filter(value -> value.x() == x && value.z() == z)
                .mapToInt(TerrainSample::height).findFirst().orElse(fallback);
    }

    public static Comparator<TerrainCandidate> ordering(SettlementTerrainPolicy policy) {
        return Comparator.comparing((TerrainCandidate value) -> policy.preferred(value)).reversed()
                .thenComparingInt(TerrainCandidate::score)
                .thenComparingInt(value -> value.anchor().x())
                .thenComparingInt(value -> value.anchor().z());
    }
}
