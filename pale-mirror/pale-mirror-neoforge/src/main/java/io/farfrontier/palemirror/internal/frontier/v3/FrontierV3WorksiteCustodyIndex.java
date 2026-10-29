package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;
import java.util.function.*;

/** Read-only join of declared scopes with current custody obligations. No admission,
 * native eligibility, witness or durability result is cached here. */
final class FrontierV3WorksiteCustodyIndex<T> {
    private record Scopes(List<SubjectId> live, List<SubjectId> preparing) { }
    record Selection<T>(List<T> live, List<T> preparing) { }
    private static final ImmutableInputView<Scopes> SCOPES = new ImmutableInputView<>();
    private Object version;
    private Map<SubjectId, PhysicalCustodyLease> leases;
    private Map<SubjectId, T> declared = Map.of();
    private Map<SubjectId, Integer> order = Map.of();
    private Selection<T> selected = new Selection<>(List.of(), List.of());

    Selection<T> update(Object declarationVersion, Supplier<List<T>> declarations, Function<T, SubjectId> scope,
                        Map<SubjectId, PhysicalCustodyLease> current) {
        Objects.requireNonNull(declarationVersion); Objects.requireNonNull(current);
        boolean changed = version != declarationVersion;
        if (changed) {
            var rows = new HashMap<SubjectId, T>(); var ranks = new HashMap<SubjectId, Integer>();
            for (var value : declarations.get()) {
                var id = Objects.requireNonNull(scope.apply(value));
                if (rows.putIfAbsent(id, value) != null) throw new IllegalArgumentException("duplicate worksite scope declaration");
                ranks.put(id, ranks.size());
            }
            declared = Map.copyOf(rows); order = Map.copyOf(ranks); version = declarationVersion;
        }
        if (!changed && leases == current) return selected;
        var scopes = SCOPES.get(List.of(current), () -> {
            var live = new ArrayList<SubjectId>(); var preparing = new ArrayList<SubjectId>();
            current.forEach((id, lease) -> {
                if (lease.live()) live.add(id);
                if (lease.status() == PhysicalCustodyLeaseStatus.PREPARING) preparing.add(id);
            });
            return new Scopes(List.copyOf(live), List.copyOf(preparing));
        });
        var next = new Selection<>(retain(selected.live(), join(scopes.live())), retain(selected.preparing(), join(scopes.preparing())));
        leases = current; return selected = next;
    }
    private List<T> retain(List<T> previous, List<T> next) {
        if (previous.size() != next.size()) return next;
        for (int i = 0; i < next.size(); i++) if (previous.get(i) != next.get(i)) return next;
        return previous;
    }
    private List<T> join(List<SubjectId> scopes) {
        return scopes.stream().filter(declared::containsKey).sorted(Comparator.comparingInt(order::get))
                .map(declared::get).toList();
    }
}
