package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.supply.SupplyPlanner;
import java.util.*;

/** Individual manufacture participates in input valuation. Its exact existing
 * item/repaired-item obligations remain owned by equipment planning. */
final class IndividualStationSupplyCapacity implements SupplyCapacityPort {
    private final ImmutableKeyedInputView<SubjectId, List<io.farfrontier.palemirror.frontier.v3.model.supply.ProductionCost>> valuations =
            new ImmutableKeyedInputView<>(SimulationCapacity.MAX_SETTLEMENTS);
    public Key key() { return Key.INDIVIDUAL_STATIONS; }
    public List<SupplyPlanner.Process> read(FrontierWorldState state, SubjectId home,
            Map<String, SupplyPlanner.Stock> stocks, SupplyPlanningWindow window) { return List.of(); }
    public List<io.farfrontier.palemirror.frontier.v3.model.supply.ProductionCost> valuation(FrontierWorldState state, SubjectId home,
            Map<String, SupplyPlanner.Stock> stocks) {
        // Restoring one worn item is not manufacturing another whole item.
        var inputs = new ArrayList<>(SupplyLabourCapacity.potentialInputs(state, home)); inputs.add(InstalledStationFacts.in(state, home));
        return valuations.getPublished(state.bootstrap(), home, inputs,
                () -> StationRecipeValuation.read(state, home, recipe -> recipe.outcome() instanceof ProductionRecipeOutcome.Individual));
    }
    public List<SupplyPlanner.Dependency> dependencies(FrontierWorldState state, SubjectId home,
            Map<String, SupplyPlanner.Stock> stocks) { return List.of(); }
}
