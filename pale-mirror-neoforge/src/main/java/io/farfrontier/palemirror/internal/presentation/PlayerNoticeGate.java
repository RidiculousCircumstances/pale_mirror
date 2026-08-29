package io.farfrontier.palemirror.internal.presentation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Ephemeral per-player admission control for presentation-only notices.
 *
 * <p>This is deliberately not canonical state, a journal or a retry queue.
 * It merely prevents one physical interaction (or two overlapping NeoForge
 * interaction events) from flooding the action bar or chat.  A reconnect,
 * restart or Minecraft-time rewind may forget a suppression window without
 * changing any player-facing world fact.</p>
 */
final class PlayerNoticeGate {
    static final int MAX_RECENT_KEYS = 32;
    static final int TRANSIENT_ACTION_BAR_INTERVAL_TICKS = 10;

    enum Channel { ACTION_BAR, CHAT }

    enum Priority { TRANSIENT, ACTION, CRITICAL }

    record Notice(String key, Channel channel, Priority priority, int duplicateCooldownTicks) {
        Notice {
            if (key == null || key.isBlank() || key.length() > 160) throw new IllegalArgumentException("notice key must be 1..160 characters");
            Objects.requireNonNull(channel, "channel"); Objects.requireNonNull(priority, "priority");
            if (duplicateCooldownTicks < 0) throw new IllegalArgumentException("duplicate cooldown must not be negative");
        }
    }

    enum Decision { DELIVER, DUPLICATE, RATE_LIMITED }

    private final Map<String, Long> lastDelivered = new LinkedHashMap<>(MAX_RECENT_KEYS + 1, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) { return size() > MAX_RECENT_KEYS; }
    };
    private long lastObservedTick = Long.MIN_VALUE;
    private long lastTransientActionBarTick = Long.MIN_VALUE;

    Decision admit(Notice notice, long gameTick) {
        Objects.requireNonNull(notice, "notice");
        if (gameTick < 0) throw new IllegalArgumentException("game tick must not be negative");
        if (gameTick < lastObservedTick) resetForClockRewind();
        lastObservedTick = gameTick;
        Long previous = lastDelivered.get(notice.key());
        if (previous != null && gameTick - previous < notice.duplicateCooldownTicks()) return Decision.DUPLICATE;
        if (notice.channel() == Channel.ACTION_BAR && notice.priority() == Priority.TRANSIENT
                && lastTransientActionBarTick != Long.MIN_VALUE
                && gameTick - lastTransientActionBarTick < TRANSIENT_ACTION_BAR_INTERVAL_TICKS) {
            return Decision.RATE_LIMITED;
        }
        lastDelivered.put(notice.key(), gameTick);
        if (notice.channel() == Channel.ACTION_BAR && notice.priority() == Priority.TRANSIENT) {
            lastTransientActionBarTick = gameTick;
        }
        return Decision.DELIVER;
    }

    int retainedKeyCount() { return lastDelivered.size(); }

    private void resetForClockRewind() {
        lastDelivered.clear();
        lastTransientActionBarTick = Long.MIN_VALUE;
    }
}
