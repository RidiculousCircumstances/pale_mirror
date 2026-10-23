package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.ArrayList;
import java.util.List;

/** Production-owned interpretation of pickup, delivery and witnessed departure. */
public final class ProductionServiceAccess implements ServiceAccessCapability {
    public sealed interface ResumeWait { String label(); }
    public enum ServiceWait implements ResumeWait {
        STATION_CUSTODY, AUTOMATIC_PROCESSING, OUTPUT_STORAGE_CAPACITY;
        @Override public String label() { return name(); }
    }
    public record StationWait(ProductionStationProcessing.StartReadiness readiness) implements ResumeWait {
        public StationWait {
            java.util.Objects.requireNonNull(readiness);
            if (readiness == ProductionStationProcessing.StartReadiness.READY)
                throw new IllegalArgumentException("ready station has no wait");
        }
        @Override public String label() { return readiness.name(); }
    }
    @Override public ServiceAccessDemand.Kind kind() { return ServiceAccessDemand.Kind.PRODUCTION; }
    @Override public ServiceAccessDemand.Priority priority() { return ServiceAccessDemand.Priority.WORK; }
    @Override public java.util.Set<SubjectId> wakePoints(FrontierWorldState previous, FrontierWorldState next) {
        var points = new java.util.HashSet<SubjectId>();
        if (previous.productionJobs() == next.productionJobs() && previous.productionStations() == next.productionStations()) return java.util.Set.of();
        for (var state : List.of(previous, next)) state.productionJobs().values().stream()
                .filter(job -> job.stationWork().isPresent())
                .filter(job -> !java.util.Objects.equals(previous.productionJobs().get(job.id()), next.productionJobs().get(job.id()))
                        || !java.util.Objects.equals(previous.productionStations().machines().get(job.stationWork().orElseThrow().stationId()),
                            next.productionStations().machines().get(job.stationWork().orElseThrow().stationId())))
                .forEach(job -> points.add(point(state, job)));
        return java.util.Set.copyOf(points);
    }

