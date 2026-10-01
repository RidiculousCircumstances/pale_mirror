package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.model.*;

import java.util.List;

/** Integrates old-rate hunger and replaces the exact resident's two engine wakes atomically. */
public final class ResidentMetabolismProcess {
    private ResidentMetabolismProcess() { }

    public static List<ProposedEvent> plan(FrontierWorldState state, ResidentMetabolismChanged change) {
        SubjectId id = change.residentId();
        ActorLocation actor = state.actorLocations().get(id);
        if (actor == null || actor.condition().status() != ActorLifeStatus.ALIVE)
            throw new IllegalArgumentException("metabolism edit requires one living resident");
        FrontierRuleset.ResidentLife rules = state.bootstrap().ruleset().residentLife();
        int oldRate = change.previous().effectiveMetabolismPermille(change.atTick());
        int newRate = change.next().effectiveMetabolismPermille(change.atTick());
        if (newRate < rules.metabolismMinPermille() || newRate > rules.metabolismMaxPermille()
                || change.next().baseMetabolismPermille() < rules.metabolismMinPermille()
                || change.next().baseMetabolismPermille() > rules.metabolismMaxPermille())
            throw new IllegalArgumentException("metabolism edit exceeds the selected world rules");
        ResidentNutrition prior = state.humanPopulation().nutrition(id);
        long oldDue = prior.nextThresholdTick(rules, oldRate);
        if (change.atTick() >= oldDue)
            throw new IllegalArgumentException("pending resident need threshold must resolve before a rate edit");
        FrontierWorldState changed = reduce(state, id, change);
        long nextDue = changed.humanPopulation().nutrition(id).nextThresholdTick(rules, newRate);
        var events = new java.util.ArrayList<>(ResidentPhysiologyComposition.BEFORE_NUTRITION.beforeRetirement(state, id, change.atTick()));
        events.addAll(List.of(new ProposedEvent(id, change),
                new ProposedEvent(id, new ScheduleEffect.Rescheduled(
                        ResidentNeedProcess.review(id, oldDue).id(), ResidentNeedProcess.review(id, nextDue))),
                new ProposedEvent(id, new ScheduleEffect.Rescheduled(
                        ResidentActivityProcess.review(id, 1L).id(),
                        ResidentActivityProcess.review(id, Math.addExact(change.atTick(), 1L))))));
        return List.copyOf(events);
    }

    public static FrontierWorldState reduce(FrontierWorldState state, SubjectId subject,
                                            ResidentMetabolismChanged change) {
        if (!subject.equals(change.residentId()))
            throw new IllegalArgumentException("metabolism edit has foreign resident subject");
        ActorLocation actor = state.actorLocations().get(subject);
        if (actor == null || actor.condition().status() != ActorLifeStatus.ALIVE)
            throw new IllegalArgumentException("metabolism edit has no living exact resident");
        return state.withHumanPopulation(state.humanPopulation().changeMetabolism(subject,
                change.previous(), change.next(), change.atTick(), state.bootstrap().ruleset().residentLife()));
    }
}
