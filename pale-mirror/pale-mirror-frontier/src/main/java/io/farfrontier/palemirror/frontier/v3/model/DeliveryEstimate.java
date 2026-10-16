package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Objects;
import java.util.Optional;

/** Relative simulation time from retained logistics facts; advice, never an arrival receipt. */
public record DeliveryEstimate(Reason reason, Optional<Timing> timing) {
    public enum Reason { ROUTED, NOT_DISPATCHED, LOADING, ROUTE_UNKNOWN, MOVEMENT_PENDING,
        GROUP_HELD, PHYSICAL_PROGRESS_REQUIRED, RECEIVER_UNAVAILABLE, UNLOADING, AWAITING_ACCEPTANCE, TERMINAL }
    public record Timing(long travelTicks, long handlingTicks, long marginTicks) {
        public Timing {
            if (travelTicks < 0 || handlingTicks < 0 || marginTicks < 0)
                throw new IllegalArgumentException("negative delivery estimate");
            Math.addExact(Math.addExact(travelTicks, handlingTicks), marginTicks);
        }
        public long planningTicks() { return Math.addExact(Math.addExact(travelTicks, handlingTicks), marginTicks); }
    }
    public DeliveryEstimate {
        Objects.requireNonNull(reason); timing = Objects.requireNonNull(timing);
        if ((reason == Reason.ROUTED) != timing.isPresent())
            throw new IllegalArgumentException("only a routed delivery has a timing estimate");
    }
    public static DeliveryEstimate unknown(Reason reason) { return new DeliveryEstimate(reason, Optional.empty()); }
}
