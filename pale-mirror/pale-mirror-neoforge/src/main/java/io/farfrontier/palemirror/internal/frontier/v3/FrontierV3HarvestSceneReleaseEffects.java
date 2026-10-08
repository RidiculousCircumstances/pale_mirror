package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.process.ResourceSiteHarvestProcess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import java.util.Set;

/** Harvest owns its carried crop and exact job account; the body lifecycle owns only physical facts. */
final class FrontierV3HarvestSceneReleaseEffects implements FrontierV3SceneReleaseEffects {
    public SceneCauseKind family() { return SceneCauseKind.RESOURCE_SITE_HARVEST; }
    public boolean matchesBody(FrontierWorldState state, SceneLease lease, SceneMember member, Mob body) { return true; }

    public Decision prepare(ServerLevel level, FrontierWorldState state, SceneLease lease,
                            SceneLeaseReleased exit, Set<SubjectId> departed) {
        if (!FrontierSceneLeaseStateSupport.hasBoundSceneHand(state, lease)) return new Ready(exit);
        if (lease.members().size() != 1) return new Conflict("release-bound-hand-without-typed-owner");
        var job = FrontierResourceSiteHarvestSceneSupport.require(state, FrontierSceneBehaviors.resourceSiteHarvest(lease));
        var hand = FrontierV3SceneReleaseHandEvidence.read(level, state, lease, lease.members().getFirst(),
                departed, ActorContainerItemOrder.Hand.OFF).orElse(null);
        if (hand == null || !hand.itemKind().equals("minecraft:wheat"))
            return new Conflict("bound-hand-release-physical-foreign");
        var stack = new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ActorHand(job.workerId(),
                lease.members().getFirst().entityId()), hand.itemKind(), hand.quantity());
        final PhysicalStackBinding binding;
        try { binding = ActorCarriedResources.requireBinding(state.inventory().fungibleResources(),
                job.workerId(), job.actorAccountId(), stack); }
        catch (IllegalArgumentException invalid) { return new Conflict("bound-hand-release-preflight:" + invalid.getMessage()); }
        var payload = new ResourceSiteHarvestHandRelease(job.siteId(), job.id(), job.actorAccountId(), binding.authorityEpoch(),
                new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ActorHand(job.workerId(),
                        lease.members().getFirst().entityId()), hand.itemKind(), hand.quantity()), exit);
        try { ResourceSiteHarvestProcess.reduceHandRelease(state, state.resourceSite(job.siteId()).settlementId(), payload); }
        catch (IllegalArgumentException invalid) { return new Conflict("bound-hand-release-preflight:" + invalid.getMessage()); }
        return new Ready(payload);
    }
}
