package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;

/** One participant's accepted intent and addressed invalidations. An empty resource
 * set on a cause explicitly means the whole participant, never an unknown resource. */
public final class SettlementEconomicPlanningState {
    private final long inputRevision;
    private final Optional<SettlementEconomicPlan> accepted;
    private final Map<Change, Set<String>> affected;
    private final EconomicInputInvalidations invalidations;
    /** Rebuildable same-process evidence, not a canonical revision or history.
     * Only MARKET invalidations inherit it. No predecessor state is retained. */
    private final Object nonMarketBasis;
    private static final SettlementEconomicPlanningState EMPTY =
            new SettlementEconomicPlanningState(0, Optional.empty(), Map.of());
    public long inputRevision() { return inputRevision; }
    public Optional<SettlementEconomicPlan> accepted() { return accepted; }
    public Map<Change, Set<String>> affected() { return affected; }
    @Override public boolean equals(Object other) {
        return this == other || other instanceof SettlementEconomicPlanningState value
                && inputRevision == value.inputRevision && accepted.equals(value.accepted) && affected.equals(value.affected);
    }
    @Override public int hashCode() { return (Long.hashCode(inputRevision) * 31 + accepted.hashCode()) * 31 + affected.hashCode(); }
    @Override public String toString() {
        return "SettlementEconomicPlanningState[inputRevision=" + inputRevision + ", accepted=" + accepted + ", affected=" + affected + "]";
    }
    public enum Change {
        STOCK("STOCK"), COMMITMENT("COMMITMENT"), CASH("CASH"), CAPACITY("CAPACITY"),
        MARKET("MARKET"), NEED("NEED"), DELIVERY("DELIVERY"), DEADLINE("DEADLINE");
        private final String wireTag;
        Change(String wireTag) { this.wireTag = wireTag; }
        public String wireTag() { return wireTag; }
        public static Change fromWireTag(String tag) {
            return Arrays.stream(values()).filter(value -> value.wireTag.equals(tag)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("unknown economic change tag: " + tag));
        }
    }
    public enum Status { AWAITING_PLAN, CURRENT, REVIEW_REQUIRED }
    /** External/bootstrap/hydration input starts a fresh proof boundary. */
    public SettlementEconomicPlanningState(long inputRevision, Optional<SettlementEconomicPlan> accepted,
            Map<Change, Set<String>> affected) {
        this(inputRevision, accepted, EconomicInputInvalidations.copyOf(affected), new Object());
    }
    private SettlementEconomicPlanningState(long inputRevision, Optional<SettlementEconomicPlan> accepted,
            EconomicInputInvalidations invalidations, Object nonMarketBasis) {
        accepted = Objects.requireNonNull(accepted);
        if (inputRevision < 0 || accepted.filter(plan -> plan.inputRevision() > inputRevision).isPresent())
            throw new IllegalArgumentException("invalid economic input generation");
        this.inputRevision = inputRevision; this.accepted = accepted;
        this.invalidations = Objects.requireNonNull(invalidations); this.affected = invalidations.affected();
        this.nonMarketBasis = Objects.requireNonNull(nonMarketBasis);
    }
    public static SettlementEconomicPlanningState empty() {
        return EMPTY;
    }
    public Set<Change> changes() { return affected.keySet(); }
    public Status status(long now) {
        return accepted.map(plan -> plan.inputRevision() != inputRevision || now >= plan.reviewAtTick()
                ? Status.REVIEW_REQUIRED : Status.CURRENT).orElse(Status.AWAITING_PLAN);
    }
    private Set<String> resources(Change reason, Set<String> catalog) {
        var items = affected.get(reason);
        return items == null ? Set.of() : items.isEmpty() ? catalog : items;
    }
    public Set<String> awaitingResources(long now, Set<String> catalog) {
        if (accepted.isEmpty() || now >= accepted.orElseThrow().reviewAtTick()) return Set.copyOf(catalog);
        return invalidatedProduction(catalog);
    }
    /** Critical input invalidations, separate from a technical review deadline.
     * Priced advice has its own explicit expiry; this grants no work authority. */
    public Set<String> invalidatedProduction(Set<String> catalog) {
        if (accepted.isEmpty()) return Set.copyOf(catalog);
        var blocked = new HashSet<String>();
        for (var reason : List.of(Change.CAPACITY, Change.NEED))
            for (var item : resources(reason, catalog))
                blocked.addAll(accepted.orElseThrow().affectedProducts().getOrDefault(item, Set.of(item)));
        // Current coverage and exact reservations consume the stock/cash plan.
        // Ordinary execution does not revoke independent production authority.
        return Set.copyOf(blocked);
    }
    public Set<String> awaitingTrade(long now, Set<String> catalog) {
        if (affected.containsKey(Change.CASH)) return Set.copyOf(catalog);
        var blocked = new HashSet<>(awaitingResources(now, catalog));
        // New external offers request a review, but do not revoke an existing
        // consent: matching still requires the exact current quote and stock.

        return Set.copyOf(blocked);
    }
    public SettlementEconomicPlanningState invalidate(Map<Change, Set<String>> changes) {
        if (changes.isEmpty()) throw new IllegalArgumentException("economic invalidation requires its cause");
        return new SettlementEconomicPlanningState(Math.incrementExact(inputRevision), accepted, invalidations.with(changes),
                changes.keySet().equals(Set.of(Change.MARKET)) ? nonMarketBasis : new Object());
    }
    /** Market notifications are dated observations. A closed inherited basis
     * proves no new non-market change since capture, even when the captured
     * state was already dirty. Equal dirty sets cannot prove that history. */
    public boolean onlyMarketChangesSince(SettlementEconomicPlanningState captured) {
        return inputRevision > captured.inputRevision && accepted.equals(captured.accepted)
                && nonMarketBasis == captured.nonMarketBasis;
    }
    /** Exact hard-input proof, independent of advisory MARKET notifications. */
    public boolean sameNonMarketInputs(SettlementEconomicPlanningState captured) {
        return accepted.equals(captured.accepted) && nonMarketBasis == captured.nonMarketBasis;
    }
    public SettlementEconomicPlanningState accept(SettlementEconomicPlan plan) {
        long next = Math.incrementExact(accepted.map(SettlementEconomicPlan::revision).orElse(0L));
        if (plan.inputRevision() != inputRevision || plan.revision() != next
                || accepted.filter(prior -> plan.atTick() < prior.atTick()).isPresent())
            throw new IllegalArgumentException("stale economic publication");
        return new SettlementEconomicPlanningState(inputRevision, Optional.of(plan), Map.of());
    }
}
