package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;

import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Bounded fair selection for one physical-intent family.
 *
 * <p>A naturally unavailable endpoint is a deferral, not a queue lock.  An
 * invalid canonical request remains actionable so its owning executor can
 * fail it closed rather than silently retaining it forever.  The caller owns
 * the bounded candidate family and all mutation; this helper only makes the
 * deterministic selection.</p>
 */
final class FrontierV3PhysicalIntentScheduling {
    enum Readiness { INVALID, DEFERRED, RUNNABLE }

    private FrontierV3PhysicalIntentScheduling() { }

    static Optional<PhysicalIntent> firstActionable(Collection<PhysicalIntent> candidates,
                                                     Function<PhysicalIntent, Readiness> readiness) {
        Objects.requireNonNull(candidates, "physical intent candidates");
        Objects.requireNonNull(readiness, "physical intent readiness");
        return candidates.stream().sorted(Comparator.comparing(PhysicalIntent::id))
                .filter(intent -> readiness.apply(intent) != Readiness.DEFERRED)
                .findFirst();
    }
}
