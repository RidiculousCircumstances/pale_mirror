package io.farfrontier.palemirror.frontier.v3.model.economy;

import io.farfrontier.palemirror.frontier.v3.model.supply.*;
import java.util.*;

/** Conditional export production consumes spare capacity, never a neighbour's money or an assumed sale. */
final class EconomicExportPlanning {
    record Plan(List<EconomicPlan.Export> exports, Map<String, Long> additionalProduction) {
        Plan { exports = List.copyOf(exports); additionalProduction = Map.copyOf(additionalProduction); }
    }
    Plan plan(EconomicSnapshot facts, SupplyPlanner.Plan supply,
                                  Map<String, Long> saleable, EconomicPlanningRules policy, long horizonTicks) {
        var labour = new TreeMap<String, Long>(); var capacity = new HashMap<String, Long>();
        var heat = new HashMap<String, Long>();
        var usedRecipes = new HashMap<String, Long>();
        var inputs = new TreeMap<String, Long>();
        facts.stocks().forEach((item, stock) -> inputs.put(item, saleable.get(item)));
        for (var process : facts.processes()) process.labourCapacity().forEach((worker, ticks) -> {
            var previous = labour.putIfAbsent(worker, ticks);
            if (previous != null && !previous.equals(ticks)) throw new IllegalArgumentException("inconsistent economic labour capacity");
        });
        for (var process : facts.processes()) {
            var demand = supply.require(process.product());
            long domesticBatches = demand.process().filter(process.id()::equals).isPresent()
                    ? Math.ceilDiv(Math.addExact(demand.production(), demand.recoveryProduction()), process.outputPerBatch()) : 0;
            // A later crop wave is not spare export capacity inside this plan.
            // Use the same owner-dated release boundary as domestic forecasts.
            capacity.put(process.id(), Math.max(0, process.recipesAvailableBy(horizonTicks) - domesticBatches));
            usedRecipes.put(process.id(), domesticBatches);
            heat.put(process.id(), Math.max(0, process.availableHeat() - Math.multiplyExact(domesticBatches, process.heatPerBatch())));
            consumeLabour(labour, process, domesticBatches);
        }
        var buyerCash = new TreeMap<String, Long>();
        record BudgetKey(String buyer, String item) { }
        var budgets = new HashMap<BudgetKey, Long>();
        for (var interest : facts.exportInterest()) buyerCash.merge(interest.buyer(), interest.availableCash(), Math::min);
        for (var interest : facts.exportInterest())
            budgets.merge(new BudgetKey(interest.buyer(), interest.item()), interest.commodityBudget(), Math::min);
        var result = new TreeMap<String, EconomicPlan.Export>();
        var valuation = new ExportProductionValuation(facts);
        var allocation = new ProductionChainAllocation(facts.processes(), inputs, usedRecipes, labour, heat, facts.replacementPrices());
        long remaining = policy.maximumExportItems();
        for (var interest : facts.exportInterest().stream().sorted(valuation.order(facts, capacity)).toList()) {
            var budget = new BudgetKey(interest.buyer(), interest.item());
            long spending = Math.min(buyerCash.get(interest.buyer()), budgets.get(budget));
            long wanted = Math.min(remaining, Math.min(interest.quantity(), spending / interest.buyerUnitExpense()));
            String item = interest.item();
            var allowed = facts.processes().stream().filter(p -> p.product().equals(item)
                            && valuation.returnOn(p, interest.unitPrice()).margin() >= 0)
                    .map(SupplyPlanner.Process::id).collect(java.util.stream.Collectors.toSet());
            var selected = allocation.allocate(item, wanted, horizonTicks, allowed);
            allocation = selected.next();
            long held = selected.existingStock(), made = selected.quantity() - held;
            long quantity = selected.quantity(); if (quantity == 0) continue;
            long proceeds = Math.multiplyExact(quantity, interest.unitPrice());
            long expense = Math.multiplyExact(quantity, interest.buyerUnitExpense());
            buyerCash.merge(interest.buyer(), -expense, Long::sum); remaining -= quantity;
            budgets.merge(budget, -expense, Long::sum);
            var previous = result.getOrDefault(item, new EconomicPlan.Export(item, 0, 0, 0));
            result.put(item, new EconomicPlan.Export(item, Math.addExact(previous.existingStock(), held),
                    Math.addExact(previous.additionalProduction(), made), Math.addExact(previous.conditionalRevenue(), proceeds)));
        }
        return new Plan(List.copyOf(result.values()), allocation.additionalProduction());
    }
    private static void consumeLabour(Map<String, Long> labour, SupplyPlanner.Process process, long batches) {
        long missing = process.labourTicks(batches);
        for (var worker : process.labourCapacity().keySet().stream().sorted().toList()) {
            long take = Math.min(missing, labour.get(worker)); labour.merge(worker, -take, Long::sum); missing -= take;
        }
    }
}
