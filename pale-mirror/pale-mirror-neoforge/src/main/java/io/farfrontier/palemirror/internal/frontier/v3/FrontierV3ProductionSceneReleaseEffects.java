package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.process.ProductionProcess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import java.util.Set;

/** Production owns hand identity, binding epoch and its atomic resource/scope release. */
final class FrontierV3ProductionSceneReleaseEffects implements FrontierV3SceneReleaseEffects {
    public SceneCauseKind family() { return SceneCauseKind.PRODUCTION_WORK; }
    public boolean matchesBody(FrontierWorldState state, SceneLease lease, SceneMember member, Mob body) {
        return FrontierV3BakeryHandProjection.matchesCurrent(state, lease, member, body);
    }

    public Decision prepare(ServerLevel level, FrontierWorldState state, SceneLease lease,
                            SceneLeaseReleased exit, Set<SubjectId> departed) {
        if (!FrontierSceneLeaseStateSupport.hasBoundSceneHand(state, lease)) return new Ready(exit);
        var job = state.productionJobs().get(FrontierSceneBehaviors.productionWork(lease).jobId());
        if (job == null || job.bakeryWork().isEmpty() || lease.members().size() != 1)
            return new Conflict("release-bakery-hand-without-owner");
        var work = job.bakeryWork().orElseThrow();
        var hand = FrontierV3SceneReleaseHandEvidence.read(level, state, lease, lease.members().getFirst(),
                departed, ActorContainerItemOrder.Hand.MAIN).orElse(null);
        if (hand == null) return new Conflict("release-bakery-hand-binding-unavailable");
        var bindings = state.inventory().fungibleResources().bindings().values().stream()
                .filter(value -> value.accountId().equals(work.actorAccountId())).toList();
        if (bindings.size() != 1) return new Conflict("release-bakery-hand-binding-unavailable");
        var payload = new BakeryHotHandRelease(job.id(), work.actorAccountId(), bindings.getFirst().authorityEpoch(),
                new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ActorHand(job.workerId(),
                        lease.members().getFirst().entityId(), ActorContainerItemOrder.Hand.MAIN), hand.itemKind(), hand.quantity()), exit);
        try { ProductionProcess.reduceBakeryHotHandRelease(state, job.settlementId(), payload); }
        catch (IllegalArgumentException invalid) { return new Conflict("release-bakery-hand-preflight:" + invalid.getMessage()); }
        return new Ready(payload);
    }
}
