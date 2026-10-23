package io.farfrontier.palemirror.frontier.v3.model.tooling;

import io.farfrontier.palemirror.frontier.v3.model.ExactItemStack;

/** Combines independent worker and equipment factors; neither owns the other one's statistics. */
public final class ToolWorkRate {
    public static int speedPermille(int workerSpeed, ToolCatalog tools, ExactItemStack item, ToolRequirement requirement) {
        if (workerSpeed < 1 || !tools.serviceable(item, requirement))
            throw new IllegalArgumentException("work speed requires a suitable serviceable tool and positive worker rate");
        return Math.toIntExact(Math.max(1L, Math.multiplyExact((long) workerSpeed, tools.require(item.itemKind()).speedPermille()) / 1_000L));
    }
    private ToolWorkRate() { }
}
