package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionState;
import io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovement;
import java.util.Map;
import java.util.Objects;

/** Ephemeral transaction contributions. Never a published world, persisted ledger or second authority. */
record FungibleForfeitureSettlement(ExactInventory inventory, CompanyRegistry companies, ShipmentState shipments,
                                    ActorExecutionState executions, Map<SubjectId, ActorMovement> movements,
                                    Map<SubjectId, ProductionJob> productionJobs, StrategicPlanState plans,
                                    HiveColony hive, Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId,
                                        io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent> intents) {
    FungibleForfeitureSettlement {
        Objects.requireNonNull(inventory); Objects.requireNonNull(companies); Objects.requireNonNull(shipments);
        Objects.requireNonNull(executions); movements = Map.copyOf(movements);
        productionJobs = Map.copyOf(productionJobs); Objects.requireNonNull(plans); Objects.requireNonNull(hive);
        intents = Map.copyOf(intents);
    }
    static FungibleForfeitureSettlement from(FrontierWorldState state, ExactInventory inventory) {
        return new FungibleForfeitureSettlement(inventory, state.companies(), state.shipments(), state.actorExecutions(),
                state.actorMovements(), state.productionJobs(), state.strategicPlans(), state.hiveColony(), state.physicalIntents());
    }
    FungibleForfeitureSettlement withTrade(ExactInventory nextInventory, CompanyRegistry nextCompanies) {
        return new FungibleForfeitureSettlement(nextInventory, nextCompanies, shipments, executions, movements,
                productionJobs, plans, hive, intents);
    }
    FungibleForfeitureSettlement withShipments(ShipmentState next, ActorExecutionState authority,
                                              Map<SubjectId, ActorMovement> motion) {
        return new FungibleForfeitureSettlement(inventory, companies, next, authority, motion, productionJobs, plans, hive, intents);
    }
    FungibleForfeitureSettlement withProduction(ExactInventory nextInventory, CompanyRegistry nextCompanies,
                                               ActorExecutionState authority, Map<SubjectId, ProductionJob> jobs,
                                               StrategicPlanState strategic) {
        return new FungibleForfeitureSettlement(nextInventory, nextCompanies, shipments, authority, movements,
                jobs, strategic, hive, intents);
    }
    FungibleForfeitureSettlement withHive(HiveColony colony, StrategicPlanState strategic,
            Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId, io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent> effects) {
        return new FungibleForfeitureSettlement(inventory, companies, shipments, executions, movements,
                productionJobs, strategic, colony, effects);
    }
    FrontierWorldStateUpdate contribution() {
        return FrontierWorldStateUpdate.begin().inventory(inventory).companies(companies).shipments(shipments)
                .actorExecutions(executions).actorMovements(movements).productionJobs(productionJobs)
                .strategicPlans(plans).hiveColony(hive).physicalIntents(intents);
    }
}
