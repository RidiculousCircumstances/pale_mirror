package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;
import java.util.Optional;

/** An owner's exact physical destination; optional inbound demand already covered by this slot. */
public record ContainerSlotClaim(Owner owner, InventoryCustody.ContainerSlot slot,
                                 Optional<ContainerInboundCapacity.Demand> coveredDemand) {
    public enum Family { HARVEST, PRODUCTION, SHIPMENT, EXTRACTION }
    public record Owner(Family family, SubjectId id) {
        public Owner { Objects.requireNonNull(family); Objects.requireNonNull(id); }
    }
    public ContainerSlotClaim {
        Objects.requireNonNull(owner); Objects.requireNonNull(slot);
        coveredDemand = Objects.requireNonNull(coveredDemand);
        if (coveredDemand.filter(demand -> !demand.container().equals(slot.containerId())).isPresent())
            throw new IllegalArgumentException("physical slot covers a foreign container demand");
    }
    public ContainerSlotClaim(Owner owner, InventoryCustody.ContainerSlot slot) {
        this(owner, slot, Optional.empty());
    }
}
