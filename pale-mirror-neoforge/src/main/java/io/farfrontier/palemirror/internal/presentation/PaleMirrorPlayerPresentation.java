package io.farfrontier.palemirror.internal.presentation;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import io.farfrontier.palemirror.internal.network.PaleMirrorNetwork;
import io.farfrontier.palemirror.internal.network.PlayerContextCardPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * The sole server-side delivery boundary for compact, noncanonical player notices.
 *
 * <p>World boards own persistent object state; the future journal/atlas owns
 * history and work queues.  This class owns only a bounded, per-connected-player
 * suppression window for direct rejection cues and explicit inspection cards.
 * Successful actions remain legible through their real inventory/world
 * consequence and the object board, never as a HUD ticker.</p>
 */
public final class PaleMirrorPlayerPresentation {
    private static final int MAX_CONNECTED_PLAYERS = 128;
    private static final int CONTEXT_DURATION_TICKS = 80;
    private static final Map<MinecraftServer, Map<UUID, PlayerNoticeGate>> GATES = new IdentityHashMap<>();

    private PaleMirrorPlayerPresentation() { }

    /**
     * A local, non-textual refusal of the recipient's just-completed physical
     * action. The reason remains on the affected object board or explicit
     * inspection card; PM never claims the action bar or chat for it.
     */
    public static void actionRejected(ServerPlayer player, String key) {
        Objects.requireNonNull(player, "player");
        PlayerNoticeGate gate = gates(Objects.requireNonNull(player.getServer(), "player server"))
                .computeIfAbsent(player.getUUID(), ignored -> new PlayerNoticeGate());
        if (gate.admit(new PlayerNoticeGate.Notice(key, PlayerNoticeGate.Channel.DENIAL_CUE,
                PlayerNoticeGate.Priority.REJECTION, PlayerNoticeGate.Origin.PLAYER_ACTION, 100),
                player.serverLevel().getGameTime()) != PlayerNoticeGate.Decision.DELIVER) return;
        player.playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.35F, 0.9F);
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

    /** Clears the only ephemeral state at server shutdown; no player/world state is retained. */
    public static void clear(MinecraftServer server) {
        GATES.remove(Objects.requireNonNull(server, "server"));
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
