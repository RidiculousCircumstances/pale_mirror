package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.ArrayList;
import java.util.List;

/** Harvest owns the distinction between its field goal and a retained delivery obligation. */
public final class HarvestServiceAccess implements ServiceAccessCapability {
    @Override public ServiceAccessDemand.Kind kind() { return ServiceAccessDemand.Kind.FIELD_HARVEST; }
    @Override public ServiceAccessDemand.Priority priority() { return ServiceAccessDemand.Priority.WORK; }
    @Override public java.util.Set<SubjectId> wakePoints(FrontierWorldState previous, FrontierWorldState next) {
        return ServiceAccessWakeDependencies.changed(previous.resourceSites().sites(), next.resourceSites().sites(),
                site -> site.harvestJobs().values().stream().flatMap(job -> java.util.stream.Stream.concat(java.util.stream.Stream.of(
                        job.outputSlot().containerId(), job.equipment().source().containerId()),
                        job.equipment().returnTarget().stream().map(ShipmentEndpoint::containerId))));
    }

    @Override public List<ServiceAccessDemand> demands(FrontierWorldState state, SubjectId pointId) {
        var boundary = ServiceAccessCoordinator.boundary(state, pointId);
        var result = new ArrayList<ServiceAccessDemand>();
        for (var site : state.resourceSites().sites().values()) {
            for (var job : site.harvestJobs().values()) {
                if (state.humanPopulation().meals().containsKey(job.workerId())
                        || state.actorMovements().containsKey(job.workerId())
                        || !ResourceSiteHarvestGoal.servicePort(state, job).containerId().equals(pointId)) continue;
                boolean occupied = ServiceAccessCoordinator.occupies(state, boundary, job.workerId());
                if (ResourceSiteHarvestGoal.current(state, job).kind() != ResourceSiteHarvestGoal.Kind.WORK_CELL || occupied)
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
        var port = ResourceSiteHarvestGoal.servicePort(state, job);
        return ServiceAccessCoordinator.available(state, identity(job, port.containerId()));
    }

    public static boolean mayAdvance(FrontierWorldState state, ResourceSiteHarvestJob job, SurfaceAnchor destination) {
        var port = ResourceSiteHarvestGoal.servicePort(state, job);
        return ServiceAccessCoordinator.mayAdvance(state,
                identity(job, port.containerId()), destination);
    }

    public static boolean witnessedExit(FrontierWorldState state, ResourceSiteHarvestJob job,
                                        SceneLeaseId leaseId, BodyPosition observedBody) {
        SceneLease lease = FrontierResourceSiteHarvestSceneSupport.requireHotLease(state, job, leaseId);
        var goal = ResourceSiteHarvestGoal.current(state, job);
        var ports = new java.util.HashSet<SubjectId>();
        ports.add(job.outputSlot().containerId()); ports.add(job.equipment().source().containerId());
        job.equipment().returnTarget().ifPresent(target -> ports.add(target.containerId()));
        if (goal.kind() != ResourceSiteHarvestGoal.Kind.WORK_CELL) ports.remove(ResourceSiteHarvestGoal.servicePort(state, job).containerId());
        return ports.stream().filter(state.inventory().containers()::containsKey).anyMatch(point ->
                ServiceAccessCoordinator.witnessedExit(ResourceStorageAccess.at(state, point).accessBoundary(),
                        lease.memberBody(state.actorLocations(), job.workerId()), observedBody));
    }
}
