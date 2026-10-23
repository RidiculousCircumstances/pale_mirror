package io.farfrontier.palemirror.frontier.v3.model.tooling;
import io.farfrontier.palemirror.frontier.v3.model.ExactItemStack;
import java.util.*;

/** Explicit alternatives of complete capability sets, never an inferred meaning of a collection. */
public record EquipmentRequirements(List<Set<ToolRequirement>> alternatives) {
    public EquipmentRequirements {
        alternatives = alternatives.stream().map(Set::copyOf).toList();
        if (alternatives.isEmpty() || alternatives.stream().anyMatch(Set::isEmpty))
            throw new IllegalArgumentException("equipment must declare nonempty capability alternatives");
    }
    public static EquipmentRequirements anyOf(Set<ToolRequirement> requirements) {
        return new EquipmentRequirements(requirements.stream().map(Set::of).toList());
    }
    public static EquipmentRequirements allOf(Set<ToolRequirement> requirements) {
        return new EquipmentRequirements(List.of(requirements));
    }
    public boolean serviceable(ToolCatalog catalog, ExactItemStack item) {
        return alternatives.stream().anyMatch(group -> group.stream().allMatch(requirement -> catalog.serviceable(item, requirement)));
    }
}
