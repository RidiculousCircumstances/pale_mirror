package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Declared compartments share one physical container and lease, but retain distinct resource custody. */
public final class ContainerResourceAccounts {
    public static SubjectId id(ResourceCustody.Container holder) {
        String base = ReferenceContainerCustody.scopeId(holder.containerId()).value();
        return holder.section().equals(ContainerSectionId.STOCK) ? new SubjectId(base)
                : new SubjectId(base + "-section-" + holder.section().value());
    }

    public static List<FungiblePhysicalObservation.Stack> observed(ExactInventory inventory,
            ResourceCustody.Container holder, List<FungiblePhysicalObservation.Stack> image) {
        var record = Objects.requireNonNull(inventory.containers().get(holder.containerId()), "compartment container");
        var slots = record.compartmentLayout().slots(holder.section());
        for (var stack : image) if (!(stack.address() instanceof PhysicalStackAddress.ContainerSlot address)
                || !address.slot().containerId().equals(holder.containerId()) || (address.slot().slot() < 0 || address.slot().slot() >= record.slotCount()))
            throw new IllegalArgumentException("compartment image contains a foreign address");
        return image.stream().filter(stack -> slots.contains(((PhysicalStackAddress.ContainerSlot) stack.address()).slot().slot())).toList();
    }

    private ContainerResourceAccounts() { }
}
