package io.farfrontier.palemirror.internal.presentation;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The sole server-side delivery boundary for compact, noncanonical player notices.
 *
 * <p>World boards own persistent object state; the future journal/atlas owns
 * history and work queues.  This class owns only a bounded, per-connected-player
 * suppression window for immediate interaction feedback.</p>
 */
public final class PaleMirrorPlayerPresentation {
    private static final int MAX_CONNECTED_PLAYERS = 128;
    private static final Map<MinecraftServer, Map<UUID, PlayerNoticeGate>> GATES = new IdentityHashMap<>();

    private PaleMirrorPlayerPresentation() { }

    public static void transientAction(ServerPlayer player, String key, Component message) {
        deliver(player, new PlayerNoticeGate.Notice(key, PlayerNoticeGate.Channel.ACTION_BAR,
                PlayerNoticeGate.Priority.TRANSIENT, 20), message.copy().withStyle(ChatFormatting.GRAY));
    }

    public static void action(ServerPlayer player, String key, Component message) {
        deliver(player, new PlayerNoticeGate.Notice(key, PlayerNoticeGate.Channel.ACTION_BAR,
                PlayerNoticeGate.Priority.ACTION, 8), message.copy().withStyle(ChatFormatting.YELLOW));
    }

    public static void critical(ServerPlayer player, String key, Component message) {
        deliver(player, new PlayerNoticeGate.Notice(key, PlayerNoticeGate.Channel.CHAT,
                PlayerNoticeGate.Priority.CRITICAL, 100), message.copy().withStyle(ChatFormatting.RED));
    }

    /** Clears the only ephemeral state at server shutdown; no player/world state is retained. */
    public static void clear(MinecraftServer server) {
        GATES.remove(Objects.requireNonNull(server, "server"));
    }

    private static void deliver(ServerPlayer player, PlayerNoticeGate.Notice notice, Component message) {
        Objects.requireNonNull(player, "player"); Objects.requireNonNull(message, "message");
        MinecraftServer server = Objects.requireNonNull(player.getServer(), "player server");
        PlayerNoticeGate gate = gates(server).computeIfAbsent(player.getUUID(), ignored -> new PlayerNoticeGate());
        if (gate.admit(notice, player.serverLevel().getGameTime()) != PlayerNoticeGate.Decision.DELIVER) return;
        if (notice.channel() == PlayerNoticeGate.Channel.ACTION_BAR) player.displayClientMessage(message, true);
        else player.sendSystemMessage(message);
    }

    private static Map<UUID, PlayerNoticeGate> gates(MinecraftServer server) {
        return GATES.computeIfAbsent(server, ignored -> new LinkedHashMap<>(MAX_CONNECTED_PLAYERS + 1, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<UUID, PlayerNoticeGate> eldest) {
                return size() > MAX_CONNECTED_PLAYERS;
            }
        });
    }
}
