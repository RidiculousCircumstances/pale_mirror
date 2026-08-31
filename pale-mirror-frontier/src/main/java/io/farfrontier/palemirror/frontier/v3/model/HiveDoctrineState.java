package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Objects;

/** Durable selected posture and its exact decision instant. */
public record HiveDoctrineState(HiveDoctrine doctrine, long selectedAt) {
    public HiveDoctrineState {
        Objects.requireNonNull(doctrine, "hive doctrine");
        if (selectedAt < 0L) throw new IllegalArgumentException("hive doctrine selection tick must be non-negative");
    }
    public static HiveDoctrineState initial() { return new HiveDoctrineState(HiveDoctrine.CONSOLIDATE, 0L); }
}
