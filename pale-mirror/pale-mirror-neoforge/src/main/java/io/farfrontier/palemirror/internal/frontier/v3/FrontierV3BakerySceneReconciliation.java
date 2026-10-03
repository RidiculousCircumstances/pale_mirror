package io.farfrontier.palemirror.internal.frontier.v3;

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
        if (!(entity instanceof Mob body) || !body.isAlive() || !FrontierV3SceneExecutor.owned(body, state, lease, member)
                || !FrontierV3BakeryHandProjection.matchesCurrent(state, lease, member, body)) return;
        var observed = FrontierV3SupportedBodyCapture.observe(level, body);
        var fence = state.fencedRecovery().current().get(FrontierSceneLeaseStateSupport.bodyRecoveryBindingId(member.actorId()));
        if (observed.isEmpty() || fence == null || body.getMainHandItem().isEmpty()) return;
        var jobId = FrontierSceneBehaviors.productionWork(lease).jobId();
        var job = state.productionJobs().get(jobId);
        if (job == null) return;
        var hand = body.getMainHandItem();
        var receipt = new BakerySceneReconciled(jobId, lease.id(), lease.revision(), fence.authorityEpoch(), observed.orElseThrow(),
                new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ActorHand(member.actorId(), member.entityId(),
                        ActorContainerItemOrder.Hand.MAIN), net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(hand.getItem()).toString(), hand.getCount()));
        try { BakerySceneReconciliation.reduce(state, job.settlementId(), receipt); }
        catch (IllegalArgumentException unresolved) { return; }
        FrontierV3DiagnosticTrace.recordScene(level.getServer(), "bakery_scene_reconciled", lease,
                FrontierV3CommandSubmission.submit(runtime, "bakery-scene-reconciled", lease.id().value(), receipt));
    }
}
