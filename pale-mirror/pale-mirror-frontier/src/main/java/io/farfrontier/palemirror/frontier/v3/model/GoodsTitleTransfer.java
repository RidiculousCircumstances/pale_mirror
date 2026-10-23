package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

/** Commercial settlement depends on rights/quantity, not the representation of stored goods. */
public sealed interface GoodsTitleTransfer permits ResourceTitleTransfer, IndividualTitleTransfer {
    SubjectId claimId();
    SubjectId sourceOwnerId();
    SubjectId destinationOwnerId();
    int quantity();
}
