package io.farfrontier.palemirror.frontier.v3.model.combat;

/** Hashed finite miss response. Normal weapon cadence and coordinated strike admission still apply. */
public record MeleeFollowupRules(int windowTicks, int recoveryTicks, MeleeTechniques.Technique technique) {
    public MeleeFollowupRules {
        java.util.Objects.requireNonNull(technique);
        if (windowTicks < 1 || windowTicks > 60 || recoveryTicks < 1 || recoveryTicks >= windowTicks)
            throw new IllegalArgumentException("invalid melee followup policy");
    }
    public static MeleeFollowupRules initial() {
        return new MeleeFollowupRules(40, 14, CombatTrainingCatalog.standard().require(MeleeAttack.FOLLOWUP).timing());
    }
    public String canonicalText() { return windowTicks + ":" + recoveryTicks + ":" + technique.canonicalText(); }
}
