package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.process.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldPayloadCodecs;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;

import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
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
        assertEquals(state.actorLocations().get(scout.id()).position(), advanced.priorPosition().orElseThrow());
        assertEquals(HiveScoutPatrolProcess.nextPosition(state, scout), advanced.position());
        assertEquals(advanced, FrontierWorldRuntimeDefinition.payloadCodecs().decode(advanced.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(advanced)));

        FrontierWorldState moved = HiveScoutPatrolProcess.reduce(state, state.bootstrap().hive().id(), advanced);
        assertEquals(advanced.position(), moved.actorLocations().get(scout.id()).position());
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

    @Test void freshWorldSchedulesEveryBootstrapScoutBeforeTheFirstHiveReview() {
        var configuration = FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:scout-patrol-schedule"), 91L);
        List<SubjectId> scouts = configuration.initialState().bootstrap().hive().bioforms().stream().filter(value -> value.role() == BioformRole.SCOUT)
                .map(Bioform::id).sorted().toList();
        List<ScheduledAction> patrols = configuration.initialSchedules().stream().filter(action -> action.kind().equals("frontier.hive.scout.patrol")).toList();
        assertEquals(scouts, patrols.stream().map(ScheduledAction::subject).sorted().toList());
        assertTrue(patrols.stream().allMatch(action -> action.dueAt().ticks() < 3_200L));
    }

    @Test void expandedScoutingCircuitCanNaturallyReachTheNearestSettlementSensorRange() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:scout-patrol-discovery"), 92L));
        Bioform scout = scout(state); Settlement nearest = state.bootstrap().settlements().stream()
                .min(java.util.Comparator.comparingLong(value -> distanceSquared(state.actorLocations().get(scout.id()).position(), value.anchor()))).orElseThrow();
        BlockPosition position = state.actorLocations().get(scout.id()).position(); boolean reached = false;
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
        Bioform scout = scout(state); BlockPosition prior = state.actorLocations().get(scout.id()).position();
        AmbientActorLease lease = AmbientActorProcess.nextLease(state, scout.id(), SimInstant.ZERO);
        assertEquals(AmbientGoalKind.SCOUT_PATROL, lease.goal());
        state = AmbientLeaseStateProcess.prepare(state, lease);
        state = AmbientLeaseStateProcess.transition(state, scout.id(), AmbientLeaseStatus.HOT);
        ScoutPatrolAdvanced advanced = new ScoutPatrolAdvanced(scout.id(), 1L, lease.goalPosition(), java.util.Optional.of(prior));

        FrontierWorldState moved = HiveScoutPatrolProcess.reduce(state, state.bootstrap().hive().id(), advanced);
        assertEquals(lease.goalPosition(), moved.actorLocations().get(scout.id()).position());
        assertEquals(AmbientGoalKind.SCOUT_PATROL, moved.ambientLeases().get(scout.id()).goal());
        assertEquals(HiveScoutPatrolProcess.nextPosition(moved, scout), moved.ambientLeases().get(scout.id()).goalPosition());
    }

    @Test void deployedLegacyPatrolPayloadStillDecodesWithoutGrantingHotMovement() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:scout-patrol-legacy"), 91L));
        Bioform scout = scout(state); BlockPosition legacyPosition = state.bootstrap().hive().seedNests().stream()
                .filter(nest -> nest.id().equals(scout.nestId())).findFirst().orElseThrow().anchor().offset(48, 0, 0);
        // This is the byte layout emitted by the deployed pre-cursor codec: it ends at
        // position and therefore contains neither the later optional-present flag nor a
        // predecessor. Do not construct this with the current encoder, which would hide
        // a trailing compatibility-field regression.
        byte[] deployedLegacyBytes = FrontierWorldPayloadCodecs.encodeProduction(output -> {
            FrontierWorldPayloadCodecs.writeSubject(output, scout.id()); output.writeLong(2_400L);
            output.writeInt(legacyPosition.x()); output.writeInt(legacyPosition.y()); output.writeInt(legacyPosition.z());
        });
        ScoutPatrolAdvanced decoded = (ScoutPatrolAdvanced) FrontierWorldRuntimeDefinition.payloadCodecs().decode("frontier.scout_patrol_advanced",
                deployedLegacyBytes);
        assertEquals(java.util.Optional.empty(), decoded.priorPosition());
        assertEquals(legacyPosition, HiveScoutPatrolProcess.reduce(state, state.bootstrap().hive().id(), decoded).actorLocations().get(scout.id()).position());

        AmbientActorLease lease = AmbientActorProcess.nextLease(state, scout.id(), SimInstant.ZERO);
        FrontierWorldState hot = AmbientLeaseStateProcess.transition(AmbientLeaseStateProcess.prepare(state, lease), scout.id(), AmbientLeaseStatus.HOT);
        assertThrows(IllegalArgumentException.class, () -> HiveScoutPatrolProcess.reduce(hot, hot.bootstrap().hive().id(), decoded),
                "old WAL records have no predecessor proof and therefore cannot advance a HOT exact body");
    }

    private static Bioform scout(FrontierWorldState state) {
        return state.bootstrap().hive().bioforms().stream().filter(value -> value.role() == BioformRole.SCOUT).findFirst().orElseThrow();
    }
    private static long distanceSquared(BlockPosition left, BlockPosition right) {
        long x = (long) left.x() - right.x(), z = (long) left.z() - right.z(); return x * x + z * z;
    }
}
