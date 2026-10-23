package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;

/** Closed objective-to-producer registration. The executor never infers a recipe from a task ID. */
final class ProductionPlanningComposition {
    @FunctionalInterface private interface Planner {
        List<ProposedEvent> start(FrontierWorldState state, StrategicTask task, ScheduledAction action, long now);
    }
    private static final Map<StrategicObjectiveKind, Planner> PLANNERS = Map.of(
            StrategicObjectiveKind.SETTLEMENT_PRODUCE_BREAD, BakeryProductionPlanning::planStart,
            StrategicObjectiveKind.SETTLEMENT_COMPANY_PRODUCTION, CompanyBakeryPlanning::planStart,
            StrategicObjectiveKind.SETTLEMENT_MAINTAIN_EQUIPMENT, EquipmentJobAdmission::planStart);
    static List<ProposedEvent> start(FrontierWorldState state, StrategicTask task, ScheduledAction action, long now) {
        var objective = state.strategicPlans().objectives().get(task.objectiveId());
        var planner = PLANNERS.get(objective.kind());
        if (planner == null) throw new IllegalArgumentException("no production planner registered for " + objective.kind());
        return planner.start(state, task, action, now);
    }
    private ProductionPlanningComposition() { }
}
