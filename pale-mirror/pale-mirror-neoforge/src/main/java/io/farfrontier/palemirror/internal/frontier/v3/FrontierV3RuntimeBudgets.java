package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget;

/** Both execution locations consume the world's versioned deterministic admission bounds. */
final class FrontierV3RuntimeBudgets {
    private FrontierV3RuntimeBudgets() { }
    static WorkBudget ordinaryTick(io.farfrontier.palemirror.frontier.v3.model.FrontierRuleset ruleset) {
        return ruleset.execution().budget();
    }
    static WorkBudget fastForwardTick(io.farfrontier.palemirror.frontier.v3.model.FrontierRuleset ruleset) {
        return ruleset.execution().budget();
    }
}
