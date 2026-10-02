package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.process.ResourceSiteHarvestProcess;
import io.farfrontier.palemirror.frontier.v3.process.ResourceSiteHarvestWorkProcess;
import io.farfrontier.palemirror.frontier.v3.process.ResidentWorkModifiersProcess;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResourceSiteHarvestLabourTest {
    @Test void actualHotCommandAndColdOwnerAccrueEqualWorkAndSnapshotRetainsTheInterval() {
        var fixture = ResourceSiteHarvestProcessTest.coldHarvestAfterSteps(125L, 0);
        var job = fixture.job();
        var goal = ResourceSiteHarvestGoal.current(fixture.state(), job);
        var cold = fixture.state().withActorBody(job.workerId(), goal.representative().standingBody());
        var lease = ResourceSiteHarvestProcessTest.newHarvestLease(cold, fixture.site(), job, "labour");
        var hot = cold.prepareSceneLease(lease).transitionSceneLease(lease.id(), SceneLeaseStatus.HOT);
        var world = hot.bootstrap().worldId();
        var base = FrontierWorldRuntimeDefinition.configuration(world, 125L);
        var binding = ResourceSiteHarvestProcess.coldProgress(job, 22_301);
        var engine = FrontierEngines.create(new FrontierEngineConfiguration<>(world, hot, new SimInstant(22_301),
                base.commandPlanner(), base.scheduledPlanner(), base.reducer(), new FrontierWorldStateCodec(),
                base.projectionMapper(), base.limits(), List.of(binding), base.transactionCommitter()));
        assertThrows(IllegalArgumentException.class, () -> ResourceSiteHarvestProcess.reduceCropPrepared(hot,
                fixture.site(), new ResourceSiteHarvestCropPrepared(job.id(), job.progress().nextCropSlotIndex())));
        var start = ResourceSiteHarvestWorkProcess.change(hot, job, 22_301, true, binding, Optional.of(lease.id()));
        submit(engine, start);
        var coldStart = ResourceSiteHarvestWorkProcess.change(cold, job, 22_301, true, binding, Optional.empty());
        cold = ResourceSiteHarvestWorkProcess.reduce(cold, fixture.site(), coldStart);
        assertEquals(coldStart.next(), start.next());
        long finish = start.next().activeUntilTick();
        engine.advanceTo(new SimInstant(finish), new WorkBudget(100, 100));
        var restored = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        var hotJob = (ResourceSiteHarvestJob) restored.resourceSites().site(fixture.site()).activeWork().orElseThrow();
        var due = engine.checkpoint().schedules().getFirst();
        var stop = ResourceSiteHarvestWorkProcess.change(restored, hotJob, finish, false, due, Optional.of(lease.id()));
        submit(engine, stop);
        var coldJob = (ResourceSiteHarvestJob) cold.resourceSites().site(fixture.site()).activeWork().orElseThrow();
        cold = ResourceSiteHarvestWorkProcess.reduce(cold, fixture.site(), ResourceSiteHarvestWorkProcess.change(
                cold, coldJob, finish, false, ResourceSiteHarvestProcess.coldProgress(coldJob, finish), Optional.empty()));
        restored = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertEquals(cold.resourceSites().site(fixture.site()).activeWork(), restored.resourceSites().site(fixture.site()).activeWork());
        assertTrue(((ResourceSiteHarvestJob) restored.resourceSites().site(fixture.site()).activeWork().orElseThrow())
                .progress().work().orElseThrow().complete());
        ResourceSiteHarvestProcess.reduceCropPrepared(restored, fixture.site(),
                new ResourceSiteHarvestCropPrepared(job.id(), job.progress().nextCropSlotIndex()));
    }

    @Test void modifierEditSettlesOldRateWithoutChangingNutritionAndSurvivesPersistence() {
        var fixture = ResourceSiteHarvestProcessTest.coldHarvestAfterSteps(125L, 0);
        var job = fixture.job();
        var state = fixture.state().withActorBody(job.workerId(), ResourceSiteHarvestGoal.current(fixture.state(), job)
                .representative().standingBody());
        var binding = ResourceSiteHarvestProcess.coldProgress(job, 22_301);
        var start = ResourceSiteHarvestWorkProcess.change(state, job, 22_301, true, binding, Optional.empty());
        state = ResourceSiteHarvestWorkProcess.reduce(state, fixture.site(), start);
        var nutrition = state.humanPopulation().nutrition();
        var next = ResidentWorkModifiers.initial().with(new ResidentWorkModifiers.Modifier(
                new SubjectId("modifier:agriculture-bonus"), HumanCapability.AGRICULTURE, 2_000));
        state = ResidentWorkModifiersProcess.reduce(state, job.workerId(), new ResidentWorkModifiersChanged(
                job.workerId(), 22_321, ResidentWorkModifiers.initial(), next));
        assertEquals(nutrition, state.humanPopulation().nutrition());
        job = (ResourceSiteHarvestJob) state.resourceSites().site(fixture.site()).activeWork().orElseThrow();
        assertEquals(20L * start.next().ratePermille(), job.progress().work().orElseThrow().completedMilliWork());
        assertFalse(job.progress().work().orElseThrow().running());
        var resumed = ResourceSiteHarvestLabour.next(state, job, 22_321, true);
        assertEquals(2 * start.next().ratePermille(), resumed.ratePermille());
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)));
    }

    @Test void restartTransitionPausesTheExactOwnerBeforeUninspectedTimeCanAccrue() {
        var fixture = ResourceSiteHarvestProcessTest.coldHarvestAfterSteps(125L, 0);
        var job = fixture.job();
        var state = fixture.state().withActorBody(job.workerId(), ResourceSiteHarvestGoal.current(fixture.state(), job)
                .representative().standingBody());
        var lease = ResourceSiteHarvestProcessTest.newHarvestLease(state, fixture.site(), job, "labour-restart");
        state = state.prepareSceneLease(lease).transitionSceneLease(lease.id(), SceneLeaseStatus.HOT);
        var binding = ResourceSiteHarvestProcess.coldProgress(job, 22_301);
        var start = ResourceSiteHarvestWorkProcess.change(state, job, 22_301, true, binding, Optional.of(lease.id()));
        state = ResourceSiteHarvestWorkProcess.reduce(state, fixture.site(), start);
        var world = state.bootstrap().worldId();
        var base = FrontierWorldRuntimeDefinition.configuration(world, 125L);
        var engine = FrontierEngines.create(new FrontierEngineConfiguration<>(world, state, new SimInstant(22_321),
                base.commandPlanner(), base.scheduledPlanner(), base.reducer(), new FrontierWorldStateCodec(),
                base.projectionMapper(), base.limits(), List.of(binding), base.transactionCommitter()));
        var id = new CommandId("command:labour-restart");
        assertInstanceOf(CommandResult.Accepted.class, engine.submit(new FrontierCommand(2, id, world,
                Revision.ZERO, new SimInstant(22_321), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(id),
                new SceneLeaseTransition(lease.id(), SceneLeaseStatus.UNKNOWN_AFTER_RESTART))));
        var restored = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        var work = ((ResourceSiteHarvestJob) restored.resourceSites().site(fixture.site()).activeWork().orElseThrow())
                .progress().work().orElseThrow();
        assertFalse(work.running());
        assertEquals(20L * start.next().ratePermille(), work.completedAt(100_000));
    }

    private static void submit(FrontierEngine<?> engine, ResourceSiteHarvestWorkChanged payload) {
        var checkpoint = engine.checkpoint();
        var id = new CommandId("command:labour-" + checkpoint.revision().value());
        var result = engine.submit(new FrontierCommand(2, id, checkpoint.worldId(), checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(id), payload,
                Optional.of(new EngineScheduleBinding(checkpoint.revision(), checkpoint.schedules().getFirst()))));
        assertInstanceOf(CommandResult.Accepted.class, result, result.toString());
    }
}
