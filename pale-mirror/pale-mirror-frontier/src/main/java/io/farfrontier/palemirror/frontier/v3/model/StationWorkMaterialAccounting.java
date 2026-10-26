package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** The station-work owner retains all material promises while one worker carries one leg at a time. */
public final class StationWorkMaterialAccounting {
    public static Map<String, Integer> plannedKinds(ProductionJob job) {
        var work = job.stationWork().orElseThrow(); var quantities = new TreeMap<String, Integer>();
        work.materials().loaded().forEach(input -> quantities.merge(input.itemKind(), input.quantity(), Math::addExact));
        work.materials().queued().forEach(input -> quantities.merge(input.itemKind(), input.quantity(), Math::addExact));
        if (work.phase() != StationWorkState.Phase.PROCESSING && !StationWorkCargo.output(job))
            quantities.merge(work.inputItemKind(), job.inputCount(), Math::addExact);
        else if (work.materials().loaded().isEmpty()) quantities.put(work.inputItemKind(), job.inputCount());
        return Map.copyOf(quantities);
    }
    public static int plannedUnits(ProductionJob job) { return plannedKinds(job).values().stream().mapToInt(Integer::intValue).sum(); }
    public static Set<SubjectId> claims(ProductionJob job) {
        var ids = new HashSet<SubjectId>();
        var work = job.stationWork().orElseThrow();
        work.materials().queued().forEach(input -> ids.add(input.claimId()));
        work.materials().loaded().forEach(input -> ids.add(input.claimId()));
        if (job.inputHold() instanceof ProductionInputHold.FungibleCold hold) ids.add(hold.claimId());
        else if (job.inputHold() instanceof ProductionInputHold.FungibleBound hold) ids.add(hold.claimId());
        return Set.copyOf(ids);
    }
    static FungibleResourceLedger reserveQueued(FrontierWorldState state, ProductionJob job, FungibleResourceLedger ledger) {
        var work = job.stationWork().orElseThrow();
        for (var input : work.materials().queued()) {
            if (input.withdrawn() || !ProductionInputAccess.eligible(state, input.sourceContainer(), job.settlementId(), job.facilityId(), work.recipeId())
                    || !FungibleResourceCustodySupport.canReserve(state, input.selection()))
                throw new IllegalArgumentException("new order lacks a complete admissible material bundle");
            var account = ledger.accounts().get(input.sourceAccount());
            if (!(account.custody() instanceof ResourceCustody.Container holder) || !holder.containerId().equals(input.sourceContainer()))
                throw new IllegalArgumentException("queued material changed its declared source container");
            var claim = new ClaimAllocation(input.claimId(), job.id(), job.rights().resourceOwner().id(), input.itemKind(),
                    input.quantity(), input.lots(), ClaimPurpose.PRODUCTION_WORK);
            if (ReferenceContainerCustody.hasLiveCustody(state, holder.containerId())) {
                long epoch = state.replicaCustody().custodyByScope().get(ReferenceContainerCustody.scopeId(holder.containerId())).authorityEpoch();
                ledger = ledger.reserveBound(claim, input.sourceAccount(), epoch);
            } else ledger = ledger.reserve(claim, input.sourceAccount());
        }
        return ledger;
    }
    static void validate(ExactInventory inventory, ProductionJob job) {
        var work = job.stationWork().orElseThrow(); var ledger = inventory.fungibleResources();
        boolean consumed = StationWorkCargo.output(job);
        for (var input : work.materials().queued()) {
            if (consumed) throw new IllegalArgumentException("produced output retains unfinished material trips");
            if (input.withdrawn()) {
                if (ledger.claims().containsKey(input.claimId())) throw new IllegalArgumentException("withdrawn material retains its claim");
                continue;
            }
            require(ledger, input.sourceAccount(), input.claimId(), input.lots(), job.id(), ClaimPurpose.PRODUCTION_WORK);
            var holder = ledger.accounts().get(input.sourceAccount()).custody();
            if (!(holder instanceof ResourceCustody.Container container) || !container.containerId().equals(input.sourceContainer()))
                throw new IllegalArgumentException("queued station input has a foreign source");
        }
        for (var input : work.materials().loaded()) {
            if (consumed) {
                if (ledger.claims().containsKey(input.claimId())) throw new IllegalArgumentException("consumed station input retains its claim");
            } else require(ledger, work.stationAccountId(), input.claimId(), input.lots(),
                    work.phase() == StationWorkState.Phase.PROCESSING ? StationWorkOperation.operationId(job).value() : job.id(),
                    work.phase() == StationWorkState.Phase.PROCESSING ? ClaimPurpose.STATION_PROCESSING : ClaimPurpose.PRODUCTION_WORK);
        }
    }
    private static void require(FungibleResourceLedger ledger, SubjectId accountId, SubjectId claimId,
            Map<SubjectId, Integer> lots, SubjectId owner, ClaimPurpose purpose) {
        var account = ledger.accounts().get(accountId); var claim = ledger.claims().get(claimId);
        if (account == null || claim == null || !claim.claimantId().equals(owner) || claim.purpose() != purpose
                || !claim.lotQuantities().equals(lots) || account.claimQuantities().getOrDefault(claimId, 0) != claim.quantity()
                || lots.entrySet().stream().anyMatch(entry -> account.lotQuantities().getOrDefault(entry.getKey(), 0) < entry.getValue()))
            throw new IllegalArgumentException("station work lost a retained material promise");
    }
    private StationWorkMaterialAccounting() { }
}
