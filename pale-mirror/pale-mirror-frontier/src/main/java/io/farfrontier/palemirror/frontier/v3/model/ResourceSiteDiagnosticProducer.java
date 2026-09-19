package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;

/** Complete named producer declarations for resource-site terminal facts. */
public enum ResourceSiteDiagnosticProducer {
    PLAYER_REMOVED(1, ResourceSiteConflictReason.PLAYER_REMOVED_MANAGED_CELL, ResourceSiteConflictSource.PLAYER_WORLD_OBSERVATION, DiagnosticReason.RESOURCE_SITE_PLAYER_REMOVED, DiagnosticSubjectKind.RESOURCE_SITE_CELL),
    EXPLOSION_DAMAGE(2, ResourceSiteConflictReason.EXPLOSION_DAMAGED_MANAGED_CELL, ResourceSiteConflictSource.EXPLOSION_WITNESS, DiagnosticReason.RESOURCE_SITE_EXPLOSION_DAMAGE, DiagnosticSubjectKind.RESOURCE_SITE_CELL),
    ORDINARY_OBSERVATION_MISMATCH(3, ResourceSiteConflictReason.OBSERVED_MANAGED_CELL_MISMATCH, ResourceSiteConflictSource.LIFECYCLE_RECONCILIATION, DiagnosticReason.RESOURCE_SITE_LIFECYCLE_RECONCILIATION, DiagnosticSubjectKind.RESOURCE_SITE_CELL),
    RESTART_OBSERVATION_MISMATCH(4, ResourceSiteConflictReason.OBSERVED_MANAGED_CELL_MISMATCH, ResourceSiteConflictSource.RESTART_RECONCILIATION, DiagnosticReason.RESOURCE_SITE_RECOVERY_UNRESOLVED, DiagnosticSubjectKind.PHYSICAL_EFFECT),
    PHYSICAL_INTENT_RECOVERY(5, ResourceSiteConflictReason.RECOVERY_UNRESOLVED, ResourceSiteConflictSource.PHYSICAL_INTENT_RECOVERY, DiagnosticReason.RESOURCE_SITE_RECOVERY_UNRESOLVED, DiagnosticSubjectKind.PHYSICAL_EFFECT),
    WORKER_DIED(6, ResourceSiteConflictReason.WORKER_DIED, ResourceSiteConflictSource.WORKER_DEATH, DiagnosticReason.RESOURCE_SITE_WORKER_DEATH, DiagnosticSubjectKind.HARVEST_WORKER),
    ADAPTER_WRITE_FAILURE(7, ResourceSiteConflictReason.OBSERVED_MANAGED_CELL_MISMATCH, ResourceSiteConflictSource.ADAPTER_WRITE_FAILURE, DiagnosticReason.RESOURCE_SITE_ADAPTER_WRITE_FAILURE, DiagnosticSubjectKind.ADAPTER_BOUNDARY),
    SCENE_CARRIER_FENCE(8, ResourceSiteConflictReason.CARRIER_FENCE_UNRESOLVED, ResourceSiteConflictSource.SCENE_CARRIER_FENCE, DiagnosticReason.RESOURCE_SITE_CARRIER_FENCE_UNRESOLVED, DiagnosticSubjectKind.SCENE_CARRIER),
    LAWFUL_LIFECYCLE_LAG(9, ResourceSiteConflictReason.OBSERVED_MANAGED_CELL_MISMATCH, ResourceSiteConflictSource.LAWFUL_LIFECYCLE_LAG, DiagnosticReason.RESOURCE_SITE_LAWFUL_LAG, DiagnosticSubjectKind.RESOURCE_SITE_CELL);

    private final int wireTag; private final ResourceSiteConflictReason reason; private final ResourceSiteConflictSource source;
    private final DiagnosticReason diagnosticReason; private final DiagnosticSubjectKind subjectKind;
    ResourceSiteDiagnosticProducer(int wireTag, ResourceSiteConflictReason reason, ResourceSiteConflictSource source, DiagnosticReason diagnosticReason, DiagnosticSubjectKind subjectKind) {
        this.wireTag = wireTag; this.reason = reason; this.source = source; this.diagnosticReason = diagnosticReason; this.subjectKind = subjectKind;
    }
    public int wireTag() { return wireTag; }
    public ResourceSiteConflictReason reason() { return reason; }
    public ResourceSiteConflictSource source() { return source; }
    public DiagnosticReason diagnosticReason() { return diagnosticReason; }
    public DiagnosticTuple stamp(SubjectId siteId, SubjectId affectedSubject) {
        Objects.requireNonNull(siteId, "resource-site owner"); Objects.requireNonNull(affectedSubject, "resource-site subject");
        return new DiagnosticTuple(diagnosticReason, diagnosticReason.category(), new DiagnosticOwner(DiagnosticOwnerKind.RESOURCE_SITE, siteId),
                new DiagnosticSubject(subjectKind, affectedSubject), diagnosticReason.disposition());
    }
    public static ResourceSiteDiagnosticProducer requireWireTag(int tag) {
        return switch (tag) {
            case 1 -> PLAYER_REMOVED; case 2 -> EXPLOSION_DAMAGE; case 3 -> ORDINARY_OBSERVATION_MISMATCH;
            case 4 -> RESTART_OBSERVATION_MISMATCH; case 5 -> PHYSICAL_INTENT_RECOVERY; case 6 -> WORKER_DIED;
            case 7 -> ADAPTER_WRITE_FAILURE; case 8 -> SCENE_CARRIER_FENCE; case 9 -> LAWFUL_LIFECYCLE_LAG;
            default -> throw new IllegalArgumentException("unknown resource-site diagnostic producer tag: " + tag);
        };
    }
}
