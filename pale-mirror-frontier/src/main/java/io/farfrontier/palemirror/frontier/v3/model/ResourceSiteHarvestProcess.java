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

/** Plans one durable 64-cell harvest only when its real settlement dependencies are available. */
final class ResourceSiteHarvestProcess {
    static final long RETRY_INTERVAL = 200L;
    private ResourceSiteHarvestProcess() { }

    static ScheduledAction review(ResourceSiteLifecycle lifecycle, long dueAt) {
        if (lifecycle.phase() != ResourceSitePhase.READY) throw new IllegalArgumentException("only ready fields can review harvest");
        String suffix = lifecycle.siteId().value().substring("site:".length());
        return new ScheduledAction(new ScheduleId("schedule:resource-site-harvest-" + suffix + "-" + lifecycle.growthEpoch() + "-" + dueAt),
                new SimInstant(dueAt), 0, lifecycle.siteId(), "frontier.resource_site.harvest", 1);
    }

    static List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action) {
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(action.subject());
        if (lifecycle.phase() != ResourceSitePhase.READY || !action.id().equals(review(lifecycle, action.dueAt().ticks()).id())) return List.of();
        ResourceSite site = site(state, lifecycle.siteId()); Settlement settlement = settlement(state, site.settlementId());
        if (state.structureConditions().get(site.facilityId()) != StructureCondition.INTACT) return retry(lifecycle, action);
        ResidentProfile farmer = FrontierWorldStateSupport.availableFieldResident(state, settlement.id(), ResidentRole.FARMER).orElse(null);
        if (farmer == null) return retry(lifecycle, action);
        SubjectId depot = FrontierWorldState.depotId(settlement.id());
        if (state.inventory().surfaces().get(depot) == null || state.inventory().surfaces().get(depot).status() != ContainerSurfaceStatus.ACTIVE) return retry(lifecycle, action);
        OptionalInt slot = state.inventory().firstFreeSlot(depot); if (slot.isEmpty()) return retry(lifecycle, action);
        ResourceSiteHarvestJob job = job(lifecycle, farmer, new InventoryCustody.ContainerSlot(depot, slot.getAsInt()));
        BlockPosition origin = site.cropSlots().getFirst(); PhysicalIntent intent = new PhysicalIntent(job.intentId(), PhysicalIntentKind.RESOURCE_SITE_HARVEST,
                PhysicalIntentStatus.PREPARED, lifecycle.siteId(), List.of(job.siteId(), job.id(), job.workerId(), job.outputItemId()),
                new FixedPosition(FixedScalar.whole(origin.x()), FixedScalar.whole(origin.y()), FixedScalar.whole(origin.z())), 0,
                PhysicalPostcondition.RESOURCE_SITE_HARVESTED_OBSERVED);
        return List.of(new ProposedEvent(lifecycle.siteId(), new ResourceSiteHarvestStarted(job)), new ProposedEvent(lifecycle.siteId(), new PhysicalIntentPrepared(intent)));
    }

    static FrontierWorldState reduceStarted(FrontierWorldState state, SubjectId subject, ResourceSiteHarvestStarted started) {
        ResourceSiteHarvestJob job = started.job(); if (!subject.equals(job.siteId())) throw new IllegalArgumentException("resource-site harvest has a foreign event owner");
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(job.siteId()); validateJob(state, lifecycle, job);
        return state.withResourceSites(state.resourceSites().replace(lifecycle.harvesting(job)));
    }

    static FrontierWorldState reducePrepared(FrontierWorldState state, SubjectId subject, PhysicalIntent intent) {
        if (intent.kind() != PhysicalIntentKind.RESOURCE_SITE_HARVEST || !subject.equals(intent.causeSubjectId())) throw new IllegalArgumentException("resource-site harvest intent is invalid");
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(intent.causeSubjectId()); validateIntent(state, lifecycle, intent);
        return state.preparePhysicalIntent(intent);
    }

    static List<ProposedEvent> planTransition(FrontierWorldState state, PhysicalIntent intent, PhysicalIntentTransition transition, long now) {
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(intent.causeSubjectId()); validateBinding(state, lifecycle, intent);
        if (transition.status() != PhysicalIntentStatus.CONFIRMED) return List.of(new ProposedEvent(lifecycle.siteId(), transition));
        ResourceSiteLifecycle next = lifecycle.harvested();
        return List.of(new ProposedEvent(lifecycle.siteId(), transition), new ProposedEvent(lifecycle.siteId(),
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

    private static List<ProposedEvent> retry(ResourceSiteLifecycle lifecycle, ScheduledAction action) {
        return List.of(new ProposedEvent(lifecycle.siteId(), new ScheduleEffect.Created(review(lifecycle, Math.addExact(action.dueAt().ticks(), RETRY_INTERVAL)))));
    }

    private static void validateJob(FrontierWorldState state, ResourceSiteLifecycle lifecycle, ResourceSiteHarvestJob job) {
        if (lifecycle.phase() != ResourceSitePhase.READY || lifecycle.growthStage() != ResourceSiteLifecycle.MATURE_STAGE) throw new IllegalArgumentException("resource-site harvest requires a ready field");
        ResourceSite site = site(state, job.siteId()); Settlement settlement = settlement(state, site.settlementId());
        if (state.structureConditions().get(site.facilityId()) != StructureCondition.INTACT) throw new IllegalArgumentException("resource-site harvest farm is unavailable");
        ResidentProfile worker = FrontierWorldStateSupport.availableFieldResident(state, settlement.id(), ResidentRole.FARMER).orElse(null);
        if (worker == null || !worker.id().equals(job.workerId())) throw new IllegalArgumentException("resource-site harvest worker is unavailable");
        SubjectId depot = FrontierWorldState.depotId(settlement.id());
        if (!job.outputSlot().containerId().equals(depot) || state.inventory().itemAt(depot, job.outputSlot().slot()).isPresent()
                || state.inventory().surfaces().get(depot) == null || state.inventory().surfaces().get(depot).status() != ContainerSurfaceStatus.ACTIVE) {
            throw new IllegalArgumentException("resource-site harvest output slot is unavailable");
        }
        if (state.inventory().items().containsKey(job.outputItemId())) throw new IllegalArgumentException("resource-site harvest output identity already exists");
    }

    private static ResourceSiteHarvestJob job(ResourceSiteLifecycle lifecycle, ResidentProfile farmer, InventoryCustody.ContainerSlot outputSlot) {
        String suffix = lifecycle.siteId().value().substring("site:".length()) + "-" + lifecycle.growthEpoch();
        return new ResourceSiteHarvestJob(new SubjectId("job:site-harvest-" + suffix), lifecycle.siteId(), farmer.id(),
                new SubjectId("item:site-harvest-" + suffix + "-wheat"), outputSlot, new PhysicalIntentId("intent:site-harvest-" + suffix));
    }

    private static ResourceSite site(FrontierWorldState state, SubjectId siteId) {
        ResourceSite site = FrontierResourceSitePlan.compile(state.bootstrap()).get(siteId);
        if (site == null) throw new IllegalArgumentException("resource-site harvest has an unknown site"); return site;
    }
    private static Settlement settlement(FrontierWorldState state, SubjectId settlementId) { return FrontierWorldStateSupport.settlement(state.bootstrap(), settlementId); }
}
