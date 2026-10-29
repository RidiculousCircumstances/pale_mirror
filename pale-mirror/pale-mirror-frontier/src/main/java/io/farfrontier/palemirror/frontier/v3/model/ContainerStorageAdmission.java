package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;
import java.util.function.Predicate;

/** Shared storage admission. Future-demand ownership is declared by the composition, not discovered here. */
public final class ContainerStorageAdmission {
    private ContainerStorageAdmission() { }
    private record ReceiveQuery(SubjectId container, String kind, int quantity, Optional<SubjectId> completing, boolean individual) { }
    private static final ImmutableInputView<Map<ReceiveQuery, Boolean>> RECEIVING = new ImmutableInputView<>();
    private static boolean receiving(FrontierWorldState state, ReceiveQuery query, java.util.function.BooleanSupplier assess) {
        // Only identical immutable drafts share an admission answer. A player change,
        // competing claim, custody transition or station mutation always rechecks it.
        var cache = RECEIVING.get(List.of(state), HashMap::new);
        var existing = cache.get(query);
        if (existing != null) return existing;
        boolean result = assess.getAsBoolean();
        if (cache.size() >= 512) cache.clear();
        cache.put(query, result); return result;
    }
    private record IncomingQuery(SubjectId container, ContainerSectionId section, Optional<SubjectId> completing) { }
    private static final ImmutableKeyedInputView<IncomingQuery, ContainerIncomingStock> INCOMING = new ImmutableKeyedInputView<>(512);
    private static final ImmutableResultView<Boolean, ContainerInboundCapacity.Index> DEMAND_INDEXES = new ImmutableResultView<>(4);
    private static ContainerInboundCapacity.Index index(List<ContainerInboundCapacity.Demand> immutableDemands) {
        return DEMAND_INDEXES.get(List.of(immutableDemands), Boolean.TRUE, () -> ContainerInboundCapacity.index(immutableDemands));
    }

