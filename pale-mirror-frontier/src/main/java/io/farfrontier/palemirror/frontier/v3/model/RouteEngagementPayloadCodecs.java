package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** WAL codecs for an exact route engagement and its per-bioform COLD travel. */
final class RouteEngagementPayloadCodecs {
    private RouteEngagementPayloadCodecs() { }
    static PayloadCodec started() { return new PayloadCodec() {
        @Override public String type() { return "frontier.route_engagement_started"; }
        @Override public byte[] encode(FrontierPayload payload) { return FrontierWorldPayloadCodecs.encodeProduction(output -> write(output, ((RouteEngagementStarted) payload).engagement())); }
        @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new RouteEngagementStarted(read(input))); }
    }; }
    static PayloadCodec advanced() { return new PayloadCodec() {
        @Override public String type() { return "frontier.route_engagement_attacker_advanced"; }
        @Override public byte[] encode(FrontierPayload payload) { return FrontierWorldPayloadCodecs.encodeProduction(output -> {
            RouteEngagementAttackerAdvanced advanced = (RouteEngagementAttackerAdvanced) payload;
            subject(output, advanced.engagementId()); subject(output, advanced.attackerId()); output.writeByte(advanced.routeIndex());
        }); }
        @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes, input ->
                new RouteEngagementAttackerAdvanced(subject(input), subject(input), input.readUnsignedByte())); }
    }; }
    static PayloadCodec transition() { return new PayloadCodec() {
        @Override public String type() { return "frontier.route_engagement_transition"; }
        @Override public byte[] encode(FrontierPayload payload) { return FrontierWorldPayloadCodecs.encodeProduction(output -> {
            RouteEngagementTransition transition = (RouteEngagementTransition) payload;
            subject(output, transition.engagementId()); output.writeByte(transition.status().ordinal());
        }); }
        @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> {
            SubjectId engagement = subject(input); int status = input.readUnsignedByte();
            if (status >= RouteEngagementStatus.values().length) throw new IllegalArgumentException("unknown route engagement status");
            return new RouteEngagementTransition(engagement, RouteEngagementStatus.values()[status]);
        }); }
    }; }
    private static void write(DataOutputStream output, RouteEngagement engagement) throws IOException {
        subject(output, engagement.id()); subject(output, engagement.taskId()); subject(output, engagement.operationId()); subject(output, engagement.hiveId());
        output.writeByte(engagement.attackers().size());
        for (EngagementAttacker attacker : engagement.attackers()) {
            subject(output, attacker.actorId()); output.writeByte(attacker.route().size());
            for (BlockPosition position : attacker.route()) position(output, position);
            output.writeByte(attacker.routeIndex());
        }
        position(output, engagement.intercept()); output.writeByte(engagement.status().ordinal());
    }
    private static RouteEngagement read(DataInputStream input) throws IOException {
        SubjectId id = subject(input), task = subject(input), operation = subject(input), hive = subject(input);
        List<EngagementAttacker> attackers = new ArrayList<>();
        for (int index = 0, count = input.readUnsignedByte(); index < count; index++) {
            SubjectId actor = subject(input); List<BlockPosition> route = new ArrayList<>();
            for (int point = 0, size = input.readUnsignedByte(); point < size; point++) route.add(position(input));
            attackers.add(new EngagementAttacker(actor, route, input.readUnsignedByte()));
        }
        BlockPosition intercept = position(input); int status = input.readUnsignedByte();
        if (status >= RouteEngagementStatus.values().length) throw new IllegalArgumentException("unknown route engagement status");
        return new RouteEngagement(id, task, operation, hive, attackers, intercept, RouteEngagementStatus.values()[status]);
    }
    private static void subject(DataOutputStream output, SubjectId value) throws IOException { FrontierWorldPayloadCodecs.writeSubject(output, value); }
    private static SubjectId subject(DataInputStream input) throws IOException { return FrontierWorldPayloadCodecs.readSubject(input).value(); }
    private static void position(DataOutputStream output, BlockPosition value) throws IOException { output.writeInt(value.x()); output.writeInt(value.y()); output.writeInt(value.z()); }
    private static BlockPosition position(DataInputStream input) throws IOException { return new BlockPosition(input.readInt(), input.readInt(), input.readInt()); }
}
