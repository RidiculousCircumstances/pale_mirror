package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget;

/** Keeps ordinary player turns and explicitly requested operator advancement on distinct bounds. */
final class FrontierV3RuntimeBudgets {
    private static final WorkBudget ORDINARY_TICK = new WorkBudget(1, 512);
    // A queued operator turn must not turn one coincident recurring-duty wave into a seconds-long
    // server-thread batch. The lifecycle may advance several otherwise-idle canonical turns in
    // one slice, but each turn commits at most this many durable due actions in their normal
    // stable order. Remaining due work is retained by the engine for the next canonical turn.
    private static final WorkBudget FAST_FORWARD_TICK = new WorkBudget(4, 512);

    private FrontierV3RuntimeBudgets() { }

    /** Stable due order carries deferred canonical actions into later ordinary player turns. */
    static WorkBudget ordinaryTick() { return ORDINARY_TICK; }
    static WorkBudget fastForwardTick() { return FAST_FORWARD_TICK; }
}
