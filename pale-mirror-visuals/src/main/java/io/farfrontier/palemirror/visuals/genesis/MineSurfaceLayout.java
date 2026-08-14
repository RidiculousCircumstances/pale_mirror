package io.farfrontier.palemirror.visuals.genesis;

import io.farfrontier.palemirror.api.AuthoredMineRole;
import io.farfrontier.palemirror.api.VisualBounds;
import io.farfrontier.palemirror.api.VisualPoint;
import java.util.List;

/** Shared geometry for terrain surveying and the authored MineSite grammar. */
final class MineSurfaceLayout {
    static final int APRON = 2;
    static final int MAXIMUM_RELIEF = 8;
    static final int MAXIMUM_CUT = 4;
    static final int MAXIMUM_FILL = 4;

    private MineSurfaceLayout() { }

    static List<Pad> pads(AuthoredMineRole role) {
        if (role == AuthoredMineRole.PRIMARY) return List.of(
                new Pad("portal", 0, -10, 23, 19),
                new Pad("crew", -18, -34, 12, 9),
                new Pad("processing", 0, -51, 31, 17),
                new Pad("power", -20, -52, 13, 15),
                new Pad("loading", 19, -34, 14, 9),
                new Pad("maintenance", 18, -52, 12, 10));
        return List.of(
                new Pad("portal", 0, -10, 23, 19),
                new Pad("crew", -18, -34, 12, 9),
                new Pad("dispatch", 19, -34, 14, 13),
                new Pad("processing", 0, -51, 31, 17),
                new Pad("power", -22, -52, 19, 22),
                new Pad("freight", 19, -52, 14, 9));
    }

    static Pad require(AuthoredMineRole role, String id) {
        return pads(role).stream().filter(value -> value.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown MineSite surface pad " + role + ":" + id));
    }

    /** Whole-yard translations preserve the authored relationship between every building. */
    static List<YardOffset> candidateYards() {
        java.util.ArrayList<YardOffset> result = new java.util.ArrayList<>();
        for (int outward : new int[]{0, -8, -16}) {
            for (int right : new int[]{0, 8, -8}) result.add(new YardOffset(right, outward));
        }
        return List.copyOf(result);
    }

    static VisualPoint center(Pad pad, VisualPoint portal, int direction, YardOffset yard) {
        return local(portal, pad.right() + yard.right(), pad.inward() + yard.outward(), 0, direction);
    }

    static VisualPoint local(VisualPoint portal, int right, int inward, int up, int direction) {
        int dx = switch (Math.floorMod(direction, 4)) {
            case 0 -> inward; case 1 -> -right; case 2 -> -inward; default -> right;
        };
        int dz = switch (Math.floorMod(direction, 4)) {
            case 0 -> right; case 1 -> inward; case 2 -> -right; default -> -inward;
        };
        return new VisualPoint(portal.x() + dx, portal.y() + up, portal.z() + dz);
    }

    record Pad(String id, int right, int inward, int width, int depth) {
        Pad {
            if (id == null || id.isBlank() || width < 1 || depth < 1) {
                throw new IllegalArgumentException("MineSite surface pad is invalid");
            }
        }

        VisualPoint center(VisualPoint portal, int direction, int y) {
            VisualPoint horizontal = local(portal, right, inward, 0, direction);
            return new VisualPoint(horizontal.x(), y, horizontal.z());
        }

        VisualBounds bounds(VisualPoint portal, int direction, int y) {
            return bounds(center(portal, direction, y), direction);
        }

        VisualBounds bounds(VisualPoint center, int direction) {
            boolean swap = Math.floorMod(direction, 2) == 1;
            int xSize = swap ? depth : width;
            int zSize = swap ? width : depth;
            return new VisualBounds(new VisualPoint(center.x() - xSize / 2, center.y(), center.z() - zSize / 2),
                    new VisualPoint(center.x() + (xSize - 1) / 2, center.y(),
                            center.z() + (zSize - 1) / 2));
        }
    }

    record YardOffset(int right, int outward) { }
}
