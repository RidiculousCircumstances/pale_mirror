package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;

import java.util.Objects;
import java.util.UUID;

/**
 * Self-contained terminal authorization for one physical cargo projection. Historical scene
 * compaction must not erase this still-pending obligation. It cannot grant live custody,
 * recreate cargo or authorize deletion without an exact provider observation.
 */
public record CargoProjectionRetirement(WorldId worldId, SceneLeaseId leaseId, SubjectId cargoId,
                                        UUID entityId, FencedRecoveryTombstone authorization,
                                        Disposition disposition) {
    public CargoProjectionRetirement {
        Objects.requireNonNull(worldId, "retired projection world");
        Objects.requireNonNull(leaseId, "retired projection scene");
        Objects.requireNonNull(cargoId, "retired projection cargo");
        Objects.requireNonNull(entityId, "retired projection entity");
        Objects.requireNonNull(authorization, "retired projection authorization");
        Objects.requireNonNull(disposition, "retired projection disposition");
        if (authorization.asset() != FencedRecoveryAsset.CARGO
                || authorization.disposition() != FencedRecoveryDisposition.REJECT_STALE
                || !authorization.bindingId().equals(FrontierSceneLeaseStateSupport.cargoRecoveryBindingId(cargoId))
                || !authorization.ownerId().equals(FrontierSceneLeaseStateSupport.recoveryOwner(leaseId))
                || !entityId.equals(CargoCarrierIdentity.id(worldId, leaseId, cargoId))) {
            throw new IllegalArgumentException("cargo projection retirement requires exact terminal cargo authority");
        }
    }

    /** The owning CLOSED transition supplies the authorization, never a missing-record inference. */
    public static CargoProjectionRetirement confirmed(SceneLease closed, FencedRecoveryTombstone authorization,
                                                       Disposition disposition) {
        Objects.requireNonNull(closed, "retired cargo scene");
        Objects.requireNonNull(authorization, "retired cargo authorization");
        if (closed.status() != SceneLeaseStatus.CLOSED || !FrontierSceneBehaviors.isLogistics(closed)
                || !authorization.ownerId().equals(FrontierSceneLeaseStateSupport.recoveryOwner(closed))
                || authorization.ownerRevision() != closed.revision()) {
            throw new IllegalArgumentException("cargo projection retirement requires its exact closed owner");
        }
        return new CargoProjectionRetirement(closed.worldId(), closed.id(), FrontierSceneBehaviors.logistics(closed).cargoId(),
                CargoCarrierIdentity.id(closed), authorization, disposition);
    }

    public enum Disposition {
        REMOVE_PROJECTION(1), RETAIN_WORLD_CUSTODY(2);

        private final int wireTag;
        Disposition(int wireTag) { this.wireTag = wireTag; }
        public int wireTag() { return wireTag; }
        public static Disposition fromWireTag(int tag) {
            return switch (tag) {
                case 1 -> REMOVE_PROJECTION;
                case 2 -> RETAIN_WORLD_CUSTODY;
                default -> throw new IllegalArgumentException("unknown cargo retirement disposition");
            };
        }
    }
}
