package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.extraction.ExtractionWork;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;
import io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation;
import java.util.*;

/** Mining supplies its portion and retained execution; the warehouse selector owns placement policy. */
public final class ExtractionStorageSelection {
    private ExtractionStorageSelection() { }
    public static Optional<ShipmentEndpoint> replacement(FrontierWorldState state, ExtractionWork job) {
        if (job.pending().isPresent() || job.phase() != ExtractionWork.Phase.STORE && job.phase() != ExtractionWork.Phase.RETURN_TOOL)
            return Optional.empty();
        String kind = job.phase() == ExtractionWork.Phase.STORE ? job.outputKind()
                : Objects.requireNonNull(state.inventory().items().get(job.toolId()), "held mining tool").itemKind();
        int quantity = job.phase() == ExtractionWork.Phase.STORE ? ExtractionWorkAuthority.carried(state, job) : 1;
        if (quantity == 0) throw new IllegalArgumentException("empty mining cargo cannot request storage");
        var origin = state.actorLocations().get(job.execution().actorId()).supportingSurface();
        return ResourceStorageSelection.destination(state, job.storageTarget().settlementId(), kind, quantity, origin,
                Set.of(ResourceStorageRole.LOCAL_BUFFER, ResourceStorageRole.WAREHOUSE), Optional.empty(), endpoint -> {
                    if (job.phase() == ExtractionWork.Phase.RETURN_TOOL
                            && ContainerStorageAdmission.first(state, endpoint.containerId(), Optional.empty(), slot -> false).isEmpty()) return false;
                    var order = new MovementOrder(job.id(), job.execution().actorId(), job.phase().wireTag(), job.revision(),
                            List.of(endpoint.station()), TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION);
                    try { ShipmentEndpointComposition.knowledge(state, List.of(endpoint)).path(origin, order); return true; }
                    catch (KnownPedestrianNavigation.RouteUnavailable unavailable) { return false; }
                });
    }
    public static boolean retainedAvailable(FrontierWorldState state, ExtractionWork job) {
        var destination = job.storageTarget();
        var record = state.inventory().containers().get(destination.containerId());
        if (record == null || record.storage().isEmpty()
                || !record.storage().orElseThrow().endpoint().equals(destination)
                || ReferenceContainerCustody.blocksCanonicalUse(state, destination.containerId())) return false;
        if (job.phase() == ExtractionWork.Phase.STORE)
            return ContainerStorageAdmission.receive(state, destination.containerId(), job.outputKind(), ExtractionWorkAuthority.carried(state, job), Optional.empty());
        var tool = Objects.requireNonNull(state.inventory().items().get(job.toolId()), "held mining tool");
        return record.storage().orElseThrow().filter().accepts(tool.itemKind())
                && ContainerStorageAdmission.first(state, destination.containerId(), Optional.empty(), slot -> false).isPresent();
    }
}
