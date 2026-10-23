package io.farfrontier.palemirror.frontier.v3.model.tooling;

import io.farfrontier.palemirror.frontier.v3.model.ExactItemStack;
import io.farfrontier.palemirror.frontier.v3.model.InventoryCustody;
import java.util.Objects;

/** Retained before/after condition for one confirmed work operation, not a second item ledger. */
public record ToolUse(ExactItemStack before, int wear) {
    public ToolUse {
        Objects.requireNonNull(before);
        if (!(before.custody() instanceof InventoryCustody.Actor) || before.count() != 1
                || wear < 1 || before.condition().remaining() <= wear)
            throw new IllegalArgumentException("work use requires held equipment and a surviving maintenance reserve");
    }
    public static ToolUse prepare(ExactItemStack item, ToolCatalog tools, ToolRequirement requirement) {
        if (!tools.serviceable(item, requirement)) throw new IllegalArgumentException("equipment requires service or cannot perform this operation");
        return new ToolUse(item, requirement.wear());
    }
    public ExactItemStack after() { return before.withCondition(before.condition().wear(wear)); }
}
