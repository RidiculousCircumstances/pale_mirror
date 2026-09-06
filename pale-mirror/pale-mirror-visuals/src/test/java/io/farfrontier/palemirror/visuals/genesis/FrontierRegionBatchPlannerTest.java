package io.farfrontier.palemirror.visuals.genesis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test void parallelCandidateWavesProduceTheSequentialManifestOrder() {
        List<FrontierSiteSelector.SelectedSite> sites = List.of(
                site(1000), site(3000), site(5000), site(7000), site(9000));
        MineAnchorResolver mine = (requirement, candidates) -> new MountainMineAnchor(candidates.getFirst(), 0);
        SettlementLayoutResolver layouts = (source, anchor, climate, direction, terrain) -> {
            if (anchor.x() == 1000 || anchor.x() == 5000) {
                throw new DryMineSiteUnavailableException("rejected " + anchor.x());
            }
            if (anchor.x() == 3000) java.util.concurrent.locks.LockSupport.parkNanos(5_000_000L);
            return new SettlementLayoutPlanner().plan(source, anchor, climate, direction, terrain);
        };
        FrontierRegionBatchPlanner planner = new FrontierRegionBatchPlanner();
        FrontierRegionBatchPlanner.Result sequential = planner.plan(42L,
                new RegionCountRange(2, 2, 2), RegionPlacementProfiles.IRON_FRONTIER, sites,
                new FrontierRegionPlanner(), mine, FrontierRegionPlanner::gradedManhattanRail,
                layouts, 0);

        try (DeterministicGenesisWorkers workers = new DeterministicGenesisWorkers(3)) {
            FrontierRegionBatchPlanner.Result parallel = planner.plan(42L,
                    new RegionCountRange(2, 2, 2), RegionPlacementProfiles.IRON_FRONTIER, sites,
                    new FrontierRegionPlanner(), mine, FrontierRegionPlanner::gradedManhattanRail,
                    layouts, 0, workers);

            assertEquals(sequential.manifests(), parallel.manifests());
            assertEquals(sequential.rejectedRegionCandidates(), parallel.rejectedRegionCandidates());
            assertEquals(List.of(3000, 7000), parallel.manifests().stream()
                    .map(seed -> seed.anchor().x()).toList());
            assertTrue(parallel.speculativeRegionCandidates() > 0);
        }
    }

    private static FrontierSiteSelector.SelectedSite site(int x) {
        return site(x, false);
    }

    private static FrontierSiteSelector.SelectedSite site(int x, boolean near) {
        return new FrontierSiteSelector.SelectedSite(new TerrainCandidate(new VisualPoint(x, 70, 0),
                0, 0, 0, 0), FrontierClimate.TEMPERATE, near);
    }
}
