package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.model.*;

import io.farfrontier.palemirror.frontier.v3.api.FixedPosition;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
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
import java.util.OptionalInt;

/**
 * Plans one exact 64-cell harvest through the physical-intent boundary.
 *
 * <p>A mature field reserves its farmer, depot slot and output identity in canonical state. Its
 * COLD driver advances the same exact farmer and retained approach cursor until the next physical
 * crop station; its HOT driver performs only observed crop work. It produces neither crop nor
 * inventory until the naturally loaded executor observes the whole field reset and the exact
 * tagged stack in that reserved chest slot. F0.2 will give off-screen irreversible crop work its
 * semantic-consequence/deferred-aftermath boundary; F0.1 does not counterfeit that effect.</p>
 */
public final class ResourceSiteHarvestProcess {
    public static final String COLD_PROGRESS_KIND = "frontier.resource_site.harvest.cold_progress";

    private ResourceSiteHarvestProcess() { }

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
        ResidentProfile farmer = FrontierWorldStateSupport.availableFieldResident(state, settlement.id(), ResidentProfession.AGRICULTURAL_WORKER).orElse(null);
        if (farmer == null) return blocked(task);
        SubjectId depot = FrontierWorldState.depotId(settlement.id());
        OptionalInt slot = state.firstFreeContainerSlot(depot); if (slot.isEmpty()) return blocked(task);
        ResourceSiteHarvestJob job = job(state, lifecycle, task, farmer, new InventoryCustody.ContainerSlot(depot, slot.getAsInt()));
        PhysicalIntent intent = intent(site, job);
        long firstColdStep = Math.addExact(action.dueAt().ticks(), state.bootstrap().ruleset().cadence().resourceHarvestRetryInterval());
        return List.of(transition(task, StrategicTaskStatus.ACTIVE), new ProposedEvent(lifecycle.siteId(), new ResourceSiteHarvestStarted(job)),
                new ProposedEvent(lifecycle.siteId(), new PhysicalIntentPrepared(intent)), schedule(coldProgress(job, firstColdStep)));
    }

    /**
     * One deterministic COLD step of the exact harvest worker. The action has the job's stable
     * identity, so HOT hand-off and later release resume the same due action rather than creating
     * an auxiliary scene cursor or a second worker journey.
     */
    public static ScheduledAction coldProgress(ResourceSiteHarvestJob job, long dueAt) {
        return new ScheduledAction(new ScheduleId("schedule:resource-site-harvest-cold-progress-"
                + job.id().value().substring("job:".length())), new SimInstant(dueAt), 0, job.id(),
                COLD_PROGRESS_KIND, 1);
    }

    /**
     * Advances only the retained pedestrian approach while no harvest scene owns the worker.
     * Reaching a crop station is deliberately a physical-effect boundary, not permission to
     * remove an unloaded crop or create its wheat receipt.
     */
    public static List<ProposedEvent> planColdProgress(FrontierWorldState state, ScheduledAction action) {
        ResourceSiteHarvestJob job = activeJob(state, action.subject());
        if (job == null || !action.id().equals(coldProgress(job, action.dueAt().ticks()).id())) return List.of();
        long nextDue = Math.addExact(action.dueAt().ticks(), state.bootstrap().ruleset().cadence().resourceHarvestRetryInterval());
        if (FrontierResourceSiteHarvestSceneSupport.hasNonClosedScene(state, job.id())) {
            return List.of(reschedule(action, coldProgress(job, nextDue)));
        }
        // An ambient lease is already a physical-authority hand-off in progress.  Even a
        // PREPARED body cannot coexist with speculative COLD cursor movement: the registered
        // HOT behavior must either adopt that exact body or leave the retained cursor intact.
        if (!FrontierSceneAdmission.available(state, List.of(job.workerId()))) {
            return List.of(reschedule(action, coldProgress(job, nextDue)));
        }
        // Keep the stable due action while the worker is waiting at the next semantic
        // crop station. A later HOT lease can therefore fence/reschedule this exact action
        // on release instead of inventing a second COLD schedule.
        if (!job.hasNextTraversalStep()) return List.of(reschedule(action, coldProgress(job, nextDue)));
        return List.of(new ProposedEvent(job.siteId(), new ResourceSiteHarvestColdTraversalAdvanced(job.id(), job.workerId(), job.traversalCursor() + 1)),
                reschedule(action, coldProgress(job, nextDue)));
    }

    public static FrontierWorldState reduceStarted(FrontierWorldState state, SubjectId subject, ResourceSiteHarvestStarted started) {
        ResourceSiteHarvestJob job = started.job(); if (!subject.equals(job.siteId())) throw new IllegalArgumentException("resource-site harvest has a foreign event owner");
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(job.siteId()); validateJob(state, lifecycle, job);
        return state.withResourceSites(state.resourceSites().replace(lifecycle.harvesting(job)));
    }

    public static FrontierWorldState reducePrepared(FrontierWorldState state, SubjectId subject, PhysicalIntent intent) {
        if (intent.kind() != PhysicalIntentKind.RESOURCE_SITE_HARVEST || !subject.equals(intent.causeSubjectId())) throw new IllegalArgumentException("resource-site harvest intent is invalid");
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(intent.causeSubjectId()); validateIntent(state, lifecycle, intent);
        return state.preparePhysicalIntent(intent);
    }

    public static FrontierWorldState reduceProgressed(FrontierWorldState state, SubjectId subject, ResourceSiteHarvestProgressed progressed) {
        ResourceSiteLifecycle lifecycle = state.resourceSites().sites().values().stream().filter(value -> value.activeWork()
                .filter(ResourceSiteHarvestJob.class::isInstance).map(ResourceSiteHarvestJob.class::cast)
                .map(job -> job.id().equals(progressed.jobId())).orElse(false)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("resource-site harvest progress has no active job"));
        ResourceSiteHarvestJob job = lifecycle.activeWork().filter(ResourceSiteHarvestJob.class::isInstance).map(ResourceSiteHarvestJob.class::cast).orElseThrow();
        if (!subject.equals(lifecycle.siteId())) {
            throw new IllegalArgumentException("resource-site harvest progress has a foreign owner");
        }
        return state.withResourceSites(state.resourceSites().replace(lifecycle.advanceHarvest(job, progressed.completedCropSlots())));
    }

    public static FrontierWorldState reduceCropPrepared(FrontierWorldState state, SubjectId subject, ResourceSiteHarvestCropPrepared prepared) {
        ResourceSiteLifecycle lifecycle = state.resourceSites().sites().values().stream().filter(value -> value.activeWork()
                .filter(ResourceSiteHarvestJob.class::isInstance).map(ResourceSiteHarvestJob.class::cast)
                .map(job -> job.id().equals(prepared.jobId())).orElse(false)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("resource-site harvest crop preparation has no active job"));
        ResourceSiteHarvestJob job = lifecycle.activeWork().filter(ResourceSiteHarvestJob.class::isInstance).map(ResourceSiteHarvestJob.class::cast).orElseThrow();
        if (!subject.equals(lifecycle.siteId())) throw new IllegalArgumentException("resource-site harvest crop preparation has a foreign owner");
        if (!job.atCurrentCropStation()) throw new IllegalArgumentException("resource-site harvest crop preparation requires its exact retained workstation");
        return state.withResourceSites(state.resourceSites().replace(lifecycle.prepareHarvestCrop(job, prepared.cropSlotIndex())));
    }

    /**
     * Advances one retained semantic edge without physical authority.  COLD may never forge a
     * lease or observed Minecraft body: it proves the current actor/cursor directly from the
     * canonical job, then atomically moves that same actor with the job cursor.
     */
    public static FrontierWorldState reduceColdTraversalAdvanced(FrontierWorldState state, SubjectId subject,
                                                                  ResourceSiteHarvestColdTraversalAdvanced advanced) {
        HarvestTraversal current = requireTraversal(state, subject, advanced.jobId(), advanced.workerId(), advanced.nextCursor());
        if (FrontierResourceSiteHarvestSceneSupport.hasNonClosedScene(state, current.job().id())) {
            throw new IllegalArgumentException("resource-site COLD traversal cannot advance while its physical lease is retained");
        }
        if (!FrontierSceneAdmission.available(state, List.of(current.job().workerId()))) {
            throw new IllegalArgumentException("resource-site COLD traversal cannot advance while its ambient authority is retained");
        }
        BodyPosition retained = current.job().traversal().linearCorridorSurfaces().get(current.job().traversalCursor()).standingBody();
        ActorLocation actor = state.actorLocations().get(current.job().workerId());
        if (actor == null || !actor.body().equals(retained)) {
            throw new IllegalArgumentException("resource-site COLD traversal actor diverges from its retained cursor");
        }
        ResourceSiteHarvestJob replacement = current.job().advanceTraversal(advanced.nextCursor());
        BodyPosition next = replacement.traversal().linearCorridorSurfaces().get(replacement.traversalCursor()).standingBody();
        java.util.Map<SubjectId, ActorLocation> actors = new java.util.LinkedHashMap<>(state.actorLocations());
        actors.put(replacement.workerId(), actor.withBody(next));
        return state.withChanges(FrontierWorldStateUpdate.begin()
                .actorLocations(actors)
                .resourceSites(state.resourceSites().replace(current.lifecycle().advanceHarvestTraversal(current.job(), advanced.nextCursor()))));
    }

    /**
     * Commits a loaded checkpoint from one exact HOT lease.  The same semantic job transition as
     * COLD is applied, but its observed body is additionally the lease recovery checkpoint and
     * the only canonical actor position.  No partial job/lease/actor state can be installed.
     */
    public static FrontierWorldState reduceHotTraversalAdvanced(FrontierWorldState state, SubjectId subject,
                                                                 ResourceSiteHarvestHotTraversalAdvanced advanced) {
        HarvestTraversal current = requireTraversal(state, subject, advanced.jobId(), advanced.workerId(), advanced.nextCursor());
        return FrontierResourceSiteHarvestSceneSupport.advanceWorker(state, current.lifecycle(), current.job(),
                current.replacement(), advanced.leaseId(), advanced.observedWorker());
    }

    /** Shared semantic precondition for both COLD and HOT executions of one retained edge. */
    private static HarvestTraversal requireTraversal(FrontierWorldState state, SubjectId subject, SubjectId jobId,
                                                      SubjectId workerId, int nextCursor) {
        ResourceSiteLifecycle lifecycle = state.resourceSites().sites().values().stream().filter(value -> value.activeWork()
                .filter(ResourceSiteHarvestJob.class::isInstance).map(ResourceSiteHarvestJob.class::cast)
                .map(job -> job.id().equals(jobId)).orElse(false)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("resource-site field-work traversal has no active job"));
        ResourceSiteHarvestJob job = lifecycle.activeWork().filter(ResourceSiteHarvestJob.class::isInstance).map(ResourceSiteHarvestJob.class::cast).orElseThrow();
        if (!subject.equals(lifecycle.siteId()) || !job.workerId().equals(workerId)) {
            throw new IllegalArgumentException("resource-site field-work traversal has a foreign owner or worker");
        }
        ResourceSiteHarvestJob replacement = job.advanceTraversal(nextCursor);
        return new HarvestTraversal(lifecycle, job, replacement);
    }

    private record HarvestTraversal(ResourceSiteLifecycle lifecycle, ResourceSiteHarvestJob job,
                                    ResourceSiteHarvestJob replacement) { }

    /**
     * An ambient body is free to complete a sub-cell local turn between harvest admission and
     * the first loaded scene tick.  The durable hand-off observation, rather than that stale
     * admission sample, is therefore the only correct start of an otherwise unstarted field
     * topology.  Once COLD or HOT has retained an edge, a later hand-off must prove the exact
     * retained station and may not rebase it.
     */
    public static FrontierWorldState rebaseForAmbientHandoff(FrontierWorldState state, SubjectId subject,
                                                              ResourceSiteHarvestSceneLeaseHandoff handoff) {
        ResourceSiteHarvestJob job = FrontierResourceSiteHarvestSceneSupport.require(state, FrontierSceneBehaviors.resourceSiteHarvest(handoff.lease()));
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(job.siteId());
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
        if (job.progress().completedCropSlots() == 0 && !job.progress().hasPendingCrop() && job.traversalCursor() == 0) {
            TraversalTopology rebased = ResourceSiteHarvestTraversal.compile(state.bootstrap(), site,
                    new ActorLocation(capture.body(), current.condition()), job.id());
            return state.withResourceSites(state.resourceSites().replace(lifecycle.rebaseUnstartedHarvestTraversal(job, rebased)));
        }
        BodyPosition retained = job.traversal().linearCorridorSurfaces().get(job.traversalCursor()).standingBody();
        if (!retained.equals(current.body()) || !retained.equals(capture.body())) {
            throw new IllegalArgumentException("resource-site field-work hand-off must retain its exact COLD/HOT cursor station");
        }
        return state;
    }

    public static List<ProposedEvent> planTransition(FrontierWorldState state, PhysicalIntent intent, PhysicalIntentTransition transition, long now) {
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(intent.causeSubjectId()); validateBinding(state, lifecycle, intent);
        ResourceSiteHarvestJob job = harvest(lifecycle, intent.id()); StrategicTask task = task(state, job.taskId(), StrategicTaskStatus.ACTIVE);
        if (transition.status() == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART) {
            return List.of(new ProposedEvent(lifecycle.siteId(), transition), transition(task, StrategicTaskStatus.BLOCKED));
        }
        if (transition.status() != PhysicalIntentStatus.CONFIRMED) return List.of(new ProposedEvent(lifecycle.siteId(), transition));
        if (!job.progress().complete()) throw new IllegalArgumentException("resource-site harvest output cannot complete before every crop is observed");
        ResourceSiteLifecycle next = lifecycle.harvested();
        return List.of(new ProposedEvent(lifecycle.siteId(), transition), transition(task, StrategicTaskStatus.COMPLETED), new ProposedEvent(lifecycle.siteId(),
                new ScheduleEffect.Created(ResourceSiteProcess.nextGrowth(next, Math.addExact(now,
                        state.bootstrap().ruleset().cadence().resourceGrowthStageInterval())))));
    }

    public static void validateIntent(FrontierWorldState state, ResourceSiteLifecycle lifecycle, PhysicalIntent intent) {
        if (intent.status() != PhysicalIntentStatus.PREPARED) throw new IllegalArgumentException("resource-site harvest must begin prepared");
        validateBinding(state, lifecycle, intent);
    }

    public static void validateBinding(FrontierWorldState state, ResourceSiteLifecycle lifecycle, PhysicalIntent intent) {
        ResourceSiteHarvestJob job = harvest(lifecycle, intent.id()); ResourceSite site = site(state, lifecycle.siteId());
        BlockPosition origin = site.cropSlots().getFirst(); FixedPosition expected = new FixedPosition(FixedScalar.whole(origin.x()), FixedScalar.whole(origin.y()), FixedScalar.whole(origin.z()));
        if (!intent.subjectIds().equals(List.of(job.siteId(), job.id(), job.workerId(), job.outputItemId()))
                || !intent.origin().equals(expected) || intent.radiusBlocks() != 0 || intent.postcondition() != PhysicalPostcondition.RESOURCE_SITE_HARVESTED_OBSERVED) {
            throw new IllegalArgumentException("resource-site harvest intent does not exactly bind its mature field and output");
        }
    }

    public static ResourceSiteHarvestJob harvest(ResourceSiteLifecycle lifecycle, PhysicalIntentId intentId) {
        return lifecycle.activeWork().filter(ResourceSiteHarvestJob.class::isInstance).map(ResourceSiteHarvestJob.class::cast)
                .filter(job -> job.intentId().equals(intentId)).orElseThrow(() -> new IllegalArgumentException("resource-site harvest has no matching active work"));
    }

    private static ResourceSiteHarvestJob activeJob(FrontierWorldState state, SubjectId jobId) {
        return state.resourceSites().sites().values().stream().map(ResourceSiteLifecycle::activeWork).flatMap(java.util.Optional::stream)
                .filter(ResourceSiteHarvestJob.class::isInstance).map(ResourceSiteHarvestJob.class::cast)
                .filter(job -> job.id().equals(jobId)).findFirst().orElse(null);
    }

    private static List<ProposedEvent> blocked(StrategicTask task) {
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
            ActorLocation location = state.actorLocations().get(job.workerId());
            if (location == null || !job.traversal().equals(ResourceSiteHarvestTraversal.compile(state.bootstrap(), site, location, job.id()))
                    || job.traversalCursor() != 0) {
                throw new IllegalArgumentException("resource-site harvest must pin its worker's immutable field-work traversal at admission");
            }
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

    private static ResourceSiteHarvestJob job(FrontierWorldState state, ResourceSiteLifecycle lifecycle, StrategicTask task, ResidentProfile farmer,
                                              InventoryCustody.ContainerSlot outputSlot) {
        String suffix = lifecycle.siteId().value().substring("site:".length()) + "-" + lifecycle.growthEpoch();
        SubjectId jobId = new SubjectId("job:site-harvest-" + suffix);
        ResourceSite site = site(state, lifecycle.siteId()); ActorLocation worker = state.actorLocations().get(farmer.id());
        if (worker == null) throw new IllegalArgumentException("resource-site harvest worker has no canonical body");
        return new ResourceSiteHarvestJob(jobId, task.id(), lifecycle.siteId(), farmer.id(),
                new SubjectId("item:site-harvest-" + suffix + "-wheat"), outputSlot, new PhysicalIntentId("intent:site-harvest-" + suffix),
                ResourceSiteHarvestProgress.notStarted(), ResourceSiteHarvestTraversal.compile(state.bootstrap(), site, worker, jobId), 0);
    }

    private static PhysicalIntent intent(ResourceSite site, ResourceSiteHarvestJob job) {
        BlockPosition origin = site.cropSlots().getFirst();
        return new PhysicalIntent(job.intentId(), PhysicalIntentKind.RESOURCE_SITE_HARVEST, PhysicalIntentStatus.PREPARED, job.siteId(),
                List.of(job.siteId(), job.id(), job.workerId(), job.outputItemId()),
                new FixedPosition(FixedScalar.whole(origin.x()), FixedScalar.whole(origin.y()), FixedScalar.whole(origin.z())), 0,
                PhysicalPostcondition.RESOURCE_SITE_HARVESTED_OBSERVED);
    }

    private static StrategicTask task(FrontierWorldState state, SubjectId taskId, StrategicTaskStatus status) {
        StrategicTask task = state.strategicPlans().tasks().get(taskId);
        if (task == null || task.kind() != StrategicTaskKind.HARVEST_RESOURCE_SITE || task.status() != status) {
            throw new IllegalArgumentException("resource-site harvest has no matching strategic task");
        }
        return task;
    }
    private static ProposedEvent transition(StrategicTask task, StrategicTaskStatus status) {
        return new ProposedEvent(task.ownerId(), new StrategicTaskTransition(task.id(), status));
    }
    private static ProposedEvent schedule(ScheduledAction action) { return new ProposedEvent(action.subject(), new ScheduleEffect.Created(action)); }
    private static ProposedEvent reschedule(ScheduledAction current, ScheduledAction replacement) {
        if (!current.id().equals(replacement.id())) throw new IllegalArgumentException("resource-site harvest COLD step must retain its schedule identity");
        return new ProposedEvent(current.subject(), new ScheduleEffect.Rescheduled(current.id(), replacement));
    }

    private static ResourceSite site(FrontierWorldState state, SubjectId siteId) {
        ResourceSite site = FrontierResourceSitePlan.compile(state.bootstrap()).get(siteId);
        if (site == null) throw new IllegalArgumentException("resource-site harvest has an unknown site"); return site;
    }
    private static Settlement settlement(FrontierWorldState state, SubjectId settlementId) { return FrontierWorldStateSupport.settlement(state.bootstrap(), settlementId); }
}
