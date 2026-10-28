package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import io.farfrontier.palemirror.frontier.v3.model.WorldBounds;
import java.util.Objects;
import java.util.Optional;
import io.farfrontier.palemirror.frontier.v3.model.geometry.*;

/** Physical-envelope decorator. Terrain and immutable obstacles remain owned by their provider. */
public final class ProfiledPedestrianGeometry implements PedestrianRouteGeometry {
    /** Rebuildable geometry-only retention ceiling, not actor positions or route authority. */
    private static final int MAX_VOLUME_RESULTS = 8_192;
    private static final java.util.concurrent.atomic.LongAdder EDGE_QUERIES = new java.util.concurrent.atomic.LongAdder();
    private static final java.util.concurrent.atomic.LongAdder REGIONAL_READS = new java.util.concurrent.atomic.LongAdder();
    private static final java.util.concurrent.atomic.LongAdder REGIONAL_COMPUTATIONS = new java.util.concurrent.atomic.LongAdder();
    private static final java.util.concurrent.atomic.LongAdder UNVERSIONED_COMPUTATIONS = new java.util.concurrent.atomic.LongAdder();
    private static final java.util.concurrent.atomic.LongAdder SCOPE_CONSTRUCTIONS = new java.util.concurrent.atomic.LongAdder();
    /** Process-lifetime bounded counters only; a read never drives cache or domain decisions. */
    public record EdgeFactsDiagnostic(long queries, long regionalReads, long regionalComputations,
                                      long unversionedComputations, long scopeConstructionAttempts) { }
    public static EdgeFactsDiagnostic edgeFactsDiagnostic() {
        return new EdgeFactsDiagnostic(EDGE_QUERIES.sum(), REGIONAL_READS.sum(), REGIONAL_COMPUTATIONS.sum(),
                UNVERSIONED_COMPUTATIONS.sum(), SCOPE_CONSTRUCTIONS.sum());
    }
    private record Volume(double x, double z, int feetY) { }
    private record DirectedEdge(SurfaceAnchor from, SurfaceAnchor to) { }
    /** Exact local facts plus a bounded declared geometry epoch; no world-state or actor snapshot. */
    private record RegionalEdge(Object geometryScope, Object baseline, UnitNavigationProfile profile,
                                WorldBounds bounds, DirectedEdge edge, Object originFacts, Object targetFacts) {
        // Regional facts can contain detailed collision sets. Do not deep-hash
        // them on each lookup; equality still compares the COMPLETE local facts.
        @Override public int hashCode() { return 31 * (31 * edge.hashCode() + profile.hashCode()) + geometryScope.hashCode(); }
        @Override public boolean equals(Object other) {
            return this == other || other instanceof RegionalEdge value && baseline == value.baseline
                    && geometryScope.equals(value.geometryScope) && profile.equals(value.profile)
                    && bounds.equals(value.bounds) && edge.equals(value.edge)
                    && Objects.equals(originFacts, value.originFacts) && Objects.equals(targetFacts, value.targetFacts);
        }
    }
    // At most eight declared geometry/profile scopes, each with at most the
    // existing 8192-fact bound. Eviction releases epochs and only causes rereads.
    private static final io.farfrontier.palemirror.frontier.v3.model.BoundedValueMemo<Object,
            io.farfrontier.palemirror.frontier.v3.model.BoundedValueMemo<RegionalEdge, Boolean>> REGIONAL_EDGES =
            new io.farfrontier.palemirror.frontier.v3.model.BoundedValueMemo<>(8);
    private final java.util.Map<DirectedEdge, Boolean> edges = new java.util.LinkedHashMap<>(128, 0.75f, true);
    private final java.util.Map<Volume, Boolean> volumes = new java.util.LinkedHashMap<>(128, 0.75f, true);
    private final java.util.Map<SurfaceAnchor, Boolean> knownClearance = new java.util.LinkedHashMap<>(128, 0.75f, true);
    private final java.util.Map<io.farfrontier.palemirror.frontier.v3.model.TerrainColumn, Boolean> detailedColumns = new java.util.LinkedHashMap<>(128, 0.75f, true);
    private record Version(Object terrain, UnitNavigationProfile profile) { }
    private final PedestrianRouteGeometry terrain;
    private final UnitNavigationProfile profile;
    private final NavigationObstacleView occupied;
    private final Object version;
    private final Optional<PedestrianGeometryRevision> revision;
    private final double[] poseOffsets;
    private final java.util.Map<SurfaceAnchor, java.util.List<NavigationContact>> contacts = new java.util.LinkedHashMap<>();

