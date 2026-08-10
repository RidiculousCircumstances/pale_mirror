package io.farfrontier.palemirror.domain;

import java.util.Objects;

/** Durable social identity and autonomous policy state, independent from any one physical place. */
public final class SettlementCommunity {
    private final WorldObjectId id;
    private final int population;
    private boolean rationing;
    private boolean supplyRequested;
    private CrisisState crisisState;
    private int stableSupplySteps;

    public SettlementCommunity(WorldObjectId id, int population) {
        this(id, population, false, false, CrisisState.NONE, 0);
    }

    public SettlementCommunity(WorldObjectId id, int population, boolean rationing, boolean supplyRequested,
                               CrisisState crisisState, int stableSupplySteps) {
        this.id = Objects.requireNonNull(id, "id");
        if (population < 0 || stableSupplySteps < 0) throw new IllegalArgumentException("Community values must not be negative");
        this.population = population;
        this.rationing = rationing;
        this.supplyRequested = supplyRequested;
        this.crisisState = Objects.requireNonNull(crisisState, "crisisState");
        this.stableSupplySteps = stableSupplySteps;
    }

    public WorldObjectId id() { return id; }
    public int population() { return population; }
    public boolean rationing() { return rationing; }
    public boolean supplyRequested() { return supplyRequested; }
    public CrisisState crisisState() { return crisisState; }
    public int stableSupplySteps() { return stableSupplySteps; }
    boolean setRationing(boolean value) { boolean changed = rationing != value; rationing = value; return changed; }
    boolean setSupplyRequested(boolean value) { boolean changed = supplyRequested != value; supplyRequested = value; return changed; }
    boolean setCrisisState(CrisisState value) { boolean changed = crisisState != value; crisisState = Objects.requireNonNull(value); return changed; }
    void setStableSupplySteps(int value) { stableSupplySteps = Math.max(0, value); }
}
