package io.farfrontier.palemirror.visuals.genesis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.farfrontier.palemirror.api.VisualPoint;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class FrontierRegionBatchPlannerTest {
    @Test void rejectsIslandCenterAndUsesDryReserveCandidate() {
        List<FrontierSiteSelector.SelectedSite> sites = List.of(site(1000, false), site(3000, false));
        AtomicInteger resolutions = new AtomicInteger();
        MineAnchorResolver resolver = (requirement, candidates) -> {
            if (resolutions.getAndIncrement() == 0) {
                throw new DryMineSiteUnavailableException("island");
            }
            return new MountainMineAnchor(candidates.getFirst(), 0);
        };

        FrontierRegionBatchPlanner.Result result = new FrontierRegionBatchPlanner().plan(42L, 1,
                RegionPlacementProfiles.IRON_FRONTIER, sites,
                new FrontierRegionPlanner(), resolver,
                FrontierRegionPlanner::gradedManhattanRail);

        assertEquals(1, result.manifests().size());
        assertEquals(3000, result.manifests().getFirst().anchor().x());
        assertEquals(1, result.rejectedRegionCandidates());
    }

    @Test void exhaustedReserveFailsClosed() {
        MineAnchorResolver resolver = (requirement, candidates) -> {
            throw new DryMineSiteUnavailableException("water");
        };
        assertThrows(IllegalStateException.class, () -> new FrontierRegionBatchPlanner().plan(42L, 1,
                RegionPlacementProfiles.IRON_FRONTIER, List.of(site(1000)), new FrontierRegionPlanner(), resolver,
                FrontierRegionPlanner::gradedManhattanRail));
    }

    @Test void replacesInvalidNearCenterBeforeConsideringRemoteCenters() {
        List<FrontierSiteSelector.SelectedSite> sites = List.of(site(1000, true), site(1800, true),
                site(3000, false));
        AtomicInteger resolutions = new AtomicInteger();
        MineAnchorResolver resolver = (requirement, candidates) -> {
            if (resolutions.getAndIncrement() == 0) throw new DryMineSiteUnavailableException("island");
            return new MountainMineAnchor(candidates.getFirst(), 0);
        };

        FrontierRegionBatchPlanner.Result result = new FrontierRegionBatchPlanner().plan(42L, 1,
                RegionPlacementProfiles.IRON_FRONTIER, sites,
                new FrontierRegionPlanner(), resolver,
                FrontierRegionPlanner::gradedManhattanRail);
        assertEquals(1800, result.manifests().getFirst().anchor().x());
    }

    @Test void rangeAcceptsMinimumWhenTargetCannotBeMet() {
        List<FrontierSiteSelector.SelectedSite> sites = List.of(site(1000), site(3000), site(5000));
        AtomicInteger resolutions = new AtomicInteger();
        MineAnchorResolver resolver = (requirement, candidates) -> {
            if (resolutions.incrementAndGet() > 4) throw new DryMineSiteUnavailableException("mountain unavailable");
            return new MountainMineAnchor(candidates.getFirst(), 0);
        };

        FrontierRegionBatchPlanner.Result result = new FrontierRegionBatchPlanner().plan(42L,
                new RegionCountRange(2, 3, 4), RegionPlacementProfiles.IRON_FRONTIER, sites,
                new FrontierRegionPlanner(), resolver, FrontierRegionPlanner::gradedManhattanRail);

        assertEquals(2, result.manifests().size());
        assertEquals(false, result.targetMet());
    }

    @Test void rangeFailsBelowMinimum() {
        MineAnchorResolver resolver = (requirement, candidates) -> {
            throw new DryMineSiteUnavailableException("mountain unavailable");
        };
        assertThrows(IllegalStateException.class, () -> new FrontierRegionBatchPlanner().plan(42L,
                new RegionCountRange(2, 3, 4), RegionPlacementProfiles.IRON_FRONTIER,
                List.of(site(1000), site(3000)), new FrontierRegionPlanner(), resolver,
                FrontierRegionPlanner::gradedManhattanRail));
    }

    @Test void finalSpacingAppliesOnlyBetweenAcceptedRegions() {
        MineAnchorResolver resolver = (requirement, candidates) -> new MountainMineAnchor(candidates.getFirst(), 0);
        FrontierRegionBatchPlanner.Result result = new FrontierRegionBatchPlanner().plan(42L,
                new RegionCountRange(2, 2, 2), RegionPlacementProfiles.IRON_FRONTIER,
                List.of(site(1000), site(1500), site(3000)), new FrontierRegionPlanner(), resolver,
                FrontierRegionPlanner::gradedManhattanRail, 1000);

        assertEquals(List.of(1000, 3000), result.manifests().stream().map(seed -> seed.anchor().x()).toList());
        assertEquals(1, result.spacingRejectedCandidates());
    }

    private static FrontierSiteSelector.SelectedSite site(int x) {
        return site(x, false);
    }

    private static FrontierSiteSelector.SelectedSite site(int x, boolean near) {
        return new FrontierSiteSelector.SelectedSite(new TerrainCandidate(new VisualPoint(x, 70, 0),
                0, 0, 0, 0), FrontierClimate.TEMPERATE, near);
    }
}
