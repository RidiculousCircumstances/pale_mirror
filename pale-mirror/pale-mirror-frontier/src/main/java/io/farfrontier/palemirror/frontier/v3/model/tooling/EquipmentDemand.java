package io.farfrontier.palemirror.frontier.v3.model.tooling;

import java.util.*;

/** Minimum usable units plus optional forecast work wear. Neither grants an item or mutates condition. */
public record EquipmentDemand(String pool, List<EquipmentRequirement> requirements, int concurrentUnits,
        Optional<Wear> workWear, Acquisition acquisition) {
    public enum Acquisition { PRODUCE_OR_BUY, PRODUCE_LOCAL }
    public record Wear(long units, long labourTicksPerUnit) {
        public Wear { if (units < 1 || labourTicksPerUnit < 1) throw new IllegalArgumentException("invalid equipment work forecast"); }
    }
    public EquipmentDemand {
        Objects.requireNonNull(pool); requirements = List.copyOf(requirements);
        Objects.requireNonNull(workWear); Objects.requireNonNull(acquisition);
        if (pool.isBlank() || requirements.isEmpty() || concurrentUnits < 1)
            throw new IllegalArgumentException("invalid equipment availability demand");
    }
    public EquipmentDemand(String pool, List<ToolRequirement> operations, int units, long wear, long labour) {
        this(pool, operations.stream().map(value -> (EquipmentRequirement) new EquipmentRequirement.Operation(value)).toList(),
                units, Optional.of(new Wear(wear, labour)), Acquisition.PRODUCE_OR_BUY);
    }
    public long plannedWear() { return workWear.map(Wear::units).orElse(0L); }
    public long labourTicksPerWear() { return workWear.map(Wear::labourTicksPerUnit).orElse(0L); }
    public boolean supports(ToolDefinition definition) { return requirements.stream().allMatch(requirement -> requirement.supports(definition)); }
    public long usableWear(ToolDefinition definition, io.farfrontier.palemirror.frontier.v3.model.ItemCondition condition) {
        return supports(definition) ? Math.max(0, condition.remaining() - definition.maintenanceReserve()) : 0;
    }
}
