package io.farfrontier.palemirror.internal.client;

import io.farfrontier.palemirror.PaleMirrorMod;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid=PaleMirrorMod.MOD_ID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class PaleMirrorCombatRenderers {
    static final ModelLayerLocation LAYER=new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(PaleMirrorMod.MOD_ID,"combat_villager"),"main");
    @SubscribeEvent public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event){event.registerLayerDefinition(LAYER,CombatVillagerModel::layer);}
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event){event.registerEntityRenderer(EntityType.VILLAGER,ResidentResourcePacks.enabled()?HumanVillagerRenderer::create:CombatVillagerRenderer::new);}
    @SubscribeEvent public static void packs(net.neoforged.neoforge.event.AddPackFindersEvent event){ResidentResourcePacks.register(event);}
    @SubscribeEvent public static void setup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event){
        if(net.neoforged.fml.ModList.get().isLoaded("entity_model_features"))event.enqueueWork(HumanEraVisuals::install);
    }
    private PaleMirrorCombatRenderers(){}
}
