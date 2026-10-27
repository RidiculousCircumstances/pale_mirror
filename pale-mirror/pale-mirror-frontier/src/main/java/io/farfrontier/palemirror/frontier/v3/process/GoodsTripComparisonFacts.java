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
    private static final ImmutableKeyedInputView<SubjectId, BoundedValueMemo<Long, GoodsTripComparisonFacts>> DECISIONS =
            new ImmutableKeyedInputView<>(256);
    private final Map<SubjectId, Measured> measured = new LinkedHashMap<>(16, .75f, true);
    static GoodsTripComparisonFacts forDecision(FrontierWorldState state, GoodsParticipant buyer, long now) {
        if (now < 0) throw new IllegalArgumentException("negative comparison instant");
        // Exact immutable identities fence changed stock, funding, workforce,
        // geometry and consent. Working views are local to the preparation thread.
        return DECISIONS.get(state.bootstrap(), buyer.party().id(), List.of(state, buyer), () -> new BoundedValueMemo<>(1))
                .get(now, GoodsTripComparisonFacts::new);
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
