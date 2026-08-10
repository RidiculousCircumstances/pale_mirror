package io.farfrontier.palemirror.internal.materialization;

import io.farfrontier.palemirror.domain.ThreatTier;
import io.farfrontier.palemirror.internal.world.InfectionBiomeStage;
import io.farfrontier.palemirror.internal.world.MutableCell;

/**
 * Fixed, vanilla-only palette for the controlled test mine.  It is deliberately
 * a pure mapping: the domain owns the tier and this class owns no state.
 */
final class TestMineInfectionBiomePalette {
    static final int MAX_REGISTERED_CELLS = 70;

    private TestMineInfectionBiomePalette() { }

    static String desiredBlock(MutableCell cell, ThreatTier tier) {
        InfectionBiomeStage stage = cell.infectionStage();
        if (!stage.activeAt(tier)) return cell.baselineBlock();
        return switch (stage) {
            case FOOTHOLD -> switch (tier) {
                case FOOTHOLD -> "minecraft:netherrack";
                case INFESTED -> "minecraft:crimson_nylium";
                case SIEGE, APEX -> "minecraft:nether_wart_block";
                case DORMANT -> cell.baselineBlock();
            };
            case INFESTED -> switch (tier) {
                case INFESTED -> "minecraft:netherrack";
                case SIEGE, APEX -> "minecraft:crimson_nylium";
                case DORMANT, FOOTHOLD -> cell.baselineBlock();
            };
            case SIEGE -> switch (tier) {
                case SIEGE -> "minecraft:netherrack";
                case APEX -> "minecraft:nether_wart_block";
                case DORMANT, FOOTHOLD, INFESTED -> cell.baselineBlock();
            };
            case APEX -> tier == ThreatTier.APEX ? "minecraft:shroomlight" : cell.baselineBlock();
            case NODE -> cell.baselineBlock();
        };
    }
}
