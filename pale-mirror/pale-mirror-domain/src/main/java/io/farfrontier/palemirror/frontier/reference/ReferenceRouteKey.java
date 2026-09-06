package io.farfrontier.palemirror.frontier.reference;

/** Canonical undirected identity for one physical trade route. */
public record ReferenceRouteKey(int lowerSettlementId, int upperSettlementId) {
    public static ReferenceRouteKey between(int first, int second) {
        return new ReferenceRouteKey(Math.min(first, second), Math.max(first, second));
    }
}
