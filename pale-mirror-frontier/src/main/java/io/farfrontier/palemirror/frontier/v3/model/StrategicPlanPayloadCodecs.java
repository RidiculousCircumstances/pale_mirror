package io.farfrontier.palemirror.frontier.v3.model;

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
            StrategicTaskTransition transition = (StrategicTaskTransition) payload; subject(output, transition.taskId()); output.writeByte(transition.status().ordinal());
        }); }
        @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> {
            SubjectId task = subject(input); int status = input.readUnsignedByte();
            if (status >= StrategicTaskStatus.values().length) throw new IllegalArgumentException("unknown strategic task transition");
            return new StrategicTaskTransition(task, StrategicTaskStatus.values()[status]);
        }); }
    }; }
    private static void writeObjective(DataOutputStream output, StrategicObjective value) throws IOException {
        subject(output, value.id()); subject(output, value.ownerId()); output.writeByte(value.kind().ordinal()); target(output, value.infectionTarget());
        optionalSubject(output, value.resourceSiteTarget());
        output.writeInt(value.decisionOrdinal()); output.writeByte(value.status().ordinal());
    }
    private static StrategicObjective readObjective(DataInputStream input) throws IOException {
        SubjectId id = subject(input), owner = subject(input); int kind = input.readUnsignedByte(); Optional<InfectionCell> target = target(input);
        Optional<SubjectId> resourceSiteTarget = optionalSubject(input); int ordinal = input.readInt(), status = input.readUnsignedByte();
        if (kind >= StrategicObjectiveKind.values().length || status >= StrategicObjectiveStatus.values().length) throw new IllegalArgumentException("unknown strategic objective value");
        return new StrategicObjective(id, owner, StrategicObjectiveKind.values()[kind], target, resourceSiteTarget, ordinal, StrategicObjectiveStatus.values()[status]);
    }
    private static void writeTask(DataOutputStream output, StrategicTask value) throws IOException {
        subject(output, value.id()); subject(output, value.objectiveId()); subject(output, value.ownerId()); output.writeByte(value.kind().ordinal()); target(output, value.infectionTarget());
        optionalSubject(output, value.operationTarget()); optionalSubject(output, value.resourceSiteTarget());
        count(output, value.requirements().size()); for (StrategicTaskRequirement requirement : value.requirements()) output.writeByte(requirement.ordinal());
        count(output, value.dependencies().size()); for (SubjectId dependency : value.dependencies()) subject(output, dependency); output.writeByte(value.status().ordinal());
    }
    private static StrategicTask readTask(DataInputStream input) throws IOException {
        SubjectId id = subject(input), objective = subject(input), owner = subject(input); int kind = input.readUnsignedByte(); Optional<InfectionCell> target = target(input);
        Optional<SubjectId> operationTarget = optionalSubject(input); Optional<SubjectId> resourceSiteTarget = optionalSubject(input);
        List<StrategicTaskRequirement> requirements = new ArrayList<>();
        for (int index = 0, count = count(input); index < count; index++) {
            int value = input.readUnsignedByte();
            if (value >= StrategicTaskRequirement.values().length) throw new IllegalArgumentException("unknown strategic task requirement");
            requirements.add(StrategicTaskRequirement.values()[value]);
        }
        List<SubjectId> dependencies = new ArrayList<>(); for (int index = 0, count = count(input); index < count; index++) dependencies.add(subject(input)); int status = input.readUnsignedByte();
        if (kind >= StrategicTaskKind.values().length || status >= StrategicTaskStatus.values().length) throw new IllegalArgumentException("unknown strategic task value");
        return new StrategicTask(id, objective, owner, StrategicTaskKind.values()[kind], target, operationTarget, resourceSiteTarget, requirements, dependencies, StrategicTaskStatus.values()[status]);
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
    private static void subject(DataOutputStream output, SubjectId value) throws IOException { FrontierWorldPayloadCodecs.writeSubject(output, value); }
    private static SubjectId subject(DataInputStream input) throws IOException { return FrontierWorldPayloadCodecs.readSubject(input).value(); }
    private static void count(DataOutputStream output, int value) throws IOException { if (value > 255) throw new IllegalArgumentException("too many strategic task entries"); output.writeByte(value); }
    private static int count(DataInputStream input) throws IOException { return input.readUnsignedByte(); }
}
