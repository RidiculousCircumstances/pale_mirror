package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;

/** Settles old-rate labour through the registered owner before replacing stats. */
public final class ResidentWorkModifiersProcess {
    private ResidentWorkModifiersProcess() { }
    public static FrontierWorldState reduce(FrontierWorldState state, SubjectId subject, ResidentWorkModifiersChanged change) {
        var resident = state.humanPopulation().resident(subject);
        var actor = state.actorLocations().get(subject);
        if (!subject.equals(change.residentId()) || resident == null || actor == null
                || actor.condition().status() != ActorLifeStatus.ALIVE
                || !resident.characteristics().workModifiers().equals(change.previous()))
            throw new IllegalArgumentException("work modifier edit has a stale or foreign resident");
        state = ActivityExecutionCapabilities.pauseLabour(state,
                HumanAssignmentProjection.compile(state).assignment(subject), change.atTick());
        return state.withHumanPopulation(state.humanPopulation().changeWorkModifiers(subject,
                change.previous(), change.next()));
    }
}
