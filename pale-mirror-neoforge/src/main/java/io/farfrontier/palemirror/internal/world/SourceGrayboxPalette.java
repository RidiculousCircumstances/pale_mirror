package io.farfrontier.palemirror.internal.world;

import java.util.Locale;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Stable colour vocabulary for readable source-graybox records. */
final class SourceGrayboxPalette {
    private SourceGrayboxPalette() { }

    static BlockState block(String colour) {
        String token = colour.toLowerCase(Locale.ROOT);
        if (token.contains("collapsed") || token.contains("disabled") || token.contains("destroyed") || token.contains("feral_severe")
                || token.contains("abandoned") || token.contains("failed") || token.contains("aborted")) return Blocks.BLACK_WOOL.defaultBlockState();
        if (token.contains("feral_active") || token.contains("route.disrupted") || token.contains("sector.hive")
                || token.contains("siege") || token.contains("core") || token.contains("strongpoint") || token.contains("engage")
                || token.contains("engaging") || token.contains("intercepted")) {
            return Blocks.RED_WOOL.defaultBlockState();
        }
        if (token.contains("emergency") || token.contains("mine") || token.contains("breaker") || token.contains("trace")
                || token.contains("sector.contested") || token.contains("assessment") || token.contains("build_up")) {
            return Blocks.ORANGE_WOOL.defaultBlockState();
        }
        if (token.contains("watch") || token.contains("armory") || token.contains("power") || token.contains("observation_post")
                || token.contains("energy") || token.contains("tools") || token.contains("assembling")) return Blocks.YELLOW_WOOL.defaultBlockState();
        if (token.contains("recovery") || token.contains("farm") || token.contains("digestive") || token.contains("harvester")
                || token.contains("cargo.food") || token.contains("cargo.seeds")) return Blocks.LIME_WOOL.defaultBlockState();
        if (token.contains("clinic") || token.contains("housing") || token.contains("medicine")) return Blocks.WHITE_WOOL.defaultBlockState();
        if (token.contains("warehouse") || token.contains("forest") || token.contains("wood") || token.contains("timber")) return Blocks.BROWN_WOOL.defaultBlockState();
        if (token.contains("synapse") || token.contains("signal") || token.contains("quarantin") || token.contains("carrier")
                || token.contains("sporulator") || token.contains("contaminated")) return Blocks.PURPLE_WOOL.defaultBlockState();
        if (token.contains("brood") || token.contains("chrysalis")) return Blocks.PINK_WOOL.defaultBlockState();
        if (token.contains("workshop") || token.contains("route.open") || token.contains("supply_corridor")
                || token.contains("en_route") || token.contains("forward_base")) return Blocks.BLUE_WOOL.defaultBlockState();
        if (token.contains("fortification") || token.contains("fortified_line") || token.contains("field") || token.contains("sector.human")
                || token.contains("checkpoint") || token.contains("returning") || token.contains("withdraw")) return Blocks.CYAN_WOOL.defaultBlockState();
        if (token.contains("cargo.weapons") || token.contains("cargo.ammo")) return Blocks.RED_WOOL.defaultBlockState();
        if (token.contains("cargo.ore")) return Blocks.ORANGE_WOOL.defaultBlockState();
        return Blocks.LIGHT_GRAY_WOOL.defaultBlockState();
    }

    static Item residentHat(String occupation, String condition) {
        if (condition.equalsIgnoreCase("wounded")) return Items.WHITE_WOOL;
        return switch (occupation.toLowerCase(Locale.ROOT)) {
            case "farmer" -> Items.LIME_WOOL;
            case "miner" -> Items.ORANGE_WOOL;
            case "forester" -> Items.GREEN_WOOL;
            case "engineer" -> Items.YELLOW_WOOL;
            case "medic" -> Items.WHITE_WOOL;
            case "merchant" -> Items.PURPLE_WOOL;
            case "guard" -> Items.RED_WOOL;
            default -> Items.LIGHT_GRAY_WOOL;
        };
    }

    static Item bioformHat(String kind) {
        return switch (kind.toLowerCase(Locale.ROOT)) {
            case "harvester" -> Items.LIME_WOOL;
            case "raider" -> Items.RED_WOOL;
            case "breaker" -> Items.ORANGE_WOOL;
            case "propagule_carrier" -> Items.MAGENTA_WOOL;
            default -> Items.BLACK_WOOL;
        };
    }

    static boolean managed(Block block) {
        return block == Blocks.WHITE_WOOL || block == Blocks.ORANGE_WOOL || block == Blocks.MAGENTA_WOOL
                || block == Blocks.LIGHT_BLUE_WOOL || block == Blocks.YELLOW_WOOL || block == Blocks.LIME_WOOL
                || block == Blocks.PINK_WOOL || block == Blocks.GRAY_WOOL || block == Blocks.LIGHT_GRAY_WOOL
                || block == Blocks.CYAN_WOOL || block == Blocks.PURPLE_WOOL || block == Blocks.BLUE_WOOL
                || block == Blocks.BROWN_WOOL || block == Blocks.GREEN_WOOL || block == Blocks.RED_WOOL
                || block == Blocks.BLACK_WOOL;
    }
}
