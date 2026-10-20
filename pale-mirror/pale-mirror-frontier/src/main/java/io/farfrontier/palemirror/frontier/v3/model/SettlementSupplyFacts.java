package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.supply.*;
import java.util.*;

/** One immutable, explicitly dated collection of owner facts, shared by planning and valuation. */
public record SettlementSupplyFacts(SubjectId settlement, long atTick, SettlementReservePlanning.Basis basis,
        Map<String, SupplyPlanner.Stock> stocks, Map<String, SupplyReservePlanner.Target> reserves,
        List<SupplyPlanner.Process> capacities, List<SupplyPlanner.Dependency> commitments, SourcingMarket market) {
    public SettlementSupplyFacts {
        Objects.requireNonNull(settlement); Objects.requireNonNull(market); Objects.requireNonNull(basis);
        stocks = Map.copyOf(stocks); reserves = Map.copyOf(reserves);
        capacities = List.copyOf(capacities); commitments = List.copyOf(commitments);
        if (atTick < 0) throw new IllegalArgumentException("negative economic evaluation instant");
    }
}
