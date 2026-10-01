package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.process.ResourceSiteHarvestSceneReconciliation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Read-only inspection; only the field owner can resume its exact retained completed batch. */
final class FrontierV3ResourceSiteHarvestReconciliation {
    private FrontierV3ResourceSiteHarvestReconciliation() { }

    static boolean reconcileOne(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                FrontierWorldState state) {
        for (SceneLease lease : state.sceneLeases().values()) {
            if (lease.status() != SceneLeaseStatus.CONFLICT || !FrontierSceneBehaviors.isResourceSiteHarvest(lease)
                    || lease.members().size() != 1) continue;
            var cause = FrontierSceneBehaviors.resourceSiteHarvest(lease);
            var lifecycle = state.resourceSites().sites().get(cause.siteId());
            if (lifecycle == null) continue;
            var job = lifecycle.activeWork().filter(ResourceSiteHarvestJob.class::isInstance)
                    .map(ResourceSiteHarvestJob.class::cast).orElse(null);
            if (job == null || !job.id().equals(cause.jobId()) || !job.progress().complete()
                    || job.progress().hasPendingCrop() || job.navigationBlock().isPresent()
                    || state.resourceSites().hasPendingWorldChange(job.siteId())) continue;
            var cycle = state.resourceSites().cycle(job.siteId());
            if (!cycle.cycleAccounted() || !cycle.pendingPlayerBreaks().isEmpty()
                    || !currentField(level, job, cycle)) continue;
            var member = lease.members().getFirst();
            var entity = level.getEntity(member.entityId());
            if (!(entity instanceof Mob worker) || !worker.isAlive()
                    || !FrontierV3SceneExecutor.owned(worker, state, lease, member)) continue;
            var observed = FrontierV3SupportedBodyCapture.observe(level, worker);
            if (observed.isEmpty()) continue;
            ItemStack hand = worker.getOffhandItem();
            var reviewed = FrontierV3ActorHandObservation.classify(job.workerId(), member.entityId(),
                    hand.is(Items.WHEAT) ? "minecraft:wheat" : "", hand.getCount(),
                    ItemStack.isSameItemSameComponents(hand, new ItemStack(Items.WHEAT, hand.getCount())));
            if (reviewed.disposition() != FrontierV3ActorHandObservation.Disposition.WHEAT) continue;
            var receipt = new ResourceSiteHarvestSceneReconciled(job.siteId(), job.id(), lease.id(), lease.revision(),
                    worker.getPersistentData().getLong(FrontierV3AmbientActorExecutor.CUSTODY_EPOCH_KEY),
                    observed.orElseThrow(), reviewed.stack().orElseThrow());
            try { ResourceSiteHarvestSceneReconciliation.reduce(state, job.siteId(), receipt); }
            catch (IllegalArgumentException unresolved) { continue; }
            FrontierV3CommandSubmission.submit(runtime, "resource-site-harvest-scene-reconciled", lease.id().value(), receipt);
            return true;
        }
        return false;
    }

    private static boolean currentField(ServerLevel level, ResourceSiteHarvestJob job, ResourceFieldCycle cycle) {
        var claim = FrontierV3ResourceSiteLedger.get(level).fieldClaim(job.siteId());
        if (!(claim instanceof FrontierV3ResourceSiteLedger.FieldOwnership owner)
                || owner.status() != FrontierV3ResourceSiteLedger.Status.ACTIVE
                || !owner.witness().matchesCycle(cycle)) return false;
        for (var cell : cycle.layout().cells()) {
            var retained = owner.witness().cell(cell.id());
            if (retained.pending().isPresent() || retained.foreign().isPresent()
                    || !retained.committed().equals(ResourceFieldPhysicalSurface.Condition.of(cycle.cell(cell.id())))
                    || FrontierV3ResourceFieldObservation.observe(level, cycle, owner.witness(), cell.id(),
                        "harvest-scene-reconciliation").disposition() != FrontierV3ResourceFieldObservation.Disposition.CURRENT)
                return false;
        }
        return true;
    }
}
