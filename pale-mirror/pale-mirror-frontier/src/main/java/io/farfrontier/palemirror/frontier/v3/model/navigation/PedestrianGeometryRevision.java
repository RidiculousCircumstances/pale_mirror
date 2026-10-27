package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.geometry.ImmutableRegionMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable derived regional knowledge, never task/arrival/custody authority. */
public final class PedestrianGeometryRevision {
    private final Object scope, geometryScope, baseline;
    private final ImmutableRegionMap<Object> revisions;
    private final int hash;
    private record ProfileSeed(ImmutableRegionMap<Object> terrain, ImmutableRegionMap<Object> envelope) { }
    private final Map<UnitNavigationProfile, ProfileSeed> profileSeeds;
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
        this(scope, geometryScope, baseline, revisions, Map.of());
    }
    private PedestrianGeometryRevision(Object scope, Object geometryScope, Object baseline,
            Map<Region, Object> revisions, Map<UnitNavigationProfile, ProfileSeed> profileSeeds) {
        this.profileSeeds = profileSeeds;
        this.scope = Objects.requireNonNull(scope); this.geometryScope = Objects.requireNonNull(geometryScope);
        this.baseline = Objects.requireNonNull(baseline);
        if (revisions.size() > MAX_REGIONS) throw new IllegalArgumentException("regional knowledge exceeds its safety bound");
        // Reuse immutable spatial pages and their exact hashes between captured
        // views; only changed pages rebuild facts on the preparation lane.
        this.revisions = ImmutableRegionMap.copyOf(revisions);
        // Hash immutable facts once, not for every queue/map lookup of a route request.
        // Equality still checks the complete facts: a hash is never route authority.
        this.hash = Objects.hash(scope, geometryScope, baseline, this.revisions);
    }
    /** The immutable terrain owner shares its body-envelope revision. This is a
     * bounded rebuildable projection; neither live positions nor access enter it. */
    public PedestrianGeometryRevision profiled(UnitNavigationProfile profile) {
        Objects.requireNonNull(profile);
        synchronized (profileViews) { var prior = profileViews.get(profile); if (prior != null) return prior; }
        ProfileSeed seed;
        synchronized (profileViews) { seed = profileSeeds.get(profile); }
        int margin = profile.footprintRadius() == 0 ? 0 : 1;
        ImmutableRegionMap<Object> expanded;
        if (margin == 0) expanded = revisions;
        else {
            var affected = new java.util.HashSet<Region>();
            var changed = seed == null ? revisions.keySet() : revisions.changesFrom(seed.terrain());
            for (var region : changed) for (int x = -margin; x <= margin; x++) for (int z = -margin; z <= margin; z++)
                affected.add(new Region(region.x() + x, region.z() + z));
            var updates = new java.util.HashMap<Region, java.util.Optional<Object>>();
            for (var target : affected) {
                Object token = null;
                // Match the full projection's lexicographic source order exactly.
                for (int x = -margin; x <= margin; x++) for (int z = -margin; z <= margin; z++) {
                    var source = revisions.get(new Region(target.x() + x, target.z() + z));
                    if (source != null) token = token == null ? source : java.util.List.of(token, source);
                }
                updates.put(target, java.util.Optional.ofNullable(token));
            }
            expanded = (seed == null ? ImmutableRegionMap.<Object>copyOf(Map.of()) : seed.envelope()).with(updates);
        }
        var result = new PedestrianGeometryRevision(new ProfileScope(scope, profile),
                new ProfileScope(geometryScope, profile), baseline, expanded);
        synchronized (profileViews) {
            var prior = profileViews.get(profile); if (prior != null) return prior;
            if (profileViews.size() >= MAX_PROFILE_VIEWS) profileViews.remove(profileViews.keySet().iterator().next());
            profileViews.put(profile, result); return result;
        }
    }
    /** Successor facts share unchanged pages and bounded profile seeds, never a chain
     * of predecessor revisions or their task/cache state. Scope ownership is unchanged. */
    public PedestrianGeometryRevision withRevisions(Map<Region, Object> facts) {
        var next = ImmutableRegionMap.copyOf(facts);
        if (revisions.equals(next)) return this;
        synchronized (profileViews) {
            var seeds = new java.util.LinkedHashMap<>(profileSeeds);
            profileViews.forEach((profile, view) -> seeds.put(profile, new ProfileSeed(revisions, view.revisions)));
            while (seeds.size() > MAX_PROFILE_VIEWS) seeds.remove(seeds.keySet().iterator().next());
            return new PedestrianGeometryRevision(scope, geometryScope, baseline, next,
                    java.util.Collections.unmodifiableMap(seeds));
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
        var changes = new java.util.HashMap<Region, java.util.Optional<Object>>();
        Object revision = new Object(); regions.forEach(region -> changes.put(region, java.util.Optional.of(revision)));
        return withRevisions(revisions.with(changes));
    }
    public Set<Region> changesSince(PedestrianGeometryRevision prior) {
        if (this == prior) return Set.of();
        if (!scope.equals(prior.scope) || !geometryScope.equals(prior.geometryScope) || baseline != prior.baseline)
            throw new IllegalArgumentException("regional comparison needs the same declared knowledge scope");
        var regions = new java.util.HashSet<>(revisions.changesFrom(prior.revisions));
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
