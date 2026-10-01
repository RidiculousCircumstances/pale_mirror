package io.farfrontier.palemirror.frontier.v3.model;

/** Family-supplied Strategy; selection uses the assignment's explicit declared kind. */
public interface ActivityExecutionCapability {
    HumanAssignmentKind kind();
    ActivityExecutionCheckpoint checkpoint(FrontierWorldState state, HumanAssignment assignment);
}
