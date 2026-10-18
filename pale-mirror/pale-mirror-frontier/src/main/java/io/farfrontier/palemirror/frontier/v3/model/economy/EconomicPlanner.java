package io.farfrontier.palemirror.frontier.v3.model.economy;

import io.farfrontier.palemirror.frontier.v3.model.supply.*;
import java.util.*;

/** Settlement-level coordination; the supply solver owns recipe feasibility and source selection. */
public final class EconomicPlanner {
    public SupplyPlanner.Plan plan(EconomicSnapshot facts, EconomicPlanningRules policy) {
        long horizon = Math.max(Math.multiplyExact(facts.dayTicks(), policy.horizonDays()),
                facts.reserves().values().stream().mapToLong(SupplyReservePlanner.Target::horizonTicks).max().orElseThrow());
        var requirements = new EconomicRequirements();
        var stocks = new TreeMap<String, SupplyPlanner.Stock>(); var reserves = new TreeMap<String, SupplyReservePlanner.Target>();
        var domestic = new TreeMap<String, Long>();
        for (var entry : facts.stocks().entrySet()) {
            String item = entry.getKey(); var stock = entry.getValue(); var need = facts.needs().get(item);
            // Only direct needs enter source selection. SupplyPlanner expands the
            // inputs of the selected production, never of its purchase alternative.
            long minimum = Math.addExact(need.immediate(), need.consumption().over(facts.dayTicks()));
            long target = Math.addExact(need.immediate(), need.consumption().over(horizon)); domestic.put(item, target);
            stocks.put(item, new SupplyPlanner.Stock(stock.available(), stock.incoming(), stock.promisedProduction(), target,
                    minimum, stock.consumptionUnits(), stock.consumptionPeriodTicks(), stock.policy(), stock.arrivals(), OptionalLong.of(target)));
        }
        var supply = new SupplyPlanner().plan(stocks, facts.processes(), facts.commitments(), facts.market());
        supply.inputRequirements().forEach((item, amount) -> domestic.merge(item, amount, Math::addExact));
        var missing = new TreeMap<String, Long>();
        domestic.forEach((item, target) -> {
            var stock = facts.stocks().get(item); var reserve = facts.reserves().get(item);
            var need = facts.needs().get(item);
            long directProtection = Math.addExact(need.immediate(), need.consumption().over(
                    Math.min(horizon, Math.max(facts.dayTicks(), reserve.timing().leadTicks()))));
            long minimum = Math.min(target, Math.max(directProtection, supply.require(item).protectedInput()));
            reserves.put(item, new SupplyReservePlanner.Target(target, minimum, target, Math.max(horizon, reserve.timing().leadTicks()),
                    reserve.consumption(), reserve.timing()));
            long arriving = stock.arrivals().stream().filter(a -> a.afterTicks() <= horizon).mapToLong(SupplyPlanner.Arrival::quantity).sum();
            missing.put(item, Math.max(0, target - Math.addExact(stock.available(), arriving)));
        });
        var projected = new EconomicRequirements.Projection(domestic, missing);
        var funding = new EconomicFunding().allocate(facts, supply, projected, horizon);
        var saleable = new TreeMap<String, Long>();
        for (var entry : facts.stocks().entrySet()) {
            String item = entry.getKey();
            // Act on forecast cash shortfall before a restart actually stalls.
            // Only optional upper reserves become offers; immediate needs and
            // backed production inputs remain protected. Offers are not income.
            long keep = funding.unfunded() > 0 ? reserves.get(item).protectedQuantity() : domestic.get(item);
            saleable.put(item, Math.max(0, entry.getValue().available() - keep));
        }
        var exportPlan = new EconomicExportPlanning().plan(facts, supply, saleable, policy, horizon);
        var exports = exportPlan.exports();
        var production = new TreeMap<String, Long>();
        facts.stocks().forEach((item, stock) -> {
            var demand = supply.require(item);
            // Only the chosen own-production alternative may start work. A desired
            // reserve is not an order to produce goods already selected for import.
            long own = Math.addExact(demand.production(), demand.recoveryProduction());
            boolean selectedSource = demand.process().isPresent()
                    || supply.preferredPurchaseSpending().getOrDefault(item, 0L) > 0;
            // An uncovered need still reaches source-development owners: opening
            // the next mining face is what creates capacity, not its prerequisite.
            // Admission receives an incremental quantity, not an absolute target
            // measured against this snapshot's usable-stock accounting scope.
            long uncovered = Math.max(0, domestic.get(item) - stock.covered());
            production.put(item, selectedSource ? Math.min(uncovered, own) : uncovered);
        });
        exportPlan.additionalProduction().forEach((item, amount) -> production.merge(item, amount, Math::addExact));
        var constraints = EnumSet.noneOf(EconomicPlan.Constraint.class);
        if (supply.demands().values().stream().anyMatch(d -> d.recoveryProduction() > 0 || d.purchase() > 0))
            constraints.add(EconomicPlan.Constraint.MATERIAL_SHORTFALL);
        if (funding.unfunded() > 0) constraints.add(EconomicPlan.Constraint.CASH_SHORTFALL);
        if (!exports.isEmpty()) constraints.add(EconomicPlan.Constraint.CONDITIONAL_EXPORT_INCOME);
        else if (funding.unfunded() > 0) constraints.add(EconomicPlan.Constraint.NO_EXPORT_MARKET);
        var checkpoints = new ArrayList<EconomicPlan.Checkpoint>();
        var manufactured = facts.flows().stream().map(SupplyReservePlanner.Flow::product).collect(java.util.stream.Collectors.toSet());
        for (int i = 1; i <= policy.checkpoints(); i++) {
            long after = Math.ceilDiv(Math.multiplyExact(horizon, i), policy.checkpoints());
            var projection = requirements.project(facts, after, supply.preferredPurchaseQuantities()); long cost = 0;
            for (var entry : projection.missing().entrySet()) {
                String item = entry.getKey(); long selected = supply.preferredPurchaseQuantities().getOrDefault(item, 0L);
                long buying = Math.min(entry.getValue(), selected);
                if (buying > 0) cost = Math.addExact(cost, Math.ceilDiv(Math.multiplyExact(buying,
                        supply.preferredPurchaseSpending().get(item)), selected));
                if (!manufactured.contains(item)) cost = Math.addExact(cost, Math.multiplyExact(Math.max(0,
                        entry.getValue() - buying - EconomicForecast.directOutputBy(facts, supply, item, after)), facts.replacementPrices().get(item)));
            }
            checkpoints.add(new EconomicPlan.Checkpoint(after, projection.missing(), cost, Math.subtractExact(facts.market().availableMoney(), cost)));
        }
        var economy = new EconomicPlan(horizon, domestic, production, funding.budgets(), funding.liquidity(),
                funding.unfunded(), facts.contractReceivables(), checkpoints, exports, constraints,
                funding.prices(), funding.fundedRestarts(), saleable);
        return new SupplyPlanner.Plan(supply.demands(), reserves, supply.preferredPurchaseSpending(),
                supply.preferredPurchaseQuantities(), supply.inputRequirements(), Optional.of(economy));
    }
}
