package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Read-only shared-boundary arbitration. Registered families own business stages and access demand. */
public final class ServiceAccessCoordinator {
    private ServiceAccessCoordinator() { }

    /** Bulk/group events can move several actors even when their subject is not an actor. */
    public static java.util.Set<SubjectId> wakePoints(FrontierWorldState previous, FrontierWorldState next) {
        var actors = new java.util.HashSet<SubjectId>();
        changedActors(previous.actorLocations(), next.actorLocations(), actors);
        changedActors(previous.actorMovements(), next.actorMovements(), actors);
        changedActors(previous.ambientLeases(), next.ambientLeases(), actors);
        var points = new java.util.HashSet<SubjectId>();
        for (SubjectId actor : actors) {
            points.addAll(wakePoints(previous, actor));
            points.addAll(wakePoints(next, actor));
        }
        return java.util.Set.copyOf(points);
    }

    private static <T> void changedActors(java.util.Map<SubjectId, T> previous,
                                         java.util.Map<SubjectId, T> next, java.util.Set<SubjectId> actors) {
        if (previous == next) return;
        previous.forEach((id, value) -> { if (!Objects.equals(value, next.get(id))) actors.add(id); });
        next.forEach((id, value) -> { if (!previous.containsKey(id)) actors.add(id); });
    }

    /**
     * Address an actor's actual service footprint, not their home settlement.
     * Both sides of a transition are needed: completing an exit removes its
     * context and can leave the body outside the point it just released.
     */
    public static java.util.Set<SubjectId> wakePoints(FrontierWorldState state, SubjectId actorId) {
        var points = new java.util.HashSet<SubjectId>();
        var movement = state.actorMovements().get(actorId);
        if (movement != null) movement.context().clearancePoint().ifPresent(points::add);
        var actor = state.actorLocations().get(actorId);
        if (actor != null) state.bootstrap().settlements().stream()
                .flatMap(settlement -> settlement.structures().stream())
                .filter(structure -> structure.kind() == StructureKind.DEPOT)
                .map(SettlementDepotServicePort::forDepot)
                .filter(port -> port.accessBoundary().occupied(actor.body()))
                .forEach(port -> points.add(FrontierWorldState.depotId(port.settlementId())));
        return java.util.Set.copyOf(points);
    }

    public static ServiceAccessBoundary boundary(FrontierWorldState state, SubjectId pointId) {
        return port(state, pointId).accessBoundary();
    }

    public static boolean witnessedActorMovementExit(FrontierWorldState state,
                                                     io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovement movement,
                                                     BodyPosition observedBody) {
        if (movement.context().clearancePoint().isEmpty()) return false;
        SubjectId depotId = movement.context().clearancePoint().orElseThrow();
        ActorLocation actor = state.actorLocations().get(movement.order().actorId());
        return actor != null && witnessedExit(boundary(state, depotId), actor.body(), observedBody);
    }
    public static boolean available(FrontierWorldState state, ServiceAccessDemand.Identity requested) {
        Objects.requireNonNull(state, "service access state");
        Objects.requireNonNull(requested, "complete service identity");
        var access = boundary(state, requested.pointId());
        var demands = ServiceAccessCapabilities.current(state, requested.pointId());
        var selfCare = demands.stream().filter(demand -> demand.priority() == ServiceAccessDemand.Priority.SELF_CARE).toList();
        var entering = selfCare.stream().filter(demand -> demand.presence() == ServiceAccessDemand.Presence.ENTERING)
                .min(Comparator.comparingLong(ServiceAccessDemand::requestedAtTick)
                        .thenComparing(demand -> demand.identity().actorId()));
        var workers = demands.stream().filter(demand -> demand.priority() == ServiceAccessDemand.Priority.WORK)
                .sorted(Comparator.<ServiceAccessDemand, Boolean>comparing(demand ->
                        demand.presence() != ServiceAccessDemand.Presence.OCCUPIED)
                        .thenComparing(demand -> demand.identity().ownerId())).toList();
        boolean requestingSelfCare = ServiceAccessCapabilities.priority(requested) == ServiceAccessDemand.Priority.SELF_CARE;
        boolean departing = state.actorMovements().values().stream().anyMatch(movement ->
                movement.context().clearancePoint().equals(Optional.of(requested.pointId()))
                        && !awaitingBody(state, movement.order().actorId())
                        && access.occupied(currentBody(state, movement.order().actorId()))
                        && (!requestingSelfCare || !movement.order().actorId().equals(requested.actorId())));
        // Priority selects the next visitor; it cannot revoke an incumbent's
        // atomic service/clearance turn while another visitor approaches.
        var incumbent = demands.stream().filter(demand -> demand.presence() == ServiceAccessDemand.Presence.OCCUPIED)
                .min(Comparator.comparing(ServiceAccessDemand::priority)
                        .thenComparing(demand -> !demand.physicallyAdmitted())
                        .thenComparingLong(ServiceAccessDemand::requestedAtTick)
                        .thenComparing(demand -> demand.identity().ownerId()));
        if (departing) return false;
        if (incumbent.isPresent()) return incumbent.orElseThrow().identity().equals(requested);
        if (requestingSelfCare)
            return entering.map(demand -> demand.identity().equals(requested)).orElse(true);
        return entering.isEmpty() && workers.stream().findFirst()
                .filter(demand -> demand.identity().equals(requested)).isPresent();
    }

