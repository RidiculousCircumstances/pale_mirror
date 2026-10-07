package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.List;

/** Feeding owns its entrance checkpoint and meal completion, not the shared access arbiter. */
public final class ResidentMealServiceAccess implements ServiceAccessCapability {
    @Override public ServiceAccessDemand.Kind kind() { return ServiceAccessDemand.Kind.MEAL; }
    @Override public ServiceAccessDemand.Priority priority() { return ServiceAccessDemand.Priority.SELF_CARE; }

    @Override public List<ServiceAccessDemand> demands(FrontierWorldState state, SubjectId pointId) {
        var port = ServiceAccessCoordinator.port(state, pointId);
        var boundary = port.accessBoundary();
        return state.humanPopulation().meals().values().stream().filter(meal -> !meal.portable() && meal.depotId().equals(pointId))
                .map(meal -> new ServiceAccessDemand(identity(pointId, meal.residentId()), priority(),
                        ServiceAccessCoordinator.occupies(state, boundary, meal.residentId()) ? ServiceAccessDemand.Presence.OCCUPIED
                                : committedEntrance(meal, boundary) ? ServiceAccessDemand.Presence.ENTERING
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
    private static boolean committedEntrance(ResidentMeal meal, ServiceAccessBoundary boundary) {
        return meal.phase() == ResidentMeal.Phase.MOVE && meal.coldTravel().map(travel -> {
            var destination = travel.route().getLast();
            return boundary.occupied(destination.standingBody());
        }).orElse(false);
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
        if (meal.portable()) return body.equals(state.actorLocations().get(meal.residentId()).body());
        return body.equals(meal.clearingSurface().standingBody())
                && ServiceAccessCoordinator.boundary(state, meal.depotId()).cleared(body);
    }
}
