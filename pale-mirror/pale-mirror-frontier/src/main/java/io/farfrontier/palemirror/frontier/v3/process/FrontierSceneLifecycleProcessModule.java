package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.*;

import java.util.List;
import java.util.Set;

/** Shared lifecycle owner for assault and engineering presentation scopes. */
final class FrontierSceneLifecycleProcessModule implements FrontierWorldProcessModule {
    @Override public List<io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction> retiredSchedules(
            FrontierWorldState previous, FrontierWorldState next,
            java.util.function.Supplier<List<io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction>> pending) {
        return ResidentLifeScheduleRetirement.afterDeath(previous, next, pending);
    }
    @Override public CommandPlan planCommand(FrontierWorldState state, FrontierCommand command) {
        if (command.payload() instanceof SettlementAssaultSceneLeasePrepared prepared) {
            try { return new CommandPlan.Accepted(List.of(new ProposedEvent(FrontierSettlementAssaultSceneSupport.owner(state, prepared.lease()), prepared))); }
            catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
        }
        if (command.payload() instanceof EngineeringWorkSceneLeasePrepared prepared) {
            try { return new CommandPlan.Accepted(List.of(new ProposedEvent(FrontierEngineeringWorkSceneSupport.owner(state,
                    FrontierSceneBehaviors.engineeringWorksite(prepared.lease())), prepared))); }
            catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
        }
        if (command.payload() instanceof SettlementAssaultSceneLeaseHandoff handoff) {
            try { return new CommandPlan.Accepted(List.of(new ProposedEvent(FrontierSettlementAssaultSceneSupport.owner(state, handoff.lease()), handoff))); }
            catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
        }
        if (command.payload() instanceof EngineeringWorkSceneLeaseHandoff handoff) {
            try { return new CommandPlan.Accepted(List.of(new ProposedEvent(FrontierEngineeringWorkSceneSupport.owner(state,
                    FrontierSceneBehaviors.engineeringWorksite(handoff.lease())), handoff))); }
            catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
        }
        if (command.payload() instanceof SceneLeaseTransition transition) {
            SceneLease lease = state.sceneLeases().get(transition.leaseId());
            if (lease == null) return FrontierWorldCommandPlanner.rejected("scene lease is unknown");
            if (!transition.appliesTo(lease)) return FrontierWorldCommandPlanner.rejected("scene lease transition is not allowed from its current status");
            try {
                // Physical participants must already be confirmed by the body owner.
                // Reject an invalid transition before WAL admission, never in its reducer.
                state.transitionSceneLease(transition.leaseId(), transition.status());
                return new CommandPlan.Accepted(List.of(new ProposedEvent(FrontierSceneOwnerSupport.owner(state, lease), transition)));
            }
            catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
        }
        if (command.payload() instanceof SceneLeaseReleased released) return planSceneReleased(state, command, released);
        if (command.payload() instanceof SceneLeaseRecoveryUnresolved unresolved) return planRecoveryUnresolved(state, command, unresolved);
        if (command.payload() instanceof SceneLeaseRecoveryRevoked)
            return FrontierWorldCommandPlanner.rejected("scene recovery revoke has no saved body/health proof");
        return FrontierWorldCommandPlanner.rejected("scene lifecycle process does not admit command: " + command.payload().type());
    }

    @Override public FrontierWorldState reduce(FrontierWorldState state, FrontierEvent event) {
        return switch (event.payload()) {
            case SettlementAssaultSceneLeasePrepared prepared -> reduceAssaultPrepared(state, event.subject(), event, prepared);
            case SettlementAssaultSceneLeaseHandoff handoff -> reduceAssaultHandoff(state, event.subject(), event, handoff);
            case EngineeringWorkSceneLeasePrepared prepared -> reduceEngineeringPrepared(state, event.subject(), event, prepared);
            case EngineeringWorkSceneLeaseHandoff handoff -> reduceEngineeringHandoff(state, event.subject(), event, handoff);
            case SceneLeaseTransition transition -> reduceSceneTransition(state, event.subject(), transition, event.instant().ticks());
            case SceneLeaseReleased released -> reduceSceneReleased(state, event.subject(), released);
            case SceneLeaseRecoveryUnresolved unresolved -> reduceRecoveryUnresolved(state, event.subject(), unresolved);
            case SceneLeaseRecoveryRevoked revoked -> reduceRecoveryRevoked(state, event.subject(), revoked);
            default -> throw new IllegalArgumentException("scene lifecycle process does not own event: " + event.payload().type());
        };
    }

    private static CommandPlan planSceneReleased(FrontierWorldState state, FrontierCommand command, SceneLeaseReleased released) {
        SceneLease lease = state.sceneLeases().get(released.leaseId());
        if (lease == null) return FrontierWorldCommandPlanner.rejected("scene lease is unknown");
        try { return new CommandPlan.Accepted(FrontierSceneContinuationPlanner.releaseEvents(state, lease, command.submittedAt().ticks(), released,
                command.scheduleBinding().map(io.farfrontier.palemirror.frontier.v3.api.EngineScheduleBinding::action))); }
        catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
    }

