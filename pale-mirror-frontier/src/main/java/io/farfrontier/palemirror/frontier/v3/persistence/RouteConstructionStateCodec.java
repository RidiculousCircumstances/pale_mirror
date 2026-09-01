package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.model.*;

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
            output.writeShort(project.workCells().size());
            for (BlockPosition cell : project.workCells()) FrontierWorldStateCodec.writePosition(output, cell);
            output.writeBoolean(project.cargoId().isPresent()); if (project.cargoId().isPresent()) FrontierWorldStateCodec.writeString(output, project.cargoId().orElseThrow().value());
            output.writeBoolean(project.team().isPresent());
            if (project.team().isPresent()) writeTeam(output, project.team().orElseThrow());
            output.writeBoolean(project.assembly().isPresent());
            if (project.assembly().isPresent()) writeAssembly(output, project.assembly().orElseThrow());
        }
    }
    static Map<SubjectId, RouteConstruction> read(DataInputStream input, boolean includesCargo, boolean includesTeam, boolean includesAssembly,
                                                  boolean includesWorkCells) throws IOException {
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
            java.util.ArrayList<BlockPosition> workCells = new java.util.ArrayList<>();
            if (includesWorkCells) {
                int cells = input.readUnsignedShort();
                if (cells < 1 || cells > 65_535) throw new IllegalArgumentException("route construction work plan size is invalid");
                for (int cell = 0; cell < cells; cell++) workCells.add(FrontierWorldStateCodec.readPosition(input));
            }
            java.util.Optional<SubjectId> cargo = includesCargo && input.readBoolean() ? java.util.Optional.of(new SubjectId(FrontierWorldStateCodec.readString(input))) : java.util.Optional.empty();
            java.util.Optional<EngineeringRecoveryTeam> team = includesTeam && input.readBoolean() ? java.util.Optional.of(readTeam(input)) : java.util.Optional.empty();
            java.util.Optional<EngineeringWorkAssembly> assembly = includesAssembly && input.readBoolean() ? java.util.Optional.of(readAssembly(input)) : java.util.Optional.empty();
            RouteConstruction project = new RouteConstruction(id, settlement, List.copyOf(waypoints), List.copyOf(workCells), confirmed,
                    FrontierWireTags.require(RouteConstructionStatus.class, status), cargo, team, assembly);
            if (projects.put(id, project) != null) throw new IllegalArgumentException("duplicate route construction id");
        }
        return Map.copyOf(projects);
    }

    private static void writeTeam(DataOutputStream output, EngineeringRecoveryTeam team) throws IOException {
        FrontierWorldStateCodec.writeString(output, team.id().value()); FrontierWorldStateCodec.writeString(output, team.ownerId().value());
        FrontierWorldStateCodec.writeString(output, team.settlementId().value()); FrontierWorldStateCodec.writeString(output, team.leaderId().value());
        output.writeByte(team.memberIds().size());
        for (SubjectId member : team.memberIds()) FrontierWorldStateCodec.writeString(output, member.value());
    }

    private static EngineeringRecoveryTeam readTeam(DataInputStream input) throws IOException {
        SubjectId id = new SubjectId(FrontierWorldStateCodec.readString(input)); SubjectId owner = new SubjectId(FrontierWorldStateCodec.readString(input));
        SubjectId settlement = new SubjectId(FrontierWorldStateCodec.readString(input)); SubjectId leader = new SubjectId(FrontierWorldStateCodec.readString(input));
        int count = input.readUnsignedByte();
        if (count < EngineeringRecoveryTeam.MIN_MEMBERS || count > EngineeringRecoveryTeam.MAX_MEMBERS) throw new IllegalArgumentException("route construction team size is invalid");
        java.util.ArrayList<SubjectId> members = new java.util.ArrayList<>();
        for (int index = 0; index < count; index++) members.add(new SubjectId(FrontierWorldStateCodec.readString(input)));
        return new EngineeringRecoveryTeam(id, owner, settlement, leader, List.copyOf(members));
    }

    private static void writeAssembly(DataOutputStream output, EngineeringWorkAssembly assembly) throws IOException {
        output.writeByte(assembly.members().size());
        for (Map.Entry<SubjectId, EngineeringWorkAssembly.Member> entry : assembly.members().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            FrontierWorldStateCodec.writeString(output, entry.getKey().value());
            EngineeringWorkAssembly.Member member = entry.getValue();
            output.writeShort(member.corridor().size());
            for (BlockPosition cell : member.corridor()) FrontierWorldStateCodec.writePosition(output, cell);
            output.writeShort(member.cursor());
        }
    }

    private static EngineeringWorkAssembly readAssembly(DataInputStream input) throws IOException {
        int count = input.readUnsignedByte();
        if (count < EngineeringRecoveryTeam.MIN_MEMBERS || count > EngineeringRecoveryTeam.MAX_MEMBERS) throw new IllegalArgumentException("route construction assembly size is invalid");
        Map<SubjectId, EngineeringWorkAssembly.Member> members = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            SubjectId actor = new SubjectId(FrontierWorldStateCodec.readString(input)); int cells = input.readUnsignedShort();
            if (cells < 1 || cells > OperationTravel.MAX_CELLS) throw new IllegalArgumentException("route construction assembly corridor size is invalid");
            java.util.ArrayList<BlockPosition> corridor = new java.util.ArrayList<>();
            for (int cell = 0; cell < cells; cell++) corridor.add(FrontierWorldStateCodec.readPosition(input));
            if (members.put(actor, new EngineeringWorkAssembly.Member(corridor, input.readUnsignedShort())) != null) {
                throw new IllegalArgumentException("route construction assembly has duplicate member");
            }
        }
        return new EngineeringWorkAssembly(members);
    }
}
