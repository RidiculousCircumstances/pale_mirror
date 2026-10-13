package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Observed fixed inventory admission. Does not choose a territorial owner, machine capability or storage policy. */
public final class FixedContainerAdoption {
    public static ExactInventory adopt(ExactInventory inventory, ContainerRecord container, BlockPosition position, List<ResourceLot> contents) {
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
        var quantities = new LinkedHashMap<SubjectId, Integer>();
        for (var lot : contents) {
            if (lots.putIfAbsent(lot.id(), lot) != null) throw new IllegalArgumentException("adoption cannot reissue an existing lot");
            quantities.put(lot.id(), lot.quantity());
        }
        if (!quantities.isEmpty()) {
            var id = ReferenceContainerCustody.scopeId(container.id());
            if (accounts.putIfAbsent(id, new CustodyAccount(id, new ResourceCustody.Container(container.id()), quantities, Map.of())) != null)
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
