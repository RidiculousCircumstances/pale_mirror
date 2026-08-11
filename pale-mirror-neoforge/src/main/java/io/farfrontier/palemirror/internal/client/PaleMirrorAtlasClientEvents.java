package io.farfrontier.palemirror.internal.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.internal.network.PaleMirrorNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

/** Client-only input lifecycle. Atlas data is requested only after the player asks for it. */
@EventBusSubscriber(modid = PaleMirrorMod.MOD_ID, value = Dist.CLIENT)
public final class PaleMirrorAtlasClientEvents {
    private static final KeyMapping OPEN_ATLAS = new KeyMapping("key.pale_mirror.open_atlas", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_P, "key.categories.pale_mirror");

    private PaleMirrorAtlasClientEvents() { }

    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) {
        while (OPEN_ATLAS.consumeClick()) {
            if (Minecraft.getInstance().player != null) PaleMirrorNetwork.requestAtlas();
        }
    }

    @SubscribeEvent
    public static void login(ClientPlayerNetworkEvent.LoggingIn event) {
        PaleMirrorNetwork.synchronizeAtlas();
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        PaleMirrorAtlasClient.clear();
    }

    static KeyMapping openAtlasKey() { return OPEN_ATLAS; }
}
