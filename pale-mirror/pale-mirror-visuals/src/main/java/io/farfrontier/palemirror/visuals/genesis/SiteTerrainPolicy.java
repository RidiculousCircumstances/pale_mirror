package io.farfrontier.palemirror.visuals.genesis;

import java.util.List;

/** Exact and cheap terrain evidence needed by one typed site. */
public record SiteTerrainPolicy(
        Feature feature,
        boolean requireDryFootprint,
        int footprintHalfExtent,
        int biomeSampleStep,
        List<Integer> nearbyEvidenceDistances,
        int apronDistance,
        int apronMaximumRelief,
        List<RiseSample> riseSamples) {
    public SiteTerrainPolicy {
        if (feature == null) throw new IllegalArgumentException("site terrain feature is required");
        if (footprintHalfExtent < 0) throw new IllegalArgumentException("footprintHalfExtent must be non-negative");
        if (biomeSampleStep < 1) throw new IllegalArgumentException("biomeSampleStep must be positive");
        nearbyEvidenceDistances = List.copyOf(nearbyEvidenceDistances);
        riseSamples = List.copyOf(riseSamples);
        if (nearbyEvidenceDistances.stream().anyMatch(value -> value < 1)) {
            throw new IllegalArgumentException("nearby evidence distances must be positive");
        }
        if (apronDistance < 0 || apronMaximumRelief < 0) {
            throw new IllegalArgumentException("apron constraints must be non-negative");
        }
        if (feature == Feature.MOUNTAIN_FACE && riseSamples.isEmpty()) {
            throw new IllegalArgumentException("mountain-face sites require rise samples");
        }
        int previous = 0;
        for (RiseSample sample : riseSamples) {
            if (sample.distance() <= previous) throw new IllegalArgumentException("rise samples must be ordered");
            previous = sample.distance();
        }
    }

    public enum Feature { MOUNTAIN_FACE }

    public record RiseSample(int distance, int minimumRise) {
        public RiseSample {
            if (distance < 1 || minimumRise < 0) {
                throw new IllegalArgumentException("rise sample distance must be positive and rise non-negative");
            }
        }
    }
}
