package io.farfrontier.palemirror.frontier.v3.process;

/** First activity-lifecycle composition: existing work capabilities plus the movement owner. */
final class ResidentActivityExecutionComposition {
    static final ActivityInterruptionPlanner INTERRUPTION = new ActorMovementInterruptionPlanner();
    private ResidentActivityExecutionComposition() { }
}
