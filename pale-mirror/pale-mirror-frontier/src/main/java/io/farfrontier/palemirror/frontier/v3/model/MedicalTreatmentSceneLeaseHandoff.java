package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;

import java.util.List;
import java.util.Objects;

/** Exact ambient-to-infirmary-scene transfer; it neither spawns nor moves a person. */
public record MedicalTreatmentSceneLeaseHandoff(SceneLease lease, List<SceneMemberPosition> ambientMembers) implements FrontierPayload, SceneLeaseAdmission {
    public MedicalTreatmentSceneLeaseHandoff {
        Objects.requireNonNull(lease, "medical treatment scene lease");
        ambientMembers = List.copyOf(Objects.requireNonNull(ambientMembers, "medical treatment ambient members"));
        if (!FrontierSceneBehaviors.isMedicalTreatment(lease)) {
            throw new IllegalArgumentException("medical scene hand-off requires a medical treatment cause");
        }
    }

    @Override public String type() { return "frontier.medical_treatment_scene_lease_handoff"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
