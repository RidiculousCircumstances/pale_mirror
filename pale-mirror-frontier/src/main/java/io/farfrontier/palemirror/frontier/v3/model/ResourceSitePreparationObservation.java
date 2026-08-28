package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Exact postcondition that every named soil/crop cell of one field is now PM-owned and visible. */
public record ResourceSitePreparationObservation(PhysicalObservationId id, PhysicalIntentId intentId, SubjectId siteId,
                                                  int preparedSoilSlots, int preparedCropSlots) implements PhysicalEffectObservation {
    public ResourceSitePreparationObservation {
        Objects.requireNonNull(id, "resource-site preparation observation id"); Objects.requireNonNull(intentId, "resource-site preparation intent id");
        Objects.requireNonNull(siteId, "resource-site preparation observation site id");
        if (!siteId.value().startsWith("site:") || preparedSoilSlots != ResourceSiteKind.WHEAT_FIELD.cropSlotCount()
                || preparedCropSlots != ResourceSiteKind.WHEAT_FIELD.cropSlotCount()) throw new IllegalArgumentException("resource-site preparation receipt is incomplete");
    }
}
