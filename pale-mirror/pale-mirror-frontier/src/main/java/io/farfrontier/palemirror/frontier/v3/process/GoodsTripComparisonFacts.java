package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionTiming;
import io.farfrontier.palemirror.frontier.v3.model.market.ProcurementSelection;
import java.util.*;

/** Disposable measured comparison facts for one unchanged decision. No draft world,
 * reservation, crew, navigation receipt or permission survives here. */
final class GoodsTripComparisonFacts {
    record Measured(ProcurementSelection.Offer offer, ExpeditionTiming timing) {
        Measured { Objects.requireNonNull(offer); Objects.requireNonNull(timing); }
    }
    // Same complete alternative ceiling accepted by ProcurementSelection.
    private static final int MAX_MEASURED = 2048;
    private record Decision(GoodsParticipant buyer, long now) {
        // Match the previous exact declaration identity without hashing the
        // participant's complete counterparty list or price history.
        @Override public int hashCode() { return 31 * System.identityHashCode(buyer) + Long.hashCode(now); }
        @Override public boolean equals(Object other) {
            return this == other || other instanceof Decision value && buyer == value.buyer && now == value.now;
        }
    }
    private static final ImmutableInputView<BoundedValueMemo<Decision, GoodsTripComparisonFacts>> DECISIONS = new ImmutableInputView<>();
    private final Map<SubjectId, Measured> measured = new LinkedHashMap<>(16, .75f, true);
    static GoodsTripComparisonFacts forDecision(FrontierWorldState state, GoodsParticipant buyer, long now) {
        if (now < 0) throw new IllegalArgumentException("negative comparison instant");
        // Exact world/declaration/time identity still fences every input. Retain
        // one current world image, not a historical root per party. A suspended
        // calculation owns its captured working facts independently of this memo.
        // Mutable comparisons never enter a shared completed-result cache.
        return DECISIONS.get(List.of(state), () -> new BoundedValueMemo<>(256))
                .get(new Decision(Objects.requireNonNull(buyer), now), GoodsTripComparisonFacts::new);
    }
    Measured rank(ProcurementSelection.Offer provisional, ExpeditionTiming timing) {
        var prior = measured.get(provisional.id());
        return prior == null ? new Measured(provisional, timing) : prior;
    }
    void remember(ProcurementSelection.Offer offer, ExpeditionTiming timing) {
        if (measured.size() >= MAX_MEASURED && !measured.containsKey(offer.id()))
            measured.remove(measured.keySet().iterator().next());
        measured.put(offer.id(), new Measured(offer, timing));
    }
}
