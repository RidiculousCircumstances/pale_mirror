package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovement;
import io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovementContext;

import java.util.Map;

/** Bounded reference closure for the canonical movement aggregate. */
final class ActorMovementStateSupport {
    private ActorMovementStateSupport() { }

    static void validate(Map<SubjectId, ActorMovement> movements, Map<SubjectId, ActorLocation> actors,
                         HumanPopulation people, ExactInventory inventory) {
        if (movements.size() > 4_096)
            throw new IllegalArgumentException("actor movement retention bound exceeded");
        for (var entry : movements.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().order().actorId())
                    || !actors.containsKey(entry.getKey()) || people.meals().containsKey(entry.getKey()))
                throw new IllegalArgumentException("actor movement must bind one exact actor without a competing meal owner");
            switch (entry.getValue().context()) {
                case ActorMovementContext.ServiceExit exit -> {
                    ResidentProfile resident = people.resident(entry.getKey());
                    if (resident == null || !resident.settlementId().equals(exit.settlementId())
                            || !FrontierWorldState.depotId(exit.settlementId()).equals(exit.depotId())
                            || entry.getValue().order().capability() != TraversalCapability.PEDESTRIAN
                            || !inventory.containers().containsKey(exit.depotId()))
                        throw new IllegalArgumentException("actor movement service exit lacks its declared resident, settlement or depot");
                }
            }
        }
    }
}
