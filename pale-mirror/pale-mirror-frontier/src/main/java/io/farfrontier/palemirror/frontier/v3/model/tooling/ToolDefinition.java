package io.farfrontier.palemirror.frontier.v3.model.tooling;

import io.farfrontier.palemirror.frontier.v3.model.ItemCondition;
import java.util.Map;

/** Data-only provider profile. Neither inventory, work selection nor manufacturing authority. */
public record ToolDefinition(String itemKind, Map<String, Integer> abilities, int durability, int speedPermille,
                             int maintenanceReserve) {
    public ToolDefinition {
        requireKey(itemKind); abilities = Map.copyOf(abilities);
        if (abilities.isEmpty() || abilities.size() > 64 || durability < 1 || speedPermille < 1 || speedPermille > 100_000
                || maintenanceReserve < 1 || maintenanceReserve >= durability)
            throw new IllegalArgumentException("invalid bounded equipment definition");
        abilities.forEach((ability, grade) -> {
            requireKey(ability);
            if (grade < 0) throw new IllegalArgumentException("negative equipment grade");
        });
    }
    public boolean supports(ToolRequirement requirement) {
        return abilities.getOrDefault(requirement.ability(), -1) >= requirement.minimumGrade();
    }
    public ItemCondition pristine() { return new ItemCondition(durability, 0); }
    public String canonicalText() {
        return itemKind + ":" + durability + ":" + speedPermille + ":" + maintenanceReserve + ":" + abilities.entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(java.util.stream.Collectors.joining(","));
    }
    static void requireKey(String value) {
        if (value == null || !value.matches("[a-z][a-z0-9_-]{0,31}:[a-z0-9][a-z0-9_./-]{0,127}"))
            throw new IllegalArgumentException("equipment requires a namespaced identity");
    }
}
