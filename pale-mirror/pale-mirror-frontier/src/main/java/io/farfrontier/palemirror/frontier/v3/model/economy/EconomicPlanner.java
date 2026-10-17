package io.farfrontier.palemirror.frontier.v3.model.economy;

import io.farfrontier.palemirror.frontier.v3.model.supply.*;
import java.util.*;

/** Settlement-level coordination; the supply solver owns recipe feasibility and source selection. */
public final class EconomicPlanner {
    public SupplyPlanner.Plan plan(EconomicSnapshot facts, EconomicPlanningRules policy) {
        long horizon = Math.max(Math.multiplyExact(facts.dayTicks(), policy.horizonDays()),
                facts.reserves().values().stream().mapToLong(SupplyReservePlanner.Target::horizonTicks).max().orElseThrow());
        var requirements = new EconomicRequirements(); var projected = requirements.project(facts, horizon);
        var critical = requirements.project(facts, facts.dayTicks());
        var stocks = new TreeMap<String, SupplyPlanner.Stock>(); var reserves = new TreeMap<String, SupplyReservePlanner.Target>();
        var domestic = new TreeMap<String, Long>();
        for (var entry : facts.stocks().entrySet()) {
            String item = entry.getKey(); var stock = entry.getValue(); var reserve = facts.reserves().get(item);
            long minimum = critical.gross().get(item);
            long target = Math.max(minimum, projected.gross().get(item)); domestic.put(item, target);
            stocks.put(item, new SupplyPlanner.Stock(stock.available(), stock.incoming(), stock.promisedProduction(), target,
                    minimum, stock.consumptionUnits(), stock.consumptionPeriodTicks(), stock.policy(), stock.arrivals(), OptionalLong.of(target)));
            reserves.put(item, new SupplyReservePlanner.Target(target, minimum, target, Math.max(horizon, reserve.timing().leadTicks()),
                    reserve.consumption(), reserve.timing()));
        }
        var supply = new SupplyPlanner().plan(stocks, facts.processes(), facts.commitments(), facts.market());
        var funding = new EconomicFunding().allocate(facts, supply, projected, horizon);
        var exports = new EconomicExportPlanning().plan(facts, supply, domestic, policy);
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
            production.put(item, selectedSource ? Math.min(domestic.get(item), Math.addExact(stock.covered(), own)) : domestic.get(item));
        });
        for (var export : exports) production.merge(export.item(), export.additionalProduction(), Math::addExact);
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
            var projection = requirements.project(facts, after); long cost = 0;
            for (var entry : projection.missing().entrySet()) if (!manufactured.contains(entry.getKey()))
                cost = Math.addExact(cost, Math.multiplyExact(Math.max(0, entry.getValue() - EconomicForecast.directOutputBy(facts, supply, entry.getKey(), after)), facts.replacementPrices().get(entry.getKey())));
            checkpoints.add(new EconomicPlan.Checkpoint(after, projection.missing(), cost, Math.subtractExact(facts.market().availableMoney(), cost)));
        }
        var economy = new EconomicPlan(horizon, domestic, production, funding.budgets(), funding.liquidity(),
                funding.unfunded(), facts.contractReceivables(), checkpoints, exports, constraints);
        return new SupplyPlanner.Plan(supply.demands(), reserves, supply.preferredPurchaseSpending(), Optional.of(economy));
    }
}
