package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Pilot-only observation of the ordinary post-disconnect release fence. */
@EventBusSubscriber(modid = PaleMirrorMod.MOD_ID)
public final class FrontierV3PilotDemandLossReceipt {
    private static final Map<net.minecraft.server.MinecraftServer, Set<String>> PUBLISHED = new IdentityHashMap<>();

    private FrontierV3PilotDemandLossReceipt() { }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        net.minecraft.server.MinecraftServer server = event.getServer();
        Optional<String> request = FrontierV3PilotLifecycleSignal.normalDemandLossAuthorized();
        if (request.isEmpty() || PUBLISHED.getOrDefault(server, Set.of()).contains(request.get())
                || !server.getPlayerList().getPlayers().isEmpty() || !FrontierV3ServerLifecycle.normalDemandLossReleased(server)) return;
        FrontierV3PilotLifecycleSignal.normalDemandLossRelease(request.get());
        PUBLISHED.computeIfAbsent(server, ignored -> new HashSet<>()).add(request.get());
    }
}
