package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.model.*;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Payload encodings for candidate admission and explicit topology cutover. */
final class RouteConstructionPayloadCodecs {
    private RouteConstructionPayloadCodecs() { }
    static PayloadCodec started() { return new PayloadCodec() {
        @Override public String type() { return "frontier.route_construction_started"; }
        @Override public byte[] encode(FrontierPayload payload) { return FrontierWorldPayloadCodecs.encodeProduction(output -> { var value = (RouteConstructionStarted) payload; write(output, value.project());
                ActorExecutionStateCodec.writeOptionalGroup(output, value.executions()); }); }
        @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes,
                input -> new RouteConstructionStarted(read(input), ActorExecutionStateCodec.readOptionalGroup(input))); }
    }; }
    static PayloadCodec cutover() { return new PayloadCodec() {
        @Override public String type() { return "frontier.route_topology_cutover"; }
        @Override public byte[] encode(FrontierPayload payload) { return FrontierWorldPayloadCodecs.encodeProduction(output -> { var value = (RouteTopologyCutover) payload; FrontierWorldPayloadCodecs.writeString(output,
                value.projectId().value()); ActorExecutionStateCodec.writeOptionalGroup(output, value.executions()); }); }
        @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new RouteTopologyCutover(new SubjectId(FrontierWorldPayloadCodecs.readString(input)),
                ActorExecutionStateCodec.readOptionalGroup(input))); }
    }; }
    static PayloadCodec materialLoaded() { return new PayloadCodec() {
        @Override public String type() { return "frontier.route_construction_material_loaded"; }
        @Override public byte[] encode(FrontierPayload payload) {
            RouteConstructionMaterialLoaded loaded = (RouteConstructionMaterialLoaded) payload;
            return FrontierWorldPayloadCodecs.encodeProduction(output -> {
                FrontierWorldPayloadCodecs.writeSubject(output, loaded.projectId()); FrontierWorldPayloadCodecs.writeSubject(output, loaded.cargo().id());
                FrontierWorldPayloadCodecs.writeSubject(output, loaded.cargo().ownerId()); output.writeByte(loaded.cargo().itemIds().size());
                for (SubjectId item : loaded.cargo().itemIds()) FrontierWorldPayloadCodecs.writeSubject(output, item);
            });
        }
        @Override public FrontierPayload decode(byte[] bytes) {
            return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> {
                SubjectId project = FrontierWorldPayloadCodecs.readSubject(input).value(); SubjectId cargo = FrontierWorldPayloadCodecs.readSubject(input).value();
                SubjectId owner = FrontierWorldPayloadCodecs.readSubject(input).value(); int count = input.readUnsignedByte();
                if (count < 1 || count > 64) throw new IllegalArgumentException("route construction cargo payload has invalid item count");
                List<SubjectId> items = new ArrayList<>(); for (int index = 0; index < count; index++) items.add(FrontierWorldPayloadCodecs.readSubject(input).value());
                return new RouteConstructionMaterialLoaded(project, new CargoBatch(cargo, owner, items));
            });
        }
    }; }
    static PayloadCodec assemblyStarted() { return assembly("frontier.route_construction_assembly_started", (id, assembly, executions, work, arrival) -> {
        EngineeringArrivalCodec.requireAbsent(arrival);
        return new RouteConstructionAssemblyStarted(id, assembly, executions, work);
    }); }
    static PayloadCodec assemblyAdvanced() { return assembly("frontier.route_construction_assembly_advanced", RouteConstructionAssemblyAdvanced::new); }

    private interface AssemblyFactory { FrontierPayload apply(SubjectId id, EngineeringWorkAssembly assembly, io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionGroup executions,
            java.util.Optional<io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionGroup> workExecutions,
            java.util.Optional<io.farfrontier.palemirror.frontier.v3.model.EngineeringHotArrival> arrival); }
    private static PayloadCodec assembly(String type, AssemblyFactory factory) {
        return new PayloadCodec() {
            @Override public String type() { return type; }
            @Override public byte[] encode(FrontierPayload payload) {
                SubjectId project; EngineeringWorkAssembly assembly; io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionGroup executions;
                        java.util.Optional<io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionGroup> work;
                if (payload instanceof RouteConstructionAssemblyStarted started) { project = started.projectId(); assembly = started.assembly(); executions = started.executions(); work = started.workExecutions(); }
                else if (payload instanceof RouteConstructionAssemblyAdvanced advanced) { project = advanced.projectId(); assembly = advanced.assembly(); executions = advanced.executions(); work = advanced.workExecutions(); }
                else throw new IllegalArgumentException("route construction assembly codec received foreign payload");
                return FrontierWorldPayloadCodecs.encodeProduction(output -> { FrontierWorldPayloadCodecs.writeSubject(output, project); writeAssembly(output, assembly); ActorExecutionStateCodec.writeGroup(output,
                        executions); ActorExecutionStateCodec.writeOptionalGroup(output, work);
                        EngineeringArrivalCodec.write(output, payload instanceof RouteConstructionAssemblyAdvanced advanced ? advanced.hotArrival() : java.util.Optional.empty()); });
            }
            @Override public FrontierPayload decode(byte[] bytes) {
                return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> factory.apply(FrontierWorldPayloadCodecs.readSubject(input).value(), readAssembly(input), ActorExecutionStateCodec.readGroup(input),
                        ActorExecutionStateCodec.readOptionalGroup(input), EngineeringArrivalCodec.read(input)));
            }
        };
    }
    private static void write(DataOutputStream output, RouteConstruction project) throws IOException {
        FrontierWorldPayloadCodecs.writeString(output, project.id().value()); FrontierWorldPayloadCodecs.writeString(output, project.settlementId().value());
        output.writeByte(0xFF); output.writeBoolean(project.team().isPresent());
        if (project.team().isPresent()) writeTeam(output, project.team().orElseThrow());
        output.writeByte(project.status().wireTag()); output.writeShort(project.confirmedCells()); output.writeByte(project.waypoints().size());
        for (BlockPosition waypoint : project.waypoints()) FrontierWorldPayloadCodecs.writePosition(output, waypoint);
    }
    private static RouteConstruction read(DataInputStream input) throws IOException {
        SubjectId id = new SubjectId(FrontierWorldPayloadCodecs.readString(input)), settlement = new SubjectId(FrontierWorldPayloadCodecs.readString(input));
        int marker = input.readUnsignedByte();
        java.util.Optional<EngineeringRecoveryTeam> team = java.util.Optional.empty();
        int status;
        if (marker != 0xFF) throw new IllegalArgumentException("obsolete construction admission schema");
        if (input.readBoolean()) team = java.util.Optional.of(readTeam(input));
        status = input.readUnsignedByte();
        int confirmed = input.readUnsignedShort(), points = input.readUnsignedByte();
        if (status >= RouteConstructionStatus.values().length || points < RouteTopology.MIN_WAYPOINTS || points > RouteTopology.MAX_WAYPOINTS) {
            throw new IllegalArgumentException("route construction payload is invalid");
        }
        List<BlockPosition> waypoints = new ArrayList<>(); for (int index = 0; index < points; index++) waypoints.add(FrontierWorldPayloadCodecs.readPosition(input));
        return new RouteConstruction(id, settlement, waypoints, confirmed, FrontierWireTags.require(RouteConstructionStatus.class, status), java.util.Optional.empty(), team);
    }

    private static void writeTeam(DataOutputStream output, EngineeringRecoveryTeam team) throws IOException {
        FrontierWorldPayloadCodecs.writeSubject(output, team.id()); FrontierWorldPayloadCodecs.writeSubject(output, team.ownerId());
        FrontierWorldPayloadCodecs.writeSubject(output, team.settlementId()); FrontierWorldPayloadCodecs.writeSubject(output, team.leaderId());
        output.writeByte(team.memberIds().size());
        for (SubjectId member : team.memberIds()) FrontierWorldPayloadCodecs.writeSubject(output, member);
    }

    private static EngineeringRecoveryTeam readTeam(DataInputStream input) throws IOException {
        SubjectId id = FrontierWorldPayloadCodecs.readSubject(input).value(); SubjectId owner = FrontierWorldPayloadCodecs.readSubject(input).value();
        SubjectId settlement = FrontierWorldPayloadCodecs.readSubject(input).value(); SubjectId leader = FrontierWorldPayloadCodecs.readSubject(input).value();
        int count = input.readUnsignedByte();
        if (count < EngineeringRecoveryTeam.MIN_MEMBERS || count > EngineeringRecoveryTeam.MAX_MEMBERS) throw new IllegalArgumentException("route construction team payload has invalid size");
        List<SubjectId> members = new ArrayList<>(); for (int index = 0; index < count; index++) members.add(FrontierWorldPayloadCodecs.readSubject(input).value());
        return new EngineeringRecoveryTeam(id, owner, settlement, leader, members);
    }

    static void writeAssembly(DataOutputStream output, EngineeringWorkAssembly assembly) throws IOException {
        RouteConstructionStateCodec.writeAssembly(output, assembly);
    }
    static EngineeringWorkAssembly readAssembly(DataInputStream input) throws IOException {
        return RouteConstructionStateCodec.readAssembly(input);
    }
}
