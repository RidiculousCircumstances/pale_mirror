package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;

/** An unfulfilled allocation was released, not delivered, consumed or implicitly retitled. */
public record GoodsTradeDisposition(SubjectId id, SubjectId contractId, long expectedContractRevision,
                                    SubjectId claimId, int quantity, Reason reason) {
    public enum Reason {
        CANCELLED_BEFORE_LOADING(1), OBSERVED_ALLOCATION_CHANGED(2);
        private final int wireTag;
        Reason(int wireTag) { this.wireTag = wireTag; }
        public int wireTag() { return wireTag; }
        public static Reason fromWireTag(int tag) {
            for (Reason reason : values()) if (reason.wireTag == tag) return reason;
            throw new IllegalArgumentException("unknown goods disposition reason tag: " + tag);
        }
    }
    public GoodsTradeDisposition {
        Objects.requireNonNull(id); Objects.requireNonNull(contractId); Objects.requireNonNull(claimId); Objects.requireNonNull(reason);
        if (expectedContractRevision < 0 || quantity < 1 || quantity > ResourceLot.MAX_QUANTITY) {
            throw new IllegalArgumentException("invalid goods allocation disposition");
        }
    }
}
