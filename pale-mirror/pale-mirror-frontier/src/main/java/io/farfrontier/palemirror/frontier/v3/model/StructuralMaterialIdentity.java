package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationBlockState;
import java.util.Set;

/** Structural identity excludes operating state and neighbour-derived connections.
 * Typed block-entity material data must additionally be witnessed by the native observer. */
public final class StructuralMaterialIdentity {
    private static final Set<String> OPERATING = Set.of("open", "powered", "lit", "waterlogged", "occupied",
            "shape", "north", "south", "east", "west", "up", "down");
    private StructuralMaterialIdentity() { }
    public static boolean matches(NavigationBlockState original, NavigationBlockState actual) {
        return original.kind().equals(actual.kind()) && original.properties().entrySet().stream()
                .filter(entry -> !OPERATING.contains(entry.getKey()))
                .allMatch(entry -> normalized(original.kind(), entry.getKey(), entry.getValue())
                        .equals(normalized(actual.kind(), entry.getKey(), actual.properties().get(entry.getKey()))));
    }
    private static String normalized(String kind, String property, String value) {
        // Vanilla fence-gate use can reverse facing along its existing axis.
        // A ninety-degree rotation still changes the installed passage.
        if (kind.startsWith("minecraft:") && kind.endsWith("_fence_gate") && property.equals("facing") && value != null)
            return switch (value) {
                case "north", "south" -> "z";
                case "east", "west" -> "x";
                default -> value;
            };
        if (kind.equals("minecolonies:blockminecoloniesrack") && property.equals("variant") && value != null)
            return switch (value) {
                case "blockrackemptysingle", "blockrackfullsingle" -> "single";
                case "blockrackempty", "blockrackfull" -> "double";
                default -> value;
            };
        return value;
    }
}
