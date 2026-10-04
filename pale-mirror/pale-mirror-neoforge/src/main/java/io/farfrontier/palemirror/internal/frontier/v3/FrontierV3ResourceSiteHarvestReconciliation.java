package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.process.ResourceSiteHarvestSceneReconciliation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Read-only complete field/hand inspection; only the field owner may resume its exact safe checkpoint. */
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
            var job = lifecycle.harvestJob(cause.jobId()).orElse(null);
            if (job == null || !job.id().equals(cause.jobId())
                    || job.navigationBlock().isPresent()
                    || state.resourceSites().hasPendingWorldChange(job.siteId())) continue;
            var cycle = state.resourceSites().cycle(job.siteId());
            if (!cycle.pendingPlayerBreaks().isEmpty()
                    || !currentField(level, job, cycle)) continue;
            var member = lease.members().getFirst();
            var entity = level.getEntity(member.entityId());
            if (!(entity instanceof Mob worker) || !worker.isAlive()
                    || !FrontierV3SceneExecutor.owned(worker, state, lease, member)) continue;
            if (!FrontierV3ActorBodyController.inspectCurrent(level, runtime, worker)) continue;
            state = runtime.decodedState().orElseThrow();
            var observed = FrontierV3SupportedBodyCapture.observe(level, worker);
            if (observed.isEmpty()) continue;
            ItemStack hand = worker.getOffhandItem();
            var reviewed = FrontierV3ActorHandObservation.classify(job.workerId(), member.entityId(),
                    hand.is(Items.WHEAT) ? "minecraft:wheat" : "", hand.getCount(),
                    ItemStack.isSameItemSameComponents(hand, new ItemStack(Items.WHEAT, hand.getCount())));
            if (reviewed.disposition() != FrontierV3ActorHandObservation.Disposition.WHEAT) continue;
            boolean unbound = state.inventory().fungibleResources().bindings().values().stream()
                    .noneMatch(binding -> binding.accountId().equals(job.actorAccountId()));
            if (unbound && (job.progress().hasPendingCrop()
                    || FrontierV3ResourceSiteLedger.get(level).fieldDelivery(job.siteId()) != null
                    || FrontierV3ResourceSiteLedger.get(level).fieldHandProjection(job.siteId()) != null
                    || !FrontierV3ActorCarryProjection.witnessed(state, job.workerId(), worker))) continue;
            // Common body inspection has already committed current physical permission/pose.
            // This family receipt only resumes the inspected process and exact carried batch.
            var recoveryEpoch = recoveryEpoch(state, lease);
            if (recoveryEpoch.isEmpty()) continue;
            var receipt = new ResourceSiteHarvestSceneReconciled(job.siteId(), job.id(), lease.id(), lease.revision(),
                    recoveryEpoch.getAsLong(),
                    observed.orElseThrow(), reviewed.stack().orElseThrow());
            try { ResourceSiteHarvestSceneReconciliation.reduce(state, job.siteId(), receipt); }
            catch (IllegalArgumentException unresolved) { continue; }
            FrontierV3DiagnosticTrace.recordScene(level.getServer(), "resource_site_harvest_scene_reconciled", lease,
                    FrontierV3CommandSubmission.submit(runtime, "resource-site-harvest-scene-reconciled", lease.id().value(), receipt));
            return true;
        }
        return false;
    }

    static java.util.OptionalLong recoveryEpoch(FrontierWorldState state, SceneLease lease) {
        if (lease.status() != SceneLeaseStatus.CONFLICT || lease.members().size() != 1)
            return java.util.OptionalLong.empty();
        var recovery = state.fencedRecovery().current().get(
                ActorBodyId.recoveryBindingId(lease.members().getFirst().actorId()));
        return recovery != null && recovery.asset() == FencedRecoveryAsset.BODY
                && recovery.ownerId().equals(lease.members().getFirst().actorId())
                && recovery.ownerRevision() == 0L
                && recovery.phase() == FencedRecoveryPhase.RUNNING
                ? java.util.OptionalLong.of(recovery.authorityEpoch()) : java.util.OptionalLong.empty();
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
