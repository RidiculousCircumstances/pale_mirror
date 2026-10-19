package io.farfrontier.palemirror.frontier.v3.model.expedition;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.group.TransportGroupMissionPort;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import java.util.List;

/** Accepted round-trip geometry; the enclosing departure event owns mission, revision and time. */
public record ExpeditionJourneyProof(PedestrianPathProof outward, PedestrianPathProof returning) {
    public ExpeditionJourneyProof {
        java.util.Objects.requireNonNull(outward); java.util.Objects.requireNonNull(returning);
        if (!outward.origin().equals(returning.destination()) || !outward.destination().equals(returning.origin()))
            throw new IllegalArgumentException("expedition estimate needs matching round-trip endpoints");
    }
    public ExpeditionJourneyProof(List<SurfaceAnchor> outward, List<SurfaceAnchor> returning) {
        this(PedestrianPathProof.from(outward), PedestrianPathProof.from(returning));
    }
    public static ExpeditionJourneyProof select(FrontierWorldState state, TransportMission mission) {
        return select(state, mission, java.util.Objects.requireNonNull(state.unitGroups().groups().get(mission.groupId()), "expedition roster"));
    }
    public static ExpeditionJourneyProof select(FrontierWorldState state, TransportMission mission,
            io.farfrontier.palemirror.frontier.v3.model.group.UnitGroup group) {
        var knowledge = knowledge(state, mission, group);
        var retained = mission.supplies().map(ExpeditionSupplyLoad::journey);
        if (retained.isPresent()) {
            var prior = retained.orElseThrow();
            if (!prior.outward().origin().equals(mission.homeRendezvous())
                    || !prior.outward().destination().equals(mission.destinationRendezvous()))
                throw new IllegalArgumentException("retained expedition changed its rendezvous");
            if (knowledge.supportsPath(prior.outward()) && knowledge.supportsPath(prior.returning())) return prior;
        }
        return new ExpeditionJourneyProof(knowledge.plannedBetween(mission.homeRendezvous(), mission.destinationRendezvous()),
                knowledge.plannedBetween(mission.destinationRendezvous(), mission.homeRendezvous()));
    }
    public void require(FrontierWorldState state, TransportMission mission) {
        require(state, mission, java.util.Objects.requireNonNull(state.unitGroups().groups().get(mission.groupId()), "expedition roster"));
    }
    public void require(FrontierWorldState state, TransportMission mission,
            io.farfrontier.palemirror.frontier.v3.model.group.UnitGroup group) {
        if (!outward.origin().equals(mission.homeRendezvous()) || !outward.destination().equals(mission.destinationRendezvous()))
            throw new IllegalArgumentException("expedition estimate substituted its declared rendezvous");
        var knowledge = knowledge(state, mission, group);
        knowledge.requirePath(outward); knowledge.requirePath(returning);
    }
    private static KnownPedestrianRouteKnowledge knowledge(FrontierWorldState state, TransportMission mission,
            io.farfrontier.palemirror.frontier.v3.model.group.UnitGroup group) {
        if (!group.id().equals(mission.groupId())) throw new IllegalArgumentException("route proof has a foreign roster");
        return TransportGroupMissionPort.knowledgeForEndpoints(state, mission.sender(), mission.receiver())
                .forActors(state, group.members().stream().map(io.farfrontier.palemirror.frontier.v3.model.group.UnitGroup.Member::actorId).toList());
    }
}
