package io.farfrontier.palemirror.frontier.v3.model;

import java.util.List;
import java.util.Objects;

/**
 * Pure deterministic street/perimeter placement for exact settlement residents.
 *
 * <p>Canonical actor positions are physical hand-off locations, not abstract population
 * centroids. A slot therefore never occupies an intact structure cell or either of its two
 * required body-clearance cells. Minecraft may still defer a player-altered slot; it may not
 * replace that conflict with a different canonical position.</p>
 */
final class FrontierSettlementActorSlots {
    private static final int FIRST_RING_RADIUS = 6;
    private static final int RING_INCREMENT = 2;
    private static final int MAX_RING_RADIUS = 256;

    private FrontierSettlementActorSlots() { }

    static BlockPosition slot(WorldBounds bounds, Settlement settlement, int ordinal) {
        Objects.requireNonNull(settlement, "settlement");
        return slot(bounds, settlement.anchor(), settlement.structures(), ordinal);
    }

    static BlockPosition slot(WorldBounds bounds, BlockPosition anchor, List<SettlementStructure> structures, int ordinal) {
        Objects.requireNonNull(bounds, "bounds"); Objects.requireNonNull(anchor, "anchor"); Objects.requireNonNull(structures, "structures");
        if (ordinal < 0) throw new IllegalArgumentException("resident placement ordinal must not be negative");
        int accepted = 0;
        for (int radius = FIRST_RING_RADIUS; radius <= MAX_RING_RADIUS; radius += RING_INCREMENT) {
            for (int x = -radius; x <= radius; x += RING_INCREMENT) {
                BlockPosition candidate = anchor.offset(x, 0, -radius);
                if (traversable(bounds, structures, candidate) && accepted++ == ordinal) return candidate;
            }
            for (int z = -radius + RING_INCREMENT; z <= radius; z += RING_INCREMENT) {
                BlockPosition candidate = anchor.offset(radius, 0, z);
                if (traversable(bounds, structures, candidate) && accepted++ == ordinal) return candidate;
            }
            for (int x = radius - RING_INCREMENT; x >= -radius; x -= RING_INCREMENT) {
                BlockPosition candidate = anchor.offset(x, 0, radius);
                if (traversable(bounds, structures, candidate) && accepted++ == ordinal) return candidate;
            }
            for (int z = radius - RING_INCREMENT; z > -radius; z -= RING_INCREMENT) {
                BlockPosition candidate = anchor.offset(-radius, 0, z);
                if (traversable(bounds, structures, candidate) && accepted++ == ordinal) return candidate;
            }
        }
        throw new IllegalStateException("frontier settlement has no bounded free resident placement slot for ordinal " + ordinal);
    }

    private static boolean traversable(WorldBounds bounds, List<SettlementStructure> structures, BlockPosition position) {
        return bounds.contains(position) && structures.stream().noneMatch(structure ->
                FrontierGrayboxPlan.intactStructureCell(structure, position) != null
                        || FrontierGrayboxPlan.intactStructureCell(structure, position.offset(0, 1, 0)) != null
                        || FrontierGrayboxPlan.intactStructureCell(structure, position.offset(0, 2, 0)) != null);
    }
}
