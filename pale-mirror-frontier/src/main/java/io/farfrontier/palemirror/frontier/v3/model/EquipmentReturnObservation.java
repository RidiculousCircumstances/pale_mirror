package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Observed exact former-defender hand to active-depot-slot equipment return. */
public record EquipmentReturnObservation(PhysicalObservationId id, PhysicalIntentId intentId, SubjectId assaultId,
                                         SubjectId residentId, SubjectId itemId, InventoryCustody.ContainerSlot targetSlot)
        implements PhysicalEffectObservation {
    public EquipmentReturnObservation {
        Objects.requireNonNull(id, "equipment return observation id"); Objects.requireNonNull(intentId, "equipment return intent");
        Objects.requireNonNull(assaultId, "equipment return assault"); Objects.requireNonNull(residentId, "equipment return resident");
        Objects.requireNonNull(itemId, "equipment return item"); Objects.requireNonNull(targetSlot, "equipment return target slot");
    }
}
