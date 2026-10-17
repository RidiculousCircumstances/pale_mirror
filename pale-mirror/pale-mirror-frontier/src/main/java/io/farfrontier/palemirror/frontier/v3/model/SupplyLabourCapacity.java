package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** A common finite planning horizon; capability owners alone select eligible workers. */
final class SupplyLabourCapacity {
    /** Advisory workforce potential precedes demand-driven staffing. This grants no permission. */
    static List<ResidentProfile> candidates(FrontierWorldState state, SubjectId home, ResidentWorkKind work,
            HumanCapability capability, java.util.function.Predicate<ResidentProfile> retainedByOwner) {
        var permissions = SettlementWorkPolicy.permissions(state, home);
        if (state.bootstrap().ruleset().goodsTrade().supplyPlanning().economics().isEmpty())
            return permissions.workers(work).stream().map(state.humanPopulation()::resident)
                    .filter(r -> state.actorLocations().get(r.id()).condition().status() == ActorLifeStatus.ALIVE
                            && r.capability(capability) > 0).toList();
        var rule = state.bootstrap().ruleset().labour().entries().get(work);
        if (rule == null) return List.of();
        var assignments = HumanAssignmentProjection.compile(state);
        return state.humanPopulation().residents().values().stream()
                .filter(r -> r.settlementId().equals(home) && r.capability(capability) > 0
                        && !state.humanPopulation().migrations().containsKey(r.id())
                        && state.actorLocations().get(r.id()).condition().status() == ActorLifeStatus.ALIVE)
                .filter(r -> assignments.idle(r.id()) || retainedByOwner.test(r))
                .sorted(Comparator.comparingInt((ResidentProfile r) -> permissions.permits(work, r.id()) ? 0 : 1)
                        .thenComparing(Comparator.comparingInt((ResidentProfile r) -> r.capability(capability)).reversed())
                        .thenComparing(ResidentProfile::id))
                .limit(rule.targetWorkers()).toList();
    }
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
