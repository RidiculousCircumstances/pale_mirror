package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldStateUpdate;
import io.farfrontier.palemirror.frontier.v3.model.ActorExecutionComposition;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionResumed;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorPresenceStarted;

/** Shared committed execution transitions; no family progress, body or food dispatch here. */
final class FrontierActorExecutionProcessModule implements FrontierWorldProcessModule {
    /** Native presentation may request passive authority, never synthesize an actuation lease. */
    @Override public io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan planCommand(
            FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.FrontierCommand command) {
        if (!(command.payload() instanceof ActorPresenceStarted presence))
            return FrontierWorldCommandPlanner.rejected("execution command is not passive presence");
        var actor=presence.execution().actorId();var lease=state.ambientLeases().get(actor);
        var retained=state.actorExecutions().actors().get(actor);
        if (presence.atTick()!=command.submittedAt().ticks() || retained!=null && retained.current().isPresent()
                || !presence.execution().equals(state.actorExecutions().next(actor,
                        io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.PRESENCE,actor))
                || lease==null || lease.status()!=io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseStatus.HOT
                || !io.farfrontier.palemirror.frontier.v3.model.ActorBodyAuthority.retainsPhysicalCustody(state,actor)
                || state.actorMovements().containsKey(actor)
                || io.farfrontier.palemirror.frontier.v3.model.ActorInventoryInteractionFences.pendingOwner(state,actor).isPresent())
            return FrontierWorldCommandPlanner.rejected("passive presence lacks exact vacant HOT authority");
        var capability=ActorExecutionComposition.CAPABILITIES.require(presence.execution().activityKind());
        try { capability.validateReference(state,presence.execution());
            if (!capability.permitsAmbientMotion(state,presence.execution(),lease))
                return FrontierWorldCommandPlanner.rejected("ambient purpose is not passive presence");
            ActorExecutionComposition.LIFECYCLE.prepareBegin(state,presence.execution(),presence.atTick());
        } catch (IllegalArgumentException denied) { return FrontierWorldCommandPlanner.rejected(denied.getMessage()); }
        return new io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan.Accepted(java.util.List.of(
                new io.farfrontier.palemirror.frontier.v3.api.ProposedEvent(actor,presence)));
    }
    @Override public FrontierWorldState reduce(FrontierWorldState state, FrontierEvent event) {
        if (event.payload() instanceof ActorPresenceStarted presence) {
            if (!event.subject().equals(presence.execution().actorId()))
                throw new IllegalArgumentException("presence event subject=" + event.subject() + " differs from actor=" + presence.execution().actorId());
            if (event.instant().ticks() != presence.atTick())
                throw new IllegalArgumentException("presence event tick=" + event.instant().ticks() + " differs from activity tick="
                        + presence.atTick() + " for " + presence.execution());
            var retained = state.actorExecutions().actors().get(event.subject());
            if (retained != null && retained.current().isPresent())
                throw new IllegalArgumentException("presence cannot replace an active execution");
            ActorExecutionComposition.CAPABILITIES.require(presence.execution().activityKind())
                    .validateReference(state, presence.execution());
            return ActorExecutionComposition.LIFECYCLE.prepareBegin(state, presence.execution(), presence.atTick())
                    .commit(state, FrontierWorldStateUpdate.begin());
        }
        if (!(event.payload() instanceof ActorExecutionResumed resumed)
                || !event.subject().equals(resumed.successor().actorId()) || event.instant().ticks() != resumed.atTick())
            throw new IllegalArgumentException("execution module does not own this exact transition");
        return ActorExecutionComposition.LIFECYCLE.prepareResume(state, resumed.suspended(),
                resumed.successor(), resumed.releasing(), resumed.atTick()).commit(state, FrontierWorldStateUpdate.begin());
    }
}
