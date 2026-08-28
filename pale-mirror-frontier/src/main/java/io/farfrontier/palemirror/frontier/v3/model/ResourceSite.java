package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/** Immutable identity and physical crop slots of one canonical renewable resource site. */
public record ResourceSite(SubjectId id, SubjectId settlementId, SubjectId facilityId, ResourceSiteKind kind,
                           List<BlockPosition> cropSlots) {
    public ResourceSite {
        Objects.requireNonNull(id, "resource site id"); Objects.requireNonNull(settlementId, "resource site settlement id");
        Objects.requireNonNull(facilityId, "resource site facility id"); Objects.requireNonNull(kind, "resource site kind");
        cropSlots = List.copyOf(Objects.requireNonNull(cropSlots, "resource site crop slots"));
        if (!id.value().startsWith("site:") || !settlementId.value().startsWith("settlement:") || !facilityId.value().startsWith("structure:")) {
            throw new IllegalArgumentException("resource site identities must use canonical namespaces");
        }
        if (cropSlots.size() != kind.cropSlotCount() || new LinkedHashSet<>(cropSlots).size() != cropSlots.size()) {
            throw new IllegalArgumentException("resource site must retain every distinct crop slot");
        }
    }
}
