package io.farfrontier.palemirror.api;

import java.util.Objects;

/** Read-only, semantic camera pose used by the repeatable visual-audit harness. */
public record VisualAuditView(String id, String targetKind, String targetId, String dimensionId,
                              VisualPoint playerFeet, float yaw, float pitch) {
    public VisualAuditView {
        if (id == null || !id.matches("[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("visual audit view id is invalid");
        }
        if (targetKind == null || !targetKind.matches("[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("visual audit target kind is invalid");
        }
        if (targetId == null || targetId.isBlank()) throw new IllegalArgumentException("targetId is required");
        if (dimensionId == null || dimensionId.isBlank()) throw new IllegalArgumentException("dimensionId is required");
        Objects.requireNonNull(playerFeet, "playerFeet");
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch) || pitch < -90.0F || pitch > 90.0F) {
            throw new IllegalArgumentException("visual audit rotation is invalid");
        }
    }
}
