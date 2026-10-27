package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import io.farfrontier.palemirror.frontier.v3.model.WorldBounds;
import java.util.*;

/** Positive regional shortcut only across an explicitly certified clear plane.
 * It expands to ordinary adjacent portals; absence resumes the complete search. */
final class PedestrianOpenRegionShortcut {
    private static final int SPAN = 16;
    record Proof(SurfaceAnchor exit, SurfaceAnchor entrance, int dx, int dz,
                 List<PedestrianRegion.Tile> covered) {
        Proof { covered = List.copyOf(covered); }
        int span() { return covered.size() - 1; }
        PedestrianRegion.Tile target() { return covered.getLast(); }
        PedestrianItineraryCache.Portal portal(int offset) {
            var position = exit.support().offset(offset * dx * PedestrianRegion.SIDE, 0, offset * dz * PedestrianRegion.SIDE);
            return new PedestrianItineraryCache.Portal(new SurfaceAnchor(position), new SurfaceAnchor(position.offset(dx, 0, dz)), 0, 0);
        }
    }
    private PedestrianOpenRegionShortcut() { }
    static Optional<Proof> find(PedestrianRouteGeometry geometry, PedestrianRegion.Tile origin, int direction) {
        int dx = direction == 0 ? 1 : direction == 2 ? -1 : 0;
        int dz = direction == 3 ? 1 : direction == 1 ? -1 : 0;
        var target = new PedestrianRegion.Tile(origin.x() + dx * SPAN, origin.z() + dz * SPAN);
        long minX = (long) Math.min(origin.x(), target.x()) * PedestrianRegion.SIDE;
        long minZ = (long) Math.min(origin.z(), target.z()) * PedestrianRegion.SIDE;
        long maxX = ((long) Math.max(origin.x(), target.x()) + 1) * PedestrianRegion.SIDE;
        long maxZ = ((long) Math.max(origin.z(), target.z()) + 1) * PedestrianRegion.SIDE;
        var bounds = geometry.bounds();
        if (minX < bounds.minX() || minZ < bounds.minZ() || maxX > bounds.maxXExclusive() || maxZ > bounds.maxZExclusive())
            return Optional.empty();
        var y = geometry.uniformSupport(new WorldBounds((int) minX, (int) minZ, (int) (maxX - minX), (int) (maxZ - minZ)));
        if (y.isEmpty()) return Optional.empty();
        int x = origin.x() * PedestrianRegion.SIDE + (dx > 0 ? PedestrianRegion.SIDE - 1 : dx < 0 ? 0 : PedestrianRegion.SIDE / 2);
        int z = origin.z() * PedestrianRegion.SIDE + (dz > 0 ? PedestrianRegion.SIDE - 1 : dz < 0 ? 0 : PedestrianRegion.SIDE / 2);
        var exit = SurfaceAnchor.at(x, y.orElseThrow(), z);
        var entrance = new SurfaceAnchor(exit.support().offset(dx * ((SPAN - 1) * PedestrianRegion.SIDE + 1), 0,
                dz * ((SPAN - 1) * PedestrianRegion.SIDE + 1)));
        var covered = new ArrayList<PedestrianRegion.Tile>();
        for (int offset = 0; offset <= SPAN; offset++) covered.add(new PedestrianRegion.Tile(origin.x() + dx * offset, origin.z() + dz * offset));
        return Optional.of(new Proof(exit, entrance, dx, dz, covered));
    }
}
