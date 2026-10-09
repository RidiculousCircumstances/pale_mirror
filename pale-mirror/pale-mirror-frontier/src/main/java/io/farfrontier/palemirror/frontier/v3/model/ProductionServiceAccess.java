package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.ArrayList;
import java.util.List;

/** Production-owned interpretation of pickup, delivery and witnessed departure. */
public final class ProductionServiceAccess implements ServiceAccessCapability {
    @Override public ServiceAccessDemand.Kind kind() { return ServiceAccessDemand.Kind.PRODUCTION; }
    @Override public ServiceAccessDemand.Priority priority() { return ServiceAccessDemand.Priority.WORK; }

    @Override public List<ServiceAccessDemand> demands(FrontierWorldState state, SubjectId pointId) {
        var boundary = ServiceAccessCoordinator.boundary(state, pointId);
        var result = new ArrayList<ServiceAccessDemand>();
        for (var job : state.productionJobs().values()) {
            if (job.bakeryWork().isEmpty() || !FrontierWorldState.depotId(job.settlementId()).equals(pointId)
                    || state.humanPopulation().meals().containsKey(job.workerId())
                    || state.actorMovements().containsKey(job.workerId())
                    || !operationEligible(state, job)) continue;
            var phase = job.bakeryWork().orElseThrow().phase();
            boolean occupied = ServiceAccessCoordinator.occupies(state, boundary, job.workerId());
            if (phase == BakeryWorkState.Phase.DEPOT_PICKUP || phase == BakeryWorkState.Phase.DEPOT_DELIVERY || occupied)
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
                && ServiceAccessCoordinator.available(state, identity(job, FrontierWorldState.depotId(job.settlementId())));
    }

    /** Storage backpressure retains the job/cargo, not an exclusive service turn. */
    private static boolean operationEligible(FrontierWorldState state, ProductionJob job) {
        var work = job.bakeryWork().orElseThrow();
        if (work.pendingPhysicalStep().isPresent() || work.phase() != BakeryWorkState.Phase.DEPOT_DELIVERY) return true;
        var depot = FrontierWorldState.depotId(job.settlementId());
        return ReferenceContainerCustody.hasLiveCustody(state, depot)
                ? ProductionOutputCapacity.deliverySlot(state, job).isPresent()
                : !ProductionOutputCapacity.depotDeliveryUnavailable(state, job);
    }

    public static boolean mayAdvance(FrontierWorldState state, ProductionJob job, SurfaceAnchor destination) {
        if (!operationEligible(state, job))
            return ServiceAccessCoordinator.boundary(state, FrontierWorldState.depotId(job.settlementId()))
                    .cleared(destination.standingBody());
        return ServiceAccessCoordinator.mayAdvance(state,
                identity(job, FrontierWorldState.depotId(job.settlementId())), destination);
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
                ServiceAccessCoordinator.boundary(state, FrontierWorldState.depotId(job.settlementId())),
                lease.memberBody(state.actorLocations(), job.workerId()), observedBody);
    }
}
