package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;

/** Economic invalidation port. Producers name an actual party or changed container; they do not run a market policy. */
public final class GoodsParticipantWakeup {
    public static final String OPPORTUNITY = "frontier.goods.participant.opportunity";
    public static List<ProposedEvent> party(FrontierWorldState state, SubjectId party, String cause, long now) {
        if (!state.companies().goodsTrade().participants().participants().containsKey(party)) return List.of();
        var action = new ScheduledAction(new ScheduleId("schedule:goods-opportunity/" + WorkOpportunityIdentity.digest(party.value() + "|" + cause + "|" + now)),
                new SimInstant(Math.addExact(now, 1)), 0, party, OPPORTUNITY, 1);
        return List.of(new ProposedEvent(party, new ScheduleEffect.ReconsiderationRequested(action)));
    }
    public static List<ProposedEvent> container(FrontierWorldState state, SubjectId container, String cause, long now) {
        return state.companies().goodsTrade().participants().participants().values().stream()
                .filter(value -> value.endpoint().containerId().equals(container)).sorted(Comparator.comparing(value -> value.party().id()))
                .flatMap(value -> party(state, value.party().id(), cause, now).stream()).toList();
    }
    private GoodsParticipantWakeup() { }
}
