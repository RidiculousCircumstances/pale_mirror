package io.farfrontier.palemirror.frontier.v3.process;

/** Bounded deterministic correlation spelling, never discovery of an owner or work kind. */
final class WorkOpportunityIdentity {
    private WorkOpportunityIdentity() { }
    static String digest(String cause) {
        return io.farfrontier.palemirror.frontier.v3.model.StableCorrelationIdentity.digest(cause);
    }
}
