package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.*;

import java.util.List;

/** Exact owner for ambient presentation leases, never physical body observations. */
final class FrontierAmbientProcessModule implements FrontierWorldProcessModule {
    @Override public List<io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction> retiredSchedules(
            FrontierWorldState previous, FrontierWorldState next,
            java.util.function.Supplier<List<io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction>> pending) {
        return ResidentLifeScheduleRetirement.afterDeath(previous, next, pending);
    }
    @Override public List<PhysicalIntentLifecycleCapability> physicalIntentLifecycleCapabilities() {
        return List.of(new NoPhysicalIntentLifecyclePolicy(
                io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner.AMBIENT_ACTOR_CUSTODY));
    }
    @Override public CommandPlan planCommand(FrontierWorldState state, FrontierCommand command) {
        if (command.payload() instanceof AmbientBodyConfirmed || command.payload() instanceof AmbientLeasePrepared || command.payload() instanceof AmbientLeaseTransition
                || command.payload() instanceof AmbientLeaseReleased || command.payload() instanceof AmbientLeaseRestartAbsenceObserved)
            return AmbientActorProcess.planLease(state, command.payload(), command.submittedAt().ticks());
        return FrontierWorldCommandPlanner.rejected("ambient process does not admit command: " + command.payload().type());
    }

    @Override public FrontierWorldState reduce(FrontierWorldState state, FrontierEvent event) {
        if (event.payload() instanceof AmbientBodyConfirmed || event.payload() instanceof AmbientLeasePrepared || event.payload() instanceof AmbientLeaseTransition
                || event.payload() instanceof AmbientLeaseReleased || event.payload() instanceof AmbientLeaseRestartAbsenceObserved) {
            return AmbientActorProcess.reduceLease(state, event.subject(), event.instant(), event.payload());
        }
        throw new IllegalArgumentException("ambient process does not own event: " + event.payload().type());
    }
}
