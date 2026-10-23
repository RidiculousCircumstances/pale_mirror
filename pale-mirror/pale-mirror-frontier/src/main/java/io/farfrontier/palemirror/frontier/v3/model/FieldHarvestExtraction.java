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
            new BlockExtraction.Block("minecraft:air", Map.of()),
            new io.farfrontier.palemirror.frontier.v3.model.tooling.ToolRequirement(
                    io.farfrontier.palemirror.frontier.v3.model.tooling.ToolAbilities.HARVEST, 0, 1),
            "pale_mirror:extraction/grain_harvest_v1", List.of(new BlockExtraction.Output(ITEM, 1)));
    private FieldHarvestExtraction() { }

    public static BlockExtraction.Definition definition(int quantity) {
        if (quantity < 1 || quantity > 64)
            throw new IllegalArgumentException("undeclared grain extraction yield " + quantity);
        return quantity == 1 ? DEFINITION : new BlockExtraction.Definition(
                "pale_mirror:grain_harvest_" + quantity, DEFINITION.before(), DEFINITION.after(), DEFINITION.toolRequirement(),
                DEFINITION.lootTable(), List.of(new BlockExtraction.Output(ITEM, quantity)));
    }
    public static BlockExtraction.Definition definition(FrontierBootstrap bootstrap, BlockPosition position) {
        return definition(FieldAgronomy.yield(bootstrap, position));
    }
    public static boolean isDefinition(BlockExtraction.Definition value) {
        return value.coldOutput().size() == 1 && definition(value.coldOutput().getFirst().quantity()).equals(value);
    }

    public static BlockExtraction prepareKnown(ResourceFieldCycle cycle, ResourceFieldLayout.CellId id,
                                                String operationId,
                                                io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId execution) {
        return prepareKnown(cycle, id, operationId, execution, DEFINITION);
    }
    public static BlockExtraction prepareKnown(ResourceFieldCycle cycle, ResourceFieldLayout.CellId id,
                                                String operationId,
                                                io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId execution,
                                                BlockExtraction.Definition definition) {
        if (!isDefinition(definition)) throw new IllegalArgumentException("foreign crop extraction definition");
        if (cycle.expectedWorkOutcome(id) != ResourceFieldCycle.WorkOutcome.HARVESTED)
            throw new IllegalArgumentException("grain extraction requires the exact ripe field generation");
        var cell = cycle.layout().requireCell(id);
        return BlockExtraction.prepareKnown(operationId, execution, cell.crop(), definition, definition.before());
    }
}
