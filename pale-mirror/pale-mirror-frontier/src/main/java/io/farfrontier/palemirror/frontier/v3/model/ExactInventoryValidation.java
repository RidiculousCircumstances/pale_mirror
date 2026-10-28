package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Inventory-owned exact identity, title and custody proofs. No allocation or process policy.
 * Unchanged immutable inputs retain one successful image; full ingress forces every check. */
final class ExactInventoryValidation {
    private ExactInventoryValidation() { }
    private static final ImmutableInvariantCheck<Void> CONTAINERS = new ImmutableInvariantCheck<>();
    private static final ImmutableInvariantCheck<Void> ITEM_TITLES = new ImmutableInvariantCheck<>();
    private static final ImmutableInvariantCheck<Void> LOT_TITLES = new ImmutableInvariantCheck<>();
    private static final ImmutableInvariantCheck<Void> CONFLICTS = new ImmutableInvariantCheck<>();
    private static final ImmutableInvariantCheck<Void> CARGO_TITLES = new ImmutableInvariantCheck<>();
    private static final ImmutableInvariantCheck<Void> EXACT_CUSTODY = new ImmutableInvariantCheck<>();
    private static final ImmutableInvariantCheck<Void> FUNGIBLE_CUSTODY = new ImmutableInvariantCheck<>();
    private static final ImmutableRegistryView<SubjectId, CustodyAccount, ResourceCustody> HOLDERS =
            new ImmutableRegistryView<>(FungibleResourceLedger.MAX_ACCOUNTS);
    static void validate(Map<SubjectId, ContainerRecord> containers, Map<SubjectId, ExactItemStack> items,
                         Map<SubjectId, CargoBatch> cargo, Map<UUID, List<SubjectId>> playerItems,
                         Map<UUID, List<SubjectId>> worldCarrierItems, Map<SubjectId, InventoryConflict> conflicts,
                         Map<SubjectId, ContainerSurface> surfaces, EconomicLedger economics,
                         FungibleResourceLedger fungibleResources, Map<InventoryCustody.ContainerSlot, SubjectId> occupiedSlots) {
        CONTAINERS.validate(() -> {
            for (Map.Entry<SubjectId, ContainerRecord> entry : containers.entrySet()) {
                if (!entry.getKey().equals(entry.getValue().id())) throw new IllegalArgumentException("container map key does not match container identity");
                economics.require(entry.getValue().ownerId());
            }
            if (!containers.keySet().equals(surfaces.keySet())) throw new IllegalArgumentException("every exact container requires one physical surface");
            for (Map.Entry<SubjectId, ContainerSurface> entry : surfaces.entrySet()) {
                if (!entry.getKey().equals(entry.getValue().containerId()) || !containers.containsKey(entry.getKey())) throw new IllegalArgumentException("container surface must own one known container");
                var purpose = containers.get(entry.getKey()).purpose();
                if (purpose.referenceScope() && (purpose == ContainerPurpose.MOBILE_STORAGE) != !entry.getValue().fixed())
                    throw new IllegalArgumentException("container purpose and physical attachment disagree");
            }
        }, containers, surfaces, economics.accounts());
        ITEM_TITLES.validate(() -> {
            for (Map.Entry<SubjectId, ExactItemStack> entry : items.entrySet()) {
                if (!entry.getKey().equals(entry.getValue().id())) throw new IllegalArgumentException("item map key does not match item identity");
                economics.require(entry.getValue().economicOwnerId());
            }
        }, items, economics.accounts());
        LOT_TITLES.validate(() -> {
            for (ResourceLot lot : fungibleResources.lots().values()) {
                economics.require(lot.economicOwnerId());
            }
        }, fungibleResources.lots(), economics.accounts());
        CONFLICTS.validate(() -> {
            for (Map.Entry<SubjectId, InventoryConflict> entry : conflicts.entrySet()) {
                InventoryConflict conflict = entry.getValue();
                if (!entry.getKey().equals(conflict.id())) {
                    throw new IllegalArgumentException("inventory conflict map key does not match conflict identity");
                }
                ContainerRecord container = containers.get(conflict.containerId());
                if (container == null || conflict.slot() >= container.slotCount()) throw new IllegalArgumentException("inventory conflict targets an invalid container slot");
                // A conflict is durable evidence rooted at the physical slot.  Its original exact
                // subject may legitimately have been consumed, moved out of the active ledger, or
                // destroyed after the observation; retaining the container anchor keeps that
                // evidence restart-safe without resurrecting the item or adopting foreign state.
            }
        }, conflicts, containers);
        CARGO_TITLES.validate(() -> {
            for (Map.Entry<SubjectId, CargoBatch> entry : cargo.entrySet()) {
                if (!entry.getKey().equals(entry.getValue().id())) throw new IllegalArgumentException("cargo map key does not match cargo identity");
                economics.require(entry.getValue().ownerId());
            }
        }, cargo, economics.accounts());
        EXACT_CUSTODY.validate(() -> {
            Map<InventoryCustody.ContainerSlot, SubjectId> slots = new HashMap<>();
            for (ExactItemStack item : items.values()) switch (item.custody()) {
                case InventoryCustody.ContainerSlot slot -> {
                    ContainerRecord container = containers.get(slot.containerId());
                    if (container == null || slot.slot() >= container.slotCount() || slots.put(slot, item.id()) != null) throw new IllegalArgumentException("invalid or duplicate container slot custody");
                }
                case InventoryCustody.Cargo batch -> {
                    CargoBatch value = cargo.get(batch.cargoId());
                    if (value == null || value.fungibleContents() || !value.itemIds().contains(item.id())) throw new IllegalArgumentException("dangling or conflicting cargo custody");
                    if (!value.ownerId().equals(item.economicOwnerId())) throw new IllegalArgumentException("cargo-held item claim must belong to its sender");
                }
                case InventoryCustody.Player player -> {
                    List<SubjectId> value = playerItems.get(player.playerId());
                    if (value == null || !value.contains(item.id())) throw new IllegalArgumentException("dangling or conflicting player custody");
                }
                case InventoryCustody.WorldCarrier carrier -> {
                    List<SubjectId> value = worldCarrierItems.get(carrier.carrierId());
                    if (value == null || !value.contains(item.id())) throw new IllegalArgumentException("dangling or conflicting world carrier custody");
                }
                case InventoryCustody.Actor ignored -> { }
            };
            for (CargoBatch batch : cargo.values()) {
                if (batch.fungibleContents()) continue;
                for (SubjectId item : batch.itemIds()) require(items.get(item), new InventoryCustody.Cargo(batch.id()), "cargo reverse custody");
            }
            for (Map.Entry<UUID, List<SubjectId>> player : playerItems.entrySet()) {
                requireDistinct(player.getValue(), "player custody");
                for (SubjectId item : player.getValue()) require(items.get(item), new InventoryCustody.Player(player.getKey()), "player reverse custody");
            }
            for (Map.Entry<UUID, List<SubjectId>> carrier : worldCarrierItems.entrySet()) {
                requireDistinct(carrier.getValue(), "world carrier custody");
                for (SubjectId item : carrier.getValue()) require(items.get(item), new InventoryCustody.WorldCarrier(carrier.getKey()), "world carrier reverse custody");
            }
            if (!slots.equals(occupiedSlots)) throw new IllegalArgumentException("container slot index must exactly match exact item custody");
        }, items, cargo, playerItems, worldCarrierItems, containers, occupiedSlots);
        var holders = ImmutableInvariantCheck.fullAudit()
                ? HOLDERS.project(containers, fungibleResources.accounts(), (before, after) -> false, (id, account) -> account.custody())
                : HOLDERS.project(containers, fungibleResources.accounts(), (id, account) -> account.custody());
        FUNGIBLE_CUSTODY.validate(() -> validateFungibleResourceCustody(containers, cargo, occupiedSlots, holders, fungibleResources.bindings()),
                containers, cargo, occupiedSlots, holders, fungibleResources.bindings());
    }
    private static void validateFungibleResourceCustody(Map<SubjectId, ContainerRecord> containers,
                                                        Map<SubjectId, CargoBatch> cargo,
                                                        Map<InventoryCustody.ContainerSlot, SubjectId> occupiedSlots,
                                                        Map<SubjectId, ResourceCustody> accounts, Map<SubjectId, PhysicalStackBinding> bindings) {
        Map<ResourceCustody, SubjectId> accountsByCustody = new HashMap<>();
        for (var entry : accounts.entrySet()) {
            var custody = entry.getValue();
            if (!(custody instanceof ResourceCustody.Actor)
                    && accountsByCustody.put(custody, entry.getKey()) != null) {
                throw new IllegalArgumentException("one physical/resource location must have one fungible custody account");
            }
            switch (custody) {
                case ResourceCustody.Container container -> {
                    ContainerRecord record = containers.get(container.containerId());
                    if (record == null) throw new IllegalArgumentException("fungible account references an unknown container");
                    record.compartmentLayout().slots(container.section());
                    // A holder may store another registered party's goods. Custody is not
                    // title; admission and commercial acceptance enforce their own authority.
                }
                case ResourceCustody.Cargo cargoCustody -> {
                    CargoBatch batch = cargo.get(cargoCustody.cargoId());
                    if (batch == null || !batch.fungibleContents()) throw new IllegalArgumentException("fungible account references an unknown exact cargo batch");
                }
                case ResourceCustody.Actor ignored -> { }
                case ResourceCustody.Player ignored -> { }
                case ResourceCustody.WorldCarrier ignored -> { }
                case ResourceCustody.Ground ignored -> { }
            }
        }
        for (CargoBatch batch : cargo.values()) if (batch.fungibleContents()) {
            long matching = accounts.values().stream().filter(value -> value instanceof ResourceCustody.Cargo holder
                    && holder.cargoId().equals(batch.id())).count();
            if (matching != 1) throw new IllegalArgumentException("fungible cargo must retain one exact custody account");
        }
        for (PhysicalStackBinding binding : bindings.values()) {
            ResourceCustody custody = accounts.get(binding.accountId());
            if (binding.address() instanceof PhysicalStackAddress.ContainerSlot slot) {
                ContainerRecord record = containers.get(slot.slot().containerId());
                if (record == null || slot.slot().slot() >= record.slotCount()
                        || !(custody instanceof ResourceCustody.Container container)
                        || !container.containerId().equals(slot.slot().containerId())
                        || !record.compartmentLayout().slots(container.section()).contains(slot.slot().slot())) {
                    throw new IllegalArgumentException("fungible physical binding has no matching owned container account");
                }
                if (occupiedSlots.containsKey(slot.slot())) {
                    throw new IllegalArgumentException("fungible physical binding overlaps an exact-item slot");
                }
            }
        }
    }
    static void require(Object value, Object expected, String label) {
        if (value instanceof ExactItemStack item && item.custody().equals(expected)) return;
        throw new IllegalArgumentException("dangling or conflicting " + label);
    }
    static void requireDistinct(List<SubjectId> values, String label) {
        if (values.stream().distinct().count() != values.size()) throw new IllegalArgumentException(label + " must contain distinct exact item identities");
    }
}
