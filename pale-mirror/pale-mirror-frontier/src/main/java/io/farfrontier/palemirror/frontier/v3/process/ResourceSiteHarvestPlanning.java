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

import static io.farfrontier.palemirror.frontier.v3.process.ResourceSiteHarvestProcess.*;

/** Planning and retained COLD continuation for field work. */
final class ResourceSiteHarvestPlanning {
    private ResourceSiteHarvestPlanning() { }

    public static ScheduledAction start(StrategicTask task, long dueAt) {
        if (task.kind() != StrategicTaskKind.HARVEST_RESOURCE_SITE || task.resourceSiteTarget().isEmpty()) {
            throw new IllegalArgumentException("resource-site harvest start requires its exact strategic task");
        }
        return new ScheduledAction(new ScheduleId("schedule:resource-site-harvest-task-start-" + task.id().value().replace(':', '-')),
                new SimInstant(dueAt), 0, task.id(), "frontier.resource_site.harvest", 1);
    }

    public static List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action) {
        StrategicTask task = task(state, action.subject(), StrategicTaskStatus.PENDING);
        if (!action.id().equals(start(task, action.dueAt().ticks()).id())) return List.of();
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(task.resourceSiteTarget().orElseThrow());
        if (lifecycle.phase() != ResourceSitePhase.READY) return blocked(task);
        ResourceSite site = site(state, lifecycle.siteId()); Settlement settlement = settlement(state, site.settlementId());
        if (!task.ownerId().equals(settlement.id()) || !task.resourceSiteTarget().equals(java.util.Optional.of(site.id()))) {
            throw new IllegalArgumentException("resource-site harvest task has a foreign field owner");
        }
        if (state.structureConditions().get(site.facilityId()) != StructureCondition.INTACT) return blocked(task);
        ResidentProfile farmer = successorFarmer(state, lifecycle, settlement.id());
        if (farmer == null) return blocked(task);
        // Selecting an idle strategic worker is not enough to take its physical body.  In
        // particular, an ordinary ambient lease can still be carrying its prior post-work
        // return goal through restart recovery. Starting a field job from that body would
        // give COLD and HOT different owners of the same actor.  Keep this durable task pending
        // and retry its stable start action only after that hand-off is conclusively closed.
        if (!FrontierSceneAdmission.available(state, List.of(farmer.id()))
                && !retainsExactAmbientHandoff(state, lifecycle, farmer)) {
            long retryAt = Math.addExact(action.dueAt().ticks(), state.bootstrap().ruleset().cadence().resourceHarvestRetryInterval());
            return List.of(reschedule(action, start(task, retryAt)));
        }
        SubjectId depot = FrontierWorldState.depotId(settlement.id());
        OptionalInt slot = state.firstFreeContainerSlot(depot);
        if (slot.isEmpty()) {
            long retryAt = Math.addExact(action.dueAt().ticks(), state.bootstrap().ruleset().cadence().strategicReviewInterval());
            return List.of(reschedule(action, start(task, retryAt)));
        }
        ActorLocation worker = state.actorLocations().get(farmer.id());
        if (worker == null) throw new IllegalArgumentException("resource-site harvest worker has no canonical body");
        ResourceSiteHarvestJob job = job(site, lifecycle, task, farmer,
                new InventoryCustody.ContainerSlot(depot, slot.getAsInt()));
        int selected = state.resourceSites().cycle(site.id()).nextWorkSlot(worker.supportingSurface()).orElseThrow();
        job = job.withProgress(job.progress().withSelectedCropSlot(selected));
        if (state.resourceSites().cycle(site.id()).expectedWorkOutcome(
                site.layout().cells().get(selected).id()) != ResourceFieldCycle.WorkOutcome.SKIPPED_BLOCKED) {
            try {
                ResourceSiteHarvestKnownNavigation.path(state.withResourceSites(
                        state.resourceSites().replace(lifecycle.harvesting(job))), job);
            } catch (ResourceSiteHarvestKnownNavigation.KnowledgeUnavailable unavailable) {
                FrontierWorldState admitted = state.withResourceSites(state.resourceSites().replace(lifecycle.harvesting(job)));
                var alternate = ResourceSiteHarvestRetargeting.coldReachableWorkTarget(admitted, job);
                if (alternate.isEmpty()) return blocked(task);
                job = job.withProgress(job.progress().withSelectedCropSlot(alternate.getAsInt()));
            }
        }
        PhysicalIntent intent = intent(site, job);
        long firstColdStep = Math.addExact(action.dueAt().ticks(), state.bootstrap().ruleset().cadence().resourceHarvestTraversalInterval());
        return List.of(transition(task, StrategicTaskStatus.ACTIVE), new ProposedEvent(lifecycle.siteId(), new ResourceSiteHarvestStarted(job)),
                new ProposedEvent(lifecycle.siteId(), new PhysicalIntentPrepared(intent)), schedule(coldProgress(job, firstColdStep)));
    }

    /**
     * Policy chooses a farmer only for the first epoch.  A completed epoch has
     * already admitted its successor identity in the resource-site lifecycle;
     * loss of that exact resident is a local blocked task, never permission to
     * select another currently eligible farmer.
     */
    private static ResidentProfile successorFarmer(FrontierWorldState state, ResourceSiteLifecycle lifecycle, SubjectId settlementId) {
        if (lifecycle.harvestLineage().isEmpty()) {
            return FrontierWorldStateSupport.availableFieldResident(state, settlementId, ResidentProfession.AGRICULTURAL_WORKER).orElse(null);
        }
        SubjectId workerId = lifecycle.harvestLineage().orElseThrow().workerId();
        ResidentProfile farmer = state.humanPopulation().resident(workerId);
        if (farmer == null || !farmer.settlementId().equals(settlementId) || farmer.profession() != ResidentProfession.AGRICULTURAL_WORKER
                || !FrontierWorldStateSupport.workCapable(state, farmer)
                || !HumanAssignmentProjection.compile(state).idle(workerId)) {
            return null;
        }
        return farmer;
    }

    /**
     * A field start may transfer one exact idle PATROL body into its registered scene.  This is
     * required for the first epoch as well as a retained successor: the candidate which names
     * the scene cannot exist until the start declares the job, while waiting for a HOT ambient
     * body to close leaves a READY field permanently self-blocked under player demand.  A later
     * epoch may additionally retain its lifecycle-owned WORK hand-off.  No other ambient purpose
     * is eligible, and the exact body/idle/no-scene checks keep this a transfer rather than a
     * second authority over a visible actor.
     */
    private static boolean retainsExactAmbientHandoff(FrontierWorldState state, ResourceSiteLifecycle lifecycle,
                                                      ResidentProfile farmer) {
        boolean successor = lifecycle.harvestLineage().filter(lineage -> lineage.workerId().equals(farmer.id())).isPresent();
        AmbientActorLease lease = state.ambientLeases().get(farmer.id());
        var location = state.actorLocations().get(farmer.id());
        // The first epoch admits only the idle PATROL at its own retained body.  WORK is a
        // lifecycle-owned recovery hand-off and therefore remains valid only for the exact
        // successor worker retained by the preceding epoch.
        if (lease == null || location == null || lease.status() != AmbientLeaseStatus.HOT
                || (lease.goal() != AmbientGoalKind.PATROL && (!successor || lease.goal() != AmbientGoalKind.WORK))
                || !HumanAssignmentProjection.compile(state).idle(farmer.id())
                || !location.body().equals(lease.handoffBody()) || !location.body().equals(lease.goalBody())) return false;
        return state.sceneLeases().values().stream().noneMatch(scene -> scene.status() != SceneLeaseStatus.CLOSED
                && scene.members().stream().anyMatch(member -> member.actorId().equals(farmer.id())));
    }

    /**
     * One deterministic COLD step of the exact harvest worker. The action declares its site
     * owner; its stable ID still identifies the job, so HOT hand-off and later release resume
     * the same due action rather than creating an auxiliary scene journey.
     */
    public static ScheduledAction coldProgress(ResourceSiteHarvestJob job, long dueAt) {
        return new ScheduledAction(new ScheduleId("schedule:resource-site-harvest-cold-progress-"
                + job.id().value().substring("job:".length())), new SimInstant(dueAt), 0, job.siteId(),
                COLD_PROGRESS_KIND, 1);
    }

    /** Descriptor-owned validation of the one engine action that may continue this field job. */
    public static void requireContinuationBinding(ResourceSiteHarvestJob job, ScheduledAction action) {
        Objects.requireNonNull(job, "resource-site harvest job");
        Objects.requireNonNull(action, "resource-site harvest continuation binding");
        ScheduledAction expected = coldProgress(job, action.dueAt().ticks());
        if (!expected.equals(action)) {
            throw new IllegalArgumentException("resource-site harvest binding is not its exact cold continuation");
        }
    }

    /** Only an already-due retained continuation may authorize irreversible crop work. */
    public static void requireDueContinuationBinding(ResourceSiteHarvestJob job, ScheduledAction action, long instant) {
        requireContinuationBinding(job, action);
        if (instant < action.dueAt().ticks()) {
            throw new IllegalArgumentException("resource-site crop work is before its retained continuation due turn");
        }
    }

    /** Semantic crop completion uses the same retained cadence transition as COLD. */
    public static ProposedEvent advanceBoundContinuation(FrontierWorldState state, ResourceSiteHarvestJob job, ScheduledAction action) {
        Objects.requireNonNull(state, "resource-site harvest continuation state");
        requireContinuationBinding(job, action);
        long nextDue = Math.addExact(action.dueAt().ticks(), continuationInterval(state, job));
        return reschedule(action, coldProgress(job, nextDue));
    }

    /**
     * Advances one retained COLD work step while no harvest scene owns the worker.  The crop
     * receipt is canonical and durable before a later natural physical projection; it never
     * reads or writes an unloaded Minecraft crop or depot surface.
     */
    public static List<ProposedEvent> planColdProgress(FrontierWorldState state, ScheduledAction action) {
        ResourceSiteHarvestJob job = activeJobAtSite(state, action.subject());
        // A terminal receipt retires its recurrent continuation by stable schedule identity.
        // Recovery can still encounter that exact, already-retired action in a pre-transition
        // checkpoint/WAL seam.  It is a bounded terminal disposition only when the owning
        // lifecycle retains the resolved predecessor lineage.  Do not turn an arbitrary absent
        // job into a harmless no-op: an unknown durable action is a temporal referential-
        // integrity violation and must retain the kernel's fail-closed boundary.
        if (job == null) {
            if (!retiredContinuation(state, action)) {
                throw new IllegalArgumentException("resource-site harvest continuation has no active owner or resolved terminal disposition");
            }
            return List.of(new ProposedEvent(action.subject(), new ScheduleEffect.Consumed(action.id())));
        }
        if (!coldProgress(job, action.dueAt().ticks()).equals(action)) {
            if (retiredContinuation(state, action))
                return List.of(new ProposedEvent(action.subject(), new ScheduleEffect.Consumed(action.id())));
            throw new IllegalArgumentException("resource-site harvest continuation is neither this site's active job nor its resolved terminal tail");
        }
        // A player-admitted conflict retains this job only as terminal causal evidence.  Its
        // already-durable COLD action must be consumed without a successor; otherwise a stale
        // scheduler turn could recreate field-work after the sole player disposition.
        if (state.resourceSites().site(job.siteId()).phase() == ResourceSitePhase.CONFLICT)
            return List.of(new ProposedEvent(action.subject(), new ScheduleEffect.Consumed(action.id())));
        if (state.resourceSites().site(job.siteId()).phase() != ResourceSitePhase.HARVESTING)
            throw new IllegalArgumentException("resource-site harvest continuation has an active job outside HARVESTING or terminal CONFLICT");
        long nextDue = Math.addExact(action.dueAt().ticks(), continuationInterval(state, job));
        if (coldProgressHeld(state, action)) {
            // The current engine action is also the HOT checkpoint's only binding.  Advancing its
        // due instant while another owner holds this step (a HOT scene or a pending player
        // removal at the next cell) makes its eventual observation wait for an unrelated
        // cadence turn. Preserve the action byte-for-byte until that owner closes it.
            return List.of(reschedule(action, action));
        }
        ResourceFieldCycle currentField = state.resourceSites().cycle(job.siteId());
        if (job.navigationBlock().isPresent()) {
            // A previously observed HOT blockage remains authoritative in COLD. In
            // particular, do not retry a blocked-cell skip before its exact clearance.
            return List.of(reschedule(action, coldProgress(job, nextDue)));
        }
        if (!job.progress().complete() && !job.returningForBatch() && !job.progress().hasPendingCrop()) {
            ResourceFieldLayout.Cell nextCell = currentField.layout().cells().get(job.progress().nextCropSlotIndex());
            if (currentField.expectedWorkOutcome(nextCell.id()) == ResourceFieldCycle.WorkOutcome.SKIPPED_BLOCKED) {
                if (currentField.pendingPlayerBreaks().containsKey(nextCell.id()))
                    return List.of(reschedule(action, action));
                ResourceSiteHarvestBlockedCellSkipped skipped = new ResourceSiteHarvestBlockedCellSkipped(
                        job.siteId(), job.id(), job.workerId(), currentField.layout().revision(), List.of(nextCell.id()),
                        action.id(), action.dueAt().ticks(), java.util.Optional.empty());
                reduceBlockedCellSkipped(state, job.siteId(), skipped);
                return List.of(new ProposedEvent(job.siteId(), skipped),
                        reschedule(action, coldProgress(job, nextDue)));
            }
        }
        // COLD owns the same recurrent duty cycle as HOT while no physical scene owns this
        // worker.  Stopping after the first receipt left a farmer permanently parked at crop
        // one across unload/restart intervals, so elapsed zero-player time had no truthful
        // continuation.  Each step below advances one retained pedestrian edge or one exact
        // crop receipt; it still never touches an unloaded block or manufactures final output.
        if (ResourceSiteHarvestGoal.actorAtDepot(state, job)) {
            if (job.returningForBatch() && !batchDeliveryCapacityAvailable(state, job))
                return List.of(reschedule(action, coldProgress(job, nextDue)));
            ProposedEvent returned = new ProposedEvent(job.siteId(), new ResourceSiteHarvestReturned(job.id(), job.workerId(),
                    action.id(), action.dueAt().ticks()));
            return job.returningForBatch()
                    ? List.of(returned, stockWake(state, job, job.deliveredYieldQuantity(), action.dueAt().ticks()),
                            reschedule(action, coldProgress(job, nextDue)))
                    : coldTerminal(state, action, job, returned);
        }
        ResourceSiteHarvestGoal goal = ResourceSiteHarvestGoal.current(state, job);
        ActorLocation worker = state.actorLocations().get(job.workerId());
        if (worker == null || worker.condition().status() != ActorLifeStatus.ALIVE)
            throw new IllegalArgumentException("COLD field goal has no living retained worker");
        if (goal.arrivedAt(worker.supportingSurface())) {
            if (ResourceSiteHarvestGoal.actorAtWorkCell(state, job))
                return coldCropReceipt(state, action, job);
            if (goal.kind() == ResourceSiteHarvestGoal.Kind.DEPOT_SERVICE && ResourceSiteHarvestGoal.actorAtDepot(state, job))
                throw new IllegalStateException("depot arrival must have been handled above");
        }
        List<SurfaceAnchor> path;
        try {
            path = ResourceSiteHarvestKnownNavigation.path(state, job);
        } catch (ResourceSiteHarvestKnownNavigation.KnowledgeUnavailable unavailable) {
            if (goal.kind() == ResourceSiteHarvestGoal.Kind.WORK_CELL) {
                var alternate = ResourceSiteHarvestRetargeting.coldReachableWorkTarget(state, job);
                if (alternate.isPresent()) {
                    var retargeted = new ResourceSiteHarvestTargetRetargeted(job.siteId(), job.id(), job.workerId(),
                            goal.layoutRevision(), job.progress().nextCropSlotIndex(), alternate.getAsInt(),
                            action.id(), action.dueAt().ticks(), java.util.Optional.empty());
                    ResourceSiteHarvestRetargeting.reduceTargetRetargeted(state, job.siteId(), retargeted);
                    return List.of(new ProposedEvent(job.siteId(), retargeted),
                            reschedule(action, coldProgress(job, nextDue)));
                }
            }
            return coldGoalHold(state, job, action, nextDue, goal,
                    ResourceSiteHarvestNavigationBlock.Reason.KNOWN_GEOMETRY_UNAVAILABLE);
        }
        BodyPosition next = path.get(Math.min(1, path.size() - 1)).standingBody();
        ResourceSiteHarvestColdGoalAdvanced advanced = new ResourceSiteHarvestColdGoalAdvanced(
                job.id(), job.workerId(), goal.layoutRevision(), goal.nextWorkSlot(), goal.kind(),
                next, action.id(), action.dueAt().ticks());
        reduceColdGoalAdvanced(state, job.siteId(), advanced);
        return List.of(new ProposedEvent(job.siteId(), advanced),
                reschedule(action, coldProgress(job, nextDue)));
    }

    private static List<ProposedEvent> coldGoalHold(FrontierWorldState state, ResourceSiteHarvestJob job,
                                                     ScheduledAction action, long nextDue, ResourceSiteHarvestGoal goal,
                                                     ResourceSiteHarvestNavigationBlock.Reason reason) {
        ResourceSiteHarvestColdGoalHeld held = new ResourceSiteHarvestColdGoalHeld(
                job.siteId(), job.id(), job.workerId(), goal.layoutRevision(), goal.nextWorkSlot(),
                goal.kind(), goal.representative(), reason, action.id(), action.dueAt().ticks());
        reduceColdGoalHeld(state, job.siteId(), held);
        return List.of(new ProposedEvent(job.siteId(), held), reschedule(action, coldProgress(job, nextDue)));
    }

    private static List<ProposedEvent> coldTerminal(FrontierWorldState state, ScheduledAction action,
                                                    ResourceSiteHarvestJob returned, ProposedEvent terminalEvent) {
        PhysicalIntent terminalIntent = state.physicalIntents().get(returned.intentId());
        if (terminalIntent == null || (terminalIntent.status() != PhysicalIntentStatus.PREPARED
                && terminalIntent.status() != PhysicalIntentStatus.RUNNING))
            throw new IllegalArgumentException("resource-site COLD return has no admissible physical receipt state");
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(returned.siteId());
        ActorLocation actor = state.actorLocations().get(returned.workerId());
        if (actor == null || !ResourceSiteHarvestGoal.actorAtDepot(state, returned))
            throw new IllegalArgumentException("COLD terminal has no actual depot worker body");
        ResourceSiteLifecycle terminal = lifecycle.harvestedDeferred(returned,
                terminalIntent.status() == PhysicalIntentStatus.PREPARED,
                ResourceSiteHarvestCausality.notCaptured(returned), actor.body());
        StrategicTask task = task(state, returned.taskId(), StrategicTaskStatus.ACTIVE);
        List<ProposedEvent> events = new java.util.ArrayList<>();
        events.add(terminalEvent);
        if (state.resourceSites().cycle(returned.siteId()).harvestedCount() > returned.deliveredYieldQuantity())
            events.add(stockWake(state, returned, -1, action.dueAt().ticks()));
        events.add(transition(task, StrategicTaskStatus.COMPLETED));
        events.add(new ProposedEvent(returned.siteId(), new ScheduleEffect.Cancelled(action.id())));
        events.add(new ProposedEvent(returned.siteId(), new ScheduleEffect.Created(ResourceSiteProcess.nextGrowth(terminal,
                Math.addExact(action.dueAt().ticks(), state.bootstrap().ruleset().cadence().resourceGrowthStageInterval())))));
        return List.copyOf(events);
    }

    static ProposedEvent stockWake(FrontierWorldState state, ResourceSiteHarvestJob job,
                                   int deliveredBefore, long now) {
        SubjectId settlementId = site(state, job.siteId()).settlementId();
        return new ProposedEvent(settlementId, new ScheduleEffect.Created(
                StrategicObjectiveProcess.stockReconsideration(settlementId, job.id(), deliveredBefore,
                        Math.addExact(now, 1L))));
    }

    public static boolean coldProgressHeld(FrontierWorldState state, ScheduledAction action) {
        ResourceSiteHarvestJob job = activeJobAtSite(state, action.subject());
        return job != null && action.equals(coldProgress(job, action.dueAt().ticks()))
                && state.resourceSites().site(job.siteId()).phase() == ResourceSitePhase.HARVESTING
                && (state.resourceSites().hasPendingWorldChange(job.siteId())
                    || state.humanPopulation().meals().containsKey(job.workerId())
                    || FrontierResourceSiteHarvestSceneSupport.hasNonClosedScene(state, job)
                    || !FrontierSceneAdmission.available(state, List.of(job.workerId()))
                    || pendingPlayerBreakAtNextCell(state, job)
                    || loadedDepotCustodyBlocksDelivery(state, job));
    }

    /** COLD may not transfer an actor part into a chest held by a live physical custodian. */
    private static boolean loadedDepotCustodyBlocksDelivery(FrontierWorldState state, ResourceSiteHarvestJob job) {
        if (!(job.progress().complete() || job.returningForBatch()) || !state.inventory().fungibleResources().accounts()
                .containsKey(job.actorAccountId())) return false;
        SubjectId depot = FrontierWorldState.depotId(site(state, job.siteId()).settlementId());
        // The physical service goal may be any authorized depot station. A historical
        // route endpoint/cursor cannot decide whether COLD may cross the chest custody
        // boundary; hold the return while that chest has a live physical owner.
        return ReferenceContainerCustody.hasLiveCustody(state, depot);
    }

    static boolean batchDeliveryCapacityAvailable(FrontierWorldState state, ResourceSiteHarvestJob job) {
        return job.batchSuccessorSlot().isPresent()
                || state.firstFreeContainerSlot(job.outputSlot().containerId()).isPresent();
    }

    private static boolean pendingPlayerBreakAtNextCell(FrontierWorldState state, ResourceSiteHarvestJob job) {
        if (job.progress().complete()) return false;
        ResourceFieldCycle cycle = state.resourceSites().cycle(job.siteId());
        ResourceFieldLayout.CellId nextCell = cycle.layout().cells().get(job.progress().nextCropSlotIndex()).id();
        // A durable Vanilla removal permission is not yet a crop-loss observation.  Neither
        // travel onto the workstation nor a COLD receipt may overtake that unresolved effect.
        return cycle.pendingPlayerBreaks().containsKey(nextCell);
    }

    private static List<ProposedEvent> coldCropReceipt(FrontierWorldState state, ScheduledAction action, ResourceSiteHarvestJob job, ProposedEvent... prefix) {
        if (!ResourceSiteHarvestGoal.actorAtWorkCell(state, job))
            throw new IllegalArgumentException("resource-site COLD crop receipt requires its actual farmer at the current CellId station");
        ResourceFieldCycle field = state.resourceSites().cycle(job.siteId());
        ResourceFieldLayout.CellId cellId = field.layout().cells().get(job.progress().nextCropSlotIndex()).id();
        ResourceFieldCycle worked = field.worked(cellId, field.expectedWorkOutcome(cellId));
        int nextSelected = worked.nextWorkSlotAfter(job.progress().nextCropSlotIndex()).orElse(-1);
        ResourceSiteHarvestProgress progressed = job.progress().prepareNextCrop().confirmPreparedCrop(nextSelected);
        ResourceSiteHarvestJob replacement = job.withProgress(progressed);
        List<ProposedEvent> events = new java.util.ArrayList<>(List.of(prefix));
        events.add(new ProposedEvent(job.siteId(), new ResourceSiteHarvestCropPrepared(job.id(), job.progress().nextCropSlotIndex())));
        events.add(new ProposedEvent(job.siteId(), new ResourceSiteHarvestProgressed(job.siteId(), field.epoch(), job.id(), progressed.completedCropSlots(),
                field.layout().revision(), cellId, field.expectedWorkOutcome(cellId), action.id(), action.dueAt().ticks())));
        events.add(reschedule(action, coldProgress(replacement, Math.addExact(action.dueAt().ticks(), continuationInterval(state, replacement)))));
        return List.copyOf(events);
    }
}
