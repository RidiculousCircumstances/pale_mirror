package io.farfrontier.palemirror.internal.client;

import io.farfrontier.palemirror.PaleMirrorMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/** Draws the one contextual card after the normal HUD without replacing Minecraft's own UI. */
@EventBusSubscriber(modid = PaleMirrorMod.MOD_ID, value = Dist.CLIENT)
public final class PaleMirrorContextCardClientEvents {
    private PaleMirrorContextCardClientEvents() { }

    @SubscribeEvent
    public static void render(RenderGuiEvent.Post event) {
        PaleMirrorContextCardClient.render(event.getGuiGraphics());
    }
}
