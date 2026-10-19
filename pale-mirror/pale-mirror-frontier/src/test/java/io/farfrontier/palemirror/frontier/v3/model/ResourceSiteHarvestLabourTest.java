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
    @Test void economicWithdrawalOfSowingCanSuspendAlreadyStartedSowing() {
        var fixture = ResourceSiteHarvestProcessTest.coldHarvestAfterSteps(125L, 0);
        var job = fixture.job(); var state = fixture.state();
        var cycle = state.resourceSites().cycle(job.siteId());
        var cell = cycle.layout().cells().get(job.progress().nextCropSlotIndex()).id();
        cycle = cycle.worked(cell);
        state = state.withResourceSites(state.resourceSites().replace(
                state.resourceSites().site(job.siteId()).bindHarvestTarget(job, cycle), cycle));
        job = state.resourceSites().site(job.siteId()).harvestJob(job.id()).orElseThrow();
        state = state.withActorBody(job.workerId(), ResourceSiteHarvestGoal.current(state, job).representative().standingBody());
        assertEquals(WorkOperation.SOW, ResourceSiteHarvestLabour.operation(state, job));
        var binding = ResourceSiteHarvestProcess.coldProgress(job, 22_301);
        state = ResourceSiteHarvestWorkProcess.reduce(state, job.siteId(),
                ResourceSiteHarvestWorkProcess.change(state, job, 22_301, true, binding, Optional.empty()));
        state = state.withResourceSites(state.resourceSites().planSowing(job.siteId(), Set.of()));
        job = state.resourceSites().site(job.siteId()).harvestJob(job.id()).orElseThrow();
        var stop = ResourceSiteHarvestWorkProcess.change(state, job, 22_321, false, binding, Optional.empty());
        state = ResourceSiteHarvestWorkProcess.reduce(state, job.siteId(), stop);
        assertFalse(state.resourceSites().site(job.siteId()).harvestJob(job.id()).orElseThrow().progress().work().orElseThrow().running());
        assertEquals(ResourceFieldCycle.Crop.ABSENT, state.resourceSites().cycle(job.siteId()).cell(cell).crop(),
                "stopping labour never plants a revoked cell");
    }
    @Test void excludedCellCanStopItsRetainedClockButCannotStartMoreWork() {
        var fixture = ResourceSiteHarvestProcessTest.coldHarvestAfterSteps(125L, 0);
        var job = fixture.job();
        var state = fixture.state().withActorBody(job.workerId(), ResourceSiteHarvestGoal.current(fixture.state(), job)
                .representative().standingBody());
        var binding = ResourceSiteHarvestProcess.coldProgress(job, 22_301);
        state = ResourceSiteHarvestWorkProcess.reduce(state, fixture.site(),
                ResourceSiteHarvestWorkProcess.change(state, job, 22_301, true, binding, Optional.empty()));
        job = state.resourceSites().site(fixture.site()).harvestJob(job.id()).orElseThrow();
        var cycle = state.resourceSites().cycle(fixture.site());
        var cell = cycle.layout().cells().get(job.progress().nextCropSlotIndex()).id();
        state = state.withResourceSites(state.resourceSites().replace(state.resourceSites().site(fixture.site()),
                cycle.observedWorkAccess(cell, true)));
        var invalid = state; var retained = job;
        assertThrows(IllegalArgumentException.class, () -> ResourceSiteHarvestLabour.operation(invalid, retained));
        var stopped = ResourceSiteHarvestWorkProcess.change(state, job, 22_321, false, binding, Optional.empty());
        assertInstanceOf(ResourceSiteHarvestWorkSuspended.class, stopped);
        var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
        assertEquals(stopped, codecs.decode(stopped.type(), codecs.encode(stopped)));
        var after = ResourceSiteHarvestWorkProcess.reduce(state, fixture.site(), stopped);
        var paused = after.resourceSites().site(fixture.site()).harvestJob(job.id()).orElseThrow();
        assertFalse(paused.progress().work().orElseThrow().running());
        assertThrows(IllegalArgumentException.class, () -> ResourceSiteHarvestWorkProcess.change(
                after, paused, 22_322, true, binding, Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> ResourceSiteHarvestWorkProcess.reduce(after, fixture.site(), stopped),
                "a replayed predecessor cannot settle the clock twice");
    }
    @Test void actualHotCommandAndColdOwnerAccrueEqualWorkAndSnapshotRetainsTheInterval() {
        var fixture = ResourceSiteHarvestProcessTest.coldHarvestAfterSteps(125L, 0);
        var job = fixture.job();
        var goal = ResourceSiteHarvestGoal.current(fixture.state(), job);
        var cold = fixture.state().withActorBody(job.workerId(), goal.representative().standingBody());
        var lease = ResourceSiteHarvestProcessTest.newHarvestLease(cold, fixture.site(), job, "labour");
        var prepared = cold.prepareSceneLease(lease);
        var hot = ModeledActorBodyFacts.present(prepared, job.workerId()).transitionSceneLease(lease.id(), SceneLeaseStatus.HOT);
        var world = hot.bootstrap().worldId();
        var base = FrontierWorldRuntimeDefinition.configuration(hot.bootstrap());
        var binding = ResourceSiteHarvestProcess.coldProgress(job, 22_301);
        var engine = FrontierEngines.create(new FrontierEngineConfiguration<>(world, hot, new SimInstant(22_301),
                base.commandPlanner(), base.scheduledPlanner(), base.reducer(), base.stateCodec(),
                base.projectionMapper(), base.limits(), List.of(binding), base.transactionCommitter()));
        assertThrows(IllegalArgumentException.class, () -> ResourceSiteHarvestProcess.reduceCropPrepared(hot,
                fixture.site(), new ResourceSiteHarvestCropPrepared(job.id(), job.progress().nextCropSlotIndex(), job.target().generation())));
        var start = ResourceSiteHarvestWorkProcess.change(hot, job, 22_301, true, binding, Optional.of(lease.id()));
        submit(engine, start);
        var coldStart = ResourceSiteHarvestWorkProcess.change(cold, job, 22_301, true, binding, Optional.empty());
        cold = ResourceSiteHarvestWorkProcess.reduce(cold, fixture.site(), coldStart);
        assertEquals(coldStart.next(), start.next());
        long finish = start.next().activeUntilTick();
        engine.advanceTo(new SimInstant(finish), new WorkBudget(100, 100));
        var restored = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        var hotJob = (ResourceSiteHarvestJob) restored.resourceSites().site(fixture.site()).harvestJobs().values().stream().reduce(HarvestFixtureOwners::rejectMultiple).orElseThrow();
        var due = engine.checkpoint().schedules().getFirst();
        var stop = ResourceSiteHarvestWorkProcess.change(restored, hotJob, finish, false, due, Optional.of(lease.id()));
        submit(engine, stop);
        var coldJob = (ResourceSiteHarvestJob) cold.resourceSites().site(fixture.site()).harvestJobs().values().stream().reduce(HarvestFixtureOwners::rejectMultiple).orElseThrow();
        cold = ResourceSiteHarvestWorkProcess.reduce(cold, fixture.site(), ResourceSiteHarvestWorkProcess.change(
                cold, coldJob, finish, false, ResourceSiteHarvestProcess.coldProgress(coldJob, finish), Optional.empty()));
        restored = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertEquals(cold.resourceSites().site(fixture.site()).harvestJobs().values().stream()
                .reduce(HarvestFixtureOwners::rejectMultiple), restored.resourceSites().site(fixture.site()).harvestJobs().values().stream().reduce(HarvestFixtureOwners::rejectMultiple));
        assertTrue(((ResourceSiteHarvestJob) restored.resourceSites().site(fixture.site()).harvestJobs().values().stream().reduce(HarvestFixtureOwners::rejectMultiple).orElseThrow())
                .progress().work().orElseThrow().complete());
        ResourceSiteHarvestProcess.reduceCropPrepared(restored, fixture.site(),
                new ResourceSiteHarvestCropPrepared(job.id(), job.progress().nextCropSlotIndex(), job.target().generation()));
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
        job = (ResourceSiteHarvestJob) state.resourceSites().site(fixture.site()).harvestJobs().values().stream().reduce(HarvestFixtureOwners::rejectMultiple).orElseThrow();
        assertEquals(20L * start.next().ratePermille(), job.progress().work().orElseThrow().completedMilliWork());
        assertFalse(job.progress().work().orElseThrow().running());
        var resumed = ResourceSiteHarvestLabour.next(state, job, 22_321, true);
        assertEquals(2 * start.next().ratePermille(), resumed.ratePermille());
        var codec = new FrontierWorldStateCodec(state.bootstrap());
        assertEquals(state, codec.decode(codec.encode(state)));
    }

    @Test void restartTransitionPausesTheExactOwnerBeforeUninspectedTimeCanAccrue() {
        var fixture = ResourceSiteHarvestProcessTest.coldHarvestAfterSteps(125L, 0);
        var job = fixture.job();
        var state = fixture.state().withActorBody(job.workerId(), ResourceSiteHarvestGoal.current(fixture.state(), job)
                .representative().standingBody());
        var lease = ResourceSiteHarvestProcessTest.newHarvestLease(state, fixture.site(), job, "labour-restart");
        state = ModeledActorBodyFacts.present(state.prepareSceneLease(lease), job.workerId())
                .transitionSceneLease(lease.id(), SceneLeaseStatus.HOT);
        var binding = ResourceSiteHarvestProcess.coldProgress(job, 22_301);
        var start = ResourceSiteHarvestWorkProcess.change(state, job, 22_301, true, binding, Optional.of(lease.id()));
        state = ResourceSiteHarvestWorkProcess.reduce(state, fixture.site(), start);
        var world = state.bootstrap().worldId();
        var base = FrontierWorldRuntimeDefinition.configuration(state.bootstrap());
        var engine = FrontierEngines.create(new FrontierEngineConfiguration<>(world, state, new SimInstant(22_321),
                base.commandPlanner(), base.scheduledPlanner(), base.reducer(), base.stateCodec(),
                base.projectionMapper(), base.limits(), List.of(binding), base.transactionCommitter()));
        var id = new CommandId("command:labour-restart");
        assertInstanceOf(CommandResult.Accepted.class, engine.submit(new FrontierCommand(2, id, world,
                Revision.ZERO, new SimInstant(22_321), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(id),
                new SceneLeaseTransition(lease.id(), SceneLeaseStatus.UNKNOWN_AFTER_RESTART))));
        var restored = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        var work = ((ResourceSiteHarvestJob) restored.resourceSites().site(fixture.site()).harvestJobs().values().stream().reduce(HarvestFixtureOwners::rejectMultiple).orElseThrow())
                .progress().work().orElseThrow();
        assertFalse(work.running());
        assertEquals(20L * start.next().ratePermille(), work.completedAt(100_000));
    }

    private static void submit(FrontierEngine<?> engine, ResourceSiteHarvestLabourChange payload) {
        var checkpoint = engine.checkpoint();
        var id = new CommandId("command:labour-" + checkpoint.revision().value());
        var result = engine.submit(new FrontierCommand(2, id, checkpoint.worldId(), checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(id), payload,
                Optional.of(new EngineScheduleBinding(checkpoint.revision(), checkpoint.schedules().getFirst()))));
        assertInstanceOf(CommandResult.Accepted.class, result, result.toString());
    }
}
