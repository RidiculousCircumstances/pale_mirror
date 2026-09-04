package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.SettlementServiceWorkStarted;

/** Sole pure-domain reducer owner for resident-owned settlement service work. */
final class FrontierSettlementServiceWorkProcessModule implements FrontierWorldProcessModule {
    @Override public CommandPlan planCommand(FrontierWorldState state, FrontierCommand command) {
        return FrontierWorldCommandPlanner.rejected("settlement-service-work process does not admit command: " + command.payload().type());
    }

    @Override public FrontierWorldState reduce(FrontierWorldState state, FrontierEvent event) {
        if (event.payload() instanceof SettlementServiceWorkStarted started) {
            return SettlementServiceWorkProcess.reduceStarted(state, event.subject(), started);
        }
        throw new IllegalArgumentException("settlement-service-work process does not own event: " + event.payload().type());
    }
}
