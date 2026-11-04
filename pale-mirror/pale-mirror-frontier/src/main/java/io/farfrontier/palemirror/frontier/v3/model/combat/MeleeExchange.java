package io.farfrontier.palemirror.frontier.v3.model.combat;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId;
import java.util.Objects;
import java.util.OptionalLong;

/** Bounded encounter memory. Observations are idempotent; no native clock or unbounded hit log. */
public record MeleeExchange(ActorExecutionId execution, SubjectId weapon, CombatTarget target,
        long harmedAt, int hits, int misses, long failedAt,
        long handledThrough, long withdrawnThrough, int side) {
    public MeleeExchange {
        Objects.requireNonNull(execution); Objects.requireNonNull(weapon); Objects.requireNonNull(target);
        if (harmedAt < -1 || hits < 0 || hits > 8 || misses < 0 || misses > 8
                || failedAt < -1 || handledThrough < -1 || withdrawnThrough < -1 || side < -1 || side > 1)
            throw new IllegalArgumentException("invalid melee exchange");
    }
    public static MeleeExchange first(CombatOrder order) {
        return new MeleeExchange(order.execution(),order.weapon(),order.target(),-1,0,0,-1,-1,-1,0);
    }
    public static MeleeExchange first(MeleeStrike strike) {
        return new MeleeExchange(strike.attacker().execution(),strike.weapon(),strike.target(),-1,0,0,-1,-1,-1,0);
    }
    public boolean matches(MeleeStrike strike) {
        return execution.equals(strike.attacker().execution()) && weapon.equals(strike.weapon()) && target.equals(strike.target());
    }
    public boolean matches(CombatOrder order) {
        return execution.equals(order.execution()) && weapon.equals(order.weapon()) && target.equals(order.target());
    }
    public MeleeExchange observed(OptionalLong harm, long tick, MeleeDuelRules rules) {
        if (harm.isEmpty() || harm.getAsLong() <= harmedAt || harm.getAsLong() > tick) return this;
        return new MeleeExchange(execution,weapon,target,harm.getAsLong(),Math.min(8,harmedAt>=0 && harm.getAsLong()-harmedAt<=rules.pressureWindowTicks()?hits+1:1),misses,failedAt,handledThrough,withdrawnThrough,side);
    }
    public long signal() { return Math.max(harmedAt,failedAt); }
    public boolean pressured(long tick, MeleeDuelRules rules) {
        boolean repeated = hits >= rules.repeatedHits() && tick >= harmedAt && tick-harmedAt <= rules.pressureWindowTicks();
        boolean failed = misses >= rules.repeatedMisses() && tick >= failedAt && tick-failedAt <= rules.pressureWindowTicks();
        return signal() > handledThrough && (repeated || failed);
    }
    public MeleeExchange completed(MeleeOutcome outcome, long tick, MeleeDuelRules rules) {
        int count = outcome==MeleeOutcome.MISS ? Math.min(8,failedAt>=0 && tick-failedAt<=rules.pressureWindowTicks()?misses+1:1)
                : outcome==MeleeOutcome.HIT ? 0 : misses;
        return new MeleeExchange(execution,weapon,target,harmedAt,hits,count,
                outcome==MeleeOutcome.MISS?tick:failedAt,handledThrough,
                outcome==MeleeOutcome.CANCELLED?Math.max(withdrawnThrough,signal()):withdrawnThrough,side);
    }
    public MeleeExchange repositioned(MeleeManeuver.Kind kind) {
        if (!kind.lateral()) return this;
        return new MeleeExchange(execution,weapon,target,harmedAt,hits,0,failedAt,signal(),withdrawnThrough,kind.side());
    }
}
