package io.farfrontier.palemirror.visuals.runtime;

import io.farfrontier.palemirror.api.VisualStateProjection;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bounded latest-value cache for read-only canonical projections. Delivery is
 * independent of chunk availability: a later loaded tick reconciles the same
 * desired projection without asking Core to publish a new revision.
 */
final class VisualProjectionLedger {
    private static final int MAX_PROJECTIONS_PER_DIMENSION = 64;
    private final Map<String, Map<String, VisualStateProjection>> byDimension = new LinkedHashMap<>();

    synchronized void accept(String dimensionId, VisualStateProjection projection) {
        Map<String, VisualStateProjection> values = byDimension.computeIfAbsent(
                dimensionId, ignored -> new LinkedHashMap<>());
        if (!values.containsKey(projection.objectId()) && values.size() >= MAX_PROJECTIONS_PER_DIMENSION) {
            throw new IllegalStateException("Visual projection limit exceeded in " + dimensionId);
        }
        VisualStateProjection previous = values.get(projection.objectId());
        if (previous != null && projection.projectionRevision() == previous.projectionRevision()) {
            if (!previous.equals(projection)) {
                throw new IllegalStateException("Conflicting visual projection revision for "
                        + projection.objectId());
            }
            return;
        }
        values.put(projection.objectId(), projection);
    }

    synchronized Collection<VisualStateProjection> projections(String dimensionId) {
        Map<String, VisualStateProjection> values = byDimension.get(dimensionId);
        return values == null ? java.util.List.of() : java.util.List.copyOf(values.values());
    }

    synchronized void clear() { byDimension.clear(); }
}
