package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.model.*;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodecs;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** WAL codecs for an exact route engagement and its per-bioform COLD travel. */
final class RouteEngagementPayloadCodecs {
    private RouteEngagementPayloadCodecs() { }
    static PayloadCodecs codecs() { return new PayloadCodecs(List.of(started(), advanced(), transition(), strike(), resolved())); }
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
            subject(output, transition.engagementId()); output.writeByte(transition.status().wireTag());
        }); }
        @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> {
            SubjectId engagement = subject(input); int status = input.readUnsignedByte();
            if (status >= RouteEngagementStatus.values().length) throw new IllegalArgumentException("unknown route engagement status");
            return new RouteEngagementTransition(engagement, FrontierWireTags.require(RouteEngagementStatus.class, status));
        }); }
    }; }
    static PayloadCodec strike() { return new PayloadCodec() {
        @Override public String type() { return "frontier.route_engagement_strike"; }
        @Override public byte[] encode(FrontierPayload payload) { return FrontierWorldPayloadCodecs.encodeProduction(output -> {
            RouteEngagementStrike strike = (RouteEngagementStrike) payload;
            subject(output, strike.engagementId()); subject(output, strike.attackerId()); subject(output, strike.targetId()); output.writeInt(strike.epoch()); output.writeLong(strike.damage().raw());
        }); }
        @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes, input ->
                new RouteEngagementStrike(subject(input), subject(input), subject(input), input.readInt(), new io.farfrontier.palemirror.frontier.v3.api.FixedScalar(input.readLong()))); }
    }; }
    static PayloadCodec resolved() { return new PayloadCodec() {
        @Override public String type() { return "frontier.route_engagement_resolved"; }
        @Override public byte[] encode(FrontierPayload payload) { return FrontierWorldPayloadCodecs.encodeProduction(output -> {
            RouteEngagementResolved resolved = (RouteEngagementResolved) payload; subject(output, resolved.engagementId()); output.writeByte(resolved.outcome().wireTag());
        }); }
        @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> {
            int outcome; SubjectId engagement = subject(input); outcome = input.readUnsignedByte();
            if (outcome >= RouteEngagementOutcome.values().length) throw new IllegalArgumentException("unknown route engagement outcome");
            return new RouteEngagementResolved(engagement, FrontierWireTags.require(RouteEngagementOutcome.class, outcome));
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
        position(output, engagement.intercept()); output.writeByte(engagement.status().wireTag()); output.writeInt(engagement.nextStrikeEpoch());
        output.writeBoolean(engagement.outcome().isPresent()); if (engagement.outcome().isPresent()) output.writeByte(engagement.outcome().orElseThrow().wireTag());
    }
    private static RouteEngagement read(DataInputStream input) throws IOException {
        SubjectId id = subject(input), task = subject(input), operation = subject(input), hive = subject(input);
        List<EngagementAttacker> attackers = new ArrayList<>();
        for (int index = 0, count = input.readUnsignedByte(); index < count; index++) {
            SubjectId actor = subject(input); List<BlockPosition> route = new ArrayList<>();
            for (int point = 0, size = input.readUnsignedByte(); point < size; point++) route.add(position(input));
            attackers.add(new EngagementAttacker(actor, route, input.readUnsignedByte()));
        }
        BlockPosition intercept = position(input); int status = input.readUnsignedByte(), epoch = input.readInt();
        java.util.Optional<RouteEngagementOutcome> outcome = input.readBoolean() ? java.util.Optional.of(readOutcome(input)) : java.util.Optional.empty();
        if (status >= RouteEngagementStatus.values().length) throw new IllegalArgumentException("unknown route engagement status");
        return new RouteEngagement(id, task, operation, hive, attackers, intercept, FrontierWireTags.require(RouteEngagementStatus.class, status), epoch, outcome);
    }
    private static RouteEngagementOutcome readOutcome(DataInputStream input) throws IOException {
        int value = input.readUnsignedByte(); if (value >= RouteEngagementOutcome.values().length) throw new IllegalArgumentException("unknown route engagement outcome");
        return FrontierWireTags.require(RouteEngagementOutcome.class, value);
    }
    private static void subject(DataOutputStream output, SubjectId value) throws IOException { FrontierWorldPayloadCodecs.writeSubject(output, value); }
    private static SubjectId subject(DataInputStream input) throws IOException { return FrontierWorldPayloadCodecs.readSubject(input).value(); }
    private static void position(DataOutputStream output, BlockPosition value) throws IOException { output.writeInt(value.x()); output.writeInt(value.y()); output.writeInt(value.z()); }
    private static BlockPosition position(DataInputStream input) throws IOException { return new BlockPosition(input.readInt(), input.readInt(), input.readInt()); }
}
