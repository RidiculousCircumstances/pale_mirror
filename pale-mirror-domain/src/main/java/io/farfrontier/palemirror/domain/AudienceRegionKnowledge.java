package io.farfrontier.palemirror.domain;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** Persisted facts known by one StoryAudience about one living region. */
public final class AudienceRegionKnowledge {
    private final StoryAudienceId audience;
    private final String regionId;
    private final EnumSet<KnownRegionalFeature> features;
    private long supplyChainDiscoveredAtStep;

    public AudienceRegionKnowledge(StoryAudienceId audience, String regionId) {
        this(audience, regionId, Set.of(), -1L);
    }

    public AudienceRegionKnowledge(StoryAudienceId audience, String regionId,
                                   Set<KnownRegionalFeature> features,
                                   long supplyChainDiscoveredAtStep) {
        this.audience = Objects.requireNonNull(audience, "audience");
        if (regionId == null || regionId.isBlank()) throw new IllegalArgumentException("regionId must not be blank");
        if (supplyChainDiscoveredAtStep < -1) throw new IllegalArgumentException("Invalid discovery step");
        this.regionId = regionId;
        this.features = features.isEmpty() ? EnumSet.noneOf(KnownRegionalFeature.class) : EnumSet.copyOf(features);
        this.supplyChainDiscoveredAtStep = supplyChainDiscoveredAtStep;
    }

    public StoryAudienceId audience() { return audience; }
    public String regionId() { return regionId; }
    public Set<KnownRegionalFeature> features() { return Set.copyOf(features); }
    public long supplyChainDiscoveredAtStep() { return supplyChainDiscoveredAtStep; }
    public boolean knows(KnownRegionalFeature feature) { return features.contains(feature); }

    public boolean discover(KnownRegionalFeature feature, long simulationStep) {
        Objects.requireNonNull(feature, "feature");
        if (simulationStep < 0) throw new IllegalArgumentException("Negative discovery step");
        if (!features.add(feature)) return false;
        updateSupplyChainStep(simulationStep);
        return true;
    }

    public boolean introductorySupplyChainKnown() {
        return features.contains(KnownRegionalFeature.SETTLEMENT)
                && features.contains(KnownRegionalFeature.DEPOT)
                && features.contains(KnownRegionalFeature.PRIMARY_ROUTE);
    }

    private void updateSupplyChainStep(long simulationStep) {
        if (supplyChainDiscoveredAtStep < 0 && introductorySupplyChainKnown()) {
            supplyChainDiscoveredAtStep = simulationStep;
        }
    }
}
