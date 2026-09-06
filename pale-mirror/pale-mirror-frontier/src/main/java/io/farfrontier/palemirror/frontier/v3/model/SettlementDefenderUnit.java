package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.List;
import java.util.Objects;

/**
 * Exact settlement defenders retained by one assault, not a second population
 * registry.  The assault owns this unit's lifecycle and exclusive assignment.
 */
public record SettlementDefenderUnit(SubjectId id, SubjectId settlementId, SubjectId leaderId, List<SubjectId> memberIds) {
    public SettlementDefenderUnit {
        Objects.requireNonNull(id, "defender unit id"); Objects.requireNonNull(settlementId, "defender unit settlement");
        Objects.requireNonNull(leaderId, "defender unit leader"); memberIds = List.copyOf(memberIds);
        if (!id.value().startsWith("unit:")) throw new IllegalArgumentException("defender unit must have canonical identity");
        if (memberIds.isEmpty() || memberIds.size() > SettlementAssault.MAX_DEFENDERS
                || memberIds.stream().distinct().count() != memberIds.size() || !leaderId.equals(memberIds.getFirst())) {
            throw new IllegalArgumentException("defender unit must retain a first exact leader and distinct members");
        }
    }

    public static SettlementDefenderUnit forAssault(SubjectId assaultId, SubjectId settlementId, List<SubjectId> memberIds) {
        Objects.requireNonNull(assaultId, "assault id");
        if (!assaultId.value().startsWith("assault:") || memberIds.isEmpty()) {
            throw new IllegalArgumentException("defender unit needs one named assault and member");
        }
        return new SettlementDefenderUnit(new SubjectId("unit:" + assaultId.value().substring("assault:".length())), settlementId,
                memberIds.getFirst(), memberIds);
    }
}
