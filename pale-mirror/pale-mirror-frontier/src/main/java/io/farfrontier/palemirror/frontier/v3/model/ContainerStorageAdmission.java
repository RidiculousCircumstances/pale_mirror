package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;
import java.util.function.Predicate;

/** Shared storage admission. Future-demand ownership is declared by the composition, not discovered here. */
final class ContainerStorageAdmission {
    private ContainerStorageAdmission() { }

    static Map<String, Long> inbound(FrontierWorldState state, SubjectId container, Optional<SubjectId> completing) {
        // Prove the completion's declared owner even when its entire demand is now slot-backed.
        ContainerInboundCapacity.incoming(ContainerStorageDemandSources.demands(state), container, completing);
        var uncovered = ContainerPhysicalReservations.uncoveredDemands(state).stream()
                .filter(demand -> completing.filter(demand.owner()::equals).isEmpty()).toList();
        return ContainerInboundCapacity.incoming(uncovered, container, Optional.empty());
    }
    static boolean receive(FrontierWorldState state, SubjectId container, String kind, int quantity, Optional<SubjectId> completing) {
        return state.inventory().canReceiveFungible(container, kind, quantity, ContainerPhysicalReservations.slots(state, container, completing),
                inbound(state, container, completing));
    }
    static boolean receiveCargo(FrontierWorldState state, SubjectId cargo, SubjectId container) {
        return state.inventory().canReceiveFungibleCargo(cargo, container, state.reservedContainerSlots(container),
                inbound(state, container, Optional.empty()));
    }
    static boolean available(FrontierWorldState state, InventoryCustody.ContainerSlot slot,
                             Optional<SubjectId> completing, Predicate<InventoryCustody.ContainerSlot> held) {
        return state.inventory().availableSlots(slot.containerId(), ContainerPhysicalReservations.slots(state, slot.containerId(), completing),
                inbound(state, slot.containerId(), completing)).contains(slot.slot())
                && ReferenceContainerCustody.expectedFungibleSlot(state, slot.containerId(), slot.slot()).isEmpty()
                && !held.test(slot);
    }
    static OptionalInt first(FrontierWorldState state, SubjectId container, Optional<SubjectId> completing,
                             Predicate<InventoryCustody.ContainerSlot> held) {
        for (int slot : state.inventory().availableSlots(container, ContainerPhysicalReservations.slots(state, container, completing), inbound(state, container, completing))) {
            var address = new InventoryCustody.ContainerSlot(container, slot);
            if (ReferenceContainerCustody.expectedFungibleSlot(state, container, slot).isEmpty() && !held.test(address))
                return OptionalInt.of(slot);
        }
        return OptionalInt.empty();
    }
}
