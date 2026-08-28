package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceSiteProcessTest {
    @Test
    void coldGrowthAdvancesOneKnownStageAndPersistsItsNextDueAction() {
        FrontierWorldState state = prepared(initial()); SubjectId site = new SubjectId("site:1-wheat-field");
        ResourceSiteLifecycle before = state.resourceSites().site(site);
        ScheduledAction due = ResourceSiteProcess.nextGrowth(before, 5_000L);
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> planned = ResourceSiteProcess.planGrowth(state, due);

        assertEquals(2, planned.size());
        ResourceSiteGrowthAdvanced advanced = (ResourceSiteGrowthAdvanced) planned.getFirst().payload();
        assertEquals(before.growthEpoch(), advanced.growthEpoch()); assertEquals(before.growthStage(), advanced.growthStage());
        assertTrue(planned.get(1).payload() instanceof ScheduleEffect.Created);
        FrontierWorldState next = ResourceSiteProcess.reduceGrowth(state, site, advanced);
        assertEquals(1, next.resourceSites().site(site).growthStage());
        ScheduledAction successor = ((ScheduleEffect.Created) planned.get(1).payload()).action();
        assertEquals(5_000L + ResourceSiteProcess.WHEAT_STAGE_INTERVAL, successor.dueAt().ticks());
        assertEquals(next, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(next)));
    }

    @Test
    void staleOrReadyGrowthActionIsConsumedWithoutInventingAStage() {
        FrontierWorldState state = prepared(initial()); SubjectId site = new SubjectId("site:1-wheat-field");
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(site);
        ScheduledAction due = ResourceSiteProcess.nextGrowth(lifecycle, 1_000L);
        FrontierWorldState advanced = ResourceSiteProcess.reduceGrowth(state, site,
                new ResourceSiteGrowthAdvanced(site, lifecycle.growthEpoch(), lifecycle.growthStage()));

        assertTrue(ResourceSiteProcess.planGrowth(advanced, due).isEmpty());
    }

    @Test
    void matureStageBecomesReadyAndDoesNotScheduleAnEighthGrowth() {
        FrontierWorldState state = prepared(initial()); SubjectId site = new SubjectId("site:1-wheat-field");
        for (int stage = 0; stage < ResourceSiteLifecycle.MATURE_STAGE - 1; stage++) {
            ResourceSiteLifecycle current = state.resourceSites().site(site);
            state = ResourceSiteProcess.reduceGrowth(state, site, new ResourceSiteGrowthAdvanced(site, current.growthEpoch(), current.growthStage()));
        }
        ResourceSiteLifecycle finalGrowing = state.resourceSites().site(site);
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> planned = ResourceSiteProcess.planGrowth(state,
                ResourceSiteProcess.nextGrowth(finalGrowing, 10_000L));

        assertEquals(1, planned.size());
        FrontierWorldState ready = ResourceSiteProcess.reduceGrowth(state, site, (ResourceSiteGrowthAdvanced) planned.getFirst().payload());
        assertEquals(ResourceSitePhase.READY, ready.resourceSites().site(site).phase());
    }

    private static FrontierWorldState prepared(FrontierWorldState state) {
        SubjectId site = new SubjectId("site:1-wheat-field");
        ResourceSitePreparationJob job = new ResourceSitePreparationJob(new SubjectId("job:site-prepare-1-wheat-field"), site,
                new PhysicalIntentId("intent:site-prepare-1-wheat-field"));
        return state.withResourceSites(state.resourceSites().replace(state.resourceSites().site(site).preparing(job).prepared()));
    }

    private static FrontierWorldState initial() {
        return FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:resource-site-process"), 125L));
    }
}
