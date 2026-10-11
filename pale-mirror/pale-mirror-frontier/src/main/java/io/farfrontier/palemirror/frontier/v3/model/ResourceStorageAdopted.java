package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import java.util.*;

/** Admission of an existing physical chest. No projection may manufacture its observed contents. */
public record ResourceStorageAdopted(ResourceStorageDeclared declaration, List<ResourceLot> contents)
        implements FrontierPayload {
    public ResourceStorageAdopted {
        Objects.requireNonNull(declaration);
        contents = List.copyOf(contents);
        if (contents.size() > declaration.container().slotCount()
                || contents.stream().map(ResourceLot::id).distinct().count() != contents.size()
                || contents.stream().anyMatch(lot -> !lot.economicOwnerId().equals(declaration.container().ownerId())
                    || !lot.lineage().isEmpty() || lot.quantity() > 64))
            throw new IllegalArgumentException("adoption requires bounded newly observed stock with explicit title");
    }
    @Override public String type() { return "frontier.resource_storage_adopted"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
    public FrontierWorldState apply(FrontierWorldState state, SubjectId owner) {
        if (!SettlementStorageTerritory.owner(state.bootstrap(), declaration.position()).map(Settlement::id)
                .filter(owner::equals).isPresent())
            throw new IllegalArgumentException("default storage admission lacks one exact territorial owner");
        var declared = declaration.apply(state, owner);
        var inventory = declared.inventory(); var ledger = inventory.fungibleResources();
        var lots = new LinkedHashMap<>(ledger.lots()); var accounts = new LinkedHashMap<>(ledger.accounts());
        var quantities = new LinkedHashMap<SubjectId, Integer>();
        for (var lot : contents) {
            if (lots.putIfAbsent(lot.id(), lot) != null) throw new IllegalArgumentException("adoption cannot reissue an existing lot");
            quantities.put(lot.id(), lot.quantity());
        }
        if (!quantities.isEmpty()) {
            var id = ReferenceContainerCustody.scopeId(declaration.container().id());
            if (accounts.putIfAbsent(id, new CustodyAccount(id, new ResourceCustody.Container(declaration.container().id()),
                    quantities, Map.of())) != null) throw new IllegalArgumentException("adoption account is already owned");
        }
        var surfaces = new LinkedHashMap<>(inventory.surfaces());
        surfaces.put(declaration.container().id(), new ContainerSurface(declaration.container().id(), declaration.position(), ContainerSurfaceStatus.ADOPTING));
        var recovery = declared.fencedRecovery(); var binding = FencedRecoveryContainerSupport.bindingId(declaration.container());
        recovery = recovery.prepare(FencedRecoveryBinding.prepared(binding, FencedRecoveryAsset.CONTAINER, owner, 0L,
                recovery.nextEpoch(binding), true));
        return declared.withChanges(FrontierWorldStateUpdate.begin().inventory(new ExactInventory(inventory.containers(), inventory.items(),
                inventory.cargo(), inventory.playerItems(), inventory.worldCarrierItems(), inventory.conflicts(), surfaces,
                inventory.economics(), new FungibleResourceLedger(lots, ledger.claims(), accounts, ledger.bindings())))
                .fencedRecovery(recovery));
    }
}
