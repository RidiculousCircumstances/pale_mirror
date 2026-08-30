package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.List;
import java.util.Objects;

/** One bounded slice of one named exact food stack assigned to a current settlement ration cycle. */
public record SettlementRationAllocation(SubjectId itemId, List<SubjectId> recipientIds) {
    public SettlementRationAllocation {
        Objects.requireNonNull(itemId, "ration item id");
        recipientIds = List.copyOf(Objects.requireNonNull(recipientIds, "ration recipients"));
        if (recipientIds.isEmpty() || recipientIds.size() > 64 || recipientIds.stream().distinct().count() != recipientIds.size()) {
            throw new IllegalArgumentException("ration allocation recipients must be distinct and fit one exact stack");
        }
    }

    public int count() {
        return recipientIds.size();
    }
}
