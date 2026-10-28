package io.farfrontier.palemirror.frontier.v3.kernel;

import java.util.Optional;

/** Disposable host calculation. Poll/revalidation belong to the canonical owner. */
public interface ScheduledAdviceCalculation<S> {
    Optional<ScheduledAdvisory.Completion<S>> poll();
    /** Non-mutating technical readiness, explicitly supplied by every executor. */
    boolean ready();
    default void cancel() { }
}
