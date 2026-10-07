package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionSupplyLoad;
import java.util.List;

/** Provisioning declares access to an existing service point, never its own queue. */
public final class ExpeditionSupplyServiceAccess implements ServiceAccessCapability {
    @Override public ServiceAccessDemand.Kind kind() { return ServiceAccessDemand.Kind.EXPEDITION_SUPPLY; }
    @Override public ServiceAccessDemand.Priority priority() { return ServiceAccessDemand.Priority.WORK; }
    @Override public List<ServiceAccessDemand> demands(FrontierWorldState state, SubjectId point) {
        var boundary = ServiceAccessCoordinator.boundary(state, point);
        return state.shipments().missions().values().stream().filter(m -> m.stage() == TransportMission.Stage.LOADING
                && m.sender().containerId().equals(point)).flatMap(m -> m.supplies().stream().flatMap(load -> load.next().stream())
                .map(a -> new ServiceAccessDemand(identity(m, a), priority(),
                        ServiceAccessCoordinator.occupies(state, boundary, a.actorId())
                                ? ServiceAccessDemand.Presence.OCCUPIED : ServiceAccessDemand.Presence.APPROACH,
                        m.supplies().orElseThrow().calculatedAtTick(), a.pending().isPresent()))).toList();
    }
    public static ServiceAccessDemand.Identity identity(TransportMission mission, ExpeditionSupplyLoad.Allocation allocation) {
        return new ServiceAccessDemand.Identity(ServiceAccessDemand.Kind.EXPEDITION_SUPPLY, allocation.claimId(),
                allocation.actorId(), mission.sender().containerId());
    }
}
