package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;

import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Read-only admission policy for a shared physical service boundary. The depot is
 * the first provider; each contender keeps its own exact meal or work owner. This
 * projection owns no second job, item ledger or due-time queue. Waiting processes
 * retain their normal schedule until the physical access boundary changes.
 */
public final class ServiceAccessCoordinator {
    private ServiceAccessCoordinator() { }

    private record Applicant(SubjectId jobId, SubjectId workerId, boolean alreadyAtPort) { }

    public static ServiceAccessBoundary boundary(FrontierWorldState state, SubjectId depotId) {
        return port(state, depotId).accessBoundary();
    }

    public static boolean witnessedActorMovementExit(FrontierWorldState state,
                                                     io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovement movement,
                                                     BodyPosition observedBody) {
        if (!(movement.context() instanceof io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovementContext.ServiceExit exit))
            return false;
        SubjectId depotId = exit.depotId();
        ActorLocation actor = state.actorLocations().get(movement.order().actorId());
        return actor != null && witnessedExit(boundary(state, depotId), actor.body(), observedBody);
    }

    /** Travelling is not service: residents may approach concurrently with independent bread claims. */
    public static boolean depotMayStartMeal(FrontierWorldState state, SubjectId depotId, SubjectId residentId) {
        ActorLocation actor = state.actorLocations().get(residentId);
        return actor != null && (port(state, depotId).accessBoundary().cleared(actor.body())
                || depotAvailableForMeal(state, depotId, residentId));
    }

    /** One deterministic turn owns the physical throat; other retained meals wait outside it. */
    public static boolean depotAvailableForMeal(FrontierWorldState state, SubjectId depotId,
                                                SubjectId residentId) {
        Objects.requireNonNull(state, "service access state");
        Objects.requireNonNull(depotId, "service access depot");
        Objects.requireNonNull(residentId, "service access resident");
        SettlementDepotServicePort servicePort = port(state, depotId);
        ServiceAccessBoundary access = servicePort.accessBoundary();
        var first = state.humanPopulation().meals().values().stream()
                .filter(meal -> meal.depotId().equals(depotId) && mealOccupiesAccess(state, meal, access))
                .min(Comparator.<ResidentMeal, Boolean>comparing(meal -> physicallyAdmitted(state, meal.residentId())).reversed()
                        .thenComparingLong(ResidentMeal::startedAtTick).thenComparing(ResidentMeal::residentId));
        boolean departing = state.actorMovements().values().stream().anyMatch(movement ->
                movement.context() instanceof io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovementContext.ServiceExit exit
                        && exit.depotId().equals(depotId)
                        && !awaitingBody(state, movement.order().actorId())
                        && access.occupied(currentBody(state, movement.order().actorId()))
                        && !movement.order().actorId().equals(residentId));
        // A committed final entrance leg is the sole contender for the
        // single-file throat until it arrives or is explicitly invalidated.
        // Long approaches to independent side pockets retain no permit.
        var entering = first.isPresent() ? Optional.<ResidentMeal>empty()
                : state.humanPopulation().meals().values().stream()
                    .filter(meal -> meal.depotId().equals(depotId)
                            && meal.phase() == ResidentMeal.Phase.MOVE
                            && meal.coldTravel().map(travel -> travel.route().getLast()
                                .equals(servicePort.serviceSurface())).orElse(false))
                    .min(Comparator.comparingLong(ResidentMeal::startedAtTick)
                            .thenComparing(ResidentMeal::residentId));
        boolean residentOwnsOccupiedTurn = first.isPresent() && first.orElseThrow().residentId().equals(residentId);
        return !departing && first.or(() -> entering).map(meal -> meal.residentId().equals(residentId)).orElse(true)
                // If an old COLD state contains both a meal body and a worker at
                // the port, the worker already yields to that meal above. Apply
                // the same precedence here or neither contender can ever move.
                && (residentOwnsOccupiedTurn || workApplicants(state, depotId).stream()
                    .filter(Applicant::alreadyAtPort)
                    .allMatch(applicant -> applicant.workerId().equals(residentId)));
    }

