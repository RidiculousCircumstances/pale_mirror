package io.farfrontier.palemirror.frontier.v3.model.combat;
import java.util.Objects;
/** Explicit training identity; another weapon or actor may declare its own catalogued skill. */
public record CombatSkillId(String value) implements Comparable<CombatSkillId> {
    public static final CombatSkillId CROSSBOW=new CombatSkillId("frontier:crossbow");
    public static final CombatSkillId SWORD=new CombatSkillId("frontier:sword");
    public static final CombatSkillId EVASION=new CombatSkillId("frontier:evasion");
    public static final CombatSkillId WEAPON_DEFENCE=new CombatSkillId("frontier:weapon_defence");
    public CombatSkillId {Objects.requireNonNull(value);if(!value.matches("[a-z][a-z0-9_]*:[a-z][a-z0-9_]*") || value.length()>96)
        throw new IllegalArgumentException("invalid combat skill identity");}
    public int compareTo(CombatSkillId other){return value.compareTo(other.value);}
}
