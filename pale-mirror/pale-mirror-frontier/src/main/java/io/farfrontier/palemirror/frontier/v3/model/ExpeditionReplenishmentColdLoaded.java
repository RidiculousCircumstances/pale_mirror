package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.api.*;
import java.util.Objects;
public record ExpeditionReplenishmentColdLoaded(SubjectId missionId, SubjectId claimId) implements FrontierPayload {
    public ExpeditionReplenishmentColdLoaded { Objects.requireNonNull(missionId); Objects.requireNonNull(claimId); }
    @Override public String type() { return "frontier.expedition_replenishment_cold_loaded"; }
}
