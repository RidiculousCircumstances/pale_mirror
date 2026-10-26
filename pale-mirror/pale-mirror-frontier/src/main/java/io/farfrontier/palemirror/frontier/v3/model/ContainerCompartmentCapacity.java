package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;

/** Packs declared material commitments only inside their retained compartment. */
final class ContainerCompartmentCapacity {
    static boolean receive(FrontierWorldState state, ContainerRecord record, ContainerSectionId section,
            String kind, int quantity, ContainerIncomingStock incoming, Optional<io.farfrontier.palemirror.frontier.v3.api.SubjectId> completing) {
        if (quantity < 1 || quantity > 64 || !incoming.individual().isEmpty()) return false;
        var allowed = record.compartmentLayout().slots(section);
        var quantities = new HashMap<String, Long>(incoming.fungible());
        quantities.merge(kind, (long) quantity, Math::addExact);
        var image = ReferenceContainerCustody.expectedFungibleSlots(state, record.id());
        var reserved = ContainerPhysicalReservations.slots(state, record.id(), completing);
        int free = allowed.size();
        for (int slot : allowed) {
            if (reserved.contains(slot) || state.inventory().occupiedSlots().containsKey(new InventoryCustody.ContainerSlot(record.id(), slot))) free--;
            var stack = image.get(slot);
            if (stack != null) quantities.merge(stack.itemKind(), (long) stack.quantity(), Math::addExact);
        }
        long needed = 0;
        for (long count : quantities.values()) needed = Math.addExact(needed, (count + 63L) / 64L);
        return needed <= free;
    }
    private ContainerCompartmentCapacity() { }
}
