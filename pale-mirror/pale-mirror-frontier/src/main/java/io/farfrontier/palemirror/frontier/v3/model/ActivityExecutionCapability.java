package io.farfrontier.palemirror.frontier.v3.model;

/** Family-supplied Strategy; selection uses the assignment's explicit declared kind. */
public interface ActivityExecutionCapability {
    HumanAssignmentKind kind();
    ActivityExecutionCheckpoint checkpoint(FrontierWorldState state, HumanAssignment assignment);
    /** Owner says whether a resource wait may relinquish a temporary service position. */
    default boolean waitingForServiceResource(FrontierWorldState state, HumanAssignment assignment) { return false; }
    default FrontierWorldState pauseLabour(FrontierWorldState state, HumanAssignment assignment, long tick) {
        throw new IllegalStateException("owner has no declared labour suspension strategy");
    }
    default java.util.List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> workStatsChanged(
            FrontierWorldState state, HumanAssignment assignment, long tick) {
        throw new IllegalStateException("owner has no declared work-stat wake strategy");
    }
}
