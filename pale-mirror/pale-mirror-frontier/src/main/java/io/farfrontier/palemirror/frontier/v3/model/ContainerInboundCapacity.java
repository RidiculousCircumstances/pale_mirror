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
        Objects.requireNonNull(container); Objects.requireNonNull(section); Objects.requireNonNull(completingOwner);
        var totals = new HashMap<String, Long>();
        var individual = new HashMap<String, Long>();
        var seen = new HashSet<Key>();
        boolean completionDeclared = completingOwner.isEmpty();
        for (Demand demand : demands) {
            if (!seen.add(new Key(demand.owner(), demand.container(), demand.section(), demand.itemKind())))
                throw new IllegalArgumentException("duplicate inbound storage demand");
            if (!demand.container().equals(container) || !demand.section().equals(section)) continue;
            if (completingOwner.filter(demand.owner()::equals).isPresent()) completionDeclared = true;
            else (demand.representation() == Representation.INDIVIDUAL ? individual : totals)
                    .merge(demand.itemKind(), (long) demand.quantity(), Math::addExact);
        }
        if (!completionDeclared) throw new IllegalArgumentException("output completion lacks its declared storage demand");
        return new ContainerIncomingStock(totals, individual);
    }
}
