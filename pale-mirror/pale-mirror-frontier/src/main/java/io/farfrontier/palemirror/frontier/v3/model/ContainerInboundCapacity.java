package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Generic read-only storage commitments supplied by their owning processes. */
public final class ContainerInboundCapacity {
    private ContainerInboundCapacity() { }
    private record Key(SubjectId owner, SubjectId container, String kind) { }

    public record Demand(SubjectId owner, SubjectId container, String itemKind, int quantity) {
        public Demand {
            Objects.requireNonNull(owner); Objects.requireNonNull(container); Objects.requireNonNull(itemKind);
            if (itemKind.isBlank() || quantity <= 0) throw new IllegalArgumentException("invalid inbound storage demand");
        }
    }

    public static Map<String, Long> incoming(List<Demand> demands, SubjectId container,
                                             Optional<SubjectId> completingOwner) {
        Objects.requireNonNull(container); Objects.requireNonNull(completingOwner);
        var totals = new HashMap<String, Long>();
        var seen = new HashSet<Key>();
        boolean completionDeclared = completingOwner.isEmpty();
        for (Demand demand : demands) {
            if (!seen.add(new Key(demand.owner(), demand.container(), demand.itemKind())))
                throw new IllegalArgumentException("duplicate inbound storage demand");
            if (!demand.container().equals(container)) continue;
            if (completingOwner.filter(demand.owner()::equals).isPresent()) completionDeclared = true;
            else totals.merge(demand.itemKind(), (long) demand.quantity(), Math::addExact);
        }
        if (!completionDeclared) throw new IllegalArgumentException("output completion lacks its declared storage demand");
        return Map.copyOf(totals);
    }
}
