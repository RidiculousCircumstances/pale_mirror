package io.farfrontier.palemirror.frontier.v3.model.combat;

import java.util.Objects;

/** Preferences over already legal actions; never extra damage, reach, speed or cooldown authority. */
public record CombatStyle(CombatStyleId id,String name,int preferredGapMicros,int attackWeight,int runWeight,
        int burstWeight,int lateralWeight,int retreatWeight,int approachWeight,int effortReserve) {
    public CombatStyle {
        Objects.requireNonNull(id);Objects.requireNonNull(name);
        if(name.isBlank() || name.length()>32 || name.chars().anyMatch(Character::isISOControl) || preferredGapMicros<1_000_000 || preferredGapMicros>3_000_000
                || effortReserve<0 || effortReserve>60)throw new IllegalArgumentException("invalid combat style");
        for(int weight:new int[]{attackWeight,runWeight,burstWeight,lateralWeight,retreatWeight,approachWeight})
            if(weight<0 || weight>1000)throw new IllegalArgumentException("invalid combat style weight");
        if(attackWeight<1 || runWeight+burstWeight+approachWeight<1)throw new IllegalArgumentException("combat style has no attack or approach");
    }
}
