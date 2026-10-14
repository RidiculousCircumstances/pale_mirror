package io.farfrontier.palemirror.frontier.v3.kernel;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/** Owner-issued read proof that this exact action remains held. The predicate
 * must cover every canonical/time/external dependency of the refusal. Missing
 * proof means reevaluate, never infer a reusable hold from wake addresses. */
public record ScheduledHold<S>(ScheduledAction action, Set<SubjectId> wakeKeys, Predicate<S> stillHeld) {
    public ScheduledHold {
        Objects.requireNonNull(action, "held action");
        wakeKeys = Set.copyOf(Objects.requireNonNull(wakeKeys, "hold wake keys"));
        Objects.requireNonNull(stillHeld, "hold validity");
    }
}
