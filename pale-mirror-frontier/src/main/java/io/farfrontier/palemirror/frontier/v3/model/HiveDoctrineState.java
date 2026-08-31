package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Objects;

/** Durable selected posture and its exact decision instant. */
record HiveDoctrineState(HiveDoctrine doctrine, long selectedAt) {
    HiveDoctrineState {
        Objects.requireNonNull(doctrine, "hive doctrine");
        if (selectedAt < 0L) throw new IllegalArgumentException("hive doctrine selection tick must be non-negative");
    }
    static HiveDoctrineState initial() { return new HiveDoctrineState(HiveDoctrine.CONSOLIDATE, 0L); }
}
