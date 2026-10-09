package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.util.List;

/** Extraction-owned assignment adapter; the life coordinator sees only a generic checkpoint. */
final class ExtractionAssignmentCapability implements ActivityExecutionCapability {
    @Override public HumanAssignmentKind kind() { return HumanAssignmentKind.EXTRACTION; }
    @Override public ActivityExecutionCheckpoint checkpoint(FrontierWorldState state, HumanAssignment assignment) {
        var job = state.extractionSites().work().get(assignment.ownerId().orElseThrow());
        if (job == null || !job.execution().actorId().equals(assignment.residentId()))
            throw new IllegalArgumentException("mining assignment lost its exact work");
        var retained = state.actorExecutions().actors().get(assignment.residentId());
        if (retained == null || !retained.current().equals(java.util.Optional.of(job.execution())))
            return new ActivityExecutionCheckpoint(state, assignment, ResidentWorkYield.Status.OWNER_NOT_CURRENT);
        var checkpoint = ActorExecutionComposition.CAPABILITIES.require(ActorActivityKind.EXTRACTION).checkpoint(state, job.execution());
        checkpoint.validate(state, job.execution());
        return new ActivityExecutionCheckpoint(state, assignment, checkpoint.waiting()
                .map(wait -> wait.reason() == ActorActivityCheckpoint.Reason.PHYSICAL_OPERATION
                        ? ResidentWorkYield.Status.PENDING_PHYSICAL_EFFECT : ResidentWorkYield.Status.OWNER_SAFETY_HOLD)
                .orElse(ResidentWorkYield.Status.READY));
    }
    @Override public FrontierWorldState pauseLabour(FrontierWorldState state, HumanAssignment assignment, long tick) {
        checkpoint(state, assignment);
        return ExtractionWorkAuthority.pauseLabour(state, state.extractionSites().work().get(assignment.ownerId().orElseThrow()), tick);
    }
    @Override public List<ProposedEvent> workStatsChanged(FrontierWorldState state, HumanAssignment assignment, long tick) {
        checkpoint(state, assignment); return List.of(ExtractionContinuation.wake(assignment.ownerId().orElseThrow(), tick));
    }
}
