package io.farfrontier.palemirror.frontier;

/** Immutable simulation scale. The graybox profile uses people, never visual population proxies. */
public record FrontierProfile(String id, int widthCells, int heightCells, int blocksPerCell,
                              int settlementCount, int infectionSeeds, int populationScale,
                              int minimumPopulation, int maximumPopulation) {
    public static final FrontierProfile REFERENCE_64X44 = new FrontierProfile(
            "reference-64x44", 64, 44, 16, 12, 2, 1, 800, 1_300);
    public static final FrontierProfile GRAYBOX_10 = new FrontierProfile(
            "graybox-10", 64, 64, 16, 10, 2, 40, 20, 32);

    public FrontierProfile {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id is required");
        if (widthCells < 8 || heightCells < 8 || blocksPerCell < 1) {
            throw new IllegalArgumentException("world dimensions are too small");
        }
        if (settlementCount < 1 || infectionSeeds < 0 || populationScale < 1
                || minimumPopulation < 1 || maximumPopulation < minimumPopulation) {
            throw new IllegalArgumentException("invalid Frontier profile");
        }
    }

    public int widthBlocks() { return Math.multiplyExact(widthCells, blocksPerCell); }
    public int heightBlocks() { return Math.multiplyExact(heightCells, blocksPerCell); }

    public static FrontierProfile require(String id) {
        if (REFERENCE_64X44.id.equals(id)) return REFERENCE_64X44;
        if (GRAYBOX_10.id.equals(id)) return GRAYBOX_10;
        throw new IllegalArgumentException("unknown Frontier profile " + id);
    }
}
