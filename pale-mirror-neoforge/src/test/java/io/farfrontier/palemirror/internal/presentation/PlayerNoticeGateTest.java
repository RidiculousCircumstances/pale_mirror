package io.farfrontier.palemirror.internal.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PlayerNoticeGateTest {
    @Test
    void duplicateActionIsSuppressedButCanBeShownAgainAfterItsCooldown() {
        PlayerNoticeGate gate = new PlayerNoticeGate();
        var notice = new PlayerNoticeGate.Notice("frontier-v3:cargo-released", PlayerNoticeGate.Channel.ACTION_BAR,
                PlayerNoticeGate.Priority.ACTION, 8);

        assertEquals(PlayerNoticeGate.Decision.DELIVER, gate.admit(notice, 100));
        assertEquals(PlayerNoticeGate.Decision.DUPLICATE, gate.admit(notice, 107));
        assertEquals(PlayerNoticeGate.Decision.DELIVER, gate.admit(notice, 108));
    }

    @Test
    void transientBriefingsCannotReplaceTheActionBarEveryTick() {
        PlayerNoticeGate gate = new PlayerNoticeGate();
        var first = new PlayerNoticeGate.Notice("source:briefing:field-1", PlayerNoticeGate.Channel.ACTION_BAR,
                PlayerNoticeGate.Priority.TRANSIENT, 20);
        var different = new PlayerNoticeGate.Notice("source:briefing:field-2", PlayerNoticeGate.Channel.ACTION_BAR,
                PlayerNoticeGate.Priority.TRANSIENT, 20);

        assertEquals(PlayerNoticeGate.Decision.DELIVER, gate.admit(first, 100));
        assertEquals(PlayerNoticeGate.Decision.RATE_LIMITED, gate.admit(different, 105));
        assertEquals(PlayerNoticeGate.Decision.DELIVER, gate.admit(different, 110));
    }

    @Test
    void contextCardsReplaceRatherThanQueueAndCannotFlickerAcrossRapidTargets() {
        PlayerNoticeGate gate = new PlayerNoticeGate();
        var field = new PlayerNoticeGate.Notice("source:briefing:field-1", PlayerNoticeGate.Channel.CONTEXT_CARD,
                PlayerNoticeGate.Priority.CONTEXT, 20);
        var hive = new PlayerNoticeGate.Notice("source:briefing:hive-1", PlayerNoticeGate.Channel.CONTEXT_CARD,
                PlayerNoticeGate.Priority.CONTEXT, 20);

        assertEquals(PlayerNoticeGate.Decision.DELIVER, gate.admit(field, 100));
        assertEquals(PlayerNoticeGate.Decision.RATE_LIMITED, gate.admit(hive, 103));
        assertEquals(PlayerNoticeGate.Decision.DELIVER, gate.admit(hive, 104));
        assertEquals(PlayerNoticeGate.Decision.DUPLICATE, gate.admit(hive, 105));
    }

    @Test
    void criticalChatIsNotDelayedByATransientOverlayAndClockRewindClearsOnlyEphemeralState() {
        PlayerNoticeGate gate = new PlayerNoticeGate();
        var transientNotice = new PlayerNoticeGate.Notice("source:briefing:hive", PlayerNoticeGate.Channel.ACTION_BAR,
                PlayerNoticeGate.Priority.TRANSIENT, 20);
        var critical = new PlayerNoticeGate.Notice("frontier-v3:unrecorded-physical-change", PlayerNoticeGate.Channel.CHAT,
                PlayerNoticeGate.Priority.CRITICAL, 100);

        assertEquals(PlayerNoticeGate.Decision.DELIVER, gate.admit(transientNotice, 100));
        assertEquals(PlayerNoticeGate.Decision.DELIVER, gate.admit(critical, 101));
        assertEquals(PlayerNoticeGate.Decision.DELIVER, gate.admit(transientNotice, 10),
                "Minecraft presentation time can be rewound independently from Frontier v3 time");
    }

    @Test
    void retentionIsBoundedAndEvictionCannotAffectWorldState() {
        PlayerNoticeGate gate = new PlayerNoticeGate();
        for (int index = 0; index <= PlayerNoticeGate.MAX_RECENT_KEYS; index++) {
            var notice = new PlayerNoticeGate.Notice("transient:" + index, PlayerNoticeGate.Channel.ACTION_BAR,
                    PlayerNoticeGate.Priority.ACTION, 1000);
            assertEquals(PlayerNoticeGate.Decision.DELIVER, gate.admit(notice, index));
        }

        assertEquals(PlayerNoticeGate.MAX_RECENT_KEYS, gate.retainedKeyCount(),
                "notice suppression is bounded presentation memory, never an accumulating per-player history");
    }
}
