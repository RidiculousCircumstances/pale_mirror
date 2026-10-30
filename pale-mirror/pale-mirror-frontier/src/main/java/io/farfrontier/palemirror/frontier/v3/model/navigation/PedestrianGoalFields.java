package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import io.farfrontier.palemirror.frontier.v3.model.WorldBounds;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;

/** Planner-owned bounded goal fields. Current graphs share work; changed images only borrow settled labels. */
final class PedestrianGoalFields {
    private static final int MAX_FIELDS = 128, MAX_NODES = 65_536;
    private record Key(PedestrianRegion region, SurfaceAnchor goal) { }
    private record Scope(Object geometry, WorldBounds bounds, Object baseline, PedestrianRegion.Tile tile, SurfaceAnchor goal) { }
    private final Map<Key, Entry> fields = new LinkedHashMap<>(16, .75f, true);
    private final Map<Scope, WeakReference<Entry>> seeds = new LinkedHashMap<>();
    private int nodes;
    private final AtomicLong hits = new AtomicLong(), builds = new AtomicLong(), repairs = new AtomicLong(), reused = new AtomicLong(), updates = new AtomicLong(), work = new AtomicLong();
    final class Entry {
        private final PedestrianRegion region;
        private final SurfaceAnchor goal;
        private final WeakReference<Entry> seed;
        private final ReentrantLock lock = new ReentrantLock();
        private IncrementalGoalField<SurfaceAnchor> field;
        private volatile IncrementalGoalField.Image<SurfaceAnchor> image;
        private int snapshotMark;
        private long countedReuse, countedUpdates;
        Entry(PedestrianRegion region, SurfaceAnchor goal, WeakReference<Entry> seed) {
            this.region = region; this.goal = goal; this.seed = seed;
        }
        private void initialize() {
            if (field != null) return;
            var parent = seed == null ? null : seed.get();
            var previous = parent == null ? null : parent.image;
            field = new IncrementalGoalField<>(region.goalGraph(), goal, previous);
            if (previous != null) repairs.incrementAndGet(); else builds.incrementAndGet();
        }
        private void publish() {
            reused.addAndGet(field.reusedLabels() - countedReuse); countedReuse = field.reusedLabels();
            updates.addAndGet(field.repairedVertices() - countedUpdates); countedUpdates = field.repairedVertices();
            // Geometric growth bounds cumulative copying. A snapshot owns exact
            // settled labels, never the live frontier or a chain of field states.
            if (image == null || field.finiteLabels() >= Math.max(1, snapshotMark * 2) || !image.complete() && field.complete()) {
                image = field.snapshot(); snapshotMark = field.finiteLabels();
            }
        }
        boolean ready(SurfaceAnchor from) {
            if (!lock.tryLock()) return false;
            try { return field != null && field.settled(from) && field.distance(from) <= PedestrianReuseBudget.cells(from, goal); }
            finally { lock.unlock(); }
        }
        Cursor cursor(SurfaceAnchor from) { return new Cursor(this, from); }
    }
    final class Cursor {
        private final Entry entry;
        private final SurfaceAnchor origin;
        private SurfaceAnchor at;
        private final List<SurfaceAnchor> route = new ArrayList<>();
        private List<SurfaceAnchor> result;
        Cursor(Entry entry, SurfaceAnchor from) { this.entry = entry; origin = at = from; }
        int advance(int budget, BooleanSupplier mayContinue) {
            if (budget <= 0 || result != null || !mayContinue.getAsBoolean()) return 0;
            if (!entry.lock.tryLock()) return 1;
            try {
                entry.initialize();
                int spent = entry.field.advanceUntil(origin, budget, mayContinue); work.addAndGet(spent);
                if (!entry.field.settled(origin)) return spent;
                while (spent < budget && result == null && mayContinue.getAsBoolean()) {
                    spent++; route.add(at);
                    if (at.equals(entry.goal)) { result = List.copyOf(route); entry.publish(); }
                    else at = Objects.requireNonNull(entry.field.next(at), "regional goal field lost its connected path");
                }
                return spent;
            } finally { entry.lock.unlock(); }
        }
        List<SurfaceAnchor> result() { return result; }
    }
    synchronized Entry acquire(PedestrianRouteGeometry geometry, PedestrianRegion region, SurfaceAnchor goal) {
        var key = new Key(region, goal); var previous = fields.get(key);
        if (previous != null) { hits.incrementAndGet(); return previous; }
        Scope scope = geometry.regionalRevision().map(revision -> new Scope(revision.geometryScope(), geometry.bounds(),
                revision.baseline(), region.tile, goal)).orElse(null);
        var next = new Entry(region, goal, scope == null ? null : seeds.get(scope));
        while (!fields.isEmpty() && (fields.size() >= MAX_FIELDS || nodes + region.size() > MAX_NODES)) {
            var iterator = fields.entrySet().iterator(); var old = iterator.next(); iterator.remove(); nodes -= old.getKey().region().size();
        }
        fields.put(key, next); nodes += region.size();
        if (scope != null) {
            if (seeds.size() >= MAX_FIELDS * 2 && !seeds.containsKey(scope)) seeds.remove(seeds.keySet().iterator().next());
            seeds.put(scope, new WeakReference<>(next));
        }
        return next;
    }
    synchronized Entry completed(PedestrianRegion region, SurfaceAnchor goal, SurfaceAnchor from) {
        var value = fields.get(new Key(region, goal));
        if (value != null && value.ready(from)) { hits.incrementAndGet(); return value; }
        return null;
    }
    synchronized Map<String, Long> diagnostic() {
        return Map.of("sharedHits", hits.get(), "builds", builds.get(), "repairs", repairs.get(),
                "seededLabels", reused.get(), "updates", updates.get(), "work", work.get(), "fields", (long) fields.size(), "nodes", (long) nodes);
    }
    synchronized void clear() {
        fields.clear(); seeds.clear(); nodes = 0;
        hits.set(0); builds.set(0); repairs.set(0); reused.set(0); updates.set(0); work.set(0);
    }
}
