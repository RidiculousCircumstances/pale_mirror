package io.farfrontier.palemirror.frontier.v3.model.tooling;
import io.farfrontier.palemirror.frontier.v3.model.ExactItemStack;
import java.util.*;

/** Explicit alternatives of complete capability sets, never an inferred meaning of a collection. */
public record EquipmentRequirements(List<Set<EquipmentRequirement>> alternatives) {
    public EquipmentRequirements {
        alternatives = alternatives.stream().map(Set::copyOf).toList();
        if (alternatives.isEmpty() || alternatives.stream().anyMatch(Set::isEmpty))
            throw new IllegalArgumentException("equipment must declare nonempty capability alternatives");
    }
    public static EquipmentRequirements anyOf(Set<ToolRequirement> requirements) {
        return new EquipmentRequirements(requirements.stream().map(value -> Set.<EquipmentRequirement>of(new EquipmentRequirement.Operation(value))).toList());
    }
    public static EquipmentRequirements allOf(Set<ToolRequirement> requirements) {
        return all(requirements.stream().map(value -> (EquipmentRequirement) new EquipmentRequirement.Operation(value)).collect(java.util.stream.Collectors.toSet()));
    }
    public static EquipmentRequirements all(Set<EquipmentRequirement> requirements) {
        return new EquipmentRequirements(List.of(requirements));
    }
    public boolean supports(ToolDefinition definition) {
        return alternatives.stream().anyMatch(group -> group.stream().allMatch(requirement -> requirement.supports(definition)));
    }
    public boolean serviceable(ToolCatalog catalog, ExactItemStack item) {
        return alternatives.stream().anyMatch(group -> group.stream().allMatch(requirement -> requirement.serviceable(catalog, item)));
    }
}
