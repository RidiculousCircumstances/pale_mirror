package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;

/** Durable nominal identity. Geometry and plant biology belong to the current field cycle. */
public record ResourceSiteDeclaration(SubjectId id, SubjectId settlementId,
                                      SubjectId facilityId, ResourceSiteKind kind, FieldLandOrigin land) {
    public ResourceSiteDeclaration {
        Objects.requireNonNull(id); Objects.requireNonNull(settlementId);
        Objects.requireNonNull(facilityId); Objects.requireNonNull(kind);
        Objects.requireNonNull(land);
        if (!id.value().startsWith("site:") || !settlementId.value().startsWith("settlement:")
                || !facilityId.value().startsWith("structure:"))
            throw new IllegalArgumentException("resource site declaration has a foreign identity namespace");
    }
    public static ResourceSiteDeclaration of(ResourceSite site) {
        return new ResourceSiteDeclaration(site.id(), site.settlementId(), site.facilityId(), site.kind(),
                new FieldLandOrigin(FieldLandOrigin.Kind.AUTHORED, java.util.Map.of()));
    }
    public ResourceSite describe(ResourceFieldLayout layout) {
        return new ResourceSite(id, settlementId, facilityId, kind, layout);
    }
}
