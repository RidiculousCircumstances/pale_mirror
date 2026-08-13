package io.farfrontier.palemirror.visuals.genesis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.farfrontier.palemirror.api.AuthoredRegionSeed;
import io.farfrontier.palemirror.api.VisualPoint;
import java.util.List;
import org.junit.jupiter.api.Test;

class RegionPlacementProfileTest {
    @Test void ironProfileOwnsTheExistingMountainContract() {
        RegionPlacementProfile profile = RegionPlacementProfiles.IRON_FRONTIER;

        assertEquals("pale_mirror:iron_frontier", profile.id());
        assertEquals(new DistanceBand(160, 320), profile.requireSite(RegionPlacementProfiles.PRIMARY_MINE)
                .distanceFromSettlement());
        assertEquals(new DistanceBand(320, 560), profile.requireSite(RegionPlacementProfiles.ALTERNATE_MINE)
                .distanceFromSettlement());
        assertEquals(24, profile.requireSite(RegionPlacementProfiles.PRIMARY_MINE).exactValidationBudget());
        assertEquals(72, profile.settlementTerrain().surveyRadius());
        assertEquals(0, profile.settlementTerrain().minimumLandscapeScore());
        assertEquals(4, profile.route().horizontalBlocksPerVerticalBlock());
        assertEquals(true, profile.route().preferDryLand());
        assertEquals(96, profile.route().searchCorridorHalfWidth());
        assertEquals(12_000, profile.route().searchBudget());
        assertEquals(24, profile.route().maximumWaterSpan());
        assertEquals(2, profile.route().bridgeClearance());
        assertEquals(160, profile.search().maximumSurveyCandidates());
        assertEquals(154, profile.search().reserveCandidateCount());
        assertEquals(48, profile.search().remoteCandidatesPerRegion(11));
        assertEquals(512, profile.search().remoteCandidatesPerRegion(24));
        assertEquals(6, profile.search().exactSettlementCandidatesPerRegion(11));
        assertEquals(12, profile.search().exactSettlementCandidatesPerRegion(24));
        assertEquals(List.of(192, 256, 320),
                profile.search().landscapeProjectionDistances());
        assertEquals(HorizontalOffset.ORIGIN, profile.search().settlementRefinementOffsets().getFirst());
        assertThrows(UnsupportedOperationException.class, () -> profile.requiredSites().clear());
    }

    @Test void ironLayoutConsumesProfileDistanceBandsInsteadOfHiddenConstants() {
        RegionPlacementProfile profile = profileWithDistances(new DistanceBand(200, 200),
                new DistanceBand(400, 400));
        FrontierRegionPlanner planner = new FrontierRegionPlanner(profile);

        AuthoredRegionSeed seed = planner.plan(91L, 0, new VisualPoint(0, 70, 0), FrontierClimate.TEMPERATE);

        assertEquals(200, horizontalDistance(seed.anchor(), seed.primaryMine()));
        assertEquals(400, horizontalDistance(seed.anchor(), seed.alternateMine()));
    }

    @Test void invalidCrossSiteReferenceFailsClosedAtProfileConstruction() {
        SitePlacementRequirement primary = site("primary", new DistanceBand(100, 200), "missing");
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () ->
                new RegionPlacementProfile("test:invalid", "invalid", search(), settlement(), List.of(primary),
                        new RoutePlacementRequirement("settlement", "primary", true,
                                4, 96, 12_000, 24, 2, 250)));
        assertEquals("unknown cardinal-sector reference missing", failure.getMessage());
    }

    @Test void ironPlannerRejectsAProfileWhoseRouteTargetsAnotherSite() {
        RegionPlacementProfile base = profileWithDistances(new DistanceBand(200, 200),
                new DistanceBand(400, 400));
        RegionPlacementProfile invalid = new RegionPlacementProfile(base.id(), base.addressSalt(), base.search(),
                base.settlementTerrain(), base.requiredSites(),
                new RoutePlacementRequirement("settlement", RegionPlacementProfiles.ALTERNATE_MINE,
                        true, 4, 96, 12_000, 24, 2, 250));

        assertThrows(IllegalArgumentException.class, () -> new FrontierRegionPlanner(invalid));
    }

    private static RegionPlacementProfile profileWithDistances(DistanceBand primary, DistanceBand alternate) {
        return new RegionPlacementProfile("test:iron", "iron", search(), settlement(), List.of(
                site(RegionPlacementProfiles.PRIMARY_MINE, primary, ""),
                site(RegionPlacementProfiles.ALTERNATE_MINE, alternate, RegionPlacementProfiles.PRIMARY_MINE)),
                new RoutePlacementRequirement("settlement", RegionPlacementProfiles.PRIMARY_MINE,
                        true, 4, 96, 12_000, 24, 2, 250));
    }

    private static SitePlacementRequirement site(String role, DistanceBand distance, String separateFrom) {
        return new SitePlacementRequirement(role, distance, separateFrom,
                List.of(distance.minimum()), List.of(0), 1, 1,
                RegionPlacementProfiles.IRON_FRONTIER.requireSite(RegionPlacementProfiles.PRIMARY_MINE).terrain());
    }

    private static RegionPlacementProfile.SearchPolicy search() {
        return RegionPlacementProfiles.IRON_FRONTIER.search();
    }

    private static SettlementTerrainPolicy settlement() {
        return RegionPlacementProfiles.IRON_FRONTIER.settlementTerrain();
    }

    private static int horizontalDistance(VisualPoint first, VisualPoint second) {
        int dx = first.x() - second.x();
        int dz = first.z() - second.z();
        return (int) Math.round(Math.sqrt((long) dx * dx + (long) dz * dz));
    }
}