    /** Resource ownership endpoints and the current physical service point are different relations. */
    public static SubjectId point(FrontierWorldState state, ProductionJob job) {
        var work = job.stationWork().orElseThrow();
        return switch (work.phase()) {
            case TARGET_PICKUP, DEPOT_PICKUP, DEPOT_DELIVERY, DELIVERED -> StationWorkStorage.current(job);
            case TARGET_LOAD, STATION_LOAD, PROCESSING, STATION_UNLOAD -> ProductionStationEnergy.record(state, work.stationId()).id();
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
    public static boolean mayResumeWork(FrontierWorldState state, HumanAssignment assignment) {
        FrontierProductionWorkSceneSupport.executionCheckpoint(state, assignment);
        var job = state.productionJobs().get(assignment.ownerId().orElseThrow());
        return resumeBlocker(state, job).isEmpty();
    }
    /** Owner-provided explanation, shared by admission and read-only diagnostics. */
    public static java.util.Optional<ResumeWait> resumeBlocker(FrontierWorldState state, ProductionJob job) {
        var work = job.stationWork().orElseThrow();
        if (work.phase() == StationWorkState.Phase.PROCESSING
                && state.bootstrap().ruleset().production().require(work.recipeId()).processingMode() == ProductionRecipe.ProcessingMode.AUTOMATIC)
            return StationWorkOperation.require(state, job).complete() ? java.util.Optional.empty()
                    : java.util.Optional.of(ServiceWait.AUTOMATIC_PROCESSING);
        return operationBlocker(state, job);
    }
    public static java.util.Set<SubjectId> resumeWakeKeys(FrontierWorldState state, HumanAssignment assignment) {
        FrontierProductionWorkSceneSupport.executionCheckpoint(state, assignment);
        var job = state.productionJobs().get(assignment.ownerId().orElseThrow());
        return java.util.Set.of(job.id(), point(state, job));
    }

    @Override public List<ServiceAccessDemand> demands(FrontierWorldState state, SubjectId pointId) {
        var boundary = ServiceAccessCoordinator.boundary(state, pointId);
        var result = new ArrayList<ServiceAccessDemand>();
        for (var job : state.productionJobs().values()) {
            if (job.stationWork().isEmpty()
                    || !state.actorExecutions().owns(job.workerId(),
                        io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.PRODUCTION, job.id())
                    || !(point(state, job).equals(pointId) || StationWorkStorage.source(job).equals(pointId) || StationWorkStorage.destination(job).equals(pointId))
                    || state.humanPopulation().meals().containsKey(job.workerId())
                    || state.actorMovements().containsKey(job.workerId())
                    || !operationEligible(state, job)) continue;
            var phase = job.stationWork().orElseThrow().phase();
            boolean occupied = ServiceAccessCoordinator.occupies(state, boundary, job.workerId());
            if (phase != StationWorkState.Phase.DELIVERED && point(state, job).equals(pointId) || occupied)
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
        return operationBlocker(state, job).isEmpty();
    }
    private static java.util.Optional<ResumeWait> operationBlocker(FrontierWorldState state, ProductionJob job) {
        var work = job.stationWork().orElseThrow();
        if (work.pendingPhysicalStep().isEmpty() && work.phase() == StationWorkState.Phase.STATION_LOAD) {
            var station = ProductionStationEnergy.record(state, work.stationId());
            if (ReferenceContainerCustody.blocksCanonicalUse(state, station.id())) return java.util.Optional.of(ServiceWait.STATION_CUSTODY);
            var readiness = StationWorkOperation.loadReadiness(state, job);
            return readiness == ProductionStationProcessing.StartReadiness.READY ? java.util.Optional.empty()
                    : java.util.Optional.of(new StationWait(readiness));
        }
        if (work.phase() == StationWorkState.Phase.PROCESSING
                && state.bootstrap().ruleset().production().require(work.recipeId()).processingMode()
                    == ProductionRecipe.ProcessingMode.AUTOMATIC) return java.util.Optional.of(ServiceWait.AUTOMATIC_PROCESSING);
        if (work.pendingPhysicalStep().isPresent() || work.phase() != StationWorkState.Phase.DEPOT_DELIVERY) return java.util.Optional.empty();
        var depot = StationWorkStorage.current(job);
        boolean available = ReferenceContainerCustody.hasLiveCustody(state, depot)
                ? ProductionOutputCapacity.deliverySlot(state, job).isPresent()
                : !ProductionOutputCapacity.depotDeliveryUnavailable(state, job);
        return available ? java.util.Optional.empty() : java.util.Optional.of(ServiceWait.OUTPUT_STORAGE_CAPACITY);
    }

    public static boolean mayAdvance(FrontierWorldState state, ProductionJob job, SurfaceAnchor destination) {
        if (!operationEligible(state, job))
            return ServiceAccessCoordinator.boundary(state, point(state, job))
                    .cleared(destination.standingBody());
        return ServiceAccessCoordinator.mayAdvance(state,
                identity(job, point(state, job)), destination);
    }

    public static boolean witnessedExit(FrontierWorldState state, ProductionJob job, BodyPosition observedBody) {
        if (job.stationWork().isEmpty()) return false;
        var phase = job.stationWork().orElseThrow().phase();
        if (phase.sourcePickup() || phase == StationWorkState.Phase.DEPOT_DELIVERY) return false;
        SceneLease lease = state.sceneLeases().values().stream()
                .filter(candidate -> candidate.status() == SceneLeaseStatus.HOT
                        && FrontierSceneBehaviors.isProductionWork(candidate)
                        && FrontierSceneBehaviors.productionWork(candidate).jobId().equals(job.id()))
                .findFirst().orElse(null);
        return lease != null && ServiceAccessCoordinator.witnessedExit(
                ServiceAccessCoordinator.boundary(state, StationWorkStorage.current(job)),
                lease.memberBody(state.actorLocations(), job.workerId()), observedBody);
    }
}
