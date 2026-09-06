package io.farfrontier.palemirror.frontier.reference;

import java.util.Objects;

/**
 * Source-owned compatibility record for Python's dormant {@code HiveIntent}
 * ledger. It is kept even though the active V2 director expresses decisions
 * through {@link ReferenceHiveOrder}; canonical state may not omit it.
 */
public record ReferenceHiveIntent(String kind, int sourceId, int targetX, int targetY, Integer targetId,
                                  int untilDay, String reason) {
    public ReferenceHiveIntent {
        kind = Objects.requireNonNull(kind, "kind");
        reason = Objects.requireNonNull(reason, "reason");
    }
}
