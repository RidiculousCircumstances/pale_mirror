package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledActionPlanner;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import java.util.Set;

/** Registered owner port; common kernel admission knows no concrete family. */
@FunctionalInterface
interface FrontierScheduledPlanner extends ScheduledActionPlanner<FrontierWorldState> {
    default boolean acceptsReconsideration() { return false; }
    default Set<SubjectId> wakeDependencies(FrontierWorldState state, ScheduledAction action) { return Set.of(); }
    default java.util.Optional<java.util.function.Supplier<java.util.List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent>>>
            recoveryPlan(FrontierWorldState state, ScheduledAction action, SimInstant instant) { return java.util.Optional.empty(); }
}
