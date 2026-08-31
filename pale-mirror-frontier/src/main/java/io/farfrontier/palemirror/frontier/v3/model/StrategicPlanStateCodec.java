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
            writeTarget(output, objective.infectionTarget()); writeOptionalSubject(output, objective.resourceSiteTarget());
            output.writeInt(objective.decisionOrdinal()); output.writeByte(objective.status().ordinal());
        }
        writeCount(output, plans.tasks().size());
        for (StrategicTask task : plans.tasks().values().stream().sorted(Comparator.comparing(StrategicTask::id)).toList()) {
            writeSubject(output, task.id()); writeSubject(output, task.objectiveId()); writeSubject(output, task.ownerId()); output.writeByte(task.kind().ordinal());
            writeTarget(output, task.infectionTarget()); writeOptionalSubject(output, task.operationTarget()); writeOptionalSubject(output, task.resourceSiteTarget());
            writeCount(output, task.requirements().size());
            for (StrategicTaskRequirement requirement : task.requirements()) output.writeByte(requirement.ordinal());
            writeCount(output, task.dependencies().size()); for (SubjectId dependency : task.dependencies()) writeSubject(output, dependency);
            output.writeByte(task.status().ordinal()); writeOptionalPosition(output, task.operationObservationPosition());
        }
        writeCount(output, plans.routePatrols().size());
        for (RoutePatrol patrol : plans.routePatrols().values().stream().sorted(Comparator.comparing(RoutePatrol::taskId)).toList()) {
            writeSubject(output, patrol.taskId()); writeSubject(output, patrol.settlementId()); writeSubject(output, patrol.guardId());
            writeCount(output, patrol.route().size()); for (BlockPosition position : patrol.route()) writePosition(output, position);
            output.writeByte(patrol.routeIndex()); output.writeByte(patrol.status().ordinal()); output.writeBoolean(patrol.obstruction().isPresent());
            if (patrol.obstruction().isPresent()) writePosition(output, patrol.obstruction().orElseThrow());
        }
        writeCount(output, plans.routeEngagements().size());
        for (RouteEngagement engagement : plans.routeEngagements().values().stream().sorted(Comparator.comparing(RouteEngagement::id)).toList()) {
            writeSubject(output, engagement.id()); writeSubject(output, engagement.taskId()); writeSubject(output, engagement.operationId()); writeSubject(output, engagement.hiveId());
            writeCount(output, engagement.attackers().size());
            for (EngagementAttacker attacker : engagement.attackers()) {
                writeSubject(output, attacker.actorId()); writeCount(output, attacker.route().size());
                for (BlockPosition position : attacker.route()) writePosition(output, position);
                output.writeByte(attacker.routeIndex());
            }
            writePosition(output, engagement.intercept()); output.writeByte(engagement.status().ordinal()); output.writeInt(engagement.nextStrikeEpoch());
            output.writeBoolean(engagement.outcome().isPresent()); if (engagement.outcome().isPresent()) output.writeByte(engagement.outcome().orElseThrow().ordinal());
        }
        writeCount(output, plans.infectionKnowledge().entries().size());
        for (Map.Entry<SubjectId, Map<InfectionCell, SettlementInfectionKnowledge.KnownInfection>> settlement : plans.infectionKnowledge().entries().entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).toList()) {
            writeSubject(output, settlement.getKey()); writeCount(output, settlement.getValue().size());
            for (SettlementInfectionKnowledge.KnownInfection known : settlement.getValue().values().stream().sorted(Comparator.comparingInt((SettlementInfectionKnowledge.KnownInfection value) -> value.cell().x())
                    .thenComparingInt(value -> value.cell().z())).toList()) {
                writeTarget(output, Optional.of(known.cell())); output.writeLong(known.intensity().value().raw()); output.writeLong(known.observedAt());
            }
        }
        writeCount(output, plans.hiveOperationKnowledge().entries().size());
        for (HiveOperationKnowledge.Sighting sighting : plans.hiveOperationKnowledge().entries().values().stream().sorted(Comparator.comparing(HiveOperationKnowledge.Sighting::operationId)).toList()) {
            writeSubject(output, sighting.operationId()); writeSubject(output, sighting.scoutId()); writePosition(output, sighting.position()); output.writeLong(sighting.observedAt());
        }
    }

    static StrategicPlanState read(DataInputStream input) throws IOException { return read(input, false, true, true, true); }

    /** Version 66 and earlier described one-to-one bread conversion as requiring a spare slot. */
    static StrategicPlanState read(DataInputStream input, boolean migrateLegacyProductionSlotRequirement) throws IOException {
        return read(input, migrateLegacyProductionSlotRequirement, true, true, true);
    }

    static StrategicPlanState read(DataInputStream input, boolean migrateLegacyProductionSlotRequirement, boolean hasInfectionKnowledge,
                                   boolean hasHiveOperationKnowledge, boolean hasOperationObservationPosition) throws IOException {
        Map<SubjectId, StrategicObjective> objectives = new LinkedHashMap<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            SubjectId id = readSubject(input), owner = readSubject(input); int kind = input.readUnsignedByte(); Optional<InfectionCell> target = readTarget(input);
            Optional<SubjectId> resourceSiteTarget = readOptionalSubject(input);
            int ordinal = input.readInt(), status = input.readUnsignedByte();
            if (kind >= StrategicObjectiveKind.values().length || status >= StrategicObjectiveStatus.values().length
                    || objectives.put(id, new StrategicObjective(id, owner, StrategicObjectiveKind.values()[kind], target, resourceSiteTarget, ordinal, StrategicObjectiveStatus.values()[status])) != null) {
                throw new IllegalArgumentException("invalid or duplicate strategic objective");
            }
        }
        Map<SubjectId, StrategicTask> tasks = new LinkedHashMap<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            SubjectId id = readSubject(input), objective = readSubject(input), owner = readSubject(input); int kind = input.readUnsignedByte(); Optional<InfectionCell> target = readTarget(input);
            Optional<SubjectId> operationTarget = readOptionalSubject(input); Optional<SubjectId> resourceSiteTarget = readOptionalSubject(input);
            List<StrategicTaskRequirement> requirements = readRequirements(input); List<SubjectId> dependencies = readDependencies(input); int status = input.readUnsignedByte();
            Optional<BlockPosition> operationObservationPosition = hasOperationObservationPosition ? readOptionalPosition(input) : Optional.empty();
            if (migrateLegacyProductionSlotRequirement && kind < StrategicTaskKind.values().length
                    && StrategicTaskKind.values()[kind] == StrategicTaskKind.PRODUCE_BREAD) {
                List<StrategicTaskRequirement> legacy = List.of(StrategicTaskRequirement.ACTIVE_WORKSHOP,
                        StrategicTaskRequirement.EXACT_WHEAT_INPUT, StrategicTaskRequirement.FREE_DEPOT_SLOT);
                if (requirements.equals(legacy)) requirements = List.of(StrategicTaskRequirement.ACTIVE_WORKSHOP, StrategicTaskRequirement.EXACT_WHEAT_INPUT);
            }
            if (kind >= StrategicTaskKind.values().length || status >= StrategicTaskStatus.values().length
                    || tasks.put(id, new StrategicTask(id, objective, owner, StrategicTaskKind.values()[kind], target, operationTarget, resourceSiteTarget,
                    requirements, dependencies, StrategicTaskStatus.values()[status], operationObservationPosition)) != null) {
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
        Map<SubjectId, RouteEngagement> engagements = new LinkedHashMap<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            SubjectId id = readSubject(input), task = readSubject(input), operation = readSubject(input), hive = readSubject(input);
            List<EngagementAttacker> attackers = new ArrayList<>();
            for (int attacker = 0, attackerCount = readCount(input); attacker < attackerCount; attacker++) {
                SubjectId attackerId = readSubject(input); List<BlockPosition> route = new ArrayList<>();
                for (int point = 0, routeSize = readCount(input); point < routeSize; point++) route.add(readPosition(input));
                attackers.add(new EngagementAttacker(attackerId, route, input.readUnsignedByte()));
            }
            BlockPosition intercept = readPosition(input); int status = input.readUnsignedByte(), epoch = input.readInt();
            Optional<RouteEngagementOutcome> outcome = input.readBoolean() ? Optional.of(readOutcome(input)) : Optional.empty();
            if (status >= RouteEngagementStatus.values().length || engagements.put(id, new RouteEngagement(id, task, operation, hive, attackers, intercept, RouteEngagementStatus.values()[status], epoch, outcome)) != null) {
                throw new IllegalArgumentException("invalid or duplicate route engagement");
            }
        }
        Map<SubjectId, Map<InfectionCell, SettlementInfectionKnowledge.KnownInfection>> knowledge = new LinkedHashMap<>();
        if (hasInfectionKnowledge) for (int index = 0, count = readCount(input); index < count; index++) {
            SubjectId settlement = readSubject(input); Map<InfectionCell, SettlementInfectionKnowledge.KnownInfection> cells = new LinkedHashMap<>();
            for (int cellIndex = 0, cellCount = readCount(input); cellIndex < cellCount; cellIndex++) {
                InfectionCell cell = readTarget(input).orElseThrow(() -> new IllegalArgumentException("known infection must retain a cell"));
                SettlementInfectionKnowledge.KnownInfection known = new SettlementInfectionKnowledge.KnownInfection(cell,
                        new io.farfrontier.palemirror.frontier.v3.api.FixedRatio(new io.farfrontier.palemirror.frontier.v3.api.FixedScalar(input.readLong())), input.readLong());
                if (cells.put(cell, known) != null) throw new IllegalArgumentException("duplicate known infection cell");
            }
            if (knowledge.put(settlement, cells) != null) throw new IllegalArgumentException("duplicate settlement infection knowledge");
        }
        Map<SubjectId, HiveOperationKnowledge.Sighting> hiveKnowledge = new LinkedHashMap<>();
        if (hasHiveOperationKnowledge) for (int index = 0, count = readCount(input); index < count; index++) {
            SubjectId operation = readSubject(input), scout = readSubject(input); HiveOperationKnowledge.Sighting sighting = new HiveOperationKnowledge.Sighting(operation, scout, readPosition(input), input.readLong());
            if (hiveKnowledge.put(operation, sighting) != null) throw new IllegalArgumentException("duplicate hive operation sighting");
        }
        return new StrategicPlanState(objectives, tasks, patrols, engagements, new SettlementInfectionKnowledge(knowledge), new HiveOperationKnowledge(hiveKnowledge));
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
    private static void writeOptionalSubject(DataOutputStream output, Optional<SubjectId> value) throws IOException {
        output.writeBoolean(value.isPresent()); if (value.isPresent()) writeSubject(output, value.orElseThrow());
    }
    private static Optional<SubjectId> readOptionalSubject(DataInputStream input) throws IOException {
        return input.readBoolean() ? Optional.of(readSubject(input)) : Optional.empty();
    }
    private static void writeOptionalPosition(DataOutputStream output, Optional<BlockPosition> value) throws IOException {
        output.writeBoolean(value.isPresent()); if (value.isPresent()) writePosition(output, value.orElseThrow());
    }
    private static Optional<BlockPosition> readOptionalPosition(DataInputStream input) throws IOException {
        return input.readBoolean() ? Optional.of(readPosition(input)) : Optional.empty();
    }
    private static RouteEngagementOutcome readOutcome(DataInputStream input) throws IOException {
        int value = input.readUnsignedByte(); if (value >= RouteEngagementOutcome.values().length) throw new IllegalArgumentException("unknown route engagement outcome");
        return RouteEngagementOutcome.values()[value];
    }
    private static void writePosition(DataOutputStream output, BlockPosition position) throws IOException { output.writeInt(position.x()); output.writeInt(position.y()); output.writeInt(position.z()); }
    private static BlockPosition readPosition(DataInputStream input) throws IOException { return new BlockPosition(input.readInt(), input.readInt(), input.readInt()); }
    private static void writeSubject(DataOutputStream output, SubjectId id) throws IOException { FrontierWorldStateCodec.writeString(output, id.value()); }
    private static SubjectId readSubject(DataInputStream input) throws IOException { return new SubjectId(FrontierWorldStateCodec.readString(input)); }
    private static void writeCount(DataOutputStream output, int count) throws IOException { if (count > 512) throw new IllegalArgumentException("too many strategic plan entries"); output.writeShort(count); }
    private static int readCount(DataInputStream input) throws IOException { int count = input.readUnsignedShort(); if (count > 512) throw new IllegalArgumentException("too many strategic plan entries"); return count; }
}
