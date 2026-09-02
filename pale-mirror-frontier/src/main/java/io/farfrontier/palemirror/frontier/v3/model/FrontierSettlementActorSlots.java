package io.farfrontier.palemirror.frontier.v3.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Pure deterministic street/perimeter placement for exact settlement residents.
 *
 * <p>Canonical actor positions are physical hand-off locations, not abstract population
 * centroids. A slot therefore never occupies an intact structure cell or either of its two
 * required body-clearance cells. Minecraft may still defer a player-altered slot; it may not
 * replace that conflict with a different canonical position.</p>
 */
public final class FrontierSettlementActorSlots {
    private static final int FIRST_RING_RADIUS = 6;
    private static final int RING_INCREMENT = 2;
    private static final int MAX_RING_RADIUS = 256;

    private FrontierSettlementActorSlots() { }

    public static BlockPosition slot(WorldBounds bounds, TerrainSurfacePlan terrain, Settlement settlement, int ordinal) {
        Objects.requireNonNull(terrain, "terrain"); Objects.requireNonNull(settlement, "settlement");
        return slot(bounds, terrain, settlement.anchor(), settlement.structures(), ordinal);
    }

    /**
     * One exact resident home surface from the settlement-owned, capacity-bounded apron.
     * Population processes must supply the retained ruleset capacity; a terrain ring is not a
     * substitute for a planned settlement home when the site deck is elevated.
     */
    public static BlockPosition residentSlot(WorldBounds bounds, TerrainSurfacePlan terrain, Settlement settlement, int housingBeds, int ordinal) {
        Objects.requireNonNull(bounds, "bounds"); Objects.requireNonNull(terrain, "terrain"); Objects.requireNonNull(settlement, "settlement");
        if (ordinal < 0 || ordinal >= housingBeds) throw new IllegalArgumentException("resident placement ordinal is outside housing capacity");
        return SettlementResidentIngressPlan.compile(bounds, terrain, settlement, housingBeds).homeSlots().get(ordinal);
    }

    static List<BlockPosition> residentSlots(WorldBounds bounds, TerrainSurfacePlan terrain, Settlement settlement, int housingBeds, int count) {
        Objects.requireNonNull(bounds, "bounds"); Objects.requireNonNull(terrain, "terrain"); Objects.requireNonNull(settlement, "settlement");
        if (count < 0 || count > housingBeds) throw new IllegalArgumentException("resident placement count is outside housing capacity");
        return SettlementResidentIngressPlan.compile(bounds, terrain, settlement, housingBeds).homeSlots().subList(0, count);
    }

    public static BlockPosition slot(WorldBounds bounds, TerrainSurfacePlan terrain, BlockPosition anchor, List<SettlementStructure> structures, int ordinal) {
        Objects.requireNonNull(bounds, "bounds"); Objects.requireNonNull(terrain, "terrain"); Objects.requireNonNull(anchor, "anchor"); Objects.requireNonNull(structures, "structures");
        if (ordinal < 0) throw new IllegalArgumentException("resident placement ordinal must not be negative");
        return collect(bounds, terrain, anchor, intactStructureOccupancy(terrain, structures), ordinal + 1).get(ordinal);
    }

    static List<BlockPosition> slots(WorldBounds bounds, TerrainSurfacePlan terrain, BlockPosition anchor, List<SettlementStructure> structures, int count) {
        Objects.requireNonNull(bounds, "bounds"); Objects.requireNonNull(terrain, "terrain"); Objects.requireNonNull(anchor, "anchor"); Objects.requireNonNull(structures, "structures");
        if (count < 0) throw new IllegalArgumentException("resident placement count must not be negative");
        return collect(bounds, terrain, anchor, intactStructureOccupancy(terrain, structures), count);
    }

    /** Whether an immutable settlement plan leaves this exact support column clear for one body. */
    static boolean clearFloor(WorldBounds bounds, TerrainSurfacePlan terrain, Settlement settlement, BlockPosition position) {
        Objects.requireNonNull(terrain, "terrain"); Objects.requireNonNull(settlement, "settlement");
        return clearFloor(bounds, terrain, settlement.anchor(), settlement.structures(), position);
    }

