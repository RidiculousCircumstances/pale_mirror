package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Releases one birth permit whose physical food effect cannot be proven after recovery. */
public record ResidentBirthCancelled(SubjectId jobId) implements FrontierPayload {
    public ResidentBirthCancelled { Objects.requireNonNull(jobId, "resident birth job id"); }
    @Override public String type() { return "frontier.resident_birth_cancelled"; }
}
