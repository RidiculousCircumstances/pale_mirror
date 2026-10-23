package io.farfrontier.palemirror.frontier.v3.model.tooling;

/** An operation's equipment contract, independent of a Minecraft item or worker. */
public record ToolRequirement(String ability, int minimumGrade, int wear) {
    public ToolRequirement {
        ToolDefinition.requireKey(ability);
        if (minimumGrade < 0 || wear < 1) throw new IllegalArgumentException("invalid operation equipment requirement");
    }
    public String canonicalText() { return ability + ":" + minimumGrade + ":" + wear; }
}
