package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;
import java.util.Optional;

/** Selected declaration or explicit settled retirement, never a search during event reduction. */
public record DefenderEquipmentReturnVisitChanged(SubjectId assaultId,
        Optional<DefenderEquipmentReturnVisit> predecessor, Optional<DefenderEquipmentReturnVisit> selected) implements FrontierPayload {
    public DefenderEquipmentReturnVisitChanged {
        Objects.requireNonNull(assaultId); Objects.requireNonNull(predecessor); Objects.requireNonNull(selected);
        if (predecessor.equals(selected)
                || predecessor.stream().anyMatch(visit -> !visit.ownerId().equals(assaultId))
                || selected.stream().anyMatch(visit -> !visit.ownerId().equals(assaultId)))
            throw new IllegalArgumentException("equipment return visit must declare an exact admission or retirement");
    }
    @Override public String type() { return "frontier.defender_equipment_return_visit_changed"; }
}
