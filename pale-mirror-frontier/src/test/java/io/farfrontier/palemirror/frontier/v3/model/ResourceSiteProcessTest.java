package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.process.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;

import io.farfrontier.palemirror.frontier.v3.api.EngineStatus;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget;
import io.farfrontier.palemirror.frontier.v3.persistence.RecoveryImage;
import io.farfrontier.palemirror.frontier.v3.persistence.SnapshotRecord;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

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
        assertEquals(5_000L + state.bootstrap().ruleset().cadence().resourceGrowthStageInterval(), successor.dueAt().ticks());
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
    void conflictedFieldCancelsItsPersistedGrowthActionAfterRecoveryWithoutQuarantining() {
        FrontierWorldState state = prepared(initial()); SubjectId site = new SubjectId("site:1-wheat-field");
        ScheduledAction stale = ResourceSiteProcess.nextGrowth(state.resourceSites().site(site), 1_000L);
        BlockPosition affected = FrontierResourceSitePlan.compile(state.bootstrap()).get(site).soilSlots().getFirst();
        FrontierWorldState conflicted = ResourceSiteProcess.reduceConflict(state, site, new ResourceSiteConflictObserved(site, affected, "observed:farmland-decay"));
        WorldId world = conflicted.bootstrap().worldId();
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> base = FrontierWorldRuntimeDefinition.configuration(world, conflicted.bootstrap().seed());
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> configuration = new FrontierEngineConfiguration<>(world, conflicted,
                SimInstant.ZERO, base.commandPlanner(), base.scheduledPlanner(), base.reducer(), base.stateCodec(), base.projectionMapper(), base.limits(),
                List.of(stale), base.transactionCommitter());
        var original = FrontierEngines.create(configuration);
        var recovered = FrontierEngines.recover(configuration, new RecoveryImage(world, Optional.of(new SnapshotRecord(original.checkpoint(), 0L)), List.of()));

        var result = recovered.advanceTo(new SimInstant(1_000L), new WorkBudget(8, 64));

        assertEquals(EngineStatus.Kind.ACTIVE, result.status().kind());
        assertEquals(1, result.transactions().size(), "the stale head must become one durable cancellation transaction");
        assertEquals(ResourceSitePhase.CONFLICT, new FrontierWorldStateCodec().decode(recovered.checkpoint().canonicalState()).resourceSites().site(site).phase());
        assertTrue(recovered.checkpoint().schedules().isEmpty(), "the recovered stale growth action must not remain queued");
    }

    @Test
    void matureStageBecomesReadyAndSchedulesOneStrategicHarvestOpportunityInsteadOfAnEighthGrowth() {
        FrontierWorldState state = prepared(initial()); SubjectId site = new SubjectId("site:1-wheat-field");
        for (int stage = 0; stage < ResourceSiteLifecycle.MATURE_STAGE - 1; stage++) {
            ResourceSiteLifecycle current = state.resourceSites().site(site);
            state = ResourceSiteProcess.reduceGrowth(state, site, new ResourceSiteGrowthAdvanced(site, current.growthEpoch(), current.growthStage()));
        }
        ResourceSiteLifecycle finalGrowing = state.resourceSites().site(site);
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> planned = ResourceSiteProcess.planGrowth(state,
                ResourceSiteProcess.nextGrowth(finalGrowing, 10_000L));

        assertEquals(2, planned.size());
        FrontierWorldState ready = ResourceSiteProcess.reduceGrowth(state, site, (ResourceSiteGrowthAdvanced) planned.getFirst().payload());
        assertEquals(ResourceSitePhase.READY, ready.resourceSites().site(site).phase());
        ScheduledAction harvest = ((ScheduleEffect.Created) planned.get(1).payload()).action();
        assertEquals("frontier.objective.resource_harvest", harvest.kind());
        assertEquals(site, harvest.subject());
        assertEquals(10_001L, harvest.dueAt().ticks());
    }

    private static FrontierWorldState prepared(FrontierWorldState state) {
        SubjectId site = new SubjectId("site:1-wheat-field");
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> planned = ResourceSiteProcess.planPreparation(state, ResourceSiteProcess.preparation(site, 4_000L));
        state = ResourceSiteProcess.reducePreparationStarted(state, site, (ResourceSitePreparationStarted) planned.getFirst().payload());
        return ResourceSiteProcess.reducePrepared(state, site, (ResourceSitePrepared) planned.get(1).payload());
    }

    private static FrontierWorldState initial() {
        return FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:resource-site-process"), 125L));
    }
}
