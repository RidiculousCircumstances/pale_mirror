package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.List;
import java.util.Objects;

/** Actual loaded source/destination postcondition for one prepared bakery effect. */
public record BakeryHotEffectObserved(SubjectId jobId, SceneLeaseId leaseId,
                                      BakeryWorkState.Phase phase, BodyPosition observedWorker,
                                      long sourceEpoch, long destinationEpoch,
                                      List<FungiblePhysicalObservation.Stack> remainingSource,
                                      List<FungiblePhysicalObservation.Stack> destination)
        implements FrontierPayload {
    public BakeryHotEffectObserved {
        Objects.requireNonNull(jobId, "bakery observed job");
        Objects.requireNonNull(leaseId, "bakery observed lease");
        Objects.requireNonNull(phase, "bakery observed phase");
        Objects.requireNonNull(observedWorker, "bakery observed worker");
        remainingSource = List.copyOf(Objects.requireNonNull(remainingSource, "bakery source observation"));
        destination = List.copyOf(Objects.requireNonNull(destination, "bakery destination observation"));
        if (sourceEpoch < 0 || destinationEpoch < 0 || remainingSource.size() > 27 || destination.size() > 27)
            throw new IllegalArgumentException("bakery observed physical layout exceeds bounded authority");
    }
    @Override public String type() { return "frontier.bakery_hot_effect_observed"; }
}
