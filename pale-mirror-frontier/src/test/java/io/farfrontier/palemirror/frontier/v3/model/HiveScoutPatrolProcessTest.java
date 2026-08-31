package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HiveScoutPatrolProcessTest {
    @Test void anUnleasedExactScoutAdvancesOnlyAlongItsOwnNestPerimeterAndPersistsTheStep() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:scout-patrol"), 91L));
        Bioform scout = scout(state);
        ScheduledAction action = HiveScoutPatrolProcess.patrol(scout.id(), 1, 2_400L);

        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> events = HiveScoutPatrolProcess.plan(state, action);
        ScoutPatrolAdvanced advanced = (ScoutPatrolAdvanced) events.stream().map(io.farfrontier.palemirror.frontier.v3.api.ProposedEvent::payload)
                .filter(ScoutPatrolAdvanced.class::isInstance).findFirst().orElseThrow();
        assertEquals(HiveScoutPatrolProcess.positionAt(state, scout, advanced.phase()), advanced.position());
        assertEquals(advanced, FrontierWorldRuntimeDefinition.payloadCodecs().decode(advanced.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(advanced)));

        FrontierWorldState moved = HiveScoutPatrolProcess.reduce(state, state.bootstrap().hive().id(), advanced);
        assertEquals(advanced.position(), moved.actorLocations().get(scout.id()).position());
        assertEquals(moved, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(moved)));
        ScheduleEffect.Created next = (ScheduleEffect.Created) events.getLast().payload();
        assertEquals(action.dueAt().ticks() + HiveScoutPatrolProcess.INTERVAL, next.action().dueAt().ticks());
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

    private static Bioform scout(FrontierWorldState state) {
        return state.bootstrap().hive().bioforms().stream().filter(value -> value.role() == BioformRole.SCOUT).findFirst().orElseThrow();
    }
}
