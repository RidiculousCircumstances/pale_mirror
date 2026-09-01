package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.model.*;

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
public final class StrategicPlanStateCodec {
    private StrategicPlanStateCodec() { }

    public static void write(DataOutputStream output, StrategicPlanState plans) throws IOException {
        writeCount(output, plans.objectives().size());
        for (StrategicObjective objective : plans.objectives().values().stream().sorted(Comparator.comparing(StrategicObjective::id)).toList()) {
            writeSubject(output, objective.id()); writeSubject(output, objective.ownerId()); output.writeByte(objective.kind().wireTag());
            writeTarget(output, objective.infectionTarget()); writeOptionalSubject(output, objective.resourceSiteTarget());
            output.writeInt(objective.decisionOrdinal()); output.writeByte(objective.status().wireTag());
        }
        writeCount(output, plans.tasks().size());
        for (StrategicTask task : plans.tasks().values().stream().sorted(Comparator.comparing(StrategicTask::id)).toList()) {
            writeSubject(output, task.id()); writeSubject(output, task.objectiveId()); writeSubject(output, task.ownerId()); output.writeByte(task.kind().wireTag());
            writeTarget(output, task.infectionTarget()); writeOptionalSubject(output, task.operationTarget()); writeOptionalSubject(output, task.resourceSiteTarget());
            writeCount(output, task.requirements().size());
            for (StrategicTaskRequirement requirement : task.requirements()) output.writeByte(requirement.wireTag());
            writeCount(output, task.dependencies().size()); for (SubjectId dependency : task.dependencies()) writeSubject(output, dependency);
            output.writeByte(task.status().wireTag()); writeOptionalPosition(output, task.operationObservationPosition());
        }
        writeCount(output, plans.routePatrols().size());
        for (RoutePatrol patrol : plans.routePatrols().values().stream().sorted(Comparator.comparing(RoutePatrol::taskId)).toList()) {
            writeSubject(output, patrol.taskId()); writeSubject(output, patrol.settlementId()); RouteUnitManifestCodec.write(output, patrol.unit());
            writeCount(output, patrol.route().size()); for (BlockPosition position : patrol.route()) writePosition(output, position);
            output.writeByte(patrol.routeIndex()); output.writeByte(patrol.status().wireTag()); output.writeBoolean(patrol.obstruction().isPresent());
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
            writePosition(output, engagement.intercept()); output.writeByte(engagement.status().wireTag()); output.writeInt(engagement.nextStrikeEpoch());
            output.writeBoolean(engagement.outcome().isPresent()); if (engagement.outcome().isPresent()) output.writeByte(engagement.outcome().orElseThrow().wireTag());
        }
        writeCount(output, plans.settlementAssaults().size());
        for (SettlementAssault assault : plans.settlementAssaults().values().stream().sorted(Comparator.comparing(SettlementAssault::id)).toList()) {
            writeSubject(output, assault.id()); writeSubject(output, assault.taskId()); writeSubject(output, assault.hiveId());
            writeSubject(output, assault.sighting().settlementId()); writeSubject(output, assault.sighting().scoutId());
            writePosition(output, assault.sighting().settlementAnchor()); output.writeLong(assault.sighting().observedAt());
            writeCount(output, assault.attackers().size());
            for (SettlementAssaultAttacker attacker : assault.attackers()) {
                writeSubject(output, attacker.actorId()); writeCount(output, attacker.route().size());
                for (BlockPosition position : attacker.route()) writePosition(output, position);
                output.writeByte(attacker.routeIndex());
            }
            writeCount(output, assault.defenderIds().size());
            for (SubjectId defender : assault.defenderIds()) writeSubject(output, defender);
            output.writeByte(assault.status().wireTag()); output.writeInt(assault.nextStrikeEpoch());
            output.writeBoolean(assault.outcome().isPresent()); if (assault.outcome().isPresent()) output.writeByte(assault.outcome().orElseThrow().wireTag());
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
        writeCount(output, plans.hiveTerritoryKnowledge().entries().size());
        for (HiveTerritoryKnowledge.Belief belief : plans.hiveTerritoryKnowledge().entries().values().stream()
                .sorted(Comparator.comparingInt((HiveTerritoryKnowledge.Belief value) -> value.cell().x()).thenComparingInt(value -> value.cell().z())).toList()) {
            writeTarget(output, Optional.of(belief.cell())); output.writeLong(belief.intensity().value().raw()); writeSubject(output, belief.observerId());
            writePosition(output, belief.sensorPosition()); output.writeLong(belief.observedAt());
        }
        writeCount(output, plans.hiveSettlementKnowledge().entries().size());
        for (HiveSettlementKnowledge.Sighting sighting : plans.hiveSettlementKnowledge().entries().values().stream()
                .sorted(Comparator.comparing(HiveSettlementKnowledge.Sighting::settlementId)).toList()) {
            writeSubject(output, sighting.settlementId()); writeSubject(output, sighting.scoutId()); writePosition(output, sighting.settlementAnchor());
            output.writeLong(sighting.observedAt());
        }
        output.writeByte(plans.hiveDoctrine().doctrine().wireTag()); output.writeLong(plans.hiveDoctrine().selectedAt());
    }

    public static StrategicPlanState read(DataInputStream input) throws IOException {
        return read(input, false, true, true, true, true, true, true, true, FrontierWorldStateCodec.VERSION);
    }

    /** Version 66 and earlier described one-to-one bread conversion as requiring a spare slot. */
    static StrategicPlanState read(DataInputStream input, boolean migrateLegacyProductionSlotRequirement) throws IOException {
        return read(input, migrateLegacyProductionSlotRequirement, true, true, true, true, true, true, true, FrontierWorldStateCodec.VERSION);
    }

    static StrategicPlanState read(DataInputStream input, boolean migrateLegacyProductionSlotRequirement, boolean hasInfectionKnowledge,
                                   boolean hasHiveOperationKnowledge, boolean hasOperationObservationPosition) throws IOException {
        return read(input, migrateLegacyProductionSlotRequirement, hasInfectionKnowledge, hasHiveOperationKnowledge, hasOperationObservationPosition, true, true, true, true,
                FrontierWorldStateCodec.VERSION);
    }

    static StrategicPlanState read(DataInputStream input, boolean migrateLegacyProductionSlotRequirement, boolean hasInfectionKnowledge,
                                   boolean hasHiveOperationKnowledge, boolean hasOperationObservationPosition, boolean hasHiveTerritoryKnowledge,
                                   boolean hasHiveDoctrine, boolean hasHiveSettlementKnowledge, boolean hasSettlementAssaults, int snapshotVersion) throws IOException {
        // Frontier v3 deliberately has no in-place world migration.  This nested codec is not
        // a second admission path around FrontierWorldStateCodec: every field below belongs to
        // the one current snapshot layout.
        if (snapshotVersion != FrontierWorldStateCodec.VERSION || migrateLegacyProductionSlotRequirement
                || !hasInfectionKnowledge || !hasHiveOperationKnowledge || !hasOperationObservationPosition
                || !hasHiveTerritoryKnowledge || !hasHiveDoctrine || !hasHiveSettlementKnowledge || !hasSettlementAssaults) {
            throw new IllegalArgumentException("strategic plan requires the fresh current-schema layout");
        }
        Map<SubjectId, StrategicObjective> objectives = new LinkedHashMap<>();
        List<RawObjective> encodedObjectives = new ArrayList<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            SubjectId id = readSubject(input), owner = readSubject(input); int kind = input.readUnsignedByte(); Optional<InfectionCell> target = readTarget(input);
            Optional<SubjectId> resourceSiteTarget = readOptionalSubject(input);
            int ordinal = input.readInt(), status = input.readUnsignedByte();
            encodedObjectives.add(new RawObjective(id, owner, kind, target, resourceSiteTarget, ordinal, status));
        }
        boolean preAssaultOrdinals = usesPreAssaultOrdinals(snapshotVersion, encodedObjectives);
        for (RawObjective encoded : encodedObjectives) {
            StrategicObjectiveKind objectiveKind = objectiveKind(encoded.kind(), preAssaultOrdinals);
            StrategicObjectiveStatus objectiveStatus = FrontierWireTags.require(StrategicObjectiveStatus.class, encoded.status());
            StrategicObjective objective = new StrategicObjective(encoded.id(), encoded.owner(), objectiveKind, encoded.target(), encoded.resourceSiteTarget(),
                    encoded.decisionOrdinal(), objectiveStatus);
            if (encoded.kind() >= StrategicObjectiveKind.values().length || encoded.status() >= StrategicObjectiveStatus.values().length
                    || objectives.put(encoded.id(), objective) != null) {
                throw new IllegalArgumentException("invalid or duplicate strategic objective");
            }
        }
        Map<SubjectId, StrategicTask> tasks = new LinkedHashMap<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            SubjectId id = readSubject(input), objective = readSubject(input), owner = readSubject(input); int kind = input.readUnsignedByte(); Optional<InfectionCell> target = readTarget(input);
            Optional<SubjectId> operationTarget = readOptionalSubject(input); Optional<SubjectId> resourceSiteTarget = readOptionalSubject(input);
            List<StrategicTaskRequirement> requirements = readRequirements(input); List<SubjectId> dependencies = readDependencies(input); int status = input.readUnsignedByte();
            Optional<BlockPosition> operationObservationPosition = hasOperationObservationPosition ? readOptionalPosition(input) : Optional.empty();
            StrategicTaskKind taskKind = taskKind(kind, preAssaultOrdinals);
            if (migrateLegacyProductionSlotRequirement && kind < StrategicTaskKind.values().length && taskKind == StrategicTaskKind.PRODUCE_BREAD) {
                List<StrategicTaskRequirement> legacy = List.of(StrategicTaskRequirement.ACTIVE_WORKSHOP,
                        StrategicTaskRequirement.EXACT_WHEAT_INPUT, StrategicTaskRequirement.FREE_DEPOT_SLOT);
                if (requirements.equals(legacy)) requirements = List.of(StrategicTaskRequirement.ACTIVE_WORKSHOP, StrategicTaskRequirement.EXACT_WHEAT_INPUT);
            }
            if (kind >= StrategicTaskKind.values().length || status >= StrategicTaskStatus.values().length
                    || tasks.put(id, new StrategicTask(id, objective, owner, taskKind, target, operationTarget, resourceSiteTarget,
                    requirements, dependencies, FrontierWireTags.require(StrategicTaskStatus.class, status), operationObservationPosition)) != null) {
                throw new IllegalArgumentException("invalid or duplicate strategic task");
            }
        }
        Map<SubjectId, RoutePatrol> patrols = new LinkedHashMap<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            SubjectId task = readSubject(input), settlement = readSubject(input);
            RouteUnitManifest unit = snapshotVersion >= 83 ? RouteUnitManifestCodec.read(input) : RouteUnitManifest.legacyPatrol(task, readSubject(input));
            List<BlockPosition> route = new ArrayList<>(); for (int point = 0, routeCount = readCount(input); point < routeCount; point++) route.add(readPosition(input));
            int cursor = input.readUnsignedByte(), status = input.readUnsignedByte(); Optional<BlockPosition> obstruction = input.readBoolean() ? Optional.of(readPosition(input)) : Optional.empty();
            if (status >= RoutePatrolStatus.values().length || patrols.put(task, new RoutePatrol(task, settlement, unit, route, cursor, FrontierWireTags.require(RoutePatrolStatus.class, status), obstruction)) != null) {
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
            if (status >= RouteEngagementStatus.values().length || engagements.put(id, new RouteEngagement(id, task, operation, hive, attackers, intercept, FrontierWireTags.require(RouteEngagementStatus.class, status), epoch, outcome)) != null) {
                throw new IllegalArgumentException("invalid or duplicate route engagement");
            }
        }
        Map<SubjectId, SettlementAssault> assaults = new LinkedHashMap<>();
        if (hasSettlementAssaults) for (int index = 0, count = readCount(input); index < count; index++) {
            SubjectId id = readSubject(input), task = readSubject(input), hive = readSubject(input);
            HiveSettlementKnowledge.Sighting sighting = new HiveSettlementKnowledge.Sighting(readSubject(input), readSubject(input), readPosition(input), input.readLong());
            List<SettlementAssaultAttacker> attackers = new ArrayList<>();
            for (int attacker = 0, attackerCount = readCount(input); attacker < attackerCount; attacker++) {
                SubjectId attackerId = readSubject(input); List<BlockPosition> route = new ArrayList<>();
                for (int point = 0, routeSize = readCount(input); point < routeSize; point++) route.add(readPosition(input));
                attackers.add(new SettlementAssaultAttacker(attackerId, route, input.readUnsignedByte()));
            }
            List<SubjectId> defenders = new ArrayList<>();
            for (int defender = 0, defenderCount = readCount(input); defender < defenderCount; defender++) defenders.add(readSubject(input));
            int status = input.readUnsignedByte(), epoch = input.readInt();
            Optional<SettlementAssaultOutcome> outcome = input.readBoolean() ? Optional.of(readAssaultOutcome(input)) : Optional.empty();
            if (status >= SettlementAssaultStatus.values().length || assaults.put(id, new SettlementAssault(id, task, hive, sighting, attackers, defenders,
                    FrontierWireTags.require(SettlementAssaultStatus.class, status), epoch, outcome)) != null) {
                throw new IllegalArgumentException("invalid or duplicate settlement assault");
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
        Map<InfectionCell, HiveTerritoryKnowledge.Belief> territory = new LinkedHashMap<>();
        if (hasHiveTerritoryKnowledge) for (int index = 0, count = readCount(input); index < count; index++) {
            InfectionCell cell = readTarget(input).orElseThrow(() -> new IllegalArgumentException("hive territory belief must retain a cell"));
            HiveTerritoryKnowledge.Belief belief = new HiveTerritoryKnowledge.Belief(cell,
                    new io.farfrontier.palemirror.frontier.v3.api.FixedRatio(new io.farfrontier.palemirror.frontier.v3.api.FixedScalar(input.readLong())), readSubject(input), readPosition(input), input.readLong());
            if (territory.put(cell, belief) != null) throw new IllegalArgumentException("duplicate hive territory belief");
        }
        Map<SubjectId, HiveSettlementKnowledge.Sighting> settlementSightings = new LinkedHashMap<>();
        if (hasHiveSettlementKnowledge) for (int index = 0, count = readCount(input); index < count; index++) {
            SubjectId settlement = readSubject(input), scout = readSubject(input);
            HiveSettlementKnowledge.Sighting sighting = new HiveSettlementKnowledge.Sighting(settlement, scout, readPosition(input), input.readLong());
            if (settlementSightings.put(settlement, sighting) != null) throw new IllegalArgumentException("duplicate hive settlement sighting");
        }
        HiveDoctrineState doctrine = HiveDoctrineState.initial();
        if (hasHiveDoctrine) { int kind = input.readUnsignedByte(); if (kind >= HiveDoctrine.values().length) throw new IllegalArgumentException("unknown hive doctrine");
            doctrine = new HiveDoctrineState(FrontierWireTags.require(HiveDoctrine.class, kind), input.readLong()); }
        return new StrategicPlanState(objectives, tasks, patrols, engagements, new SettlementInfectionKnowledge(knowledge), new HiveOperationKnowledge(hiveKnowledge),
                new HiveTerritoryKnowledge(territory), new HiveSettlementKnowledge(settlementSightings), doctrine, assaults);
    }

    /**
     * Snapshots before stable tags should normally use the assault-era ordinal layout from
     * schema 78/79. One deployed r41 lineage recorded its pre-assault harvest layout while
     * retaining schema 79; an exact field target on raw tag 8 is unambiguous evidence of that
     * older layout because assault-era tag 8 is route construction and can never carry a field.
     */
    private static boolean usesPreAssaultOrdinals(int snapshotVersion, List<RawObjective> objectives) {
        if (snapshotVersion < 78) return true;
        if (snapshotVersion >= 80) return false;
        return objectives.stream().anyMatch(value -> value.kind() == 8 && value.resourceSiteTarget().isPresent());
    }

    static StrategicObjectiveKind objectiveKind(int tag, boolean preAssaultOrdinals) {
        if (!preAssaultOrdinals) return FrontierWireTags.require(StrategicObjectiveKind.class, tag);
        return switch (tag) {
            case 0 -> StrategicObjectiveKind.SETTLEMENT_CONTAIN_LOCAL_INFECTION;
            case 1 -> StrategicObjectiveKind.HIVE_EXPAND_INFECTION;
            case 2 -> StrategicObjectiveKind.HIVE_GROW_ORGANISM;
            case 3 -> StrategicObjectiveKind.HIVE_INTERCEPT_ROUTE_OPERATION;
            case 4 -> StrategicObjectiveKind.SETTLEMENT_PRODUCE_BREAD;
            case 5 -> StrategicObjectiveKind.SETTLEMENT_DELIVER_BREAD_TO_HIVE;
            case 6 -> StrategicObjectiveKind.SETTLEMENT_PATROL_OBSTRUCTED_ROUTE;
            case 7 -> StrategicObjectiveKind.SETTLEMENT_CONSTRUCT_ROUTE_BYPASS;
            case 8 -> StrategicObjectiveKind.SETTLEMENT_HARVEST_RESOURCE_SITE;
            default -> throw new IllegalArgumentException("unknown pre-assault StrategicObjectiveKind wire tag: " + tag);
        };
    }

    static StrategicTaskKind taskKind(int tag, boolean preAssaultOrdinals) {
        if (!preAssaultOrdinals) return FrontierWireTags.require(StrategicTaskKind.class, tag);
        return switch (tag) {
            case 0 -> StrategicTaskKind.DECONTAMINATE_INFECTION_CELL;
            case 1 -> StrategicTaskKind.SPREAD_INFECTION_CELL;
            case 2 -> StrategicTaskKind.GROW_HIVE_ORGANISM;
            case 3 -> StrategicTaskKind.INTERCEPT_ROUTE_OPERATION;
            case 4 -> StrategicTaskKind.PRODUCE_BREAD;
            case 5 -> StrategicTaskKind.PREPARE_BREAD_CARGO;
            case 6 -> StrategicTaskKind.DELIVER_BREAD_TO_HIVE;
            case 7 -> StrategicTaskKind.PATROL_OBSTRUCTED_ROUTE;
            case 8 -> StrategicTaskKind.CONSTRUCT_ROUTE_BYPASS;
            case 9 -> StrategicTaskKind.HARVEST_RESOURCE_SITE;
            default -> throw new IllegalArgumentException("unknown pre-assault StrategicTaskKind wire tag: " + tag);
        };
    }

    private record RawObjective(SubjectId id, SubjectId owner, int kind, Optional<InfectionCell> target,
                                Optional<SubjectId> resourceSiteTarget, int decisionOrdinal, int status) { }

    private static List<StrategicTaskRequirement> readRequirements(DataInputStream input) throws IOException {
        List<StrategicTaskRequirement> values = new ArrayList<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            int value = input.readUnsignedByte(); if (value >= StrategicTaskRequirement.values().length) throw new IllegalArgumentException("unknown strategic task requirement");
            values.add(FrontierWireTags.require(StrategicTaskRequirement.class, value));
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
        return FrontierWireTags.require(RouteEngagementOutcome.class, value);
    }
    private static SettlementAssaultOutcome readAssaultOutcome(DataInputStream input) throws IOException {
        int value = input.readUnsignedByte(); if (value >= SettlementAssaultOutcome.values().length) throw new IllegalArgumentException("unknown settlement assault outcome");
        return FrontierWireTags.require(SettlementAssaultOutcome.class, value);
    }
    private static void writePosition(DataOutputStream output, BlockPosition position) throws IOException { output.writeInt(position.x()); output.writeInt(position.y()); output.writeInt(position.z()); }
    private static BlockPosition readPosition(DataInputStream input) throws IOException { return new BlockPosition(input.readInt(), input.readInt(), input.readInt()); }
    private static void writeSubject(DataOutputStream output, SubjectId id) throws IOException { FrontierWorldStateCodec.writeString(output, id.value()); }
    private static SubjectId readSubject(DataInputStream input) throws IOException { return new SubjectId(FrontierWorldStateCodec.readString(input)); }
    private static void writeCount(DataOutputStream output, int count) throws IOException { if (count > 512) throw new IllegalArgumentException("too many strategic plan entries"); output.writeShort(count); }
    private static int readCount(DataInputStream input) throws IOException { int count = input.readUnsignedShort(); if (count > 512) throw new IllegalArgumentException("too many strategic plan entries"); return count; }
}
