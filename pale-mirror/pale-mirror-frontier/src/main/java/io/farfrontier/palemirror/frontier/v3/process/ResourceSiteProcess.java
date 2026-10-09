package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleBinding;

import io.farfrontier.palemirror.frontier.v3.model.*;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.FixedPosition;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalPostcondition;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Advances a canonical field by COLD server time; projection never gates its food economy. */
public final class ResourceSiteProcess {
    public static final String PREPARATION_ACTION = "frontier.resource_site.prepare";
    private ResourceSiteProcess() { }
    public static ScheduledAction nextGrowth(ResourceSiteLifecycle lifecycle, long dueAt) {
        return ResourceFieldGrowthProcess.next(lifecycle, dueAt);
    }

    public static ScheduledAction preparation(SubjectId siteId, long dueAt) {
        return new ScheduledAction(new ScheduleId("schedule:resource-site-prepare-" + siteId.value().substring("site:".length())), new SimInstant(dueAt), 0,
                siteId, PREPARATION_ACTION, 1);
    }

    public static List<ProposedEvent> planPreparation(FrontierWorldState state, ScheduledAction action) {
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(action.subject());
        if (lifecycle.phase() != ResourceSitePhase.UNPREPARED || lifecycle.hasWork() || !action.id().equals(preparation(lifecycle.siteId(), action.dueAt().ticks()).id())) return List.of();
        String suffix = lifecycle.siteId().value().substring("site:".length()); ResourceSitePreparationJob job = new ResourceSitePreparationJob(
                new SubjectId("job:site-prepare-" + suffix), lifecycle.siteId(), new PhysicalIntentId("intent:site-prepare-" + suffix));
        ResourceSite site = state.resourceSite(lifecycle.siteId()); BlockPosition origin = site.cropSlots().getFirst();
        ResourceSiteLifecycle prepared = lifecycle.preparing(job).prepared();
        return List.of(new ProposedEvent(lifecycle.siteId(), new ResourceSitePreparationStarted(job)), new ProposedEvent(lifecycle.siteId(), new ResourceSitePrepared(job)),
                new ProposedEvent(lifecycle.siteId(), new ScheduleEffect.Created(nextGrowth(prepared, Math.addExact(action.dueAt().ticks(),
                        state.bootstrap().ruleset().cadence().resourceGrowthStageInterval())))));
    }

    public static FrontierWorldState reducePreparationStarted(FrontierWorldState state, SubjectId subject, ResourceSitePreparationStarted started) {
        ResourceSitePreparationJob job = started.job();
        if (!subject.equals(job.siteId())) throw new IllegalArgumentException("resource-site preparation has a foreign event owner");
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(job.siteId());
        return state.withResourceSites(state.resourceSites().replace(lifecycle.preparing(job)));
    }

    public static FrontierWorldState reducePrepared(FrontierWorldState state, SubjectId subject, ResourceSitePrepared prepared) {
        ResourceSitePreparationJob job = prepared.job();
        if (!subject.equals(job.siteId())) throw new IllegalArgumentException("resource-site preparation completion has a foreign event owner");
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(job.siteId());
        ResourceSitePreparationJob active = lifecycle.preparationWork()
                .orElseThrow(() -> new IllegalArgumentException("resource-site preparation completion has no active work"));
        if (!active.equals(job)) throw new IllegalArgumentException("resource-site preparation completion does not match active work");
        ResourceFieldCycle seeded = ResourceFieldCycle.seeded(job.siteId(),
                state.resourceSites().cycle(job.siteId()).layout(), 1L);
        return state.withResourceSites(state.resourceSites().replace(lifecycle.prepared(), seeded));
    }

