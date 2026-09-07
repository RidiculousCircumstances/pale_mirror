package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** One-tick ownership fence between an accepted player-action packet and vanilla's break event. */
final class FrontierV3PlayerBreakDisposition {
    private static final Map<MinecraftServer, Map<UUID, BlockPos>> ACCEPTED = new IdentityHashMap<>();

    private FrontierV3PlayerBreakDisposition() { }

    static void accept(MinecraftServer server, UUID player, BlockPos position) {
        Objects.requireNonNull(server, "server"); Objects.requireNonNull(player, "player"); Objects.requireNonNull(position, "position");
        ACCEPTED.computeIfAbsent(server, ignored -> new HashMap<>()).put(player, position.immutable());
    }

    static boolean accepted(MinecraftServer server, UUID player, BlockPos position) {
        Map<UUID, BlockPos> pending = ACCEPTED.get(Objects.requireNonNull(server, "server"));
        return pending != null && Objects.requireNonNull(position, "position").equals(pending.get(Objects.requireNonNull(player, "player")));
    }

    static boolean consume(MinecraftServer server, UUID player, BlockPos position) {
        Map<UUID, BlockPos> pending = ACCEPTED.get(Objects.requireNonNull(server, "server"));
        if (pending == null || !Objects.requireNonNull(position, "position").equals(pending.get(Objects.requireNonNull(player, "player")))) return false;
        pending.remove(player); if (pending.isEmpty()) ACCEPTED.remove(server); return true;
    }

    static void clear(MinecraftServer server) { ACCEPTED.remove(Objects.requireNonNull(server, "server")); }
}
