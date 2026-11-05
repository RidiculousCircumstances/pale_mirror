package io.farfrontier.palemirror.frontier.v3.model.buildings;

import java.util.Objects;

/** Retained exact selection. Changing installed content never silently changes a saved building. */
public record BuildingPlanRef(BuildingStyleId style, BuildingTemplateId template, String sha256) {
    public BuildingPlanRef {
        Objects.requireNonNull(style);
        Objects.requireNonNull(template);
        if (sha256 == null || !sha256.matches("[a-f0-9]{64}"))
            throw new IllegalArgumentException("building selection requires exact content hash");
    }
}
