package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Pure immutable source-site geometry derived from the stable fresh-world bootstrap. */
public final class FrontierResourceSitePlan {
    private static final int FIELD_SIDE = 8;
    /** Leaves a two-cell field edge clear of the Farm shell and the three-wide public route. */
    private static final int FIELD_CLEARANCE = 2;
    /**
     * Resource-site geometry is a pure function of an immutable fresh-world bootstrap.
     *
     * <p>Complete canonical validation may need that geometry on every accepted event. Keeping
     * it in a bounded exact-bootstrap cache avoids reconstructing the same twelve 64-cell field
     * plans without walking the full value graph on every HOT/COLD lookup. A bootstrap is also
     * the owner of its surveyed coordinate frame: test/runtime adapters can construct a
     * translated bootstrap with the same world identity. Sharing a plan across those distinct
     * immutable owners would retain foreign field coordinates. The returned value is immutable
     * and the cache is never canonical world state.</p>
     */
    private static final int MAX_CACHED_BOOTSTRAPS = 64;
    private static final Map<FrontierBootstrap, Map<SubjectId, ResourceSite>> BY_BOOTSTRAP =
            Collections.synchronizedMap(new IdentityHashMap<>());

    private FrontierResourceSitePlan() { }

    public static Map<SubjectId, ResourceSite> compile(FrontierBootstrap bootstrap) {
        Objects.requireNonNull(bootstrap, "bootstrap");
        synchronized (BY_BOOTSTRAP) {
            Map<SubjectId, ResourceSite> cached = BY_BOOTSTRAP.get(bootstrap);
            if (cached != null) return cached;
            // Derived plans have no lifecycle authority. A bounded eviction only rebuilds pure
            // geometry for an exact owner; it must never redirect another coordinate frame.
            if (BY_BOOTSTRAP.size() >= MAX_CACHED_BOOTSTRAPS) BY_BOOTSTRAP.clear();
            Map<SubjectId, ResourceSite> compiled = compileFresh(bootstrap);
            BY_BOOTSTRAP.put(bootstrap, compiled);
            return compiled;
        }
    }

