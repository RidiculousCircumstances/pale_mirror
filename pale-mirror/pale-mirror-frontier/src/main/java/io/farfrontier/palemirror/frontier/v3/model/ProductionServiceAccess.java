package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.ArrayList;
import java.util.List;

/** Production-owned interpretation of pickup, delivery and witnessed departure. */
public final class ProductionServiceAccess implements ServiceAccessCapability {
    @Override public ServiceAccessDemand.Kind kind() { return ServiceAccessDemand.Kind.PRODUCTION; }
    @Override public ServiceAccessDemand.Priority priority() { return ServiceAccessDemand.Priority.WORK; }
    @Override public java.util.Set<SubjectId> wakePoints(FrontierWorldState previous, FrontierWorldState next) {
        var points = new java.util.HashSet<SubjectId>();
        if (previous.productionJobs() == next.productionJobs() && previous.productionStations() == next.productionStations()) return java.util.Set.of();
        for (var state : List.of(previous, next)) state.productionJobs().values().stream()
                .filter(job -> job.bakeryWork().isPresent())
                .filter(job -> !java.util.Objects.equals(previous.productionJobs().get(job.id()), next.productionJobs().get(job.id()))
                        || !java.util.Objects.equals(previous.productionStations().machines().get(job.bakeryWork().orElseThrow().stationId()),
                            next.productionStations().machines().get(job.bakeryWork().orElseThrow().stationId())))
                .forEach(job -> points.add(point(state, job)));
        return java.util.Set.copyOf(points);
    }

    /** Resource ownership endpoints and the current physical service point are different relations. */
    public static SubjectId point(FrontierWorldState state, ProductionJob job) {
        var work = job.bakeryWork().orElseThrow();
        return switch (work.phase()) {
            case DEPOT_PICKUP, DEPOT_DELIVERY, DELIVERED -> BakeryStorage.current(job);
            case STATION_LOAD, PROCESSING, STATION_UNLOAD -> ProductionStationEnergy.record(state, work.stationId()).id();
        };
    }

    public static boolean waitingForServiceResource(FrontierWorldState state, HumanAssignment assignment) {
        FrontierProductionWorkSceneSupport.executionCheckpoint(state, assignment);
        var job = state.productionJobs().get(assignment.ownerId().orElseThrow());
        // A destination resource wait can yield that destination's temporary
        // footprint, not an unrelated source where the worker must depart normally.
        return !operationEligible(state, job)
                && ServiceAccessCoordinator.turnoverPoint(state, assignment.residentId())
                    .filter(current -> current.pointId().equals(point(state, job))).isPresent();
    }

    @Override public List<ServiceAccessDemand> demands(FrontierWorldState state, SubjectId pointId) {
        var boundary = ServiceAccessCoordinator.boundary(state, pointId);
        var result = new ArrayList<ServiceAccessDemand>();
        for (var job : state.productionJobs().values()) {
            if (job.bakeryWork().isEmpty()
                    || !(point(state, job).equals(pointId) || BakeryStorage.source(job).equals(pointId) || BakeryStorage.destination(job).equals(pointId))
                    || state.humanPopulation().meals().containsKey(job.workerId())
                    || state.actorMovements().containsKey(job.workerId())
                    || !operationEligible(state, job)) continue;
            var phase = job.bakeryWork().orElseThrow().phase();
            boolean occupied = ServiceAccessCoordinator.occupies(state, boundary, job.workerId());
            if (phase != BakeryWorkState.Phase.DELIVERED && point(state, job).equals(pointId) || occupied)
                result.add(new ServiceAccessDemand(identity(job, pointId), priority(), occupied
                        ? ServiceAccessDemand.Presence.OCCUPIED : ServiceAccessDemand.Presence.APPROACH, 0L, false));
        }
        return List.copyOf(result);
    }

    private static ServiceAccessDemand.Identity identity(ProductionJob job, SubjectId pointId) {
        return new ServiceAccessDemand.Identity(ServiceAccessDemand.Kind.PRODUCTION, job.id(), job.workerId(), pointId);
    }

    public static boolean available(FrontierWorldState state, ProductionJob job) {
        return operationEligible(state, job)
                && ServiceAccessCoordinator.available(state, identity(job, point(state, job)));
    }

    /** Storage backpressure retains the job/cargo, not an exclusive service turn. */
    private static boolean operationEligible(FrontierWorldState state, ProductionJob job) {
        var work = job.bakeryWork().orElseThrow();
        if (work.pendingPhysicalStep().isEmpty() && work.phase() == BakeryWorkState.Phase.STATION_LOAD) {
            var station = ProductionStationEnergy.record(state, work.stationId());
            return !ReferenceContainerCustody.blocksCanonicalUse(state, station.id())
                    && ProductionStationProcessing.canStart(state, work.stationId(), work.recipeId(), job.inputCount());
        }
        if (work.phase() == BakeryWorkState.Phase.PROCESSING
                && state.bootstrap().ruleset().production().require(work.recipeId()).processingMode()
                    == ProductionRecipe.ProcessingMode.AUTOMATIC) return false;
        if (work.pendingPhysicalStep().isPresent() || work.phase() != BakeryWorkState.Phase.DEPOT_DELIVERY) return true;
        var depot = BakeryStorage.current(job);
        return ReferenceContainerCustody.hasLiveCustody(state, depot)
                ? ProductionOutputCapacity.deliverySlot(state, job).isPresent()
                : !ProductionOutputCapacity.depotDeliveryUnavailable(state, job);
    }

    public static boolean mayAdvance(FrontierWorldState state, ProductionJob job, SurfaceAnchor destination) {
        if (!operationEligible(state, job))
            return ServiceAccessCoordinator.boundary(state, point(state, job))
                    .cleared(destination.standingBody());
        return ServiceAccessCoordinator.mayAdvance(state,
                identity(job, point(state, job)), destination);
    }

    public static boolean witnessedExit(FrontierWorldState state, ProductionJob job, BodyPosition observedBody) {
        if (job.bakeryWork().isEmpty()) return false;
        var phase = job.bakeryWork().orElseThrow().phase();
        if (phase == BakeryWorkState.Phase.DEPOT_PICKUP || phase == BakeryWorkState.Phase.DEPOT_DELIVERY) return false;
        SceneLease lease = state.sceneLeases().values().stream()
                .filter(candidate -> candidate.status() == SceneLeaseStatus.HOT
                        && FrontierSceneBehaviors.isProductionWork(candidate)
                        && FrontierSceneBehaviors.productionWork(candidate).jobId().equals(job.id()))
                .findFirst().orElse(null);
        return lease != null && ServiceAccessCoordinator.witnessedExit(
                ServiceAccessCoordinator.boundary(state, BakeryStorage.current(job)),
                lease.memberBody(state.actorLocations(), job.workerId()), observedBody);
    }
}
