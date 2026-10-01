package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.List;

/** Sole starvation-condition owner. No scheduling, navigation, work or physical vitality authority. */
public final class ResidentStarvationProcess {
    private ResidentStarvationProcess() { }
    public static List<ProposedEvent> planBeforeNutrition(FrontierWorldState state, SubjectId resident, long tick) {
        requireLiving(state, resident);
        var previous = state.humanPopulation().health(resident).starvation();
        var next = evaluate(state, resident, tick);
        if (previous.equals(next)) return List.of();
        return List.of(new ProposedEvent(resident, new ResidentStarvationIntegrated(resident,
                state.humanPopulation().nutrition(resident).lastEvaluatedTick(), tick, previous, next)));
    }
    public static ResidentStarvation evaluate(FrontierWorldState state, SubjectId resident, long tick) {
        requireLiving(state, resident);
        return state.humanPopulation().health(resident).starvation().integrateThrough(
                state.humanPopulation().nutrition(resident), tick, state.bootstrap().ruleset().residentLife(),
                state.humanPopulation().resident(resident).characteristics().effectiveMetabolismPermille(tick));
    }
    public static FrontierWorldState reduce(FrontierWorldState state, SubjectId subject, ResidentStarvationIntegrated fact) {
        requireLiving(state, subject);
        if (!subject.equals(fact.residentId())
                || state.humanPopulation().nutrition(subject).lastEvaluatedTick() != fact.previousNutritionTick()
                || !state.humanPopulation().health(subject).starvation().equals(fact.previous())
                || !evaluate(state, subject, fact.atTick()).equals(fact.next()))
            throw new IllegalArgumentException("starvation fact lacks its exact resident, predecessor or elapsed effect");
        return state.withHumanPopulation(state.humanPopulation().withStarvation(subject, fact.next()));
    }
    private static void requireLiving(FrontierWorldState state, SubjectId resident) {
        var actor = state.actorLocations().get(resident);
        if (state.humanPopulation().resident(resident) == null || actor == null
                || actor.condition().status() != ActorLifeStatus.ALIVE)
            throw new IllegalArgumentException("starvation condition has no exact living resident");
    }
}
