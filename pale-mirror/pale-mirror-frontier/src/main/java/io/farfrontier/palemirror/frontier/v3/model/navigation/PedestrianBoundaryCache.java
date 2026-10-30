package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import io.farfrontier.palemirror.frontier.v3.model.WorldBounds;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;

/** Shared directed connectivity between exact regional images. No goal-dependent
 * winner, actor occupancy, geometry mutation or domain execution authority. */
final class PedestrianBoundaryCache {
    // Four directed neighbours per retained regional graph. These are derived
    // retention ceilings, never a limit on the completeness of one boundary.
    private static final int MAX_ENTRIES = 2_048 * 4, MAX_BUILDERS = 64;
    private static final int MAX_PORTALS = MAX_ENTRIES * PedestrianRegion.SIDE;
    record Key(Object scope, WorldBounds bounds, PedestrianRegion.Tile from, PedestrianRegion.Tile to,
               Object originVersion, Object targetVersion) { }
    record Lookup(List<PedestrianItineraryCache.Portal> portals, int work) { }
    private record Advance(List<PedestrianItineraryCache.Portal> portals, int work, int checks) { }
    private final Map<Key, Entry> entries = new HashMap<>();
    private final Map<Object, Set<Key>> scopes = new HashMap<>();
    private final LinkedHashSet<Entry> completed = new LinkedHashSet<>(), building = new LinkedHashSet<>();
    private int retainedPortals;
    private long generation, lookups, hits, buildsStarted, buildsCompleted, traversalChecks, certifiedPlanes, evictions;
    private static final class Entry {
        final Key key;
        final long generation;
        final ReentrantLock lock = new ReentrantLock();
        volatile List<PedestrianItineraryCache.Portal> portals;
        Build build;
        boolean accounted;
        Entry(Key key, long generation, PedestrianRouteGeometry geometry, PedestrianRegion from, PedestrianRegion to) {
            this.key = key; this.generation = generation; build = new Build(geometry, from, to);
        }
        Advance advance(int budget, BooleanSupplier mayContinue) {
            if (portals != null) return new Advance(portals, 1, 0);
            if (!lock.tryLock()) return new Advance(null, 1, 0);
            try {
                if (portals != null) return new Advance(portals, 1, 0);
                var next = build.advance(budget, mayContinue);
                if (next.portals() != null) { portals = next.portals(); build = null; }
                return next;
            } finally { lock.unlock(); }
        }
    }
    Lookup lookup(Key key, PedestrianRouteGeometry geometry, PedestrianRegion from, PedestrianRegion to,
                  int budget, BooleanSupplier mayContinue, BooleanSupplier liveScope) {
        if (budget <= 0) return new Lookup(null, 0);
        Entry entry;
        synchronized (this) {
            lookups++; entry = entries.get(key);
            if (entry != null && entry.portals != null) {
                hits++;
                if (entry.accounted) { completed.remove(entry); completed.add(entry); }
            }
        }
        int proofWork = 0;
        if (entry == null) {
            proofWork = 1;
            // Cheap certified traffic cannot evict expensive layered boundaries.
            var plane = plane(geometry, from, to);
            if (plane != null) {
                synchronized (this) { certifiedPlanes++; }
                return new Lookup(plane, 1);
            }
            synchronized (this) {
                entry = entries.get(key);
                if (entry == null) {
                    buildsStarted++; entry = new Entry(key, generation, geometry, from, to);
                    if (liveScope.getAsBoolean()) {
                        entries.put(key, entry); scopes.computeIfAbsent(key.scope(), ignored -> new HashSet<>()).add(key);
                        building.add(entry); trim();
                    }
                }
            }
        }
        if (budget == proofWork) return new Lookup(null, proofWork);
        var result = entry.advance(budget - proofWork, mayContinue);
        synchronized (this) {
            if (entry.generation == generation) traversalChecks += result.checks();
            if (entries.get(key) == entry && !entry.accounted && result.portals() != null) {
                buildsCompleted++;
                entry.accounted = true; building.remove(entry); completed.add(entry);
                retainedPortals += result.portals().size(); trim();
            }
        }
        return new Lookup(result.portals(), result.work() + proofWork);
    }
    private void trim() {
        while (entries.size() > MAX_ENTRIES || retainedPortals > MAX_PORTALS || building.size() > MAX_BUILDERS)
            remove(building.size() > MAX_BUILDERS ? building.getFirst() : completed.getFirst());
    }
    private void remove(Entry entry) {
        if (!entries.remove(entry.key, entry)) throw new IllegalStateException("boundary cache lost its exact entry");
        building.remove(entry); completed.remove(entry);
        if (entry.accounted) retainedPortals -= entry.portals.size();
        var keys = scopes.get(entry.key.scope()); keys.remove(entry.key);
        if (keys.isEmpty()) scopes.remove(entry.key.scope());
        evictions++;
    }
    synchronized void forget(Object scope) {
        var keys = scopes.get(scope);
        if (keys != null) for (var key : List.copyOf(keys)) remove(entries.get(key));
    }
    synchronized void clear() {
        generation++; entries.clear(); scopes.clear(); completed.clear(); building.clear(); retainedPortals = 0;
        lookups = hits = buildsStarted = buildsCompleted = traversalChecks = certifiedPlanes = evictions = 0;
    }
    synchronized PedestrianBoundaryCacheDiagnostic diagnostic() {
        return new PedestrianBoundaryCacheDiagnostic(lookups, hits, buildsStarted, buildsCompleted, traversalChecks,
                certifiedPlanes, evictions, entries.size(), building.size(), retainedPortals);
    }
    /** A positive certificate covers the whole union and therefore its seam,
     * including the profile's body halo. Two separate plane flags alone do not. */
    private static List<PedestrianItineraryCache.Portal> plane(PedestrianRouteGeometry geometry,
            PedestrianRegion from, PedestrianRegion to) {
        int dx = to.tile.x() - from.tile.x(), dz = to.tile.z() - from.tile.z();
        if (Math.abs((long) dx) + Math.abs((long) dz) != 1)
            throw new IllegalArgumentException("regional boundary requires adjacent tiles");
        if (!from.certifiedPlane() || !to.certifiedPlane() || from.surface(0).y() != to.surface(0).y()) return null;
        int minX = Math.min(from.tile.x(), to.tile.x()) * PedestrianRegion.SIDE;
        int minZ = Math.min(from.tile.z(), to.tile.z()) * PedestrianRegion.SIDE;
        var union = new WorldBounds(minX, minZ, (Math.abs(dx) + 1) * PedestrianRegion.SIDE,
                (Math.abs(dz) + 1) * PedestrianRegion.SIDE);
        var proof = geometry.uniformSupport(union);
        return proof.isPresent() && proof.orElseThrow() == from.surface(0).y()
                ? new PlaneLinks(from.tile, dx, dz, proof.orElseThrow()) : null;
    }
    /** Constant-size analytical list; it retains neither geometry nor regional graphs. */
    private static final class PlaneLinks extends AbstractList<PedestrianItineraryCache.Portal> implements RandomAccess {
        private final PedestrianRegion.Tile from;
        private final int dx, dz, y;
        PlaneLinks(PedestrianRegion.Tile from, int dx, int dz, int y) { this.from = from; this.dx = dx; this.dz = dz; this.y = y; }
        @Override public int size() { return PedestrianRegion.SIDE; }
        @Override public PedestrianItineraryCache.Portal get(int index) {
            Objects.checkIndex(index, size());
            int x = dx > 0 ? PedestrianRegion.SIDE - 1 : dx < 0 ? 0 : index;
            int z = dz > 0 ? PedestrianRegion.SIDE - 1 : dz < 0 ? 0 : index;
            var a = SurfaceAnchor.at(from.x() * PedestrianRegion.SIDE + x, y, from.z() * PedestrianRegion.SIDE + z);
            return new PedestrianItineraryCache.Portal(a, new SurfaceAnchor(a.support().offset(dx, 0, dz)), 0, 0);
        }
    }
    /** Private cooperative producer. Only a complete list is shared with readers;
     * an unfinished or empty result never becomes an invented positive passage. */
    private static final class Build {
        final PedestrianRouteGeometry geometry;
        final PedestrianRegion from, to;
        final int dx, dz;
        final List<PedestrianItineraryCache.Portal> portals = new ArrayList<>();
        List<SurfaceAnchor> exits, entrances;
        int offset, exit, entrance;
        Build(PedestrianRouteGeometry geometry, PedestrianRegion from, PedestrianRegion to) {
            this.geometry = geometry; this.from = from; this.to = to;
            dx = to.tile.x() - from.tile.x(); dz = to.tile.z() - from.tile.z();
            if (Math.abs((long) dx) + Math.abs((long) dz) != 1)
                throw new IllegalArgumentException("regional boundary requires adjacent tiles");
        }
        Advance advance(int budget, BooleanSupplier mayContinue) {
            int work = 0, checks = 0;
            while (work < budget && mayContinue.getAsBoolean()) {
                work++;
                if (offset == PedestrianRegion.SIDE) return new Advance(List.copyOf(portals), work, checks);
                int x = dx > 0 ? PedestrianRegion.SIDE - 1 : dx < 0 ? 0 : offset;
                int z = dz > 0 ? PedestrianRegion.SIDE - 1 : dz < 0 ? 0 : offset;
                int otherX = dx > 0 ? 0 : dx < 0 ? PedestrianRegion.SIDE - 1 : x;
                int otherZ = dz > 0 ? 0 : dz < 0 ? PedestrianRegion.SIDE - 1 : z;
                if (exits == null) {
                    exits = from.surfaces(x, z); entrances = to.surfaces(otherX, otherZ);
                    if (exits.size() > PedestrianRegionBuild.MAX_LEVELS || entrances.size() > PedestrianRegionBuild.MAX_LEVELS)
                        throw new IllegalArgumentException("boundary column exceeds the regional level invariant");
                    continue;
                }
                if (exit == exits.size()) { offset++; exit = entrance = 0; exits = entrances = null; continue; }
                if (entrance == entrances.size()) { exit++; entrance = 0; continue; }
                var a = exits.get(exit); var b = entrances.get(entrance++); checks++;
                if (geometry.canTraverse(a, b))
                    portals.add(new PedestrianItineraryCache.Portal(a, b, from.component(a), to.component(b)));
            }
            return new Advance(null, work, checks);
        }
    }
}
