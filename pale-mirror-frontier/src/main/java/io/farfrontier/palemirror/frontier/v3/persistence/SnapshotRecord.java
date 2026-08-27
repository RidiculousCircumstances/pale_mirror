package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;

import java.util.Objects;

/** A verified snapshot and the exact WAL prefix it covers. */
public record SnapshotRecord(CheckpointImage checkpoint, long coveredWalSequence) {
    public SnapshotRecord {
        Objects.requireNonNull(checkpoint, "checkpoint");
        if (coveredWalSequence < 0L) throw new IllegalArgumentException("covered WAL sequence cannot be negative");
    }
}
