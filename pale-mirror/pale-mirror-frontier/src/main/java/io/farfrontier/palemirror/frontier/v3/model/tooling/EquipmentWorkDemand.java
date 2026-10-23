package io.farfrontier.palemirror.frontier.v3.model.tooling;

import java.util.*;

/** Productive capability needed by one work pool. Wear is demand, never an inventory mutation. */
public record EquipmentWorkDemand(String pool, List<ToolRequirement> requirements, int concurrentUnits, long plannedWear, long labourTicksPerWear) {
    public EquipmentWorkDemand {
        Objects.requireNonNull(pool); requirements = List.copyOf(requirements);
        if (pool.isBlank() || requirements.isEmpty() || concurrentUnits < 1 || plannedWear < concurrentUnits || labourTicksPerWear < 1)
            throw new IllegalArgumentException("invalid productive equipment demand");
    }
    public boolean supports(ToolDefinition definition) { return requirements.stream().allMatch(definition::supports); }
    public long usableWear(ToolDefinition definition, io.farfrontier.palemirror.frontier.v3.model.ItemCondition condition) {
        return supports(definition) ? Math.max(0, condition.remaining() - definition.maintenanceReserve()) : 0;
    }
}
