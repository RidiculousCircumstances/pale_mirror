package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.List;
import java.util.Set;

/** Feeding owns its entrance checkpoint and meal completion, not the shared access arbiter. */
public final class ResidentMealServiceAccess implements ServiceAccessCapability {
    @Override public ServiceAccessDemand.Kind kind() { return ServiceAccessDemand.Kind.MEAL; }
    @Override public ServiceAccessDemand.Priority priority() { return ServiceAccessDemand.Priority.SELF_CARE; }

    @Override public List<ServiceAccessDemand> demands(FrontierWorldState state, SubjectId pointId) {
        var port = ServiceAccessCoordinator.port(state, pointId);
        var boundary = port.accessBoundary();
        var egress = SettlementServiceAccessPoints.egress(state, port);
        return state.humanPopulation().meals().values().stream().filter(meal -> meal.depotId().equals(pointId))
                .map(meal -> new ServiceAccessDemand(identity(pointId, meal.residentId()), priority(),
                        occupies(state, meal, boundary, egress) ? ServiceAccessDemand.Presence.OCCUPIED
                                : committedEntrance(meal, boundary, egress) ? ServiceAccessDemand.Presence.ENTERING
                                : ServiceAccessDemand.Presence.APPROACH,
                        meal.startedAtTick(), physicallyAdmitted(state, meal.residentId()))).toList();
    }

    private static ServiceAccessDemand.Identity identity(SubjectId pointId, SubjectId residentId) {
        return new ServiceAccessDemand.Identity(ServiceAccessDemand.Kind.MEAL, residentId, residentId, pointId);
    }
    public static boolean available(FrontierWorldState state, SubjectId pointId, SubjectId residentId) {
        return ServiceAccessCoordinator.available(state, identity(pointId, residentId));
    }
    public static boolean mayStart(FrontierWorldState state, SubjectId pointId, SubjectId residentId) {
        var actor = state.actorLocations().get(residentId);
        return actor != null && (ServiceAccessCoordinator.boundary(state, pointId).cleared(actor.body())
                || available(state, pointId, residentId));
    }
    private static boolean committedEntrance(ResidentMeal meal, ServiceAccessBoundary boundary, Set<SurfaceAnchor> egress) {
        return meal.phase() == ResidentMeal.Phase.MOVE && meal.coldTravel().map(travel -> {
            var destination = travel.route().getLast();
            return boundary.occupied(destination.standingBody()) || egress.contains(destination);
        }).orElse(false);
    }
    private static boolean occupies(FrontierWorldState state, ResidentMeal meal, ServiceAccessBoundary access,
                                     Set<SurfaceAnchor> egress) {
        var lease = state.ambientLeases().get(meal.residentId());
        if (lease != null && lease.status() == AmbientLeaseStatus.PREPARED) return false;
        var actor = state.actorLocations().get(meal.residentId());
        return actor == null || !access.cleared(actor.body())
                || meal.phase() == ResidentMeal.Phase.MOVE && egress.contains(actor.supportingSurface());
    }
    private static boolean physicallyAdmitted(FrontierWorldState state, SubjectId actorId) {
        var lease = state.ambientLeases().get(actorId);
        return lease != null && (lease.status() == AmbientLeaseStatus.HOT || lease.status() == AmbientLeaseStatus.DRAINING);
    }
    public static boolean witnessedExit(FrontierWorldState state, ResidentMeal meal, BodyPosition observedBody) {
        if (!meal.movesToClearance() || meal.pendingPhysicalStep().isPresent()) return false;
        var actor = state.actorLocations().get(meal.residentId());
        return actor != null && ServiceAccessCoordinator.witnessedExit(
                ServiceAccessCoordinator.boundary(state, meal.depotId()), actor.body(), observedBody);
    }
    public static boolean cleared(FrontierWorldState state, ResidentMeal meal, BodyPosition body) {
        return body.equals(meal.clearingSurface().standingBody())
                && ServiceAccessCoordinator.boundary(state, meal.depotId()).cleared(body);
    }
}
