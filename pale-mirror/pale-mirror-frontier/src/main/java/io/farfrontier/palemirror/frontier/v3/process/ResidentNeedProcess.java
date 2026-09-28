package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.model.*;

import java.util.List;

/** Sparse canonical need clock. This owner remains dormant until provision retirement. */
public final class ResidentNeedProcess {
    public static final String REVIEW = "frontier.resident.need.review";
    private ResidentNeedProcess() { }

    public static ScheduledAction review(SubjectId residentId, long dueAt) {
        if (!residentId.value().startsWith("resident:") || dueAt < 1)
            throw new IllegalArgumentException("need review requires one exact living-person due instant");
        return new ScheduledAction(new ScheduleId("schedule:resident-need-"
                + residentId.value().substring("resident:".length()) + "-" + dueAt),
                new SimInstant(dueAt), 10, residentId, REVIEW, 1);
    }

    public static ScheduledAction firstReviewAfter(SubjectId residentId, long birthOrStartTick,
                                                    FrontierRuleset.ResidentLife rules) {
        if (birthOrStartTick < 0) birthOrStartTick = 0;
        long day = birthOrStartTick / rules.hungerUnitTicks();
        return review(residentId, Math.multiplyExact(Math.addExact(day, 1L), rules.hungerUnitTicks()));
    }

    public static List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action) {
        if (!action.kind().equals(REVIEW) || !action.id().equals(review(action.subject(), action.dueAt().ticks()).id()))
            throw new IllegalArgumentException("need review has a foreign action identity");
        ResidentProfile resident = state.humanPopulation().resident(action.subject());
        if (resident == null) return List.of(new ProposedEvent(action.subject(),
                new ScheduleEffect.Consumed(action.id())));
        ActorLocation actor = state.actorLocations().get(resident.id());
        if (actor == null || actor.condition().status() != ActorLifeStatus.ALIVE)
            return List.of(new ProposedEvent(action.subject(), new ScheduleEffect.Consumed(action.id())));
        FrontierRuleset.ResidentLife rules = state.bootstrap().ruleset().residentLife();
        if (action.dueAt().ticks() % rules.hungerUnitTicks() != 0)
            throw new IllegalArgumentException("need review is not a selected hunger boundary");
        ResidentNutrition previous = state.humanPopulation().nutrition(resident.id());
        ResidentNutrition next = previous.accrueThrough(action.dueAt().ticks(), rules);
        if (next.lastIntegratedDay() <= previous.lastIntegratedDay())
            throw new IllegalArgumentException("need review cannot replay an integrated day");
        return List.of(new ProposedEvent(resident.id(), new ResidentNeedIntegrated(resident.id(),
                        action.dueAt().ticks(), previous.lastIntegratedDay(), next.lastIntegratedDay(),
                        next.hungerDeficit())),
                new ProposedEvent(resident.id(), new ScheduleEffect.Created(
                        review(resident.id(), Math.addExact(action.dueAt().ticks(), rules.hungerUnitTicks())))));
    }

    public static FrontierWorldState reduce(FrontierWorldState state, SubjectId subject,
                                            ResidentNeedIntegrated integrated) {
        if (!subject.equals(integrated.residentId()) || state.actorLocations().get(subject) == null
                || state.actorLocations().get(subject).condition().status() != ActorLifeStatus.ALIVE)
            throw new IllegalArgumentException("need integration has no living exact resident");
        ResidentNutrition previous = state.humanPopulation().nutrition(subject);
        ResidentNutrition next = previous.accrueThrough(integrated.atTick(), state.bootstrap().ruleset().residentLife());
        if (previous.lastIntegratedDay() != integrated.previousDay()
                || next.lastIntegratedDay() != integrated.integratedDay()
                || next.hungerDeficit() != integrated.hungerDeficit())
            throw new IllegalArgumentException("need integration differs from its deterministic predecessor");
        return state.withHumanPopulation(state.humanPopulation().accrueHunger(subject,
                integrated.atTick(), state.bootstrap().ruleset().residentLife()));
    }
}
