package io.farfrontier.palemirror.frontier.v3.model.combat;
import java.util.*;
/** Complete weapon permission. Crossbows grant only the explicit learned contact kick, never sword tactics or synthetic ranged hits. */
public sealed interface CombatUse permits CombatUse.Melee, CombatUse.Crossbow {
    CombatMethod method();
    CombatSkillId skill();
    record Melee(CombatMethod method, CombatSkillId skill, Set<MeleeAttack> techniques, MeleeAttack fallback) implements CombatUse {
        public Melee {
            Objects.requireNonNull(skill); techniques=Set.copyOf(techniques); Objects.requireNonNull(fallback);
            if(method!=CombatMethod.MELEE || techniques.isEmpty() || !techniques.contains(fallback))
                throw new IllegalArgumentException("invalid declared melee permission");
        }
    }
    record Crossbow(CombatMethod method, CombatSkillId skill, String ammunition) implements CombatUse {
        public Crossbow {
            Objects.requireNonNull(skill); Objects.requireNonNull(ammunition);
            if(method!=CombatMethod.CROSSBOW || !skill.equals(CombatSkillId.CROSSBOW) || !ammunition.matches("[a-z][a-z0-9_]*:[a-z][a-z0-9_]*"))
                throw new IllegalArgumentException("invalid declared crossbow permission");
        }
    }
}
