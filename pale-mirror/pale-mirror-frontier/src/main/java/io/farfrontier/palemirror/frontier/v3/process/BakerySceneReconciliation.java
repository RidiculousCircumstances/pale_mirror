package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.LinkedHashMap;

/** Production-owned recovery of an unchanged bound batch at a safe owner checkpoint. */
public final class BakerySceneReconciliation {
    private BakerySceneReconciliation() { }

    /** Reopens only the ordinary release boundary, never production or a physical recipe. */
    public static FrontierWorldState reduce(FrontierWorldState state, SubjectId subject, BakeryStationSceneReconciled receipt) {
        var job = state.productionJobs().get(receipt.jobId());
        var lease = state.sceneLeases().get(receipt.leaseId());
        if (job == null || !subject.equals(job.settlementId()) || job.bakeryWork().isEmpty()
                || job.inputHold() instanceof ProductionInputHold.Materialized
                || lease == null || lease.status() != SceneLeaseStatus.CONFLICT
                || lease.revision() != receipt.leaseRevision() || lease.members().size() != 1
                || !FrontierSceneBehaviors.isProductionWork(lease)
                || !FrontierSceneBehaviors.productionWork(lease).jobId().equals(job.id())
                || !lease.members().getFirst().actorId().equals(job.workerId())
                || !lease.members().getFirst().entityId().equals(receipt.entityId()))
            throw new IllegalArgumentException("station recovery lacks its exact conflicted owner/body");
        var work = job.bakeryWork().orElseThrow();
        var actor = state.actorLocations().get(job.workerId());
        var ledger = state.inventory().fungibleResources();
        var station = state.inventory().containers().values().stream()
                .flatMap(container -> container.productionStation().stream())
                .filter(candidate -> candidate.id().equals(work.stationId())).findFirst().orElseThrow(
                        () -> new IllegalArgumentException("station recovery lost its production station"));
        var batch = ledger.accounts().get(work.stationAccountId());
        String kind = work.phase() == BakeryWorkState.Phase.PROCESSING ? "minecraft:wheat" : "minecraft:bread";
        if (work.phase() != receipt.phase() || work.pendingPhysicalStep().isPresent()
                || actor == null || actor.condition().status() != ActorLifeStatus.ALIVE
                || !ActorExecutionCoordinator.ambientAvailable(state, java.util.List.of(job.workerId()))
                || state.actorMovements().containsKey(job.workerId())
                || ledger.accounts().containsKey(work.actorAccountId())
                || FrontierSceneLeaseStateSupport.hasBoundSceneHand(state, lease)
                || batch == null || !batch.custody().equals(new ResourceCustody.Container(station.containerId()))
                || batch.lotQuantities().values().stream().mapToInt(Integer::intValue).sum() != job.outputCount()
                || batch.lotQuantities().keySet().stream().anyMatch(id -> !ledger.lots().get(id).itemKind().equals(kind)))
            throw new IllegalArgumentException("station recovery cannot abandon cargo or a pending physical effect");
        var fence = state.fencedRecovery().current().get(ActorBodyId.recoveryBindingId(job.workerId()));
        if (fence == null || fence.asset() != FencedRecoveryAsset.BODY || !fence.ownerId().equals(job.workerId())
                || fence.ownerRevision() != 0L || fence.authorityEpoch() != receipt.recoveryEpoch()
                || fence.phase() != FencedRecoveryPhase.RUNNING
                    && !(receipt.source() == io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyInspected.Source.SAVED_DEPARTURE
                        && fence.phase() == FencedRecoveryPhase.AMBIGUOUS)
                || !actor.body().equals(receipt.observedBody())
                || !state.bootstrap().bounds().contains(receipt.observedBody().supportingSurface().support()))
            throw new IllegalArgumentException("station recovery has a stale body fence");
        var leases = new LinkedHashMap<>(state.sceneLeases());
        leases.put(lease.id(), lease.withStatus(SceneLeaseStatus.DRAINING));
        return state.withChanges(FrontierWorldStateUpdate.begin().sceneLeases(leases));
    }
    public static FrontierWorldState reduce(FrontierWorldState state, SubjectId subject, BakerySceneReconciled receipt) {
        var job = state.productionJobs().get(receipt.jobId());
        var lease = state.sceneLeases().get(receipt.leaseId());
        if (job == null || !subject.equals(job.settlementId()) || job.bakeryWork().isEmpty()
                || job.inputHold() instanceof ProductionInputHold.Materialized
                || lease == null || lease.status() != SceneLeaseStatus.CONFLICT
                || lease.revision() != receipt.leaseRevision() || lease.members().size() != 1
                || !FrontierSceneBehaviors.isProductionWork(lease)
                || !FrontierSceneBehaviors.productionWork(lease).jobId().equals(job.id())
                || !lease.members().getFirst().actorId().equals(job.workerId()))
            throw new IllegalArgumentException("bakery recovery lacks its exact conflicted owner");
        var work = job.bakeryWork().orElseThrow();
        var hand = (PhysicalStackAddress.ActorHand) receipt.observedHand().address();
        var actor = state.actorLocations().get(job.workerId());
        var account = state.inventory().fungibleResources().accounts().get(work.actorAccountId());
        var bindings = state.inventory().fungibleResources().bindings().values().stream()
                .filter(value -> value.accountId().equals(work.actorAccountId())).toList();
        String kind = work.phase() == BakeryWorkState.Phase.STATION_LOAD ? "minecraft:wheat" : "minecraft:bread";
        if (work.pendingPhysicalStep().isPresent()
                || work.phase() != BakeryWorkState.Phase.STATION_LOAD && work.phase() != BakeryWorkState.Phase.DEPOT_DELIVERY
                || actor == null || actor.condition().status() != ActorLifeStatus.ALIVE
                || state.actorMovements().containsKey(job.workerId())
                || !ActorExecutionCoordinator.ambientAvailable(state, java.util.List.of(job.workerId()))
                || !hand.actorId().equals(job.workerId()) || !hand.entityId().equals(lease.members().getFirst().entityId())
                || !kind.equals(receipt.observedHand().itemKind()) || receipt.observedHand().quantity() != job.outputCount()
                || account == null || !account.custody().equals(new ResourceCustody.Actor(job.workerId()))
                || bindings.size() > 1
                || !bindings.isEmpty() && (!bindings.getFirst().address().equals(hand)
                    || !bindings.getFirst().lotQuantities().equals(account.lotQuantities())
                    || !bindings.getFirst().claimQuantities().equals(account.claimQuantities())))
            throw new IllegalArgumentException("bakery recovery cannot replace or replay its cargo/effect");
        if (!bindings.isEmpty()) ActorCarriedResources.requireBinding(state.inventory().fungibleResources(), job.workerId(),
                work.actorAccountId(), receipt.observedHand());
        // A retained meal is waiting for this owner to yield, not an admitted
        // ambient actuator. Restore the exact work scope so its ordinary
        // safe-point release can run; retaining the meal must not prevent
        // the very recovery that makes self-care possible.
        var recoveryId = ActorBodyId.recoveryBindingId(job.workerId());
        var fence = state.fencedRecovery().current().get(recoveryId);
        if (fence == null || fence.asset() != FencedRecoveryAsset.BODY
                || !fence.ownerId().equals(job.workerId()) || fence.ownerRevision() != 0L
                || fence.authorityEpoch() != receipt.recoveryEpoch() || fence.phase() != FencedRecoveryPhase.RUNNING
                || !actor.body().equals(receipt.observedBody()))
            throw new IllegalArgumentException("bakery recovery has a stale body fence");
        if (!state.bootstrap().bounds().contains(receipt.observedBody().supportingSurface().support()))
            throw new IllegalArgumentException("bakery recovery body is outside world");
        var leases = new LinkedHashMap<>(state.sceneLeases());
        leases.put(lease.id(), lease.withStatus(SceneLeaseStatus.HOT));
        var restored = state.withChanges(FrontierWorldStateUpdate.begin().sceneLeases(leases));
        // A body can be admitted before its positive cargo projection is bound.
        // Reuse the ordinary issuer atomically; never replace an existing binding
        // or grant work permission while leaving the carried stock unaccounted.
        return bindings.isEmpty() ? BakeryProcess.materializeHotHand(restored, subject,
                new BakeryHotHandMaterialized(job.id(), lease.id(), work.actorAccountId(),
                        Math.max(1L, lease.revision()), receipt.observedHand())) : restored;
    }
}
