package io.farfrontier.palemirror.internal.world;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import io.farfrontier.palemirror.domain.WorldObjectId;

/**
 * Stable identities for one authored living-region instance.  This stays in
 * NeoForge because it translates a physical observed place into domain object
 * identifiers; the domain only receives the resulting source-neutral ids.
 */
public record RegionBindings(String regionId, WorldObjectId communityId, WorldObjectId primaryMineId,
                             WorldObjectId alternateMineId, WorldObjectId primaryRouteId,
                             WorldObjectId alternateRouteId, WorldObjectId primaryDispatchSiteId,
                             WorldObjectId alternateDispatchSiteId, WorldObjectId receivingSiteId,
                             String alternateDispatchStation, String receivingStation) {
    private static final String NAMESPACE = "pale_mirror:";

    public static RegionBindings forObserved(long worldSeed, WorldObjectId observedPlaceId) {
        String source = worldSeed + ":pale_mirror:iron_frontier:" + observedPlaceId.value();
        String key = UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8)).toString().replace("-", "").substring(0, 12);
        return fromKey("iron_frontier_" + key);
    }

    /** The immutable compatibility mapping for a v27 Ironhill snapshot. */
    public static RegionBindings legacyIronhill() {
        return new RegionBindings(CampaignRegionBootstrapper.IRONHILL_ID, CampaignRegionBootstrapper.IRONHILL,
                CampaignRegionBootstrapper.MINE17, CampaignRegionBootstrapper.RED_VALLEY,
                CampaignRegionBootstrapper.MINE17_ROUTE, CampaignRegionBootstrapper.RED_VALLEY_ROUTE,
                CampaignRegionBootstrapper.MINE17_DISPATCH_SITE, CampaignRegionBootstrapper.RED_VALLEY_DISPATCH_SITE,
                CampaignRegionBootstrapper.IRONHILL_RECEIVING_SITE, CampaignRegionBootstrapper.RED_VALLEY_DISPATCH,
                CampaignRegionBootstrapper.IRONHILL_RECEIVING);
    }

    public static RegionBindings fromRegionId(String regionId) {
        if (CampaignRegionBootstrapper.IRONHILL_ID.equals(regionId)) return legacyIronhill();
        if (!regionId.startsWith(NAMESPACE + "iron_frontier_")) {
            throw new IllegalArgumentException("Unknown living-region id " + regionId);
        }
        return fromKey(regionId.substring(NAMESPACE.length()));
    }

    private static RegionBindings fromKey(String key) {
        String prefix = NAMESPACE + key;
        String display = key.substring("iron_frontier_".length()).toUpperCase(java.util.Locale.ROOT);
        return new RegionBindings(prefix, new WorldObjectId(prefix + "_community"),
                new WorldObjectId(prefix + "_mine"), new WorldObjectId(prefix + "_alternate_ironworks"),
                new WorldObjectId(prefix + "_legacy_route"), new WorldObjectId(prefix + "_alternate_route"),
                new WorldObjectId(prefix + "_mine_dispatch"), new WorldObjectId(prefix + "_alternate_dispatch"),
                new WorldObjectId(prefix + "_receiving"), "PM " + display + " Dispatch",
                "PM " + display + " Receiving");
    }
}
