package io.farfrontier.palemirror.frontier.v3.model.navigation;

import java.util.*;
import java.lang.ref.WeakReference;

/** Bounded reusable layered regions, including cooperative construction; no world/task authority. */
final class PedestrianRegionCache {
    private static final int MAX_REGIONS = 2_048, MAX_SURFACES = MAX_REGIONS * PedestrianRegion.CELLS;
    private static final int MAX_BUILDERS = 64;
    private final PedestrianBoundaryCache boundaries = new PedestrianBoundaryCache();
    private final PedestrianGoalFields goalFields = new PedestrianGoalFields();
    private long lookups, buildsStarted, evictions, certifiedPlanes, layeredGraphs;
    private long completedHits, layeredCacheMisses, newVersionMisses, recentEvictedRebuilds, weakGraphHits;
    // Four strong-cache turnovers of metadata can still name graphs held by
    // other admitted searches. Values remain weak: this adds no graph pin or
    // strong surface capacity, and retired scope cleanup severs every key.
    private static final int MAX_RECENT_EVICTIONS = MAX_REGIONS * 4;
    private record Evicted(Object bucket, Object key) { }
    // Share completed graphs still held by admitted searches after strong-cache
    // eviction. Weak references add no strong graph retention; exact keys and the
    // bounded directory keep the ordinary version/retirement boundary intact.
    private final Map<Evicted, WeakReference<PedestrianRegion>> recentEvictions = new LinkedHashMap<>();
    private final Map<PedestrianRouteGeometry, Bucket<PedestrianRegion.Tile>> views = new WeakHashMap<>();
    private record RegionalKey(PedestrianRegion.Tile tile, Object baseline, Object revision, io.farfrontier.palemirror.frontier.v3.model.WorldBounds bounds) {
        @Override public int hashCode() { return 31 * tile.hashCode() + bounds.hashCode(); }
        @Override public boolean equals(Object other) {
            return this == other || other instanceof RegionalKey value && baseline == value.baseline
                    && tile.equals(value.tile) && bounds.equals(value.bounds) && Objects.equals(revision, value.revision);
        }
    }
    // Reconstructible terrain/profile value keys must not disappear while an equal
    // current view is in use. Scope and every graph queue remain explicitly bounded.
    private final Map<Object, Bucket<RegionalKey>> scoped = new LinkedHashMap<>(16, 0.75f, true);
    // One-use surveys enter probation. Only a completed graph hit proves reuse;
    // cooperative builder polls do not. Each half can borrow unused capacity,
    // while the reused half cannot pin all graph slots against new geography.
    private final LinkedHashSet<Entry> probation = new LinkedHashSet<>(), reused = new LinkedHashSet<>(), building = new LinkedHashSet<>();
    private int slots, reusedSlots;
    private final Set<Bucket<PedestrianRegion.Tile>> unversionedBuckets = new HashSet<>();
    private static final class Entry {
        private final java.util.concurrent.locks.ReentrantLock builderLock = new java.util.concurrent.locks.ReentrantLock();
        private PedestrianRouteGeometry initialGeometry;
        private final PedestrianRegion.Tile tile;
        private final Bucket<?> bucket;
        private final Object key;
        private PedestrianRegionBuild build;
        private volatile PedestrianRegion region;
        private volatile boolean limited;
        // Accounted only under the cache lock, independently of a worker's private builder.
        private int accountedSize = PedestrianRegionBuild.MAX_SURFACES;
        private boolean accountedBuilding = true;
        private boolean countedCompletion;
        Entry(PedestrianRouteGeometry geometry, PedestrianRegion.Tile tile, Bucket<?> bucket, Object key) {
            initialGeometry = geometry; this.tile = tile; this.bucket = bucket; this.key = key;
        }
        Lookup advance(int budget, java.util.function.BooleanSupplier mayContinue) {
            if (region != null || limited) return new Lookup(region, 1, limited);
            if (!builderLock.tryLock()) return new Lookup(null, 1, false);
            try {
                if (region != null || limited) return new Lookup(region, 1, limited);
                if (build == null) { build = new PedestrianRegionBuild(initialGeometry, tile, OptionalInt.empty()); initialGeometry = null; }
                int work = build.advance(budget, mayContinue); region = build.result(); limited = build.limited();
                if (region != null || limited) build = null;
                return new Lookup(region, work, limited);
            } finally { builderLock.unlock(); }
        }
    }
    /** Key lookup and tile counts share the entry's lifetime with the bounded retention queues. */
    private static final class Bucket<K> {
        private final Map<K, Entry> entries = new LinkedHashMap<>();
        private final Map<PedestrianRegion.Tile, Integer> tiles = new HashMap<>();
        private int slots, builders;
        private volatile boolean retired;
        Entry acquire(K key, PedestrianRouteGeometry geometry, PedestrianRegion.Tile tile) {
            var entry = new Entry(geometry, tile, this, key); entries.put(key, entry);
            tiles.merge(tile, 1, Integer::sum); slots += entry.accountedSize; builders++;
            return entry;
        }
        void remove(Entry entry) {
            if (!entries.remove(entry.key, entry)) throw new IllegalStateException("region cache eviction lost its counted entry");
            slots -= entry.accountedSize; if (entry.accountedBuilding) builders--;
            int versions = tiles.get(entry.tile);
            if (versions == 1) tiles.remove(entry.tile); else tiles.put(entry.tile, versions - 1);
        }
    }
    record Lookup(PedestrianRegion region, int work, boolean limited) { }
    Lookup region(PedestrianRouteGeometry geometry, PedestrianRegion.Tile tile, int budget) {
        return region(geometry, tile, budget, () -> true);
    }
    Lookup region(PedestrianRouteGeometry geometry, PedestrianRegion.Tile tile, int budget,
            java.util.function.BooleanSupplier mayContinue) {
        if (budget <= 0) return new Lookup(null, 0, false);
        if (geometry.regionalRevision().isPresent()) {
            var revision = geometry.regionalRevision().orElseThrow();
            var regions = regionalScope(revision);
            var key = new RegionalKey(tile, revision.baseline(), revision.at(tile.x(), tile.z()), geometry.bounds());
            return advance(regions, key, geometry, tile, budget, mayContinue);
        } else {
            var regions = viewScope(geometry);
            return advance(regions, tile, geometry, tile, budget, mayContinue);
        }
    }
    PedestrianRegion.Refinement refine(PedestrianRouteGeometry geometry, PedestrianRegion region,
                                       io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor from,
                                       io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor to) {
        return region.certifiedPlane() || from.equals(to) ? region.refine(from, to)
                : region.refine(from, to, goalFields.acquire(geometry, region, to));
    }
    /** Pure warm-field lookup. No terrain reads, graph construction or queue acquisition. */
    synchronized PedestrianRegion.Refinement sharedLocal(PedestrianRouteGeometry geometry,
            io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor from,
            io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor to) {
        var tile = PedestrianRegion.Tile.at(from);
        if (!tile.equals(PedestrianRegion.Tile.at(to)) || geometry.regionalRevision().isEmpty()) return null;
        var revision = geometry.regionalRevision().orElseThrow(); var bucket = scoped.get(revision.geometryScope());
        if (bucket == null) return null;
        var entry = bucket.entries.get(new RegionalKey(tile, revision.baseline(), revision.at(tile.x(), tile.z()), geometry.bounds()));
        var region = entry == null ? null : entry.region;
        if (region == null || region.certifiedPlane() || region.component(from) < 0 || region.component(from) != region.component(to)) return null;
        var field = goalFields.completed(region, to, from);
        return field == null ? null : region.refine(from, to, field);
    }
    Map<String, Long> goalFieldDiagnostic() { return goalFields.diagnostic(); }
    private synchronized Bucket<RegionalKey> regionalScope(PedestrianGeometryRevision revision) {
        if (scoped.size() >= 64 && !scoped.containsKey(revision.geometryScope()))
            forget(scoped.remove(scoped.keySet().iterator().next()));
        return scoped.computeIfAbsent(revision.geometryScope(), ignored -> new Bucket<>());
    }
    private synchronized Bucket<PedestrianRegion.Tile> viewScope(PedestrianRouteGeometry geometry) {
        retireCollectedViews();
        if (views.size() >= 64 && !views.containsKey(geometry)) {
            for (var bucket : views.values()) forget(bucket);
            views.clear(); unversionedBuckets.clear();
        }
        var result = views.computeIfAbsent(geometry, ignored -> new Bucket<>());
        unversionedBuckets.add(result); return result;
    }
    PedestrianBoundaryCache.Lookup boundary(PedestrianRouteGeometry geometry, PedestrianRegion from, PedestrianRegion to,
            int budget, java.util.function.BooleanSupplier mayContinue) {
        var revision = geometry.regionalRevision();
        Bucket<?> scope = revision.isPresent() ? regionalScope(revision.orElseThrow()) : viewScope(geometry);
        Object origin = revision.map(value -> value.at(from.tile.x(), from.tile.z())).orElse(geometry.version());
        Object target = revision.map(value -> value.at(to.tile.x(), to.tile.z())).orElse(geometry.version());
        Object baseline = revision.map(PedestrianGeometryRevision::baseline).orElse(geometry.version());
        var key = new PedestrianBoundaryCache.Key(scope, geometry.bounds(), from.tile, to.tile, baseline, origin, target);
        return boundaries.lookup(key, geometry, from, to, budget, mayContinue, () -> !scope.retired);
    }
    private <K> Lookup advance(Bucket<K> bucket, K key, PedestrianRouteGeometry geometry, PedestrianRegion.Tile tile,
                                int budget, java.util.function.BooleanSupplier mayContinue) {
        Entry entry;
        synchronized (this) {
            lookups++; entry = bucket.entries.get(key);
            if (entry != null) { if (entry.region != null) completedHits++; touch(entry); }
            else {
                var retained = recentEvictions.get(new Evicted(bucket, key));
                var shared = retained == null ? null : retained.get();
                if (shared != null) {
                    completedHits++; weakGraphHits++;
                    return new Lookup(shared, 1, false);
                }
            }
        }
        if (entry == null) {
            var plane = PedestrianRegionBuild.uniformSupport(geometry, tile);
            if (plane.isPresent()) {
                synchronized (this) { buildsStarted++; certifiedPlanes++; }
                return new Lookup(PedestrianRegion.certifiedPlane(tile, plane.orElseThrow()), 1, false);
            }
            synchronized (this) {
                entry = bucket.entries.get(key);
                if (entry == null) {
                    buildsStarted++;
                    layeredCacheMisses++;
                    if (bucket.tiles.containsKey(tile)) newVersionMisses++;
                    if (recentEvictions.remove(new Evicted(bucket, key)) != null) recentEvictedRebuilds++;
                    if (bucket.retired) entry = new Entry(geometry, tile, bucket, key);
                    else {
                        entry = bucket.acquire(key, geometry, tile);
                        slots += entry.accountedSize; building.add(entry); trim();
                    }
                } else touch(entry);
            }
        }
        // A worker owns one builder lock, never the cache lock while surveying.
        var result = entry.advance(budget, mayContinue);
        synchronized (this) {
            if (result.region() != null && !entry.countedCompletion) {
                if (result.region().certifiedPlane()) certifiedPlanes++; else layeredGraphs++;
                entry.countedCompletion = true;
            }
            if (bucket.entries.get(key) == entry && entry.accountedBuilding && (entry.region != null || entry.limited)) {
                int size = entry.region == null ? 0 : entry.region.size();
                slots += size - entry.accountedSize; bucket.slots += size - entry.accountedSize;
                entry.accountedSize = size; entry.accountedBuilding = false; bucket.builders--;
                building.remove(entry); probation.add(entry);
            }
            trim();
        }
        return result;
    }
    private void touch(Entry entry) {
        if (entry.accountedBuilding) return;
        probation.remove(entry);
        if (!reused.remove(entry)) reusedSlots += entry.accountedSize;
        reused.add(entry);
        while (reused.size() > MAX_REGIONS / 2 || reusedSlots > MAX_SURFACES / 2) {
            var oldest = reused.getFirst(); reused.remove(oldest);
            reusedSlots -= oldest.accountedSize; probation.add(oldest);
        }
    }
    private void trim() {
        while (probation.size() + reused.size() + building.size() > MAX_REGIONS || slots > MAX_SURFACES || building.size() > MAX_BUILDERS) {
            // Completed probation is the first victim. Unfinished work has its
            // own smaller ceiling and can only be displaced by builder pressure.
            var victim = building.size() > MAX_BUILDERS ? building.getFirst()
                    : !probation.isEmpty() ? probation.getFirst() : reused.getFirst();
            evict(victim);
        }
    }
    private void evict(Entry entry) {
        var history = new Evicted(entry.bucket, entry.key);
        if (!recentEvictions.containsKey(history) && recentEvictions.size() >= MAX_RECENT_EVICTIONS)
            recentEvictions.remove(recentEvictions.keySet().iterator().next());
        recentEvictions.put(history, new WeakReference<>(entry.region));
        entry.bucket.remove(entry); slots -= entry.accountedSize;
        probation.remove(entry); building.remove(entry);
        if (reused.remove(entry)) reusedSlots -= entry.accountedSize;
        evictions++;
    }
    private void forget(Bucket<?> bucket) {
        bucket.retired = true; boundaries.forget(bucket);
        for (var entry : new ArrayList<>(bucket.entries.values())) evict(entry);
        recentEvictions.keySet().removeIf(history -> history.bucket() == bucket);
    }
    private void retireCollectedViews() {
        var live = new HashSet<>(views.values());
        // At most 64 view buckets, never a scan of retained graph entries.
        var iterator = unversionedBuckets.iterator();
        while (iterator.hasNext()) {
            var bucket = iterator.next();
            if (!live.contains(bucket)) { forget(bucket); iterator.remove(); }
        }
    }
    synchronized PedestrianRegionCacheDiagnostic diagnostic() {
        long entries = 0, distinct = 0, builders = 0;
        for (var bucket : scoped.values()) {
            entries += bucket.entries.size(); distinct += bucket.tiles.size(); builders += bucket.builders;
        }
        for (var bucket : views.values()) {
            entries += bucket.entries.size(); distinct += bucket.tiles.size(); builders += bucket.builders;
        }
        return new PedestrianRegionCacheDiagnostic(lookups, buildsStarted, evictions, certifiedPlanes, layeredGraphs,
                scoped.size() + views.size(), entries, distinct, builders,
                completedHits, layeredCacheMisses, newVersionMisses, recentEvictedRebuilds, weakGraphHits, boundaries.diagnostic());
    }
    /** Bounded scope counters; neither ordinary diagnostics nor inspection scan regional graphs. */
    synchronized Map<String, Long> retentionDiagnostic() {
        var counts = diagnostic(); long reserved = 0;
        for (var bucket : scoped.values()) reserved += bucket.slots;
        for (var bucket : views.values()) reserved += bucket.slots;
        return Map.of("scopes", counts.retainedScopes(), "entries", counts.retainedEntries(),
                "distinctScopedTiles", counts.distinctScopedTiles(), "reservedSurfaceSlots", reserved, "builders", counts.retainedBuilders(),
                "weakEntries", (long) recentEvictions.size());
    }
    synchronized void clear() {
        for (var bucket : scoped.values()) forget(bucket);
        for (var bucket : unversionedBuckets) forget(bucket);
        views.clear(); unversionedBuckets.clear(); scoped.clear(); probation.clear(); reused.clear(); building.clear(); slots = reusedSlots = 0;
        boundaries.clear(); goalFields.clear();
        lookups = buildsStarted = evictions = certifiedPlanes = layeredGraphs = 0;
        completedHits = layeredCacheMisses = newVersionMisses = recentEvictedRebuilds = weakGraphHits = 0; recentEvictions.clear();
    }
}