    public static FrontierWorldState reducePrepared(FrontierWorldState state, SubjectId subject, PhysicalIntent intent) {
        if (intent.kind() != PhysicalIntentKind.RESOURCE_SITE_PREPARATION || !subject.equals(intent.causeSubjectId())) throw new IllegalArgumentException("resource-site preparation intent is invalid");
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(intent.causeSubjectId());
        ResourceSitePreparationJob job = lifecycle.preparationWork()
                .orElseThrow(() -> new IllegalArgumentException("resource-site preparation lacks active work"));
        if (!intent.id().equals(job.intentId()) || !intent.roles().equals(PhysicalIntentRoleBinding.sitePreparation(job.siteId(), job.id())) || intent.postcondition() != PhysicalPostcondition.RESOURCE_SITE_PREPARED_OBSERVED) {
            throw new IllegalArgumentException("resource-site preparation intent does not bind its active work");
        }
        return state.preparePhysicalIntent(intent);
    }

    public static List<ProposedEvent> planPreparationTransition(FrontierWorldState state, PhysicalIntent intent, PhysicalIntentTransition transition, long now) {
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(intent.causeSubjectId());
        if (lifecycle.phase() == ResourceSitePhase.DESTROYED) {
            if (transition.status() != PhysicalIntentStatus.UNKNOWN_AFTER_RESTART) throw new IllegalArgumentException("destroyed resource site can only retain unknown preparation evidence");
            return List.of(new ProposedEvent(lifecycle.siteId(), transition));
        }
        if (lifecycle.preparationWork()
                .filter(job -> job.intentId().equals(intent.id())).isEmpty()) throw new IllegalArgumentException("resource-site preparation transition has no active work");
        if (transition.status() == PhysicalIntentStatus.CONFIRMED) {
            return List.of(new ProposedEvent(lifecycle.siteId(), transition), new ProposedEvent(lifecycle.siteId(), new ScheduleEffect.Created(
                    nextGrowth(lifecycle.prepared(), Math.addExact(now, state.bootstrap().ruleset().cadence().resourceGrowthStageInterval())))));
        }
        return List.of(new ProposedEvent(lifecycle.siteId(), transition));
    }

    public static List<ProposedEvent> planGrowth(FrontierWorldState state, ScheduledAction action) {
        return ResourceFieldGrowthProcess.plan(state, action);
    }

    public static FrontierWorldState reduceGrowth(FrontierWorldState state, SubjectId subject, ResourceSiteGrowthAdvanced advanced) {
        return ResourceFieldGrowthProcess.reduce(state, subject, advanced);
    }

    /** Persists one exact permission before Vanilla can remove the owned crop. */
    public static FrontierWorldState reducePlayerBreakPrepared(FrontierWorldState state, SubjectId subject,
                                                               ResourceFieldPlayerBreakPrepared prepared) {
        if (!subject.equals(prepared.siteId()))
            throw new IllegalArgumentException("prepared field break has a foreign event owner");
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(prepared.siteId());
        if (lifecycle.phase() == ResourceSitePhase.UNPREPARED || lifecycle.phase() == ResourceSitePhase.CONFLICT
                || lifecycle.phase() == ResourceSitePhase.DESTROYED)
            throw new IllegalArgumentException("prepared field break has no active owned field");
        ResourceFieldCycle cycle = state.resourceSites().cycle(prepared.siteId());
        if (state.resourceSites().hasPendingCellMutation(prepared.siteId(), prepared.cellId()))
            throw new IllegalArgumentException("player break overlaps an unresolved world field change");
        if (cycle.epoch() != prepared.epoch() || cycle.layout().revision() != prepared.layoutRevision())
            throw new IllegalArgumentException("prepared field break has a stale epoch or layout revision");
        // The physical ingress refuses a cell with a pending farmer effect witness.
        // A merely prepared job has not necessarily written a block or hand yet;
        // its exact player postcondition may close that work target instead.
        var pending = new ResourceFieldCycle.PendingPlayerBreak(prepared.playerId(), prepared.actionId(), prepared.before());
        return state.withResourceSites(state.resourceSites().replace(lifecycle,
                cycle.preparePlayerBreak(prepared.cellId(), pending)));
    }

