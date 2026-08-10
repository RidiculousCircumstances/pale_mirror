package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.domain.ThreatTier;

/**
 * Provenance category for one controlled test-mine cell.  NODE cells are
 * reserved for the siege and never participate in the decorative biome
 * palette.  Every other value is the earliest PM threat tier allowed to own
 * that cell.
 */
public enum InfectionBiomeStage {
    NODE,
    FOOTHOLD,
    INFESTED,
    SIEGE,
    APEX;

    public boolean isBiomeCell() {
        return this != NODE;
    }

    public boolean activeAt(ThreatTier tier) {
        return isBiomeCell() && tier.ordinal() >= ordinal();
    }
}
