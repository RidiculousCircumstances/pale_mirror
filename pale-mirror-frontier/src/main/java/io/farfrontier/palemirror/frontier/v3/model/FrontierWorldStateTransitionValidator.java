package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.kernel.StateValidator;

/** Kernel-bound canonical validator: complete at ingress, dependency-aware before each WAL append. */
final class FrontierWorldStateTransitionValidator implements StateValidator<FrontierWorldState> {
    static final FrontierWorldStateTransitionValidator INSTANCE = new FrontierWorldStateTransitionValidator();

    private FrontierWorldStateTransitionValidator() { }

    @Override public void validateInitial(FrontierWorldState state) { state.validateComplete(); }

    @Override public void validateTransition(FrontierWorldState previous, FrontierWorldState next) {
        next.validateTransitionFrom(previous);
    }
}
