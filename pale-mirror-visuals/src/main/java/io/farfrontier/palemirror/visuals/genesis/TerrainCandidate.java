package io.farfrontier.palemirror.visuals.genesis;

import io.farfrontier.palemirror.api.VisualPoint;
import java.util.Comparator;
import java.util.List;

/** Deterministic terrain score; lower is better and water-heavy sites fail closed. */
public record TerrainCandidate(VisualPoint anchor, int relief, int cutFillCost, int waterSamples, int score,
                               int slopeX, int slopeZ, LandscapeSurfaceQuality surfaceQuality) {
    public TerrainCandidate(VisualPoint anchor, int relief, int cutFillCost, int waterSamples, int score) {
        this(anchor, relief, cutFillCost, waterSamples, score, 0, 0, LandscapeSurfaceQuality.pristine());
    }

    public TerrainCandidate(VisualPoint anchor, int relief, int cutFillCost, int waterSamples, int score,
                            int slopeX, int slopeZ) {
        this(anchor, relief, cutFillCost, waterSamples, score, slopeX, slopeZ,
                LandscapeSurfaceQuality.pristine());
    }

    public TerrainCandidate {
        if (anchor == null || surfaceQuality == null) {
            throw new IllegalArgumentException("terrain candidate anchor and surface quality are required");
        }
    }

    public static TerrainCandidate evaluate(int centerX, int centerZ, List<TerrainSample> samples) {
        return evaluate(centerX, centerZ, samples, 2);
    }

    public static TerrainCandidate evaluate(int centerX, int centerZ, List<TerrainSample> samples,
                                            int buildableHeightTolerance) {
        if (samples.isEmpty()) throw new IllegalArgumentException("Terrain sample set is empty");
        if (buildableHeightTolerance < 0) {
            throw new IllegalArgumentException("buildable height tolerance must be non-negative");
        }
        List<Integer> heights = samples.stream().map(TerrainSample::height).sorted().toList();
        int median = heights.get(heights.size() / 2);
        int min = heights.getFirst();
        int max = heights.getLast();
        int cost = samples.stream().mapToInt(sample -> Math.abs(sample.height() - median)).sum();
        int water = (int) samples.stream().filter(TerrainSample::water).count();
        LandscapeSurfaceQuality surface = surfaceQuality(samples, buildableHeightTolerance);
        int score = (max - min) * 1_000 + cost * 8 + water * 100_000
                + surface.maximumLocalGrade() * 256
                + surface.maximumRoughness() * 128
                + surface.depressionDepth() * 256
                + (100 - surface.buildablePercent()) * 16;
        int west = heightAt(samples, samples.stream().mapToInt(TerrainSample::x).min().orElse(centerX), centerZ, median);
        int east = heightAt(samples, samples.stream().mapToInt(TerrainSample::x).max().orElse(centerX), centerZ, median);
        int north = heightAt(samples, centerX, samples.stream().mapToInt(TerrainSample::z).min().orElse(centerZ), median);
        int south = heightAt(samples, centerX, samples.stream().mapToInt(TerrainSample::z).max().orElse(centerZ), median);
        return new TerrainCandidate(new VisualPoint(centerX, median, centerZ), max - min, cost, water, score,
                east - west, south - north, surface);
    }

    private static LandscapeSurfaceQuality surfaceQuality(List<TerrainSample> samples, int tolerance) {
        java.util.Map<Integer, List<TerrainSample>> rows = new java.util.TreeMap<>();
        java.util.Map<Integer, List<TerrainSample>> columns = new java.util.TreeMap<>();
        for (TerrainSample sample : samples) {
            rows.computeIfAbsent(sample.z(), ignored -> new java.util.ArrayList<>()).add(sample);
            columns.computeIfAbsent(sample.x(), ignored -> new java.util.ArrayList<>()).add(sample);
        }
        int maximumGrade = 0;
        int cliffEdges = 0;
        java.util.Map<Long, Integer> residuals = new java.util.HashMap<>();
        java.util.Map<Long, Integer> depressions = new java.util.HashMap<>();
        for (List<TerrainSample> line : rows.values()) {
            line.sort(java.util.Comparator.comparingInt(TerrainSample::x));
            Grade grade = measureLine(line, true, residuals, depressions);
            maximumGrade = Math.max(maximumGrade, grade.maximum());
            cliffEdges += grade.cliffs();
        }
        for (List<TerrainSample> line : columns.values()) {
            line.sort(java.util.Comparator.comparingInt(TerrainSample::z));
            Grade grade = measureLine(line, false, residuals, depressions);
            maximumGrade = Math.max(maximumGrade, grade.maximum());
            cliffEdges += grade.cliffs();
        }
        int maximumRoughness = residuals.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        int depressionDepth = depressions.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        long buildable = samples.stream().filter(sample -> residuals.getOrDefault(key(sample), 0) <= tolerance)
                .count();
        int buildablePercent = (int) Math.floorDiv(buildable * 100L, samples.size());
        return new LandscapeSurfaceQuality(maximumGrade, maximumRoughness, depressionDepth,
                buildablePercent, cliffEdges);
    }

    private static Grade measureLine(List<TerrainSample> line, boolean horizontal,
                                     java.util.Map<Long, Integer> residuals,
                                     java.util.Map<Long, Integer> depressions) {
        int maximum = 0;
        int cliffs = 0;
        for (int index = 1; index < line.size(); index++) {
            TerrainSample previous = line.get(index - 1);
            TerrainSample current = line.get(index);
            int distance = Math.abs((horizontal ? current.x() - previous.x() : current.z() - previous.z()));
            if (distance == 0) continue;
            int normalized = ceilDiv(Math.abs(current.height() - previous.height()) * 16, distance);
            maximum = Math.max(maximum, normalized);
            if (normalized > 4) cliffs++;
        }
        for (int index = 1; index + 1 < line.size(); index++) {
            TerrainSample previous = line.get(index - 1);
            TerrainSample current = line.get(index);
            TerrainSample next = line.get(index + 1);
            int previousCoordinate = horizontal ? previous.x() : previous.z();
            int currentCoordinate = horizontal ? current.x() : current.z();
            int nextCoordinate = horizontal ? next.x() : next.z();
            int span = nextCoordinate - previousCoordinate;
            if (span == 0) continue;
            long weighted = (long) previous.height() * (nextCoordinate - currentCoordinate)
                    + (long) next.height() * (currentCoordinate - previousCoordinate);
            int expected = roundedDivide(weighted, span);
            int residual = Math.abs(current.height() - expected);
            int depression = Math.max(0, expected - current.height());
            residuals.merge(key(current), residual, Math::max);
            depressions.merge(key(current), depression, Math::max);
        }
        return new Grade(maximum, cliffs);
    }

    private static int ceilDiv(int value, int divisor) {
        return value == 0 ? 0 : 1 + Math.floorDiv(value - 1, divisor);
    }

    private static int roundedDivide(long value, int divisor) {
        return (int) Math.floorDiv(value + divisor / 2L, divisor);
    }

    private static long key(TerrainSample sample) {
        return (long) sample.x() << 32 ^ Integer.toUnsignedLong(sample.z());
    }

    private record Grade(int maximum, int cliffs) { }

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