    static boolean clearFloor(WorldBounds bounds, TerrainSurfacePlan terrain, BlockPosition anchor, List<SettlementStructure> structures, BlockPosition position) {
        Objects.requireNonNull(bounds, "bounds"); Objects.requireNonNull(terrain, "terrain"); Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(structures, "structures"); Objects.requireNonNull(position, "position");
        return clearFloor(bounds, intactStructureOccupancy(terrain, structures), position);
    }

    /** One exact immutable occupancy view may serve every candidate of the same pure compilation. */
    static Set<BlockPosition> intactStructureOccupancy(TerrainSurfacePlan terrain, List<SettlementStructure> structures) {
        return FrontierGrayboxPlan.intactStructureOccupancy(terrain, structures);
    }

    static List<BlockPosition> slots(WorldBounds bounds, TerrainSurfacePlan terrain, BlockPosition anchor, Set<BlockPosition> structureCells, int count) {
        Objects.requireNonNull(bounds, "bounds"); Objects.requireNonNull(terrain, "terrain"); Objects.requireNonNull(anchor, "anchor"); Objects.requireNonNull(structureCells, "structure cells");
        if (count < 0) throw new IllegalArgumentException("resident placement count must not be negative");
        return collect(bounds, terrain, anchor, structureCells, count);
    }

    static boolean clearFloor(WorldBounds bounds, Set<BlockPosition> structureCells, BlockPosition position) {
        Objects.requireNonNull(bounds, "bounds"); Objects.requireNonNull(structureCells, "structure cells"); Objects.requireNonNull(position, "position");
        return traversable(bounds, structureCells, position);
    }

    private static List<BlockPosition> collect(WorldBounds bounds, TerrainSurfacePlan terrain, BlockPosition anchor, Set<BlockPosition> structureCells, int count) {
        List<BlockPosition> accepted = new ArrayList<>(count);
        if (count == 0) return List.of();
        for (int radius = FIRST_RING_RADIUS; radius <= MAX_RING_RADIUS; radius += RING_INCREMENT) {
            for (int x = -radius; x <= radius; x += RING_INCREMENT) {
                BlockPosition candidate = terrainSurface(terrain, anchor.x() + x, anchor.z() - radius);
                if (traversable(bounds, structureCells, candidate)) { accepted.add(candidate); if (accepted.size() == count) return List.copyOf(accepted); }
            }
            for (int z = -radius + RING_INCREMENT; z <= radius; z += RING_INCREMENT) {
                BlockPosition candidate = terrainSurface(terrain, anchor.x() + radius, anchor.z() + z);
                if (traversable(bounds, structureCells, candidate)) { accepted.add(candidate); if (accepted.size() == count) return List.copyOf(accepted); }
            }
            for (int x = radius - RING_INCREMENT; x >= -radius; x -= RING_INCREMENT) {
                BlockPosition candidate = terrainSurface(terrain, anchor.x() + x, anchor.z() + radius);
                if (traversable(bounds, structureCells, candidate)) { accepted.add(candidate); if (accepted.size() == count) return List.copyOf(accepted); }
            }
            for (int z = radius - RING_INCREMENT; z > -radius; z -= RING_INCREMENT) {
                BlockPosition candidate = terrainSurface(terrain, anchor.x() - radius, anchor.z() + z);
                if (traversable(bounds, structureCells, candidate)) { accepted.add(candidate); if (accepted.size() == count) return List.copyOf(accepted); }
            }
        }
        throw new IllegalStateException("frontier settlement has no bounded free resident placement slots for count " + count);
    }

    private static boolean traversable(WorldBounds bounds, Set<BlockPosition> structureCells, BlockPosition position) {
        return bounds.contains(position) && !structureCells.contains(position) && !structureCells.contains(position.offset(0, 1, 0))
                && !structureCells.contains(position.offset(0, 2, 0));
    }

    private static BlockPosition terrainSurface(TerrainSurfacePlan terrain, int x, int z) {
        return new BlockPosition(x, Math.addExact(terrain.supportYAt(x, z), 1), z);
    }
}
