package io.farfrontier.palemirror.domain;

import java.util.Objects;

/** Abstract consequence of an evacuation; physical caravans are intentionally out of scope. */
public final class MigrantGroupState {
    private final WorldObjectId id;
    private final WorldObjectId originSettlement;
    private final int population;
    private MigrantGroupStatus status;

    public MigrantGroupState(WorldObjectId id, WorldObjectId originSettlement, int population, MigrantGroupStatus status) {
        this.id = Objects.requireNonNull(id, "id");
        this.originSettlement = Objects.requireNonNull(originSettlement, "originSettlement");
        if (population <= 0) throw new IllegalArgumentException("Migrant population must be positive");
        this.population = population;
        this.status = Objects.requireNonNull(status, "status");
    }

    public WorldObjectId id() { return id; }
    public WorldObjectId originSettlement() { return originSettlement; }
    public int population() { return population; }
    public MigrantGroupStatus status() { return status; }
    public void setStatus(MigrantGroupStatus value) { status = Objects.requireNonNull(value, "value"); }
}
