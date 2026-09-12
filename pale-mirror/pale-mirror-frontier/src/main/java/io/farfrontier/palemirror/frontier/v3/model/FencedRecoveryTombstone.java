package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Durable bounded rejection fact retained after current physical authority has moved on. */
public record FencedRecoveryTombstone(SubjectId bindingId, FencedRecoveryAsset asset, SubjectId ownerId,
                                      long ownerRevision, long retiredEpoch, FencedRecoveryDisposition disposition,
                                      String reason) {
    public FencedRecoveryTombstone {
        Objects.requireNonNull(bindingId, "tombstone binding id"); Objects.requireNonNull(asset, "tombstone asset");
        Objects.requireNonNull(ownerId, "tombstone owner"); Objects.requireNonNull(disposition, "tombstone disposition");
        Objects.requireNonNull(reason, "tombstone reason");
        if (ownerRevision < 0 || retiredEpoch < 1 || reason.isBlank() || reason.length() > 96) throw new IllegalArgumentException("recovery tombstone is invalid");
    }
}
