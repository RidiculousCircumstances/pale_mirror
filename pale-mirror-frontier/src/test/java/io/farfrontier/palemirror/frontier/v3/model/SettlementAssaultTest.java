package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SettlementAssaultTest {
    @Test void retainsExactSidesAndCannotReuseRouteEngagementSemantics() throws Exception {
        SubjectId hive = new SubjectId("hive:frontier");
        StrategicObjective objective = new StrategicObjective(new SubjectId("objective:assault"), hive,
                StrategicObjectiveKind.HIVE_ASSAULT_SETTLEMENT, Optional.empty(), 1, StrategicObjectiveStatus.ACTIVE);
        StrategicTask task = new StrategicTask(new SubjectId("task:assault"), objective.id(), hive,
                StrategicTaskKind.ASSAULT_SETTLEMENT, Optional.empty(),
                List.of(StrategicTaskRequirement.AVAILABLE_HIVE_GUARD, StrategicTaskRequirement.AVAILABLE_HIVE_BOMBER), List.of(), StrategicTaskStatus.ACTIVE);
        SettlementAssault assault = assault(task, List.of(new SubjectId("bioform:west-0")), List.of(new SubjectId("resident:1-1")));
        StrategicPlanState plans = new StrategicPlanState(Map.of(objective.id(), objective), Map.of(task.id(), task), Map.of(), Map.of(),
                SettlementInfectionKnowledge.empty(), HiveOperationKnowledge.empty(), HiveTerritoryKnowledge.empty(), HiveSettlementKnowledge.empty(),
                HiveDoctrineState.initial(), Map.of(assault.id(), assault));

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        StrategicPlanStateCodec.write(new DataOutputStream(bytes), plans);
        StrategicPlanState restored = StrategicPlanStateCodec.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));

        assertEquals(assault, restored.settlementAssaults().get(assault.id()));
        assertEquals(assault.sighting().settlementAnchor(), restored.settlementAssaults().get(assault.id()).settlementAnchor());
        assertThrows(IllegalArgumentException.class, () -> new StrategicPlanState(Map.of(objective.id(), objective), Map.of(), Map.of(), Map.of(),
                SettlementInfectionKnowledge.empty(), HiveOperationKnowledge.empty(), HiveTerritoryKnowledge.empty(), HiveSettlementKnowledge.empty(),
                HiveDoctrineState.initial(), Map.of(assault.id(), assault)));
    }

    @Test void refusesDuplicateDefendersAndAResolvedStateWithoutAnOutcome() {
        StrategicTask task = new StrategicTask(new SubjectId("task:assault"), new SubjectId("objective:assault"), new SubjectId("hive:frontier"),
                StrategicTaskKind.ASSAULT_SETTLEMENT, Optional.empty(),
                List.of(StrategicTaskRequirement.AVAILABLE_HIVE_GUARD, StrategicTaskRequirement.AVAILABLE_HIVE_BOMBER), List.of(), StrategicTaskStatus.ACTIVE);
        assertThrows(IllegalArgumentException.class, () -> assault(task, List.of(new SubjectId("bioform:west-0")),
                List.of(new SubjectId("resident:1-1"), new SubjectId("resident:1-1"))));
        assertThrows(IllegalArgumentException.class, () -> new SettlementAssault(new SubjectId("assault:northwatch"), task.id(), task.ownerId(), sighting(),
                List.of(new SettlementAssaultAttacker(new SubjectId("bioform:west-0"), List.of(new BlockPosition(0, 64, 0), sighting().settlementAnchor()), 0)),
                List.of(new SubjectId("resident:1-1")), SettlementAssaultStatus.RESOLVED, 0, Optional.empty()));
    }

    private static SettlementAssault assault(StrategicTask task, List<SubjectId> attackers, List<SubjectId> defenders) {
        HiveSettlementKnowledge.Sighting sighting = sighting();
        return new SettlementAssault(new SubjectId("assault:northwatch"), task.id(), task.ownerId(), sighting,
                attackers.stream().map(id -> new SettlementAssaultAttacker(id,
                        List.of(new BlockPosition(0, 64, 0), sighting.settlementAnchor()), 0)).toList(), defenders,
                SettlementAssaultStatus.APPROACHING, 0, Optional.empty());
    }

    private static HiveSettlementKnowledge.Sighting sighting() {
        return new HiveSettlementKnowledge.Sighting(new SubjectId("settlement:northwatch"), new SubjectId("bioform:west-0"),
                new BlockPosition(10, 64, 10), 100L);
    }
}
