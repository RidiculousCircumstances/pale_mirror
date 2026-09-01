package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Terminal or running transition derived from the exact treatment supply receipt. */
public record MedicalTreatmentTransition(SubjectId operationId, MedicalEvacuationStatus status) implements FrontierPayload {
    public MedicalTreatmentTransition {
        Objects.requireNonNull(operationId, "medical treatment operation"); Objects.requireNonNull(status, "medical treatment status");
        if (status == MedicalEvacuationStatus.PREPARED) {
            throw new IllegalArgumentException("medical treatment transition must leave prepared care");
        }
    }
    @Override public String type() { return "frontier.medical_treatment_transition"; }
}
