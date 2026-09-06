package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;
import java.util.Optional;

/** Exact conflict outcome for a physical cocoon release; it never authorizes a replacement body. */
public record HiveMobilizationConflicted(SubjectId mobilizationId, HiveMobilizationConflictReason reason,
                                         Optional<HiveAssemblyBlockage> assemblyBlockage) implements FrontierPayload {
    public HiveMobilizationConflicted {
        Objects.requireNonNull(mobilizationId, "hive mobilization id");
        Objects.requireNonNull(reason, "hive mobilization conflict reason");
        assemblyBlockage = Objects.requireNonNull(assemblyBlockage, "hive assembly blockage");
        if ((reason == HiveMobilizationConflictReason.ASSEMBLY_PATH_BLOCKED) != assemblyBlockage.isPresent()) {
            throw new IllegalArgumentException("only an assembly path conflict retains its exact blocked edge");
        }
    }
    public HiveMobilizationConflicted(SubjectId mobilizationId, HiveMobilizationConflictReason reason) {
        this(mobilizationId, reason, Optional.empty());
    }
    @Override public String type() { return "frontier.hive_mobilization_conflicted"; }
}
