package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Durable visible reason that a named hive could not begin its next exact growth job. */
public record HiveGrowthBlocked(SubjectId hiveId, SubjectId nestId, SubjectId workId, HiveGrowthBlockReason reason,
                                DiagnosticTuple diagnostic) implements FrontierPayload {
    public HiveGrowthBlocked {
        Objects.requireNonNull(hiveId, "hive id"); Objects.requireNonNull(nestId, "nest id");
        Objects.requireNonNull(workId, "growth work id"); Objects.requireNonNull(reason, "growth block reason"); diagnostic = Objects.requireNonNull(diagnostic, "growth block diagnostic");
        if (diagnostic.reason() != DiagnosticReason.HIVE_GROWTH_BLOCKED || !diagnostic.owner().id().equals(workId)
                || !diagnostic.subject().id().equals(nestId)) throw new IllegalArgumentException("growth block has a foreign diagnostic tuple");
    }
    @Override public String type() { return "frontier.hive_growth_blocked"; }
}
