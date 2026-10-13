package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind;
import java.util.*;

/** A disrupted station order releases its own task and scope, never the resident's body or unrelated activity. */
final class BakeryStationCancellation {
    static ProductionJob require(FrontierWorldState before, ProductionOperation operation) {
        var job = before.productionJobs().get(operation.orderId());
        if (job == null || !BakeryStationOperation.require(before, job).equals(operation)
                || job.bakeryWork().orElseThrow().phase() != (operation.transformed()
                    ? BakeryWorkState.Phase.STATION_UNLOAD : BakeryWorkState.Phase.PROCESSING)
                || job.bakeryWork().orElseThrow().pendingPhysicalStep().isPresent())
            throw new IllegalArgumentException("station input loss has no current uncommitted bakery order");
        return job;
    }
    static FungibleForfeitureSettlement cancel(FrontierWorldState before, ProductionOperation operation, FungibleForfeitureSettlement transaction) {
        var job = require(before, operation);
        var jobs = new LinkedHashMap<>(transaction.productionJobs()); jobs.remove(job.id());
        var inventory = transaction.inventory(); var companies = transaction.companies();
        var invoice = companies.market().acceptedForJob(job.id());
        if (invoice.isPresent()) {
            var order = invoice.orElseThrow();
            inventory = inventory.withEconomics(inventory.economics().release(order.reservationId()));
            companies = companies.withMarket(companies.market().cancel(order.id(), MarketWorkOrderStatus.CANCELLED));
        }
        var plans = transaction.plans().transitionTask(job.taskId(), StrategicTaskStatus.BLOCKED);
        var executions = ActorExecutionComposition.LIFECYCLE.retire(transaction.executions(), job.workerId(), ActorActivityKind.PRODUCTION, job.id());
        var leases = new LinkedHashMap<>(transaction.leases());
        for (var lease : transaction.leases().values()) {
            if (lease.status() != SceneLeaseStatus.CLOSED && FrontierSceneBehaviors.isProductionWork(lease)
                    && FrontierSceneBehaviors.productionWork(lease).jobId().equals(job.id()))
                leases.put(lease.id(), lease.withStatus(SceneLeaseStatus.CLOSED));
        }
        return transaction.withProduction(inventory, companies, executions, jobs, plans).withLeases(leases);
    }
    static List<SourceStockClaimLossOwner.ReleasedActivity> released(FrontierWorldState before, ProductionOperation operation) {
        var job = require(before, operation);
        return List.of(new SourceStockClaimLossOwner.ReleasedActivity(job.workerId(),
                Optional.of(ProductionContinuation.at(job, 0).id()), OptionalLong.empty()));
    }
    private BakeryStationCancellation() { }
}
