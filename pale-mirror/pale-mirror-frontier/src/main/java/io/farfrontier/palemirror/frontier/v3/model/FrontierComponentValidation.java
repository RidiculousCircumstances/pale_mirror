package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionState;
import io.farfrontier.palemirror.frontier.v3.model.extraction.ExtractionSiteState;
import io.farfrontier.palemirror.frontier.v3.model.forestry.ForestState;

/** Construction-boundary composition. Component owners retain their checks; complete
 * immutable argument tuples avoid rechecking unchanged components on unrelated writes. */
final class FrontierComponentValidation {
    private static final ImmutableInvariantCheck<Void> EXTRACTION_LAND = new ImmutableInvariantCheck<>();
    private static final ImmutableInvariantCheck<Void> EXTRACTION_WORK = new ImmutableInvariantCheck<>();
    private static final ImmutableInvariantCheck<Void> FORESTRY_WORK = new ImmutableInvariantCheck<>();
    private FrontierComponentValidation() { }

    static void validateLandAndWork(FrontierBootstrap bootstrap, ExtractionSiteState extraction,
                                    ForestState forest, ActorExecutionState executions, HumanPopulation people) {
        EXTRACTION_LAND.validate(() -> extraction.validate(bootstrap), extraction, bootstrap);
        EXTRACTION_WORK.validate(() -> ExtractionWorkAuthority.validateReferences(extraction, executions), extraction, executions);
        FORESTRY_WORK.validate(() -> ForestryWorkAuthority.validateReferences(forest, executions, people), forest, executions, people);
    }

    static void validateMachines(ProductionStations stations, ExactInventory inventory,
                                 ProductionCatalog catalog, io.farfrontier.palemirror.frontier.v3.model.tooling.ToolCatalog tools) {
        stations.validate(inventory, catalog, tools);
    }
}
