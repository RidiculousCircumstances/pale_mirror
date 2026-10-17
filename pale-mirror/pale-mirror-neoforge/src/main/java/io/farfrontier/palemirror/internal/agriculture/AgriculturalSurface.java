package io.farfrontier.palemirror.internal.agriculture;

import io.farfrontier.palemirror.frontier.v3.model.agriculture.GrayboxAgriculture;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Visual content only. Fertility belongs to the shared biome map, never to these decorative blocks. */
final class AgriculturalSurface {
    private AgriculturalSurface() { }
    static BlockState at(int x, int z) {
        return switch (GrayboxAgriculture.rules().biomeAt(x, z)) {
            case GrayboxAgriculture.FERTILE, GrayboxAgriculture.ORDINARY -> Blocks.GRASS_BLOCK.defaultBlockState();
            case GrayboxAgriculture.ROCKY -> Math.floorMod(Math.floorDiv(x, 3) * 31L + Math.floorDiv(z, 3) * 17L, 5) == 0
                    ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.STONE.defaultBlockState();
            default -> throw new IllegalStateException("undeclared agricultural surface biome");
        };
    }
}
