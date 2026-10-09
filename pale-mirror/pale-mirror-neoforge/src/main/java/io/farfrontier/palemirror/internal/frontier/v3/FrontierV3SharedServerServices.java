package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.internal.integration.distanthorizons.DistantHorizonsRuntime;
import net.minecraft.server.MinecraftServer;
import java.util.IdentityHashMap;
import java.util.Map;

/** Shared presentation service lifetime, independent of the retired simulation/SavedData. */
public final class FrontierV3SharedServerServices {
    private static final Map<MinecraftServer, DistantHorizonsRuntime> SERVICES = new IdentityHashMap<>();
    private FrontierV3SharedServerServices() { }
    public static void start(MinecraftServer server) { SERVICES.computeIfAbsent(server, ignored -> DistantHorizonsRuntime.create()); }
    public static void tick(MinecraftServer server) { var service = SERVICES.get(server); if (service != null) service.tick(server); }
    public static void stop(MinecraftServer server) { var service = SERVICES.remove(server); if (service != null) service.close(); }
}
