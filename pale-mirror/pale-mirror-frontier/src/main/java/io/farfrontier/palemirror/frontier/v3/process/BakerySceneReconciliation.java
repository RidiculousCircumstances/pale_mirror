package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.LinkedHashMap;

/** Production-owned recovery of an unchanged bound batch at a safe owner checkpoint. */
public final class BakerySceneReconciliation {
    private BakerySceneReconciliation() { }
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
                || state.humanPopulation().meals().containsKey(job.workerId())
                || state.actorMovements().containsKey(job.workerId())
                || !ActorExecutionCoordinator.ambientAvailable(state, java.util.List.of(job.workerId()))
                || !hand.actorId().equals(job.workerId()) || !hand.entityId().equals(lease.members().getFirst().entityId())
                || !kind.equals(receipt.observedHand().itemKind()) || receipt.observedHand().quantity() != job.outputCount()
                || account == null || !account.custody().equals(new ResourceCustody.Actor(job.workerId()))
                || bindings.size() != 1 || !bindings.getFirst().address().equals(hand)
                || bindings.getFirst().authorityEpoch() != lease.revision()
                || !bindings.getFirst().lotQuantities().equals(account.lotQuantities())
                || !bindings.getFirst().claimQuantities().equals(account.claimQuantities()))
            throw new IllegalArgumentException("bakery recovery cannot replace or replay its cargo/effect");
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
        return state.withChanges(FrontierWorldStateUpdate.begin().sceneLeases(leases));
    }
}
