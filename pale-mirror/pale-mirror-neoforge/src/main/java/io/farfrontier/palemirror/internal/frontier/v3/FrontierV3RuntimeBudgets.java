package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget;

/** Keeps ordinary player turns and explicitly requested operator advancement on distinct bounds. */
final class FrontierV3RuntimeBudgets {
    private static final WorkBudget ORDINARY_TICK = new WorkBudget(1, 512);
    private static final WorkBudget FAST_FORWARD_TICK = new WorkBudget(128, 512);

    private FrontierV3RuntimeBudgets() { }

    /** Stable due order carries deferred canonical actions into later ordinary player turns. */
    static WorkBudget ordinaryTick() { return ORDINARY_TICK; }
    static WorkBudget fastForwardTick() { return FAST_FORWARD_TICK; }
}
