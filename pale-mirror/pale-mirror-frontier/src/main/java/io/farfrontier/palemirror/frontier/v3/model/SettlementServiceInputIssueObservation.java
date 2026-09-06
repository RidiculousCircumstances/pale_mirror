package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Observed hand-off of one exact depot stack to the retained worker of one service aggregate. */
public record SettlementServiceInputIssueObservation(PhysicalObservationId id, PhysicalIntentId intentId,
                                                     SubjectId workId, SubjectId workerId, SubjectId itemId,
                                                     InventoryCustody.ContainerSlot sourceSlot)
        implements PhysicalEffectObservation {
    public SettlementServiceInputIssueObservation {
        Objects.requireNonNull(id, "service input issue observation id");
        Objects.requireNonNull(intentId, "service input issue intent id");
        Objects.requireNonNull(workId, "service input issue work id");
        Objects.requireNonNull(workerId, "service input issue worker id");
        Objects.requireNonNull(itemId, "service input issue item id");
        Objects.requireNonNull(sourceSlot, "service input issue source slot");
    }
}
