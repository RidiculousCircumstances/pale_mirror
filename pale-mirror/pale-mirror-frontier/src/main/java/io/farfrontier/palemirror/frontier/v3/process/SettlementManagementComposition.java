package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.model.*;

/** Closed composition root; the common arbiter depends only on the planner port. */
public final class SettlementManagementComposition {
    public static final SettlementManagement MANAGEMENT = new SettlementManagement(java.util.List.of(
            new SettlementSupplyPlanner(), new SettlementFoodPlanner(),
            new SettlementHealthPlanner(), new SettlementFieldPlanner(), new CompanyBakeryPlanning()), SettlementManagementPolicy.standard());
    private SettlementManagementComposition() { }
}
