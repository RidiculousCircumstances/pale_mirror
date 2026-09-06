package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** One exact medical operation temporarily executed as a naturally loaded infirmary scene. */
public record MedicalTreatmentSceneCause(SubjectId operationId) implements SceneCause {
    public MedicalTreatmentSceneCause {
        Objects.requireNonNull(operationId, "medical scene operation");
        if (!operationId.value().startsWith("medical:")) throw new IllegalArgumentException("medical scene requires a medical operation id");
    }

    @Override public SceneCauseKind kind() { return SceneCauseKind.MEDICAL_TREATMENT; }
}
