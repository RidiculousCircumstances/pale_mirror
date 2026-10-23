package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;

/** A reservation of one individual item, not a duplicate inventory or a physical slot reservation. */
public record IndividualResourceAllocation(SubjectId id, SubjectId claimantId, SubjectId economicOwnerId,
        SubjectId itemId, String itemKind, ItemCondition condition) {
    public IndividualResourceAllocation {
        Objects.requireNonNull(id); Objects.requireNonNull(claimantId); Objects.requireNonNull(economicOwnerId);
        Objects.requireNonNull(itemId); Objects.requireNonNull(itemKind); Objects.requireNonNull(condition);
        if (!condition.durable() || !itemKind.matches("[a-z][a-z0-9_-]{0,31}:[a-z0-9][a-z0-9_./-]{0,127}"))
            throw new IllegalArgumentException("individual allocation requires an explicit durable item specification");
    }
    public int quantity() { return 1; }
    public boolean matches(ExactItemStack item) {
        return item != null && item.id().equals(itemId) && item.economicOwnerId().equals(economicOwnerId)
                && item.itemKind().equals(itemKind) && item.count() == 1 && item.condition().equals(condition);
    }
}
