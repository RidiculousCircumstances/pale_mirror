package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import java.util.List;
import java.util.Objects;

/** Immutable polyline geometry only. No actor state, terrain permission, route
 * choice or progress authority. Equal nearest segments prefer the earliest one. */
public final class RouteSpatialIndex {
    private static final int LEAF_SEGMENTS = 32;
    private record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        double distance(ActorPositionView.TravelPoint point) {
            double x = Math.max(0, Math.max(minX - point.x(), point.x() - maxX));
            double y = Math.max(0, Math.max(minY - point.y(), point.y() - maxY));
            double z = Math.max(0, Math.max(minZ - point.z(), point.z() - maxZ));
            return Math.nextDown(x * x + y * y + z * z);
        }
        boolean encloses(double x, double y, double z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }
        Box union(Box other) {
            return new Box(Math.min(minX, other.minX), Math.min(minY, other.minY), Math.min(minZ, other.minZ),
                    Math.max(maxX, other.maxX), Math.max(maxY, other.maxY), Math.max(maxZ, other.maxZ));
        }
    }
    private record Node(int start, int end, Box box, Node left, Node right) { }
    private static final class Nearest {
        int segment;
        double distance = Double.POSITIVE_INFINITY, progress;
    }
    private final List<SurfaceAnchor> route;
    private final double[] travelled;
    private final Node root;

    public RouteSpatialIndex(List<SurfaceAnchor> route) {
        route = List.copyOf(Objects.requireNonNull(route)); this.route = route;
        if (route.isEmpty()) throw new IllegalArgumentException("route geometry needs at least one node");
        travelled = new double[route.size()];
        for (int index = 0; index < route.size() - 1; index++) {
            var a = route.get(index); var b = route.get(index + 1);
            double x = (double) b.x() - a.x(), y = (double) b.y() - a.y(), z = (double) b.z() - a.z();
            travelled[index + 1] = travelled[index] + Math.sqrt(x * x + y * y + z * z);
        }
        root = build(0, route.size() - 1);
    }
    private Node build(int start, int end) {
        if (end - start > LEAF_SEGMENTS) {
            int middle = start + (end - start) / 2;
            var left = build(start, middle); var right = build(middle, end);
            return new Node(start, end, left.box().union(right.box()), left, right);
        }
        double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
        double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
        for (int index = start; index <= end; index++) {
            var point = route.get(index);
            double x = point.x() + 0.5, y = point.y() + 1.0, z = point.z() + 0.5;
            minX = Math.min(minX, x); minY = Math.min(minY, y); minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); maxZ = Math.max(maxZ, z);
        }
        return new Node(start, end, new Box(minX, minY, minZ, maxX, maxY, maxZ), null, null);
    }
    public double projection(ActorPositionView.TravelPoint position) {
        Objects.requireNonNull(position);
        var nearest = new Nearest(); project(root, position, nearest); return nearest.progress;
    }
    private void project(Node node, ActorPositionView.TravelPoint position, Nearest nearest) {
        if (node.box().distance(position) > nearest.distance) return;
        if (node.left() != null) {
            var first = node.left(); var second = node.right();
            if (second.box().distance(position) < first.box().distance(position)) { first = node.right(); second = node.left(); }
            project(first, position, nearest); project(second, position, nearest); return;
        }
        for (int index = node.start(); index < node.end(); index++) {
            var a = route.get(index); var b = route.get(index + 1);
            double ax = a.x() + 0.5, ay = a.y() + 1.0, az = a.z() + 0.5;
            double dx = (double) b.x() - a.x(), dy = (double) b.y() - a.y(), dz = (double) b.z() - a.z();
            double lengthSquared = dx * dx + dy * dy + dz * dz;
            double fraction = lengthSquared == 0 ? 0 : Math.clamp(
                    ((position.x() - ax) * dx + (position.y() - ay) * dy + (position.z() - az) * dz) / lengthSquared, 0, 1);
            double x = ax + fraction * dx - position.x(), y = ay + fraction * dy - position.y(), z = az + fraction * dz - position.z();
            double candidate = x * x + y * y + z * z;
            if (candidate < nearest.distance || candidate == nearest.distance && index < nearest.segment) {
                nearest.distance = candidate; nearest.segment = index;
                nearest.progress = travelled[index] + fraction * Math.sqrt(lengthSquared);
            }
        }
    }
    public boolean containsNode(ActorPositionView.TravelPoint point) {
        Objects.requireNonNull(point); return contains(root, point.x(), point.y(), point.z());
    }
    public boolean containsNode(SurfaceAnchor surface) {
        Objects.requireNonNull(surface); return contains(root, surface.x() + 0.5, surface.y() + 1.0, surface.z() + 0.5);
    }
    private boolean contains(Node node, double x, double y, double z) {
        if (!node.box().encloses(x, y, z)) return false;
        if (node.left() != null) return contains(node.left(), x, y, z) || contains(node.right(), x, y, z);
        for (int index = node.start(); index <= node.end(); index++) {
            var surface = route.get(index);
            if (Double.compare(surface.x() + 0.5, x) == 0 && Double.compare(surface.y() + 1.0, y) == 0
                    && Double.compare(surface.z() + 0.5, z) == 0) return true;
        }
        return false;
    }
}
