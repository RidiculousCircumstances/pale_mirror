package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Bounded disposable economic population projection; ordinary movement does not invalidate stock policy. */
final class LivingResidentStockView {
    private static final ImmutableInputView<Map<SubjectId, Integer>> VIEW = new ImmutableInputView<>();
    private static final ThreadLocal<Map<SubjectId, Integer>> PREVIOUS = ThreadLocal.withInitial(Map::of);
    private LivingResidentStockView() { }
    static Map<SubjectId, Integer> counts(FrontierWorldState state) {
        return VIEW.get(List.of(state.humanPopulation().residents(), state.actorLocations()), () -> {
            var next = new HashMap<SubjectId, Integer>();
            for (var resident : state.humanPopulation().residents().values())
                if (state.actorLocations().get(resident.id()).condition().status() == ActorLifeStatus.ALIVE)
                    next.merge(resident.settlementId(), 1, Math::addExact);
            var previous = PREVIOUS.get();
            if (previous.equals(next)) return previous;
            var result = Map.copyOf(next); PREVIOUS.set(result);
            return result;
        });
    }
}
