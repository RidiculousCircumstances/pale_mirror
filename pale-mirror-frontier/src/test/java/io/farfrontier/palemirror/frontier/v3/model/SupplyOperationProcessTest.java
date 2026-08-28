package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SupplyOperationProcessTest {
    @Test
    void missingBreadBlocksThePreparationAndItsDependentDelivery() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:supply-blocked"), 91L));
        Settlement settlement = state.bootstrap().settlements().getFirst();
        StrategicObjective objective = new StrategicObjective(new SubjectId("objective:supply"), settlement.id(),
                StrategicObjectiveKind.SETTLEMENT_DELIVER_BREAD_TO_HIVE, Optional.empty(), 1, StrategicObjectiveStatus.ACTIVE);
        StrategicTask preparation = new StrategicTask(new SubjectId("task:supply-prepare"), objective.id(), settlement.id(), StrategicTaskKind.PREPARE_BREAD_CARGO,
                Optional.empty(), List.of(StrategicTaskRequirement.EXACT_BREAD_CARGO), List.of(), StrategicTaskStatus.PENDING);
        StrategicTask delivery = new StrategicTask(new SubjectId("task:supply-deliver"), objective.id(), settlement.id(), StrategicTaskKind.DELIVER_BREAD_TO_HIVE,
                Optional.empty(), List.of(StrategicTaskRequirement.PASSABLE_SUPPLY_ROUTE, StrategicTaskRequirement.AVAILABLE_HAULER,
                StrategicTaskRequirement.AVAILABLE_GUARD), List.of(preparation.id()), StrategicTaskStatus.PENDING);
        state = state.withStrategicPlans(StrategicPlanState.empty().addObjective(objective).addTask(preparation).addTask(delivery));

        List<ProposedEvent> planned = SupplyOperationProcess.planStart(state, SupplyOperationProcess.start(preparation, 100L));

        assertEquals(List.of(new StrategicTaskTransition(preparation.id(), StrategicTaskStatus.BLOCKED),
                new StrategicTaskTransition(delivery.id(), StrategicTaskStatus.BLOCKED)), planned.stream().map(ProposedEvent::payload).toList());
        FrontierWorldState reduced = StrategicObjectiveProcess.reduceTaskTransition(state, settlement.id(), (StrategicTaskTransition) planned.getFirst().payload());
        reduced = StrategicObjectiveProcess.reduceTaskTransition(reduced, settlement.id(), (StrategicTaskTransition) planned.get(1).payload());
        assertEquals(StrategicObjectiveStatus.BLOCKED, reduced.strategicPlans().objectives().get(objective.id()).status());
    }
}
