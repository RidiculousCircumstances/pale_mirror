package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import java.util.List;

/** Participant strategy port. Matching/transport do not know how its targets are selected. */
public interface GoodsParticipantPolicy {
    record Intent(String itemKind, GoodsTradeOrder.Side side, int quantity, FixedScalar limit) {
        public Intent {
            java.util.Objects.requireNonNull(itemKind); java.util.Objects.requireNonNull(side); java.util.Objects.requireNonNull(limit);
            if (quantity < 1 || quantity > 64 || limit.raw() <= 0) throw new IllegalArgumentException("invalid goods policy intent");
        }
    }
    GoodsPolicyKind kind();
    List<Intent> decide(GoodsParticipantView view, GoodsTradeRules rules);
    String explain(GoodsParticipantView view, GoodsTradeRules rules);
}
