package io.farfrontier.palemirror.frontier.v3.model.combat;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId;
import java.util.Objects;

/** One expiring continuation of an exact missed lunge, never another chase or hit permission. */
public record MeleeFollowup(ActorActuationId actor, CombatTarget target, SubjectId weapon,
        long targetIncarnation, long missedOrdinal, long openedAt, long expiresAt,MeleeAttack next) {
    public MeleeFollowup {
        Objects.requireNonNull(actor); Objects.requireNonNull(target); Objects.requireNonNull(weapon);Objects.requireNonNull(next);
        if (targetIncarnation < 1 || missedOrdinal < 1 || openedAt < 0 || expiresAt <= openedAt)
            throw new IllegalArgumentException("invalid missed-lunge continuation");
    }
    public SubjectId owner() { return actor.execution().activityOwnerId(); }
    public boolean matches(CombatOrder order, ActorActuationId observer, long incarnation, long tick) {
        return tick >= openedAt && tick < expiresAt && actor.equals(observer)
                && actor.execution().equals(order.execution()) && target.equals(order.target())
                && weapon.equals(order.weapon()) && targetIncarnation == incarnation;
    }
}
