package io.farfrontier.palemirror.visuals.runtime;

import io.farfrontier.palemirror.api.AuthoredRegionSeed;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Bounded process cache rebuilt from persisted genesis markers as chunks load. */
public final class AuthoredRegionMarkerIndex {
    private static final int MAX_MARKERS = 64;
    private final Map<String, Map<String, AuthoredRegionSeed>> byDimension = new ConcurrentHashMap<>();

    public void observe(AuthoredRegionSeed seed) {
        Map<String, AuthoredRegionSeed> values = byDimension.computeIfAbsent(seed.dimensionId(), key ->
                java.util.Collections.synchronizedMap(new LinkedHashMap<>()));
        synchronized (values) {
            if (!values.containsKey(seed.planId()) && values.size() >= MAX_MARKERS) {
                throw new IllegalStateException("Authored region marker limit exceeded in " + seed.dimensionId());
            }
            AuthoredRegionSeed prior = values.putIfAbsent(seed.planId(), seed);
            if (prior != null && !prior.contentHash().equals(seed.contentHash())) {
                throw new IllegalStateException("Conflicting authored region marker " + seed.planId());
            }
        }
    }

    public Collection<AuthoredRegionSeed> discovered(String dimensionId) {
        Map<String, AuthoredRegionSeed> values = byDimension.get(dimensionId);
        if (values == null) return ListHolder.EMPTY;
        synchronized (values) { return ListHolder.copy(values.values()); }
    }

    public void clear() { byDimension.clear(); }

    private static final class ListHolder {
        private static final Collection<AuthoredRegionSeed> EMPTY = java.util.List.of();
        private static Collection<AuthoredRegionSeed> copy(Collection<AuthoredRegionSeed> values) {
            return java.util.List.copyOf(values);
        }
    }
}
