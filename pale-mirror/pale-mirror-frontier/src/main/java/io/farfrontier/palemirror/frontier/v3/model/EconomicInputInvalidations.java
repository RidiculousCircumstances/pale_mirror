package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;
import io.farfrontier.palemirror.frontier.v3.model.SettlementEconomicPlanningState.Change;

/** Immutable cause scopes only. Reusing a covered scope never reuses an input
 * generation, accepted plan or non-market lineage proof. Empty scope means all. */
final class EconomicInputInvalidations {
    private static final EconomicInputInvalidations EMPTY = new EconomicInputInvalidations(Map.of());
    private final Map<Change, Set<String>> affected;
    private EconomicInputInvalidations(Map<Change, Set<String>> affected) { this.affected = affected; }
    static EconomicInputInvalidations copyOf(Map<Change, Set<String>> source) {
        if (source.isEmpty()) return EMPTY;
        var copied = new EnumMap<Change, Set<String>>(Change.class);
        source.forEach((reason, items) -> {
            var resources = Set.copyOf(items);
            if (resources.size() > 256) throw new IllegalArgumentException("invalid economic input generation");
            for (var item : resources) if (item.isBlank()) throw new IllegalArgumentException("invalid economic input generation");
            copied.put(Objects.requireNonNull(reason), resources);
        });
        return copied.isEmpty() ? EMPTY : new EconomicInputInvalidations(Map.copyOf(copied));
    }
    Map<Change, Set<String>> affected() { return affected; }
    EconomicInputInvalidations with(Map<Change, Set<String>> changes) {
        if (affected.isEmpty()) return copyOf(changes);
        EnumMap<Change, Set<String>> next = null;
        for (var change : changes.entrySet()) {
            var reason = Objects.requireNonNull(change.getKey()); var added = Set.copyOf(change.getValue());
            var prior = (next == null ? affected : next).get(reason);
            var combined = prior == null ? added : unionScopes(prior, added);
            if (prior == combined) continue;
            if (next == null) { next = new EnumMap<>(Change.class); next.putAll(affected); }
            next.put(reason, combined);
        }
        return next == null ? this : copyOf(next);
    }
    /** Both inputs are already immutable scopes. This is not ordinary set union:
     * an empty declaration covers the whole participant and absorbs subsets. */
    static Set<String> unionScopes(Set<String> prior, Set<String> added) {
        if (prior.isEmpty()) return prior;
        if (added.isEmpty()) return added;
        if (prior.containsAll(added)) return prior;
        if (added.containsAll(prior)) return added;
        var union = new HashSet<>(prior); union.addAll(added); return Set.copyOf(union);
    }
}
