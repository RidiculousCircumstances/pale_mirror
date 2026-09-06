package io.farfrontier.palemirror.visuals.genesis;

import java.util.List;

/** One required regional site and its relation to the settlement and other sites. */
public record SitePlacementRequirement(
        String role,
        DistanceBand distanceFromSettlement,
        String relatedSiteRole,
        int minimumSeparationFromRelatedSite,
        boolean requireSeparateCardinalSector,
        List<Integer> landscapeEvidenceDistances,
        List<Integer> lateralOffsets,
        int preferredDistanceCandidateLimit,
        int exactValidationBudget,
        SiteTerrainPolicy terrain) {
    public SitePlacementRequirement {
        if (role == null || role.isBlank()) throw new IllegalArgumentException("site role is required");
        if (distanceFromSettlement == null) throw new IllegalArgumentException("site distance is required");
        relatedSiteRole = relatedSiteRole == null ? "" : relatedSiteRole;
        if (minimumSeparationFromRelatedSite < 0) {
            throw new IllegalArgumentException("site separation must be non-negative");
        }
        if (relatedSiteRole.isBlank()
                && (minimumSeparationFromRelatedSite != 0 || requireSeparateCardinalSector)) {
            throw new IllegalArgumentException("site relation policy requires a related site role");
        }
        landscapeEvidenceDistances = List.copyOf(landscapeEvidenceDistances);
        lateralOffsets = List.copyOf(lateralOffsets);
        if (landscapeEvidenceDistances.isEmpty()
                || landscapeEvidenceDistances.stream().anyMatch(value -> value < 1)) {
            throw new IllegalArgumentException("site landscape evidence distances must be positive");
        }
        if (lateralOffsets.isEmpty()) throw new IllegalArgumentException("site lateral offsets are required");
        if (preferredDistanceCandidateLimit < 1 || exactValidationBudget < 1) {
            throw new IllegalArgumentException("site candidate and exact-validation budgets must be positive");
        }
        if (terrain == null) throw new IllegalArgumentException("site terrain policy is required");
    }
}
