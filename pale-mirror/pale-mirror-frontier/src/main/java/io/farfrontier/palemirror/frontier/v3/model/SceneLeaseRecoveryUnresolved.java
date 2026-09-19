package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;
import java.util.Set;

/** Exact loaded-world observation that prevents an UNKNOWN supply scene from being reclaimed. */
public record SceneLeaseRecoveryUnresolved(SceneLeaseId leaseId, Set<SubjectId> missingActorIds,
                                           boolean missingCargoCarrier, DiagnosticTuple diagnostic) implements FrontierPayload {
    public SceneLeaseRecoveryUnresolved {
        Objects.requireNonNull(leaseId, "scene recovery lease");
        missingActorIds = Set.copyOf(missingActorIds);
        if (missingActorIds.isEmpty() && !missingCargoCarrier) {
            throw new IllegalArgumentException("scene recovery must name a missing actor or carrier");
        }
        diagnostic = Objects.requireNonNull(diagnostic, "scene recovery diagnostic");
        if (diagnostic.reason() != DiagnosticReason.SCENE_LEASE_RECOVERY_UNRESOLVED
                || diagnostic.owner().kind() != DiagnosticOwnerKind.SCENE_LEASE || !diagnostic.owner().id().equals(FrontierSceneLeaseStateSupport.recoveryOwner(leaseId))
                || diagnostic.subject().kind() != DiagnosticSubjectKind.SCENE_CARRIER || !diagnostic.subject().id().equals(FrontierSceneLeaseStateSupport.recoveryOwner(leaseId))) {
            throw new IllegalArgumentException("scene recovery has a foreign diagnostic tuple");
        }
    }
    public SceneLeaseRecoveryUnresolved(SceneLeaseId leaseId, Set<SubjectId> missingActorIds, boolean missingCargoCarrier) {
        this(leaseId, missingActorIds, missingCargoCarrier, SceneLeaseRecoveryDiagnosticProducer.forLease(leaseId));
    }
    @Override public String type() { return "frontier.scene_lease_recovery_unresolved"; }
}
