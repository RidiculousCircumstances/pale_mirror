package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;
import java.util.Optional;

/** Exact conflict outcome for a physical cocoon release; it never authorizes a replacement body. */
public record HiveMobilizationConflicted(SubjectId mobilizationId, HiveMobilizationConflictReason reason,
                                         Optional<HiveAssemblyBlockage> assemblyBlockage, DiagnosticTuple diagnostic) implements FrontierPayload {
    public HiveMobilizationConflicted {
        Objects.requireNonNull(mobilizationId, "hive mobilization id");
        Objects.requireNonNull(reason, "hive mobilization conflict reason");
        assemblyBlockage = Objects.requireNonNull(assemblyBlockage, "hive assembly blockage");
        diagnostic = Objects.requireNonNull(diagnostic, "hive mobilization diagnostic");
        if ((reason == HiveMobilizationConflictReason.ASSEMBLY_PATH_BLOCKED) != assemblyBlockage.isPresent()) {
            throw new IllegalArgumentException("only an assembly path conflict retains its exact blocked edge");
        }
        if (diagnostic.reason() != DiagnosticReason.HIVE_MOBILIZATION_CONFLICT || !diagnostic.owner().id().equals(mobilizationId)) {
            throw new IllegalArgumentException("mobilization conflict has a foreign diagnostic tuple");
        }
    }
    public HiveMobilizationConflicted(SubjectId mobilizationId, HiveMobilizationConflictReason reason, DiagnosticTuple diagnostic) {
        this(mobilizationId, reason, Optional.empty(), diagnostic);
    }
    @Override public String type() { return "frontier.hive_mobilization_conflicted"; }
}