    public ProfiledPedestrianGeometry(PedestrianRouteGeometry terrain, UnitNavigationProfile profile,
                                      NavigationObstacleView occupied) {
        this.terrain = Objects.requireNonNull(terrain); this.profile = Objects.requireNonNull(profile);
        this.poseOffsets = NavigationBodyGeometry.poseOffsets(profile);
        this.occupied = Objects.requireNonNull(occupied); this.version = new Version(terrain.version(), profile);
        // Regional expansion/hash depends only on immutable terrain + explicit body
        // profile, not the query, actor or live collision predicate.
        this.revision = terrain.regionalRevision().map(source -> source.profiled(profile));
    }
    @Override public Object version() { return version; }
    @Override public Optional<PedestrianGeometryRevision> regionalRevision() { return revision; }
    @Override public WorldBounds bounds() { return terrain.bounds(); }
    @Override public java.util.OptionalInt uniformSupport(WorldBounds area) {
        // A body envelope and its swept volume must fit the certified plane,
        // including neighbouring columns. The provider certifies full clear air.
        var envelope = NavigationRegionCoverage.expanded(area, profile.footprintRadius() + 1);
        return occupied.emptyArea(envelope) ? terrain.uniformSupport(envelope) : java.util.OptionalInt.empty();
    }
    @Override public SurfaceAnchor supportAt(int x, int z) { return terrain.supportAt(x, z); }
    @Override public java.util.List<SurfaceAnchor> surfacesAt(int x, int z) { return terrain.surfacesAt(x, z); }
    @Override public boolean supportExcluded(SurfaceAnchor surface) { return terrain.supportExcluded(surface); }
    @Override public boolean blockKnown(BlockPosition position) { return terrain.blockKnown(position); }
    @Override public Optional<NavigationCollisionView> collisions() { return terrain.collisions(); }
    private boolean detailed(SurfaceAnchor surface) {
        return remembered(detailedColumns, new io.farfrontier.palemirror.frontier.v3.model.TerrainColumn(surface.x(), surface.z()),
                () -> collisions().filter(view -> view.detailedAt(surface.x(), surface.z())).isPresent());
    }
    /** Both positive and missing knowledge belong only to this captured immutable geometry. */
    private static <K> boolean remembered(java.util.Map<K, Boolean> cache, K key, java.util.function.BooleanSupplier read) {
        synchronized (cache) { var prior = cache.get(key); if (prior != null) return prior; }
        boolean result = read.getAsBoolean();
        synchronized (cache) {
            if (cache.size() >= MAX_VOLUME_RESULTS && !cache.containsKey(key)) cache.remove(cache.keySet().iterator().next());
            cache.put(key, result);
        }
        return result;
    }
    private java.util.List<NavigationContact> contacts(SurfaceAnchor surface) {
        synchronized (contacts) { var prior = contacts.get(surface); if (prior != null) return prior; }
        var view = collisions().orElseThrow();
        var result = new java.util.ArrayList<NavigationContact>();
        for (double x : poseOffsets) for (double z : poseOffsets)
            result.addAll(NavigationBodyGeometry.contacts(view, surface, profile, x, z));
        if (!result.isEmpty()) {
            var reference = result.getFirst();
            // The strategic node is one support, not a sub-cell graph. Retain a
            // single connected component so successive edges cannot teleport
            // between disconnected ledges inside the same voxel.
            result.removeIf(contact -> !contact.equals(reference)
                    && (!NavigationBodyGeometry.traversable(view, profile, reference, contact)
                        || !NavigationBodyGeometry.traversable(view, profile, contact, reference)));
        }
        var immutable = java.util.List.copyOf(result);
        synchronized (contacts) {
            if (contacts.size() >= MAX_VOLUME_RESULTS) contacts.remove(contacts.keySet().iterator().next());
            contacts.put(surface, immutable);
        }
        return immutable;
    }
    @Override public boolean clearanceKnown(SurfaceAnchor surface) {
        return remembered(knownClearance, surface, () -> readClearanceKnown(surface));
    }
    private boolean readClearanceKnown(SurfaceAnchor surface) {
        if (detailed(surface)) return NavigationBodyGeometry.known(collisions().orElseThrow(), surface, profile);
        int radius = profile.footprintRadius();
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            var contact = SurfaceAnchor.at(surface.x() + dx, surface.y(), surface.z() + dz);
            if (!terrain.clearanceKnown(contact)) return false;
            for (int y = 0; y <= profile.bodyLayers(); y++) if (!blockKnown(contact.support().offset(0, y, 0))) return false;
        }
        return true;
    }
    @Override public java.util.Set<BlockPosition> missingGeometry(SurfaceAnchor surface) {
        if (detailed(surface)) return NavigationBodyGeometry.missing(collisions().orElseThrow(), surface, profile);
        var missing = new java.util.HashSet<BlockPosition>();
        int radius = profile.footprintRadius();
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            var contact = SurfaceAnchor.at(surface.x() + dx, surface.y(), surface.z() + dz);
            missing.addAll(terrain.missingGeometry(contact));
            for (int y = 0; y <= profile.bodyLayers(); y++) {
                var position = contact.support().offset(0, y, 0);
                if (!blockKnown(position)) missing.add(position);
            }
        }
        return java.util.Set.copyOf(missing);
    }
    @Override public boolean blocked(SurfaceAnchor surface) {
        if (!terrain.supportAvailable(surface)) return true;
        return !clearBody(surface);
    }
    @Override public boolean clearBody(SurfaceAnchor surface) {
        if (detailed(surface)) return !contacts(surface).isEmpty();
        if (!clearanceKnown(surface)) return false;
        for (double x : poseOffsets) for (double z : poseOffsets)
            if (clearVolume(surface.x() + x, surface.z() + z, surface.y() + 1)) return true;
        return false;
    }
    /** Interaction stations require the named centre; transit may use a clear sub-cell position.
     * A wide actor can approach a one-block rise without pretending its body overlaps the rise. */
    @Override public boolean clearStation(SurfaceAnchor surface) {
        if (detailed(surface)) return terrain.supportAvailable(surface)
                && !NavigationBodyGeometry.contacts(collisions().orElseThrow(), surface, profile, 0.5, 0.5).isEmpty();
        return terrain.supportAvailable(surface) && clearVolume(surface.x() + 0.5, surface.z() + 0.5, surface.y() + 1);
    }
    @Override public boolean canProjectPlacement(SurfaceAnchor retained, SurfaceAnchor target) {
        if (detailed(retained) || detailed(target)) {
            if (Math.abs((long) retained.x() - target.x()) + Math.abs((long) retained.z() - target.z()) != 1
                    || !bounds().contains(target.support()) || !knowsSupport(target) || !clearStation(target)) return false;
            var view = collisions().orElseThrow();
            var source = view.cell(retained.support()).flatMap(cell -> {
                var top = cell.shape().topAt(.5, .5); return top.isPresent() ? Optional.of(retained.y() + top.orElseThrow()) : Optional.empty();
            });
            if (source.isEmpty()) return false;
            return NavigationBodyGeometry.contacts(view, target, profile, .5, .5).stream().anyMatch(contact ->
                    contact.feetY() - source.orElseThrow() <= profile.stepUpBlocks() + 1e-7
                            && source.orElseThrow() - contact.feetY() <= profile.dropBlocks() + 1e-7);
        }
        return Math.abs((long) retained.x() - target.x()) + Math.abs((long) retained.z() - target.z()) == 1
                && profile.permitsGrade(retained.y(), target.y()) && bounds().contains(target.support())
                && knowsSupport(target) && clearStation(target);
    }
    private boolean clearVolume(double centerX, double centerZ, int feetY) {
        // This decorator captures one immutable terrain/obstacle revision. The
        // same standing/swept volumes recur across neighbouring graph edges and
        // body-pose pairs; a new knowledge revision creates another decorator.
        var key = new Volume(centerX, centerZ, feetY);
        Boolean prior;
        synchronized (volumes) { prior = volumes.get(key); }
        if (prior != null) return prior;
        boolean result = computeClearVolume(centerX, centerZ, feetY);
        synchronized (volumes) {
            if (volumes.size() == MAX_VOLUME_RESULTS && !volumes.containsKey(key)) volumes.remove(volumes.keySet().iterator().next());
            volumes.put(key, result);
        }
        return result;
    }
    private boolean computeClearVolume(double centerX, double centerZ, int feetY) {
        if (!clearanceKnown(SurfaceAnchor.at((int) Math.floor(centerX), feetY - 1, (int) Math.floor(centerZ)))) return false;
        double half = profile.width() / 2.0;
        int minX = (int) Math.floor(centerX - half + 0.0000001), maxX = (int) Math.ceil(centerX + half - 0.0000001) - 1;
        int minZ = (int) Math.floor(centerZ - half + 0.0000001), maxZ = (int) Math.ceil(centerZ + half - 0.0000001) - 1;
        int topY = feetY + profile.bodyLayers();
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
            var levels = terrain.surfacesAt(x, z);
            // An upper floor is solid only where it intersects this body, not an
            // obstacle stretching down through every lower floor in the column.
            boolean supported = false;
            for (var ground : levels) {
                if (ground.y() >= feetY && ground.y() < topY) return false;
                if (ground.y() < feetY && bounds().contains(ground.support())) supported = true;
            }
            if (!supported) return false;
            for (int y = feetY; y < topY; y++)
                if (occupied.test(new BlockPosition(x, y, z))) return false;
        }
        return true;
    }
    @Override public boolean canTraverse(SurfaceAnchor from, SurfaceAnchor to) {
        // A captured geometry/profile is immutable. The per-view hot cache
        // shares directed swept-body facts; reuse across views below requires
        // their complete declared local regional facts to match.
        EDGE_QUERIES.increment();
        var edge = new DirectedEdge(from, to);
        return remembered(edges, edge, () -> {
            if (revision.isEmpty()) { UNVERSIONED_COMPUTATIONS.increment(); return traverse(from, to); }
            REGIONAL_READS.increment();
            var facts = revision.orElseThrow();
            var origin = PedestrianGeometryRevision.Region.at(from.support());
            var target = PedestrianGeometryRevision.Region.at(to.support());
            // The profiled revision already covers neighbouring body columns and
            // contextual collision halos. Only those exact immutable facts are
            // retained; a remote edit need not destroy this local result.
            var key = new RegionalEdge(facts.geometryScope(), facts.baseline(), profile, bounds(), edge,
                    facts.at(origin.x(), origin.z()), facts.at(target.x(), target.z()));
            var shared = REGIONAL_EDGES.get(facts.geometryScope(), () -> {
                SCOPE_CONSTRUCTIONS.increment();
                return new io.farfrontier.palemirror.frontier.v3.model.BoundedValueMemo<>(MAX_VOLUME_RESULTS);
            });
            return shared.get(key, () -> { REGIONAL_COMPUTATIONS.increment(); return traverse(from, to); });
        });
    }
    private boolean traverse(SurfaceAnchor from, SurfaceAnchor to) {
        if (detailed(from) || detailed(to)) {
            if (Math.abs((long) from.x() - to.x()) + Math.abs((long) from.z() - to.z()) != 1
                    || !terrain.supportAvailable(from) || !terrain.supportAvailable(to)) return false;
            var view = collisions().orElseThrow();
            for (var origin : contacts(from)) for (var target : contacts(to))
                if (NavigationBodyGeometry.traversable(view, profile, origin, target)) return true;
            return false;
        }
        long dy = (long) to.y() - from.y();
        if (Math.abs((long) from.x() - to.x()) + Math.abs((long) from.z() - to.z()) != 1
                || dy > profile.stepUpBlocks() || -dy > profile.dropBlocks()
                || !terrain.supportAvailable(from) || !terrain.supportAvailable(to)) return false;
        int feet = Math.max(from.y(), to.y()) + 1;
        if (profile.footprintRadius() == 0)
            return clearBody(from) && clearBody(to)
                    && clearVolume(from.x() + 0.5, from.z() + 0.5, feet) && clearVolume(to.x() + 0.5, to.z() + 0.5, feet);
        // Preserve the complete ordered pose search, but read each endpoint only
        // when reached. A clear centre pair must not eagerly survey all 18 poses.
        // Target facts are reused across origins, never recalculated per pair.
        var targets = new byte[poseOffsets.length * poseOffsets.length];
        for (double x : poseOffsets) for (double z : poseOffsets) {
            double originX = from.x() + x, originZ = from.z() + z;
            if (!clearVolume(originX, originZ, from.y() + 1)) continue;
            for (int index = 0; index < targets.length; index++) {
                double targetX = to.x() + poseOffsets[index / poseOffsets.length];
                double targetZ = to.z() + poseOffsets[index % poseOffsets.length];
                if (targets[index] == 0) targets[index] = (byte) (clearVolume(targetX, targetZ, to.y() + 1) ? 2 : 1);
                if (targets[index] == 2 && clearSweep(originX, originZ, targetX, targetZ, feet)) return true;
            }
        }
        return false;
    }
    private boolean clearSweep(double originX, double originZ, double targetX, double targetZ, int feet) {
        for (int sample = 0; sample <= 4; sample++) {
            double fraction = sample / 4.0;
            if (!clearVolume(originX + (targetX - originX) * fraction,
                    originZ + (targetZ - originZ) * fraction, feet)) return false;
        }
        return true;
    }
}
