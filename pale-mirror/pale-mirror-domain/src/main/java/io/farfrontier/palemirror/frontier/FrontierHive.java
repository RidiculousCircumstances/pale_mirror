package io.farfrontier.palemirror.frontier;

import java.util.Objects;

/** Canonical hive metabolism. Its organs and bioforms remain separate addressable objects. */
public final class FrontierHive {
    public enum State { ACTIVE, DECAPITATED, ERADICATED }

    private final String id;
    private final FrontierPoint anchor;
    private long biomass;
    private long geneticMaterial;
    private State state = State.ACTIVE;
    private long revision;

    FrontierHive(String id, FrontierPoint anchor, long biomass) {
        if (id == null || id.isBlank() || biomass < 0) throw new IllegalArgumentException("invalid hive");
        this.id = id;
        this.anchor = Objects.requireNonNull(anchor, "anchor");
        this.biomass = biomass;
    }

    public String id() { return id; }
    public FrontierPoint anchor() { return anchor; }
    public long biomass() { return biomass; }
    /** Samples returned by living harvesters; later adaptation consumes this explicit pool. */
    public long geneticMaterial() { return geneticMaterial; }
    public State state() { return state; }
    public long revision() { return revision; }
    public String materializationId() { return "frontier:hive:" + id; }
    boolean addBiomass(long amount) { return changeBiomass(amount); }
    boolean spendBiomass(long amount) {
        if (amount < 1 || biomass < amount) return false;
        biomass -= amount;
        revision++;
        return true;
    }
    boolean addGeneticMaterial(long amount) {
        if (amount < 0) throw new IllegalArgumentException("genetic material cannot be negative");
        if (amount == 0) return false;
        geneticMaterial = Math.addExact(geneticMaterial, amount);
        revision++;
        return true;
    }
    boolean spendGeneticMaterial(long amount) {
        if (amount < 1 || geneticMaterial < amount) return false;
        geneticMaterial -= amount;
        revision++;
        return true;
    }
    boolean reconcile(boolean coreAlive, boolean organAlive) {
        State next = coreAlive ? State.ACTIVE : organAlive ? State.DECAPITATED : State.ERADICATED;
        if (state == next) return false;
        state = next;
        revision++;
        return true;
    }
    void restore(long biomass, long geneticMaterial, State state, long revision) {
        if (biomass < 0 || geneticMaterial < 0 || revision < 0) throw new IllegalArgumentException("invalid persisted hive state");
        this.biomass = biomass;
        this.geneticMaterial = geneticMaterial;
        this.state = Objects.requireNonNull(state, "state");
        this.revision = revision;
    }
    private boolean changeBiomass(long amount) {
        long next = Math.addExact(biomass, amount);
        if (next < 0) throw new IllegalArgumentException("hive biomass cannot be negative");
        biomass = next;
        revision++;
        return true;
    }
}
