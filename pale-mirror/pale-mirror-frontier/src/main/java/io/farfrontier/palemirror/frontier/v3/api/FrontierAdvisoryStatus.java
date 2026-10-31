package io.farfrontier.palemirror.frontier.v3.api;

import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import java.util.Optional;

/** Read-only technical admission evidence, never a schedule or execution authority. */
public record FrontierAdvisoryStatus(int inFlight, int ready, int capacityWaiters, boolean hostAvailable,
        Optional<ScheduledAction> reservation, Readiness reservationReadiness, Optional<ScheduledAction> capacityHead, Optional<SimInstant> capacityHeadSince) {
    public enum Readiness { NONE, READY, CHECKING, FUTURE, CALCULATION, HELD, ABSENT }
}
