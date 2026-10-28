package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionState;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind;
import java.util.List;

/** Meal-owned exact reference closure; shared execution state knows no food phases or claims. */
final class ResidentMealExecutionAuthority {
    private static final ImmutableReferenceIndex.Source<ActorExecutionState, SubjectId,
            io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecution> EXECUTIONS =
            new ImmutableReferenceIndex.Source<>(ActorExecutionState::actors);
    private static final ImmutableReferenceIndex<ActorExecutionState, SubjectId, ResidentMeal> FORWARD = new ImmutableReferenceIndex<>(
            HumanPopulation.MAX_RESIDENTS, 1, List.of(EXECUTIONS),
            (executions, meal) -> List.of(EXECUTIONS.at(meal.executionId().actorId())),
            (executions, actor, meal) -> executions.requireCurrent(meal.executionId()));
    private static final ImmutableReferenceIndex.Source<HumanPopulation, SubjectId, ResidentMeal> MEALS =
            new ImmutableReferenceIndex.Source<>(HumanPopulation::meals);
    private static final ImmutableReferenceIndex<HumanPopulation, SubjectId, ActorExecutionId> REVERSE = new ImmutableReferenceIndex<>(
            HumanPopulation.MAX_RESIDENTS, 1, List.of(MEALS),
            (people, execution) -> List.of(MEALS.at(execution.actorId())), (people, actor, execution) -> {
                var meal = people.meals().get(execution.actorId());
                if (meal == null || !meal.executionId().equals(execution))
                    throw new IllegalArgumentException("meal execution lost its exact retained activity");
            });
    private ResidentMealExecutionAuthority() { }
    static void validate(HumanPopulation people, ActorExecutionState executions) {
        FORWARD.validate(ResidentMealExecutionAuthority.class, people.meals(), executions);
        REVERSE.validate(ResidentMealExecutionAuthority.class, executions.current(ActorActivityKind.MEAL), people);
    }
}