    /** Retain the exact physical cause before COLD may advance this site's field epoch. */
    public static FrontierWorldState reduceWorldChangeHeld(FrontierWorldState state, SubjectId subject,
                                                            ResourceFieldWorldChangeHeld held) {
        ResourceFieldCellObserved observation = held.observation();
        if (!subject.equals(observation.siteId()))
            throw new IllegalArgumentException("world field hold has a foreign event owner");
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(subject);
        if (lifecycle.phase() == ResourceSitePhase.UNPREPARED || lifecycle.phase() == ResourceSitePhase.CONFLICT
                || lifecycle.phase() == ResourceSitePhase.DESTROYED)
            throw new IllegalArgumentException("world field hold has no active owned field");
        ResourceFieldCycle cycle = state.resourceSites().cycle(subject);
        if (cycle.epoch() != observation.epoch() || cycle.layout().revision() != observation.layoutRevision()
                || !ResourceFieldPhysicalSurface.Condition.of(cycle.cell(observation.cellId())).equals(observation.before())
                || cycle.pendingPlayerBreaks().containsKey(observation.cellId()))
            throw new IllegalArgumentException("world field hold has a stale or competing canonical predecessor");
        // The physical producer admits this hold only while the crop has no
        // persisted field-effect witness. The held site then prevents COLD and
        // HOT work until the observed result either applies or is cancelled.
        return state.withResourceSites(state.resourceSites().holdWorldChange(observation));
    }

    /** Release only the retained cause whose physical claim and block were just rechecked. */
    public static FrontierWorldState reduceWorldChangeAcknowledged(FrontierWorldState state, SubjectId subject,
                                                                    ResourceFieldWorldChangeAcknowledged acknowledged) {
        ResourceFieldCellObserved observation = acknowledged.observation();
        if (!subject.equals(observation.siteId())
                || !observation.equals(state.resourceSites().pendingWorldChange(subject, observation.cellId())))
            throw new IllegalArgumentException("world field acknowledgement has no exact held owner");
        ResourceFieldCycle cycle = state.resourceSites().cycle(subject);
        if (cycle.epoch() != observation.epoch() || cycle.layout().revision() != observation.layoutRevision()
                || !ResourceFieldPhysicalSurface.Condition.of(cycle.cell(observation.cellId()))
                .equals(acknowledged.physical()))
            throw new IllegalArgumentException("world field acknowledgement disagrees with canonical cell condition");
        return state.withResourceSites(state.resourceSites().acknowledgeWorldChange(observation));
    }

    /** An exact physical predecessor fences this site before a foreign Vanilla write. */
    public static FrontierWorldState reduceForeignChangeHeld(FrontierWorldState state, SubjectId subject,
                                                              ResourceFieldForeignChangeHeld held) {
        if (!subject.equals(held.siteId()))
            throw new IllegalArgumentException("foreign field hold has a different event owner");
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(subject);
        if (lifecycle.phase() == ResourceSitePhase.UNPREPARED || lifecycle.phase() == ResourceSitePhase.CONFLICT
                || lifecycle.phase() == ResourceSitePhase.DESTROYED)
            throw new IllegalArgumentException("foreign field hold has no active owned site");
        ResourceFieldCycle cycle = state.resourceSites().cycle(subject);
        if (cycle.epoch() != held.epoch() || cycle.layout().revision() != held.layoutRevision()
                || !cycle.cell(held.cellId()).equals(held.before())
                || cycle.pendingPlayerBreaks().containsKey(held.cellId()))
            throw new IllegalArgumentException("foreign field hold has a stale or competing predecessor");
        // The physical predecessor check excludes an already started cell effect;
        // an admitted foreign hold can therefore suspend a prepared-only crop.
        return state.withResourceSites(state.resourceSites().holdForeignChange(held));
    }

