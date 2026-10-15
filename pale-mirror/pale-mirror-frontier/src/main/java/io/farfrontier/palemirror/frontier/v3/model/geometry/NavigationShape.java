package io.farfrontier.palemirror.frontier.v3.model.geometry;

import java.util.List;

/** Immutable block-local collision facts, with no Minecraft objects or resource/work policy. */
public record NavigationShape(List<Box> boxes) {
    public static final int MAX_BOXES = 256;
    public static final NavigationShape EMPTY = new NavigationShape(List.of());
    public static final NavigationShape FULL = new NavigationShape(List.of(new Box(0, 0, 0, 1, 1, 1)));
    public NavigationShape {
        boxes = List.copyOf(boxes);
        if (boxes.size() > MAX_BOXES) throw new IllegalArgumentException("collision shape exceeds bounded box count");
    }
    public java.util.OptionalDouble topAt(double x, double z) {
        return boxes.stream().filter(box -> box.covers(x, z)).mapToDouble(Box::maxY).max();
    }
    /** Bounds may extend into adjacent cells (fences, moving parts). Never silently clip them. */
    public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        public Box {
            if (!finite(minX) || !finite(minY) || !finite(minZ) || !finite(maxX) || !finite(maxY) || !finite(maxZ)
                    || minX >= maxX || minY >= maxY || minZ >= maxZ)
                throw new IllegalArgumentException("invalid bounded collision box");
        }
        private static boolean finite(double value) { return Double.isFinite(value) && value >= -1 && value <= 2; }
        public boolean overlaps(double x0, double y0, double z0, double x1, double y1, double z1) {
            return maxX > x0 + 1e-7 && minX < x1 - 1e-7 && maxY > y0 + 1e-7
                    && minY < y1 - 1e-7 && maxZ > z0 + 1e-7 && minZ < z1 - 1e-7;
        }
        public boolean covers(double x, double z) { return x >= minX && x < maxX && z >= minZ && z < maxZ; }
    }
}
