package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.api.*;
import java.util.Objects;
public record ExpeditionReplenishmentStarted(SubjectId missionId, UnitResourceTransfer transfer) implements FrontierPayload {
    public ExpeditionReplenishmentStarted { Objects.requireNonNull(missionId); Objects.requireNonNull(transfer); }
    @Override public String type() { return "frontier.expedition_replenishment_started"; }
}
