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
import java.util.Optional;

/** WAL payload codecs for durable strategic decision and task admission. */
final class StrategicPlanPayloadCodecs {
    private StrategicPlanPayloadCodecs() { }
    static PayloadCodec selected() { return new PayloadCodec() {
        @Override public String type() { return "frontier.strategic_objective_selected"; }
        @Override public byte[] encode(FrontierPayload payload) { return FrontierWorldPayloadCodecs.encodeProduction(output -> writeObjective(output, ((StrategicObjectiveSelected) payload).objective())); }
        @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new StrategicObjectiveSelected(readObjective(input))); }
    }; }
    static PayloadCodec taskPlanned() { return new PayloadCodec() {
        @Override public String type() { return "frontier.strategic_task_planned"; }
        @Override public byte[] encode(FrontierPayload payload) { return FrontierWorldPayloadCodecs.encodeProduction(output -> writeTask(output, ((StrategicTaskPlanned) payload).task())); }
        @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new StrategicTaskPlanned(readTask(input))); }
    }; }
    static PayloadCodec transition() { return new PayloadCodec() {
        @Override public String type() { return "frontier.strategic_task_transition"; }
        @Override public byte[] encode(FrontierPayload payload) { return FrontierWorldPayloadCodecs.encodeProduction(output -> {
            StrategicTaskTransition transition = (StrategicTaskTransition) payload; subject(output, transition.taskId()); output.writeByte(transition.status().wireTag());
        }); }
        @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> {
            SubjectId task = subject(input); int status = input.readUnsignedByte();
            if (status >= StrategicTaskStatus.values().length) throw new IllegalArgumentException("unknown strategic task transition");
            return new StrategicTaskTransition(task, FrontierWireTags.require(StrategicTaskStatus.class, status));
        }); }
    }; }
    static PayloadCodec infectionObserved() { return new PayloadCodec() {
        @Override public String type() { return "frontier.settlement_infection_observed"; }
        @Override public byte[] encode(FrontierPayload payload) { return FrontierWorldPayloadCodecs.encodeProduction(output -> {
            SettlementInfectionObserved observed = (SettlementInfectionObserved) payload; subject(output, observed.settlementId());
            output.writeInt(observed.cell().x()); output.writeInt(observed.cell().z()); output.writeLong(observed.intensity().value().raw()); output.writeLong(observed.observedAt());
        }); }
        @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new SettlementInfectionObserved(subject(input),
                new InfectionCell(input.readInt(), input.readInt()), new io.farfrontier.palemirror.frontier.v3.api.FixedRatio(new io.farfrontier.palemirror.frontier.v3.api.FixedScalar(input.readLong())), input.readLong())); }
    }; }
    static PayloadCodec hiveOperationObserved() { return new PayloadCodec() {
        @Override public String type() { return "frontier.hive_operation_observed"; }
        @Override public byte[] encode(FrontierPayload payload) { return FrontierWorldPayloadCodecs.encodeProduction(output -> {
            HiveOperationKnowledge.Sighting sighting = ((HiveOperationObserved) payload).sighting(); subject(output, sighting.operationId()); subject(output, sighting.scoutId());
            output.writeInt(sighting.position().x()); output.writeInt(sighting.position().y()); output.writeInt(sighting.position().z()); output.writeLong(sighting.observedAt());
        }); }
        @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new HiveOperationObserved(new HiveOperationKnowledge.Sighting(
                subject(input), subject(input), new BlockPosition(input.readInt(), input.readInt(), input.readInt()), input.readLong()))); }
    }; }
    static PayloadCodec hiveTerritoryObserved() { return new PayloadCodec() {
        @Override public String type() { return "frontier.hive_territory_observed"; }
        @Override public byte[] encode(FrontierPayload payload) { return FrontierWorldPayloadCodecs.encodeProduction(output -> {
            HiveTerritoryKnowledge.Belief belief = ((HiveTerritoryObserved) payload).belief();
            output.writeInt(belief.cell().x()); output.writeInt(belief.cell().z()); output.writeLong(belief.intensity().value().raw());
            subject(output, belief.observerId()); output.writeInt(belief.sensorPosition().x()); output.writeInt(belief.sensorPosition().y()); output.writeInt(belief.sensorPosition().z()); output.writeLong(belief.observedAt());
        }); }
        @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new HiveTerritoryObserved(
                new HiveTerritoryKnowledge.Belief(new InfectionCell(input.readInt(), input.readInt()),
                        new io.farfrontier.palemirror.frontier.v3.api.FixedRatio(new io.farfrontier.palemirror.frontier.v3.api.FixedScalar(input.readLong())),
                        subject(input), new BlockPosition(input.readInt(), input.readInt(), input.readInt()), input.readLong()))); }
    }; }
    static PayloadCodec hiveSettlementObserved() { return new PayloadCodec() {
        @Override public String type() { return "frontier.hive_settlement_observed"; }
        @Override public byte[] encode(FrontierPayload payload) { return FrontierWorldPayloadCodecs.encodeProduction(output -> {
            HiveSettlementKnowledge.Sighting sighting = ((HiveSettlementObserved) payload).sighting();
            subject(output, sighting.settlementId()); subject(output, sighting.scoutId());
            output.writeInt(sighting.settlementAnchor().x()); output.writeInt(sighting.settlementAnchor().y()); output.writeInt(sighting.settlementAnchor().z());
            output.writeLong(sighting.observedAt());
        }); }
        @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new HiveSettlementObserved(
                new HiveSettlementKnowledge.Sighting(subject(input), subject(input), new BlockPosition(input.readInt(), input.readInt(), input.readInt()), input.readLong()))); }
    }; }
    static PayloadCodec hiveDoctrineSelected() { return new PayloadCodec() {
        @Override public String type() { return "frontier.hive_doctrine_selected"; }
        @Override public byte[] encode(FrontierPayload payload) { return FrontierWorldPayloadCodecs.encodeProduction(output -> {
            HiveDoctrineState state = ((HiveDoctrineSelected) payload).state(); output.writeByte(state.doctrine().wireTag()); output.writeLong(state.selectedAt());
        }); }
        @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> {
            int doctrine = input.readUnsignedByte(); if (doctrine >= HiveDoctrine.values().length) throw new IllegalArgumentException("unknown hive doctrine");
            return new HiveDoctrineSelected(new HiveDoctrineState(FrontierWireTags.require(HiveDoctrine.class, doctrine), input.readLong()));
        }); }
    }; }
    static PayloadCodec hotScoutOperationObserved() { return new PayloadCodec() {
        @Override public String type() { return "frontier.hot_scout_operation_observed"; }
        @Override public byte[] encode(FrontierPayload payload) { return FrontierWorldPayloadCodecs.encodeProduction(output -> {
            HotScoutOperationObserved observed = (HotScoutOperationObserved) payload;
            output.writeUTF(observed.sceneLeaseId().value()); subject(output, observed.operationId()); subject(output, observed.scoutId());
            output.writeInt(observed.seenCarrierPosition().x()); output.writeInt(observed.seenCarrierPosition().y()); output.writeInt(observed.seenCarrierPosition().z()); output.writeLong(observed.observedAt());
        }); }
        @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new HotScoutOperationObserved(
                new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId(input.readUTF()), subject(input), subject(input),
                new BlockPosition(input.readInt(), input.readInt(), input.readInt()), input.readLong())); }
    }; }
    static PayloadCodec scoutPatrolAdvanced() { return new PayloadCodec() {
        @Override public String type() { return "frontier.scout_patrol_advanced"; }
        @Override public byte[] encode(FrontierPayload payload) { return FrontierWorldPayloadCodecs.encodeProduction(output -> {
            ScoutPatrolAdvanced advanced = (ScoutPatrolAdvanced) payload; subject(output, advanced.scoutId()); output.writeLong(advanced.phase());
            output.writeInt(advanced.position().x()); output.writeInt(advanced.position().y()); output.writeInt(advanced.position().z());
            optionalPosition(output, advanced.priorPosition());
        }); }
        @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new ScoutPatrolAdvanced(
                subject(input), input.readLong(), new BlockPosition(input.readInt(), input.readInt(), input.readInt()),
                input.available() == 0 ? Optional.empty() : optionalPosition(input))); }
    }; }
    static PayloadCodec scoutPatrolLeaseRecovered() { return new PayloadCodec() {
        @Override public String type() { return "frontier.scout_patrol_lease_recovered"; }
        @Override public byte[] encode(FrontierPayload payload) { return FrontierWorldPayloadCodecs.encodeProduction(output -> {
            ScoutPatrolLeaseRecovered recovered = (ScoutPatrolLeaseRecovered) payload;
            subject(output, recovered.scoutId()); position(output, recovered.priorCanonicalPosition());
            position(output, recovered.observedLeasePosition()); position(output, recovered.nextGoalPosition());
        }); }
        @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes, input ->
                new ScoutPatrolLeaseRecovered(subject(input), position(input), position(input), position(input))); }
    }; }
    private static void position(DataOutputStream output, BlockPosition position) throws IOException {
        output.writeInt(position.x()); output.writeInt(position.y()); output.writeInt(position.z());
    }
    private static BlockPosition position(DataInputStream input) throws IOException {
        return new BlockPosition(input.readInt(), input.readInt(), input.readInt());
    }
    private static void writeObjective(DataOutputStream output, StrategicObjective value) throws IOException {
        subject(output, value.id()); subject(output, value.ownerId()); output.writeByte(value.kind().wireTag()); target(output, value.infectionTarget());
        optionalSubject(output, value.resourceSiteTarget());
        output.writeInt(value.decisionOrdinal()); output.writeByte(value.status().wireTag());
    }
    private static StrategicObjective readObjective(DataInputStream input) throws IOException {
        SubjectId id = subject(input), owner = subject(input); int kind = input.readUnsignedByte(); Optional<InfectionCell> target = target(input);
        Optional<SubjectId> resourceSiteTarget = optionalSubject(input); int ordinal = input.readInt(), status = input.readUnsignedByte();
        if (kind >= StrategicObjectiveKind.values().length || status >= StrategicObjectiveStatus.values().length) throw new IllegalArgumentException("unknown strategic objective value");
        return new StrategicObjective(id, owner, FrontierWireTags.require(StrategicObjectiveKind.class, kind), target, resourceSiteTarget, ordinal, FrontierWireTags.require(StrategicObjectiveStatus.class, status));
    }
    private static void writeTask(DataOutputStream output, StrategicTask value) throws IOException {
        subject(output, value.id()); subject(output, value.objectiveId()); subject(output, value.ownerId()); output.writeByte(value.kind().wireTag()); target(output, value.infectionTarget());
        optionalSubject(output, value.operationTarget()); optionalSubject(output, value.resourceSiteTarget());
        count(output, value.requirements().size()); for (StrategicTaskRequirement requirement : value.requirements()) output.writeByte(requirement.wireTag());
        count(output, value.dependencies().size()); for (SubjectId dependency : value.dependencies()) subject(output, dependency); output.writeByte(value.status().wireTag());
        optionalPosition(output, value.operationObservationPosition());
    }
    private static StrategicTask readTask(DataInputStream input) throws IOException {
        SubjectId id = subject(input), objective = subject(input), owner = subject(input); int kind = input.readUnsignedByte(); Optional<InfectionCell> target = target(input);
        Optional<SubjectId> operationTarget = optionalSubject(input); Optional<SubjectId> resourceSiteTarget = optionalSubject(input);
        List<StrategicTaskRequirement> requirements = new ArrayList<>();
        for (int index = 0, count = count(input); index < count; index++) {
            int value = input.readUnsignedByte();
            if (value >= StrategicTaskRequirement.values().length) throw new IllegalArgumentException("unknown strategic task requirement");
            requirements.add(FrontierWireTags.require(StrategicTaskRequirement.class, value));
        }
        List<SubjectId> dependencies = new ArrayList<>(); for (int index = 0, count = count(input); index < count; index++) dependencies.add(subject(input)); int status = input.readUnsignedByte();
        Optional<BlockPosition> operationObservationPosition = input.available() == 0 ? Optional.empty() : optionalPosition(input);
        if (kind >= StrategicTaskKind.values().length || status >= StrategicTaskStatus.values().length) throw new IllegalArgumentException("unknown strategic task value");
        return new StrategicTask(id, objective, owner, FrontierWireTags.require(StrategicTaskKind.class, kind), target, operationTarget, resourceSiteTarget, requirements, dependencies,
                FrontierWireTags.require(StrategicTaskStatus.class, status), operationObservationPosition);
    }
    private static void target(DataOutputStream output, Optional<InfectionCell> target) throws IOException {
        output.writeBoolean(target.isPresent()); if (target.isPresent()) { output.writeInt(target.orElseThrow().x()); output.writeInt(target.orElseThrow().z()); }
    }
    private static Optional<InfectionCell> target(DataInputStream input) throws IOException { return input.readBoolean() ? Optional.of(new InfectionCell(input.readInt(), input.readInt())) : Optional.empty(); }
    private static void optionalSubject(DataOutputStream output, Optional<SubjectId> value) throws IOException {
        output.writeBoolean(value.isPresent()); if (value.isPresent()) subject(output, value.orElseThrow());
    }
    private static Optional<SubjectId> optionalSubject(DataInputStream input) throws IOException {
        return input.readBoolean() ? Optional.of(subject(input)) : Optional.empty();
    }
    private static void optionalPosition(DataOutputStream output, Optional<BlockPosition> value) throws IOException {
        output.writeBoolean(value.isPresent()); if (value.isPresent()) { BlockPosition position = value.orElseThrow(); output.writeInt(position.x()); output.writeInt(position.y()); output.writeInt(position.z()); }
    }
    private static Optional<BlockPosition> optionalPosition(DataInputStream input) throws IOException {
        return input.readBoolean() ? Optional.of(new BlockPosition(input.readInt(), input.readInt(), input.readInt())) : Optional.empty();
    }
    private static void subject(DataOutputStream output, SubjectId value) throws IOException { FrontierWorldPayloadCodecs.writeSubject(output, value); }
    private static SubjectId subject(DataInputStream input) throws IOException { return FrontierWorldPayloadCodecs.readSubject(input).value(); }
    private static void count(DataOutputStream output, int value) throws IOException { if (value > 255) throw new IllegalArgumentException("too many strategic task entries"); output.writeByte(value); }
    private static int count(DataInputStream input) throws IOException { return input.readUnsignedByte(); }
}
