package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;

/** Periodic land-use owner, separate from market, plant biology and worker execution. */
final class CultivationProcessModule implements FrontierWorldProcessModule {
    static final String REVIEW = "frontier.cultivation.review";
    static final Set<String> TYPES = Set.of(CultivationReviewed.TYPE);
    static final DeterministicProcessDescriptor DESCRIPTOR = new DeterministicProcessDescriptor("cultivation", Set.of(),
            Set.of(REVIEW), TYPES, Set.of(CultivationReviewed.TYPE, "kernel.schedule_created", "kernel.schedule_rescheduled"), TYPES);
    static ScheduledAction next(SubjectId home, long atTick) {
        return new ScheduledAction(new ScheduleId("schedule:cultivation-" + home.value().replace(':', '-')),
                new SimInstant(atTick), 0, home, REVIEW, 1);
    }
    @Override public List<ScheduledAction> initialSchedules(FrontierBootstrap bootstrap) {
        if (bootstrap.ruleset().fields().cultivation().isEmpty()) return List.of();
        return bootstrap.settlements().stream().map(home -> next(home.id(), bootstrap.ruleset().goodsTrade().reviewInterval())).toList();
    }
    static List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action, SimInstant instant) {
        if (state.bootstrap().ruleset().fields().cultivation().isEmpty()
                || !action.id().equals(next(action.subject(), action.dueAt().ticks()).id())) throw new IllegalArgumentException("undeclared cultivation review");
        long now = instant.ticks(); var prior = state.resourceSites().cultivation().get(action.subject());
        var result = CultivationPlanning.read(state, action.subject(), now);
        var events = new ArrayList<ProposedEvent>();
        if (result.sites() != state.resourceSites()) {
            events.add(new ProposedEvent(action.subject(), new CultivationReviewed(prior == null ? -1 : prior.atTick(), now)));
            var projected = state.withResourceSites(result.sites());
            for (var id : result.declared()) events.add(new ProposedEvent(id, new ScheduleEffect.Created(
                    ResourceFieldGrowthProcess.next(result.sites().site(id), Math.addExact(now, state.bootstrap().ruleset().cadence().resourceGrowthStageInterval())))));
            for (var declaration : result.sites().declarations().values().stream().sorted(Comparator.comparing(ResourceSiteDeclaration::id)).toList()) {
                if (!declaration.settlementId().equals(action.subject()) || !result.sites().cycle(declaration.id()).hasAvailableWork()) continue;
                var before = state.resourceSites().cycles().get(declaration.id());
                var after = result.sites().cycle(declaration.id());
                if (before != null && before.sowing().equals(after.sowing()) && before.layout().equals(after.layout())) continue;
                events.add(new ProposedEvent(declaration.id(), new ScheduleEffect.Created(StrategicObjectiveProcess.resourceHarvestOpportunity(
                        projected, result.sites().site(declaration.id()), Math.addExact(now, 1), "cultivation|" + now))));
            }
        }
        events.add(new ProposedEvent(action.subject(), new ScheduleEffect.Rescheduled(action.id(),
                next(action.subject(), Math.addExact(now, state.bootstrap().ruleset().goodsTrade().reviewInterval())))));
        return List.copyOf(events);
    }
    @Override public FrontierWorldState reduce(FrontierWorldState state, FrontierEvent event) {
        var reviewed = (CultivationReviewed) event.payload();
        var prior = state.resourceSites().cultivation().get(event.subject());
        if (reviewed.atTick() != event.instant().ticks() || reviewed.previousReviewTick() != (prior == null ? -1 : prior.atTick()))
            throw new IllegalArgumentException("cultivation review has a stale predecessor or clock");
        return state.withResourceSites(CultivationPlanning.read(state, event.subject(), reviewed.atTick()).sites());
    }
}
