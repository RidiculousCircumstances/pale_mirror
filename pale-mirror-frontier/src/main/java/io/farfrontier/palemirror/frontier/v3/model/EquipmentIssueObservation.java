package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Observed exact active-depot to defender-hand equipment hand-off. */
public record EquipmentIssueObservation(PhysicalObservationId id, PhysicalIntentId intentId, SubjectId assaultId,
                                        SubjectId residentId, SubjectId itemId, InventoryCustody.ContainerSlot sourceSlot)
        implements PhysicalEffectObservation {
    public EquipmentIssueObservation {
        Objects.requireNonNull(id, "equipment issue observation id"); Objects.requireNonNull(intentId, "equipment issue intent");
        Objects.requireNonNull(assaultId, "equipment issue assault"); Objects.requireNonNull(residentId, "equipment issue resident");
        Objects.requireNonNull(itemId, "equipment issue item"); Objects.requireNonNull(sourceSlot, "equipment issue source slot");
    }
}
