package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bounded snapshot encoding for inactive, resumable replacement-route work. */
final class RouteConstructionStateCodec {
    private RouteConstructionStateCodec() { }
    static void write(DataOutputStream output, Map<SubjectId, RouteConstruction> projects) throws IOException {
        if (projects.size() > RouteConstructionStateSupport.MAX_CONSTRUCTIONS) throw new IllegalArgumentException("route construction count is out of bounds");
        output.writeByte(projects.size());
        for (RouteConstruction project : projects.values().stream().sorted(java.util.Comparator.comparing(RouteConstruction::id)).toList()) {
            FrontierWorldStateCodec.writeString(output, project.id().value()); FrontierWorldStateCodec.writeString(output, project.settlementId().value());
            output.writeByte(project.status().wireTag()); output.writeShort(project.confirmedCells()); output.writeByte(project.waypoints().size());
            for (BlockPosition waypoint : project.waypoints()) FrontierWorldStateCodec.writePosition(output, waypoint);
            output.writeBoolean(project.cargoId().isPresent()); if (project.cargoId().isPresent()) FrontierWorldStateCodec.writeString(output, project.cargoId().orElseThrow().value());
        }
    }
    static Map<SubjectId, RouteConstruction> read(DataInputStream input, boolean includesCargo) throws IOException {
        int count = input.readUnsignedByte(); if (count > RouteConstructionStateSupport.MAX_CONSTRUCTIONS) throw new IllegalArgumentException("route construction count is out of bounds");
        Map<SubjectId, RouteConstruction> projects = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            SubjectId id = new SubjectId(FrontierWorldStateCodec.readString(input)); SubjectId settlement = new SubjectId(FrontierWorldStateCodec.readString(input));
            int status = input.readUnsignedByte(), confirmed = input.readUnsignedShort(), waypointCount = input.readUnsignedByte();
            if (status >= RouteConstructionStatus.values().length || waypointCount < RouteTopology.MIN_WAYPOINTS || waypointCount > RouteTopology.MAX_WAYPOINTS) {
                throw new IllegalArgumentException("route construction encoding is invalid");
            }
            java.util.ArrayList<BlockPosition> waypoints = new java.util.ArrayList<>();
            for (int point = 0; point < waypointCount; point++) waypoints.add(FrontierWorldStateCodec.readPosition(input));
            java.util.Optional<SubjectId> cargo = includesCargo && input.readBoolean() ? java.util.Optional.of(new SubjectId(FrontierWorldStateCodec.readString(input))) : java.util.Optional.empty();
            RouteConstruction project = new RouteConstruction(id, settlement, List.copyOf(waypoints), confirmed, FrontierWireTags.require(RouteConstructionStatus.class, status), cargo);
            if (projects.put(id, project) != null) throw new IllegalArgumentException("duplicate route construction id");
        }
        return Map.copyOf(projects);
    }
}
