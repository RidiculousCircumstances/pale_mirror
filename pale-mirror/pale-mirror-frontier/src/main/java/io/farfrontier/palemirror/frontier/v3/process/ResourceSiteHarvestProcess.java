package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.model.*;

import io.farfrontier.palemirror.frontier.v3.api.FixedPosition;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleBinding;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalPostcondition;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;

import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * Plans one retained field-worker duty through the physical-intent boundary.
 *
 * <p>A mature field reserves its farmer, depot slot and output identity in canonical state. Its
 * COLD driver advances the same exact farmer from its current actor body toward the next
 * semantic work or depot goal. COLD commits bounded per-crop semantic receipts; natural
 * loading later projects that exact current partial field. HOT owns visible pose, local
 * collision, and the loaded physical continuation.
 * Each yielding cell accrues only its current bounded actor-held part.  The returned COLD worker
 * transfers that part to the depot exactly once; the former exact-stack terminal is retired.
 * HOT physical hand/depot receipt is a separate cutover obligation before player acceptance.</p>
 */
public final class ResourceSiteHarvestProcess {
    public static final String COLD_PROGRESS_KIND = "frontier.resource_site.harvest.cold_progress";
    /** A valid retained farmer has no bounded route to its next semantic goal. */
    public static final class ContinuationUnavailable extends IllegalArgumentException {
        public ContinuationUnavailable(IllegalArgumentException cause) {
            super("field work continuation has no bounded route", cause);
        }
    }
    private ResourceSiteHarvestProcess() { }

    /** Physical materialization remains a loaded postcondition; exact output ownership does not. */
    public static boolean irreversibleCropEffectsAdmitted() { return true; }

    public static ScheduledAction start(StrategicTask task, long dueAt) { return ResourceSiteHarvestPlanning.start(task, dueAt); }

