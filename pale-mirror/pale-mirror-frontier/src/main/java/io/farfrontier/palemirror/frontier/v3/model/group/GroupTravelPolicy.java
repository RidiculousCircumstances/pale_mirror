package io.farfrontier.palemirror.frontier.v3.model.group;

/** Mission-supplied formation spacing, elastic lag and calibrated COLD pace. */
public record GroupTravelPolicy(int spacing, int maximumStretch, long ticksPerEdge) {
    public GroupTravelPolicy {
        if (spacing < 2 || spacing > 16 || maximumStretch < spacing || maximumStretch > 128 || ticksPerEdge < 1)
            throw new IllegalArgumentException("invalid declared group travel policy");
    }
}
