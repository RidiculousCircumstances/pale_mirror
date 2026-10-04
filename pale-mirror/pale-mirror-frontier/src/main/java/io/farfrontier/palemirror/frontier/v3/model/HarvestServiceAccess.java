package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.ArrayList;
import java.util.List;

/** Harvest owns the distinction between its field goal and a retained delivery obligation. */
public final class HarvestServiceAccess implements ServiceAccessCapability {
    @Override public ServiceAccessDemand.Kind kind() { return ServiceAccessDemand.Kind.FIELD_HARVEST; }
    @Override public ServiceAccessDemand.Priority priority() { return ServiceAccessDemand.Priority.WORK; }

    @Override public List<ServiceAccessDemand> demands(FrontierWorldState state, SubjectId pointId) {
        var port = ServiceAccessCoordinator.port(state, pointId);
        var result = new ArrayList<ServiceAccessDemand>();
        for (var site : state.resourceSites().sites().values()) {
            for (var job : site.harvestJobs().values()) {
                if (state.humanPopulation().meals().containsKey(job.workerId())
                        || state.actorMovements().containsKey(job.workerId())
                        || !ResourceSiteHarvestGoal.depotPort(state, job).settlementId().equals(port.settlementId())) continue;
                boolean occupied = ServiceAccessCoordinator.occupies(state, port.accessBoundary(), job.workerId());
                if (ResourceSiteHarvestGoal.current(state, job).kind() == ResourceSiteHarvestGoal.Kind.DEPOT_SERVICE || occupied)
                    result.add(new ServiceAccessDemand(identity(job, pointId), priority(), occupied
                            ? ServiceAccessDemand.Presence.OCCUPIED : ServiceAccessDemand.Presence.APPROACH, 0L, false));
            }
        }
        return List.copyOf(result);
    }

    private static ServiceAccessDemand.Identity identity(ResourceSiteHarvestJob job, SubjectId pointId) {
        return new ServiceAccessDemand.Identity(ServiceAccessDemand.Kind.FIELD_HARVEST, job.id(), job.workerId(), pointId);
    }

    public static boolean available(FrontierWorldState state, ResourceSiteHarvestJob job) {
        var port = ResourceSiteHarvestGoal.depotPort(state, job);
        return ServiceAccessCoordinator.available(state, identity(job, FrontierWorldState.depotId(port.settlementId())));
    }

    public static boolean witnessedExit(FrontierWorldState state, ResourceSiteHarvestJob job,
                                        SceneLeaseId leaseId, BodyPosition observedBody) {
        if (ResourceSiteHarvestGoal.current(state, job).kind() == ResourceSiteHarvestGoal.Kind.DEPOT_SERVICE) return false;
        SceneLease lease = FrontierResourceSiteHarvestSceneSupport.requireHotLease(state, job, leaseId);
        return ServiceAccessCoordinator.witnessedExit(ResourceSiteHarvestGoal.depotPort(state, job).accessBoundary(),
                lease.memberBody(state.actorLocations(), job.workerId()), observedBody);
    }
}
