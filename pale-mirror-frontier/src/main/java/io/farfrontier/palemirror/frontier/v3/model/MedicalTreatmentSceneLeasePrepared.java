package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;

import java.util.Objects;

/** Durable-before-effect admission of one exact patient and retained medical team. */
public record MedicalTreatmentSceneLeasePrepared(SceneLease lease) implements FrontierPayload, SceneLeaseAdmission {
    public MedicalTreatmentSceneLeasePrepared {
        Objects.requireNonNull(lease, "medical treatment scene lease");
        if (!FrontierSceneBehaviors.isMedicalTreatment(lease)) {
            throw new IllegalArgumentException("medical scene preparation requires a medical treatment cause");
        }
    }

    @Override public String type() { return "frontier.medical_treatment_scene_lease_prepared"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
