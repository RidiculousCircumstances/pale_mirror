package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind;
import io.farfrontier.palemirror.frontier.v3.model.group.UnitGroup;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import java.util.List;

/** A roster reservation is not unfinished labour: vacant members may eat while waiting. */
final class GroupAssignmentCapability implements ActivityExecutionCapability {
    @Override public HumanAssignmentKind kind() { return HumanAssignmentKind.GROUP_MEMBER; }
    @Override public ActivityExecutionCheckpoint checkpoint(FrontierWorldState state, HumanAssignment assignment) {
        var group = state.unitGroups().groups().get(assignment.ownerId().orElseThrow());
        if (assignment.kind() != kind() || group == null || group.phase() == UnitGroup.Phase.CLOSED)
            throw new IllegalArgumentException("group checkpoint lacks its exact active roster");
        group.member(assignment.residentId());
        var retained = state.actorExecutions().actors().get(assignment.residentId());
        if (retained == null || retained.current().isEmpty())
            return new ActivityExecutionCheckpoint(state, assignment, ResidentWorkYield.Status.READY);
        var execution = retained.current().orElseThrow();
        if (execution.activityKind() == ActorActivityKind.GROUP_MEMBER && execution.activityOwnerId().equals(group.id())) {
            var owner = ActorExecutionComposition.CAPABILITIES.require(execution.activityKind());
            owner.checkpoint(state, execution).validate(state, execution);
            return new ActivityExecutionCheckpoint(state, assignment, ResidentWorkYield.Status.READY);
        }
        var owner = ActorExecutionComposition.CAPABILITIES.require(execution.activityKind());
        var safeVacancy = retained.suspended().isEmpty() && owner.interruption()
                == io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityCapability.Interruption.RELEASE
                && owner.checkpoint(state, execution).ready();
        return new ActivityExecutionCheckpoint(state, assignment, safeVacancy ? ResidentWorkYield.Status.READY : ResidentWorkYield.Status.OWNER_NOT_CURRENT);
    }
    @Override public FrontierWorldState pauseLabour(FrontierWorldState state, HumanAssignment assignment, long tick) {
        checkpoint(state, assignment); return state;
    }
    @Override public List<ProposedEvent> workStatsChanged(FrontierWorldState state, HumanAssignment assignment, long tick) {
        checkpoint(state, assignment); return List.of(UnitGroupContinuation.wake(assignment.ownerId().orElseThrow(), tick));
    }
}
