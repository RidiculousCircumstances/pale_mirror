package io.farfrontier.palemirror.frontier.v3.model.combat;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;

/** Producer-declared cooperation identity. Combat never derives allegiance from a role or settlement. */
public record CombatPartyId(SubjectId value) {
    public CombatPartyId { Objects.requireNonNull(value); }
}
