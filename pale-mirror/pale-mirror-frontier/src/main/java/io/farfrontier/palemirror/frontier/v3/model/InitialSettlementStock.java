package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;

/** Finite public stock declared at genesis; never a market grant or recurring source. */
public record InitialSettlementStock(SubjectId settlementId, String itemKind, int quantity) {
    public InitialSettlementStock {
        Objects.requireNonNull(settlementId);
        if (itemKind == null || !itemKind.matches("[a-z][a-z0-9_-]{0,31}:[a-z0-9][a-z0-9_./-]{0,127}")
                || quantity < 1 || quantity > 1024)
            throw new IllegalArgumentException("invalid finite initial settlement stock");
    }
    public String canonicalText() { return settlementId.value() + ',' + itemKind + ',' + quantity; }
}
