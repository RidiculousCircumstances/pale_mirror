package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.Revision;

import java.util.Objects;

/** Evidence that an atomically installed snapshot was verified by its store. */
public record SnapshotReceipt(Revision revision, long snapshotSequence) {
    public SnapshotReceipt {
        Objects.requireNonNull(revision, "revision");
        if (snapshotSequence < 0L) throw new IllegalArgumentException("snapshot sequence cannot be negative");
    }
}
