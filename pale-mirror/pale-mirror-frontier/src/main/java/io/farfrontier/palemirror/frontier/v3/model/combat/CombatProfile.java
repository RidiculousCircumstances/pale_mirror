package io.farfrontier.palemirror.frontier.v3.model.combat;
import java.util.*;
/** Actor-owned immutable preparation, independent of species, employment and style. No XP process. General defences are declared explicitly, including zero. */
public record CombatProfile(CombatAttributes attributes,Map<CombatSkillId,Integer> skills) {
    public CombatProfile {
        Objects.requireNonNull(attributes);Objects.requireNonNull(skills);
        if(skills.isEmpty() || skills.size()>32)throw new IllegalArgumentException("bounded explicit combat skills required");
        skills.forEach((key,value)->{Objects.requireNonNull(key);if(value==null || value<0 || value>100)
            throw new IllegalArgumentException("combat skill outside0..100");});
        if(!skills.containsKey(CombatSkillId.EVASION) || !skills.containsKey(CombatSkillId.WEAPON_DEFENCE))
            throw new IllegalArgumentException("explicit general defence levels required, including zero");
        skills=Map.copyOf(skills);
    }
    public int skill(CombatSkillId key){var value=skills.get(key);if(value==null)throw new IllegalArgumentException("undeclared combat skill "+key.value());return value;}
    public boolean knows(CombatSkillId key,int level){return skills.containsKey(key) && skill(key)>=level;}
}
