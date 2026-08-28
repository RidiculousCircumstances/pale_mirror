package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Snapshot encoding for the bounded strategic objective/task owner. */
final class StrategicPlanStateCodec {
    private StrategicPlanStateCodec() { }

    static void write(DataOutputStream output, StrategicPlanState plans) throws IOException {
        writeCount(output, plans.objectives().size());
        for (StrategicObjective objective : plans.objectives().values().stream().sorted(Comparator.comparing(StrategicObjective::id)).toList()) {
            writeSubject(output, objective.id()); writeSubject(output, objective.ownerId()); output.writeByte(objective.kind().ordinal());
            writeTarget(output, objective.infectionTarget()); output.writeInt(objective.decisionOrdinal()); output.writeByte(objective.status().ordinal());
        }
        writeCount(output, plans.tasks().size());
        for (StrategicTask task : plans.tasks().values().stream().sorted(Comparator.comparing(StrategicTask::id)).toList()) {
            writeSubject(output, task.id()); writeSubject(output, task.objectiveId()); writeSubject(output, task.ownerId()); output.writeByte(task.kind().ordinal());
            writeTarget(output, task.infectionTarget()); writeCount(output, task.requirements().size());
            for (StrategicTaskRequirement requirement : task.requirements()) output.writeByte(requirement.ordinal());
            writeCount(output, task.dependencies().size()); for (SubjectId dependency : task.dependencies()) writeSubject(output, dependency);
            output.writeByte(task.status().ordinal());
        }
        writeCount(output, plans.routePatrols().size());
        for (RoutePatrol patrol : plans.routePatrols().values().stream().sorted(Comparator.comparing(RoutePatrol::taskId)).toList()) {
            writeSubject(output, patrol.taskId()); writeSubject(output, patrol.settlementId()); writeSubject(output, patrol.guardId());
            writeCount(output, patrol.route().size()); for (BlockPosition position : patrol.route()) writePosition(output, position);
            output.writeByte(patrol.routeIndex()); output.writeByte(patrol.status().ordinal()); output.writeBoolean(patrol.obstruction().isPresent());
            if (patrol.obstruction().isPresent()) writePosition(output, patrol.obstruction().orElseThrow());
        }
    }

    static StrategicPlanState read(DataInputStream input) throws IOException {
        Map<SubjectId, StrategicObjective> objectives = new LinkedHashMap<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            SubjectId id = readSubject(input), owner = readSubject(input); int kind = input.readUnsignedByte(); Optional<InfectionCell> target = readTarget(input);
            int ordinal = input.readInt(), status = input.readUnsignedByte();
            if (kind >= StrategicObjectiveKind.values().length || status >= StrategicObjectiveStatus.values().length
                    || objectives.put(id, new StrategicObjective(id, owner, StrategicObjectiveKind.values()[kind], target, ordinal, StrategicObjectiveStatus.values()[status])) != null) {
                throw new IllegalArgumentException("invalid or duplicate strategic objective");
            }
        }
        Map<SubjectId, StrategicTask> tasks = new LinkedHashMap<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            SubjectId id = readSubject(input), objective = readSubject(input), owner = readSubject(input); int kind = input.readUnsignedByte(); Optional<InfectionCell> target = readTarget(input);
            List<StrategicTaskRequirement> requirements = readRequirements(input); List<SubjectId> dependencies = readDependencies(input); int status = input.readUnsignedByte();
            if (kind >= StrategicTaskKind.values().length || status >= StrategicTaskStatus.values().length
                    || tasks.put(id, new StrategicTask(id, objective, owner, StrategicTaskKind.values()[kind], target, requirements, dependencies, StrategicTaskStatus.values()[status])) != null) {
                throw new IllegalArgumentException("invalid or duplicate strategic task");
            }
        }
        Map<SubjectId, RoutePatrol> patrols = new LinkedHashMap<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            SubjectId task = readSubject(input), settlement = readSubject(input), guard = readSubject(input);
            List<BlockPosition> route = new ArrayList<>(); for (int point = 0, routeCount = readCount(input); point < routeCount; point++) route.add(readPosition(input));
            int cursor = input.readUnsignedByte(), status = input.readUnsignedByte(); Optional<BlockPosition> obstruction = input.readBoolean() ? Optional.of(readPosition(input)) : Optional.empty();
            if (status >= RoutePatrolStatus.values().length || patrols.put(task, new RoutePatrol(task, settlement, guard, route, cursor, RoutePatrolStatus.values()[status], obstruction)) != null) {
                throw new IllegalArgumentException("invalid or duplicate route patrol");
            }
        }
        return new StrategicPlanState(objectives, tasks, patrols);
    }

    private static List<StrategicTaskRequirement> readRequirements(DataInputStream input) throws IOException {
        List<StrategicTaskRequirement> values = new ArrayList<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            int value = input.readUnsignedByte(); if (value >= StrategicTaskRequirement.values().length) throw new IllegalArgumentException("unknown strategic task requirement");
            values.add(StrategicTaskRequirement.values()[value]);
        }
        return values;
    }
    private static List<SubjectId> readDependencies(DataInputStream input) throws IOException {
        List<SubjectId> values = new ArrayList<>(); for (int index = 0, count = readCount(input); index < count; index++) values.add(readSubject(input)); return values;
    }
    private static void writeTarget(DataOutputStream output, Optional<InfectionCell> target) throws IOException {
        output.writeBoolean(target.isPresent()); if (target.isPresent()) { output.writeInt(target.orElseThrow().x()); output.writeInt(target.orElseThrow().z()); }
    }
    private static Optional<InfectionCell> readTarget(DataInputStream input) throws IOException {
        return input.readBoolean() ? Optional.of(new InfectionCell(input.readInt(), input.readInt())) : Optional.empty();
    }
    private static void writePosition(DataOutputStream output, BlockPosition position) throws IOException { output.writeInt(position.x()); output.writeInt(position.y()); output.writeInt(position.z()); }
    private static BlockPosition readPosition(DataInputStream input) throws IOException { return new BlockPosition(input.readInt(), input.readInt(), input.readInt()); }
    private static void writeSubject(DataOutputStream output, SubjectId id) throws IOException { FrontierWorldStateCodec.writeString(output, id.value()); }
    private static SubjectId readSubject(DataInputStream input) throws IOException { return new SubjectId(FrontierWorldStateCodec.readString(input)); }
    private static void writeCount(DataOutputStream output, int count) throws IOException { if (count > 512) throw new IllegalArgumentException("too many strategic plan entries"); output.writeShort(count); }
    private static int readCount(DataInputStream input) throws IOException { int count = input.readUnsignedShort(); if (count > 512) throw new IllegalArgumentException("too many strategic plan entries"); return count; }
}
