package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

/** Immutable exact custody ledger. Any dangling, duplicate or mixed custody is rejected. */
public record ExactInventory(Map<SubjectId, ContainerRecord> containers, Map<SubjectId, ExactItemStack> items,
                             Map<SubjectId, CargoBatch> cargo, Map<UUID, List<SubjectId>> playerItems,
                             Map<UUID, List<SubjectId>> worldCarrierItems, Map<SubjectId, InventoryConflict> conflicts,
                             Map<SubjectId, ContainerSurface> surfaces,
                             EconomicLedger economics,
                             FungibleResourceLedger fungibleResources,
                             Map<InventoryCustody.ContainerSlot, SubjectId> occupiedSlots) {
    private static final int MAX_WORLD_CARRIERS = 4_096;
    private static final int MAX_CONFLICTS = 4_096;
    public ExactInventory {
        containers = Map.copyOf(containers); items = Map.copyOf(items); cargo = Map.copyOf(cargo);
        playerItems = playerItems.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
        worldCarrierItems = worldCarrierItems.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
        conflicts = Map.copyOf(conflicts);
        surfaces = Map.copyOf(surfaces); Objects.requireNonNull(economics, "economic ledger");
        Objects.requireNonNull(fungibleResources, "fungible resource ledger"); occupiedSlots = Map.copyOf(occupiedSlots);
        if (worldCarrierItems.size() > MAX_WORLD_CARRIERS) throw new IllegalArgumentException("world carrier retention limit exceeded");
        if (conflicts.size() > MAX_CONFLICTS) throw new IllegalArgumentException("inventory conflict retention limit exceeded");
        Map<InventoryCustody.ContainerSlot, SubjectId> slots = new HashMap<>();
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
        for (Map.Entry<SubjectId, ExactItemStack> entry : items.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().id())) throw new IllegalArgumentException("item map key does not match item identity");
            economics.require(entry.getValue().economicOwnerId());
        }
        for (ResourceLot lot : fungibleResources.lots().values()) {
            economics.require(lot.economicOwnerId());
        }
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
        for (Map.Entry<SubjectId, CargoBatch> entry : cargo.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().id())) throw new IllegalArgumentException("cargo map key does not match cargo identity");
            economics.require(entry.getValue().ownerId());
        }
        for (ExactItemStack item : items.values()) switch (item.custody()) {
            case InventoryCustody.ContainerSlot slot -> {
                ContainerRecord container = containers.get(slot.containerId());
                if (container == null || slot.slot() >= container.slotCount() || slots.put(slot, item.id()) != null) throw new IllegalArgumentException("invalid or duplicate container slot custody");
                if (!container.ownerId().equals(item.economicOwnerId())) throw new IllegalArgumentException("container-held item claim must belong to its container owner");
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
        validateFungibleResourceCustody(containers, cargo, occupiedSlots, fungibleResources);
        if (!slots.equals(occupiedSlots)) throw new IllegalArgumentException("container slot index must exactly match exact item custody");
    }

    /**
     * The slot index is a derived immutable acceleration structure, never a second custody
     * source of truth.  Deserializers and ordinary callers supply only canonical item custody;
     * the strict canonical constructor proves that its reconstructed index is exact.
     */
    public ExactInventory(Map<SubjectId, ContainerRecord> containers, Map<SubjectId, ExactItemStack> items,
                          Map<SubjectId, CargoBatch> cargo, Map<UUID, List<SubjectId>> playerItems,
                          Map<UUID, List<SubjectId>> worldCarrierItems, Map<SubjectId, InventoryConflict> conflicts,
                          Map<SubjectId, ContainerSurface> surfaces, EconomicLedger economics) {
        this(containers, items, cargo, playerItems, worldCarrierItems, conflicts, surfaces, economics, FungibleResourceLedger.empty(), occupiedSlots(items));
    }
    public ExactInventory(Map<SubjectId, ContainerRecord> containers, Map<SubjectId, ExactItemStack> items,
                          Map<SubjectId, CargoBatch> cargo, Map<UUID, List<SubjectId>> playerItems,
                          Map<UUID, List<SubjectId>> worldCarrierItems, Map<SubjectId, InventoryConflict> conflicts,
                          Map<SubjectId, ContainerSurface> surfaces, EconomicLedger economics,
                          FungibleResourceLedger fungibleResources) {
        this(containers, items, cargo, playerItems, worldCarrierItems, conflicts, surfaces, economics, fungibleResources, occupiedSlots(items));
    }
    public ExactInventory(Map<SubjectId, ContainerRecord> containers, Map<SubjectId, ExactItemStack> items,
                          Map<SubjectId, CargoBatch> cargo, Map<UUID, List<SubjectId>> playerItems,
                          Map<UUID, List<SubjectId>> worldCarrierItems, Map<SubjectId, InventoryConflict> conflicts,
                          Map<SubjectId, ContainerSurface> surfaces) {
        this(containers, items, cargo, playerItems, worldCarrierItems, conflicts, surfaces,
                EconomicLedger.fromClaimHolders(containers.values(), items.values(), cargo.values()));
    }
    public ExactInventory(Map<SubjectId, ContainerRecord> containers, Map<SubjectId, ExactItemStack> items,
                          Map<SubjectId, CargoBatch> cargo, Map<UUID, List<SubjectId>> playerItems,
                          Map<UUID, List<SubjectId>> worldCarrierItems, Map<SubjectId, InventoryConflict> conflicts,
                          Map<SubjectId, ContainerSurface> surfaces,
                          Map<InventoryCustody.ContainerSlot, SubjectId> occupiedSlots) {
        this(containers, items, cargo, playerItems, worldCarrierItems, conflicts, surfaces,
                EconomicLedger.fromClaimHolders(containers.values(), items.values(), cargo.values()), FungibleResourceLedger.empty(), occupiedSlots);
    }
    public ExactInventory(Map<SubjectId, ContainerRecord> containers, Map<SubjectId, ExactItemStack> items,
                          Map<SubjectId, CargoBatch> cargo, Map<UUID, List<SubjectId>> playerItems) {
        this(containers, items, cargo, playerItems, Map.of(), Map.of(), Map.of());
    }
    public ExactInventory(Map<SubjectId, ContainerRecord> containers, Map<SubjectId, ExactItemStack> items,
                          Map<SubjectId, CargoBatch> cargo, Map<UUID, List<SubjectId>> playerItems, Map<UUID, List<SubjectId>> worldCarrierItems) {
        this(containers, items, cargo, playerItems, worldCarrierItems, Map.of(), Map.of());
    }
    public static ExactInventory empty() { return new ExactInventory(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), new EconomicLedger(Map.of())); }

    /** Returns the exact stack occupying this physical container slot, if any. */
    public Optional<ExactItemStack> itemAt(SubjectId containerId, int slot) {
        Objects.requireNonNull(containerId, "container id");
        return Optional.ofNullable(occupiedSlots.get(new InventoryCustody.ContainerSlot(containerId, slot))).map(items::get);
    }

    /** A slot already carrying a fungible HOT stack is not free for an exact output. */
    public boolean slotVacant(InventoryCustody.ContainerSlot slot) {
        Objects.requireNonNull(slot, "container slot");
        return availableSlots(slot.containerId()).contains(slot.slot());
    }

    /** Any candidate slot may be used if the remaining total capacity still fits COLD stock. */
    public List<Integer> availableSlots(SubjectId containerId) {
        return availableSlots(containerId, Set.of());
    }

    /** Capacity and physical addresses are both fenced by outstanding work reservations. */
    public List<Integer> availableSlots(SubjectId containerId, Set<Integer> reservedSlots) {
        return availableSlots(containerId, reservedSlots, Map.of());
    }

    /** Future inbound stock consumes capacity, not physical slot addresses. */
    public List<Integer> availableSlots(SubjectId containerId, Set<Integer> reservedSlots, Map<String, Long> inbound) {
        ContainerRecord container = containers.get(Objects.requireNonNull(containerId, "container id"));
        if (container == null) throw new IllegalArgumentException("unknown container: " + containerId.value());
        ContainerSlotBudget budget = slotBudget(containerId);
        if (!validReservations(container, budget, reservedSlots)
                || budget.withIncoming(inbound).requiredSlots() + reservedSlots.size() >= container.slotCount()) return List.of();
        var available = new java.util.ArrayList<Integer>();
        for (int slot = 0; slot < container.slotCount(); slot++) {
            if (!budget.occupied().contains(slot) && !budget.bound().contains(slot)
                    && !reservedSlots.contains(slot)) available.add(slot);
        }
        return List.copyOf(available);
    }

    public boolean canReserveSlots(SubjectId containerId, Set<Integer> reservedSlots) {
        return canReserveSlots(containerId, reservedSlots, Map.of());
    }

    public boolean canReserveSlots(SubjectId containerId, Set<Integer> reservedSlots, Map<String, Long> inbound) {
        ContainerRecord container = containers.get(Objects.requireNonNull(containerId, "container id"));
        if (container == null) throw new IllegalArgumentException("unknown container: " + containerId.value());
        ContainerSlotBudget budget = slotBudget(containerId);
        return validReservations(container, budget, reservedSlots)
                && budget.withIncoming(inbound).requiredSlots() + reservedSlots.size() <= container.slotCount();
    }

    private static boolean validReservations(ContainerRecord container, ContainerSlotBudget budget, Set<Integer> reservedSlots) {
        Objects.requireNonNull(reservedSlots, "container reservations");
        return reservedSlots.stream().allMatch(slot -> slot != null && slot >= 0 && slot < container.slotCount()
                && !budget.occupied().contains(slot) && !budget.bound().contains(slot));
    }

    private record ContainerSlotBudget(java.util.Set<Integer> occupied, java.util.Set<Integer> bound, long packedStacks,
                                       Map<String, Long> stockByKind) {
        long requiredSlots() { return occupied.size() + Math.max(packedStacks, bound.size()); }
        ContainerSlotBudget withIncoming(Map<String, Long> incoming) {
            if (incoming.isEmpty()) return this;
            Map<String, Long> projected = new HashMap<>(stockByKind);
            incoming.forEach((kind, quantity) -> projected.merge(kind, quantity, Math::addExact));
            long packed = projected.values().stream().mapToLong(quantity -> (quantity + 63) / 64).sum();
            return new ContainerSlotBudget(occupied, bound, packed, projected);
        }
    }

    /** Canonical capacity, not a claim that an unloaded chest has been physically observed. */
    public record ContainerCapacity(int slotCount, int exactSlots, int boundFungibleSlots,
                                    long packedFungibleSlots, int reservedSlots, long freeCapacitySlots,
                                    boolean reservationsValid) { }

    public ContainerCapacity containerCapacity(SubjectId containerId, Set<Integer> reservedSlots) {
        ContainerRecord container = containers.get(Objects.requireNonNull(containerId, "container id"));
        if (container == null) throw new IllegalArgumentException("unknown container: " + containerId.value());
        ContainerSlotBudget budget = slotBudget(containerId);
        boolean valid = validReservations(container, budget, reservedSlots);
        return new ContainerCapacity(container.slotCount(), budget.occupied().size(), budget.bound().size(),
                budget.packedStacks(), reservedSlots.size(), valid
                        ? Math.max(0L, container.slotCount() - budget.requiredSlots() - reservedSlots.size()) : 0L, valid);
    }

    private ContainerSlotBudget slotBudget(SubjectId containerId) {
        var occupied = new java.util.HashSet<Integer>();
        occupiedSlots.keySet().stream().filter(slot -> slot.containerId().equals(containerId))
                .map(InventoryCustody.ContainerSlot::slot).forEach(occupied::add);
        var bound = new java.util.HashSet<Integer>();
        fungibleResources.bindings().values().stream()
                .filter(binding -> binding.address() instanceof PhysicalStackAddress.ContainerSlot address
                        && address.slot().containerId().equals(containerId))
                .map(binding -> ((PhysicalStackAddress.ContainerSlot) binding.address()).slot().slot())
                .forEach(bound::add);
        Map<String, Long> stockByKind = new HashMap<>();
        fungibleResources.accounts().values().stream()
                .filter(account -> account.custody() instanceof ResourceCustody.Container location
                        && location.containerId().equals(containerId))
                .forEach(account -> account.lotQuantities().forEach((lotId, quantity) ->
                        stockByKind.merge(fungibleResources.lots().get(lotId).itemKind(), quantity.longValue(), Math::addExact)));
        long packedStacks = stockByKind.values().stream().mapToLong(quantity -> ((long) quantity + 63) / 64).sum();
        return new ContainerSlotBudget(occupied, bound, packedStacks, stockByKind);
    }

    /** A recipe replaces stored input; a full container alone does not forbid equal-size conversion. */
    public boolean canTransformFungible(SubjectId containerId, String inputKind, int inputCount,
                                        String outputKind, int outputCount, Set<Integer> reserved,
                                        Map<String, Long> inbound) {
        Objects.requireNonNull(inputKind); Objects.requireNonNull(outputKind); Objects.requireNonNull(inbound);
        if (inputCount <= 0 || outputCount <= 0) throw new IllegalArgumentException("invalid storage conversion");
        ContainerRecord container = Objects.requireNonNull(containers.get(containerId), "unknown container");
        long available = fungibleResources.accounts().values().stream()
                .filter(account -> account.custody() instanceof ResourceCustody.Container custody
                        && custody.containerId().equals(containerId))
                .flatMap(account -> account.lotQuantities().entrySet().stream())
                .filter(entry -> fungibleResources.lots().get(entry.getKey()).itemKind().equals(inputKind))
                .mapToLong(entry -> entry.getValue().longValue()).sum();
        if (available < inputCount) return false;
        var changes = new HashMap<>(inbound);
        changes.merge(inputKind, -(long) inputCount, Math::addExact);
        changes.merge(outputKind, (long) outputCount, Math::addExact);
        var budget = slotBudget(containerId);
        return validReservations(container, budget, reserved)
                && budget.withIncoming(changes).requiredSlots() + reserved.size() <= container.slotCount();
    }

    /** Capacity admission for an arrived fungible shipment, including partially filled stacks. */
    public boolean canReceiveFungibleCargo(SubjectId cargoId, SubjectId targetContainerId) {
        return canReceiveFungibleCargo(cargoId, targetContainerId, Set.of());
    }

    public boolean canReceiveFungibleCargo(SubjectId cargoId, SubjectId targetContainerId, Set<Integer> reservedSlots) {
        return canReceiveFungibleCargo(cargoId, targetContainerId, reservedSlots, Map.of());
    }
    public boolean canReceiveFungibleCargo(SubjectId cargoId, SubjectId targetContainerId, Set<Integer> reservedSlots,
                                           Map<String, Long> inbound) {
        CargoBatch batch = cargo.get(Objects.requireNonNull(cargoId, "fungible cargo id"));
        ContainerRecord target = containers.get(Objects.requireNonNull(targetContainerId, "target container id"));
        if (batch == null || !batch.fungibleContents() || target == null) {
            throw new IllegalArgumentException("fungible cargo capacity check has no target container");
        }
        CustodyAccount source = fungibleResources.accounts().values().stream()
                .filter(account -> account.custody() instanceof ResourceCustody.Cargo custody && custody.cargoId().equals(cargoId))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("fungible cargo account is absent"));
        Map<String, Long> incomingByKind = new HashMap<>(inbound);
        source.lotQuantities().forEach((lotId, quantity) -> incomingByKind.merge(
                fungibleResources.lots().get(lotId).itemKind(), quantity.longValue(), Math::addExact));
        var budget = slotBudget(targetContainerId);
        long before = budget.requiredSlots();
        long after = budget.withIncoming(incomingByKind).requiredSlots();
        return validReservations(target, budget, reservedSlots)
                && after + reservedSlots.size() <= Math.max(target.slotCount(), before + reservedSlots.size());
    }

    /** Pure capacity admission for a fungible actor batch entering one owned container. */
    public boolean canReceiveFungible(SubjectId targetContainerId, String itemKind, int quantity) {
        return canReceiveFungible(targetContainerId, itemKind, quantity, Set.of());
    }

    public boolean canReceiveFungible(SubjectId targetContainerId, String itemKind, int quantity, Set<Integer> reservedSlots) {
        return canReceiveFungible(targetContainerId, itemKind, quantity, reservedSlots, Map.of());
    }
    public boolean canReceiveFungible(SubjectId targetContainerId, String itemKind, int quantity,
                                      Set<Integer> reservedSlots, Map<String, Long> inbound) {
        ContainerRecord target = containers.get(Objects.requireNonNull(targetContainerId, "target container id"));
        Objects.requireNonNull(itemKind, "item kind");
        if (target == null || quantity <= 0) throw new IllegalArgumentException("invalid fungible container admission");
        var budget = slotBudget(targetContainerId);
        long before = budget.requiredSlots();
        var incoming = new HashMap<>(inbound); incoming.merge(itemKind, (long) quantity, Math::addExact);
        long after = budget.withIncoming(incoming).requiredSlots();
        return validReservations(target, budget, reservedSlots)
                && after + reservedSlots.size() <= Math.max(target.slotCount(), before + reservedSlots.size());
    }

    /** Restored old overcommit may be inspected and reduced, but no transition may make it worse. */
    private ExactInventory admitNoNewContainerOvercommit(ExactInventory next) {
        for (ContainerRecord container : containers.values()) {
            long before = slotBudget(container.id()).requiredSlots();
            long after = next.slotBudget(container.id()).requiredSlots();
            if (after > Math.max(container.slotCount(), before)) {
                throw new IllegalArgumentException("canonical container capacity would be overcommitted: "
                        + container.id().value() + " requires " + after + "/" + container.slotCount() + " slots");
            }
        }
        return next;
    }

    /** Lowest available semantic slot; callers must still validate the container's owner and use. */
    public OptionalInt firstFreeSlot(SubjectId containerId) {
        List<Integer> available = availableSlots(containerId);
        return available.isEmpty() ? OptionalInt.empty() : OptionalInt.of(available.getFirst());
    }

    /** Exact equipment/cargo retained by one actor; this is derived from single-source item custody. */
    public List<ExactItemStack> actorItems(SubjectId actorId) {
        InventoryCustody.Actor custody = new InventoryCustody.Actor(Objects.requireNonNull(actorId, "actor id"));
        return items.values().stream().filter(item -> item.custody().equals(custody)).sorted(java.util.Comparator.comparing(ExactItemStack::id)).toList();
    }

    /**
     * Atomically consumes one exact input stack and places one exact output stack. The output
     * identity must be new and its target slot must be vacant after the input is removed.
     */
    public ExactInventory consumeAndStore(SubjectId consumedItemId, ExactItemStack producedItem) {
        Objects.requireNonNull(consumedItemId, "consumed item id");
        Objects.requireNonNull(producedItem, "produced item");
        if (!items.containsKey(consumedItemId)) throw new IllegalArgumentException("consumed item is absent: " + consumedItemId.value());
        if (items.containsKey(producedItem.id())) throw new IllegalArgumentException("produced item identity already exists: " + producedItem.id().value());
        if (!(producedItem.custody() instanceof InventoryCustody.ContainerSlot)) throw new IllegalArgumentException("production output must enter a container slot");
        requireContainerClaim(producedItem);
        Map<SubjectId, ExactItemStack> nextItems = new HashMap<>(items);
        nextItems.remove(consumedItemId);
        nextItems.put(producedItem.id(), producedItem);
        return admitNoNewContainerOvercommit(new ExactInventory(containers, nextItems, cargo, playerItems, worldCarrierItems,
                conflicts, surfaces, economics, fungibleResources));
    }

    /** Removes one exact item only after the caller has recorded the durable process which owns it. */
    public ExactInventory withoutItem(SubjectId itemId) {
        Objects.requireNonNull(itemId, "item id");
        if (!items.containsKey(itemId)) throw new IllegalArgumentException("item is absent: " + itemId.value());
        Map<SubjectId, ExactItemStack> nextItems = new HashMap<>(items);
        nextItems.remove(itemId);
        return new ExactInventory(containers, nextItems, cargo, playerItems, worldCarrierItems, conflicts, surfaces, economics, fungibleResources);
    }

    /** Removes one exact stack only when its surviving canonical custody still matches physical evidence. */
    public ExactInventory destroyObservedItem(SubjectId itemId, InventoryCustody source) {
        Objects.requireNonNull(itemId, "item id"); Objects.requireNonNull(source, "item source");
        ExactItemStack current = items.get(itemId);
        if (current == null || !current.custody().equals(source)) throw new IllegalArgumentException("destroyed item source does not match canonical custody");
        Map<UUID, List<SubjectId>> nextPlayers = mutableCustody(playerItems);
        Map<UUID, List<SubjectId>> nextCarriers = mutableCustody(worldCarrierItems);
        removePlayerCustody(nextPlayers, source, itemId); removeCarrierCustody(nextCarriers, source, itemId);
        Map<SubjectId, ExactItemStack> nextItems = new HashMap<>(items); nextItems.remove(itemId);
        return new ExactInventory(containers, nextItems, cargo, nextPlayers, nextCarriers, conflicts, surfaces, economics, fungibleResources);
    }

    /** Records one real item consumed from a physical stack; the stack identity remains stable. */
    public ExactInventory consumeOne(SubjectId itemId) {
        ExactItemStack current = items.get(Objects.requireNonNull(itemId, "item id"));
        if (current == null) throw new IllegalArgumentException("consumed item is absent: " + itemId.value());
        if (!(current.custody() instanceof InventoryCustody.ContainerSlot)) {
            throw new IllegalArgumentException("repair material must remain in an owned container slot");
        }
        Map<SubjectId, ExactItemStack> nextItems = new HashMap<>(items);
        if (current.count() == 1) nextItems.remove(itemId);
        else nextItems.put(itemId, new ExactItemStack(current.id(), current.economicOwnerId(), current.itemKind(), current.count() - 1, current.custody()));
        return new ExactInventory(containers, nextItems, cargo, playerItems, worldCarrierItems, conflicts, surfaces, economics, fungibleResources);
    }

    /** Decrements one exact owned stack after a matching durable physical receipt. */
    public ExactInventory consume(SubjectId itemId, int count) {
        ExactItemStack current = items.get(Objects.requireNonNull(itemId, "item id"));
        if (current == null || count < 1 || count > current.count()) throw new IllegalArgumentException("exact consumption count does not match current stack");
        Map<SubjectId, ExactItemStack> nextItems = new HashMap<>(items);
        if (count == current.count()) nextItems.remove(itemId);
        else nextItems.put(itemId, new ExactItemStack(current.id(), current.economicOwnerId(), current.itemKind(), current.count() - count, current.custody()));
        return new ExactInventory(containers, nextItems, cargo, playerItems, worldCarrierItems, conflicts, surfaces, economics, fungibleResources);
    }

    /** Consumes one exact COLD cargo unit only through the owning work process. */
    public ExactInventory consumeCargoUnit(SubjectId cargoId, SubjectId itemId) {
        CargoBatch batch = cargo.get(Objects.requireNonNull(cargoId, "cargo id"));
        ExactItemStack current = items.get(Objects.requireNonNull(itemId, "item id"));
        if (batch == null || !batch.itemIds().equals(java.util.List.of(itemId)) || current == null
                || !current.custody().equals(new InventoryCustody.Cargo(cargoId))) {
            throw new IllegalArgumentException("construction material is not one exact work cargo");
        }
        Map<SubjectId, ExactItemStack> nextItems = new HashMap<>(items); Map<SubjectId, CargoBatch> nextCargo = new HashMap<>(cargo);
        if (current.count() == 1) { nextItems.remove(itemId); nextCargo.remove(cargoId); }
        else nextItems.put(itemId, new ExactItemStack(current.id(), current.economicOwnerId(), current.itemKind(), current.count() - 1, current.custody()));
        return new ExactInventory(containers, nextItems, nextCargo, playerItems, worldCarrierItems, conflicts, surfaces, economics, fungibleResources);
    }

    /** Splits one real owned-container unit into one new exact single-unit COLD cargo. */
    public ExactInventory extractOneToCargo(SubjectId sourceItemId, CargoBatch batch, SubjectId cargoItemId) {
        Objects.requireNonNull(batch, "cargo batch"); Objects.requireNonNull(cargoItemId, "cargo item id");
        ExactItemStack source = items.get(Objects.requireNonNull(sourceItemId, "source item id"));
        if (source == null || !(source.custody() instanceof InventoryCustody.ContainerSlot slot) || source.count() < 1
                || !batch.itemIds().equals(java.util.List.of(cargoItemId)) || cargo.containsKey(batch.id()) || items.containsKey(cargoItemId)) {
            throw new IllegalArgumentException("route construction extraction has invalid exact source or cargo identity");
        }
        ContainerRecord container = containers.get(slot.containerId());
        if (container == null || !container.ownerId().equals(batch.ownerId()) || !source.economicOwnerId().equals(batch.ownerId())) {
            throw new IllegalArgumentException("route construction extraction source is not owned by its cargo sender");
        }
        Map<SubjectId, ExactItemStack> nextItems = new HashMap<>(items);
        if (source.count() == 1) nextItems.remove(sourceItemId);
        else nextItems.put(sourceItemId, new ExactItemStack(source.id(), source.economicOwnerId(), source.itemKind(), source.count() - 1, source.custody()));
        nextItems.put(cargoItemId, new ExactItemStack(cargoItemId, source.economicOwnerId(), source.itemKind(), 1, new InventoryCustody.Cargo(batch.id())));
        Map<SubjectId, CargoBatch> nextCargo = new HashMap<>(cargo); nextCargo.put(batch.id(), batch);
        return new ExactInventory(containers, nextItems, nextCargo, playerItems, worldCarrierItems, conflicts, surfaces, economics, fungibleResources);
    }

    /** Stores a new exact stack only in an actual currently-free owned container slot. */
    public ExactInventory store(ExactItemStack item) {
        Objects.requireNonNull(item, "item");
        if (items.containsKey(item.id())) throw new IllegalArgumentException("stored item identity already exists: " + item.id().value());
        if (!(item.custody() instanceof InventoryCustody.ContainerSlot slot)) throw new IllegalArgumentException("stored item must enter a container slot");
        requireContainerClaim(item);
        if (!slotVacant(slot)) throw new IllegalArgumentException("stored item has no available physical container slot");
        Map<SubjectId, ExactItemStack> nextItems = new HashMap<>(items);
        nextItems.put(item.id(), item);
        return new ExactInventory(containers, nextItems, cargo, playerItems, worldCarrierItems, conflicts, surfaces, economics, fungibleResources);
    }

    /**
     * Moves a complete named set of warehouse stacks into one new identified cargo batch. The
     * stacks keep their identities and counts; only their one canonical custody changes.
     */
    public ExactInventory loadCargo(CargoBatch batch) {
        Objects.requireNonNull(batch, "cargo batch");
        if (cargo.containsKey(batch.id())) throw new IllegalArgumentException("cargo identity already exists: " + batch.id().value());
        Map<SubjectId, ExactItemStack> nextItems = new HashMap<>(items);
        for (SubjectId itemId : batch.itemIds()) {
            ExactItemStack item = nextItems.get(itemId);
            if (item == null) throw new IllegalArgumentException("cargo item is absent: " + itemId.value());
            if (!(item.custody() instanceof InventoryCustody.ContainerSlot)) {
                throw new IllegalArgumentException("cargo item is not in a warehouse slot: " + itemId.value());
            }
            InventoryCustody.ContainerSlot slot = (InventoryCustody.ContainerSlot) item.custody();
            ContainerRecord container = containers.get(slot.containerId());
            if (container == null || !container.ownerId().equals(batch.ownerId())) {
                throw new IllegalArgumentException("cargo item is not owned by the cargo sender: " + itemId.value());
            }
            if (!item.economicOwnerId().equals(batch.ownerId())) throw new IllegalArgumentException("cargo item claim does not belong to its sender");
            nextItems.put(itemId, new ExactItemStack(item.id(), item.economicOwnerId(), item.itemKind(), item.count(), new InventoryCustody.Cargo(batch.id())));
        }
        Map<SubjectId, CargoBatch> nextCargo = new HashMap<>(cargo);
        nextCargo.put(batch.id(), batch);
        return new ExactInventory(containers, nextItems, nextCargo, playerItems, worldCarrierItems, conflicts, surfaces, economics, fungibleResources);
    }

    /** Moves an exact COLD resource portion into its stable cargo batch without creating stack identities. */
    public ExactInventory loadFungibleCargo(CargoBatch batch, SubjectId sourceAccountId, Map<SubjectId, Integer> lotQuantities,
                                            Map<SubjectId, Integer> claimQuantities) {
        Objects.requireNonNull(batch, "fungible cargo batch");
        if (!batch.fungibleContents() || cargo.containsKey(batch.id())) throw new IllegalArgumentException("fungible cargo identity is unavailable");
        CustodyAccount destination = new CustodyAccount(new SubjectId("custody:" + batch.id().value().replace(':', '-')),
                new ResourceCustody.Cargo(batch.id()), lotQuantities, claimQuantities);
        FungibleResourceLedger resources = fungibleResources.transferToNewAccount(sourceAccountId, destination);
        Map<SubjectId, CargoBatch> nextCargo = new HashMap<>(cargo); nextCargo.put(batch.id(), batch);
        return new ExactInventory(containers, items, nextCargo, playerItems, worldCarrierItems, conflicts, surfaces, economics, resources);
    }

    /** Moves an observed HOT source portion into COLD cargo without ever releasing its source binding. */
    public ExactInventory loadObservedFungibleCargo(CargoBatch batch, SubjectId sourceAccountId, long sourceEpoch,
                                                    Map<SubjectId, Integer> lotQuantities, Map<SubjectId, Integer> claimQuantities,
                                                    List<FungiblePhysicalObservation.Stack> remainingStacks) {
        Objects.requireNonNull(batch, "observed fungible cargo batch");
        if (!batch.fungibleContents() || cargo.containsKey(batch.id())) throw new IllegalArgumentException("observed fungible cargo identity is unavailable");
        CustodyAccount destination = new CustodyAccount(new SubjectId("custody:" + batch.id().value().replace(':', '-')),
                new ResourceCustody.Cargo(batch.id()), lotQuantities, claimQuantities);
        List<PhysicalStackBinding> remaining = remainingStacks.isEmpty() ? List.of()
                : FungiblePhysicalObservation.bind(fungibleResources, sourceAccountId, sourceEpoch, remainingStacks);
        FungibleResourceLedger resources = fungibleResources.transferObservedToColdNewAccount(sourceAccountId, destination, sourceEpoch,
                lotQuantities, claimQuantities, remaining);
        Map<SubjectId, CargoBatch> nextCargo = new HashMap<>(cargo); nextCargo.put(batch.id(), batch);
        return new ExactInventory(containers, items, nextCargo, playerItems, worldCarrierItems, conflicts, surfaces, economics, resources);
    }

    /** Atomically retains a contract claim while moving its COLD lot portion into identified cargo. */
    public ExactInventory reserveAndLoadFungibleCargo(CargoBatch batch, SubjectId sourceAccountId, ResourceLot lot, ClaimAllocation claim) {
        Objects.requireNonNull(lot, "cargo lot"); Objects.requireNonNull(claim, "cargo claim");
        if (!batch.fungibleContents() || cargo.containsKey(batch.id()) || !lot.itemKind().equals(claim.itemKind())
                || lot.quantity() < claim.quantity()) {
            throw new IllegalArgumentException("fungible cargo reservation is invalid");
        }
        FungibleResourceLedger resources = fungibleResources;
        ResourceLot cargoLot = lot;
        if (lot.quantity() > claim.quantity()) {
            SubjectId childId = new SubjectId("lot:cargo-" + batch.id().value().replace(':', '-') + "-portion");
            cargoLot = lot.splitChild(childId, claim.quantity());
            resources = resources.split(sourceAccountId, lot.id(), cargoLot, claim.quantity());
        }
        Map<SubjectId, Integer> quantities = Map.of(cargoLot.id(), claim.quantity());
        CustodyAccount destination = new CustodyAccount(new SubjectId("custody:" + batch.id().value().replace(':', '-')),
                new ResourceCustody.Cargo(batch.id()), quantities, Map.of(claim.id(), claim.quantity()));
        resources = resources.reserveThenTransferToNewAccount(claim, sourceAccountId, destination);
        Map<SubjectId, CargoBatch> nextCargo = new HashMap<>(cargo); nextCargo.put(batch.id(), batch);
        return new ExactInventory(containers, items, nextCargo, playerItems, worldCarrierItems, conflicts, surfaces, economics, resources);
    }

    /** Moves every exact cargo item into observed receiver slots and removes the completed batch. */

    /** Exact and fungible custody share one world-carrier ownership predicate. */
    public boolean hasWorldCarrierCustody(UUID carrierId) {
        Objects.requireNonNull(carrierId, "world carrier identity");
        var exact = worldCarrierItems.get(carrierId);
        return exact != null && !exact.isEmpty() || fungibleResources.accounts().values().stream()
                .anyMatch(account -> account.custody().equals(new ResourceCustody.WorldCarrier(carrierId))
                        && !account.lotQuantities().isEmpty());
    }

    /** Delivers a COLD fungible cargo account into one existing or fresh owned container account. */
    public ExactInventory completeFungibleCargoHandoff(SubjectId cargoId, SubjectId targetContainerId) {
        CargoBatch batch = cargo.get(Objects.requireNonNull(cargoId, "fungible cargo id")); ContainerRecord target = containers.get(targetContainerId);
        if (batch == null || !batch.fungibleContents() || target == null) {
            throw new IllegalArgumentException("fungible cargo handoff has no target container");
        }
        CustodyAccount source = fungibleResources.accounts().values().stream().filter(account -> account.custody() instanceof ResourceCustody.Cargo custody
                && custody.cargoId().equals(cargoId)).findFirst().orElseThrow(() -> new IllegalArgumentException("fungible cargo account is absent"));
        CustodyAccount targetAccount = fungibleResources.accounts().values().stream().filter(account -> account.custody() instanceof ResourceCustody.Container custody
                && custody.containerId().equals(targetContainerId)).findFirst().orElse(null);
        CustodyAccount receiving = targetAccount == null
                ? new CustodyAccount(new SubjectId("custody:" + targetContainerId.value().replace(':', '-')), new ResourceCustody.Container(targetContainerId),
                source.lotQuantities(), Map.of()) : targetAccount;
        FungibleResourceLedger resources = fungibleResources.deliverCargoToContainer(source.id(), receiving, target.ownerId());
        Map<SubjectId, CargoBatch> nextCargo = new HashMap<>(cargo); nextCargo.remove(cargoId);
        return admitNoNewContainerOvercommit(new ExactInventory(containers, items, nextCargo, playerItems,
                worldCarrierItems, conflicts, surfaces, economics, resources));
    }

    /** Commits an observed HOT cargo arrival and its complete target-stack layout as one transaction. */
    public ExactInventory completeObservedFungibleCargoHandoff(SubjectId cargoId, SubjectId targetContainerId, long authorityEpoch,
                                                                List<FungiblePhysicalObservation.Stack> observedStacks) {
        CargoBatch batch = cargo.get(Objects.requireNonNull(cargoId, "observed fungible cargo id")); ContainerRecord target = containers.get(targetContainerId);
        if (batch == null || !batch.fungibleContents() || target == null || authorityEpoch < 1) {
            throw new IllegalArgumentException("observed fungible cargo handoff has no target");
        }
        CustodyAccount source = fungibleResources.accounts().values().stream().filter(account -> account.custody() instanceof ResourceCustody.Cargo custody
                && custody.cargoId().equals(cargoId)).findFirst().orElseThrow(() -> new IllegalArgumentException("observed fungible cargo account is absent"));
        if (fungibleResources.bindings().values().stream().anyMatch(binding -> binding.accountId().equals(source.id()))) {
            throw new IllegalArgumentException("observed fungible cargo cannot bypass a live source binding");
        }
        CustodyAccount targetAccount = fungibleResources.accounts().values().stream().filter(account -> account.custody() instanceof ResourceCustody.Container custody
                && custody.containerId().equals(targetContainerId)).findFirst().orElse(null);
        CustodyAccount receiving = targetAccount == null
                ? new CustodyAccount(new SubjectId("custody:" + targetContainerId.value().replace(':', '-')), new ResourceCustody.Container(targetContainerId),
                source.lotQuantities(), Map.of()) : targetAccount;
        SubjectId receivingAccount = receiving.id();
        FungibleResourceLedger resources;
        if (targetAccount == null) {
            resources = fungibleResources.deliverCargoToContainer(source.id(), receiving, target.ownerId());
            resources = resources.rebind(receivingAccount, authorityEpoch, FungiblePhysicalObservation.bind(resources, receivingAccount, authorityEpoch, observedStacks));
        } else {
            resources = fungibleResources.deliverObservedCargoToBoundContainer(source.id(), receivingAccount, target.ownerId(), authorityEpoch, observedStacks);
        }
        Map<SubjectId, CargoBatch> nextCargo = new HashMap<>(cargo); nextCargo.remove(cargoId);
        return admitNoNewContainerOvercommit(new ExactInventory(containers, items, nextCargo, playerItems,
                worldCarrierItems, conflicts, surfaces, economics, resources));
    }

    /**
     * Releases one complete shipment to a single observed physical carrier. This is deliberately
     * all-or-nothing: individual stacks may leave only after the batch no longer claims custody.
     */
    public ExactInventory releaseCargoToWorldCarrier(SubjectId cargoId, UUID carrierId) {
        CargoBatch batch = cargo.get(Objects.requireNonNull(cargoId, "cargo id"));
        Objects.requireNonNull(carrierId, "carrier id");
        if (batch == null) throw new IllegalArgumentException("released cargo is absent: " + cargoId.value());
        if (batch.fungibleContents()) return releaseFungibleCargoToWorldCarrier(batch, carrierId);
        if (worldCarrierItems.containsKey(carrierId)) throw new IllegalArgumentException("released cargo carrier identity is already owned");
        Map<SubjectId, ExactItemStack> nextItems = new HashMap<>(items);
        for (SubjectId itemId : batch.itemIds()) {
            ExactItemStack item = nextItems.get(itemId);
            if (item == null || !item.custody().equals(new InventoryCustody.Cargo(cargoId))) {
                throw new IllegalArgumentException("released item is not in its exact cargo batch: " + itemId.value());
            }
            nextItems.put(item.id(), new ExactItemStack(item.id(), item.economicOwnerId(), item.itemKind(), item.count(), new InventoryCustody.WorldCarrier(carrierId)));
        }
        Map<SubjectId, CargoBatch> nextCargo = new HashMap<>(cargo); nextCargo.remove(cargoId);
        Map<UUID, List<SubjectId>> nextCarriers = mutableCustody(worldCarrierItems);
        nextCarriers.put(carrierId, new java.util.ArrayList<>(batch.itemIds()));
        return new ExactInventory(containers, nextItems, nextCargo, playerItems, nextCarriers, conflicts, surfaces, economics, fungibleResources);
    }

    /**
     * Turns one already-materialized fungible shipment into the single HOT world-carrier
     * account. The release event is the physical boundary: it is allowed only when one
     * ordinary Vanilla stack can represent the complete batch, so it never fabricates a
     * second stock counter or leaves a COLD-spendable cargo account behind.
     */
    private ExactInventory releaseFungibleCargoToWorldCarrier(CargoBatch batch, UUID carrierId) {
        SubjectId carrierAccountId = new SubjectId("custody:world-carrier-" + carrierId);
        CustodyAccount cargoAccount = fungibleResources.accounts().values().stream()
                .filter(account -> account.custody() instanceof ResourceCustody.Cargo custody && custody.cargoId().equals(batch.id()))
                .reduce((left, right) -> { throw new IllegalArgumentException("released fungible cargo has ambiguous cargo accounts"); })
                .orElse(null);
        if (cargoAccount == null || !(cargoAccount.custody() instanceof ResourceCustody.Cargo custody)
                || !custody.cargoId().equals(batch.id()) || fungibleResources.accounts().containsKey(carrierAccountId)) {
            throw new IllegalArgumentException("released fungible cargo has no sole current cargo account");
        }
        int quantity = cargoAccount.lotQuantities().values().stream().mapToInt(Integer::intValue).sum();
        String kind = cargoAccount.lotQuantities().keySet().stream().map(fungibleResources.lots()::get)
                .map(ResourceLot::itemKind).distinct().reduce((left, right) -> {
                    throw new IllegalArgumentException("released fungible cargo has mixed physical kinds");
                }).orElseThrow(() -> new IllegalArgumentException("released fungible cargo has no physical lot"));
        if (quantity > 64) throw new IllegalArgumentException("released fungible cargo exceeds one physical carrier stack");
        CustodyAccount carrier = new CustodyAccount(carrierAccountId, new ResourceCustody.WorldCarrier(carrierId),
                cargoAccount.lotQuantities(), cargoAccount.claimQuantities());
        FungibleResourceLedger transferred = fungibleResources.transferToNewAccount(cargoAccount.id(), carrier);
        List<FungiblePhysicalObservation.Stack> observed = List.of(new FungiblePhysicalObservation.Stack(
                new PhysicalStackAddress.WorldEntity(carrierId), kind, quantity));
        List<PhysicalStackBinding> bindings = FungiblePhysicalObservation.bind(transferred, carrierAccountId, 1L, observed);
        transferred = transferred.rebind(carrierAccountId, 1L, bindings);
        Map<SubjectId, CargoBatch> nextCargo = new HashMap<>(cargo); nextCargo.remove(batch.id());
        return new ExactInventory(containers, items, nextCargo, playerItems, worldCarrierItems, conflicts, surfaces, economics, transferred);
    }

    /** Applies one observed trusted-surface transfer only when its exact source still agrees. */
    public ExactInventory moveObservedItem(SubjectId itemId, InventoryCustody from, InventoryCustody to) {
        Objects.requireNonNull(itemId, "item id"); Objects.requireNonNull(from, "source custody"); Objects.requireNonNull(to, "target custody");
        ExactItemStack current = items.get(itemId);
        if (current == null || !current.custody().equals(from)) throw new IllegalArgumentException("observed item source does not match canonical custody");
        if (to instanceof InventoryCustody.ContainerSlot target) {
            ContainerRecord container = containers.get(target.containerId());
            if (container == null || target.slot() >= container.slotCount() || itemAt(target.containerId(), target.slot()).isPresent()) {
                throw new IllegalArgumentException("observed container target is unavailable");
            }
        }
        Map<UUID, List<SubjectId>> nextPlayers = mutableCustody(playerItems);
        removePlayerCustody(nextPlayers, from, itemId);
        addPlayerCustody(nextPlayers, to, itemId);
        Map<UUID, List<SubjectId>> nextCarriers = mutableCustody(worldCarrierItems);
        removeCarrierCustody(nextCarriers, from, itemId);
        addCarrierCustody(nextCarriers, to, itemId);
        Map<SubjectId, ExactItemStack> nextItems = new HashMap<>(items);
        SubjectId nextOwner = to instanceof InventoryCustody.ContainerSlot target ? containers.get(target.containerId()).ownerId() : current.economicOwnerId();
        nextItems.put(itemId, new ExactItemStack(current.id(), nextOwner, current.itemKind(), current.count(), to));
        return admitNoNewContainerOvercommit(new ExactInventory(containers, nextItems, cargo, nextPlayers,
                nextCarriers, conflicts, surfaces, economics, fungibleResources));
    }

    /**
     * Applies the exact-custody part of an actor item order after arrival and, in HOT,
     * after the physical executor has confirmed both endpoint observations. This method
     * does not itself authorize a Minecraft effect or prove that a body reached the station.
     */
    public ExactInventory transferActorOrder(ActorContainerItemOrder order) {
        Objects.requireNonNull(order, "actor item order");
        if (order.containerEndpoint() instanceof ActorContainerItemOrder.ContainerEndpoint.ExactStationSlot)
            order.requireCurrentStation(this);
        if (!(order.portion() instanceof ActorContainerItemOrder.Portion.Exact exact)) {
            throw new IllegalArgumentException("resource-lot order belongs to the fungible ledger");
        }
        ExactItemStack current = items.get(exact.item().id());
        if (current == null || !current.equals(exact.item())) {
            throw new IllegalArgumentException("actor item order no longer matches its exact canonical stack");
        }
        InventoryCustody.Actor actor = new InventoryCustody.Actor(order.actorId());
        InventoryCustody.ContainerSlot slot = order.exactSlot();
        if (order.direction() == ActorContainerItemOrder.Direction.TAKE) {
            if (items.values().stream().anyMatch(item -> item.custody().equals(actor))) {
                throw new IllegalArgumentException("actor already carries a stack");
            }
            return moveObservedItem(current.id(), slot, actor);
        }
        ContainerRecord destination = containers.get(slot.containerId());
        if (destination == null || !destination.ownerId().equals(current.economicOwnerId())) {
            throw new IllegalArgumentException("actor may not silently transfer economic ownership on deposit");
        }
        return moveObservedItem(current.id(), actor, slot);
    }

    public ExactInventory recordConflict(InventoryConflict conflict) {
        Objects.requireNonNull(conflict, "inventory conflict");
        InventoryConflict previous = conflicts.get(conflict.id());
        if (conflict.equals(previous)) return this;
        if (previous != null) throw new IllegalArgumentException("inventory conflict identity cannot change its evidence");
        Map<SubjectId, InventoryConflict> next = new HashMap<>(conflicts);
        if (next.size() >= MAX_CONFLICTS) throw new IllegalArgumentException("inventory conflict retention limit exceeded");
        next.put(conflict.id(), conflict);
        return new ExactInventory(containers, items, cargo, playerItems, worldCarrierItems, next, surfaces, economics, fungibleResources);
    }
    public ExactInventory withSurfaceStatus(SubjectId containerId, ContainerSurfaceStatus status) {
        ContainerSurface current = surfaces.get(Objects.requireNonNull(containerId, "container id"));
        if (current == null) throw new IllegalArgumentException("container has no physical surface: " + containerId.value());
        Map<SubjectId, ContainerSurface> next = new HashMap<>(surfaces); next.put(containerId, current.transitionTo(status));
        return new ExactInventory(containers, items, cargo, playerItems, worldCarrierItems, conflicts, next, economics, fungibleResources);
    }

    /** Replaces only the canonical financial ledger; exact item custody/claims remain unchanged. */
    public ExactInventory withEconomics(EconomicLedger nextEconomics) {
        return new ExactInventory(containers, items, cargo, playerItems, worldCarrierItems, conflicts, surfaces,
                Objects.requireNonNull(nextEconomics, "economic ledger"), fungibleResources);
    }

    /** Replaces the one canonical fungible-resource ledger without altering stable equipment/cargo identities. */
    public ExactInventory withFungibleResources(FungibleResourceLedger nextResources) {
        return admitNoNewContainerOvercommit(new ExactInventory(containers, items, cargo, playerItems,
                worldCarrierItems, conflicts, surfaces, economics,
                Objects.requireNonNull(nextResources, "fungible resource ledger")));
    }

    private static Map<UUID, List<SubjectId>> mutableCustody(Map<UUID, List<SubjectId>> values) {
        Map<UUID, List<SubjectId>> mutable = new HashMap<>();
        values.forEach((player, itemIds) -> mutable.put(player, new java.util.ArrayList<>(itemIds)));
        return mutable;
    }
    private static void removePlayerCustody(Map<UUID, List<SubjectId>> players, InventoryCustody custody, SubjectId itemId) {
        if (custody instanceof InventoryCustody.Player player) {
            List<SubjectId> held = players.get(player.playerId());
            if (held == null || !held.remove(itemId)) throw new IllegalArgumentException("observed player source is unavailable");
            if (held.isEmpty()) players.remove(player.playerId());
        }
    }
    private static void addPlayerCustody(Map<UUID, List<SubjectId>> players, InventoryCustody custody, SubjectId itemId) {
        if (custody instanceof InventoryCustody.Player player) {
            players.computeIfAbsent(player.playerId(), ignored -> new java.util.ArrayList<>()).add(itemId);
        }
    }
    private static void removeCarrierCustody(Map<UUID, List<SubjectId>> carriers, InventoryCustody custody, SubjectId itemId) {
        if (custody instanceof InventoryCustody.WorldCarrier carrier) {
            List<SubjectId> held = carriers.get(carrier.carrierId());
            if (held == null || !held.remove(itemId)) throw new IllegalArgumentException("observed world carrier source is unavailable");
            if (held.isEmpty()) carriers.remove(carrier.carrierId());
        }
    }
    private static void addCarrierCustody(Map<UUID, List<SubjectId>> carriers, InventoryCustody custody, SubjectId itemId) {
        if (custody instanceof InventoryCustody.WorldCarrier carrier) {
            carriers.computeIfAbsent(carrier.carrierId(), ignored -> new java.util.ArrayList<>()).add(itemId);
        }
    }
    private static void require(Object value, Object expected, String label) {
        if (value instanceof ExactItemStack item && item.custody().equals(expected)) return;
        throw new IllegalArgumentException("dangling or conflicting " + label);
    }
    private static void requireDistinct(List<SubjectId> values, String label) {
        if (values.stream().distinct().count() != values.size()) throw new IllegalArgumentException(label + " must contain distinct exact item identities");
    }
    private static Map<InventoryCustody.ContainerSlot, SubjectId> occupiedSlots(Map<SubjectId, ExactItemStack> items) {
        Map<InventoryCustody.ContainerSlot, SubjectId> result = new HashMap<>();
        for (ExactItemStack item : items.values()) if (item.custody() instanceof InventoryCustody.ContainerSlot slot) {
            result.put(slot, item.id());
        }
        return result;
    }
    private void requireContainerClaim(ExactItemStack item) {
        InventoryCustody.ContainerSlot slot = (InventoryCustody.ContainerSlot) item.custody();
        ContainerRecord container = containers.get(slot.containerId());
        if (container == null || slot.slot() >= container.slotCount() || !container.ownerId().equals(item.economicOwnerId())) {
            throw new IllegalArgumentException("stored item claim must belong to its target container owner");
        }
    }

    private static void validateFungibleResourceCustody(Map<SubjectId, ContainerRecord> containers,
                                                        Map<SubjectId, CargoBatch> cargo,
                                                        Map<InventoryCustody.ContainerSlot, SubjectId> occupiedSlots,
                                                        FungibleResourceLedger fungibleResources) {
        Map<ResourceCustody, SubjectId> accountsByCustody = new HashMap<>();
        for (CustodyAccount account : fungibleResources.accounts().values()) {
            if (!(account.custody() instanceof ResourceCustody.Actor)
                    && accountsByCustody.put(account.custody(), account.id()) != null) {
                throw new IllegalArgumentException("one physical/resource location must have one fungible custody account");
            }
            switch (account.custody()) {
                case ResourceCustody.Container container -> {
                    ContainerRecord record = containers.get(container.containerId());
                    if (record == null) throw new IllegalArgumentException("fungible account references an unknown container");
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
            }
        }
        for (CargoBatch batch : cargo.values()) if (batch.fungibleContents()) {
            long matching = fungibleResources.accounts().values().stream().filter(account -> account.custody() instanceof ResourceCustody.Cargo custody
                    && custody.cargoId().equals(batch.id())).count();
            if (matching != 1) throw new IllegalArgumentException("fungible cargo must retain one exact custody account");
        }
        for (PhysicalStackBinding binding : fungibleResources.bindings().values()) {
            CustodyAccount account = fungibleResources.accounts().get(binding.accountId());
            if (binding.address() instanceof PhysicalStackAddress.ContainerSlot slot) {
                ContainerRecord record = containers.get(slot.slot().containerId());
                if (record == null || slot.slot().slot() >= record.slotCount()
                        || !(account.custody() instanceof ResourceCustody.Container container)
                        || !container.containerId().equals(slot.slot().containerId())) {
                    throw new IllegalArgumentException("fungible physical binding has no matching owned container account");
                }
                if (occupiedSlots.containsKey(slot.slot())) {
                    throw new IllegalArgumentException("fungible physical binding overlaps an exact-item slot");
                }
            }
        }
    }
    public ExactInventory completeCargoHandoff(SubjectId cargoId, List<CargoHandoffPlacement> placements) {
        CargoBatch batch = cargo.get(Objects.requireNonNull(cargoId, "cargo id"));
        if (batch == null) throw new IllegalArgumentException("completed cargo is absent: " + cargoId.value());
        List<SubjectId> observedItems = placements.stream().map(CargoHandoffPlacement::itemId).sorted().toList();
        if (!observedItems.equals(batch.itemIds().stream().sorted().toList())) {
            throw new IllegalArgumentException("cargo hand-off observation does not place every exact cargo item");
        }
        Map<SubjectId, ExactItemStack> nextItems = new HashMap<>(items);
        for (CargoHandoffPlacement placement : placements) {
            ExactItemStack item = nextItems.get(placement.itemId());
            if (item == null || !item.custody().equals(new InventoryCustody.Cargo(cargoId))) {
                throw new IllegalArgumentException("handoff item is not in its exact cargo batch: " + placement.itemId().value());
            }
            ContainerRecord receiver = containers.get(placement.receiverSlot().containerId());
            if (receiver == null) throw new IllegalArgumentException("handoff receiver container is unknown");
            nextItems.put(item.id(), new ExactItemStack(item.id(), receiver.ownerId(), item.itemKind(), item.count(), placement.receiverSlot()));
        }
        Map<SubjectId, CargoBatch> nextCargo = new HashMap<>(cargo);
        nextCargo.remove(cargoId);
        return admitNoNewContainerOvercommit(new ExactInventory(containers, nextItems, nextCargo, playerItems,
                worldCarrierItems, conflicts, surfaces, economics, fungibleResources));
    }
}
