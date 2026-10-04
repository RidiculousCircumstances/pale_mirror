package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityBodyCheckpoint;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind;
import io.farfrontier.palemirror.frontier.v3.model.navigation.TraversalRejoin;
import java.util.LinkedHashMap;

/** Family-owned continuation composed atomically with the common physical departure. */
final class EngineeringBodyCheckpoint {
    private EngineeringBodyCheckpoint() { }
    static ActorActivityBodyCheckpoint.Acknowledgement acknowledge(ActorActivityBodyCheckpoint.Request request, EngineeringWorkOrder owner) {
        var update = FrontierWorldStateUpdate.begin();
        // A work effect/terminal explanation is not an advancing spatial journey.
        if (request.execution().activityKind() != ActorActivityKind.ENGINEERING_ASSEMBLY || owner.assembly().isEmpty()
                || (!owner.building() && !owner.readyForToolReturn()))
            return new ActorActivityBodyCheckpoint.Acknowledgement(request, update);
        var member = owner.assembly().orElseThrow().members().get(request.execution().actorId());
        var observed = request.observedPosition().supportingSurface();
        if (member.currentSurface().equals(observed)) return new ActorActivityBodyCheckpoint.Acknowledgement(request, update);
        var state = request.expectedState();
        var path = EngineeringJourneyKnowledge.rejoin(state, owner, request.execution().actorId(), observed);
        var next = owner.assembly().orElseThrow().checkpoint(request.execution().actorId(), new TraversalRejoin(path, 0));
        switch (owner) {
            case RouteConstruction construction -> {
                var projects = new LinkedHashMap<>(state.routeConstructions());
                projects.put(owner.id(), construction.withAdvancedAssembly(next));
                update.routeConstructions(projects);
            }
            case RouteMaintenance maintenance -> {
                var projects = new LinkedHashMap<>(state.routeMaintenances());
                projects.put(owner.id(), maintenance.withAdvancedAssembly(next));
                update.routeMaintenances(projects);
            }
        }
        return new ActorActivityBodyCheckpoint.Acknowledgement(request, update);
    }
}
