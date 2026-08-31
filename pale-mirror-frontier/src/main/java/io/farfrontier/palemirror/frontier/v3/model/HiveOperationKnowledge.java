package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Bounded scout-owned sightings; a hive objective cannot inspect a route operation directly. */
final class HiveOperationKnowledge {
    static final int MAX_SIGHTINGS = 64;
    private final Map<SubjectId, Sighting> sightings;

    HiveOperationKnowledge(Map<SubjectId, Sighting> sightings) {
        Map<SubjectId, Sighting> copy = new LinkedHashMap<>();
        for (Map.Entry<SubjectId, Sighting> entry : Objects.requireNonNull(sightings, "hive sightings").entrySet()) {
            if (!entry.getKey().equals(entry.getValue().operationId())) throw new IllegalArgumentException("hive sighting key must match operation");
            if (copy.put(entry.getKey(), entry.getValue()) != null) throw new IllegalArgumentException("duplicate hive sighting");
        }
        if (copy.size() > MAX_SIGHTINGS) throw new IllegalArgumentException("hive sighting retention limit exceeded");
        this.sightings = Map.copyOf(copy);
    }

    static HiveOperationKnowledge empty() { return new HiveOperationKnowledge(Map.of()); }
    Map<SubjectId, Sighting> entries() { return sightings; }
    Optional<Sighting> freshest(long now, long maximumAge) {
        return sightings.values().stream().filter(value -> value.observedAt() >= Math.subtractExact(now, maximumAge))
                .sorted(java.util.Comparator.comparingLong(Sighting::observedAt).reversed().thenComparing(Sighting::operationId)).findFirst();
    }
    HiveOperationKnowledge observe(Sighting sighting) {
        Map<SubjectId, Sighting> next = new LinkedHashMap<>(sightings); next.put(sighting.operationId(), sighting);
        while (next.size() > MAX_SIGHTINGS) next.remove(next.values().stream()
                .min(java.util.Comparator.comparingLong(Sighting::observedAt).thenComparing(Sighting::operationId)).orElseThrow().operationId());
        return new HiveOperationKnowledge(next);
    }
    void validate(FrontierBootstrap bootstrap, HiveColony colony, Map<SubjectId, ActorLocation> actors) {
        for (Sighting sighting : sightings.values()) {
            Bioform scout = FrontierWorldStateSupport.bioform(bootstrap, colony, sighting.scoutId());
            if (scout.role() != BioformRole.SCOUT || actors.get(scout.id()).condition().status() != ActorLifeStatus.ALIVE) {
                throw new IllegalArgumentException("hive sighting must retain one living scout");
            }
        }
    }
    @Override public boolean equals(Object other) { return other instanceof HiveOperationKnowledge value && sightings.equals(value.sightings); }
    @Override public int hashCode() { return sightings.hashCode(); }

    record Sighting(SubjectId operationId, SubjectId scoutId, BlockPosition position, long observedAt) {
        Sighting {
            Objects.requireNonNull(operationId, "sighted operation");
            Objects.requireNonNull(scoutId, "sighting scout");
            Objects.requireNonNull(position, "sighting position");
            if (observedAt < 0L) throw new IllegalArgumentException("sighting tick must be non-negative");
        }
    }
}
