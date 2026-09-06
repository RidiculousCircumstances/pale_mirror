package io.farfrontier.palemirror.frontier.v3.model;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable terrain-facing support plan for one settlement structure.
 *
 * <p>The structure anchor is its finished floor datum.  The natural support below it belongs
 * to the immutable bootstrap survey, while every cell between that datum and the planned floor
 * is a structure-owned foundation.  This keeps a low surveyed site physical without treating a
 * loaded Minecraft height map or player scaffolding as part of a facility.</p>
 */
public final class SettlementStructureFootprint {
    private SettlementStructureFootprint() { }

    /** All plan-owned support columns: the structural floor plus its declared exterior sills. */
    public static Set<SurfaceAnchor> supportSurfaces(SettlementStructure structure) {
        Objects.requireNonNull(structure, "structure");
        LinkedHashSet<SurfaceAnchor> surfaces = new LinkedHashSet<>();
        int width = width(structure.kind()), depth = depth(structure.kind());
        for (int x = -width / 2; x <= (width - 1) / 2; x++) for (int z = -depth / 2; z <= (depth - 1) / 2; z++) {
            surfaces.add(new SurfaceAnchor(structure.anchor().offset(x, 0, z)));
        }
        switch (structure.kind()) {
            case HALL -> surfaces.addAll(SettlementAccessPort.forHall(structure).ownedSurfaces());
            case DEPOT -> surfaces.addAll(SettlementDepotServicePort.forDepot(structure).ownedAccessSurfaces());
            case WORKSHOP -> surfaces.addAll(SettlementWorkshopServicePort.forWorkshop(structure).ownedAccessSurfaces());
            case INFIRMARY -> surfaces.addAll(SettlementInfirmaryTreatmentPort.forInfirmary(structure).ownedAccessSurfaces());
            default -> { }
        }
        return Set.copyOf(surfaces);
    }

    /** Provider-owned fill below every declared support surface, excluding the finished floor. */
    public static Set<BlockPosition> foundationFill(TerrainSurfacePlan terrain, SettlementStructure structure) {
        Objects.requireNonNull(terrain, "terrain"); Objects.requireNonNull(structure, "structure");
        LinkedHashSet<BlockPosition> fill = new LinkedHashSet<>();
        for (SurfaceAnchor surface : supportSurfaces(structure)) {
            int terrainY = terrain.supportYAt(surface.x(), surface.z());
            if (terrainY >= surface.y()) {
                throw new IllegalArgumentException("surveyed terrain occupies or exceeds structure floor at " + surface.support());
            }
            for (int y = terrainY + 1; y < surface.y(); y++) fill.add(new BlockPosition(surface.x(), y, surface.z()));
        }
        return Set.copyOf(fill);
    }

    /** Chooses one settlement datum from the exact compiled building/sill support columns. */
    public static int settlementDeckY(TerrainSurfacePlan terrain, List<SettlementStructure> provisionalStructures) {
        Objects.requireNonNull(terrain, "terrain"); provisionalStructures = List.copyOf(provisionalStructures);
        if (provisionalStructures.isEmpty()) throw new IllegalArgumentException("settlement requires one structure");
        int highest = Integer.MIN_VALUE;
        for (SettlementStructure structure : provisionalStructures) for (SurfaceAnchor surface : supportSurfaces(structure)) {
            highest = Math.max(highest, terrain.supportYAt(surface.x(), surface.z()));
        }
        for (BlockPosition surface : SettlementLocalCirculation.surfaceCells(provisionalStructures)) {
            highest = Math.max(highest, terrain.supportYAt(surface.x(), surface.z()));
        }
        return Math.addExact(highest, 1);
    }

    public static int width(StructureKind kind) {
        return switch (Objects.requireNonNull(kind, "structure kind")) {
            case HALL, DEPOT -> 8; case FARM -> 9; case WORKSHOP, INFIRMARY -> 7; case HOUSING -> 6;
        };
    }

    public static int depth(StructureKind kind) {
        return switch (Objects.requireNonNull(kind, "structure kind")) {
            case HALL, FARM, WORKSHOP, DEPOT -> 7; case HOUSING, INFIRMARY -> 6;
        };
    }
}
