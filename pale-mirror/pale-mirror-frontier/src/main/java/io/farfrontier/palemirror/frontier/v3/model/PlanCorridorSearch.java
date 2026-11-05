package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.buildings.BuildingVolume;

import java.util.*;

/** Generation-only rectilinear corridor design around reserved land; never an actor pathfinder. */
final class PlanCorridorSearch {
    private record Point(int x, int z) {}

    private record Candidate(Point point, long cost, long estimate) {}

    static List<BlockPosition> connect(
            BlockPosition from,
            BlockPosition to,
            WorldBounds bounds,
            TerrainSurfacePlan terrain,
            List<BuildingVolume> reserved,
            int clearance) {
        var xs = new TreeSet<Integer>();
        var zs = new TreeSet<Integer>();
        xs.add(from.x());
        xs.add(to.x());
        zs.add(from.z());
        zs.add(to.z());
        xs.add(bounds.minX() + clearance);
        xs.add(bounds.maxXExclusive() - clearance - 1);
        zs.add(bounds.minZ() + clearance);
        zs.add(bounds.maxZExclusive() - clearance - 1);
        for (var box : reserved) {
            for (int x : new int[] {box.min().x() - clearance - 1, box.max().x() + clearance})
                if (x >= bounds.minX() + clearance && x < bounds.maxXExclusive() - clearance)
                    xs.add(x);
            for (int z : new int[] {box.min().z() - clearance - 1, box.max().z() + clearance})
                if (z >= bounds.minZ() + clearance && z < bounds.maxZExclusive() - clearance)
                    zs.add(z);
        }
        if (xs.size() > 1024 || zs.size() > 1024)
            throw new IllegalArgumentException(
                    "road corridor design exceeds bounded content scale");
        var x = List.copyOf(xs);
        var z = List.copyOf(zs);
        var start = new Point(x.indexOf(from.x()), z.indexOf(from.z()));
        var goal = new Point(x.indexOf(to.x()), z.indexOf(to.z()));
        var distances = new HashMap<Point, Long>();
        var previous = new HashMap<Point, Point>();
        var queue =
                new PriorityQueue<Candidate>(
                        Comparator.comparingLong(Candidate::estimate)
                                .thenComparingLong(Candidate::cost)
                                .thenComparingInt(c -> c.point().x())
                                .thenComparingInt(c -> c.point().z()));
        distances.put(start, 0L);
        queue.add(new Candidate(start, 0, 0));
        while (!queue.isEmpty()) {
            var candidate = queue.remove();
            var p = candidate.point();
            if (candidate.cost() != distances.get(p)) continue;
            if (p.equals(goal)) break;
            if (distances.size() > 262144)
                throw new IllegalArgumentException("road corridor search exceeded safety bound");
            for (var direction : FacilityFacing.values()) {
                var q = new Point(p.x() + direction.x(), p.z() + direction.z());
                if (q.x() < 0 || q.z() < 0 || q.x() >= x.size() || q.z() >= z.size()) continue;
                int ax = x.get(p.x()), az = z.get(p.z()), bx = x.get(q.x()), bz = z.get(q.z());
                if (!clear(ax, az, bx, bz, reserved, clearance)) continue;
                long cost = candidate.cost() + Math.abs((long) bx - ax) + Math.abs((long) bz - az);
                if (cost >= distances.getOrDefault(q, Long.MAX_VALUE)) continue;
                distances.put(q, cost);
                previous.put(q, p);
                queue.add(
                        new Candidate(
                                q,
                                cost,
                                cost
                                        + Math.abs((long) bx - to.x())
                                        + Math.abs((long) bz - to.z())));
            }
        }
        if (!distances.containsKey(goal))
            throw new IllegalArgumentException("no road corridor between declared endpoints");
        var raw = new ArrayList<BlockPosition>();
        for (var p = goal; ; p = previous.get(p)) {
            int wx = x.get(p.x()), wz = z.get(p.z());
            raw.add(new BlockPosition(wx, terrain.supportYAt(wx, wz), wz));
            if (p.equals(start)) break;
        }
        Collections.reverse(raw);
        var path = new ArrayList<BlockPosition>();
        path.add(raw.getFirst());
        for (int i = 1; i < raw.size() - 1; i++) {
            var a = raw.get(i - 1);
            var b = raw.get(i);
            var c = raw.get(i + 1);
            if ((a.x() == b.x()) != (b.x() == c.x()) || a.y() != b.y() || b.y() != c.y())
                path.add(b);
        }
        if (!raw.getLast().equals(path.getLast())) path.add(raw.getLast());
        return List.copyOf(path);
    }

    private static boolean clear(
            int ax, int az, int bx, int bz, List<BuildingVolume> boxes, int margin) {
        for (var box : boxes)
            if (Math.min(ax, bx) <= box.max().x() - 1 + margin
                    && Math.max(ax, bx) >= box.min().x() - margin
                    && Math.min(az, bz) <= box.max().z() - 1 + margin
                    && Math.max(az, bz) >= box.min().z() - margin) return false;
        return true;
    }

    private PlanCorridorSearch() {}
}
