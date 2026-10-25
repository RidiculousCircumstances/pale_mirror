package io.farfrontier.palemirror.frontier.v3.model.forestry;

import io.farfrontier.palemirror.frontier.v3.model.extraction.BlockExtraction;
import io.farfrontier.palemirror.frontier.v3.model.tooling.ToolRequirement;
import java.util.*;

/** Explicit supported natural content. Native loot must equal this COLD output before any effect. */
public record TreeHarvestProfile(String id, String species, String logKind, String lootTable, String outputItem) {
    public TreeHarvestProfile {
        for (var key : List.of(id, species, logKind, lootTable, outputItem))
            if (!key.matches("[a-z0-9_]+:[a-z0-9_./-]+")) throw new IllegalArgumentException("invalid tree harvest registry key");
    }
    public BlockExtraction.Definition definition(BlockExtraction.Block source, ToolRequirement tool) {
        if (!source.kind().equals(logKind)) throw new IllegalArgumentException("undeclared tree harvest material");
        return new BlockExtraction.Definition(id, source, new BlockExtraction.Block("minecraft:air", Map.of()), tool, lootTable,
                List.of(new BlockExtraction.Output(outputItem, 1)));
    }
    public String canonicalText() { return id + ":" + species + ":" + logKind + ":" + lootTable + ":" + outputItem; }
}
