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
        var work = job.bakeryWork().orElseThrow();
        if (work.phase() != BakeryWorkState.Phase.DEPOT_DELIVERY || !unavailable(state, job)
                || work.pendingPhysicalStep().isPresent()
                || !ActorExecutionCoordinator.coldAvailable(state, job.workerId())
                || ActorExecutionCoordinator.sceneOwns(state, job.workerId())
                || state.actorMovements().containsKey(job.workerId())) return Optional.empty();
        var origin = state.actorLocations().get(job.workerId()).supportingSurface();
        return ResourceStorageSelection.destination(state, job.settlementId(), job.outputItemKind(), job.outputCount(), origin,
                Set.of(ResourceStorageRole.WAREHOUSE), Optional.empty(), endpoint -> {
                    if (endpoint.containerId().equals(BakeryStorage.destination(job))) return false;
                    var candidate = job.retargetBakeryOutput(endpoint.containerId());
                    try {
                        BakeryKnownNavigation.path(state, candidate);
                        return true;
                    } catch (KnownPedestrianNavigation.RouteUnavailable unavailable) { return false; }
                }).map(endpoint -> new BakeryStorageRetargeted(job.id(), work.phase(), BakeryStorage.destination(job), endpoint.containerId()));
    }
    public static FrontierWorldState apply(FrontierWorldState state, SubjectId subject, BakeryStorageRetargeted event) {
        var job = state.productionJobs().get(event.jobId());
        if (job == null || !subject.equals(job.settlementId()) || !select(state, job).equals(Optional.of(event)))
            throw new IllegalArgumentException("bakery output replacement lacks its exact unbegun checkpoint");
        var jobs = new LinkedHashMap<>(state.productionJobs()); jobs.put(job.id(), job.retargetBakeryOutput(event.destinationContainer()));
        return state.withChanges(FrontierWorldStateUpdate.begin().productionJobs(jobs));
    }
}
