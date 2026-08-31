package io.farfrontier.palemirror.frontier.v3.model;

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
        @Override public byte[] encode(FrontierPayload payload) { return FrontierWorldPayloadCodecs.encodeProduction(output -> write(output, ((RouteConstructionStarted) payload).project())); }
        @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new RouteConstructionStarted(read(input))); }
    }; }
    static PayloadCodec cutover() { return new PayloadCodec() {
        @Override public String type() { return "frontier.route_topology_cutover"; }
        @Override public byte[] encode(FrontierPayload payload) { return FrontierWorldPayloadCodecs.encodeProduction(output -> FrontierWorldPayloadCodecs.writeString(output, ((RouteTopologyCutover) payload).projectId().value())); }
        @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new RouteTopologyCutover(new SubjectId(FrontierWorldPayloadCodecs.readString(input)))); }
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
    private static void write(DataOutputStream output, RouteConstruction project) throws IOException {
        FrontierWorldPayloadCodecs.writeString(output, project.id().value()); FrontierWorldPayloadCodecs.writeString(output, project.settlementId().value());
        output.writeByte(project.status().wireTag()); output.writeShort(project.confirmedCells()); output.writeByte(project.waypoints().size());
        for (BlockPosition waypoint : project.waypoints()) FrontierWorldPayloadCodecs.writePosition(output, waypoint);
    }
    private static RouteConstruction read(DataInputStream input) throws IOException {
        SubjectId id = new SubjectId(FrontierWorldPayloadCodecs.readString(input)), settlement = new SubjectId(FrontierWorldPayloadCodecs.readString(input));
        int status = input.readUnsignedByte(), confirmed = input.readUnsignedShort(), points = input.readUnsignedByte();
        if (status >= RouteConstructionStatus.values().length || points < RouteTopology.MIN_WAYPOINTS || points > RouteTopology.MAX_WAYPOINTS) {
            throw new IllegalArgumentException("route construction payload is invalid");
        }
        List<BlockPosition> waypoints = new ArrayList<>(); for (int index = 0; index < points; index++) waypoints.add(FrontierWorldPayloadCodecs.readPosition(input));
        return new RouteConstruction(id, settlement, waypoints, confirmed, FrontierWireTags.require(RouteConstructionStatus.class, status));
    }
}
