package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Bounded owner registry for settlement and whole-Hivemind decision scopes. */
public final class DecisionAuthorityState {
    private static final int MAX_AUTHORITIES = 13;
    private record InitialImage(FrontierBootstrap bootstrap, DecisionAuthorityState state) { }
    // The full-state audit needs the same immutable declared owner/policy roster. Rebuilding
    // every settlement's initial staffing per transaction supplies no new validation evidence.
    // Keep just the most recent bootstrap image per thread, never world history/current decisions.
    private static final ThreadLocal<InitialImage> INITIAL_IMAGE = new ThreadLocal<>();
    private final Map<SubjectId, DecisionAuthority> authorities;

    public DecisionAuthorityState(Map<SubjectId, DecisionAuthority> authorities) {
        LinkedHashMap<SubjectId, DecisionAuthority> copied = new LinkedHashMap<>(Objects.requireNonNull(authorities, "decision authorities"));
        if (copied.size() > MAX_AUTHORITIES || copied.entrySet().stream().anyMatch(entry -> !entry.getKey().equals(entry.getValue().ownerId()))) {
            throw new IllegalArgumentException("decision authority registry is invalid");
        }
        this.authorities = Map.copyOf(copied);
    }

    public static DecisionAuthorityState empty() { return new DecisionAuthorityState(Map.of()); }

    /** Fresh worlds install all decision owners before any review can run. */
    public static DecisionAuthorityState initial(FrontierBootstrap bootstrap) {
        Objects.requireNonNull(bootstrap, "bootstrap");
        var image = INITIAL_IMAGE.get();
        if (image != null && image.bootstrap() == bootstrap) return image.state();
        Map<SubjectId, DecisionAuthority> initial = new LinkedHashMap<>();
        bootstrap.settlements().stream().sorted(java.util.Comparator.comparing(Settlement::id)).forEach(settlement ->
                initial.put(settlement.id(), new DecisionAuthority(settlement.id(), DecisionAuthorityKind.SETTLEMENT,
                        new DecisionPolicyDescriptor("frontier:settlement", 1), 0L, java.util.List.of(), java.util.List.of(),
                        SettlementWorkPolicy.initial(settlement, bootstrap.ruleset().labour()))));
        SubjectId hive = bootstrap.hive().id();
        initial.put(hive, new DecisionAuthority(hive, DecisionAuthorityKind.HIVEMIND,
                new DecisionPolicyDescriptor("frontier:hivemind", 1), 0L, java.util.List.of(), java.util.List.of()));
        var state = new DecisionAuthorityState(initial);
        INITIAL_IMAGE.set(new InitialImage(bootstrap, state));
        return state;
    }
    public Map<SubjectId, DecisionAuthority> authorities() { return authorities; }
    public DecisionAuthority require(SubjectId ownerId) {
        DecisionAuthority authority = authorities.get(Objects.requireNonNull(ownerId, "decision owner"));
        if (authority == null) throw new IllegalArgumentException("missing canonical decision authority");
        return authority;
    }

    public DecisionAuthorityState replace(DecisionAuthority next) {
        Objects.requireNonNull(next, "decision authority");
        if (!authorities.containsKey(next.ownerId())) throw new IllegalArgumentException("cannot replace an absent decision authority");
        Map<SubjectId, DecisionAuthority> replaced = new LinkedHashMap<>(authorities);
        replaced.put(next.ownerId(), next);
        return new DecisionAuthorityState(replaced);
    }

    @Override public boolean equals(Object other) {
        return other instanceof DecisionAuthorityState state && authorities.equals(state.authorities);
    }

    @Override public int hashCode() { return authorities.hashCode(); }
}
