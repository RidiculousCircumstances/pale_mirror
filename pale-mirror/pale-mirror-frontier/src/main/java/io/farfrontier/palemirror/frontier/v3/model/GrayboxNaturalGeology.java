package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.extraction.BlockExtraction;
import io.farfrontier.palemirror.frontier.v3.model.geometry.RegionalBlockMap;
import java.util.HashMap;

/** Initial content only. No worker, custody, mining progress or runtime regeneration. */
public final class GrayboxNaturalGeology {
    private GrayboxNaturalGeology() { }

    public static RegionalBlockMap<BlockExtraction.Block> compile(FrontierBootstrap bootstrap) {
        var cells = new HashMap<BlockPosition, BlockExtraction.Block>();
        for (var deposit : GrayboxQuarryPlan.compile(bootstrap).sites().deposits().values()) {
            deposit.site().layout().terrainSupports().forEach((position, block) -> add(cells, position, block));
            for (var cell : deposit.site().layout().cells()) add(cells, cell.source(), cell.definition().before());
        }
        return RegionalBlockMap.copyOf(cells);
    }

    private static void add(HashMap<BlockPosition, BlockExtraction.Block> cells, BlockPosition position,
            BlockExtraction.Block block) {
        var previous = cells.putIfAbsent(position, block);
        if (previous != null && !previous.equals(block))
            throw new IllegalArgumentException("competing initial geological materials at " + position);
    }
}
