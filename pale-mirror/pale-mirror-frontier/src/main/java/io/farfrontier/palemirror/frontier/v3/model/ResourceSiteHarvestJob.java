package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;
import java.util.Optional;

/** One named farmer owns CellId outcomes and exact hand/depot custody, never a physical path. */
public record ResourceSiteHarvestJob(SubjectId id, SubjectId taskId, SubjectId siteId, SubjectId workerId,
                                     SubjectId actorAccountId, SubjectId depotAccountId, SubjectId outputItemId,
                                     InventoryCustody.ContainerSlot outputSlot, PhysicalIntentId intentId,
                                     ResourceSiteHarvestProgress progress, int deliveredYieldQuantity,
                                     boolean returningForBatch,
                                     Optional<InventoryCustody.ContainerSlot> batchSuccessorSlot,
                                     Optional<ResourceSiteHarvestBatchDelivered> lastConfirmedBatch,
                                     Optional<ResourceSiteHarvestNavigationBlock> navigationBlock,
                                     int harvestedYieldQuantity, ResourceFieldWorkTarget target) implements ResourceSiteWork {
    public ResourceSiteHarvestJob {
        Objects.requireNonNull(id, "field job"); Objects.requireNonNull(taskId, "field task");
        Objects.requireNonNull(siteId, "field site"); Objects.requireNonNull(workerId, "field worker");
        Objects.requireNonNull(actorAccountId, "field actor account"); Objects.requireNonNull(depotAccountId, "field depot account");
        Objects.requireNonNull(outputItemId, "field output item identity"); Objects.requireNonNull(outputSlot, "field output slot");
        Objects.requireNonNull(intentId, "field physical intent"); Objects.requireNonNull(progress, "field progress");
        batchSuccessorSlot = Objects.requireNonNull(batchSuccessorSlot, "field batch successor reservation");
        lastConfirmedBatch = Objects.requireNonNull(lastConfirmedBatch, "last confirmed field batch");
        navigationBlock = Objects.requireNonNull(navigationBlock, "field navigation block");
        Objects.requireNonNull(target, "exact field work target");
        if (!target.siteId().equals(siteId)) throw new IllegalArgumentException("field target belongs to another site");
        if (!id.value().startsWith("job:site-harvest-") || !taskId.value().startsWith("task:")
                || !siteId.value().startsWith("site:") || !workerId.value().startsWith("resident:")
                || !actorAccountId.value().startsWith("custody:field-actor-") || !depotAccountId.value().startsWith("custody:")
                || !outputItemId.value().startsWith("item:site-harvest-") || !intentId.value().startsWith("intent:site-harvest-"))
            throw new IllegalArgumentException("field job identities must use their declared namespaces");
        if (actorAccountId.equals(depotAccountId)
                || !depotAccountId.equals(ReferenceContainerCustody.scopeId(outputSlot.containerId()))
                || harvestedYieldQuantity < 0 || harvestedYieldQuantity > progress.completedCropSlots()
                || deliveredYieldQuantity < 0 || deliveredYieldQuantity > harvestedYieldQuantity
                || harvestedYieldQuantity - deliveredYieldQuantity > 64
                || deliveredYieldQuantity % 64 != 0
                || returningForBatch && (progress.complete() || progress.hasPendingCrop())
                || batchSuccessorSlot.isPresent() && (!returningForBatch
                    || !batchSuccessorSlot.orElseThrow().containerId().equals(outputSlot.containerId())
                    || batchSuccessorSlot.orElseThrow().equals(outputSlot))
                || lastConfirmedBatch.isPresent() && !matchesLastBatch(lastConfirmedBatch.orElseThrow(), id, intentId,
                    siteId, workerId, actorAccountId, depotAccountId, deliveredYieldQuantity)
                || navigationBlock.isPresent()
                    && navigationBlock.orElseThrow().reason() == ResourceSiteHarvestNavigationBlock.Reason.CONTINUATION_UNAVAILABLE
                        && (progress.hasPendingCrop() || progress.complete() || returningForBatch))
            throw new IllegalArgumentException("field job has invalid progress, custody or semantic goal state");
    }

    public ResourceSiteHarvestJob(SubjectId id, SubjectId taskId, SubjectId siteId, SubjectId workerId,
                                  SubjectId actorAccountId, SubjectId depotAccountId, SubjectId outputItemId,
                                  InventoryCustody.ContainerSlot outputSlot, PhysicalIntentId intentId,
                                  ResourceSiteHarvestProgress progress, ResourceFieldWorkTarget target) {
        this(id, taskId, siteId, workerId, actorAccountId, depotAccountId, outputItemId, outputSlot, intentId,
                progress, 0, false, Optional.empty(), Optional.empty(), Optional.empty(), 0, target);
    }

    private static boolean matchesLastBatch(ResourceSiteHarvestBatchDelivered batch, SubjectId jobId,
                                            PhysicalIntentId intentId, SubjectId siteId, SubjectId workerId,
                                            SubjectId actorAccountId, SubjectId depotAccountId, int delivered) {
        ResourceSiteHarvestDeliveryObservation receipt = batch.receipt();
        return delivered >= 64 && batch.deliveredYieldBefore() == delivered - 64
                && receipt.jobId().equals(jobId) && receipt.intentId().equals(intentId)
                && receipt.siteId().equals(siteId) && receipt.workerId().equals(workerId)
                && receipt.actorAccountId().equals(actorAccountId) && receipt.depotAccountId().equals(depotAccountId);
    }

    /** Confirmed output history, not a second mutable custody balance. */
    public int undeliveredYieldQuantity() {
        return harvestedYieldQuantity - deliveredYieldQuantity;
    }

    public ResourceSiteHarvestJob withConfirmedCrop(ResourceSiteHarvestProgress next, ResourceFieldCycle.WorkOutcome outcome) {
        Objects.requireNonNull(outcome, "confirmed field outcome");
        if (next.completedCropSlots() != progress.completedCropSlots() + 1 || !progress.hasPendingCrop())
            throw new IllegalArgumentException("field yield history requires one confirmed pending cell outcome");
        int produced = outcome == ResourceFieldCycle.WorkOutcome.HARVESTED ? 1 : 0;
        return new ResourceSiteHarvestJob(id, taskId, siteId, workerId, actorAccountId, depotAccountId,
                outputItemId, outputSlot, intentId, next, deliveredYieldQuantity, returningForBatch,
                batchSuccessorSlot, lastConfirmedBatch, navigationBlock, Math.addExact(harvestedYieldQuantity, produced), target);
    }

    /** Bind only a chosen current cell; terminal and depot-bound jobs retain their historical target. */
    public ResourceSiteHarvestJob bindTarget(ResourceFieldCycle cycle) {
        if (progress.complete() || returningForBatch) return this;
        ResourceFieldWorkTarget next = cycle.target(cycle.layout().cells().get(progress.nextCropSlotIndex()).id());
        if (progress.hasPendingCrop() && !target.equals(next))
            throw new IllegalArgumentException("pending physical work cannot change its generation claim");
        if (target.equals(next)) return this;
        ResourceSiteHarvestProgress selected = new ResourceSiteHarvestProgress(progress.totalCropSlots(),
                progress.completedCropSlots(), progress.pendingCropSlotIndex(), progress.selectedCropSlotIndex(),
                progress.lastCompletedCropSlotIndex());
        return new ResourceSiteHarvestJob(id, taskId, siteId, workerId, actorAccountId, depotAccountId,
                outputItemId, outputSlot, intentId, selected, deliveredYieldQuantity, returningForBatch,
                batchSuccessorSlot, lastConfirmedBatch, navigationBlock, harvestedYieldQuantity, next);
    }

    public ResourceSiteHarvestJob withWork(WorkProgress work) {
        return copy(progress.withWork(work), deliveredYieldQuantity, returningForBatch,
                batchSuccessorSlot, lastConfirmedBatch, navigationBlock);
    }
    public ResourceSiteHarvestJob withProgress(ResourceSiteHarvestProgress next) {
        Objects.requireNonNull(next, "next field progress");
        if (navigationBlock.isPresent() || next.totalCropSlots() != progress.totalCropSlots())
            throw new IllegalArgumentException("blocked farmer or changed layout cannot progress crop work");
        return copy(next, deliveredYieldQuantity, returningForBatch, batchSuccessorSlot, lastConfirmedBatch, navigationBlock);
    }

    /** One external cell loss changes the work pool, not the actor's body or wheat hand. */
    public ResourceSiteHarvestJob withObservedCellLoss(int lostSlot, int nextSelectedCropSlotIndex) {
        ResourceSiteHarvestProgress ready = lostSlot == progress.selectedCropSlotIndex() && progress.hasPendingCrop()
                ? progress.cancelPreparedCrop() : progress;
        ResourceSiteHarvestProgress next = ready.accountObservedLoss(lostSlot, nextSelectedCropSlotIndex);
        boolean terminal = next.complete();
        return copy(next, deliveredYieldQuantity, terminal ? false : returningForBatch,
                terminal ? Optional.empty() : batchSuccessorSlot, lastConfirmedBatch,
                terminal || lostSlot == progress.selectedCropSlotIndex() ? Optional.empty() : navigationBlock);
    }

    /** A route-inaccessible target remains pending while an alternate goal clears only its local route hold. */
    public ResourceSiteHarvestJob retargetTo(int nextSelectedCropSlotIndex) {
        if (returningForBatch || progress.complete() || progress.hasPendingCrop()
                || navigationBlock.filter(block -> !block.reroutable()).isPresent())
            throw new IllegalArgumentException("area work cannot retarget a different movement obligation");
        return copy(progress.withSelectedCropSlot(nextSelectedCropSlotIndex), deliveredYieldQuantity,
                false, batchSuccessorSlot, lastConfirmedBatch, Optional.empty());
    }

    /** Account a physically/currently witnessed contiguous obstructed CellId prefix at zero yield. */
    public ResourceSiteHarvestJob withBlockedCellsSkipped(int count) {
        if (count < 1 || count > progress.totalCropSlots() - progress.completedCropSlots()
                || progress.complete() || progress.hasPendingCrop() || returningForBatch
                || batchSuccessorSlot.isPresent() || undeliveredYieldQuantity() >= 64)
            throw new IllegalArgumentException("blocked field cells cannot replace another worker's progress");
        ResourceSiteHarvestProgress advanced = progress;
        for (int index = 0; index < count; index++) advanced = advanced.prepareNextCrop().confirmPreparedCrop();
        return copy(advanced, deliveredYieldQuantity, false, Optional.empty(), lastConfirmedBatch, Optional.empty());
    }

    /** One observed unavailable target advances the area ledger, not a path cursor. */
    public ResourceSiteHarvestJob withSelectedCellSkipped(int nextSelectedCropSlotIndex) {
        if (progress.complete() || progress.hasPendingCrop() || returningForBatch
                || batchSuccessorSlot.isPresent() || undeliveredYieldQuantity() >= 64)
            throw new IllegalArgumentException("unavailable field target cannot replace another work result");
        return copy(progress.skipSelectedCrop(nextSelectedCropSlotIndex), deliveredYieldQuantity,
                false, Optional.empty(), lastConfirmedBatch, Optional.empty());
    }

    /** The current hand filled before the final CellId; the next goal is the depot. */
    public ResourceSiteHarvestJob withFullBatchReturn() {
        if (progress.complete() || progress.hasPendingCrop() || returningForBatch || navigationBlock.isPresent()
                || undeliveredYieldQuantity() != 64)
            throw new IllegalArgumentException("field batch return lacks its exact full hand");
        return copy(progress, deliveredYieldQuantity, true, Optional.empty(), lastConfirmedBatch, Optional.empty());
    }

    public ResourceSiteHarvestJob reserveBatchSuccessorSlot(InventoryCustody.ContainerSlot nextSlot) {
        Objects.requireNonNull(nextSlot, "reserved next field output slot");
        if (!returningForBatch || batchSuccessorSlot.isPresent() || navigationBlock.isPresent()
                || undeliveredYieldQuantity() != 64
                || !nextSlot.containerId().equals(outputSlot.containerId()) || nextSlot.equals(outputSlot))
            throw new IllegalArgumentException("field batch successor reservation lacks its full hand and depot");
        return copy(progress, deliveredYieldQuantity, true, Optional.of(nextSlot), lastConfirmedBatch, Optional.empty());
    }

    /** The reducer first proves the physical/canonical depot handoff and current worker body. */
    public ResourceSiteHarvestJob afterFullBatchDelivery(InventoryCustody.ContainerSlot nextSlot,
                                                          Optional<ResourceSiteHarvestBatchDelivered> confirmedBatch) {
        return afterFullBatchDelivery(Optional.of(nextSlot), confirmedBatch);
    }

    /** Current delivery is independent of future capacity. No continuation ends only this work offer. */
    public ResourceSiteHarvestJob afterFullBatchDelivery(Optional<InventoryCustody.ContainerSlot> nextSlot,
                                                          Optional<ResourceSiteHarvestBatchDelivered> confirmedBatch) {
        Objects.requireNonNull(nextSlot, "optional next field depot slot");
        Objects.requireNonNull(confirmedBatch, "confirmed field batch receipt");
        if (!returningForBatch || progress.complete() || navigationBlock.isPresent()
                || undeliveredYieldQuantity() != 64
                || nextSlot.filter(slot -> !slot.containerId().equals(outputSlot.containerId()) || slot.equals(outputSlot)).isPresent()
                || batchSuccessorSlot.isPresent() && !batchSuccessorSlot.equals(nextSlot))
            throw new IllegalArgumentException("field batch delivery has no exact depot continuation");
        return new ResourceSiteHarvestJob(id, taskId, siteId, workerId, actorAccountId, depotAccountId,
                outputItemId, nextSlot.orElse(outputSlot), intentId,
                nextSlot.isPresent() ? progress : progress.afterDeliverySelection(-1), deliveredYieldQuantity + 64,
                false, Optional.empty(), confirmedBatch, Optional.empty(), harvestedYieldQuantity, target);
    }

    /** A settled, finished offer retains its output address as history, not an empty-slot reservation. */
    public boolean reservesOutputCapacity() { return !progress.complete() || undeliveredYieldQuantity() > 0; }

    public boolean matchesWorkGoal(ResourceSiteHarvestGoal goal, SurfaceAnchor observedStation) {
        Objects.requireNonNull(goal, "current field work goal");
        Objects.requireNonNull(observedStation, "observed field worker station");
        return !progress.complete() && !returningForBatch && navigationBlock.isEmpty()
                && goal.kind() == ResourceSiteHarvestGoal.Kind.WORK_CELL
                && goal.jobId().equals(id) && goal.siteId().equals(siteId) && goal.workerId().equals(workerId)
                && goal.nextWorkSlot() == progress.nextCropSlotIndex() && goal.arrivedAt(observedStation);
    }

    /** Arrival changes only an exact goal block; common inspection already retained the actor body. */
    public ResourceSiteHarvestJob arriveAtSemanticGoal(ResourceSiteHarvestGoal goal) {
        Objects.requireNonNull(goal, "arrived field goal");
        if (!goal.jobId().equals(id) || !goal.siteId().equals(siteId) || !goal.workerId().equals(workerId)
                || goal.capability() != TraversalCapability.PEDESTRIAN
                || goal.kind() == ResourceSiteHarvestGoal.Kind.WORK_CELL
                    && (returningForBatch || progress.complete() || goal.nextWorkSlot() != progress.nextCropSlotIndex())
                || goal.kind() == ResourceSiteHarvestGoal.Kind.DEPOT_SERVICE
                    && ((!returningForBatch && !progress.complete()) || goal.nextWorkSlot() != progress.totalCropSlots())
                || navigationBlock.filter(block -> block.reason() == ResourceSiteHarvestNavigationBlock.Reason.CONTINUATION_UNAVAILABLE
                    || block.layoutRevision() != goal.layoutRevision()
                    || !block.target().equals(goal.representative())).isPresent())
            throw new IllegalArgumentException("field goal arrival has a foreign or blocked job");
        return navigationBlock.isEmpty() ? this
                : copy(progress, deliveredYieldQuantity, returningForBatch, batchSuccessorSlot, lastConfirmedBatch, Optional.empty());
    }

    /** A displaced worker may need to re-approach the SAME prepared cell. The route
     * hold neither cancels that physical obligation nor authorizes a different target. */
    public ResourceSiteHarvestJob withNavigationBlock(ResourceSiteHarvestNavigationBlock block) {
        Objects.requireNonNull(block, "farmer navigation block");
        if (navigationBlock.isPresent()
                || block.reason() == ResourceSiteHarvestNavigationBlock.Reason.CONTINUATION_UNAVAILABLE
                    && (progress.hasPendingCrop() || progress.complete() || returningForBatch))
            throw new IllegalArgumentException("farmer has no exact unblocked next movement goal: job=" + id.value()
                    + ";worker=" + workerId.value() + ";cell=" + target.cellId()
                    + ";pendingCrop=" + progress.hasPendingCrop() + ";existingBlock=" + navigationBlock
                    + ";requestedBlock=" + block);
        return copy(progress, deliveredYieldQuantity, returningForBatch, batchSuccessorSlot,
                lastConfirmedBatch, Optional.of(block));
    }

    public ResourceSiteHarvestJob clearNavigationBlock(ResourceSiteHarvestNavigationBlock expected) {
        if (!navigationBlock.equals(Optional.of(Objects.requireNonNull(expected, "expected navigation block"))))
            throw new IllegalArgumentException("farmer route clear lacks its exact retained block");
        return copy(progress, deliveredYieldQuantity, returningForBatch, batchSuccessorSlot,
                lastConfirmedBatch, Optional.empty());
    }

    private ResourceSiteHarvestJob copy(ResourceSiteHarvestProgress nextProgress, int delivered, boolean returning,
                                        Optional<InventoryCustody.ContainerSlot> successor,
                                        Optional<ResourceSiteHarvestBatchDelivered> batch,
                                        Optional<ResourceSiteHarvestNavigationBlock> blocked) {
        return new ResourceSiteHarvestJob(id, taskId, siteId, workerId, actorAccountId, depotAccountId,
                outputItemId, outputSlot, intentId, nextProgress, delivered, returning, successor, batch, blocked, harvestedYieldQuantity, target);
    }
}
