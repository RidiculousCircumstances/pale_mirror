package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.process.ResourceSiteHarvestProcess;
import io.farfrontier.palemirror.frontier.v3.process.ResourceSiteProcess;
import io.farfrontier.palemirror.frontier.v3.process.StrategicObjectiveProcess;

import java.util.List;

/**
 * Read-only development ingress for one actual harvest before its first physical crop effect.
 * It owns no HOT lease, body, block, receipt, or alternate cursor: a normally visiting player
 * still has to admit and execute the retained farmer's first crop station.
 */
final class FrontierResourceSiteHarvestFixture {
    private FrontierResourceSiteHarvestFixture() { }

    static Fixture create(WorldId worldId, long seed) {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(worldId, seed));
        return create(state);
    }

    /** Composes the same ordinary harvest ingress with an already-retained disjoint front. */
    static Fixture create(FrontierWorldState state) {
        SubjectId siteId = new SubjectId("site:1-wheat-field");
        List<ProposedEvent> preparation = ResourceSiteProcess.planPreparation(state, ResourceSiteProcess.preparation(siteId, 4_000L));
        state = ResourceSiteProcess.reducePreparationStarted(state, siteId, (ResourceSitePreparationStarted) preparation.getFirst().payload());
        state = ResourceSiteProcess.reducePrepared(state, siteId, (ResourceSitePrepared) preparation.get(1).payload());
        for (int stage = 0; stage < ResourceSiteLifecycle.MATURE_STAGE; stage++) {
            ResourceSiteLifecycle current = state.resourceSites().site(siteId);
            state = ResourceSiteProcess.reduceGrowth(state, siteId,
                    new ResourceSiteGrowthAdvanced(siteId, current.growthEpoch(), current.growthStage()));
        }
        List<ProposedEvent> opportunity = StrategicObjectiveProcess.planResourceHarvestOpportunity(state,
                StrategicObjectiveProcess.resourceHarvestOpportunity(state, state.resourceSites().site(siteId), 22_000L));
        state = StrategicObjectiveProcess.reduceObjective(state, new SubjectId("settlement:1"),
                (StrategicObjectiveSelected) opportunity.getFirst().payload());
        state = StrategicObjectiveProcess.reduceTask(state, new SubjectId("settlement:1"),
                (StrategicTaskPlanned) opportunity.get(1).payload());
        StrategicTask task = state.strategicPlans().tasks().values().stream()
                .filter(value -> value.kind() == StrategicTaskKind.HARVEST_RESOURCE_SITE).findFirst()
                .orElseThrow(() -> new IllegalStateException("harvest fixture has no exact strategic task"));
        List<ProposedEvent> started = ResourceSiteHarvestProcess.plan(state, ResourceSiteHarvestProcess.start(task, 22_100L));
        state = StrategicObjectiveProcess.reduceTaskTransition(state, new SubjectId("settlement:1"),
                (StrategicTaskTransition) started.getFirst().payload());
        ResourceSiteHarvestStarted harvest = (ResourceSiteHarvestStarted) started.get(1).payload();
        state = ResourceSiteHarvestProcess.reduceStarted(state, siteId, harvest);
        state = ResourceSiteHarvestProcess.reducePrepared(state, siteId, ((PhysicalIntentPrepared) started.get(2).payload()).intent());
        ScheduledAction continuation = started.stream().map(ProposedEvent::payload).filter(ScheduleEffect.Created.class::isInstance)
                .map(ScheduleEffect.Created.class::cast).map(ScheduleEffect.Created::action).findFirst()
                .orElseThrow(() -> new IllegalStateException("harvest fixture has no retained COLD continuation"));
        ResourceSiteHarvestJob job = harvest.job();
        long instant = 22_100L;
        while (job.hasNextTraversalStep()) {
            List<ProposedEvent> step = ResourceSiteHarvestProcess.planColdProgress(state, continuation);
            ResourceSiteHarvestColdTraversalAdvanced advanced = step.stream().map(ProposedEvent::payload)
                    .filter(ResourceSiteHarvestColdTraversalAdvanced.class::isInstance).map(ResourceSiteHarvestColdTraversalAdvanced.class::cast)
                    .findFirst().orElseThrow(() -> new IllegalStateException("harvest fixture COLD traversal stalled before first crop"));
            state = ResourceSiteHarvestProcess.reduceColdTraversalAdvanced(state, siteId, advanced);
            continuation = step.stream().map(ProposedEvent::payload).filter(ScheduleEffect.Rescheduled.class::isInstance)
                    .map(ScheduleEffect.Rescheduled.class::cast).map(ScheduleEffect.Rescheduled::replacement).findFirst()
                    .orElseThrow(() -> new IllegalStateException("harvest fixture COLD traversal lost its continuation"));
            instant = continuation.dueAt().ticks() - 1L;
            job = (ResourceSiteHarvestJob) state.resourceSites().site(siteId).activeWork().orElseThrow();
        }
        return new Fixture(state, new SimInstant(instant), List.of(continuation), siteId, job.id());
    }

    record Fixture(FrontierWorldState state, SimInstant instant, List<ScheduledAction> schedules, SubjectId siteId, SubjectId jobId) {
        Fixture { schedules = List.copyOf(schedules); }
    }
}
