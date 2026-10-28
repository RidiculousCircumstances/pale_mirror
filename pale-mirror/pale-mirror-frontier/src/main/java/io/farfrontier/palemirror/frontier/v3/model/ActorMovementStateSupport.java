package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.model.extraction.ExtractionSiteState;
import io.farfrontier.palemirror.frontier.v3.model.group.UnitGroupState;
import java.util.Map;

/** Shared identity/authority closure; nominal family contexts own their retained relationships. */
final class ActorMovementStateSupport {
    private static final ImmutableInvariantRegistry<SubjectId> VALIDATION = new ImmutableInvariantRegistry<>(SimulationCapacity.MAX_ACTORS);
    private ActorMovementStateSupport() { }
    static void validate(FrontierBootstrap bootstrap, Map<SubjectId, ActorMovement> movements, Map<SubjectId, ActorLocation> actors,
                         HumanPopulation people, ExactInventory inventory, ActorExecutionState executions,
                         ShipmentState shipments, UnitGroupState groups, ExtractionSiteState extractionSites, StrategicPlanState strategicPlans,
                         io.farfrontier.palemirror.frontier.v3.model.forestry.ForestState forest, Map<SubjectId, ProductionJob> productionJobs) {
        if (movements.size() > SimulationCapacity.MAX_ACTORS) throw new IllegalArgumentException("actor movement retention bound exceeded");
        var references = new ActorMovementReferences(people, inventory, shipments, groups, extractionSites, strategicPlans, forest, productionJobs);
        for (var id : executions.current(ActorActivityKind.SERVICE_EXIT).values()) {
            var movement = movements.get(id.actorId());
            if (movement == null || !movement.executionId().equals(id))
                throw new IllegalArgumentException("service-exit execution lost its exact movement");
        }
        for (var entry : movements.entrySet()) {
            var movement = entry.getValue();
            var inputs = new java.util.ArrayList<Object>();
            inputs.add(executions.actors().get(movement.order().actorId()));
            inputs.add(Boolean.valueOf(actors.containsKey(entry.getKey())));
            inputs.add(people.meals().get(entry.getKey()));
            java.util.Collections.addAll(inputs, movement.context().validationInputs(references));
            VALIDATION.validate(bootstrap, movements, entry.getKey(), movement, () -> {
                executions.requireCurrent(movement.executionId());
                if (!entry.getKey().equals(movement.order().actorId()) || !actors.containsKey(entry.getKey())
                        || people.meals().containsKey(entry.getKey()))
                    throw new IllegalArgumentException("actor movement must bind one exact actor without a competing meal owner");
                movement.context().validateReferences(references, movement);
            }, inputs.toArray());
        }
    }
}