    private static CommandPlan planRecoveryUnresolved(FrontierWorldState state, FrontierCommand command, SceneLeaseRecoveryUnresolved unresolved) {
        SceneLease lease = state.sceneLeases().get(unresolved.leaseId());
        if (lease == null || lease.status() != SceneLeaseStatus.UNKNOWN_AFTER_RESTART || lease.recoveryEvidence().isPresent()) {
            return FrontierWorldCommandPlanner.rejected("scene recovery evidence does not bind one unresolved restart lease");
        }
        try { return new CommandPlan.Accepted(FrontierSceneContinuationPlanner.recoveryUnresolvedEvents(state, lease, command.submittedAt().ticks(), unresolved)); }
        catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
    }


    private static FrontierWorldState reduceAssaultPrepared(FrontierWorldState state, SubjectId subject, FrontierEvent event, SettlementAssaultSceneLeasePrepared prepared) {
        SceneLease lease = prepared.lease();
        if (!subject.equals(FrontierSettlementAssaultSceneSupport.owner(state, lease)) || !lease.handoffInstant().equals(event.instant())) {
            throw new IllegalArgumentException("assault scene lease does not match its retained battle hand-off");
        }
        return state.prepareSceneLease(lease);
    }

    private static FrontierWorldState reduceAssaultHandoff(FrontierWorldState state, SubjectId subject, FrontierEvent event, SettlementAssaultSceneLeaseHandoff handoff) {
        SceneLease lease = handoff.lease();
        if (!subject.equals(FrontierSettlementAssaultSceneSupport.owner(state, lease)) || !lease.handoffInstant().equals(event.instant())) {
            throw new IllegalArgumentException("assault scene hand-off does not match its retained battle");
        }
        return state.handoffAmbientScene(new SceneLeaseHandoff(lease, handoff.ambientMembers()));
    }

    private static FrontierWorldState reduceEngineeringPrepared(FrontierWorldState state, SubjectId subject, FrontierEvent event,
                                                                 EngineeringWorkSceneLeasePrepared prepared) {
        SceneLease lease = prepared.lease();
        if (!subject.equals(FrontierEngineeringWorkSceneSupport.owner(state, FrontierSceneBehaviors.engineeringWorksite(lease)))
                || !lease.handoffInstant().equals(event.instant())) {
            throw new IllegalArgumentException("engineering scene lease does not match its retained work-site hand-off");
        }
        return state.prepareSceneLease(lease);
    }

    private static FrontierWorldState reduceEngineeringHandoff(FrontierWorldState state, SubjectId subject, FrontierEvent event,
                                                                EngineeringWorkSceneLeaseHandoff handoff) {
        SceneLease lease = handoff.lease();
        if (!subject.equals(FrontierEngineeringWorkSceneSupport.owner(state, FrontierSceneBehaviors.engineeringWorksite(lease)))
                || !lease.handoffInstant().equals(event.instant())) {
            throw new IllegalArgumentException("engineering scene hand-off does not match its retained work-site");
        }
        return state.handoffAmbientScene(new SceneLeaseHandoff(lease, handoff.ambientMembers()));
    }

    private static FrontierWorldState reduceSceneTransition(FrontierWorldState state, SubjectId subject,
                                                          SceneLeaseTransition transition, long tick) {
        SceneLease lease = state.sceneLeases().get(transition.leaseId());
        if (lease == null || !subject.equals(FrontierSceneOwnerSupport.owner(state, lease))) throw new IllegalArgumentException("scene lease transition lacks its owning scene");
        if (transition.status() != SceneLeaseStatus.HOT) {
            for (SceneMember member : lease.members()) {
                if (state.humanPopulation().resident(member.actorId()) != null)
                    state = ActivityExecutionCapabilities.pauseLabour(state,
                            HumanAssignmentProjection.compile(state).assignment(member.actorId()), tick);
            }
        }
        return state.transitionSceneLease(transition.leaseId(), transition.status());
    }

    private static FrontierWorldState reduceSceneReleased(FrontierWorldState state, SubjectId subject, SceneLeaseReleased released) {
        SceneLease lease = state.sceneLeases().get(released.leaseId());
        if (lease == null || !subject.equals(FrontierSceneOwnerSupport.owner(state, lease))) throw new IllegalArgumentException("scene release lacks its owning scene");
        return state.releaseSceneLease(released.leaseId(), released.members());
    }

    private static FrontierWorldState reduceRecoveryUnresolved(FrontierWorldState state, SubjectId subject, SceneLeaseRecoveryUnresolved unresolved) {
        SceneLease lease = state.sceneLeases().get(unresolved.leaseId());
        if (lease == null || !subject.equals(FrontierSceneOwnerSupport.owner(state, lease))) throw new IllegalArgumentException("scene recovery evidence lacks its owning scene");
        return FrontierSceneLeaseStateSupport.recoveryUnresolved(state, unresolved);
    }

    private static FrontierWorldState reduceRecoveryRevoked(FrontierWorldState state, SubjectId subject, SceneLeaseRecoveryRevoked revoked) {
        SceneLease lease = state.sceneLeases().get(revoked.leaseId());
        if (lease == null || !subject.equals(FrontierSceneOwnerSupport.owner(state, lease))) {
            throw new IllegalArgumentException("scene recovery revoke lacks its owning scene");
        }
        return FrontierSceneLeaseStateSupport.revokeUnknownPatrolToCold(state, revoked);
    }


}
