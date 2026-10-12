package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import java.util.*;

/** Mining supplies its portion and retained execution; the warehouse selector owns placement policy. */
public final class ExtractionStorageSelection {
    private ExtractionStorageSelection() { }
    public static Optional<ExtractionWorkDecision.Storage> replacement(FrontierWorldState state, ExtractionWork job) {
        if (job.pending().isPresent() || job.phase() != ExtractionWork.Phase.STORE && job.phase() != ExtractionWork.Phase.RETURN_TOOL)
            return Optional.empty();
        String kind = job.phase() == ExtractionWork.Phase.STORE ? job.outputKind()
                : Objects.requireNonNull(state.inventory().items().get(job.toolId()), "held mining tool").itemKind();
        int quantity = job.phase() == ExtractionWork.Phase.STORE ? ExtractionWorkAuthority.carried(state, job) : 1;
        if (quantity == 0) throw new IllegalArgumentException("empty mining cargo cannot request storage");
        var origin = state.actorLocations().get(job.execution().actorId()).supportingSurface();
        for (var endpoint : ResourceStorageSelection.receivingCandidates(state, job.storageTarget().settlementId(), kind, quantity, origin,
                Set.of(ResourceStorageRole.LOCAL_BUFFER, ResourceStorageRole.WAREHOUSE), Optional.empty())) {
            if (endpoint.equals(job.storageTarget())) continue;
                    if (job.phase() == ExtractionWork.Phase.RETURN_TOOL
                            && ContainerStorageAdmission.first(state, endpoint.containerId(), Optional.empty(), slot -> false).isEmpty()) continue;
            try {
                var route = ResourceStorageNavigation.plannedPath(state, origin, endpoint);
                return Optional.of(new ExtractionWorkDecision.Storage(job.execution(), origin, endpoint,
                        route.size() > 1 ? Optional.of(new PedestrianRouteReceipt(route)) : Optional.empty()));
            } catch (KnownPedestrianNavigation.RouteUnavailable unavailable) {
                if (unavailable.status() != PedestrianRouteResult.Status.NO_PATH) throw unavailable;
            }
        }
        return Optional.empty();
    }
    public static void requireReplacement(FrontierWorldState state, ExtractionWork job, ExtractionWorkDecision.Storage selected) {
        String kind = job.phase() == ExtractionWork.Phase.STORE ? job.outputKind()
                : Objects.requireNonNull(state.inventory().items().get(job.toolId()), "held mining tool").itemKind();
        int quantity = job.phase() == ExtractionWork.Phase.STORE ? ExtractionWorkAuthority.carried(state, job) : 1;
        if (!selected.execution().equals(job.execution()) || !selected.origin().equals(
                state.actorLocations().get(job.execution().actorId()).supportingSurface())
                || retainedAvailable(state, job) || job.storageTarget().equals(selected.destination())
                || !ResourceStorageSelection.receivesAt(state, selected.destination(), job.storageTarget().settlementId(), kind, quantity,
                    Set.of(ResourceStorageRole.WAREHOUSE, ResourceStorageRole.LOCAL_BUFFER), Optional.empty())
                || !retainedAvailable(state, job.retargetStorage(selected.destination())))
            throw new IllegalArgumentException("replacement mining storage has stale authority, departure or capacity");
        ResourceStorageNavigation.requireAcceptedDestination(state, selected.origin(), selected.destination(), selected.route());
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
