package io.farfrontier.palemirror.domain;

import java.util.Objects;

/** Abstract settlement affected by a facility's resource flow; it has no Minecraft representation yet. */
public final class SettlementState {
    private final WorldObjectId id;
    private final WorldObjectId ironSource;
    private final int expectedIronSupply;
    private final int baseDefense;
    private int currentIronSupply;
    private int currentDefense;
    private boolean supplyDisrupted;

    public SettlementState(WorldObjectId id, WorldObjectId ironSource, int expectedIronSupply, int baseDefense) {
        this(id, ironSource, expectedIronSupply, baseDefense, expectedIronSupply, baseDefense, false);
    }

    public SettlementState(WorldObjectId id, WorldObjectId ironSource, int expectedIronSupply, int baseDefense,
                           int currentIronSupply, int currentDefense, boolean supplyDisrupted) {
        this.id = Objects.requireNonNull(id, "id");
        this.ironSource = Objects.requireNonNull(ironSource, "ironSource");
        this.expectedIronSupply = expectedIronSupply;
        this.baseDefense = baseDefense;
        this.currentIronSupply = currentIronSupply;
        this.currentDefense = currentDefense;
        this.supplyDisrupted = supplyDisrupted;
    }

    public WorldObjectId id() { return id; }
    public WorldObjectId ironSource() { return ironSource; }
    public int expectedIronSupply() { return expectedIronSupply; }
    public int baseDefense() { return baseDefense; }
    public int currentIronSupply() { return currentIronSupply; }
    public int currentDefense() { return currentDefense; }
    public boolean supplyDisrupted() { return supplyDisrupted; }
    public boolean disrupt(int availableIron) {
        int newSupply = Math.max(0, availableIron);
        int newDefense = Math.max(0, baseDefense - Math.max(1, baseDefense / 4));
        boolean changed = !supplyDisrupted || currentIronSupply != newSupply || currentDefense != newDefense;
        currentIronSupply = newSupply;
        currentDefense = newDefense;
        supplyDisrupted = true;
        return changed;
    }
    public boolean restore() {
        boolean changed = supplyDisrupted || currentIronSupply != expectedIronSupply || currentDefense != baseDefense;
        currentIronSupply = expectedIronSupply;
        currentDefense = baseDefense;
        supplyDisrupted = false;
        return changed;
    }
}
