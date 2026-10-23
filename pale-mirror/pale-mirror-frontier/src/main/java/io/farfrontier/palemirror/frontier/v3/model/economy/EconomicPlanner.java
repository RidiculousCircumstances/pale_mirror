package io.farfrontier.palemirror.frontier.v3.model.economy;

import io.farfrontier.palemirror.frontier.v3.model.supply.*;
import java.util.*;

/** Settlement-level coordination; the supply solver owns recipe feasibility and source selection. */
public final class EconomicPlanner {
    private record Input(EconomicSnapshot facts, EconomicPlanningRules policy) { }
    private final io.farfrontier.palemirror.frontier.v3.model.BoundedValueMemo<Input, Domestic> domesticViews =
            new io.farfrontier.palemirror.frontier.v3.model.BoundedValueMemo<>(32);
    /** An assessed plan cannot be paired with another state's facts or policy. */
    public static final class Domestic {
        private final EconomicSnapshot facts;
        private final EconomicPlanningRules policy;
        private final SupplyPlanner.Plan plan;
        private Domestic(EconomicSnapshot facts, EconomicPlanningRules policy, SupplyPlanner.Plan plan) {
            this.facts = facts; this.policy = policy; this.plan = plan;
        }
        public SupplyPlanner.Plan plan() { return plan; }
        public EconomicSnapshot facts() { return facts; }
    }
    public SupplyPlanner.Plan plan(EconomicSnapshot facts, EconomicPlanningRules policy) {
        return withExports(domestic(facts, policy), facts.exportInterest());
    }

    /** Independent of neighbours' plans: the funded domestic envelope is reusable
     * by quotation and export evaluation without recursive settlement planning. */
    public Domestic domestic(EconomicSnapshot facts, EconomicPlanningRules policy) {
        return domesticViews.get(new Input(facts, policy), () -> evaluateDomestic(facts, policy));
    }
    private Domestic evaluateDomestic(EconomicSnapshot facts, EconomicPlanningRules policy) {
        var selected = evaluate(facts, policy, facts.market());
        var economy = selected.plan.economy().orElseThrow();
        if (economy.unfundedRequirements() == 0 || selected.plan.preferredPurchaseQuantities().isEmpty()) return selected;
        // Unit profitability alone is not a cash-flow plan. Compare the selected
        // import portfolio with own production, using the SAME real funding facts.
        // Zero here disables optional sourcing only; it does not erase treasury
        // money from financing, add a subsidy or credit conditional exports.
        var local = evaluate(facts, policy, new SourcingMarket(0, facts.market().switchingSavingsPermille(),
                facts.market().processUnitCosts(), facts.market().offers()));
        return EconomicPlanChoice.prefer(local.plan, selected.plan, facts) ? local : selected;
    }
    private Domestic evaluate(EconomicSnapshot facts, EconomicPlanningRules policy, SourcingMarket selection) {
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
        var supply = new SupplyPlanner().plan(stocks, facts.processes(), facts.commitments(), selection);
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
        var saleable = EconomicStockRelease.assess(facts, supply, reserves);
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
        var constraints = EnumSet.noneOf(EconomicPlan.Constraint.class);
        if (supply.demands().values().stream().anyMatch(d -> d.recoveryProduction() > 0 || d.purchase() > 0))
            constraints.add(EconomicPlan.Constraint.MATERIAL_SHORTFALL);
        if (funding.unfunded() > 0) constraints.add(EconomicPlan.Constraint.CASH_SHORTFALL);
        if (funding.unfunded() > 0) constraints.add(EconomicPlan.Constraint.NO_EXPORT_MARKET);
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
                funding.unfunded(), facts.contractReceivables(), checkpoints, List.of(), constraints,
                funding.prices(), funding.fundedRestarts(), saleable);
        return new Domestic(facts, policy, new SupplyPlanner.Plan(supply.demands(), reserves, supply.preferredPurchaseSpending(),
                supply.preferredPurchaseQuantities(), supply.inputRequirements(), Optional.of(economy), supply.backedRecipes()));
    }

    /** Adds conditional sales to an already evaluated domestic plan. Does not
     * re-run source selection, allocate the buyer's money or count future revenue as cash. */
    public SupplyPlanner.Plan withExports(Domestic evaluated, List<EconomicSnapshot.ExportInterest> interests) {
        var facts = evaluated.facts.withExportInterest(interests);
        var domestic = evaluated.plan;
        var base = domestic.economy().orElseThrow();
        var exports = new EconomicExportPlanning().plan(facts, domestic, base.saleableStock(), evaluated.policy, base.horizonTicks());
        var production = new TreeMap<>(base.additionalProduction());
        exports.additionalProduction().forEach((item, amount) -> production.merge(item, amount, Math::addExact));
        var constraints = EnumSet.noneOf(EconomicPlan.Constraint.class);
        constraints.addAll(base.constraints());
        if (!exports.exports().isEmpty()) {
            constraints.remove(EconomicPlan.Constraint.NO_EXPORT_MARKET);
            constraints.add(EconomicPlan.Constraint.CONDITIONAL_EXPORT_INCOME);
        }
        var economy = new EconomicPlan(base.horizonTicks(), base.domesticTargets(), production, base.purchaseBudgets(),
                base.retainedLiquidity(), base.unfundedRequirements(), base.contractReceivables(), base.checkpoints(),
                exports.exports(), constraints, base.purchasePrices(), base.fundedRestarts(), base.saleableStock());
        return new SupplyPlanner.Plan(domestic.demands(), domestic.reserves(), domestic.preferredPurchaseSpending(),
                domestic.preferredPurchaseQuantities(), domestic.inputRequirements(), Optional.of(economy), domestic.backedRecipes());
    }
}
