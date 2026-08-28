package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedRatio;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HiveInfectionProcessTest {
    @Test
    void liveHeartReseedsThroughItsDurableExpansionTaskAfterExactDecontamination() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:hive-infection"), 105L));
        for (InfectionCell cell : List.copyOf(state.infection().keySet())) state = state.withInfection(cell, new FixedRatio(FixedScalar.ZERO));
        InfectionCell target = HiveInfectionProcess.expansionTarget(state).orElseThrow();
        state = withTask(state, target);

        List<ProposedEvent> planned = HiveInfectionProcess.plan(state, HiveInfectionProcess.task(onlyTask(state), 1, 100L));

        assertEquals(new StrategicTaskTransition(onlyTask(state).id(), StrategicTaskStatus.ACTIVE), planned.getFirst().payload());
        InfectionChanged changed = assertInstanceOf(InfectionChanged.class, planned.get(1).payload());
        assertEquals(target, changed.cell());
        assertTrue(changed.intensity().value().raw() > 0L);
        assertInstanceOf(ScheduleEffect.Created.class, planned.get(2).payload());
    }

    @Test
    void destroyedHeartsBlockTheOwnedTaskInsteadOfContinuingOwnerlessMetabolism() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:hive-infection-destroyed"), 106L));
        InfectionCell target = HiveInfectionProcess.expansionTarget(state).orElseThrow();
        for (HiveOrgan heart : state.bootstrap().hive().organs().stream().filter(organ -> organ.kind() == HiveOrganKind.HEART).toList()) {
            int threshold = (FrontierGrayboxPlan.intactOrganCellCount(heart) + 2) / 3;
            List<GrayboxCell> cells = FrontierGrayboxPlan.compile(state).cells().values().stream().filter(cell -> cell.ownerId().equals(heart.id()))
                    .sorted(java.util.Comparator.comparingInt((GrayboxCell cell) -> cell.position().x())
                            .thenComparingInt(cell -> cell.position().y()).thenComparingInt(cell -> cell.position().z())).toList();
            for (int index = 0; index < threshold; index++) {
                GrayboxCell cell = cells.get(index);
                state = state.recordPhysicalDelta(new PhysicalDelta(cell.position(), PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS,
                        Optional.of(heart.id()), Optional.of(cell.semanticPart()), "test:heart-loss"));
            }
        }
        state = withTask(state, target);

        List<ProposedEvent> planned = HiveInfectionProcess.plan(state, HiveInfectionProcess.task(onlyTask(state), 1, 100L));

        assertEquals(List.of(new ProposedEvent(state.bootstrap().hive().id(), new StrategicTaskTransition(onlyTask(state).id(), StrategicTaskStatus.BLOCKED))), planned);
    }

    private static FrontierWorldState withTask(FrontierWorldState state, InfectionCell target) {
        SubjectId hive = state.bootstrap().hive().id();
        StrategicObjective objective = new StrategicObjective(new SubjectId("objective:hive-infection"), hive, StrategicObjectiveKind.HIVE_EXPAND_INFECTION,
                Optional.of(target), 1, StrategicObjectiveStatus.ACTIVE);
        StrategicTask task = new StrategicTask(new SubjectId("task:hive-infection"), objective.id(), hive, StrategicTaskKind.SPREAD_INFECTION_CELL,
                Optional.of(target), List.of(StrategicTaskRequirement.OPERATIONAL_HEART), List.of(), StrategicTaskStatus.PENDING);
        return state.withStrategicPlans(StrategicPlanState.empty().addObjective(objective).addTask(task));
    }

    private static StrategicTask onlyTask(FrontierWorldState state) { return state.strategicPlans().tasks().values().stream().findFirst().orElseThrow(); }
}
