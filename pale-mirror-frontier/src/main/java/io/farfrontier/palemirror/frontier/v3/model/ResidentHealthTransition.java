package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** One durable disease-state transition for one exact resident. */
record ResidentHealthTransition(SubjectId residentId, ResidentHealthStatus status, long atTick) implements FrontierPayload {
    ResidentHealthTransition {
        Objects.requireNonNull(residentId, "resident health resident");
        Objects.requireNonNull(status, "resident health status");
    }

    @Override public String type() { return "frontier.resident_health_transition"; }
}
