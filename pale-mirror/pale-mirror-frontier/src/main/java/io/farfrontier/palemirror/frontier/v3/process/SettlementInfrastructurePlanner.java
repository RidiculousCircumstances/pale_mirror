package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.Optional;

/** Proposes inspection of the settlement's owned roads, independently of goods transport. */
final class SettlementInfrastructurePlanner implements SettlementOperationPlanner {
    @Override public String id() { return "frontier:infrastructure"; }

    @Override public Assessment assess(FrontierWorldState state, Settlement settlement) {
        boolean constructionActive = state.routeConstructions().values().stream()
                .anyMatch(project -> project.settlementId().equals(settlement.id()));
        boolean alreadyConfirmed = state.strategicPlans().routePatrols().values().stream()
                .anyMatch(patrol -> patrol.settlementId().equals(settlement.id())
                        && patrol.status() == RoutePatrolStatus.OBSTRUCTION_CONFIRMED
                        && patrol.obstruction().stream().anyMatch(state.physicalDeltas()::containsKey));
        // Inspection does not authorize a fabricated bypass: the maintenance owner
        // repairs the exact observed cell and retains its independent receipts.
        if (!constructionActive && alreadyConfirmed)
            return Assessment.held(Reason.ROUTE_RECOVERY_ALREADY_OWNED);
        if (!constructionActive && !alreadyConfirmed
                && !state.routeTopology().routePassable(state.bootstrap(), settlement.id()))
            return Assessment.offer(settlement.id(), new StrategicOperationProposal(
                    StrategicObjectiveKind.SETTLEMENT_PATROL_OBSTRUCTED_ROUTE, Optional.empty(), Long.MAX_VALUE), Priority.CRITICAL);
        return Assessment.empty();
    }
}
