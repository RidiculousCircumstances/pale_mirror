package io.farfrontier.palemirror.frontier.v3.model.economy;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.model.market.ProductionValuation;
import io.farfrontier.palemirror.frontier.v3.model.supply.SupplyPlanner;
import java.math.BigInteger;
import java.util.*;

/** Values additional public production: materials are spent, spare labour is allocated, not paid a fictional wage. */
final class ExportProductionValuation {
    record Return(long margin, long outputPerOperation, long labourPerOperation) implements Comparable<Return> {
        @Override public int compareTo(Return other) {
            return BigInteger.valueOf(margin).multiply(BigInteger.valueOf(outputPerOperation))
                    .multiply(BigInteger.valueOf(other.labourPerOperation))
                    .compareTo(BigInteger.valueOf(other.margin).multiply(BigInteger.valueOf(other.outputPerOperation))
                            .multiply(BigInteger.valueOf(labourPerOperation)));
        }
    }
    private final Map<String, Long> materials;
    ExportProductionValuation(EconomicSnapshot facts) {
        var prices = new TreeMap<String, FixedScalar>();
        facts.replacementPrices().forEach((item, price) -> prices.put(item, new FixedScalar(price)));
        var costs = new HashMap<String, Long>();
        for (var process : facts.processes()) {
            var fuels = process.fuels().entrySet().stream().filter(e -> prices.containsKey(e.getKey()))
                    .map(e -> new ProductionValuation.Fuel(prices.get(e.getKey()), e.getValue())).toList();
            ProductionValuation.unitCost(process.inputs(), process.outputPerBatch(), process.heatPerBatch(), prices, fuels)
                    .ifPresent(cost -> costs.put(process.id(), cost.raw()));
        }
        materials = Map.copyOf(costs);
    }
    Return returnOn(SupplyPlanner.Process process, long unitPrice) {
        Long cost = materials.get(process.id());
        return new Return(cost == null ? 0 : Math.max(0, unitPrice - cost),
                Math.multiplyExact((long) process.outputPerBatch(), process.recipesPerOperation()),
                Math.max(1, process.labourTicksPerOperation()));
    }
    Comparator<EconomicSnapshot.ExportInterest> order(EconomicSnapshot facts, Map<String, Long> capacity) {
        return Comparator.<EconomicSnapshot.ExportInterest, Return>comparing(interest -> facts.processes().stream()
                .filter(p -> p.product().equals(interest.item()) && capacity.get(p.id()) > 0)
                .map(p -> returnOn(p, interest.unitPrice())).max(Return::compareTo).orElse(new Return(0, 1, 1)))
                .reversed().thenComparing(Comparator.comparingLong(EconomicSnapshot.ExportInterest::unitPrice).reversed())
                .thenComparing(EconomicSnapshot.ExportInterest::buyer).thenComparing(EconomicSnapshot.ExportInterest::item);
    }
}
