package io.farfrontier.palemirror.frontier.v3.kernel;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/** Ephemeral preparation. Poll and completion run on the engine owner; no partial events are published. */
public interface ScheduledCalculation {
    Optional<List<ProposedEvent>> poll();
    default void cancel() { }
    static ScheduledCalculation immediate(Supplier<List<ProposedEvent>> completion) {
        return () -> Optional.of(List.copyOf(completion.get()));
    }
}
