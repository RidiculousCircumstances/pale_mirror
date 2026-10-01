package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.model.*;

import java.util.List;

/** Sparse canonical need clock; settlement-wide provision no longer advances nutrition. */
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

    public static ScheduledAction firstReviewAfter(ResidentProfile resident, long birthOrStartTick,
                                                    FrontierRuleset.ResidentLife rules) {
        return review(resident.id(), ResidentNutrition.nourishedAtTick(birthOrStartTick, rules)
                .nextThresholdTick(rules, resident.characteristics().effectiveMetabolismPermille(birthOrStartTick)));
    }

    /** A witnessed meal changes the next threshold, including when its old due action is queued but not yet run. */
    public static ProposedEvent requeueAfterConfirmedBread(FrontierWorldState state, SubjectId residentId,
                                                            long consumedAtTick) {
        ResidentProfile resident = state.humanPopulation().resident(residentId);
        if (resident == null || state.actorLocations().get(residentId) == null
                || state.actorLocations().get(residentId).condition().status() != ActorLifeStatus.ALIVE)
            throw new IllegalArgumentException("need review replacement requires one living resident");
        FrontierRuleset.ResidentLife rules = state.bootstrap().ruleset().residentLife();
        ResidentNutrition before = state.humanPopulation().nutrition(residentId);
        int rate = resident.characteristics().effectiveMetabolismPermille(consumedAtTick);
        long oldDue = before.nextThresholdTick(rules, rate);
        ResidentNutrition after = before.consumeBreadAt(consumedAtTick, rules, rate);
        long nextDue = after.nextThresholdTick(rules, rate);
        return new ProposedEvent(residentId, new ScheduleEffect.Rescheduled(
                review(residentId, oldDue).id(), review(residentId, nextDue)));
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
        ResidentNutrition previous = state.humanPopulation().nutrition(resident.id());
        int rate = resident.characteristics().effectiveMetabolismPermille(action.dueAt().ticks());
        if (action.dueAt().ticks() != previous.nextThresholdTick(rules, rate))
            throw new IllegalArgumentException("need review is not this resident's exact threshold");
        ResidentNutrition next = previous.accrueThrough(action.dueAt().ticks(), rules, rate);
        if (next.satietyUnits() >= previous.satietyUnits() && next.satietyUnits() > 0)
            throw new IllegalArgumentException("need review did not advance its exact threshold");
        return List.of(new ProposedEvent(resident.id(), new ResidentNeedIntegrated(resident.id(),
                        action.dueAt().ticks(), previous.lastEvaluatedTick(), next.satietyUnits(),
                        next.fractionalProgress())),
                new ProposedEvent(resident.id(), new ScheduleEffect.Created(
                        review(resident.id(), next.nextThresholdTick(rules, rate)))));
    }

    public static FrontierWorldState reduce(FrontierWorldState state, SubjectId subject,
                                            ResidentNeedIntegrated integrated) {
        if (!subject.equals(integrated.residentId()) || state.actorLocations().get(subject) == null
                || state.actorLocations().get(subject).condition().status() != ActorLifeStatus.ALIVE)
            throw new IllegalArgumentException("need integration has no living exact resident");
        ResidentNutrition previous = state.humanPopulation().nutrition(subject);
        ResidentNutrition next = previous.accrueThrough(integrated.atTick(), state.bootstrap().ruleset().residentLife(),
                state.humanPopulation().resident(subject).characteristics().effectiveMetabolismPermille(integrated.atTick()));
        if (previous.lastEvaluatedTick() != integrated.previousTick()
                || next.satietyUnits() != integrated.satietyUnits()
                || next.fractionalProgress() != integrated.fractionalProgress())
            throw new IllegalArgumentException("need integration differs from its deterministic predecessor");
        return state.withHumanPopulation(state.humanPopulation().accrueHunger(subject,
                integrated.atTick(), state.bootstrap().ruleset().residentLife()));
    }
}
