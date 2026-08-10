package io.farfrontier.palemirror.internal.world;

import java.util.Objects;

import io.farfrontier.palemirror.domain.WorldObjectId;
import io.farfrontier.palemirror.internal.adapter.SettlementObservation;
import net.minecraft.core.BlockPos;

/** Persistent physical evidence only; it never becomes a second settlement simulation. */
public final class SettlementObservationRecord {
    private final WorldObjectId id;
    private final String dimensionId;
    private final BlockPos anchor;
    private final BlockPos minBounds;
    private final BlockPos maxBounds;
    private final String provenance;
    private int observedPopulation;
    private int observedGuards;
    private long lastObservedGameTime;
    private int observedResidentDeaths;
    private int observedGuardDeaths;

    public SettlementObservationRecord(SettlementObservation observation) {
        this(observation.settlementId(), observation.dimensionId(), observation.anchor(), observation.minBounds(),
                observation.maxBounds(), observation.population(), observation.guards(), observation.observedAtGameTime(),
                observation.provenance(), 0, 0);
    }

    public SettlementObservationRecord(WorldObjectId id, String dimensionId, BlockPos anchor, BlockPos minBounds,
                                       BlockPos maxBounds, int observedPopulation, int observedGuards,
                                       long lastObservedGameTime, String provenance, int observedResidentDeaths,
                                       int observedGuardDeaths) {
        this.id = Objects.requireNonNull(id, "id");
        this.dimensionId = Objects.requireNonNull(dimensionId, "dimensionId");
        this.anchor = Objects.requireNonNull(anchor, "anchor").immutable();
        this.minBounds = Objects.requireNonNull(minBounds, "minBounds").immutable();
        this.maxBounds = Objects.requireNonNull(maxBounds, "maxBounds").immutable();
        this.provenance = Objects.requireNonNull(provenance, "provenance");
        if (observedPopulation < 0 || observedGuards < 0 || lastObservedGameTime < 0
                || observedResidentDeaths < 0 || observedGuardDeaths < 0) {
            throw new IllegalArgumentException("Settlement physical observation values must not be negative");
        }
        this.observedPopulation = observedPopulation;
        this.observedGuards = observedGuards;
        this.lastObservedGameTime = lastObservedGameTime;
        this.observedResidentDeaths = observedResidentDeaths;
        this.observedGuardDeaths = observedGuardDeaths;
    }

    public WorldObjectId id() { return id; }
    public String dimensionId() { return dimensionId; }
    public BlockPos anchor() { return anchor; }
    public BlockPos minBounds() { return minBounds; }
    public BlockPos maxBounds() { return maxBounds; }
    public String provenance() { return provenance; }
    public int observedPopulation() { return observedPopulation; }
    public int observedGuards() { return observedGuards; }
    public long lastObservedGameTime() { return lastObservedGameTime; }
    public int observedResidentDeaths() { return observedResidentDeaths; }
    public int observedGuardDeaths() { return observedGuardDeaths; }
    public boolean contains(String dimension, BlockPos position) {
        return dimensionId.equals(dimension) && position.getX() >= minBounds.getX() && position.getX() <= maxBounds.getX()
                && position.getY() >= minBounds.getY() && position.getY() <= maxBounds.getY()
                && position.getZ() >= minBounds.getZ() && position.getZ() <= maxBounds.getZ();
    }
    public boolean observe(SettlementObservation observation) {
        if (!id.equals(observation.settlementId()) || !dimensionId.equals(observation.dimensionId())
                || !anchor.equals(observation.anchor()) || !provenance.equals(observation.provenance())) {
            throw new IllegalArgumentException("Settlement observation identity changed for " + id.value());
        }
        boolean changed = observedPopulation != observation.population() || observedGuards != observation.guards()
                || lastObservedGameTime != observation.observedAtGameTime();
        observedPopulation = observation.population();
        observedGuards = observation.guards();
        lastObservedGameTime = observation.observedAtGameTime();
        return changed;
    }
    public boolean recordResidentDeath() { observedResidentDeaths++; return true; }
    public boolean recordGuardDeath() { observedGuardDeaths++; return true; }
    public WorldObjectRegistryEntry registryEntry() {
        return new WorldObjectRegistryEntry(id, dimensionId, anchor, minBounds, maxBounds,
                "minecraft:observed_village", provenance, WorldObjectLifecycle.REPRESENTED);
    }
}
