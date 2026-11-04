package io.farfrontier.palemirror.frontier.v3.model.combat;
/** Permanent actor properties. No health, injury, hunger or role-derived modifiers. */
public record CombatAttributes(int strength,int agility,int endurance,int perception,int toughness) {
    public CombatAttributes {for(int value:new int[]{strength,agility,endurance,perception,toughness})
        if(value<0 || value>100)throw new IllegalArgumentException("combat attribute outside0..100");}
}
