package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId;
import java.util.*;

/** Positive pre-loot witness of an already prepared transfer; never permission to actuate a corpse. */
public record ExpeditionTransferDeathObserved(SubjectId missionId, SubjectId claimId, ActorBodyId body,
        ActorItemTransferStep step, boolean applied, List<FungiblePhysicalObservation.Stack> source,
        List<FungiblePhysicalObservation.Stack> destination) implements FrontierPayload {
    public ExpeditionTransferDeathObserved {
        Objects.requireNonNull(missionId); Objects.requireNonNull(claimId); Objects.requireNonNull(body); Objects.requireNonNull(step);
        source = List.copyOf(source); destination = List.copyOf(destination);
        if (source.size() > 27 || destination.size() > 27) throw new IllegalArgumentException("unbounded expedition death witness");
    }
    @Override public String type() { return "frontier.expedition_transfer_death_observed"; }
}
