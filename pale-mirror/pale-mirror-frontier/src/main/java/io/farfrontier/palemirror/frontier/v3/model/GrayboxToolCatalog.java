package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.tooling.*;
import java.util.Map;

/** Authored equipment content, not an inference from a registry name or an activity role. */
public final class GrayboxToolCatalog {
    private GrayboxToolCatalog() { }
    public static java.util.List<ToolDefinition> initialFor(ToolCatalog catalog,
            java.util.Collection<ToolRequirement> requirements) {
        return requirements.stream().map(requirement -> catalog.definitions().values().stream()
                .filter(definition -> definition.supports(requirement))
                .min(java.util.Comparator.comparingInt(ToolDefinition::durability).thenComparing(ToolDefinition::itemKind))
                .orElseThrow(() -> new IllegalArgumentException("initial work has no declared equipment provider")))
                .distinct().sorted(java.util.Comparator.comparing(ToolDefinition::itemKind)).toList();
    }
    public static ToolCatalog initial() {
        return new ToolCatalog(Map.of(
                "minecraft:stone_pickaxe", new ToolDefinition("minecraft:stone_pickaxe", Map.of(ToolAbilities.MINE, 1), 131, 1000, 1),
                "minecraft:iron_pickaxe", new ToolDefinition("minecraft:iron_pickaxe", Map.of(ToolAbilities.MINE, 2), 250, 1500, 1),
                "minecraft:stone_shovel", new ToolDefinition("minecraft:stone_shovel", Map.of(ToolAbilities.DIG, 1), 131, 1000, 1),
                "minecraft:iron_shovel", new ToolDefinition("minecraft:iron_shovel", Map.of(ToolAbilities.DIG, 2), 250, 1500, 1),
                "minecraft:stone_hoe", new ToolDefinition("minecraft:stone_hoe", Map.of(ToolAbilities.TILL, 1, ToolAbilities.HARVEST, 1), 131, 1000, 1),
                "minecraft:iron_hoe", new ToolDefinition("minecraft:iron_hoe", Map.of(ToolAbilities.TILL, 2, ToolAbilities.HARVEST, 2), 250, 1500, 1)));
    }
}
