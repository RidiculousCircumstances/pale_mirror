package io.farfrontier.palemirror.frontier.v3.model.geometry;

import java.util.Set;

/** Exact, state-independent empty-collision declarations for known vanilla blocks.
 * Other blocks remain conservative obstacles; air-like clearance is not permission
 * to remove an object, use it as support, or assume an unknown block is safe.
 */
public final class KnownBlockClearance {
    private static final Set<String> CLEAR = Set.of(
            "minecraft:air", "minecraft:cave_air", "minecraft:void_air",
            "minecraft:torch", "minecraft:wall_torch",
            "minecraft:soul_torch", "minecraft:soul_wall_torch",
            "minecraft:redstone_torch", "minecraft:redstone_wall_torch");

    public static boolean emptyCollision(String blockKind) { return CLEAR.contains(blockKind); }
    private KnownBlockClearance() { }
}
