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
        if (state.structureConditions().get(site(state, lifecycle.siteId()).facilityId()) != StructureCondition.INTACT)
            return blocked(task);
        var admitted = admissions(state, task, action.dueAt().ticks());
        if (!admitted.isEmpty()) return admitted;
        if (!livingFarmerExists(state, task.ownerId())) return blocked(task);
        return List.of(reschedule(action, start(task, Math.addExact(action.dueAt().ticks(),
                state.bootstrap().ruleset().cadence().resourceHarvestRetryInterval()))));
    }

    /** A free participant joins the retained work pool, not a second strategic objective. */
    static List<ProposedEvent> expandActiveTask(FrontierWorldState state, StrategicTask task, long atTick) {
        if (task.kind() != StrategicTaskKind.HARVEST_RESOURCE_SITE || task.status() != StrategicTaskStatus.ACTIVE
                || !task.equals(state.strategicPlans().tasks().get(task.id())))
            throw new IllegalArgumentException("field expansion requires its exact active task");
        if (state.resourceSites().site(task.resourceSiteTarget().orElseThrow()).phase() != ResourceSitePhase.HARVESTING)
            return List.of();
        return admissions(state, task, atTick);
    }

    private static List<ProposedEvent> admissions(FrontierWorldState state, StrategicTask task, long atTick) {
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(task.resourceSiteTarget().orElseThrow());
        ResourceSite site = site(state, lifecycle.siteId()); Settlement settlement = settlement(state, site.settlementId());
        if (!task.ownerId().equals(settlement.id()) || !task.resourceSiteTarget().equals(java.util.Optional.of(site.id()))) {
            throw new IllegalArgumentException("resource-site harvest task has a foreign field owner");
        }
        if (state.structureConditions().get(site.facilityId()) != StructureCondition.INTACT) return List.of();
        var candidates = ResidentWorkComposition.SELECTION.eligible(state, settlement.id(),
                ResidentWorkKind.AGRICULTURE, HumanCapability.AGRICULTURE, atTick);
        var events = new java.util.ArrayList<ProposedEvent>();
        boolean activate = task.status() == StrategicTaskStatus.PENDING;
        FrontierWorldState projected = activate ? state.withStrategicPlans(
                state.strategicPlans().transitionTask(task.id(), StrategicTaskStatus.ACTIVE)) : state;
        ResidentWorkProvider<ResourceSiteHarvestJob> provider = provider(task, site);
        for (ResidentProfile farmer : candidates) {
            var offered = ResidentWorkComposition.SELECTION.offer(projected, farmer, atTick, provider);
            if (offered.isEmpty()) continue;
            ResourceSiteHarvestJob job = offered.orElseThrow().execution();
            PhysicalIntent intent = intent(site, job);
            if (events.isEmpty() && activate) events.add(transition(task, StrategicTaskStatus.ACTIVE));
            events.add(new ProposedEvent(site.id(), new ResourceSiteHarvestStarted(job)));
            events.add(new ProposedEvent(site.id(), new PhysicalIntentPrepared(intent)));
            long due = Math.addExact(atTick, state.bootstrap().ruleset().resourceHarvestColdTravelTicksPerEdge());
            events.add(schedule(coldProgress(job, due)));
            // Validate subsequent offers against exactly the successor produced by admission.
            // This is immutable transaction planning, not a parallel reservation cache.
            projected = admitStarted(projected, site.id(), new ResourceSiteHarvestStarted(job))
                    .preparePhysicalIntent(intent);
        }
        return List.copyOf(events);
    }

    private static ResidentWorkProvider<ResourceSiteHarvestJob> provider(StrategicTask task, ResourceSite site) {
        return new ResidentWorkProvider<>() {
            @Override public ResidentWorkKind kind() { return ResidentWorkKind.AGRICULTURE; }
            @Override public HumanCapability capability() { return HumanCapability.AGRICULTURE; }
            @Override public java.util.Optional<ResidentWorkOffer<ResourceSiteHarvestJob>> discover(
                    FrontierWorldState state, ResidentProfile resident, long atTick) {
                return offer(state, task, site, resident, atTick).map(job -> new ResidentWorkOffer<>(
                        kind(), resident.id(), job, List.of(new WorkReservationClaim.Cell(job.target()),
                                new WorkReservationClaim.ContainerCapacity(job.outputSlot()))));
            }
        };
    }

    static SettlementStaffingPort.Demand staffingDemand(FrontierWorldState state, SubjectId home, SettlementLabourRules.Entry rules) {
        var fields = state.resourceSiteDescriptors().values().stream().filter(site -> site.settlementId().equals(home))
                .filter(site -> state.structureConditions().get(site.facilityId()) == StructureCondition.INTACT).toList();
        boolean workable = fields.stream().anyMatch(site -> state.resourceSites().site(site.id()).phase() == ResourceSitePhase.READY
                || state.resourceSites().site(site.id()).phase() == ResourceSitePhase.HARVESTING);
        var retained = state.resourceSites().sites().values().stream().flatMap(site -> site.harvestJobs().values().stream())
                .filter(job -> state.resourceSite(job.siteId()).settlementId().equals(home))
                .map(ResourceSiteHarvestJob::workerId).collect(java.util.stream.Collectors.toSet());
        int reserve = fields.isEmpty() ? 0 : rules.minimumLocalStaff();
        return new SettlementStaffingPort.Demand(ResidentWorkKind.AGRICULTURE, HumanCapability.AGRICULTURE,
                workable ? rules.targetWorkers() : reserve, reserve, rules.priority(), retained);
    }

    /** Family-owned executable opportunity; this probe creates no assignment, movement or reservation. */
    static boolean availableWork(FrontierWorldState state, ResidentProfile resident, long atTick) {
        if (state.firstFreeContainerSlot(FrontierWorldState.depotId(resident.settlementId())).isEmpty()) return false;
        for (var task : state.strategicPlans().tasks().values().stream()
                .filter(task -> task.ownerId().equals(resident.settlementId())
                        && task.kind() == StrategicTaskKind.HARVEST_RESOURCE_SITE
                        && (task.status() == StrategicTaskStatus.PENDING || task.status() == StrategicTaskStatus.ACTIVE))
                .sorted(java.util.Comparator.comparing(StrategicTask::id)).toList()) {
            var lifecycle = state.resourceSites().site(task.resourceSiteTarget().orElseThrow());
            if (lifecycle.phase() != ResourceSitePhase.READY && lifecycle.phase() != ResourceSitePhase.HARVESTING) continue;
            var site = site(state, lifecycle.siteId());
            if (state.structureConditions().get(site.facilityId()) != StructureCondition.INTACT) continue;
            var projected = task.status() == StrategicTaskStatus.PENDING
                    ? state.withStrategicPlans(state.strategicPlans().transitionTask(task.id(), StrategicTaskStatus.ACTIVE)) : state;
            if (offer(projected, task, site, resident, atTick).isPresent()) return true;
        }
        return false;
    }

    private static java.util.Optional<ResourceSiteHarvestJob> offer(FrontierWorldState state, StrategicTask task,
            ResourceSite site, ResidentProfile farmer, long tick) {
        if (!ResidentActivityCoordinator.mayStartOrdinaryWork(state, farmer.id(), tick)
                || !ActorExecutionCoordinator.ordinaryWorkAdmission(state, farmer.id()).permitted())
            return java.util.Optional.empty();
        SubjectId depot = FrontierWorldState.depotId(site.settlementId());
        OptionalInt slot = state.firstFreeContainerSlot(depot);
        if (slot.isEmpty()) return java.util.Optional.empty();
        ActorLocation worker = state.actorLocations().get(farmer.id());
        if (worker == null) throw new IllegalArgumentException("resource-site harvest worker has no canonical body");
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(site.id());
        var field = state.resourceSites().cycle(site.id());
        ResourceSiteHarvestJob job = job(site, lifecycle, task, farmer,
                new InventoryCustody.ContainerSlot(depot, slot.getAsInt()), field);
        if (!ActorCarriedResources.canAddAccount(state.inventory().fungibleResources(),
                farmer.id(), job.actorAccountId())) return java.util.Optional.empty();
        var selected = lifecycle.selectHarvestStart(job, field, worker.supportingSurface(), index -> actionable(field, index));
        if (selected.isEmpty()) return java.util.Optional.empty();
        job = job.withProgress(job.progress().withSelectedCropSlot(selected.getAsInt())).bindTarget(field);
        var outcome = field.expectedWorkOutcome(field.layout().cells().get(selected.getAsInt()).id());
        if (outcome != ResourceFieldCycle.WorkOutcome.SKIPPED_BLOCKED
                && outcome != ResourceFieldCycle.WorkOutcome.SKIPPED_IMMATURE) {
            FrontierWorldState admitted = state.withResourceSites(state.resourceSites().replace(lifecycle.harvesting(job)));
            try {
                ResourceSiteHarvestKnownNavigation.path(admitted, job);
            } catch (ResourceSiteHarvestKnownNavigation.KnowledgeUnavailable unavailable) {
                var alternate = ResourceSiteHarvestRetargeting.coldReachableWorkTarget(admitted, job);
                if (alternate.isEmpty()) return java.util.Optional.empty();
                job = job.withProgress(job.progress().withSelectedCropSlot(alternate.getAsInt())).bindTarget(field);
            }
        }
        return java.util.Optional.of(job);
    }

    private static boolean actionable(ResourceFieldCycle field, int index) {
        var cell = field.layout().cells().get(index).id();
        var condition = field.cell(cell);
        return !field.pendingPlayerBreaks().containsKey(cell) && !condition.workAccessBlocked()
                && (condition.crop() == ResourceFieldCycle.Crop.MATURE
                    || condition.crop() == ResourceFieldCycle.Crop.ABSENT
                        && (condition.soil() == ResourceFieldCycle.Soil.FARMLAND
                            || condition.soil() == ResourceFieldCycle.Soil.DIRT));
    }

    private static boolean livingFarmerExists(FrontierWorldState state, SubjectId settlementId) {
        return state.humanPopulation().residents().values().stream()
                .anyMatch(resident -> resident.settlementId().equals(settlementId)
                        && SettlementWorkPolicy.permissions(state, settlementId).permits(ResidentWorkKind.AGRICULTURE, resident.id())
                        && state.actorLocations().containsKey(resident.id())
                        && state.actorLocations().get(resident.id()).condition().status() == ActorLifeStatus.ALIVE);
    }

    /**
     * One deterministic COLD step of the exact harvest worker. The action declares its site
     * owner; its stable ID still identifies the job, so HOT hand-off and later release resume
     * the same due action rather than creating an auxiliary scene journey.
     */
    public static ScheduledAction coldProgress(ResourceSiteHarvestJob job, long dueAt) {
        return ResourceSiteHarvestContinuation.at(job, dueAt);
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
    public static List<ProposedEvent> planColdProgress(FrontierWorldState state, ScheduledAction action, long currentTick) {
        ResourceSiteHarvestJob job = jobForContinuation(state, action);
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
        long now = Math.max(action.dueAt().ticks(), currentTick);
        if (!state.humanPopulation().meals().containsKey(job.workerId())
                && (!state.actorExecutions().owns(job.workerId(),
                        io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.FIELD_HARVEST, job.id())
                    || !ResidentActivityCoordinator.ordinaryWorkPermitted(state, job.workerId(), now))) {
            var paused = new java.util.ArrayList<ProposedEvent>();
            if (job.progress().work().filter(WorkProgress::running).isPresent()
                    && !FrontierResourceSiteHarvestSceneSupport.hasNonClosedScene(state, job)
                    && ActorExecutionCoordinator.coldAvailable(state, job.workerId()))
                paused.add(new ProposedEvent(job.siteId(), ResourceSiteHarvestWorkProcess.change(state, job, now, false,
                        action, java.util.Optional.empty())));
            paused.add(reschedule(action, coldProgress(job, ResidentActivityCoordinator.nextOrdinaryWorkCheck(
                    state, job.workerId(), now))));
            return List.copyOf(paused);
        }
        // A HOT scene held this same action while the body moved physically. Its
        // overdue due instant is binding evidence, not permission to replay
        // unobserved COLD travel or labor in a rapid catch-up burst.
        long nextDue = Math.addExact(now, continuationInterval(state, job));
        if (coldProgressHeld(state, action)) {
            // The current engine action is also the HOT checkpoint's only binding.  Advancing its
        // due instant while another owner holds this step (a HOT scene or a pending player
        // removal at the next cell) makes its eventual observation wait for an unrelated
        // cadence turn. Preserve the action byte-for-byte until that owner closes it.
            return List.of(reschedule(action, action));
        }
        ResourceFieldCycle currentField = state.resourceSites().cycle(job.siteId());
        if (job.navigationBlock().filter(block -> !block.reroutable()).isPresent()) {
            // Physical/support holds need exact clearance. A local HOT path failure
            // may be reconsidered by COLD only after the scene releases its worker.
            return List.of(reschedule(action, coldProgress(job, nextDue)));
        }
        if (!job.progress().complete() && !job.returningForBatch() && !job.progress().hasPendingCrop()) {
            ResourceFieldLayout.Cell nextCell = currentField.layout().cells().get(job.progress().nextCropSlotIndex());
            if (currentField.expectedWorkOutcome(nextCell.id()) == ResourceFieldCycle.WorkOutcome.SKIPPED_IMMATURE
                    && !currentField.pendingPlayerBreaks().containsKey(nextCell.id())) {
                var skipped = new ResourceSiteHarvestImmatureCellSkipped(job.siteId(), job.id(), job.workerId(),
                        currentField.layout().revision(), List.of(nextCell.id()), action.id(), action.dueAt().ticks(), java.util.Optional.empty());
                FrontierWorldState after = reduceCellSkipped(state, job.siteId(), skipped);
                var nextJob = after.resourceSites().site(job.siteId()).harvestJob(job.id()).orElseThrow();
                return List.of(new ProposedEvent(job.siteId(), skipped),
                        reschedule(action, coldProgress(nextJob, Math.addExact(now, continuationInterval(after, nextJob)))));
            }
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
            if (!HarvestServiceAccess.available(state, job))
                return List.of(reschedule(action, coldProgress(job, nextDue)));
            if (job.returningForBatch() && !batchDeliveryCapacityAvailable(state, job))
                return List.of(reschedule(action, coldProgress(job, nextDue)));
            ProposedEvent returned = new ProposedEvent(job.siteId(), new ResourceSiteHarvestReturned(job.id(), job.workerId(),
                    action.id(), action.dueAt().ticks()));
            return job.returningForBatch()
                    ? List.of(returned, stockWake(state, job, job.deliveredYieldQuantity(), now),
                            reschedule(action, coldProgress(job, nextDue)))
                    : coldTerminal(state, action, job, returned, now);
        }
        ResourceSiteHarvestGoal goal = ResourceSiteHarvestGoal.current(state, job);
        ActorLocation worker = state.actorLocations().get(job.workerId());
        if (worker == null || worker.condition().status() != ActorLifeStatus.ALIVE)
            throw new IllegalArgumentException("COLD field goal has no living retained worker");
        if (job.navigationBlock().isEmpty() && goal.arrivedAt(worker.supportingSurface())) {
            if (ResourceSiteHarvestGoal.actorAtWorkCell(state, job)) {
                var work = job.progress().work();
                if (work.isEmpty() || !work.orElseThrow().complete()) {
                    if (work.filter(WorkProgress::running).isPresent() && now < work.orElseThrow().activeUntilTick())
                        return List.of(reschedule(action, coldProgress(job, work.orElseThrow().activeUntilTick())));
                    boolean run = work.isEmpty() || !work.orElseThrow().running();
                    var changed = ResourceSiteHarvestWorkProcess.change(state, job, now, run, action, java.util.Optional.empty());
                    var events = new java.util.ArrayList<>(ResourceSiteHarvestWorkProcess.events(state, changed, action));
                    if (!run) events.add(reschedule(action, coldProgress(job, Math.addExact(now, 1L))));
                    return List.copyOf(events);
                }
                return coldCropReceipt(state, action, job, now);
            }
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
            // Preserve the exact prior HOT hold while no COLD alternative is known;
            // a second hold event would be invalid and would hide the original cause.
            if (job.navigationBlock().isPresent())
                return List.of(reschedule(action, coldProgress(job, nextDue)));
            return coldGoalHold(state, job, action, nextDue, goal,
                    ResourceSiteHarvestNavigationBlock.Reason.KNOWN_GEOMETRY_UNAVAILABLE);
        }
        BodyPosition next = path.get(Math.min(1, path.size() - 1)).standingBody();
        if (goal.kind() == ResourceSiteHarvestGoal.Kind.DEPOT_SERVICE
                && !HarvestServiceAccess.mayAdvance(state, job, next.supportingSurface()))
            return List.of(reschedule(action, coldProgress(job, nextDue)));
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
                                                    ResourceSiteHarvestJob returned, ProposedEvent terminalEvent, long now) {
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
                ResourceSiteHarvestCausality.notCaptured(returned), actor.body(),
                ResourceSiteHarvestHistoryRetention.reclaimable(state, lifecycle, returned.workerId()));
        StrategicTask task = task(state, returned.taskId(), StrategicTaskStatus.ACTIVE);
        List<ProposedEvent> events = new java.util.ArrayList<>();
        events.add(terminalEvent);
        if (returned.undeliveredYieldQuantity() > 0)
            events.add(stockWake(state, returned, -1, now));
        if (terminal.harvestJobs().values().stream().noneMatch(sibling -> sibling.taskId().equals(task.id())))
            events.add(transition(task, StrategicTaskStatus.COMPLETED));
        events.add(new ProposedEvent(returned.siteId(), new ScheduleEffect.Cancelled(action.id())));
        events.addAll(ResourceFieldGrowthProcess.afterWork(state, terminal,
                state.resourceSites().cycle(returned.siteId()), returned.id(), now));
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
        ResourceSiteHarvestJob job = jobForContinuation(state, action);
        return job != null && action.equals(coldProgress(job, action.dueAt().ticks()))
                && state.resourceSites().site(job.siteId()).phase() == ResourceSitePhase.HARVESTING
                && (job.progress().acceptance().isPresent()
                    || state.resourceSites().hasPendingWorldChange(job.siteId())
                    || state.humanPopulation().meals().containsKey(job.workerId())
                    || state.actorMovements().containsKey(job.workerId())
                    || !ActorExecutionCoordinator.coldAvailable(state, job.workerId())
                    || pendingPlayerBreakAtNextCell(state, job)
                    || loadedDepotCustodyBlocksDelivery(state, job)
                    || serviceEntryHeld(state, job)
                    || (ResourceSiteHarvestGoal.actorAtDepot(state, job) && job.returningForBatch()
                        && !batchDeliveryCapacityAvailable(state, job))
                    || job.navigationBlock().filter(block -> !block.reroutable()).isPresent());
    }

    private static boolean serviceEntryHeld(FrontierWorldState state, ResourceSiteHarvestJob job) {
        if (ResourceSiteHarvestGoal.current(state, job).kind() != ResourceSiteHarvestGoal.Kind.DEPOT_SERVICE
                || HarvestServiceAccess.available(state, job)) return false;
        if (ResourceSiteHarvestGoal.actorAtDepot(state, job)) return true;
        try {
            var path = ResourceSiteHarvestKnownNavigation.path(state, job);
            return !HarvestServiceAccess.mayAdvance(state, job, path.get(Math.min(1, path.size() - 1)));
        } catch (ResourceSiteHarvestKnownNavigation.KnowledgeUnavailable unavailable) {
            return false; // The planner must record the exact knowledge hold, not park it as a service wait.
        }
    }

    /** Exact dependencies of an admitted field continuation; parking never changes its due time. */
    static java.util.Set<SubjectId> coldProgressWakeKeys(FrontierWorldState state, ScheduledAction action) {
        var job = jobForContinuation(state, action);
        if (job == null) return java.util.Set.of(action.subject());
        var settlement = site(state, job.siteId()).settlementId();
        return java.util.Set.of(job.id(), job.siteId(), job.workerId(), settlement,
                FrontierWorldState.depotId(settlement));
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
        return ResourceSiteHarvestCargo.deliveryCapacityAvailable(state, job);
    }

    private static boolean pendingPlayerBreakAtNextCell(FrontierWorldState state, ResourceSiteHarvestJob job) {
        if (job.progress().complete()) return false;
        ResourceFieldCycle cycle = state.resourceSites().cycle(job.siteId());
        ResourceFieldLayout.CellId nextCell = cycle.layout().cells().get(job.progress().nextCropSlotIndex()).id();
        // A durable Vanilla removal permission is not yet a crop-loss observation.  Neither
        // travel onto the workstation nor a COLD receipt may overtake that unresolved effect.
        return cycle.pendingPlayerBreaks().containsKey(nextCell);
    }

    private static List<ProposedEvent> coldCropReceipt(FrontierWorldState state, ScheduledAction action,
                                                        ResourceSiteHarvestJob job, long now, ProposedEvent... prefix) {
        if (!ResourceSiteHarvestGoal.actorAtWorkCell(state, job))
            throw new IllegalArgumentException("resource-site COLD crop receipt requires its actual farmer at the current CellId station");
        ResourceFieldCycle field = state.resourceSites().cycle(job.siteId());
        ResourceFieldLayout.CellId cellId = field.layout().cells().get(job.progress().nextCropSlotIndex()).id();
        ResourceFieldCycle worked = field.worked(cellId, field.expectedWorkOutcome(cellId));
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(job.siteId());
        int nextSelected = lifecycle.nextHarvestTarget(job, worked);
        ResourceSiteHarvestProgress progressed = job.progress().prepareNextCrop().confirmPreparedCrop(nextSelected);
        ResourceSiteHarvestJob replacement = job.withProgress(progressed).bindTarget(worked);
        List<ProposedEvent> events = new java.util.ArrayList<>(List.of(prefix));
        events.add(new ProposedEvent(job.siteId(), new ResourceSiteHarvestCropPrepared(job.id(), job.progress().nextCropSlotIndex(), job.target().generation())));
        events.add(new ProposedEvent(job.siteId(), new ResourceSiteHarvestProgressed(job.siteId(), field.epoch(), job.id(), progressed.completedCropSlots(),
                field.layout().revision(), cellId, job.target().generation(), field.expectedWorkOutcome(cellId), action.id(), action.dueAt().ticks())));
        events.add(reschedule(action, coldProgress(replacement, Math.addExact(now, continuationInterval(state, replacement)))));
        return List.copyOf(events);
    }

    static FrontierWorldState admitStarted(FrontierWorldState state, SubjectId subject, ResourceSiteHarvestStarted started) {
        ResourceSiteHarvestJob job = started.job(); if (!subject.equals(job.siteId())) throw new IllegalArgumentException("resource-site harvest has a foreign event owner");
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(job.siteId()); validateJob(state, lifecycle, job);
        ResourceSite descriptor = state.resourceSite(job.siteId());
        SettlementCommitmentComposition.ADMISSION.requireParticipants(state, new SettlementCommitmentAdmission.Request(
                job.taskId(), descriptor.settlementId(), descriptor.facilityId(), List.of(job.workerId())));
        var execution = state.actorExecutions().next(job.workerId(),
                io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.FIELD_HARVEST, job.id());
        return ActorExecutionComposition.LIFECYCLE.prepareVacant(state, execution).commit(state,
                FrontierWorldStateUpdate.begin().resourceSites(state.resourceSites().replace(lifecycle.harvesting(job))));
    }
}
