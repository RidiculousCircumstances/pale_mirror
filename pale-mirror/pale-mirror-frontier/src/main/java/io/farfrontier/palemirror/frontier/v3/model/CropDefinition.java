package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.extraction.BlockExtraction;
import io.farfrontier.palemirror.frontier.v3.model.tooling.ToolRequirement;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Crop content only. Field biology, work, custody and native mutation retain their owners. */
public record CropDefinition(String id, String blockKind, String outputKind, String seedKind,
                             String harvestLootTable, ToolRequirement harvestTool) {
    public CropDefinition {
        ProductionRecipe.requireItemKey(id);
        ProductionRecipe.requireItemKey(blockKind);
        ProductionRecipe.requireItemKey(outputKind);
        ProductionRecipe.requireItemKey(seedKind);
        ProductionRecipe.requireItemKey(harvestLootTable);
        Objects.requireNonNull(harvestTool);
    }
    public BlockExtraction.Definition harvest(int quantity) {
        if (quantity < 1 || quantity > 64) throw new IllegalArgumentException("invalid crop harvest quantity");
        return new BlockExtraction.Definition(id + "/harvest/" + quantity,
                new BlockExtraction.Block(blockKind, Map.of("age", "7")),
                new BlockExtraction.Block("minecraft:air", Map.of()), harvestTool, harvestLootTable,
                List.of(new BlockExtraction.Output(outputKind, quantity)));
    }
    public String canonicalText() {
        return id + ":" + blockKind + ":" + outputKind + ":" + seedKind + ":" + harvestLootTable + ":" + harvestTool.canonicalText();
    }
}
