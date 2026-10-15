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
    private record Session(PlayerNoticeGate gate, ContextCardPager pager) {
        Session() { this(new PlayerNoticeGate(), new ContextCardPager()); }
    }
    private static final Map<MinecraftServer, Map<UUID, Session>> GATES = new IdentityHashMap<>();

    private PaleMirrorPlayerPresentation() { }

    /**
     * A local, non-textual refusal of the recipient's just-completed physical
     * action. The reason remains on the affected object board or explicit
     * inspection card; PM never claims the action bar or chat for it.
     */
    public static void actionRejected(ServerPlayer player, String key) {
        Objects.requireNonNull(player, "player");
        PlayerNoticeGate gate = gates(Objects.requireNonNull(player.getServer(), "player server"))
                .computeIfAbsent(player.getUUID(), ignored -> new Session()).gate();
        if (gate.admit(new PlayerNoticeGate.Notice(key, PlayerNoticeGate.Channel.DENIAL_CUE,
                PlayerNoticeGate.Priority.REJECTION, PlayerNoticeGate.Origin.PLAYER_ACTION, 100),
                player.serverLevel().getGameTime()) != PlayerNoticeGate.Decision.DELIVER) return;
        player.playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.35F, 0.9F);
    }

    /** Replaces the one short card after an explicit object inspection; it is never a background alert. */
    public static void inspect(ServerPlayer player, String key, PlayerContextCard card) {
        Objects.requireNonNull(player, "player"); Objects.requireNonNull(card, "card");
        PlayerNoticeGate gate = gates(Objects.requireNonNull(player.getServer(), "player server"))
                .computeIfAbsent(player.getUUID(), ignored -> new Session()).gate();
        if (gate.admit(new PlayerNoticeGate.Notice(key, PlayerNoticeGate.Channel.CONTEXT_CARD,
                PlayerNoticeGate.Priority.CONTEXT, PlayerNoticeGate.Origin.PLAYER_ACTION, 20), player.serverLevel().getGameTime()) != PlayerNoticeGate.Decision.DELIVER) return;
        int duration = card.lines().size() > 2 ? 200 : CONTEXT_DURATION_TICKS;
        PaleMirrorNetwork.sendContextCard(player, new PlayerContextCardPayload(card.title(), card.lines(), duration, card.accentRgb()));
    }

    /** Explicit repeated inspection pages; duplicate interaction events must not advance the cursor. */
    public static void inspectPages(ServerPlayer player, String key, java.util.List<PlayerContextCard> pages) {
        if (pages.isEmpty() || pages.stream().anyMatch(card -> card.lines().size() >= PlayerContextCard.MAX_LINES))
            throw new IllegalArgumentException("paged cards must leave room for navigation");
        var session = gates(Objects.requireNonNull(player.getServer(), "player server"))
                .computeIfAbsent(player.getUUID(), ignored -> new Session());
        long now = player.serverLevel().getGameTime();
        if (session.gate().admit(new PlayerNoticeGate.Notice(key, PlayerNoticeGate.Channel.CONTEXT_CARD,
                PlayerNoticeGate.Priority.CONTEXT, PlayerNoticeGate.Origin.PLAYER_ACTION, 20), now)
                != PlayerNoticeGate.Decision.DELIVER) return;
        int index = session.pager().next(key, pages.size(), now, player.isShiftKeyDown());
        PaleMirrorNetwork.sendContextCard(player, pagePayload(pages, index));
    }

    /** Payload construction is shared with the boundary regression, including protocol limits. */
    static PlayerContextCardPayload pagePayload(java.util.List<PlayerContextCard> pages, int index) {
        var card = pages.get(index);
        var lines = new java.util.ArrayList<>(card.lines());
        lines.add((index + 1) + "/" + pages.size() + " · Click: next · Sneak: first");
        return new PlayerContextCardPayload(card.title(), lines, 200, card.accentRgb());
    }

    /** Clears the only ephemeral state at server shutdown; no player/world state is retained. */
    public static void clear(MinecraftServer server) {
        GATES.remove(Objects.requireNonNull(server, "server"));
    }

    private static Map<UUID, Session> gates(MinecraftServer server) {
        return GATES.computeIfAbsent(server, ignored -> new LinkedHashMap<>(MAX_CONNECTED_PLAYERS + 1, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<UUID, Session> eldest) {
                return size() > MAX_CONNECTED_PLAYERS;
            }
        });
    }
}
