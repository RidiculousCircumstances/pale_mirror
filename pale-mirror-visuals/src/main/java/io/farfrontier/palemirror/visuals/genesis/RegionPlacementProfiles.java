package io.farfrontier.palemirror.visuals.genesis;

import java.util.List;

/** Built-in placement profiles. Layout/content grammars consume these contracts but do not own them. */
public final class RegionPlacementProfiles {
    public static final String PRIMARY_MINE = "primary_mine";
    public static final String ALTERNATE_MINE = "alternate_mine";

    public static final RegionPlacementProfile IRON_FRONTIER = new RegionPlacementProfile(
            "pale_mirror:iron_frontier",
            "iron_frontier",
            new RegionPlacementProfile.SearchPolicy(64, 16, 900, 3_000, 1_024, 1_000, 1, 3,
                    new RegionPlacementProfile.SearchBand(512, new DistanceBand(1_024, 4_096)),
                    new RegionPlacementProfile.SearchBand(2_048, new DistanceBand(2_600, Integer.MAX_VALUE)),
                    48, 24, 512, 6, 12, List.of(192, 256, 320),
                    List.of(HorizontalOffset.ORIGIN)),
            new SettlementTerrainPolicy(SettlementTerrainPolicy.LandscapeAffinity.MOUNTAIN_FOOTHILL,
                    72, true, 8, 24, 0),
            List.of(
                    new SitePlacementRequirement(PRIMARY_MINE, new DistanceBand(160, 320), "",
                            List.of(192, 256, 320),
                            List.of(0, -64, 64, -128, 128), 12, 24, mountainMine()),
                    new SitePlacementRequirement(ALTERNATE_MINE, new DistanceBand(320, 560), PRIMARY_MINE,
                            List.of(384, 480, 560),
                            List.of(0, -64, 64, -128, 128), 12, 24, mountainMine())),
            new RoutePlacementRequirement("settlement", PRIMARY_MINE, 3, List.of(-32, 0, 32),
                    true, 4));

    private RegionPlacementProfiles() { }

    private static SiteTerrainPolicy mountainMine() {
        return new SiteTerrainPolicy(SiteTerrainPolicy.Feature.MOUNTAIN_FACE, true, 8, 4,
                List.of(16, 32, 48), 16, 8,
                List.of(new SiteTerrainPolicy.RiseSample(16, 8),
                        new SiteTerrainPolicy.RiseSample(32, 20),
                        new SiteTerrainPolicy.RiseSample(48, 32)));
    }
}
