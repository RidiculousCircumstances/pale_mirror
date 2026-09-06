package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.*;

/** Exact reducer owner for settlement strategic facts. */
final class FrontierStrategyProcessModule implements FrontierWorldProcessModule {
    @Override public FrontierWorldState reduce(FrontierWorldState state, FrontierEvent event) {
        return switch (event.payload()) {
            case SettlementInfectionObserved observed -> SettlementPerceptionProcess.reduce(state, event.subject(), observed);
            case StrategicObjectiveSelected selected -> StrategicObjectiveProcess.reduceObjective(state, event.subject(), selected);
            case StrategicTaskPlanned planned -> StrategicObjectiveProcess.reduceTask(state, event.subject(), planned);
            case StrategicTaskTransition transition -> StrategicObjectiveProcess.reduceTaskTransition(state, event.subject(), transition);
            default -> throw new IllegalArgumentException("strategy process does not own event: " + event.payload().type());
        };
    }
}