    public static List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action) { return ResourceSiteHarvestPlanning.plan(state, action); }

    public static ScheduledAction coldProgress(ResourceSiteHarvestJob job, long dueAt) { return ResourceSiteHarvestPlanning.coldProgress(job, dueAt); }

    public static void requireContinuationBinding(ResourceSiteHarvestJob job, ScheduledAction action) {
        ResourceSiteHarvestPlanning.requireContinuationBinding(job, action);
    }

    public static void requireDueContinuationBinding(ResourceSiteHarvestJob job, ScheduledAction action, long instant) {
        ResourceSiteHarvestPlanning.requireDueContinuationBinding(job, action, instant);
    }

    public static ProposedEvent advanceBoundContinuation(FrontierWorldState state, ResourceSiteHarvestJob job, ScheduledAction action) {
        return ResourceSiteHarvestPlanning.advanceBoundContinuation(state, job, action);
    }

    public static List<ProposedEvent> planColdProgress(FrontierWorldState state, ScheduledAction action) {
        return ResourceSiteHarvestPlanning.planColdProgress(state, action);
    }

    public static boolean coldProgressHeld(FrontierWorldState state, ScheduledAction action) {
        return ResourceSiteHarvestPlanning.coldProgressHeld(state, action);
    }

    public static FrontierWorldState reduceStarted(FrontierWorldState state, SubjectId subject, ResourceSiteHarvestStarted started) {
        ResourceSiteHarvestJob job = started.job(); if (!subject.equals(job.siteId())) throw new IllegalArgumentException("resource-site harvest has a foreign event owner");
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(job.siteId()); validateJob(state, lifecycle, job);
        return state.withResourceSites(state.resourceSites().replace(lifecycle.harvesting(job)));
    }

    public static FrontierWorldState reducePrepared(FrontierWorldState state, SubjectId subject, PhysicalIntent intent) {
        if (intent.kind() != PhysicalIntentKind.RESOURCE_SITE_HARVEST || !subject.equals(intent.causeSubjectId())) throw new IllegalArgumentException("resource-site harvest intent is invalid");
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(intent.causeSubjectId()); validateIntent(state, lifecycle, intent);
        FrontierWorldState prepared = state.preparePhysicalIntent(intent);
        return prepared.withChanges(FrontierWorldStateUpdate.begin().fencedRecovery(
                FencedRecoveryPhysicalIntentSupport.prepared(prepared.fencedRecovery(), intent, FencedRecoveryAsset.EFFECT)));
    }

    /** Binds an already-issued COLD farmer part only after the same HOT body visibly carries it. */
    public static FrontierWorldState reduceHandProjected(FrontierWorldState state, SubjectId subject,
                                                         ResourceSiteHarvestHandProjected projected) {
        if (!subject.equals(projected.siteId())) throw new IllegalArgumentException("field hand has a foreign site owner");
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(subject);
        if (lifecycle.phase() != ResourceSitePhase.HARVESTING
                || !(lifecycle.activeWork().orElse(null) instanceof ResourceSiteHarvestJob job)
                || !job.id().equals(projected.jobId()) || !job.actorAccountId().equals(projected.actorAccountId())
                || job.progress().hasPendingCrop()) {
            throw new IllegalArgumentException("field hand projection lacks its active declared harvest job");
        }
        PhysicalIntent intent = state.physicalIntents().get(job.intentId());
        if (intent == null || intent.status() != PhysicalIntentStatus.PREPARED
                && intent.status() != PhysicalIntentStatus.RUNNING) {
            throw new IllegalArgumentException("field hand projection has no admitted harvest intent");
        }
        SceneLease lease = FrontierResourceSiteHarvestSceneSupport.requireHotLease(state, job, projected.leaseId());
        PhysicalStackAddress.ActorHand address = (PhysicalStackAddress.ActorHand) projected.hand().address();
        if (projected.actorEpoch() != lease.revision() || !address.actorId().equals(job.workerId())
                || !address.entityId().equals(lease.members().getFirst().entityId())) {
            throw new IllegalArgumentException("field hand projection has a foreign physical farmer or epoch");
        }
        ResourceFieldCycle field = state.resourceSites().cycle(subject);
        if (field.accountedCount() != job.progress().completedCropSlots()) {
            throw new IllegalArgumentException("field hand projection disagrees with completed cell work");
        }
        ResourceLot part = ResourceFieldYield.currentCarriedLot(subject, state.resourceSite(subject).settlementId(),
                field, field.accountedCount(), job.deliveredYieldQuantity()).orElseThrow(
                () -> new IllegalArgumentException("field hand projection has no positive COLD-carried part"));
        if (part.quantity() != projected.hand().quantity()) {
            throw new IllegalArgumentException("field hand projection changes the accounted crop quantity");
        }
        FungibleResourceLedger resources = state.inventory().fungibleResources();
        CustodyAccount account = resources.accounts().get(job.actorAccountId());
        if (account == null || !account.custody().equals(new ResourceCustody.Actor(job.workerId()))
                || !account.lotQuantities().equals(java.util.Map.of(part.id(), part.quantity()))
                || !account.claimQuantities().isEmpty()
                || resources.bindings().values().stream().anyMatch(binding -> binding.accountId().equals(account.id()))) {
            throw new IllegalArgumentException("field hand projection lacks its unbound exact actor part");
        }
        var bindings = FungiblePhysicalObservation.bind(resources, account.id(), projected.actorEpoch(),
                java.util.List.of(projected.hand()));
        return state.withInventory(state.inventory().withFungibleResources(resources.rebind(account.id(),
                projected.actorEpoch(), bindings)));
    }

    /** Releases exact physical hand authority and its scene in one event, never in two crash windows. */
    public static FrontierWorldState reduceHandRelease(FrontierWorldState state, SubjectId subject,
                                                       ResourceSiteHarvestHandRelease released) {
        ResourceSite site = site(state, released.siteId());
        if (!subject.equals(site.settlementId()))
            throw new IllegalArgumentException("harvest hand release has a foreign settlement owner");
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(released.siteId());
        if (lifecycle.phase() != ResourceSitePhase.HARVESTING
                || !(lifecycle.activeWork().orElse(null) instanceof ResourceSiteHarvestJob job)
                || !job.id().equals(released.jobId()) || !job.actorAccountId().equals(released.actorAccountId())
                || job.progress().hasPendingCrop())
            throw new IllegalArgumentException("harvest hand release lacks its exact active job");
        SceneLease lease = state.sceneLeases().get(released.sceneRelease().leaseId());
        if (lease == null || lease.status() != SceneLeaseStatus.DRAINING
                || !FrontierSceneBehaviors.isResourceSiteHarvest(lease)
                || !FrontierSceneBehaviors.resourceSiteHarvest(lease).siteId().equals(job.siteId())
                || !FrontierSceneBehaviors.resourceSiteHarvest(lease).jobId().equals(job.id())
                || lease.members().size() != 1 || !lease.members().getFirst().actorId().equals(job.workerId())
                || lease.revision() != released.actorEpoch())
            throw new IllegalArgumentException("harvest hand release lacks its draining worker lease");
        PhysicalStackAddress.ActorHand address = (PhysicalStackAddress.ActorHand) released.observedHand().address();
        if (!address.actorId().equals(job.workerId())
                || !address.entityId().equals(lease.members().getFirst().entityId()))
            throw new IllegalArgumentException("harvest hand release observes a foreign worker body");
        FungibleResourceLedger resources = state.inventory().fungibleResources();
        CustodyAccount account = resources.accounts().get(job.actorAccountId());
        var bound = resources.bindings().values().stream()
                .filter(binding -> binding.accountId().equals(job.actorAccountId())).toList();
        if (account == null || !account.custody().equals(new ResourceCustody.Actor(job.workerId()))
                || !account.claimQuantities().isEmpty() || bound.size() != 1
                || !bound.getFirst().address().equals(address)
                || bound.getFirst().authorityEpoch() != released.actorEpoch()
                || !bound.getFirst().itemKind().equals(released.observedHand().itemKind())
                || bound.getFirst().quantity() != released.observedHand().quantity()
                || !bound.getFirst().lotQuantities().equals(account.lotQuantities())
                || !bound.getFirst().claimQuantities().isEmpty()
                || job.carriedYieldQuantity(state.resourceSites().cycle(job.siteId()).harvestedCount())
                        != released.observedHand().quantity())
            throw new IllegalArgumentException("harvest hand release disagrees with exact bound wheat custody");
        FrontierWorldState unbound = state.withInventory(state.inventory().withFungibleResources(
                resources.releaseBindings(account.id(), released.actorEpoch())));
        return unbound.releaseSceneLease(lease.id(), released.sceneRelease().members());
    }

    public static FrontierWorldState reduceProgressed(FrontierWorldState state, SubjectId subject, ResourceSiteHarvestProgressed progressed) {
        if (!subject.equals(progressed.siteId()))
            throw new IllegalArgumentException("resource-site harvest progress has a foreign declared site owner");
        // The event already declares its site owner. Never discover authority by
        // scanning for a matching job in another site's active-work collection.
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(subject);
        ResourceSiteHarvestJob job = lifecycle.activeWork().filter(ResourceSiteHarvestJob.class::isInstance)
                .map(ResourceSiteHarvestJob.class::cast)
                .orElseThrow(() -> new IllegalArgumentException("resource-site harvest progress has no active job at its declared site owner"));
        if (!job.id().equals(progressed.jobId()))
            throw new IllegalArgumentException("resource-site harvest progress has a foreign job for its declared site owner");
        if (!ResourceSiteHarvestGoal.actorAtWorkCell(state, job))
            throw new IllegalArgumentException("resource-site harvest receipt lacks its actual farmer at the current CellId station");
        requireContinuationBinding(job, new ScheduledAction(progressed.coldScheduleId(), new SimInstant(progressed.coldDueAt()),
                0, subject, COLD_PROGRESS_KIND, 1));
        ResourceFieldCycle field = state.resourceSites().cycle(job.siteId());
        ResourceFieldLayout.Cell cell = field.layout().cells().get(job.progress().nextCropSlotIndex());
        if (progressed.epoch() != field.epoch() || progressed.layoutRevision() != field.layout().revision()
                || !progressed.cellId().equals(cell.id()))
            throw new IllegalArgumentException("resource-site harvest receipt targets a stale or foreign field cell");
        ResourceFieldCycle worked = field.worked(cell.id(), progressed.outcome());
        SurfaceAnchor workerStation = state.actorLocations().get(job.workerId()).supportingSurface();
        int nextSelected = worked.nextWorkSlotAfter(job.progress().nextCropSlotIndex()).orElse(-1);
        ResourceSiteLifecycle advanced = lifecycle.advanceHarvest(job, progressed.completedCropSlots(),
                ResourceSiteHarvestGoal.current(state, job), workerStation, nextSelected);
        ResourceSiteHarvestJob advancedJob = (ResourceSiteHarvestJob) advanced.activeWork().orElseThrow();
        if (!advancedJob.progress().complete()
                && advancedJob.carriedYieldQuantity(worked.harvestedCount()) == 64) {
            advanced = advanced.returnFullHarvestBatch(advancedJob, worked.harvestedCount());
        }
        ExactInventory inventory = state.inventory();
        ResourceSite site = site(state, job.siteId());
        SubjectId actorAccount = job.actorAccountId();
        List<SceneLease> hot = state.sceneLeases().values().stream()
                .filter(FrontierSceneBehaviors::isResourceSiteHarvest)
                .filter(lease -> lease.status() == SceneLeaseStatus.HOT
                        && FrontierSceneBehaviors.resourceSiteHarvest(lease).siteId().equals(job.siteId())
                        && FrontierSceneBehaviors.resourceSiteHarvest(lease).jobId().equals(job.id()))
                .toList();
        if (hot.size() > 1) throw new IllegalArgumentException("field crop work has competing HOT owners");
        if (hot.isEmpty() && FrontierResourceSiteHarvestSceneSupport.hasNonClosedScene(state, job))
            throw new IllegalArgumentException("COLD field crop work cannot bypass a retained physical scene");
        if (!hot.isEmpty()) {
            SceneLease lease = hot.getFirst();
            ResourceSiteHarvestProgressed.HandObservation hand = progressed.observedHand().orElseThrow(
                    () -> new IllegalArgumentException("HOT crop work has no observed physical farmer hand"));
            if (lease.members().size() != 1 || !lease.members().getFirst().actorId().equals(job.workerId())
                    || !hand.address().actorId().equals(job.workerId())
                    || !hand.address().entityId().equals(lease.members().getFirst().entityId())
                    || hand.authorityEpoch() != lease.revision()
                    || hand.quantity() != job.carriedYieldQuantity(worked.harvestedCount()))
                throw new IllegalArgumentException("HOT crop work disagrees with its exact actor hand and custody epoch");
            FungibleResourceLedger resources = inventory.fungibleResources();
            if (worked.harvestedCount() != field.harvestedCount()) {
                ResourceLot carried = ResourceFieldYield.currentCarriedLot(job.siteId(), site.settlementId(),
                        worked, worked.accountedCount(), job.deliveredYieldQuantity()).orElseThrow();
                inventory = inventory.withFungibleResources(resources.accrueObservedActorHarvestPart(carried,
                        actorAccount, job.workerId(), hand.authorityEpoch(),
                        new FungiblePhysicalObservation.Stack(hand.address(), "minecraft:wheat", hand.quantity())));
            } else if (hand.quantity() == 0) {
                if (resources.accounts().containsKey(actorAccount))
                    throw new IllegalArgumentException("zero-yield HOT hand retains an unexpected actor part");
            } else {
                CustodyAccount account = resources.accounts().get(actorAccount);
                List<PhysicalStackBinding> bindings = resources.bindings().values().stream()
                        .filter(binding -> binding.accountId().equals(actorAccount)).toList();
                if (account == null || !(account.custody() instanceof ResourceCustody.Actor actor)
                        || !actor.actorId().equals(job.workerId()) || bindings.size() != 1
                        || !bindings.getFirst().address().equals(hand.address())
                        || bindings.getFirst().authorityEpoch() != hand.authorityEpoch()
                        || bindings.getFirst().lotQuantities().values().stream().mapToInt(Integer::intValue).sum() != hand.quantity())
                    throw new IllegalArgumentException("non-yielding HOT cell lacks its retained bound farmer part");
            }
        } else {
            if (progressed.observedHand().isPresent())
                throw new IllegalArgumentException("COLD crop work cannot carry a forged Minecraft hand observation");
            if (worked.harvestedCount() != field.harvestedCount()) {
            ResourceLot carried = ResourceFieldYield.currentCarriedLot(job.siteId(), site.settlementId(),
                    worked, worked.accountedCount(), job.deliveredYieldQuantity()).orElseThrow();
            inventory = inventory.withFungibleResources(inventory.fungibleResources()
                    .accrueColdActorHarvestPart(carried, actorAccount, job.workerId()));
            }
        }
        // The last crop is not the final worker position. Return along the retained
        // corridor before output and successor admission.
        return state.withChanges(FrontierWorldStateUpdate.begin()
                .resourceSites(state.resourceSites().replace(advanced, worked)).inventory(inventory));
    }

    private static FrontierWorldState finishAfterColdReturn(FrontierWorldState state, ResourceSiteHarvestJob job,
                                                             ScheduledAction action) {
        if (!job.progress().complete()
                || !ResourceSiteHarvestGoal.actorAtDepot(state, job)
                || FrontierResourceSiteHarvestSceneSupport.hasNonClosedScene(state, job))
            throw new IllegalArgumentException("resource-site COLD terminal lacks its returned worker");
        requireContinuationBinding(job, action);
        ResourceFieldCycle worked = state.resourceSites().cycle(job.siteId());
        if (!worked.cycleAccounted())
            throw new IllegalArgumentException("field harvest cannot complete with unaccounted cells");
        ResourceSite site = site(state, job.siteId());
        ResourceFieldYield yield = ResourceFieldYield.fromCompletedCycle(job.siteId(), site.settlementId(), worked);
        ResourceFieldCycle successor = worked.nextEpoch();
        ExactInventory inventory = state.inventory();
        if (job.carriedYieldQuantity(yield.quantity()) > 0) {
            inventory = inventory.withFungibleResources(inventory.fungibleResources().deliverColdActorHarvestPart(
                    worked, site.settlementId(), job.deliveredYieldQuantity(), job.actorAccountId(), job.workerId(),
                    job.depotAccountId()));
        }
        PhysicalIntent intent = state.physicalIntents().get(job.intentId());
        if (intent == null || (intent.status() != PhysicalIntentStatus.PREPARED && intent.status() != PhysicalIntentStatus.RUNNING)) {
            throw new IllegalArgumentException("resource-site COLD completion lacks its exact admissible physical intent");
        }
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(job.siteId());
        ResourceSiteLifecycle next = terminalLifecycle(state, lifecycle, job, action);
        // Only an untouched request can be composed away.  A running effect already has a
        // physical history, so its terminal lineage stays pending until that exact receipt is
        // observed or its local recovery disposition resolves it; neither COLD nor a successor
        // may erase that fact.
        java.util.Map<PhysicalIntentId, PhysicalIntent> intents = new java.util.LinkedHashMap<>(state.physicalIntents());
        FencedRecoveryState recovery = retireSupersededPendingReceipt(state, lifecycle, intents);
        FrontierWorldStateUpdate update = FrontierWorldStateUpdate.begin()
                .resourceSites(state.resourceSites().replace(next, successor))
                .inventory(inventory)
                .physicalIntents(intents)
                .fencedRecovery(recovery);
        if (intent.status() == PhysicalIntentStatus.RUNNING) return state.withChanges(update);
        intents.remove(intent.id());
        return state.withChanges(FrontierWorldStateUpdate.begin()
                .resourceSites(state.resourceSites().replace(next, successor))
                .inventory(inventory)
                .physicalIntents(intents)
                .fencedRecovery(FencedRecoveryPhysicalIntentSupport.composed(recovery, intent, FencedRecoveryAsset.EFFECT)));
    }

    private static FrontierWorldState finishAfterColdBatchReturn(FrontierWorldState state, ResourceSiteHarvestJob job,
                                                                  ScheduledAction action) {
        if (!job.returningForBatch() || job.progress().complete()
                || FrontierResourceSiteHarvestSceneSupport.hasNonClosedScene(state, job)
                || ReferenceContainerCustody.hasLiveCustody(state, job.outputSlot().containerId()))
            throw new IllegalArgumentException("COLD field batch has no exclusively returned farmer and depot");
        requireContinuationBinding(job, action);
        ResourceFieldCycle cycle = state.resourceSites().cycle(job.siteId());
        if (job.carriedYieldQuantity(cycle.harvestedCount()) != 64 || !ResourceSiteHarvestPlanning.batchDeliveryCapacityAvailable(state, job))
            throw new IllegalArgumentException("COLD field batch lacks a full hand or next depot reservation");
        ResourceSite site = site(state, job.siteId());
        ActorLocation actor = state.actorLocations().get(job.workerId());
        if (actor == null || !ResourceSiteHarvestGoal.actorAtDepot(state, job))
            throw new IllegalArgumentException("COLD field batch lacks its retained depot station");
        InventoryCustody.ContainerSlot nextSlot = job.batchSuccessorSlot().orElseGet(() ->
                new InventoryCustody.ContainerSlot(job.outputSlot().containerId(),
                        state.firstFreeContainerSlot(job.outputSlot().containerId()).orElseThrow()));
        FungibleResourceLedger resources = state.inventory().fungibleResources().deliverColdActorHarvestPart(
                cycle, site.settlementId(), job.deliveredYieldQuantity(), job.actorAccountId(), job.workerId(), job.depotAccountId());
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(job.siteId());
        return state.withChanges(FrontierWorldStateUpdate.begin()
                .resourceSites(state.resourceSites().replace(lifecycle.deliverFullHarvestBatch(job, nextSlot,
                        cycle.harvestedCount())))
                .inventory(state.inventory().withFungibleResources(resources)));
    }

    /**
     * A renewable site can begin its exact successor while the preceding HOT effect remains
     * physically unresolved.  If that successor reaches its own COLD terminal boundary first,
     * the one retained lineage slot must not silently orphan the older RUNNING intent.  Retire
     * that older effect as one bounded local ambiguity: its immutable recovery tombstone rejects
     * a later stale replica without replaying either wheat result, while the new terminal keeps
     * its own exact receipt authority.
     */
    private static FencedRecoveryState retireSupersededPendingReceipt(FrontierWorldState state,
                                                                        ResourceSiteLifecycle lifecycle,
                                                                        java.util.Map<PhysicalIntentId, PhysicalIntent> intents) {
        ResourceSiteHarvestLineage predecessor = lifecycle.harvestLineage()
                .filter(ResourceSiteHarvestLineage::receiptPending).orElse(null);
        if (predecessor == null) return state.fencedRecovery();
        PhysicalIntent prior = intents.remove(predecessor.predecessorIntentId());
        if (prior == null || prior.status() != PhysicalIntentStatus.RUNNING
                || !prior.causeSubjectId().equals(lifecycle.siteId())) {
            throw new IllegalArgumentException("resource-site successor terminal lacks its exact pending predecessor receipt");
        }
        return FencedRecoveryPhysicalIntentSupport.transition(state.fencedRecovery(), prior, PhysicalIntentStatus.CONFLICTED,
                FencedRecoveryAsset.EFFECT);
    }

    private static ResourceSiteLifecycle terminalLifecycle(FrontierWorldState state, ResourceSiteLifecycle advanced,
                                                            ResourceSiteHarvestJob job, ScheduledAction action) {
        PhysicalIntent intent = state.physicalIntents().get(job.intentId());
        if (intent == null || (intent.status() != PhysicalIntentStatus.PREPARED && intent.status() != PhysicalIntentStatus.RUNNING)) {
            throw new IllegalArgumentException("resource-site COLD terminal lifecycle lacks its exact admissible physical intent");
        }
        requireContinuationBinding(job, action);
        ResourceSiteHarvestJob completed = advanced.activeWork().filter(ResourceSiteHarvestJob.class::isInstance)
                .map(ResourceSiteHarvestJob.class::cast).orElseThrow();
        ActorLocation actor = state.actorLocations().get(completed.workerId());
        if (actor == null || !ResourceSiteHarvestGoal.actorAtDepot(state, completed))
            throw new IllegalArgumentException("resource-site terminal has no legal returned farmer body");
        return advanced.harvestedDeferred(completed, intent.status() == PhysicalIntentStatus.PREPARED,
                ResourceSiteHarvestCausality.captured(state, completed, action, intent.status() == PhysicalIntentStatus.RUNNING), actor.body());
    }

    public static FrontierWorldState reduceCropPrepared(FrontierWorldState state, SubjectId subject, ResourceSiteHarvestCropPrepared prepared) {
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(subject);
        ResourceSiteHarvestJob job = lifecycle.activeWork().filter(ResourceSiteHarvestJob.class::isInstance)
                .map(ResourceSiteHarvestJob.class::cast)
                .orElseThrow(() -> new IllegalArgumentException("resource-site harvest crop preparation has no active job at its declared site owner"));
        if (!job.id().equals(prepared.jobId()))
            throw new IllegalArgumentException("resource-site harvest crop preparation has a foreign job for its declared site owner");
        if (!ResourceSiteHarvestGoal.actorAtWorkCell(state, job))
            throw new IllegalArgumentException("resource-site harvest crop preparation requires its actual farmer at the current CellId station");
        return state.withResourceSites(state.resourceSites().replace(lifecycle.prepareHarvestCrop(job, prepared.cropSlotIndex(),
                ResourceSiteHarvestGoal.current(state, job), state.actorLocations().get(job.workerId()).supportingSurface())));
    }

    /** Old persisted route-renewal events cannot mutate a goal-owned farmer. */
    public static FrontierWorldState reduceSegmentRenewed(FrontierWorldState state, SubjectId subject,
                                                          ResourceSiteHarvestSegmentRenewed renewed) {
        throw new IllegalArgumentException("field route segments are retired by goal navigation");
    }

    /** Only the selected outstanding CellId may be skipped; completed count is not a slot. */
    public static List<ResourceFieldLayout.CellId> blockedPrefix(ResourceFieldCycle cycle, int firstSlot) {
        var cells = cycle.layout().cells();
        if (firstSlot < 0 || firstSlot >= cells.size()) throw new IllegalArgumentException("blocked field prefix starts outside layout");
        ResourceFieldLayout.CellId id = cells.get(firstSlot).id();
        ResourceFieldCycle.CellState condition = cycle.cell(id);
        return cycle.pendingPlayerBreaks().containsKey(id) || condition.accounted()
                || condition.crop() != ResourceFieldCycle.Crop.OBSTRUCTED ? List.of() : List.of(id);
    }

    public static boolean blockedPrefixMeetsPendingPlayerBreak(ResourceFieldCycle cycle, int firstSlot,
                                                                List<ResourceFieldLayout.CellId> prefix) {
        if (prefix.isEmpty()) return false;
        ResourceFieldCycle worked = cycle.skipBlocked(prefix.getFirst());
        int next = worked.nextWorkSlotAfter(firstSlot).orElse(-1);
        return next >= 0 && cycle.pendingPlayerBreaks().containsKey(cycle.layout().cells().get(next).id());
    }

    /** The next semantic goal after an observed blocked prefix, not an old route waypoint. */
    public static ResourceSiteHarvestGoal blockedPrefixContinuationGoal(FrontierWorldState state,
                                                                        ResourceSiteHarvestJob job,
                                                                        List<ResourceFieldLayout.CellId> prefix) {
        ResourceFieldCycle cycle = state.resourceSites().cycle(job.siteId());
        if (prefix.size() != 1 || !cycle.layout().cells().get(job.progress().nextCropSlotIndex()).id().equals(prefix.getFirst()))
            throw new IllegalArgumentException("blocked continuation has no selected CellId");
        ResourceFieldCycle worked = cycle.skipBlocked(prefix.getFirst());
        int next = worked.nextWorkSlotAfter(job.progress().nextCropSlotIndex()).orElse(cycle.layout().cells().size());
        return ResourceSiteHarvestGoal.forSlot(state, job, next);
    }

    /** Pure bounded route probe shared by skip and exact clearance; never changes canonical progress. */
    public static void blockedPrefixContinuation(FrontierWorldState state,
                                                                               SubjectId subject,
                                                                               ResourceSiteHarvestJob job,
                                                                               List<ResourceFieldLayout.CellId> prefix) {
        ResourceFieldCycle cycle = state.resourceSites().cycle(subject);
        ActorLocation actor = state.actorLocations().get(job.workerId());
        if (actor == null || actor.condition().status() != ActorLifeStatus.ALIVE)
            throw new IllegalArgumentException("blocked-cell continuation has no retained farmer body");
        int nextSlot = blockedPrefixContinuationGoal(state, job, prefix).nextWorkSlot();
        ResourceFieldCycle worked = cycle.skipBlocked(prefix.getFirst());
        int carried = job.carriedYieldQuantity(cycle.harvestedCount());
        if (nextSlot < cycle.layout().cells().size() && carried >= 64)
            throw new IllegalArgumentException("field work continuation cannot exceed its physical hand capacity");
        try {
            ResourceSiteLifecycle lifecycle = state.resourceSites().site(subject);
            ResourceSiteLifecycle advanced = lifecycle.skipSelectedHarvestCell(job,
                    nextSlot == cycle.layout().cells().size() ? -1 : nextSlot, cycle.harvestedCount());
            ResourceSiteHarvestJob nextJob = (ResourceSiteHarvestJob) advanced.activeWork().orElseThrow();
            ResourceSiteHarvestKnownNavigation.path(state.withResourceSites(
                    state.resourceSites().replace(advanced, worked)), nextJob);
        } catch (ResourceSiteHarvestKnownNavigation.KnowledgeUnavailable noRoute) {
            throw new ContinuationUnavailable(noRoute);
        }
    }

    public static FrontierWorldState reduceBlockedCellSkipped(FrontierWorldState state, SubjectId subject,
                                                              ResourceSiteHarvestBlockedCellSkipped skipped) {
        if (!subject.equals(skipped.siteId()))
            throw new IllegalArgumentException("blocked field cell has a foreign site owner");
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(subject);
        ResourceSiteHarvestJob job = lifecycle.activeWork().filter(ResourceSiteHarvestJob.class::isInstance)
                .map(ResourceSiteHarvestJob.class::cast).orElseThrow(
                        () -> new IllegalArgumentException("blocked field cell has no active farmer"));
        ResourceFieldCycle cycle = state.resourceSites().cycle(subject);
        if (lifecycle.phase() != ResourceSitePhase.HARVESTING || !job.id().equals(skipped.jobId())
                || !job.workerId().equals(skipped.workerId()) || job.progress().complete()
                || job.progress().hasPendingCrop() || job.returningForBatch()
                || state.resourceSites().hasPendingWorldChange(subject)
                || skipped.layoutRevision() != cycle.layout().revision())
            throw new IllegalArgumentException("blocked field cell has a stale or physically unresolved work boundary");
        ResourceFieldLayout.Cell selected = cycle.layout().cells().get(job.progress().nextCropSlotIndex());
        if (!skipped.cellIds().equals(List.of(selected.id()))
                || cycle.cell(selected.id()).accounted()
                || cycle.cell(selected.id()).crop() != ResourceFieldCycle.Crop.OBSTRUCTED
                    && !cycle.cell(selected.id()).workAccessBlocked()
                || cycle.pendingPlayerBreaks().containsKey(selected.id()))
            throw new IllegalArgumentException("blocked field skip lacks its exact selected target obstruction");
        if (job.navigationBlock().isPresent()
                && !job.navigationBlock().orElseThrow().target().equals(ResourceSiteHarvestGoal.current(state, job).representative()))
            throw new IllegalArgumentException("blocked field skip has a different retained movement goal");
        requireContinuationBinding(job, new ScheduledAction(skipped.coldScheduleId(),
                new SimInstant(skipped.coldDueAt()), 0, subject, COLD_PROGRESS_KIND, 1));
        if (skipped.hotLeaseId().isPresent()) {
            FrontierResourceSiteHarvestSceneSupport.requireHotLease(state, job, skipped.hotLeaseId().orElseThrow());
        } else if (FrontierResourceSiteHarvestSceneSupport.hasNonClosedScene(state, job)
                || !FrontierSceneAdmission.available(state, List.of(job.workerId()))) {
            throw new IllegalArgumentException("COLD blocked-cell continuation cannot bypass a physical farmer");
        }
        ResourceFieldCycle worked = cycle.skipBlocked(selected.id());
        ActorLocation actor = state.actorLocations().get(job.workerId());
        if (actor == null || actor.condition().status() != ActorLifeStatus.ALIVE)
            throw new IllegalArgumentException("blocked field skip has no retained worker body");
        int nextSelected = worked.nextWorkSlotAfter(job.progress().nextCropSlotIndex()).orElse(-1);
        ResourceSiteLifecycle advanced = lifecycle.skipSelectedHarvestCell(job, nextSelected, cycle.harvestedCount());
        return state.withResourceSites(state.resourceSites().replace(advanced, worked));
    }

    public static FrontierWorldState reduceRouteBlocked(FrontierWorldState state, SubjectId subject,
                                                         ResourceSiteHarvestRouteBlocked blocked) {
        if (!subject.equals(blocked.siteId())) throw new IllegalArgumentException("farmer route block has foreign site owner");
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(subject);
        ResourceSiteHarvestJob job = lifecycle.activeWork().filter(ResourceSiteHarvestJob.class::isInstance)
                .map(ResourceSiteHarvestJob.class::cast).orElseThrow(
                        () -> new IllegalArgumentException("farmer route block has no active job"));
        if (lifecycle.phase() != ResourceSitePhase.HARVESTING || !job.id().equals(blocked.jobId())
                || !job.workerId().equals(blocked.workerId()) || job.navigationBlock().isPresent()
                || blocked.block().layoutRevision() != state.resourceSites().cycle(subject).layout().revision())
            throw new IllegalArgumentException("farmer route block has stale job, target or layout");
        if (blocked.block().reason() != ResourceSiteHarvestNavigationBlock.Reason.CONTINUATION_UNAVAILABLE
                && !blocked.block().target().equals(ResourceSiteHarvestGoal.current(state, job).representative()))
            throw new IllegalArgumentException("farmer goal block has a foreign semantic target");
        if (blocked.block().reason() == ResourceSiteHarvestNavigationBlock.Reason.CONTINUATION_UNAVAILABLE) {
            ResourceFieldCycle cycle = state.resourceSites().cycle(subject);
            if (state.resourceSites().hasPendingWorldChange(subject))
                throw new IllegalArgumentException("farmer continuation block cannot overtake pending physical field change");
            List<ResourceFieldLayout.CellId> prefix = blockedPrefix(cycle, job.progress().nextCropSlotIndex());
            if (prefix.isEmpty()
                    || blockedPrefixMeetsPendingPlayerBreak(cycle, job.progress().nextCropSlotIndex(), prefix)
                    || !blocked.block().target().equals(blockedPrefixContinuationGoal(state, job, prefix).representative()))
                throw new IllegalArgumentException("farmer continuation block lacks its exact observed work prefix");
            boolean unavailable = false;
            try {
                blockedPrefixContinuation(state, subject, job, prefix);
            } catch (ContinuationUnavailable noRoute) {
                unavailable = true;
            }
            if (!unavailable) throw new IllegalArgumentException("farmer continuation is available and cannot be blocked");
        }
        requireContinuationBinding(job, new ScheduledAction(blocked.coldScheduleId(),
                new SimInstant(blocked.coldDueAt()), 0, subject, COLD_PROGRESS_KIND, 1));
        FrontierResourceSiteHarvestSceneSupport.requireHotLease(state, job, blocked.leaseId());
        ActorLocation actor = state.actorLocations().get(job.workerId());
        if (actor == null || actor.condition().status() != ActorLifeStatus.ALIVE)
            throw new IllegalArgumentException("farmer route block lacks the retained worker station");
        return state.withResourceSites(state.resourceSites().replace(lifecycle.blockHarvestRoute(job, blocked.block())));
    }

    public static FrontierWorldState reduceRouteCleared(FrontierWorldState state, SubjectId subject,
                                                         ResourceSiteHarvestRouteCleared cleared) {
        if (!subject.equals(cleared.siteId())) throw new IllegalArgumentException("farmer route clearance has foreign site owner");
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(subject);
        ResourceSiteHarvestJob job = lifecycle.activeWork().filter(ResourceSiteHarvestJob.class::isInstance)
                .map(ResourceSiteHarvestJob.class::cast).orElseThrow(
                        () -> new IllegalArgumentException("farmer route clearance has no active job"));
        if (lifecycle.phase() != ResourceSitePhase.HARVESTING || !job.id().equals(cleared.jobId())
                || !job.workerId().equals(cleared.workerId())
                || cleared.expected().layoutRevision() != state.resourceSites().cycle(subject).layout().revision())
            throw new IllegalArgumentException("farmer route clearance has stale job or layout");
        if (cleared.expected().reason() != ResourceSiteHarvestNavigationBlock.Reason.CONTINUATION_UNAVAILABLE
                && !cleared.expected().target().equals(ResourceSiteHarvestGoal.current(state, job).representative()))
            throw new IllegalArgumentException("farmer goal clearance has a foreign semantic target");
        if (cleared.expected().reason() == ResourceSiteHarvestNavigationBlock.Reason.CONTINUATION_UNAVAILABLE) {
            ResourceFieldCycle cycle = state.resourceSites().cycle(subject);
            if (state.resourceSites().hasPendingWorldChange(subject))
                throw new IllegalArgumentException("farmer continuation clearance cannot overtake pending physical field change");
            List<ResourceFieldLayout.CellId> prefix = blockedPrefix(cycle, job.progress().nextCropSlotIndex());
            if (blockedPrefixMeetsPendingPlayerBreak(cycle, job.progress().nextCropSlotIndex(), prefix))
                throw new IllegalArgumentException("farmer continuation clearance would overtake a player break");
            if (!prefix.isEmpty()
                    && cleared.expected().target().equals(blockedPrefixContinuationGoal(state, job, prefix).representative()))
                blockedPrefixContinuation(state, subject, job, prefix);
        }
        requireContinuationBinding(job, new ScheduledAction(cleared.coldScheduleId(),
                new SimInstant(cleared.coldDueAt()), 0, subject, COLD_PROGRESS_KIND, 1));
        FrontierResourceSiteHarvestSceneSupport.requireHotLease(state, job, cleared.leaseId());
        ActorLocation actor = state.actorLocations().get(job.workerId());
        if (actor == null || actor.condition().status() != ActorLifeStatus.ALIVE)
            throw new IllegalArgumentException("farmer route clearance would change a displaced worker");
        return state.withResourceSites(state.resourceSites().replace(lifecycle.clearHarvestRouteBlock(job, cleared.expected())));
    }

    /** Pins next-batch capacity before either the farmer hand or chest may be written. */
    public static FrontierWorldState reduceBatchPrepared(FrontierWorldState state, SubjectId subject,
                                                         ResourceSiteHarvestBatchPrepared prepared) {
        if (!subject.equals(prepared.siteId())) throw new IllegalArgumentException("field batch reservation has a foreign site owner");
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(subject);
        ResourceSiteHarvestJob job = lifecycle.activeWork().filter(ResourceSiteHarvestJob.class::isInstance)
                .map(ResourceSiteHarvestJob.class::cast).orElseThrow(
                        () -> new IllegalArgumentException("field batch reservation has no active worker"));
        PhysicalIntent intent = state.physicalIntents().get(job.intentId());
        ResourceFieldCycle cycle = state.resourceSites().cycle(subject);
        if (lifecycle.phase() != ResourceSitePhase.HARVESTING || !job.id().equals(prepared.jobId())
                || !job.returningForBatch()
                || job.deliveredYieldQuantity() != prepared.deliveredYieldBefore()
                || job.batchSuccessorSlot().isPresent() || job.carriedYieldQuantity(cycle.harvestedCount()) != 64
                || intent == null || intent.status() != PhysicalIntentStatus.RUNNING)
            throw new IllegalArgumentException("field batch reservation lacks its exact full HOT hand");
        FrontierResourceSiteHarvestSceneSupport.requireHotLease(state, job, prepared.leaseId());
        int expectedSlot = state.firstFreeContainerSlot(job.outputSlot().containerId()).orElseThrow(
                () -> new IllegalArgumentException("field batch has no vacant successor depot slot"));
        if (!prepared.nextOutputSlot().equals(new InventoryCustody.ContainerSlot(job.outputSlot().containerId(), expectedSlot)))
            throw new IllegalArgumentException("field batch reservation does not name the deterministic next vacant slot");
        ActorLocation actor = state.actorLocations().get(job.workerId());
        if (actor == null || !ResourceSiteHarvestGoal.actorAtDepot(state, job))
            throw new IllegalArgumentException("field batch reservation lacks its returned farmer station");
        return state.withResourceSites(state.resourceSites().replace(lifecycle.reserveHarvestBatchSuccessor(job,
                prepared.nextOutputSlot(), cycle.harvestedCount())));
    }

    /** Historical cursor events are rejected at the new goal-only schema boundary. */
    public static FrontierWorldState reduceColdTraversalAdvanced(FrontierWorldState state, SubjectId subject,
                                                                  ResourceSiteHarvestColdTraversalAdvanced advanced) {
        throw new IllegalArgumentException("field cursor traversal is retired by goal navigation");
    }

    public static FrontierWorldState reduceReturned(FrontierWorldState state, SubjectId subject,
                                                    ResourceSiteHarvestReturned returned) {
        ResourceSiteHarvestJob job = activeJobAtSite(state, subject);
        if (job == null || !job.id().equals(returned.jobId()) || !job.workerId().equals(returned.workerId()))
            throw new IllegalArgumentException("resource-site COLD terminal has no exact returned job");
        ActorLocation actor = state.actorLocations().get(job.workerId());
        if (actor == null || !ResourceSiteHarvestGoal.actorAtDepot(state, job))
            throw new IllegalArgumentException("resource-site COLD terminal lacks its returned worker body");
        ScheduledAction action = new ScheduledAction(returned.coldScheduleId(), new SimInstant(returned.coldDueAt()),
                0, subject, COLD_PROGRESS_KIND, 1);
        return job.returningForBatch() ? finishAfterColdBatchReturn(state, job, action)
                : finishAfterColdReturn(state, job, action);
    }

    /**
     * Commits a loaded checkpoint from one exact HOT lease.  The same semantic job transition as
     * COLD is applied, but its observed body is additionally the lease recovery checkpoint and
     * the only canonical actor position.  No partial job/lease/actor state can be installed.
     */
    public static FrontierWorldState reduceHotTraversalAdvanced(FrontierWorldState state, SubjectId subject,
                                                                 ResourceSiteHarvestHotTraversalAdvanced advanced) {
        throw new IllegalArgumentException("field cursor traversal is retired by goal navigation");
    }

    /** A goal arrival changes only the worker's location/work gate, never CellId output. */
    public static FrontierWorldState reduceHotGoalArrived(FrontierWorldState state, SubjectId subject,
                                                          ResourceSiteHarvestHotGoalArrived arrived) {
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(subject);
        ResourceSiteHarvestJob job = lifecycle.activeWork().filter(ResourceSiteHarvestJob.class::isInstance)
                .map(ResourceSiteHarvestJob.class::cast).orElseThrow(
                        () -> new IllegalArgumentException("field HOT goal arrival has no active job"));
        if (lifecycle.phase() != ResourceSitePhase.HARVESTING || !subject.equals(job.siteId())
                || !job.id().equals(arrived.jobId()) || !job.workerId().equals(arrived.workerId()))
            throw new IllegalArgumentException("field HOT goal arrival has a foreign job owner");
        ResourceSiteHarvestGoal goal = ResourceSiteHarvestGoal.current(state, job);
        if (arrived.layoutRevision() != goal.layoutRevision()
                || arrived.nextWorkSlot() != goal.nextWorkSlot() || arrived.kind() != goal.kind())
            throw new IllegalArgumentException("field HOT goal arrival has a stale semantic target");
        return FrontierResourceSiteHarvestSceneSupport.arriveGoal(state, lifecycle, job, goal,
                arrived.leaseId(), arrived.observedWorker());
    }

    public static FrontierWorldState reduceHotTransitObserved(FrontierWorldState state, SubjectId subject,
                                                               ResourceSiteHarvestHotTransitObserved observed) {
        ResourceSiteHarvestJob job = activeJobAtSite(state, subject);
        if (job == null || !job.id().equals(observed.jobId()) || !job.workerId().equals(observed.workerId())
                || state.resourceSites().site(subject).phase() != ResourceSitePhase.HARVESTING)
            throw new IllegalArgumentException("interrupted HOT field goal has no current farmer");
        ResourceSiteHarvestGoal goal = ResourceSiteHarvestGoal.current(state, job);
        if (observed.layoutRevision() != goal.layoutRevision() || observed.nextWorkSlot() != goal.nextWorkSlot()
                || observed.kind() != goal.kind() || goal.arrivedAt(observed.observedWorker().supportingSurface()))
            throw new IllegalArgumentException("interrupted HOT field goal has a stale or arrived target");
        return FrontierResourceSiteHarvestSceneSupport.observeTransit(state, job, observed.leaseId(), observed.observedWorker());
    }

    /** Retains actual COLD travel without promoting an intermediate support into a work cursor. */
    public static FrontierWorldState reduceColdGoalAdvanced(FrontierWorldState state, SubjectId subject,
                                                             ResourceSiteHarvestColdGoalAdvanced advanced) {
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(subject);
        ResourceSiteHarvestJob job = lifecycle.activeWork().filter(ResourceSiteHarvestJob.class::isInstance)
                .map(ResourceSiteHarvestJob.class::cast).orElseThrow(
                        () -> new IllegalArgumentException("COLD field goal has no active job"));
        if (lifecycle.phase() != ResourceSitePhase.HARVESTING || !subject.equals(job.siteId())
                || !job.id().equals(advanced.jobId()) || !job.workerId().equals(advanced.workerId())
                || job.navigationBlock().isPresent()
                || !advanced.coldScheduleId().equals(coldProgress(job, advanced.coldDueAt()).id())
                || FrontierResourceSiteHarvestSceneSupport.hasNonClosedScene(state, job)
                || !FrontierSceneAdmission.available(state, List.of(job.workerId())))
            throw new IllegalArgumentException("COLD field goal has no exclusive current worker and schedule");
        ResourceSiteHarvestGoal goal = ResourceSiteHarvestGoal.current(state, job);
        if (goal.layoutRevision() != advanced.layoutRevision()
                || goal.nextWorkSlot() != advanced.nextWorkSlot() || goal.kind() != advanced.kind())
            throw new IllegalArgumentException("COLD field goal has a stale work target");
        List<SurfaceAnchor> known = ResourceSiteHarvestKnownNavigation.path(state, job);
        BodyPosition next = known.get(Math.min(1, known.size() - 1)).standingBody();
        if (!next.equals(advanced.nextBody()))
            throw new IllegalArgumentException("COLD field goal step is not the next known support");
        ActorLocation actor = state.actorLocations().get(job.workerId());
        boolean arrived = goal.arrivedAt(advanced.nextBody().supportingSurface());
        if (actor == null || (actor.body().equals(next)
                && (!arrived || job.arriveAtSemanticGoal(goal).equals(job))))
            throw new IllegalArgumentException("COLD field goal has no forward travel or new arrival");
        java.util.Map<SubjectId, ActorLocation> actors = new java.util.LinkedHashMap<>(state.actorLocations());
        actors.put(job.workerId(), actor.withBody(next));
        return state.withChanges(FrontierWorldStateUpdate.begin().actorLocations(actors)
                .resourceSites(arrived ? state.resourceSites().replace(lifecycle.arriveHarvestGoal(job, goal))
                        : state.resourceSites()));
    }

    /** A COLD planner failure is retained as a typed local pause, never repeated as silent progress. */
    public static FrontierWorldState reduceColdGoalHeld(FrontierWorldState state, SubjectId subject,
                                                        ResourceSiteHarvestColdGoalHeld held) {
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(subject);
        ResourceSiteHarvestJob job = lifecycle.activeWork().filter(ResourceSiteHarvestJob.class::isInstance)
                .map(ResourceSiteHarvestJob.class::cast).orElseThrow(
                        () -> new IllegalArgumentException("COLD goal hold has no active field job"));
        if (!subject.equals(held.siteId()) || lifecycle.phase() != ResourceSitePhase.HARVESTING
                || !job.id().equals(held.jobId()) || !job.workerId().equals(held.workerId())
                || job.navigationBlock().isPresent()
                || FrontierResourceSiteHarvestSceneSupport.hasNonClosedScene(state, job)
                || !FrontierSceneAdmission.available(state, List.of(job.workerId())))
            throw new IllegalArgumentException("COLD goal hold has no exclusive current field owner");
        ResourceSiteHarvestGoal goal;
        List<ResourceFieldLayout.CellId> blockedPrefix = List.of();
        if (held.reason() == ResourceSiteHarvestNavigationBlock.Reason.CONTINUATION_UNAVAILABLE) {
            ResourceFieldCycle cycle = state.resourceSites().cycle(subject);
            blockedPrefix = blockedPrefix(cycle, job.progress().nextCropSlotIndex());
            if (state.resourceSites().hasPendingWorldChange(subject)
                    || blockedPrefix.isEmpty()
                    || blockedPrefixMeetsPendingPlayerBreak(cycle, job.progress().nextCropSlotIndex(), blockedPrefix))
                throw new IllegalArgumentException("COLD continuation hold lacks a stable blocked work prefix");
            goal = blockedPrefixContinuationGoal(state, job, blockedPrefix);
        } else {
            goal = ResourceSiteHarvestGoal.current(state, job);
        }
        if (goal.layoutRevision() != held.layoutRevision() || goal.nextWorkSlot() != held.nextWorkSlot()
                || goal.kind() != held.kind() || !goal.representative().equals(held.target()))
            throw new IllegalArgumentException("COLD goal hold names a foreign semantic destination");
        requireContinuationBinding(job, new ScheduledAction(held.coldScheduleId(),
                new SimInstant(held.coldDueAt()), 0, subject, COLD_PROGRESS_KIND, 1));
        boolean unavailable = false;
        if (held.reason() == ResourceSiteHarvestNavigationBlock.Reason.CONTINUATION_UNAVAILABLE) {
            try { blockedPrefixContinuation(state, subject, job, blockedPrefix); }
            catch (ContinuationUnavailable noRoute) { unavailable = true; }
        } else {
            try { ResourceSiteHarvestKnownNavigation.path(state, job); }
            catch (ResourceSiteHarvestKnownNavigation.KnowledgeUnavailable noKnowledge) { unavailable = true; }
        }
        if (!unavailable) throw new IllegalArgumentException("COLD goal hold cannot suppress an available path");
        var block = new ResourceSiteHarvestNavigationBlock(goal.representative(),
                goal.layoutRevision(), held.reason());
        return state.withResourceSites(state.resourceSites().replace(lifecycle.blockHarvestRoute(job, block)));
    }

    /** The common scene hand-off captures the actual farmer body; it never rebases the work goal. */
    public static FrontierWorldState rebaseForAmbientHandoff(FrontierWorldState state, SubjectId subject,
                                                              ResourceSiteHarvestSceneLeaseHandoff handoff) {
        ResourceSiteHarvestJob job = FrontierResourceSiteHarvestSceneSupport.require(state, FrontierSceneBehaviors.resourceSiteHarvest(handoff.lease()));
        ResourceSite site = site(state, job.siteId());
        if (!subject.equals(site.settlementId()) || handoff.ambientMembers().size() != 1
                || !handoff.ambientMembers().getFirst().actorId().equals(job.workerId())) {
            throw new IllegalArgumentException("resource-site field-work hand-off must capture its one exact farmer");
        }
        SceneMemberPosition capture = handoff.ambientMembers().getFirst();
        ActorLocation current = state.actorLocations().get(job.workerId());
        if (current == null || current.condition().status() != ActorLifeStatus.ALIVE) {
            throw new IllegalArgumentException("resource-site field-work hand-off has no living farmer");
        }
        FrontierWorldStateSupport.requirePosition(state.bootstrap().bounds(), capture.body().supportingSurface().support());
        // The common hand-off atomically replaces the prior ambient body with this physical
        // capture.  Recompiling a persisted corridor here would silently choose a new work
        // journey and could rewind COLD travel before the first crop.
        return state;
    }

    public static List<ProposedEvent> planTransition(FrontierWorldState state, PhysicalIntent intent, PhysicalIntentTransition transition, long now) {
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(intent.causeSubjectId());
        if (lifecycle.harvestLineage().filter(ResourceSiteHarvestLineage::receiptPending)
                .filter(lineage -> lineage.predecessorIntentId().equals(intent.id())).isPresent()) {
            if (transition.status() == PhysicalIntentStatus.RUNNING) {
                FencedRecoveryPhysicalIntentSupport.requirePreparedExecutionAuthority(state.fencedRecovery(), intent,
                        FencedRecoveryAsset.EFFECT);
                validateRunningTransition(state, intent); return List.of(new ProposedEvent(lifecycle.siteId(), transition));
            }
            if (transition.status() == PhysicalIntentStatus.CONFIRMED && intent.status() == PhysicalIntentStatus.RUNNING) {
                return List.of(new ProposedEvent(lifecycle.siteId(), transition));
            }
            if (transition.status() == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART
                    && (intent.status() == PhysicalIntentStatus.PREPARED || intent.status() == PhysicalIntentStatus.RUNNING)) {
                // COLD has already closed its task, output custody and exact-successor
                // eligibility. A later missing or foreign physical receipt therefore resolves
                // only this retained materialization owner into its typed local conflict.
                return List.of(new ProposedEvent(lifecycle.siteId(), transition));
            }
            throw new IllegalArgumentException("resource-site deferred harvest receipt has an invalid transition");
        }
        validateBinding(state, lifecycle, intent);
        ResourceSiteHarvestJob job = harvest(lifecycle, intent.id()); StrategicTask task = task(state, job.taskId(), StrategicTaskStatus.ACTIVE);
        if (transition.status() == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART) {
            return List.of(new ProposedEvent(lifecycle.siteId(), transition), transition(task, StrategicTaskStatus.BLOCKED));
        }
        if (transition.status() == PhysicalIntentStatus.RUNNING) validateRunningTransition(state, intent);
        if (transition.status() == PhysicalIntentStatus.CONFIRMED && intent.status() != PhysicalIntentStatus.RUNNING) {
            throw new IllegalArgumentException("resource-site harvest confirmation requires its durable running boundary");
        }
        if (transition.status() != PhysicalIntentStatus.CONFIRMED) return List.of(new ProposedEvent(lifecycle.siteId(), transition));
        if (!ResourceSiteHarvestGoal.actorAtDepot(state, job))
            throw new IllegalArgumentException("resource-site harvest output cannot complete before the worker reaches its depot");
        ResourceSiteLifecycle next = lifecycle.harvestedAt(ResourceSiteHarvestGoal.current(state, job),
                state.actorLocations().get(job.workerId()).body());
        List<ProposedEvent> events = new java.util.ArrayList<>();
        events.add(new ProposedEvent(lifecycle.siteId(), transition));
        ResourceSiteHarvestDeliveryObservation receipt = transition.observation()
                .filter(ResourceSiteHarvestDeliveryObservation.class::isInstance)
                .map(ResourceSiteHarvestDeliveryObservation.class::cast).orElseThrow(
                        () -> new IllegalArgumentException("HOT harvest confirmation lacks its exact depot receipt"));
        if (receipt.harvestedQuantity() > 0)
            events.add(ResourceSiteHarvestPlanning.stockWake(state, job, -1, now));
        events.add(transition(task, StrategicTaskStatus.COMPLETED));
        events.add(new ProposedEvent(lifecycle.siteId(), new ScheduleEffect.Cancelled(coldProgress(job, now).id())));
        events.add(new ProposedEvent(lifecycle.siteId(), new ScheduleEffect.Created(ResourceSiteProcess.nextGrowth(next, Math.addExact(now,
                state.bootstrap().ruleset().cadence().resourceGrowthStageInterval())))));
        return List.copyOf(events);
    }

    private static boolean hotLeaseOwnsCropWork(FrontierWorldState state, ResourceSiteHarvestJob job) {
        return state.sceneLeases().values().stream().filter(FrontierSceneBehaviors::isResourceSiteHarvest)
                .anyMatch(lease -> lease.status() == SceneLeaseStatus.HOT
                        && FrontierSceneBehaviors.resourceSiteHarvest(lease).siteId().equals(job.siteId())
                        && FrontierSceneBehaviors.resourceSiteHarvest(lease).jobId().equals(job.id()));
    }

    /**
     * A RUNNING harvest intent has exactly two lawful owners.  HOT may admit the next observed
     * crop while it owns the farmer body; after COLD has durably completed that same retained
     * 64-slot cursor, COLD may admit only the deferred physical receipt.  The latter never
     * creates a body, route, output, or alternate worker.
     */
    public static void validateRunningTransition(FrontierWorldState state, PhysicalIntent intent) {
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(intent.causeSubjectId());
        if (lifecycle.harvestLineage().filter(ResourceSiteHarvestLineage::receiptPending)
                .filter(lineage -> lineage.predecessorIntentId().equals(intent.id())).isPresent()) {
            ActorLocation actor = state.actorLocations().get(lifecycle.harvestLineage().orElseThrow().workerId());
            if (actor == null || actor.condition().status() != ActorLifeStatus.ALIVE
                    || !actor.body().equals(lifecycle.harvestLineage().orElseThrow().terminalBody())) {
                throw new IllegalArgumentException("resource-site deferred terminal admission has no living exact farmer");
            }
            return;
        }
        validateBinding(state, lifecycle, intent);
        ResourceSiteHarvestJob job = harvest(lifecycle, intent.id());
        if (hotLeaseOwnsCropWork(state, job)) return;
        ActorLocation actor = state.actorLocations().get(job.workerId());
        if (FrontierResourceSiteHarvestSceneSupport.hasNonClosedScene(state, job)
                || actor == null || !ResourceSiteHarvestGoal.actorAtDepot(state, job)) {
            throw new IllegalArgumentException("resource-site COLD terminal admission lacks its exact completed farmer cursor");
        }
    }

    public static void validateIntent(FrontierWorldState state, ResourceSiteLifecycle lifecycle, PhysicalIntent intent) {
        if (intent.status() != PhysicalIntentStatus.PREPARED) throw new IllegalArgumentException("resource-site harvest must begin prepared");
        validateBinding(state, lifecycle, intent);
    }

    public static void validateBinding(FrontierWorldState state, ResourceSiteLifecycle lifecycle, PhysicalIntent intent) {
        ResourceSiteHarvestJob job = harvest(lifecycle, intent.id()); ResourceSite site = site(state, lifecycle.siteId());
        BlockPosition origin = site.cropSlots().getFirst(); FixedPosition expected = new FixedPosition(FixedScalar.whole(origin.x()), FixedScalar.whole(origin.y()), FixedScalar.whole(origin.z()));
        if (!intent.roles().equals(PhysicalIntentRoleBinding.siteHarvest(job.siteId(), job.id(), job.workerId(),
                job.actorAccountId(), job.depotAccountId()))
                || !intent.origin().equals(expected) || intent.radiusBlocks() != 0 || intent.postcondition() != PhysicalPostcondition.RESOURCE_SITE_HARVESTED_OBSERVED) {
            throw new IllegalArgumentException("resource-site harvest intent does not exactly bind its mature field and output");
        }
    }

    public static ResourceSiteHarvestJob harvest(ResourceSiteLifecycle lifecycle, PhysicalIntentId intentId) {
        return lifecycle.activeWork().filter(ResourceSiteHarvestJob.class::isInstance).map(ResourceSiteHarvestJob.class::cast)
                .filter(job -> job.intentId().equals(intentId)).orElseThrow(() -> new IllegalArgumentException("resource-site harvest has no matching active work"));
    }

    static ResourceSiteHarvestJob activeJobAtSite(FrontierWorldState state, SubjectId siteId) {
        ResourceSiteLifecycle lifecycle = state.resourceSites().sites().get(siteId);
        return lifecycle == null ? null : lifecycle.activeWork().filter(ResourceSiteHarvestJob.class::isInstance)
                .map(ResourceSiteHarvestJob.class::cast).filter(job -> job.siteId().equals(siteId)).orElse(null);
    }

    /**
     * Accepts only a recoverable tail for the exact terminal owner that atomically retired it.
     * The renewable site retains one bounded predecessor lineage; once a later epoch replaces
     * that lineage, an older action is no longer classified and therefore fails closed.
     */
    static boolean retiredContinuation(FrontierWorldState state, ScheduledAction action) {
        if (!COLD_PROGRESS_KIND.equals(action.kind()) || action.priority() != 0 || action.weight() != 1) return false;
        ResourceSiteLifecycle lifecycle = state.resourceSites().sites().get(action.subject());
        if (lifecycle == null) return false;
        return lifecycle.harvestLineage().stream().filter(ResourceSiteHarvestLineage::outputReceiptResolved)
                .map(ResourceSiteHarvestLineage::causality)
                .anyMatch(causality -> action.id().value().equals(causality.coldScheduleId())
                        && action.dueAt().ticks() == causality.coldDueAt());
    }

    /** COLD travel and station work use distinct cadences without consulting a route cursor. */
    static long continuationInterval(FrontierWorldState state, ResourceSiteHarvestJob job) {
        ActorLocation actor = state.actorLocations().get(job.workerId());
        if (actor == null || actor.condition().status() != ActorLifeStatus.ALIVE)
            throw new IllegalArgumentException("field cadence has no living worker");
        return !ResourceSiteHarvestGoal.current(state, job).arrivedAt(actor.supportingSurface())
                ? state.bootstrap().ruleset().cadence().resourceHarvestTraversalInterval()
                : state.bootstrap().ruleset().cadence().resourceHarvestRetryInterval();
    }


    static List<ProposedEvent> blocked(StrategicTask task) {
        return List.of(transition(task, StrategicTaskStatus.BLOCKED));
    }

    private static void validateJob(FrontierWorldState state, ResourceSiteLifecycle lifecycle, ResourceSiteHarvestJob job) {
        if ((lifecycle.phase() != ResourceSitePhase.READY && lifecycle.phase() != ResourceSitePhase.HARVESTING)
                || lifecycle.growthStage() != ResourceSiteLifecycle.MATURE_STAGE) throw new IllegalArgumentException("resource-site harvest requires a ready field");
        if (lifecycle.phase() == ResourceSitePhase.HARVESTING && lifecycle.activeWork().filter(ResourceSiteHarvestJob.class::isInstance)
                .map(ResourceSiteHarvestJob.class::cast).filter(job::equals).isEmpty()) {
            throw new IllegalArgumentException("resource-site harvest completion does not match active work");
        }
        ResourceSite site = site(state, job.siteId()); Settlement settlement = settlement(state, site.settlementId());
        if (job.progress().totalCropSlots() != state.resourceSites().cycle(job.siteId()).layout().cells().size())
            throw new IllegalArgumentException("resource-site harvest job has a foreign admitted cell count");
        if (job.deliveredYieldQuantity() > state.resourceSites().cycle(job.siteId()).harvestedCount()
                || lifecycle.phase() == ResourceSitePhase.READY && job.deliveredYieldQuantity() != 0)
            throw new IllegalArgumentException("resource-site harvest job has an unearned delivered batch cursor");
        StrategicTask task = task(state, job.taskId(), StrategicTaskStatus.ACTIVE);
        if (!task.ownerId().equals(settlement.id()) || !task.resourceSiteTarget().equals(java.util.Optional.of(job.siteId()))) {
            throw new IllegalArgumentException("resource-site harvest job has a foreign strategic task");
        }
        if (state.structureConditions().get(site.facilityId()) != StructureCondition.INTACT) throw new IllegalArgumentException("resource-site harvest farm is unavailable");
        ResidentProfile worker = lifecycle.phase() == ResourceSitePhase.READY
                ? FrontierWorldStateSupport.availableFieldResident(state, settlement.id(), ResidentProfession.AGRICULTURAL_WORKER).orElse(null)
                : state.humanPopulation().resident(job.workerId());
        if (worker == null || !worker.id().equals(job.workerId()) || worker.profession() != ResidentProfession.AGRICULTURAL_WORKER) {
            throw new IllegalArgumentException("resource-site harvest worker is unavailable");
        }
        if (lifecycle.phase() == ResourceSitePhase.READY) {
            if (state.inventory().fungibleResources().accounts().containsKey(job.actorAccountId())
                    || state.inventory().fungibleResources().accounts().values().stream().anyMatch(account ->
                    account.custody().equals(new ResourceCustody.Actor(job.workerId()))))
                throw new IllegalArgumentException("resource-site harvest actor already has resource custody");
            if (job.progress().completedCropSlots() != 0 || job.progress().hasPendingCrop()
                    || job.returningForBatch() || job.navigationBlock().isPresent())
                throw new IllegalArgumentException("field start must declare an unstarted semantic job");
            ResourceFieldCycle cycle = state.resourceSites().cycle(job.siteId());
            ActorLocation location = state.actorLocations().get(job.workerId());
            int selected = cycle.nextWorkSlot(location.supportingSurface()).orElseThrow();
            if (job.progress().nextCropSlotIndex() != selected)
                throw new IllegalArgumentException("field start has a foreign area-work selection");
            if (cycle.expectedWorkOutcome(cycle.layout().cells().get(selected).id())
                    != ResourceFieldCycle.WorkOutcome.SKIPPED_BLOCKED)
                ResourceSiteHarvestKnownNavigation.path(state.withResourceSites(
                        state.resourceSites().replace(lifecycle.harvesting(job))), job);
        }
        if (lifecycle.phase() == ResourceSitePhase.HARVESTING) {
            HumanAssignment assignment = HumanAssignmentProjection.compile(state).assignment(job.workerId());
            if (assignment.kind() != HumanAssignmentKind.FIELD_HARVEST || !assignment.ownerId().equals(java.util.Optional.of(job.id()))) {
                throw new IllegalArgumentException("resource-site harvest worker lacks the exact active assignment");
            }
        }
        SubjectId depot = FrontierWorldState.depotId(settlement.id());
        if (!job.outputSlot().containerId().equals(depot) || !state.containerSlotAvailable(job.outputSlot())) {
            throw new IllegalArgumentException("resource-site harvest output slot is unavailable");
        }
        if (state.inventory().items().containsKey(job.outputItemId())) throw new IllegalArgumentException("resource-site harvest output identity already exists");
    }

    static ResourceSiteHarvestJob job(ResourceSite site, ResourceSiteLifecycle lifecycle, StrategicTask task, ResidentProfile farmer,
                                              InventoryCustody.ContainerSlot outputSlot) {
        String suffix = lifecycle.siteId().value().substring("site:".length()) + "-" + lifecycle.growthEpoch();
        SubjectId jobId = harvestJobId(lifecycle);
        return new ResourceSiteHarvestJob(jobId, task.id(), lifecycle.siteId(), farmer.id(),
                ResourceFieldYield.actorAccountId(jobId),
                ReferenceContainerCustody.scopeId(outputSlot.containerId()),
                new SubjectId("item:site-harvest-" + suffix + "-wheat"), outputSlot, new PhysicalIntentId("intent:site-harvest-" + suffix),
                ResourceSiteHarvestProgress.notStarted(site.layout().cells().size()));
    }

    private static SubjectId harvestJobId(ResourceSiteLifecycle lifecycle) {
        String suffix = lifecycle.siteId().value().substring("site:".length()) + "-" + lifecycle.growthEpoch();
        return new SubjectId("job:site-harvest-" + suffix);
    }

    static PhysicalIntent intent(ResourceSite site, ResourceSiteHarvestJob job) {
        BlockPosition origin = site.cropSlots().getFirst();
        return new PhysicalIntent(job.intentId(), PhysicalIntentKind.RESOURCE_SITE_HARVEST, PhysicalIntentStatus.PREPARED, job.siteId(),
                PhysicalIntentRoleBinding.siteHarvest(job.siteId(), job.id(), job.workerId(), job.actorAccountId(), job.depotAccountId()),
                new FixedPosition(FixedScalar.whole(origin.x()), FixedScalar.whole(origin.y()), FixedScalar.whole(origin.z())), 0,
                PhysicalPostcondition.RESOURCE_SITE_HARVESTED_OBSERVED, io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner.RESOURCE_SITE_HARVEST);
    }

    static StrategicTask task(FrontierWorldState state, SubjectId taskId, StrategicTaskStatus status) {
        StrategicTask task = state.strategicPlans().tasks().get(taskId);
        if (task == null || task.kind() != StrategicTaskKind.HARVEST_RESOURCE_SITE || task.status() != status) {
            throw new IllegalArgumentException("resource-site harvest has no matching strategic task");
        }
        return task;
    }
    static ProposedEvent transition(StrategicTask task, StrategicTaskStatus status) {
        return new ProposedEvent(task.ownerId(), new StrategicTaskTransition(task.id(), status));
    }
    static ProposedEvent schedule(ScheduledAction action) { return new ProposedEvent(action.subject(), new ScheduleEffect.Created(action)); }
    static ProposedEvent reschedule(ScheduledAction current, ScheduledAction replacement) {
        if (!current.id().equals(replacement.id())) throw new IllegalArgumentException("resource-site harvest COLD step must retain its schedule identity");
        return new ProposedEvent(current.subject(), new ScheduleEffect.Rescheduled(current.id(), replacement));
    }

    static ResourceSite site(FrontierWorldState state, SubjectId siteId) {
        ResourceSite site = state.resourceSite(siteId);
        if (site == null) throw new IllegalArgumentException("resource-site harvest has an unknown site"); return site;
    }
    static Settlement settlement(FrontierWorldState state, SubjectId settlementId) { return FrontierWorldStateSupport.settlement(state.bootstrap(), settlementId); }
}
