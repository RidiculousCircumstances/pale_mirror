package io.farfrontier.palemirror.internal.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PlayerNoticeGateTest {
    @Test
    void duplicateDenialCueIsSuppressedButCanBePlayedAgainAfterItsCooldown() {
        PlayerNoticeGate gate = new PlayerNoticeGate();
        var notice = new PlayerNoticeGate.Notice("frontier-v3:cargo-rejected", PlayerNoticeGate.Channel.DENIAL_CUE,
                PlayerNoticeGate.Priority.REJECTION, PlayerNoticeGate.Origin.PLAYER_ACTION, PlayerNoticeGate.DENIAL_CUE_INTERVAL_TICKS);

        assertEquals(PlayerNoticeGate.Decision.DELIVER, gate.admit(notice, 100));
        assertEquals(PlayerNoticeGate.Decision.DUPLICATE, gate.admit(notice, 100 + PlayerNoticeGate.DENIAL_CUE_INTERVAL_TICKS - 1));
        assertEquals(PlayerNoticeGate.Decision.DELIVER, gate.admit(notice, 100 + PlayerNoticeGate.DENIAL_CUE_INTERVAL_TICKS));
    }

    @Test
    void distinctPlayerActionRejectionsCannotRepeatTheCueDuringTheQuietWindow() {
        PlayerNoticeGate gate = new PlayerNoticeGate();
        var first = new PlayerNoticeGate.Notice("action:field-1", PlayerNoticeGate.Channel.DENIAL_CUE,
                PlayerNoticeGate.Priority.REJECTION, PlayerNoticeGate.Origin.PLAYER_ACTION, 100);
        var different = new PlayerNoticeGate.Notice("action:field-2", PlayerNoticeGate.Channel.DENIAL_CUE,
                PlayerNoticeGate.Priority.REJECTION, PlayerNoticeGate.Origin.PLAYER_ACTION, 100);

        assertEquals(PlayerNoticeGate.Decision.DELIVER, gate.admit(first, 100));
        assertEquals(PlayerNoticeGate.Decision.RATE_LIMITED, gate.admit(different, 105));
        assertEquals(PlayerNoticeGate.Decision.DELIVER, gate.admit(different, 100 + PlayerNoticeGate.DENIAL_CUE_INTERVAL_TICKS));
    }

    @Test
    void explicitInspectionsMayReplaceTheSingleCardImmediatelyButDuplicatesDoNotFlicker() {
        PlayerNoticeGate gate = new PlayerNoticeGate();
        var field = new PlayerNoticeGate.Notice("source:briefing:field-1", PlayerNoticeGate.Channel.CONTEXT_CARD,
                PlayerNoticeGate.Priority.CONTEXT, PlayerNoticeGate.Origin.PLAYER_ACTION, 20);
        var hive = new PlayerNoticeGate.Notice("source:briefing:hive-1", PlayerNoticeGate.Channel.CONTEXT_CARD,
                PlayerNoticeGate.Priority.CONTEXT, PlayerNoticeGate.Origin.PLAYER_ACTION, 20);

        assertEquals(PlayerNoticeGate.Decision.DELIVER, gate.admit(field, 100));
        assertEquals(PlayerNoticeGate.Decision.DELIVER, gate.admit(hive, 103));
        assertEquals(PlayerNoticeGate.Decision.DUPLICATE, gate.admit(hive, 104));
    }

    @Test
    void backgroundSimulationCannotClaimPlayerPresentationSurfaces() {
        PlayerNoticeGate gate = new PlayerNoticeGate();
        assertThrows(IllegalArgumentException.class, () -> new PlayerNoticeGate.Notice("background:combat", PlayerNoticeGate.Channel.DENIAL_CUE,
                PlayerNoticeGate.Priority.REJECTION, PlayerNoticeGate.Origin.BACKGROUND, 20));
        assertThrows(IllegalArgumentException.class, () -> new PlayerNoticeGate.Notice("player:success", PlayerNoticeGate.Channel.DENIAL_CUE,
                PlayerNoticeGate.Priority.CONTEXT, PlayerNoticeGate.Origin.PLAYER_ACTION, 20));
        assertThrows(IllegalArgumentException.class, () -> new PlayerNoticeGate.Notice("background:route", PlayerNoticeGate.Channel.CONTEXT_CARD,
                PlayerNoticeGate.Priority.CONTEXT, PlayerNoticeGate.Origin.BACKGROUND, 20));
    }

    @Test
    void retentionIsBoundedAndEvictionCannotAffectWorldState() {
        PlayerNoticeGate gate = new PlayerNoticeGate();
        for (int index = 0; index <= PlayerNoticeGate.MAX_RECENT_KEYS; index++) {
            var notice = new PlayerNoticeGate.Notice("transient:" + index, PlayerNoticeGate.Channel.DENIAL_CUE,
                    PlayerNoticeGate.Priority.REJECTION, PlayerNoticeGate.Origin.PLAYER_ACTION, 1000);
            assertEquals(PlayerNoticeGate.Decision.DELIVER, gate.admit(notice, index * PlayerNoticeGate.DENIAL_CUE_INTERVAL_TICKS));
        }

        assertEquals(PlayerNoticeGate.MAX_RECENT_KEYS, gate.retainedKeyCount(),
                "notice suppression is bounded presentation memory, never an accumulating per-player history");
    }
}
