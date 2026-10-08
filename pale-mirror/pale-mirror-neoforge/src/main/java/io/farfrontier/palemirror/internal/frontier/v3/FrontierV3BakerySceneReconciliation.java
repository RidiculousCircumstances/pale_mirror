package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.process.BakerySceneReconciliation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

/** Fresh inspection of naturally loaded owned bodies; no spawning, inventory repair or force-load. */
final class FrontierV3BakerySceneReconciliation {
    private FrontierV3BakerySceneReconciliation() { }
    static void inspect(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                        FrontierWorldState state, SceneLease lease) {
        if (lease.members().size() != 1) return;
        var member = lease.members().getFirst();
        var entity = level.getEntity(member.entityId());
        if (entity == null) {
            inspectSavedStation(level, runtime, state, lease, member);
            return;
        }
        if (!(entity instanceof Mob body) || !body.isAlive() || !FrontierV3SceneExecutor.owned(body, state, lease, member)
                || !FrontierV3BakeryHandProjection.matchesCurrent(state, lease, member, body)) return;
        if (!FrontierV3ActorBodyController.inspectCurrent(level, runtime, body)) return;
        state = runtime.decodedState().orElseThrow();
        var observed = FrontierV3SupportedBodyCapture.observe(level, body);
        var fence = state.fencedRecovery().current().get(ActorBodyId.recoveryBindingId(member.actorId()));
        if (observed.isEmpty() || fence == null) return;
        var jobId = FrontierSceneBehaviors.productionWork(lease).jobId();
        var job = state.productionJobs().get(jobId);
        if (job == null) return;
        if (body.getMainHandItem().isEmpty()) {
            var phase = job.bakeryWork().orElseThrow().phase();
            if (phase != BakeryWorkState.Phase.PROCESSING && phase != BakeryWorkState.Phase.STATION_UNLOAD) return;
            var receipt = new BakeryStationSceneReconciled(jobId, lease.id(), lease.revision(),
                    fence.authorityEpoch(), body.getUUID(), observed.orElseThrow(), phase);
            try { BakerySceneReconciliation.reduce(state, job.settlementId(), receipt); }
            catch (IllegalArgumentException unresolved) {
                FrontierV3PhysicalWaitTrace.reconciliation(body, lease, unresolved.getMessage()); return;
            }
            FrontierV3DiagnosticTrace.recordScene(level.getServer(), "bakery_station_scene_reconciled", lease,
                    FrontierV3CommandSubmission.submit(runtime, "bakery-station-scene-reconciled", lease.id().value(), receipt));
            return;
        }
        var hand = body.getMainHandItem();
        var accountId = job.bakeryWork().orElseThrow().actorAccountId();
        boolean unbound = state.inventory().fungibleResources().bindings().values().stream()
                .noneMatch(binding -> binding.accountId().equals(accountId));
        if (unbound && !FrontierV3ActorCarryProjection.witnessed(state, member.actorId(), body)) return;
        var receipt = new BakerySceneReconciled(jobId, lease.id(), lease.revision(), fence.authorityEpoch(), observed.orElseThrow(),
                new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ActorHand(member.actorId(), member.entityId(),
                        ActorContainerItemOrder.Hand.MAIN), net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(hand.getItem()).toString(), hand.getCount()));
        try { BakerySceneReconciliation.reduce(state, job.settlementId(), receipt); }
        catch (IllegalArgumentException unresolved) {
            FrontierV3PhysicalWaitTrace.reconciliation(body, lease, unresolved.getMessage()); return;
        }
        FrontierV3DiagnosticTrace.recordScene(level.getServer(), "bakery_scene_reconciled", lease,
                FrontierV3CommandSubmission.submit(runtime, "bakery-scene-reconciled", lease.id().value(), receipt));
    }

    /** The common body owner supplies the same unload/write/sync/read proof used by ordinary release. */
    private static void inspectSavedStation(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                            FrontierWorldState state, SceneLease lease, SceneMember member) {
        var jobId = FrontierSceneBehaviors.productionWork(lease).jobId();
        var job = state.productionJobs().get(jobId);
        if (job == null || job.bakeryWork().isEmpty()) return;
        var phase = job.bakeryWork().orElseThrow().phase();
        if (phase != BakeryWorkState.Phase.PROCESSING && phase != BakeryWorkState.Phase.STATION_UNLOAD) return;
        var ledger = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
        var departure = FrontierV3SceneDepartureObserver.validDeparture(state, lease, member, ledger).orElse(null);
        // OFF may contain body-owned personal inventory; only bakery MAIN is empty
        // in these station phases. The common owner retains the full saved carry proof.
        if (departure == null || departure.mainhand().isPresent()
                || !FrontierV3ActorBodyController.checkpointSavedDeparture(level, runtime, member.actorId())) return;
        state = runtime.decodedState().orElseThrow();
        var fence = state.fencedRecovery().current().get(ActorBodyId.recoveryBindingId(member.actorId()));
        if (fence == null) return;
        var receipt = new BakeryStationSceneReconciled(jobId, lease.id(), lease.revision(), fence.authorityEpoch(),
                member.entityId(), departure.observed().body(), phase,
                io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyInspected.Source.SAVED_DEPARTURE);
        try { BakerySceneReconciliation.reduce(state, job.settlementId(), receipt); }
        catch (IllegalArgumentException unresolved) { return; }
        FrontierV3DiagnosticTrace.recordScene(level.getServer(), "bakery_station_scene_reconciled_saved", lease,
                FrontierV3CommandSubmission.submit(runtime, "bakery-station-scene-reconciled-saved", lease.id().value(), receipt));
    }
}
