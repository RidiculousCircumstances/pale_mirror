package io.farfrontier.palemirror.frontier.v3.model;

import org.junit.jupiter.api.Test;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.process.ProductionProcess;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProductionWorkClockTest {
    @Test void actualHotCommandsAndColdEventsEarnTheSameLaborAtEachRetainedDeadline() {
        var fixture = ProductionProcessTest.activeMaterializedProduction();
        var state = fixture.state();
        var workshop = state.bootstrap().settlements().getFirst().structures().stream()
                .filter(value -> value.id().equals(fixture.job().facilityId())).findFirst().orElseThrow();
        var route = ProductionWorkTraversal.compile(state, workshop, fixture.job().workerId(),
                state.actorLocations().get(fixture.job().workerId()), fixture.job().id());
        var job = fixture.job().withWorkTraversal(route, route.linearCorridorSurfaces().size() - 1)
                .withWorkProgress(ProductionWorkProgress.processing(0));
        var body = route.linearCorridorSurfaces().getLast().standingBody();
        var cold = state.withChanges(FrontierWorldStateUpdate.begin().productionJobs(Map.of(job.id(), job)))
                .withActorBody(job.workerId(), body);
        var world = state.bootstrap().worldId();
        var lease = SceneLease.forCause(new SceneLeaseId("lease:production-clock"), world, new ProductionWorkSceneCause(job.id()),
                body.supportingSurface().support(), SimInstant.ZERO, 1L, SceneLeaseStatus.PREPARED,
                List.of(new SceneMember(job.workerId(), SceneLease.deterministicEntityId(world, job.workerId()))), Set.of(), Optional.empty());
        var hot = cold.prepareSceneLease(lease).transitionSceneLease(lease.id(), SceneLeaseStatus.HOT);
        var base = FrontierWorldRuntimeDefinition.configuration(world, 91L);
        var engine = FrontierEngines.create(new FrontierEngineConfiguration<>(world, hot, SimInstant.ZERO,
                base.commandPlanner(), base.scheduledPlanner(), base.reducer(), new FrontierWorldStateCodec(),
                base.projectionMapper(), base.limits(), List.of(ProductionProcess.complete(job, 20)), base.transactionCommitter()));
        var coldAction = ProductionProcess.complete(job, 20);
        for (int unit = 1; unit <= ProductionWorkProgress.REQUIRED_PROCESSING_TICKS; unit++) {
            var next = unit == ProductionWorkProgress.REQUIRED_PROCESSING_TICKS ? ProductionWorkProgress.outputReady() : ProductionWorkProgress.processing(unit);
            var payload = new ProductionWorkProgressed(job.id(), lease.id(), body, next);
            var early = engine.checkpoint();
            var earlyId = new CommandId("command:production-early-" + unit);
            assertInstanceOf(CommandResult.Rejected.class, engine.submit(new FrontierCommand(2, earlyId, world,
                    early.revision(), early.instant(), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(earlyId), payload,
                    Optional.of(new EngineScheduleBinding(early.revision(), early.schedules().getFirst())))));
            engine.advanceTo(new SimInstant(unit * 20L), new WorkBudget(100, 100));
            var checkpoint = engine.checkpoint();
            var id = new CommandId("command:production-due-" + unit);
            var result = engine.submit(new FrontierCommand(2, id, world,
                    checkpoint.revision(), checkpoint.instant(), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(id), payload,
                    Optional.of(new EngineScheduleBinding(checkpoint.revision(), checkpoint.schedules().getFirst()))));
            assertInstanceOf(CommandResult.Accepted.class, result, result.toString());
            var planned = ProductionProcess.planCompletion(cold, coldAction);
            cold = ProductionProcess.reduceColdWorkAdvanced(cold, job.settlementId(), (ProductionColdWorkAdvanced) planned.getFirst().payload());
            coldAction = ((ScheduleEffect.Rescheduled) planned.get(1).payload()).replacement();
            var restoredHot = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
            assertEquals(cold.productionJobs().get(job.id()).workProgress(), restoredHot.productionJobs().get(job.id()).workProgress());
            assertEquals(coldAction.dueAt(), engine.checkpoint().schedules().getFirst().dueAt());
        }
    }
    @Test void arrivalStartsLaborWithoutCreditingTravelOrObserverAbsence() {
        assertEquals(1_020L, ProductionWorkProgress.inputReady().nextWorkDue(100, 1_000));
        assertThrows(IllegalArgumentException.class, () -> ProductionWorkProgress.processing(0).nextWorkDue(1_020, 1_019));
        assertEquals(1_040L, ProductionWorkProgress.processing(0).nextWorkDue(1_020, 1_020));
        assertEquals(5_020L, ProductionWorkProgress.processing(1).nextWorkDue(1_040, 5_000),
                "a delayed physical observation earns one unit, not unobserved catch-up labor");
    }

    @Test void switchingRepresentationDoesNotChangeAStationaryUnitsDeadline() {
        long due = ProductionWorkProgress.inputReady().nextWorkDue(0, 100);
        for (int completed = 0; completed < ProductionWorkProgress.REQUIRED_PROCESSING_TICKS; completed++) {
            long coldNext = Math.addExact(due, ProductionWorkProgress.SIMULATION_TICKS_PER_WORK_UNIT);
            long hotNext = ProductionWorkProgress.processing(completed).nextWorkDue(due, due);
            assertEquals(coldNext, hotNext);
            due = (completed % 2 == 0) ? hotNext : coldNext;
        }
        assertEquals(1_720L, due);
    }
}