    private static Map<SubjectId, ResourceSite> compileFresh(FrontierBootstrap bootstrap) {
        Map<SubjectId, ResourceSite> sites = new LinkedHashMap<>();
        Set<BlockPosition> occupied = immutableReservations(bootstrap);
        for (Settlement settlement : bootstrap.settlements()) {
            SettlementStructure farm = settlement.structures().stream().filter(structure -> structure.kind() == StructureKind.FARM).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("settlement lacks a farm: " + settlement.id().value()));
            String suffix = settlement.id().value().substring("settlement:".length()); SubjectId id = new SubjectId("site:" + suffix + "-wheat-field");
            ResourceFieldLayout authored = bootstrap.initialFieldLayouts().get(id);
            ResourceSite site = authored == null ? availableField(bootstrap.bounds(), id, settlement, farm, occupied)
                    : new ResourceSite(id, settlement.id(), farm.id(), ResourceSiteKind.WHEAT_FIELD, authored);
            if (site.layout().cells().isEmpty() || site.managedSlots().stream().anyMatch(slot -> !bootstrap.bounds().contains(slot)
                    || occupied.contains(slot)) || site.layout().cells().stream().anyMatch(cell ->
                    !bootstrap.bounds().contains(cell.workstation().support())))
                throw new IllegalArgumentException("resource site has an out-of-bounds or occupied initial layout: " + id.value());
            if (sites.put(id, site) != null) throw new IllegalArgumentException("duplicate resource site: " + id.value());
            occupied.addAll(site.managedSlots());
        }
        if (!sites.keySet().containsAll(bootstrap.initialFieldLayouts().keySet()))
            throw new IllegalArgumentException("initial field layout manifest names an unknown site");
        return Map.copyOf(sites);
    }

    /**
     * Resource sites are independently materialized, but must never claim another immutable
     * plan's cell. This is the bootstrap-only dependency set (not {@link FrontierGrayboxPlan}):
     * the latter needs a full initial state, whose resource lifecycle intentionally depends on
     * this compiler. Keeping the dependency one-way preserves fresh-world construction while
     * using the same owning geometry compilers as the structural baseline.
     */
    private static Set<BlockPosition> immutableReservations(FrontierBootstrap bootstrap) {
        Set<BlockPosition> reserved = new LinkedHashSet<>();
        FrontierRouteNetwork.RouteFootprint routes = FrontierRouteNetwork.footprint(bootstrap, RouteTopology.initial());
        reserved.addAll(routes.foundationCells());
        reserved.addAll(routes.surfaceCells());
        reserved.addAll(FrontierGrayboxPlan.intactOrganOccupancy(bootstrap.hive().organs()));
        for (Settlement settlement : bootstrap.settlements()) {
            reserved.addAll(FrontierSettlementActorSlots.intactStructureOccupancy(bootstrap.terrain(), settlement.structures()));
            SettlementResidentIngressPlan.Plan ingress = SettlementResidentIngressPlan.compile(bootstrap.bounds(), bootstrap.terrain(), settlement,
                    bootstrap.ruleset().facilityCapacity().intactHousingBeds());
            reserved.addAll(ingress.foundationCells());
            ingress.ownedSurfaces().forEach(surface -> reserved.add(surface.support()));
            reserved.addAll(SettlementLocalCirculation.foundationCells(bootstrap.terrain(), settlement));
            reserved.addAll(SettlementLocalCirculation.surfaceCells(settlement));
        }
        return reserved;
    }

    private static ResourceSite availableField(WorldBounds bounds, SubjectId id, Settlement settlement, SettlementStructure farm,
                                               Set<BlockPosition> occupied) {
        for (FacilityFacing side : candidateSides(farm.facing())) {
            ResourceSite candidate = new ResourceSite(id, settlement.id(), farm.id(), ResourceSiteKind.WHEAT_FIELD, initialGrayboxLayout(cropSlots(farm, side)));
            if (candidate.managedSlots().stream().allMatch(bounds::contains) && candidate.managedSlots().stream().noneMatch(occupied::contains)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("resource site has no clear bounded field side for " + farm.id().value());
    }

    /** Current bootstrap choice only; generic field geometry never assumes this rectangle. */
    public static ResourceFieldLayout initialGrayboxLayout(List<BlockPosition> crops) {
        if (crops.size() != FIELD_SIDE * FIELD_SIDE) throw new IllegalArgumentException("graybox bootstrap requires its 8x8 layout");
        int minX = crops.stream().mapToInt(BlockPosition::x).min().orElseThrow();
        int maxX = crops.stream().mapToInt(BlockPosition::x).max().orElseThrow();
        int minZ = crops.stream().mapToInt(BlockPosition::z).min().orElseThrow();
        int maxZ = crops.stream().mapToInt(BlockPosition::z).max().orElseThrow();
        int soilY = crops.getFirst().y() - 1;
        if (maxX - minX != FIELD_SIDE - 1 || maxZ - minZ != FIELD_SIDE - 1
                || crops.stream().anyMatch(crop -> crop.y() != soilY + 1))
            throw new IllegalArgumentException("graybox producer cannot supply irregular field irrigation");
        var cells = new java.util.ArrayList<ResourceFieldLayout.Cell>(crops.size());
        for (int index = 0; index < crops.size(); index++) {
            var soil = new SurfaceAnchor(crops.get(index).offset(0, -1, 0));
            cells.add(new ResourceFieldLayout.Cell(new ResourceFieldLayout.CellId(index + 1L), crops.get(index), soil, soil));
        }
        return new ResourceFieldLayout(1L, crops.size() + 1L, cells,
                List.of(new BlockPosition(minX + 2, soilY, minZ - 1), new BlockPosition(maxX - 1, soilY, minZ - 1),
                        new BlockPosition(minX + 2, soilY, maxZ + 1), new BlockPosition(maxX - 1, soilY, maxZ + 1)));
    }

    /**
     * A farm's exterior orientation is the stable local reference. Prefer the production side
     * opposite its entrance, then turn around the same local frame; a fresh-world compiler must
     * not assume a global east-side field or overwrite a later pedestrian/terrain plan.
     */
    private static List<FacilityFacing> candidateSides(FacilityFacing facing) {
        return switch (facing) {
            case NORTH -> List.of(FacilityFacing.SOUTH, FacilityFacing.EAST, FacilityFacing.WEST, FacilityFacing.NORTH);
            case EAST -> List.of(FacilityFacing.WEST, FacilityFacing.NORTH, FacilityFacing.SOUTH, FacilityFacing.EAST);
            case SOUTH -> List.of(FacilityFacing.NORTH, FacilityFacing.WEST, FacilityFacing.EAST, FacilityFacing.SOUTH);
            case WEST -> List.of(FacilityFacing.EAST, FacilityFacing.SOUTH, FacilityFacing.NORTH, FacilityFacing.WEST);
        };
    }

    private static List<BlockPosition> cropSlots(SettlementStructure farm, FacilityFacing side) {
        java.util.ArrayList<BlockPosition> slots = new java.util.ArrayList<>(ResourceSiteKind.WHEAT_FIELD.cropSlotCount());
        int halfWidth = SettlementStructureFootprint.width(farm.kind()) / 2;
        int halfDepth = SettlementStructureFootprint.depth(farm.kind()) / 2;
        int minX;
        int minZ;
        if (side.x() > 0) {
            minX = farm.anchor().x() + halfWidth + FIELD_CLEARANCE + 1;
            minZ = farm.anchor().z() - FIELD_SIDE / 2 + 1;
        } else if (side.x() < 0) {
            minX = farm.anchor().x() - halfWidth - FIELD_CLEARANCE - FIELD_SIDE;
            minZ = farm.anchor().z() - FIELD_SIDE / 2 + 1;
        } else if (side.z() > 0) {
            minX = farm.anchor().x() - FIELD_SIDE / 2 + 1;
            minZ = farm.anchor().z() + halfDepth + FIELD_CLEARANCE + 1;
        } else {
            minX = farm.anchor().x() - FIELD_SIDE / 2 + 1;
            minZ = farm.anchor().z() - halfDepth - FIELD_CLEARANCE - FIELD_SIDE;
        }
        for (int x = minX; x < minX + FIELD_SIDE; x++) {
            // This stable index is also the worker's physical work order.  A serpentine row
            // order keeps every subsequent crop station adjacent; row-major order would make
            // each row boundary an invisible seven-cell jump or require a second navigator.
            int startZ = (x - minX) % 2 == 0 ? minZ : minZ + FIELD_SIDE - 1;
            int endZ = (x - minX) % 2 == 0 ? minZ + FIELD_SIDE : minZ - 1;
            int step = (x - minX) % 2 == 0 ? 1 : -1;
            for (int z = startZ; z != endZ; z += step) slots.add(new BlockPosition(x, farm.anchor().y(), z));
        }
        return List.copyOf(slots);
    }
}
