package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation;
import java.util.*;

/** Bakery supplies its checkpoint and route constraint; common storage owns eligibility. */
public final class StationWorkStorageReplacement {
    private StationWorkStorageReplacement() { }
    public static boolean unavailable(FrontierWorldState state, ProductionJob job) {
        var container = StationWorkStorage.destination(job);
        var record = state.inventory().containers().get(container);
        return record == null || ReferenceContainerCustody.blocksCanonicalUse(state, container)
                || record.storage().map(policy -> !policy.filter().accepts(job.outputItemKind())).orElse(true)
                || ProductionOutputCapacity.depotDeliveryUnavailable(state, job);
    }
    public static Optional<BakeryStorageRetargeted> select(FrontierWorldState state, ProductionJob job) {
        if (!checkpoint(state, job)) return Optional.empty();
        var work = job.stationWork().orElseThrow();
        for (var endpoint : destinations(state, job)) {
            var candidate = job.retargetStationOutput(endpoint.containerId());
            try {
                var route = StationWorkNavigation.plannedPath(state, candidate);
                var execution = state.actorExecutions().current(
                        io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.PRODUCTION).get(job.workerId());
                return Optional.of(new BakeryStorageRetargeted(job.id(), work.phase(), StationWorkStorage.destination(job),
                        endpoint.containerId(), execution, route.size() < 2 ? Optional.empty() : Optional.of(
                            new io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteReceipt(route))));
            } catch (KnownPedestrianNavigation.RouteUnavailable unavailable) {
                if (unavailable.status() != io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult.Status.NO_PATH)
                    return Optional.empty();
            }
        }
        return Optional.empty();
    }
    private static boolean checkpoint(FrontierWorldState state, ProductionJob job) {
        var work = job.stationWork().orElseThrow();
        if (work.phase() != StationWorkState.Phase.DEPOT_DELIVERY || !unavailable(state, job)
                || work.pendingPhysicalStep().isPresent()
                || !ActorExecutionCoordinator.coldAvailable(state, job.workerId())
                || ActorExecutionCoordinator.sceneOwns(state, job.workerId())
                || state.actorMovements().containsKey(job.workerId())) return false;
        return state.actorExecutions().owns(job.workerId(),
                io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.PRODUCTION, job.id());
    }
    private static List<ShipmentEndpoint> destinations(FrontierWorldState state, ProductionJob job) {
        var origin = state.actorLocations().get(job.workerId()).supportingSurface();
        return ResourceStorageSelection.candidates(state, job.settlementId(), job.outputItemKind(), origin,
                Set.of(ResourceStorageRole.WAREHOUSE)).stream()
                .filter(endpoint -> !endpoint.containerId().equals(StationWorkStorage.destination(job)))
                .filter(endpoint -> StationWorkProduct.exact(state, job)
                        ? state.firstFreeContainerSlot(endpoint.containerId()).isPresent()
                        : state.canReceiveFungible(endpoint.containerId(), job.outputItemKind(), job.outputCount())).toList();
    }
    public static FrontierWorldState apply(FrontierWorldState state, SubjectId subject, BakeryStorageRetargeted event) {
        var job = state.productionJobs().get(event.jobId());
        if (job == null || !subject.equals(job.settlementId()) || !checkpoint(state, job)
                || job.stationWork().orElseThrow().phase() != event.phase()
                || !StationWorkStorage.destination(job).equals(event.previousContainer())
                || !event.executionId().actorId().equals(job.workerId())
                || destinations(state, job).stream().noneMatch(endpoint -> endpoint.containerId().equals(event.destinationContainer())))
            throw new IllegalArgumentException("bakery output replacement lacks its exact unbegun checkpoint");
        state.actorExecutions().requireCurrent(event.executionId());
        var replacement = job.retargetStationOutput(event.destinationContainer());
        StationWorkNavigation.requireAcceptedDestination(state, replacement, event.plannedRoute());
        var jobs = new LinkedHashMap<>(state.productionJobs()); jobs.put(job.id(), replacement);
        return state.withChanges(FrontierWorldStateUpdate.begin().productionJobs(jobs));
    }
}