    /** The physical reader, never a replacement prediction, selects the one local result. */
    public static FrontierWorldState reduceForeignCellObserved(FrontierWorldState state, SubjectId subject,
                                                                ResourceFieldForeignCellObserved observed) {
        ResourceFieldForeignChangeHeld held = observed.hold();
        if (!subject.equals(held.siteId()) || !held.equals(state.resourceSites().pendingForeignChange(subject, held.cellId())))
            throw new IllegalArgumentException("foreign field observation lacks its exact retained cause");
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(subject);
        ResourceFieldCycle cycle = state.resourceSites().cycle(subject);
        if (cycle.epoch() != held.epoch() || cycle.layout().revision() != held.layoutRevision()
                || !cycle.cell(held.cellId()).equals(held.before()))
            throw new IllegalArgumentException("foreign field observation has a stale cell predecessor");
        ResourceFieldCycle next = cycle.observedInterference(held.cellId(), observed.after());
        return applyObservedCellChange(state, lifecycle, cycle, next, held.cellId());
    }

    public static FrontierWorldState reduceForeignChangeAcknowledged(FrontierWorldState state, SubjectId subject,
                                                                      ResourceFieldForeignChangeAcknowledged acknowledged) {
        ResourceFieldForeignChangeHeld held = acknowledged.hold();
        if (!subject.equals(held.siteId()) || !held.equals(state.resourceSites().pendingForeignChange(subject, held.cellId())))
            throw new IllegalArgumentException("foreign field acknowledgement lacks its exact retained cause");
        ResourceFieldCycle cycle = state.resourceSites().cycle(subject);
        if (cycle.epoch() != held.epoch() || cycle.layout().revision() != held.layoutRevision()
                || !cycle.cell(held.cellId()).equals(acknowledged.physical()))
            throw new IllegalArgumentException("foreign field acknowledgement disagrees with canonical cell");
        return state.withResourceSites(state.resourceSites().acknowledgeForeignChange(held));
    }

    /** Reduces one adapter-declared postcondition without retiring an otherwise usable field. */
    public static FrontierWorldState reduceCellObserved(FrontierWorldState state, SubjectId subject,
                                                        ResourceFieldCellObserved observed) {
        if (!subject.equals(observed.siteId()))
            throw new IllegalArgumentException("field cell observation has a foreign event owner");
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(observed.siteId());
        if (lifecycle.phase() == ResourceSitePhase.UNPREPARED || lifecycle.phase() == ResourceSitePhase.CONFLICT
                || lifecycle.phase() == ResourceSitePhase.DESTROYED)
            throw new IllegalArgumentException("field cell observation has no active owned field");
        ResourceFieldCycle cycle = state.resourceSites().cycle(observed.siteId());
        if (cycle.epoch() != observed.epoch() || cycle.layout().revision() != observed.layoutRevision())
            throw new IllegalArgumentException("field cell observation has a stale epoch or layout revision");
        ResourceFieldCycle.CellState prior = cycle.cell(observed.cellId());
        if (!ResourceFieldPhysicalSurface.Condition.of(prior).equals(observed.before()))
            throw new IllegalArgumentException("field cell observation has a stale canonical predecessor");
        ResourceFieldCycle ready = cycle;
        ResourceFieldCellObserved heldWorld = state.resourceSites().pendingWorldChange(observed.siteId(), observed.cellId());
        if (observed.source() == ResourceFieldCellObserved.Source.PLAYER) {
            if (state.resourceSites().pendingWorldChange(observed.siteId(), observed.cellId()) != null || state.resourceSites().pendingForeignChange(observed.siteId(), observed.cellId()) != null)
                throw new IllegalArgumentException("player field action overlaps a held world change");
            var pending = cycle.pendingPlayerBreaks().get(observed.cellId());
            if (pending == null || !pending.actionId().equals(observed.causationId())
                    || !pending.before().equals(observed.before()))
                throw new IllegalArgumentException("player field observation lacks its exact durable break permission");
            ready = cycle.closePlayerBreak(observed.cellId(), observed.causationId());
        } else {
            if (state.resourceSites().pendingForeignChange(observed.siteId(), observed.cellId()) != null
                    || heldWorld == null || !heldWorld.causationId().equals(observed.causationId())
                    || !heldWorld.siteId().equals(observed.siteId()) || heldWorld.epoch() != observed.epoch()
                    || heldWorld.layoutRevision() != observed.layoutRevision()
                    || !heldWorld.cellId().equals(observed.cellId()) || !heldWorld.before().equals(observed.before())
                    || !heldWorld.equals(observed))
                throw new IllegalArgumentException("world field observation lacks its exact canonical recovery hold");
            if (cycle.pendingPlayerBreaks().containsKey(observed.cellId()))
                throw new IllegalArgumentException("world field observation overlaps an unresolved player action");
        }
        ResourceFieldCycle next = switch (observed.change()) {
            case CROP_GROWN -> ready.observedGrowth(observed.cellId(), observed.after());
            case CROP_REPLANTED -> ready.cropReplanted(observed.cellId());
            case CROP_REMOVED -> ready.cropRemoved(observed.cellId());
            case SOIL_BECAME_DIRT -> ready.soilBecameDirt(observed.cellId());
            case UNCHANGED -> ready;
        };
        if (!ResourceFieldPhysicalSurface.Condition.of(next.cell(observed.cellId())).equals(observed.after()))
            throw new IllegalArgumentException("field cell observation disagrees with its canonical successor");
        return applyObservedCellChange(state, lifecycle, cycle, next, observed.cellId());
    }

