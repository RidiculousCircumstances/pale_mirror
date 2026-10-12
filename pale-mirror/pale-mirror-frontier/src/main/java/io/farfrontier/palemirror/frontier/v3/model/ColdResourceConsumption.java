package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;

/** Selected portions for one atomic COLD resource sink, not an outstanding physical claim. */
public sealed interface ColdResourceConsumption permits ColdResourceConsumption.Exact, ColdResourceConsumption.Fungible {
    SubjectId ownerId();
    SubjectId containerId();
    String itemKind();
    int quantity();
    /** Historical diagnostic identity; dispatch always uses the declared variant, never this ID. */
    SubjectId firstResourceId();

    record Exact(SubjectId ownerId, SubjectId containerId, String itemKind, SubjectId itemId, int quantity)
            implements ColdResourceConsumption {
        public Exact {
            requireMetadata(ownerId, containerId, itemKind); Objects.requireNonNull(itemId);
            if (quantity < 1 || quantity > 64) throw new IllegalArgumentException("resource consumption needs a bounded real portion");
        }
        @Override public SubjectId firstResourceId() { return itemId; }
    }
    record Fungible(SubjectId ownerId, SubjectId containerId, String itemKind,
                    FungibleResourceCustodySupport.LotSelection lots) implements ColdResourceConsumption {
        public Fungible { requireMetadata(ownerId, containerId, itemKind); Objects.requireNonNull(lots); }
        @Override public int quantity() { return lots.quantity(); }
        @Override public SubjectId firstResourceId() { return lots.firstLotId(); }
    }
    private static void requireMetadata(SubjectId ownerId, SubjectId containerId, String itemKind) {
        Objects.requireNonNull(ownerId); Objects.requireNonNull(containerId);
        if (itemKind == null || !itemKind.matches("[a-z][a-z0-9_-]{0,31}:[a-z0-9][a-z0-9_./-]{0,127}"))
            throw new IllegalArgumentException("resource consumption needs a declared namespaced resource kind");
    }
}
