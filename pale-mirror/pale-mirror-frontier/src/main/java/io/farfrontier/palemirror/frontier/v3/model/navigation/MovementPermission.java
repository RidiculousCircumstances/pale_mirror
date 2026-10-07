package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;
import java.util.Optional;

/** One read-only decision consumed by both execution and explanation; never an arrival receipt. */
public record MovementPermission(Reason reason, Optional<SubjectId> waitingFor) {
    public enum Reason { ALLOWED, INACTIVE_MEMBER, GROUP_SPATIAL_STRETCH, GROUP_PROGRESS_STRETCH,
        MISSION_STOP_REQUESTED, REFILL_TRANSFER, FOOD_STOCK_REQUIRED }
    public MovementPermission { Objects.requireNonNull(reason); waitingFor = Objects.requireNonNull(waitingFor); }
    public boolean allowed() { return reason == Reason.ALLOWED; }
    public static MovementPermission allow() { return new MovementPermission(Reason.ALLOWED, Optional.empty()); }
    public static MovementPermission hold(Reason reason) {
        if (reason == Reason.ALLOWED) throw new IllegalArgumentException("hold requires a reason");
        return new MovementPermission(reason, Optional.empty());
    }
    public static MovementPermission hold(Reason reason, SubjectId peer) {
        return new MovementPermission(reason, Optional.of(peer));
    }
}