    private static FrontierWorldState applyObservedCellChange(FrontierWorldState state, ResourceSiteLifecycle lifecycle,
                                                              ResourceFieldCycle cycle, ResourceFieldCycle next,
                                                              ResourceFieldLayout.CellId cellId) {
        ResourceSiteHarvestJob job = lifecycle.harvestJobs().values().stream()
                .filter(candidate -> !candidate.progress().complete() && !candidate.returningForBatch()
                        && cycle.layout().cells().get(candidate.progress().selectedCropSlotIndex()).id().equals(cellId))
                .reduce((left, right) -> { throw new IllegalArgumentException("observed cell has duplicate execution owners"); }).orElse(null);
        ResourceFieldCycle.CellState before = cycle.cell(cellId);
        ResourceFieldCycle.CellState after = next.cell(cellId);
        boolean preparedHere = job != null && job.progress().hasPendingCrop()
                && cycle.layout().cells().get(job.progress().pendingCropSlotIndex()).id().equals(cellId);
        boolean lostPlant = lifecycle.phase() == ResourceSitePhase.HARVESTING && !before.accounted()
                && (before.crop() == ResourceFieldCycle.Crop.GROWING || before.crop() == ResourceFieldCycle.Crop.MATURE)
                && (after.crop() == ResourceFieldCycle.Crop.ABSENT || after.crop() == ResourceFieldCycle.Crop.OBSTRUCTED
                    || after.crop() == ResourceFieldCycle.Crop.GROWING && after.growthStage() < before.growthStage());
        if (job != null && lostPlant) {
            int lostSlot = cycle.layout().cells().indexOf(cycle.layout().requireCell(cellId));
            if (lostSlot < 0) throw new IllegalArgumentException("observed crop loss has no admitted work slot");
            // Close only the execution's obsolete claim. The replacement generation
            // remains available for sowing; it is not completed work or harvested yield.
            int nextSelected = job.progress().selectedCropSlotIndex() == lostSlot
                    ? job.progress().completedCropSlots() + 1 >= job.progress().totalCropSlots() ? -1
                    : lifecycle.selectHarvestTarget(job, next, state.actorLocations().get(job.workerId()).supportingSurface(),
                            index -> index != lostSlot).orElse(-1)
                    : job.progress().selectedCropSlotIndex();
            lifecycle = lifecycle.accountObservedLostHarvestCell(job, lostSlot, nextSelected, next);
        } else if (preparedHere) {
            lifecycle = lifecycle.cancelPreparedHarvestCrop(cellId, cycle);
        }
        if (job != null) lifecycle = lifecycle.bindHarvestTarget(lifecycle.harvestJob(job.id()).orElseThrow(), next);
        return state.withResourceSites(state.resourceSites().replace(lifecycle.withPlantReadiness(next), next));
    }

