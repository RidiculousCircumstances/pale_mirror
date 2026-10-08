package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import java.util.Objects;
import java.util.UUID;

/** Physical inspection of an empty-handed baker whose batch remains in its station. */
public record BakeryStationSceneReconciled(SubjectId jobId, SceneLeaseId leaseId, long leaseRevision,
        long recoveryEpoch, UUID entityId, BodyPosition observedBody, BakeryWorkState.Phase phase,
        io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyInspected.Source source) implements FrontierPayload {
    public BakeryStationSceneReconciled(SubjectId jobId, SceneLeaseId leaseId, long leaseRevision,
            long recoveryEpoch, UUID entityId, BodyPosition observedBody, BakeryWorkState.Phase phase) {
        this(jobId, leaseId, leaseRevision, recoveryEpoch, entityId, observedBody, phase,
                io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyInspected.Source.INDEXED_LIVING);
    }
    public BakeryStationSceneReconciled {
        Objects.requireNonNull(jobId); Objects.requireNonNull(leaseId); Objects.requireNonNull(entityId);
        Objects.requireNonNull(observedBody); Objects.requireNonNull(phase);
        Objects.requireNonNull(source);
        if (leaseRevision < 1 || recoveryEpoch < 1
                || source != io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyInspected.Source.INDEXED_LIVING
                    && source != io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyInspected.Source.SAVED_DEPARTURE
                || phase != BakeryWorkState.Phase.PROCESSING && phase != BakeryWorkState.Phase.STATION_UNLOAD)
            throw new IllegalArgumentException("station reconciliation requires exact empty-hand station phase");
    }
    @Override public String type() { return "frontier.bakery_station_scene_reconciled"; }
}