    /** COLD transit is shared; only entry into the service boundary needs a turn. */
    public static boolean mayAdvance(FrontierWorldState state, ServiceAccessDemand.Identity requested,
                                     SurfaceAnchor destination) {
        Objects.requireNonNull(destination, "service approach destination");
        Objects.requireNonNull(requested, "complete service approach identity");
        if (ServiceAccessCapabilities.current(state, requested.pointId()).stream()
                .noneMatch(demand -> demand.identity().equals(requested)))
            throw new IllegalArgumentException("service approach has no exact current owner declaration");
        return boundary(state, requested.pointId()).cleared(destination.standingBody()) || available(state, requested);
    }

    /** Access ends at the first witnessed exit, never on a later work or return-route endpoint. */
    public static boolean witnessedExit(ServiceAccessBoundary boundary, BodyPosition previous, BodyPosition observed) {
        return boundary.occupied(previous) && boundary.cleared(observed);
    }

    static boolean occupies(FrontierWorldState state, ServiceAccessBoundary access, SubjectId actorId) {
        return !awaitingBody(state, actorId) && access.occupied(currentBody(state, actorId));
    }

    private static boolean awaitingBody(FrontierWorldState state, SubjectId actorId) {
        AmbientActorLease ambient = state.ambientLeases().get(actorId);
        return ambient != null && ambient.status() == AmbientLeaseStatus.PREPARED
                || state.sceneLeases().values().stream().anyMatch(lease -> lease.status() == SceneLeaseStatus.PREPARED
                        && lease.retainsMemberCustody(actorId));
    }

    private static BodyPosition currentBody(FrontierWorldState state, SubjectId actorId) {
        ActorLocation actor = state.actorLocations().get(actorId);
        if (actor == null) throw new IllegalArgumentException("service access has no exact actor position");
        return actor.body();
    }

    static SettlementDepotServicePort port(FrontierWorldState state, SubjectId depotId) {
        ContainerRecord container = Objects.requireNonNull(state.inventory().containers().get(depotId),
                "unknown depot service container");
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), container.ownerId());
        if (!FrontierWorldState.depotId(settlement.id()).equals(depotId))
            throw new IllegalArgumentException("service container is not the settlement's declared depot");
        SettlementStructure depot = settlement.structures().stream()
                .filter(structure -> structure.kind() == StructureKind.DEPOT)
                .reduce((left, right) -> { throw new IllegalArgumentException("duplicate depot service identity"); })
                .orElseThrow(() -> new IllegalArgumentException("unknown depot service identity"));
        return SettlementDepotServicePort.forDepot(depot);
    }

    /** Derived turnover request; activity owns whether and when this occupant may leave. */
    public static Optional<ServiceAccessPoint> turnoverPoint(FrontierWorldState state, SubjectId residentId) {
        ResidentProfile resident = state.humanPopulation().resident(residentId);
        ActorLocation actor = state.actorLocations().get(residentId);
        if (resident == null || actor == null) return Optional.empty();
        // A service visitor need not belong to the facility's settlement.
        // The declared point owns geometry; residence owns neither occupancy nor exit.
        return state.bootstrap().settlements().stream()
                .flatMap(settlement -> SettlementServiceAccessPoints.forSettlement(state, settlement.id()).stream())
                .filter(point -> ServiceAreaDestinations.temporary(point, actor.supportingSurface())).findFirst();
    }

    /**
     * A personal clear, materialized apron spot, selected before the permit is taken and then
     * retained by the meal. Another resident's current body is never chosen as an exit.
     */
    public static Optional<SurfaceAnchor> mealClearingSurface(FrontierWorldState state, SubjectId residentId) {
        ResidentProfile resident = Objects.requireNonNull(state.humanPopulation().resident(residentId), "service resident");
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), resident.settlementId());
        SettlementDepotServicePort port = port(state, FrontierWorldState.depotId(settlement.id()));
        SettlementStructure depot = settlement.structures().stream()
                .filter(structure -> structure.id().equals(port.depotId())).findFirst().orElseThrow();
        java.util.Set<SurfaceAnchor> excluded = ServiceDestinationClaims.excludedFor(state, residentId);
        var knowledge = KnownPedestrianRouteKnowledge.forSettlement(state, settlement.id(), List.of(
                        new KnownPedestrianRouteKnowledge.Passage(depot,
                                KnownPedestrianRouteKnowledge.Passage.Reach.PUBLIC_ACCESS)));
        return ServiceClearanceTargets.select(SettlementServiceAccessPoints.forDepot(state, settlement, depot),
                residentId, knowledge, excluded);
    }

}