    /** A work owner may approach during meal travel, never while a meal body occupies the port. */
    public static boolean depotAvailableForWork(FrontierWorldState state, SubjectId depotId, SubjectId jobId,
                                                SubjectId workerId) {
        Objects.requireNonNull(state, "service access state");
        Objects.requireNonNull(depotId, "service access depot");
        Objects.requireNonNull(jobId, "service access job");
        Objects.requireNonNull(workerId, "service access worker");
        SettlementDepotServicePort servicePort = port(state, depotId);
        ServiceAccessBoundary access = servicePort.accessBoundary();
        var applicant = workApplicants(state, depotId).stream().findFirst();
        boolean mealAtPort = state.humanPopulation().meals().values().stream().anyMatch(meal ->
                meal.depotId().equals(depotId) && mealOccupiesAccess(state, meal, access)
                    && access.occupied(
                        state.actorLocations().get(meal.residentId()).body()));
        boolean mealEnteringPort = state.humanPopulation().meals().values().stream().anyMatch(meal ->
                meal.depotId().equals(depotId) && meal.phase() == ResidentMeal.Phase.MOVE
                    && meal.coldTravel().map(travel -> travel.route().getLast()
                        .equals(servicePort.serviceSurface())).orElse(false));
        boolean workerEating = state.humanPopulation().meals().containsKey(workerId);
        boolean departing = state.actorMovements().values().stream().anyMatch(movement ->
                movement.context() instanceof io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovementContext.ServiceExit exit
                        && exit.depotId().equals(depotId)
                        && !awaitingBody(state, movement.order().actorId())
                        && access.occupied(currentBody(state, movement.order().actorId())));
        return applicant.filter(first -> !mealAtPort && !mealEnteringPort && !departing && !workerEating)
                .map(first -> first.jobId().equals(jobId) && first.workerId().equals(workerId))
                .orElse(false);
    }

    public static boolean depotAvailableForHarvest(FrontierWorldState state, ResourceSiteHarvestJob job) {
        SubjectId settlementId = ResourceSiteHarvestGoal.depotPort(state, job).settlementId();
        return depotAvailableForWork(state, FrontierWorldState.depotId(settlementId), job.id(), job.workerId());
    }

    private static boolean mealOccupiesAccess(FrontierWorldState state, ResidentMeal meal, ServiceAccessBoundary access) {
        AmbientActorLease lease = state.ambientLeases().get(meal.residentId());
        // A frozen handoff location is not a live socket occupant. Admission confirms
        // its actual body first; a blocked creation cannot hold a phantom service turn.
        if (lease != null && lease.status() == AmbientLeaseStatus.PREPARED) return false;
        ActorLocation actor = state.actorLocations().get(meal.residentId());
        // Starting a meal reserves bread, not the depot's sole service socket.
        // Otherwise the entire approach and return journey serializes all meals.
        return actor == null || !access.cleared(actor.body());
    }
    private static boolean physicallyAdmitted(FrontierWorldState state, SubjectId actorId) {
        AmbientActorLease lease = state.ambientLeases().get(actorId);
        return lease != null && (lease.status() == AmbientLeaseStatus.HOT || lease.status() == AmbientLeaseStatus.DRAINING);
    }

    /** The same physical boundary test is used before HOT submission and by its reducer. */
    public static boolean witnessedMealExit(FrontierWorldState state, ResidentMeal meal, BodyPosition observedBody) {
        if (!meal.movesToClearance() || meal.pendingPhysicalStep().isPresent()) return false;
        ServiceAccessBoundary boundary = port(state, meal.depotId()).accessBoundary();
        ActorLocation actor = state.actorLocations().get(meal.residentId());
        if (actor == null || !boundary.occupied(actor.body()) || !boundary.cleared(observedBody)) return false;
        return witnessedExit(boundary, actor.body(), observedBody);
    }

    public static boolean witnessedBakeryExit(FrontierWorldState state, ProductionJob job, BodyPosition observedBody) {
        if (job.bakeryWork().isEmpty() || job.bakeryWork().orElseThrow().phase() != BakeryWorkState.Phase.DELIVERED)
            return false;
        SceneLease lease = state.sceneLeases().values().stream()
                .filter(candidate -> candidate.status() == SceneLeaseStatus.HOT
                        && FrontierSceneBehaviors.isProductionWork(candidate)
                        && FrontierSceneBehaviors.productionWork(candidate).jobId().equals(job.id()))
                .findFirst().orElse(null);
        if (lease == null) return false;
        BodyPosition previous = lease.memberBody(state.actorLocations(), job.workerId());
        ServiceAccessBoundary boundary = port(state, FrontierWorldState.depotId(job.settlementId())).accessBoundary();
        if (!boundary.occupied(previous) || !boundary.cleared(observedBody)) return false;
        return witnessedExit(boundary, previous, observedBody);
    }

    /** Field work also releases its depot turn before its next crop goal is reached. */
    public static boolean witnessedHarvestExit(FrontierWorldState state, ResourceSiteHarvestJob job,
                                               SceneLeaseId leaseId, BodyPosition observedBody) {
        if (ResourceSiteHarvestGoal.current(state, job).kind() == ResourceSiteHarvestGoal.Kind.DEPOT_SERVICE)
            return false;
        SceneLease lease = FrontierResourceSiteHarvestSceneSupport.requireHotLease(state, job, leaseId);
        ServiceAccessBoundary boundary = ResourceSiteHarvestGoal.depotPort(state, job).accessBoundary();
        return witnessedExit(boundary, lease.memberBody(state.actorLocations(), job.workerId()), observedBody);
    }

