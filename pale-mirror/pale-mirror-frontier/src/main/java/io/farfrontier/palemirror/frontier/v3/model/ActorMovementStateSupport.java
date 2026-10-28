package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.model.extraction.ExtractionSiteState;
import io.farfrontier.palemirror.frontier.v3.model.group.UnitGroupState;
import java.util.Map;

/** Shared identity/authority closure; nominal family contexts own their retained relationships. */
final class ActorMovementStateSupport {
    // One bounded manifest may declare 32 parcels in addition to the common actor/authority reads.
    private static final ImmutableReferenceIndex<ActorMovementReferences, SubjectId, ActorMovement> VALIDATION =
            new ImmutableReferenceIndex<>(SimulationCapacity.MAX_ACTORS, 64, ActorMovementReferences.SOURCES,
                    ActorMovementReferences::reads, ActorMovementStateSupport::validateMovement);
    private ActorMovementStateSupport() { }
    static void validate(FrontierWorldState state) {
        validate(state.bootstrap(), state.actorMovements(), state.actorLocations(), state.humanPopulation(), state.inventory(),
                state.actorExecutions(), state.shipments(), state.unitGroups(), state.extractionSites(), state.strategicPlans(),
                state.forest(), state.productionJobs());
    }
    static void validate(FrontierBootstrap bootstrap, Map<SubjectId, ActorMovement> movements, Map<SubjectId, ActorLocation> actors,
                         HumanPopulation people, ExactInventory inventory, ActorExecutionState executions,
                         ShipmentState shipments, UnitGroupState groups, ExtractionSiteState extractionSites, StrategicPlanState strategicPlans,
                         io.farfrontier.palemirror.frontier.v3.model.forestry.ForestState forest, Map<SubjectId, ProductionJob> productionJobs) {
        if (movements.size() > SimulationCapacity.MAX_ACTORS) throw new IllegalArgumentException("actor movement retention bound exceeded");
        var references = new ActorMovementReferences(people, inventory, shipments, groups, extractionSites, strategicPlans, forest, productionJobs, actors, executions);
        for (var id : executions.current(ActorActivityKind.SERVICE_EXIT).values()) {
            var movement = movements.get(id.actorId());
            if (movement == null || !movement.executionId().equals(id))
                throw new IllegalArgumentException("service-exit execution lost its exact movement");
        }
        VALIDATION.validate(bootstrap, movements, references);
    }
    private static void validateMovement(ActorMovementReferences references, SubjectId id, ActorMovement movement) {
        references.executions().requireCurrent(movement.executionId());
        if (!id.equals(movement.order().actorId()) || !references.actors().containsKey(id) || references.people().meals().containsKey(id))
            throw new IllegalArgumentException("actor movement must bind one exact actor without a competing meal owner");
        movement.context().validateReferences(references, movement);
    }
}
