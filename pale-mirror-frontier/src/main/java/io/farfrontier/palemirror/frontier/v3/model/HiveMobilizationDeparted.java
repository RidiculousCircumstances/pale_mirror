package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** One atomic custody transfer from a complete cocoon assembly to its exact operation. */
public record HiveMobilizationDeparted(SubjectId mobilizationId, SettlementAssault assault) implements FrontierPayload {
    public HiveMobilizationDeparted {
        Objects.requireNonNull(mobilizationId, "hive mobilization id");
        Objects.requireNonNull(assault, "departed settlement assault");
    }
    @Override public String type() { return "frontier.hive_mobilization_departed"; }
}
