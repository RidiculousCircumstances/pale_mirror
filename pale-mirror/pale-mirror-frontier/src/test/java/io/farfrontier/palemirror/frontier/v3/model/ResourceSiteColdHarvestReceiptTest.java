package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.process.ResourceSiteHarvestProcess;
import io.farfrontier.palemirror.frontier.v3.process.ResourceSiteProcess;
import io.farfrontier.palemirror.frontier.v3.process.StrategicObjectiveProcess;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceSiteColdHarvestReceiptTest {
    @Test
    void zeroPlayerColdHarvestCommitsTheFirstDurableCropReceiptBeforeAnyHotLease() {
        SubjectId site = new SubjectId("site:1-wheat-field");
        FrontierWorldState state = matureField();
        List<ProposedEvent> opportunity = StrategicObjectiveProcess.planResourceHarvestOpportunity(state,
                StrategicObjectiveProcess.resourceHarvestOpportunity(state, state.resourceSites().site(site), 22_000L));
        state = StrategicObjectiveProcess.reduceObjective(state, new SubjectId("settlement:1"),
                (StrategicObjectiveSelected) opportunity.getFirst().payload());
        state = StrategicObjectiveProcess.reduceTask(state, new SubjectId("settlement:1"),
                (StrategicTaskPlanned) opportunity.get(1).payload());
        StrategicTask task = state.strategicPlans().tasks().values().stream()
                .filter(candidate -> candidate.kind() == StrategicTaskKind.HARVEST_RESOURCE_SITE).findFirst().orElseThrow();
        List<ProposedEvent> planned = ResourceSiteHarvestProcess.plan(state, ResourceSiteHarvestProcess.start(task, 22_100L));
        state = StrategicObjectiveProcess.reduceTaskTransition(state, new SubjectId("settlement:1"),
                (StrategicTaskTransition) planned.getFirst().payload());
        ResourceSiteHarvestStarted started = (ResourceSiteHarvestStarted) planned.get(1).payload();
        state = ResourceSiteHarvestProcess.reduceStarted(state, site, started);
        state = ResourceSiteHarvestProcess.reducePrepared(state, site, ((PhysicalIntentPrepared) planned.get(2).payload()).intent());
        ScheduledAction[] action = { ((ScheduleEffect.Created) planned.get(3).payload()).action() };

        while (((ResourceSiteHarvestJob) state.resourceSites().site(site).activeWork().orElseThrow()).progress().completedCropSlots() == 0) {
            for (ProposedEvent event : ResourceSiteHarvestProcess.planColdProgress(state, action[0])) {
                if (event.payload() instanceof ResourceSiteHarvestColdTraversalAdvanced traversal) state = ResourceSiteHarvestProcess.reduceColdTraversalAdvanced(state, site, traversal);
                else if (event.payload() instanceof ResourceSiteHarvestCropPrepared crop) state = ResourceSiteHarvestProcess.reduceCropPrepared(state, site, crop);
                else if (event.payload() instanceof ResourceSiteHarvestProgressed progressed) state = ResourceSiteHarvestProcess.reduceProgressed(state, site, progressed);
                else if (event.payload() instanceof ScheduleEffect.Rescheduled rescheduled) action[0] = rescheduled.replacement();
                else throw new AssertionError("unexpected COLD harvest event: " + event.payload());
            }
        }

        ResourceSiteHarvestJob progressed = (ResourceSiteHarvestJob) state.resourceSites().site(site).activeWork().orElseThrow();
        assertEquals(1, progressed.progress().completedCropSlots());
        assertEquals(-1, progressed.progress().pendingCropSlotIndex());
        assertEquals(PhysicalIntentStatus.PREPARED, state.physicalIntents().get(progressed.intentId()).status());
        assertFalse(state.inventory().items().containsKey(progressed.outputItemId()));
        List<ProposedEvent> awaitingHot = ResourceSiteHarvestProcess.planColdProgress(state, action[0]);
        assertEquals(1, awaitingHot.size());
        assertTrue(awaitingHot.getFirst().payload() instanceof ScheduleEffect.Rescheduled);
    }

    private static FrontierWorldState matureField() {
        SubjectId site = new SubjectId("site:1-wheat-field");
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:cold-harvest-receipt"), 125L));
        List<ProposedEvent> preparation = ResourceSiteProcess.planPreparation(state, ResourceSiteProcess.preparation(site, 4_000L));
        state = ResourceSiteProcess.reducePreparationStarted(state, site, (ResourceSitePreparationStarted) preparation.getFirst().payload());
        state = ResourceSiteProcess.reducePrepared(state, site, (ResourceSitePrepared) preparation.get(1).payload());
        for (int stage = 0; stage < ResourceSiteLifecycle.MATURE_STAGE; stage++) {
            ResourceSiteLifecycle lifecycle = state.resourceSites().site(site);
            state = ResourceSiteProcess.reduceGrowth(state, site,
                    new ResourceSiteGrowthAdvanced(site, lifecycle.growthEpoch(), lifecycle.growthStage()));
        }
        return state;
    }
}
