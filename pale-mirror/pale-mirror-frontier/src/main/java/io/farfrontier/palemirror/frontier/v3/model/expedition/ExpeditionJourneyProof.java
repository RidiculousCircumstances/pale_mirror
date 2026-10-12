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
        var knowledge = TransportGroupMissionPort.knowledgeForEndpoints(state, mission.sender(), mission.receiver());
        return new ExpeditionJourneyProof(knowledge.plannedBetween(mission.homeRendezvous(), mission.destinationRendezvous()),
                knowledge.plannedBetween(mission.destinationRendezvous(), mission.homeRendezvous()));
    }
    public void require(FrontierWorldState state, TransportMission mission) {
        if (!outward.getFirst().equals(mission.homeRendezvous()) || !outward.getLast().equals(mission.destinationRendezvous()))
            throw new IllegalArgumentException("expedition estimate substituted its declared rendezvous");
        var knowledge = TransportGroupMissionPort.knowledgeForEndpoints(state, mission.sender(), mission.receiver());
        knowledge.requireRoute(outward); knowledge.requireRoute(returning);
    }
}
