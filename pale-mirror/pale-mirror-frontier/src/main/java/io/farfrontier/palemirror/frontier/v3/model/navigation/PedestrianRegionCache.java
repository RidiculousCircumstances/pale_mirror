package io.farfrontier.palemirror.frontier.v3.model.navigation;

import java.util.*;

/** Bounded reusable layered regions, including cooperative construction; no world/task authority. */
final class PedestrianRegionCache {
    private static final int MAX_REGIONS = 2_048, MAX_SURFACES = MAX_REGIONS * PedestrianRegion.CELLS;
    private static final int MAX_BUILDERS = 64;
    private long lookups, buildsStarted, evictions, certifiedPlanes, layeredGraphs;
    private final Map<PedestrianRouteGeometry, Bucket<PedestrianRegion.Tile>> views = new WeakHashMap<>();
    private record RegionalKey(PedestrianRegion.Tile tile, Object revision, io.farfrontier.palemirror.frontier.v3.model.WorldBounds bounds) { }
    // These are reconstructible value keys (terrain epoch + body profile), not
    // the lifetime of a geometry object. A weak key can disappear while an equal
    // current view is still in use, discarding all reusable regional graphs.
    // Keep the small declared scope set strongly and evict by actual use; graph
    // contents remain bounded below and revision/bounds still fence every hit.
    private final Map<Object, Bucket<RegionalKey>> scoped = new LinkedHashMap<>(16, 0.75f, true);
    private static final class Entry {
        private final java.util.concurrent.locks.ReentrantLock builderLock = new java.util.concurrent.locks.ReentrantLock();
        private PedestrianRouteGeometry initialGeometry;
        private final PedestrianRegion.Tile tile;
        private PedestrianRegionBuild build;
        private volatile PedestrianRegion region;
        private volatile boolean limited;
        // Accounted only under the cache lock, independently of a worker's private builder.
        private int accountedSize = PedestrianRegionBuild.MAX_SURFACES;
        private boolean accountedBuilding = true;
        private boolean countedCompletion;
        Entry(PedestrianRouteGeometry geometry, PedestrianRegion.Tile tile) { initialGeometry = geometry; this.tile = tile; }
        Lookup advance(int budget, java.util.function.BooleanSupplier mayContinue) {
            if (region != null || limited) return new Lookup(region, 1, limited);
            if (!builderLock.tryLock()) return new Lookup(null, 1, false);
            try {
                if (region != null || limited) return new Lookup(region, 1, limited);
                // The immutable negative certificate was checked before admission.
                if (build == null) { build = new PedestrianRegionBuild(initialGeometry, tile, OptionalInt.empty()); initialGeometry = null; }
                int work = build.advance(budget, mayContinue); region = build.result(); limited = build.limited();
                if (region != null || limited) build = null;
                return new Lookup(region, work, limited);
            } finally { builderLock.unlock(); }
        }
    }
    /** Maintain counts when entries change; a cache hit must not scan every retained region. */
    private static final class Bucket<K> {
        private final Map<K, Entry> entries = new LinkedHashMap<>(16, 0.75f, true);
        private int slots, builders;
        Entry acquire(K key, PedestrianRouteGeometry geometry, PedestrianRegion.Tile tile) {
            var entry = entries.get(key);
            if (entry == null) {
                entry = new Entry(geometry, tile); entries.put(key, entry);
                slots += entry.accountedSize; builders++;
            }
            return entry;
        }
        void reconcile(K key, Entry entry) {
            if (entries.get(key) != entry || !entry.accountedBuilding) return;
            var region = entry.region;
            if (region == null && !entry.limited) return;
            int size = region == null ? 0 : region.size();
            slots += size - entry.accountedSize; entry.accountedSize = size;
            entry.accountedBuilding = false; builders--;
        }
        void evict(boolean building) {
            var iterator = entries.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next().getValue();
                if (entry.accountedBuilding != building) continue;
                slots -= entry.accountedSize; if (entry.accountedBuilding) builders--;
                iterator.remove(); return;
            }
            throw new IllegalStateException("region cache eviction lost its counted entry");
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
                if (scoped.size() >= 64 && !scoped.containsKey(revision.geometryScope())) {
                    evictions += scoped.remove(scoped.keySet().iterator().next()).entries.size();
                }
                regions = scoped.computeIfAbsent(revision.geometryScope(), ignored -> new Bucket<>());
            }
            var key = new RegionalKey(tile, revision.at(tile.x(), tile.z()), geometry.bounds());
            return advance(regions, key, geometry, tile, budget, mayContinue);
        } else {
            Bucket<PedestrianRegion.Tile> regions;
            synchronized (this) {
                if (views.size() >= 64 && !views.containsKey(geometry)) {
                    evictions += views.values().stream().mapToInt(value -> value.entries.size()).sum(); views.clear();
                }
                regions = views.computeIfAbsent(geometry, ignored -> new Bucket<>());
            }
            return advance(regions, tile, geometry, tile, budget, mayContinue);
        }
    }
    private <K> Lookup advance(Bucket<K> bucket, K key, PedestrianRouteGeometry geometry, PedestrianRegion.Tile tile,
                                int budget, java.util.function.BooleanSupplier mayContinue) {
        Entry entry;
        synchronized (this) { lookups++; entry = bucket.entries.get(key); }
        if (entry == null) {
            // Certify outside the cache lock and before reserving graph space.
            // A plane is one arithmetic descriptor, not a survey worth retaining;
            // even a full cache must survive long clear journeys unchanged.
            var plane = PedestrianRegionBuild.uniformSupport(geometry, tile);
            if (plane.isPresent()) {
                synchronized (this) { buildsStarted++; certifiedPlanes++; }
                return new Lookup(PedestrianRegion.certifiedPlane(tile, plane.orElseThrow()), 1, false);
            }
            synchronized (this) {
                if (!bucket.entries.containsKey(key)) buildsStarted++;
                entry = bucket.acquire(key, geometry, tile); trim();
            }
        }
        // Different regions build concurrently; the same region has one builder.
        // Neither a second worker nor the host blocks behind its survey.
        var result = entry.advance(budget, mayContinue);
        synchronized (this) {
            if (result.region() != null && !entry.countedCompletion) {
                if (result.region().certifiedPlane()) certifiedPlanes++; else layeredGraphs++;
                entry.countedCompletion = true;
            }
            bucket.reconcile(key, entry); trim();
        }
        return result;
    }
    private void trim() {
        var buckets = new ArrayList<Bucket<?>>(); buckets.addAll(scoped.values()); buckets.addAll(views.values());
        int count = 0, size = 0, builders = 0;
        for (var bucket : buckets) { count += bucket.entries.size(); size += bucket.slots; builders += bucket.builders; }
        while (count > MAX_REGIONS || size > MAX_SURFACES || builders > MAX_BUILDERS) {
            boolean building = builders > MAX_BUILDERS;
            // Graph capacity evicts completed derived data, never an unfinished
            // worker continuation. Builders have their own smaller bound; their
            // worst-case reserved size alone fits below both graph ceilings.
            var victim = buckets.stream().filter(bucket -> building ? bucket.builders > 0
                    : bucket.entries.size() > bucket.builders).findFirst().orElseThrow();
            int oldSize = victim.slots, oldBuilders = victim.builders;
            victim.evict(building); evictions++; count--; size += victim.slots - oldSize; builders += victim.builders - oldBuilders;
        }
    }
    synchronized PedestrianRegionCacheDiagnostic diagnostic() {
        return new PedestrianRegionCacheDiagnostic(lookups, buildsStarted, evictions, certifiedPlanes, layeredGraphs);
    }
    synchronized void clear() { views.clear(); scoped.clear(); lookups = buildsStarted = evictions = certifiedPlanes = layeredGraphs = 0; }
}
