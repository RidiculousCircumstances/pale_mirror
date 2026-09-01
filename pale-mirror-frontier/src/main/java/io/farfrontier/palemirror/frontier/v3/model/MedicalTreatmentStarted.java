package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;

import java.util.Objects;

/** Durable admission of one exact local treatment before its physical supply is consumed. */
public record MedicalTreatmentStarted(MedicalEvacuationOperation operation) implements FrontierPayload {
    public MedicalTreatmentStarted { Objects.requireNonNull(operation, "medical treatment operation"); }
    @Override public String type() { return "frontier.medical_treatment_started"; }
}
