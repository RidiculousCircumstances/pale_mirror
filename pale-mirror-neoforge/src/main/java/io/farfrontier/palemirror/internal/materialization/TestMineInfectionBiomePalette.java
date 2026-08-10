package io.farfrontier.palemirror.internal.materialization;

import io.farfrontier.palemirror.domain.ThreatTier;
import io.farfrontier.palemirror.domain.InfectionSourceId;
import io.farfrontier.palemirror.internal.world.InfectionBiomeStage;
import io.farfrontier.palemirror.internal.world.MutableCell;

/**
 * Fixed, vanilla-only palette for the controlled test mine.  It is deliberately
 * a pure mapping: the domain owns the tier and this class owns no state.
 */
final class TestMineInfectionBiomePalette {
    static final int MAX_REGISTERED_CELLS = 70;

    private TestMineInfectionBiomePalette() { }

    static String desiredBlock(MutableCell cell, InfectionSourceId source, ThreatTier tier) {
        return switch (source.value()) {
            case "pale_mirror:crimson" -> crimsonBlock(cell, tier);
            case "pale_mirror:spore" -> sporeBlock(cell, tier);
            default -> cell.baselineBlock();
        };
    }

    private static String crimsonBlock(MutableCell cell, ThreatTier tier) {
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

    /**
     * Spore's native terrain conversion stays disabled.  This is an entirely
     * PM-owned, vanilla-only visual palette and therefore cannot spread past
     * the recorded cells or claim third-party block provenance.
     */
    private static String sporeBlock(MutableCell cell, ThreatTier tier) {
        InfectionBiomeStage stage = cell.infectionStage();
        if (!stage.activeAt(tier)) return cell.baselineBlock();
        return switch (stage) {
            case FOOTHOLD -> switch (tier) {
                case FOOTHOLD -> "minecraft:moss_block";
                case INFESTED -> "minecraft:mycelium";
                case SIEGE, APEX -> "minecraft:brown_mushroom_block";
                case DORMANT -> cell.baselineBlock();
            };
            case INFESTED -> switch (tier) {
                case INFESTED -> "minecraft:moss_block";
                case SIEGE, APEX -> "minecraft:mycelium";
                case DORMANT, FOOTHOLD -> cell.baselineBlock();
            };
            case SIEGE -> switch (tier) {
                case SIEGE -> "minecraft:moss_block";
                case APEX -> "minecraft:brown_mushroom_block";
                case DORMANT, FOOTHOLD, INFESTED -> cell.baselineBlock();
            };
            case APEX -> tier == ThreatTier.APEX ? "minecraft:verdant_froglight" : cell.baselineBlock();
            case NODE -> cell.baselineBlock();
        };
    }
}
