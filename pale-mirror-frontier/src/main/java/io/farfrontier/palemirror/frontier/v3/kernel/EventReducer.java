package io.farfrontier.palemirror.frontier.v3.kernel;

import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;

/** Pure event application. Implementations return a new immutable state and never mutate input. */
@FunctionalInterface
public interface EventReducer<S> {
    S apply(S state, FrontierEvent event);
}