    /** A service permit ends at the first witnessed exit, independently of the owner's later route. */
    public static boolean witnessedExit(ServiceAccessBoundary boundary, BodyPosition previous,
                                        BodyPosition observed) {
        return boundary.occupied(previous) && boundary.cleared(observed);
    }

    private static List<Applicant> workApplicants(FrontierWorldState state, SubjectId depotId) {
        SettlementDepotServicePort port = port(state, depotId);
        List<Applicant> applicants = new ArrayList<>();
        for (ProductionJob job : state.productionJobs().values()) {
            if (job.bakeryWork().isEmpty() || !job.settlementId().equals(port.settlementId())) continue;
            if (state.humanPopulation().meals().containsKey(job.workerId())
                    || state.actorMovements().containsKey(job.workerId())) continue;
            BakeryWorkState.Phase phase = job.bakeryWork().orElseThrow().phase();
            SurfaceAnchor body = currentSurface(state, job.workerId());
            boolean atPort = atPort(port, body);
            if (phase == BakeryWorkState.Phase.DEPOT_PICKUP || phase == BakeryWorkState.Phase.DEPOT_DELIVERY
                    || atPort)
                applicants.add(new Applicant(job.id(), job.workerId(), atPort));
        }
        for (ResourceSiteHarvestJob job : state.resourceSites().sites().values().stream()
                .flatMap(site -> site.harvestJobs().values().stream()).toList()) {
            if (state.humanPopulation().meals().containsKey(job.workerId())
                    || state.actorMovements().containsKey(job.workerId())) continue;
            ResourceSiteHarvestGoal goal = ResourceSiteHarvestGoal.current(state, job);
            if (!ResourceSiteHarvestGoal.depotPort(state, job).settlementId().equals(port.settlementId())) continue;
            SurfaceAnchor body = currentSurface(state, job.workerId());
            boolean atPort = atPort(port, body);
            if (goal.kind() == ResourceSiteHarvestGoal.Kind.DEPOT_SERVICE || atPort)
                applicants.add(new Applicant(job.id(), job.workerId(), atPort));
        }
        applicants.sort(Comparator.comparing(Applicant::alreadyAtPort).reversed()
                .thenComparing(Applicant::jobId));
        return List.copyOf(applicants);
    }

    private static SurfaceAnchor currentSurface(FrontierWorldState state, SubjectId workerId) {
        if (awaitingBody(state, workerId)) return null;
        ActorLocation actor = state.actorLocations().get(workerId);
        return actor == null ? null : actor.supportingSurface();
    }
    private static boolean awaitingBody(FrontierWorldState state, SubjectId actorId) {
        AmbientActorLease ambient = state.ambientLeases().get(actorId);
        return ambient != null && ambient.status() == AmbientLeaseStatus.PREPARED
                || state.sceneLeases().values().stream().anyMatch(lease -> lease.status() == SceneLeaseStatus.PREPARED
                        && lease.retainsMemberCustody(actorId));
    }

    private static BodyPosition currentBody(FrontierWorldState state, SubjectId actorId) {
        ActorLocation actor = state.actorLocations().get(actorId);
        if (actor == null) throw new IllegalArgumentException("service exit has no exact actor body");
        return actor.body();
    }

    private static SettlementDepotServicePort port(FrontierWorldState state, SubjectId depotId) {
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
        return SettlementServiceAccessPoints.forSettlement(state, resident.settlementId()).stream()
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
        return ServiceClearanceTargets.select(SettlementServiceAccessPoints.forDepot(settlement, depot, knowledge,
                        SettlementServiceAccessPoints.population(state, settlement.id())),
                residentId, knowledge, excluded);
    }

    /** Final return requires the exact target; access may already have been released. */
    public static boolean cleared(FrontierWorldState state, ResidentMeal meal, BodyPosition body) {
        Objects.requireNonNull(state, "service access state");
        Objects.requireNonNull(meal, "service access meal");
        Objects.requireNonNull(body, "service access body");
        if (!body.equals(meal.clearingSurface().standingBody())) return false;
        SettlementDepotServicePort port = port(state, meal.depotId());
        return outsideThroat(port, body.supportingSurface());
    }

    private static boolean outsideThroat(SettlementDepotServicePort port, SurfaceAnchor surface) {
        return port.accessBoundary().cleared(surface.standingBody());
    }

    private static boolean atPort(SettlementDepotServicePort port, SurfaceAnchor surface) {
        return surface != null && !outsideThroat(port, surface);
    }
}
