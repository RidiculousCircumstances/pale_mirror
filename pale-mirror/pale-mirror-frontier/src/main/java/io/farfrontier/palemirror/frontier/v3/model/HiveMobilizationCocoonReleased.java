package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Loaded-world confirmation that one exact owned cocoon became air through its mobilization. */
public record HiveMobilizationCocoonReleased(SubjectId mobilizationId, SubjectId bioformId) implements FrontierPayload {
    public HiveMobilizationCocoonReleased {
        Objects.requireNonNull(mobilizationId, "hive mobilization id");
        Objects.requireNonNull(bioformId, "hive mobilization bioform");
    }
    @Override public String type() { return "frontier.hive_mobilization_cocoon_released"; }
}
