package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

/** Closed first-boundary producers for retained hive-growth blocks. */
public enum HiveGrowthDiagnosticProducer {
    BIOMASS_UNAVAILABLE(HiveGrowthBlockReason.BIOMASS_UNAVAILABLE),
    GROWTH_CAPACITY_UNAVAILABLE(HiveGrowthBlockReason.GROWTH_CAPACITY_UNAVAILABLE),
    PHYSICAL_CONSUMPTION_UNKNOWN(HiveGrowthBlockReason.PHYSICAL_CONSUMPTION_UNKNOWN);
    private final HiveGrowthBlockReason reason;
    HiveGrowthDiagnosticProducer(HiveGrowthBlockReason reason) { this.reason = reason; }
    public HiveGrowthBlocked create(SubjectId hiveId, SubjectId nestId, SubjectId workId) {
        return new HiveGrowthBlocked(hiveId, nestId, workId, reason, new DiagnosticTuple(DiagnosticReason.HIVE_GROWTH_BLOCKED,
                DiagnosticCategory.WAIT_OR_BLOCKED, new DiagnosticOwner(DiagnosticOwnerKind.HIVE_GROWTH, workId),
                new DiagnosticSubject(DiagnosticSubjectKind.FACILITY, nestId), DiagnosticDisposition.RETRY));
    }
}
