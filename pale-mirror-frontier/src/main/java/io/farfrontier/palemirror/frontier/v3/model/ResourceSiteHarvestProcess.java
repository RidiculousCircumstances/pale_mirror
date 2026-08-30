package io.farfrontier.palemirror.frontier.v3.model;

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

/** Plans one exact 64-cell COLD harvest; loaded-world projection follows canonical completion. */
final class ResourceSiteHarvestProcess {
    static final long RETRY_INTERVAL = 200L;
    private ResourceSiteHarvestProcess() { }

    static ScheduledAction start(StrategicTask task, long dueAt) {
        if (task.kind() != StrategicTaskKind.HARVEST_RESOURCE_SITE || task.resourceSiteTarget().isEmpty()) {
            throw new IllegalArgumentException("resource-site harvest start requires its exact strategic task");
        }
        return new ScheduledAction(new ScheduleId("schedule:resource-site-harvest-task-start-" + task.id().value().replace(':', '-')),
                new SimInstant(dueAt), 0, task.id(), "frontier.resource_site.harvest", 1);
    }

    static List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action) {
        StrategicTask task = task(state, action.subject(), StrategicTaskStatus.PENDING);
        if (!action.id().equals(start(task, action.dueAt().ticks()).id())) return List.of();
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(task.resourceSiteTarget().orElseThrow());
        if (lifecycle.phase() != ResourceSitePhase.READY) return blocked(task);
        ResourceSite site = site(state, lifecycle.siteId()); Settlement settlement = settlement(state, site.settlementId());
        if (!task.ownerId().equals(settlement.id()) || !task.resourceSiteTarget().equals(java.util.Optional.of(site.id()))) {
            throw new IllegalArgumentException("resource-site harvest task has a foreign field owner");
        }
        if (state.structureConditions().get(site.facilityId()) != StructureCondition.INTACT) return blocked(task);
        ResidentProfile farmer = FrontierWorldStateSupport.availableFieldResident(state, settlement.id(), ResidentRole.FARMER).orElse(null);
        if (farmer == null) return blocked(task);
        SubjectId depot = FrontierWorldState.depotId(settlement.id());
        OptionalInt slot = state.inventory().firstFreeSlot(depot); if (slot.isEmpty()) return blocked(task);
        ResourceSiteHarvestJob job = job(lifecycle, task, farmer, new InventoryCustody.ContainerSlot(depot, slot.getAsInt()));
        ExactItemStack output = new ExactItemStack(job.outputItemId(), settlement.id(), "minecraft:wheat", 64, job.outputSlot());
        ResourceSiteLifecycle harvested = lifecycle.harvesting(job).harvested();
        return List.of(transition(task, StrategicTaskStatus.ACTIVE), new ProposedEvent(lifecycle.siteId(), new ResourceSiteHarvestStarted(job)),
                new ProposedEvent(lifecycle.siteId(), new ResourceSiteHarvested(job, output)), transition(task, StrategicTaskStatus.COMPLETED),
                new ProposedEvent(lifecycle.siteId(), new ScheduleEffect.Created(ResourceSiteProcess.nextGrowth(harvested,
                        Math.addExact(action.dueAt().ticks(), ResourceSiteProcess.WHEAT_STAGE_INTERVAL)))));
    }

    static FrontierWorldState reduceStarted(FrontierWorldState state, SubjectId subject, ResourceSiteHarvestStarted started) {
        ResourceSiteHarvestJob job = started.job(); if (!subject.equals(job.siteId())) throw new IllegalArgumentException("resource-site harvest has a foreign event owner");
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(job.siteId()); validateJob(state, lifecycle, job);
        return state.withResourceSites(state.resourceSites().replace(lifecycle.harvesting(job)));
    }

    static FrontierWorldState reduceHarvested(FrontierWorldState state, SubjectId subject, ResourceSiteHarvested harvested) {
        ResourceSiteHarvestJob job = harvested.job();
        if (!subject.equals(job.siteId())) throw new IllegalArgumentException("resource-site harvest completion has a foreign event owner");
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(job.siteId());
        validateJob(state, lifecycle, job);
        ExactItemStack output = harvested.output();
        if (!output.id().equals(job.outputItemId()) || !output.custody().equals(job.outputSlot()) || output.count() != 64
                || !output.itemKind().equals("minecraft:wheat") || state.inventory().items().containsKey(output.id())) {
            throw new IllegalArgumentException("resource-site harvest completion has an invalid exact output");
        }
        ResourceSite site = site(state, lifecycle.siteId());
        if (!output.economicOwnerId().equals(site.settlementId())) throw new IllegalArgumentException("resource-site harvest completion has a foreign output owner");
        return state.withResourceSites(state.resourceSites().replace(lifecycle.harvested())).withInventory(state.inventory().store(output));
    }

    static FrontierWorldState reducePrepared(FrontierWorldState state, SubjectId subject, PhysicalIntent intent) {
        if (intent.kind() != PhysicalIntentKind.RESOURCE_SITE_HARVEST || !subject.equals(intent.causeSubjectId())) throw new IllegalArgumentException("resource-site harvest intent is invalid");
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(intent.causeSubjectId()); validateIntent(state, lifecycle, intent);
        return state.preparePhysicalIntent(intent);
    }

    static List<ProposedEvent> planTransition(FrontierWorldState state, PhysicalIntent intent, PhysicalIntentTransition transition, long now) {
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(intent.causeSubjectId()); validateBinding(state, lifecycle, intent);
        ResourceSiteHarvestJob job = harvest(lifecycle, intent.id()); StrategicTask task = task(state, job.taskId(), StrategicTaskStatus.ACTIVE);
        if (transition.status() == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART) {
            return List.of(new ProposedEvent(lifecycle.siteId(), transition), transition(task, StrategicTaskStatus.BLOCKED));
        }
        if (transition.status() != PhysicalIntentStatus.CONFIRMED) return List.of(new ProposedEvent(lifecycle.siteId(), transition));
        ResourceSiteLifecycle next = lifecycle.harvested();
        return List.of(new ProposedEvent(lifecycle.siteId(), transition), transition(task, StrategicTaskStatus.COMPLETED), new ProposedEvent(lifecycle.siteId(),
                new ScheduleEffect.Created(ResourceSiteProcess.nextGrowth(next, Math.addExact(now, ResourceSiteProcess.WHEAT_STAGE_INTERVAL)))));
    }

    static void validateIntent(FrontierWorldState state, ResourceSiteLifecycle lifecycle, PhysicalIntent intent) {
        if (intent.status() != PhysicalIntentStatus.PREPARED) throw new IllegalArgumentException("resource-site harvest must begin prepared");
        validateBinding(state, lifecycle, intent);
    }

    static void validateBinding(FrontierWorldState state, ResourceSiteLifecycle lifecycle, PhysicalIntent intent) {
        ResourceSiteHarvestJob job = harvest(lifecycle, intent.id()); ResourceSite site = site(state, lifecycle.siteId());
        BlockPosition origin = site.cropSlots().getFirst(); FixedPosition expected = new FixedPosition(FixedScalar.whole(origin.x()), FixedScalar.whole(origin.y()), FixedScalar.whole(origin.z()));
        if (!intent.subjectIds().equals(List.of(job.siteId(), job.id(), job.workerId(), job.outputItemId()))
                || !intent.origin().equals(expected) || intent.radiusBlocks() != 0 || intent.postcondition() != PhysicalPostcondition.RESOURCE_SITE_HARVESTED_OBSERVED) {
            throw new IllegalArgumentException("resource-site harvest intent does not exactly bind its mature field and output");
        }
    }

    static ResourceSiteHarvestJob harvest(ResourceSiteLifecycle lifecycle, PhysicalIntentId intentId) {
        return lifecycle.activeWork().filter(ResourceSiteHarvestJob.class::isInstance).map(ResourceSiteHarvestJob.class::cast)
                .filter(job -> job.intentId().equals(intentId)).orElseThrow(() -> new IllegalArgumentException("resource-site harvest has no matching active work"));
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
        ResidentProfile worker = FrontierWorldStateSupport.availableFieldResident(state, settlement.id(), ResidentRole.FARMER).orElse(null);
        if (worker == null || !worker.id().equals(job.workerId())) throw new IllegalArgumentException("resource-site harvest worker is unavailable");
        SubjectId depot = FrontierWorldState.depotId(settlement.id());
        if (!job.outputSlot().containerId().equals(depot) || state.inventory().itemAt(depot, job.outputSlot().slot()).isPresent()) {
            throw new IllegalArgumentException("resource-site harvest output slot is unavailable");
        }
        if (state.inventory().items().containsKey(job.outputItemId())) throw new IllegalArgumentException("resource-site harvest output identity already exists");
    }

    private static ResourceSiteHarvestJob job(ResourceSiteLifecycle lifecycle, StrategicTask task, ResidentProfile farmer, InventoryCustody.ContainerSlot outputSlot) {
        String suffix = lifecycle.siteId().value().substring("site:".length()) + "-" + lifecycle.growthEpoch();
        return new ResourceSiteHarvestJob(new SubjectId("job:site-harvest-" + suffix), task.id(), lifecycle.siteId(), farmer.id(),
                new SubjectId("item:site-harvest-" + suffix + "-wheat"), outputSlot, new PhysicalIntentId("intent:site-harvest-" + suffix));
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

    private static ResourceSite site(FrontierWorldState state, SubjectId siteId) {
        ResourceSite site = FrontierResourceSitePlan.compile(state.bootstrap()).get(siteId);
        if (site == null) throw new IllegalArgumentException("resource-site harvest has an unknown site"); return site;
    }
    private static Settlement settlement(FrontierWorldState state, SubjectId settlementId) { return FrontierWorldStateSupport.settlement(state.bootstrap(), settlementId); }
}
