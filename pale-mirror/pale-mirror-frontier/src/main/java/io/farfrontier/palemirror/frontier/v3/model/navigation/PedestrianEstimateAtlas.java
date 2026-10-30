package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.ImmutableLookupMap;
import java.util.*;

/** Bounded reusable positive distance facts, independent of result-delivery ownership and market decisions. */
final class PedestrianEstimateAtlas {
    private static final int MAX_ESTIMATES = 2_048;
    private static final long MAX_UNITS = 131_072;
    private Map<PedestrianDemandKey, PedestrianCalculationMemory.Read> facts = Map.of();
    private final LinkedHashSet<PedestrianDemandKey> order = new LinkedHashSet<>();
    private long units, hits, retained;
    Optional<PedestrianCalculationMemory.Read> find(PedestrianRouteRequest request) {
        var found = find(facts, request); if (found.isPresent()) hits++; return found;
    }
    static Optional<PedestrianCalculationMemory.Read> find(Map<PedestrianDemandKey, PedestrianCalculationMemory.Read> facts,
                                                          PedestrianRouteRequest request) {
        if (!request.extent().estimateOnly()) return Optional.empty();
        return Optional.ofNullable(facts.get(PedestrianDemandKey.of(request))).filter(read -> read.matches(request));
    }
    void remember(PedestrianRouteRequest request, Optional<PedestrianCalculationMemory.Read> prepared) {
        if (!request.extent().estimateOnly() || prepared.isEmpty()) return;
        var read = prepared.orElseThrow();
        if (read.result().status() != PedestrianRouteResult.Status.ESTIMATED || read.retainedUnits() > MAX_UNITS) return;
        var key = PedestrianDemandKey.of(request); var prior = facts.get(key);
        if (prior != null) { units -= prior.retainedUnits(); facts = ImmutableLookupMap.withoutEntry(facts, key); order.remove(key); }
        while (!order.isEmpty() && (facts.size() >= MAX_ESTIMATES || units + read.retainedUnits() > MAX_UNITS)) {
            var oldest = order.removeFirst(); units -= facts.get(oldest).retainedUnits(); facts = ImmutableLookupMap.withoutEntry(facts, oldest);
        }
        facts = ImmutableLookupMap.withEntry(facts, key, read); order.addLast(key); units += read.retainedUnits(); retained++;
    }
    Map<PedestrianDemandKey, PedestrianCalculationMemory.Read> snapshot() { return facts; }
    Map<String, Long> diagnostic() { return Map.of("hits", hits, "publications", retained, "estimates", (long) facts.size(), "units", units); }
    void clear() { facts = Map.of(); order.clear(); units = hits = retained = 0; }
}
