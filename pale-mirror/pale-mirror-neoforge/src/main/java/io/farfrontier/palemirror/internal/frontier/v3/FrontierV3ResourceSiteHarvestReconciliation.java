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
            if (FrontierV3HarvestSceneStandingAdmission.abortObstructedBodyFreePreparation(level, runtime, state, lease)) return true;
            var cause = FrontierSceneBehaviors.resourceSiteHarvest(lease);
            var lifecycle = state.resourceSites().sites().get(cause.siteId());
            if (lifecycle == null) continue;
            var job = lifecycle.harvestJob(cause.jobId()).orElse(null);
            if (job == null || !job.id().equals(cause.jobId())
                    || job.navigationBlock().isPresent()
                    || state.resourceSites().harvestMutationPending(job)) continue;
            var cycle = state.resourceSites().cycle(job.siteId());
            if (!cycle.pendingPlayerBreaks().isEmpty()
                    || !currentField(level, lease, job, cycle)) continue;
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
            boolean firstApplied = job.progress().hasPendingCrop() && ResourceSiteHarvestCargo.quantity(state, job) == 0
                    && ownedPending(FrontierV3ResourceSiteLedger.get(level), lease, job, cycle).isPresent();
            if (unbound && !firstApplied && (job.progress().hasPendingCrop()
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
            if (reviewed.stack().orElseThrow().quantity() == ResourceSiteHarvestCargo.quantity(state, job) + 1) {
                var pending = ownedPending(FrontierV3ResourceSiteLedger.get(level), lease, job, cycle);
                if (pending.isEmpty() || !pending.orElseThrow().handConfirmed()
                        || pending.orElseThrow().completedSteps() != pending.orElseThrow().transition().steps().size()) continue;
                var due = FrontierV3TraversalScheduleGate.dueBinding(runtime.executionView().orElseThrow(), job);
                if (due.isEmpty()) continue;
                var binding = due.orElseThrow();
                var cell = cycle.layout().cells().get(job.progress().pendingCropSlotIndex()).id();
                var applied = new ResourceSiteHarvestProgressed(job.siteId(), cycle.epoch(), job.id(),
                        job.progress().completedCropSlots() + 1, cycle.layout().revision(), cell, job.target().generation(),
                        ResourceFieldCycle.WorkOutcome.HARVESTED, binding.id(), binding.dueAt().ticks(),
                        java.util.Optional.of(new ResourceSiteHarvestProgressed.HandObservation(
                                (PhysicalStackAddress.ActorHand) receipt.observedHand().address(),
                                pending.orElseThrow().handEffect().orElseThrow().authorityEpoch(), receipt.observedHand().quantity())));
                var recovered = new ResourceSiteHarvestEffectReconciled(receipt, applied);
                try { ResourceSiteHarvestSceneReconciliation.reduceApplied(state, job.siteId(), recovered); }
                catch (IllegalArgumentException unresolved) {
                    FrontierV3PhysicalWaitTrace.reconciliation(worker, lease, unresolved.getMessage()); continue;
                }
                FrontierV3DiagnosticTrace.recordScene(level.getServer(), "resource_site_harvest_effect_reconciled", lease,
                        FrontierV3CommandSubmission.submitBound(runtime, "resource-site-harvest-effect-reconciled",
                                lease.id().value(), recovered, binding));
                return true;
            }
            try { ResourceSiteHarvestSceneReconciliation.reduce(state, job.siteId(), receipt); }
            catch (IllegalArgumentException unresolved) {
                FrontierV3PhysicalWaitTrace.reconciliation(worker, lease, unresolved.getMessage()); continue;
            }
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

    static java.util.Optional<FrontierV3ResourceFieldWitness.Pending> ownedPending(
            FrontierV3ResourceSiteLedger ledger, SceneLease lease, ResourceSiteHarvestJob job, ResourceFieldCycle cycle) {
        if (!job.progress().hasPendingCrop() || lease.members().size() != 1
                || !(ledger.fieldClaim(job.siteId()) instanceof FrontierV3ResourceSiteLedger.FieldOwnership owner)
                || owner.status() != FrontierV3ResourceSiteLedger.Status.ACTIVE || !owner.witness().matchesCycle(cycle))
            return java.util.Optional.empty();
        var cell = cycle.layout().cells().get(job.progress().pendingCropSlotIndex()).id();
        return owner.witness().cell(cell).pending().filter(pending -> pending.canonicalSource().isEmpty()
                && pending.causationId().equals(FrontierV3ResourceFieldWorkExecutor.cause(job, cycle, cell))
                && cycle.physicalWorkTransition(cell).filter(pending.transition()::equals).isPresent()
                && pending.handEffect().filter(hand -> hand.siteId().equals(job.siteId()) && hand.jobId().equals(job.id())
                    && hand.actorId().equals(job.workerId()) && hand.entityId().equals(lease.members().getFirst().entityId())
                    && hand.beforeCount() == job.undeliveredYieldQuantity()).isPresent());
    }

    private static boolean currentField(ServerLevel level, SceneLease lease, ResourceSiteHarvestJob job, ResourceFieldCycle cycle) {
        var ledger = FrontierV3ResourceSiteLedger.get(level);
        var claim = ledger.fieldClaim(job.siteId());
        if (!(claim instanceof FrontierV3ResourceSiteLedger.FieldOwnership owner)
                || owner.status() != FrontierV3ResourceSiteLedger.Status.ACTIVE
                || !owner.witness().matchesCycle(cycle)) return false;
        for (var cell : cycle.layout().cells()) {
            var retained = owner.witness().cell(cell.id());
            if (job.progress().hasPendingCrop()
                    && cycle.layout().cells().get(job.progress().pendingCropSlotIndex()).id().equals(cell.id())
                    && ownedPending(ledger, lease, job, cycle).isPresent()) {
                var review = FrontierV3ResourceFieldObservation.observe(level, cycle, owner.witness(), cell.id(),
                        "harvest-effect-reconciliation");
                if (retained.foreign().isPresent() || review.disposition() != FrontierV3ResourceFieldObservation.Disposition.CURRENT)
                    return false;
                continue; // This exact pending effect owns its physical prefix, not the old canonical mature crop.
            }
            if (retained.pending().isPresent() || retained.foreign().isPresent()
                    || !retained.committed().equals(ResourceFieldPhysicalSurface.Condition.of(cycle.cell(cell.id())))
                    || FrontierV3ResourceFieldObservation.observe(level, cycle, owner.witness(), cell.id(),
                        "harvest-scene-reconciliation").disposition() != FrontierV3ResourceFieldObservation.Disposition.CURRENT)
                return false;
        }
        return true;
    }
}
