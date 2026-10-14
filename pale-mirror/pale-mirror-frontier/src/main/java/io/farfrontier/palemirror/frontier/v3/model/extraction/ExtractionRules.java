package io.farfrontier.palemirror.frontier.v3.model.extraction;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Persisted content policy. The work/transport algorithms do not select a commodity. */
public record ExtractionRules(boolean enabled, int producerStride, int width, int depth,
                              int reserveItems, int minersPerSite, int haulBatch,
                              long reviewTicks, int exteriorClearance, int pitDepth, int projectionWritesPerTurn, BlockExtraction.Definition source,
                              int developmentBatchCells,
                              java.util.Optional<io.farfrontier.palemirror.frontier.v3.model.geometry.KnownBlockStratum> geology,
                              java.util.Optional<io.farfrontier.palemirror.frontier.v3.model.geometry.ExcavationTerraces> terraces,
                              java.util.Optional<ExtractionSurfaceCut> surfaceCut,
                              java.util.Optional<ExtractionRockRise> rockRise,
                              java.util.Optional<ExtractionPocketProfile> pocket) {
    public ExtractionRules(boolean enabled, int producerStride, int width, int depth, int reserveItems,
            int minersPerSite, int haulBatch, long reviewTicks, int exteriorClearance, int pitDepth,
            int projectionWritesPerTurn, BlockExtraction.Definition source, int developmentBatchCells,
            java.util.Optional<io.farfrontier.palemirror.frontier.v3.model.geometry.KnownBlockStratum> geology,
            java.util.Optional<io.farfrontier.palemirror.frontier.v3.model.geometry.ExcavationTerraces> terraces,
            java.util.Optional<ExtractionSurfaceCut> surfaceCut, java.util.Optional<ExtractionRockRise> rockRise) {
        this(enabled, producerStride, width, depth, reserveItems, minersPerSite, haulBatch, reviewTicks,
                exteriorClearance, pitDepth, projectionWritesPerTurn, source, developmentBatchCells, geology,
                terraces, surfaceCut, rockRise, java.util.Optional.empty());
    }
    public ExtractionRules(boolean enabled, int producerStride, int width, int depth, int reserveItems,
            int minersPerSite, int haulBatch, long reviewTicks, int exteriorClearance, int pitDepth,
            int projectionWritesPerTurn, BlockExtraction.Definition source, int developmentBatchCells,
            java.util.Optional<io.farfrontier.palemirror.frontier.v3.model.geometry.KnownBlockStratum> geology,
            java.util.Optional<io.farfrontier.palemirror.frontier.v3.model.geometry.ExcavationTerraces> terraces,
            java.util.Optional<ExtractionSurfaceCut> surfaceCut) {
        this(enabled, producerStride, width, depth, reserveItems, minersPerSite, haulBatch, reviewTicks,
                exteriorClearance, pitDepth, projectionWritesPerTurn, source, developmentBatchCells, geology, terraces, surfaceCut,
                java.util.Optional.empty());
    }
    public ExtractionRules(boolean enabled, int producerStride, int width, int depth, int reserveItems,
            int minersPerSite, int haulBatch, long reviewTicks, int exteriorClearance, int pitDepth,
            int projectionWritesPerTurn, BlockExtraction.Definition source, int developmentBatchCells,
            java.util.Optional<io.farfrontier.palemirror.frontier.v3.model.geometry.KnownBlockStratum> geology,
            java.util.Optional<io.farfrontier.palemirror.frontier.v3.model.geometry.ExcavationTerraces> terraces) {
        this(enabled, producerStride, width, depth, reserveItems, minersPerSite, haulBatch, reviewTicks,
                exteriorClearance, pitDepth, projectionWritesPerTurn, source, developmentBatchCells, geology, terraces, java.util.Optional.empty());
    }
    public ExtractionRules(boolean enabled, int producerStride, int width, int depth, int reserveItems,
            int minersPerSite, int haulBatch, long reviewTicks, int exteriorClearance, int pitDepth,
            int projectionWritesPerTurn, BlockExtraction.Definition source, int developmentBatchCells,
            java.util.Optional<io.farfrontier.palemirror.frontier.v3.model.geometry.KnownBlockStratum> geology) {
        this(enabled, producerStride, width, depth, reserveItems, minersPerSite, haulBatch, reviewTicks,
                exteriorClearance, pitDepth, projectionWritesPerTurn, source, developmentBatchCells, geology, java.util.Optional.empty());
    }
    public ExtractionRules(boolean enabled, int producerStride, int width, int depth, int reserveItems,
            int minersPerSite, int haulBatch, long reviewTicks, int exteriorClearance, int pitDepth,
            int projectionWritesPerTurn, BlockExtraction.Definition source, int developmentBatchCells) {
        this(enabled, producerStride, width, depth, reserveItems, minersPerSite, haulBatch, reviewTicks,
                exteriorClearance, pitDepth, projectionWritesPerTurn, source, developmentBatchCells, java.util.Optional.empty());
    }
    public ExtractionRules(boolean enabled, int producerStride, int width, int depth, int reserveItems,
            int minersPerSite, int haulBatch, long reviewTicks, int exteriorClearance, int pitDepth,
            int projectionWritesPerTurn, BlockExtraction.Definition source) {
        this(enabled, producerStride, width, depth, reserveItems, minersPerSite, haulBatch, reviewTicks,
                exteriorClearance, pitDepth, projectionWritesPerTurn, source, 0);
    }
    public ExtractionRules {
        Objects.requireNonNull(source); Objects.requireNonNull(geology); Objects.requireNonNull(terraces); Objects.requireNonNull(surfaceCut);
        Objects.requireNonNull(rockRise); Objects.requireNonNull(pocket);
        pocket.ifPresent(value -> {
            if (!enabled || !value.material().toolKind().equals(source.toolKind()) || value.material().before().equals(source.before())
                    || (value.firstDepth() + value.depthCells() > depth || value.firstRow() + value.rowCells() > width)
                        && (geology.isEmpty() || terraces.isEmpty()))
                throw new IllegalArgumentException("deposit inclusion has no finite compatible geological development");
        });
        if (rockRise.isPresent() && (!enabled || surfaceCut.isEmpty() || geology.isEmpty()))
            throw new IllegalArgumentException("rock rise requires an explicit contiguous terrain baseline");
        if (producerStride < 1 || producerStride > 12 || width < 2 || width > 32
                || depth < 1 || depth > 32 || reserveItems < 1 || reserveItems > 3_456
                || minersPerSite < 1 || minersPerSite > 8 || haulBatch < 1 || haulBatch > 64
                || reviewTicks < 1 || reviewTicks > 24_000 || exteriorClearance < 4 || exteriorClearance > 64
                || pitDepth < 1 || pitDepth > 8 || projectionWritesPerTurn < 1 || projectionWritesPerTurn > 256
                || developmentBatchCells < 0 || developmentBatchCells > 256
                || terraces.isPresent() && (geology.isEmpty() || developmentBatchCells < 2))
            throw new IllegalArgumentException("invalid bounded extraction content rules");
        if (surfaceCut.isPresent() && (terraces.isEmpty() || geology.isEmpty()
                || surfaceCut.orElseThrow().topY() != geology.orElseThrow().maxY() + 1
                || !surfaceCut.orElseThrow().cover().toolKind().equals(source.toolKind())
                || surfaceCut.orElseThrow().cover().before().equals(source.before())))
            throw new IllegalArgumentException("surface cut requires contiguous declared geology and one compatible tool");
    }
    public static ExtractionRules disabled() { return initial(false); }
    public static ExtractionRules graybox() { return initial(true); }
    public static ExtractionRules expandingGraybox() {
        var base = graybox();
        return new ExtractionRules(true, base.producerStride(), base.width(), 32, base.reserveItems(),
                base.minersPerSite(), base.haulBatch(), base.reviewTicks(), base.exteriorClearance(),
                base.pitDepth(), base.projectionWritesPerTurn(), base.source(), 16);
    }
    public static ExtractionRules developingGraybox() {
        var base = expandingGraybox();
        // Matches the independent graybox datapack: bedrock -64, stone -63..62, cap63.
        return new ExtractionRules(base.enabled(), base.producerStride(), base.width(), base.depth(), base.reserveItems(),
                base.minersPerSite(), base.haulBatch(), base.reviewTicks(), base.exteriorClearance(), base.pitDepth(),
                base.projectionWritesPerTurn(), base.source(), base.developmentBatchCells(), java.util.Optional.of(
                        new io.farfrontier.palemirror.frontier.v3.model.geometry.KnownBlockStratum(-63, 62, "minecraft:stone", 1)));
    }
    public static ExtractionRules terracedGraybox() {
        var base = developingGraybox();
        // A modest real initial face makes later development visible before the first reserves fill.
        return new ExtractionRules(base.enabled(), base.producerStride(), base.width(), 4, base.reserveItems(),
                base.minersPerSite(), base.haulBatch(), base.reviewTicks(), base.exteriorClearance(), base.pitDepth(),
                base.projectionWritesPerTurn(), base.source(), base.developmentBatchCells(), base.geology(),
                java.util.Optional.of(new io.farfrontier.palemirror.frontier.v3.model.geometry.ExcavationTerraces(4, 4)),
                java.util.Optional.of(new ExtractionSurfaceCut(63, 1, new BlockExtraction.Definition(
                        "pale_mirror:quarry_surface_preparation", new BlockExtraction.Block("minecraft:grass_block", Map.of("snowy", "false")),
                        new BlockExtraction.Block("minecraft:air", Map.of()), base.source().toolKind(), "minecraft:blocks/grass_block",
                        List.of(new BlockExtraction.Output("minecraft:dirt", 1))))));
    }
    private static ExtractionRules initial(boolean enabled) {
        return new ExtractionRules(enabled, 2, 8, 16, 64, 2, 64, 200, 8, 3, 64,
                new BlockExtraction.Definition("pale_mirror:stone_extraction",
                        new BlockExtraction.Block("minecraft:stone", Map.of()),
                        new BlockExtraction.Block("minecraft:air", Map.of()),
                        "minecraft:stone_pickaxe", "minecraft:blocks/stone",
                        List.of(new BlockExtraction.Output("minecraft:cobblestone", 1))));
    }
    public static ExtractionRules availableFrontGraybox() {
        var base = terracedGraybox();
        return new ExtractionRules(base.enabled(), base.producerStride(), base.width(), base.depth(), base.reserveItems(),
                base.minersPerSite(), base.haulBatch(), base.reviewTicks(), base.exteriorClearance(), base.pitDepth(),
                base.projectionWritesPerTurn(), base.source(), base.developmentBatchCells(), base.geology(), base.terraces(),
                base.surfaceCut(), java.util.Optional.of(new ExtractionRockRise(6, 4, 2)));
    }
    public ExtractionRules withReserveItems(int quantity) {
        return new ExtractionRules(enabled, producerStride, width, depth, quantity, minersPerSite, haulBatch,
                reviewTicks, exteriorClearance, pitDepth, projectionWritesPerTurn, source, developmentBatchCells,
                geology, terraces, surfaceCut, rockRise, pocket);
    }
    public ExtractionRules withPocket(ExtractionPocketProfile value) {
        return new ExtractionRules(enabled, producerStride, width, depth, reserveItems, minersPerSite, haulBatch,
                reviewTicks, exteriorClearance, pitDepth, projectionWritesPerTurn, source, developmentBatchCells,
                geology, terraces, surfaceCut, rockRise, java.util.Optional.of(value));
    }
    public String canonicalText() {
        return enabled + "|" + producerStride + "|" + width + "|" + depth + "|" + reserveItems
                + "|" + minersPerSite + "|" + haulBatch + "|" + reviewTicks
                + "|" + exteriorClearance + "|" + pitDepth + "|" + projectionWritesPerTurn + "|" + source.id()
                + "|" + blockText(source.before()) + "|" + blockText(source.after())
                + "|" + source.toolKind() + "|" + source.lootTable() + "|"
                + source.coldOutput().stream().map(value -> value.itemKind() + ":" + value.quantity())
                    .collect(java.util.stream.Collectors.joining(";"))
                + (developmentBatchCells == 0 ? "" : "|development=" + developmentBatchCells)
                + geology.map(value -> "|geology=" + value.canonicalText()).orElse("")
                + terraces.map(value -> "|terraces=" + value.canonicalText()).orElse("")
                + surfaceCut.map(value -> "|surface-cut=" + value.canonicalText()).orElse("")
                + rockRise.map(value -> "|rock-rise=" + value.canonicalText()).orElse("")
                + pocket.map(value -> "|pocket=" + value.canonicalText()).orElse("");
    }
    public java.util.Optional<BlockExtraction.Definition> definitionFor(BlockExtraction.Block block) {
        return definitions().stream().filter(definition -> definition.before().equals(block)).findFirst();
    }
    public List<BlockExtraction.Definition> definitions() {
        var values = new java.util.ArrayList<BlockExtraction.Definition>(); values.add(source);
        surfaceCut.ifPresent(value -> values.add(value.cover())); pocket.ifPresent(value -> values.add(value.material()));
        return List.copyOf(values);
    }
    static String blockText(BlockExtraction.Block block) {
        return block.kind() + block.properties().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> ";" + entry.getKey() + "=" + entry.getValue())
                .collect(java.util.stream.Collectors.joining());
    }
}
