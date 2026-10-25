package io.farfrontier.palemirror.frontier.v3.model.economy;

import io.farfrontier.palemirror.frontier.v3.model.supply.*;
import java.util.*;

/** Immutable owner-supplied facts. Market interest is explicitly not a contract or future cash. */
public record EconomicSnapshot(long dayTicks, Map<String, SupplyPlanner.Stock> stocks,
        Map<String, SupplyReservePlanner.Need> needs, List<SupplyReservePlanner.Flow> flows,
        Map<String, SupplyReservePlanner.Target> reserves, List<SupplyPlanner.Process> processes,
        List<SupplyPlanner.Dependency> commitments, SourcingMarket market,
        Map<String, Long> replacementPrices, List<ExportInterest> exportInterest, long contractReceivables) {
    public EconomicSnapshot withExportInterest(List<ExportInterest> interests) {
        return new EconomicSnapshot(dayTicks, stocks, needs, flows, reserves, processes, commitments,
                market, replacementPrices, interests, contractReceivables);
    }
    public record ExportInterest(String buyer, String item, long quantity, long unitPrice,
                                 long availableCash, long commodityBudget, long deliveryUnitExpense, long deliveryUnitCash) {
        public ExportInterest(String buyer, String item, long quantity, long unitPrice,
                              long availableCash, long commodityBudget, long deliveryUnitExpense) {
            this(buyer, item, quantity, unitPrice, availableCash, commodityBudget, deliveryUnitExpense, deliveryUnitExpense);
        }
        public ExportInterest {
            if (buyer == null || buyer.isBlank() || item == null || item.isBlank()
                    || quantity < 1 || unitPrice < 1 || availableCash < 0 || commodityBudget < 0 || commodityBudget > availableCash
                    || deliveryUnitExpense < 0 || deliveryUnitCash < 0 || deliveryUnitCash > deliveryUnitExpense)
                throw new IllegalArgumentException("invalid economic export interest");
            Math.addExact(unitPrice, deliveryUnitExpense);
        }
        public long buyerUnitExpense() { return Math.addExact(unitPrice, deliveryUnitExpense); }
        public long buyerUnitCash() { return Math.addExact(unitPrice, deliveryUnitCash); }
    }
    public EconomicSnapshot {
        stocks = Map.copyOf(stocks); needs = Map.copyOf(needs); flows = List.copyOf(flows);
        reserves = Map.copyOf(reserves); processes = List.copyOf(processes); commitments = List.copyOf(commitments);
        Objects.requireNonNull(market); replacementPrices = Map.copyOf(replacementPrices); exportInterest = List.copyOf(exportInterest);
        var declared = stocks.keySet();
        if (dayTicks < 1 || contractReceivables < 0 || stocks.isEmpty() || stocks.size() > 256 || flows.size() > 256
                || processes.size() > 256 || commitments.size() > 4096 || exportInterest.size() > 4096
                || !stocks.keySet().equals(needs.keySet()) || !stocks.keySet().equals(reserves.keySet())
                || !stocks.keySet().equals(replacementPrices.keySet()) || replacementPrices.values().stream().anyMatch(p -> p < 1)
                || exportInterest.stream().anyMatch(e -> !declared.contains(e.item())))
            throw new IllegalArgumentException("incomplete bounded economic snapshot");
    }
}
