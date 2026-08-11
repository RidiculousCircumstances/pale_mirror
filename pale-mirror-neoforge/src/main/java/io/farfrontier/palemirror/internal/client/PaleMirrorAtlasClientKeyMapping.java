package io.farfrontier.palemirror.internal.client;

import io.farfrontier.palemirror.PaleMirrorMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/** Mod-bus registration is separate from the client game-event listener by NeoForge design. */
@EventBusSubscriber(modid = PaleMirrorMod.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class PaleMirrorAtlasClientKeyMapping {
    private PaleMirrorAtlasClientKeyMapping() { }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(PaleMirrorAtlasClientEvents.openAtlasKey());
    }
}
