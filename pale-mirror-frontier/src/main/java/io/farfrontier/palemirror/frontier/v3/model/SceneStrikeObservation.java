package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Exact terminal receipt of one HOT scene hit; death remains a separate real entity observation. */
public record SceneStrikeObservation(PhysicalObservationId id, PhysicalIntentId intentId, SubjectId attackerId, SubjectId targetId,
                                     FixedScalar targetHealthBefore, FixedScalar targetHealthAfter) implements PhysicalEffectObservation {
    public SceneStrikeObservation {
        Objects.requireNonNull(id, "scene strike observation id"); Objects.requireNonNull(intentId, "scene strike intent id");
        Objects.requireNonNull(attackerId, "scene strike attacker"); Objects.requireNonNull(targetId, "scene strike target");
        Objects.requireNonNull(targetHealthBefore, "scene strike prior health"); Objects.requireNonNull(targetHealthAfter, "scene strike resulting health");
        if (attackerId.equals(targetId) || targetHealthBefore.compareTo(FixedScalar.ZERO) <= 0 || targetHealthAfter.compareTo(FixedScalar.ZERO) < 0
                || targetHealthAfter.compareTo(targetHealthBefore) > 0) throw new IllegalArgumentException("scene strike receipt has invalid exact health bounds");
    }
}
