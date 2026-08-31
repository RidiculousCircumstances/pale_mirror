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
final class HiveTerritoryKnowledge {
    static final int MAX_BELIEFS = 256;
    static final long MAX_AGE = 2_400L;
    private final Map<InfectionCell, Belief> beliefs;

    HiveTerritoryKnowledge(Map<InfectionCell, Belief> beliefs) {
        Map<InfectionCell, Belief> copy = new LinkedHashMap<>();
        for (Map.Entry<InfectionCell, Belief> entry : Objects.requireNonNull(beliefs, "hive territory beliefs").entrySet()) {
            if (!entry.getKey().equals(entry.getValue().cell())) throw new IllegalArgumentException("hive territory key must match cell");
            if (copy.put(entry.getKey(), entry.getValue()) != null) throw new IllegalArgumentException("duplicate hive territory belief");
        }
        if (copy.size() > MAX_BELIEFS) throw new IllegalArgumentException("hive territory belief retention limit exceeded");
        this.beliefs = Map.copyOf(copy);
    }

    static HiveTerritoryKnowledge empty() { return new HiveTerritoryKnowledge(Map.of()); }
    Map<InfectionCell, Belief> entries() { return beliefs; }

    HiveTerritoryKnowledge observe(Belief belief) {
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

    Map<InfectionCell, FixedRatio> freshInfection(long now) {
        Map<InfectionCell, FixedRatio> result = new LinkedHashMap<>();
        beliefs.values().stream().filter(value -> value.observedAt() >= Math.subtractExact(now, MAX_AGE))
                .sorted(Comparator.comparingInt((Belief value) -> value.cell().x()).thenComparingInt(value -> value.cell().z()))
                .forEach(value -> result.put(value.cell(), value.intensity()));
        return Map.copyOf(result);
    }

    void validate(FrontierBootstrap bootstrap, HiveColony colony, Map<SubjectId, ActorLocation> actors, Map<SubjectId, StructureCondition> structures) {
        for (Belief belief : beliefs.values()) HiveTerritoryPerceptionProcess.validateObservation(bootstrap, colony, actors, structures, belief);
    }

    @Override public boolean equals(Object other) { return other instanceof HiveTerritoryKnowledge value && beliefs.equals(value.beliefs); }
    @Override public int hashCode() { return beliefs.hashCode(); }

    record Belief(InfectionCell cell, FixedRatio intensity, SubjectId observerId, BlockPosition sensorPosition, long observedAt) {
        Belief {
            Objects.requireNonNull(cell, "hive territory cell"); Objects.requireNonNull(intensity, "hive territory intensity");
            Objects.requireNonNull(observerId, "hive territory observer"); Objects.requireNonNull(sensorPosition, "hive territory sensor position");
            if (intensity.value().raw() <= 0L) throw new IllegalArgumentException("hive territory belief must retain nonzero infection");
            if (observedAt < 0L) throw new IllegalArgumentException("hive territory observation tick must be non-negative");
        }
    }
}
