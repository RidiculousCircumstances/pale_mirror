package io.farfrontier.palemirror.frontier.v3.model.tooling;

import io.farfrontier.palemirror.frontier.v3.model.ExactItemStack;
import java.util.Objects;

/** A capability contract need not be an operation or imply any equipment wear. */
public sealed interface EquipmentRequirement permits EquipmentRequirement.Capability, EquipmentRequirement.Operation {
    boolean supports(ToolDefinition definition);
    boolean serviceable(ToolCatalog catalog, ExactItemStack item);
    record Capability(String ability, int minimumGrade) implements EquipmentRequirement {
        public Capability {
            ToolDefinition.requireKey(ability);
            if (minimumGrade < 0) throw new IllegalArgumentException("negative equipment capability grade");
        }
        public boolean supports(ToolDefinition definition) { return definition.abilities().getOrDefault(ability, -1) >= minimumGrade; }
        public boolean serviceable(ToolCatalog catalog, ExactItemStack item) {
            var definition = catalog.find(item.itemKind());
            return item.count() == 1 && item.condition().durable() && definition.filter(this::supports)
                    .filter(value -> item.condition().remaining() > value.maintenanceReserve()).isPresent();
        }
    }
    record Operation(ToolRequirement requirement) implements EquipmentRequirement {
        public Operation { Objects.requireNonNull(requirement); }
        public boolean supports(ToolDefinition definition) { return definition.supports(requirement); }
        public boolean serviceable(ToolCatalog catalog, ExactItemStack item) { return catalog.serviceable(item, requirement); }
    }
}
