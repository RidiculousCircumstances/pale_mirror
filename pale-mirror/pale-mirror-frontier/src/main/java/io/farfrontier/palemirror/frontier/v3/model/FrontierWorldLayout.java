package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;

/** Explicit immutable genesis placement. All geometry, recovery and worldgen
 * consumers receive the same ruleset value; coordinates are not inferred from IDs. */
public record FrontierWorldLayout(WorldBounds bounds, List<BlockPosition> settlements,
        List<String> settlementNames, Roads roads, Optional<Set<Integer>> extractionProducers, Terrain terrain, ProductionSites productionSites) {
    public enum ProductionSites { ADJACENT, DISPERSED }
    public FrontierWorldLayout(WorldBounds bounds,List<BlockPosition> settlements,List<String> names,Roads roads,
            Optional<Set<Integer>> extractionProducers,Terrain terrain) {
        this(bounds,settlements,names,roads,extractionProducers,terrain,ProductionSites.ADJACENT);
    }
    public FrontierWorldLayout withProductionSites(ProductionSites mode) {
        return new FrontierWorldLayout(bounds,settlements,settlementNames,roads,extractionProducers,terrain,mode);
    }
    public enum Terrain { FLAT, RELIEF }
    public enum Roads { HISTORICAL_GRID, LOCAL_ACCESS }
    public record RoadLink(int from, int to) { }
    private static final List<String> HISTORICAL_NAMES = List.of("Northwatch", "Stonefield", "Dawnbridge", "Redwillow",
            "Ashcross", "Hearthvale", "Clearwater", "Ironmeadow", "Southgate", "Mossbrook", "Westhaven", "Sunreach");
    public FrontierWorldLayout(WorldBounds bounds, List<BlockPosition> settlements, List<String> names, Roads roads,
            Optional<Set<Integer>> producers) { this(bounds, settlements, names, roads, producers, Terrain.FLAT); }
    public FrontierWorldLayout(WorldBounds bounds, List<BlockPosition> settlements, List<String> names, Roads roads) {
        this(bounds, settlements, names, roads, Optional.empty());
    }
    public FrontierWorldLayout(WorldBounds bounds, List<BlockPosition> settlements) {
        this(bounds, settlements, names(settlements.size()), settlements.size() == 12 ? Roads.HISTORICAL_GRID : Roads.LOCAL_ACCESS);
    }
    private static List<String> names(int count) {
        var result = new ArrayList<String>();
        for (int i = 0; i < count; i++) result.add(i < HISTORICAL_NAMES.size() ? HISTORICAL_NAMES.get(i) : "Frontier " + (i + 1));
        return List.copyOf(result);
    }
    public FrontierWorldLayout {
        Objects.requireNonNull(productionSites); Objects.requireNonNull(bounds); settlements = List.copyOf(settlements);
        settlementNames = List.copyOf(settlementNames); Objects.requireNonNull(roads); Objects.requireNonNull(terrain);
        extractionProducers = Objects.requireNonNull(extractionProducers).map(Set::copyOf);
        int count = settlements.size();
        if (extractionProducers.stream().flatMap(Set::stream).anyMatch(i -> i < 0 || i >= count))
            throw new IllegalArgumentException("mineral content names an absent settlement placement");
        if (settlements.isEmpty() || settlements.size() > SimulationCapacity.MAX_SETTLEMENTS
                || settlementNames.size() != settlements.size() || settlementNames.stream().anyMatch(String::isBlank)
                || settlementNames.stream().distinct().count() != settlementNames.size()
                || roads == Roads.HISTORICAL_GRID && settlements.size() != 12 || settlements.stream().distinct().count() != settlements.size()
                || settlements.stream().anyMatch(p -> p.y() != 0 || !bounds.contains(p)))
            throw new IllegalArgumentException("invalid graybox settlement layout");
    }
    public static FrontierWorldLayout compact() {
        return new FrontierWorldLayout(new WorldBounds(-512, -512, 1024, 1024), positions(new int[][] {
                {-360,-340},{-120,-340},{120,-340},{360,-340},
                {-360,0},{-120,0},{120,0},{360,0},
                {-360,340},{-120,340},{120,340},{360,340}}));
    }
    public static FrontierWorldLayout distant() {
        return new FrontierWorldLayout(new WorldBounds(-2048, -2048, 4096, 4096), positions(new int[][] {
                {-1480,-1360},{-600,-1360},{440,-1360},{1600,-1360},
                {-1480,0},{-600,0},{440,0},{1600,0},
                {-1480,1480},{-600,1480},{440,1480},{1600,1480}}));
    }
    /** Sparse local pads; long-distance walking consumes regional terrain, not a pre-expanded world road grid. */
    public static FrontierWorldLayout scale() {
        var positions = new ArrayList<BlockPosition>();
        for (int row = 0; row < 10; row++) for (int col = 0; col < 10; col++) {
            int jitterX = Math.floorMod(row * 317 + col * 173, 1_201) - 600;
            int jitterZ = Math.floorMod(row * 211 + col * 397, 1_401) - 700;
            positions.add(new BlockPosition(-21_000 + col * 4_500 + jitterX, 0, -21_000 + row * 4_500 + jitterZ));
        }
        return scaleLayout(positions);
    }
    /** Half-density scale profile retains the original occupied geographical extent. */
    public static FrontierWorldLayout scaleFifty() {
        var original = scale().settlements();
        var positions = new ArrayList<BlockPosition>();
        for (int index = 0; index < original.size(); index++)
            if ((index / 10 + index % 10) % 2 == 0) positions.add(original.get(index));
        return scaleLayout(positions);
    }
    private static FrontierWorldLayout scaleLayout(List<BlockPosition> positions) {
        var producers = new HashSet<Integer>();
        // Authored content repeats the twelve environmental sectors, independently of ID ordering.
        for (int i = 0; i < positions.size(); i++) if (Set.of(0, 1, 3, 5, 7, 10).contains(i % 12)) producers.add(i);
        return new FrontierWorldLayout(new WorldBounds(-50_000, -50_000, 100_000, 100_000), positions, names(positions.size()),
                Roads.LOCAL_ACCESS, Optional.of(producers));
    }
    public List<RoadLink> roadLinks() {
        if (roads == Roads.LOCAL_ACCESS) return List.of();
        var result = new ArrayList<RoadLink>();
        for (int i = 0; i < 12; i++) {
            if (i % 4 != 3) result.add(new RoadLink(i, i + 1));
            if (i < 8) result.add(new RoadLink(i, i + 4));
        }
        return List.copyOf(result);
    }
    private static List<BlockPosition> positions(int[][] coordinates) {
        return Arrays.stream(coordinates).map(p -> new BlockPosition(p[0], 0, p[1])).toList();
    }
    String canonicalText() {
        String base = bounds + ":" + settlements;
        return roads == Roads.HISTORICAL_GRID && settlementNames.equals(HISTORICAL_NAMES) && extractionProducers.isEmpty() && terrain == Terrain.FLAT && productionSites == ProductionSites.ADJACENT ? base
                : base + ":names=" + settlementNames + ":roads=" + roads + ":extraction="
                    + extractionProducers.map(values -> values.stream().sorted().toList().toString()).orElse("historical-stride") + ":terrain=" + terrain + ":productionSites=" + productionSites;
    }
}
