package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionSupplyLoad;
import java.util.Objects;

/** A retained loading owner replaces changed promises, preserving already-carried stock in custody. */
public record ExpeditionSupplyReplanned(SubjectId missionId, long expectedRevision, ExpeditionSupplyLoad replacement)
        implements FrontierPayload {
    public ExpeditionSupplyReplanned {
        Objects.requireNonNull(missionId); Objects.requireNonNull(replacement);
        if (expectedRevision < 1 || replacement.revision() != Math.addExact(expectedRevision, 1))
            throw new IllegalArgumentException("supply replanning lacks an exact versioned predecessor");
    }
    @Override public String type() { return "frontier.expedition_supply_replanned"; }
}
