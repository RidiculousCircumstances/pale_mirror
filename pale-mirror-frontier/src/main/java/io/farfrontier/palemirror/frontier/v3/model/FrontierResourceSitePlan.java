package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;

/** Pure immutable source-site geometry derived from the stable fresh-world bootstrap. */
public final class FrontierResourceSitePlan {
    private static final int FIELD_SIDE = 8;
    /** Leaves a two-cell field edge clear of the Farm shell and the three-wide public route. */
    private static final int FIELD_CLEARANCE = 2;
    /**
     * Resource-site geometry is a pure function of an immutable fresh-world bootstrap.
     *
     * <p>Complete canonical validation may need that geometry on every accepted event. Keeping
     * it in a weak, derived cache avoids reconstructing the same twelve 64-cell field plans,
     * without making the cache a source of world state or retaining retired worlds. The returned
     * value is immutable, so callers cannot alter a later validation through this cache.</p>
     */
    private static final Map<FrontierBootstrap, Map<SubjectId, ResourceSite>> BY_BOOTSTRAP =
            Collections.synchronizedMap(new WeakHashMap<>());

    private FrontierResourceSitePlan() { }

    public static Map<SubjectId, ResourceSite> compile(FrontierBootstrap bootstrap) {
        Objects.requireNonNull(bootstrap, "bootstrap");
        synchronized (BY_BOOTSTRAP) {
            return BY_BOOTSTRAP.computeIfAbsent(bootstrap, FrontierResourceSitePlan::compileFresh);
        }
    }

    private static Map<SubjectId, ResourceSite> compileFresh(FrontierBootstrap bootstrap) {
        Map<SubjectId, ResourceSite> sites = new LinkedHashMap<>();
        Set<BlockPosition> occupied = immutableReservations(bootstrap);
        for (Settlement settlement : bootstrap.settlements()) {
            SettlementStructure farm = settlement.structures().stream().filter(structure -> structure.kind() == StructureKind.FARM).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("settlement lacks a farm: " + settlement.id().value()));
            String suffix = settlement.id().value().substring("settlement:".length()); SubjectId id = new SubjectId("site:" + suffix + "-wheat-field");
            ResourceSite site = availableField(bootstrap.bounds(), id, settlement, farm, occupied);
            if (sites.put(id, site) != null) throw new IllegalArgumentException("duplicate resource site: " + id.value());
            occupied.addAll(site.managedSlots());
        }
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
            ResourceSite candidate = new ResourceSite(id, settlement.id(), farm.id(), ResourceSiteKind.WHEAT_FIELD, cropSlots(farm, side));
            if (candidate.managedSlots().stream().allMatch(bounds::contains) && candidate.managedSlots().stream().noneMatch(occupied::contains)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("resource site has no clear bounded field side for " + farm.id().value());
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
            for (int z = minZ; z < minZ + FIELD_SIDE; z++) slots.add(new BlockPosition(x, farm.anchor().y(), z));
        }
        return List.copyOf(slots);
    }
}
