package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;

import java.util.Objects;
import java.util.Optional;

/** Mutable canonical facts for one fixed resource site, excluding Minecraft blocks and item custody. */
public record ResourceSiteLifecycle(SubjectId siteId, ResourceSitePhase phase, long growthEpoch, int growthStage,
                                    Optional<ResourceSiteWork> activeWork,
                                    Optional<ResourceSiteConflictDisposition> conflictDisposition,
                                    Optional<ResourceSiteHarvestLineage> harvestLineage) {
    public static final int MATURE_STAGE = 7;

    public ResourceSiteLifecycle {
        Objects.requireNonNull(siteId, "resource-site lifecycle id"); Objects.requireNonNull(phase, "resource-site phase");
        activeWork = Optional.ofNullable(activeWork).orElse(Optional.empty());
        conflictDisposition = Optional.ofNullable(conflictDisposition).orElse(Optional.empty());
        harvestLineage = Optional.ofNullable(harvestLineage).orElse(Optional.empty());
        if (!siteId.value().startsWith("site:") || growthEpoch < 0L || growthStage < 0 || growthStage > MATURE_STAGE) {
            throw new IllegalArgumentException("resource-site lifecycle value is invalid");
        }
        harvestLineage.ifPresent(lineage -> {
            if (lineage.completedGrowthEpoch() + 1L != growthEpoch) {
                throw new IllegalArgumentException("resource-site harvest lineage must bind the immediately preceding growth epoch");
            }
        });
        activeWork.ifPresent(work -> {
            if (!siteId.equals(work.siteId())) throw new IllegalArgumentException("resource-site work must belong to its lifecycle site");
        });
        switch (phase) {
            case UNPREPARED -> {
                if (conflictDisposition.isPresent() || harvestLineage.isPresent()) throw new IllegalArgumentException("unprepared resource site cannot retain terminal harvest state");
                if (growthEpoch != 0L || growthStage != 0 || activeWork.filter(ResourceSitePreparationJob.class::isInstance).isEmpty() && activeWork.isPresent()) {
                    throw new IllegalArgumentException("unprepared resource site may retain only its initial preparation work");
                }
            }
            case GROWING -> {
                if (conflictDisposition.isPresent()) throw new IllegalArgumentException("growing resource site cannot retain a conflict disposition");
                if (growthEpoch == 0L || growthStage >= MATURE_STAGE || activeWork.isPresent()) throw new IllegalArgumentException("growing resource site state is invalid");
            }
            case READY -> {
                if (conflictDisposition.isPresent()) throw new IllegalArgumentException("ready resource site cannot retain a conflict disposition");
                if (growthEpoch == 0L || growthStage != MATURE_STAGE || activeWork.isPresent()) throw new IllegalArgumentException("ready resource site state is invalid");
            }
            case HARVESTING -> {
                if (conflictDisposition.isPresent()) throw new IllegalArgumentException("harvesting resource site cannot retain a conflict disposition");
                if (growthEpoch == 0L || growthStage != MATURE_STAGE || activeWork.filter(ResourceSiteHarvestJob.class::isInstance).isEmpty()) {
                    throw new IllegalArgumentException("harvesting resource site must retain one mature harvest job");
                }
                ResourceSiteHarvestJob activeHarvest = activeWork.filter(ResourceSiteHarvestJob.class::isInstance).map(ResourceSiteHarvestJob.class::cast).orElseThrow();
                harvestLineage.ifPresent(lineage -> {
                    if (lineage.successorJobId().isEmpty() || !lineage.successorJobId().orElseThrow().equals(activeHarvest.id())
                            || !lineage.successorTaskId().orElseThrow().equals(activeHarvest.taskId()) || !lineage.workerId().equals(activeHarvest.workerId())) {
                        throw new IllegalArgumentException("harvesting successor must retain its declared prior farmer");
                    }
                });
            }
            case CONFLICT -> {
                // A failed harvest retains its exact job only as causal evidence for its
                // terminal unknown intent. It is never eligible for a new assignment.
                if (activeWork.isPresent() && activeWork.filter(ResourceSiteHarvestJob.class::isInstance).isEmpty()) {
                    throw new IllegalArgumentException("resource-site conflict may retain only its failed harvest identity");
                }
                if (conflictDisposition.isEmpty()) throw new IllegalArgumentException("resource-site conflict requires one typed disposition");
            }
            case DESTROYED -> {
                if (conflictDisposition.isPresent()) throw new IllegalArgumentException("destroyed resource site cannot retain a conflict disposition");
                if (activeWork.isPresent()) throw new IllegalArgumentException("destroyed resource-site condition cannot retain active work");
            }
        }
    }

    public ResourceSiteLifecycle(SubjectId siteId, ResourceSitePhase phase, long growthEpoch, int growthStage,
                                 Optional<ResourceSiteWork> activeWork) {
        this(siteId, phase, growthEpoch, growthStage, activeWork, Optional.empty(), Optional.empty());
    }

    public ResourceSiteLifecycle(SubjectId siteId, ResourceSitePhase phase, long growthEpoch, int growthStage,
                                 Optional<ResourceSiteWork> activeWork, Optional<ResourceSiteConflictDisposition> conflictDisposition) {
        this(siteId, phase, growthEpoch, growthStage, activeWork, conflictDisposition, Optional.empty());
    }

    public static ResourceSiteLifecycle unprepared(SubjectId siteId) { return new ResourceSiteLifecycle(siteId, ResourceSitePhase.UNPREPARED, 0L, 0, Optional.empty(), Optional.empty(), Optional.empty()); }
    public ResourceSiteLifecycle preparing(ResourceSitePreparationJob job) {
        if (phase != ResourceSitePhase.UNPREPARED || activeWork.isPresent()) throw new IllegalStateException("resource site is not available for preparation");
        return next(phase, growthEpoch, growthStage, Optional.of(job));
    }
    public ResourceSiteLifecycle prepared() {
        if (phase != ResourceSitePhase.UNPREPARED || activeWork.filter(ResourceSitePreparationJob.class::isInstance).isEmpty()) throw new IllegalStateException("resource site has no active preparation");
        return next(ResourceSitePhase.GROWING, 1L, 0, Optional.empty());
    }
    public ResourceSiteLifecycle advanceGrowth() {
        if (phase != ResourceSitePhase.GROWING) throw new IllegalStateException("resource site is not growing");
        int nextStage = Math.addExact(growthStage, 1);
        return next(nextStage == MATURE_STAGE ? ResourceSitePhase.READY : ResourceSitePhase.GROWING, growthEpoch, nextStage, Optional.empty());
    }
    public ResourceSiteLifecycle harvesting(ResourceSiteHarvestJob job) {
        if (phase != ResourceSitePhase.READY) throw new IllegalStateException("resource site is not ready for harvest");
        Optional<ResourceSiteHarvestLineage> nextLineage = harvestLineage.map(lineage -> lineage.bindSuccessor(job));
        return next(ResourceSitePhase.HARVESTING, growthEpoch, growthStage, Optional.of(job), nextLineage);
    }
    public ResourceSiteLifecycle advanceHarvest(ResourceSiteHarvestJob expected, int completedCropSlots,
                                                ResourceSiteHarvestGoal goal, SurfaceAnchor observedStation) {
        return advanceHarvest(expected, completedCropSlots, goal, observedStation,
                completedCropSlots == expected.progress().totalCropSlots() ? -1 : completedCropSlots);
    }
    public ResourceSiteLifecycle advanceHarvest(ResourceSiteHarvestJob expected, int completedCropSlots,
                                                ResourceSiteHarvestGoal goal, SurfaceAnchor observedStation,
                                                int nextSelectedCropSlotIndex) {
        ResourceSiteHarvestJob active = activeWork.filter(ResourceSiteHarvestJob.class::isInstance).map(ResourceSiteHarvestJob.class::cast)
                .orElseThrow(() -> new IllegalStateException("resource site has no active harvest"));
        if (phase != ResourceSitePhase.HARVESTING || !active.equals(expected)
                || !active.matchesWorkGoal(goal, observedStation)
                || completedCropSlots != active.progress().completedCropSlots() + 1) {
            throw new IllegalArgumentException("resource-site harvest progress is stale or invalid");
        }
        return next(phase, growthEpoch, growthStage, Optional.of(active.withProgress(
                active.progress().confirmPreparedCrop(nextSelectedCropSlotIndex))));
    }
    public ResourceSiteLifecycle returnFullHarvestBatch(ResourceSiteHarvestJob expected, int totalYield) {
        ResourceSiteHarvestJob active = activeWork.filter(ResourceSiteHarvestJob.class::isInstance).map(ResourceSiteHarvestJob.class::cast)
                .orElseThrow(() -> new IllegalStateException("resource site has no active harvest"));
        if (phase != ResourceSitePhase.HARVESTING || !active.equals(expected))
            throw new IllegalArgumentException("resource-site full-batch return has a stale job");
        return next(phase, growthEpoch, growthStage, Optional.of(active.withFullBatchReturn(totalYield)));
    }
    /** Obstruction accounts one work target; no physical action or worker movement occurs. */
    public ResourceSiteLifecycle skipBlockedHarvestCell(ResourceSiteHarvestJob expected, int count, int totalYield) {
        ResourceSiteHarvestJob active = activeWork.filter(ResourceSiteHarvestJob.class::isInstance)
                .map(ResourceSiteHarvestJob.class::cast).orElseThrow(
                        () -> new IllegalStateException("resource site has no active harvest"));
        if (phase != ResourceSitePhase.HARVESTING || !active.equals(expected))
            throw new IllegalArgumentException("blocked field cell has a stale harvest job");
        return next(phase, growthEpoch, growthStage,
                Optional.of(active.withBlockedCellsSkipped(count, totalYield)));
    }
    public ResourceSiteLifecycle skipSelectedHarvestCell(ResourceSiteHarvestJob expected,
                                                         int nextSelectedCropSlotIndex, int totalYield) {
        ResourceSiteHarvestJob active = activeWork.filter(ResourceSiteHarvestJob.class::isInstance)
                .map(ResourceSiteHarvestJob.class::cast).orElseThrow();
        if (phase != ResourceSitePhase.HARVESTING || !active.equals(expected))
            throw new IllegalArgumentException("unavailable field target has a stale harvest owner");
        return next(phase, growthEpoch, growthStage,
                Optional.of(active.withSelectedCellSkipped(nextSelectedCropSlotIndex, totalYield)));
    }
    public ResourceSiteLifecycle retargetHarvestCell(ResourceSiteHarvestJob expected, int nextSelectedCropSlotIndex) {
        ResourceSiteHarvestJob active = activeWork.filter(ResourceSiteHarvestJob.class::isInstance)
                .map(ResourceSiteHarvestJob.class::cast).orElseThrow();
        if (phase != ResourceSitePhase.HARVESTING || !active.equals(expected))
            throw new IllegalArgumentException("area work retarget has a stale harvest owner");
        return next(phase, growthEpoch, growthStage, Optional.of(
                active.retargetTo(nextSelectedCropSlotIndex)));
    }
    public ResourceSiteLifecycle deliverFullHarvestBatch(ResourceSiteHarvestJob expected,
                                                         InventoryCustody.ContainerSlot nextSlot, int totalYield,
                                                         Optional<ResourceSiteHarvestBatchDelivered> confirmedBatch) {
        ResourceSiteHarvestJob active = activeWork.filter(ResourceSiteHarvestJob.class::isInstance).map(ResourceSiteHarvestJob.class::cast)
                .orElseThrow(() -> new IllegalStateException("resource site has no active harvest"));
        if (phase != ResourceSitePhase.HARVESTING || !active.equals(expected))
            throw new IllegalArgumentException("resource-site batch delivery has a stale job");
        return next(phase, growthEpoch, growthStage,
                Optional.of(active.afterFullBatchDelivery(nextSlot, totalYield, confirmedBatch)));
    }

    public ResourceSiteLifecycle deliverFullHarvestBatch(ResourceSiteHarvestJob expected,
                                                         InventoryCustody.ContainerSlot nextSlot, int totalYield) {
        return deliverFullHarvestBatch(expected, nextSlot, totalYield, Optional.empty());
    }
    public ResourceSiteLifecycle reserveHarvestBatchSuccessor(ResourceSiteHarvestJob expected,
                                                              InventoryCustody.ContainerSlot nextSlot, int totalYield) {
        ResourceSiteHarvestJob active = activeWork.filter(ResourceSiteHarvestJob.class::isInstance).map(ResourceSiteHarvestJob.class::cast)
                .orElseThrow(() -> new IllegalStateException("resource site has no active harvest"));
        if (phase != ResourceSitePhase.HARVESTING || !active.equals(expected))
            throw new IllegalArgumentException("resource-site batch reservation has a stale job");
        return next(phase, growthEpoch, growthStage, Optional.of(active.reserveBatchSuccessorSlot(nextSlot, totalYield)));
    }
    public ResourceSiteLifecycle arriveHarvestGoal(ResourceSiteHarvestJob expected, ResourceSiteHarvestGoal goal) {
        ResourceSiteHarvestJob active = activeWork.filter(ResourceSiteHarvestJob.class::isInstance)
                .map(ResourceSiteHarvestJob.class::cast).orElseThrow(
                        () -> new IllegalStateException("resource site has no active harvest goal"));
        if (phase != ResourceSitePhase.HARVESTING || !active.equals(expected))
            throw new IllegalArgumentException("resource-site goal arrival has a stale job");
        return next(phase, growthEpoch, growthStage, Optional.of(active.arriveAtSemanticGoal(goal)));
    }
    public ResourceSiteLifecycle blockHarvestRoute(ResourceSiteHarvestJob expected,
                                                    ResourceSiteHarvestNavigationBlock block) {
        ResourceSiteHarvestJob active = activeWork.filter(ResourceSiteHarvestJob.class::isInstance)
                .map(ResourceSiteHarvestJob.class::cast).orElseThrow(
                        () -> new IllegalStateException("resource site has no active harvest"));
        if (phase != ResourceSitePhase.HARVESTING || !active.equals(expected))
            throw new IllegalArgumentException("farmer route block has a stale job");
        return next(phase, growthEpoch, growthStage, Optional.of(active.withNavigationBlock(block)));
    }
    public ResourceSiteLifecycle clearHarvestRouteBlock(ResourceSiteHarvestJob expected,
                                                         ResourceSiteHarvestNavigationBlock blocked) {
        ResourceSiteHarvestJob active = activeWork.filter(ResourceSiteHarvestJob.class::isInstance)
                .map(ResourceSiteHarvestJob.class::cast).orElseThrow(
                        () -> new IllegalStateException("resource site has no active harvest"));
        if (phase != ResourceSitePhase.HARVESTING || !active.equals(expected))
            throw new IllegalArgumentException("farmer route clearance has a stale job");
        return next(phase, growthEpoch, growthStage, Optional.of(active.clearNavigationBlock(blocked)));
    }
    public ResourceSiteLifecycle prepareHarvestCrop(ResourceSiteHarvestJob expected, int cropSlotIndex,
                                                     ResourceSiteHarvestGoal goal, SurfaceAnchor observedStation) {
        ResourceSiteHarvestJob active = activeWork.filter(ResourceSiteHarvestJob.class::isInstance).map(ResourceSiteHarvestJob.class::cast)
                .orElseThrow(() -> new IllegalStateException("resource site has no active harvest"));
        if (phase != ResourceSitePhase.HARVESTING || !active.equals(expected)
                || !active.matchesWorkGoal(goal, observedStation)
                || cropSlotIndex != active.progress().nextCropSlotIndex()) {
            throw new IllegalArgumentException("resource-site harvest crop preparation is stale or invalid");
        }
        return next(phase, growthEpoch, growthStage, Optional.of(active.withProgress(active.progress().prepareNextCrop())));
    }

    /** Retain the same farmer and work cell after a witnessed external cell change. */
    public ResourceSiteLifecycle cancelPreparedHarvestCrop(ResourceFieldLayout.CellId cellId,
                                                           ResourceFieldCycle cycle) {
        ResourceSiteHarvestJob active = activeWork.filter(ResourceSiteHarvestJob.class::isInstance)
                .map(ResourceSiteHarvestJob.class::cast).orElseThrow();
        if (phase != ResourceSitePhase.HARVESTING || !active.progress().hasPendingCrop()
                || !cycle.layout().cells().get(active.progress().pendingCropSlotIndex()).id().equals(cellId))
            throw new IllegalArgumentException("field interruption lacks the current prepared farmer cell");
        return next(phase, growthEpoch, growthStage,
                Optional.of(active.withProgress(active.progress().cancelPreparedCrop())));
    }

    /** A confirmed crop loss closes one CellId in the same transition as the field ledger. */
    public ResourceSiteLifecycle accountObservedLostHarvestCell(ResourceSiteHarvestJob expected,
                                                                int lostSlot, int nextSelectedCropSlotIndex,
                                                                int totalYield) {
        ResourceSiteHarvestJob active = activeWork.filter(ResourceSiteHarvestJob.class::isInstance)
                .map(ResourceSiteHarvestJob.class::cast).orElseThrow();
        if (phase != ResourceSitePhase.HARVESTING || !active.equals(expected))
            throw new IllegalArgumentException("observed crop loss has a stale harvest owner");
        return next(phase, growthEpoch, growthStage, Optional.of(
                active.withObservedCellLoss(lostSlot, nextSelectedCropSlotIndex, totalYield)));
    }

    /** Closes an observed HOT harvest at its actual declared depot service station. */
    public ResourceSiteLifecycle harvestedAt(ResourceSiteHarvestGoal goal, BodyPosition terminalBody) {
        ResourceSiteHarvestJob completed = activeWork.filter(ResourceSiteHarvestJob.class::isInstance).map(ResourceSiteHarvestJob.class::cast)
                .filter(job -> job.progress().complete()).orElse(null);
        if (phase != ResourceSitePhase.HARVESTING || completed == null || goal.kind() != ResourceSiteHarvestGoal.Kind.DEPOT_SERVICE
                || !goal.jobId().equals(completed.id()) || !goal.workerId().equals(completed.workerId())
                || !goal.arrivedAt(terminalBody.supportingSurface()))
            throw new IllegalStateException("resource site has no fully observed returned harvest");
        return next(ResourceSitePhase.GROWING, Math.addExact(growthEpoch, 1L), 0, Optional.empty(),
                Optional.of(ResourceSiteHarvestLineage.completed(completed, growthEpoch, true,
                        ResourceSiteHarvestCausality.notCaptured(completed), terminalBody)));
    }
    /** Closes COLD semantic work after atomically composing and fencing its exact physical request. */
    /** Retains the actual returned body, including an alternate legal depot service station. */
    public ResourceSiteLifecycle harvestedDeferred(ResourceSiteHarvestJob completed, boolean outputReceiptResolved,
                                                   ResourceSiteHarvestCausality causality, BodyPosition terminalBody) {
        ResourceSiteHarvestJob active = activeWork.filter(ResourceSiteHarvestJob.class::isInstance).map(ResourceSiteHarvestJob.class::cast)
                .orElseThrow(() -> new IllegalStateException("resource site has no active harvest"));
        if (phase != ResourceSitePhase.HARVESTING || !active.id().equals(completed.id()) || !completed.progress().complete()
                || !(active.equals(completed) || completed.progress().completedCropSlots() == active.progress().completedCropSlots() + 1)) {
            throw new IllegalArgumentException("resource site has no exact next complete COLD harvest");
        }
        return new ResourceSiteLifecycle(siteId, ResourceSitePhase.GROWING, Math.addExact(growthEpoch, 1L), 0, Optional.empty(), Optional.empty(),
                Optional.of(ResourceSiteHarvestLineage.completed(completed, growthEpoch, outputReceiptResolved, causality, terminalBody)));
    }
    public ResourceSiteLifecycle confirmDeferredHarvestReceipt(PhysicalIntentId intentId) {
        return confirmDeferredHarvestReceipt(intentId, null);
    }
    public ResourceSiteLifecycle confirmDeferredHarvestReceipt(PhysicalIntentId intentId,
                                                                io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId observationId) {
        ResourceSiteHarvestLineage lineage = harvestLineage.filter(ResourceSiteHarvestLineage::receiptPending)
                .filter(value -> value.predecessorIntentId().equals(intentId)).orElseThrow(
                        () -> new IllegalArgumentException("resource-site deferred harvest receipt has no matching lineage"));
        return next(phase, growthEpoch, growthStage, activeWork, Optional.of(lineage.resolveReceipt(observationId)));
    }
    public ResourceSiteLifecycle withHarvestTrace(RetainedDiagnosticTrace trace) {
        ResourceSiteHarvestLineage lineage = harvestLineage.orElseThrow(() -> new IllegalArgumentException("resource-site trace has no lineage"));
        if (!lineage.causality().trace().correlation().equals(trace.correlation())) throw new IllegalArgumentException("resource-site trace has foreign correlation");
        return next(phase, growthEpoch, growthStage, activeWork, Optional.of(lineage.withTrace(trace)));
    }
    public ResourceSiteLifecycle conflicted(ResourceSiteConflictDisposition disposition) {
        if (phase == ResourceSitePhase.DESTROYED) throw new IllegalStateException("destroyed resource site cannot become a conflict");
        Optional<ResourceSiteWork> retainedHarvest = activeWork.filter(ResourceSiteHarvestJob.class::isInstance);
        return new ResourceSiteLifecycle(siteId, ResourceSitePhase.CONFLICT, growthEpoch, growthStage, retainedHarvest, Optional.of(disposition), harvestLineage);
    }
    public ResourceSiteLifecycle destroyed() { return next(ResourceSitePhase.DESTROYED, growthEpoch, growthStage, Optional.empty()); }

    private ResourceSiteLifecycle next(ResourceSitePhase nextPhase, long nextEpoch, int nextStage, Optional<ResourceSiteWork> nextWork) {
        return next(nextPhase, nextEpoch, nextStage, nextWork, harvestLineage);
    }
    private ResourceSiteLifecycle next(ResourceSitePhase nextPhase, long nextEpoch, int nextStage, Optional<ResourceSiteWork> nextWork,
                                       Optional<ResourceSiteHarvestLineage> nextLineage) {
        return new ResourceSiteLifecycle(siteId, nextPhase, nextEpoch, nextStage, nextWork, Optional.empty(), nextLineage);
    }
}
