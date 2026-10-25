package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;

/** One participant's accepted intent and addressed invalidations. An empty resource
 * set on a cause explicitly means the whole participant, never an unknown resource. */
public record SettlementEconomicPlanningState(long inputRevision, Optional<SettlementEconomicPlan> accepted,
        Map<Change, Set<String>> affected) {
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
    public SettlementEconomicPlanningState {
        accepted = Objects.requireNonNull(accepted);
        var copy = new EnumMap<Change, Set<String>>(Change.class);
        affected.forEach((reason, resources) -> copy.put(Objects.requireNonNull(reason), Set.copyOf(resources)));
        affected = Map.copyOf(copy);
        if (inputRevision < 0 || accepted.filter(plan -> plan.inputRevision() > inputRevision).isPresent()
                || affected.values().stream().anyMatch(items -> items.size() > 256 || items.stream().anyMatch(String::isBlank)))
            throw new IllegalArgumentException("invalid economic input generation");
    }
    public static SettlementEconomicPlanningState empty() {
        return new SettlementEconomicPlanningState(0, Optional.empty(), Map.of());
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
        var next = new EnumMap<Change, Set<String>>(Change.class); next.putAll(affected);
        changes.forEach((reason, items) -> next.merge(reason, Set.copyOf(items), (prior, added) -> {
            if (prior.isEmpty() || added.isEmpty()) return Set.of();
            var union = new HashSet<>(prior); union.addAll(added); return Set.copyOf(union);
        }));
        return new SettlementEconomicPlanningState(Math.incrementExact(inputRevision), accepted, next);
    }
    public SettlementEconomicPlanningState accept(SettlementEconomicPlan plan) {
        long next = Math.incrementExact(accepted.map(SettlementEconomicPlan::revision).orElse(0L));
        if (plan.inputRevision() != inputRevision || plan.revision() != next
                || accepted.filter(prior -> plan.atTick() < prior.atTick()).isPresent())
            throw new IllegalArgumentException("stale economic publication");
        return new SettlementEconomicPlanningState(inputRevision, Optional.of(plan), Map.of());
    }
}
