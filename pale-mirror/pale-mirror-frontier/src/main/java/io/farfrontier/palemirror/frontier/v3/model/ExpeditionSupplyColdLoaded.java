package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.api.*;
import java.util.Objects;
public record ExpeditionSupplyColdLoaded(SubjectId missionId, SubjectId claimId) implements FrontierPayload {
    public ExpeditionSupplyColdLoaded { Objects.requireNonNull(missionId); Objects.requireNonNull(claimId); }
    @Override public String type() { return "frontier.expedition_supply_cold_loaded"; }
}
