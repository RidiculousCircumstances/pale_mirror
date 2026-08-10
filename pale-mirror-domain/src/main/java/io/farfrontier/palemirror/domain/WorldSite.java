package io.farfrontier.palemirror.domain;

import java.util.Objects;

/** Reusable logical infrastructure. Physical bounds and provenance remain in the World Registry. */
public final class WorldSite {
    private final WorldObjectId id;
    private final WorldSiteType type;
    private OperationalState operationalState;

    public WorldSite(WorldObjectId id, WorldSiteType type, OperationalState operationalState) {
        this.id = Objects.requireNonNull(id, "id");
        this.type = Objects.requireNonNull(type, "type");
        this.operationalState = Objects.requireNonNull(operationalState, "operationalState");
    }

    public WorldObjectId id() { return id; }
    public WorldSiteType type() { return type; }
    public OperationalState operationalState() { return operationalState; }
    public void setOperationalState(OperationalState value) { operationalState = Objects.requireNonNull(value); }
}
