package io.farfrontier.palemirror.internal.presentation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Ephemeral per-player presentation policy for immediate, noncanonical notices.
 *
 * <p>This is deliberately not canonical state, a journal or a retry queue.
 * It merely prevents one physical interaction (or two overlapping NeoForge
 * interaction events) from flooding the action bar or chat.  A reconnect,
 * restart or Minecraft-time rewind may forget a suppression window without
 * changing any player-facing world fact.</p>
 */
final class PlayerNoticeGate {
    static final int MAX_RECENT_KEYS = 32;
    /**
     * The action bar is an interruption, not a status feed.  It is reserved for
     * a direct rejected action and must leave room for Minecraft's own combat,
     * item and navigation feedback.
     */
    static final int ACTION_BAR_INTERVAL_TICKS = 60;

    enum Channel { ACTION_BAR, CONTEXT_CARD, CHAT }

    enum Priority { REJECTION, CONTEXT, CRITICAL }

    /** HUD is reserved for the result of an action the recipient just chose. */
    enum Origin { PLAYER_ACTION, BACKGROUND }

    record Notice(String key, Channel channel, Priority priority, Origin origin, int duplicateCooldownTicks) {
        Notice {
            if (key == null || key.isBlank() || key.length() > 160) throw new IllegalArgumentException("notice key must be 1..160 characters");
            Objects.requireNonNull(channel, "channel"); Objects.requireNonNull(priority, "priority");
            Objects.requireNonNull(origin, "origin");
            if (duplicateCooldownTicks < 0) throw new IllegalArgumentException("duplicate cooldown must not be negative");
            if (channel == Channel.ACTION_BAR && (priority != Priority.REJECTION || origin != Origin.PLAYER_ACTION)) {
                throw new IllegalArgumentException("action-bar notices must be direct player-action rejections");
            }
            if (channel == Channel.CONTEXT_CARD && (priority != Priority.CONTEXT || origin != Origin.PLAYER_ACTION)) {
                throw new IllegalArgumentException("context cards must be explicit player inspections");
            }
            if (channel == Channel.CHAT && priority != Priority.CRITICAL) {
                throw new IllegalArgumentException("chat notices are reserved for durable critical failures");
            }
        }
    }

    enum Decision { DELIVER, DUPLICATE, RATE_LIMITED }

    private final Map<String, Long> lastDelivered = new LinkedHashMap<>(MAX_RECENT_KEYS + 1, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) { return size() > MAX_RECENT_KEYS; }
    };
    private long lastObservedTick = Long.MIN_VALUE;
    private long lastActionBarTick = Long.MIN_VALUE;

    Decision admit(Notice notice, long gameTick) {
        Objects.requireNonNull(notice, "notice");
        if (gameTick < 0) throw new IllegalArgumentException("game tick must not be negative");
        if (gameTick < lastObservedTick) resetForClockRewind();
        lastObservedTick = gameTick;
        Long previous = lastDelivered.get(notice.key());
        if (previous != null && gameTick - previous < notice.duplicateCooldownTicks()) return Decision.DUPLICATE;
        if (notice.channel() == Channel.ACTION_BAR && lastActionBarTick != Long.MIN_VALUE
                && gameTick - lastActionBarTick < ACTION_BAR_INTERVAL_TICKS) {
            return Decision.RATE_LIMITED;
        }
        lastDelivered.put(notice.key(), gameTick);
        if (notice.channel() == Channel.ACTION_BAR) lastActionBarTick = gameTick;
        return Decision.DELIVER;
    }

    int retainedKeyCount() { return lastDelivered.size(); }

    private void resetForClockRewind() {
        lastDelivered.clear();
        lastActionBarTick = Long.MIN_VALUE;
    }
}
