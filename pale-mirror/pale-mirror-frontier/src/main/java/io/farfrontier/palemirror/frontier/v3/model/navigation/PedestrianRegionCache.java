package io.farfrontier.palemirror.frontier.v3.model.navigation;

import java.util.*;

/** Bounded reusable layered regions, including cooperative construction; no world/task authority. */
final class PedestrianRegionCache {
    private static final int MAX_REGIONS = 2_048, MAX_SURFACES = MAX_REGIONS * PedestrianRegion.CELLS;
    private static final int MAX_BUILDERS = 64;
    private final Map<PedestrianRouteGeometry, Bucket<PedestrianRegion.Tile>> views = new WeakHashMap<>();
    private record RegionalKey(PedestrianRegion.Tile tile, Object revision, io.farfrontier.palemirror.frontier.v3.model.WorldBounds bounds) { }
    private final Map<Object, Bucket<RegionalKey>> scoped = new WeakHashMap<>();
    private static final class Entry {
        private PedestrianRegionBuild build;
        private PedestrianRegion region;
        private boolean limited;
        Entry(PedestrianRouteGeometry geometry, PedestrianRegion.Tile tile) { build = new PedestrianRegionBuild(geometry, tile); }
        Lookup advance(int budget) {
            if (region != null || limited) return new Lookup(region, 1, limited);
            int work = build.advance(budget); region = build.result(); limited = build.limited();
            if (region != null || limited) build = null;
            return new Lookup(region, work, limited);
        }
        int size() { return build == null ? region == null ? 0 : region.size() : PedestrianRegionBuild.MAX_SURFACES; }
        boolean building() { return build != null; }
    }
    /** Maintain counts when entries change; a cache hit must not scan every retained region. */
    private static final class Bucket<K> {
        private final Map<K, Entry> entries = new LinkedHashMap<>(16, 0.75f, true);
        private int slots, builders;
        Lookup advance(K key, PedestrianRouteGeometry geometry, PedestrianRegion.Tile tile, int budget) {
            var entry = entries.get(key);
            if (entry == null) {
                entry = new Entry(geometry, tile); entries.put(key, entry);
                slots += entry.size(); builders++;
            }
            int oldSize = entry.size(); boolean wasBuilding = entry.building();
            var result = entry.advance(budget); slots += entry.size() - oldSize;
            if (wasBuilding && !entry.building()) builders--;
            return result;
        }
        void evict(boolean building) {
            var iterator = entries.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next().getValue();
                if (building && !entry.building()) continue;
                slots -= entry.size(); if (entry.building()) builders--;
                iterator.remove(); return;
            }
            throw new IllegalStateException("region cache eviction lost its counted entry");
        }
    }
    record Lookup(PedestrianRegion region, int work, boolean limited) { }
    synchronized Lookup region(PedestrianRouteGeometry geometry, PedestrianRegion.Tile tile, int budget) {
        if (budget <= 0) return new Lookup(null, 0, false);
        Lookup result;
        if (geometry.regionalRevision().isPresent()) {
            var revision = geometry.regionalRevision().orElseThrow();
            if (scoped.size() >= 64 && !scoped.containsKey(revision.scope())) scoped.remove(scoped.keySet().iterator().next());
            var regions = scoped.computeIfAbsent(revision.scope(), ignored -> new Bucket<>());
            var key = new RegionalKey(tile, revision.at(tile.x(), tile.z()), geometry.bounds());
            result = regions.advance(key, geometry, tile, budget);
        } else {
            if (views.size() >= 64 && !views.containsKey(geometry)) views.clear();
            var regions = views.computeIfAbsent(geometry, ignored -> new Bucket<>());
            result = regions.advance(tile, geometry, tile, budget);
        }
        trim(); return result;
    }
    private void trim() {
        var buckets = new ArrayList<Bucket<?>>(); buckets.addAll(scoped.values()); buckets.addAll(views.values());
        int count = 0, size = 0, builders = 0;
        for (var bucket : buckets) { count += bucket.entries.size(); size += bucket.slots; builders += bucket.builders; }
        while (count > MAX_REGIONS || size > MAX_SURFACES || builders > MAX_BUILDERS) {
            boolean building = builders > MAX_BUILDERS;
            var victim = buckets.stream().filter(bucket -> building ? bucket.builders > 0 : !bucket.entries.isEmpty()).findFirst().orElseThrow();
            int oldSize = victim.slots, oldBuilders = victim.builders;
            victim.evict(building); count--; size += victim.slots - oldSize; builders += victim.builders - oldBuilders;
        }
    }
    synchronized void clear() { views.clear(); scoped.clear(); }
}
