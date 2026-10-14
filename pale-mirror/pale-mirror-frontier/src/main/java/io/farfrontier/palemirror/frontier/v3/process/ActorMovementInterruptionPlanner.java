package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

/** Movement owner separates mandatory access clearance from the optional remainder of a journey. */
final class ActorMovementInterruptionPlanner implements ActivityInterruptionPlanner {
    @Override public boolean mayAttempt(FrontierWorldState state, SubjectId residentId, long atTick) {
        ActorMovement movement = state.actorMovements().get(residentId);
        if (movement == null) return true;
        var body = checkpoint(state, movement, atTick);
        if (body.isEmpty()) return false;
        var retained = new ActorMovementInterrupted(residentId, movement.order().goalRevision(), atTick,
                body.orElseThrow(), movement.executionId());
        // A work owner may quite correctly refuse release while this route still exists.
        // That refusal cannot prevent asking the movement owner to prove its interruption;
        // actual work/meal admission is checked again against the resulting state.
        if (ActorMovementProviders.require(movement).permitsReplacement(ResidentActivityChoice.Kind.EAT)
                && ResidentActivityCoordinator.requestsFoodAtMovementCheckpoint(state, retained)) return true;
        var choice = ResidentActivityCoordinator.assessMovementInterruptionEligibility(state, retained);
        // A retained assignment is not an executable successor. Resuming an
        // owner still waiting for resources would issue the same clearance
        // again, reset its travel and generate interruption/resume WAL forever.
        // The registered work owner supplies feasibility; navigation knows no
        // production phase, recipe, field or resource kind.
        if (choice.kind() == ResidentActivityChoice.Kind.WORK
                && !ActivityExecutionCapabilities.mayResumeWork(state,
                    HumanAssignmentProjection.compile(state).assignment(residentId))) return false;
        return (choice.kind() == ResidentActivityChoice.Kind.WORK || choice.kind() == ResidentActivityChoice.Kind.EAT)
                && ActorMovementProviders.require(movement).permitsReplacement(choice.kind());
    }

    @Override public Assessment assess(FrontierWorldState state, SubjectId residentId, long atTick) {
        return assess(state, residentId, atTick, true);
    }

    /** The exact activity owner is already planning its successor and owns its continuation.
     * Interrupt movement, but do not independently wake that same owner a second time. */
    Assessment assessForOwnerTransition(FrontierWorldState state,
            io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId execution, long atTick) {
        state.actorExecutions().requireCurrent(execution);
        var movement = state.actorMovements().get(execution.actorId());
        if (movement != null && !movement.executionId().equals(execution))
            throw new IllegalArgumentException("owner transition cannot interrupt another execution's movement");
        return assess(state, execution.actorId(), atTick, false);
    }

    private Assessment assess(FrontierWorldState state, SubjectId residentId, long atTick, boolean notifyOwner) {
        ActorMovement movement = state.actorMovements().get(residentId);
        if (movement == null) return new Ready(residentId, state, state, List.of());
        Optional<BodyPosition> checkpoint = checkpoint(state, movement, atTick);
        if (checkpoint.isEmpty()) return new Waiting(ActorExecutionCoordinator.sceneOwns(state, residentId)
                ? Reason.AUTHORITY_HANDOFF : Reason.SERVICE_CLEARANCE);
        ActorMovementInterrupted event = new ActorMovementInterrupted(residentId,
                movement.order().goalRevision(), atTick, checkpoint.orElseThrow(), movement.executionId());
        var events = new java.util.ArrayList<ProposedEvent>();
        events.add(new ProposedEvent(residentId, event));
        events.add(new ProposedEvent(residentId, new ScheduleEffect.Cancelled(ActorMovementProcess.progress(
                movement, Math.addExact(movement.issuedAtTick(), 1L)).id())));
        if (notifyOwner) events.addAll(ActorMovementProviders.require(movement).interrupted(state, movement, atTick));
        return new Ready(residentId, state, reduce(state, residentId, event), List.copyOf(events));
    }

    private static Optional<BodyPosition> checkpoint(FrontierWorldState state, ActorMovement movement, long atTick) {
        SubjectId actorId = movement.order().actorId();
        if (atTick < movement.issuedAtTick() || state.humanPopulation().meals().containsKey(actorId)
                || ActorExecutionCoordinator.sceneOwns(state, actorId)) return Optional.empty();
        // Admission may inspect an older parked activity wake after this route was committed.
        // That historical instant cannot authorize interrupting the newer travel checkpoint.
        if (movement.coldTravel().filter(travel -> atTick < travel.departedAtTick()).isPresent())
            return Optional.empty();
        AmbientActorLease lease = state.ambientLeases().get(actorId);
        if (lease != null && lease.status() != AmbientLeaseStatus.CLOSED
                && (lease.status() != AmbientLeaseStatus.HOT || lease.goal() != AmbientGoalKind.ACTOR_MOVEMENT
                    || !lease.goalBody().supportingSurface().equals(movement.order().legalStations().getFirst())))
            return Optional.empty();
        return ActorMovementProviders.require(movement).interruptionCheckpoint(state, movement, atTick);
    }

    static FrontierWorldState reduce(FrontierWorldState state, SubjectId subject, ActorMovementInterrupted event) {
        ActorMovement movement = state.actorMovements().get(subject);
        if (!subject.equals(event.actorId()) || movement == null
                || !movement.executionId().equals(event.executionId())
                || movement.order().goalRevision() != event.goalRevision()
                || !checkpoint(state, movement, event.atTick()).equals(Optional.of(event.retainedBody())))
            throw new IllegalArgumentException("interruption has no exact safe movement checkpoint");
        state.actorExecutions().requireCurrent(movement.executionId());
        var movements = new LinkedHashMap<>(state.actorMovements());
        movements.remove(subject);
        var actors = new LinkedHashMap<>(state.actorLocations());
        actors.put(subject, actors.get(subject).withBody(event.retainedBody()));
        return state.withChanges(FrontierWorldStateUpdate.begin().actorMovements(movements).actorLocations(actors)
                .actorExecutions(ActorMovementProviders.require(movement).interruptionAuthority(state, movement)));
    }
}
