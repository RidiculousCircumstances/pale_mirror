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
            output.writeInt(objective.decisionOrdinal()); output.writeByte(objective.status().wireTag()); writeSubject(output, objective.authorityId()); output.writeLong(objective.authorityEpoch());
        }
        writeCount(output, plans.tasks().size());
        for (StrategicTask task : plans.tasks().values().stream().sorted(Comparator.comparing(StrategicTask::id)).toList()) {
            writeSubject(output, task.id()); writeSubject(output, task.objectiveId()); writeSubject(output, task.ownerId()); output.writeByte(task.kind().wireTag());
            writeTarget(output, task.infectionTarget()); writeOptionalSubject(output, task.resourceSiteTarget());
            writeCount(output, task.requirements().size());
            for (StrategicTaskRequirement requirement : task.requirements()) output.writeByte(requirement.wireTag());
            writeCount(output, task.dependencies().size()); for (SubjectId dependency : task.dependencies()) writeSubject(output, dependency);
            output.writeByte(task.status().wireTag()); writeSubject(output, task.authorityId()); output.writeLong(task.authorityEpoch());
        }
        writeCount(output, plans.routePatrols().size());
        for (RoutePatrol patrol : plans.routePatrols().values().stream().sorted(Comparator.comparing(RoutePatrol::taskId)).toList()) {
            writeSubject(output, patrol.taskId()); writeSubject(output, patrol.settlementId()); RouteUnitManifestCodec.write(output, patrol.unit());
            TraversalTopologyStateCodec.write(output, patrol.inspectionRoute()); PatrolStateCodec.writeAssembly(output, patrol.assembly());
            PatrolStateCodec.writeTravel(output, patrol.travel()); TacticalPlanStateCodec.write(output, patrol.tacticalPlan());
            output.writeByte(patrol.status().wireTag()); output.writeBoolean(patrol.obstruction().isPresent());
            if (patrol.obstruction().isPresent()) writePosition(output, patrol.obstruction().orElseThrow());
            output.writeBoolean(patrol.blockReason().isPresent());
            if (patrol.blockReason().isPresent()) output.writeByte(patrol.blockReason().orElseThrow().wireTag());
        }
        writeCount(output, plans.settlementAssaults().size());
        for (SettlementAssault assault : plans.settlementAssaults().values().stream().sorted(Comparator.comparing(SettlementAssault::id)).toList()) {
            writeSubject(output, assault.id()); writeSubject(output, assault.taskId()); writeSubject(output, assault.hiveId()); writeSubject(output, assault.overseerId());
            writeSubject(output, assault.sighting().settlementId()); writeSubject(output, assault.sighting().scoutId());
            writePosition(output, assault.sighting().settlementAnchor()); output.writeLong(assault.sighting().observedAt());
            writeCount(output, assault.attackers().size());
            for (SettlementAssaultAttacker attacker : assault.attackers()) {
                writeSubject(output, attacker.actorId()); writeCount(output, attacker.route().size());
                for (BlockPosition position : attacker.route()) writePosition(output, position);
                output.writeByte(attacker.routeIndex());
            }
            writeMarch(output, assault.march());
            writeCount(output, assault.defenderIds().size());
            for (SubjectId defender : assault.defenderIds()) writeSubject(output, defender);
            TacticalPlanStateCodec.write(output, assault.tacticalPlan());
            output.writeByte(assault.status().wireTag()); output.writeInt(assault.nextStrikeEpoch());
            output.writeBoolean(assault.outcome().isPresent()); if (assault.outcome().isPresent()) output.writeByte(assault.outcome().orElseThrow().wireTag());
        }
        writeCount(output, plans.decisionAuthorities().authorities().size());
        for (DecisionAuthority authority : plans.decisionAuthorities().authorities().values().stream().sorted(Comparator.comparing(DecisionAuthority::ownerId)).toList()) {
            writeSubject(output, authority.ownerId()); output.writeByte(FrontierWireTags.tag(authority.kind()));
            FrontierWorldStateCodec.writeString(output, authority.policy().id()); output.writeInt(authority.policy().version());
            output.writeLong(authority.reconsiderationEpoch()); writeSubjects(output, authority.commitmentIds()); writeSubjects(output, authority.provenanceIds());
            ResidentWorkPermissionsCodec.write(output, authority.workPermissions());
        }
        writeCount(output, plans.frontEffects().applied().size());
        for (OperationFrontEffectKey effect : plans.frontEffects().applied().stream()
                .sorted(Comparator.comparing(OperationFrontEffectKey::causeId)
                        .thenComparing(OperationFrontEffectKey::sourceFrontId)
                        .thenComparing(OperationFrontEffectKey::targetFrontId)
                        .thenComparingLong(OperationFrontEffectKey::authorityEpoch)).toList()) {
            writeSubject(output, effect.causeId()); writeSubject(output, effect.sourceFrontId());
            writeSubject(output, effect.targetFrontId()); output.writeLong(effect.authorityEpoch());
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
        ScoutPatrolCodec.write(output, plans);
    }

    public static StrategicPlanState read(DataInputStream input) throws IOException {
        Map<SubjectId, StrategicObjective> objectives = new LinkedHashMap<>();
        List<RawObjective> encodedObjectives = new ArrayList<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            SubjectId id = readSubject(input), owner = readSubject(input); int kind = input.readUnsignedByte(); Optional<InfectionCell> target = readTarget(input);
            Optional<SubjectId> resourceSiteTarget = readOptionalSubject(input);
            int ordinal = input.readInt(), status = input.readUnsignedByte(); SubjectId authority = readSubject(input); long authorityEpoch = input.readLong();
            encodedObjectives.add(new RawObjective(id, owner, kind, target, resourceSiteTarget, ordinal, status, authority, authorityEpoch));
        }
        for (RawObjective encoded : encodedObjectives) {
            StrategicObjectiveKind objectiveKind = FrontierWireTags.require(StrategicObjectiveKind.class, encoded.kind());
            StrategicObjectiveStatus objectiveStatus = FrontierWireTags.require(StrategicObjectiveStatus.class, encoded.status());
            StrategicObjective objective = new StrategicObjective(encoded.id(), encoded.owner(), objectiveKind, encoded.target(), encoded.resourceSiteTarget(),
                    encoded.decisionOrdinal(), objectiveStatus, encoded.authorityId(), encoded.authorityEpoch());
            if (encoded.status() >= StrategicObjectiveStatus.values().length
                    || objectives.put(encoded.id(), objective) != null) {
                throw new IllegalArgumentException("invalid or duplicate strategic objective");
            }
        }
        Map<SubjectId, StrategicTask> tasks = new LinkedHashMap<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            SubjectId id = readSubject(input), objective = readSubject(input), owner = readSubject(input); int kind = input.readUnsignedByte(); Optional<InfectionCell> target = readTarget(input);
            Optional<SubjectId> resourceSiteTarget = readOptionalSubject(input);
            List<StrategicTaskRequirement> requirements = readRequirements(input); List<SubjectId> dependencies = readDependencies(input); int status = input.readUnsignedByte();
            SubjectId authority = readSubject(input); long authorityEpoch = input.readLong();
            StrategicTaskKind taskKind = FrontierWireTags.require(StrategicTaskKind.class, kind);
            if (status >= StrategicTaskStatus.values().length
                    || tasks.put(id, new StrategicTask(id,
                objective,
                owner,
                taskKind,
                target,
                resourceSiteTarget,
                requirements,
                dependencies,
                FrontierWireTags.require(StrategicTaskStatus.class, status),
                authority,
                authorityEpoch)) != null) {
                throw new IllegalArgumentException("invalid or duplicate strategic task");
            }
        }
        Map<SubjectId, RoutePatrol> patrols = new LinkedHashMap<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            SubjectId task = readSubject(input), settlement = readSubject(input);
            RouteUnitManifest unit = RouteUnitManifestCodec.read(input); TraversalTopology inspection = TraversalTopologyStateCodec.read(input);
            PatrolAssembly assembly = PatrolStateCodec.readAssembly(input); PatrolTravel travel = PatrolStateCodec.readTravel(input);
            TacticalPlan tacticalPlan = TacticalPlanStateCodec.read(input);
            int status = input.readUnsignedByte(); Optional<BlockPosition> obstruction = input.readBoolean() ? Optional.of(readPosition(input)) : Optional.empty();
            Optional<RoutePatrolBlockReason> blockReason = input.readBoolean() ? Optional.of(readPatrolBlockReason(input)) : Optional.empty();
            if (status >= RoutePatrolStatus.values().length || patrols.put(task, new RoutePatrol(task, settlement, unit, inspection, assembly, travel,
                    tacticalPlan, FrontierWireTags.require(RoutePatrolStatus.class, status), obstruction, blockReason)) != null) {
                throw new IllegalArgumentException("invalid or duplicate route patrol");
            }
        }
        Map<SubjectId, SettlementAssault> assaults = new LinkedHashMap<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            SubjectId id = readSubject(input), task = readSubject(input), hive = readSubject(input), overseer = readSubject(input);
            HiveSettlementKnowledge.Sighting sighting = new HiveSettlementKnowledge.Sighting(readSubject(input), readSubject(input), readPosition(input), input.readLong());
            List<SettlementAssaultAttacker> attackers = new ArrayList<>();
            for (int attacker = 0, attackerCount = readCount(input); attacker < attackerCount; attacker++) {
                SubjectId attackerId = readSubject(input); List<BlockPosition> route = new ArrayList<>();
                for (int point = 0, routeSize = readCount(input); point < routeSize; point++) route.add(readPosition(input));
                attackers.add(new SettlementAssaultAttacker(attackerId, route, input.readUnsignedByte()));
            }
            ExpeditionMarch march = readMarch(input);
            List<SubjectId> defenders = new ArrayList<>();
            for (int defender = 0, defenderCount = readCount(input); defender < defenderCount; defender++) defenders.add(readSubject(input));
            TacticalPlan tacticalPlan = TacticalPlanStateCodec.read(input);
            int status = input.readUnsignedByte(), epoch = input.readInt();
            Optional<SettlementAssaultOutcome> outcome = input.readBoolean() ? Optional.of(readAssaultOutcome(input)) : Optional.empty();
            if (assaults.put(id, new SettlementAssault(id, task, hive, sighting, overseer, attackers, march,
                    SettlementDefenderUnit.forAssault(id, sighting.settlementId(), defenders), tacticalPlan,
                    FrontierWireTags.require(SettlementAssaultStatus.class, status), epoch, outcome)) != null) {
                throw new IllegalArgumentException("invalid or duplicate settlement assault");
            }
        }
        Map<SubjectId, DecisionAuthority> authorities = new LinkedHashMap<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            SubjectId owner = readSubject(input); int kind = input.readUnsignedByte();
            DecisionAuthorityKind authorityKind = FrontierWireTags.require(DecisionAuthorityKind.class, kind);
            DecisionPolicyDescriptor policy = new DecisionPolicyDescriptor(FrontierWorldStateCodec.readString(input), input.readInt());
            long epoch = input.readLong(); var commitments = readSubjects(input); var provenance = readSubjects(input);
            ResidentWorkPermissions permissions = ResidentWorkPermissionsCodec.read(input);
            DecisionAuthority authority = new DecisionAuthority(owner, authorityKind, policy, epoch, commitments, provenance,
                    permissions);
            if (authorities.put(owner, authority) != null) throw new IllegalArgumentException("duplicate decision authority");
        }
        java.util.Set<OperationFrontEffectKey> frontEffects = new java.util.LinkedHashSet<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            OperationFrontEffectKey effect = new OperationFrontEffectKey(readSubject(input), readSubject(input), readSubject(input), input.readLong());
            if (!frontEffects.add(effect)) throw new IllegalArgumentException("duplicate cross-front effect receipt");
        }
        Map<SubjectId, Map<InfectionCell, SettlementInfectionKnowledge.KnownInfection>> knowledge = new LinkedHashMap<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            SubjectId settlement = readSubject(input); Map<InfectionCell, SettlementInfectionKnowledge.KnownInfection> cells = new LinkedHashMap<>();
            for (int cellIndex = 0, cellCount = readCount(input); cellIndex < cellCount; cellIndex++) {
                InfectionCell cell = readTarget(input).orElseThrow(() -> new IllegalArgumentException("known infection must retain a cell"));
                SettlementInfectionKnowledge.KnownInfection known = new SettlementInfectionKnowledge.KnownInfection(cell,
                        new io.farfrontier.palemirror.frontier.v3.api.FixedRatio(new io.farfrontier.palemirror.frontier.v3.api.FixedScalar(input.readLong())), input.readLong());
                if (cells.put(cell, known) != null) throw new IllegalArgumentException("duplicate known infection cell");
            }
            if (knowledge.put(settlement, cells) != null) throw new IllegalArgumentException("duplicate settlement infection knowledge");
        }
        Map<InfectionCell, HiveTerritoryKnowledge.Belief> territory = new LinkedHashMap<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            InfectionCell cell = readTarget(input).orElseThrow(() -> new IllegalArgumentException("hive territory belief must retain a cell"));
            HiveTerritoryKnowledge.Belief belief = new HiveTerritoryKnowledge.Belief(cell,
                    new io.farfrontier.palemirror.frontier.v3.api.FixedRatio(new io.farfrontier.palemirror.frontier.v3.api.FixedScalar(input.readLong())), readSubject(input), readPosition(input), input.readLong());
            if (territory.put(cell, belief) != null) throw new IllegalArgumentException("duplicate hive territory belief");
        }
        Map<SubjectId, HiveSettlementKnowledge.Sighting> settlementSightings = new LinkedHashMap<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            SubjectId settlement = readSubject(input), scout = readSubject(input);
            HiveSettlementKnowledge.Sighting sighting = new HiveSettlementKnowledge.Sighting(settlement, scout, readPosition(input), input.readLong());
            if (settlementSightings.put(settlement, sighting) != null) throw new IllegalArgumentException("duplicate hive settlement sighting");
        }
        HiveDoctrineState doctrine = HiveDoctrineState.initial();
        { int kind = input.readUnsignedByte(); if (kind >= HiveDoctrine.values().length) throw new IllegalArgumentException("unknown hive doctrine");
            doctrine = new HiveDoctrineState(FrontierWireTags.require(HiveDoctrine.class, kind), input.readLong()); }
        return new StrategicPlanState(objectives, tasks, patrols, new SettlementInfectionKnowledge(knowledge),
                new HiveTerritoryKnowledge(territory), new HiveSettlementKnowledge(settlementSightings), doctrine,
                assaults, new DecisionAuthorityState(authorities), new OperationFrontEffectCoordinator(frontEffects),
                ScoutPatrolCodec.read(input));
    }



    private record RawObjective(SubjectId id, SubjectId owner, int kind, Optional<InfectionCell> target,
                                Optional<SubjectId> resourceSiteTarget, int decisionOrdinal, int status, SubjectId authorityId, long authorityEpoch) { }

    private static List<StrategicTaskRequirement> readRequirements(DataInputStream input) throws IOException {
        List<StrategicTaskRequirement> values = new ArrayList<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            int value = input.readUnsignedByte();
            values.add(FrontierWireTags.require(StrategicTaskRequirement.class, value));
        }
        return values;
    }
    private static List<SubjectId> readDependencies(DataInputStream input) throws IOException {
        List<SubjectId> values = new ArrayList<>(); for (int index = 0, count = readCount(input); index < count; index++) values.add(readSubject(input)); return values;
    }
    private static void writeSubjects(DataOutputStream output, List<SubjectId> values) throws IOException {
        writeCount(output, values.size()); for (SubjectId value : values) writeSubject(output, value);
    }
    private static List<SubjectId> readSubjects(DataInputStream input) throws IOException {
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
    private static SettlementAssaultOutcome readAssaultOutcome(DataInputStream input) throws IOException {
        return FrontierWireTags.require(SettlementAssaultOutcome.class, input.readUnsignedByte());
    }
    private static void writeMarch(DataOutputStream output, ExpeditionMarch march) throws IOException {
        ExpeditionMarchCodec.write(output, march);
    }
    private static ExpeditionMarch readMarch(DataInputStream input) throws IOException {
        return ExpeditionMarchCodec.read(input);
    }
    private static RoutePatrolBlockReason readPatrolBlockReason(DataInputStream input) throws IOException {
        return FrontierWireTags.require(RoutePatrolBlockReason.class, input.readUnsignedByte());
    }
    private static void writePosition(DataOutputStream output, BlockPosition position) throws IOException { output.writeInt(position.x()); output.writeInt(position.y()); output.writeInt(position.z()); }
    private static BlockPosition readPosition(DataInputStream input) throws IOException { return new BlockPosition(input.readInt(), input.readInt(), input.readInt()); }
    private static void writeSubject(DataOutputStream output, SubjectId id) throws IOException { FrontierWorldStateCodec.writeString(output, id.value()); }
    private static SubjectId readSubject(DataInputStream input) throws IOException { return new SubjectId(FrontierWorldStateCodec.readString(input)); }
    private static void writeCount(DataOutputStream output, int count) throws IOException { if (count > 512) throw new IllegalArgumentException("too many strategic plan entries"); output.writeShort(count); }
    private static int readCount(DataInputStream input) throws IOException { int count = input.readUnsignedShort(); if (count > 512) throw new IllegalArgumentException("too many strategic plan entries"); return count; }
}
