package io.farfrontier.palemirror.visuals.genesis;

import io.farfrontier.palemirror.api.AuthoredRegionSeed;
import java.util.ArrayList;
import java.util.List;

/** Converts surveyed centers into a bounded range of complete regions, failing only below the minimum. */
public final class FrontierRegionBatchPlanner {
    public Result plan(long worldSeed, int requested, RegionPlacementProfile profile,
                       List<FrontierSiteSelector.SelectedSite> candidates,
                       FrontierRegionPlanner planner, MineAnchorResolver mineAnchors, RailPathResolver railPaths) {
        return plan(worldSeed, RegionCountRange.exact(requested), profile, candidates, planner, mineAnchors, railPaths, 0);
    }

    public Result plan(long worldSeed, RegionCountRange counts, RegionPlacementProfile profile,
                       List<FrontierSiteSelector.SelectedSite> candidates,
                       FrontierRegionPlanner planner, MineAnchorResolver mineAnchors, RailPathResolver railPaths) {
        return plan(worldSeed, counts, profile, candidates, planner, mineAnchors, railPaths, 0);
    }

    public Result plan(long worldSeed, RegionCountRange counts, RegionPlacementProfile profile,
                       List<FrontierSiteSelector.SelectedSite> candidates,
                       FrontierRegionPlanner planner, MineAnchorResolver mineAnchors, RailPathResolver railPaths,
                       SettlementLayoutResolver settlementLayouts, int minimumRegionSpacing) {
        return planInternal(worldSeed, counts, profile, candidates, planner, mineAnchors, railPaths,
                settlementLayouts, minimumRegionSpacing);
    }

    public Result plan(long worldSeed, RegionCountRange counts, RegionPlacementProfile profile,
                       List<FrontierSiteSelector.SelectedSite> candidates,
                       FrontierRegionPlanner planner, MineAnchorResolver mineAnchors, RailPathResolver railPaths,
                       int minimumRegionSpacing) {
        return planInternal(worldSeed, counts, profile, candidates, planner, mineAnchors, railPaths,
                (source, anchor, climate, direction, terrain) -> new SettlementLayoutPlanner()
                        .plan(source, anchor, climate, direction, terrain), minimumRegionSpacing);
    }

    private Result planInternal(long worldSeed, RegionCountRange counts, RegionPlacementProfile profile,
                       List<FrontierSiteSelector.SelectedSite> candidates,
                       FrontierRegionPlanner planner, MineAnchorResolver mineAnchors, RailPathResolver railPaths,
                       SettlementLayoutResolver settlementLayouts, int minimumRegionSpacing) {
        if (counts.maximum() > profile.search().maximumRegions()) {
            throw new IllegalArgumentException("region maximum exceeds placement profile capacity");
        }
        if (minimumRegionSpacing < 0) throw new IllegalArgumentException("region spacing must be non-negative");
        List<AuthoredRegionSeed> manifests = new ArrayList<>(counts.maximum());
        int rejected = 0;
        int spacingRejected = 0;
        int nearAccepted = 0;
        java.util.Map<String, Integer> rejectionReasons = new java.util.LinkedHashMap<>();
        for (FrontierSiteSelector.SelectedSite site : candidates) {
            if (manifests.size() == counts.maximum()) break;
            if (site.nearCandidate() && nearAccepted >= profile.search().maximumNearAcceptedRegions()) continue;
            if (!separated(site.terrain().anchor(), manifests, minimumRegionSpacing)) {
                spacingRejected++;
                continue;
            }
            try {
                manifests.add(planner.plan(worldSeed, manifests.size(), site.terrain(), site.climate(),
                        mineAnchors, railPaths, settlementLayouts));
                if (site.nearCandidate()) nearAccepted++;
            } catch (DryMineSiteUnavailableException unavailable) {
                rejected++;
                rejectionReasons.merge(unavailable.getMessage(), 1, Integer::sum);
            }
        }
        if (manifests.size() < counts.minimum()) throw new IllegalStateException("Cannot plan minimum "
                + counts.minimum() + " complete authored regions from " + candidates.size() + " surveyed centers; "
                + rejected + " lacked bounded dry MineSites and " + spacingRejected
                + " overlapped accepted-region spacing; accepted centers="
                + manifests.stream().map(seed -> seed.anchor().x() + "," + seed.anchor().z()).toList()
                + "; rejection reasons=" + rejectionReasons.entrySet().stream()
                        .sorted(java.util.Map.Entry.<String, Integer>comparingByValue().reversed())
                        .limit(8).toList());
        return new Result(manifests, rejected, spacingRejected, manifests.size() >= counts.target());
    }

    private static boolean separated(io.farfrontier.palemirror.api.VisualPoint candidate,
                                     List<AuthoredRegionSeed> accepted, int spacing) {
        long required = (long) spacing * spacing;
        for (AuthoredRegionSeed existing : accepted) {
            long dx = (long) candidate.x() - existing.anchor().x();
            long dz = (long) candidate.z() - existing.anchor().z();
            if (dx * dx + dz * dz < required) return false;
        }
        return true;
    }

    public record Result(List<AuthoredRegionSeed> manifests, int rejectedRegionCandidates,
                         int spacingRejectedCandidates, boolean targetMet) {
        public Result {
            manifests = List.copyOf(manifests);
            if (rejectedRegionCandidates < 0) throw new IllegalArgumentException("rejected count must be non-negative");
            if (spacingRejectedCandidates < 0) throw new IllegalArgumentException("spacing count must be non-negative");
        }
    }
}
