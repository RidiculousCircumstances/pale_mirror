package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.GrayboxMaterial;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Native palette shared by generation, observation and explicit runtime mutations. */
final class FrontierV3GrayboxPalette {
    private FrontierV3GrayboxPalette() { }
    /** Operating and neighbour-derived properties do not replace a structural material. */
    static boolean sameStructure(BlockState original, BlockState actual) {
        return io.farfrontier.palemirror.frontier.v3.model.StructuralMaterialIdentity.matches(
                FrontierV3BlockStates.describe(original), FrontierV3BlockStates.describe(actual));
    }
    static boolean matches(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos position,
            io.farfrontier.palemirror.frontier.v3.model.StructureMaterial material) {
        return sameStructure(block(material), level.getBlockState(position))
                && (!(material instanceof io.farfrontier.palemirror.frontier.v3.model.StructureMaterial.Authored authored)
                    || authored.data().isEmpty() || FrontierV3AuthoredBlockData.matches(level, position, authored.data().orElseThrow()));
    }
    static BlockState block(io.farfrontier.palemirror.frontier.v3.model.StructureMaterial material) {
        return switch(material) {
            case io.farfrontier.palemirror.frontier.v3.model.StructureMaterial.Authored authored -> FrontierV3BlockStates.resolve(authored.block());
            case GrayboxMaterial palette -> block(palette);
        };
    }
    static BlockState block(GrayboxMaterial material) {
        return (switch (material) {
            case HALL, HIVE_COCOON -> Blocks.WHITE_CONCRETE;
            case HOUSING, HIVE_SPORULATOR -> Blocks.ORANGE_CONCRETE;
            case FARM, HIVE_MORPHER -> Blocks.LIME_CONCRETE;
            case WORKSHOP -> Blocks.BLUE_CONCRETE;
            case WORKSHOP_INPUT, HIVE_HIBERNACULUM -> Blocks.CYAN_CONCRETE;
            case WORKSHOP_PROCESS, HIVE_STORE -> Blocks.MAGENTA_CONCRETE;
            case DEPOT -> Blocks.YELLOW_CONCRETE;
            case INFIRMARY, HIVE_RELAY -> Blocks.PINK_CONCRETE;
            case HIVE_GANGLION -> Blocks.RED_CONCRETE;
            case HIVE_BROOD -> Blocks.PURPLE_CONCRETE;
            case HIVE_DIGESTER -> Blocks.BROWN_CONCRETE;
            case HIVE_SENSOR, WORKSITE -> Blocks.LIGHT_BLUE_CONCRETE;
            case ROUTE -> Blocks.STONE_BRICKS;
            case ROUTE_FOUNDATION -> Blocks.COBBLESTONE;
            case INFECTION -> throw new IllegalArgumentException("infection requires the dynamic overlay executor");
        }).defaultBlockState();
    }
}
