package io.farfrontier.palemirror.visuals.genesis;

import io.farfrontier.palemirror.api.VisualPoint;
import java.util.Comparator;
import java.util.List;

/** Deterministic terrain score; lower is better and water-heavy sites fail closed. */
public record TerrainCandidate(VisualPoint anchor, int relief, int cutFillCost, int waterSamples, int score) {
    public static TerrainCandidate evaluate(int centerX, int centerZ, List<TerrainSample> samples) {
        if (samples.isEmpty()) throw new IllegalArgumentException("Terrain sample set is empty");
        List<Integer> heights = samples.stream().map(TerrainSample::height).sorted().toList();
        int median = heights.get(heights.size() / 2);
        int min = heights.getFirst();
        int max = heights.getLast();
        int cost = samples.stream().mapToInt(sample -> Math.abs(sample.height() - median)).sum();
        int water = (int) samples.stream().filter(TerrainSample::water).count();
        int score = (max - min) * 1_000 + cost * 8 + water * 100_000;
        return new TerrainCandidate(new VisualPoint(centerX, median, centerZ), max - min, cost, water, score);
    }

    public boolean preferred() { return waterSamples == 0 && relief <= 8; }

    public static Comparator<TerrainCandidate> ordering() {
        return Comparator.comparing(TerrainCandidate::preferred).reversed()
                .thenComparingInt(TerrainCandidate::score)
                .thenComparingInt(value -> value.anchor().x())
                .thenComparingInt(value -> value.anchor().z());
    }
}
