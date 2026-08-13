package io.farfrontier.palemirror.visuals.genesis;

import io.farfrontier.palemirror.api.AuthoredRegionSeed;
import java.util.ArrayList;
import java.util.List;

/** Converts a bounded reserve of surveyed centers into the requested count of complete regions. */
public final class FrontierRegionBatchPlanner {
    public Result plan(long worldSeed, int requested, RegionPlacementProfile profile,
                       List<FrontierSiteSelector.SelectedSite> candidates,
                       FrontierRegionPlanner planner, MineAnchorResolver mineAnchors, RailPathResolver railPaths) {
        List<AuthoredRegionSeed> manifests = new ArrayList<>(requested);
        int rejected = 0;
        int nearAccepted = 0;
        for (FrontierSiteSelector.SelectedSite site : candidates) {
            if (manifests.size() == requested) break;
            if (site.nearCandidate() && nearAccepted >= profile.search().maximumNearAcceptedRegions()) continue;
            try {
                manifests.add(planner.plan(worldSeed, manifests.size(), site.terrain().anchor(), site.climate(),
                        mineAnchors, railPaths));
                if (site.nearCandidate()) nearAccepted++;
            } catch (DryMineSiteUnavailableException unavailable) {
                rejected++;
            }
        }
        if (manifests.size() != requested) throw new IllegalStateException("Cannot plan " + requested
                + " complete authored regions from " + candidates.size() + " surveyed centers; "
                + rejected + " lacked bounded dry MineSites; accepted centers="
                + manifests.stream().map(seed -> seed.anchor().x() + "," + seed.anchor().z()).toList());
        return new Result(manifests, rejected);
    }

    public record Result(List<AuthoredRegionSeed> manifests, int rejectedRegionCandidates) {
        public Result {
            manifests = List.copyOf(manifests);
            if (rejectedRegionCandidates < 0) throw new IllegalArgumentException("rejected count must be non-negative");
        }
    }
}
