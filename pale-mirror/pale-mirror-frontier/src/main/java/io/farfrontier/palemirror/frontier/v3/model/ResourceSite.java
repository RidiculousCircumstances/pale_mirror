package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.List;
import java.util.Objects;

/** Immutable identity and physical crop slots of one canonical renewable resource site. */
public record ResourceSite(SubjectId id, SubjectId settlementId, SubjectId facilityId, ResourceSiteKind kind,
                           ResourceFieldLayout layout) {
    public ResourceSite {
        Objects.requireNonNull(id, "resource site id"); Objects.requireNonNull(settlementId, "resource site settlement id");
        Objects.requireNonNull(facilityId, "resource site facility id"); Objects.requireNonNull(kind, "resource site kind");
        Objects.requireNonNull(layout, "resource site field layout");
        if (!id.value().startsWith("site:") || !settlementId.value().startsWith("settlement:") || !facilityId.value().startsWith("structure:")) {
            throw new IllegalArgumentException("resource site identities must use canonical namespaces");
        }
    }

    public List<BlockPosition> cropSlots() { return layout.cropSlots(); }

    /** Each crop slot has one fixed soil capital cell immediately below it. */
    public List<BlockPosition> soilSlots() {
        return layout.soilSlots();
    }

    /** Producer-declared irrigation geometry; never guessed from a field bounding rectangle. */
    public List<BlockPosition> irrigationSlots() {
        return layout.irrigationSlots();
    }

    /** All source-owned field cells, including the non-yielding physical irrigation works. */
    public List<BlockPosition> managedSlots() {
        return layout.managedSlots();
    }

    public boolean contains(BlockPosition position) { return layout.contains(position); }
    public List<ResourceFieldLayout.ChunkColumn> occupiedChunks() { return layout.occupiedChunks(); }
}
