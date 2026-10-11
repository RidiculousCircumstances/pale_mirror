package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation;
import java.util.*;

/** Bakery supplies its checkpoint and route constraint; common storage owns eligibility. */
public final class BakeryStorageReplacement {
    private BakeryStorageReplacement() { }
    public static boolean unavailable(FrontierWorldState state, ProductionJob job) {
        var container = BakeryStorage.destination(job);
        var record = state.inventory().containers().get(container);
        return record == null || ReferenceContainerCustody.blocksCanonicalUse(state, container)
                || record.storage().map(policy -> !policy.filter().accepts(job.outputItemKind())).orElse(true)
                || ProductionOutputCapacity.depotDeliveryUnavailable(state, job);
    }
    public static Optional<BakeryStorageRetargeted> select(FrontierWorldState state, ProductionJob job) {
        if (!checkpoint(state, job)) return Optional.empty();
        var work = job.bakeryWork().orElseThrow();
        for (var endpoint : destinations(state, job)) {
            var candidate = job.retargetBakeryOutput(endpoint.containerId());
            try {
                var route = BakeryKnownNavigation.plannedPath(state, candidate);
                var execution = state.actorExecutions().current(
                        io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.PRODUCTION).get(job.workerId());
                return Optional.of(new BakeryStorageRetargeted(job.id(), work.phase(), BakeryStorage.destination(job),
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
        var work = job.bakeryWork().orElseThrow();
        if (work.phase() != BakeryWorkState.Phase.DEPOT_DELIVERY || !unavailable(state, job)
                || work.pendingPhysicalStep().isPresent()
                || !ActorExecutionCoordinator.coldAvailable(state, job.workerId())
                || ActorExecutionCoordinator.sceneOwns(state, job.workerId())
                || state.actorMovements().containsKey(job.workerId())) return false;
        return state.actorExecutions().owns(job.workerId(),
                io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.PRODUCTION, job.id());
    }
    private static List<ShipmentEndpoint> destinations(FrontierWorldState state, ProductionJob job) {
        var origin = state.actorLocations().get(job.workerId()).supportingSurface();
        return ResourceStorageSelection.receivingCandidates(state, job.settlementId(), job.outputItemKind(), job.outputCount(), origin,
                Set.of(ResourceStorageRole.WAREHOUSE), Optional.empty()).stream()
                .filter(endpoint -> !endpoint.containerId().equals(BakeryStorage.destination(job)))
                .filter(endpoint -> !(job.inputHold() instanceof ProductionInputHold.Materialized)
                        || state.firstFreeContainerSlotForOutput(endpoint.containerId(), job.id()).isPresent()).toList();
    }
    public static FrontierWorldState apply(FrontierWorldState state, SubjectId subject, BakeryStorageRetargeted event) {
        var job = state.productionJobs().get(event.jobId());
        if (job == null || !subject.equals(job.settlementId()) || !checkpoint(state, job)
                || job.bakeryWork().orElseThrow().phase() != event.phase()
                || !BakeryStorage.destination(job).equals(event.previousContainer())
                || !event.executionId().actorId().equals(job.workerId())
                || destinations(state, job).stream().noneMatch(endpoint -> endpoint.containerId().equals(event.destinationContainer())))
            throw new IllegalArgumentException("bakery output replacement lacks its exact unbegun checkpoint");
        state.actorExecutions().requireCurrent(event.executionId());
        var replacement = job.retargetBakeryOutput(event.destinationContainer());
        BakeryKnownNavigation.requireAcceptedDestination(state, replacement, event.plannedRoute());
        var jobs = new LinkedHashMap<>(state.productionJobs()); jobs.put(job.id(), replacement);
        return state.withChanges(FrontierWorldStateUpdate.begin().productionJobs(jobs));
    }
}
