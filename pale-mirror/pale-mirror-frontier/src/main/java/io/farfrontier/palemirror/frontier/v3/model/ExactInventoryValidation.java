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
    private static final ImmutableInvariantCheck<Void> FUNGIBLE_CARGO = new ImmutableInvariantCheck<>();
    private static final ImmutableRegistryView<SubjectId, CustodyAccount, ResourceCustody> HOLDERS =
            new ImmutableRegistryView<>(FungibleResourceLedger.MAX_ACCOUNTS, Objects::equals);
    private record FungibleContext(Map<SubjectId, ContainerRecord> containers,
                                    Map<SubjectId, CargoBatch> cargo,
                                    Map<InventoryCustody.ContainerSlot, SubjectId> occupied,
                                    Map<SubjectId, ResourceCustody> holders) { }
    private static final ImmutableReferenceIndex.Source<FungibleContext, SubjectId, ContainerRecord> HOLDER_CONTAINERS =
            new ImmutableReferenceIndex.Source<>(FungibleContext::containers);
    private static final ImmutableReferenceIndex.Source<FungibleContext, SubjectId, CargoBatch> HOLDER_CARGO =
            new ImmutableReferenceIndex.Source<>(FungibleContext::cargo);
    private static final ImmutableReferenceIndex.Source<FungibleContext, SubjectId, ResourceCustody> HOLDER_ACCOUNTS =
            new ImmutableReferenceIndex.Source<>(FungibleContext::holders);
    private static final ImmutableReferenceIndex.Source<FungibleContext, InventoryCustody.ContainerSlot, SubjectId> EXACT_SLOTS =
            new ImmutableReferenceIndex.Source<>(FungibleContext::occupied);
    private static final ImmutableGroupView<SubjectId, ResourceCustody, ResourceCustody> LOCATIONS = new ImmutableGroupView<>();
    private static final ImmutableReferenceIndex<FungibleContext, SubjectId, ResourceCustody> HOLDER_REFERENCES =
            new ImmutableReferenceIndex<>(FungibleResourceLedger.MAX_ACCOUNTS, 1,
                    List.of(HOLDER_CONTAINERS, HOLDER_CARGO), (context, custody) -> switch (custody) {
                        case ResourceCustody.Container container -> List.of(HOLDER_CONTAINERS.at(container.containerId()));
                        case ResourceCustody.Cargo cargo -> List.of(HOLDER_CARGO.at(cargo.cargoId()));
                        default -> List.of();
                    }, (context, id, custody) -> validateHolder(context.containers(), context.cargo(), custody));
    private static final ImmutableReferenceIndex<FungibleContext, SubjectId, PhysicalStackBinding> BINDING_REFERENCES =
            new ImmutableReferenceIndex<>(FungibleResourceLedger.MAX_BINDINGS, 3,
                    List.of(HOLDER_CONTAINERS, HOLDER_ACCOUNTS, EXACT_SLOTS), (context, binding) ->
                        binding.address() instanceof PhysicalStackAddress.ContainerSlot slot
                                ? List.of(HOLDER_CONTAINERS.at(slot.slot().containerId()), HOLDER_ACCOUNTS.at(binding.accountId()), EXACT_SLOTS.at(slot.slot()))
                                : List.of(),
                    (context, id, binding) -> validateBinding(context.containers(), context.occupied(), context.holders(), binding));
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
                ? HOLDERS.project(ExactInventoryValidation.class, fungibleResources.accounts(), (before, after) -> false, (id, account) -> account.custody())
                : HOLDERS.project(ExactInventoryValidation.class, fungibleResources.accounts(), (id, account) -> account.custody());
        FUNGIBLE_CUSTODY.validate(() -> {
            if (ImmutableInvariantCheck.fullAudit())
                validateFungibleResourceCustody(containers, cargo, occupiedSlots, holders, fungibleResources.bindings());
            else validateFungibleReferences(containers, cargo, occupiedSlots, holders, fungibleResources.bindings());
        },
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
            validateHolder(containers, cargo, custody);
        }

        for (CargoBatch batch : cargo.values()) if (batch.fungibleContents()) {
            long matching = accounts.values().stream().filter(value -> value instanceof ResourceCustody.Cargo holder
                    && holder.cargoId().equals(batch.id())).count();
            if (matching != 1) throw new IllegalArgumentException("fungible cargo must retain one exact custody account");
        }
        for (PhysicalStackBinding binding : bindings.values()) validateBinding(containers, occupiedSlots, accounts, binding);
    }
    private static void validateHolder(Map<SubjectId, ContainerRecord> containers, Map<SubjectId, CargoBatch> cargo,
                                       ResourceCustody custody) {
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
    private static void validateBinding(Map<SubjectId, ContainerRecord> containers,
                                    Map<InventoryCustody.ContainerSlot, SubjectId> occupiedSlots,
                                    Map<SubjectId, ResourceCustody> accounts, PhysicalStackBinding binding) {
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
    private static Set<ResourceCustody> exclusiveLocation(ResourceCustody custody) {
        return custody instanceof ResourceCustody.Actor ? Set.of() : Set.of(custody);
    }
    private static void validateFungibleReferences(Map<SubjectId, ContainerRecord> containers, Map<SubjectId, CargoBatch> cargo,
                                                  Map<InventoryCustody.ContainerSlot, SubjectId> occupied,
                                                  Map<SubjectId, ResourceCustody> holders, Map<SubjectId, PhysicalStackBinding> bindings) {
        if (!LOCATIONS.uniqueMemberships(ExactInventoryValidation.class, holders, ExactInventoryValidation::exclusiveLocation))
            throw new IllegalArgumentException("one physical/resource location must have one fungible custody account");
        var context = new FungibleContext(containers, cargo, occupied, holders);
        HOLDER_REFERENCES.validate(ExactInventoryValidation.class, holders, context);
        FUNGIBLE_CARGO.validate(() -> {
            for (CargoBatch batch : cargo.values()) if (batch.fungibleContents()) {
                var matching = LOCATIONS.unorderedRows(ExactInventoryValidation.class, holders,
                        ExactInventoryValidation::exclusiveLocation, new ResourceCustody.Cargo(batch.id()));
                if (matching.size() != 1) throw new IllegalArgumentException("fungible cargo must retain one exact custody account");
            }
        }, cargo, holders);
        BINDING_REFERENCES.validate(ExactInventoryValidation.class, bindings, context);
    }
    static void require(Object value, Object expected, String label) {
        if (value instanceof ExactItemStack item && item.custody().equals(expected)) return;
        throw new IllegalArgumentException("dangling or conflicting " + label);
    }
    static void requireDistinct(List<SubjectId> values, String label) {
        if (values.stream().distinct().count() != values.size()) throw new IllegalArgumentException(label + " must contain distinct exact item identities");
    }
}
