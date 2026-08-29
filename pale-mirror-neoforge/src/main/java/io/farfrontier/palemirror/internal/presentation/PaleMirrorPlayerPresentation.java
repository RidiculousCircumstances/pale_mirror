package io.farfrontier.palemirror.internal.presentation;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import io.farfrontier.palemirror.internal.network.PaleMirrorNetwork;
import io.farfrontier.palemirror.internal.network.PlayerContextCardPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The sole server-side delivery boundary for compact, noncanonical player notices.
 *
 * <p>World boards own persistent object state; the future journal/atlas owns
 * history and work queues.  This class owns only a bounded, per-connected-player
 * suppression window for direct rejections, explicit inspection cards and
 * durable critical alerts. Successful actions remain legible through their real
 * inventory/world consequence and the object board, never as a HUD ticker.</p>
 */
public final class PaleMirrorPlayerPresentation {
    private static final int MAX_CONNECTED_PLAYERS = 128;
    private static final int CONTEXT_DURATION_TICKS = 80;
    private static final Map<MinecraftServer, Map<UUID, PlayerNoticeGate>> GATES = new IdentityHashMap<>();

    private PaleMirrorPlayerPresentation() { }

    /**
     * One compact refusal of the recipient's just-completed physical action.
     * Successful outcomes must be shown by their physical result or an explicit
     * object inspection, not by replacing the Minecraft action bar.
     */
    public static void actionRejected(ServerPlayer player, String key, Component message) {
        deliver(player, new PlayerNoticeGate.Notice(key, PlayerNoticeGate.Channel.ACTION_BAR,
                PlayerNoticeGate.Priority.REJECTION, PlayerNoticeGate.Origin.PLAYER_ACTION, 100), oneLine(message).withStyle(ChatFormatting.RED));
    }

    /** Replaces the one short card after an explicit object inspection; it is never a background alert. */
    public static void inspect(ServerPlayer player, String key, PlayerContextCard card) {
        Objects.requireNonNull(player, "player"); Objects.requireNonNull(card, "card");
        PlayerNoticeGate gate = gates(Objects.requireNonNull(player.getServer(), "player server"))
                .computeIfAbsent(player.getUUID(), ignored -> new PlayerNoticeGate());
        if (gate.admit(new PlayerNoticeGate.Notice(key, PlayerNoticeGate.Channel.CONTEXT_CARD,
                PlayerNoticeGate.Priority.CONTEXT, PlayerNoticeGate.Origin.PLAYER_ACTION, 20), player.serverLevel().getGameTime()) != PlayerNoticeGate.Decision.DELIVER) return;
        PaleMirrorNetwork.sendContextCard(player, new PlayerContextCardPayload(card.title(), card.lines(), CONTEXT_DURATION_TICKS, card.accentRgb()));
    }

    public static void critical(ServerPlayer player, String key, Component message) {
        deliver(player, new PlayerNoticeGate.Notice(key, PlayerNoticeGate.Channel.CHAT,
                PlayerNoticeGate.Priority.CRITICAL, PlayerNoticeGate.Origin.BACKGROUND, 100), message.copy().withStyle(ChatFormatting.RED));
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

    private static MutableComponent oneLine(Component message) {
        String line = message.getString().replace('\n', ' ').replaceAll("\\s+", " ").trim();
        if (line.length() > 112) line = line.substring(0, 111) + "…";
        return Component.literal(line);
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
