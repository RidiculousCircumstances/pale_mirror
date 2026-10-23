package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;

/** Exact preimage at acceptance; title changes without recreating or repairing the delivered item. */
public record IndividualTitleTransfer(SubjectId claimId, ExactItemStack before, SubjectId destinationOwnerId) implements GoodsTitleTransfer {
    public IndividualTitleTransfer {
        Objects.requireNonNull(claimId); Objects.requireNonNull(before); Objects.requireNonNull(destinationOwnerId);
        if (before.count() != 1 || !before.condition().durable() || before.economicOwnerId().equals(destinationOwnerId))
            throw new IllegalArgumentException("invalid individual title transfer");
    }
    public SubjectId sourceOwnerId() { return before.economicOwnerId(); }
    public int quantity() { return 1; }
}
