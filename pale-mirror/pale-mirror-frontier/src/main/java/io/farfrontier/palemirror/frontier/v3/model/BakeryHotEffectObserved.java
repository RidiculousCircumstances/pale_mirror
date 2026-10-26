package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.List;
import java.util.Objects;

/** Actual loaded source/destination postcondition for one prepared bakery effect. */
public record BakeryHotEffectObserved(SubjectId jobId, SceneLeaseId leaseId,
                                      StationWorkState.Phase phase, BodyPosition observedWorker,
                                      long sourceEpoch, long destinationEpoch,
                                      List<FungiblePhysicalObservation.Stack> remainingSource,
                                      List<FungiblePhysicalObservation.Stack> destination, long workflowOrdinal)
        implements FrontierPayload {
    public BakeryHotEffectObserved(SubjectId job, SceneLeaseId lease, StationWorkState.Phase phase, BodyPosition worker,
            long sourceEpoch, long destinationEpoch, List<FungiblePhysicalObservation.Stack> source, List<FungiblePhysicalObservation.Stack> destination) {
        this(job, lease, phase, worker, sourceEpoch, destinationEpoch, source, destination, 0L);
    }
    public BakeryHotEffectObserved {
        Objects.requireNonNull(jobId, "bakery observed job");
        Objects.requireNonNull(leaseId, "bakery observed lease");
        Objects.requireNonNull(phase, "bakery observed phase");
        Objects.requireNonNull(observedWorker, "bakery observed worker");
        remainingSource = List.copyOf(Objects.requireNonNull(remainingSource, "bakery source observation"));
        destination = List.copyOf(Objects.requireNonNull(destination, "bakery destination observation"));
        if (workflowOrdinal < 0 || sourceEpoch < 0 || destinationEpoch < 0 || remainingSource.size() > 54 || destination.size() > 54)
            throw new IllegalArgumentException("bakery observed physical layout exceeds bounded authority");
    }
    @Override public String type() { return "frontier.bakery_hot_effect_observed"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