    /** A loaded physical observation changes work access only, never crop or yield. */
    public static FrontierWorldState reduceWorkAccessObserved(FrontierWorldState state, SubjectId subject,
                                                              ResourceFieldWorkAccessObserved observed) {
        if (!subject.equals(observed.siteId()))
            throw new IllegalArgumentException("field work access has a foreign site owner");
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(subject);
        ResourceFieldCycle cycle = state.resourceSites().cycle(subject);
        ResourceFieldLayout.Cell cell = cycle.layout().requireCell(observed.cellId());
        if (lifecycle.phase() == ResourceSitePhase.UNPREPARED || lifecycle.phase() == ResourceSitePhase.DESTROYED
                || lifecycle.phase() == ResourceSitePhase.CONFLICT
                || observed.epoch() != cycle.epoch() || observed.layoutRevision() != cycle.layout().revision()
                || !observed.headroom().equals(cell.workstation().support().offset(0, 2, 0))
                || state.resourceSites().hasPendingCellMutation(subject, cell.id())
                || cycle.pendingPlayerBreaks().containsKey(cell.id()))
            throw new IllegalArgumentException("field work access has a stale or unresolved cell observation");
        if (observed.hotLeaseId().isPresent()) {
            SceneLease lease = state.sceneLeases().get(observed.hotLeaseId().orElseThrow());
            if (lease == null || !FrontierSceneBehaviors.isResourceSiteHarvest(lease))
                throw new IllegalArgumentException("field work access has no physical farmer owner");
            ResourceSiteHarvestJob job = lifecycle.harvestJob(
                    FrontierSceneBehaviors.resourceSiteHarvest(lease).jobId()).orElseThrow();
            FrontierResourceSiteHarvestSceneSupport.requireHotLease(state, job, observed.hotLeaseId().orElseThrow());
        }
        return state.withResourceSites(state.resourceSites().replace(lifecycle,
                cycle.observedWorkAccess(cell.id(), observed.blocked())));
    }

