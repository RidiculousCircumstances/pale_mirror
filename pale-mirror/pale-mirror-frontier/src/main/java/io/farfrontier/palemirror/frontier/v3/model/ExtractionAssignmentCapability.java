package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.util.List;

/** Extraction-owned assignment adapter; the life coordinator sees only a generic checkpoint. */
final class ExtractionAssignmentCapability implements ActivityExecutionCapability {
    @Override public HumanAssignmentKind kind() { return HumanAssignmentKind.EXTRACTION; }
    @Override public boolean mayResumeWork(FrontierWorldState state, HumanAssignment assignment) {
        return checkpoint(state, assignment).status() == ResidentWorkYield.Status.READY;
    }
    @Override public ActivityExecutionCheckpoint checkpoint(FrontierWorldState state, HumanAssignment assignment) {
        var job = state.extractionSites().work().get(assignment.ownerId().orElseThrow());
        if (job == null || !job.execution().actorId().equals(assignment.residentId()))
            throw new IllegalArgumentException("mining assignment lost its exact work");
        return ActivityExecutionCapabilities.retainedCheckpoint(state, assignment, job.execution());
    }
    @Override public FrontierWorldState pauseLabour(FrontierWorldState state, HumanAssignment assignment, long tick) {
        checkpoint(state, assignment);
        return ExtractionWorkAuthority.pauseLabour(state, state.extractionSites().work().get(assignment.ownerId().orElseThrow()), tick);
    }
    @Override public List<ProposedEvent> workStatsChanged(FrontierWorldState state, HumanAssignment assignment, long tick) {
        checkpoint(state, assignment); return List.of(ExtractionContinuation.wake(assignment.ownerId().orElseThrow(), tick));
    }
}
