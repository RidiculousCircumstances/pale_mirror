package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Owners declare future supply and demand, never a second inventory balance or trade consent. */
public interface StockSupplyPort {
    enum Key { BAKERY, FIELD, EXTRACTION, TRADE }
    record Commitment(SubjectId subject, SubjectId economicOwner, String itemKind, long incoming, long outgoing) {
        public Commitment {
            Objects.requireNonNull(subject); Objects.requireNonNull(economicOwner); Objects.requireNonNull(itemKind);
            if (itemKind.isBlank() || incoming < 0 || outgoing < 0)
                throw new IllegalArgumentException("invalid stock commitment");
        }
    }
    Key key();
    List<Commitment> pending(FrontierWorldState state);
    /** Recipe owners declare upstream demand from the output-only supply view, never recursive input planning. */
    long inputDemand(FrontierWorldState state, SubjectId owner, String itemKind);
}