    public static FrontierWorldState reduceConflict(FrontierWorldState state, SubjectId subject, ResourceSiteConflictObserved conflict) {
        if (!subject.equals(conflict.siteId())) throw new IllegalArgumentException("resource-site conflict has a foreign event owner");
        ResourceSite site = state.resourceSite(conflict.siteId());
        boolean retainedTraversalSupport = lifecycleTraversalSupport(state, conflict);
        if (site == null || (!site.managedSlots().contains(conflict.position()) && !retainedTraversalSupport)) {
            throw new IllegalArgumentException("resource-site conflict must name one exact field cell");
        }
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(conflict.siteId());
        if (lifecycle.phase() == ResourceSitePhase.DESTROYED || lifecycle.phase() == ResourceSitePhase.CONFLICT) return state;
        ConflictIncident incident = ResourceSiteConflictIncidents.first(lifecycle, conflict);
        ResourceSiteConflictDisposition disposition = ResourceSiteConflictDisposition.requiresRecoveryInspection(conflict.reason())
                ? ResourceSiteConflictDisposition.recovery(conflict.position(), conflict.reason(), incident)
                : ResourceSiteConflictDisposition.terminal(conflict.position(), conflict.reason(), incident);
        ResourceSiteLifecycle conflicted = lifecycle.conflicted(disposition);
        if (lifecycle.harvestJobs().isEmpty()) {
            return state.withResourceSites(state.resourceSites().replace(conflicted));
        }

        // A player-observed field loss is one authoritative disposition, not an invitation for
        // the field worker or its intent to continue.  Install every consequence in the same
        // aggregate transition: after this returns there is no nonterminal harvest intent whose
        // canonical subjects were just retired, and no HOT scene can reinterpret the break.
        Map<PhysicalIntentId, PhysicalIntent> intents = new LinkedHashMap<>(state.physicalIntents());
        Map<SceneLeaseId, SceneLease> leases = new LinkedHashMap<>(state.sceneLeases());
        StrategicPlanState plans = state.strategicPlans();
        FencedRecoveryState recovery = state.fencedRecovery();
        for (ResourceSiteHarvestJob job : lifecycle.harvestJobs().values()) {
            PhysicalIntent intent = intents.get(job.intentId());
            if (intent == null || intent.kind() != PhysicalIntentKind.RESOURCE_SITE_HARVEST
                    || !intent.causeSubjectId().equals(lifecycle.siteId())
                    || (intent.status() != PhysicalIntentStatus.PREPARED && intent.status() != PhysicalIntentStatus.RUNNING))
                throw new IllegalArgumentException("resource-site player conflict has no active exact harvest intent");
            intents.put(intent.id(), intent.withStatus(PhysicalIntentStatus.CONFLICTED, Optional.empty()));
            leases.replaceAll((id, lease) -> {
                if (!FrontierSceneBehaviors.isResourceSiteHarvest(lease)
                        || !FrontierSceneBehaviors.resourceSiteHarvest(lease).jobId().equals(job.id())) return lease;
                return switch (lease.status()) {
                    case HOT, UNKNOWN_AFTER_RESTART -> lease.withStatus(
                            FrontierSceneLeaseStateSupport.hasBoundSceneHand(state, lease)
                                    ? SceneLeaseStatus.CONFLICT : SceneLeaseStatus.DRAINING);
                    case PREPARED -> lease.withStatus(SceneLeaseStatus.CONFLICT);
                    default -> lease;
                };
            });
            if (plans.tasks().get(job.taskId()).status() != StrategicTaskStatus.BLOCKED)
                plans = plans.transitionTask(job.taskId(), StrategicTaskStatus.BLOCKED);
            recovery = FencedRecoveryPhysicalIntentSupport.transition(recovery, intent, PhysicalIntentStatus.CONFLICTED,
                    FencedRecoveryAsset.EFFECT);
        }
        return state.withChanges(FrontierWorldStateUpdate.begin()
                .resourceSites(state.resourceSites().replace(conflicted)).strategicPlans(plans)
                .physicalIntents(intents).sceneLeases(leases).fencedRecovery(recovery));
    }

    private static boolean lifecycleTraversalSupport(FrontierWorldState state, ResourceSiteConflictObserved conflict) {
        if (conflict.producer().source() != ResourceSiteConflictSource.SCENE_TRAVERSAL) return false;
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(conflict.siteId());
        return lifecycle.harvestJobs().values().stream().anyMatch(job -> {
                    ActorLocation actor = state.actorLocations().get(job.workerId());
                    return actor != null && actor.supportingSurface().support().equals(conflict.position());
                });
    }

    public static List<ProposedEvent> planConflict(FrontierWorldState state, ResourceSiteConflictObserved conflict) {
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(conflict.siteId());
        reduceConflict(state, conflict.siteId(), conflict);
        var events = new java.util.ArrayList<ProposedEvent>();
        events.add(new ProposedEvent(conflict.siteId(), conflict));
        lifecycle.harvestJobs().values().stream().sorted(java.util.Comparator.comparing(ResourceSiteHarvestJob::id))
                .forEach(job -> events.add(new ProposedEvent(conflict.siteId(),
                        new ScheduleEffect.Cancelled(ResourceSiteHarvestProcess.coldProgress(job, 0L).id()))));
        return List.copyOf(events);
    }

}
