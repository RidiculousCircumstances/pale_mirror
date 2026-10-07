package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.api.*;
import java.util.*;
public record ExpeditionReplenishmentHotLoaded(SubjectId missionId, SubjectId claimId, ActorItemTransferStep step,
        List<FungiblePhysicalObservation.Stack> remainingSource, List<FungiblePhysicalObservation.Stack> destination) implements FrontierPayload {
    public ExpeditionReplenishmentHotLoaded {
        Objects.requireNonNull(missionId); Objects.requireNonNull(claimId); Objects.requireNonNull(step);
        remainingSource = List.copyOf(remainingSource); destination = List.copyOf(destination);
        if (remainingSource.size() > 27 || destination.size() != 1) throw new IllegalArgumentException("invalid personal replenishment physical layout");
    }
    @Override public String type() { return "frontier.expedition_replenishment_hot_loaded"; }
}
