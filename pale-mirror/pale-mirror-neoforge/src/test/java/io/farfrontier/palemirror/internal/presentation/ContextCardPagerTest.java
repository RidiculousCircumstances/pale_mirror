package io.farfrontier.palemirror.internal.presentation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ContextCardPagerTest {
    @Test void pagedDeliveryActuallyConstructsAProtocolValidPayload() {
        var card = new PlayerContextCard("Town", java.util.List.of("Trade"), 0xffffff);
        var payload = PaleMirrorPlayerPresentation.pagePayload(java.util.List.of(card, card), 1);
        assertEquals(200, payload.durationTicks());
        assertEquals("2/2 · Click: next · Sneak: first", payload.lines().getLast());
    }
    @Test void pagesCycleResetOnOtherObjectExpiryRewindAndExplicitReset() {
        var pager = new ContextCardPager();
        assertEquals(0, pager.next("a", 3, 0, false));
        assertEquals(1, pager.next("a", 3, 20, false));
        assertEquals(0, pager.next("a", 2, 40, false));
        assertEquals(0, pager.next("b", 3, 60, false));
        assertEquals(1, pager.next("b", 3, 80, false));
        assertEquals(0, pager.next("b", 3, 81, true));
        assertEquals(1, pager.next("b", 3, 101, false));
        assertEquals(0, pager.next("b", 3, 702, false));
        assertEquals(0, pager.next("b", 3, 1, false));
    }
}
