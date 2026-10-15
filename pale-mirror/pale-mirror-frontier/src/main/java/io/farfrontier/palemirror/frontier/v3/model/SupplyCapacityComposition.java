package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.supply.SupplyPlanner;
import java.util.*;

/** Closed owner composition; missing/duplicate producers never inherit availability. */
public final class SupplyCapacityComposition {
    private static final Map<SupplyCapacityPort.Key, SupplyCapacityPort> PORTS = register(List.of(new BakerySupplyCapacity()));
    private SupplyCapacityComposition() { }
    public static Map<SupplyCapacityPort.Key, SupplyCapacityPort> register(List<SupplyCapacityPort> ports) {
        var result = new EnumMap<SupplyCapacityPort.Key, SupplyCapacityPort>(SupplyCapacityPort.Key.class);
        for (var port : ports) if (result.put(port.key(), port) != null) throw new IllegalArgumentException("duplicate supply capacity owner");
        if (result.size() != SupplyCapacityPort.Key.values().length) throw new IllegalArgumentException("missing supply capacity owner");
        return Map.copyOf(result);
    }
    public static List<SupplyPlanner.Process> read(FrontierWorldState state, SubjectId home, Map<String, SupplyPlanner.Stock> stocks) {
        var result = new ArrayList<SupplyPlanner.Process>();
        for (var key : SupplyCapacityPort.Key.values()) result.addAll(PORTS.get(key).read(state, home, stocks));
        return List.copyOf(result);
    }
    public static List<SupplyPlanner.Dependency> dependencies(FrontierWorldState state, SubjectId home, Map<String, SupplyPlanner.Stock> stocks) {
        var result = new ArrayList<SupplyPlanner.Dependency>();
        for (var key : SupplyCapacityPort.Key.values()) result.addAll(PORTS.get(key).dependencies(state, home, stocks));
        return List.copyOf(result);
    }
}
