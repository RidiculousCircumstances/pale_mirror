package io.farfrontier.palemirror.visuals.genesis;

import io.farfrontier.palemirror.api.AuthoredRegionSeed;
import java.util.ArrayList;
import java.util.List;

/** Converts a bounded reserve of surveyed centers into the requested count of complete regions. */
public final class FrontierRegionBatchPlanner {
    public static final int RESERVE_CANDIDATES = 8;

    public Result plan(long worldSeed, int requested, List<FrontierSiteSelector.SelectedSite> candidates,
                       FrontierRegionPlanner planner, MineAnchorResolver mineAnchors) {
        List<AuthoredRegionSeed> manifests = new ArrayList<>(requested);
        int rejected = 0;
        boolean nearAccepted = false;
        for (FrontierSiteSelector.SelectedSite site : candidates) {
            if (manifests.size() == requested) break;
            if (site.nearCandidate() && nearAccepted) continue;
            try {
                manifests.add(planner.plan(worldSeed, manifests.size(), site.terrain().anchor(), site.climate(),
                        mineAnchors));
                if (site.nearCandidate()) nearAccepted = true;
            } catch (DryMineSiteUnavailableException unavailable) {
                rejected++;
            }
        }
        if (manifests.size() != requested) throw new IllegalStateException("Cannot plan " + requested
                + " complete authored regions from " + candidates.size() + " surveyed centers; "
                + rejected + " lacked bounded dry MineSites");
        return new Result(manifests, rejected);
    }

    public record Result(List<AuthoredRegionSeed> manifests, int rejectedRegionCandidates) {
        public Result {
            manifests = List.copyOf(manifests);
            if (rejectedRegionCandidates < 0) throw new IllegalArgumentException("rejected count must be non-negative");
        }
    }
}
