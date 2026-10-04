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
        state = confirmFixturePhysicalBody(state, scout.id());
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
        ModeledActorBodyFacts.present(engine, scout.id());
        var dying = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertTrue(engine.submit(new FrontierCommand(1, command, world, engine.checkpoint().revision(), engine.checkpoint().instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(command),
                ModeledActorBodyFacts.death(dying, scout.id(), dying.actorLocations().get(scout.id()).body(), "entity:test-player")))
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
            assertEquals(io.farfrontier.palemirror.frontier.v3.api.EngineStatus.Kind.ACTIVE,
                    engine.status().kind(), engine.status().toString());
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
        state = confirmFixturePhysicalBody(state, scout.id());
        state = AmbientLeaseStateProcess.transition(state, scout.id(), AmbientLeaseStatus.HOT);
        state = HiveScoutPatrolProcess.reduceStarted(state, state.bootstrap().hive().id(), HiveScoutPatrolProcess.start(state, scout.id()));
        var captured = HiveScoutPatrolProcess.requireExecution(state, scout.id());
        ActorExecutionComposition.CAPABILITIES.requireAmbientMotion(state, captured, state.ambientLeases().get(scout.id()));
        var journey = HiveScoutPatrolProcess.journey(state, scout.id());
        var actuation = new io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId(
                ActorBodyAuthority.current(state, scout.id()), captured);
        ScoutPatrolAdvanced advanced = new ScoutPatrolAdvanced(captured, journey.goalRevision(), 1L,
                journey.target(), new SurfaceAnchor(prior), java.util.Optional.of(new ScoutPatrolAdvanced.HotArrival(actuation, lease.revision())));
        var beforeInspection = state;
        assertThrows(IllegalArgumentException.class, () -> HiveScoutPatrolProcess.reduce(beforeInspection,
                beforeInspection.bootstrap().hive().id(), advanced), "family receipt cannot fabricate HOT arrival");
        var path = HiveGroundNavigation.scoutRoute(state, scout, new SurfaceAnchor(prior), journey.target());
        state = ModeledActorBodyFacts.inspected(state, scout.id(), path.get(Math.min(1, path.size() - 1)).standingBody());
        assertEquals(journey, HiveScoutPatrolProcess.journey(state, scout.id()), "physical movement cannot retarget a scout goal");
        ActorExecutionComposition.CAPABILITIES.requireAmbientMotion(state, captured, state.ambientLeases().get(scout.id()));
        state = ModeledActorBodyFacts.inspected(state, scout.id(), journey.target().standingBody());
        var observed = state;
        var staleScope = new ScoutPatrolAdvanced(captured, journey.goalRevision(), 1L, journey.target(), advanced.priorSurface(),
                java.util.Optional.of(new ScoutPatrolAdvanced.HotArrival(actuation, lease.revision() + 1L)));
        var staleBody = new ScoutPatrolAdvanced(captured, journey.goalRevision(), 1L, journey.target(), advanced.priorSurface(),
                java.util.Optional.of(new ScoutPatrolAdvanced.HotArrival(new io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId(
                        new io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId(scout.id(), actuation.body().physicalEpoch() + 1L), captured), lease.revision())));
        assertThrows(IllegalArgumentException.class, () -> HiveScoutPatrolProcess.reduce(observed, observed.bootstrap().hive().id(), staleScope));
        assertThrows(IllegalArgumentException.class, () -> HiveScoutPatrolProcess.reduce(observed, observed.bootstrap().hive().id(), staleBody));
        assertEquals(advanced, FrontierWorldRuntimeDefinition.payloadCodecs().decode(advanced.type(),
                FrontierWorldRuntimeDefinition.payloadCodecs().encode(advanced)));
        FrontierWorldState moved = HiveScoutPatrolProcess.reduce(observed, observed.bootstrap().hive().id(), advanced);
        assertEquals(observed.actorLocations(), moved.actorLocations(), "HOT family owns progress, not positions");
        assertEquals(journey.goalRevision() + 1L, HiveScoutPatrolProcess.journey(moved, scout.id()).goalRevision());
        assertThrows(IllegalArgumentException.class, () -> HiveScoutPatrolProcess.reduce(moved, moved.bootstrap().hive().id(), advanced));
        assertEquals(moved, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(moved)));
        ActorExecutionComposition.CAPABILITIES.requireAmbientMotion(moved, captured, moved.ambientLeases().get(scout.id()));
        var capturedPresentation = state.ambientLeases().get(scout.id());
        assertThrows(IllegalArgumentException.class, () -> ActorExecutionComposition.CAPABILITIES.requireAmbientMotion(
                moved, captured, capturedPresentation), "old scout target cannot borrow the next goal's authority");
        assertEquals(lease.goalBody().supportingSurface().support(), FrontierTestPositions.supportOf(moved.actorLocations().get(scout.id())));
        assertEquals(AmbientGoalKind.SCOUT_PATROL, moved.ambientLeases().get(scout.id()).goal());
        assertEquals(HiveScoutPatrolProcess.nextPosition(moved, scout), moved.ambientLeases().get(scout.id()).goalBody().supportingSurface().support());
    }

    @Test void stalePatrolCallbackCannotMoveANewExecutionOfTheSameScout() {
        var initial = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:scout-generation"), 91L));
        var scout = scout(initial);
        var running = HiveScoutPatrolProcess.reduceStarted(initial, initial.bootstrap().hive().id(), HiveScoutPatrolProcess.start(initial, scout.id()));
        var key = HiveScoutPatrolProcess.requireExecution(running, scout.id());
        var journey = HiveScoutPatrolProcess.journey(running, scout.id());
        var oldAdvance = new ScoutPatrolAdvanced(key, journey.goalRevision(), 1L, journey.target(),
                running.actorLocations().get(scout.id()).supportingSurface(), java.util.Optional.empty());
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

    @Test void partialHotDepartureKeepsTheGoalThroughColdRecoveryAndRejectsKnownBlockage() {
        var world = new WorldId("frontier:scout-partial-departure");
        var baseline = FrontierWorldState.initial(FrontierBootstrapper.create(world, 91L));
        var firstTarget = HiveScoutPatrolProcess.nextPosition(baseline, scout(baseline));
        var terrain = baseline.bootstrap().terrain().withSurveyedSupport(firstTarget.x(), firstTarget.z(), firstTarget.y() + 1);
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(world, 91L, baseline.bootstrap().ruleset(), terrain));
        var scout = scout(state);
        state = HiveScoutPatrolProcess.reduceStarted(state, state.bootstrap().hive().id(), HiveScoutPatrolProcess.start(state, scout.id()));
        var journey = HiveScoutPatrolProcess.journey(state, scout.id());
        var lease = AmbientActorProcess.nextLease(state, scout.id(), SimInstant.ZERO);
        state = AmbientLeaseStateProcess.prepare(state, lease);
        state = confirmFixturePhysicalBody(state, scout.id());
        state = AmbientLeaseStateProcess.transition(state, scout.id(), AmbientLeaseStatus.HOT);
        var path = HiveGroundNavigation.scoutRoute(state, scout, state.actorLocations().get(scout.id()).supportingSurface(), journey.target());
        assertEquals(firstTarget.y() + 1, journey.target().y(), "the declared goal follows surveyed support, not nest datum");
        assertTrue(path.stream().map(SurfaceAnchor::y).distinct().count() > 1L);
        assertTrue(path.size() > 2, "production scout leg supplies a genuine partial departure");
        var partial = path.get(1).standingBody();
        state = ModeledActorBodyFacts.inspected(state, scout.id(), partial);
        state = AmbientLeaseStateProcess.transition(state, scout.id(), AmbientLeaseStatus.DRAINING);
        var held = state;
        var action = HiveScoutPatrolProcess.patrol(scout.id(), 1, 2_400L);
        assertTrue(HiveScoutPatrolProcess.plan(held, action).stream().noneMatch(event -> event.payload() instanceof ScoutPatrolAdvanced));
        state = ModeledActorBodyFacts.unloaded(state, scout.id());
        state = AmbientLeaseStateProcess.release(state, new AmbientLeaseReleased(scout.id(), partial,
                state.actorLocations().get(scout.id()).condition().health()));
        state = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
        assertEquals(journey, HiveScoutPatrolProcess.journey(state, scout.id()));
        assertEquals(partial, state.actorLocations().get(scout.id()).body());
        var recovered = state;
        var advance = (ScoutPatrolAdvanced) HiveScoutPatrolProcess.plan(recovered, action).stream()
                .map(io.farfrontier.palemirror.frontier.v3.api.ProposedEvent::payload)
                .filter(ScoutPatrolAdvanced.class::isInstance).findFirst().orElseThrow();
        assertEquals(partial.supportingSurface(), advance.priorSurface());
        assertEquals(journey.target(), advance.target());
        var deltas = new java.util.LinkedHashMap<>(recovered.physicalDeltas());
        var feet = journey.target().support().offset(0, 1, 0);
        deltas.put(feet, new PhysicalDelta(feet, PhysicalDeltaKind.UNKNOWN_SCAR,
                java.util.Optional.empty(), java.util.Optional.empty(), "player:scout-destination-block"));
        var blocked = recovered.withChanges(FrontierWorldStateUpdate.begin().physicalDeltas(deltas));
        assertTrue(HiveScoutPatrolProcess.plan(blocked, action).stream().noneMatch(event -> event.payload() instanceof ScoutPatrolAdvanced));
        assertThrows(IllegalArgumentException.class, () -> HiveScoutPatrolProcess.reduce(blocked, blocked.bootstrap().hive().id(), advance));
        assertEquals(journey, HiveScoutPatrolProcess.journey(blocked, scout.id()));
        var moved = HiveScoutPatrolProcess.reduce(recovered, recovered.bootstrap().hive().id(), advance);
        assertEquals(journey.target().standingBody(), moved.actorLocations().get(scout.id()).body());
        assertEquals(recovered.actorExecutions(), moved.actorExecutions());
        assertEquals(recovered.inventory(), moved.inventory());
        assertEquals(journey.goalRevision() + 1L, HiveScoutPatrolProcess.journey(moved, scout.id()).goalRevision());
        assertThrows(IllegalArgumentException.class, () -> HiveScoutPatrolProcess.reduce(moved, moved.bootstrap().hive().id(), advance));
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

    /** Modeled body confirmation, not a shortcut through activity admission. */
    private static FrontierWorldState confirmFixturePhysicalBody(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.SubjectId actor) {
        var location = state.actorLocations().get(actor);
        return ActorBodyAuthority.present(state, new io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyPresent(
                ActorBodyAuthority.current(state, actor), location.body(), location.condition().health(),
                location.body(), location.condition().health()));
    }

    private static Bioform scout(FrontierWorldState state) {
        return state.bootstrap().hive().bioforms().stream().filter(Bioform::isScout).findFirst().orElseThrow();
    }
    private static long distanceSquared(BlockPosition left, BlockPosition right) {
        long x = (long) left.x() - right.x(), z = (long) left.z() - right.z(); return x * x + z * z;
    }
}
