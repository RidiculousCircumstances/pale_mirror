package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionState;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind;

/** Meal-owned exact reference closure; shared execution state knows no food phases or claims. */
final class ResidentMealExecutionAuthority {
    private ResidentMealExecutionAuthority() { }
    static void validate(HumanPopulation people, ActorExecutionState executions) {
        for (ResidentMeal meal : people.meals().values()) executions.requireCurrent(meal.executionId());
        for (var id : executions.current(ActorActivityKind.MEAL).values()) {
            ResidentMeal meal = people.meals().get(id.actorId());
            if (meal == null || !meal.executionId().equals(id))
                throw new IllegalArgumentException("meal execution lost its exact retained activity");
        }
    }
}
