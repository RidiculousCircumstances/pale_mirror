package io.farfrontier.palemirror.domain;

/** PM-owned escalation level for a local threat site; it never mirrors a source mod's global phases. */
public enum ThreatTier {
    DORMANT,
    FOOTHOLD,
    INFESTED,
    SIEGE,
    APEX;

    public boolean atLeast(ThreatTier other) {
        return ordinal() >= other.ordinal();
    }
}
