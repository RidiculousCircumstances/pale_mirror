package io.farfrontier.palemirror.frontier.v3.model.navigation;

import java.util.*;

/** Bounded reusable layered regions, including cooperative construction; no world/task authority. */
final class PedestrianRegionCache {
    private static final int MAX_REGIONS = 2_048, MAX_SURFACES = MAX_REGIONS * PedestrianRegion.CELLS;
    private static final int MAX_BUILDERS = 64;
    private long lookups, buildsStarted, evictions, certifiedPlanes, layeredGraphs;
    private final Map<PedestrianRouteGeometry, Bucket<PedestrianRegion.Tile>> views = new WeakHashMap<>();
    private record RegionalKey(PedestrianRegion.Tile tile, Object revision, io.farfrontier.palemirror.frontier.v3.model.WorldBounds bounds) { }
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
        private boolean retired;
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
            Bucket<RegionalKey> regions;
            synchronized (this) {
                if (scoped.size() >= 64 && !scoped.containsKey(revision.geometryScope()))
                    forget(scoped.remove(scoped.keySet().iterator().next()));
                regions = scoped.computeIfAbsent(revision.geometryScope(), ignored -> new Bucket<>());
            }
            var key = new RegionalKey(tile, revision.at(tile.x(), tile.z()), geometry.bounds());
            return advance(regions, key, geometry, tile, budget, mayContinue);
        } else {
            Bucket<PedestrianRegion.Tile> regions;
            synchronized (this) {
                // Weak view keys may disappear without a map mutation. Detach their
                // graph queues too, so weak-key cleanup cannot retain dead worlds.
                retireCollectedViews();
                if (views.size() >= 64 && !views.containsKey(geometry)) {
                    for (var bucket : views.values()) forget(bucket);
                    views.clear(); unversionedBuckets.clear();
                }
                regions = views.computeIfAbsent(geometry, ignored -> new Bucket<>());
                unversionedBuckets.add(regions);
            }
            return advance(regions, tile, geometry, tile, budget, mayContinue);
        }
    }
    private <K> Lookup advance(Bucket<K> bucket, K key, PedestrianRouteGeometry geometry, PedestrianRegion.Tile tile,
                                int budget, java.util.function.BooleanSupplier mayContinue) {
        Entry entry;
        synchronized (this) { lookups++; entry = bucket.entries.get(key); if (entry != null) touch(entry); }
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
        entry.bucket.remove(entry); slots -= entry.accountedSize;
        probation.remove(entry); building.remove(entry);
        if (reused.remove(entry)) reusedSlots -= entry.accountedSize;
        evictions++;
    }
    private void forget(Bucket<?> bucket) {
        bucket.retired = true;
        for (var entry : new ArrayList<>(bucket.entries.values())) evict(entry);
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
                scoped.size() + views.size(), entries, distinct, builders);
    }
    /** Bounded scope counters; neither ordinary diagnostics nor inspection scan regional graphs. */
    synchronized Map<String, Long> retentionDiagnostic() {
        var counts = diagnostic(); long reserved = 0;
        for (var bucket : scoped.values()) reserved += bucket.slots;
        for (var bucket : views.values()) reserved += bucket.slots;
        return Map.of("scopes", counts.retainedScopes(), "entries", counts.retainedEntries(),
                "distinctScopedTiles", counts.distinctScopedTiles(), "reservedSurfaceSlots", reserved, "builders", counts.retainedBuilders());
    }
    synchronized void clear() {
        for (var bucket : scoped.values()) forget(bucket);
        for (var bucket : unversionedBuckets) forget(bucket);
        views.clear(); unversionedBuckets.clear(); scoped.clear(); probation.clear(); reused.clear(); building.clear(); slots = reusedSlots = 0;
        lookups = buildsStarted = evictions = certifiedPlanes = layeredGraphs = 0;
    }
}
