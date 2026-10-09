package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.extraction.BlockExtraction;
import java.util.List;
import java.util.Map;

/** Farming alone declares the grain-only profile. Sowing is not block extraction. */
public final class FieldHarvestExtraction {
    public static final String ITEM = "minecraft:wheat";
    public static final BlockExtraction.Definition DEFINITION = new BlockExtraction.Definition(
            "pale_mirror:grain_harvest_v1",
            new BlockExtraction.Block("minecraft:wheat", Map.of("age", "7")),
            new BlockExtraction.Block("minecraft:air", Map.of()), "minecraft:air",
            "pale_mirror:extraction/grain_harvest_v1", List.of(new BlockExtraction.Output(ITEM, 1)));
    private FieldHarvestExtraction() { }

    public static BlockExtraction prepareKnown(ResourceFieldCycle cycle, ResourceFieldLayout.CellId id,
                                                String operationId,
                                                io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId execution) {
        if (cycle.expectedWorkOutcome(id) != ResourceFieldCycle.WorkOutcome.HARVESTED)
            throw new IllegalArgumentException("grain extraction requires the exact ripe field generation");
        var cell = cycle.layout().requireCell(id);
        return BlockExtraction.prepareKnown(operationId, execution, cell.crop(), DEFINITION, DEFINITION.before());
    }
}
