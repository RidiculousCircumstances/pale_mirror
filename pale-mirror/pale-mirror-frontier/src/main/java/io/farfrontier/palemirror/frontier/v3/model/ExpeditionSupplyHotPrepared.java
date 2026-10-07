package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.api.*;
import java.util.Objects;
public record ExpeditionSupplyHotPrepared(SubjectId missionId, SubjectId claimId, ActorItemTransferStep step) implements FrontierPayload {
    public ExpeditionSupplyHotPrepared { Objects.requireNonNull(missionId); Objects.requireNonNull(claimId); Objects.requireNonNull(step); }
    @Override public String type() { return "frontier.expedition_supply_hot_prepared"; }
}
