package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationBlockState;
import java.util.Set;

/** Structural identity excludes operating state and neighbour-derived connections.
 * Typed block-entity material data must additionally be witnessed by the native observer. */
public final class StructuralMaterialIdentity {
    private static final Set<String> OPERATING = Set.of("open", "powered", "lit", "waterlogged",
            "shape", "north", "south", "east", "west", "up", "down");
    private StructuralMaterialIdentity() { }
    public static boolean matches(NavigationBlockState original, NavigationBlockState actual) {
        return original.kind().equals(actual.kind()) && original.properties().entrySet().stream()
                .filter(entry -> !OPERATING.contains(entry.getKey()))
                .allMatch(entry -> entry.getValue().equals(actual.properties().get(entry.getKey())));
    }
}
