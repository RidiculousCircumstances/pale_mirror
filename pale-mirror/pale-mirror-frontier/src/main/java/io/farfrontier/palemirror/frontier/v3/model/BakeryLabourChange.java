package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Objects;
import java.util.Optional;

/** Retained predecessor and successor of labour, never permission to transform station stock. */
public record BakeryLabourChange(Optional<WorkProgress> previous, Optional<ProductionStationClock.Attendance> previousAttendance,
                                 WorkProgress next, Optional<ProductionStationClock.Attendance> attendance) {
    public BakeryLabourChange {
        Objects.requireNonNull(previous); Objects.requireNonNull(next); Objects.requireNonNull(previousAttendance); Objects.requireNonNull(attendance);
        if (previous.equals(Optional.of(next)) && previousAttendance.equals(attendance)) throw new IllegalArgumentException("unchanged bakery labour");
    }
    public long atTick() { return next.evaluatedAtTick(); }
}
