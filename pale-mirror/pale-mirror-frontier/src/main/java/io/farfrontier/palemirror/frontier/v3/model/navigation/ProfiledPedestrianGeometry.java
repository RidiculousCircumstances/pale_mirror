package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import io.farfrontier.palemirror.frontier.v3.model.WorldBounds;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/** Physical-envelope decorator. Terrain and immutable obstacles remain owned by their provider. */
public final class ProfiledPedestrianGeometry implements PedestrianRouteGeometry {
    private record Scope(Object terrain, UnitNavigationProfile profile) { }
    private record Version(Object terrain, UnitNavigationProfile profile) { }
    private final PedestrianRouteGeometry terrain;
    private final UnitNavigationProfile profile;
    private final Predicate<BlockPosition> occupied;
    private final Object version;
    private final Optional<PedestrianGeometryRevision> revision;

    public ProfiledPedestrianGeometry(PedestrianRouteGeometry terrain, UnitNavigationProfile profile,
                                      Predicate<BlockPosition> occupied) {
        this.terrain = Objects.requireNonNull(terrain); this.profile = Objects.requireNonNull(profile);
        this.occupied = Objects.requireNonNull(occupied); this.version = new Version(terrain.version(), profile);
        this.revision = terrain.regionalRevision().map(source -> {
            // A body at a region edge also observes the neighbouring region's obstacles.
            var expanded = new java.util.LinkedHashMap<PedestrianGeometryRevision.Region, Object>();
            int margin = profile.footprintRadius() == 0 ? 0 : 1;
            source.revisions().forEach((region, token) -> {
                for (int x = -margin; x <= margin; x++) for (int z = -margin; z <= margin; z++)
                    expanded.merge(new PedestrianGeometryRevision.Region(region.x() + x, region.z() + z), token,
                            (left, right) -> java.util.List.of(left, right));
            });
            return new PedestrianGeometryRevision(new Scope(source.scope(), profile), source.baseline(), expanded);
        });
    }
    @Override public Object version() { return version; }
    @Override public Optional<PedestrianGeometryRevision> regionalRevision() { return revision; }
    @Override public WorldBounds bounds() { return terrain.bounds(); }
    @Override public SurfaceAnchor supportAt(int x, int z) { return terrain.supportAt(x, z); }
    @Override public java.util.List<SurfaceAnchor> surfacesAt(int x, int z) { return terrain.surfacesAt(x, z); }
    @Override public boolean supportExcluded(SurfaceAnchor surface) { return terrain.supportExcluded(surface); }
    @Override public boolean blocked(SurfaceAnchor surface) {
        if (!terrain.supportAvailable(surface)) return true;
        return !clearBody(surface);
    }
    @Override public boolean clearBody(SurfaceAnchor surface) {
        return profile.footprintRadius() == 0 ? clearVolume(surface.x() + 0.5, surface.z() + 0.5, surface.y() + 1) : !poses(surface).isEmpty();
    }
    /** Interaction stations require the named centre; transit may use a clear sub-cell position.
     * A wide actor can approach a one-block rise without pretending its body overlaps the rise. */
    @Override public boolean clearStation(SurfaceAnchor surface) {
        return terrain.supportAvailable(surface) && clearVolume(surface.x() + 0.5, surface.z() + 0.5, surface.y() + 1);
    }
    @Override public boolean canProjectPlacement(SurfaceAnchor retained, SurfaceAnchor target) {
        return Math.abs((long) retained.x() - target.x()) + Math.abs((long) retained.z() - target.z()) == 1
                && profile.permitsGrade(retained.y(), target.y()) && bounds().contains(target.support())
                && knowsSupport(target) && clearStation(target);
    }
    private java.util.List<double[]> poses(SurfaceAnchor surface) {
        double overhang = Math.max(0, (profile.width() - 1.0) / 2.0);
        double shift = Math.min(0.499999, overhang + 0.000001);
        var offsets = overhang == 0 ? new double[]{0.5} : new double[]{0.5, 0.5 - shift, 0.5 + shift};
        var result = new java.util.ArrayList<double[]>();
        for (double x : offsets) for (double z : offsets)
            if (clearVolume(surface.x() + x, surface.z() + z, surface.y() + 1))
                result.add(new double[]{surface.x() + x, surface.z() + z});
        return result;
    }
    private boolean clearVolume(double centerX, double centerZ, int feetY) {
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
        long dy = (long) to.y() - from.y();
        if (Math.abs((long) from.x() - to.x()) + Math.abs((long) from.z() - to.z()) != 1
                || dy > profile.stepUpBlocks() || -dy > profile.dropBlocks() || blocked(from) || blocked(to)) return false;
        int feet = Math.max(from.y(), to.y()) + 1;
        if (profile.footprintRadius() == 0)
            return clearVolume(from.x() + 0.5, from.z() + 0.5, feet) && clearVolume(to.x() + 0.5, to.z() + 0.5, feet);
        for (var origin : poses(from)) for (var target : poses(to)) {
            boolean clear = true;
            for (int sample = 0; sample <= 4; sample++) {
                double fraction = sample / 4.0;
                if (!clearVolume(origin[0] + (target[0] - origin[0]) * fraction,
                        origin[1] + (target[1] - origin[1]) * fraction, feet)) { clear = false; break; }
            }
            if (clear) return true;
        }
        return false;
    }
}
