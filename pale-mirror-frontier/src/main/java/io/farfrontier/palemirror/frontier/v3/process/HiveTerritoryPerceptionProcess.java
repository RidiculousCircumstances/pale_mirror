package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.model.*;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Local hive biology is the only source of durable territorial beliefs. */
public final class HiveTerritoryPerceptionProcess {
    private static final int MAX_OBSERVATIONS_PER_REFRESH = 4;
    private HiveTerritoryPerceptionProcess() { }

    public static Refresh refresh(FrontierWorldState state, long now) {
        HiveTerritoryKnowledge next = state.strategicPlans().hiveTerritoryKnowledge();
        List<ProposedEvent> events = new ArrayList<>();
        List<SubjectId> sensors = sensors(state);
        for (Map.Entry<InfectionCell, io.farfrontier.palemirror.frontier.v3.api.FixedRatio> entry : state.infection().entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<InfectionCell, io.farfrontier.palemirror.frontier.v3.api.FixedRatio> value) -> value.getKey().x())
                        .thenComparingInt(value -> value.getKey().z())).toList()) {
            SubjectId observer = sensors.stream().filter(candidate -> canObserve(state, candidate, entry.getKey())).findFirst().orElse(null);
            if (observer == null) continue;
            BlockPosition sensorPosition = sensorPosition(state.bootstrap(), state.hiveColony(), state.actorLocations(), state.structureConditions(), observer);
            HiveTerritoryKnowledge.Belief belief = new HiveTerritoryKnowledge.Belief(entry.getKey(), entry.getValue(), observer, sensorPosition, now);
            HiveTerritoryKnowledge.Belief previous = next.entries().get(entry.getKey());
            if (previous != null && previous.intensity().equals(belief.intensity()) && previous.observerId().equals(observer)
                    && previous.sensorPosition().equals(sensorPosition) && previous.observedAt() >= now
                    - state.bootstrap().ruleset().cadence().hiveTerritoryKnowledgeMaxAge() / 3L) continue;
            next = next.observe(belief); events.add(new ProposedEvent(state.bootstrap().hive().id(), new HiveTerritoryObserved(belief)));
            if (events.size() >= MAX_OBSERVATIONS_PER_REFRESH) break;
        }
        return new Refresh(next, List.copyOf(events));
    }

    public static FrontierWorldState reduce(FrontierWorldState state, SubjectId subject, HiveTerritoryObserved observed) {
        if (!subject.equals(state.bootstrap().hive().id())) throw new IllegalArgumentException("hive territory observation has a foreign owner");
        HiveTerritoryKnowledge.Belief belief = observed.belief();
        validateObservation(state.bootstrap(), state.hiveColony(), state.actorLocations(), state.structureConditions(), belief);
        if (!canCurrentlyObserve(state, belief)) throw new IllegalArgumentException("hive territory observer is no longer active at its observed position");
        if (!belief.intensity().equals(state.infection().get(belief.cell()))) throw new IllegalArgumentException("hive territory observation is not current infection");
        return state.withStrategicPlans(state.strategicPlans().withHiveTerritoryKnowledge(state.strategicPlans().hiveTerritoryKnowledge().observe(belief)));
    }

    static void validateObservation(FrontierBootstrap bootstrap, HiveColony colony, Map<SubjectId, ActorLocation> actors,
                                    Map<SubjectId, StructureCondition> structures, HiveTerritoryKnowledge.Belief belief) {
        sensorPosition(bootstrap, colony, actors, structures, belief.observerId());
        if (!nearby(belief.sensorPosition(), belief.cell().originAtY(belief.sensorPosition().y()), sensorRadius(bootstrap, colony, belief.observerId()))) {
            throw new IllegalArgumentException("hive territory observation is outside its local sensor radius");
        }
    }

    private static List<SubjectId> sensors(FrontierWorldState state) {
        List<SubjectId> sensors = new ArrayList<>();
        java.util.stream.Stream.concat(state.bootstrap().hive().organs().stream(), state.hiveColony().addedOrgans().values().stream())
                .filter(organ -> organ.kind() == HiveOrganKind.HEART && state.isHiveOrganOperational(organ.id()))
                .map(HiveOrgan::id).forEach(sensors::add);
        java.util.stream.Stream.concat(state.bootstrap().hive().bioforms().stream(), state.hiveColony().spawnedBioforms().values().stream())
                .filter(bioform -> bioform.role() == BioformRole.SCOUT)
                .filter(bioform -> state.actorLocations().get(bioform.id()).condition().status() == ActorLifeStatus.ALIVE)
                .map(Bioform::id).forEach(sensors::add);
        return sensors.stream().sorted().toList();
    }

    private static boolean canObserve(FrontierWorldState state, SubjectId observer, InfectionCell cell) {
        BlockPosition sensor = sensorPosition(state.bootstrap(), state.hiveColony(), state.actorLocations(), state.structureConditions(), observer);
        return nearby(sensor, cell.originAtY(sensor.y()), sensorRadius(state.bootstrap(), state.hiveColony(), observer));
    }

    private static BlockPosition sensorPosition(FrontierBootstrap bootstrap, HiveColony colony, Map<SubjectId, ActorLocation> actors,
                                                Map<SubjectId, StructureCondition> structures, SubjectId observer) {
        HiveOrgan organ = organs(bootstrap, colony).stream().filter(value -> value.id().equals(observer)).findFirst().orElse(null);
        if (organ != null) {
            if (organ.kind() != HiveOrganKind.HEART) throw new IllegalArgumentException("hive territory observer is not a heart");
            return organ.anchor();
        }
        Bioform scout = FrontierWorldStateSupport.bioform(bootstrap, colony, observer);
        ActorLocation location = actors.get(scout.id());
        if (scout.role() != BioformRole.SCOUT) {
            throw new IllegalArgumentException("hive territory observer is not a scout");
        }
        return location.supportingSurface().support();
    }

    private static int sensorRadius(FrontierBootstrap bootstrap, HiveColony colony, SubjectId observer) {
        return organs(bootstrap, colony).stream().anyMatch(value -> value.id().equals(observer))
                ? bootstrap.ruleset().spatial().hiveTerritoryHeartRadius() : bootstrap.ruleset().spatial().hiveTerritoryScoutRadius();
    }

    private static List<HiveOrgan> organs(FrontierBootstrap bootstrap, HiveColony colony) {
        return java.util.stream.Stream.concat(bootstrap.hive().organs().stream(), colony.addedOrgans().values().stream()).toList();
    }

    private static boolean isOperational(HiveOrgan organ, Map<SubjectId, StructureCondition> structures) {
        return structures.getOrDefault(organ.id(), StructureCondition.INTACT) != StructureCondition.DESTROYED;
    }

    private static boolean canCurrentlyObserve(FrontierWorldState state, HiveTerritoryKnowledge.Belief belief) {
        SubjectId observer = belief.observerId();
        HiveOrgan organ = organs(state.bootstrap(), state.hiveColony()).stream().filter(value -> value.id().equals(observer)).findFirst().orElse(null);
        if (organ != null) return organ.kind() == HiveOrganKind.HEART && state.isHiveOrganOperational(organ.id()) && organ.anchor().equals(belief.sensorPosition());
        Bioform scout = FrontierWorldStateSupport.bioform(state.bootstrap(), state.hiveColony(), observer);
        if (scout.role() != BioformRole.SCOUT || state.actorLocations().get(scout.id()).condition().status() != ActorLifeStatus.ALIVE) return false;
        BlockPosition current = state.actorLocations().get(scout.id()).supportingSurface().support();
        return current.equals(belief.sensorPosition()) || HiveScoutPatrolProcess.nextPosition(state, scout, belief.sensorPosition()).equals(current);
    }

    private static boolean nearby(BlockPosition left, BlockPosition right, int radius) {
        long x = (long) left.x() - right.x(), z = (long) left.z() - right.z();
        return x * x + z * z <= (long) radius * radius;
    }

    public record Refresh(HiveTerritoryKnowledge knowledge, List<ProposedEvent> events) { }
}
