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

    static PayloadCodecs codecs() { return new PayloadCodecs(List.of(started(), advanced(), formationObserved(), marchIssueObserved(), transition(), strike(), resolved())); }

    static PayloadCodec started() { return new PayloadCodec() {
        @Override public String type() { return "frontier.settlement_assault_started"; }
        @Override public byte[] encode(FrontierPayload payload) {
            return FrontierWorldPayloadCodecs.encodeProduction(output -> { var value = (SettlementAssaultStarted) payload; write(output, value.assault()); ActorExecutionStateCodec.writeGroup(output, value.executions()); });
        }
        @Override public FrontierPayload decode(byte[] bytes) {
            return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new SettlementAssaultStarted(read(input), ActorExecutionStateCodec.readGroup(input)));
        }
    }; }

    static PayloadCodec advanced() { return new PayloadCodec() {
        @Override public String type() { return "frontier.settlement_assault_attacker_advanced"; }
        @Override public byte[] encode(FrontierPayload payload) {
            return FrontierWorldPayloadCodecs.encodeProduction(output -> {
                SettlementAssaultAttackerAdvanced value = (SettlementAssaultAttackerAdvanced) payload;
                subject(output, value.assaultId()); subject(output, value.attackerId());
                ExpeditionMarchCodec.writeStep(output, value.predecessor()); ActorExecutionStateCodec.writeGroup(output, value.executions());
            });
        }
        @Override public FrontierPayload decode(byte[] bytes) {
            return FrontierWorldPayloadCodecs.decodeProduction(bytes,
                    input -> new SettlementAssaultAttackerAdvanced(subject(input), subject(input), ExpeditionMarchCodec.readStep(input), ActorExecutionStateCodec.readGroup(input)));
        }
    }; }

    static PayloadCodec transition() { return new PayloadCodec() {
        @Override public String type() { return "frontier.settlement_assault_transition"; }
        @Override public byte[] encode(FrontierPayload payload) {
            return FrontierWorldPayloadCodecs.encodeProduction(output -> {
                SettlementAssaultTransition value = (SettlementAssaultTransition) payload;
                subject(output, value.assaultId()); output.writeByte(value.status().wireTag()); output.writeBoolean(value.diagnostic().isPresent()); if (value.diagnostic().isPresent())
                        FrontierWorldPayloadCodecs.writeDiagnosticTuple(output, value.diagnostic().orElseThrow());
                ActorExecutionStateCodec.writeGroup(output, value.executions());
            });
        }
        @Override public FrontierPayload decode(byte[] bytes) {
            return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> {
                SubjectId assault = subject(input); int status = input.readUnsignedByte();
                return new SettlementAssaultTransition(assault, FrontierWireTags.require(SettlementAssaultStatus.class, status), input.readBoolean() ?
                        java.util.Optional.of(FrontierWorldPayloadCodecs.readDiagnosticTuple(input)) : java.util.Optional.empty(), ActorExecutionStateCodec.readGroup(input));
            });
        }
    }; }

    static PayloadCodec formationObserved() { return new PayloadCodec() {
        @Override public String type() { return "frontier.settlement_assault_formation_observed"; }
        @Override public byte[] encode(FrontierPayload payload) {
            return FrontierWorldPayloadCodecs.encodeProduction(output -> {
                SettlementAssaultFormationObserved value = (SettlementAssaultFormationObserved) payload;
                subject(output, value.assaultId()); FrontierWorldPayloadCodecs.writeString(output, value.leaseId().value());
                output.writeLong(value.leaseRevision()); ExpeditionMarchCodec.writeStep(output, value.predecessor());
                ActorExecutionStateCodec.writeActuations(output, value.actuations());
                output.writeByte(value.bodies().size());
                for (var entry : value.bodies().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).toList()) {
                    subject(output, entry.getKey()); position(output, entry.getValue().supportingSurface().support());
                }
                ActorExecutionStateCodec.writeGroup(output, value.executions());
            });
        }
        @Override public FrontierPayload decode(byte[] bytes) {
            return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> {
                SubjectId assault = subject(input); io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId lease = new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId(FrontierWorldPayloadCodecs.readString(input));
                long revision = input.readLong(); var predecessor = ExpeditionMarchCodec.readStep(input);
                var actuations = ActorExecutionStateCodec.readActuations(input);
                int count = input.readUnsignedByte();
                if (count < 2 || count > ExpeditionMarch.MAX_MEMBERS) throw new IOException("invalid expedition body count");
                java.util.Map<SubjectId, BodyPosition> bodies = new java.util.LinkedHashMap<>();
                for (int index = 0; index < count; index++) {
                    if (bodies.put(subject(input), BodyPosition.above(new SurfaceAnchor(position(input)))) != null)
                        throw new IOException("duplicate expedition body");
                }
                return new SettlementAssaultFormationObserved(assault, lease, revision, predecessor, actuations, bodies, ActorExecutionStateCodec.readGroup(input));
            });
        }
    }; }

    static PayloadCodec marchIssueObserved() { return new PayloadCodec() {
        @Override public String type() { return "frontier.settlement_assault_march_issue_observed"; }
        @Override public byte[] encode(FrontierPayload payload) {
            return FrontierWorldPayloadCodecs.encodeProduction(output -> {
                SettlementAssaultMarchIssueObserved value = (SettlementAssaultMarchIssueObserved) payload;
                subject(output, value.assaultId()); FrontierWorldPayloadCodecs.writeString(output, value.leaseId().value());
                output.writeLong(value.leaseRevision()); ExpeditionMarchCodec.writeStep(output, value.predecessor());
                ActorExecutionStateCodec.writeActuations(output, value.actuations());
                output.writeByte(FrontierWireTags.tag(value.issue().kind())); subject(output, value.issue().memberId());
                FrontierWorldPayloadCodecs.writeString(output, value.issue().edgeId().value()); output.writeShort(value.issue().expectedCursor()); ActorExecutionStateCodec.writeGroup(output, value.executions());
            });
        }
        @Override public FrontierPayload decode(byte[] bytes) {
            return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> {
                SubjectId assault = subject(input); var lease = new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId(FrontierWorldPayloadCodecs.readString(input));
                long revision = input.readLong(); var predecessor = ExpeditionMarchCodec.readStep(input);
                var actuations = ActorExecutionStateCodec.readActuations(input);
                ExpeditionMarchIssue issue = new ExpeditionMarchIssue(FrontierWireTags.require(ExpeditionMarchIssueKind.class, input.readUnsignedByte()), subject(input),
                        new TraversalEdgeId(FrontierWorldPayloadCodecs.readString(input)), input.readUnsignedShort());
                return new SettlementAssaultMarchIssueObserved(assault, lease, revision, predecessor, actuations, issue, ActorExecutionStateCodec.readGroup(input));
            });
        }
    }; }

    static PayloadCodec strike() { return new PayloadCodec() {
        @Override public String type() { return "frontier.settlement_assault_strike"; }
        @Override public byte[] encode(FrontierPayload payload) {
            return FrontierWorldPayloadCodecs.encodeProduction(output -> {
                SettlementAssaultStrike value = (SettlementAssaultStrike) payload;
                subject(output, value.assaultId()); subject(output, value.attackerId()); subject(output, value.targetId());
                output.writeInt(value.epoch()); output.writeLong(value.damage().raw()); ActorExecutionStateCodec.writeGroup(output, value.executions());
            });
        }
        @Override public FrontierPayload decode(byte[] bytes) {
            return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new SettlementAssaultStrike(subject(input), subject(input), subject(input),
                    input.readInt(), new io.farfrontier.palemirror.frontier.v3.api.FixedScalar(input.readLong()), ActorExecutionStateCodec.readGroup(input)));
        }
    }; }

    static PayloadCodec resolved() { return new PayloadCodec() {
        @Override public String type() { return "frontier.settlement_assault_resolved"; }
        @Override public byte[] encode(FrontierPayload payload) {
            return FrontierWorldPayloadCodecs.encodeProduction(output -> {
                SettlementAssaultResolved value = (SettlementAssaultResolved) payload;
                subject(output, value.assaultId()); output.writeByte(value.outcome().wireTag());
                HiveReturnAdmissionCodec.write(output, value.returnAdmission()); ActorExecutionStateCodec.writeGroup(output, value.executions());
            });
        }
        @Override public FrontierPayload decode(byte[] bytes) {
            return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> {
                SubjectId assault = subject(input); int outcome = input.readUnsignedByte();
                return new SettlementAssaultResolved(assault, FrontierWireTags.require(SettlementAssaultOutcome.class, outcome), HiveReturnAdmissionCodec.read(input), ActorExecutionStateCodec.readGroup(input));
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
        ExpeditionMarchCodec.write(output, march);
    }
    private static ExpeditionMarch readMarch(DataInputStream input) throws IOException {
        return ExpeditionMarchCodec.read(input);
    }

    private static void subject(DataOutputStream output, SubjectId value) throws IOException { FrontierWorldPayloadCodecs.writeSubject(output, value); }
    private static SubjectId subject(DataInputStream input) throws IOException { return FrontierWorldPayloadCodecs.readSubject(input).value(); }
    private static void position(DataOutputStream output, BlockPosition value) throws IOException { output.writeInt(value.x()); output.writeInt(value.y()); output.writeInt(value.z()); }
    private static BlockPosition position(DataInputStream input) throws IOException { return new BlockPosition(input.readInt(), input.readInt(), input.readInt()); }
}
