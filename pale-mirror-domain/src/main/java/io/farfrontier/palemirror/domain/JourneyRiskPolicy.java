package io.farfrontier.palemirror.domain;

/** Pinned full-risk policy. All values are integer basis points for deterministic arithmetic. */
public record JourneyRiskPolicy(String id, String version, int exposureBasisPoints,
                                int maximumLossPerStep, int guardMitigationBasisPoints) {
    public JourneyRiskPolicy {
        if (id == null || id.isBlank() || version == null || version.isBlank()) {
            throw new IllegalArgumentException("Journey risk policy identity is required");
        }
        if (exposureBasisPoints < 0 || exposureBasisPoints > 10_000 || maximumLossPerStep < 0
                || guardMitigationBasisPoints < 0 || guardMitigationBasisPoints > 10_000) {
            throw new IllegalArgumentException("Invalid journey risk policy");
        }
    }

    public static JourneyRiskPolicy evacuationDefault() {
        return new JourneyRiskPolicy("pale_mirror:evacuation_full_risk", "1", 350, 3, 500);
    }
    public static JourneyRiskPolicy communityReturn() {
        return new JourneyRiskPolicy("pale_mirror:community_return", "1", 100, 1, 750);
    }
}
