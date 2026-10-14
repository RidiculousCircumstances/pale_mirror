package io.farfrontier.palemirror.frontier.v3.model;

/** Family-supplied Strategy; selection uses the assignment's explicit declared kind. */
public interface ActivityExecutionCapability {
    HumanAssignmentKind kind();
    ActivityExecutionCheckpoint checkpoint(FrontierWorldState state, HumanAssignment assignment);
    /** Owner-declared executable successor, distinct from a safe interruption checkpoint. */
    boolean mayResumeWork(FrontierWorldState state, HumanAssignment assignment);
    /** Advisory causal addresses; absence never bypasses conservative admission. */
    default java.util.Set<io.farfrontier.palemirror.frontier.v3.api.SubjectId> resumeWakeKeys(
            FrontierWorldState state, HumanAssignment assignment) { return java.util.Set.of(); }
    /** Mission owners can prohibit an independent home-food detour while retaining local self-care. */
    default boolean permitsHomeFood(FrontierWorldState state, HumanAssignment assignment) { return true; }
    /** Owner says whether the wait belongs to the currently occupied temporary service point. */
    default boolean waitingForServiceResource(FrontierWorldState state, HumanAssignment assignment) { return false; }
    default FrontierWorldState pauseLabour(FrontierWorldState state, HumanAssignment assignment, long tick) {
        throw new IllegalStateException("owner has no declared labour suspension strategy");
    }
    default java.util.List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> workStatsChanged(
            FrontierWorldState state, HumanAssignment assignment, long tick) {
        throw new IllegalStateException("owner has no declared work-stat wake strategy");
    }
}
