package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.supply.SupplyPlanner;
import java.util.*;

/** Individual manufacture participates in input valuation. Its exact existing
 * item/repaired-item obligations remain owned by equipment planning. */
final class IndividualStationSupplyCapacity implements SupplyCapacityPort {
    public Key key() { return Key.INDIVIDUAL_STATIONS; }
    public List<SupplyPlanner.Process> read(FrontierWorldState state, SubjectId home,
            Map<String, SupplyPlanner.Stock> stocks, SupplyPlanningWindow window) { return List.of(); }
    public List<SupplyPlanner.Process> valuation(FrontierWorldState state, SubjectId home,
            Map<String, SupplyPlanner.Stock> stocks, SupplyPlanningWindow window) {
        // Restoring one worn item is not manufacturing another whole item.
        return StationRecipeValuation.read(state, home, recipe -> recipe.outcome() instanceof ProductionRecipeOutcome.Individual);
    }
    public List<SupplyPlanner.Dependency> dependencies(FrontierWorldState state, SubjectId home,
            Map<String, SupplyPlanner.Stock> stocks) { return List.of(); }
}
