package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RouteEngagementTest {
    @Test void retainsOnlyExactBoundedDistinctAttackers() {
        RouteEngagement engagement = engagement(List.of(new SubjectId("bioform:west-0"), new SubjectId("bioform:west-1")));
        assertEquals(RouteEngagementStatus.APPROACHING, engagement.status());
        assertEquals(RouteEngagementStatus.WAITING_FOR_INTERCEPT, engagement.withStatus(RouteEngagementStatus.WAITING_FOR_INTERCEPT).status());
        assertThrows(IllegalArgumentException.class, () -> engagement(List.of(new SubjectId("bioform:west-0"), new SubjectId("bioform:west-0"))));
    }

    @Test void survivesStrategicPlanSnapshotAndRejectsOrphanTask() throws Exception {
        SubjectId hive = new SubjectId("hive:frontier");
        StrategicObjective objective = new StrategicObjective(new SubjectId("objective:engagement"), hive, StrategicObjectiveKind.HIVE_EXPAND_INFECTION,
                Optional.of(new InfectionCell(0, 0)), 1, StrategicObjectiveStatus.ACTIVE);
        StrategicTask task = new StrategicTask(new SubjectId("task:1"), objective.id(), hive, StrategicTaskKind.SPREAD_INFECTION_CELL,
                Optional.of(new InfectionCell(0, 0)), List.of(StrategicTaskRequirement.OPERATIONAL_HEART), List.of(), StrategicTaskStatus.ACTIVE);
        RouteEngagement engagement = engagement(List.of(new SubjectId("bioform:west-0")));
        StrategicPlanState plans = new StrategicPlanState(Map.of(objective.id(), objective), Map.of(task.id(), task), Map.of(), Map.of(engagement.id(), engagement));
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        StrategicPlanStateCodec.write(new java.io.DataOutputStream(bytes), plans);
        StrategicPlanState restored = StrategicPlanStateCodec.read(new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray())));
        assertEquals(engagement, restored.routeEngagements().get(engagement.id()));
        assertThrows(IllegalArgumentException.class, () -> new StrategicPlanState(Map.of(objective.id(), objective), Map.of(), Map.of(), Map.of(engagement.id(), engagement)));
        assertEquals(RouteEngagementStatus.WAITING_FOR_INTERCEPT, restored.transitionEngagement(engagement.id(), RouteEngagementStatus.WAITING_FOR_INTERCEPT)
                .routeEngagements().get(engagement.id()).status());
    }

    @Test void compactionRetiresEngagementWithItsTerminalTask() {
        SubjectId hive = new SubjectId("hive:frontier");
        StrategicObjective objective = new StrategicObjective(new SubjectId("objective:engagement"), hive, StrategicObjectiveKind.HIVE_EXPAND_INFECTION,
                Optional.of(new InfectionCell(0, 0)), 1, StrategicObjectiveStatus.ACTIVE);
        StrategicTask task = new StrategicTask(new SubjectId("task:1"), objective.id(), hive, StrategicTaskKind.SPREAD_INFECTION_CELL,
                Optional.of(new InfectionCell(0, 0)), List.of(StrategicTaskRequirement.OPERATIONAL_HEART), List.of(), StrategicTaskStatus.ACTIVE);
        RouteEngagement engagement = engagement(List.of(new SubjectId("bioform:west-0")));
        StrategicPlanState state = new StrategicPlanState(Map.of(objective.id(), objective), Map.of(task.id(), task), Map.of(), Map.of())
                .startEngagement(engagement)
                .transitionTask(task.id(), StrategicTaskStatus.COMPLETED);

        for (int index = 0; index < 128; index++) {
            SubjectId objectiveId = new SubjectId("objective:completed-" + index);
            SubjectId taskId = new SubjectId("task:completed-" + index);
            SubjectId ownerId = new SubjectId("settlement:" + index);
            state = state.addObjective(new StrategicObjective(objectiveId, ownerId, StrategicObjectiveKind.HIVE_GROW_ORGANISM,
                    Optional.empty(), index + 2, StrategicObjectiveStatus.ACTIVE));
            state = state.addTask(new StrategicTask(taskId, objectiveId, ownerId, StrategicTaskKind.GROW_HIVE_ORGANISM,
                    Optional.empty(), List.of(StrategicTaskRequirement.EXACT_HIVE_BIOMASS), List.of(), StrategicTaskStatus.ACTIVE));
            state = state.transitionTask(taskId, StrategicTaskStatus.COMPLETED);
        }

        assertFalse(state.routeEngagements().containsKey(engagement.id()));
    }

    @Test void fullWorldRejectsEngagementWithoutItsCanonicalOperation() {
        FrontierWorldState initial = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:engagement"), 17L));
        SubjectId hive = initial.bootstrap().hive().id();
        StrategicObjective objective = new StrategicObjective(new SubjectId("objective:engagement"), hive, StrategicObjectiveKind.HIVE_EXPAND_INFECTION,
                Optional.of(new InfectionCell(0, 0)), 1, StrategicObjectiveStatus.ACTIVE);
        StrategicTask task = new StrategicTask(new SubjectId("task:1"), objective.id(), hive, StrategicTaskKind.SPREAD_INFECTION_CELL,
                Optional.of(new InfectionCell(0, 0)), List.of(StrategicTaskRequirement.OPERATIONAL_HEART), List.of(), StrategicTaskStatus.ACTIVE);
        SubjectId attacker = initial.bootstrap().hive().bioforms().getFirst().id();
        StrategicPlanState plans = StrategicPlanState.empty().addObjective(objective).addTask(task)
                .startEngagement(new RouteEngagement(new SubjectId("engagement:missing-operation"), task.id(), new SubjectId("operation:missing"), hive,
                        List.of(new EngagementAttacker(attacker, List.of(initial.actorLocations().get(attacker).position(), new BlockPosition(1, 64, 1)), 0)),
                        new BlockPosition(1, 64, 1), RouteEngagementStatus.APPROACHING, 0, Optional.empty()));

        assertThrows(IllegalArgumentException.class, () -> initial.withStrategicPlans(plans));
    }

    private static RouteEngagement engagement(List<SubjectId> attackers) {
        BlockPosition intercept = new BlockPosition(1, 64, 1);
        return new RouteEngagement(new SubjectId("engagement:1"), new SubjectId("task:1"), new SubjectId("operation:1"),
                new SubjectId("hive:frontier"), attackers.stream().map(attacker -> new EngagementAttacker(attacker,
                List.of(new BlockPosition(0, 64, 0), intercept), 0)).toList(), intercept, RouteEngagementStatus.APPROACHING, 0, Optional.empty());
    }
}
