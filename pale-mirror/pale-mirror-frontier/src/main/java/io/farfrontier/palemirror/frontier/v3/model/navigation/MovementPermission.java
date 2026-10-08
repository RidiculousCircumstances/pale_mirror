package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;
import java.util.Optional;

/** One read-only decision consumed by both execution and explanation; never an arrival receipt. */
public record MovementPermission(Reason reason, Optional<SubjectId> waitingFor, Optional<TravelPace> pace,
                                 Optional<Spacing> spacing) {
    /** Read-only constraint evidence in route-distance units; no lifecycle or family state. */
    public record Spacing(double progressGap, double spatialGap, double stopLimit, double normalSpacing) {
        public Spacing {
            if (!Double.isFinite(progressGap) || !Double.isFinite(spatialGap) || !Double.isFinite(stopLimit)
                    || !Double.isFinite(normalSpacing) || spatialGap < 0 || stopLimit < 0 || normalSpacing <= 0)
                throw new IllegalArgumentException("invalid movement spacing evidence");
        }
    }
    public enum Reason { ALLOWED, INACTIVE_MEMBER, POSITION_UNAVAILABLE, GROUP_SPATIAL_STRETCH, GROUP_PROGRESS_STRETCH,
        MISSION_STOP_REQUESTED, REFILL_TRANSFER, FOOD_STOCK_REQUIRED }
    public MovementPermission {
        Objects.requireNonNull(reason); waitingFor = Objects.requireNonNull(waitingFor);
        pace = Objects.requireNonNull(pace);
        spacing = Objects.requireNonNull(spacing);
        if (reason != Reason.ALLOWED && pace.isPresent())
            throw new IllegalArgumentException("a held movement cannot request a walking pace");
    }
    public MovementPermission(Reason reason, Optional<SubjectId> waitingFor, Optional<TravelPace> pace) {
        this(reason, waitingFor, pace, Optional.empty());
    }
    public MovementPermission(Reason reason, Optional<SubjectId> waitingFor) {
        this(reason, waitingFor, Optional.empty());
    }
    public boolean allowed() { return reason == Reason.ALLOWED; }
    public MovementPermission withSpacing(Spacing evidence) {
        return new MovementPermission(reason, waitingFor, pace, Optional.of(evidence));
    }
    public static MovementPermission allow() { return new MovementPermission(Reason.ALLOWED, Optional.empty()); }
    public static MovementPermission allow(TravelPace pace) {
        return new MovementPermission(Reason.ALLOWED, Optional.empty(), Optional.of(pace));
    }
    public static MovementPermission hold(Reason reason) {
        if (reason == Reason.ALLOWED) throw new IllegalArgumentException("hold requires a reason");
        return new MovementPermission(reason, Optional.empty());
    }
    public static MovementPermission hold(Reason reason, SubjectId peer) {
        return new MovementPermission(reason, Optional.of(peer));
    }
}
