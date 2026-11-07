package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import java.util.Objects;

/** The expedition owner withdraws one still-reversible food purchase, never a physical effect. */
public record ExpeditionReplenishmentCancelled(SubjectId missionId, SubjectId claimId) implements FrontierPayload {
    public ExpeditionReplenishmentCancelled { Objects.requireNonNull(missionId); Objects.requireNonNull(claimId); }
    @Override public String type() { return "frontier.expedition_replenishment_cancelled"; }
}
