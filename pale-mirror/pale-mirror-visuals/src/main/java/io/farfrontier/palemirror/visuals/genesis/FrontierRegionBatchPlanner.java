package io.farfrontier.palemirror.visuals.genesis;

import io.farfrontier.palemirror.api.AuthoredRegionSeed;
import java.util.ArrayList;
import java.util.List;

/** Converts surveyed centers into a bounded range of complete regions, failing only below the minimum. */
public final class FrontierRegionBatchPlanner {
    private static final int CANDIDATE_LOOKAHEAD_MULTIPLIER = 2;
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
                settlementLayouts, minimumRegionSpacing, null);
    }

    public Result plan(long worldSeed, RegionCountRange counts, RegionPlacementProfile profile,
                       List<FrontierSiteSelector.SelectedSite> candidates,
                       FrontierRegionPlanner planner, MineAnchorResolver mineAnchors, RailPathResolver railPaths,
                       SettlementLayoutResolver settlementLayouts, int minimumRegionSpacing,
                       DeterministicGenesisWorkers workers) {
        return planInternal(worldSeed, counts, profile, candidates, planner, mineAnchors, railPaths,
                settlementLayouts, minimumRegionSpacing, java.util.Objects.requireNonNull(workers, "workers"));
    }

    public Result plan(long worldSeed, RegionCountRange counts, RegionPlacementProfile profile,
                       List<FrontierSiteSelector.SelectedSite> candidates,
                       FrontierRegionPlanner planner, MineAnchorResolver mineAnchors, RailPathResolver railPaths,
                       int minimumRegionSpacing) {
        return planInternal(worldSeed, counts, profile, candidates, planner, mineAnchors, railPaths,
                (source, anchor, climate, direction, terrain) -> new SettlementLayoutPlanner()
                        .plan(source, anchor, climate, direction, terrain), minimumRegionSpacing, null);
    }

    private Result planInternal(long worldSeed, RegionCountRange counts, RegionPlacementProfile profile,
                       List<FrontierSiteSelector.SelectedSite> candidates,
                       FrontierRegionPlanner planner, MineAnchorResolver mineAnchors, RailPathResolver railPaths,
                       SettlementLayoutResolver settlementLayouts, int minimumRegionSpacing,
                       DeterministicGenesisWorkers workers) {
        if (counts.maximum() > profile.search().maximumRegions()) {
            throw new IllegalArgumentException("region maximum exceeds placement profile capacity");
        }
        if (minimumRegionSpacing < 0) throw new IllegalArgumentException("region spacing must be non-negative");
        List<AuthoredRegionSeed> manifests = new ArrayList<>(counts.maximum());
        int rejected = 0;
        int spacingRejected = 0;
        int nearAccepted = 0;
        int evaluated = 0;
        int speculative = 0;
        java.util.Map<String, Integer> rejectionReasons = new java.util.LinkedHashMap<>();
        int cursor = 0;
        int waveLimit = workers == null ? 1
                : Math.multiplyExact(workers.parallelism(), CANDIDATE_LOOKAHEAD_MULTIPLIER);
        while (manifests.size() < counts.maximum() && cursor < candidates.size()) {
            List<IndexedSite> wave = new ArrayList<>(waveLimit);
            while (cursor < candidates.size() && wave.size() < waveLimit) {
                int index = cursor++;
                FrontierSiteSelector.SelectedSite site = candidates.get(index);
                if (site.nearCandidate() && nearAccepted >= profile.search().maximumNearAcceptedRegions()) continue;
                if (!separated(site.terrain().anchor(), manifests, minimumRegionSpacing)) {
                    spacingRejected++;
                    continue;
                }
                wave.add(new IndexedSite(index, site));
            }
            if (wave.isEmpty()) continue;
            int ordinal = manifests.size();
            List<Attempt> attempts = workers == null
                    ? List.of(attempt(worldSeed, ordinal, wave.getFirst().site(), planner,
                            mineAnchors, railPaths, settlementLayouts))
                    : workers.mapIndexed(wave.size(), index -> attempt(worldSeed, ordinal, wave.get(index).site(),
                            planner, mineAnchors, railPaths, settlementLayouts));
            evaluated += attempts.size();
            boolean accepted = false;
            for (int index = 0; index < attempts.size(); index++) {
                Attempt attempt = attempts.get(index);
                if (accepted) {
                    speculative++;
                    continue;
                }
                if (attempt.manifest() == null) {
                    rejected++;
                    rejectionReasons.merge(attempt.failure(), 1, Integer::sum);
                    continue;
                }
                manifests.add(attempt.manifest());
                if (wave.get(index).site().nearCandidate()) nearAccepted++;
                accepted = true;
                // Later work in this wave was speculative. Revisit those candidates
                // using the next accepted ordinal so output matches sequential planning.
                cursor = wave.get(index).candidateIndex() + 1;
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
        return new Result(manifests, rejected, spacingRejected, evaluated, speculative,
                manifests.size() >= counts.target());
    }

    private static Attempt attempt(long worldSeed, int ordinal, FrontierSiteSelector.SelectedSite site,
                                   FrontierRegionPlanner planner, MineAnchorResolver mineAnchors,
                                   RailPathResolver railPaths, SettlementLayoutResolver settlementLayouts) {
        try {
            return new Attempt(planner.plan(worldSeed, ordinal, site.terrain(), site.climate(),
                    mineAnchors, railPaths, settlementLayouts), "");
        } catch (DryMineSiteUnavailableException unavailable) {
            return new Attempt(null, unavailable.getMessage());
        }
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

    private record IndexedSite(int candidateIndex, FrontierSiteSelector.SelectedSite site) { }
    private record Attempt(AuthoredRegionSeed manifest, String failure) { }

    public record Result(List<AuthoredRegionSeed> manifests, int rejectedRegionCandidates,
                         int spacingRejectedCandidates, int evaluatedRegionCandidates,
                         int speculativeRegionCandidates, boolean targetMet) {
        public Result {
            manifests = List.copyOf(manifests);
            if (rejectedRegionCandidates < 0) throw new IllegalArgumentException("rejected count must be non-negative");
            if (spacingRejectedCandidates < 0) throw new IllegalArgumentException("spacing count must be non-negative");
            if (evaluatedRegionCandidates < rejectedRegionCandidates || speculativeRegionCandidates < 0
                    || speculativeRegionCandidates > evaluatedRegionCandidates) {
                throw new IllegalArgumentException("candidate evaluation counts are inconsistent");
            }
        }
    }
}
