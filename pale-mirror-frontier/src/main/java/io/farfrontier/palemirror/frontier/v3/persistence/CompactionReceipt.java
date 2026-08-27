package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.Revision;

import java.util.Objects;

/** Bounded-compaction result; only WAL fully covered by the snapshot may be removed. */
public record CompactionReceipt(Revision coveredRevision, long retainedTransactionCount) {
    public CompactionReceipt {
        Objects.requireNonNull(coveredRevision, "covered revision");
        if (retainedTransactionCount < 0L) throw new IllegalArgumentException("retained transaction count cannot be negative");
    }
}
