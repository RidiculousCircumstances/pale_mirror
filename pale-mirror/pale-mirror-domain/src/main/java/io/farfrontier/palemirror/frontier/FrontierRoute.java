package io.farfrontier.palemirror.frontier;

/** Directed capacity is unnecessary: the clearing phase chooses one direction per day. */
public record FrontierRoute(String id, String leftSettlementId, String rightSettlementId, long capacity) {
    public FrontierRoute {
        if (id == null || id.isBlank() || leftSettlementId == null || leftSettlementId.isBlank()
                || rightSettlementId == null || rightSettlementId.isBlank() || leftSettlementId.equals(rightSettlementId)
                || capacity < 1) throw new IllegalArgumentException("invalid Frontier route");
    }
}
