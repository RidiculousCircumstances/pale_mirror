package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.GrayboxMaterial;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Native palette shared by generation, observation and explicit runtime mutations. */
final class FrontierV3GrayboxPalette {
    private FrontierV3GrayboxPalette() { }
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
            case ROUTE, ROUTE_FOUNDATION -> Blocks.GRAY_CONCRETE;
            case INFECTION -> throw new IllegalArgumentException("infection requires the dynamic overlay executor");
        }).defaultBlockState();
    }
}
