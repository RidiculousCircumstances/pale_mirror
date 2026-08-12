package io.farfrontier.palemirror.visuals.genesis;

import io.farfrontier.palemirror.visuals.PaleMirrorVisualsMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registered once; data-driven configured/placed feature invokes the chunk-local catalog. */
public final class VisualWorldgenFeatures {
    private static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE,
            PaleMirrorVisualsMod.MOD_ID);
    public static final DeferredHolder<Feature<?>, Feature<NoneFeatureConfiguration>> AUTHORED_REGION =
            FEATURES.register("authored_region", () -> new FrontierWorldgenFeature(NoneFeatureConfiguration.CODEC));

    private VisualWorldgenFeatures() { }

    public static void register(IEventBus bus) { FEATURES.register(bus); }
}
