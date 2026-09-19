package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Objects;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

/**
 * One bounded, chunk-addressable physical consequence.  The expected empty cell is an
 * observation precondition, not authority to clear a later block.
 */
public record DeferredAftermathCell(BlockPosition position, PhysicalDeltaSemanticTarget semanticTarget, GrayboxMaterial expectedMaterial,
                                    GrayboxSemanticPart expectedPart, long authorityRevision, DeferredAftermathCellStatus status) {
    public DeferredAftermathCell(BlockPosition position, PhysicalDeltaSemanticTarget semanticTarget, GrayboxMaterial expectedMaterial,
                                 GrayboxSemanticPart expectedPart, DeferredAftermathCellStatus status) {
        this(position, semanticTarget, expectedMaterial, expectedPart, -1L, status);
    }
    public DeferredAftermathCell {
        Objects.requireNonNull(position, "aftermath cell position");
        Objects.requireNonNull(semanticTarget, "aftermath cell semantic target");
        Objects.requireNonNull(expectedMaterial, "aftermath cell material");
        Objects.requireNonNull(expectedPart, "aftermath cell semantic part");
        Objects.requireNonNull(status, "aftermath cell status");
        if (authorityRevision < -1L || status == DeferredAftermathCellStatus.PENDING && authorityRevision != -1L
                || status == DeferredAftermathCellStatus.RUNNING && authorityRevision < 0L) {
            throw new IllegalArgumentException("aftermath cell authority revision is invalid");
        }
    }
    /** Read-only subject label; authority remains the typed semantic target. */
    public SubjectId expectedOwner() { return semanticTarget.subjectId(); }

    public DeferredAftermathCell begin(long revision) {
        if (status != DeferredAftermathCellStatus.PENDING) throw new IllegalArgumentException("aftermath cell is not pending");
        return new DeferredAftermathCell(position, semanticTarget, expectedMaterial, expectedPart, revision, DeferredAftermathCellStatus.RUNNING);
    }
    public DeferredAftermathCell resolved(DeferredAftermathCellStatus next) {
        if (status != DeferredAftermathCellStatus.RUNNING || next == DeferredAftermathCellStatus.PENDING || next == DeferredAftermathCellStatus.RUNNING) {
            throw new IllegalArgumentException("aftermath cell is not running");
        }
        return new DeferredAftermathCell(position, semanticTarget, expectedMaterial, expectedPart, authorityRevision, next);
    }

}
