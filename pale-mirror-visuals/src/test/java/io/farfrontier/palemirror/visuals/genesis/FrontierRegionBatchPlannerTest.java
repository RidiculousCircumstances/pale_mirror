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
        MineAnchorResolver resolver = candidates -> {
            if (resolutions.getAndIncrement() == 0) {
                throw new DryMineSiteUnavailableException("island");
            }
            return candidates.getFirst();
        };

        FrontierRegionBatchPlanner.Result result = new FrontierRegionBatchPlanner().plan(42L, 1, sites,
                new FrontierRegionPlanner(), resolver);

        assertEquals(1, result.manifests().size());
        assertEquals(3000, result.manifests().getFirst().anchor().x());
        assertEquals(1, result.rejectedRegionCandidates());
    }

    @Test void exhaustedReserveFailsClosed() {
        MineAnchorResolver resolver = candidates -> {
            throw new DryMineSiteUnavailableException("water");
        };
        assertThrows(IllegalStateException.class, () -> new FrontierRegionBatchPlanner().plan(42L, 1,
                List.of(site(1000)), new FrontierRegionPlanner(), resolver));
    }

    @Test void replacesInvalidNearCenterBeforeConsideringRemoteCenters() {
        List<FrontierSiteSelector.SelectedSite> sites = List.of(site(1000, true), site(1800, true),
                site(3000, false));
        AtomicInteger resolutions = new AtomicInteger();
        MineAnchorResolver resolver = candidates -> {
            if (resolutions.getAndIncrement() == 0) throw new DryMineSiteUnavailableException("island");
            return candidates.getFirst();
        };

        FrontierRegionBatchPlanner.Result result = new FrontierRegionBatchPlanner().plan(42L, 1, sites,
                new FrontierRegionPlanner(), resolver);
        assertEquals(1800, result.manifests().getFirst().anchor().x());
    }

    private static FrontierSiteSelector.SelectedSite site(int x) {
        return site(x, false);
    }

    private static FrontierSiteSelector.SelectedSite site(int x, boolean near) {
        return new FrontierSiteSelector.SelectedSite(new TerrainCandidate(new VisualPoint(x, 70, 0),
                0, 0, 0, 0), FrontierClimate.TEMPERATE, near);
    }
}
