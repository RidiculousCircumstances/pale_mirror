package io.farfrontier.palemirror.frontier.v3.model.group;

import io.farfrontier.palemirror.frontier.v3.model.group.UnitGroup;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/** Composition alone names mission implementations. Generic coordination depends on the port. */
public final class UnitGroupMissionPorts {
    private static final Map<UnitGroup.MissionKind, UnitGroupMissionPort> PORTS = registry(List.of(new TransportGroupMissionPort()));
    private UnitGroupMissionPorts() { }
    public static Map<UnitGroup.MissionKind, UnitGroupMissionPort> registry(List<UnitGroupMissionPort> ports) {
        var result = new EnumMap<UnitGroup.MissionKind, UnitGroupMissionPort>(UnitGroup.MissionKind.class);
        for (var port : List.copyOf(ports)) if (result.putIfAbsent(port.kind(), port) != null)
            throw new IllegalArgumentException("duplicate group mission provider");
        if (!result.keySet().equals(EnumSet.allOf(UnitGroup.MissionKind.class)))
            throw new IllegalArgumentException("missing group mission provider");
        return Map.copyOf(result);
    }
    public static UnitGroupMissionPort require(UnitGroup group) {
        var port = PORTS.get(group.mission().kind());
        if (port == null) throw new IllegalArgumentException("unknown group mission provider");
        return port;
    }
}
