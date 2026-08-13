package io.farfrontier.palemirror.visuals.genesis;

import java.util.List;

/** Immutable, reusable terrain and spatial contract for one authored region archetype. */
public record RegionPlacementProfile(
        String id,
        String addressSalt,
        SearchPolicy search,
        SettlementTerrainPolicy settlementTerrain,
        List<SitePlacementRequirement> requiredSites,
        RoutePlacementRequirement route) {
    public RegionPlacementProfile {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("placement profile id is required");
        if (addressSalt == null || addressSalt.isBlank()) {
            throw new IllegalArgumentException("placement profile address salt is required");
        }
        if (search == null || settlementTerrain == null || route == null) {
            throw new IllegalArgumentException("placement profile policies are required");
        }
        requiredSites = List.copyOf(requiredSites);
        if (requiredSites.isEmpty()) throw new IllegalArgumentException("placement profile requires at least one site");
        java.util.Set<String> roles = new java.util.HashSet<>();
        for (SitePlacementRequirement site : requiredSites) {
            if (!roles.add(site.role())) throw new IllegalArgumentException("duplicate site role " + site.role());
        }
        for (SitePlacementRequirement site : requiredSites) {
            if (!site.separateCardinalSectorFromRole().isBlank()
                    && !roles.contains(site.separateCardinalSectorFromRole())) {
                throw new IllegalArgumentException("unknown cardinal-sector reference "
                        + site.separateCardinalSectorFromRole());
            }
        }
        if (!route.originRole().equals("settlement") && !roles.contains(route.originRole())) {
            throw new IllegalArgumentException("unknown route origin role " + route.originRole());
        }
        if (!route.destinationRole().equals("settlement") && !roles.contains(route.destinationRole())) {
            throw new IllegalArgumentException("unknown route destination role " + route.destinationRole());
        }
    }

    public SitePlacementRequirement requireSite(String role) {
        return requiredSites.stream().filter(site -> site.role().equals(role)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Placement profile " + id + " has no site role " + role));
    }

    public record SearchPolicy(
            int maximumRegions,
            int maximumSurveyCandidates,
            int reserveCandidateCount,
            int regionEnvelope,
            int minimumMapRadius,
            int minimumRegionSpacing,
            int remoteSpacingMargin,
            int maximumNearAcceptedRegions,
            int nearReserveCandidates,
            SearchBand near,
            SearchBand remote,
            int candidatesPerRemoteRegion,
            int largeBatchThreshold,
            int largeBatchCandidatesPerRemoteRegion,
            int exactSettlementCandidatesPerRegion,
            int largeBatchExactSettlementCandidatesPerRegion,
            List<Integer> landscapeProjectionDistances,
            List<HorizontalOffset> settlementRefinementOffsets) {
        public SearchPolicy {
            if (maximumRegions < 1 || maximumRegions > 64) {
                throw new IllegalArgumentException("maximumRegions must be between 1 and 64");
            }
            if (maximumSurveyCandidates < maximumRegions || maximumSurveyCandidates > 256) {
                throw new IllegalArgumentException(
                        "maximumSurveyCandidates must be between maximumRegions and 256");
            }
            if (reserveCandidateCount < 0 || regionEnvelope < 1 || minimumMapRadius < 1 || minimumRegionSpacing < 1
                    || remoteSpacingMargin < 0 || maximumNearAcceptedRegions < 1 || nearReserveCandidates < 0
                    || near == null || remote == null) {
                throw new IllegalArgumentException("search bands and region envelope are required");
            }
            if (maximumNearAcceptedRegions > maximumRegions) {
                throw new IllegalArgumentException("near-region limit exceeds maximum region count");
            }
            if (reserveCandidateCount >= maximumSurveyCandidates) {
                throw new IllegalArgumentException("reserve candidate count must be below survey capacity");
            }
            if (candidatesPerRemoteRegion < 1 || largeBatchThreshold < 1
                    || largeBatchCandidatesPerRemoteRegion < candidatesPerRemoteRegion
                    || exactSettlementCandidatesPerRegion < 1
                    || largeBatchExactSettlementCandidatesPerRegion < exactSettlementCandidatesPerRegion) {
                throw new IllegalArgumentException("search budgets must be positive");
            }
            landscapeProjectionDistances = List.copyOf(landscapeProjectionDistances);
            if (landscapeProjectionDistances.isEmpty()
                    || landscapeProjectionDistances.stream().anyMatch(value -> value < 1)) {
                throw new IllegalArgumentException("landscape projection distances must be positive");
            }
            settlementRefinementOffsets = List.copyOf(settlementRefinementOffsets);
            if (settlementRefinementOffsets.isEmpty()
                    || !settlementRefinementOffsets.getFirst().equals(HorizontalOffset.ORIGIN)
                    || settlementRefinementOffsets.stream().distinct().count() != settlementRefinementOffsets.size()) {
                throw new IllegalArgumentException("settlement refinement offsets must be unique and start at origin");
            }
        }

        public int remoteCandidatesPerRegion(int requestedRegions) {
            return requestedRegions >= largeBatchThreshold
                    ? largeBatchCandidatesPerRemoteRegion : candidatesPerRemoteRegion;
        }

        public int exactSettlementCandidatesPerRegion(int requestedRegions) {
            return requestedRegions >= largeBatchThreshold
                    ? largeBatchExactSettlementCandidatesPerRegion : exactSettlementCandidatesPerRegion;
        }
    }

    public record SearchBand(int candidateCount, DistanceBand spawnDistance) {
        public SearchBand {
            if (candidateCount < 1 || spawnDistance == null) {
                throw new IllegalArgumentException("search band must have candidates and a distance");
            }
        }
    }
}
