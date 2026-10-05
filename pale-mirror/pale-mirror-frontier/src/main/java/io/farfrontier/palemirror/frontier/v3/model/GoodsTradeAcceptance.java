package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;

/** Receipt for rights over already-accounted goods at the named receiving container. */
public record GoodsTradeAcceptance(SubjectId id, SubjectId contractId, long expectedContractRevision,
                                   ResourceTitleTransfer title) {
    public GoodsTradeAcceptance {
        Objects.requireNonNull(id); Objects.requireNonNull(contractId); Objects.requireNonNull(title);
        if (expectedContractRevision < 0) throw new IllegalArgumentException("negative trade receipt revision");
    }
}
