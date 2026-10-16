package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** A common finite planning horizon; capability owners alone select eligible workers. */
final class SupplyLabourCapacity {
    static Map<String, Long> day(FrontierWorldState state, SubjectId home, Collection<SubjectId> workers) {
        long ticks = state.humanPopulation().schedule(home).segments().stream()
                .filter(segment -> segment.window() == SettlementDailySchedule.Window.WORK)
                .mapToLong(segment -> segment.endExclusive() - segment.startInclusive()).sum();
        if (ticks == 0) return Map.of();
        var result = new TreeMap<String, Long>();
        for (var worker : workers) {
            if (!state.humanPopulation().resident(worker).settlementId().equals(home))
                throw new IllegalArgumentException("supply capacity references a foreign worker");
            result.put(worker.value(), ticks);
        }
        return Map.copyOf(result);
    }
    private SupplyLabourCapacity() { }
}
