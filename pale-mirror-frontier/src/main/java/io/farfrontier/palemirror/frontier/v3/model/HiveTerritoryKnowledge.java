package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedRatio;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Bounded durable picture of terrain that the hive has actually sensed.  It is deliberately
 * separate from the canonical infection field: a strategic decision may reason from this map,
 * but may not scan the whole world field to discover a remote frontier.
 */
public final class HiveTerritoryKnowledge {
    public static final int MAX_BELIEFS = 256;
    private final Map<InfectionCell, Belief> beliefs;

    public HiveTerritoryKnowledge(Map<InfectionCell, Belief> beliefs) {
        Map<InfectionCell, Belief> copy = new LinkedHashMap<>();
        for (Map.Entry<InfectionCell, Belief> entry : Objects.requireNonNull(beliefs, "hive territory beliefs").entrySet()) {
            if (!entry.getKey().equals(entry.getValue().cell())) throw new IllegalArgumentException("hive territory key must match cell");
            if (copy.put(entry.getKey(), entry.getValue()) != null) throw new IllegalArgumentException("duplicate hive territory belief");
        }
        if (copy.size() > MAX_BELIEFS) throw new IllegalArgumentException("hive territory belief retention limit exceeded");
        this.beliefs = Map.copyOf(copy);
    }

    public static HiveTerritoryKnowledge empty() { return new HiveTerritoryKnowledge(Map.of()); }
    public Map<InfectionCell, Belief> entries() { return beliefs; }

    public HiveTerritoryKnowledge observe(Belief belief) {
        Map<InfectionCell, Belief> next = new LinkedHashMap<>(beliefs);
        Belief previous = next.get(belief.cell());
        if (previous != null && previous.observedAt() > belief.observedAt()) return this;
        next.put(belief.cell(), belief);
        while (next.size() > MAX_BELIEFS) {
            InfectionCell oldest = next.values().stream().min(Comparator.comparingLong(Belief::observedAt)
                    .thenComparingInt(value -> value.cell().x()).thenComparingInt(value -> value.cell().z())).orElseThrow().cell();
            next.remove(oldest);
        }
        return new HiveTerritoryKnowledge(next);
    }

    public Map<InfectionCell, FixedRatio> freshInfection(FrontierRuleset ruleset, long now) {
        Map<InfectionCell, FixedRatio> result = new LinkedHashMap<>();
        beliefs.values().stream().filter(value -> value.observedAt() >= Math.subtractExact(now, ruleset.cadence().hiveTerritoryKnowledgeMaxAge()))
                .sorted(Comparator.comparingInt((Belief value) -> value.cell().x()).thenComparingInt(value -> value.cell().z()))
                .forEach(value -> result.put(value.cell(), value.intensity()));
        return Map.copyOf(result);
    }

    void validate(FrontierBootstrap bootstrap, HiveColony colony, Map<SubjectId, ActorLocation> actors, Map<SubjectId, StructureCondition> structures) {
        for (Belief belief : beliefs.values()) validateObservation(bootstrap, colony, actors, belief);
    }

    @Override public boolean equals(Object other) { return other instanceof HiveTerritoryKnowledge value && beliefs.equals(value.beliefs); }
    @Override public int hashCode() { return beliefs.hashCode(); }

    private static void validateObservation(FrontierBootstrap bootstrap, HiveColony colony, Map<SubjectId, ActorLocation> actors, Belief belief) {
        sensorPosition(bootstrap, colony, actors, belief.observerId());
        if (!nearby(belief.sensorPosition(), belief.cell().originAtY(belief.sensorPosition().y()), sensorRadius(bootstrap, colony, belief.observerId()))) {
            throw new IllegalArgumentException("hive territory observation is outside its local sensor radius");
        }
    }

    private static BlockPosition sensorPosition(FrontierBootstrap bootstrap, HiveColony colony, Map<SubjectId, ActorLocation> actors, SubjectId observer) {
        HiveOrgan organ = organs(bootstrap, colony).stream().filter(value -> value.id().equals(observer)).findFirst().orElse(null);
        if (organ != null) {
            if (organ.kind() != HiveOrganKind.HEART) throw new IllegalArgumentException("hive territory observer is not a heart");
            return organ.anchor();
        }
        Bioform scout = FrontierWorldStateSupport.bioform(bootstrap, colony, observer);
        ActorLocation location = actors.get(scout.id());
        if (scout.role() != BioformRole.SCOUT || location == null) throw new IllegalArgumentException("hive territory observer is not a live scout");
        return location.position();
    }

    private static int sensorRadius(FrontierBootstrap bootstrap, HiveColony colony, SubjectId observer) {
        return organs(bootstrap, colony).stream().anyMatch(value -> value.id().equals(observer))
                ? bootstrap.ruleset().spatial().hiveTerritoryHeartRadius() : bootstrap.ruleset().spatial().hiveTerritoryScoutRadius();
    }

    private static java.util.List<HiveOrgan> organs(FrontierBootstrap bootstrap, HiveColony colony) {
        return java.util.stream.Stream.concat(bootstrap.hive().organs().stream(), colony.addedOrgans().values().stream()).toList();
    }

    private static boolean nearby(BlockPosition left, BlockPosition right, int radius) {
        long x = (long) left.x() - right.x(), z = (long) left.z() - right.z(); return x * x + z * z <= (long) radius * radius;
    }

    public record Belief(InfectionCell cell, FixedRatio intensity, SubjectId observerId, BlockPosition sensorPosition, long observedAt) {
        public Belief {
            Objects.requireNonNull(cell, "hive territory cell"); Objects.requireNonNull(intensity, "hive territory intensity");
            Objects.requireNonNull(observerId, "hive territory observer"); Objects.requireNonNull(sensorPosition, "hive territory sensor position");
            if (intensity.value().raw() <= 0L) throw new IllegalArgumentException("hive territory belief must retain nonzero infection");
            if (observedAt < 0L) throw new IllegalArgumentException("hive territory observation tick must be non-negative");
        }
    }
}
