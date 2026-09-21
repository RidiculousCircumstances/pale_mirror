package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;

import java.util.Objects;

/** Durable admission of one pending resident birth and its exact semantic food commitment. */
public record ResidentBirthStarted(ResidentBirthJob job) implements FrontierPayload {
    public ResidentBirthStarted { Objects.requireNonNull(job, "resident birth job"); }
    @Override public String type() { return "frontier.resident_birth_started"; }
}
