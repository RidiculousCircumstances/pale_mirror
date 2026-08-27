package io.farfrontier.palemirror.frontier.v3.kernel;

import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;

/** Pure domain policy for command validation and deterministic fact proposal. */
@FunctionalInterface
public interface CommandPlanner<S> {
    CommandPlan plan(S state, FrontierCommand command);
}
