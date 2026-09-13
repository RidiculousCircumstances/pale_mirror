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
import java.util.Optional;

/** WAL codecs for first-class exact settlement assaults. */
final class SettlementAssaultPayloadCodecs {
    private SettlementAssaultPayloadCodecs() { }

    static PayloadCodecs codecs() { return new PayloadCodecs(List.of(started(), advanced(), formationObserved(), transition(), strike(), resolved())); }

    static PayloadCodec started() { return new PayloadCodec() {
        @Override public String type() { return "frontier.settlement_assault_started"; }
        @Override public byte[] encode(FrontierPayload payload) {
            return FrontierWorldPayloadCodecs.encodeProduction(output -> write(output, ((SettlementAssaultStarted) payload).assault()));
        }
        @Override public FrontierPayload decode(byte[] bytes) {
            return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new SettlementAssaultStarted(read(input)));
        }
    }; }

    static PayloadCodec advanced() { return new PayloadCodec() {
        @Override public String type() { return "frontier.settlement_assault_attacker_advanced"; }
        @Override public byte[] encode(FrontierPayload payload) {
            return FrontierWorldPayloadCodecs.encodeProduction(output -> {
                SettlementAssaultAttackerAdvanced value = (SettlementAssaultAttackerAdvanced) payload;
                subject(output, value.assaultId()); subject(output, value.attackerId()); output.writeByte(value.routeIndex());
            });
        }
        @Override public FrontierPayload decode(byte[] bytes) {
            return FrontierWorldPayloadCodecs.decodeProduction(bytes,
                    input -> new SettlementAssaultAttackerAdvanced(subject(input), subject(input), input.readUnsignedByte()));
        }
    }; }

    static PayloadCodec transition() { return new PayloadCodec() {
        @Override public String type() { return "frontier.settlement_assault_transition"; }
        @Override public byte[] encode(FrontierPayload payload) {
            return FrontierWorldPayloadCodecs.encodeProduction(output -> {
                SettlementAssaultTransition value = (SettlementAssaultTransition) payload;
                subject(output, value.assaultId()); output.writeByte(value.status().wireTag());
            });
        }
        @Override public FrontierPayload decode(byte[] bytes) {
            return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> {
                SubjectId assault = subject(input); int status = input.readUnsignedByte();
                return new SettlementAssaultTransition(assault, FrontierWireTags.require(SettlementAssaultStatus.class, status));
            });
        }
    }; }

    static PayloadCodec formationObserved() { return new PayloadCodec() {
        @Override public String type() { return "frontier.settlement_assault_formation_observed"; }
        @Override public byte[] encode(FrontierPayload payload) {
            return FrontierWorldPayloadCodecs.encodeProduction(output -> {
                SettlementAssaultFormationObserved value = (SettlementAssaultFormationObserved) payload;
                subject(output, value.assaultId()); FrontierWorldPayloadCodecs.writeString(output, value.leaseId().value());
                output.writeByte(value.bodies().size());
                for (var entry : value.bodies().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).toList()) {
                    subject(output, entry.getKey()); position(output, entry.getValue().supportingSurface().support());
                }
            });
        }
        @Override public FrontierPayload decode(byte[] bytes) {
            return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> {
                SubjectId assault = subject(input); io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId lease = new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId(FrontierWorldPayloadCodecs.readString(input));
                java.util.Map<SubjectId, BodyPosition> bodies = new java.util.LinkedHashMap<>();
                for (int index = 0, count = input.readUnsignedByte(); index < count; index++) bodies.put(subject(input), BodyPosition.above(new SurfaceAnchor(position(input))));
                return new SettlementAssaultFormationObserved(assault, lease, bodies);
            });
        }
    }; }

    static PayloadCodec strike() { return new PayloadCodec() {
        @Override public String type() { return "frontier.settlement_assault_strike"; }
        @Override public byte[] encode(FrontierPayload payload) {
            return FrontierWorldPayloadCodecs.encodeProduction(output -> {
                SettlementAssaultStrike value = (SettlementAssaultStrike) payload;
                subject(output, value.assaultId()); subject(output, value.attackerId()); subject(output, value.targetId());
                output.writeInt(value.epoch()); output.writeLong(value.damage().raw());
            });
        }
        @Override public FrontierPayload decode(byte[] bytes) {
            return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new SettlementAssaultStrike(subject(input), subject(input), subject(input),
                    input.readInt(), new io.farfrontier.palemirror.frontier.v3.api.FixedScalar(input.readLong())));
        }
    }; }

    static PayloadCodec resolved() { return new PayloadCodec() {
        @Override public String type() { return "frontier.settlement_assault_resolved"; }
        @Override public byte[] encode(FrontierPayload payload) {
            return FrontierWorldPayloadCodecs.encodeProduction(output -> {
                SettlementAssaultResolved value = (SettlementAssaultResolved) payload;
                subject(output, value.assaultId()); output.writeByte(value.outcome().wireTag());
            });
        }
        @Override public FrontierPayload decode(byte[] bytes) {
            return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> {
                SubjectId assault = subject(input); int outcome = input.readUnsignedByte();
                return new SettlementAssaultResolved(assault, FrontierWireTags.require(SettlementAssaultOutcome.class, outcome));
            });
        }
    }; }

    static void write(DataOutputStream output, SettlementAssault assault) throws IOException {
        subject(output, assault.id()); subject(output, assault.taskId()); subject(output, assault.hiveId()); subject(output, assault.overseerId());
        subject(output, assault.sighting().settlementId()); subject(output, assault.sighting().scoutId());
        position(output, assault.sighting().settlementAnchor()); output.writeLong(assault.sighting().observedAt());
        output.writeByte(assault.attackers().size());
        for (SettlementAssaultAttacker attacker : assault.attackers()) {
            subject(output, attacker.actorId()); output.writeByte(attacker.route().size());
            for (BlockPosition position : attacker.route()) position(output, position);
            output.writeByte(attacker.routeIndex());
        }
        writeMarch(output, assault.march());
        output.writeByte(assault.defenderIds().size()); for (SubjectId defender : assault.defenderIds()) subject(output, defender);
        TacticalPlanStateCodec.write(output, assault.tacticalPlan());
        output.writeByte(assault.status().wireTag()); output.writeInt(assault.nextStrikeEpoch());
        output.writeBoolean(assault.outcome().isPresent()); if (assault.outcome().isPresent()) output.writeByte(assault.outcome().orElseThrow().wireTag());
    }

    static SettlementAssault read(DataInputStream input) throws IOException {
        SubjectId id = subject(input), task = subject(input), hive = subject(input), overseer = subject(input);
        HiveSettlementKnowledge.Sighting sighting = new HiveSettlementKnowledge.Sighting(subject(input), subject(input), position(input), input.readLong());
        List<SettlementAssaultAttacker> attackers = new ArrayList<>();
        for (int index = 0, count = input.readUnsignedByte(); index < count; index++) {
            SubjectId actor = subject(input); List<BlockPosition> route = new ArrayList<>();
            for (int point = 0, size = input.readUnsignedByte(); point < size; point++) route.add(position(input));
            attackers.add(new SettlementAssaultAttacker(actor, route, input.readUnsignedByte()));
        }
        ExpeditionMarch march = readMarch(input);
        List<SubjectId> defenders = new ArrayList<>();
        for (int index = 0, count = input.readUnsignedByte(); index < count; index++) defenders.add(subject(input));
        TacticalPlan tacticalPlan = TacticalPlanStateCodec.read(input);
        int status = input.readUnsignedByte(), epoch = input.readInt();
        Optional<SettlementAssaultOutcome> outcome = input.readBoolean() ? Optional.of(readOutcome(input)) : Optional.empty();
        return new SettlementAssault(id, task, hive, sighting, overseer, attackers, march,
                SettlementDefenderUnit.forAssault(id, sighting.settlementId(), defenders), tacticalPlan,
                FrontierWireTags.require(SettlementAssaultStatus.class, status), epoch, outcome);
    }

    private static SettlementAssaultOutcome readOutcome(DataInputStream input) throws IOException {
        return FrontierWireTags.require(SettlementAssaultOutcome.class, input.readUnsignedByte());
    }

    private static void writeMarch(DataOutputStream output, ExpeditionMarch march) throws IOException {
        subject(output, march.overseerId()); output.writeShort(march.cursor()); output.writeByte(march.memberTopologies().size());
        for (var entry : march.memberTopologies().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).toList()) {
            subject(output, entry.getKey()); TraversalTopologyStateCodec.write(output, entry.getValue());
        }
    }
    private static ExpeditionMarch readMarch(DataInputStream input) throws IOException {
        SubjectId overseer = subject(input); int cursor = input.readUnsignedShort(); java.util.Map<SubjectId, TraversalTopology> paths = new java.util.LinkedHashMap<>();
        for (int index = 0, count = input.readUnsignedByte(); index < count; index++) paths.put(subject(input), TraversalTopologyStateCodec.read(input));
        return new ExpeditionMarch(overseer, cursor, paths);
    }

    private static void subject(DataOutputStream output, SubjectId value) throws IOException { FrontierWorldPayloadCodecs.writeSubject(output, value); }
    private static SubjectId subject(DataInputStream input) throws IOException { return FrontierWorldPayloadCodecs.readSubject(input).value(); }
    private static void position(DataOutputStream output, BlockPosition value) throws IOException { output.writeInt(value.x()); output.writeInt(value.y()); output.writeInt(value.z()); }
    private static BlockPosition position(DataInputStream input) throws IOException { return new BlockPosition(input.readInt(), input.readInt(), input.readInt()); }
}
