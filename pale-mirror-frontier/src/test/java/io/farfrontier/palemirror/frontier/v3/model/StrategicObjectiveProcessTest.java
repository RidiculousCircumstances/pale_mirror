package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedRatio;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
import io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StrategicObjectiveProcessTest {
    @Test
    void hiveUtilitySelectsOneExactExpansionObjectiveAndDurableTask() {
        FrontierWorldState state = initial("frontier:strategic-hive", 401L); SubjectId hive = state.bootstrap().hive().id();

        List<ProposedEvent> planned = StrategicObjectiveProcess.plan(state, StrategicObjectiveProcess.review(hive, 1, 60L));

        assertEquals(3, planned.size());
        StrategicObjectiveSelected selected = assertInstanceOf(StrategicObjectiveSelected.class, planned.getFirst().payload());
        StrategicTaskPlanned task = assertInstanceOf(StrategicTaskPlanned.class, planned.get(1).payload());
        assertEquals(StrategicObjectiveKind.HIVE_EXPAND_INFECTION, selected.objective().kind());
        assertEquals(StrategicTaskKind.SPREAD_INFECTION_CELL, task.task().kind());
        assertEquals(List.of(StrategicTaskRequirement.OPERATIONAL_HEART), task.task().requirements());
        state = StrategicObjectiveProcess.reduceObjective(state, hive, selected);
        state = StrategicObjectiveProcess.reduceTask(state, hive, task);
        assertEquals(1, state.strategicPlans().objectives().size()); assertEquals(1, state.strategicPlans().tasks().size());
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)));
        assertEquals(selected, FrontierWorldRuntimeDefinition.payloadCodecs().decode(selected.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(selected)));
        assertEquals(task, FrontierWorldRuntimeDefinition.payloadCodecs().decode(task.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(task)));
    }

    @Test
    void settlementUtilityUsesOnlyItsLocalInfectionAndCannotDuplicateItsActiveObjective() {
        FrontierWorldState state = initial("frontier:strategic-settlement", 402L); Settlement settlement = state.bootstrap().settlements().getFirst();
        InfectionCell nearby = InfectionCell.at(settlement.anchor()); state = state.withInfection(nearby, new FixedRatio(new FixedScalar(750_000L)));

        List<ProposedEvent> planned = StrategicObjectiveProcess.plan(state, StrategicObjectiveProcess.review(settlement.id(), 1, 40L));

        StrategicObjectiveSelected selected = assertInstanceOf(StrategicObjectiveSelected.class, planned.getFirst().payload());
        assertEquals(StrategicObjectiveKind.SETTLEMENT_CONTAIN_LOCAL_INFECTION, selected.objective().kind()); assertEquals(nearby, selected.objective().infectionTarget().orElseThrow());
        state = StrategicObjectiveProcess.reduceObjective(state, settlement.id(), selected);
        state = StrategicObjectiveProcess.reduceTask(state, settlement.id(), assertInstanceOf(StrategicTaskPlanned.class, planned.get(1).payload()));
        assertEquals(1, StrategicObjectiveProcess.plan(state, StrategicObjectiveProcess.review(settlement.id(), 2, 240L)).size());
    }

    @Test
    void foreignPlannerIdentityAndForgedTaskDecompositionFailClosed() {
        FrontierWorldState state = initial("frontier:strategic-rejection", 403L);
        assertThrows(IllegalArgumentException.class, () -> StrategicObjectiveProcess.plan(state, StrategicObjectiveProcess.review(new SubjectId("settlement:foreign"), 1, 1L)));
        StrategicObjective objective = new StrategicObjective(new SubjectId("objective:test"), state.bootstrap().hive().id(), StrategicObjectiveKind.HIVE_EXPAND_INFECTION,
                java.util.Optional.of(InfectionCell.at(state.bootstrap().hive().seedNests().getFirst().anchor())), 1, StrategicObjectiveStatus.ACTIVE);
        StrategicPlanState plans = StrategicPlanState.empty().addObjective(objective);
        StrategicTask forged = new StrategicTask(new SubjectId("task:test"), objective.id(), objective.ownerId(), StrategicTaskKind.DECONTAMINATE_INFECTION_CELL,
                objective.infectionTarget(), List.of(StrategicTaskRequirement.ACTIVE_INFIRMARY, StrategicTaskRequirement.EXACT_DECONTAMINATION_REAGENT), List.of(), StrategicTaskStatus.PENDING);
        assertThrows(IllegalArgumentException.class, () -> plans.addTask(forged));
    }

    @Test
    void scheduledWorldWorkPersistsOneUtilityPlanPerEligibleSide() {
        var engine = FrontierEngines.create(FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:strategic-scheduled"), 404L));

        for (long tick = 100L; tick <= 3_300L; tick += 100L) {
            engine.advanceTo(new io.farfrontier.palemirror.frontier.v3.api.SimInstant(tick), new WorkBudget(64, 512));
        }

        FrontierWorldState state = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertEquals(io.farfrontier.palemirror.frontier.v3.api.EngineStatus.Kind.ACTIVE, engine.status().kind(), engine.status().failureDetail().orElse(""));
        assertEquals(state.strategicPlans().objectives().size(), state.strategicPlans().tasks().size());
        org.junit.jupiter.api.Assertions.assertTrue(state.strategicPlans().objectives().size() >= 1 && state.strategicPlans().objectives().size() <= 13,
                () -> "strategic objectives=" + state.strategicPlans().objectives());
        assertEquals(1, state.strategicPlans().objectives().values().stream().filter(value -> value.ownerId().equals(state.bootstrap().hive().id())).count(),
                () -> "strategic objectives=" + state.strategicPlans().objectives());
    }

    private static FrontierWorldState initial(String world, long seed) {
        return FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId(world), seed));
    }
}
