package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.api.*;
import java.util.List;
import java.util.Objects;
public record ExpeditionSupplyHotLoaded(SubjectId missionId, SubjectId claimId, ActorItemTransferStep step,
        List<FungiblePhysicalObservation.Stack> remainingSource, List<FungiblePhysicalObservation.Stack> destination) implements FrontierPayload {
    public ExpeditionSupplyHotLoaded {
        Objects.requireNonNull(missionId); Objects.requireNonNull(claimId); Objects.requireNonNull(step);
        remainingSource = List.copyOf(remainingSource); destination = List.copyOf(destination);
        if (remainingSource.size() > 27 || destination.isEmpty() || destination.size() > 27) throw new IllegalArgumentException("invalid supply physical receipt size");
    }
    @Override public String type() { return "frontier.expedition_supply_hot_loaded"; }
}
