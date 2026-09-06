package io.farfrontier.palemirror.frontier.reference;

import java.util.List;
import java.util.Objects;

/**
 * A bounded 4x4 operational view over source cells. Cells retain ecological
 * truth; this object stores only the current territorial derivation.
 */
public final class ReferenceV2OperationalSector {
    private final int x;
    private final int y;
    private final List<ReferenceGridPosition> cells;
    private double organicMass;
    private double moisture;
    private double scar;
    private double infection;
    private double sporeLoad;
    private double humanAccess;
    private double hiveInfluence;
    private double infrastructureValue;
    private List<ReferenceRouteKey> routeKeys = List.of();

    ReferenceV2OperationalSector(int x, int y, List<ReferenceGridPosition> cells) {
        this.x = x;
        this.y = y;
        this.cells = List.copyOf(Objects.requireNonNull(cells, "cells"));
        if (this.cells.isEmpty()) throw new IllegalArgumentException("sector requires cells");
    }

    public int x() { return x; }
    public int y() { return y; }
    public String key() { return x + ":" + y; }
    public List<ReferenceGridPosition> cells() { return cells; }
    public double organicMass() { return organicMass; }
    public double moisture() { return moisture; }
    public double scar() { return scar; }
    public double infection() { return infection; }
    public double sporeLoad() { return sporeLoad; }
    public double humanAccess() { return humanAccess; }
    public double hiveInfluence() { return hiveInfluence; }
    public double infrastructureValue() { return infrastructureValue; }
    public List<ReferenceRouteKey> routeKeys() { return routeKeys; }

    void territory(
            double organicMass,
            double moisture,
            double scar,
            double infection,
            double sporeLoad,
            double humanAccess,
            double hiveInfluence,
            double infrastructureValue,
            List<ReferenceRouteKey> routeKeys
    ) {
        this.organicMass = organicMass;
        this.moisture = moisture;
        this.scar = scar;
        this.infection = infection;
        this.sporeLoad = sporeLoad;
        this.humanAccess = humanAccess;
        this.hiveInfluence = hiveInfluence;
        this.infrastructureValue = infrastructureValue;
        this.routeKeys = List.copyOf(Objects.requireNonNull(routeKeys, "routeKeys"));
    }
}
