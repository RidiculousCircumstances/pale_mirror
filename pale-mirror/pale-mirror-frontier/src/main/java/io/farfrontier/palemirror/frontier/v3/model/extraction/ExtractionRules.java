package io.farfrontier.palemirror.frontier.v3.model.extraction;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Persisted content policy. The work/transport algorithms do not select a commodity. */
public record ExtractionRules(boolean enabled, int producerStride, int width, int depth,
                              int reserveItems, int minersPerSite, int haulBatch,
                              long reviewTicks, int exteriorClearance, int pitDepth, int projectionWritesPerTurn, BlockExtraction.Definition source,
                              int developmentBatchCells,
                              java.util.Optional<io.farfrontier.palemirror.frontier.v3.model.geometry.KnownBlockStratum> geology) {
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
        Objects.requireNonNull(source); Objects.requireNonNull(geology);
        if (producerStride < 1 || producerStride > 12 || width < 2 || width > 32
                || depth < 1 || depth > 32 || reserveItems < 1 || reserveItems > 3_456
                || minersPerSite < 1 || minersPerSite > 8 || haulBatch < 1 || haulBatch > 64
                || reviewTicks < 1 || reviewTicks > 24_000 || exteriorClearance < 4 || exteriorClearance > 64
                || pitDepth < 1 || pitDepth > 8 || projectionWritesPerTurn < 1 || projectionWritesPerTurn > 256
                || developmentBatchCells < 0 || developmentBatchCells > 256)
            throw new IllegalArgumentException("invalid bounded extraction content rules");
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
    private static ExtractionRules initial(boolean enabled) {
        return new ExtractionRules(enabled, 2, 8, 16, 64, 2, 64, 200, 8, 3, 64,
                new BlockExtraction.Definition("pale_mirror:stone_extraction",
                        new BlockExtraction.Block("minecraft:stone", Map.of()),
                        new BlockExtraction.Block("minecraft:air", Map.of()),
                        "minecraft:stone_pickaxe", "minecraft:blocks/stone",
                        List.of(new BlockExtraction.Output("minecraft:cobblestone", 1))));
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
                + geology.map(value -> "|geology=" + value.canonicalText()).orElse("");
    }
    private static String blockText(BlockExtraction.Block block) {
        return block.kind() + block.properties().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> ";" + entry.getKey() + "=" + entry.getValue())
                .collect(java.util.stream.Collectors.joining());
    }
}
