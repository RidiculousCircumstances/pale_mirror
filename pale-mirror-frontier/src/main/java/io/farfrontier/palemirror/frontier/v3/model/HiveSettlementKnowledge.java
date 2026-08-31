package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Bounded, scout-observed settlement knowledge.
 *
 * <p>The hive may retain this picture for planning, but it must not select a settlement by
 * scanning bootstrap geometry.  A sighting names the exact Scout, the exact visible settlement
 * anchor and the instant at which that local fact was observed.</p>
 */
final class HiveSettlementKnowledge {
    static final int MAX_SIGHTINGS = 32;
    static final long MAX_AGE = 2_400L;
    private final Map<SubjectId, Sighting> sightings;

    HiveSettlementKnowledge(Map<SubjectId, Sighting> sightings) {
        Map<SubjectId, Sighting> copy = new LinkedHashMap<>();
        for (Map.Entry<SubjectId, Sighting> entry : Objects.requireNonNull(sightings, "hive settlement sightings").entrySet()) {
            if (!entry.getKey().equals(entry.getValue().settlementId())) {
                throw new IllegalArgumentException("hive settlement sighting key must match settlement");
            }
            if (copy.put(entry.getKey(), entry.getValue()) != null) throw new IllegalArgumentException("duplicate hive settlement sighting");
        }
        if (copy.size() > MAX_SIGHTINGS) throw new IllegalArgumentException("hive settlement sighting retention limit exceeded");
        this.sightings = Map.copyOf(copy);
    }

    static HiveSettlementKnowledge empty() { return new HiveSettlementKnowledge(Map.of()); }
    Map<SubjectId, Sighting> entries() { return sightings; }

    Optional<Sighting> freshest(long now) {
        return sightings.values().stream().filter(value -> value.observedAt() >= Math.subtractExact(now, MAX_AGE))
                .sorted(Comparator.comparingLong(Sighting::observedAt).reversed().thenComparing(Sighting::settlementId)).findFirst();
    }

    HiveSettlementKnowledge observe(Sighting sighting) {
        Map<SubjectId, Sighting> next = new LinkedHashMap<>(sightings);
        Sighting previous = next.get(sighting.settlementId());
        if (previous != null && previous.observedAt() > sighting.observedAt()) return this;
        next.put(sighting.settlementId(), sighting);
        while (next.size() > MAX_SIGHTINGS) {
            SubjectId oldest = next.values().stream().min(Comparator.comparingLong(Sighting::observedAt)
                    .thenComparing(Sighting::settlementId)).orElseThrow().settlementId();
            next.remove(oldest);
        }
        return new HiveSettlementKnowledge(next);
    }

    void validate(FrontierBootstrap bootstrap, HiveColony colony, Map<SubjectId, ActorLocation> actors) {
        for (Sighting sighting : sightings.values()) {
            boolean knownSettlement = bootstrap.settlements().stream().anyMatch(value -> value.id().equals(sighting.settlementId())
                    && value.anchor().equals(sighting.settlementAnchor()));
            Bioform scout = FrontierWorldStateSupport.bioform(bootstrap, colony, sighting.scoutId());
            if (!knownSettlement || scout.role() != BioformRole.SCOUT || actors.get(scout.id()).condition().status() != ActorLifeStatus.ALIVE) {
                throw new IllegalArgumentException("hive settlement sighting must retain one living scout and a known settlement anchor");
            }
        }
    }

    @Override public boolean equals(Object other) { return other instanceof HiveSettlementKnowledge value && sightings.equals(value.sightings); }
    @Override public int hashCode() { return sightings.hashCode(); }

    record Sighting(SubjectId settlementId, SubjectId scoutId, BlockPosition settlementAnchor, long observedAt) {
        Sighting {
            Objects.requireNonNull(settlementId, "sighted settlement");
            Objects.requireNonNull(scoutId, "settlement sighting scout");
            Objects.requireNonNull(settlementAnchor, "sighted settlement anchor");
            if (observedAt < 0L) throw new IllegalArgumentException("settlement sighting tick must be non-negative");
        }
    }
}
