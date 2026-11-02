package io.farfrontier.palemirror.frontier.v3.model.combat;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.ItemCondition;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId;
import java.util.Objects;
/** Exact mechanical attempt, independent of the caller's role. */
public record MeleeStrike(SubjectId owner,long ownerRevision,ActorActuationId attacker,CombatTarget target,
        SubjectId weapon,ItemCondition before,WeaponProfile profile,MeleeAttack attack,long targetIncarnation,long attemptOrdinal,long preparedAt) {
    public long impactAt(MeleeRules rules){return Math.addExact(preparedAt,rules.windup(attack));}
    public MeleeStrike {
        Objects.requireNonNull(owner);Objects.requireNonNull(attacker);Objects.requireNonNull(target);
        Objects.requireNonNull(weapon);Objects.requireNonNull(before);Objects.requireNonNull(profile);Objects.requireNonNull(attack);
        if(!owner.equals(attacker.execution().activityOwnerId()) || targetIncarnation<1 || ownerRevision<1 || attemptOrdinal<1
                || preparedAt<0 || !before.usable() || before.remaining()<profile.wear())throw new IllegalArgumentException("invalid exact melee attempt");
    }
}
