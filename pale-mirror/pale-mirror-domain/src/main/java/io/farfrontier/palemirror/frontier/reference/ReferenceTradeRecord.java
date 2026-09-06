package io.farfrontier.palemirror.frontier.reference;

import java.util.List;
import java.util.Objects;

/** Completed aggregate goods transfer from Python {@code TradeRecord}. */
public record ReferenceTradeRecord(
        int day,
        int sellerId,
        int buyerId,
        ReferenceResource resource,
        double shipped,
        double delivered,
        double unitPrice,
        List<Integer> path
) {
    public ReferenceTradeRecord {
        Objects.requireNonNull(resource, "resource");
        path = List.copyOf(Objects.requireNonNull(path, "path"));
    }

    public double value() {
        return delivered * unitPrice;
    }
}
