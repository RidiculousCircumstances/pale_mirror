package io.farfrontier.palemirror.api;

import java.util.List;

/** Complete frontier boundary; intentional gate throats are the only openings. */
public record PerimeterPlan(List<PerimeterModulePlan> modules) {
    public PerimeterPlan {
        modules = List.copyOf(modules);
        if (modules.isEmpty()) throw new IllegalArgumentException("perimeter modules are required");
        if (modules.stream().map(PerimeterModulePlan::moduleId).distinct().count() != modules.size()) {
            throw new IllegalArgumentException("perimeter module ids must be unique");
        }
        long freight = modules.stream().filter(value -> value.kind() == PerimeterModuleKind.FREIGHT_GATE).count();
        long pedestrian = modules.stream().filter(value -> value.kind() == PerimeterModuleKind.PEDESTRIAN_GATE).count();
        if (freight != 1 || pedestrian != 3) {
            throw new IllegalArgumentException("frontier perimeter requires one freight and three pedestrian gates");
        }
    }
}
