package io.farfrontier.palemirror.frontier.v3.model.buildings;

import java.util.*;
/** Content-owned ground datum and intentional below-grade voids, independent of the entrance floor. */
public record BuildingSiteDefinition(int groundY, List<BuildingVolume> excavations) {
    public BuildingSiteDefinition {
        excavations = List.copyOf(excavations);
        if (Math.abs(groundY) > 256 || excavations.size() > 32) throw new IllegalArgumentException("unbounded building site requirements");
    }
}
