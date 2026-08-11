package io.farfrontier.palemirror.domain;

import java.util.Objects;

/** Last canonical presence/reachability observation for one audience and region. */
public final class AudienceRegionAccess {
    private final StoryAudienceId audience;
    private final String regionId;
    private AudienceRegionReachability reachability;
    private boolean present;
    private long observedAtStep;
    private String observationId;

    public AudienceRegionAccess(StoryAudienceId audience, String regionId,
                                AudienceRegionReachability reachability, boolean present,
                                long observedAtStep, String observationId) {
        this.audience = Objects.requireNonNull(audience, "audience");
        if (regionId == null || regionId.isBlank()) throw new IllegalArgumentException("Blank region id");
        this.regionId = regionId;
        this.reachability = Objects.requireNonNull(reachability, "reachability");
        if (observedAtStep < 0) throw new IllegalArgumentException("Invalid observation step");
        this.present = present;
        this.observedAtStep = observedAtStep;
        this.observationId = requireText(observationId, "observationId");
    }

    public StoryAudienceId audience() { return audience; }
    public String regionId() { return regionId; }
    public AudienceRegionReachability reachability() { return reachability; }
    public boolean present() { return present; }
    public long observedAtStep() { return observedAtStep; }
    public String observationId() { return observationId; }

    public boolean observe(AudienceRegionReachability nextReachability, boolean nextPresent,
                           long step, String nextObservationId) {
        Objects.requireNonNull(nextReachability, "nextReachability");
        if (step < observedAtStep) throw new IllegalArgumentException("Audience access observation moved backwards");
        String id = requireText(nextObservationId, "observationId");
        if (observationId.equals(id)) return false;
        boolean changed = reachability != nextReachability || present != nextPresent;
        reachability = nextReachability;
        present = nextPresent;
        observedAtStep = step;
        observationId = id;
        return changed;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}
