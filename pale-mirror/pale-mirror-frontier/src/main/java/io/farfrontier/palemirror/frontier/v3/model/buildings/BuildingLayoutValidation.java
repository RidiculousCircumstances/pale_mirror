package io.farfrontier.palemirror.frontier.v3.model.buildings;

import io.farfrontier.palemirror.frontier.v3.model.*;

import java.util.*;

/**
 * Placement admission only: an authored empty room cannot erase another structure's known solids.
 */
public final class BuildingLayoutValidation {
    public static void requireSeparateFabric(List<SettlementStructure> structures) {
        for (var structure : structures) {
            if (structure.definition().representation()
                    != BuildingDefinition.Representation.AUTHORED) continue;
            var bounds = structure.placement().volume(structure.definition().fabric());
            for (var other : structures) {
                if (other.id().equals(structure.id())) continue;
                boolean overlaps =
                        other.definition().representation()
                                        == BuildingDefinition.Representation.AUTHORED
                                ? bounds.overlaps(
                                        other.placement().volume(other.definition().fabric()))
                                : SettlementStructureFootprint.supportSurfaces(other).stream()
                                        .anyMatch(surface -> bounds.contains(surface.support()));
                if (overlaps)
                    throw new IllegalArgumentException(
                            "building fabric overlaps another facility: "
                                    + structure.id()
                                    + " / "
                                    + other.id());
            }
        }
    }

    private BuildingLayoutValidation() {}
}
