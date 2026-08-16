package io.farfrontier.palemirror.visuals.runtime;

import io.farfrontier.palemirror.visuals.PaleMirrorVisualsMod;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Reconciles retained desired projections only against naturally loaded chunks. */
@EventBusSubscriber(modid = PaleMirrorVisualsMod.MOD_ID)
public final class DynamicVisualRuntime {
    private DynamicVisualRuntime() { }

    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post event) {
        long cueTick = event.getServer().getTickCount();
        if (cueTick % 20L != 0L) return;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            AuthoredVisualProvider.INSTANCE.tickVisuals(level, cueTick);
        }
    }
}
