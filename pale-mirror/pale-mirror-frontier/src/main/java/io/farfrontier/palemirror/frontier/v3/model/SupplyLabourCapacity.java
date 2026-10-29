package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** A common finite planning horizon; capability owners alone select eligible workers. */
final class SupplyLabourCapacity {
    private record Candidate(ResidentProfile resident, boolean permitted, int skill) { }
    private record Potential(SubjectId home, ResidentWorkKind work, HumanCapability capability) { }
    private static final ImmutableKeyedInputView<Potential, List<ResidentProfile>> POTENTIALS = new ImmutableKeyedInputView<>(256);
    /** Complete dynamic reads of long-run potential. Current assignment, hunger
     * and feet position do not change a person's declared productive capacity. */
    static List<Object> potentialInputs(FrontierWorldState state, SubjectId home) {
        return List.of(ResidentWorkforceFacts.available(state, home), state.humanPopulation().schedule(home));
    }
    /** Long-run workforce potential ignores temporary activity/assignment. */
    static List<ResidentProfile> potential(FrontierWorldState state, SubjectId home, ResidentWorkKind work, HumanCapability capability) {
        var rule = state.bootstrap().ruleset().labour().entries().get(work);
        if (rule == null) return List.of();
        // Installed recipes repeatedly ask for the same long-run workforce.
        // Retain only its complete declared population/migration/life read set;
        // current body movement, stock reservations and market drafts are not it.
        return POTENTIALS.getPublished(state.bootstrap(), new Potential(home, work, capability), potentialInputs(state, home),
                () -> potential(state, home, capability, rule.targetWorkers()));
    }
    private static List<ResidentProfile> potential(FrontierWorldState state, SubjectId home, HumanCapability capability, int limit) {
        return ResidentWorkforceFacts.available(state, home).stream().filter(r -> r.capability(capability) > 0)
                .sorted(Comparator.comparingInt((ResidentProfile r) -> r.capability(capability)).reversed().thenComparing(ResidentProfile::id))
                .limit(limit).toList();
    }
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
        return state.humanPopulation().residentsIn(home).stream()
                .filter(r -> r.capability(capability) > 0
                        && !state.humanPopulation().migrations().containsKey(r.id())
                        && state.actorLocations().get(r.id()).condition().status() == ActorLifeStatus.ALIVE)
                .filter(r -> assignments.idle(r.id()) || retainedByOwner.test(r))
                .map(r -> new Candidate(r, permissions.permits(work, r.id()), r.capability(capability)))
                .sorted(Comparator.comparingInt((Candidate c) -> c.permitted() ? 0 : 1)
                        .thenComparing(Comparator.comparingInt(Candidate::skill).reversed())
                        .thenComparing(c -> c.resident().id()))
                .limit(rule.targetWorkers()).map(Candidate::resident).toList();
    }
    static Map<String, Long> over(FrontierWorldState state, SubjectId home, Collection<SubjectId> workers,
                                  SupplyPlanningWindow window) {
        long ticks = window.workTicks(state.humanPopulation().schedule(home));
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
