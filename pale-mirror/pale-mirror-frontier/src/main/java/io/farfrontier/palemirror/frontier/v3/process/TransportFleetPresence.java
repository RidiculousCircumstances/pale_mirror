package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.util.*;

/** Fleet policy supplies passive purpose; shared execution/courtesy alone authorize motion. */
final class TransportFleetPresence {
    record Selection(FrontierWorldState state, List<ProposedEvent> events) { }
    private TransportFleetPresence() { }

    /** Reuses settlement review, including recovered worlds; no physical writer or retry queue. */
    static Selection select(FrontierWorldState state, SubjectId home, long atTick) {
        FrontierWorldStateSupport.settlement(state.bootstrap(), home);
        var events = new ArrayList<ProposedEvent>();
        for (var asset : state.transportFleet().assets().values().stream()
                .filter(value -> value.homeSettlementId().equals(home))
                .sorted(Comparator.comparing(value -> value.actorId())).toList()) {
            var actor = asset.actorId();
            var execution = state.actorExecutions().actors().get(actor);
            if (asset.missionId().isPresent() || state.actorLocations().get(actor).condition().status() != ActorLifeStatus.ALIVE
                    || execution != null && (execution.current().isPresent() || execution.suspended().isPresent())) continue;
            var presence = state.actorExecutions().next(actor, ActorActivityKind.PRESENCE, actor);
            ActorExecutionComposition.CAPABILITIES.require(ActorActivityKind.PRESENCE).validateReference(state, presence);
            state = ActorExecutionComposition.LIFECYCLE.prepareVacant(state, presence)
                    .commit(state, FrontierWorldStateUpdate.begin());
            events.add(new ProposedEvent(actor, new ActorPresenceStarted(presence, atTick)));
        }
        return new Selection(state, List.copyOf(events));
    }
}
