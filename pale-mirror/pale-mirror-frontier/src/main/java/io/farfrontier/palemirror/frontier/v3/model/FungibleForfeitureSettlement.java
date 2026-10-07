package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionState;
import io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovement;
import java.util.Map;
import java.util.Objects;

/** Ephemeral transaction contributions. Never a published world, persisted ledger or second authority. */
record FungibleForfeitureSettlement(ExactInventory inventory, CompanyRegistry companies, ShipmentState shipments,
                                    ActorExecutionState executions, Map<SubjectId, ActorMovement> movements) {
    FungibleForfeitureSettlement {
        Objects.requireNonNull(inventory); Objects.requireNonNull(companies); Objects.requireNonNull(shipments);
        Objects.requireNonNull(executions); movements = Map.copyOf(movements);
    }
    FungibleForfeitureSettlement withTrade(ExactInventory nextInventory, CompanyRegistry nextCompanies) {
        return new FungibleForfeitureSettlement(nextInventory, nextCompanies, shipments, executions, movements);
    }
    FungibleForfeitureSettlement withShipments(ShipmentState next, ActorExecutionState authority,
                                              Map<SubjectId, ActorMovement> motion) {
        return new FungibleForfeitureSettlement(inventory, companies, next, authority, motion);
    }
    FrontierWorldStateUpdate contribution() {
        return FrontierWorldStateUpdate.begin().inventory(inventory).companies(companies).shipments(shipments)
                .actorExecutions(executions).actorMovements(movements);
    }
}
