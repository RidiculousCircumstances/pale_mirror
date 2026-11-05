package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.extraction.BlockExtraction;
import java.util.List;
import java.util.Map;

/** Field content declares the crop-specific extraction profile. Sowing is not block extraction. */
public final class FieldHarvestExtraction {
    /** Named wheat content used by existing isolated fixtures, never inferred from a job. */
    public static final String ITEM = ResourceSiteKind.WHEAT_FIELD.resourceKind();
    public static final BlockExtraction.Definition DEFINITION = ResourceSiteKind.WHEAT_FIELD.crop().harvest(1);
    private FieldHarvestExtraction() { }

    public static BlockExtraction.Definition definition(ResourceSiteKind kind, int quantity) {
        return kind.crop().harvest(quantity);
    }
    public static BlockExtraction.Definition definition(int quantity) {
        return definition(ResourceSiteKind.WHEAT_FIELD, quantity);
    }
    public static BlockExtraction.Definition definition(FrontierBootstrap bootstrap, ResourceSite site, BlockPosition position) {
        if (!site.contains(position)) throw new IllegalArgumentException("crop extraction is outside its declared field");
        return definition(site.kind(), FieldAgronomy.yield(bootstrap, site.kind(), position));
    }
    /** Explicit wheat fixture composition. Production callers provide their exact resource site. */
    public static BlockExtraction.Definition definition(FrontierBootstrap bootstrap, BlockPosition position) {
        return definition(ResourceSiteKind.WHEAT_FIELD, FieldAgronomy.yield(bootstrap, ResourceSiteKind.WHEAT_FIELD, position));
    }
    public static boolean isDefinition(BlockExtraction.Definition value) {
        return value.coldOutput().size() == 1 && java.util.Arrays.stream(ResourceSiteKind.values())
                .anyMatch(kind -> definition(kind, value.coldOutput().getFirst().quantity()).equals(value));
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
            throw new IllegalArgumentException("crop extraction requires the exact ripe field generation");
        var cell = cycle.layout().requireCell(id);
        return BlockExtraction.prepareKnown(operationId, execution, cell.crop(), definition, definition.before());
    }
}
