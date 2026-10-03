package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.process.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldPayloadCodecs;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;

import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HiveScoutPatrolProcessTest {
    @Test void anUnleasedExactScoutAdvancesOnlyAlongItsOwnNestPerimeterAndPersistsTheStep() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:scout-patrol"), 91L));
        Bioform scout = scout(state);
        ScheduledAction action = HiveScoutPatrolProcess.patrol(scout.id(), 1, 2_400L);

        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> events = HiveScoutPatrolProcess.plan(state, action);
        ScoutPatrolAdvanced advanced = (ScoutPatrolAdvanced) events.stream().map(io.farfrontier.palemirror.frontier.v3.api.ProposedEvent::payload)
                .filter(ScoutPatrolAdvanced.class::isInstance).findFirst().orElseThrow();
        assertEquals(FrontierTestPositions.supportOf(state.actorLocations().get(scout.id())), advanced.priorPosition());
        assertEquals(HiveScoutPatrolProcess.nextPosition(state, scout), advanced.position());
        assertEquals(advanced, FrontierWorldRuntimeDefinition.payloadCodecs().decode(advanced.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(advanced)));

        if (HiveScoutPatrolProcess.active(state, scout.id()).isEmpty()) {
            var started = HiveScoutPatrolProcess.start(state, scout.id());
            assertEquals(started, FrontierWorldRuntimeDefinition.payloadCodecs().decode(started.type(),
                    FrontierWorldRuntimeDefinition.payloadCodecs().encode(started)));
            state = HiveScoutPatrolProcess.reduceStarted(state, state.bootstrap().hive().id(), started);
        }
        FrontierWorldState moved = HiveScoutPatrolProcess.reduce(state, state.bootstrap().hive().id(), advanced);
        assertEquals(advanced.position(), FrontierTestPositions.supportOf(moved.actorLocations().get(scout.id())));
        assertEquals(moved, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(moved)));
        ScheduleEffect.Created next = (ScheduleEffect.Created) events.getLast().payload();
        assertEquals(action.dueAt().ticks() + state.bootstrap().ruleset().cadence().hiveScoutPatrolInterval(), next.action().dueAt().ticks());
    }

    @Test void aHotScoutKeepsItsExactPhysicalHandOffAndColdPatrolOnlyReschedules() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:scout-patrol-hot"), 91L));
        Bioform scout = scout(state);
        AmbientActorLease lease = AmbientActorProcess.nextLease(state, scout.id(), SimInstant.ZERO);
        state = AmbientLeaseStateProcess.prepare(state, lease);
        state = AmbientLeaseStateProcess.transition(state, scout.id(), AmbientLeaseStatus.HOT);

        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> events = HiveScoutPatrolProcess.plan(state,
                HiveScoutPatrolProcess.patrol(scout.id(), 1, 2_400L));
        assertFalse(events.stream().map(io.farfrontier.palemirror.frontier.v3.api.ProposedEvent::payload).anyMatch(ScoutPatrolAdvanced.class::isInstance));
        assertTrue(events.stream().map(io.farfrontier.palemirror.frontier.v3.api.ProposedEvent::payload).anyMatch(ScheduleEffect.Created.class::isInstance));
    }

    @Test void physicalCustodyExcludesColdPatrolEvenWithoutAnAmbientOrSceneLease() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:scout-body-custody"), 91L));
        var scout = scout(state);
        var action = HiveScoutPatrolProcess.patrol(scout.id(), 1, 2_400L);
        var advance = (ScoutPatrolAdvanced) HiveScoutPatrolProcess.plan(state, action).stream()
                .map(io.farfrontier.palemirror.frontier.v3.api.ProposedEvent::payload)
                .filter(ScoutPatrolAdvanced.class::isInstance).findFirst().orElseThrow();
        state = HiveScoutPatrolProcess.reduceStarted(state, state.bootstrap().hive().id(), HiveScoutPatrolProcess.start(state, scout.id()));
        var retained = ActorBodyAuthority.demand(state, scout.id());
        assertFalse(ActorExecutionCoordinator.coldAvailable(retained, java.util.List.of(scout.id())));
        assertTrue(HiveScoutPatrolProcess.plan(retained, action).stream()
                .noneMatch(event -> event.payload() instanceof ScoutPatrolAdvanced));
        assertThrows(IllegalArgumentException.class,
                () -> HiveScoutPatrolProcess.reduce(retained, retained.bootstrap().hive().id(), advance));
    }

    @Test void aDeadScoutRetiresItsDuePatrolInsteadOfQuarantiningTheWorld() {
        WorldId world = new WorldId("frontier:scout-patrol-death");
        var engine = FrontierEngines.create(FrontierWorldRuntimeDefinition.configuration(world, 91L));
        FrontierWorldState before = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        Bioform scout = scout(before);
        CommandId admission = new CommandId("command:scout-patrol-admission");
        assertTrue(engine.submit(new FrontierCommand(1, admission, world, engine.checkpoint().revision(), engine.checkpoint().instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(admission), HiveScoutPatrolProcess.start(before, scout.id())))
                instanceof CommandResult.Accepted);
        CommandId command = new CommandId("command:scout-patrol-death");
        assertTrue(engine.submit(new FrontierCommand(1, command, world, engine.checkpoint().revision(), engine.checkpoint().instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(command),
                new AmbientActorDied(scout.id(), before.actorLocations().get(scout.id()).body(), "entity:test-player")))
                instanceof CommandResult.Accepted);

        ScheduledAction duePatrol = engine.checkpoint().schedules().stream()
                .filter(action -> action.kind().equals("frontier.hive.scout.patrol") && action.subject().equals(scout.id()))
                .findFirst().orElseThrow();
        FrontierWorldState dead = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertTrue(dead.actorExecutions().actors().get(scout.id()).current().isEmpty());
        assertTrue(HiveScoutPatrolProcess.plan(dead, duePatrol).stream()
                .map(io.farfrontier.palemirror.frontier.v3.api.ProposedEvent::payload)
                .anyMatch(effect -> effect instanceof ScheduleEffect.Cancelled cancelled && cancelled.scheduleId().equals(duePatrol.id())));
        engine.advanceTo(duePatrol.dueAt(), new WorkBudget(64, 512));

        assertEquals(io.farfrontier.palemirror.frontier.v3.api.EngineStatus.Kind.ACTIVE, engine.status().kind());
    }

    @Test void freshWorldSchedulesOnlyThePurposefullyDeployedScoutsBeforeTheFirstHiveReview() {
        var configuration = FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:scout-patrol-schedule"), 91L);
        List<SubjectId> scouts = configuration.initialState().bootstrap().hive().bioforms().stream().filter(Bioform::isScout)
                .filter(value -> HivePhysiologySupport.permitsAmbientLease(configuration.initialState().hiveColony(), value.id()))
                .map(Bioform::id).sorted().toList();
        List<ScheduledAction> patrols = configuration.initialSchedules().stream().filter(action -> action.kind().equals("frontier.hive.scout.patrol")).toList();
        assertEquals(scouts, patrols.stream().map(ScheduledAction::subject).sorted().toList());
        assertTrue(patrols.stream().allMatch(action -> action.dueAt().ticks() < 3_200L));
    }

    @Test void expandedScoutingCircuitCanNaturallyReachTheNearestSettlementSensorRange() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:scout-patrol-discovery"), 92L));
        Bioform scout = scout(state); Settlement nearest = state.bootstrap().settlements().stream()
                .min(java.util.Comparator.comparingLong(value -> distanceSquared(FrontierTestPositions.supportOf(state.actorLocations().get(scout.id())), value.anchor()))).orElseThrow();
        BlockPosition position = FrontierTestPositions.supportOf(state.actorLocations().get(scout.id())); boolean reached = false;
        for (int step = 0; step < 96; step++) {
            position = HiveScoutPatrolProcess.nextPosition(state, scout, position);
            int radius = state.bootstrap().ruleset().spatial().hiveSettlementSightRadius();
            if (distanceSquared(position, nearest.anchor()) <= (long) radius * radius) {
                reached = true; break;
            }
        }
        assertTrue(reached, "the bounded circuit must make a settlement sighting possible without a hidden target route");
    }

    @Test void ordinaryCausalSchedulesEventuallyPersistASettlementSighting() {
        var engine = FrontierEngines.create(FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:scout-patrol-planner"), 93L));
        FrontierWorldState state = null;
        for (long tick = 1L; tick <= 12_000L; tick++) {
            engine.advanceTo(new SimInstant(tick), new WorkBudget(64, 512));
            if (tick % 100L != 0L) continue;
            state = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
            if (!state.strategicPlans().hiveSettlementKnowledge().entries().isEmpty()) break;
        }
        assertTrue(state != null && !state.strategicPlans().hiveSettlementKnowledge().entries().isEmpty(),
                "a production schedule must be able to create the scout fact without a test reposition");
    }

    @Test void aHotScoutAdvancesTheSameCursorAndRetargetsOnlyItsExactNextStep() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:scout-patrol-cursor"), 91L));
        Bioform scout = scout(state); BlockPosition prior = FrontierTestPositions.supportOf(state.actorLocations().get(scout.id()));
        AmbientActorLease lease = AmbientActorProcess.nextLease(state, scout.id(), SimInstant.ZERO);
        assertEquals(AmbientGoalKind.SCOUT_PATROL, lease.goal());
        state = AmbientLeaseStateProcess.prepare(state, lease);
        state = AmbientLeaseStateProcess.transition(state, scout.id(), AmbientLeaseStatus.HOT);
        state = HiveScoutPatrolProcess.reduceStarted(state, state.bootstrap().hive().id(), HiveScoutPatrolProcess.start(state, scout.id()));
        ScoutPatrolAdvanced advanced = new ScoutPatrolAdvanced(HiveScoutPatrolProcess.requireExecution(state, scout.id()),
                1L, lease.goalBody().supportingSurface().support(), prior);

        FrontierWorldState moved = HiveScoutPatrolProcess.reduce(state, state.bootstrap().hive().id(), advanced);
        assertEquals(lease.goalBody().supportingSurface().support(), FrontierTestPositions.supportOf(moved.actorLocations().get(scout.id())));
        assertEquals(AmbientGoalKind.SCOUT_PATROL, moved.ambientLeases().get(scout.id()).goal());
        assertEquals(HiveScoutPatrolProcess.nextPosition(moved, scout), moved.ambientLeases().get(scout.id()).goalBody().supportingSurface().support());
    }

    @Test void stalePatrolCallbackCannotMoveANewExecutionOfTheSameScout() {
        var initial = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:scout-generation"), 91L));
        var scout = scout(initial);
        var running = HiveScoutPatrolProcess.reduceStarted(initial, initial.bootstrap().hive().id(), HiveScoutPatrolProcess.start(initial, scout.id()));
        var key = HiveScoutPatrolProcess.requireExecution(running, scout.id());
        var oldAdvance = new ScoutPatrolAdvanced(key, 1L, HiveScoutPatrolProcess.nextPosition(running, scout),
                running.actorLocations().get(scout.id()).supportingSurface().support());
        var presence = running.actorExecutions().next(scout.id(),
                io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.PRESENCE, scout.id());
        var released = ActorExecutionComposition.LIFECYCLE.prepareVacant(running, presence)
                .commit(running, FrontierWorldStateUpdate.begin());
        var successor = HiveScoutPatrolProcess.reduceStarted(released, released.bootstrap().hive().id(),
                HiveScoutPatrolProcess.start(released, scout.id()));
        assertThrows(IllegalArgumentException.class, () -> HiveScoutPatrolProcess.reduce(successor, successor.bootstrap().hive().id(), oldAdvance));
        assertEquals(running.actorLocations(), successor.actorLocations());
        assertTrue(HiveScoutPatrolProcess.requireExecution(successor, scout.id()).generation() > key.generation());
    }

    @Test void predecessorLessHistoricalPatrolPayloadIsExplicitlyRejected() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:scout-patrol-legacy"), 91L));
        var scout = scout(state);
        byte[] old = FrontierWorldPayloadCodecs.encodeProduction(output -> {
            FrontierWorldPayloadCodecs.writeSubject(output, scout.id()); output.writeLong(2_400L);
            output.writeInt(1); output.writeInt(64); output.writeInt(1);
        });
        assertThrows(IllegalArgumentException.class, () ->
                FrontierWorldRuntimeDefinition.payloadCodecs().decode("frontier.scout_patrol_advanced", old));
    }

    private static Bioform scout(FrontierWorldState state) {
        return state.bootstrap().hive().bioforms().stream().filter(Bioform::isScout).findFirst().orElseThrow();
    }
    private static long distanceSquared(BlockPosition left, BlockPosition right) {
        long x = (long) left.x() - right.x(), z = (long) left.z() - right.z(); return x * x + z * z;
    }
}
