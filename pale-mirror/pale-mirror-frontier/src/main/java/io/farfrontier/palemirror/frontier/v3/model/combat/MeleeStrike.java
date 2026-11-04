package io.farfrontier.palemirror.frontier.v3.model.combat;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.ItemCondition;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId;
import java.util.Objects;
/** Exact mechanical attempt, independent of the caller's role. */
public record MeleeStrike(SubjectId owner,long ownerRevision,ActorActuationId attacker,CombatTarget target,
        SubjectId weapon,ItemCondition before,WeaponProfile profile,MeleeAttack attack,long targetIncarnation,long attemptOrdinal,long preparedAt,CombatProfile actorProfile,java.util.List<CombatVictim> additional) {
    public long impactAt(MeleeRules rules){return Math.addExact(preparedAt,timing(rules).preparationTicks());}
    public MeleeTechniques.Technique timing(MeleeRules rules){return CombatMechanics.timing(actorProfile,attack,rules);}
    public int wear(){return attack==MeleeAttack.KICK?0:profile.wear();}
    public MeleeStrike {
        additional=java.util.List.copyOf(additional);
        if(additional.size()>4 || additional.stream().map(CombatVictim::target).distinct().count()!=additional.size()
                || additional.stream().anyMatch(v->v.target().equals(target)) || attack!=MeleeAttack.CIRCLE && !additional.isEmpty())
            throw new IllegalArgumentException("invalid bounded area participants");
        Objects.requireNonNull(actorProfile);if(!CombatMechanics.unlocked(actorProfile,profile,attack))throw new IllegalArgumentException("unlearned technique");
        Objects.requireNonNull(owner);Objects.requireNonNull(attacker);Objects.requireNonNull(target);
        Objects.requireNonNull(weapon);Objects.requireNonNull(before);Objects.requireNonNull(profile);Objects.requireNonNull(attack);
        if(!owner.equals(attacker.execution().activityOwnerId()) || targetIncarnation<1 || ownerRevision<1 || attemptOrdinal<1
                || preparedAt<0 || !before.usable() || before.remaining()<profile.wear())throw new IllegalArgumentException("invalid exact melee attempt");
    }
}
