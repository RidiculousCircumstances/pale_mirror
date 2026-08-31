package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.CommandRejection;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.RejectionCode;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;
import io.farfrontier.palemirror.frontier.v3.kernel.DeterministicProcessRegistry;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;

/** Trusted physical-command admission through the closed process registry. */
public final class FrontierWorldCommandPlanner {
    private FrontierWorldCommandPlanner() { }

    public static CommandPlan plan(FrontierWorldState state, FrontierCommand command,
                                   DeterministicProcessRegistry processRegistry, SubjectId trustedPhysicalExecutor) {
        if (!trustedPhysicalExecutor.equals(command.actor())) {
            return rejected("command is not from the trusted physical executor");
        }
        final String processId;
        try { processId = processRegistry.requireCommandOwner(command.payload().type()); }
        catch (IllegalArgumentException invalid) { return rejected(invalid.getMessage()); }
        CommandPlan plan = FrontierWorldProcessCatalog.planCommand(processId, state, command);
        if (plan instanceof CommandPlan.Accepted accepted) {
            return new CommandPlan.Accepted(processRegistry.validateEmissions(processId, accepted.events()));
        }
        return plan;
    }

    static CommandPlan.Rejected rejected(String message) {
        return new CommandPlan.Rejected(new CommandRejection(RejectionCode.REJECTED_BY_POLICY, message));
    }
}
