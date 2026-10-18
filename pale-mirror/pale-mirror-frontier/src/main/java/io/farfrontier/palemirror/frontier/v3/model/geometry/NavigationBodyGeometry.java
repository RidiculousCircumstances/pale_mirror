package io.farfrontier.palemirror.frontier.v3.model.geometry;

import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import io.farfrontier.palemirror.frontier.v3.model.navigation.UnitNavigationProfile;
import java.util.*;

/** Shared fractional contact/body-volume policy. No path search, task selection or block-name rules. */
public final class NavigationBodyGeometry {
    public enum Clearance { CLEAR, BLOCKED, UNKNOWN }
    private NavigationBodyGeometry() { }

    /** Exact missing reads from the same clearance calculation, not an invented survey radius. */
    public static Set<BlockPosition> missing(NavigationCollisionView view, SurfaceAnchor support, UnitNavigationProfile profile) {
        var missing = new LinkedHashSet<BlockPosition>();
        boolean known = known(position -> {
            var cell = view.cell(position); if (cell.isEmpty()) missing.add(position); return cell;
        }, support, profile);
        return known ? Set.of() : Set.copyOf(missing);
    }

    public static boolean known(NavigationCollisionView view, SurfaceAnchor support, UnitNavigationProfile profile) {
        var source = view.cell(support.support()); if (source.isEmpty()) return false;
        boolean unknown = false;
        for (double x : poseOffsets(profile)) for (double z : poseOffsets(profile))
            for (var box : source.orElseThrow().shape().boxes()) if (box.covers(x, z)) {
                var result = clearance(view, profile, support.x() + x, support.y() + box.maxY(), support.z() + z);
                if (result == Clearance.CLEAR) return true;
                if (result == Clearance.UNKNOWN) unknown = true;
            }
        return !unknown;
    }
    public static double[] poseOffsets(UnitNavigationProfile profile) {
        double overhang = Math.max(0, (profile.width() - 1) / 2);
        double shift = Math.min(.499999, overhang + .000001);
        return overhang == 0 ? new double[]{.5} : new double[]{.5, .5 - shift, .5 + shift};
    }

    public static List<NavigationContact> contacts(NavigationCollisionView view, SurfaceAnchor support,
                                                    UnitNavigationProfile profile, double offsetX, double offsetZ) {
        var cell = view.cell(support.support());
        if (cell.isEmpty() || !cell.orElseThrow().dry()) return List.of();
        var levels = new TreeSet<Double>(Comparator.reverseOrder());
        for (var box : cell.orElseThrow().shape().boxes())
            if (box.covers(offsetX, offsetZ)) levels.add(support.y() + box.maxY());
        var result = new ArrayList<NavigationContact>();
        for (double top : levels) {
            var contact = new NavigationContact(support, support.x() + offsetX, top, support.z() + offsetZ);
            if (clearance(view, profile, contact.x(), top, contact.z()) == Clearance.CLEAR) result.add(contact);
        }
        return List.copyOf(result);
    }

    public static Clearance clearance(NavigationCollisionView view, UnitNavigationProfile profile,
                                      double x, double feetY, double z) {
        double half = profile.width() / 2, top = feetY + profile.height();
        boolean unknown = false;
        // One-cell halo includes protruding collision boxes; provider bounds are explicit.
        for (int bx = floor(x - half) - 1; bx <= floor(x + half - 1e-7) + 1; bx++)
            for (int bz = floor(z - half) - 1; bz <= floor(z + half - 1e-7) + 1; bz++)
                for (int by = floor(feetY) - 1; by <= floor(top - 1e-7) + 1; by++) {
                    var cell = view.cell(new BlockPosition(bx, by, bz));
                    if (cell.isEmpty()) { unknown = true; continue; }
                    var value = cell.orElseThrow();
                    if (!value.dry() && NavigationShape.FULL.boxes().getFirst().overlaps(
                            x - half - bx, feetY - by, z - half - bz, x + half - bx, top - by, z + half - bz))
                        return Clearance.BLOCKED;
                    for (var box : value.shape().boxes()) if (box.overlaps(x - half - bx, feetY - by, z - half - bz,
                            x + half - bx, top - by, z + half - bz)) return Clearance.BLOCKED;
                }
        return unknown ? Clearance.UNKNOWN : Clearance.CLEAR;
    }

    public static boolean traversable(NavigationCollisionView view, UnitNavigationProfile profile,
                                      NavigationContact from, NavigationContact to) {
        double rise = to.feetY() - from.feetY();
        if (rise > profile.stepUpBlocks() + 1e-7 || -rise > profile.dropBlocks() + 1e-7) return false;
        // A horizontal swept body at the upper contact plus vertical ascent/descent.
        // Boxes give exact continuous collision checks, not sparse samples that can miss a thin wall.
        double top = Math.max(from.feetY(), to.feetY());
        return sweep(view, profile, from.x(), from.feetY(), from.z(), from.x(), top, from.z())
                && sweep(view, profile, from.x(), top, from.z(), to.x(), top, to.z())
                && sweep(view, profile, to.x(), top, to.z(), to.x(), to.feetY(), to.z());
    }
    /** Vertical gravity continuation from a spatial pose, without inventing a source contact. */
    public static boolean canDescend(NavigationCollisionView view, UnitNavigationProfile profile,
                                    double x, double feetY, double z, NavigationContact landing) {
        double drop = feetY - landing.feetY();
        return landing.x() == x && landing.z() == z && drop > 0 && drop <= profile.dropBlocks() + 1e-7
                && sweep(view, profile, x, feetY, z, landing.x(), landing.feetY(), landing.z());
    }

    private static boolean sweep(NavigationCollisionView view, UnitNavigationProfile profile,
            double x0, double y0, double z0, double x1, double y1, double z1) {
        // Exact for axis-aligned edges; a shifted pose pair uses a conservative
        // enclosing sweep. It may reject a tight diagonal but never tunnels through a wall.
        double half = profile.width() / 2;
        double minX = Math.min(x0, x1) - half, maxX = Math.max(x0, x1) + half;
        double minY = Math.min(y0, y1), maxY = Math.max(y0, y1) + profile.height();
        double minZ = Math.min(z0, z1) - half, maxZ = Math.max(z0, z1) + half;
        for (int x = floor(minX) - 1; x <= floor(maxX - 1e-7) + 1; x++)
            for (int z = floor(minZ) - 1; z <= floor(maxZ - 1e-7) + 1; z++)
                for (int y = floor(minY) - 1; y <= floor(maxY - 1e-7) + 1; y++) {
                    var cell = view.cell(new BlockPosition(x, y, z));
                    if (cell.isEmpty()) return false;
                    var value = cell.orElseThrow();
                    if (!value.dry() && NavigationShape.FULL.boxes().getFirst().overlaps(
                            minX-x, minY-y, minZ-z, maxX-x, maxY-y, maxZ-z)) return false;
                    for (var box : value.shape().boxes())
                        if (box.overlaps(minX-x, minY-y, minZ-z, maxX-x, maxY-y, maxZ-z)) return false;
                }
        return true;
    }
    private static int floor(double value) { return (int) Math.floor(value); }
}
