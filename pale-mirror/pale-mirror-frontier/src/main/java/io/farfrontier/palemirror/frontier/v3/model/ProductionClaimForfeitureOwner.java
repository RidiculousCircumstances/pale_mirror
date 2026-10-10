package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

/** Production owns input reallocation, payment and retirement, not the stock ledger. */
final class ProductionClaimForfeitureOwner implements FungibleClaimForfeitureOwner {
    @Override public void validate(FrontierWorldState state, ClaimAllocation claim, FungibleResourceHandoffObserved observed) {
        requireJob(state, claim);
    }

    @Override public FungibleForfeitureSettlement settle(FrontierWorldState before, List<ClaimAllocation> claims,
                                                        FungibleForfeitureSettlement transaction) {
        var jobs = new LinkedHashMap<>(transaction.productionJobs());
        var inventory = transaction.inventory();
        var companies = transaction.companies();
        var strategic = transaction.plans();
        var executions = transaction.executions();
        for (var claim : claims) {
            ProductionJob job = requireJob(before, claim);
            if (job.bakeryWork().isPresent()) {
                BakeryWorkState work = job.bakeryWork().orElseThrow();
                CustodyAccount sourceAfter = inventory.fungibleResources().accounts().get(work.sourceAccountId());
                int available = sourceAfter == null ? 0 : sourceAfter.lotQuantities().values().stream().mapToInt(Integer::intValue).sum();
                jobs.put(job.id(), job.withBakeryWork(work.withBlock(Optional.of(new BakeryWorkBlock(
                        BakeryWorkBlock.Reason.SOURCE_CHANGED, FrontierWorldState.depotId(job.settlementId()), -1,
                        claim.itemKind(), Math.min(available, 127))))));
                continue; // Keep the accepted order/payment; this baker may reallocate before pickup.
            }
            MarketWorkOrder order = before.companies().market().acceptedForJob(job.id()).orElse(null);
            if (order != null) {
                inventory = inventory.withEconomics(inventory.economics().release(order.reservationId()));
                companies = companies.withMarket(companies.market().cancel(order.id(), MarketWorkOrderStatus.CANCELLED));
            }
            strategic = strategic.transitionTask(job.taskId(), StrategicTaskStatus.BLOCKED);
            jobs.remove(job.id());
            executions = ActorExecutionComposition.LIFECYCLE.retire(executions, job.workerId(), ActorActivityKind.PRODUCTION, job.id());
        }
        return transaction.withProduction(inventory, companies, executions, jobs, strategic);
    }

    private static ProductionJob requireJob(FrontierWorldState state, ClaimAllocation claim) {
        ProductionJob job = state.productionJobs().get(claim.claimantId());
        if (job == null || claim.purpose() != ClaimPurpose.PRODUCTION_WORK)
            throw new IllegalArgumentException("stock loss has no declared production owner");
        if (!(job.inputHold() instanceof ProductionInputHold.FungibleBound held) || !held.claimId().equals(claim.id())
                || !claim.economicOwnerId().equals(job.settlementId()) || !"minecraft:wheat".equals(claim.itemKind())
                || claim.quantity() != job.outputCount() || !claim.lotQuantities().equals(held.inputLots())
                || state.physicalIntents().values().stream().anyMatch(intent -> intent.causeSubjectId().equals(job.id()))
                || job.bakeryWork().isEmpty() && state.sceneLeases().values().stream().anyMatch(lease -> lease.status() != SceneLeaseStatus.CLOSED
                    && FrontierSceneBehaviors.isProductionWork(lease) && FrontierSceneBehaviors.productionWork(lease).jobId().equals(job.id())))
            throw new IllegalArgumentException("stock loss cannot bypass an active production effect");
        StrategicTask task = state.strategicPlans().tasks().get(job.taskId());
        if (task == null || task.kind() != StrategicTaskKind.PRODUCE_BREAD || task.status() != StrategicTaskStatus.ACTIVE
                || !task.ownerId().equals(job.settlementId()))
            throw new IllegalArgumentException("stock loss has no exact current production task");
        if (job.bakeryWork().isPresent() && (job.bakeryWork().orElseThrow().phase() != BakeryWorkState.Phase.DEPOT_PICKUP
                || job.bakeryWork().orElseThrow().pendingPhysicalStep().isPresent()))
            throw new IllegalArgumentException("bakery input cannot be reallocated after a physical effect begins");
        MarketWorkOrder order = state.companies().market().acceptedForJob(job.id()).orElse(null);
        if (order != null) {
            FinancialReservation reservation = state.inventory().economics().reservations().get(order.reservationId());
            if (!order.taskId().equals(job.taskId()) || reservation == null || !reservation.reasonId().equals(job.id())
                    || !reservation.payerId().equals(job.settlementId()) || !reservation.payeeId().equals(order.sellerId()))
                throw new IllegalArgumentException("stock loss has no matching production reservation");
        }
        return job;
    }
}
