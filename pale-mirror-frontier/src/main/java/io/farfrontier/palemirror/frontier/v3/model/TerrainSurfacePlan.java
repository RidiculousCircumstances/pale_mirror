package io.farfrontier.palemirror.frontier.v3.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable surveyed support datum consumed by topology and materialization compilers. */
public record TerrainSurfacePlan(int baselineSupportY, Map<TerrainColumn, Integer> surveyedSupportY) {
    public static final int MAX_SURVEYED_COLUMNS = 4_096;

    public TerrainSurfacePlan {
        Objects.requireNonNull(surveyedSupportY, "surveyed support columns");
        if (surveyedSupportY.size() > MAX_SURVEYED_COLUMNS) throw new IllegalArgumentException("terrain survey column limit exceeded");
        Map<TerrainColumn, Integer> copy = new LinkedHashMap<>();
        surveyedSupportY.forEach((column, supportY) -> {
            TerrainColumn key = Objects.requireNonNull(column, "terrain survey column");
            int value = Objects.requireNonNull(supportY, "terrain support y");
            if (value == baselineSupportY) throw new IllegalArgumentException("terrain survey may not retain a baseline-equivalent column");
            if (copy.put(key, value) != null) throw new IllegalArgumentException("duplicate terrain survey column");
        });
        surveyedSupportY = Map.copyOf(copy);
    }

    public static TerrainSurfacePlan uniform(int supportY) { return new TerrainSurfacePlan(supportY, Map.of()); }

    public int supportYAt(int x, int z) { return surveyedSupportY.getOrDefault(new TerrainColumn(x, z), baselineSupportY); }

    public TerrainSurfacePlan withSurveyedSupport(int x, int z, int supportY) {
        Map<TerrainColumn, Integer> next = new LinkedHashMap<>(surveyedSupportY);
        TerrainColumn column = new TerrainColumn(x, z);
        if (supportY == baselineSupportY) next.remove(column); else next.put(column, supportY);
        return new TerrainSurfacePlan(baselineSupportY, next);
    }

    /** Stable canonical text independent of map insertion order. */
    public String canonicalText() {
        StringBuilder text = new StringBuilder("base=").append(baselineSupportY);
        surveyedSupportY.entrySet().stream().sorted(Map.Entry.comparingByKey(java.util.Comparator
                .comparingInt((TerrainColumn column) -> column.x()).thenComparingInt(TerrainColumn::z)))
                .forEach(entry -> text.append('|').append(entry.getKey().x()).append(',').append(entry.getKey().z())
                        .append('=').append(entry.getValue()));
        return text.toString();
    }
}
