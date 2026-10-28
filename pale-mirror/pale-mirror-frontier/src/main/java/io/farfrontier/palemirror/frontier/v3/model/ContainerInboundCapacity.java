package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Generic read-only storage commitments supplied by their owning processes. */
public final class ContainerInboundCapacity {
    private ContainerInboundCapacity() { }
    private record Key(SubjectId owner, SubjectId container, ContainerSectionId section, String kind) { }

    public enum Representation { FUNGIBLE, INDIVIDUAL }
    public record Demand(SubjectId owner, SubjectId container, String itemKind, int quantity, Representation representation, ContainerSectionId section) {
        public Demand {
            Objects.requireNonNull(owner); Objects.requireNonNull(container); Objects.requireNonNull(itemKind);
            Objects.requireNonNull(representation); Objects.requireNonNull(section);
            if (itemKind.isBlank() || quantity <= 0) throw new IllegalArgumentException("invalid inbound storage demand");
        }
        public Demand(SubjectId owner, SubjectId container, String itemKind, int quantity, Representation representation) {
            this(owner, container, itemKind, quantity, representation, ContainerSectionId.STOCK);
        }
        public Demand(SubjectId owner, SubjectId container, String itemKind, int quantity) {
            this(owner, container, itemKind, quantity, Representation.FUNGIBLE, ContainerSectionId.STOCK);
        }
    }

    public static ContainerIncomingStock incoming(List<Demand> demands, SubjectId container,
                                             Optional<SubjectId> completingOwner) {
        return incoming(demands, container, ContainerSectionId.STOCK, completingOwner);
    }
    public static ContainerIncomingStock incoming(List<Demand> demands, SubjectId container,
            ContainerSectionId section, Optional<SubjectId> completingOwner) {
        return index(demands).incoming(container, section, completingOwner);
    }

    /** Immutable commitments indexed once by their explicit holder and completion owner. */
    public static Index index(List<Demand> demands) {
        var scopes = new HashMap<Holder, Amounts>(); var seen = new HashSet<Key>();
        for (var demand : List.copyOf(demands)) {
            if (!seen.add(new Key(demand.owner(), demand.container(), demand.section(), demand.itemKind())))
                throw new IllegalArgumentException("duplicate inbound storage demand");
            var amounts = scopes.computeIfAbsent(new Holder(demand.container(), demand.section()), ignored -> new Amounts());
            amounts.total.add(demand);
            amounts.owners.computeIfAbsent(demand.owner(), ignored -> new Quantities()).add(demand);
        }
        var result = new HashMap<Holder, Scope>();
        scopes.forEach((holder, amounts) -> {
            var owners = new HashMap<SubjectId, ContainerIncomingStock>();
            amounts.owners.forEach((owner, quantities) -> owners.put(owner, quantities.stock()));
            result.put(holder, new Scope(amounts.total.stock(), Map.copyOf(owners)));
        });
        return new Index(Map.copyOf(result));
    }
    private record Holder(SubjectId container, ContainerSectionId section) {
        private Holder { Objects.requireNonNull(container); Objects.requireNonNull(section); }
    }
    private record Scope(ContainerIncomingStock total, Map<SubjectId, ContainerIncomingStock> owners) { }
    private static final class Quantities {
        final Map<String, Long> fungible = new HashMap<>(), individual = new HashMap<>();
        void add(Demand demand) {
            (demand.representation() == Representation.INDIVIDUAL ? individual : fungible)
                    .merge(demand.itemKind(), (long) demand.quantity(), Math::addExact);
        }
        ContainerIncomingStock stock() { return new ContainerIncomingStock(fungible, individual); }
    }
    private static final class Amounts {
        final Quantities total = new Quantities();
        final Map<SubjectId, Quantities> owners = new HashMap<>();
    }
    public static final class Index {
        private static final Scope EMPTY = new Scope(ContainerIncomingStock.empty(), Map.of());
        private final Map<Holder, Scope> scopes;
        private Index(Map<Holder, Scope> scopes) { this.scopes = scopes; }
        void requireCompletion(SubjectId container, ContainerSectionId section, Optional<SubjectId> completingOwner) {
            Objects.requireNonNull(completingOwner);
            var scope = scopes.getOrDefault(new Holder(container, section), EMPTY);
            if (completingOwner.isPresent() && !scope.owners().containsKey(completingOwner.orElseThrow()))
                throw new IllegalArgumentException("output completion lacks its declared storage demand");
        }
        public ContainerIncomingStock incoming(SubjectId container, ContainerSectionId section, Optional<SubjectId> completingOwner) {
            requireCompletion(container, section, completingOwner);
            return incomingExceptOwner(container, section, completingOwner);
        }
        /** Excluding a fully slot-backed completion need not find an uncovered demand. */
        ContainerIncomingStock incomingExceptOwner(SubjectId container, ContainerSectionId section, Optional<SubjectId> excludedOwner) {
            Objects.requireNonNull(excludedOwner);
            var scope = scopes.getOrDefault(new Holder(container, section), EMPTY);
            var owned = excludedOwner.map(scope.owners()::get).orElse(null);
            return owned == null ? scope.total() : new ContainerIncomingStock(
                    subtract(scope.total().fungible(), owned.fungible()), subtract(scope.total().individual(), owned.individual()));
        }
        private static Map<String, Long> subtract(Map<String, Long> total, Map<String, Long> owned) {
            var remaining = new HashMap<>(total);
            owned.forEach((kind, quantity) -> {
                long next = Math.subtractExact(remaining.getOrDefault(kind, 0L), quantity);
                if (next == 0) remaining.remove(kind); else remaining.put(kind, next);
            });
            return remaining;
        }
    }
}