    static ContainerIncomingStock inbound(FrontierWorldState state, SubjectId container, Optional<SubjectId> completing) {
        return inbound(state, new ResourceCustody.Container(container), completing);
    }
    static ContainerIncomingStock inbound(FrontierWorldState state, ResourceCustody.Container holder, Optional<SubjectId> completing) {
        var container = holder.containerId();
        var demands = ContainerStorageDemandSources.demands(state, container);
        var uncovered = ContainerPhysicalReservations.uncoveredDemands(state, container);
        return INCOMING.get(state.bootstrap(), new IncomingQuery(container, holder.section(), completing), List.of(demands, uncovered), () -> {
            // Prove the completion's declared owner even when its entire demand is slot-backed.
            index(demands).requireCompletion(container, holder.section(), completing);
            return ContainerInboundCapacity.index(uncovered).incomingExceptOwner(container, holder.section(), completing);
        });
    }
    static boolean receive(FrontierWorldState state, SubjectId container, String kind, int quantity, Optional<SubjectId> completing) {
        return receiving(state, new ReceiveQuery(container, kind, quantity, completing, false),
                () -> assessReceive(state, container, kind, quantity, completing));
    }
    private static boolean assessReceive(FrontierWorldState state, SubjectId container, String kind, int quantity, Optional<SubjectId> completing) {
        var record = state.inventory().containers().get(container);
        if (record == null || ReferenceContainerCustody.blocksCanonicalUse(state, container)
                || record.storage().filter(policy -> !policy.filter().accepts(kind)).isPresent()) return false;
        if (!record.compartmentLayout().sections().containsKey(ContainerSectionId.STOCK)) return false;
        return state.inventory().canReceiveFungible(container, kind, quantity, ContainerPhysicalReservations.slots(state, container, completing),
                inbound(state, container, completing));
    }
    public static boolean receive(FrontierWorldState state, ResourceCustody.Container holder, String kind, int quantity, Optional<SubjectId> completing) {
        if (holder.section().equals(ContainerSectionId.STOCK)) return receive(state, holder.containerId(), kind, quantity, completing);
        var record = state.inventory().containers().get(holder.containerId());
        if (record == null || ReferenceContainerCustody.blocksCanonicalUse(state, holder.containerId())) return false;
        return ContainerCompartmentCapacity.receive(state, record, holder.section(), kind, quantity, inbound(state, holder, completing), completing);
    }
    static boolean receiveIndividuals(FrontierWorldState state, SubjectId container, String kind, int quantity, Optional<SubjectId> completing) {
        return receiving(state, new ReceiveQuery(container, kind, quantity, completing, true),
                () -> assessIndividuals(state, container, kind, quantity, completing));
    }
    private static boolean assessIndividuals(FrontierWorldState state, SubjectId container, String kind, int quantity, Optional<SubjectId> completing) {
        var record = state.inventory().containers().get(container);
        if (quantity < 1 || record == null || ReferenceContainerCustody.blocksCanonicalUse(state, container)
                || record.storage().filter(policy -> !policy.filter().accepts(kind)).isPresent()) return false;
        var available = state.inventory().availableSlots(container, ContainerPhysicalReservations.slots(state, container, completing),
                inbound(state, container, completing));
        if (available.isEmpty()) return false;
        var fungible = ReferenceContainerCustody.expectedFungibleSlots(state, container);
        return available.stream().filter(slot -> !fungible.containsKey(slot)).count() >= quantity;
    }
    static boolean receiveCargo(FrontierWorldState state, SubjectId cargo, SubjectId container) {
        var record = state.inventory().containers().get(container);
        var carried = state.inventory().fungibleResources().accounts().get(cargo);
        if (record == null || ReferenceContainerCustody.blocksCanonicalUse(state, container)) return false;
        if (carried != null && record.storage().filter(policy -> carried.lotQuantities().keySet().stream().anyMatch(lot ->
                !policy.filter().accepts(state.inventory().fungibleResources().lots().get(lot).itemKind()))).isPresent()) return false;
        return state.inventory().canReceiveFungibleCargo(cargo, container, state.reservedContainerSlots(container),
                inbound(state, container, Optional.empty()));
    }
    static boolean available(FrontierWorldState state, InventoryCustody.ContainerSlot slot,
                             Optional<SubjectId> completing, Predicate<InventoryCustody.ContainerSlot> held) {
        if (ReferenceContainerCustody.blocksCanonicalUse(state, slot.containerId())) return false;
        return state.inventory().availableSlots(slot.containerId(), ContainerPhysicalReservations.slots(state, slot.containerId(), completing),
                inbound(state, slot.containerId(), completing)).contains(slot.slot())
                && ReferenceContainerCustody.expectedFungibleSlot(state, slot.containerId(), slot.slot()).isEmpty()
                && !held.test(slot);
    }
    static OptionalInt first(FrontierWorldState state, SubjectId container, Optional<SubjectId> completing,
                             Predicate<InventoryCustody.ContainerSlot> held) {
        if (ReferenceContainerCustody.blocksCanonicalUse(state, container)) return OptionalInt.empty();
        var available = state.inventory().availableSlots(container, ContainerPhysicalReservations.slots(state, container, completing), inbound(state, container, completing));
        if (available.isEmpty()) return OptionalInt.empty();
        var fungible = ReferenceContainerCustody.expectedFungibleSlots(state, container);
        for (int slot : available) {
            var address = new InventoryCustody.ContainerSlot(container, slot);
            if (!fungible.containsKey(slot) && !held.test(address)) return OptionalInt.of(slot);
        }
        return OptionalInt.empty();
    }
    /** Existing exact-effect inspection excludes its slot claim, not a fabricated recipe demand or new-effect fence. */
    static boolean availableForClaim(FrontierWorldState state, InventoryCustody.ContainerSlot slot,
            ContainerSlotClaim.Owner current, Predicate<InventoryCustody.ContainerSlot> held) {
        return state.inventory().availableSlots(slot.containerId(), ContainerPhysicalReservations.slotsExceptClaim(state, slot.containerId(), current),
                inbound(state, slot.containerId(), Optional.empty())).contains(slot.slot())
                && ReferenceContainerCustody.expectedFungibleSlot(state, slot.containerId(), slot.slot()).isEmpty() && !held.test(slot);
    }
}
