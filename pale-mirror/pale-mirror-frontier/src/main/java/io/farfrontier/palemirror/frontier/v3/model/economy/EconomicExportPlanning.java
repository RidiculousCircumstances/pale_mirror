package io.farfrontier.palemirror.frontier.v3.model.economy;

import io.farfrontier.palemirror.frontier.v3.model.supply.*;
import java.util.*;

/** Conditional export production consumes spare capacity, never a neighbour's money or an assumed sale. */
final class EconomicExportPlanning {
    List<EconomicPlan.Export> plan(EconomicSnapshot facts, SupplyPlanner.Plan supply,
                                  Map<String, Long> saleable, EconomicPlanningRules policy, long horizonTicks) {
        var labour = new TreeMap<String, Long>(); var capacity = new HashMap<String, Long>();
        var heat = new HashMap<String, Long>();
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
            heat.put(process.id(), Math.max(0, process.availableHeat() - Math.multiplyExact(domesticBatches, process.heatPerBatch())));
            consumeLabour(labour, process, domesticBatches);
        }
        var buyerCash = new TreeMap<String, Long>();
        for (var interest : facts.exportInterest()) buyerCash.merge(interest.buyer(), interest.availableCash(), Math::min);
        var result = new TreeMap<String, EconomicPlan.Export>();
        var valuation = new ExportProductionValuation(facts);
        long remaining = policy.maximumExportItems();
        for (var interest : facts.exportInterest().stream().sorted(valuation.order(facts, capacity)).toList()) {
            long wanted = Math.min(remaining, Math.min(interest.quantity(), buyerCash.get(interest.buyer()) / interest.unitPrice()));
            String item = interest.item(); long held = Math.min(wanted, inputs.get(item)); inputs.merge(item, -held, Long::sum);
            long made = 0;
            for (var process : facts.processes().stream().filter(p -> p.product().equals(item))
                    .sorted(Comparator.<SupplyPlanner.Process, ExportProductionValuation.Return>comparing(
                            p -> valuation.returnOn(p, interest.unitPrice())).reversed().thenComparing(SupplyPlanner.Process::id)).toList()) {
                // Sourcing's shadow labour price ranks make-or-buy alternatives;
                // it is not a cash wage or a floor for otherwise idle public work.
                if (valuation.returnOn(process, interest.unitPrice()).margin() == 0) continue;
                long batches = Math.min(capacity.get(process.id()), (wanted - held - made) / process.outputPerBatch());
                if (!process.labourCapacity().isEmpty()) batches = Math.min(batches,
                        process.recipesForLabour(process.labourCapacity().keySet().stream().mapToLong(labour::get).sum()));
                for (var input : process.inputs().entrySet()) batches = Math.min(batches, inputs.get(input.getKey()) / input.getValue());
                // Export cannot purchase inputs speculatively; use retained station
                // heat only. Fuel purchasing is reserved for the domestic chain.
                if (process.heatPerBatch() > 0) batches = Math.min(batches, heat.get(process.id()) / process.heatPerBatch());
                if (batches == 0) continue;
                long selected = batches;
                process.inputs().forEach((resource, quantity) -> inputs.merge(resource, -Math.multiplyExact(selected, quantity), Long::sum));
                consumeLabour(labour, process, batches); capacity.merge(process.id(), -batches, Long::sum);
                heat.merge(process.id(), -Math.multiplyExact(batches, process.heatPerBatch()), Long::sum);
                made = Math.addExact(made, Math.multiplyExact(batches, process.outputPerBatch()));
            }
            long quantity = Math.addExact(held, made); if (quantity == 0) continue;
            long proceeds = Math.multiplyExact(quantity, interest.unitPrice()); buyerCash.merge(interest.buyer(), -proceeds, Long::sum); remaining -= quantity;
            var previous = result.getOrDefault(item, new EconomicPlan.Export(item, 0, 0, 0));
            result.put(item, new EconomicPlan.Export(item, Math.addExact(previous.existingStock(), held),
                    Math.addExact(previous.additionalProduction(), made), Math.addExact(previous.conditionalRevenue(), proceeds)));
        }
        return List.copyOf(result.values());
    }
    private static void consumeLabour(Map<String, Long> labour, SupplyPlanner.Process process, long batches) {
        long missing = process.labourTicks(batches);
        for (var worker : process.labourCapacity().keySet().stream().sorted().toList()) {
            long take = Math.min(missing, labour.get(worker)); labour.merge(worker, -take, Long::sum); missing -= take;
        }
    }
}
