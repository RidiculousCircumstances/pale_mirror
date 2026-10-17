package io.farfrontier.palemirror.frontier.v3.model.economy;

import io.farfrontier.palemirror.frontier.v3.model.supply.SupplyPlanner;

/** Conservative dated credit for already allocated direct-source capacity. */
final class EconomicForecast {
    static long directOutputBy(EconomicSnapshot facts, SupplyPlanner.Plan plan, String item, long horizon) {
        var demand = plan.require(item);
        if (demand.production() == 0 || demand.process().isEmpty()) return 0;
        var process = facts.processes().stream().filter(p -> p.id().equals(demand.process().orElseThrow())).findFirst().orElseThrow();
        // Dependent transformations require input-arrival sequencing; do not turn
        // an aggregate output estimate into an invented dated receipt here.
        if (!process.inputs().isEmpty() || process.heatPerBatch() > 0 || process.firstOutputDelayTicks() > horizon) return 0;
        long batches = Math.min(process.maximumBatches(), Math.multiplyExact(process.recipesPerOperation(),
                Math.addExact(1, (horizon - process.firstOutputDelayTicks()) / process.operationTicks())));
        return Math.min(demand.production(), Math.multiplyExact(batches, process.outputPerBatch()));
    }
    private EconomicForecast() { }
}
