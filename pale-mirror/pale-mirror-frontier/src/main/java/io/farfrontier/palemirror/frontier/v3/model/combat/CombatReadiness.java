package io.farfrontier.palemirror.frontier.v3.model.combat;

/** One bounded effort account per combat owner; recovery is evaluated lazily in simulation time. */
public record CombatReadiness(int remaining, long spentAt) {
    public CombatReadiness {
        if (remaining < 0 || spentAt < 0) throw new IllegalArgumentException("invalid combat readiness");
    }
    public int available(long tick, CombatReadinessRules rules) {
        if (tick < spentAt || remaining > rules.capacity()) throw new IllegalArgumentException("stale combat readiness");
        long elapsed = tick - spentAt;
        long recovery = elapsed <= rules.recoveryDelayTicks() ? 0 : elapsed - rules.recoveryDelayTicks();
        return (int) (remaining + Math.min(rules.capacity() - remaining, Math.min(recovery, rules.capacity()) * rules.recoveryPerTick()));
    }
    public static int available(CombatReadiness account, long tick, CombatReadinessRules rules) {
        return account == null ? rules.capacity() : account.available(tick, rules);
    }
    public static CombatReadiness spend(CombatReadiness account, long tick, int cost, CombatReadinessRules rules) {
        int amount = available(account, tick, rules);
        if (cost < 1 || amount < cost) throw new IllegalArgumentException("insufficient combat readiness");
        return new CombatReadiness(amount - cost, tick);
    }
}
