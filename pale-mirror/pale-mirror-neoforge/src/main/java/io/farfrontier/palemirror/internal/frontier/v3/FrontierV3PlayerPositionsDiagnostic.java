package io.farfrontier.palemirror.internal.frontier.v3;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.farfrontier.palemirror.frontier.v3.api.FrontierScheduleView;
import net.minecraft.server.MinecraftServer;
import java.util.Comparator;

/** Native observation only. No player history, chunk access, simulation work or mutations. */
final class FrontierV3PlayerPositionsDiagnostic {
    private static final int MAX_PLAYERS = 32;
    private FrontierV3PlayerPositionsDiagnostic() { }

    static String render(MinecraftServer server, FrontierV3ServerRuntime<?, ?> runtime, FrontierScheduleView checkpoint) {
        if (!server.isSameThread()) throw new IllegalStateException("player observation outside server thread");
        var result = new JsonObject();
        result.addProperty("schema", 1); result.addProperty("kind", "players");
        result.addProperty("status", "ok"); result.addProperty("instant", checkpoint.instant().ticks());
        result.addProperty("worldInstance", FrontierV3EconomyObservatory.worldIdentity(runtime).orElse(null));
        result.addProperty("mapDimension", FrontierV3PhysicalWorld.DIMENSION.location().toString());
        var online = server.getPlayerList().getPlayers();
        var players = new JsonArray();
        online.stream().sorted(Comparator.comparing(player -> player.getUUID().toString())).limit(MAX_PLAYERS).forEach(player -> {
            var point = new JsonObject();
            point.addProperty("id", player.getUUID().toString());
            point.addProperty("name", player.getGameProfile().getName());
            point.addProperty("dimension", player.level().dimension().location().toString());
            point.addProperty("x", player.getX()); point.addProperty("y", player.getY()); point.addProperty("z", player.getZ());
            point.addProperty("yaw", player.getYRot()); players.add(point);
        });
        result.add("players", players); result.addProperty("total", online.size());
        result.addProperty("truncated", online.size() > MAX_PLAYERS);
        return FrontierV3DiagnosticJson.bounded("players", "", checkpoint, result.toString());
    }
}
