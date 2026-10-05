package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Field readiness and independent exact executions; no preferred-worker compatibility view. */
public record ResourceSiteLifecycle(SubjectId siteId, ResourceSitePhase phase, long growthEpoch, int growthStage,
                                    Optional<ResourceSitePreparationJob> preparationWork,
                                    Optional<ResourceSiteConflictDisposition> conflictDisposition,
                                    Map<SubjectId, ResourceSiteHarvestJob> harvestJobs,
                                    Map<PhysicalIntentId, ResourceSiteHarvestLineage> harvestLineages,
                                    long harvestSequence) {
    public static final int MATURE_STAGE = 7;
    public static final int MAX_HARVEST_WORKERS = 64;

    public ResourceSiteLifecycle {
        Objects.requireNonNull(siteId); Objects.requireNonNull(phase);
        preparationWork = Objects.requireNonNull(preparationWork);
        conflictDisposition = Objects.requireNonNull(conflictDisposition);
        harvestJobs = Map.copyOf(harvestJobs); harvestLineages = Map.copyOf(harvestLineages);
        if (growthEpoch < 0 || growthStage < 0 || growthStage > MATURE_STAGE || harvestSequence < 0
                || harvestJobs.size() > MAX_HARVEST_WORKERS || harvestLineages.size() > MAX_HARVEST_WORKERS * 2)
            throw new IllegalArgumentException("field lifecycle exceeds its declared bounds");
        var workers = new java.util.HashSet<SubjectId>();
        var intents = new java.util.HashSet<PhysicalIntentId>();
        var accounts = new java.util.HashSet<SubjectId>();
        var targets = new java.util.HashSet<Integer>();
        for (var entry : harvestJobs.entrySet()) {
            var job = entry.getValue();
            if (!entry.getKey().equals(job.id()) || !siteId.equals(job.siteId()) || !workers.add(job.workerId())
                    || !intents.add(job.intentId()) || !accounts.add(job.actorAccountId())
                    || harvestLineages.containsKey(job.intentId()))
                throw new IllegalArgumentException("field executions have foreign or duplicate owners");
            if (!job.progress().complete() && !job.returningForBatch()
                    && !targets.add(job.progress().selectedCropSlotIndex()))
                throw new IllegalArgumentException("field cell has multiple reservation owners");
        }
        for (var entry : harvestLineages.entrySet())
            if (!entry.getKey().equals(entry.getValue().predecessorIntentId()))
                throw new IllegalArgumentException("field history has a foreign physical intent key");
        if (preparationWork.isPresent() && (!siteId.equals(preparationWork.orElseThrow().siteId()) || !harvestJobs.isEmpty()))
            throw new IllegalArgumentException("preparation and harvest cannot own the field concurrently");
        switch (phase) {
            case UNPREPARED -> {
                if (growthEpoch != 0 || growthStage != 0 || !harvestJobs.isEmpty() || !harvestLineages.isEmpty())
                    throw new IllegalArgumentException("unprepared field retains renewable work");
            }
            case GROWING, READY -> {
                if (growthEpoch == 0 || preparationWork.isPresent() || !harvestJobs.isEmpty()
                        || (phase == ResourceSitePhase.READY) != (growthStage == MATURE_STAGE))
                    throw new IllegalArgumentException("field readiness disagrees with plant or work ownership");
            }
            case HARVESTING -> {
                if (growthEpoch == 0 || preparationWork.isPresent() || harvestJobs.isEmpty())
                    throw new IllegalArgumentException("harvesting field lacks independent executions");
            }
            case CONFLICT -> {
                if (conflictDisposition.isEmpty() || preparationWork.isPresent())
                    throw new IllegalArgumentException("field conflict lacks its disposition");
            }
            case DESTROYED -> {
                if (preparationWork.isPresent() || !harvestJobs.isEmpty())
                    throw new IllegalArgumentException("destroyed field retains live work");
            }
        }
        if (phase != ResourceSitePhase.CONFLICT && conflictDisposition.isPresent())
            throw new IllegalArgumentException("non-conflicted field retains a disposition");
    }

    /** Explicit initial/fixture construction, not a read projection or alternate executor. */
    public ResourceSiteLifecycle(SubjectId site, ResourceSitePhase phase, long epoch, int stage,
                                 Optional<ResourceSiteWork> work, Optional<ResourceSiteConflictDisposition> conflict,
                                 Optional<ResourceSiteHarvestLineage> lineage) {
        this(site, phase, epoch, stage, work.filter(ResourceSitePreparationJob.class::isInstance).map(ResourceSitePreparationJob.class::cast),
                conflict, work.filter(ResourceSiteHarvestJob.class::isInstance).map(ResourceSiteHarvestJob.class::cast)
                        .map(job -> Map.of(job.id(), job)).orElse(Map.of()),
                lineage.map(value -> Map.of(value.predecessorIntentId(), value)).orElse(Map.of()), work.isPresent() ? 1 : 0);
    }
    public ResourceSiteLifecycle(SubjectId site, ResourceSitePhase phase, long epoch, int stage, Optional<ResourceSiteWork> work) {
        this(site, phase, epoch, stage, work, Optional.empty(), Optional.empty());
    }
    public ResourceSiteLifecycle(SubjectId site, ResourceSitePhase phase, long epoch, int stage,
                                 Optional<ResourceSiteWork> work, Optional<ResourceSiteConflictDisposition> conflict) {
        this(site, phase, epoch, stage, work, conflict, Optional.empty());
    }
    public static ResourceSiteLifecycle unprepared(SubjectId site) {
        return new ResourceSiteLifecycle(site, ResourceSitePhase.UNPREPARED, 0, 0, Optional.empty());
    }
    public Optional<ResourceSiteHarvestJob> harvestJob(SubjectId jobId) { return Optional.ofNullable(harvestJobs.get(jobId)); }
    public Optional<ResourceSiteHarvestLineage> harvestLineage(PhysicalIntentId intent) { return Optional.ofNullable(harvestLineages.get(intent)); }
    public boolean hasWork() { return preparationWork.isPresent() || !harvestJobs.isEmpty(); }
    /** Derived reservation view; changing a target changes its sole persisted owner. */
    public boolean targetAvailable(int slot, SubjectId execution) {
        return harvestJobs.values().stream().noneMatch(job -> !job.id().equals(execution)
                && !job.progress().complete() && !job.returningForBatch()
                && job.progress().selectedCropSlotIndex() == slot);
    }
    public ResourceSiteLifecycle preparing(ResourceSitePreparationJob job) {
        if (phase != ResourceSitePhase.UNPREPARED || hasWork()) throw new IllegalStateException("field is not available for preparation");
        return copy(phase, growthEpoch, growthStage, Optional.of(job), conflictDisposition, harvestJobs, harvestLineages, harvestSequence);
    }
    public ResourceSiteLifecycle prepared() {
        if (phase != ResourceSitePhase.UNPREPARED || preparationWork.isEmpty()) throw new IllegalStateException("field has no preparation");
        return copy(ResourceSitePhase.GROWING, 1, 0, Optional.empty(), Optional.empty(), harvestJobs, harvestLineages, harvestSequence);
    }
    public ResourceSiteLifecycle advanceGrowth() {
        if (phase != ResourceSitePhase.GROWING) throw new IllegalStateException("field is not growing");
        int stage = growthStage + 1;
        return copy(stage == MATURE_STAGE ? ResourceSitePhase.READY : ResourceSitePhase.GROWING, growthEpoch, stage,
                preparationWork, conflictDisposition, harvestJobs, harvestLineages, harvestSequence);
    }
    public ResourceSiteLifecycle withPlantReadiness(ResourceFieldCycle plants) {
        if (!siteId.equals(plants.siteId()) || growthEpoch != plants.epoch()) throw new IllegalArgumentException("foreign plant readiness");
        if (phase == ResourceSitePhase.UNPREPARED || phase == ResourceSitePhase.CONFLICT || phase == ResourceSitePhase.DESTROYED) return this;
        int stage = plants.plantGrowthStage();
        var nextPhase = harvestJobs.isEmpty() ? stage == MATURE_STAGE ? ResourceSitePhase.READY : ResourceSitePhase.GROWING : ResourceSitePhase.HARVESTING;
        return copy(nextPhase, growthEpoch, stage, preparationWork, conflictDisposition, harvestJobs, harvestLineages, harvestSequence);
    }
    public ResourceSiteLifecycle harvesting(ResourceSiteHarvestJob job) {
        if (phase != ResourceSitePhase.READY && phase != ResourceSitePhase.HARVESTING || harvestJobs.containsKey(job.id()))
            throw new IllegalStateException("field cannot admit this execution");
        var jobs = new LinkedHashMap<>(harvestJobs); jobs.put(job.id(), job);
        return copy(ResourceSitePhase.HARVESTING, growthEpoch, growthStage, preparationWork, Optional.empty(), jobs, harvestLineages,
                Math.addExact(harvestSequence, 1));
    }
    private ResourceSiteHarvestJob require(ResourceSiteHarvestJob expected) {
        if (phase != ResourceSitePhase.HARVESTING || !expected.equals(harvestJobs.get(expected.id())))
            throw new IllegalArgumentException("field transition has a stale exact execution");
        return expected;
    }
    private ResourceSiteLifecycle replace(ResourceSiteHarvestJob job) {
        var jobs = new LinkedHashMap<>(harvestJobs); jobs.put(job.id(), job);
        return copy(phase, growthEpoch, growthStage, preparationWork, conflictDisposition, jobs, harvestLineages, harvestSequence);
    }
    public ResourceSiteLifecycle advanceHarvest(ResourceSiteHarvestJob expected, int count, ResourceSiteHarvestGoal goal,
                                                 SurfaceAnchor station, int nextSlot, ResourceFieldCycle.WorkOutcome outcome,
                                                 ResourceFieldCycle cycle) {
        var job = require(expected);
        if (!job.matchesWorkGoal(goal, station) || count != job.progress().completedCropSlots() + 1)
            throw new IllegalArgumentException("invalid exact crop completion");
        return replace(job.withConfirmedCrop(job.progress().confirmPreparedCrop(nextSlot), outcome).bindTarget(cycle));
    }
    public ResourceSiteLifecycle returnFullHarvestBatch(ResourceSiteHarvestJob job) { return replace(require(job).withFullBatchReturn()); }
    public ResourceSiteLifecycle skipBlockedHarvestCell(ResourceSiteHarvestJob job, int count) { return replace(require(job).withBlockedCellsSkipped(count)); }
    public ResourceSiteLifecycle withHarvestLabour(ResourceSiteHarvestJob job, WorkProgress work) { return replace(require(job).withWork(work)); }
    public ResourceSiteLifecycle withHarvestProgress(ResourceSiteHarvestJob job, ResourceSiteHarvestProgress progress) {
        return replace(require(job).withProgress(progress));
    }
    public ResourceSiteLifecycle acknowledgeHarvestWork(ResourceSiteHarvestJob job, ResourceSiteHarvestWorkAcceptance accepted) {
        return replace(require(job).acknowledgeWork(accepted));
    }
    public ResourceSiteLifecycle bindHarvestTarget(ResourceSiteHarvestJob job, ResourceFieldCycle cycle) {
        return replace(require(job).bindTarget(cycle));
    }
    /** Selection after one completed/excluded claim; sibling targets are never available work. */
    public int nextHarvestTarget(ResourceSiteHarvestJob job, ResourceFieldCycle successor) {
        require(job);
        if (job.progress().completedCropSlots() + 1 >= job.progress().totalCropSlots()) return -1;
        return selectHarvestTarget(job, successor,
                successor.layout().cells().get(job.progress().nextCropSlotIndex()).workstation(), index -> true).orElse(-1);
    }
    /** Admission and continuation share soft separation; exact job claims still own exclusion. */
    public java.util.OptionalInt selectHarvestTarget(ResourceSiteHarvestJob job, ResourceFieldCycle cycle,
            SurfaceAnchor origin, java.util.function.IntPredicate eligible) {
        return selectHarvestTarget(job, cycle, origin, eligible, AreaWorkSelection.SpatialPolicy.LOCAL_CONTINUATION);
    }
    public java.util.OptionalInt selectHarvestStart(ResourceSiteHarvestJob job, ResourceFieldCycle cycle,
            SurfaceAnchor origin, java.util.function.IntPredicate eligible) {
        return selectHarvestTarget(job, cycle, origin, eligible, AreaWorkSelection.SpatialPolicy.SPREAD_STARTS);
    }
    private java.util.OptionalInt selectHarvestTarget(ResourceSiteHarvestJob job, ResourceFieldCycle cycle,
            SurfaceAnchor origin, java.util.function.IntPredicate eligible, AreaWorkSelection.SpatialPolicy policy) {
        var peers = harvestJobs.values().stream().filter(peer -> !peer.id().equals(job.id())
                && !peer.progress().complete() && !peer.returningForBatch())
                .map(peer -> cycle.layout().cells().get(peer.progress().selectedCropSlotIndex()).workstation()).toList();
        return cycle.spatialWorkSlot(origin, index -> targetAvailable(index, job.id()) && eligible.test(index), peers, policy);
    }
    public ResourceSiteLifecycle skipSelectedHarvestCell(ResourceSiteHarvestJob job, int next, ResourceFieldCycle cycle) {
        return replace(require(job).withSelectedCellSkipped(next).bindTarget(cycle));
    }
    public ResourceSiteLifecycle retargetHarvestCell(ResourceSiteHarvestJob job, int next, ResourceFieldCycle cycle) {
        return replace(require(job).retargetTo(next).bindTarget(cycle));
    }
    public ResourceSiteLifecycle deliverFullHarvestBatch(ResourceSiteHarvestJob job, InventoryCustody.ContainerSlot slot,
                                                         Optional<ResourceSiteHarvestBatchDelivered> receipt, ResourceFieldCycle cycle,
                                                         SurfaceAnchor worker) {
        return deliverFullHarvestBatch(job, Optional.of(slot), receipt, cycle, worker);
    }
    public ResourceSiteLifecycle deliverFullHarvestBatch(ResourceSiteHarvestJob job, Optional<InventoryCustody.ContainerSlot> slot,
                                                         Optional<ResourceSiteHarvestBatchDelivered> receipt, ResourceFieldCycle cycle,
                                                         SurfaceAnchor worker) {
        ResourceSiteHarvestJob delivered = require(job).afterFullBatchDelivery(slot, receipt);
        if (delivered.progress().complete()) return replace(delivered);
        int next = delivered.progress().completedCropSlots() == delivered.progress().totalCropSlots() ? -1
                : selectHarvestTarget(job, cycle, worker, index -> true).orElse(-1);
        return replace(delivered.withProgress(delivered.progress().afterDeliverySelection(next)).bindTarget(cycle));
    }
    public ResourceSiteLifecycle deliverFullHarvestBatch(ResourceSiteHarvestJob job, InventoryCustody.ContainerSlot slot,
                                                         ResourceFieldCycle cycle, SurfaceAnchor worker) {
        return deliverFullHarvestBatch(job, slot, Optional.empty(), cycle, worker);
    }
    public ResourceSiteLifecycle reserveHarvestBatchSuccessor(ResourceSiteHarvestJob job, InventoryCustody.ContainerSlot slot) {
        return replace(require(job).reserveBatchSuccessorSlot(slot));
    }
    public ResourceSiteLifecycle arriveHarvestGoal(ResourceSiteHarvestJob job, ResourceSiteHarvestGoal goal) { return replace(require(job).arriveAtSemanticGoal(goal)); }
    public ResourceSiteLifecycle blockHarvestRoute(ResourceSiteHarvestJob job, ResourceSiteHarvestNavigationBlock block) { return replace(require(job).withNavigationBlock(block)); }
    public ResourceSiteLifecycle clearHarvestRouteBlock(ResourceSiteHarvestJob job, ResourceSiteHarvestNavigationBlock block) { return replace(require(job).clearNavigationBlock(block)); }
    public ResourceSiteLifecycle prepareHarvestCrop(ResourceSiteHarvestJob expected, int index, ResourceSiteHarvestGoal goal, SurfaceAnchor station) {
        var job = require(expected);
        if (!job.matchesWorkGoal(goal, station) || job.progress().nextCropSlotIndex() != index)
            throw new IllegalArgumentException("crop preparation has a foreign target or station");
        return replace(job.withProgress(job.progress().prepareNextCrop()));
    }
    public ResourceSiteLifecycle cancelPreparedHarvestCrop(ResourceFieldLayout.CellId cell, ResourceFieldCycle cycle) {
        var job = harvestJobs.values().stream().filter(value -> value.progress().hasPendingCrop()
                && cycle.layout().cells().get(value.progress().pendingCropSlotIndex()).id().equals(cell))
                .reduce((left, right) -> { throw new IllegalArgumentException("cell has duplicate pending owners"); }).orElseThrow();
        return replace(require(job).withProgress(job.progress().cancelPreparedCrop()));
    }
    public ResourceSiteLifecycle accountObservedLostHarvestCell(ResourceSiteHarvestJob job, int lost, int next, ResourceFieldCycle cycle) {
        return replace(require(job).withObservedCellLoss(lost, next).bindTarget(cycle));
    }
    public ResourceSiteLifecycle harvestedAt(ResourceSiteHarvestGoal goal, BodyPosition body) {
        return harvestedAt(goal, body, java.util.Set.of());
    }
    public ResourceSiteLifecycle harvestedAt(ResourceSiteHarvestGoal goal, BodyPosition body,
                                              java.util.Set<PhysicalIntentId> reclaimable) {
        var job = harvestJob(goal.jobId()).orElseThrow();
        if (goal.kind() != ResourceSiteHarvestGoal.Kind.DEPOT_SERVICE || !goal.workerId().equals(job.workerId())
                || !goal.arrivedAt(body.supportingSurface())) throw new IllegalArgumentException("terminal field goal lacks actual arrival");
        return harvestedDeferred(job, true, ResourceSiteHarvestCausality.notCaptured(job), body, reclaimable);
    }
    public ResourceSiteLifecycle harvestedDeferred(ResourceSiteHarvestJob job, boolean resolved, ResourceSiteHarvestCausality causality, BodyPosition body) {
        return harvestedDeferred(job, resolved, causality, body, java.util.Set.of());
    }
    public ResourceSiteLifecycle harvestedDeferred(ResourceSiteHarvestJob job, boolean resolved, ResourceSiteHarvestCausality causality,
                                                    BodyPosition body, java.util.Set<PhysicalIntentId> reclaimable) {
        require(job);
        if (!job.progress().complete() || job.progress().hasPendingPhysicalWork())
            throw new IllegalArgumentException("field execution is incomplete or retains physical work acceptance");
        var jobs = new LinkedHashMap<>(harvestJobs); jobs.remove(job.id());
        var histories = new LinkedHashMap<>(harvestLineages);
        for (PhysicalIntentId id : reclaimable) {
            var history = histories.get(id);
            if (history == null || !history.workerId().equals(job.workerId()) || !history.outputReceiptResolved())
                throw new IllegalArgumentException("history retirement has no resolved exact predecessor");
            histories.remove(id);
        }
        histories.put(job.intentId(), ResourceSiteHarvestLineage.completed(job, growthEpoch, resolved, causality, body));
        var nextPhase = jobs.isEmpty() ? growthStage == MATURE_STAGE ? ResourceSitePhase.READY : ResourceSitePhase.GROWING : ResourceSitePhase.HARVESTING;
        return copy(nextPhase, growthEpoch, growthStage, preparationWork, Optional.empty(), jobs, histories, harvestSequence);
    }
    public ResourceSiteLifecycle confirmDeferredHarvestReceipt(PhysicalIntentId intent) { return confirmDeferredHarvestReceipt(intent, null); }
    public ResourceSiteLifecycle confirmDeferredHarvestReceipt(PhysicalIntentId intent, io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId observation) {
        var lineage = harvestLineage(intent).filter(ResourceSiteHarvestLineage::receiptPending).orElseThrow();
        var histories = new LinkedHashMap<>(harvestLineages); histories.put(intent, lineage.resolveReceipt(observation));
        return copy(phase, growthEpoch, growthStage, preparationWork, conflictDisposition, harvestJobs, histories, harvestSequence);
    }
    public ResourceSiteLifecycle withHarvestTrace(PhysicalIntentId intent, RetainedDiagnosticTrace trace) {
        var lineage = harvestLineage(intent).orElseThrow();
        if (!lineage.causality().trace().correlation().equals(trace.correlation()))
            throw new IllegalArgumentException("field trace disagrees with its exact retained intent");
        var histories = new LinkedHashMap<>(harvestLineages); histories.put(lineage.predecessorIntentId(), lineage.withTrace(trace));
        return copy(phase, growthEpoch, growthStage, preparationWork, conflictDisposition, harvestJobs, histories, harvestSequence);
    }
    public ResourceSiteLifecycle conflicted(ResourceSiteConflictDisposition disposition) {
        if (phase == ResourceSitePhase.DESTROYED) throw new IllegalStateException("destroyed field cannot conflict");
        return copy(ResourceSitePhase.CONFLICT, growthEpoch, growthStage, Optional.empty(), Optional.of(disposition), harvestJobs, harvestLineages, harvestSequence);
    }
    public ResourceSiteLifecycle destroyed() {
        return copy(ResourceSitePhase.DESTROYED, growthEpoch, growthStage, Optional.empty(), Optional.empty(), Map.of(), harvestLineages, harvestSequence);
    }
    private ResourceSiteLifecycle copy(ResourceSitePhase next, long epoch, int stage, Optional<ResourceSitePreparationJob> preparation,
                                        Optional<ResourceSiteConflictDisposition> conflict, Map<SubjectId, ResourceSiteHarvestJob> jobs,
                                        Map<PhysicalIntentId, ResourceSiteHarvestLineage> histories, long sequence) {
        return new ResourceSiteLifecycle(siteId, next, epoch, stage, preparation, conflict, jobs, histories, sequence);
    }
}
