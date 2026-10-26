package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

/** Production owns input reallocation, payment and retirement, not the stock ledger. */
final class ProductionClaimForfeitureOwner implements FungibleClaimForfeitureOwner {
    @Override public void validate(FrontierWorldState state, ClaimAllocation claim,
                                   io.farfrontier.palemirror.frontier.v3.api.SubjectId sourceAccount) {
        var job = requireJob(state, claim);
        requireMaterial(state, job, claim, sourceAccount);
    }
    @Override public List<SourceStockClaimLossOwner.ReleasedActivity> released(FrontierWorldState before,
                                                                             ClaimAllocation claim) {
        return List.of(new SourceStockClaimLossOwner.ReleasedActivity(requireJob(before, claim).workerId(),
                Optional.empty(), java.util.OptionalLong.empty()));
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
            if (job.stationWork().isPresent()) {
                job = jobs.get(job.id());
                StationWorkState work = job.stationWork().orElseThrow();
                var currentClaim = currentClaim(job);
                if (!currentClaim.equals(claim.id())) {
                    var station = ProductionStationEnergy.record(before, work.stationId()).productionStation().orElseThrow();
                    jobs.put(job.id(), job.withStationWork(work.withMaterials(work.materials().lost(claim.id(), station.containerId(), work.stationAccountId()))));
                } else {
                    CustodyAccount sourceAfter = inventory.fungibleResources().accounts().get(work.sourceAccountId());
                    int available = sourceAfter == null ? 0 : sourceAfter.lotQuantities().values().stream().mapToInt(Integer::intValue).sum();
                    jobs.put(job.id(), job.withStationWork(work.withBlock(Optional.of(new BakeryWorkBlock(
                            BakeryWorkBlock.Reason.SOURCE_CHANGED, StationWorkStorage.source(job), -1,
                            claim.itemKind(), Math.min(available, 127))))));
                }
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

    private static io.farfrontier.palemirror.frontier.v3.api.SubjectId currentClaim(ProductionJob job) {
        return switch (job.inputHold()) {
            case ProductionInputHold.FungibleCold hold -> hold.claimId();
            case ProductionInputHold.FungibleBound hold -> hold.claimId();
            default -> throw new IllegalArgumentException("material loss has no fungible production input");
        };
    }
    private static void requireMaterial(FrontierWorldState state, ProductionJob job, ClaimAllocation claim,
            io.farfrontier.palemirror.frontier.v3.api.SubjectId source) {
        if (job.stationWork().isEmpty()) {
            if (!(job.inputHold() instanceof ProductionInputHold.FungibleBound held) || !held.claimId().equals(claim.id())
                    || !held.accountId().equals(source) || !held.inputLots().equals(claim.lotQuantities()))
                throw new IllegalArgumentException("stock loss has a foreign production allocation");
            return;
        }
        var work = job.stationWork().orElseThrow();
        if (currentClaim(job).equals(claim.id())) {
            if (!work.phase().inputAtSource() || !work.sourceAccountId().equals(source)
                    || !claim.itemKind().equals(work.inputItemKind()) || !claim.lotQuantities().equals(job.inputQuantities()))
                throw new IllegalArgumentException("current station input is already in flight");
            return;
        }
        for (var input : work.materials().queued()) if (input.claimId().equals(claim.id())) {
            if (input.withdrawn() || !input.sourceAccount().equals(source) || !input.itemKind().equals(claim.itemKind()) || !input.lots().equals(claim.lotQuantities()))
                throw new IllegalArgumentException("queued production input has a foreign allocation");
            return;
        }
        for (var input : work.materials().loaded()) if (input.claimId().equals(claim.id())) {
            if (work.phase() == StationWorkState.Phase.PROCESSING || StationWorkCargo.output(job)
                    || !work.stationAccountId().equals(source) || !input.itemKind().equals(claim.itemKind()) || !input.lots().equals(claim.lotQuantities()))
                throw new IllegalArgumentException("loaded material already belongs to a processing operation");
            return;
        }
        throw new IllegalArgumentException("stock loss is not one of this order's retained materials");
    }

    private static ProductionJob requireJob(FrontierWorldState state, ClaimAllocation claim) {
        ProductionJob job = state.productionJobs().get(claim.claimantId());
        if (job == null || claim.purpose() != ClaimPurpose.PRODUCTION_WORK)
            throw new IllegalArgumentException("stock loss has no declared production owner");
        if (job.stationWork().isPresent() && !StationWorkMaterialAccounting.claims(job).contains(claim.id())
                || !claim.economicOwnerId().equals(job.rights().resourceOwner().id())
                || state.physicalIntents().values().stream().anyMatch(intent -> intent.causeSubjectId().equals(job.id()))
                || job.stationWork().isPresent() && job.stationWork().orElseThrow().pendingPhysicalStep().isPresent()
                || job.stationWork().isEmpty() && state.sceneLeases().values().stream().anyMatch(lease -> lease.status() != SceneLeaseStatus.CLOSED
                    && FrontierSceneBehaviors.isProductionWork(lease) && FrontierSceneBehaviors.productionWork(lease).jobId().equals(job.id())))
            throw new IllegalArgumentException("stock loss cannot bypass an active production effect");
        StrategicTask task = state.strategicPlans().tasks().get(job.taskId());
        if (task == null || task.kind() != StrategicTaskKind.PRODUCE_AT_STATION || task.status() != StrategicTaskStatus.ACTIVE
                || !task.ownerId().equals(job.settlementId()))
            throw new IllegalArgumentException("stock loss has no exact current production task");
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
