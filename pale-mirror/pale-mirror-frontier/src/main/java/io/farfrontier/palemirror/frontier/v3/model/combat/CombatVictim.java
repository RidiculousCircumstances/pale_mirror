package io.farfrontier.palemirror.frontier.v3.model.combat;
import java.util.Objects;
/** Exact additional effect participant captured before durable area-strike admission. */
public record CombatVictim(CombatTarget target,long incarnation) {
    public CombatVictim{Objects.requireNonNull(target);if(incarnation<1)throw new IllegalArgumentException("invalid area target incarnation");}
}
