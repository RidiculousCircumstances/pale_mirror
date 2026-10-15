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
    // Explicit full-cube navigation facts, independent of whether a block has a mining recipe.
    // Unsupported shapes remain obstacles until a provider supplies their collision facts.
    private static final Set<String> FULL_SUPPORT = Set.of("minecraft:stone", "minecraft:cobblestone",
            "minecraft:deepslate", "minecraft:cobbled_deepslate", "minecraft:coal_ore", "minecraft:deepslate_coal_ore",
            "minecraft:iron_ore", "minecraft:deepslate_iron_ore", "minecraft:copper_ore", "minecraft:deepslate_copper_ore",
            "minecraft:dirt", "minecraft:grass_block", "minecraft:coarse_dirt", "minecraft:gravel", "minecraft:sand",
            "minecraft:andesite", "minecraft:diorite", "minecraft:granite", "minecraft:tuff", "minecraft:bedrock");
    public static KnownBlockNavigationProjection.Cell navigationCell(String blockKind) {
        return CLEAR.contains(blockKind) ? KnownBlockNavigationProjection.Cell.EMPTY
                : FULL_SUPPORT.contains(blockKind) ? KnownBlockNavigationProjection.Cell.FULL_SUPPORT
                : KnownBlockNavigationProjection.Cell.OBSTACLE;
    }
    private KnownBlockClearance() { }
}
