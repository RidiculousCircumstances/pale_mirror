package io.farfrontier.palemirror.frontier.v3.model.expedition;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.group.TransportGroupMissionPort;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import java.util.List;

/** Accepted round-trip geometry; the enclosing departure event owns mission, revision and time. */
public record ExpeditionJourneyProof(List<SurfaceAnchor> outward, List<SurfaceAnchor> returning) {
    public ExpeditionJourneyProof {
        outward = bounded(outward); returning = bounded(returning);
        if (!outward.getFirst().equals(returning.getLast()) || !outward.getLast().equals(returning.getFirst()))
            throw new IllegalArgumentException("expedition estimate needs matching round-trip endpoints");
    }
    private static List<SurfaceAnchor> bounded(List<SurfaceAnchor> route) {
        route = List.copyOf(route);
        if (route.isEmpty() || route.size() > TimedKnownRoute.MAX_SURFACES)
            throw new IllegalArgumentException("expedition estimate exceeds bounded known geometry");
        if (route.size() > 1) new PedestrianRouteReceipt(route);
        return route;
    }
    public static ExpeditionJourneyProof select(FrontierWorldState state, TransportMission mission) {
        return select(state, mission, java.util.Objects.requireNonNull(state.unitGroups().groups().get(mission.groupId()), "expedition roster"));
    }
    public static ExpeditionJourneyProof select(FrontierWorldState state, TransportMission mission,
            io.farfrontier.palemirror.frontier.v3.model.group.UnitGroup group) {
        var knowledge = knowledge(state, mission, group);
        return new ExpeditionJourneyProof(knowledge.plannedBetween(mission.homeRendezvous(), mission.destinationRendezvous()),
                knowledge.plannedBetween(mission.destinationRendezvous(), mission.homeRendezvous()));
    }
    public void require(FrontierWorldState state, TransportMission mission) {
        require(state, mission, java.util.Objects.requireNonNull(state.unitGroups().groups().get(mission.groupId()), "expedition roster"));
    }
    public void require(FrontierWorldState state, TransportMission mission,
            io.farfrontier.palemirror.frontier.v3.model.group.UnitGroup group) {
        if (!outward.getFirst().equals(mission.homeRendezvous()) || !outward.getLast().equals(mission.destinationRendezvous()))
            throw new IllegalArgumentException("expedition estimate substituted its declared rendezvous");
        var knowledge = knowledge(state, mission, group);
        knowledge.requireRoute(outward); knowledge.requireRoute(returning);
    }
    private static KnownPedestrianRouteKnowledge knowledge(FrontierWorldState state, TransportMission mission,
            io.farfrontier.palemirror.frontier.v3.model.group.UnitGroup group) {
        if (!group.id().equals(mission.groupId())) throw new IllegalArgumentException("route proof has a foreign roster");
        return TransportGroupMissionPort.knowledgeForEndpoints(state, mission.sender(), mission.receiver())
                .forActors(state, group.members().stream().map(io.farfrontier.palemirror.frontier.v3.model.group.UnitGroup.Member::actorId).toList());
    }
}
