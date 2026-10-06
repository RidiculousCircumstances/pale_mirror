package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Atomic account-layout/job-hold transitions; never reselects a job's input or worker. */
public final class ProductionResourceCustody {
    private ProductionResourceCustody() { }

    /** Admission only: select the explicit input representation from its current custody authority. */
    public static boolean canStart(FrontierWorldState state, FungibleResourceCustodySupport.LotAtContainer input, int quantity) {
        if (quantity < 1 || input.quantity() < quantity) return false;
        return canStart(state, new FungibleResourceCustodySupport.LotSelection(input.accountId(), Map.of(input.lot().id(), quantity)), quantity);
    }

    public static boolean canStart(FrontierWorldState state, FungibleResourceCustodySupport.LotSelection input, int quantity) {
        return quantity > 0 && input.quantity() == quantity && FungibleResourceCustodySupport.canReserve(state, input);
    }

    public static ProductionInputHold holdForStart(FrontierWorldState state, FungibleResourceCustodySupport.LotAtContainer input,
                                                    SubjectId claimId, int quantity) {
        return holdForStart(state, new FungibleResourceCustodySupport.LotSelection(input.accountId(), Map.of(input.lot().id(), quantity)), claimId, quantity);
    }

    public static ProductionInputHold holdForStart(FrontierWorldState state, FungibleResourceCustodySupport.LotSelection input,
                                                    SubjectId claimId, int quantity) {
        if (!canStart(state, input, quantity)) throw new IllegalArgumentException("production input has no admissible custody representation");
        var account = state.inventory().fungibleResources().accounts().get(input.accountId());
        var container = (ResourceCustody.Container) account.custody();
        if (!ReferenceContainerCustody.hasLiveCustody(state, container.containerId())) {
            return new ProductionInputHold.FungibleCold(input.firstLotId(), input.accountId(), claimId, input.lotQuantities());
        }
        long epoch = state.replicaCustody().custodyByScope().get(ReferenceContainerCustody.scopeId(container.containerId())).authorityEpoch();
        return new ProductionInputHold.FungibleBound(input.firstLotId(), input.accountId(), claimId, epoch, input.lotQuantities());
    }

    static void requireNewHold(FrontierWorldState state, ProductionJob job, SubjectId accountId, SubjectId claimId) {
        var resources = state.inventory().fungibleResources();
        var account = resources.accounts().get(accountId);
        if (account == null) throw new IllegalArgumentException("new production hold has no retained input account");
        var input = new FungibleResourceCustodySupport.LotSelection(accountId, job.inputQuantities());
        if (!job.inputHold().equals(holdForStart(state, input, claimId, job.outputCount()))) {
            throw new IllegalArgumentException("new production hold does not match current physical custody");
        }
    }

    public static FrontierWorldState bind(FrontierWorldState state, SubjectId accountId, long epoch,
                                          List<PhysicalStackBinding> bindings) {
        FungibleResourceLedger resources = state.inventory().fungibleResources().rebind(accountId, epoch, bindings);
        Map<SubjectId, ProductionJob> jobs = new LinkedHashMap<>(state.productionJobs());
        for (ProductionJob job : state.productionJobs().values()) {
            if (departedBakerySource(job, accountId)) continue;
            if (job.bakeryWork().isPresent() && BakeryWorkValidation.awaitingInputReallocation(resources,
                    job, job.bakeryWork().orElseThrow())) continue;
            if (job.inputHold() instanceof ProductionInputHold.FungibleCold cold && cold.accountId().equals(accountId)) {
                requireClaim(state, resources, job, accountId, cold.claimId());
                jobs.put(job.id(), job.withInputHold(new ProductionInputHold.FungibleBound(cold.itemId(), accountId, cold.claimId(), epoch, cold.inputLots())));
            } else if (job.inputHold() instanceof ProductionInputHold.FungibleBound bound && bound.accountId().equals(accountId)) {
                if (bound.authorityEpoch() != epoch) throw new IllegalArgumentException("production input binding has a foreign epoch");
                requireClaim(state, resources, job, accountId, bound.claimId());
            }
        }
        return state.withChanges(FrontierWorldStateUpdate.begin()
                .inventory(state.inventory().withFungibleResources(resources)).productionJobs(jobs));
    }

