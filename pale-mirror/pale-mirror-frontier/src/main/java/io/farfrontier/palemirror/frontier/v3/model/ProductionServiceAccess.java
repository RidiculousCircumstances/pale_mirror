package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.ArrayList;
import java.util.List;

/** Production-owned interpretation of pickup, delivery and witnessed departure. */
public final class ProductionServiceAccess implements ServiceAccessCapability {
    @Override public ServiceAccessDemand.Kind kind() { return ServiceAccessDemand.Kind.PRODUCTION; }
    @Override public ServiceAccessDemand.Priority priority() { return ServiceAccessDemand.Priority.WORK; }

    @Override public List<ServiceAccessDemand> demands(FrontierWorldState state, SubjectId pointId) {
        var port = ServiceAccessCoordinator.port(state, pointId);
        var result = new ArrayList<ServiceAccessDemand>();
        for (var job : state.productionJobs().values()) {
            if (job.bakeryWork().isEmpty() || !job.settlementId().equals(port.settlementId())
                    || state.humanPopulation().meals().containsKey(job.workerId())
                    || state.actorMovements().containsKey(job.workerId())) continue;
            var phase = job.bakeryWork().orElseThrow().phase();
            boolean occupied = ServiceAccessCoordinator.occupies(state, port.accessBoundary(), job.workerId());
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
        return ServiceAccessCoordinator.available(state, identity(job, FrontierWorldState.depotId(job.settlementId())));
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
