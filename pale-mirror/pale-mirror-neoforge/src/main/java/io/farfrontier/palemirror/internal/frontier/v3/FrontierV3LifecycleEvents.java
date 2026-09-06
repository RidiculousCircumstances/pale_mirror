package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/** V3-only server lifecycle bridge; teardown leaves are not COLD hand-offs. */
@EventBusSubscriber(modid = PaleMirrorMod.MOD_ID)
final class FrontierV3LifecycleEvents {
    private FrontierV3LifecycleEvents() { }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        FrontierV3ServerLifecycle.beginStopping(event.getServer());
    }
}
