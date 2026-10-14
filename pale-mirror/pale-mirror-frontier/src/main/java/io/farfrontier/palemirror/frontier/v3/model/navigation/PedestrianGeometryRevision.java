package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable derived regional knowledge, never task/arrival/custody authority. */
public record PedestrianGeometryRevision(Object scope, Object geometryScope, Object baseline, Map<Region, Object> revisions) {
    public static final int MAX_REGIONS = 16_384;
    public record Region(int x, int z) {
        public static Region at(BlockPosition position) {
            return new Region(Math.floorDiv(position.x(), 16), Math.floorDiv(position.z(), 16));
        }
    }
    public PedestrianGeometryRevision {
        Objects.requireNonNull(scope); Objects.requireNonNull(geometryScope); Objects.requireNonNull(baseline);
        revisions = Map.copyOf(revisions);
        if (revisions.size() > MAX_REGIONS) throw new IllegalArgumentException("regional knowledge exceeds its safety bound");
    }
    /** Conservative providers share only inside their exact query scope. A producer
     * may explicitly declare a wider geometry scope when its regional tokens include
     * every local support, obstacle and permission affecting traversal. This grants
     * graph reuse, never another caller's access, query result or execution authority. */
    public PedestrianGeometryRevision(Object scope, Object baseline, Map<Region, Object> revisions) {
        this(scope, scope, baseline, revisions);
    }
    public static PedestrianGeometryRevision initial() {
        return initial(new Object());
    }
    public static PedestrianGeometryRevision initial(Object scope) { return new PedestrianGeometryRevision(scope, new Object(), Map.of()); }
    public Object at(int regionX, int regionZ) { return revisions.getOrDefault(new Region(regionX, regionZ), baseline); }
    public PedestrianGeometryRevision changed(Set<Region> regions) {
        if (regions.isEmpty()) return this;
        var next = new java.util.LinkedHashMap<>(revisions);
        Object revision = new Object(); regions.forEach(region -> next.put(region, revision));
        return new PedestrianGeometryRevision(scope, geometryScope, baseline, next);
    }
    public Set<Region> changesSince(PedestrianGeometryRevision prior) {
        if (this == prior) return Set.of();
        if (!scope.equals(prior.scope) || !geometryScope.equals(prior.geometryScope) || baseline != prior.baseline)
            throw new IllegalArgumentException("regional comparison needs the same declared knowledge scope");
        var regions = new java.util.HashSet<>(revisions.keySet()); regions.addAll(prior.revisions.keySet());
        // Profile envelopes combine several immutable terrain tokens. Rebuilding
        // an equal tuple is not a changed region; plain Object epochs still use
        // their identity equality. This is calculation evidence, not actor custody.
        regions.removeIf(region -> Objects.equals(at(region.x(), region.z()), prior.at(region.x(), region.z())));
        return Set.copyOf(regions);
    }
    /** A bounded derived consumer may retain its value when all queried regional facts match. */
    public boolean matches(PedestrianGeometryRevision prior, Set<Region> dependencies) {
        if (this == prior) return true;
        if (!scope.equals(prior.scope) || !geometryScope.equals(prior.geometryScope) || baseline != prior.baseline) return false;
        return dependencies.stream().allMatch(region -> Objects.equals(at(region.x(), region.z()), prior.at(region.x(), region.z())));
    }
}
