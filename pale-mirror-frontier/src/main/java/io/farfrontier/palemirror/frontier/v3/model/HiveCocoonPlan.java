package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Objects;

/** Immutable nine-slot interior layout shared by cocoon custody and graybox projection. */
public final class HiveCocoonPlan {
    private HiveCocoonPlan() { }

    /** The physical cocoon block, one cell above the HIBERNACULUM's owned inner floor. */
    public static BlockPosition cocoonCell(HiveOrgan hibernaculum, HiveCocoonSlot slot) {
        requireMatches(hibernaculum, slot);
        int localX = Math.floorMod(slot.index(), 3) - 1;
        int localZ = Math.floorDiv(slot.index(), 3) - 1;
        return hibernaculum.anchor().offset(localX, 1, localZ);
    }

    /** Exact inner floor onto which an awakened body is released if its cocoon breaks. */
    public static SurfaceAnchor wakingSurface(HiveOrgan hibernaculum, HiveCocoonSlot slot) {
        return new SurfaceAnchor(cocoonCell(hibernaculum, slot).offset(0, -1, 0));
    }

    private static void requireMatches(HiveOrgan hibernaculum, HiveCocoonSlot slot) {
        Objects.requireNonNull(hibernaculum, "hibernaculum");
        Objects.requireNonNull(slot, "cocoon slot");
        if (hibernaculum.kind() != HiveOrganKind.HIBERNACULUM || !hibernaculum.id().equals(slot.hibernaculumId())) {
            throw new IllegalArgumentException("cocoon slot does not belong to its HIBERNACULUM");
        }
    }
}
