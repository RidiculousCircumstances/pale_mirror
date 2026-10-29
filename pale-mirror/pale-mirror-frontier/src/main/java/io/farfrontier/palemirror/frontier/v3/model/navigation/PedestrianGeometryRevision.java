package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.geometry.ImmutableRegionMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable derived regional knowledge, never task/arrival/custody authority. */
public final class PedestrianGeometryRevision {
    private final Object scope, geometryScope, baseline;
    private volatile ImmutableRegionMap<Object> revisions;
    private volatile Integer hash;
    private record ProfileExpansion(ImmutableRegionMap<Object> terrain, ProfileSeed seed) { }
    private final ProfileExpansion expansion;
    private static final Object ABSENT = new Object();
    private volatile io.farfrontier.palemirror.frontier.v3.model.BoundedLongMemo<Object> expandedReads;
    private io.farfrontier.palemirror.frontier.v3.model.BoundedLongMemo<Object> expansionReads() {
        var ready = expandedReads;
        if (ready != null) return ready;
        synchronized (this) {
            if (expandedReads == null) expandedReads = new io.farfrontier.palemirror.frontier.v3.model.BoundedLongMemo<>(256);
            return expandedReads;
        }
    }
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
        this.profileSeeds = profileSeeds; this.expansion = null;
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
        var source = materialized();
        var result = profile.footprintRadius() == 0
                ? new PedestrianGeometryRevision(new ProfileScope(scope, profile),
                        new ProfileScope(geometryScope, profile), baseline, source)
                : new PedestrianGeometryRevision(new ProfileScope(scope, profile),
                        new ProfileScope(geometryScope, profile), baseline, new ProfileExpansion(source, seed));
        synchronized (profileViews) {
            var prior = profileViews.get(profile); if (prior != null) return prior;
            if (profileViews.size() >= MAX_PROFILE_VIEWS) profileViews.remove(profileViews.keySet().iterator().next());
            profileViews.put(profile, result); return result;
        }
    }
    private PedestrianGeometryRevision(Object scope, Object geometryScope, Object baseline, ProfileExpansion expansion) {
        this.scope = scope; this.geometryScope = geometryScope; this.baseline = baseline;
        this.expansion = expansion; this.profileSeeds = Map.of();
    }
    /** Exact neighbour tuple, in the same lexical order as the complete projection.
     * A local read does not allocate, hash or retain remote expanded regions. */
    private static Object expandedToken(ImmutableRegionMap<Object> terrain, int regionX, int regionZ) {
        Object token = null;
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            var source = terrain.get(regionX + x, regionZ + z);
            if (source != null) token = token == null ? source : java.util.List.of(token, source);
        }
        return token;
    }
    private Object token(int x, int z) {
        var current = revisions;
        if (current != null) return current.get(x, z);
        // Complete local neighbour tuples belong to this immutable terrain
        // capture. Absence is reusable knowledge too, never a traversable cell.
        var reads = expansionReads(); long key = ((long) x << 32) | (z & 0xffffffffL);
        var retained = reads.get(key);
        if (retained != null) return retained == ABSENT ? null : retained;
        var value = expandedToken(expansion.terrain(), x, z);
        reads.put(key, value == null ? ABSENT : value);
        return value;
    }
    /** Complete map/hash requests remain exact and bounded. Normal route proofs
     * use at/restrictedTo and never request this whole-world representation. */
    private ImmutableRegionMap<Object> materialized() {
        var current = revisions;
        if (current != null) return current;
        synchronized (this) {
            if (revisions != null) return revisions;
            var terrain = expansion.terrain(); var seed = expansion.seed();
            var affected = new java.util.HashSet<Region>();
            var changed = seed == null ? terrain.keySet() : terrain.changesFrom(seed.terrain());
            for (var region : changed) for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
                affected.add(new Region(region.x() + x, region.z() + z));
            var updates = new java.util.HashMap<Region, java.util.Optional<Object>>();
            for (var target : affected) updates.put(target, java.util.Optional.ofNullable(expandedToken(terrain, target.x(), target.z())));
            var complete = (seed == null ? ImmutableRegionMap.<Object>copyOf(Map.of()) : seed.envelope()).with(updates);
            if (complete.size() > MAX_REGIONS) throw new IllegalArgumentException("regional knowledge exceeds its safety bound");
            revisions = complete; expandedReads = null; return complete;
        }
    }
    /** Successor facts share unchanged pages and bounded profile seeds, never a chain
     * of predecessor revisions or their task/cache state. Scope ownership is unchanged. */
    public PedestrianGeometryRevision withRevisions(Map<Region, Object> facts) {
        var next = ImmutableRegionMap.copyOf(facts);
        var current = materialized();
        if (current.equals(next)) return this;
        synchronized (profileViews) {
            var seeds = new java.util.LinkedHashMap<>(profileSeeds);
            profileViews.forEach((profile, view) -> {
                // Do not force an unused complete envelope merely to retain a seed.
                var complete = view.revisions;
                if (complete != null) seeds.put(profile, new ProfileSeed(current, complete));
            });
            while (seeds.size() > MAX_PROFILE_VIEWS) seeds.remove(seeds.keySet().iterator().next());
            return new PedestrianGeometryRevision(scope, geometryScope, baseline, next,
                    java.util.Collections.unmodifiableMap(seeds));
        }
    }
    public Object scope() { return scope; }
    public Object geometryScope() { return geometryScope; }
    public Object baseline() { return baseline; }
    public Map<Region, Object> revisions() { return materialized(); }
    @Override public int hashCode() {
        var current = hash;
        if (current == null) { current = Objects.hash(scope, geometryScope, baseline, materialized()); hash = current; }
        return current;
    }
    @Override public boolean equals(Object other) {
        return this == other || other instanceof PedestrianGeometryRevision value
                && scope.equals(value.scope) && geometryScope.equals(value.geometryScope)
                && baseline.equals(value.baseline) && hashCode() == value.hashCode()
                && materialized().equals(value.materialized());
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
    public Object at(int regionX, int regionZ) {
        var value = token(regionX, regionZ); return value == null ? baseline : value;
    }
    /** A completed local reader retains only the facts it consulted, not every
     * remote region and its historical block sets. No parent snapshot is kept. */
    public PedestrianGeometryRevision restrictedTo(Set<Region> dependencies) {
        var local = new java.util.HashMap<Region, Object>();
        for (var region : dependencies) {
            var value = token(region.x(), region.z()); if (value != null) local.put(region, value);
        }
        return new PedestrianGeometryRevision(scope, geometryScope, baseline, local);
    }
    public PedestrianGeometryRevision changed(Set<Region> regions) {
        if (regions.isEmpty()) return this;
        var changes = new java.util.HashMap<Region, java.util.Optional<Object>>();
        Object revision = new Object(); regions.forEach(region -> changes.put(region, java.util.Optional.of(revision)));
        return withRevisions(materialized().with(changes));
    }
    public Set<Region> changesSince(PedestrianGeometryRevision prior) {
        if (this == prior) return Set.of();
        if (!scope.equals(prior.scope) || !geometryScope.equals(prior.geometryScope) || baseline != prior.baseline)
            throw new IllegalArgumentException("regional comparison needs the same declared knowledge scope");
        var regions = new java.util.HashSet<Region>();
        if (expansion != null && prior.expansion != null) {
            for (var region : expansion.terrain().changesFrom(prior.expansion.terrain()))
                for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
                    regions.add(new Region(region.x() + x, region.z() + z));
        } else regions.addAll(materialized().changesFrom(prior.materialized()));
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
