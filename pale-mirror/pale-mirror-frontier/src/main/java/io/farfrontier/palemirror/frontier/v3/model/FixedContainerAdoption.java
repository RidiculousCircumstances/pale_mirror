package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Observed fixed inventory admission. Does not choose a territorial owner, machine capability or storage policy. */
public final class FixedContainerAdoption {
    public static ExactInventory adopt(ExactInventory inventory, ContainerRecord container, BlockPosition position, List<ResourceLot> contents) {
        var holders = new HashMap<SubjectId, ContainerSectionId>();
        contents.forEach(lot -> holders.put(lot.id(), ContainerSectionId.STOCK));
        return adoptDeclared(inventory, container, position, contents, holders);
    }
    public record PlacedResource(int slot, ResourceLot lot) {
        public PlacedResource { Objects.requireNonNull(lot); if (slot < 0 || slot >= 54) throw new IllegalArgumentException("invalid admitted resource slot"); }
    }
    public static ExactInventory adoptPlaced(ExactInventory inventory, ContainerRecord container, BlockPosition position, List<PlacedResource> contents) {
        var holders = new HashMap<SubjectId, ContainerSectionId>();
        var slots = new HashSet<Integer>();
        for (var value : contents) {
            if (!slots.add(value.slot()) || holders.put(value.lot().id(), container.compartmentLayout().sectionAt(value.slot())) != null)
                throw new IllegalArgumentException("adoption repeats a physical slot or lot identity");
            var observed = container.fungibleLayout().slots().get(value.slot());
            if (observed == null || !observed.itemKind().equals(value.lot().itemKind()) || observed.quantity() != value.lot().quantity())
                throw new IllegalArgumentException("placed resource differs from its declared observed slot");
        }
        if (!slots.equals(container.fungibleLayout().slots().keySet())) throw new IllegalArgumentException("adoption omits a witnessed material slot");
        return adoptDeclared(inventory, container, position, contents.stream().map(PlacedResource::lot).toList(), holders);
    }
    private static ExactInventory adoptDeclared(ExactInventory inventory, ContainerRecord container, BlockPosition position,
            List<ResourceLot> contents, Map<SubjectId, ContainerSectionId> holders) {
        if (inventory.containers().size() >= 4096 || inventory.containers().containsKey(container.id())
                || inventory.surfaces().values().stream().filter(ContainerSurface::fixed)
                    .filter(surface -> surface.status() != ContainerSurfaceStatus.RETIRED).anyMatch(surface -> surface.position().equals(position))
                || contents.size() > container.slotCount() || contents.stream().map(ResourceLot::id).distinct().count() != contents.size()
                || contents.stream().anyMatch(lot -> !lot.economicOwnerId().equals(container.ownerId()) || !lot.lineage().isEmpty() || lot.quantity() > 64))
            throw new IllegalArgumentException("fixed adoption has occupied identity/socket, excessive stock or foreign title");
        var ledger = inventory.fungibleResources();
        var records = new LinkedHashMap<>(inventory.containers()); records.put(container.id(), container);
        var surfaces = new LinkedHashMap<>(inventory.surfaces());
        surfaces.put(container.id(), new ContainerSurface(container.id(), position, ContainerSurfaceStatus.ADOPTING));
        var lots = new LinkedHashMap<>(ledger.lots()); var accounts = new LinkedHashMap<>(ledger.accounts());
        var sections = new TreeMap<ContainerSectionId, Map<SubjectId, Integer>>();
        for (var lot : contents) {
            if (lots.putIfAbsent(lot.id(), lot) != null) throw new IllegalArgumentException("adoption cannot reissue an existing lot");
            var section = Objects.requireNonNull(holders.get(lot.id()), "admitted resource compartment");
            container.compartmentLayout().slots(section);
            sections.computeIfAbsent(section, ignored -> new LinkedHashMap<>()).put(lot.id(), lot.quantity());
        }
        for (var entry : sections.entrySet()) {
            var holder = new ResourceCustody.Container(container.id(), entry.getKey()); var id = ContainerResourceAccounts.id(holder);
            if (accounts.putIfAbsent(id, new CustodyAccount(id, holder, entry.getValue(), Map.of())) != null)
                throw new IllegalArgumentException("adoption account is already owned");
        }
        return new ExactInventory(records, inventory.items(), inventory.cargo(), inventory.playerItems(), inventory.worldCarrierItems(),
                inventory.conflicts(), surfaces, inventory.economics(), new FungibleResourceLedger(lots, ledger.claims(), accounts, ledger.bindings()));
    }
    public static FencedRecoveryState prepare(FencedRecoveryState recovery, ContainerRecord container) {
        var binding = FencedRecoveryContainerSupport.bindingId(container);
        return recovery.prepare(FencedRecoveryBinding.prepared(binding, FencedRecoveryAsset.CONTAINER, container.ownerId(), 0L,
                recovery.nextEpoch(binding), true));
    }
    private FixedContainerAdoption() { }
}
