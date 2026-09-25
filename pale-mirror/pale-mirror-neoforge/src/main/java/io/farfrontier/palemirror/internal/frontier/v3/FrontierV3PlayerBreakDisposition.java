package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Ownership fence between an accepted player-action packet and vanilla's break event. */
final class FrontierV3PlayerBreakDisposition {
    private static final Map<MinecraftServer, Map<UUID, Accepted>> ACCEPTED = new IdentityHashMap<>();
    private record Accepted(BlockPos position) { }

    private FrontierV3PlayerBreakDisposition() { }

    static void accept(MinecraftServer server, UUID player, BlockPos position) {
        Objects.requireNonNull(server, "server"); Objects.requireNonNull(player, "player"); Objects.requireNonNull(position, "position");
        ACCEPTED.computeIfAbsent(server, ignored -> new HashMap<>()).put(player, new Accepted(position.immutable()));
    }

    static boolean accepted(MinecraftServer server, UUID player, BlockPos position) {
        Map<UUID, Accepted> pending = ACCEPTED.get(Objects.requireNonNull(server, "server"));
        Accepted accepted = pending == null ? null : pending.get(Objects.requireNonNull(player, "player"));
        return accepted != null && Objects.requireNonNull(position, "position").equals(accepted.position());
    }

    /** Only Vanilla's real mining state may defer recovery; no wall-clock expiry guesses. */
    static boolean active(MinecraftServer server, UUID player, BlockPos position) {
        Map<UUID, Accepted> pending = ACCEPTED.get(Objects.requireNonNull(server, "server"));
        Accepted accepted = pending == null ? null : pending.get(Objects.requireNonNull(player, "player"));
        var online = server.getPlayerList().getPlayer(player);
        return accepted != null && Objects.requireNonNull(position, "position").equals(accepted.position())
                && online != null
                && ((FrontierV3BlockBreakProgress) online.gameMode).frontierV3$isMining(position);
    }

    static boolean consume(MinecraftServer server, UUID player, BlockPos position) {
        Map<UUID, Accepted> pending = ACCEPTED.get(Objects.requireNonNull(server, "server"));
        Accepted accepted = pending == null ? null : pending.get(Objects.requireNonNull(player, "player"));
        if (accepted == null || !Objects.requireNonNull(position, "position").equals(accepted.position())) return false;
        pending.remove(player); if (pending.isEmpty()) ACCEPTED.remove(server); return true;
    }

    static void clear(MinecraftServer server) { ACCEPTED.remove(Objects.requireNonNull(server, "server")); }
}
