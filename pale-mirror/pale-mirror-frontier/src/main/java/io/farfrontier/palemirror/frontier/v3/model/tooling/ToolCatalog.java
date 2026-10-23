package io.farfrontier.palemirror.frontier.v3.model.tooling;

import io.farfrontier.palemirror.frontier.v3.model.ExactItemStack;
import java.util.Map;
import java.util.Optional;

/** Frozen provider definitions; task admission and item ownership remain outside this catalog. */
public record ToolCatalog(Map<String, ToolDefinition> definitions) {
    public ToolCatalog {
        definitions = Map.copyOf(definitions);
        if (definitions.size() > 256 || definitions.entrySet().stream().anyMatch(e -> !e.getKey().equals(e.getValue().itemKind())))
            throw new IllegalArgumentException("invalid equipment definition catalog");
    }
    public static ToolCatalog empty() { return new ToolCatalog(Map.of()); }
    public Optional<ToolDefinition> find(String itemKind) { return Optional.ofNullable(definitions.get(itemKind)); }
    public ToolDefinition require(String itemKind) {
        return find(itemKind).orElseThrow(() -> new IllegalArgumentException("unregistered equipment " + itemKind));
    }
    public boolean suitable(ExactItemStack item, ToolRequirement requirement) {
        return item.count() == 1 && suitable(item.itemKind(), item.condition(), requirement);
    }
    public boolean suitable(String itemKind, io.farfrontier.palemirror.frontier.v3.model.ItemCondition condition, ToolRequirement requirement) {
        var definition = definitions.get(itemKind);
        return definition != null && definition.supports(requirement)
                && condition.durable() && condition.remaining() >= requirement.wear();
    }
    /** Autonomous workers request service before destroying their equipment, not after it disappears. */
    public boolean serviceable(ExactItemStack item, ToolRequirement requirement) {
        return suitable(item, requirement)
                && item.condition().remaining() - requirement.wear() >= require(item.itemKind()).maintenanceReserve();
    }
    public String canonicalText() {
        return definitions.values().stream().sorted(java.util.Comparator.comparing(ToolDefinition::itemKind))
                .map(ToolDefinition::canonicalText).collect(java.util.stream.Collectors.joining(";"));
    }
}
