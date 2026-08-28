package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Optional;

/** WAL codecs for one persistent route-patrol traversal and its physical finding. */
final class RoutePatrolPayloadCodecs {
    private RoutePatrolPayloadCodecs() { }
    static PayloadCodec started() { return new PayloadCodec() {
        @Override public String type() { return "frontier.route_patrol_started"; }
        @Override public byte[] encode(FrontierPayload payload) { return FrontierWorldPayloadCodecs.encodeProduction(output -> writePatrol(output, ((RoutePatrolStarted) payload).patrol())); }
        @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new RoutePatrolStarted(readPatrol(input))); }
    }; }
    static PayloadCodec advanced() { return new PayloadCodec() {
        @Override public String type() { return "frontier.route_patrol_advanced"; }
        @Override public byte[] encode(FrontierPayload payload) { return FrontierWorldPayloadCodecs.encodeProduction(output -> {
            RoutePatrolAdvanced advanced = (RoutePatrolAdvanced) payload; subject(output, advanced.taskId()); output.writeByte(advanced.routeIndex());
        }); }
        @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new RoutePatrolAdvanced(subject(input), input.readUnsignedByte())); }
    }; }
    static PayloadCodec obstruction() { return new PayloadCodec() {
        @Override public String type() { return "frontier.route_patrol_obstruction_confirmed"; }
        @Override public byte[] encode(FrontierPayload payload) { return FrontierWorldPayloadCodecs.encodeProduction(output -> {
            RoutePatrolObstructionConfirmed confirmed = (RoutePatrolObstructionConfirmed) payload; subject(output, confirmed.taskId()); position(output, confirmed.position());
        }); }
        @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new RoutePatrolObstructionConfirmed(subject(input), position(input))); }
    }; }
    static PayloadCodec failed() { return new PayloadCodec() {
        @Override public String type() { return "frontier.route_patrol_failed"; }
        @Override public byte[] encode(FrontierPayload payload) { return FrontierWorldPayloadCodecs.encodeProduction(output -> subject(output, ((RoutePatrolFailed) payload).taskId())); }
        @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new RoutePatrolFailed(subject(input))); }
    }; }
    private static void writePatrol(DataOutputStream output, RoutePatrol patrol) throws IOException {
        subject(output, patrol.taskId()); subject(output, patrol.settlementId()); subject(output, patrol.guardId()); output.writeByte(patrol.route().size());
        for (BlockPosition point : patrol.route()) position(output, point);
        output.writeByte(patrol.routeIndex()); output.writeByte(patrol.status().ordinal()); output.writeBoolean(patrol.obstruction().isPresent());
        if (patrol.obstruction().isPresent()) position(output, patrol.obstruction().orElseThrow());
    }
    private static RoutePatrol readPatrol(DataInputStream input) throws IOException {
        SubjectId task = subject(input), settlement = subject(input), guard = subject(input); ArrayList<BlockPosition> route = new ArrayList<>();
        for (int index = 0, count = input.readUnsignedByte(); index < count; index++) route.add(position(input));
        int cursor = input.readUnsignedByte(), status = input.readUnsignedByte(); Optional<BlockPosition> obstruction = input.readBoolean() ? Optional.of(position(input)) : Optional.empty();
        if (status >= RoutePatrolStatus.values().length) throw new IllegalArgumentException("unknown route patrol status");
        return new RoutePatrol(task, settlement, guard, route, cursor, RoutePatrolStatus.values()[status], obstruction);
    }
    private static void subject(DataOutputStream output, SubjectId id) throws IOException { FrontierWorldPayloadCodecs.writeSubject(output, id); }
    private static SubjectId subject(DataInputStream input) throws IOException { return FrontierWorldPayloadCodecs.readSubject(input).value(); }
    private static void position(DataOutputStream output, BlockPosition position) throws IOException { output.writeInt(position.x()); output.writeInt(position.y()); output.writeInt(position.z()); }
    private static BlockPosition position(DataInputStream input) throws IOException { return new BlockPosition(input.readInt(), input.readInt(), input.readInt()); }
}
