package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import java.util.List;

/** Forest-owned adapter supplies safe work facts to the common resident activity selector. */
final class ForestryAssignmentCapability implements ActivityExecutionCapability {
    @Override public HumanAssignmentKind kind() { return HumanAssignmentKind.FORESTRY; }
    @Override public ActivityExecutionCheckpoint checkpoint(FrontierWorldState state, HumanAssignment assignment) {
        var job = state.forest().work().get(assignment.ownerId().orElseThrow());
        if (job == null || !job.execution().actorId().equals(assignment.residentId()))
            throw new IllegalArgumentException("forestry assignment lost its exact work");
        return ActivityExecutionCapabilities.retainedCheckpoint(state, assignment, job.execution());
    }
    @Override public boolean mayResumeWork(FrontierWorldState state, HumanAssignment assignment) {
        return checkpoint(state, assignment).status() == ResidentWorkYield.Status.READY;
    }
    @Override public FrontierWorldState pauseLabour(FrontierWorldState state, HumanAssignment assignment, long tick) {
        checkpoint(state, assignment); return ForestryWorkAuthority.pauseLabour(state, state.forest().work().get(assignment.ownerId().orElseThrow()), tick);
    }
    @Override public List<ProposedEvent> workStatsChanged(FrontierWorldState state, HumanAssignment assignment, long tick) {
        checkpoint(state, assignment); return List.of(ForestryContinuation.wake(assignment.ownerId().orElseThrow(), tick));
    }
}