    public static FrontierWorldState release(FrontierWorldState state, SubjectId accountId, long epoch) {
        FungibleResourceLedger resources = state.inventory().fungibleResources().releaseBindings(accountId, epoch);
        Map<SubjectId, ProductionJob> jobs = new LinkedHashMap<>(state.productionJobs());
        var intents = new LinkedHashMap<>(state.physicalIntents());
        var recovery = state.fencedRecovery();
        for (ProductionJob job : state.productionJobs().values()) {
            if (departedBakerySource(job, accountId)) continue;
            if (job.bakeryWork().isPresent() && BakeryWorkValidation.awaitingInputReallocation(resources,
                    job, job.bakeryWork().orElseThrow())) continue;
            if (!(job.inputHold() instanceof ProductionInputHold.FungibleBound bound) || !bound.accountId().equals(accountId)) continue;
            if (bound.authorityEpoch() != epoch) throw new IllegalArgumentException("production input release has a foreign epoch");
            for (var intent : state.physicalIntents().values()) {
                if (!intent.causeSubjectId().equals(job.id())) continue;
                if (intent.status() != io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.PREPARED) {
                    throw new IllegalArgumentException("started production effect retains its exact physical input epoch");
                }
                FungibleProductionStateSupport.validateIntent(state, intent);
                // No physical write has begun. The same retained job/claim now owns the
                // COLD continuation; revoke the old actuator's authority in this transaction.
                recovery = FencedRecoveryPhysicalIntentSupport.composed(recovery, intent, FencedRecoveryAsset.EFFECT);
                intents.remove(intent.id());
            }
            requireClaim(state, resources, job, accountId, bound.claimId());
            jobs.put(job.id(), job.withInputHold(new ProductionInputHold.FungibleCold(bound.itemId(), accountId, bound.claimId(), bound.inputLots())));
        }
        return state.withChanges(FrontierWorldStateUpdate.begin()
                .inventory(state.inventory().withFungibleResources(resources)).productionJobs(jobs)
                .physicalIntents(intents).fencedRecovery(recovery));
    }

    /** The input hold records the selected source; after pickup it is not a live depot authority. */
    private static boolean departedBakerySource(ProductionJob job, SubjectId accountId) {
        return job.bakeryWork().filter(work -> work.phase() != BakeryWorkState.Phase.DEPOT_PICKUP
                && work.sourceAccountId().equals(accountId)).isPresent();
    }

    public static FungibleResourceLedger cancelBound(FrontierWorldState state, ProductionJob job, ProductionInputHold.FungibleBound bound) {
        FungibleResourceLedger resources = state.inventory().fungibleResources();
        requireClaim(state, resources, job, bound.accountId(), bound.claimId());
        List<PhysicalStackBinding> bindings = resources.bindings().values().stream()
                .filter(binding -> binding.accountId().equals(bound.accountId())).toList();
        if (bindings.isEmpty() || bindings.stream().anyMatch(binding -> binding.authorityEpoch() != bound.authorityEpoch())) {
            throw new IllegalArgumentException("production cancellation has no exact bound input epoch");
        }
        // Cancelling unperformed work releases only its reservation, never the physical stock.
        return resources.releaseClaims(java.util.Set.of(bound.claimId()));
    }

    private static void requireClaim(FrontierWorldState state, FungibleResourceLedger resources, ProductionJob job,
                                     SubjectId accountId, SubjectId claimId) {
        FrontierProductionInputHoldValidation.validateFungibleProductionHold(job,
                FrontierWorldStateSupport.settlement(state.bootstrap(), job.settlementId()), resources, accountId, claimId, false, 0L);
    }
}
