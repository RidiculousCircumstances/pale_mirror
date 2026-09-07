package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Pilot-only observation of the ordinary post-disconnect release fence. */
@EventBusSubscriber(modid = PaleMirrorMod.MOD_ID)
public final class FrontierV3PilotDemandLossReceipt {
    private static final Set<net.minecraft.server.MinecraftServer> PUBLISHED = Collections.newSetFromMap(new IdentityHashMap<>());

    private FrontierV3PilotDemandLossReceipt() { }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        net.minecraft.server.MinecraftServer server = event.getServer();
        if (PUBLISHED.contains(server) || !FrontierV3PilotLifecycleSignal.normalDemandLossAuthorized()
                || !server.getPlayerList().getPlayers().isEmpty() || !FrontierV3ServerLifecycle.normalDemandLossReleased(server)) return;
        FrontierV3PilotLifecycleSignal.normalDemandLossRelease();
        PUBLISHED.add(server);
    }
}
