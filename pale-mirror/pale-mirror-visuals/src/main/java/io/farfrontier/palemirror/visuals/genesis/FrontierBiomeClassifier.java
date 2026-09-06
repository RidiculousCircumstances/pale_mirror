package io.farfrontier.palemirror.visuals.genesis;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;

/** Shared cheap biome semantics for settlement and mine placement ranking. */
final class FrontierBiomeClassifier {
    private FrontierBiomeClassifier() { }

    static FrontierSiteSelector.BiomeSample classify(Holder<Biome> holder) {
        ResourceKey<Biome> key = holder.unwrapKey().orElse(null);
        String path = key == null ? "" : key.location().getPath();
        boolean water = holder.is(BiomeTags.IS_OCEAN) || holder.is(BiomeTags.IS_RIVER)
                || path.contains("swamp");
        boolean hostileTerrain = path.contains("peak") || path.contains("slope")
                || path.contains("windswept") || path.contains("mountain") || path.contains("jagged");
        boolean suitable = !water && !hostileTerrain;
        FrontierClimate climate;
        if (holder.is(BiomeTags.IS_TAIGA) || holder.value().getBaseTemperature() < 0.3F) {
            climate = FrontierClimate.COLD_TAIGA;
        } else {
            climate = path.contains("desert") || path.contains("badlands") || path.contains("savanna")
                    ? FrontierClimate.DRY_ARID : FrontierClimate.TEMPERATE;
        }
        boolean denseVegetation = path.contains("jungle") || path.contains("dark_forest")
                || path.contains("mushroom") || path.contains("rainforest");
        boolean openGround = path.contains("plains") || path.contains("meadow") || path.contains("savanna")
                || path.contains("desert") || path.contains("badlands") || path.contains("steppe");
        int vegetationBurden = denseVegetation ? 3
                : path.contains("forest") || path.contains("taiga") || path.contains("grove") ? 2
                : openGround ? 0 : 1;
        int preference = openGround ? 0 : vegetationBurden == 1 ? 1 : 2;
        return new FrontierSiteSelector.BiomeSample(climate, suitable, water, preference,
                hostileTerrain, vegetationBurden, true);
    }
}
