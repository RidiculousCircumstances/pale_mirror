package io.farfrontier.palemirror.frontier.v3.model.combat;

/** Hashed finite miss response. Normal weapon cadence and coordinated strike admission still apply. */
public record MeleeFollowupRules(int windowTicks, int recoveryTicks, MeleeTechniques.Technique technique) {
    public MeleeFollowupRules {
        java.util.Objects.requireNonNull(technique);
        if (windowTicks < 1 || windowTicks > 60 || recoveryTicks < 1 || recoveryTicks >= windowTicks)
            throw new IllegalArgumentException("invalid melee followup policy");
    }
    public static MeleeFollowupRules initial() {
        return new MeleeFollowupRules(24, 2, new MeleeTechniques.Technique(3, 3, 8, 1_000_000));
    }
    public String canonicalText() { return windowTicks + ":" + recoveryTicks + ":" + technique.canonicalText(); }
}
