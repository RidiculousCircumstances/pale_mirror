package io.farfrontier.palemirror.frontier.v3.model;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Field support/occupancy facts shared with the current route provider and legacy route replay. */
public final class ResourceSiteHarvestKnownGeometry {
    private ResourceSiteHarvestKnownGeometry() { }

    public static Set<BlockPosition> occupiedBodies(FrontierBootstrap bootstrap, ResourceSite site) {
        Objects.requireNonNull(bootstrap, "field navigation bootstrap");
        Objects.requireNonNull(site, "field navigation site");
        Set<BlockPosition> blocked = KnownPedestrianRouteKnowledge.staticOccupancy(bootstrap);
        blocked.addAll(site.irrigationSlots());
        // Field supports remain pedestrian surfaces even after their crop outcome changes.
        return blocked;
    }

    /** The real terrain support, except for declared settlement ground and field farmland. */
    public static BoundedPedestrianApproach.SurveyedSurface surveyedSupports(FrontierBootstrap bootstrap, ResourceSite site) {
        Objects.requireNonNull(bootstrap, "field navigation bootstrap");
        Objects.requireNonNull(site, "field navigation site");
        Map<Long, SurfaceAnchor> fieldSurfaces = new HashMap<>();
        for (BlockPosition crop : site.cropSlots())
            fieldSurfaces.put(column(crop.x(), crop.z()), new SurfaceAnchor(crop.offset(0, -1, 0)));
        Map<TerrainColumn, SurfaceAnchor> localGround = SettlementPedestrianGround.localSupports(bootstrap, site.settlementId());
        return (x, z) -> fieldSurfaces.getOrDefault(column(x, z),
                SettlementPedestrianGround.surveyedSupport(bootstrap, localGround, x, z));
    }

    private static long column(int x, int z) {
        return (Integer.toUnsignedLong(x) << 32) | Integer.toUnsignedLong(z);
    }
}
