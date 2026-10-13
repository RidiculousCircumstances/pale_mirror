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
        if (!SettlementStorageTerritory.owner(state, declaration.position()).map(Settlement::id)
                .filter(owner::equals).isPresent())
            throw new IllegalArgumentException("default storage admission lacks one exact territorial owner");
        if (!declaration.container().ownerId().equals(owner) || !state.bootstrap().bounds().contains(declaration.position()))
            throw new IllegalArgumentException("storage adoption has foreign declaration ownership or position");
        var next = state.withChanges(FrontierWorldStateUpdate.begin()
                .inventory(FixedContainerAdoption.adopt(state.inventory(), declaration.container(), declaration.position(), contents))
                .fencedRecovery(FixedContainerAdoption.prepare(state.fencedRecovery(), declaration.container())));
        ShipmentEndpointComposition.validate(next, declaration.container().storage().orElseThrow().endpoint());
        return next;
    }
}
