package io.farfrontier.palemirror.frontier.v3.model.combat;

/** World-hashed effort costs. Walking and waiting recover; no polling or second native timer. */
public record CombatReadinessRules(int capacity, int recoveryDelayTicks, int recoveryPerTick,
        int quickCost, int lungeCost, int sweepCost, int followupCost, int burstCost, int backstepCost) {
    public CombatReadinessRules {
        if (capacity < 1 || capacity > 10000 || recoveryDelayTicks < 1 || recoveryDelayTicks > 200
                || recoveryPerTick < 1 || recoveryPerTick > capacity)
            throw new IllegalArgumentException("invalid combat recovery rules");
        for (int cost : new int[]{quickCost,lungeCost,sweepCost,followupCost,burstCost,backstepCost})
            if (cost < 1 || cost > capacity) throw new IllegalArgumentException("invalid combat effort cost");
    }
    public int cost(MeleeAttack attack) { return switch (attack) {
        case QUICK -> quickCost; case LUNGE -> lungeCost; case SWEEP -> sweepCost; case FOLLOWUP -> followupCost;default->Math.min(capacity,Math.max(quickCost,CombatTrainingCatalog.standard().require(attack).timing().preparationTicks()*3));
    }; }
    public int cost(MeleeManeuver.Kind kind) { return kind.closing() ? burstCost : backstepCost; }
    public static CombatReadinessRules initial() { return new CombatReadinessRules(100,16,2,14,30,24,18,35,25); }
    public String canonicalText() { return capacity+":"+recoveryDelayTicks+":"+recoveryPerTick+":"+quickCost+":"+lungeCost
            +":"+sweepCost+":"+followupCost+":"+burstCost+":"+backstepCost; }
}
