package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable derived regional knowledge, never task/arrival/custody authority. */
public final class PedestrianGeometryRevision {
    private final Object scope, geometryScope, baseline;
    private final Map<Region, Object> revisions;
    private final int hash;
    private record ProfileScope(Object terrain, UnitNavigationProfile profile) { }
    private static final int MAX_PROFILE_VIEWS = 8;
    private final Map<UnitNavigationProfile, PedestrianGeometryRevision> profileViews = new java.util.LinkedHashMap<>(8, .75f, true);
    public static final int MAX_REGIONS = 16_384;
    public record Region(int x, int z) {
        public static Region at(BlockPosition position) {
            return new Region(Math.floorDiv(position.x(), 16), Math.floorDiv(position.z(), 16));
        }
    }
    public PedestrianGeometryRevision(Object scope, Object geometryScope, Object baseline, Map<Region, Object> revisions) {
        this.scope = Objects.requireNonNull(scope); this.geometryScope = Objects.requireNonNull(geometryScope);
        this.baseline = Objects.requireNonNull(baseline); this.revisions = Map.copyOf(revisions);
        if (revisions.size() > MAX_REGIONS) throw new IllegalArgumentException("regional knowledge exceeds its safety bound");
        // Hash immutable facts once, not for every queue/map lookup of a route request.
        // Equality still checks the complete facts: a hash is never route authority.
        this.hash = Objects.hash(scope, geometryScope, baseline, this.revisions);
    }
    /** The immutable terrain owner shares its body-envelope revision. This is a
     * bounded rebuildable projection; neither live positions nor access enter it. */
    public PedestrianGeometryRevision profiled(UnitNavigationProfile profile) {
        Objects.requireNonNull(profile);
        synchronized (profileViews) { var prior = profileViews.get(profile); if (prior != null) return prior; }
        var expanded = new java.util.LinkedHashMap<Region, Object>();
        int margin = profile.footprintRadius() == 0 ? 0 : 1;
        revisions.entrySet().stream().sorted(java.util.Comparator
                .comparingInt((Map.Entry<Region, Object> entry) -> entry.getKey().x())
                .thenComparingInt(entry -> entry.getKey().z())).forEach(entry -> {
            var region = entry.getKey(); var token = entry.getValue();
            for (int x = -margin; x <= margin; x++) for (int z = -margin; z <= margin; z++)
                expanded.merge(new Region(region.x() + x, region.z() + z), token,
                        (left, right) -> java.util.List.of(left, right));
        });
        var result = new PedestrianGeometryRevision(new ProfileScope(scope, profile),
                new ProfileScope(geometryScope, profile), baseline, expanded);
        synchronized (profileViews) {
            var prior = profileViews.get(profile); if (prior != null) return prior;
            if (profileViews.size() >= MAX_PROFILE_VIEWS) profileViews.remove(profileViews.keySet().iterator().next());
            profileViews.put(profile, result); return result;
        }
    }
    public Object scope() { return scope; }
    public Object geometryScope() { return geometryScope; }
    public Object baseline() { return baseline; }
    public Map<Region, Object> revisions() { return revisions; }
    @Override public int hashCode() { return hash; }
    @Override public boolean equals(Object other) {
        return this == other || other instanceof PedestrianGeometryRevision value && hash == value.hash
                && scope.equals(value.scope) && geometryScope.equals(value.geometryScope)
                && baseline.equals(value.baseline) && revisions.equals(value.revisions);
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
    /** A completed local reader retains only the facts it consulted, not every
     * remote region and its historical block sets. No parent snapshot is kept. */
    public PedestrianGeometryRevision restrictedTo(Set<Region> dependencies) {
        var local = new java.util.HashMap<Region, Object>();
        for (var region : dependencies) if (revisions.containsKey(region)) local.put(region, revisions.get(region));
        return new PedestrianGeometryRevision(scope, geometryScope, baseline, local);
    }
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
